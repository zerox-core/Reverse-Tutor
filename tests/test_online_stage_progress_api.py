# -*- coding: utf-8 -*-
"""Participant stage-progress API tests (evidence write-back).

docs/specs/stage-progress-model.md §3 EvidenceEvent:
- POST /api/v1/activities/{activityId}/evidence records one evidence event,
  recomputes the completed-stage prefix and dual-writes the legacy
  participation progress; skip-ahead evidence is bookkept but never promotes;
- GET  /api/v1/activities/{activityId}/stage-progress returns the snapshot.
Real SQLite store + production auth service; no LLM traffic at all.
"""

from __future__ import annotations

from datetime import UTC, datetime, timedelta
from uuid import UUID, uuid4

import httpx
import pytest
from sqlalchemy import URL, select

import server
from adapters.online.auth_routes import (
    create_production_auth_service,
    reset_auth_service,
    set_auth_service,
)
from adapters.online.stage_progress_service import stage_progress_service
from online_db.activity_store import (
    ActivityDefinition,
    SqlAlchemyActivityStore,
)
from online_db.auth_store import SqlAlchemyAuthStore
from online_db.base import OnlineBase
from online_db.idempotency_store import SqlAlchemyIdempotencyStore
from online_db.models import ActivityEvidenceEvent, ActivityStage
from online_db.session import build_online_session_factory
from online_db.stage_store import (
    StageDefinition,
    SqlAlchemyStageStore,
)

NOW = datetime(2026, 9, 29, 8, 0, tzinfo=UTC)
SLUG = "evidence-activity"
NO_STAGES_SLUG = "evidence-no-stages"
DEVICE_ID = "device-stage-progress-0123456789"

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
        evidence_keys=("explain",),
        task_day_numbers=(3, 4, 5),
    ),
)


@pytest.fixture
async def progress_client(monkeypatch, tmp_path):
    monkeypatch.setenv(
        "ONLINE_AUTH_SIGNING_KEY", "test-signing-key-with-at-least-32-bytes"
    )
    monkeypatch.setenv(
        "ONLINE_AUTH_REFRESH_PEPPER", "test-refresh-pepper-with-at-least-32-bytes"
    )
    monkeypatch.setenv(
        "ONLINE_AUTH_IDEMPOTENCY_SEALING_KEY", "test-idempotency-sealing-key"
    )

    url = URL.create("sqlite+pysqlite", database=str(tmp_path / "online.sqlite3"))
    factory = build_online_session_factory(url.render_as_string(hide_password=False))
    OnlineBase.metadata.create_all(factory.kw["bind"])

    activity_store = SqlAlchemyActivityStore(factory)
    for slug in (SLUG, NO_STAGES_SLUG):
        activity_store.create_activity(
            ActivityDefinition(
                slug=slug,
                title="证据写回测试活动",
                description="stage progress api",
                revision=1,
                rule_version=1,
                total_days=5,
                starts_at=NOW,
                ends_at=NOW + timedelta(days=5),
                requires_online_confirmation=False,
                allows_deferred_progress=True,
            ),
            NOW,
        )
    stage_store = SqlAlchemyStageStore(factory)
    stage_store.define_activity_stages(SLUG, STAGE_DEFINITIONS, NOW)

    set_auth_service(
        create_production_auth_service(
            auth_store=SqlAlchemyAuthStore(factory),
            idempotency_store=SqlAlchemyIdempotencyStore(factory),
        )
    )
    stage_progress_service.set_stage_store(stage_store)

    transport = httpx.ASGITransport(app=server.app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        yield client, factory, activity_store
    stage_progress_service.reset_stage_store()
    reset_auth_service()
    factory.kw["bind"].dispose()


async def _bootstrap(client, device_id=DEVICE_ID):
    response = await client.post(
        "/api/v1/auth/anonymous",
        json={"deviceId": device_id, "idempotencyKey": f"bootstrap-{uuid4()}"},
    )
    assert response.status_code == 201, response.text
    issued = response.json()
    return issued, {"Authorization": f"Bearer {issued['accessToken']}"}


def _join(activity_store, slug, account_id):
    return activity_store.join_activity(
        slug,
        UUID(str(account_id)),
        expected_activity_revision=1,
        idempotency_key=f"join-{uuid4()}",
        now=NOW,
    )


def _evidence_body(stage_index, evidence_key, idempotency_key, **overrides):
    body = {
        "deviceId": DEVICE_ID,
        "stageIndex": stage_index,
        "evidenceKey": evidence_key,
        "kind": "probe_recite",
        "artifactRef": f"session://demo/{idempotency_key}",
        "idempotencyKey": idempotency_key,
    }
    body.update(overrides)
    return body


# --- record evidence --------------------------------------------------------


async def test_record_evidence_returns_snapshot(progress_client):
    client, _, activity_store = progress_client
    issued, headers = await _bootstrap(client)
    _join(activity_store, SLUG, issued["accountId"])

    response = await client.post(
        f"/api/v1/activities/{SLUG}/evidence",
        json=_evidence_body(1, "explain", "ev-1"),
        headers=headers,
    )

    assert response.status_code == 200, response.text
    body = response.json()
    assert body["activitySlug"] == SLUG
    assert body["stageDone"] == 0
    assert body["currentStageIndex"] == 1
    stage1 = body["stages"][0]
    assert stage1["stageIndex"] == 1
    assert stage1["satisfiedKeys"] == ["explain"]
    assert stage1["requiredKeys"] == ["explain", "verify"]
    assert stage1["complete"] is False
    assert body["updatedAtEpochMillis"] > 0


async def test_completing_stage_promotes_and_dual_writes_progress(progress_client):
    client, _, activity_store = progress_client
    issued, headers = await _bootstrap(client)
    _join(activity_store, SLUG, issued["accountId"])

    await client.post(
        f"/api/v1/activities/{SLUG}/evidence",
        json=_evidence_body(1, "explain", "ev-1"),
        headers=headers,
    )
    response = await client.post(
        f"/api/v1/activities/{SLUG}/evidence",
        json=_evidence_body(1, "verify", "ev-2"),
        headers=headers,
    )

    assert response.status_code == 200, response.text
    body = response.json()
    assert body["stageDone"] == 1
    assert body["currentStageIndex"] == 2
    assert body["stages"][0]["complete"] is True
    # dual-write: stage 1 covers task days 1-2 -> legacy progress = 2
    assert body["progress"] == 2
    assert body["revision"] >= 1


async def test_skip_ahead_evidence_is_bookkept_without_promoting(progress_client):
    client, factory, activity_store = progress_client
    issued, headers = await _bootstrap(client)
    _join(activity_store, SLUG, issued["accountId"])

    response = await client.post(
        f"/api/v1/activities/{SLUG}/evidence",
        json=_evidence_body(2, "explain", "ev-skip"),
        headers=headers,
    )

    assert response.status_code == 200, response.text
    body = response.json()
    assert body["stageDone"] == 0
    assert body["currentStageIndex"] == 1
    assert body["stages"][1]["satisfiedKeys"] == ["explain"]
    with factory() as database:
        event = database.scalar(
            select(ActivityEvidenceEvent)
            .join(ActivityStage, ActivityStage.id == ActivityEvidenceEvent.stage_id)
            .where(ActivityStage.stage_index == 2)
        )
        assert event is not None
        assert event.path_tag == "skip_ahead"


async def test_evidence_replay_is_idempotent(progress_client):
    client, factory, activity_store = progress_client
    issued, headers = await _bootstrap(client)
    _join(activity_store, SLUG, issued["accountId"])

    first = await client.post(
        f"/api/v1/activities/{SLUG}/evidence",
        json=_evidence_body(1, "explain", "ev-1"),
        headers=headers,
    )
    replay = await client.post(
        f"/api/v1/activities/{SLUG}/evidence",
        json=_evidence_body(1, "explain", "ev-1"),
        headers=headers,
    )
    same_content_new_key = await client.post(
        f"/api/v1/activities/{SLUG}/evidence",
        json=_evidence_body(
            1, "explain", "ev-1b", artifactRef="session://demo/ev-1"
        ),
        headers=headers,
    )

    assert first.status_code == 200, first.text
    assert replay.status_code == 200, replay.text
    assert same_content_new_key.status_code == 200, same_content_new_key.text
    with factory() as database:
        count = len(
            database.scalars(select(ActivityEvidenceEvent)).all()
        )
    assert count == 1


async def test_conflicting_evidence_409(progress_client):
    client, _, activity_store = progress_client
    issued, headers = await _bootstrap(client)
    _join(activity_store, SLUG, issued["accountId"])

    first = await client.post(
        f"/api/v1/activities/{SLUG}/evidence",
        json=_evidence_body(1, "explain", "ev-1"),
        headers=headers,
    )
    conflict = await client.post(
        f"/api/v1/activities/{SLUG}/evidence",
        json=_evidence_body(
            1, "explain", "ev-2", kind="probe_transfer", artifactRef="session://other"
        ),
        headers=headers,
    )

    assert first.status_code == 200, first.text
    assert conflict.status_code == 409
    assert conflict.json()["error"]["code"] == "stage_evidence_conflict"


async def test_evidence_key_not_a_criterion_422(progress_client):
    client, _, activity_store = progress_client
    issued, headers = await _bootstrap(client)
    _join(activity_store, SLUG, issued["accountId"])

    response = await client.post(
        f"/api/v1/activities/{SLUG}/evidence",
        json=_evidence_body(1, "not-a-criterion", "ev-1"),
        headers=headers,
    )

    assert response.status_code == 422
    assert response.json()["error"]["code"] == "invalid_evidence"


async def test_invalid_kind_rejected_422(progress_client):
    client, _, activity_store = progress_client
    issued, headers = await _bootstrap(client)
    _join(activity_store, SLUG, issued["accountId"])

    response = await client.post(
        f"/api/v1/activities/{SLUG}/evidence",
        json=_evidence_body(1, "explain", "ev-1", kind="made_up_kind"),
        headers=headers,
    )

    assert response.status_code == 422


async def test_unknown_stage_index_404(progress_client):
    client, _, activity_store = progress_client
    issued, headers = await _bootstrap(client)
    _join(activity_store, SLUG, issued["accountId"])

    response = await client.post(
        f"/api/v1/activities/{SLUG}/evidence",
        json=_evidence_body(9, "explain", "ev-1"),
        headers=headers,
    )

    assert response.status_code == 404
    assert response.json()["error"]["code"] == "stage_not_found"


async def test_unknown_activity_404(progress_client):
    client, _, activity_store = progress_client
    issued, headers = await _bootstrap(client)
    _join(activity_store, SLUG, issued["accountId"])

    response = await client.post(
        "/api/v1/activities/ghost/evidence",
        json=_evidence_body(1, "explain", "ev-1"),
        headers=headers,
    )

    assert response.status_code == 404
    assert response.json()["error"]["code"] == "activity_not_found"


async def test_stages_not_defined_404(progress_client):
    client, _, activity_store = progress_client
    issued, headers = await _bootstrap(client)
    _join(activity_store, NO_STAGES_SLUG, issued["accountId"])

    response = await client.post(
        f"/api/v1/activities/{NO_STAGES_SLUG}/evidence",
        json=_evidence_body(1, "explain", "ev-1"),
        headers=headers,
    )

    assert response.status_code == 404
    assert response.json()["error"]["code"] == "stages_not_defined"


async def test_evidence_without_participation_404(progress_client):
    client, _, _ = progress_client
    issued, headers = await _bootstrap(client)

    response = await client.post(
        f"/api/v1/activities/{SLUG}/evidence",
        json=_evidence_body(1, "explain", "ev-1"),
        headers=headers,
    )

    assert response.status_code == 404
    assert response.json()["error"]["code"] == "participation_not_found"


async def test_device_mismatch_403(progress_client):
    client, _, activity_store = progress_client
    issued, headers = await _bootstrap(client)
    _join(activity_store, SLUG, issued["accountId"])

    response = await client.post(
        f"/api/v1/activities/{SLUG}/evidence",
        json=_evidence_body(1, "explain", "ev-1", deviceId="another-device"),
        headers=headers,
    )

    assert response.status_code == 403
    assert response.json()["error"]["code"] == "device_mismatch"


async def test_evidence_requires_auth(progress_client):
    client, _, _ = progress_client

    response = await client.post(
        f"/api/v1/activities/{SLUG}/evidence",
        json=_evidence_body(1, "explain", "ev-1"),
    )

    assert response.status_code == 401


async def test_stage_store_unavailable_503(progress_client):
    client, _, activity_store = progress_client
    issued, headers = await _bootstrap(client)
    _join(activity_store, SLUG, issued["accountId"])
    stage_progress_service.reset_stage_store()

    response = await client.post(
        f"/api/v1/activities/{SLUG}/evidence",
        json=_evidence_body(1, "explain", "ev-1"),
        headers=headers,
    )

    assert response.status_code == 503
    assert response.json()["error"]["code"] == "stage_progress_unavailable"


# --- stage progress snapshot -------------------------------------------------


async def test_get_stage_progress_snapshot(progress_client):
    client, _, activity_store = progress_client
    issued, headers = await _bootstrap(client)
    _join(activity_store, SLUG, issued["accountId"])
    await client.post(
        f"/api/v1/activities/{SLUG}/evidence",
        json=_evidence_body(1, "explain", "ev-1"),
        headers=headers,
    )

    response = await client.get(
        f"/api/v1/activities/{SLUG}/stage-progress", headers=headers
    )

    assert response.status_code == 200, response.text
    body = response.json()
    assert body["activitySlug"] == SLUG
    assert body["stages"][0]["satisfiedKeys"] == ["explain"]
    assert body["stages"][1]["complete"] is False


async def test_get_stage_progress_without_participation_404(progress_client):
    client, _, _ = progress_client
    _, headers = await _bootstrap(client)

    response = await client.get(
        f"/api/v1/activities/{SLUG}/stage-progress", headers=headers
    )

    assert response.status_code == 404
    assert response.json()["error"]["code"] == "participation_not_found"


async def test_get_stage_progress_unknown_activity_404(progress_client):
    client, _, _ = progress_client
    _, headers = await _bootstrap(client)

    response = await client.get(
        "/api/v1/activities/ghost/stage-progress", headers=headers
    )

    assert response.status_code == 404
    assert response.json()["error"]["code"] == "activity_not_found"


async def test_get_stage_progress_requires_auth(progress_client):
    client, _, _ = progress_client

    response = await client.get(f"/api/v1/activities/{SLUG}/stage-progress")

    assert response.status_code == 401
