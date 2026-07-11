from __future__ import annotations

from fastapi import APIRouter, HTTPException

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
        raise HTTPException(status_code=404, detail="Activity not found")
    return activity


@router.post("/activities/{activity_id}/join")
def join_activity(activity_id: str, request: OnlineWriteIdentity) -> dict:
    result = online_service.join_activity(activity_id, request)
    if result is None:
        raise HTTPException(status_code=404, detail="Activity not found")
    return result


@router.post("/activities/{activity_id}/progress")
def update_activity_progress(
    activity_id: str,
    request: ActivityProgressRequest,
) -> dict:
    result = online_service.update_activity_progress(activity_id, request)
    if result is None:
        raise HTTPException(status_code=404, detail="Activity not found")
    return result


@router.get("/activities/{activity_id}/leaderboard")
def activity_leaderboard(activity_id: str) -> dict:
    result = online_service.activity_leaderboard(activity_id)
    if result is None:
        raise HTTPException(status_code=404, detail="Activity not found")
    return result


@router.post("/sync/push")
def push_sync(request: SyncPushRequest) -> dict:
    return online_service.push_sync(request)


@router.post("/sync/pull")
def pull_sync(request: SyncPullRequest) -> dict:
    return online_service.pull_sync(request)


@router.post("/insights/weekly")
def weekly_insight(request: WeeklyInsightRequest) -> dict:
    return online_service.weekly_insight(request)


@router.get("/app/releases/latest")
def latest_release() -> dict:
    return online_service.latest_release()
