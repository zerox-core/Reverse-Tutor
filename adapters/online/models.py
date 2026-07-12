from __future__ import annotations

from typing import Any

from pydantic import BaseModel, ConfigDict, Field


def _to_camel(value: str) -> str:
    head, *tail = value.split("_")
    return head + "".join(part[:1].upper() + part[1:] for part in tail)


class CamelModel(BaseModel):
    model_config = ConfigDict(
        alias_generator=_to_camel,
        populate_by_name=True,
        serialize_by_alias=True,
    )


class OnlineWriteIdentity(CamelModel):
    user_id: str = Field(min_length=1)
    device_id: str = Field(min_length=1)
    revision: int = Field(ge=0)
    idempotency_key: str = Field(min_length=1)


class ActivityProgressRequest(OnlineWriteIdentity):
    progress: int = Field(ge=0)


class SyncItem(CamelModel):
    envelope_id: str = Field(min_length=1)
    entity_id: str = Field(min_length=1)
    entity_type: str = Field(min_length=1)
    revision: int = Field(ge=0)
    idempotency_key: str = Field(min_length=1)
    payload: dict[str, Any] = Field(default_factory=dict)
    deleted_at_epoch_millis: int | None = None


class SyncPushRequest(CamelModel):
    user_id: str = Field(min_length=1)
    device_id: str = Field(min_length=1)
    cursor: str | None = None
    items: list[SyncItem] = Field(default_factory=list, max_length=100)


class SyncPullRequest(CamelModel):
    user_id: str = Field(min_length=1)
    device_id: str = Field(min_length=1)
    cursor: str | None = None


class WeeklyInsightRequest(CamelModel):
    user_id: str = Field(min_length=1)
    device_id: str = Field(min_length=1)
    space_id: str = Field(min_length=1)
    week_start_epoch_millis: int = Field(ge=0)
    source_revision: int = Field(ge=0)
    statistics: dict[str, int] = Field(default_factory=dict)
