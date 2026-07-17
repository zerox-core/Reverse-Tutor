from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Header, Path, Query, Response, status

from .auth_models import AuthContext
from .auth_routes import AuthContextDep
from .content_activity_models import (
    Activity,
    ActivityListResponse,
    ActivityParticipation,
    ContentDetail,
    ContentFeedResponse,
    LeaderboardResponse,
)
from .content_activity_ports import ActivityPortConflict
from .content_activity_service import ContentNotFound, ContentOffline
from .errors import OnlineApiError
from .models import (
    ActivityProgressRequest,
    OnlineWriteIdentity,
    SyncPullRequest,
    SyncPushRequest,
    WeeklyInsightRequest,
)
from .service import online_service

router = APIRouter(prefix="/api/v1")
RequestIdHeader = Annotated[str | None, Header(alias="X-Request-Id", max_length=128)]
ActivityIdPath = Annotated[str, Path(alias="activityId", min_length=1)]


@router.get(
    "/content/feed",
    response_model=ContentFeedResponse,
    tags=["Content"],
    operation_id="getContentFeed",
    openapi_extra={"security": []},
)
def content_feed(
    response: Response,
    cursor: str | None = None,
    limit: Annotated[int, Query(ge=1, le=50)] = 20,
    types: str | None = None,
    if_none_match: Annotated[
        str | None, Header(alias="If-None-Match")
    ] = None,
):
    content_types = frozenset(
        value.strip() for value in (types or "").split(",") if value.strip()
    )
    result = online_service.content_feed(
        cursor=cursor,
        limit=limit,
        content_types=content_types,
    )
    if if_none_match == result.etag:
        return Response(
            status_code=status.HTTP_304_NOT_MODIFIED,
            headers={"ETag": result.etag},
        )
    response.headers["ETag"] = result.etag
    return result.body


@router.get(
    "/content/{slug}",
    response_model=ContentDetail,
    tags=["Content"],
    operation_id="getContentDetail",
    openapi_extra={"security": []},
)
def content_detail(slug: str, response: Response) -> ContentDetail:
    try:
        result = online_service.content_detail(slug)
    except ContentNotFound:
        raise OnlineApiError(404, "content_not_found", "Content not found")
    except ContentOffline:
        raise OnlineApiError(410, "content_offline", "Content is offline")
    response.headers["ETag"] = result.etag
    return result.body


@router.get(
    "/activities",
    response_model=ActivityListResponse,
    tags=["Activities"],
    operation_id="listActivities",
    openapi_extra={"security": []},
)
def list_activities(
    cursor: str | None = None,
    limit: Annotated[int, Query(ge=1, le=50)] = 20,
) -> ActivityListResponse:
    return online_service.list_activities(cursor=cursor, limit=limit)


@router.get(
    "/activities/{activityId}",
    response_model=Activity,
    tags=["Activities"],
    operation_id="getActivity",
    openapi_extra={"security": []},
)
def get_activity(activity_id: ActivityIdPath) -> Activity:
    activity = online_service.get_activity(activity_id)
    if activity is None:
        raise OnlineApiError(404, "activity_not_found", "Activity not found")
    return activity


@router.post(
    "/activities/{activityId}/join",
    response_model=ActivityParticipation,
    tags=["Activities"],
    operation_id="joinActivity",
)
def join_activity(
    activity_id: ActivityIdPath,
    request: OnlineWriteIdentity,
    context: AuthContextDep,
    request_id: RequestIdHeader = None,
) -> ActivityParticipation:
    del request_id
    _require_request_device(request.device_id, context)
    try:
        result = online_service.join_activity(
            activity_id,
            str(context.account_id),
            request,
        )
    except ActivityPortConflict as exc:
        raise _activity_conflict(exc)
    if result is None:
        raise OnlineApiError(404, "activity_not_found", "Activity not found")
    return result


@router.post(
    "/activities/{activityId}/progress",
    response_model=ActivityParticipation,
    tags=["Activities"],
    operation_id="updateActivityProgress",
)
def update_activity_progress(
    activity_id: ActivityIdPath,
    request: ActivityProgressRequest,
    context: AuthContextDep,
    request_id: RequestIdHeader = None,
) -> ActivityParticipation:
    del request_id
    _require_request_device(request.device_id, context)
    try:
        result = online_service.update_activity_progress(
            activity_id, str(context.account_id), request
        )
    except ActivityPortConflict as exc:
        raise _activity_conflict(exc)
    if result is None:
        raise OnlineApiError(404, "activity_not_found", "Activity not found")
    return result


@router.delete(
    "/activities/{activityId}/participation",
    response_model=ActivityParticipation,
    tags=["Activities"],
    operation_id="leaveActivity",
)
def leave_activity(
    activity_id: ActivityIdPath,
    request: OnlineWriteIdentity,
    context: AuthContextDep,
    request_id: RequestIdHeader = None,
) -> ActivityParticipation:
    del request_id
    _require_request_device(request.device_id, context)
    try:
        result = online_service.leave_activity(
            activity_id,
            str(context.account_id),
            request,
        )
    except ActivityPortConflict as exc:
        raise _activity_conflict(exc)
    if result is None:
        raise OnlineApiError(404, "activity_not_found", "Activity not found")
    return result


@router.get(
    "/activities/{activityId}/leaderboard",
    response_model=LeaderboardResponse,
    tags=["Activities"],
    operation_id="getActivityLeaderboard",
    openapi_extra={"security": []},
)
def activity_leaderboard(
    activity_id: ActivityIdPath,
    cursor: str | None = None,
    limit: Annotated[int, Query(ge=1, le=100)] = 50,
) -> LeaderboardResponse:
    result = online_service.activity_leaderboard(
        activity_id,
        cursor=cursor,
        limit=limit,
    )
    if result is None:
        raise OnlineApiError(404, "activity_not_found", "Activity not found")
    return result


@router.post("/sync/push")
def push_sync(request: SyncPushRequest, context: AuthContextDep) -> dict:
    _require_request_device(request.device_id, context)
    return online_service.push_sync(str(context.account_id), request)


@router.post("/sync/pull")
def pull_sync(request: SyncPullRequest, context: AuthContextDep) -> dict:
    _require_request_device(request.device_id, context)
    return online_service.pull_sync(str(context.account_id), request)


@router.post("/insights/weekly")
def weekly_insight(request: WeeklyInsightRequest, context: AuthContextDep) -> dict:
    _require_request_device(request.device_id, context)
    return online_service.weekly_insight(str(context.account_id), request)


@router.get("/app/releases/latest")
def latest_release() -> dict:
    return online_service.latest_release()


def _require_request_device(device_id: str, context: AuthContext) -> None:
    if device_id != context.device_id:
        raise OnlineApiError(403, "device_mismatch", "Device does not match session")


def _activity_conflict(exc: ActivityPortConflict) -> OnlineApiError:
    return OnlineApiError(
        409,
        exc.code,
        exc.message,
        user_action="resolve_conflict",
        details=exc.details,
    )
