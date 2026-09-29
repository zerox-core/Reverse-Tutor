"""StageEvidenceForgettingCurve（阶段证据接缝曲线）单测 + 存储接缝集成。

口径（逐事件、无复习链，与 Kotlin R103 常数对齐）：S = 24 × eff² 天；
Δt ≤ 3.0S 证据有效（保护区 + 衰减期），Δt > 3.0S 坠入失效。
eff 取 KIND_EFFECTIVENESS 的按类锚点；未知 kind 回退 delayed_retrieval
锚点（stage-progress-model.md §10）。边界断言统一用 ±0.001 天偏移，
与 tests/test_forgetting_curve.py 的既有风格一致（避开浮点恰等）。
"""
from __future__ import annotations

from datetime import datetime, timedelta, timezone
from uuid import uuid4

import pytest
from sqlalchemy import URL

import online_db.models  # noqa: F401
from online_db.activity_store import ActivityDefinition, SqlAlchemyActivityStore
from online_db.base import OnlineBase
from online_db.forgetting_curve import (
    ABSORB_THRESHOLD,
    EVIDENCE_EFFECTIVENESS,
    INITIAL_STABILITY_DAYS,
    KIND_EFFECTIVENESS,
    NoopForgettingCurve,
    StageEvidenceForgettingCurve,
    initial_stability_days,
)
from online_db.models import AnonymousAccount
from online_db.session import build_online_session_factory
from online_db.stage_store import StageDefinition, SqlAlchemyStageStore

NOW = datetime(2026, 9, 29, 8, 0, tzinfo=timezone.utc)
DAY = timedelta(days=1)


def _valid(curve, *, kind, recorded_at, now):
    return curve.evidence_valid(
        stage_index=1,
        evidence_key="explain",
        kind=kind,
        recorded_at=recorded_at,
        now=now,
    )


def _stability_for_kind(kind: str) -> float:
    return initial_stability_days(KIND_EFFECTIVENESS[kind])


# --- 按类锚点表 ---------------------------------------------------------------


def test_kind_effectiveness_covers_all_db_event_kinds():
    # 与 online_db.models.EVIDENCE_EVENT_KINDS 保持一致（DB CHECK 的 6 种）
    assert set(KIND_EFFECTIVENESS) == {
        "baseline_probe",
        "probe_recite",
        "probe_transfer",
        "probe_error",
        "artifact",
        "manual",
    }


def test_kind_effectiveness_matches_kotlin_anchor_scores():
    assert KIND_EFFECTIVENESS["baseline_probe"] == EVIDENCE_EFFECTIVENESS["explanation"]
    assert KIND_EFFECTIVENESS["probe_recite"] == EVIDENCE_EFFECTIVENESS["retrieval"]
    assert KIND_EFFECTIVENESS["probe_transfer"] == EVIDENCE_EFFECTIVENESS["transfer"]
    assert KIND_EFFECTIVENESS["probe_error"] == EVIDENCE_EFFECTIVENESS["correction"]
    assert KIND_EFFECTIVENESS["artifact"] == EVIDENCE_EFFECTIVENESS["transfer"]
    assert KIND_EFFECTIVENESS["manual"] == EVIDENCE_EFFECTIVENESS["explanation"]


def test_stability_table_matches_kotlin_formula():
    for kind, eff in KIND_EFFECTIVENESS.items():
        assert _stability_for_kind(kind) == pytest.approx(
            INITIAL_STABILITY_DAYS * eff * eff
        )


# --- 边界：Δt ≤ 3.0S 有效，Δt > 3.0S 失效 --------------------------------------


@pytest.mark.parametrize("kind", sorted(KIND_EFFECTIVENESS))
def test_evidence_valid_until_absorb_threshold(kind):
    curve = StageEvidenceForgettingCurve()
    s_days = _stability_for_kind(kind)
    # 保护区内与衰减期内：都算数
    assert _valid(curve, kind=kind, recorded_at=NOW, now=NOW + DAY * s_days) is True
    assert (
        _valid(curve, kind=kind, recorded_at=NOW, now=NOW + DAY * 2.0 * s_days)
        is True
    )
    # 坠入边界内侧（3.0S − ε）：仍算数
    assert (
        _valid(
            curve, kind=kind, recorded_at=NOW, now=NOW + DAY * (3.0 * s_days - 0.001)
        )
        is True
    )
    # 坠入后（3.0S + ε）：失效
    assert (
        _valid(
            curve, kind=kind, recorded_at=NOW, now=NOW + DAY * (3.0 * s_days + 0.001)
        )
        is False
    )


def test_absorb_threshold_constant_is_three():
    assert ABSORB_THRESHOLD == 3.0


def test_unknown_kind_falls_back_to_delayed_retrieval_anchor():
    curve = StageEvidenceForgettingCurve()
    anchor_s = initial_stability_days(EVIDENCE_EFFECTIVENESS["delayed_retrieval"])
    inside = NOW + DAY * (3.0 * anchor_s - 0.001)
    beyond = NOW + DAY * (3.0 * anchor_s + 0.001)
    assert _valid(curve, kind="whatever", recorded_at=NOW, now=inside) is True
    assert _valid(curve, kind="whatever", recorded_at=NOW, now=beyond) is False
    # 空调用方（旧 seam 形态，不传 kind）同样回退锚点
    assert (
        curve.evidence_valid(
            stage_index=1, evidence_key="k1", recorded_at=NOW, now=inside
        )
        is True
    )
    assert (
        curve.evidence_valid(
            stage_index=1, evidence_key="k1", recorded_at=NOW, now=beyond
        )
        is False
    )


def test_future_recorded_at_is_valid():
    curve = StageEvidenceForgettingCurve()
    assert (
        _valid(
            curve,
            kind="probe_recite",
            recorded_at=NOW + DAY,
            now=NOW,
        )
        is True
    )


def test_naive_datetimes_are_treated_as_utc():
    curve = StageEvidenceForgettingCurve()
    s_days = _stability_for_kind("baseline_probe")
    naive_recorded = datetime(2026, 9, 1, 8, 0)
    naive_now = naive_recorded + DAY * (3.0 * s_days + 0.001)
    assert (
        _valid(
            curve, kind="baseline_probe", recorded_at=naive_recorded, now=naive_now
        )
        is False
    )


def test_noop_curve_never_decays():
    curve = NoopForgettingCurve()
    assert (
        _valid(
            curve,
            kind="baseline_probe",
            recorded_at=NOW,
            now=NOW + 3650 * DAY,
        )
        is True
    )


# --- 存储接缝集成：曲线注入后失效证据不再计入 satisfied --------------------------


@pytest.fixture
def sqlite_session_factory(tmp_path):
    url = URL.create("sqlite+pysqlite", database=str(tmp_path / "online.sqlite3"))
    factory = build_online_session_factory(url.render_as_string(hide_password=False))
    OnlineBase.metadata.create_all(factory.kw["bind"])
    yield factory
    factory.kw["bind"].dispose()


ONE_KEY_STAGES: tuple[StageDefinition, ...] = (
    StageDefinition(
        stage_index=1,
        name="S1",
        capability="First capability.",
        evidence_keys=("explain",),
        task_day_numbers=(1, 2),
    ),
)


def _seed_participation(sqlite_session_factory):
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
    activity_store = SqlAlchemyActivityStore(sqlite_session_factory)
    activity_store.create_activity(
        ActivityDefinition(
            slug="curve-activity",
            title="Curve Activity",
            description="Forgetting curve integration.",
            revision=1,
            rule_version=1,
            total_days=2,
            starts_at=NOW,
            ends_at=NOW + timedelta(days=2),
            requires_online_confirmation=False,
            allows_deferred_progress=True,
        ),
        NOW,
    )
    activity_store.join_activity(
        "curve-activity",
        account_id,
        expected_activity_revision=1,
        idempotency_key="join-1",
        now=NOW,
    )
    return account_id


def test_store_with_curve_unsatisfies_absorbed_evidence(sqlite_session_factory):
    account_id = _seed_participation(sqlite_session_factory)
    store = SqlAlchemyStageStore(
        sqlite_session_factory, forgetting_curve=StageEvidenceForgettingCurve()
    )
    store.define_activity_stages("curve-activity", ONE_KEY_STAGES, NOW)
    recorded = store.record_evidence(
        "curve-activity",
        account_id,
        stage_index=1,
        evidence_key="explain",
        kind="probe_recite",
        artifact_ref="artifact-ev-1",
        idempotency_key="ev-1",
        now=NOW,
    )
    assert recorded.stage_done == 1

    s_days = _stability_for_kind("probe_recite")
    # 衰减期内（2.0S）：仍然 satisfied
    decaying = store.get_stage_progress(
        "curve-activity", account_id, now=NOW + DAY * 2.0 * s_days
    )
    assert decaying.stage_done == 1
    # 坠入后（3.0S + ε）：不再 satisfied
    absorbed = store.get_stage_progress(
        "curve-activity", account_id, now=NOW + DAY * (3.0 * s_days + 0.001)
    )
    assert absorbed.stage_done == 0
    assert absorbed.stages[0].satisfied_keys == ()


def test_store_default_noop_keeps_evidence_valid(sqlite_session_factory):
    account_id = _seed_participation(sqlite_session_factory)
    store = SqlAlchemyStageStore(sqlite_session_factory)
    store.define_activity_stages("curve-activity", ONE_KEY_STAGES, NOW)
    store.record_evidence(
        "curve-activity",
        account_id,
        stage_index=1,
        evidence_key="explain",
        kind="baseline_probe",
        artifact_ref="artifact-ev-1",
        idempotency_key="ev-1",
        now=NOW,
    )
    snapshot = store.get_stage_progress(
        "curve-activity", account_id, now=NOW + 3650 * DAY
    )
    assert snapshot.stage_done == 1
