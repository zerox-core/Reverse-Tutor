from __future__ import annotations

from datetime import datetime, timedelta, timezone
from uuid import uuid4

import pytest
from sqlalchemy import URL, func, select

import online_db.models  # noqa: F401
from online_db.activity_store import (
    ActivityDefinition,
    ActivityParticipationNotFound,
    SqlAlchemyActivityStore,
)
from online_db.base import OnlineBase
from online_db.challenge01_seed import seed_challenge01
from online_db.challenge01_stages import (
    CHALLENGE01_STAGES,
    seed_challenge01_stages,
)
from online_db.challenge01_tasks import CHALLENGE01_SLUG, CHALLENGE01_TOTAL_DAYS
from online_db.models import (
    ActivityEvidenceEvent,
    ActivityParticipation,
    ActivityProgressEvent,
    ActivityProgressState,
    AnonymousAccount,
)
from online_db.session import build_online_session_factory
from online_db.stage_store import (
    ActivityStagesNotDefined,
    StageDefinition,
    StageEvidenceConflict,
    StageIndexNotFound,
    SqlAlchemyStageStore,
)


NOW = datetime(2026, 7, 17, 8, 0, tzinfo=timezone.utc)


@pytest.fixture
def sqlite_session_factory(tmp_path):
    url = URL.create("sqlite+pysqlite", database=str(tmp_path / "online.sqlite3"))
    factory = build_online_session_factory(url.render_as_string(hide_password=False))
    OnlineBase.metadata.create_all(factory.kw["bind"])
    yield factory
    factory.kw["bind"].dispose()


SIMPLE_STAGES: tuple[StageDefinition, ...] = (
    StageDefinition(
        stage_index=1,
        name="S1",
        capability="First capability.",
        evidence_keys=("explain", "verify"),
        task_day_numbers=(1, 2),
    ),
    StageDefinition(
        stage_index=2,
        name="S2",
        capability="Second capability.",
        evidence_keys=("explain", "verify"),
        task_day_numbers=(3, 4, 5),
    ),
)


def _create_account(sqlite_session_factory):
    account_id = uuid4()
    with sqlite_session_factory() as database, database.begin():
        database.add(
            AnonymousAccount(
                id=account_id,
                status="active",
                created_at=NOW,
                last_seen_at=NOW,
            )
        )
    return account_id


def _create_activity(sqlite_session_factory, *, slug="staged-activity", total_days=5):
    store = SqlAlchemyActivityStore(sqlite_session_factory)
    store.create_activity(
        ActivityDefinition(
            slug=slug,
            title="Staged Activity",
            description="Stage progress data layer.",
            revision=1,
            rule_version=1,
            total_days=total_days,
            starts_at=NOW,
            ends_at=NOW + timedelta(days=total_days),
            requires_online_confirmation=False,
            allows_deferred_progress=True,
        ),
        NOW,
    )


def _join(sqlite_session_factory, slug, account_id):
    store = SqlAlchemyActivityStore(sqlite_session_factory)
    return store.join_activity(
        slug,
        account_id,
        expected_activity_revision=1,
        idempotency_key=f"join-{account_id}",
        now=NOW,
    )


def _record(sqlite_session_factory, account_id, *, stage_index, evidence_key, key, **overrides):
    arguments = {
        "stage_index": stage_index,
        "evidence_key": evidence_key,
        "kind": "probe_recite",
        "artifact_ref": f"artifact-{key}",
        "idempotency_key": key,
        "now": NOW + timedelta(minutes=1),
    }
    arguments.update(overrides)
    store = SqlAlchemyStageStore(sqlite_session_factory)
    return store.record_evidence("staged-activity", account_id, **arguments)


def test_stage_tables_use_named_constraints_and_no_secret_columns():
    for table_name in (
        "activity_stages",
        "activity_evidence_events",
        "activity_progress_states",
    ):
        table = OnlineBase.metadata.tables[table_name]
        assert all(constraint.name for constraint in table.constraints)
        assert all(index.name for index in table.indexes)
        assert all(len(constraint.name) <= 63 for constraint in table.constraints)
        assert all(len(index.name) <= 63 for index in table.indexes)
        columns = set(table.c.keys())
        assert not {
            column
            for column in columns
            if "secret" in column.lower() or "token" in column.lower()
        }
    stages = OnlineBase.metadata.tables["activity_stages"]
    events = OnlineBase.metadata.tables["activity_evidence_events"]
    states = OnlineBase.metadata.tables["activity_progress_states"]
    assert stages.primary_key.name == "pk_activity_stages"
    assert events.primary_key.name == "pk_activity_evidence_events"
    assert states.primary_key.name == "pk_activity_progress_states"


def test_define_stages_validates_contiguity_keys_and_days(sqlite_session_factory):
    _create_activity(sqlite_session_factory)
    store = SqlAlchemyStageStore(sqlite_session_factory)

    with pytest.raises(ValueError, match="contiguous"):
        store.define_activity_stages(
            "staged-activity",
            (
                StageDefinition(
                    stage_index=2, name="S2", capability="cap", evidence_keys=("k",), task_day_numbers=(1,)
                ),
            ),
            NOW,
        )
    with pytest.raises(ValueError, match="at least one evidence key"):
        store.define_activity_stages(
            "staged-activity",
            (StageDefinition(stage_index=1, name="S1", capability="cap", evidence_keys=(), task_day_numbers=(1,)),),
            NOW,
        )
    with pytest.raises(ValueError, match="duplicate evidence keys"):
        store.define_activity_stages(
            "staged-activity",
            (
                StageDefinition(
                    stage_index=1,
                    name="S1",
                    capability="cap",
                    evidence_keys=("k", "k"),
                    task_day_numbers=(1,),
                ),
            ),
            NOW,
        )
    with pytest.raises(ValueError, match="exceeds activity total_days"):
        store.define_activity_stages(
            "staged-activity",
            (StageDefinition(stage_index=1, name="S1", capability="cap", evidence_keys=("k",), task_day_numbers=(6,)),),
            NOW,
        )


def test_define_stages_upsert_redefinition_preserves_ids_and_evidence(
    sqlite_session_factory,
):
    account_id = _create_account(sqlite_session_factory)
    _create_activity(sqlite_session_factory)
    _join(sqlite_session_factory, "staged-activity", account_id)
    store = SqlAlchemyStageStore(sqlite_session_factory)

    store.define_activity_stages("staged-activity", SIMPLE_STAGES, NOW)
    _record(
        sqlite_session_factory,
        account_id,
        stage_index=1,
        evidence_key="explain",
        key="ev-1",
    )
    original_ids = [record.id for record in store.list_activity_stages("staged-activity")]

    redefined = (
        StageDefinition(1, "S1 改名", "Updated capability.", ("explain", "verify"), (1, 2)),
        StageDefinition(2, "S2", "Second capability.", ("explain", "verify"), (3, 4, 5)),
    )
    store.define_activity_stages("staged-activity", redefined, NOW)

    records = store.list_activity_stages("staged-activity")
    assert [record.id for record in records] == original_ids
    assert records[0].name == "S1 改名"
    with sqlite_session_factory() as database:
        event_count = database.scalar(select(func.count(ActivityEvidenceEvent.id)))
    assert event_count == 1


def test_record_evidence_rejects_unknown_kind_key_and_stage(sqlite_session_factory):
    account_id = _create_account(sqlite_session_factory)
    _create_activity(sqlite_session_factory)
    _join(sqlite_session_factory, "staged-activity", account_id)
    store = SqlAlchemyStageStore(sqlite_session_factory)
    store.define_activity_stages("staged-activity", SIMPLE_STAGES, NOW)

    with pytest.raises(ValueError, match="kind must be one of"):
        store.record_evidence(
            "staged-activity",
            account_id,
            stage_index=1,
            evidence_key="explain",
            kind="guess",
            artifact_ref="artifact-x",
            idempotency_key="ev-x",
            now=NOW,
        )
    with pytest.raises(ValueError, match="not an acceptance"):
        store.record_evidence(
            "staged-activity",
            account_id,
            stage_index=1,
            evidence_key="unknown-key",
            kind="probe_recite",
            artifact_ref="artifact-x",
            idempotency_key="ev-x",
            now=NOW,
        )
    with pytest.raises(StageIndexNotFound):
        store.record_evidence(
            "staged-activity",
            account_id,
            stage_index=9,
            evidence_key="explain",
            kind="probe_recite",
            artifact_ref="artifact-x",
            idempotency_key="ev-x",
            now=NOW,
        )
    with pytest.raises(ValueError, match="timezone-aware"):
        store.record_evidence(
            "staged-activity",
            account_id,
            stage_index=1,
            evidence_key="explain",
            kind="probe_recite",
            artifact_ref="artifact-x",
            idempotency_key="ev-x",
            now=NOW.replace(tzinfo=None),
        )


def test_promotion_and_dual_write(sqlite_session_factory):
    account_id = _create_account(sqlite_session_factory)
    _create_activity(sqlite_session_factory)
    joined = _join(sqlite_session_factory, "staged-activity", account_id)
    store = SqlAlchemyStageStore(sqlite_session_factory)
    store.define_activity_stages("staged-activity", SIMPLE_STAGES, NOW)

    partial = _record(
        sqlite_session_factory, account_id, stage_index=1, evidence_key="explain", key="ev-1"
    )
    assert partial.stage_done == 0
    assert partial.current_stage_index == 1
    assert partial.progress == 0
    assert partial.revision == joined.revision
    assert partial.stages[0].satisfied_keys == ("explain",)

    complete = _record(
        sqlite_session_factory, account_id, stage_index=1, evidence_key="verify", key="ev-2"
    )
    assert complete.stage_done == 1
    assert complete.current_stage_index == 2
    assert complete.progress == 2
    assert complete.revision == joined.revision + 1
    assert complete.stages[0].complete is True
    assert complete.stages[1].complete is False

    with sqlite_session_factory() as database:
        dual_write_events = database.scalars(
            select(ActivityProgressEvent).where(ActivityProgressEvent.operation == "progress")
        ).all()
        state_row = database.get(ActivityProgressState, joined.id)
    assert len(dual_write_events) == 1
    assert dual_write_events[0].idempotency_key == "stage-evidence:ev-2"
    assert dual_write_events[0].request_progress == 2
    assert state_row is not None
    assert state_row.stage_done == 1
    assert state_row.current_stage_index == 2


def test_skip_ahead_bookkeeping_then_backfill_promotion(sqlite_session_factory):
    account_id = _create_account(sqlite_session_factory)
    _create_activity(sqlite_session_factory)
    joined = _join(sqlite_session_factory, "staged-activity", account_id)
    store = SqlAlchemyStageStore(sqlite_session_factory)
    store.define_activity_stages("staged-activity", SIMPLE_STAGES, NOW)

    _record(sqlite_session_factory, account_id, stage_index=2, evidence_key="explain", key="skip-1")
    _record(sqlite_session_factory, account_id, stage_index=2, evidence_key="verify", key="skip-2")

    with sqlite_session_factory() as database:
        events = database.scalars(select(ActivityEvidenceEvent)).all()
    assert all(event.path_tag == "skip_ahead" for event in events)

    skipped = store.get_stage_progress("staged-activity", account_id, now=NOW)
    assert skipped.stage_done == 0
    assert skipped.progress == 0

    _record(sqlite_session_factory, account_id, stage_index=1, evidence_key="explain", key="back-1")
    backfilled = _record(
        sqlite_session_factory, account_id, stage_index=1, evidence_key="verify", key="back-2"
    )
    assert backfilled.stage_done == 2
    assert backfilled.current_stage_index == 2
    assert backfilled.progress == 5
    assert backfilled.revision == joined.revision + 1

    participation = SqlAlchemyActivityStore(sqlite_session_factory).get_participation(
        "staged-activity", account_id
    )
    assert participation is not None
    assert participation.state == "completed"
    with sqlite_session_factory() as database:
        completed_at = database.scalar(select(ActivityParticipation.completed_at))
    assert completed_at is not None


def test_record_evidence_replays_identical_requests(sqlite_session_factory):
    account_id = _create_account(sqlite_session_factory)
    _create_activity(sqlite_session_factory)
    _join(sqlite_session_factory, "staged-activity", account_id)
    store = SqlAlchemyStageStore(sqlite_session_factory)
    store.define_activity_stages("staged-activity", SIMPLE_STAGES, NOW)

    first = _record(
        sqlite_session_factory, account_id, stage_index=1, evidence_key="explain", key="ev-1"
    )
    replayed = _record(
        sqlite_session_factory, account_id, stage_index=1, evidence_key="explain", key="ev-1"
    )
    duplicate_key = _record(
        sqlite_session_factory,
        account_id,
        stage_index=1,
        evidence_key="explain",
        key="ev-1b",
        artifact_ref="artifact-ev-1",
    )

    with sqlite_session_factory() as database:
        event_count = database.scalar(select(func.count(ActivityEvidenceEvent.id)))
    assert event_count == 1
    assert replayed.stage_done == first.stage_done
    assert duplicate_key.progress == first.progress


def test_record_evidence_conflicts_on_same_key_different_content(sqlite_session_factory):
    account_id = _create_account(sqlite_session_factory)
    _create_activity(sqlite_session_factory)
    _join(sqlite_session_factory, "staged-activity", account_id)
    store = SqlAlchemyStageStore(sqlite_session_factory)
    store.define_activity_stages("staged-activity", SIMPLE_STAGES, NOW)

    _record(sqlite_session_factory, account_id, stage_index=1, evidence_key="explain", key="ev-1")
    with pytest.raises(StageEvidenceConflict):
        _record(
            sqlite_session_factory,
            account_id,
            stage_index=1,
            evidence_key="explain",
            key="ev-2",
            artifact_ref="different-artifact",
        )

    with sqlite_session_factory() as database:
        event_count = database.scalar(select(func.count(ActivityEvidenceEvent.id)))
    assert event_count == 1


def test_activities_without_stages_or_participation_raise(sqlite_session_factory):
    account_id = _create_account(sqlite_session_factory)
    _create_activity(sqlite_session_factory)
    store = SqlAlchemyStageStore(sqlite_session_factory)

    with pytest.raises(ActivityStagesNotDefined):
        store.record_evidence(
            "staged-activity",
            account_id,
            stage_index=1,
            evidence_key="explain",
            kind="probe_recite",
            artifact_ref="artifact-x",
            idempotency_key="ev-x",
            now=NOW,
        )
    with pytest.raises(ActivityStagesNotDefined):
        store.get_stage_progress("staged-activity", account_id, now=NOW)

    store.define_activity_stages("staged-activity", SIMPLE_STAGES, NOW)
    with pytest.raises(ActivityParticipationNotFound):
        store.get_stage_progress("staged-activity", account_id, now=NOW)
    with pytest.raises(ActivityParticipationNotFound):
        store.record_evidence(
            "staged-activity",
            account_id,
            stage_index=1,
            evidence_key="explain",
            kind="probe_recite",
            artifact_ref="artifact-x",
            idempotency_key="ev-x",
            now=NOW,
        )


def test_left_participation_rejects_evidence(sqlite_session_factory):
    account_id = _create_account(sqlite_session_factory)
    _create_activity(sqlite_session_factory)
    _join(sqlite_session_factory, "staged-activity", account_id)
    activity_store = SqlAlchemyActivityStore(sqlite_session_factory)
    activity_store.leave_activity(
        "staged-activity",
        account_id,
        expected_revision=1,
        idempotency_key="leave-1",
        now=NOW,
    )
    store = SqlAlchemyStageStore(sqlite_session_factory)
    store.define_activity_stages("staged-activity", SIMPLE_STAGES, NOW)

    with pytest.raises(ActivityParticipationNotFound):
        store.record_evidence(
            "staged-activity",
            account_id,
            stage_index=1,
            evidence_key="explain",
            kind="probe_recite",
            artifact_ref="artifact-x",
            idempotency_key="ev-x",
            now=NOW,
        )


def test_custom_forgetting_curve_blocks_promotion(sqlite_session_factory):
    class ExpiredCurve:
        def evidence_valid(self, *, stage_index, evidence_key, kind="", recorded_at, now):
            return False

    account_id = _create_account(sqlite_session_factory)
    _create_activity(sqlite_session_factory)
    _join(sqlite_session_factory, "staged-activity", account_id)
    store = SqlAlchemyStageStore(
        sqlite_session_factory, forgetting_curve=ExpiredCurve()
    )
    store.define_activity_stages("staged-activity", SIMPLE_STAGES, NOW)

    result = store.record_evidence(
        "staged-activity",
        account_id,
        stage_index=1,
        evidence_key="explain",
        kind="probe_recite",
        artifact_ref="artifact-ev-1",
        idempotency_key="ev-1",
        now=NOW + timedelta(minutes=1),
    )
    assert result.stage_done == 0
    assert result.progress == 0
    assert result.revision == 1

    with sqlite_session_factory() as database:
        state_count = database.scalar(select(func.count(ActivityProgressState.participation_id)))
        state_row = database.scalar(select(ActivityProgressState))
        progress_events = database.scalar(
            select(func.count(ActivityProgressEvent.id)).where(
                ActivityProgressEvent.operation == "progress"
            )
        )
    assert state_count == 1
    assert state_row is not None
    assert state_row.stage_done == 0
    assert progress_events == 0


def test_dual_write_never_regresses_legacy_progress(sqlite_session_factory):
    account_id = _create_account(sqlite_session_factory)
    _create_activity(sqlite_session_factory)
    joined = _join(sqlite_session_factory, "staged-activity", account_id)
    activity_store = SqlAlchemyActivityStore(sqlite_session_factory)
    legacy = activity_store.update_progress(
        "staged-activity",
        account_id,
        expected_revision=joined.revision,
        progress=5,
        idempotency_key="legacy-1",
        now=NOW,
    )
    assert legacy.progress == 5
    assert legacy.state == "completed"

    store = SqlAlchemyStageStore(sqlite_session_factory)
    store.define_activity_stages("staged-activity", SIMPLE_STAGES, NOW)
    _record(sqlite_session_factory, account_id, stage_index=1, evidence_key="explain", key="ev-1")
    after = _record(
        sqlite_session_factory, account_id, stage_index=1, evidence_key="verify", key="ev-2"
    )

    assert after.stage_done == 1
    assert after.progress == 5
    assert after.revision == legacy.revision

    with sqlite_session_factory() as database:
        state_row = database.get(ActivityProgressState, joined.id)
    assert state_row is not None
    assert state_row.stage_done == 1


def test_challenge01_stage_seed_is_idempotent_and_covers_seventeen_days(
    sqlite_session_factory,
):
    assert seed_challenge01(sqlite_session_factory, NOW) is True
    assert seed_challenge01_stages(sqlite_session_factory, NOW) is True
    assert seed_challenge01_stages(sqlite_session_factory, NOW) is False

    store = SqlAlchemyStageStore(sqlite_session_factory)
    stages = store.list_activity_stages(CHALLENGE01_SLUG)
    assert len(stages) == 6
    assert [stage.stage_index for stage in stages] == [1, 2, 3, 4, 5, 6]
    assert sorted(
        day for stage in stages for day in stage.task_day_numbers
    ) == list(range(1, CHALLENGE01_TOTAL_DAYS + 1))
    assert "final_exam" in stages[-1].evidence_keys
    assert all(stage.evidence_keys for stage in stages)
    assert all(stage.capability for stage in stages)

    days = [day for stage in CHALLENGE01_STAGES for day in stage.task_day_numbers]
    assert sorted(days) == list(range(1, CHALLENGE01_TOTAL_DAYS + 1))
