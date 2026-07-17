from __future__ import annotations

from dataclasses import dataclass
from datetime import UTC, datetime
from types import SimpleNamespace

import httpx
import pytest

import server
from adapters.online.auth_routes import reset_auth_service
from adapters.online.service import online_service


PUBLISHED_AT = datetime(2026, 7, 17, 8, 0, tzinfo=UTC)
STARTS_AT = datetime(2026, 7, 14, 0, 0, tzinfo=UTC)
ENDS_AT = datetime(2026, 7, 21, 0, 0, tzinfo=UTC)


@dataclass(frozen=True)
class ContentRecord:
    id: str
    slug: str
    type: str
    title: str
    summary: str
    illustration_template: str
    illustration_config: dict
    cover: object | None
    publisher_name: str | None
    published_at: datetime
    content_version: int
    body_markdown: str
    body_assets: tuple
    status: str = "published"


class RecordingContentPort:
    def __init__(self) -> None:
        self.record = ContentRecord(
            id="public-028",
            slug="verify-before-opening-links",
            type="public_interest",
            title="Verify links before opening them",
            summary="Three checks for suspicious links.",
            illustration_template="dialogue-security-01",
            illustration_config={"dialogues": ["Is this link safe?", "Check its source first."]},
            cover=None,
            publisher_name="Reverse Tutor",
            published_at=PUBLISHED_AT,
            content_version=2,
            body_markdown="# Check the source\n\nPause before opening a link.",
            body_assets=(),
        )
        self.queries: list[object] = []

    def list_published(self, query):
        self.queries.append(query)
        return SimpleNamespace(
            version=28,
            updated_at=PUBLISHED_AT,
            items=(self.record,),
            next_cursor="cursor-2",
        )

    def get_by_slug(self, slug, at):
        assert at.tzinfo is UTC
        if slug == "offline-article":
            return SimpleNamespace(**{**self.record.__dict__, "slug": slug, "status": "offline"})
        if slug == self.record.slug:
            return self.record
        return None


class RecordingActivityPort:
    def __init__(self) -> None:
        self.activity = SimpleNamespace(
            id="focus-week",
            title="Focus Week",
            description="Complete focused local study sessions this week.",
            revision=1,
            starts_at=STARTS_AT,
            ends_at=ENDS_AT,
            requires_online_confirmation=False,
            allows_deferred_progress=True,
            state="active",
            session_template_id="focus-week-v1",
        )
        self.commands: list[object] = []
        self.results: dict[tuple[str, str, str], object] = {}

    def list_activities(self, query):
        assert query.at.tzinfo is UTC
        assert query.cursor == "activities-2"
        assert query.limit == 7
        return SimpleNamespace(
            items=(self.activity,),
            next_cursor=None,
            updated_at=STARTS_AT,
        )

    def get_activity(self, activity_id, at):
        assert at.tzinfo is UTC
        return self.activity if activity_id == self.activity.id else None

    def join(self, command):
        return self._write("join", command, joined=True, progress=0, state="joined")

    def update_progress(self, command):
        return self._write(
            "progress",
            command,
            joined=True,
            progress=command.progress,
            state="joined",
        )

    def leave(self, command):
        return self._write("leave", command, joined=False, progress=3, state="left")

    def leaderboard(self, query):
        assert query.at.tzinfo is UTC
        assert query.cursor == "leaders-2"
        assert query.limit == 9
        return SimpleNamespace(
            items=(
                SimpleNamespace(
                    rank=1,
                    display_name="Learner",
                    avatar_url=None,
                    progress=3,
                    is_current_user=False,
                ),
            ),
            next_cursor=None,
            updated_at=STARTS_AT,
        )

    def _write(self, operation, command, *, joined, progress, state):
        self.commands.append(command)
        if command.activity_id != self.activity.id:
            return None
        key = (operation, command.account_id, command.idempotency_key)
        if key not in self.results:
            self.results[key] = SimpleNamespace(
                activity_id=command.activity_id,
                user_id=command.account_id,
                joined=joined,
                progress=progress,
                revision=max(1, command.revision),
                state=state,
                idempotency_key=command.idempotency_key,
            )
        return self.results[key]


@pytest.fixture
async def content_client():
    reset_auth_service()
    online_service.reset()
    content_port = RecordingContentPort()
    activity_port = RecordingActivityPort()
    online_service.set_content_activity_ports(
        content_port=content_port,
        activity_port=activity_port,
    )
    transport = httpx.ASGITransport(app=server.app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        yield client, content_port, activity_port
    online_service.reset_content_activity_ports()


async def authenticated(client):
    response = await client.post(
        "/api/v1/auth/anonymous",
        json={
            "deviceId": "device-content-0123456789",
            "idempotencyKey": "bootstrap-content",
        },
    )
    assert response.status_code == 201
    issued = response.json()
    return issued, {"Authorization": f"Bearer {issued['accessToken']}"}


async def test_content_feed_is_public_lightweight_filterable_and_cacheable(content_client):
    client, content_port, _ = content_client
    first = await client.get(
        "/api/v1/content/feed",
        params={"cursor": "feed-2", "limit": 1, "types": "public_interest,announcement"},
        headers={"X-Request-Id": "req-content-feed"},
    )

    assert first.status_code == 200
    assert first.headers["X-Request-Id"] == "req-content-feed"
    assert first.headers["ETag"]
    assert first.json()["version"] == 28
    assert first.json()["nextCursor"] == "cursor-2"
    assert first.json()["items"][0]["publishedAtEpochMillis"] == 1_784_275_200_000
    assert "bodyMarkdown" not in first.json()["items"][0]
    assert "bodyAssets" not in first.json()["items"][0]
    query = content_port.queries[0]
    assert query.cursor == "feed-2"
    assert query.limit == 1
    assert query.content_types == frozenset({"public_interest", "announcement"})
    assert query.at.tzinfo is UTC

    unchanged = await client.get(
        "/api/v1/content/feed",
        headers={"If-None-Match": first.headers["ETag"]},
    )
    assert unchanged.status_code == 304
    assert unchanged.content == b""
    assert unchanged.headers["ETag"] == first.headers["ETag"]


async def test_content_detail_is_public_and_offline_or_missing_errors_are_canonical(content_client):
    client, _, _ = content_client
    detail = await client.get(
        "/api/v1/content/verify-before-opening-links",
        headers={"X-Request-Id": "req-content-detail"},
    )
    missing = await client.get("/api/v1/content/missing")
    offline = await client.get("/api/v1/content/offline-article")

    assert detail.status_code == 200
    assert detail.headers["ETag"]
    assert detail.headers["X-Request-Id"] == "req-content-detail"
    assert detail.json()["bodyMarkdown"].startswith("# Check the source")
    assert detail.json()["bodyAssets"] == []
    assert missing.status_code == 404
    assert missing.json()["error"]["code"] == "content_not_found"
    assert offline.status_code == 410
    assert offline.json()["error"]["code"] == "content_offline"


async def test_activity_reads_are_public_paginated_and_canonical(content_client):
    client, _, _ = content_client
    listed = await client.get(
        "/api/v1/activities",
        params={"cursor": "activities-2", "limit": 7},
    )
    detail = await client.get("/api/v1/activities/focus-week")
    leaderboard = await client.get(
        "/api/v1/activities/focus-week/leaderboard",
        params={"cursor": "leaders-2", "limit": 9},
    )

    assert listed.status_code == 200
    assert listed.json()["items"][0]["startsAtEpochMillis"] == 1_783_987_200_000
    assert detail.status_code == 200
    assert detail.json()["id"] == "focus-week"
    assert leaderboard.status_code == 200
    assert leaderboard.json()["items"] == [
        {
            "rank": 1,
            "displayName": "Learner",
            "avatarUrl": None,
            "progress": 3,
            "isCurrentUser": False,
        }
    ]


async def test_activity_writes_require_bearer_and_pass_authenticated_ownership_to_port(content_client):
    client, _, activity_port = content_client
    payload = {
        "deviceId": "device-content-0123456789",
        "revision": 1,
        "idempotencyKey": "join-content-1",
    }
    unauthorized = await client.post(
        "/api/v1/activities/focus-week/join",
        json=payload,
    )
    issued, headers = await authenticated(client)
    first = await client.post(
        "/api/v1/activities/focus-week/join",
        json=payload,
        headers=headers,
    )
    replay = await client.post(
        "/api/v1/activities/focus-week/join",
        json=payload,
        headers=headers,
    )

    assert unauthorized.status_code == 401
    assert unauthorized.json()["error"]["code"] == "unauthorized"
    assert first.status_code == 200
    assert first.json() == replay.json()
    command = activity_port.commands[0]
    assert command.account_id == issued["accountId"]
    assert command.device_id == issued["deviceId"]
    assert command.at.tzinfo is UTC


async def test_activity_progress_and_leave_use_persistent_port_commands(content_client):
    client, _, activity_port = content_client
    issued, headers = await authenticated(client)
    progress = await client.post(
        "/api/v1/activities/focus-week/progress",
        headers=headers,
        json={
            "deviceId": issued["deviceId"],
            "revision": 1,
            "idempotencyKey": "progress-content-1",
            "progress": 3,
        },
    )
    left = await client.request(
        "DELETE",
        "/api/v1/activities/focus-week/participation",
        headers=headers,
        json={
            "deviceId": issued["deviceId"],
            "revision": progress.json()["revision"],
            "idempotencyKey": "leave-content-1",
        },
    )

    assert progress.status_code == 200
    assert progress.json()["progress"] == 3
    assert left.status_code == 200
    assert left.json()["joined"] is False
    assert activity_port.commands[-2].progress == 3
    assert activity_port.commands[-1].account_id == issued["accountId"]


async def test_activity_idempotency_keys_follow_the_canonical_length_limit(content_client):
    client, _, _ = content_client
    issued, headers = await authenticated(client)
    response = await client.post(
        "/api/v1/activities/focus-week/join",
        headers=headers,
        json={
            "deviceId": issued["deviceId"],
            "revision": 1,
            "idempotencyKey": "x" * 129,
        },
    )

    assert response.status_code == 422
    assert response.json()["error"]["code"] == "invalid_request"
