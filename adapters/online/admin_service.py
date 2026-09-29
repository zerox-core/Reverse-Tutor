from __future__ import annotations

from threading import RLock
from typing import Any


class AdminActivityServiceUnavailable(RuntimeError):
    pass


class AdminActivityService:
    """Holds the admin-facing activity store wiring.

    The admin API operates on the same SQLAlchemy store as the public online
    API, but is wired separately so it stays disabled (503) when no online
    database is configured and can be reset between tests.
    """

    def __init__(self) -> None:
        self._lock = RLock()
        self._store: Any | None = None

    def set_activity_store(self, store: Any) -> None:
        with self._lock:
            self._store = store

    def reset_activity_store(self) -> None:
        with self._lock:
            self._store = None

    @property
    def activity_store(self) -> Any:
        with self._lock:
            if self._store is None:
                raise AdminActivityServiceUnavailable(
                    "Admin activity store is not configured"
                )
            return self._store


admin_activity_service = AdminActivityService()


class AdminStageServiceUnavailable(RuntimeError):
    pass


class AdminStageService:
    """Holds the admin-facing stage store wiring.

    Stage-plan generation (material-driven, docs/specs/material-driven-stage-generator.md)
    and stage definition run on the same SQLAlchemy stage store as the public
    online API; wired separately so the admin stage API stays disabled (503)
    when no online database is configured and can be reset between tests.
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
                raise AdminStageServiceUnavailable(
                    "Admin stage store is not configured"
                )
            return self._store


admin_stage_service = AdminStageService()


class AdminProbePlanServiceUnavailable(RuntimeError):
    pass


class AdminProbePlanService:
    """Holds the admin-facing probe plan store wiring.

    Probe-plan generation (spec docs/specs/stage-progress-model.md §11.3) is
    archived per participation through the SQLAlchemy probe plan store; wired
    separately so the admin probe-plan API stays disabled (503) when no
    online database is configured and can be reset between tests.
    """

    def __init__(self) -> None:
        self._lock = RLock()
        self._store: Any | None = None

    def set_probe_plan_store(self, store: Any) -> None:
        with self._lock:
            self._store = store

    def reset_probe_plan_store(self) -> None:
        with self._lock:
            self._store = None

    @property
    def probe_plan_store(self) -> Any:
        with self._lock:
            if self._store is None:
                raise AdminProbePlanServiceUnavailable(
                    "Admin probe plan store is not configured"
                )
            return self._store


admin_probe_plan_service = AdminProbePlanService()
