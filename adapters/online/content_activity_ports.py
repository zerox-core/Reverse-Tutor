from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Any, Literal, Protocol


@dataclass(frozen=True)
class AssetRecord:
    url: str
    mime_type: str
    width: int
    height: int
    bytes: int | None = None
    sha256: str | None = None


@dataclass(frozen=True)
class ContentRecord:
    id: str
    slug: str
    type: Literal["public_interest", "announcement"]
    title: str
    summary: str
    illustration_template: str
    illustration_config: dict[str, Any]
    cover: AssetRecord | None
    publisher_name: str | None
    published_at: datetime
    content_version: int
    body_markdown: str
    body_assets: tuple[AssetRecord, ...]
    status: Literal["published", "offline"] = "published"


@dataclass(frozen=True)
class ContentFeedQuery:
    cursor: str | None
    limit: int
    content_types: frozenset[str]
    at: datetime


@dataclass(frozen=True)
class ContentPage:
    version: int
    updated_at: datetime
    items: tuple[ContentRecord, ...]
    next_cursor: str | None


@dataclass(frozen=True)
class ActivityRecord:
    id: str
    title: str
    description: str
    revision: int
    starts_at: datetime
    ends_at: datetime
    requires_online_confirmation: bool
    allows_deferred_progress: bool
    state: Literal["scheduled", "active", "closed", "offline"]
    session_template_id: str | None = None


@dataclass(frozen=True)
class ActivityQuery:
    cursor: str | None
    limit: int
    at: datetime


@dataclass(frozen=True)
class ActivityPage:
    items: tuple[ActivityRecord, ...]
    next_cursor: str | None
    updated_at: datetime


@dataclass(frozen=True)
class ActivityWriteCommand:
    activity_id: str
    account_id: str
    device_id: str
    revision: int
    idempotency_key: str
    at: datetime
    progress: int | None = None


@dataclass(frozen=True)
class ActivityParticipationRecord:
    activity_id: str
    user_id: str
    joined: bool
    progress: int
    revision: int
    state: Literal["joined", "pending_sync", "completed", "left"]
    idempotency_key: str


@dataclass(frozen=True)
class LeaderboardQuery:
    activity_id: str
    cursor: str | None
    limit: int
    at: datetime


@dataclass(frozen=True)
class LeaderboardRecord:
    rank: int
    display_name: str
    avatar_url: str | None
    progress: int
    is_current_user: bool = False


@dataclass(frozen=True)
class LeaderboardPage:
    items: tuple[LeaderboardRecord, ...]
    next_cursor: str | None
    updated_at: datetime


class ActivityPortConflict(Exception):
    def __init__(
        self,
        code: str,
        message: str,
        *,
        details: dict[str, Any] | None = None,
    ) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.details = details or {}


class PublicContentPort(Protocol):
    def list_published(self, query: ContentFeedQuery) -> ContentPage: ...

    def get_by_slug(self, slug: str, at: datetime) -> ContentRecord | None: ...


class ActivityPort(Protocol):
    def list_activities(self, query: ActivityQuery) -> ActivityPage: ...

    def get_activity(self, activity_id: str, at: datetime) -> ActivityRecord | None: ...

    def join(
        self, command: ActivityWriteCommand
    ) -> ActivityParticipationRecord | None: ...

    def update_progress(
        self, command: ActivityWriteCommand
    ) -> ActivityParticipationRecord | None: ...

    def leave(
        self, command: ActivityWriteCommand
    ) -> ActivityParticipationRecord | None: ...

    def leaderboard(self, query: LeaderboardQuery) -> LeaderboardPage | None: ...
