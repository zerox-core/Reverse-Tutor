from __future__ import annotations

from datetime import UTC, datetime
from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Path

from online_db.activity_store import (
    ActivityNotFound,
    ActivityParticipationNotFound,
)
from online_db.stage_store import (
    ActivityStageProgressRecord,
    ActivityStagesNotDefined,
    StageEvidenceConflict,
    StageIndexNotFound,
)
from .auth_models import AuthContext
from .auth_routes import AuthContextDep
from .errors import OnlineApiError
from .stage_progress_models import (
    StageEvidenceRecordRequest,
    StageProgressItemModel,
    StageProgressResponse,
)
from .stage_progress_service import (
    StageProgressServiceUnavailable,
    stage_progress_service,
)

router = APIRouter(prefix="/api/v1", tags=["Activities"])

# Stage progress endpoints are keyed by the activity slug (same identifier
# the admin API uses); the store layer is slug-addressed.
ActivitySlugPath = Annotated[str, Path(alias="activityId", min_length=1, max_length=80)]


def _store():
    try:
        return stage_progress_service.stage_store
    except StageProgressServiceUnavailable:
        raise OnlineApiError(
            503,
            "stage_progress_unavailable",
            "Stage progress store is not configured",
            retryable=True,
        )


def _now() -> datetime:
    return datetime.now(UTC)


def _to_epoch_millis(value: datetime) -> int:
    return int(value.timestamp() * 1000)


def _progress_payload(record: ActivityStageProgressRecord) -> StageProgressResponse:
    return StageProgressResponse(
        activity_slug=record.activity_slug,
        stage_done=record.stage_done,
        current_stage_index=record.current_stage_index,
        stages=[
            StageProgressItemModel(
                stage_index=item.stage_index,
                name=item.name,
                required_keys=list(item.required_keys),
                satisfied_keys=list(item.satisfied_keys),
                complete=item.complete,
            )
            for item in record.stages
        ],
        progress=record.progress,
        revision=record.revision,
        updated_at_epoch_millis=_to_epoch_millis(record.updated_at),
    )


def _require_request_device(device_id: str, context: AuthContext) -> None:
    if device_id != context.device_id:
        raise OnlineApiError(403, "device_mismatch", "Device does not match session")


def _account_uuid(context: AuthContext) -> UUID:
    return UUID(str(context.account_id))


def _lookup_error(exc: Exception) -> OnlineApiError:
    if isinstance(exc, ActivityNotFound):
        return OnlineApiError(404, "activity_not_found", "Activity not found")
    if isinstance(exc, ActivityStagesNotDefined):
        return OnlineApiError(
            404, "stages_not_defined", "Stages are not defined for this activity"
        )
    if isinstance(exc, ActivityParticipationNotFound):
        return OnlineApiError(
            404,
            "participation_not_found",
            "No active participation for this activity",
        )
    if isinstance(exc, StageIndexNotFound):
        return OnlineApiError(404, "stage_not_found", str(exc))
    raise exc


@router.post(
    "/activities/{activityId}/evidence",
    response_model=StageProgressResponse,
    operation_id="recordActivityStageEvidence",
)
def record_stage_evidence(
    activity_id: ActivitySlugPath,
    request: StageEvidenceRecordRequest,
    context: AuthContextDep,
) -> StageProgressResponse:
    """Session-algorithm evidence write-back (stage-progress spec §3).

    The on-device session algorithm judges a probe against its rubric and
    writes the resulting evidence event back here; the server recomputes the
    completed-stage prefix and dual-writes the legacy participation progress.
    Skip-ahead evidence is bookkept (pathTag=skip_ahead) but never promotes
    on its own. Replays (same idempotencyKey, or identical content) return
    the current snapshot without duplicating the event.
    """
    _require_request_device(request.device_id, context)
    try:
        record = _store().record_evidence(
            activity_id,
            _account_uuid(context),
            stage_index=request.stage_index,
            evidence_key=request.evidence_key,
            kind=request.kind,
            artifact_ref=request.artifact_ref,
            path_tag=request.path_tag,
            idempotency_key=request.idempotency_key,
            now=_now(),
        )
    except StageEvidenceConflict as exc:
        raise OnlineApiError(
            409,
            "stage_evidence_conflict",
            str(exc),
            user_action="resolve_conflict",
        )
    except ValueError as exc:
        raise OnlineApiError(422, "invalid_evidence", str(exc))
    except (
        ActivityNotFound,
        ActivityStagesNotDefined,
        ActivityParticipationNotFound,
        StageIndexNotFound,
    ) as exc:
        raise _lookup_error(exc)
    return _progress_payload(record)


@router.get(
    "/activities/{activityId}/stage-progress",
    response_model=StageProgressResponse,
    operation_id="getActivityStageProgress",
)
def get_stage_progress(
    activity_id: ActivitySlugPath,
    context: AuthContextDep,
) -> StageProgressResponse:
    """Current stage-progress snapshot for the authenticated participant."""
    try:
        record = _store().get_stage_progress(activity_id, _account_uuid(context))
    except (
        ActivityNotFound,
        ActivityStagesNotDefined,
        ActivityParticipationNotFound,
    ) as exc:
        raise _lookup_error(exc)
    return _progress_payload(record)
