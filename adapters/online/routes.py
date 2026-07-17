from __future__ import annotations

from fastapi import APIRouter

from .auth_models import AuthContext
from .auth_routes import AuthContextDep
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


@router.get("/activities")
def list_activities() -> dict:
    return online_service.list_activities()


@router.get("/activities/{activity_id}")
def get_activity(activity_id: str) -> dict:
    activity = online_service.get_activity(activity_id)
    if activity is None:
        raise OnlineApiError(404, "activity_not_found", "Activity not found")
    return activity


@router.post("/activities/{activity_id}/join")
def join_activity(
    activity_id: str,
    request: OnlineWriteIdentity,
    context: AuthContextDep,
) -> dict:
    _require_request_device(request.device_id, context)
    result = online_service.join_activity(activity_id, str(context.account_id), request)
    if result is None:
        raise OnlineApiError(404, "activity_not_found", "Activity not found")
    return result


@router.post("/activities/{activity_id}/progress")
def update_activity_progress(
    activity_id: str,
    request: ActivityProgressRequest,
    context: AuthContextDep,
) -> dict:
    _require_request_device(request.device_id, context)
    result = online_service.update_activity_progress(
        activity_id, str(context.account_id), request
    )
    if result is None:
        raise OnlineApiError(404, "activity_not_found", "Activity not found")
    return result


@router.delete("/activities/{activity_id}/participation")
def leave_activity(
    activity_id: str,
    request: OnlineWriteIdentity,
    context: AuthContextDep,
) -> dict:
    _require_request_device(request.device_id, context)
    result = online_service.leave_activity(activity_id, str(context.account_id), request)
    if result is None:
        raise OnlineApiError(404, "activity_not_found", "Activity not found")
    return result


@router.get("/activities/{activity_id}/leaderboard")
def activity_leaderboard(activity_id: str) -> dict:
    result = online_service.activity_leaderboard(activity_id)
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
