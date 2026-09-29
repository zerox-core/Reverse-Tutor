"""探针计划存档读写（spec §11.3 的存档结构）。

会话创建时生成的整份 ``ProbePlan`` 一次性落档为逐阶段行
（``activity_probe_plans``，唯一键 = participation + stage）；
读取时按阶段重校验，保证读回的一定是能通过 ``validate_stage_plan``
的定稿形态。重存即重定义（upsert），多余阶段行删除。
"""
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from uuid import UUID, uuid4

from sqlalchemy import select
from sqlalchemy.orm import Session, sessionmaker

from online_db.activity_store import (
    ActivityNotFound,
    ActivityParticipationNotFound,
)
from online_db.models import (
    Activity,
    ActivityParticipation,
    ActivityProbePlan,
    ActivityStage,
)
from online_db.probe_plan_prompts import (
    ProbePlan,
    ProbeRubric,
    ProbeStagePlan,
    plan_to_payload,
    stage_plan_to_payload,
    validate_probe_plan,
    validate_stage_plan,
)
from online_db.stage_store import (
    ActivityStageRecord,
    ActivityStagesNotDefined,
    StageIndexNotFound,
)


@dataclass(frozen=True)
class ProbeItemRecord:
    evidence_key: str
    kind: str
    question: str
    rubric: ProbeRubric
    followups: tuple[str, ...]


@dataclass(frozen=True)
class ProbePlanRecord:
    stage_index: int
    stage_id: UUID
    stage_name: str
    capability: str
    entry_question: str
    probes: tuple[ProbeItemRecord, ...]
    model: str
    created_at: datetime
    updated_at: datetime


class ProbePlanStoreError(RuntimeError):
    pass


class ProbePlanNotFound(ProbePlanStoreError):
    pass


class SqlAlchemyProbePlanStore:
    def __init__(self, session_factory: sessionmaker[Session]) -> None:
        self._session_factory = session_factory

    def close(self) -> None:
        bind = self._session_factory.kw.get("bind")
        if bind is not None:
            bind.dispose()

    def save_probe_plan(
        self,
        activity_slug: str,
        account_id: UUID,
        *,
        plan: ProbePlan,
        model: str,
        now: datetime,
    ) -> tuple[ProbePlanRecord, ...]:
        """把整份定稿计划按阶段落档（upsert），返回全部阶段记录。"""
        resolved_now = _utc_input(now)
        if not model or not model.strip():
            raise ValueError("model must be a non-empty string")
        with self._session_factory() as database, database.begin():
            activity = self._require_activity(database, activity_slug)
            stages = self._stage_rows(database, activity.id)
            if not stages:
                raise ActivityStagesNotDefined(activity_slug)
            stage_records = tuple(
                ActivityStageRecord(
                    id=row.id,
                    stage_index=row.stage_index,
                    name=row.name,
                    capability=row.capability,
                    evidence_keys=tuple(row.evidence_keys),
                    task_day_numbers=tuple(row.task_day_numbers),
                    created_at=_as_utc(row.created_at),
                    updated_at=_as_utc(row.updated_at),
                )
                for row in stages
            )
            # 生成时的阶段阶梯可能与当前 DB 不一致：落档前按 DB 定义重校验
            validate_probe_plan({"stages": plan_to_payload(plan)}, stage_records)
            participation = self._require_participation(
                database, activity.id, account_id
            )
            existing = {
                row.stage_id: row
                for row in database.scalars(
                    select(ActivityProbePlan).where(
                        ActivityProbePlan.participation_id == participation.id
                    )
                ).all()
            }
            stage_by_index = {row.stage_index: row for row in stages}
            plan_by_index = {
                stage_plan.stage_index: stage_plan for stage_plan in plan.stages
            }
            for stage_index, stage_plan in plan_by_index.items():
                stage = stage_by_index[stage_index]
                row = existing.pop(stage.id, None)
                if row is None:
                    database.add(
                        ActivityProbePlan(
                            id=uuid4(),
                            participation_id=participation.id,
                            stage_id=stage.id,
                            plan=_stage_payload(stage_plan),
                            model=model.strip(),
                            created_at=resolved_now,
                            updated_at=resolved_now,
                        )
                    )
                else:
                    row.plan = _stage_payload(stage_plan)
                    row.model = model.strip()
                    row.updated_at = resolved_now
            for surplus in existing.values():
                database.delete(surplus)
            database.flush()
            return self._records(
                database, participation.id, stages
            )

    def get_stage_plan(
        self,
        activity_slug: str,
        account_id: UUID,
        *,
        stage_index: int,
        now: datetime | None = None,
    ) -> ProbePlanRecord:
        del now  # 读取不落库；参数保留为调用侧签名一致性
        with self._session_factory() as database:
            activity = self._require_activity(database, activity_slug, lock=False)
            stages = self._stage_rows(database, activity.id)
            stage = next(
                (row for row in stages if row.stage_index == stage_index), None
            )
            if stage is None:
                raise StageIndexNotFound(stage_index, activity_slug)
            participation = database.scalar(
                select(ActivityParticipation).where(
                    ActivityParticipation.activity_id == activity.id,
                    ActivityParticipation.account_id == account_id,
                )
            )
            if participation is None:
                raise ActivityParticipationNotFound(str(account_id))
            records = self._records(database, participation.id, [stage])
            if not records:
                raise ProbePlanNotFound(
                    f"No probe plan stored for stage {stage_index} of"
                    f" '{activity_slug}'"
                )
            return records[0]

    def list_stage_plans(
        self,
        activity_slug: str,
        account_id: UUID,
    ) -> tuple[ProbePlanRecord, ...]:
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
            records = self._records(database, participation.id, stages)
            if not records:
                raise ProbePlanNotFound(
                    f"No probe plan stored for '{activity_slug}'"
                )
            return records

    def _records(
        self,
        database: Session,
        participation_id: UUID,
        stages: list[ActivityStage],
    ) -> tuple[ProbePlanRecord, ...]:
        rows = {
            row.stage_id: row
            for row in database.scalars(
                select(ActivityProbePlan).where(
                    ActivityProbePlan.participation_id == participation_id
                )
            ).all()
        }
        records: list[ProbePlanRecord] = []
        for stage in stages:
            row = rows.get(stage.id)
            if row is None:
                continue
            stage_record = ActivityStageRecord(
                id=stage.id,
                stage_index=stage.stage_index,
                name=stage.name,
                capability=stage.capability,
                evidence_keys=tuple(stage.evidence_keys),
                task_day_numbers=tuple(stage.task_day_numbers),
                created_at=_as_utc(stage.created_at),
                updated_at=_as_utc(stage.updated_at),
            )
            stage_plan = validate_stage_plan(row.plan, stage_record)
            records.append(
                ProbePlanRecord(
                    stage_index=stage.stage_index,
                    stage_id=stage.id,
                    stage_name=stage.name,
                    capability=stage.capability,
                    entry_question=stage_plan.entry_question,
                    probes=tuple(
                        ProbeItemRecord(
                            evidence_key=probe.evidence_key,
                            kind=probe.kind,
                            question=probe.question,
                            rubric=probe.rubric,
                            followups=probe.followups,
                        )
                        for probe in stage_plan.probes
                    ),
                    model=row.model,
                    created_at=_as_utc(row.created_at),
                    updated_at=_as_utc(row.updated_at),
                )
            )
        return tuple(records)

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
    def _require_participation(
        database: Session, activity_id: UUID, account_id: UUID
    ) -> ActivityParticipation:
        participation = database.scalar(
            select(ActivityParticipation)
            .where(
                ActivityParticipation.activity_id == activity_id,
                ActivityParticipation.account_id == account_id,
            )
            .with_for_update()
        )
        if participation is None:
            raise ActivityParticipationNotFound(str(account_id))
        if participation.state == "left":
            raise ActivityParticipationNotFound("Participation is no longer active")
        return participation

    @staticmethod
    def _stage_rows(database: Session, activity_id: UUID) -> list[ActivityStage]:
        return list(
            database.scalars(
                select(ActivityStage)
                .where(ActivityStage.activity_id == activity_id)
                .order_by(ActivityStage.stage_index.asc())
            ).all()
        )


def _stage_payload(stage_plan: ProbeStagePlan) -> dict:
    return stage_plan_to_payload(stage_plan)


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
