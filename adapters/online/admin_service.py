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
