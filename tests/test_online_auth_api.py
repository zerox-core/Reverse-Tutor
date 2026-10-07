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
    transport = httpx.ASGITransport(app=server.app, raise_app_exceptions=False)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as value:
        yield value


async def bootstrap_client(client, *, device_id="device-0123456789", key="bootstrap-1"):
    response = await client.post(
        "/api/v1/auth/anonymous",
        json={"deviceId": device_id, "idempotencyKey": key, "appVersionCode": 1},
    )
    assert response.status_code == 201
    return response.json()


def bearer(issued: dict) -> dict[str, str]:
    return {"Authorization": f"Bearer {issued['accessToken']}"}


async def test_bootstrap_me_list_and_revoke(client):
    issued = await bootstrap_client(client)

    me = await client.get("/api/v1/auth/me", headers=bearer(issued))
    sessions = await client.get("/api/v1/auth/sessions", headers=bearer(issued))
    revoked = await client.delete(
        f"/api/v1/auth/sessions/{issued['sessionId']}", headers=bearer(issued)
    )

    assert me.status_code == 200
    assert me.json()["accountId"] == issued["accountId"]
    assert sessions.json()["items"][0]["sessionId"] == issued["sessionId"]
    assert sessions.json()["items"][0]["current"] is True
    assert revoked.status_code == 204
    assert await await_client_status(client, "/api/v1/auth/me", bearer(issued)) == 401


async def test_refresh_is_rotated_idempotently_and_replay_revokes_family(client):
    issued = await bootstrap_client(client)
    request = {"refreshToken": issued["refreshToken"], "idempotencyKey": "refresh-1"}

    first = await client.post("/api/v1/auth/refresh", json=request)
    retried = await client.post("/api/v1/auth/refresh", json=request)
    replayed = await client.post(
        "/api/v1/auth/refresh",
        json={"refreshToken": issued["refreshToken"], "idempotencyKey": "refresh-2"},
    )

    assert first.status_code == 200
    assert first.json() == retried.json()
    assert first.json()["refreshToken"] != issued["refreshToken"]
    assert replayed.status_code == 401
    assert replayed.json()["error"]["code"] == "refresh_token_replay"
    assert await await_client_status(
        client, "/api/v1/auth/me", bearer(first.json())
    ) == 401


async def test_bootstrap_rejects_a_registered_device_but_replays_same_idempotency_key(client):
    issued = await bootstrap_client(client, device_id="device-0123456789", key="bootstrap-1")
    replay = await bootstrap_client(client, device_id="device-0123456789", key="bootstrap-1")
    assert replay == issued

    rejected = await client.post(
        "/api/v1/auth/anonymous",
        json={"deviceId": "device-0123456789", "idempotencyKey": "bootstrap-2"},
    )
    assert rejected.status_code == 409
    assert rejected.json()["error"]["code"] == "device_already_registered"


async def test_protected_write_uses_bearer_subject_not_body_user(client):
    issued = await bootstrap_client(client)
    response = await client.post(
        "/api/v1/activities/focus-week/join",
        headers=bearer(issued),
        json={
            "deviceId": issued["deviceId"],
            "revision": 1,
            "idempotencyKey": "join-1",
        },
    )

    assert response.status_code == 200
    assert response.json()["userId"] == issued["accountId"]

    forged = await client.post(
        "/api/v1/activities/focus-week/join",
        headers=bearer(issued),
        json={
            "userId": "forged-account",
            "deviceId": issued["deviceId"],
            "revision": 1,
            "idempotencyKey": "join-forged",
        },
    )
    assert forged.status_code == 422


@pytest.mark.parametrize(
    ("method", "path", "body"),
    [
        ("GET", "/api/v1/auth/me", None),
        ("GET", "/api/v1/auth/sessions", None),
        (
            "POST",
            "/api/v1/activities/focus-week/join",
            {"deviceId": "device-0123456789", "revision": 1, "idempotencyKey": "join-1"},
        ),
        ("POST", "/api/v1/sync/pull", {"deviceId": "device-0123456789"}),
        (
            "POST",
            "/api/v1/insights/weekly",
            {
                "deviceId": "device-0123456789",
                "spaceId": "space-1",
                "weekStartEpochMillis": 0,
                "sourceRevision": 0,
            },
        ),
    ],
)
async def test_protected_endpoints_require_bearer(client, method, path, body):
    response = await client.request(method, path, json=body)

    assert response.status_code == 401
    assert response.json()["error"]["code"] == "unauthorized"


async def test_revoke_hides_session_outside_authenticated_account(client):
    first = await bootstrap_client(client)
    second = await bootstrap_client(
        client, device_id="device-9876543210", key="bootstrap-2"
    )

    response = await client.delete(
        f"/api/v1/auth/sessions/{second['sessionId']}", headers=bearer(first)
    )

    assert response.status_code == 404
    assert response.json()["error"]["code"] == "session_not_found"


async def await_client_status(client, path: str, headers: dict[str, str]) -> int:
    return (await client.get(path, headers=headers)).status_code
