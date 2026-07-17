from __future__ import annotations

from dataclasses import dataclass
from datetime import UTC, datetime
from typing import Callable

from .content_activity_models import (
    Activity,
    ActivityListResponse,
    ActivityParticipation,
    AssetRef,
    ContentDetail,
    ContentFeedItem,
    ContentFeedResponse,
    LeaderboardItem,
    LeaderboardResponse,
)
from .content_activity_ports import (
    ActivityPort,
    ActivityQuery,
    ActivityWriteCommand,
    ContentFeedQuery,
    PublicContentPort,
    LeaderboardQuery,
)
from .models import ActivityProgressRequest, OnlineWriteIdentity


class ContentNotFound(Exception):
    pass


class ContentOffline(Exception):
    pass


@dataclass(frozen=True)
class CacheableContentFeed:
    body: ContentFeedResponse
    etag: str


@dataclass(frozen=True)
class CacheableContentDetail:
    body: ContentDetail
    etag: str


class ContentActivityService:
    def __init__(
        self,
        content_port: PublicContentPort,
        activity_port: ActivityPort,
        clock: Callable[[], datetime] | None = None,
    ) -> None:
        self.content_port = content_port
        self.activity_port = activity_port
        self.clock = clock or (lambda: datetime.now(UTC))

    def content_feed(
        self,
        *,
        cursor: str | None,
        limit: int,
        content_types: frozenset[str],
    ) -> CacheableContentFeed:
        page = self.content_port.list_published(
            ContentFeedQuery(cursor, limit, content_types, self._now())
        )
        body = ContentFeedResponse(
            version=page.version,
            updated_at_epoch_millis=_epoch_millis(page.updated_at),
            items=[_content_feed_item(item) for item in page.items],
            next_cursor=page.next_cursor,
        )
        return CacheableContentFeed(body, f'"content-feed-{page.version}"')

    def content_detail(self, slug: str) -> CacheableContentDetail:
        record = self.content_port.get_by_slug(slug, self._now())
        if record is None:
            raise ContentNotFound
        if record.status == "offline":
            raise ContentOffline
        item = _content_feed_item(record)
        body = ContentDetail(
            **item.model_dump(),
            body_markdown=record.body_markdown,
            body_assets=[_asset(asset) for asset in record.body_assets],
        )
        return CacheableContentDetail(
            body,
            f'"content-{record.id}-{record.content_version}"',
        )

    def list_activities(
        self, *, cursor: str | None, limit: int
    ) -> ActivityListResponse:
        page = self.activity_port.list_activities(
            ActivityQuery(cursor, limit, self._now())
        )
        return ActivityListResponse(
            items=[_activity(item) for item in page.items],
            next_cursor=page.next_cursor,
            updated_at_epoch_millis=_epoch_millis(page.updated_at),
        )

    def get_activity(self, activity_id: str) -> Activity | None:
        record = self.activity_port.get_activity(activity_id, self._now())
        return _activity(record) if record else None

    def join_activity(
        self,
        activity_id: str,
        account_id: str,
        request: OnlineWriteIdentity,
    ) -> ActivityParticipation | None:
        command = self._command(activity_id, account_id, request)
        result = self.activity_port.join(command)
        return _participation(result) if result else None

    def update_activity_progress(
        self,
        activity_id: str,
        account_id: str,
        request: ActivityProgressRequest,
    ) -> ActivityParticipation | None:
        command = self._command(
            activity_id,
            account_id,
            request,
            progress=request.progress,
        )
        result = self.activity_port.update_progress(command)
        return _participation(result) if result else None

    def leave_activity(
        self,
        activity_id: str,
        account_id: str,
        request: OnlineWriteIdentity,
    ) -> ActivityParticipation | None:
        result = self.activity_port.leave(
            self._command(activity_id, account_id, request)
        )
        return _participation(result) if result else None

    def activity_leaderboard(
        self,
        activity_id: str,
        *,
        cursor: str | None,
        limit: int,
    ) -> LeaderboardResponse | None:
        page = self.activity_port.leaderboard(
            LeaderboardQuery(activity_id, cursor, limit, self._now())
        )
        if page is None:
            return None
        return LeaderboardResponse(
            items=[
                LeaderboardItem(
                    rank=item.rank,
                    display_name=item.display_name,
                    avatar_url=item.avatar_url,
                    progress=item.progress,
                    is_current_user=item.is_current_user,
                )
                for item in page.items
            ],
            next_cursor=page.next_cursor,
            updated_at_epoch_millis=_epoch_millis(page.updated_at),
        )

    def _command(
        self,
        activity_id: str,
        account_id: str,
        request: OnlineWriteIdentity,
        *,
        progress: int | None = None,
    ) -> ActivityWriteCommand:
        return ActivityWriteCommand(
            activity_id=activity_id,
            account_id=account_id,
            device_id=request.device_id,
            revision=request.revision,
            idempotency_key=request.idempotency_key,
            at=self._now(),
            progress=progress,
        )

    def _now(self) -> datetime:
        value = self.clock()
        if value.tzinfo is None:
            raise ValueError("Online content/activity clocks must be timezone-aware")
        return value.astimezone(UTC)


def _epoch_millis(value: datetime) -> int:
    if value.tzinfo is None:
        raise ValueError("Online content/activity timestamps must be timezone-aware")
    return int(value.astimezone(UTC).timestamp() * 1000)


def _asset(record) -> AssetRef:
    return AssetRef(
        url=record.url,
        mime_type=record.mime_type,
        width=record.width,
        height=record.height,
        bytes=record.bytes,
        sha256=record.sha256,
    )


def _content_feed_item(record) -> ContentFeedItem:
    return ContentFeedItem(
        id=record.id,
        slug=record.slug,
        type=record.type,
        title=record.title,
        summary=record.summary,
        illustration_template=record.illustration_template,
        illustration_config=record.illustration_config,
        cover=_asset(record.cover) if record.cover else None,
        publisher_name=record.publisher_name,
        published_at_epoch_millis=_epoch_millis(record.published_at),
        content_version=record.content_version,
    )


def _activity(record) -> Activity:
    return Activity(
        id=record.id,
        title=record.title,
        description=record.description,
        revision=record.revision,
        starts_at_epoch_millis=_epoch_millis(record.starts_at),
        ends_at_epoch_millis=_epoch_millis(record.ends_at),
        requires_online_confirmation=record.requires_online_confirmation,
        allows_deferred_progress=record.allows_deferred_progress,
        state=record.state,
        session_template_id=record.session_template_id,
    )


def _participation(record) -> ActivityParticipation:
    return ActivityParticipation(
        activity_id=record.activity_id,
        user_id=record.user_id,
        joined=record.joined,
        progress=record.progress,
        revision=record.revision,
        state=record.state,
        idempotency_key=record.idempotency_key,
    )
