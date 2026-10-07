from pathlib import Path

import yaml

import server


CONTRACT_PATH = Path("docs/contracts/openapi-online-v1.yaml")


def _contract() -> dict:
    return yaml.safe_load(CONTRACT_PATH.read_text(encoding="utf-8"))


def test_slice_zero_openapi_contains_auth_and_error_contracts():
    document = _contract()
    paths = document["paths"]

    assert {
        "/auth/anonymous",
        "/auth/refresh",
        "/auth/me",
        "/auth/sessions",
        "/auth/sessions/{sessionId}",
    }.issubset(paths)
    assert document["components"]["schemas"]["ApiError"]["required"] == [
        "code",
        "message",
        "retryable",
        "userAction",
        "requestId",
    ]


def test_canonical_openapi_contains_public_online_health():
    document = _contract()
    operation = document["paths"]["/health"]["get"]

    assert operation["operationId"] == "getOnlineHealth"
    assert operation["security"] == []
    assert operation["responses"]["200"]["content"]["application/json"][
        "schema"
    ] == {"$ref": "#/components/schemas/OnlineHealthResponse"}


def test_protected_request_bodies_do_not_trust_user_id():
    schemas = _contract()["components"]["schemas"]

    for schema_name in (
        "OnlineWriteIdentity",
        "SyncPushRequest",
        "SyncPullRequest",
        "WeeklyInsightRequest",
    ):
        schema = schemas[schema_name]
        assert "userId" not in schema.get("properties", {})
        assert "userId" not in schema.get("required", [])


def test_live_fastapi_openapi_matches_slice_zero_auth_surface():
    document = server.app.openapi()
    paths = document["paths"]

    assert {
        "/api/v1/auth/anonymous",
        "/api/v1/auth/refresh",
        "/api/v1/auth/me",
        "/api/v1/auth/sessions",
        "/api/v1/auth/sessions/{sessionId}",
    }.issubset(paths)
    assert paths["/api/v1/auth/me"]["get"]["security"] == [{"bearerAuth": []}]
    assert "security" not in paths["/api/v1/auth/anonymous"]["post"]

    schemas = document["components"]["schemas"]
    for schema_name in (
        "OnlineWriteIdentity",
        "SyncPushRequest",
        "SyncPullRequest",
        "WeeklyInsightRequest",
    ):
        assert "userId" not in schemas[schema_name].get("properties", {})


def test_live_fastapi_openapi_matches_content_and_activity_surface():
    paths = server.app.openapi()["paths"]
    expected_operations = {
        "/api/v1/content/feed": ("get", "getContentFeed", "ContentFeedResponse"),
        "/api/v1/content/{slug}": ("get", "getContentDetail", "ContentDetail"),
        "/api/v1/activities": ("get", "listActivities", "ActivityListResponse"),
        "/api/v1/activities/{activityId}": ("get", "getActivity", "Activity"),
        "/api/v1/activities/{activityId}/join": (
            "post",
            "joinActivity",
            "ActivityParticipation",
        ),
        "/api/v1/activities/{activityId}/progress": (
            "post",
            "updateActivityProgress",
            "ActivityParticipation",
        ),
        "/api/v1/activities/{activityId}/participation": (
            "delete",
            "leaveActivity",
            "ActivityParticipation",
        ),
        "/api/v1/activities/{activityId}/leaderboard": (
            "get",
            "getActivityLeaderboard",
            "LeaderboardResponse",
        ),
    }

    for path, (method, operation_id, response_schema) in expected_operations.items():
        operation = paths[path][method]
        assert operation["operationId"] == operation_id
        assert operation["responses"]["200"]["content"]["application/json"][
            "schema"
        ] == {"$ref": f"#/components/schemas/{response_schema}"}

    for path in (
        "/api/v1/content/feed",
        "/api/v1/content/{slug}",
        "/api/v1/activities",
        "/api/v1/activities/{activityId}",
        "/api/v1/activities/{activityId}/leaderboard",
    ):
        assert paths[path]["get"]["security"] == []

    for path, method in (
        ("/api/v1/activities/{activityId}/join", "post"),
        ("/api/v1/activities/{activityId}/progress", "post"),
        ("/api/v1/activities/{activityId}/participation", "delete"),
    ):
        assert paths[path][method]["security"] == [{"bearerAuth": []}]
