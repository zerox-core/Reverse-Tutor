from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class OnlineDatabaseSettings:
    database_url: str

    @classmethod
    def from_env(cls) -> "OnlineDatabaseSettings":
        database_url = os.getenv("ONLINE_DATABASE_URL", "").strip()
        if not database_url:
            raise RuntimeError("ONLINE_DATABASE_URL is required")
        return cls(database_url=database_url)
