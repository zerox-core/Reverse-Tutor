from __future__ import annotations

from threading import RLock
from typing import Any


class StageProgressServiceUnavailable(RuntimeError):
    pass


class StageProgressService:
    """Holds the participant-facing stage store wiring.

    Evidence write-back (spec docs/specs/stage-progress-model.md §3) runs on
    the same SQLAlchemy stage store as the admin stage API; wired separately
    so the participant endpoints stay disabled (503) when no online database
    is configured and can be reset between tests.
    """

    def __init__(self) -> None:
        self._lock = RLock()
        self._store: Any | None = None

    def set_stage_store(self, store: Any) -> None:
        with self._lock:
            self._store = store

    def reset_stage_store(self) -> None:
        with self._lock:
            self._store = None

    @property
    def stage_store(self) -> Any:
        with self._lock:
            if self._store is None:
                raise StageProgressServiceUnavailable(
                    "Stage progress store is not configured"
                )
            return self._store


stage_progress_service = StageProgressService()
