from __future__ import annotations

from dataclasses import dataclass, field
from datetime import UTC, datetime
from uuid import uuid4

import httpx
import pytest
from sqlalchemy.exc import IntegrityError

import server
from adapters.online.admin_service import admin_activity_service
from online_db.activity_store import ActivityNotFound, ActivityStateTransitionError

ADMIN_TOKEN = "test-admin-token-0123456789abcdef0123456789abcdef"
AUTH_HEADERS = {"Authorization": f"Bearer {ADMIN_TOKEN}"}

NOW = datetime(2026, 9, 24, 8, 0, tzinfo=UTC)
STARTS_MS = int(NOW.timestamp() * 1000)
ENDS_MS = int((NOW.timestamp() + 30 * 86400) * 1000)

_ALLOWED_TRANSITIONS = {
    ("scheduled", "active"),
    ("active", "closed"),
    ("scheduled", "offline"),
    ("active", "offline"),
    ("closed", "offline"),
    ("offline", "offline"),
}


@dataclass
class FakeTaskRecord:
    id: object
    day_number: int
    title: str
    task_markdown: str
    stage_goal: str | None


@dataclass
class FakeActivityRecord:
    id: object
    slug: str
    title: str
    description: str
    revision: int
    rule_version: int
    total_days: int
    starts_at: datetime
    ends_at: datetime
    requires_online_confirmation: bool
    allows_deferred_progress: bool
    state: str
    session_template_id: str | None
    public_feedback_summary: str | None
    tasks: tuple = field(default_factory=tuple)


class FakeActivityStore:
    def __init__(self) -> None:
        self.records: dict[str, FakeActivityRecord] = {}
        self.created: list[object] = []

    def create_activity(self, definition, now):
        assert now.tzinfo is UTC
        self.created.append(definition)
        if definition.slug in self.records:
            raise IntegrityError("insert", {}, Exception("unique constraint"))
        record = FakeActivityRecord(
            id=uuid4(),
            slug=definition.slug,
            title=definition.title,
            description=definition.description,
            revision=definition.revision,
            rule_version=definition.rule_version,
            total_days=definition.total_days,
            starts_at=definition.starts_at,
            ends_at=definition.ends_at,
            requires_online_confirmation=definition.requires_online_confirmation,
            allows_deferred_progress=definition.allows_deferred_progress,
            state=definition.state,
            session_template_id=definition.session_template_id,
            public_feedback_summary=definition.public_feedback_summary,
            tasks=tuple(
                FakeTaskRecord(
                    id=uuid4(),
                    day_number=task.day_number,
                    title=task.title,
                    task_markdown=task.task_markdown,
                    stage_goal=task.stage_goal,
                )
                for task in definition.tasks
            ),
        )
        self.records[definition.slug] = record
        return record

    def get_activity(self, slug):
        return self.records.get(slug)

    def list_activities(self, *, states, limit, offset):
        items = [r for r in self.records.values() if r.state in states]
        return tuple(items[offset : offset + limit])

    def set_activity_state(self, slug, state, now):
        assert now.tzinfo is UTC
        record = self.records.get(slug)
        if record is None:
            raise ActivityNotFound(slug)
        if (record.state, state) not in _ALLOWED_TRANSITIONS:
            raise ActivityStateTransitionError(record.state, state)
        record.state = state
        return record


@pytest.fixture
async def admin_client(monkeypatch):
    monkeypatch.setenv("ONLINE_ADMIN_TOKEN", ADMIN_TOKEN)
    store = FakeActivityStore()
    admin_activity_service.set_activity_store(store)
    transport = httpx.ASGITransport(app=server.app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        yield client, store
    admin_activity_service.reset_activity_store()


def _create_payload(slug: str = "teach-back-21") -> dict:
    return {
        "slug": slug,
        "title": "21 天给 AI 讲懂一个知识点",
        "description": "每天把你学到的东西讲给 AI 听。",
        "totalDays": 2,
        "startsAtEpochMillis": STARTS_MS,
        "endsAtEpochMillis": ENDS_MS,
        "requiresOnlineConfirmation": True,
        "allowsDeferredProgress": True,
        "sessionTemplateId": "teach-back-21-v1",
        "tasks": [
            {
                "dayNumber": 1,
                "title": "第 1 天",
                "taskMarkdown": "讲清一个概念",
                "stageGoal": "完成首次讲授",
            },
            {"dayNumber": 2, "title": "第 2 天", "taskMarkdown": "再讲一个"},
        ],
    }


async def test_create_activity_as_scheduled(admin_client):
    client, store = admin_client
    response = await client.post(
        "/api/admin/v1/activities", json=_create_payload(), headers=AUTH_HEADERS
    )
    assert response.status_code == 201
    body = response.json()
    assert body["slug"] == "teach-back-21"
    assert body["state"] == "scheduled"
    assert body["revision"] == 1
    assert body["totalDays"] == 2
    assert body["startsAtEpochMillis"] == STARTS_MS
    assert body["endsAtEpochMillis"] == ENDS_MS
    assert body["sessionTemplateId"] == "teach-back-21-v1"
    assert [task["dayNumber"] for task in body["tasks"]] == [1, 2]
    assert body["tasks"][0]["stageGoal"] == "完成首次讲授"
    definition = store.created[0]
    assert definition.starts_at.tzinfo is UTC


async def test_create_activity_requires_token(admin_client):
    client, _store = admin_client
    response = await client.post("/api/admin/v1/activities", json=_create_payload())
    assert response.status_code == 401
    assert response.json()["error"]["code"] == "admin_unauthorized"


async def test_create_activity_rejects_wrong_token(admin_client):
    client, _store = admin_client
    response = await client.post(
        "/api/admin/v1/activities",
        json=_create_payload(),
        headers={"Authorization": "Bearer wrong-token"},
    )
    assert response.status_code == 401


async def test_admin_unconfigured_returns_503(admin_client, monkeypatch):
    client, _store = admin_client
    monkeypatch.delenv("ONLINE_ADMIN_TOKEN")
    response = await client.get("/api/admin/v1/activities", headers=AUTH_HEADERS)
    assert response.status_code == 503
    assert response.json()["error"]["code"] == "admin_not_configured"


async def test_store_unavailable_returns_503(monkeypatch):
    monkeypatch.setenv("ONLINE_ADMIN_TOKEN", ADMIN_TOKEN)
    admin_activity_service.reset_activity_store()
    transport = httpx.ASGITransport(app=server.app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        response = await client.get("/api/admin/v1/activities", headers=AUTH_HEADERS)
    assert response.status_code == 503
    assert response.json()["error"]["code"] == "admin_unavailable"


async def test_create_duplicate_slug_conflicts(admin_client):
    client, _store = admin_client
    first = await client.post(
        "/api/admin/v1/activities", json=_create_payload(), headers=AUTH_HEADERS
    )
    assert first.status_code == 201
    second = await client.post(
        "/api/admin/v1/activities", json=_create_payload(), headers=AUTH_HEADERS
    )
    assert second.status_code == 409
    assert second.json()["error"]["code"] == "activity_slug_conflict"


async def test_create_validates_window(admin_client):
    client, _store = admin_client
    payload = _create_payload()
    payload["endsAtEpochMillis"] = payload["startsAtEpochMillis"]
    response = await client.post(
        "/api/admin/v1/activities", json=payload, headers=AUTH_HEADERS
    )
    assert response.status_code == 422


async def test_create_validates_task_day_within_total_days(admin_client):
    client, _store = admin_client
    payload = _create_payload()
    payload["tasks"][0]["dayNumber"] = 3
    response = await client.post(
        "/api/admin/v1/activities", json=payload, headers=AUTH_HEADERS
    )
    assert response.status_code == 422


async def test_get_and_list_activities(admin_client):
    client, _store = admin_client
    await client.post(
        "/api/admin/v1/activities", json=_create_payload(), headers=AUTH_HEADERS
    )
    detail = await client.get(
        "/api/admin/v1/activities/teach-back-21", headers=AUTH_HEADERS
    )
    assert detail.status_code == 200
    assert detail.json()["title"] == "21 天给 AI 讲懂一个知识点"

    listing = await client.get("/api/admin/v1/activities", headers=AUTH_HEADERS)
    assert listing.status_code == 200
    items = listing.json()["items"]
    assert [item["slug"] for item in items] == ["teach-back-21"]

    missing = await client.get(
        "/api/admin/v1/activities/nope", headers=AUTH_HEADERS
    )
    assert missing.status_code == 404


async def test_publish_close_offline_flow(admin_client):
    client, _store = admin_client
    await client.post(
        "/api/admin/v1/activities", json=_create_payload(), headers=AUTH_HEADERS
    )
    published = await client.post(
        "/api/admin/v1/activities/teach-back-21/publish", headers=AUTH_HEADERS
    )
    assert published.status_code == 200
    assert published.json()["state"] == "active"

    closed = await client.post(
        "/api/admin/v1/activities/teach-back-21/close", headers=AUTH_HEADERS
    )
    assert closed.status_code == 200
    assert closed.json()["state"] == "closed"

    offlined = await client.post(
        "/api/admin/v1/activities/teach-back-21/offline", headers=AUTH_HEADERS
    )
    assert offlined.status_code == 200
    assert offlined.json()["state"] == "offline"


async def test_illegal_transition_returns_409(admin_client):
    client, _store = admin_client
    await client.post(
        "/api/admin/v1/activities", json=_create_payload(), headers=AUTH_HEADERS
    )
    response = await client.post(
        "/api/admin/v1/activities/teach-back-21/close", headers=AUTH_HEADERS
    )
    assert response.status_code == 409
    error = response.json()["error"]
    assert error["code"] == "invalid_state_transition"
    assert error["details"] == {"currentState": "scheduled", "targetState": "closed"}


async def test_transition_missing_activity_returns_404(admin_client):
    client, _store = admin_client
    response = await client.post(
        "/api/admin/v1/activities/ghost/publish", headers=AUTH_HEADERS
    )
    assert response.status_code == 404
