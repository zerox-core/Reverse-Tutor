# -*- coding: utf-8 -*-
"""Admin stage-plan generation & stage definition API tests.

Material-driven stage generator wiring (docs/specs/material-driven-stage-generator.md):
- POST /api/admin/v1/activities/{slug}/stage-plan/generate returns a validated
  draft and persists nothing (human review gate);
- PUT  /api/admin/v1/activities/{slug}/stages stores the confirmed ladder via
  SqlAlchemyStageStore.define_activity_stages;
- GET  /api/admin/v1/activities/{slug}/stages lists the current ladder.
LLM calls go through an injected fake chat_json — no real provider traffic.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from uuid import uuid4

import httpx
import pytest
from sqlalchemy import URL

import server
from adapters.online import admin_routes
from adapters.online.admin_service import (
    admin_activity_service,
    admin_stage_service,
)
from online_db.activity_store import (
    ActivityDefinition,
    SqlAlchemyActivityStore,
)
from online_db.base import OnlineBase
from online_db.session import build_online_session_factory
from online_db.stage_generation import STAGE_GENERATION_SYSTEM_PROMPT
from online_db.stage_store import (
    ActivityStagesNotDefined,
    SqlAlchemyStageStore,
)

ADMIN_TOKEN = "test-admin-token-stage-plan-0123456789abcdef"
AUTH_HEADERS = {"Authorization": f"Bearer {ADMIN_TOKEN}"}

NOW = datetime(2026, 9, 29, 8, 0, tzinfo=UTC)
SLUG = "stage-plan-activity"
TOTAL_DAYS = 6


@dataclass
class FakeActivityRecord:
    id: object
    slug: str
    title: str
    description: str
    total_days: int


class FakeActivityStore:
    """Minimal activity store: the generate endpoint only reads meta."""

    def __init__(self) -> None:
        self.records: dict[str, FakeActivityRecord] = {}

    def seed(
        self, slug: str, *, title: str, description: str, total_days: int
    ) -> None:
        self.records[slug] = FakeActivityRecord(
            id=uuid4(),
            slug=slug,
            title=title,
            description=description,
            total_days=total_days,
        )

    def get_activity(self, slug):
        return self.records.get(slug)


def _valid_plan_payload(total_days: int = TOTAL_DAYS) -> dict:
    return {
        "stages": [
            {
                "stage_index": 1,
                "name": "基础概念",
                "capability": "学生能讲清基础概念",
                "evidence_keys": ["explain", "verify"],
                "task_day_numbers": [1, 2],
                "evidence_level": "evidenced",
                "evidence_refs": ["素材1 · 第一章"],
            },
            {
                "stage_index": 2,
                "name": "进阶综合",
                "capability": "学生能综合运用并纠错",
                "evidence_keys": ["explain", "verify"],
                "task_day_numbers": list(range(3, total_days + 1)),
                "evidence_level": "inferred",
                "evidence_refs": [],
            },
        ]
    }


def _fake_chat_json(payload, captured):
    async def fake(system, messages, *, temperature=0.7, max_tokens=2000):
        captured["system"] = system
        captured["messages"] = messages
        captured["temperature"] = temperature
        captured["max_tokens"] = max_tokens
        return payload

    return fake


@pytest.fixture
async def stage_admin_client(monkeypatch, tmp_path):
    monkeypatch.setenv("ONLINE_ADMIN_TOKEN", ADMIN_TOKEN)
    fake_store = FakeActivityStore()
    fake_store.seed(
        SLUG,
        title="素材生成活动",
        description="素材驱动阶段生成测试",
        total_days=TOTAL_DAYS,
    )
    admin_activity_service.set_activity_store(fake_store)

    url = URL.create("sqlite+pysqlite", database=str(tmp_path / "online.sqlite3"))
    factory = build_online_session_factory(url.render_as_string(hide_password=False))
    OnlineBase.metadata.create_all(factory.kw["bind"])
    SqlAlchemyActivityStore(factory).create_activity(
        ActivityDefinition(
            slug=SLUG,
            title="素材生成活动",
            description="素材驱动阶段生成测试",
            revision=1,
            rule_version=1,
            total_days=TOTAL_DAYS,
            starts_at=NOW,
            ends_at=NOW + timedelta(days=TOTAL_DAYS),
            requires_online_confirmation=False,
            allows_deferred_progress=True,
        ),
        NOW,
    )
    admin_stage_service.set_stage_store(SqlAlchemyStageStore(factory))

    transport = httpx.ASGITransport(app=server.app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        yield client, fake_store, factory
    admin_activity_service.reset_activity_store()
    admin_stage_service.reset_stage_store()
    factory.kw["bind"].dispose()


def _generate_payload() -> dict:
    return {
        "materials": [
            {"title": "目录", "text": "第一章 基础\n第二章 进阶", "ref": "upload-1"},
            {"title": "大纲", "text": "先基础后综合。"},
        ]
    }


def _confirm_stages() -> list[dict]:
    return [
        {
            "stageIndex": 1,
            "name": "基础概念",
            "capability": "学生能讲清基础概念",
            "evidenceKeys": ["explain", "verify"],
            "taskDayNumbers": [1, 2],
            "evidenceLevel": "evidenced",
            "evidenceRefs": ["素材1 · 第一章"],
        },
        {
            "stageIndex": 2,
            "name": "进阶综合",
            "capability": "学生能综合运用并纠错",
            "evidenceKeys": ["explain", "verify"],
            "taskDayNumbers": [3, 4, 5, 6],
            "evidenceLevel": "inferred",
            "evidenceRefs": [],
        },
    ]


# --- generate ---------------------------------------------------------------


async def test_generate_returns_validated_draft_without_persisting(
    stage_admin_client, monkeypatch
):
    client, _, factory = stage_admin_client
    captured = {}
    monkeypatch.setattr(
        admin_routes, "chat_json", _fake_chat_json(_valid_plan_payload(), captured)
    )

    response = await client.post(
        f"/api/admin/v1/activities/{SLUG}/stage-plan/generate",
        json=_generate_payload(),
        headers=AUTH_HEADERS,
    )

    assert response.status_code == 200, response.text
    body = response.json()
    assert body["activitySlug"] == SLUG
    assert body["totalDays"] == TOTAL_DAYS
    assert body["persisted"] is False
    assert body["inferredStageIndexes"] == [2]
    stages = body["stages"]
    assert [stage["stageIndex"] for stage in stages] == [1, 2]
    assert stages[0]["evidenceLevel"] == "evidenced"
    assert stages[0]["evidenceRefs"] == ["素材1 · 第一章"]
    assert stages[1]["evidenceLevel"] == "inferred"
    assert stages[1]["taskDayNumbers"] == [3, 4, 5, 6]
    # dry-run: nothing persisted in the stage store
    with pytest.raises(ActivityStagesNotDefined):
        SqlAlchemyStageStore(factory).list_activity_stages(SLUG)


async def test_generate_prompt_carries_activity_meta_and_defaults(
    stage_admin_client, monkeypatch
):
    client, _, _ = stage_admin_client
    captured = {}
    monkeypatch.setattr(
        admin_routes, "chat_json", _fake_chat_json(_valid_plan_payload(), captured)
    )

    response = await client.post(
        f"/api/admin/v1/activities/{SLUG}/stage-plan/generate",
        json=_generate_payload(),
        headers=AUTH_HEADERS,
    )

    assert response.status_code == 200, response.text
    assert captured["system"] == STAGE_GENERATION_SYSTEM_PROMPT
    user_prompt = captured["messages"][0]["content"]
    assert "素材生成活动" in user_prompt
    assert "素材驱动阶段生成测试" in user_prompt
    assert f"1..{TOTAL_DAYS}" in user_prompt
    assert "第一章 基础" in user_prompt
    assert captured["messages"][0]["role"] == "user"
    assert captured["temperature"] == 0.3
    assert captured["max_tokens"] == 4000


async def test_generate_honours_temperature_override(stage_admin_client, monkeypatch):
    client, _, _ = stage_admin_client
    captured = {}
    monkeypatch.setattr(
        admin_routes, "chat_json", _fake_chat_json(_valid_plan_payload(), captured)
    )
    payload = _generate_payload()
    payload["temperature"] = 0.8

    response = await client.post(
        f"/api/admin/v1/activities/{SLUG}/stage-plan/generate",
        json=payload,
        headers=AUTH_HEADERS,
    )

    assert response.status_code == 200, response.text
    assert captured["temperature"] == 0.8


async def test_generate_unknown_activity_404(stage_admin_client, monkeypatch):
    client, _, _ = stage_admin_client
    monkeypatch.setattr(
        admin_routes, "chat_json", _fake_chat_json(_valid_plan_payload(), {})
    )

    response = await client.post(
        "/api/admin/v1/activities/ghost/stage-plan/generate",
        json=_generate_payload(),
        headers=AUTH_HEADERS,
    )

    assert response.status_code == 404
    assert response.json()["error"]["code"] == "activity_not_found"


async def test_generate_invalid_llm_plan_502(stage_admin_client, monkeypatch):
    client, _, _ = stage_admin_client
    payload = _valid_plan_payload()
    payload["stages"][1]["task_day_numbers"] = [3, 4, 5]  # day 6 missing
    monkeypatch.setattr(admin_routes, "chat_json", _fake_chat_json(payload, {}))

    response = await client.post(
        f"/api/admin/v1/activities/{SLUG}/stage-plan/generate",
        json=_generate_payload(),
        headers=AUTH_HEADERS,
    )

    assert response.status_code == 502
    body = response.json()
    assert body["error"]["code"] == "stage_plan_invalid"
    assert body["error"]["retryable"] is True
    assert "missing" in body["error"]["message"]


async def test_generate_llm_failure_502(stage_admin_client, monkeypatch):
    client, _, _ = stage_admin_client

    async def boom(system, messages, *, temperature=0.7, max_tokens=2000):
        raise RuntimeError("provider down")

    monkeypatch.setattr(admin_routes, "chat_json", boom)

    response = await client.post(
        f"/api/admin/v1/activities/{SLUG}/stage-plan/generate",
        json=_generate_payload(),
        headers=AUTH_HEADERS,
    )

    assert response.status_code == 502
    body = response.json()
    assert body["error"]["code"] == "stage_plan_generation_failed"
    assert body["error"]["retryable"] is True


async def test_generate_requires_admin_token(stage_admin_client, monkeypatch):
    client, _, _ = stage_admin_client
    monkeypatch.setattr(
        admin_routes, "chat_json", _fake_chat_json(_valid_plan_payload(), {})
    )

    response = await client.post(
        f"/api/admin/v1/activities/{SLUG}/stage-plan/generate",
        json=_generate_payload(),
    )

    assert response.status_code == 401


# --- confirm (PUT stages) ---------------------------------------------------


async def test_confirm_persists_stage_definitions_with_evidence_levels(
    stage_admin_client,
):
    client, _, factory = stage_admin_client

    response = await client.put(
        f"/api/admin/v1/activities/{SLUG}/stages",
        json={"stages": _confirm_stages()},
        headers=AUTH_HEADERS,
    )

    assert response.status_code == 200, response.text
    body = response.json()
    assert body["activitySlug"] == SLUG
    assert [stage["stageIndex"] for stage in body["stages"]] == [1, 2]
    records = SqlAlchemyStageStore(factory).list_activity_stages(SLUG)
    assert len(records) == 2
    assert records[0].name == "基础概念"
    assert records[0].evidence_level == "evidenced"
    assert records[0].evidence_refs == ("素材1 · 第一章",)
    assert records[1].evidence_level == "inferred"
    assert records[1].evidence_refs == ()
    assert records[1].task_day_numbers == (3, 4, 5, 6)


async def test_confirm_redefines_in_place_preserving_ids(stage_admin_client):
    client, _, factory = stage_admin_client
    first = await client.put(
        f"/api/admin/v1/activities/{SLUG}/stages",
        json={"stages": _confirm_stages()},
        headers=AUTH_HEADERS,
    )
    assert first.status_code == 200, first.text
    ids_before = [record.id for record in SqlAlchemyStageStore(factory).list_activity_stages(SLUG)]

    updated = _confirm_stages()
    updated[0]["name"] = "基础概念（修订）"
    second = await client.put(
        f"/api/admin/v1/activities/{SLUG}/stages",
        json={"stages": updated},
        headers=AUTH_HEADERS,
    )
    assert second.status_code == 200, second.text
    records = SqlAlchemyStageStore(factory).list_activity_stages(SLUG)
    assert [record.id for record in records] == ids_before
    assert records[0].name == "基础概念（修订）"


async def test_confirm_invalid_definitions_422(stage_admin_client):
    client, _, _ = stage_admin_client
    stages = _confirm_stages()
    stages[1]["taskDayNumbers"] = [3, 4, 5, 6, 7]  # exceeds total_days

    response = await client.put(
        f"/api/admin/v1/activities/{SLUG}/stages",
        json={"stages": stages},
        headers=AUTH_HEADERS,
    )

    assert response.status_code == 422
    assert response.json()["error"]["code"] == "invalid_stage_definitions"


async def test_confirm_unknown_activity_404(stage_admin_client):
    client, _, _ = stage_admin_client

    response = await client.put(
        "/api/admin/v1/activities/ghost/stages",
        json={"stages": _confirm_stages()},
        headers=AUTH_HEADERS,
    )

    assert response.status_code == 404
    assert response.json()["error"]["code"] == "activity_not_found"


async def test_confirm_rejects_bad_evidence_level_422(stage_admin_client):
    client, _, _ = stage_admin_client
    stages = _confirm_stages()
    stages[0]["evidenceLevel"] = "guessed"

    response = await client.put(
        f"/api/admin/v1/activities/{SLUG}/stages",
        json={"stages": stages},
        headers=AUTH_HEADERS,
    )

    assert response.status_code == 422


async def test_confirm_stage_store_unavailable_503(stage_admin_client):
    client, _, _ = stage_admin_client
    admin_stage_service.reset_stage_store()

    response = await client.put(
        f"/api/admin/v1/activities/{SLUG}/stages",
        json={"stages": _confirm_stages()},
        headers=AUTH_HEADERS,
    )

    assert response.status_code == 503
    assert response.json()["error"]["code"] == "stage_store_unavailable"


# --- list (GET stages) ------------------------------------------------------


async def test_list_stages_after_confirm(stage_admin_client):
    client, _, _ = stage_admin_client
    put = await client.put(
        f"/api/admin/v1/activities/{SLUG}/stages",
        json={"stages": _confirm_stages()},
        headers=AUTH_HEADERS,
    )
    assert put.status_code == 200, put.text

    response = await client.get(
        f"/api/admin/v1/activities/{SLUG}/stages", headers=AUTH_HEADERS
    )

    assert response.status_code == 200, response.text
    body = response.json()
    assert body["activitySlug"] == SLUG
    assert [stage["name"] for stage in body["stages"]] == ["基础概念", "进阶综合"]
    assert body["stages"][0]["evidenceKeys"] == ["explain", "verify"]


async def test_list_stages_not_defined_404(stage_admin_client):
    client, _, _ = stage_admin_client

    response = await client.get(
        f"/api/admin/v1/activities/{SLUG}/stages", headers=AUTH_HEADERS
    )

    assert response.status_code == 404
    assert response.json()["error"]["code"] == "stages_not_defined"


async def test_list_stages_unknown_activity_404(stage_admin_client):
    client, _, _ = stage_admin_client

    response = await client.get(
        "/api/admin/v1/activities/ghost/stages", headers=AUTH_HEADERS
    )

    assert response.status_code == 404
    assert response.json()["error"]["code"] == "activity_not_found"
