"""Stage-based progress persistence for challenge activities.

Implements the data layer of docs/specs/stage-progress-model.md:
``ActivityStage`` definitions per activity, ``ActivityEvidenceEvent``
per participation, and an ``ActivityProgressState`` snapshot. Promotion
= largest completed stage prefix (skip-ahead evidence is bookkept via
``path_tag`` but never promotes on its own). The legacy
``activity_participations.progress`` / ``revision`` columns are
dual-written from completed stages and never regress.

Evidence validity goes through the ``ForgettingCurve`` seam
(``online_db/forgetting_curve.py``); until the backend curve lands the
default ``NoopForgettingCurve`` keeps every evidence event valid.
"""
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Mapping
from uuid import UUID, uuid4

from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session, sessionmaker

from online_db.activity_store import (
    ActivityNotFound,
    ActivityParticipationNotFound,
)
from online_db.forgetting_curve import (
    ForgettingCurve,
    NoopForgettingCurve,
)
from online_db.models import (
    Activity,
    ActivityEvidenceEvent,
    ActivityParticipation,
    ActivityProgressEvent,
    ActivityProgressState,
    ActivityStage,
    EVIDENCE_EVENT_KINDS,
    STAGE_EVIDENCE_LEVELS,
)


@dataclass(frozen=True)
class StageDefinition:
    stage_index: int
    name: str
    capability: str
    evidence_keys: tuple[str, ...] = ()
    task_day_numbers: tuple[int, ...] = ()
    evidence_level: str = "evidenced"
    evidence_refs: tuple[str, ...] = ()


@dataclass(frozen=True)
class ActivityStageRecord:
    id: UUID
    stage_index: int
    name: str
    capability: str
    evidence_keys: tuple[str, ...]
    task_day_numbers: tuple[int, ...]
    created_at: datetime
    updated_at: datetime
    evidence_level: str = "evidenced"
    evidence_refs: tuple[str, ...] = ()


@dataclass(frozen=True)
class StageProgressItem:
    stage_index: int
    name: str
    required_keys: tuple[str, ...]
    satisfied_keys: tuple[str, ...]
    complete: bool


@dataclass(frozen=True)
class ActivityStageProgressRecord:
    activity_id: UUID
    activity_slug: str
    account_id: UUID
    stage_done: int
    current_stage_index: int
    stages: tuple[StageProgressItem, ...]
    progress: int
    revision: int
    updated_at: datetime


@dataclass(frozen=True)
class ActivityEvidenceEventRecord:
    stage_index: int
    evidence_key: str
    kind: str
    artifact_ref: str
    path_tag: str
    idempotency_key: str
    created_at: datetime


class StageStoreError(RuntimeError):
    pass


class ActivityStagesNotDefined(StageStoreError):
    pass


class StageIndexNotFound(StageStoreError):
    def __init__(self, stage_index: int, activity_slug: str) -> None:
        super().__init__(
            f"Stage {stage_index} is not defined for activity '{activity_slug}'"
        )
        self.stage_index = stage_index
        self.activity_slug = activity_slug


class StageEvidenceConflict(StageStoreError):
    pass


class SqlAlchemyStageStore:
    def __init__(
        self,
        session_factory: sessionmaker[Session],
        *,
        forgetting_curve: ForgettingCurve | None = None,
    ) -> None:
        self._session_factory = session_factory
        self._forgetting_curve: ForgettingCurve = (
            forgetting_curve if forgetting_curve is not None else NoopForgettingCurve()
        )

    def close(self) -> None:
        bind = self._session_factory.kw.get("bind")
        if bind is not None:
            bind.dispose()

    def define_activity_stages(
        self,
        activity_slug: str,
        stages: tuple[StageDefinition, ...],
        now: datetime,
    ) -> tuple[ActivityStageRecord, ...]:
        """Create or redefine the stage ladder of an activity.

        Existing rows are updated in place (stage ids and their evidence
        events survive); stages no longer present are deleted. Redefinition
        is an operator action — recorded evidence keeps its rows and is
        re-evaluated against the new definitions on the next recompute.
        """
        now = _utc_input(now)
        with self._session_factory() as database, database.begin():
            activity = self._require_activity(database, activity_slug)
            _validate_stage_definitions(stages, activity.total_days)
            existing = database.scalars(
                select(ActivityStage).where(ActivityStage.activity_id == activity.id)
            ).all()
            by_index = {row.stage_index: row for row in existing}
            defined = {definition.stage_index for definition in stages}
            for definition in stages:
                row = by_index.get(definition.stage_index)
                if row is None:
                    database.add(
                        ActivityStage(
                            id=uuid4(),
                            activity_id=activity.id,
                            stage_index=definition.stage_index,
                            name=definition.name,
                            capability=definition.capability,
                            evidence_keys=list(definition.evidence_keys),
                            task_day_numbers=list(definition.task_day_numbers),
                            evidence_level=definition.evidence_level,
                            evidence_refs=list(definition.evidence_refs),
                            created_at=now,
                            updated_at=now,
                        )
                    )
                else:
                    row.name = definition.name
                    row.capability = definition.capability
                    row.evidence_keys = list(definition.evidence_keys)
                    row.task_day_numbers = list(definition.task_day_numbers)
                    row.evidence_level = definition.evidence_level
                    row.evidence_refs = list(definition.evidence_refs)
                    row.updated_at = now
            for index, row in by_index.items():
                if index not in defined:
                    database.delete(row)
            database.flush()
            return self._stage_records(database, activity.id)

    def list_activity_stages(
        self, activity_slug: str
    ) -> tuple[ActivityStageRecord, ...]:
        with self._session_factory() as database:
            activity = self._require_activity(database, activity_slug, lock=False)
            records = self._stage_records(database, activity.id)
            if not records:
                raise ActivityStagesNotDefined(activity_slug)
            return records

    def record_evidence(
        self,
        activity_slug: str,
        account_id: UUID,
        *,
        stage_index: int,
        evidence_key: str,
        kind: str,
        artifact_ref: str,
        path_tag: str = "in_order",
        idempotency_key: str,
        now: datetime,
    ) -> ActivityStageProgressRecord:
        now = _utc_input(now)
        if kind not in EVIDENCE_EVENT_KINDS:
            raise ValueError(f"kind must be one of {EVIDENCE_EVENT_KINDS}")
        if not evidence_key or not evidence_key.strip():
            raise ValueError("evidence_key must be a non-empty string")
        if artifact_ref is None or not artifact_ref.strip():
            raise ValueError("artifact_ref must be a non-empty string")
        if not path_tag or not path_tag.strip():
            raise ValueError("path_tag must be a non-empty string")
        if not idempotency_key or not idempotency_key.strip():
            raise ValueError("idempotency_key must be a non-empty string")
        for attempt in range(2):
            try:
                return self._record_evidence_once(
                    activity_slug,
                    account_id,
                    stage_index=stage_index,
                    evidence_key=evidence_key,
                    kind=kind,
                    artifact_ref=artifact_ref,
                    path_tag=path_tag,
                    idempotency_key=idempotency_key,
                    now=now,
                )
            except IntegrityError:
                if attempt == 1:
                    raise
        raise AssertionError("unreachable")

    def _record_evidence_once(
        self,
        activity_slug: str,
        account_id: UUID,
        *,
        stage_index: int,
        evidence_key: str,
        kind: str,
        artifact_ref: str,
        path_tag: str,
        idempotency_key: str,
        now: datetime,
    ) -> ActivityStageProgressRecord:
        with self._session_factory() as database, database.begin():
            activity = self._require_activity(database, activity_slug)
            stages = self._stage_rows(database, activity.id)
            if not stages:
                raise ActivityStagesNotDefined(activity_slug)
            stage = next(
                (row for row in stages if row.stage_index == stage_index), None
            )
            if stage is None:
                raise StageIndexNotFound(stage_index, activity_slug)
            if evidence_key not in stage.evidence_keys:
                raise ValueError(
                    f"evidence_key {evidence_key!r} is not an acceptance"
                    f" criterion of stage {stage_index}"
                )
            participation = database.scalar(
                select(ActivityParticipation)
                .where(
                    ActivityParticipation.activity_id == activity.id,
                    ActivityParticipation.account_id == account_id,
                )
                .with_for_update()
            )
            if participation is None:
                raise ActivityParticipationNotFound(str(account_id))
            if participation.state == "left":
                raise ActivityParticipationNotFound(
                    "Participation is no longer active"
                )

            existing = database.scalar(
                select(ActivityEvidenceEvent).where(
                    ActivityEvidenceEvent.participation_id == participation.id,
                    ActivityEvidenceEvent.stage_id == stage.id,
                    ActivityEvidenceEvent.evidence_key == evidence_key,
                )
            )
            if existing is not None:
                if (
                    existing.idempotency_key == idempotency_key
                    or (
                        existing.kind == kind
                        and existing.artifact_ref == artifact_ref
                        and existing.path_tag == path_tag
                    )
                ):
                    return self._snapshot(
                        database, activity, participation, stages, now
                    )
                raise StageEvidenceConflict(
                    f"Evidence {evidence_key!r} for stage {stage_index} was"
                    " already recorded with different content"
                )

            prior_stage_done = self._prefix_done(database, participation, stages, now)
            effective_path_tag = path_tag
            if path_tag == "in_order" and stage_index > prior_stage_done + 1:
                effective_path_tag = "skip_ahead"

            database.add(
                ActivityEvidenceEvent(
                    id=uuid4(),
                    participation_id=participation.id,
                    stage_id=stage.id,
                    evidence_key=evidence_key,
                    kind=kind,
                    artifact_ref=artifact_ref,
                    path_tag=effective_path_tag,
                    idempotency_key=idempotency_key,
                    created_at=now,
                )
            )
            database.flush()
            return self._recompute(
                database, activity, participation, stages, now, idempotency_key
            )

    def get_stage_progress(
        self,
        activity_slug: str,
        account_id: UUID,
        *,
        now: datetime | None = None,
    ) -> ActivityStageProgressRecord:
        resolved_now = (
            _utc_input(now) if now is not None else datetime.now(timezone.utc)
        )
        with self._session_factory() as database:
            activity = self._require_activity(database, activity_slug, lock=False)
            stages = self._stage_rows(database, activity.id)
            if not stages:
                raise ActivityStagesNotDefined(activity_slug)
            participation = database.scalar(
                select(ActivityParticipation).where(
                    ActivityParticipation.activity_id == activity.id,
                    ActivityParticipation.account_id == account_id,
                )
            )
            if participation is None:
                raise ActivityParticipationNotFound(str(account_id))
            return self._snapshot(database, activity, participation, stages, resolved_now)

    def list_evidence_events(
        self,
        activity_slug: str,
        account_id: UUID,
        *,
        stage_index: int | None = None,
    ) -> tuple[ActivityEvidenceEventRecord, ...]:
        with self._session_factory() as database:
            activity = self._require_activity(database, activity_slug, lock=False)
            stages = self._stage_rows(database, activity.id)
            if not stages:
                raise ActivityStagesNotDefined(activity_slug)
            stage_by_id = {row.id: row for row in stages}
            stage_row = None
            if stage_index is not None:
                stage_row = next(
                    (row for row in stages if row.stage_index == stage_index), None
                )
                if stage_row is None:
                    raise StageIndexNotFound(stage_index, activity_slug)
            participation = database.scalar(
                select(ActivityParticipation).where(
                    ActivityParticipation.activity_id == activity.id,
                    ActivityParticipation.account_id == account_id,
                )
            )
            if participation is None or participation.state == "left":
                raise ActivityParticipationNotFound(str(account_id))
            statement = (
                select(ActivityEvidenceEvent)
                .where(ActivityEvidenceEvent.participation_id == participation.id)
                .order_by(
                    ActivityEvidenceEvent.created_at.asc(),
                    ActivityEvidenceEvent.evidence_key.asc(),
                )
            )
            if stage_row is not None:
                statement = statement.where(
                    ActivityEvidenceEvent.stage_id == stage_row.id
                )
            rows = database.scalars(statement).all()
            return tuple(
                ActivityEvidenceEventRecord(
                    stage_index=stage_by_id[row.stage_id].stage_index,
                    evidence_key=row.evidence_key,
                    kind=row.kind,
                    artifact_ref=row.artifact_ref,
                    path_tag=row.path_tag,
                    idempotency_key=row.idempotency_key,
                    created_at=_as_utc(row.created_at),
                )
                for row in rows
            )

    def _recompute(
        self,
        database: Session,
        activity: Activity,
        participation: ActivityParticipation,
        stages: list[ActivityStage],
        now: datetime,
        evidence_idempotency_key: str,
    ) -> ActivityStageProgressRecord:
        satisfied = self._satisfied_keys(database, participation, now)
        stage_done = self._prefix_done_from_satisfied(stages, satisfied)
        current_stage_index = min(stage_done + 1, stages[-1].stage_index)
        derived_days = sum(
            len(stage.task_day_numbers) for stage in stages[:stage_done]
        )

        state_row = database.get(ActivityProgressState, participation.id)
        if state_row is None:
            database.add(
                ActivityProgressState(
                    participation_id=participation.id,
                    activity_id=activity.id,
                    current_stage_index=current_stage_index,
                    stage_done=stage_done,
                    updated_at=now,
                )
            )
        else:
            state_row.current_stage_index = current_stage_index
            state_row.stage_done = stage_done
            state_row.updated_at = now

        if derived_days > participation.progress:
            request_revision = participation.revision
            participation.progress = derived_days
            participation.revision += 1
            participation.state = (
                "completed"
                if participation.progress >= activity.total_days
                else "joined"
            )
            participation.completed_at = (
                now if participation.state == "completed" else None
            )
            participation.updated_at = now
            database.add(
                ActivityProgressEvent(
                    id=uuid4(),
                    participation_id=participation.id,
                    operation="progress",
                    idempotency_key=_dual_write_key(evidence_idempotency_key),
                    request_revision=request_revision,
                    request_progress=derived_days,
                    state=participation.state,
                    progress=participation.progress,
                    revision=participation.revision,
                    created_at=now,
                )
            )
        database.flush()
        return self._snapshot(database, activity, participation, stages, now)

    def _satisfied_keys(
        self,
        database: Session,
        participation: ActivityParticipation,
        now: datetime,
    ) -> Mapping[int, set[str]]:
        rows = database.execute(
            select(ActivityEvidenceEvent, ActivityStage)
            .join(ActivityStage, ActivityStage.id == ActivityEvidenceEvent.stage_id)
            .where(ActivityEvidenceEvent.participation_id == participation.id)
        ).all()
        satisfied: dict[int, set[str]] = {}
        for event, stage in rows:
            if not self._forgetting_curve.evidence_valid(
                stage_index=stage.stage_index,
                evidence_key=event.evidence_key,
                kind=event.kind,
                recorded_at=_as_utc(event.created_at),
                now=now,
            ):
                continue
            satisfied.setdefault(stage.stage_index, set()).add(event.evidence_key)
        return satisfied

    def _prefix_done(
        self,
        database: Session,
        participation: ActivityParticipation,
        stages: list[ActivityStage],
        now: datetime,
    ) -> int:
        satisfied = self._satisfied_keys(database, participation, now)
        return self._prefix_done_from_satisfied(stages, satisfied)

    @staticmethod
    def _prefix_done_from_satisfied(
        stages: list[ActivityStage], satisfied: Mapping[int, set[str]]
    ) -> int:
        stage_done = 0
        for stage in stages:
            if set(stage.evidence_keys) <= satisfied.get(stage.stage_index, set()):
                stage_done = stage.stage_index
            else:
                break
        return stage_done

    def _snapshot(
        self,
        database: Session,
        activity: Activity,
        participation: ActivityParticipation,
        stages: list[ActivityStage],
        now: datetime,
    ) -> ActivityStageProgressRecord:
        satisfied = self._satisfied_keys(database, participation, now)
        stage_done = self._prefix_done_from_satisfied(stages, satisfied)
        items = []
        for stage in stages:
            satisfied_keys = satisfied.get(stage.stage_index, set())
            items.append(
                StageProgressItem(
                    stage_index=stage.stage_index,
                    name=stage.name,
                    required_keys=tuple(stage.evidence_keys),
                    satisfied_keys=tuple(
                        key for key in stage.evidence_keys if key in satisfied_keys
                    ),
                    complete=set(stage.evidence_keys) <= satisfied_keys,
                )
            )
        return ActivityStageProgressRecord(
            activity_id=activity.id,
            activity_slug=activity.slug,
            account_id=participation.account_id,
            stage_done=stage_done,
            current_stage_index=min(stage_done + 1, stages[-1].stage_index),
            stages=tuple(items),
            progress=participation.progress,
            revision=participation.revision,
            updated_at=_as_utc(participation.updated_at),
        )

    @staticmethod
    def _require_activity(
        database: Session, slug: str, *, lock: bool = True
    ) -> Activity:
        statement = select(Activity).where(Activity.slug == slug)
        if lock:
            statement = statement.with_for_update()
        row = database.scalar(statement)
        if row is None:
            raise ActivityNotFound(slug)
        return row

    @staticmethod
    def _stage_rows(database: Session, activity_id: UUID) -> list[ActivityStage]:
        return list(
            database.scalars(
                select(ActivityStage)
                .where(ActivityStage.activity_id == activity_id)
                .order_by(ActivityStage.stage_index.asc())
            ).all()
        )

    @classmethod
    def _stage_records(
        cls, database: Session, activity_id: UUID
    ) -> tuple[ActivityStageRecord, ...]:
        rows = cls._stage_rows(database, activity_id)
        return tuple(
            ActivityStageRecord(
                id=row.id,
                stage_index=row.stage_index,
                name=row.name,
                capability=row.capability,
                evidence_keys=tuple(row.evidence_keys),
                task_day_numbers=tuple(row.task_day_numbers),
                evidence_level=row.evidence_level,
                evidence_refs=tuple(row.evidence_refs or []),
                created_at=_as_utc(row.created_at),
                updated_at=_as_utc(row.updated_at),
            )
            for row in rows
        )


def _dual_write_key(evidence_idempotency_key: str) -> str:
    return f"stage-evidence:{evidence_idempotency_key}"[:255]


def _validate_stage_definitions(
    stages: tuple[StageDefinition, ...], total_days: int
) -> None:
    if not stages:
        raise ValueError("At least one stage must be defined")
    indices = [stage.stage_index for stage in stages]
    if sorted(indices) != list(range(1, len(stages) + 1)):
        raise ValueError("stage indices must be contiguous starting at 1")
    seen_days: set[int] = set()
    for stage in stages:
        if not stage.name or not stage.name.strip():
            raise ValueError("stage name must be a non-empty string")
        if not stage.capability or not stage.capability.strip():
            raise ValueError("stage capability must be a non-empty string")
        if not stage.evidence_keys:
            raise ValueError(
                f"stage {stage.stage_index} needs at least one evidence key"
            )
        if not stage.task_day_numbers:
            raise ValueError(
                f"stage {stage.stage_index} needs at least one task day number"
            )
        if stage.evidence_level not in STAGE_EVIDENCE_LEVELS:
            raise ValueError(
                f"stage {stage.stage_index} evidence_level must be one of"
                f" {STAGE_EVIDENCE_LEVELS}"
            )
        for ref in stage.evidence_refs:
            if not isinstance(ref, str) or not ref.strip():
                raise ValueError("evidence_refs entries must be non-empty strings")
        if len(set(stage.evidence_keys)) != len(stage.evidence_keys):
            raise ValueError(f"stage {stage.stage_index} has duplicate evidence keys")
        for key in stage.evidence_keys:
            if not key or not key.strip():
                raise ValueError("evidence keys must be non-empty strings")
        for day in stage.task_day_numbers:
            if not isinstance(day, int) or isinstance(day, bool) or day < 1:
                raise ValueError("task day numbers must be positive integers")
            if day in seen_days:
                raise ValueError(f"duplicate task day number across stages: {day}")
            if day > total_days:
                raise ValueError(
                    f"task day {day} exceeds activity total_days {total_days}"
                )
            seen_days.add(day)


def _as_utc(value: datetime) -> datetime:
    if value is None:
        return value
    if value.tzinfo is None or value.utcoffset() is None:
        return value.replace(tzinfo=timezone.utc)
    return value.astimezone(timezone.utc)


def _utc_input(value: datetime) -> datetime:
    if value.tzinfo is None or value.utcoffset() is None:
        raise ValueError("datetime values must be timezone-aware")
    return value.astimezone(timezone.utc)
