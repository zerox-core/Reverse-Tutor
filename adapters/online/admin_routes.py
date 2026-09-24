from __future__ import annotations

import hmac
import os
from datetime import UTC, datetime
from typing import Annotated

from fastapi import APIRouter, Depends, Header, Path, Query, status
from sqlalchemy.exc import IntegrityError

from online_db.activity_store import (
    ActivityDefinition,
    ActivityNotFound,
    ActivityRecord,
    ActivityStateTransitionError,
    ActivityTaskDefinition,
)

from .admin_models import (
    AdminActivity,
    AdminActivityCreateRequest,
    AdminActivityListResponse,
    AdminActivityTask,
)
from .admin_service import AdminActivityServiceUnavailable, admin_activity_service
from .errors import OnlineApiError

router = APIRouter(prefix="/api/admin/v1", tags=["Admin"])

AdminSlugPath = Annotated[str, Path(alias="slug", min_length=1, max_length=80)]

_ADMIN_TOKEN_ENV = "ONLINE_ADMIN_TOKEN"


def require_admin(
    authorization: Annotated[str | None, Header(alias="Authorization")] = None,
) -> None:
    token = os.getenv(_ADMIN_TOKEN_ENV, "").strip()
    if not token:
        raise OnlineApiError(
            503,
            "admin_not_configured",
            "Admin API is not configured (ONLINE_ADMIN_TOKEN missing)",
            retryable=True,
        )
    provided = ""
    if authorization and authorization.startswith("Bearer "):
        provided = authorization[len("Bearer ") :].strip()
    if not provided or not hmac.compare_digest(
        provided.encode("utf-8"), token.encode("utf-8")
    ):
        raise OnlineApiError(401, "admin_unauthorized", "Invalid admin token")


def _store():
    try:
        return admin_activity_service.activity_store
    except AdminActivityServiceUnavailable:
        raise OnlineApiError(
            503,
            "admin_unavailable",
            "Admin activity store is not configured",
            retryable=True,
        )


def _now() -> datetime:
    return datetime.now(UTC)


def _to_epoch_millis(value: datetime) -> int:
    return int(value.timestamp() * 1000)


def _activity_payload(record: ActivityRecord) -> AdminActivity:
    return AdminActivity(
        id=str(record.id),
        slug=record.slug,
        title=record.title,
        description=record.description,
        revision=record.revision,
        rule_version=record.rule_version,
        total_days=record.total_days,
        starts_at_epoch_millis=_to_epoch_millis(record.starts_at),
        ends_at_epoch_millis=_to_epoch_millis(record.ends_at),
        requires_online_confirmation=record.requires_online_confirmation,
        allows_deferred_progress=record.allows_deferred_progress,
        state=record.state,
        session_template_id=record.session_template_id,
        public_feedback_summary=record.public_feedback_summary,
        tasks=[
            AdminActivityTask(
                day_number=task.day_number,
                title=task.title,
                task_markdown=task.task_markdown,
                stage_goal=task.stage_goal,
            )
            for task in record.tasks
        ],
    )


@router.post(
    "/activities",
    response_model=AdminActivity,
    status_code=status.HTTP_201_CREATED,
    dependencies=[Depends(require_admin)],
    operation_id="adminCreateActivity",
)
def create_activity(request: AdminActivityCreateRequest) -> AdminActivity:
    definition = ActivityDefinition(
        slug=request.slug,
        title=request.title,
        description=request.description,
        revision=1,
        rule_version=1,
        total_days=request.total_days,
        starts_at=datetime.fromtimestamp(
            request.starts_at_epoch_millis / 1000, tz=UTC
        ),
        ends_at=datetime.fromtimestamp(request.ends_at_epoch_millis / 1000, tz=UTC),
        requires_online_confirmation=request.requires_online_confirmation,
        allows_deferred_progress=request.allows_deferred_progress,
        state="scheduled",
        session_template_id=request.session_template_id,
        public_feedback_summary=request.public_feedback_summary,
        tasks=tuple(
            ActivityTaskDefinition(
                day_number=task.day_number,
                title=task.title,
                task_markdown=task.task_markdown,
                stage_goal=task.stage_goal,
            )
            for task in request.tasks
        ),
    )
    try:
        record = _store().create_activity(definition, _now())
    except IntegrityError:
        raise OnlineApiError(
            409, "activity_slug_conflict", "Activity slug already exists"
        )
    return _activity_payload(record)


@router.get(
    "/activities",
    response_model=AdminActivityListResponse,
    dependencies=[Depends(require_admin)],
    operation_id="adminListActivities",
)
def list_activities(
    cursor: str | None = None,
    limit: Annotated[int, Query(ge=1, le=50)] = 20,
) -> AdminActivityListResponse:
    try:
        offset = max(0, int(cursor or "0"))
    except ValueError:
        raise OnlineApiError(400, "invalid_cursor", "Cursor must be a numeric offset")
    records = _store().list_activities(
        states=("scheduled", "active", "closed", "offline"),
        limit=limit,
        offset=offset,
    )
    next_cursor = str(offset + limit) if len(records) == limit else None
    return AdminActivityListResponse(
        items=[_activity_payload(record) for record in records],
        next_cursor=next_cursor,
        updated_at_epoch_millis=_to_epoch_millis(_now()),
    )


@router.get(
    "/activities/{slug}",
    response_model=AdminActivity,
    dependencies=[Depends(require_admin)],
    operation_id="adminGetActivity",
)
def get_activity(slug: AdminSlugPath) -> AdminActivity:
    record = _store().get_activity(slug)
    if record is None:
        raise OnlineApiError(404, "activity_not_found", "Activity not found")
    return _activity_payload(record)


def _transition(slug: str, target: str) -> AdminActivity:
    try:
        record = _store().set_activity_state(slug, target, _now())
    except ActivityNotFound:
        raise OnlineApiError(404, "activity_not_found", "Activity not found")
    except ActivityStateTransitionError as exc:
        raise OnlineApiError(
            409,
            "invalid_state_transition",
            str(exc),
            details={"currentState": exc.current, "targetState": exc.target},
        )
    return _activity_payload(record)


@router.post(
    "/activities/{slug}/publish",
    response_model=AdminActivity,
    dependencies=[Depends(require_admin)],
    operation_id="adminPublishActivity",
)
def publish_activity(slug: AdminSlugPath) -> AdminActivity:
    return _transition(slug, "active")


@router.post(
    "/activities/{slug}/close",
    response_model=AdminActivity,
    dependencies=[Depends(require_admin)],
    operation_id="adminCloseActivity",
)
def close_activity(slug: AdminSlugPath) -> AdminActivity:
    return _transition(slug, "closed")


@router.post(
    "/activities/{slug}/offline",
    response_model=AdminActivity,
    dependencies=[Depends(require_admin)],
    operation_id="adminOfflineActivity",
)
def offline_activity(slug: AdminSlugPath) -> AdminActivity:
    return _transition(slug, "offline")
