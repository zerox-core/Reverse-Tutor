from __future__ import annotations

import hmac
import os
from datetime import UTC, datetime
from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, Header, Path, Query, status
from sqlalchemy.exc import IntegrityError

from llm import chat_json
from online_db.activity_store import (
    ActivityDefinition,
    ActivityNotFound,
    ActivityParticipationNotFound,
    ActivityRecord,
    ActivityStateTransitionError,
    ActivityTaskDefinition,
)
from online_db.probe_plan_prompts import (
    ProbePlan,
    ProbePlanValidationError,
    generate_probe_plan,
    validate_probe_plan,
)
from online_db.probe_plan_store import ProbePlanNotFound

from online_db.stage_generation import (
    MaterialDigest,
    StagePlanValidationError,
    generate_stage_plan,
)
from online_db.stage_store import (
    ActivityStagesNotDefined,
    StageDefinition,
)
from .admin_models import (
    AdminActivity,
    AdminActivityCreateRequest,
    AdminActivityListResponse,
    AdminProbeItem,
    AdminProbePlanConfirmRequest,
    AdminProbePlanGenerateRequest,
    AdminProbePlanGenerateResponse,
    AdminProbePlanResponse,
    AdminProbeRubric,
    AdminProbeStagePlan,
    AdminProbeStagePlanRecord,
    AdminStageDraft,
    AdminStagePlanGenerateRequest,
    AdminStagePlanGenerateResponse,
    AdminStagesConfirmRequest,
    AdminStagesResponse,
    AdminActivityTask,
)
from .admin_service import (
    AdminActivityServiceUnavailable,
    AdminProbePlanServiceUnavailable,
    AdminStageServiceUnavailable,
    admin_activity_service,
    admin_probe_plan_service,
    admin_stage_service,
)
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


def _stage_store():
    try:
        return admin_stage_service.stage_store
    except AdminStageServiceUnavailable:
        raise OnlineApiError(
            503,
            "stage_store_unavailable",
            "Stage store is not configured",
            retryable=True,
        )


def _stage_payload(item) -> AdminStageDraft:
    return AdminStageDraft(
        stage_index=item.stage_index,
        name=item.name,
        capability=item.capability,
        evidence_keys=list(item.evidence_keys),
        task_day_numbers=list(item.task_day_numbers),
        evidence_level=item.evidence_level,
        evidence_refs=list(item.evidence_refs),
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


@router.post(
    "/activities/{slug}/stage-plan/generate",
    response_model=AdminStagePlanGenerateResponse,
    dependencies=[Depends(require_admin)],
    operation_id="adminGenerateStagePlan",
)
async def generate_activity_stage_plan(
    slug: AdminSlugPath, request: AdminStagePlanGenerateRequest
) -> AdminStagePlanGenerateResponse:
    """Generate a material-driven stage draft (no persistence, human review first)."""
    record = _store().get_activity(slug)
    if record is None:
        raise OnlineApiError(404, "activity_not_found", "Activity not found")
    materials = tuple(
        MaterialDigest(
            title=material.title,
            text=material.text,
            ref=material.ref or "",
        )
        for material in request.materials
    )
    temperature = 0.3 if request.temperature is None else request.temperature
    try:
        plan = await generate_stage_plan(
            materials,
            activity_title=record.title,
            activity_description=record.description,
            total_days=record.total_days,
            chat_json=chat_json,
            temperature=temperature,
        )
    except StagePlanValidationError as exc:
        raise OnlineApiError(
            502,
            "stage_plan_invalid",
            f"Generated stage plan failed validation: {exc}",
            retryable=True,
        )
    except Exception as exc:
        raise OnlineApiError(
            502,
            "stage_plan_generation_failed",
            f"Stage plan generation failed: {type(exc).__name__}: {str(exc)[:300]}",
            retryable=True,
        )
    return AdminStagePlanGenerateResponse(
        activity_slug=slug,
        total_days=record.total_days,
        stages=[_stage_payload(draft) for draft in plan.stages],
        inferred_stage_indexes=[
            draft.stage_index
            for draft in plan.stages
            if draft.evidence_level == "inferred"
        ],
        persisted=False,
    )


@router.put(
    "/activities/{slug}/stages",
    response_model=AdminStagesResponse,
    dependencies=[Depends(require_admin)],
    operation_id="adminDefineActivityStages",
)
def define_activity_stages(
    slug: AdminSlugPath, request: AdminStagesConfirmRequest
) -> AdminStagesResponse:
    """Persist the human-confirmed stage ladder (in-place redefine)."""
    definitions = tuple(
        StageDefinition(
            stage_index=stage.stage_index,
            name=stage.name,
            capability=stage.capability,
            evidence_keys=tuple(stage.evidence_keys),
            task_day_numbers=tuple(stage.task_day_numbers),
            evidence_level=stage.evidence_level,
            evidence_refs=tuple(stage.evidence_refs),
        )
        for stage in request.stages
    )
    try:
        records = _stage_store().define_activity_stages(slug, definitions, _now())
    except ActivityNotFound:
        raise OnlineApiError(404, "activity_not_found", "Activity not found")
    except ValueError as exc:
        raise OnlineApiError(422, "invalid_stage_definitions", str(exc))
    return AdminStagesResponse(
        activity_slug=slug,
        stages=[_stage_payload(record) for record in records],
    )


@router.get(
    "/activities/{slug}/stages",
    response_model=AdminStagesResponse,
    dependencies=[Depends(require_admin)],
    operation_id="adminListActivityStages",
)
def list_activity_stages(slug: AdminSlugPath) -> AdminStagesResponse:
    try:
        records = _stage_store().list_activity_stages(slug)
    except ActivityNotFound:
        raise OnlineApiError(404, "activity_not_found", "Activity not found")
    except ActivityStagesNotDefined:
        raise OnlineApiError(
            404, "stages_not_defined", "Stages are not defined for this activity"
        )
    return AdminStagesResponse(
        activity_slug=slug,
        stages=[_stage_payload(record) for record in records],
    )


AccountIdPath = Annotated[UUID, Path(alias="accountId")]


def _probe_plan_store():
    try:
        return admin_probe_plan_service.probe_plan_store
    except AdminProbePlanServiceUnavailable:
        raise OnlineApiError(
            503,
            "probe_plan_store_unavailable",
            "Probe plan store is not configured",
            retryable=True,
        )


def _probe_stage_plan_payload(stage_plan) -> AdminProbeStagePlan:
    return AdminProbeStagePlan(
        stage_index=stage_plan.stage_index,
        entry_question=stage_plan.entry_question,
        probes=[
            AdminProbeItem(
                evidence_key=probe.evidence_key,
                kind=probe.kind,
                question=probe.question,
                rubric=AdminProbeRubric(
                    pass_criteria=probe.rubric.pass_criteria,
                    partial_criteria=probe.rubric.partial_criteria,
                    fail_signals=list(probe.rubric.fail_signals),
                ),
                followups=list(probe.followups),
            )
            for probe in stage_plan.probes
        ],
    )


def _probe_record_payload(record) -> AdminProbeStagePlanRecord:
    base = _probe_stage_plan_payload(record)
    return AdminProbeStagePlanRecord(
        **base.model_dump(),
        stage_name=record.stage_name,
        capability=record.capability,
        model=record.model,
        updated_at_epoch_millis=_to_epoch_millis(record.updated_at),
    )


def _probe_plan_from_request(
    stages_payload: list[AdminProbeStagePlan], stage_records
) -> ProbePlan:
    raw = {
        "stages": [
            {
                "stage_index": stage.stage_index,
                "entry_question": stage.entry_question,
                "probes": [
                    {
                        "evidence_key": probe.evidence_key,
                        "kind": probe.kind,
                        "question": probe.question,
                        "rubric": {
                            "pass": probe.rubric.pass_criteria,
                            "partial": probe.rubric.partial_criteria,
                            "fail_signals": list(probe.rubric.fail_signals),
                        },
                        "followups": list(probe.followups),
                    }
                    for probe in stage.probes
                ],
            }
            for stage in stages_payload
        ]
    }
    return validate_probe_plan(raw, stage_records)


@router.post(
    "/activities/{slug}/participations/{accountId}/probe-plan/generate",
    response_model=AdminProbePlanGenerateResponse,
    dependencies=[Depends(require_admin)],
    operation_id="adminGenerateProbePlan",
)
async def generate_participation_probe_plan(
    slug: AdminSlugPath,
    account_id: AccountIdPath,
    request: AdminProbePlanGenerateRequest,
) -> AdminProbePlanGenerateResponse:
    """Generate a probe-plan draft for a participation (no persistence).

    Probe plan + rubric are finalized at session creation and stored with the
    session strategy (stage-progress spec §11.3): generation runs against the
    current stage definitions and the human review gate (PUT confirm)
    persists the finalized plan.
    """
    record = _store().get_activity(slug)
    if record is None:
        raise OnlineApiError(404, "activity_not_found", "Activity not found")
    try:
        stage_records = _stage_store().list_activity_stages(slug)
    except ActivityStagesNotDefined:
        raise OnlineApiError(
            404, "stages_not_defined", "Stages are not defined for this activity"
        )
    temperature = 0.3 if request.temperature is None else request.temperature
    try:
        plan = await generate_probe_plan(
            stage_records,
            activity_title=record.title,
            activity_description=record.description,
            chat_json=chat_json,
            temperature=temperature,
        )
    except ProbePlanValidationError as exc:
        raise OnlineApiError(
            502,
            "probe_plan_invalid",
            f"Generated probe plan failed validation: {exc}",
            retryable=True,
        )
    except Exception as exc:
        raise OnlineApiError(
            502,
            "probe_plan_generation_failed",
            f"Probe plan generation failed: {type(exc).__name__}: {str(exc)[:300]}",
            retryable=True,
        )
    return AdminProbePlanGenerateResponse(
        activity_slug=slug,
        account_id=str(account_id),
        stages=[_probe_stage_plan_payload(stage_plan) for stage_plan in plan.stages],
        persisted=False,
    )


@router.put(
    "/activities/{slug}/participations/{accountId}/probe-plan",
    response_model=AdminProbePlanResponse,
    dependencies=[Depends(require_admin)],
    operation_id="adminConfirmProbePlan",
)
def confirm_participation_probe_plan(
    slug: AdminSlugPath,
    account_id: AccountIdPath,
    request: AdminProbePlanConfirmRequest,
) -> AdminProbePlanResponse:
    """Persist the human-confirmed probe plan (per-stage upsert)."""
    try:
        stage_records = _stage_store().list_activity_stages(slug)
    except ActivityNotFound:
        raise OnlineApiError(404, "activity_not_found", "Activity not found")
    except ActivityStagesNotDefined:
        raise OnlineApiError(
            404, "stages_not_defined", "Stages are not defined for this activity"
        )
    try:
        plan = _probe_plan_from_request(request.stages, stage_records)
    except ProbePlanValidationError as exc:
        raise OnlineApiError(422, "invalid_probe_plan", str(exc))
    try:
        records = _probe_plan_store().save_probe_plan(
            slug,
            account_id,
            plan=plan,
            model=request.model,
            now=_now(),
        )
    except ActivityNotFound:
        raise OnlineApiError(404, "activity_not_found", "Activity not found")
    except ActivityStagesNotDefined:
        raise OnlineApiError(
            404, "stages_not_defined", "Stages are not defined for this activity"
        )
    except ActivityParticipationNotFound:
        raise OnlineApiError(
            404,
            "participation_not_found",
            "No active participation for this activity",
        )
    except (ProbePlanValidationError, ValueError) as exc:
        raise OnlineApiError(422, "invalid_probe_plan", str(exc))
    return AdminProbePlanResponse(
        activity_slug=slug,
        account_id=str(account_id),
        stages=[_probe_record_payload(record) for record in records],
    )


@router.get(
    "/activities/{slug}/participations/{accountId}/probe-plan",
    response_model=AdminProbePlanResponse,
    dependencies=[Depends(require_admin)],
    operation_id="adminListProbePlan",
)
def list_participation_probe_plan(
    slug: AdminSlugPath, account_id: AccountIdPath
) -> AdminProbePlanResponse:
    """List the stored probe plan of a participation (all stages)."""
    try:
        records = _probe_plan_store().list_stage_plans(slug, account_id)
    except ActivityNotFound:
        raise OnlineApiError(404, "activity_not_found", "Activity not found")
    except ActivityStagesNotDefined:
        raise OnlineApiError(
            404, "stages_not_defined", "Stages are not defined for this activity"
        )
    except ActivityParticipationNotFound:
        raise OnlineApiError(
            404,
            "participation_not_found",
            "No active participation for this activity",
        )
    except ProbePlanNotFound:
        raise OnlineApiError(
            404,
            "probe_plan_not_found",
            "No probe plan stored for this participation",
        )
    return AdminProbePlanResponse(
        activity_slug=slug,
        account_id=str(account_id),
        stages=[_probe_record_payload(record) for record in records],
    )
