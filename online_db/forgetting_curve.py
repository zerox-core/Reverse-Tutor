"""Forgetting-curve seam for stage evidence validity.

The backend forgetting-curve design is not finalised yet (see
docs/specs/stage-progress-model.md §10). Until it lands, evidence never
decays: ``NoopForgettingCurve`` is the default implementation and every
recorded evidence event counts as satisfied forever. When the real curve
is ready, implement the ``ForgettingCurve`` protocol and inject it into
``SqlAlchemyStageStore`` — no other call sites need to change.
"""
from __future__ import annotations

from datetime import datetime
from typing import Protocol


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
        """Return True when the evidence recorded at ``recorded_at`` is still valid at ``now``."""
        ...  # pragma: no cover


class NoopForgettingCurve:
    """Placeholder that never decays evidence (backend curve pending)."""

    def evidence_valid(
        self,
        *,
        stage_index: int,
        evidence_key: str,
        recorded_at: datetime,
        now: datetime,
    ) -> bool:
        return True
