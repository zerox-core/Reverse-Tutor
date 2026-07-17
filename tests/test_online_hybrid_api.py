from __future__ import annotations

import httpx
import pytest

import server
from adapters.online.auth_routes import reset_auth_service
from adapters.online.service import online_service


@pytest.fixture
async def client():
    reset_auth_service()
    online_service.reset()
    transport = httpx.ASGITransport(app=server.app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as c:
        yield c


async def authenticated(client, suffix: str = "one") -> tuple[dict, dict[str, str]]:
    response = await client.post(
        "/api/v1/auth/anonymous",
        json={
            "deviceId": f"device-{suffix}-0123456789",
            "idempotencyKey": f"bootstrap-{suffix}",
        },
    )
    assert response.status_code == 201
    issued = response.json()
    return issued, {"Authorization": f"Bearer {issued['accessToken']}"}


async def test_activities_list_detail_and_join_are_idempotent(client):
    issued, headers = await authenticated(client)
    listed = await client.get("/api/v1/activities")
    assert listed.status_code == 200
    activity = listed.json()["items"][0]
    assert listed.json()["nextCursor"] is None
    assert isinstance(listed.json()["updatedAtEpochMillis"], int)
    assert activity["allowsDeferredProgress"] is True
    assert activity["state"] == "active"
    assert activity["sessionTemplateId"]

    detail = await client.get(f"/api/v1/activities/{activity['id']}")
    assert detail.status_code == 200
    assert detail.json()["revision"] == activity["revision"]

    payload = {
        "deviceId": issued["deviceId"],
        "revision": activity["revision"],
        "idempotencyKey": "join-1",
    }
    first = await client.post(
        f"/api/v1/activities/{activity['id']}/join", json=payload, headers=headers
    )
    second = await client.post(
        f"/api/v1/activities/{activity['id']}/join", json=payload, headers=headers
    )

    assert first.status_code == 200
    assert second.status_code == 200
    assert first.json() == second.json()
    assert online_service.activity_write_count == 1


async def test_activity_idempotency_is_scoped_to_user_and_leaderboard_is_readable(client):
    first_identity, first_headers = await authenticated(client, "one")
    second_identity, second_headers = await authenticated(client, "two")
    first = await client.post(
        "/api/v1/activities/focus-week/progress",
        headers=first_headers,
        json={
            "deviceId": first_identity["deviceId"],
            "revision": 1,
            "idempotencyKey": "same-client-key",
            "progress": 2,
        },
    )
    second = await client.post(
        "/api/v1/activities/focus-week/progress",
        headers=second_headers,
        json={
            "deviceId": second_identity["deviceId"],
            "revision": 1,
            "idempotencyKey": "same-client-key",
            "progress": 2,
        },
    )
    leaderboard = await client.get("/api/v1/activities/focus-week/leaderboard")

    assert first.status_code == 200
    assert second.status_code == 200
    assert online_service.activity_write_count == 2
    assert len(leaderboard.json()["items"]) == 2
    assert [row["rank"] for row in leaderboard.json()["items"]] == [1, 2]
    assert set(leaderboard.json()["items"][0]) == {
        "rank",
        "displayName",
        "avatarUrl",
        "progress",
        "isCurrentUser",
    }
    assert leaderboard.json()["nextCursor"] is None
    assert isinstance(leaderboard.json()["updatedAtEpochMillis"], int)


async def test_activity_progress_write_is_idempotent(client):
    issued, headers = await authenticated(client)
    payload = {
        "deviceId": issued["deviceId"],
        "revision": 1,
        "idempotencyKey": "progress-1",
        "progress": 3,
    }

    first = await client.post(
        "/api/v1/activities/focus-week/progress", json=payload, headers=headers
    )
    second = await client.post(
        "/api/v1/activities/focus-week/progress", json=payload, headers=headers
    )

    assert first.status_code == 200
    assert first.json() == second.json()
    assert online_service.activity_write_count == 1


async def test_sync_push_isolates_invalid_or_failed_items(client):
    issued, headers = await authenticated(client)
    response = await client.post(
        "/api/v1/sync/push",
        headers=headers,
        json={
            "deviceId": issued["deviceId"],
            "cursor": None,
            "items": [
                {
                    "envelopeId": "env-ok",
                    "entityId": "plan-1",
                    "entityType": "study_plan",
                    "revision": 1,
                    "idempotencyKey": "sync-ok",
                    "payload": {"completed": True},
                },
                {
                    "envelopeId": "env-rejected",
                    "entityId": "message-1",
                    "entityType": "chat_message",
                    "revision": 1,
                    "idempotencyKey": "sync-rejected",
                    "payload": {"text": "private learning content"},
                },
            ],
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["items"][0] == {
        "envelopeId": "env-ok",
        "entityId": "plan-1",
        "accepted": True,
        "remoteRevision": 2,
        "retryable": False,
    }
    assert body["items"][1] == {
        "envelopeId": "env-rejected",
        "entityId": "message-1",
        "accepted": False,
        "errorCode": "entity_type_not_syncable",
        "retryable": False,
    }
    assert all("status" not in item for item in body["items"])


async def test_sync_push_isolates_unexpected_item_failure(client, monkeypatch):
    issued, headers = await authenticated(client)
    original = online_service._push_sync_item

    def fail_one(account_id, request, item):
        if item.entity_id == "plan-fail":
            raise RuntimeError("temporary storage error")
        return original(account_id, request, item)

    monkeypatch.setattr(online_service, "_push_sync_item", fail_one)
    response = await client.post(
        "/api/v1/sync/push",
        headers=headers,
        json={
            "deviceId": issued["deviceId"],
            "items": [
                {
                    "envelopeId": "env-fail",
                    "entityId": "plan-fail",
                    "entityType": "study_plan",
                    "revision": 1,
                    "idempotencyKey": "sync-fail",
                },
                {
                    "envelopeId": "env-ok",
                    "entityId": "plan-ok",
                    "entityType": "study_plan",
                    "revision": 1,
                    "idempotencyKey": "sync-ok",
                },
            ],
        },
    )

    assert response.status_code == 200
    assert response.json()["items"][0] == {
        "envelopeId": "env-fail",
        "entityId": "plan-fail",
        "accepted": False,
        "errorCode": "temporary_sync_failure",
        "retryable": True,
    }
    assert response.json()["items"][1]["accepted"] is True


async def test_sync_push_reuses_idempotent_result_and_pull_returns_cursor(client):
    issued, headers = await authenticated(client)
    request = {
        "deviceId": issued["deviceId"],
        "items": [
            {
                "envelopeId": "env-sync-1",
                "entityId": "plan-1",
                "entityType": "study_plan",
                "revision": 1,
                "idempotencyKey": "sync-1",
                "payload": {"completed": True},
            }
        ],
    }

    first = await client.post("/api/v1/sync/push", json=request, headers=headers)
    second = await client.post("/api/v1/sync/push", json=request, headers=headers)
    pulled = await client.post(
        "/api/v1/sync/pull",
        headers=headers,
        json={"deviceId": issued["deviceId"], "cursor": None},
    )

    assert first.json() == second.json()
    assert online_service.sync_write_count == 1
    assert pulled.status_code == 200
    assert pulled.json()["cursor"]
    assert pulled.json()["items"][0]["entityType"] == "study_plan"


async def test_weekly_insight_and_latest_release_are_available_without_learning_body(client):
    issued, headers = await authenticated(client)
    insight = await client.post(
        "/api/v1/insights/weekly",
        headers=headers,
        json={
            "deviceId": issued["deviceId"],
            "spaceId": "space-1",
            "weekStartEpochMillis": 1000,
            "sourceRevision": 4,
            "statistics": {"activeDays": 3, "completedTasks": 2},
        },
    )
    release = await client.get("/api/v1/app/releases/latest")

    assert insight.status_code == 200
    assert insight.json()["sourceRevision"] == 4
    assert "statistics" not in insight.json()
    assert release.status_code == 200
    assert release.json()["versionName"]
    assert release.json()["minimumSupportedVersionCode"] >= 1
