"""教学偏好画像与细路径记账（spec §11.5 的持久层）。

规则（朱曦策手绘六环节模型的环节⑤）：
- 某知识点的**确认档泛化为下一知识点的起步档**（``current_level`` ← confirmed_level）；
- 起步档过轻（秒过 + 主动加码「吃不饱」）才升档（``lighten_starting_level``，
  向 direct 方向移动一步），避免来回震荡；
- 细路径记账 {知识点, 起始档, 确认档, 尝试序列, 探针记录} 不上面板，
  只做画像与审计的数据源。

画像按 (account, activity) 维度持久化：画像在活动内跨知识点、
跨阶段生效；活动间不互串（学科教学偏好可能完全不同）。
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
    ActivityKnowledgePath,
    ActivityParticipation,
    ActivityStage,
    ActivityTeachingProfile,
)
from online_db.models.probe import TEACHING_LEVELS
from online_db.stage_store import ActivityStagesNotDefined, StageIndexNotFound


@dataclass(frozen=True)
class TeachingProfileRecord:
    account_id: UUID
    activity_id: UUID
    current_level: str
    sample_count: int
    last_adjust_reason: str | None
    updated_at: datetime


@dataclass(frozen=True)
class KnowledgePathRecord:
    stage_index: int
    stage_name: str
    knowledge_point: str
    start_level: str
    confirmed_level: str | None
    attempt_sequence: tuple[str, ...]
    probe_records: tuple[dict, ...]
    created_at: datetime
    updated_at: datetime


class TeachingProfileStoreError(RuntimeError):
    pass


class SqlAlchemyTeachingProfileStore:
    def __init__(self, session_factory: sessionmaker[Session]) -> None:
        self._session_factory = session_factory

    def close(self) -> None:
        bind = self._session_factory.kw.get("bind")
        if bind is not None:
            bind.dispose()

    def get_profile(
        self, activity_slug: str, account_id: UUID, *, now: datetime | None = None
    ) -> TeachingProfileRecord | None:
        """读取画像；还没有画像时返回 None。"""
        resolved_now = (
            _utc_input(now) if now is not None else datetime.now(timezone.utc)
        )
        del resolved_now  # 读取不落库
        with self._session_factory() as database:
            activity = self._require_activity(database, activity_slug, lock=False)
            row = database.scalar(
                select(ActivityTeachingProfile).where(
                    ActivityTeachingProfile.account_id == account_id,
                    ActivityTeachingProfile.activity_id == activity.id,
                )
            )
            if row is None:
                return None
            return _profile_record(row)

    def get_starting_level(self, activity_slug: str, account_id: UUID) -> str:
        """下一个知识点的起步档（画像泛化规则）；无画像时默认 direct。"""
        profile = self.get_profile(activity_slug, account_id)
        if profile is None:
            return TEACHING_LEVELS[0]
        return profile.current_level

    def record_knowledge_path(
        self,
        activity_slug: str,
        account_id: UUID,
        *,
        stage_index: int,
        knowledge_point: str,
        start_level: str,
        confirmed_level: str | None = None,
        attempts: tuple[str, ...] = (),
        probe_records: tuple[dict, ...] = (),
        now: datetime,
    ) -> KnowledgePathRecord:
        """记账一个知识点的小循环结果；确认档同时泛化画像起步档。

        - 新知识点 → 新建行；同一 (阶段, 知识点) 再记账 → 追加尝试序列 /
          探针记录并更新确认档（尝试序列只增不减）；
        - ``confirmed_level`` 首次落档时画像 ``current_level`` 置为确认档
          （泛化规则），``sample_count`` 只在首次确认时 +1。
        """
        resolved_now = _utc_input(now)
        if not knowledge_point or not knowledge_point.strip():
            raise ValueError("knowledge_point must be a non-empty string")
        if start_level not in TEACHING_LEVELS:
            raise ValueError(f"start_level must be one of {TEACHING_LEVELS}")
        if confirmed_level is not None and confirmed_level not in TEACHING_LEVELS:
            raise ValueError(
                f"confirmed_level must be one of {TEACHING_LEVELS} or None"
            )
        attempts = tuple(attempts)
        for level in attempts:
            if level not in TEACHING_LEVELS:
                raise ValueError(f"attempt levels must be one of {TEACHING_LEVELS}")
        probe_records = tuple(probe_records)
        for record in probe_records:
            if not isinstance(record, dict):
                raise ValueError("probe_records must be a list of JSON objects")
        if (
            confirmed_level is not None
            and attempts
            and attempts[-1] != confirmed_level
        ):
            raise ValueError(
                "attempt sequence must end with confirmed_level when confirming"
            )
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
            participation = self._require_participation(
                database, activity.id, account_id
            )

            row = database.scalar(
                select(ActivityKnowledgePath).where(
                    ActivityKnowledgePath.participation_id == participation.id,
                    ActivityKnowledgePath.stage_id == stage.id,
                    ActivityKnowledgePath.knowledge_point == knowledge_point,
                )
            )
            first_confirmation = False
            if row is None:
                row = ActivityKnowledgePath(
                    id=uuid4(),
                    participation_id=participation.id,
                    stage_id=stage.id,
                    knowledge_point=knowledge_point,
                    start_level=start_level,
                    confirmed_level=confirmed_level,
                    attempt_sequence=list(attempts),
                    probe_records=[dict(record) for record in probe_records],
                    created_at=resolved_now,
                    updated_at=resolved_now,
                )
                database.add(row)
                first_confirmation = confirmed_level is not None
            else:
                # 尝试序列只追加：调用侧传本轮增量
                merged_attempts = list(row.attempt_sequence or []) + list(attempts)
                row.attempt_sequence = merged_attempts
                merged_probes = list(row.probe_records or []) + [
                    dict(record) for record in probe_records
                ]
                row.probe_records = merged_probes
                if confirmed_level is not None and row.confirmed_level is None:
                    first_confirmation = True
                    row.confirmed_level = confirmed_level
                elif confirmed_level is not None:
                    row.confirmed_level = confirmed_level
                row.updated_at = resolved_now

            if confirmed_level is not None:
                profile = database.scalar(
                    select(ActivityTeachingProfile).where(
                        ActivityTeachingProfile.account_id == account_id,
                        ActivityTeachingProfile.activity_id == activity.id,
                    )
                )
                if profile is None:
                    database.add(
                        ActivityTeachingProfile(
                            id=uuid4(),
                            account_id=account_id,
                            activity_id=activity.id,
                            current_level=confirmed_level,
                            sample_count=1 if first_confirmation else 0,
                            last_adjust_reason=None,
                            created_at=resolved_now,
                            updated_at=resolved_now,
                        )
                    )
                else:
                    # 泛化规则：确认档 → 下一知识点起步档
                    profile.current_level = confirmed_level
                    if first_confirmation:
                        profile.sample_count = (profile.sample_count or 0) + 1
                    profile.updated_at = resolved_now

            database.flush()
            return KnowledgePathRecord(
                stage_index=stage.stage_index,
                stage_name=stage.name,
                knowledge_point=row.knowledge_point,
                start_level=row.start_level,
                confirmed_level=row.confirmed_level,
                attempt_sequence=tuple(row.attempt_sequence or []),
                probe_records=tuple(row.probe_records or []),
                created_at=_as_utc(row.created_at),
                updated_at=_as_utc(row.updated_at),
            )

    def lighten_starting_level(
        self,
        activity_slug: str,
        account_id: UUID,
        *,
        reason: str,
        now: datetime,
    ) -> TeachingProfileRecord:
        """起步档过轻（秒过 + 主动加码「吃不饱」）才升档，向 direct 移一步。

        已在 direct 时不动作（返回当前画像）。``reason`` 落档为
        ``last_adjust_reason``，供 §7 埋点审计防震荡。
        """
        resolved_now = _utc_input(now)
        if not reason or not reason.strip():
            raise ValueError("reason must be a non-empty string")
        with self._session_factory() as database, database.begin():
            activity = self._require_activity(database, activity_slug)
            profile = database.scalar(
                select(ActivityTeachingProfile)
                .where(
                    ActivityTeachingProfile.account_id == account_id,
                    ActivityTeachingProfile.activity_id == activity.id,
                )
                .with_for_update()
            )
            if profile is None:
                profile = ActivityTeachingProfile(
                    id=uuid4(),
                    account_id=account_id,
                    activity_id=activity.id,
                    current_level=TEACHING_LEVELS[0],
                    sample_count=0,
                    last_adjust_reason=reason.strip(),
                    created_at=resolved_now,
                    updated_at=resolved_now,
                )
                database.add(profile)
            else:
                index = TEACHING_LEVELS.index(profile.current_level)
                if index > 0:
                    profile.current_level = TEACHING_LEVELS[index - 1]
                profile.last_adjust_reason = reason.strip()
                profile.updated_at = resolved_now
            database.flush()
            return _profile_record(profile)

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


def _profile_record(row: ActivityTeachingProfile) -> TeachingProfileRecord:
    return TeachingProfileRecord(
        account_id=row.account_id,
        activity_id=row.activity_id,
        current_level=row.current_level,
        sample_count=row.sample_count or 0,
        last_adjust_reason=row.last_adjust_reason,
        updated_at=_as_utc(row.updated_at),
    )


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
