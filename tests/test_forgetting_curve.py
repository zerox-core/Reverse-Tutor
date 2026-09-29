"""Numeric acceptance tests for the Python forgetting curve.

Mirrors the 17 Kotlin assertions in
``mobile-native/core/domain/src/test/java/com/reversetutor/core/domain/ForgettingCurveTest.kt``
(design docs/specs/forgetting-curve-design.md §8.2): stability table, review
chain, lapse fallback, three-stage boundaries, append-only rescue semantics —
plus the stage-evidence seam boundary.
"""
from __future__ import annotations

import math
from datetime import datetime, timedelta, timezone

import pytest

from online_db.forgetting_curve import (
    ABSORB_THRESHOLD,
    Absorbed,
    Decaying,
    NoEvidence,
    NoopForgettingCurve,
    Protection,
    RESULT_FAILED,
    StageEvidenceForgettingCurve,
    ForgettingEvent,
    initial_stability_days,
    node_stage,
    stability_days,
    strength,
)

T0 = datetime.fromtimestamp(1_700_000_000, tz=timezone.utc)
DAY = timedelta(days=1)


def event(evidence_type: str, result: str = "passed", day_offset: float = 0.0):
    return ForgettingEvent(evidence_type, result, T0 + DAY * day_offset)


# ---------- S_0 table (design §3.1) ----------


def test_initial_stability_matches_design_table():
    assert initial_stability_days(0.35) == pytest.approx(2.94, abs=1e-4)
    assert initial_stability_days(0.55) == pytest.approx(7.26, abs=1e-4)
    assert initial_stability_days(0.72) == pytest.approx(12.4416, abs=1e-4)
    assert initial_stability_days(0.82) == pytest.approx(16.1376, abs=1e-4)
    assert initial_stability_days(0.90) == pytest.approx(19.44, abs=1e-4)


def test_stability_per_evidence_type_matches_design_table():
    # explanation baseline contact: k=0 → S_0 = 2.94 days
    assert stability_days([event("explanation")]) == pytest.approx(2.94, abs=1e-4)
    # first retrieval is a successful review: k=1 → 7.26 × 2 = 14.52 days
    assert stability_days([event("retrieval")]) == pytest.approx(14.52, abs=1e-4)
    # first delayed_retrieval: k=1 → 16.1376 × 2 = 32.2752 days
    assert stability_days([event("delayed_retrieval")]) == pytest.approx(
        32.2752, abs=1e-4
    )
    # first correction: k=1 → 19.44 × 2 = 38.88 days
    assert stability_days([event("correction")]) == pytest.approx(38.88, abs=1e-4)


def test_partial_evidence_discounts_stability():
    # partial retrieval: eff = 0.55 × 0.75 = 0.4125 → S_0 = 24 × 0.4125² =
    # 4.08375; k=1 → ×2
    assert stability_days([event("retrieval", "partial")]) == pytest.approx(
        8.1675, abs=1e-4
    )


# ---------- review chain (§3.2) ----------


def _chain(success_count: int) -> float:
    events = [event("retrieval", "passed", day_offset=i) for i in range(success_count)]
    return stability_days(events)


def test_review_chain_doubles_stability_and_caps_at_180_days():
    assert _chain(1) == pytest.approx(14.52, abs=1e-4)
    assert _chain(2) == pytest.approx(29.04, abs=1e-4)
    assert _chain(3) == pytest.approx(58.08, abs=1e-4)
    assert _chain(4) == pytest.approx(116.16, abs=1e-4)
    assert _chain(5) == pytest.approx(180.0, abs=1e-4)
    assert _chain(9) == pytest.approx(180.0, abs=1e-4)


def test_explanation_does_not_boost_chain():
    events = [event("explanation", day_offset=i) for i in range(3)]
    assert stability_days(events) == pytest.approx(2.94, abs=1e-4)


def test_chain_counts_across_evidence_types():
    events = [
        event("retrieval", day_offset=0),
        event("delayed_retrieval", day_offset=1),
        event("correction", day_offset=2),
    ]
    s0 = initial_stability_days(0.90)
    assert stability_days(events) == pytest.approx(s0 * 8.0, abs=1e-4)


# ---------- lapse fallback (§3.3) ----------


def test_one_lapse_cancels_two_successes():
    events = [
        event("retrieval", day_offset=0),
        event("retrieval", day_offset=1),
        event("retrieval", day_offset=2),
        event("retrieval", RESULT_FAILED, day_offset=3),
    ]
    # k_eff = 3 − 2 = 1 → S_0(retrieval) × 2 = 14.52
    assert stability_days(events) == pytest.approx(14.52, abs=1e-4)


def test_lapses_never_drive_stability_negative():
    events = [event("retrieval", RESULT_FAILED, day_offset=i) for i in range(3)]
    # only failed: no consolidating event → None (failed is not a contact)
    assert stability_days(events) is None
    assert isinstance(node_stage(events, T0 + 400 * DAY), NoEvidence)


def test_lapse_and_success_mixed():
    events = [
        event("retrieval", day_offset=0),
        event("retrieval", RESULT_FAILED, day_offset=1),
        event("retrieval", day_offset=2),
    ]
    # k_success=2, k_lapse=1 → k_eff=0 → S = S_0(retrieval) = 7.26
    assert stability_days(events) == pytest.approx(7.26, abs=1e-4)


# ---------- three-stage boundaries (§4) ----------


def test_stage_boundaries_around_one_and_three_stabilities():
    # single correction (k=1) → S = 38.88 days; t_last = T0
    s = 38.88
    events = [event("correction")]

    # Δt = 1.0S − ε → Protection
    just_inside = T0 + DAY * (s - 0.001)
    assert isinstance(node_stage(events, just_inside), Protection)
    # Δt = 1.0S + ε → Decaying, progress ≈ 0
    just_decaying = T0 + DAY * (s + 0.001)
    stage = node_stage(events, just_decaying)
    assert isinstance(stage, Decaying)
    assert stage.progress == pytest.approx(0.001 / (2.0 * s), abs=1e-6)
    # retention at the decay threshold ≈ e^-1 ≈ 0.368
    assert stage.retention == pytest.approx(0.368, abs=0.01)
    # Δt = 3.0S − ε → Decaying, progress ≈ 1
    near_absorb = T0 + DAY * (3.0 * s - 0.001)
    stage = node_stage(events, near_absorb)
    assert isinstance(stage, Decaying)
    assert stage.progress == pytest.approx(1.0 - 0.001 / (2.0 * s), abs=1e-5)
    # retention at the absorb threshold ≈ e^-3 ≈ 0.0498
    assert stage.retention == pytest.approx(0.05, abs=0.01)
    # Δt = 3.0S + ε → Absorbed
    absorbed = T0 + DAY * (3.0 * s + 0.001)
    assert isinstance(node_stage(events, absorbed), Absorbed)


def test_protection_reports_elapsed_days():
    events = [event("retrieval")]  # S = 14.52
    stage = node_stage(events, T0 + DAY * 7.26)
    assert isinstance(stage, Protection)
    assert stage.elapsed_days == pytest.approx(7.26, abs=1e-4)


def test_clock_skew_treated_as_zero_elapsed():
    events = [event("correction", day_offset=5)]
    stage = node_stage(events, T0)  # now before the event
    assert isinstance(stage, Protection)
    assert stage.elapsed_days == pytest.approx(0.0, abs=1e-6)


# ---------- rescue semantics (append-only, §3.4 / D7) ----------


def test_append_new_consolidating_event_rescues_absorbed_node():
    old = [event("explanation", day_offset=-100)]  # long absorbed
    long_after = T0 + 400 * DAY
    assert isinstance(node_stage(old, long_after), Absorbed)

    # rescue = appending a new event (the old stream is not modified)
    rescued = old + [event("delayed_retrieval", day_offset=400)]
    stage = node_stage(rescued, long_after)
    assert isinstance(stage, Protection)
    # k_success = 1 → S_0(0.82) × 2 = 32.2752
    assert stage.stability_days == pytest.approx(32.2752, abs=1e-4)


# ---------- no evidence / unknown types ----------


def test_unknown_or_empty_streams_yield_no_evidence():
    assert isinstance(node_stage([], T0), NoEvidence)
    assert isinstance(node_stage([event("mystery_type")], T0 + 400 * DAY), NoEvidence)
    assert stability_days([]) is None


# ---------- visual strength (§5) ----------


def test_strength_sums_event_retentions():
    # event 1: retrieval (eff 0.55, k=1 → S=14.52), Δt=7 days → R = e^(-7/14.52)
    # event 2: transfer (eff 0.72, Δt=0 → R = 1; transfer stays out of the chain)
    events = [event("retrieval", day_offset=0), event("transfer", day_offset=7)]
    now = T0 + 7 * DAY
    expected = 0.55 * math.exp(-7.0 / 14.52) + 0.72
    assert strength(events, now) == pytest.approx(expected, abs=1e-4)


def test_strength_ignores_failed_events():
    events = [event("retrieval", RESULT_FAILED, day_offset=0)]
    assert strength(events, T0 + DAY) == pytest.approx(0.0, abs=1e-6)


# ---------- determinism ----------


def test_input_order_does_not_affect_results():
    events = [
        event("retrieval", day_offset=3),
        event("correction", day_offset=1),
        event("explanation", day_offset=2),
    ]
    shuffled = list(reversed(events))
    assert stability_days(events) == pytest.approx(stability_days(shuffled), abs=1e-6)
    now = T0 + 10 * DAY
    assert node_stage(events, now) == node_stage(shuffled, now)


# ---------- stage-evidence seam ----------


def test_stage_evidence_curve_valid_inside_window_and_invalid_beyond():
    curve = StageEvidenceForgettingCurve()
    # anchor delayed_retrieval: eff 0.82 → S_0 = 16.1376 days; absorb at 3S
    s0 = 16.1376
    recorded = T0
    inside = T0 + DAY * (3.0 * s0 - 0.001)
    beyond = T0 + DAY * (3.0 * s0 + 0.001)
    assert curve.evidence_valid(
        stage_index=1, evidence_key="k1", recorded_at=recorded, now=inside
    )
    assert not curve.evidence_valid(
        stage_index=1, evidence_key="k1", recorded_at=recorded, now=beyond
    )
    # absorb boundary matches the D7 model constant
    assert ABSORB_THRESHOLD == 3.0


def test_noop_curve_remains_never_expiring_default():
    curve = NoopForgettingCurve()
    assert curve.evidence_valid(
        stage_index=1,
        evidence_key="k1",
        recorded_at=T0,
        now=T0 + 3650 * DAY,
    )
