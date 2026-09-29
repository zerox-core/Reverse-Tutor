# -*- coding: utf-8 -*-
"""Admin probe-plan generation & archive API tests.

Probe-plan wiring (docs/specs/stage-progress-model.md §11.3):
- POST /api/admin/v1/activities/{slug}/participations/{accountId}/probe-plan/generate
  returns a validated draft and persists nothing (human review gate);
- PUT  /api/admin/v1/activities/{slug}/participations/{accountId}/probe-plan
  archives the confirmed plan via SqlAlchemyProbePlanStore.save_probe_plan;
- GET  .../probe-plan lists the archived plan of a participation.
LLM calls go through an injected fake chat_json — no real provider traffic.
"""

from __future__ import annotations

from datetime import UTC, datetime, timedelta
from uuid import uuid4

import httpx
import pytest
from sqlalchemy import URL

import server
from adapters.online import admin_routes
from adapters.online.admin_service import (
    admin_activity_service,
    admin_probe_plan_service,
    admin_stage_service,
)
from online_db.activity_store import (
    ActivityDefinition,
    SqlAlchemyActivityStore,
)
from online_db.base import OnlineBase
from online_db.models import AnonymousAccount
from online_db.probe_plan_prompts import PROBE_PLAN_SYSTEM_PROMPT
from online_db.probe_plan_store import (
    ProbePlanNotFound,
    SqlAlchemyProbePlanStore,
)
from online_db.session import build_online_session_factory
from online_db.stage_store import (
    StageDefinition,
    SqlAlchemyStageStore,
)

ADMIN_TOKEN = "test-admin-token-probe-plan-0123456789abcdef"
AUTH_HEADERS = {"Authorization": f"Bearer {ADMIN_TOKEN}"}

NOW = datetime(2026, 9, 29, 8, 0, tzinfo=UTC)
SLUG = "probe-plan-activity"
NO_STAGES_SLUG = "probe-plan-no-stages"
TOTAL_DAYS = 5

STAGE_DEFINITIONS = (
    StageDefinition(
        stage_index=1,
        name="基础概念",
        capability="学生能讲清基础概念",
        evidence_keys=("explain", "verify"),
        task_day_numbers=(1, 2),
    ),
    StageDefinition(
        stage_index=2,
        name="进阶综合",
        capability="学生能综合运用并纠错",
        evidence_keys=("explain", "verify"),
        task_day_numbers=(3, 4, 5),
    ),
)


def _probe(evidence_key, kind="probe_recite", **overrides):
    probe = {
        "evidence_key": evidence_key,
        "kind": kind,
        "question": f"请围绕 {evidence_key} 完成探针作答。",
        "rubric": {
            "pass": f"覆盖 {evidence_key} 的关键要点。",
            "partial": f"答出 {evidence_key} 核心但缺一块。",
            "fail_signals": ["只重复题面没有自己的话"],
        },
        "followups": ["能再具体一点吗？"],
    }
    probe.update(overrides)
    return probe


def _valid_plan_payload():
    return {
        "stages": [
            {
                "stage_index": 1,
                "entry_question": "入口问题一：描述一个你遇到的真实场景。",
                "probes": [
                    _probe("explain", "probe_recite"),
                    _probe("verify", "probe_error", followups=[]),
                ],
            },
            {
                "stage_index": 2,
                "entry_question": "入口问题二：换一个场景再试一次。",
                "probes": [
                    _probe("explain", "probe_transfer"),
                    _probe("verify", "probe_recite", followups=[]),
                ],
            },
        ]
    }


def _confirm_body(**overrides):
    payload = _valid_plan_payload()
    body = {
        "model": "fake-probe-model-v1",
        "stages": [
            {
                "stageIndex": stage["stage_index"],
                "entryQuestion": stage["entry_question"],
                "probes": [
                    {
                        "evidenceKey": probe["evidence_key"],
                        "kind": probe["kind"],
                        "question": probe["question"],
                        "rubric": {
                            "pass": probe["rubric"]["pass"],
                            "partial": probe["rubric"]["partial"],
                            "failSignals": list(probe["rubric"]["fail_signals"]),
                        },
                        "followups": list(probe["followups"]),
                    }
                    for probe in stage["probes"]
                ],
            }
            for stage in payload["stages"]
        ],
    }
    body.update(overrides)
    return body


def _fake_chat_json(payload, captured):
    async def fake(system, messages, *, temperature=0.7, max_tokens=2000):
        captured["system"] = system
        captured["messages"] = messages
        captured["temperature"] = temperature
        captured["max_tokens"] = max_tokens
        return payload

    return fake


def _create_activity(store, slug, *, title, total_days=TOTAL_DAYS):
    store.create_activity(
        ActivityDefinition(
            slug=slug,
            title=title,
            description="探针计划接线测试活动",
            revision=1,
            rule_version=1,
            total_days=total_days,
            starts_at=NOW,
            ends_at=NOW + timedelta(days=total_days),
            requires_online_confirmation=False,
            allows_deferred_progress=True,
        ),
        NOW,
    )


def _create_account(factory):
    account_id = uuid4()
    with factory() as database, database.begin():
        database.add(
            AnonymousAccount(
                id=account_id,
                status="active",
                created_at=NOW,
                last_seen_at=NOW,
            )
        )
    return account_id


@pytest.fixture
async def probe_admin_client(monkeypatch, tmp_path):
    monkeypatch.setenv("ONLINE_ADMIN_TOKEN", ADMIN_TOKEN)

    url = URL.create("sqlite+pysqlite", database=str(tmp_path / "online.sqlite3"))
    factory = build_online_session_factory(url.render_as_string(hide_password=False))
    OnlineBase.metadata.create_all(factory.kw["bind"])

    activity_store = SqlAlchemyActivityStore(factory)
    _create_activity(activity_store, SLUG, title="探针计划活动")
    _create_activity(activity_store, NO_STAGES_SLUG, title="无阶段活动")
    stage_store = SqlAlchemyStageStore(factory)
    stage_store.define_activity_stages(SLUG, STAGE_DEFINITIONS, NOW)

    account_id = _create_account(factory)
    activity_store.join_activity(
        SLUG,
        account_id,
        expected_activity_revision=1,
        idempotency_key=f"join-{account_id}",
        now=NOW,
    )

    admin_activity_service.set_activity_store(activity_store)
    admin_stage_service.set_stage_store(stage_store)
    admin_probe_plan_service.set_probe_plan_store(SqlAlchemyProbePlanStore(factory))

    transport = httpx.ASGITransport(app=server.app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        yield client, factory, account_id
    admin_activity_service.reset_activity_store()
    admin_stage_service.reset_stage_store()
    admin_probe_plan_service.reset_probe_plan_store()
    factory.kw["bind"].dispose()


def _generate_url(slug, account_id):
    return f"/api/admin/v1/activities/{slug}/participations/{account_id}/probe-plan/generate"


def _plan_url(slug, account_id):
    return f"/api/admin/v1/activities/{slug}/participations/{account_id}/probe-plan"


# --- generate ---------------------------------------------------------------


async def test_generate_returns_validated_draft_without_persisting(
    probe_admin_client, monkeypatch
):
    client, factory, account_id = probe_admin_client
    captured = {}
    monkeypatch.setattr(
        admin_routes, "chat_json", _fake_chat_json(_valid_plan_payload(), captured)
    )

    response = await client.post(
        _generate_url(SLUG, account_id), json={}, headers=AUTH_HEADERS
    )

    assert response.status_code == 200, response.text
    body = response.json()
    assert body["activitySlug"] == SLUG
    assert body["accountId"] == str(account_id)
    assert body["persisted"] is False
    stages = body["stages"]
    assert [stage["stageIndex"] for stage in stages] == [1, 2]
    assert stages[0]["entryQuestion"].startswith("入口问题一")
    probe = stages[0]["probes"][0]
    assert probe["evidenceKey"] == "explain"
    assert probe["kind"] == "probe_recite"
    assert probe["rubric"]["pass"]
    assert probe["rubric"]["partial"]
    assert probe["rubric"]["failSignals"] == ["只重复题面没有自己的话"]
    # dry-run: nothing archived in the probe plan store
    with pytest.raises(ProbePlanNotFound):
        SqlAlchemyProbePlanStore(factory).list_stage_plans(SLUG, account_id)


async def test_generate_prompt_carries_stage_definitions_and_activity_meta(
    probe_admin_client, monkeypatch
):
    client, _, account_id = probe_admin_client
    captured = {}
    monkeypatch.setattr(
        admin_routes, "chat_json", _fake_chat_json(_valid_plan_payload(), captured)
    )

    response = await client.post(
        _generate_url(SLUG, account_id), json={}, headers=AUTH_HEADERS
    )

    assert response.status_code == 200, response.text
    assert captured["system"] == PROBE_PLAN_SYSTEM_PROMPT
    user_prompt = captured["messages"][0]["content"]
    assert "探针计划活动" in user_prompt
    assert "阶段 1 · 基础概念" in user_prompt
    assert "阶段 2 · 进阶综合" in user_prompt
    assert captured["messages"][0]["role"] == "user"
    assert captured["temperature"] == 0.3
    assert captured["max_tokens"] == 6000


async def test_generate_honours_temperature_override(probe_admin_client, monkeypatch):
    client, _, account_id = probe_admin_client
    captured = {}
    monkeypatch.setattr(
        admin_routes, "chat_json", _fake_chat_json(_valid_plan_payload(), captured)
    )

    response = await client.post(
        _generate_url(SLUG, account_id),
        json={"temperature": 0.9},
        headers=AUTH_HEADERS,
    )

    assert response.status_code == 200, response.text
    assert captured["temperature"] == 0.9


async def test_generate_unknown_activity_404(probe_admin_client, monkeypatch):
    client, _, account_id = probe_admin_client
    monkeypatch.setattr(
        admin_routes, "chat_json", _fake_chat_json(_valid_plan_payload(), {})
    )

    response = await client.post(
        _generate_url("ghost", account_id), json={}, headers=AUTH_HEADERS
    )

    assert response.status_code == 404
    assert response.json()["error"]["code"] == "activity_not_found"


async def test_generate_stages_not_defined_404(probe_admin_client, monkeypatch):
    client, _, account_id = probe_admin_client
    monkeypatch.setattr(
        admin_routes, "chat_json", _fake_chat_json(_valid_plan_payload(), {})
    )

    response = await client.post(
        _generate_url(NO_STAGES_SLUG, account_id), json={}, headers=AUTH_HEADERS
    )

    assert response.status_code == 404
    assert response.json()["error"]["code"] == "stages_not_defined"


async def test_generate_invalid_llm_plan_502(probe_admin_client, monkeypatch):
    client, _, account_id = probe_admin_client
    payload = _valid_plan_payload()
    payload["stages"] = payload["stages"][:1]  # stage 2 missing
    monkeypatch.setattr(admin_routes, "chat_json", _fake_chat_json(payload, {}))

    response = await client.post(
        _generate_url(SLUG, account_id), json={}, headers=AUTH_HEADERS
    )

    assert response.status_code == 502
    body = response.json()
    assert body["error"]["code"] == "probe_plan_invalid"
    assert body["error"]["retryable"] is True
    assert "misses stages" in body["error"]["message"]


async def test_generate_llm_failure_502(probe_admin_client, monkeypatch):
    client, _, account_id = probe_admin_client

    async def boom(system, messages, *, temperature=0.7, max_tokens=2000):
        raise RuntimeError("provider down")

    monkeypatch.setattr(admin_routes, "chat_json", boom)

    response = await client.post(
        _generate_url(SLUG, account_id), json={}, headers=AUTH_HEADERS
    )

    assert response.status_code == 502
    body = response.json()
    assert body["error"]["code"] == "probe_plan_generation_failed"
    assert body["error"]["retryable"] is True


async def test_generate_requires_admin_token(probe_admin_client, monkeypatch):
    client, _, account_id = probe_admin_client
    monkeypatch.setattr(
        admin_routes, "chat_json", _fake_chat_json(_valid_plan_payload(), {})
    )

    response = await client.post(_generate_url(SLUG, account_id), json={})

    assert response.status_code == 401


# --- confirm (PUT probe-plan) -----------------------------------------------


async def test_confirm_persists_plan_and_returns_records(probe_admin_client):
    client, factory, account_id = probe_admin_client

    response = await client.put(
        _plan_url(SLUG, account_id), json=_confirm_body(), headers=AUTH_HEADERS
    )

    assert response.status_code == 200, response.text
    body = response.json()
    assert body["activitySlug"] == SLUG
    assert body["accountId"] == str(account_id)
    stages = body["stages"]
    assert [stage["stageIndex"] for stage in stages] == [1, 2]
    assert stages[0]["stageName"] == "基础概念"
    assert stages[0]["capability"] == "学生能讲清基础概念"
    assert stages[0]["model"] == "fake-probe-model-v1"
    assert stages[0]["updatedAtEpochMillis"] > 0
    records = SqlAlchemyProbePlanStore(factory).list_stage_plans(SLUG, account_id)
    assert len(records) == 2
    assert records[0].entry_question.startswith("入口问题一")
    assert records[0].probes[0].rubric.fail_signals == ("只重复题面没有自己的话",)
    assert records[1].probes[0].kind == "probe_transfer"


async def test_confirm_upserts_in_place(probe_admin_client):
    client, factory, account_id = probe_admin_client
    first = await client.put(
        _plan_url(SLUG, account_id), json=_confirm_body(), headers=AUTH_HEADERS
    )
    assert first.status_code == 200, first.text

    updated = _confirm_body(model="fake-probe-model-v2")
    updated["stages"][0]["entryQuestion"] = "入口问题一（修订版）。"
    second = await client.put(
        _plan_url(SLUG, account_id), json=updated, headers=AUTH_HEADERS
    )

    assert second.status_code == 200, second.text
    records = SqlAlchemyProbePlanStore(factory).list_stage_plans(SLUG, account_id)
    assert len(records) == 2
    assert records[0].entry_question == "入口问题一（修订版）。"
    assert records[0].model == "fake-probe-model-v2"


async def test_confirm_invalid_plan_422(probe_admin_client):
    client, _, account_id = probe_admin_client
    body = _confirm_body()
    body["stages"][0]["probes"][0]["evidenceKey"] = "not-a-criterion"

    response = await client.put(
        _plan_url(SLUG, account_id), json=body, headers=AUTH_HEADERS
    )

    assert response.status_code == 422
    assert response.json()["error"]["code"] == "invalid_probe_plan"


async def test_confirm_missing_stage_422(probe_admin_client):
    client, _, account_id = probe_admin_client
    body = _confirm_body()
    body["stages"] = body["stages"][:1]

    response = await client.put(
        _plan_url(SLUG, account_id), json=body, headers=AUTH_HEADERS
    )

    assert response.status_code == 422
    assert response.json()["error"]["code"] == "invalid_probe_plan"


async def test_confirm_unknown_activity_404(probe_admin_client):
    client, _, account_id = probe_admin_client

    response = await client.put(
        _plan_url("ghost", account_id), json=_confirm_body(), headers=AUTH_HEADERS
    )

    assert response.status_code == 404
    assert response.json()["error"]["code"] == "activity_not_found"


async def test_confirm_unknown_participation_404(probe_admin_client):
    client, _, _ = probe_admin_client

    response = await client.put(
        _plan_url(SLUG, uuid4()), json=_confirm_body(), headers=AUTH_HEADERS
    )

    assert response.status_code == 404
    assert response.json()["error"]["code"] == "participation_not_found"


async def test_confirm_stages_not_defined_404(probe_admin_client):
    client, factory, account_id = probe_admin_client

    response = await client.put(
        _plan_url(NO_STAGES_SLUG, account_id),
        json=_confirm_body(),
        headers=AUTH_HEADERS,
    )

    assert response.status_code == 404
    assert response.json()["error"]["code"] == "stages_not_defined"


async def test_confirm_probe_plan_store_unavailable_503(probe_admin_client):
    client, _, account_id = probe_admin_client
    admin_probe_plan_service.reset_probe_plan_store()

    response = await client.put(
        _plan_url(SLUG, account_id), json=_confirm_body(), headers=AUTH_HEADERS
    )

    assert response.status_code == 503
    assert response.json()["error"]["code"] == "probe_plan_store_unavailable"


# --- list (GET probe-plan) --------------------------------------------------


async def test_list_plan_after_confirm(probe_admin_client):
    client, _, account_id = probe_admin_client
    put = await client.put(
        _plan_url(SLUG, account_id), json=_confirm_body(), headers=AUTH_HEADERS
    )
    assert put.status_code == 200, put.text

    response = await client.get(_plan_url(SLUG, account_id), headers=AUTH_HEADERS)

    assert response.status_code == 200, response.text
    body = response.json()
    assert body["activitySlug"] == SLUG
    assert [stage["stageName"] for stage in body["stages"]] == ["基础概念", "进阶综合"]
    assert body["stages"][0]["probes"][1]["kind"] == "probe_error"
    assert body["stages"][0]["model"] == "fake-probe-model-v1"


async def test_list_plan_not_archived_404(probe_admin_client):
    client, _, account_id = probe_admin_client

    response = await client.get(_plan_url(SLUG, account_id), headers=AUTH_HEADERS)

    assert response.status_code == 404
    assert response.json()["error"]["code"] == "probe_plan_not_found"


async def test_list_plan_unknown_participation_404(probe_admin_client):
    client, _, _ = probe_admin_client

    response = await client.get(_plan_url(SLUG, uuid4()), headers=AUTH_HEADERS)

    assert response.status_code == 404
    assert response.json()["error"]["code"] == "participation_not_found"


async def test_list_plan_unknown_activity_404(probe_admin_client):
    client, _, account_id = probe_admin_client

    response = await client.get(_plan_url("ghost", account_id), headers=AUTH_HEADERS)

    assert response.status_code == 404
    assert response.json()["error"]["code"] == "activity_not_found"


async def test_list_plan_requires_admin_token(probe_admin_client):
    client, _, account_id = probe_admin_client

    response = await client.get(_plan_url(SLUG, account_id))

    assert response.status_code == 401
