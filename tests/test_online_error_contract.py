from __future__ import annotations

import httpx
import pytest

import server
from adapters.online.service import online_service


@pytest.fixture
async def client():
    online_service.reset()
    transport = httpx.ASGITransport(app=server.app, raise_app_exceptions=False)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as value:
        yield value


async def test_online_errors_are_canonical_and_echo_request_id(client):
    response = await client.get(
        "/api/v1/activities/missing",
        headers={"X-Request-Id": "req-contract-1"},
    )

    assert response.status_code == 404
    assert response.headers["X-Request-Id"] == "req-contract-1"
    assert response.json() == {
        "error": {
            "code": "activity_not_found",
            "message": "Activity not found",
            "retryable": False,
            "userAction": "none",
            "requestId": "req-contract-1",
            "details": {},
        }
    }


async def test_validation_error_does_not_return_fastapi_detail(client):
    response = await client.post("/api/v1/auth/anonymous", json={})

    assert response.status_code == 422
    assert set(response.json()) == {"error"}
    assert response.json()["error"]["code"] == "invalid_request"


async def test_invalid_request_id_is_replaced(client):
    response = await client.get(
        "/api/v1/activities/missing",
        headers={"X-Request-Id": "x" * 129},
    )

    request_id = response.headers["X-Request-Id"]
    assert request_id.startswith("req_")
    assert response.json()["error"]["requestId"] == request_id


async def test_unexpected_online_error_does_not_leak_exception(client, monkeypatch):
    def fail():
        raise RuntimeError("secret internal failure")

    monkeypatch.setattr(online_service, "list_activities", fail)
    response = await client.get("/api/v1/activities")

    assert response.status_code == 500
    assert response.json()["error"]["code"] == "server_error"
    assert "secret internal failure" not in response.text


async def test_legacy_api_error_shape_is_unchanged(client):
    response = await client.get("/api/sessions/missing")

    assert response.status_code == 404
    assert response.json() == {"detail": "session not found"}


async def test_unmatched_online_route_is_still_canonical(client):
    response = await client.get("/api/v1/not-a-real-route")

    assert response.status_code == 404
    assert set(response.json()) == {"error"}
    assert response.json()["error"]["code"] == "not_found"
