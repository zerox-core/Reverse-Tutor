from __future__ import annotations

import httpx
import pytest

import server
from adapters.online.health import reset_online_runtime_status, set_online_runtime_status
from adapters.online.service import online_service


@pytest.fixture
async def health_client():
    online_service.reset()
    reset_online_runtime_status()
    transport = httpx.ASGITransport(app=server.app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        yield client
    reset_online_runtime_status()


async def test_online_health_is_public_minimal_and_reports_catalog(health_client):
    set_online_runtime_status(mode="memory", schema_head="0003_content_and_activities")

    response = await health_client.get(
        "/api/v1/health",
        headers={"X-Request-Id": "req-health-1"},
    )

    assert response.status_code == 200
    assert response.headers["X-Request-Id"] == "req-health-1"
    assert response.json() == {
        "status": "ready",
        "mode": "memory",
        "schemaHead": "0003_content_and_activities",
        "catalog": {
            "contentAvailable": False,
            "activityAvailable": True,
        },
        "serverTimeEpochMillis": response.json()["serverTimeEpochMillis"],
    }
    assert response.json()["serverTimeEpochMillis"] > 0
    lowered = response.text.lower()
    assert "databaseurl" not in lowered
    assert "postgresql+" not in lowered
    assert "token" not in lowered
    assert "account" not in lowered


async def test_online_health_openapi_is_public_and_has_stable_operation_id(
    health_client,
):
    document = (await health_client.get("/openapi.json")).json()
    operation = document["paths"]["/api/v1/health"]["get"]

    assert operation["operationId"] == "getOnlineHealth"
    assert operation["security"] == []

