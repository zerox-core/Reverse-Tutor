from __future__ import annotations

from typing import Any, Literal

from pydantic import ConfigDict, Field

from .models import CamelModel


class IllustrationConfig(CamelModel):
    model_config = ConfigDict(extra="allow")

    dialogues: list[str] = Field(default_factory=list, max_length=6)
    palette: str | None = Field(default=None, max_length=40)


class AssetRef(CamelModel):
    model_config = ConfigDict(extra="forbid")

    url: str
    mime_type: str
    width: int = Field(ge=1)
    height: int = Field(ge=1)
    bytes: int | None = Field(default=None, ge=0)
    sha256: str | None = None


class ContentFeedItem(CamelModel):
    model_config = ConfigDict(extra="allow")

    id: str
    slug: str
    type: Literal["public_interest", "announcement"]
    title: str = Field(max_length=80)
    summary: str = Field(max_length=180)
    illustration_template: str
    illustration_config: IllustrationConfig
    cover: AssetRef | None = None
    publisher_name: str | None = None
    published_at_epoch_millis: int
    content_version: int = Field(ge=1)


class ContentFeedResponse(CamelModel):
    model_config = ConfigDict(extra="forbid")

    version: int = Field(ge=0)
    updated_at_epoch_millis: int
    items: list[ContentFeedItem]
    next_cursor: str | None


class ContentDetail(ContentFeedItem):
    body_markdown: str = Field(max_length=100_000)
    body_assets: list[AssetRef] = Field(max_length=5)


class ActivityTask(CamelModel):
    model_config = ConfigDict(extra="forbid")

    day_number: int = Field(ge=1, le=366)
    title: str = Field(min_length=1, max_length=200)
    task_markdown: str = Field(default="", max_length=8000)
    stage_goal: str | None = Field(default=None, max_length=500)


class Activity(CamelModel):
    model_config = ConfigDict(extra="forbid")

    id: str
    title: str
    description: str
    revision: int = Field(ge=0)
    starts_at_epoch_millis: int
    ends_at_epoch_millis: int
    requires_online_confirmation: bool
    allows_deferred_progress: bool
    state: Literal["scheduled", "active", "closed", "offline"]
    session_template_id: str | None = None
    tasks: list[ActivityTask] = Field(default_factory=list, max_length=64)


class ActivityListResponse(CamelModel):
    model_config = ConfigDict(extra="forbid")

    items: list[Activity]
    next_cursor: str | None
    updated_at_epoch_millis: int


class ActivityParticipation(CamelModel):
    model_config = ConfigDict(extra="forbid")

    activity_id: str
    user_id: str
    joined: bool
    progress: int = Field(ge=0)
    revision: int = Field(ge=0)
    state: Literal["joined", "pending_sync", "completed", "left"]
    idempotency_key: str


class LeaderboardItem(CamelModel):
    model_config = ConfigDict(extra="forbid")

    rank: int = Field(ge=1)
    display_name: str
    avatar_url: str | None = None
    progress: int = Field(ge=0)
    is_current_user: bool = False


class LeaderboardResponse(CamelModel):
    model_config = ConfigDict(extra="forbid")

    items: list[LeaderboardItem]
    next_cursor: str | None
    updated_at_epoch_millis: int
