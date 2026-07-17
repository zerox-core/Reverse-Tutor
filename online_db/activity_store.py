from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
from uuid import UUID, uuid4

from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session, sessionmaker

from online_db.models import (
    Activity,
    ActivityParticipation,
    ActivityProgressEvent,
    ActivityTask,
)


@dataclass(frozen=True)
class ActivityTaskDefinition:
    day_number: int
    title: str
    task_markdown: str
    stage_goal: str | None = None


@dataclass(frozen=True)
class ActivityDefinition:
    slug: str
    title: str
    description: str
    revision: int
    rule_version: int
    total_days: int
    starts_at: datetime
    ends_at: datetime
    requires_online_confirmation: bool
    allows_deferred_progress: bool
    state: str = "scheduled"
    session_template_id: str | None = None
    public_feedback_summary: str | None = None
    tasks: tuple[ActivityTaskDefinition, ...] = field(default_factory=tuple)


@dataclass(frozen=True)
class ActivityTaskRecord:
    id: UUID
    day_number: int
    title: str
    task_markdown: str
    stage_goal: str | None


@dataclass(frozen=True)
class ActivityRecord:
    id: UUID
    slug: str
    title: str
    description: str
    revision: int
    rule_version: int
    total_days: int
    starts_at: datetime
    ends_at: datetime
    requires_online_confirmation: bool
    allows_deferred_progress: bool
    state: str
    session_template_id: str | None
    public_feedback_summary: str | None
    tasks: tuple[ActivityTaskRecord, ...]


@dataclass(frozen=True)
class ActivityParticipationRecord:
    id: UUID
    activity_id: UUID
    activity_slug: str
    account_id: UUID
    state: str
    progress: int
    revision: int
    idempotency_key: str
    updated_at: datetime

    @property
    def joined(self) -> bool:
        return self.state != "left"


@dataclass(frozen=True)
class ActivityLeaderboardRecord:
    rank: int
    account_id: UUID
    progress: int


class ActivityStoreError(RuntimeError):
    pass


class ActivityNotFound(ActivityStoreError):
    pass


class ActivityParticipationNotFound(ActivityStoreError):
    pass


class ActivityRevisionConflict(ActivityStoreError):
    def __init__(self, expected: int, actual: int) -> None:
        super().__init__(f"Expected revision {expected}, current revision is {actual}")
        self.expected = expected
        self.actual = actual


class ActivityIdempotencyConflict(ActivityStoreError):
    pass


class SqlAlchemyActivityStore:
    def __init__(self, session_factory: sessionmaker[Session]) -> None:
        self._session_factory = session_factory

    def close(self) -> None:
        bind = self._session_factory.kw.get("bind")
        if bind is not None:
            bind.dispose()

    def create_activity(
        self, definition: ActivityDefinition, now: datetime
    ) -> ActivityRecord:
        now = _utc_input(now)
        starts_at = _utc_input(definition.starts_at)
        ends_at = _utc_input(definition.ends_at)
        if any(
            task.day_number > definition.total_days for task in definition.tasks
        ):
            raise ValueError("Task day cannot exceed total_days")
        with self._session_factory() as database, database.begin():
            row = Activity(
                id=uuid4(),
                slug=definition.slug,
                title=definition.title,
                description=definition.description,
                revision=definition.revision,
                rule_version=definition.rule_version,
                total_days=definition.total_days,
                starts_at=starts_at,
                ends_at=ends_at,
                requires_online_confirmation=definition.requires_online_confirmation,
                allows_deferred_progress=definition.allows_deferred_progress,
                state=definition.state,
                session_template_id=definition.session_template_id,
                public_feedback_summary=definition.public_feedback_summary,
                created_at=now,
                updated_at=now,
            )
            database.add(row)
            database.flush()
            for task in definition.tasks:
                database.add(
                    ActivityTask(
                        id=uuid4(),
                        activity_id=row.id,
                        day_number=task.day_number,
                        title=task.title,
                        task_markdown=task.task_markdown,
                        stage_goal=task.stage_goal,
                        created_at=now,
                    )
                )
            database.flush()
            return self._activity_record(database, row)

    def get_activity(self, slug: str) -> ActivityRecord | None:
        with self._session_factory() as database:
            row = database.scalar(select(Activity).where(Activity.slug == slug))
            return self._activity_record(database, row) if row is not None else None

    def list_activities(
        self,
        *,
        states: tuple[str, ...] = ("scheduled", "active", "closed"),
        limit: int = 20,
        offset: int = 0,
    ) -> tuple[ActivityRecord, ...]:
        if not 1 <= limit <= 50:
            raise ValueError("limit must be between 1 and 50")
        if offset < 0:
            raise ValueError("offset must be non-negative")
        statement = (
            select(Activity)
            .where(Activity.state.in_(states))
            .order_by(Activity.starts_at.desc(), Activity.id.asc())
            .offset(offset)
            .limit(limit)
        )
        with self._session_factory() as database:
            return tuple(
                self._activity_record(database, row)
                for row in database.scalars(statement).all()
            )

    def join_activity(
        self,
        activity_slug: str,
        account_id: UUID,
        *,
        expected_activity_revision: int,
        idempotency_key: str,
        now: datetime,
    ) -> ActivityParticipationRecord:
        now = _utc_input(now)
        for attempt in range(2):
            try:
                return self._join_once(
                    activity_slug,
                    account_id,
                    expected_activity_revision,
                    idempotency_key,
                    now,
                )
            except IntegrityError:
                if attempt == 1:
                    raise
        raise AssertionError("unreachable")

    def _join_once(
        self,
        activity_slug: str,
        account_id: UUID,
        expected_activity_revision: int,
        idempotency_key: str,
        now: datetime,
    ) -> ActivityParticipationRecord:
        now = _utc_input(now)
        with self._session_factory() as database, database.begin():
            activity = self._require_activity(database, activity_slug)
            participation = self._find_participation(
                database, activity.id, account_id
            )
            if participation is not None:
                replay = self._replayed(
                    database,
                    participation,
                    activity,
                    "join",
                    idempotency_key,
                    request_revision=expected_activity_revision,
                    request_progress=None,
                )
                if replay is not None:
                    return replay
            if activity.revision != expected_activity_revision:
                raise ActivityRevisionConflict(
                    expected_activity_revision, activity.revision
                )

            if participation is None:
                participation = ActivityParticipation(
                    id=uuid4(),
                    activity_id=activity.id,
                    account_id=account_id,
                    state="joined",
                    progress=0,
                    revision=1,
                    last_idempotency_key=idempotency_key,
                    joined_at=now,
                    updated_at=now,
                )
                database.add(participation)
                database.flush()
            elif participation.state == "left":
                participation.state = "joined"
                participation.revision += 1
                participation.last_idempotency_key = idempotency_key
                participation.joined_at = now
                participation.updated_at = now
                participation.left_at = None
                database.flush()
            self._add_event(
                database,
                participation,
                operation="join",
                idempotency_key=idempotency_key,
                request_revision=expected_activity_revision,
                request_progress=None,
                now=now,
            )
            return self._participation_record(
                participation, activity, idempotency_key
            )

    def update_progress(
        self,
        activity_slug: str,
        account_id: UUID,
        *,
        expected_revision: int,
        progress: int,
        idempotency_key: str,
        now: datetime,
    ) -> ActivityParticipationRecord:
        now = _utc_input(now)
        with self._session_factory() as database, database.begin():
            activity = self._require_activity(database, activity_slug)
            participation = self._require_participation(
                database, activity.id, account_id
            )
            replay = self._replayed(
                database,
                participation,
                activity,
                "progress",
                idempotency_key,
                request_revision=expected_revision,
                request_progress=progress,
            )
            if replay is not None:
                return replay
            self._check_participation_revision(participation, expected_revision)
            if participation.state == "left":
                raise ActivityParticipationNotFound(
                    "Participation is no longer active"
                )

            participation.progress = max(participation.progress, progress)
            participation.revision += 1
            participation.state = (
                "completed"
                if participation.progress >= activity.total_days
                else "joined"
            )
            participation.completed_at = (
                now if participation.state == "completed" else None
            )
            participation.last_idempotency_key = idempotency_key
            participation.updated_at = now
            self._add_event(
                database,
                participation,
                operation="progress",
                idempotency_key=idempotency_key,
                request_revision=expected_revision,
                request_progress=progress,
                now=now,
            )
            return self._participation_record(
                participation, activity, idempotency_key
            )

    def leave_activity(
        self,
        activity_slug: str,
        account_id: UUID,
        *,
        expected_revision: int,
        idempotency_key: str,
        now: datetime,
    ) -> ActivityParticipationRecord:
        now = _utc_input(now)
        with self._session_factory() as database, database.begin():
            activity = self._require_activity(database, activity_slug)
            participation = self._require_participation(
                database, activity.id, account_id
            )
            replay = self._replayed(
                database,
                participation,
                activity,
                "leave",
                idempotency_key,
                request_revision=expected_revision,
                request_progress=None,
            )
            if replay is not None:
                return replay
            self._check_participation_revision(participation, expected_revision)
            if participation.state != "left":
                participation.state = "left"
                participation.revision += 1
                participation.left_at = now
                participation.last_idempotency_key = idempotency_key
                participation.updated_at = now
            self._add_event(
                database,
                participation,
                operation="leave",
                idempotency_key=idempotency_key,
                request_revision=expected_revision,
                request_progress=None,
                now=now,
            )
            return self._participation_record(
                participation, activity, idempotency_key
            )

    def list_participations(
        self, account_id: UUID, *, include_left: bool = False
    ) -> tuple[ActivityParticipationRecord, ...]:
        statement = (
            select(ActivityParticipation, Activity)
            .join(Activity, Activity.id == ActivityParticipation.activity_id)
            .where(ActivityParticipation.account_id == account_id)
            .order_by(ActivityParticipation.updated_at.desc())
        )
        if not include_left:
            statement = statement.where(ActivityParticipation.state != "left")
        with self._session_factory() as database:
            return tuple(
                self._participation_record(
                    participation,
                    activity,
                    participation.last_idempotency_key,
                )
                for participation, activity in database.execute(statement).all()
            )

    def leaderboard(
        self, activity_slug: str, *, limit: int = 50
    ) -> tuple[ActivityLeaderboardRecord, ...]:
        if not 1 <= limit <= 100:
            raise ValueError("limit must be between 1 and 100")
        with self._session_factory() as database:
            activity = self._require_activity(database, activity_slug, lock=False)
            rows = database.scalars(
                select(ActivityParticipation)
                .where(
                    ActivityParticipation.activity_id == activity.id,
                    ActivityParticipation.state != "left",
                )
                .order_by(
                    ActivityParticipation.progress.desc(),
                    ActivityParticipation.account_id.asc(),
                )
                .limit(limit)
            ).all()
            return tuple(
                ActivityLeaderboardRecord(
                    rank=index,
                    account_id=row.account_id,
                    progress=row.progress,
                )
                for index, row in enumerate(rows, start=1)
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
    def _find_participation(
        database: Session, activity_id: UUID, account_id: UUID
    ) -> ActivityParticipation | None:
        return database.scalar(
            select(ActivityParticipation)
            .where(
                ActivityParticipation.activity_id == activity_id,
                ActivityParticipation.account_id == account_id,
            )
            .with_for_update()
        )

    @classmethod
    def _require_participation(
        cls, database: Session, activity_id: UUID, account_id: UUID
    ) -> ActivityParticipation:
        row = cls._find_participation(database, activity_id, account_id)
        if row is None:
            raise ActivityParticipationNotFound(str(account_id))
        return row

    @staticmethod
    def _check_participation_revision(
        participation: ActivityParticipation, expected: int
    ) -> None:
        if participation.revision != expected:
            raise ActivityRevisionConflict(expected, participation.revision)

    @classmethod
    def _replayed(
        cls,
        database: Session,
        participation: ActivityParticipation,
        activity: Activity,
        operation: str,
        idempotency_key: str,
        *,
        request_revision: int,
        request_progress: int | None,
    ) -> ActivityParticipationRecord | None:
        event = database.scalar(
            select(ActivityProgressEvent).where(
                ActivityProgressEvent.participation_id == participation.id,
                ActivityProgressEvent.operation == operation,
                ActivityProgressEvent.idempotency_key == idempotency_key,
            )
        )
        if event is None:
            return None
        if (
            event.request_revision != request_revision
            or event.request_progress != request_progress
        ):
            raise ActivityIdempotencyConflict(
                "Idempotency key was reused with a different request"
            )
        return ActivityParticipationRecord(
            id=participation.id,
            activity_id=activity.id,
            activity_slug=activity.slug,
            account_id=participation.account_id,
            state=event.state,
            progress=event.progress,
            revision=event.revision,
            idempotency_key=event.idempotency_key,
            updated_at=_as_utc(event.created_at),
        )

    @staticmethod
    def _add_event(
        database: Session,
        participation: ActivityParticipation,
        *,
        operation: str,
        idempotency_key: str,
        request_revision: int,
        request_progress: int | None,
        now: datetime,
    ) -> None:
        database.add(
            ActivityProgressEvent(
                id=uuid4(),
                participation_id=participation.id,
                operation=operation,
                idempotency_key=idempotency_key,
                request_revision=request_revision,
                request_progress=request_progress,
                state=participation.state,
                progress=participation.progress,
                revision=participation.revision,
                created_at=now,
            )
        )

    @staticmethod
    def _activity_record(database: Session, row: Activity) -> ActivityRecord:
        tasks = database.scalars(
            select(ActivityTask)
            .where(ActivityTask.activity_id == row.id)
            .order_by(ActivityTask.day_number.asc(), ActivityTask.id.asc())
        ).all()
        return ActivityRecord(
            id=row.id,
            slug=row.slug,
            title=row.title,
            description=row.description,
            revision=row.revision,
            rule_version=row.rule_version,
            total_days=row.total_days,
            starts_at=_as_utc(row.starts_at),
            ends_at=_as_utc(row.ends_at),
            requires_online_confirmation=row.requires_online_confirmation,
            allows_deferred_progress=row.allows_deferred_progress,
            state=row.state,
            session_template_id=row.session_template_id,
            public_feedback_summary=row.public_feedback_summary,
            tasks=tuple(
                ActivityTaskRecord(
                    id=task.id,
                    day_number=task.day_number,
                    title=task.title,
                    task_markdown=task.task_markdown,
                    stage_goal=task.stage_goal,
                )
                for task in tasks
            ),
        )

    @staticmethod
    def _participation_record(
        participation: ActivityParticipation,
        activity: Activity,
        idempotency_key: str,
    ) -> ActivityParticipationRecord:
        return ActivityParticipationRecord(
            id=participation.id,
            activity_id=activity.id,
            activity_slug=activity.slug,
            account_id=participation.account_id,
            state=participation.state,
            progress=participation.progress,
            revision=participation.revision,
            idempotency_key=idempotency_key,
            updated_at=_as_utc(participation.updated_at),
        )


def _as_utc(value: datetime) -> datetime:
    if value.tzinfo is None or value.utcoffset() is None:
        return value.replace(tzinfo=timezone.utc)
    return value.astimezone(timezone.utc)


def _utc_input(value: datetime) -> datetime:
    if value.tzinfo is None or value.utcoffset() is None:
        raise ValueError("datetime values must be timezone-aware")
    return value.astimezone(timezone.utc)
