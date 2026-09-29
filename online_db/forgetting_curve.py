"""Forgetting curve — Python equivalent of the Kotlin ``ForgettingCurve``.

Canonical design: docs/specs/forgetting-curve-design.md (finalised 2026-09-29).
Reference implementation (numeric parity target):
``mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/ForgettingCurve.kt``
(Kotlin, R103). This module mirrors the same constants, the same formulas and
the same numeric behaviour; ``tests/test_forgetting_curve.py`` mirrors the 17
Kotlin unit assertions.

Model: per-event exponential retention ``R_i(t) = exp(-(t - t_i) / S_i)``; node
forgetting emerges from the collective event stream (D7).

- Initial stability ``S_0 = 24 × eff²`` days (eff = evidence effectiveness, see
  ``EVIDENCE_EFFECTIVENESS``; partial events discount eff × 0.75);
- Review chain: ``S = S_0 × G^k`` (G = 2, capped at 180 days); successful
  reviews (retrieval / delayed_retrieval / correction, non-failed) count, one
  failed cancels two successes (``k_eff = max(0, k_success − 2 × k_lapse)``);
- Stateless: every value is recomputed from the append-only event stream
  (D2 "strength is never stored"); rescue = appending a new consolidating
  event naturally resets the curve (D7), no special-casing;
- D7 three stages (using the node's current stability ``S_last``,
  ``Δt = now − t_last``):
  ``Δt ≤ 1.0 S`` protection; ``1.0 S < Δt ≤ 3.0 S`` decaying
  (``progress = (Δt − S) / (2S) ∈ (0, 1]``); ``Δt > 3.0 S`` absorbed.

Pure Python, no I/O, no third-party dependencies. Events may arrive unordered;
results are deterministic.

Seam for stage evidence validity (used by ``SqlAlchemyStageStore``):
``NoopForgettingCurve`` remains the wired default (production semantics
unchanged). ``StageEvidenceForgettingCurve`` is the real implementation —
anchored per docs/specs/stage-progress-model.md §10 ("阶段证据可作为
delayed_retrieval 的挂点") — kept un-wired until the anchor mapping and the
rollout are ratified.
"""
from __future__ import annotations

import math
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Optional, Protocol, Sequence

# ---------------------------------------------------------------------------
# Constants (mirror the Kotlin object; keep in sync with ForgettingCurve.kt)
# ---------------------------------------------------------------------------

INITIAL_STABILITY_DAYS = 24.0
INITIAL_STABILITY_EXPONENT = 2
REVIEW_CHAIN_GAIN = 2.0
MAX_STABILITY_DAYS = 180.0
LAPSE_PENALTY_FACTOR = 2
DECAY_THRESHOLD = 1.0
ABSORB_THRESHOLD = 3.0
PARTIAL_EVIDENCE_FACTOR = 0.75
SECONDS_PER_DAY = 86_400.0

RESULT_PASSED = "passed"
RESULT_PARTIAL = "partial"
RESULT_FAILED = "failed"

#: Evidence effectiveness eff (same source as the Kotlin EvidenceEffectiveness
#: map / MasteryLedgerProjection.EvidenceTargetScores ÷ 100).
EVIDENCE_EFFECTIVENESS: dict[str, float] = {
    "explanation": 0.35,
    "retrieval": 0.55,
    "transfer": 0.72,
    "delayed_retrieval": 0.82,
    "correction": 0.90,
}

#: Evidence types that count into the review chain; explanation is a baseline
#: contact and never boosts the chain.
REVIEW_CHAIN_EVIDENCE_TYPES: frozenset[str] = frozenset(
    {"retrieval", "delayed_retrieval", "correction"}
)

#: Anchor evidence type for recorded stage evidence (stage-progress-model.md
#: §10: 阶段证据可作为 delayed_retrieval 的挂点). Ratification pending — see
#: ``StageEvidenceForgettingCurve``.
STAGE_EVIDENCE_ANCHOR_TYPE = "delayed_retrieval"


# ---------------------------------------------------------------------------
# Event & stage model
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class ForgettingEvent:
    """Normalised learning event (only the fields the algorithm needs)."""

    evidence_type: str
    result: str
    occurred_at: datetime  # timezone-aware (UTC)


class ForgettingNodeStage:
    """Base class of the D7 three-stage verdicts."""


@dataclass(frozen=True)
class NoEvidence(ForgettingNodeStage):
    """No consolidating event (empty stream / unknown types / only failed):
    forgetting is not judged; the UI layer's protection policy applies."""


@dataclass(frozen=True)
class Protection(ForgettingNodeStage):
    """Cooling protection: Δt ≤ 1.0 × S_last."""

    stability_days: float
    elapsed_days: float


@dataclass(frozen=True)
class Decaying(ForgettingNodeStage):
    """Link-decay: 1.0 S < Δt ≤ 3.0 S (breathing = about-to-forget warning)."""

    progress: float  # ∈ (0, 1]; 1.0 = absorbed
    retention: float  # retention of the latest consolidating event, e^(−Δt/S)
    stability_days: float


@dataclass(frozen=True)
class Absorbed(ForgettingNodeStage):
    """Into the core: Δt > 3.0 × S_last (projection hidden, data kept)."""


# ---------------------------------------------------------------------------
# Pure functions
# ---------------------------------------------------------------------------


def effectiveness(evidence_type: str, result: str) -> Optional[float]:
    """Effective evidence score: passed → table value; partial → × 0.75;
    failed / unknown type → None (not a consolidating event)."""
    base = EVIDENCE_EFFECTIVENESS.get(evidence_type)
    if base is None:
        return None
    if result == RESULT_PASSED:
        return base
    if result == RESULT_PARTIAL:
        return base * PARTIAL_EVIDENCE_FACTOR
    return None


def initial_stability_days(eff: float) -> float:
    """S_0 (days) = 24 × eff²."""
    return INITIAL_STABILITY_DAYS * eff**INITIAL_STABILITY_EXPONENT


def latest_consolidating_event(
    events: Sequence[ForgettingEvent],
) -> Optional[ForgettingEvent]:
    """The most recent consolidating event (largest timestamp)."""
    consolidating = [
        event
        for event in events
        if effectiveness(event.evidence_type, event.result) is not None
    ]
    if not consolidating:
        return None
    return max(consolidating, key=lambda event: event.occurred_at)


def _chain_counts(events: Sequence[ForgettingEvent]) -> tuple[int, int]:
    k_success = 0
    k_lapse = 0
    for event in events:
        if event.evidence_type not in EVIDENCE_EFFECTIVENESS:
            continue
        if event.result == RESULT_FAILED:
            k_lapse += 1
        elif event.result in (RESULT_PASSED, RESULT_PARTIAL):
            if event.evidence_type in REVIEW_CHAIN_EVIDENCE_TYPES:
                k_success += 1
    return k_success, k_lapse


def stability_days(events: Sequence[ForgettingEvent]) -> Optional[float]:
    """Node stability S_last (days): S_0(latest) × G^k_eff, capped at 180.

    Returns None when the stream has no consolidating event.
    """
    latest = latest_consolidating_event(events)
    if latest is None:
        return None
    eff = effectiveness(latest.evidence_type, latest.result)
    if eff is None:  # pragma: no cover - guarded by latest_consolidating_event
        return None
    s0 = initial_stability_days(eff)
    k_success, k_lapse = _chain_counts(events)
    k_eff = max(0, k_success - LAPSE_PENALTY_FACTOR * k_lapse)
    return min(s0 * REVIEW_CHAIN_GAIN**k_eff, MAX_STABILITY_DAYS)


def _elapsed_days(now: datetime, occurred_at: datetime) -> float:
    delta = (_as_utc(now) - _as_utc(occurred_at)).total_seconds()
    return max(0.0, delta) / SECONDS_PER_DAY


def node_stage(
    events: Sequence[ForgettingEvent], now: datetime
) -> ForgettingNodeStage:
    """D7 three-stage verdict (pure). Clock skew (now before the latest
    event) is treated as Δt = 0."""
    latest = latest_consolidating_event(events)
    if latest is None:
        return NoEvidence()
    stability = stability_days(events)
    if stability is None:  # pragma: no cover - guarded by latest
        return NoEvidence()
    elapsed = _elapsed_days(now, latest.occurred_at)
    if elapsed <= DECAY_THRESHOLD * stability:
        return Protection(stability_days=stability, elapsed_days=elapsed)
    if elapsed <= ABSORB_THRESHOLD * stability:
        return Decaying(
            progress=(elapsed - DECAY_THRESHOLD * stability)
            / ((ABSORB_THRESHOLD - DECAY_THRESHOLD) * stability),
            retention=math.exp(-elapsed / stability),
            stability_days=stability,
        )
    return Absorbed()


def strength(events: Sequence[ForgettingEvent], now: datetime) -> float:
    """Visual strength (design §5, dual-track with the stage machine — LOD and
    visual weighting only): ``strength = Σ_i eff_i × R_i(t)`` where each
    event's retention uses the chain stability at its own occurrence time.
    Streams without consolidating events return 0. Stacked weak events never
    postpone forgetting (the stage machine only looks at S_last)."""
    k_success = 0
    k_lapse = 0
    total = 0.0
    for event in sorted(events, key=lambda item: item.occurred_at):
        if event.evidence_type in EVIDENCE_EFFECTIVENESS:
            if event.result == RESULT_FAILED:
                k_lapse += 1
            elif event.result in (RESULT_PASSED, RESULT_PARTIAL):
                if event.evidence_type in REVIEW_CHAIN_EVIDENCE_TYPES:
                    k_success += 1
        eff = effectiveness(event.evidence_type, event.result)
        if eff is None:
            continue
        k_eff = max(0, k_success - LAPSE_PENALTY_FACTOR * k_lapse)
        stability = min(
            initial_stability_days(eff) * REVIEW_CHAIN_GAIN**k_eff,
            MAX_STABILITY_DAYS,
        )
        elapsed = _elapsed_days(now, event.occurred_at)
        total += eff * math.exp(-elapsed / stability)
    return total


def _as_utc(value: datetime) -> datetime:
    if value.tzinfo is None or value.utcoffset() is None:
        return value.replace(tzinfo=timezone.utc)
    return value.astimezone(timezone.utc)


# ---------------------------------------------------------------------------
# Stage-evidence validity seam (consumed by SqlAlchemyStageStore)
# ---------------------------------------------------------------------------


class ForgettingCurve(Protocol):
    """Decides whether a recorded evidence event still counts as satisfied."""

    def evidence_valid(
        self,
        *,
        stage_index: int,
        evidence_key: str,
        recorded_at: datetime,
        now: datetime,
    ) -> bool:
        """Return True when the evidence recorded at ``recorded_at`` is still
        valid at ``now``."""
        ...  # pragma: no cover


class NoopForgettingCurve:
    """Placeholder that never decays evidence (wired default — production
    semantics unchanged until the stage-evidence anchor mapping is ratified)."""

    def evidence_valid(
        self,
        *,
        stage_index: int,
        evidence_key: str,
        recorded_at: datetime,
        now: datetime,
    ) -> bool:
        return True


class StageEvidenceForgettingCurve:
    """Real curve for the seam, on the D7 three-stage model.

    Each recorded stage evidence event is anchored as
    ``STAGE_EVIDENCE_ANCHOR_TYPE`` (delayed_retrieval per
    stage-progress-model.md §10) and decays on its own initial stability
    ``S_0 = 24 × eff²``; it stops counting once
    ``Δt > ABSORB_THRESHOLD × S_0`` (absorbed — mirroring the Kotlin
    ``Absorbed`` verdict: projection hidden, data kept). Backend stage
    evidence is one event per (stage, evidence_key), so no review chain
    applies at this seam.

    NOT wired as the default: the anchor mapping and the rollout (existing
    evidence must not silently expire) are pending ratification.
    """

    def evidence_valid(
        self,
        *,
        stage_index: int,
        evidence_key: str,
        recorded_at: datetime,
        now: datetime,
    ) -> bool:
        eff = EVIDENCE_EFFECTIVENESS[STAGE_EVIDENCE_ANCHOR_TYPE]
        stability = initial_stability_days(eff)
        elapsed = _elapsed_days(now, recorded_at)
        return elapsed <= ABSORB_THRESHOLD * stability
