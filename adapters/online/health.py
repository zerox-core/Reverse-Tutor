from __future__ import annotations

from dataclasses import dataclass
from threading import RLock
from time import time_ns

from pydantic import BaseModel, ConfigDict, Field

from online_db.schema_check import online_schema_head


@dataclass(frozen=True)
class OnlineRuntimeStatus:
    mode: str
    schema_head: str


class OnlineCatalogHealth(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    content_available: bool = Field(alias="contentAvailable")
    activity_available: bool = Field(alias="activityAvailable")


class OnlineHealthResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    status: str
    mode: str
    schema_head: str = Field(alias="schemaHead")
    catalog: OnlineCatalogHealth
    server_time_epoch_millis: int = Field(alias="serverTimeEpochMillis")


_lock = RLock()
_status = OnlineRuntimeStatus(mode="memory", schema_head=online_schema_head())


def set_online_runtime_status(*, mode: str, schema_head: str) -> None:
    global _status
    with _lock:
        _status = OnlineRuntimeStatus(mode=mode, schema_head=schema_head)


def reset_online_runtime_status() -> None:
    set_online_runtime_status(mode="memory", schema_head=online_schema_head())


def online_health_response(
    *, content_available: bool, activity_available: bool
) -> OnlineHealthResponse:
    with _lock:
        current = _status
    return OnlineHealthResponse(
        status="ready",
        mode=current.mode,
        schemaHead=current.schema_head,
        catalog=OnlineCatalogHealth(
            contentAvailable=content_available,
            activityAvailable=activity_available,
        ),
        serverTimeEpochMillis=time_ns() // 1_000_000,
    )
