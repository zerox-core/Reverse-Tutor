from __future__ import annotations

from datetime import UTC, datetime
from uuid import UUID

from online_db.activity_store import (
    ActivityIdempotencyConflict,
    ActivityNotFound,
    ActivityParticipationNotFound,
    ActivityRevisionConflict,
    SqlAlchemyActivityStore,
)
from online_db.content_store import SqlAlchemyContentStore

from .content_activity_ports import (
    ActivityPage,
    ActivityPortConflict,
    ActivityQuery,
    ActivityRecord,
    ActivityParticipationRecord,
    ActivityWriteCommand,
    AssetRecord,
    ContentFeedQuery,
    ContentPage,
    ContentRecord,
    LeaderboardPage,
    LeaderboardQuery,
    LeaderboardRecord,
)


class SqlAlchemyPublicContentPort:
    def __init__(self, store: SqlAlchemyContentStore) -> None:
        self._store = store

    def list_published(self, query: ContentFeedQuery) -> ContentPage:
        offset = _cursor_offset(query.cursor)
        rows = self._store.list_published(
            query.at,
            content_types=tuple(sorted(query.content_types)) or None,
            limit=query.limit,
            offset=offset,
        )
        has_more = bool(rows) and bool(
            self._store.list_published(
                query.at,
                content_types=tuple(sorted(query.content_types)) or None,
                limit=1,
                offset=offset + len(rows),
            )
        )
        items = tuple(_content_record(row) for row in rows)
        updated_at = max(
            (item.updated_at for item in rows),
            default=datetime.fromtimestamp(0, UTC),
        )
        version = max(0, int(updated_at.timestamp() * 1_000_000))
        return ContentPage(
            version=version,
            updated_at=updated_at,
            items=items,
            next_cursor=str(offset + len(items)) if has_more else None,
        )

    def get_by_slug(self, slug: str, at: datetime) -> ContentRecord | None:
        row = self._store.get_by_slug(slug, published_only=False)
        if row is None or row.status not in {"published", "offline"}:
            return None
        if row.status == "published" and (
            row.publish_at is None or row.publish_at > at.astimezone(UTC)
        ):
            return None
        return _content_record(row)


class SqlAlchemyActivityPort:
    def __init__(self, store: SqlAlchemyActivityStore) -> None:
        self._store = store

    def list_activities(self, query: ActivityQuery) -> ActivityPage:
        offset = _cursor_offset(query.cursor)
        rows = self._store.list_activities(limit=query.limit, offset=offset)
        has_more = bool(rows) and bool(
            self._store.list_activities(limit=1, offset=offset + len(rows))
        )
        items = tuple(_activity_record(row) for row in rows)
        return ActivityPage(
            items=items,
            next_cursor=str(offset + len(items)) if has_more else None,
            updated_at=query.at.astimezone(UTC),
        )

    def get_activity(self, activity_id: str, at: datetime) -> ActivityRecord | None:
        del at
        row = self._store.get_activity(activity_id)
        return _activity_record(row) if row is not None else None

    def join(
        self, command: ActivityWriteCommand
    ) -> ActivityParticipationRecord | None:
        try:
            row = self._store.join_activity(
                command.activity_id,
                _account_id(command.account_id),
                expected_activity_revision=command.revision,
                idempotency_key=command.idempotency_key,
                now=command.at,
            )
        except ActivityNotFound:
            return None
        except ActivityRevisionConflict as exc:
            raise _revision_conflict(exc) from exc
        except ActivityIdempotencyConflict as exc:
            raise _idempotency_conflict(exc) from exc
        return _participation_record(row)

    def update_progress(
        self, command: ActivityWriteCommand
    ) -> ActivityParticipationRecord | None:
        try:
            row = self._store.update_progress(
                command.activity_id,
                _account_id(command.account_id),
                expected_revision=command.revision,
                progress=command.progress or 0,
                idempotency_key=command.idempotency_key,
                now=command.at,
            )
        except ActivityNotFound:
            return None
        except ActivityParticipationNotFound as exc:
            raise ActivityPortConflict(
                "participation_not_found",
                "Activity participation was not found",
            ) from exc
        except ActivityRevisionConflict as exc:
            raise _revision_conflict(exc) from exc
        except ActivityIdempotencyConflict as exc:
            raise _idempotency_conflict(exc) from exc
        return _participation_record(row)

    def leave(
        self, command: ActivityWriteCommand
    ) -> ActivityParticipationRecord | None:
        try:
            row = self._store.leave_activity(
                command.activity_id,
                _account_id(command.account_id),
                expected_revision=command.revision,
                idempotency_key=command.idempotency_key,
                now=command.at,
            )
        except ActivityNotFound:
            return None
        except ActivityParticipationNotFound as exc:
            raise ActivityPortConflict(
                "participation_not_found",
                "Activity participation was not found",
            ) from exc
        except ActivityRevisionConflict as exc:
            raise _revision_conflict(exc) from exc
        except ActivityIdempotencyConflict as exc:
            raise _idempotency_conflict(exc) from exc
        return _participation_record(row)

    def leaderboard(self, query: LeaderboardQuery) -> LeaderboardPage | None:
        offset = _cursor_offset(query.cursor)
        try:
            rows = self._store.leaderboard(query.activity_id, limit=100)
        except ActivityNotFound:
            return None
        selected = rows[offset : offset + query.limit]
        items = tuple(
            LeaderboardRecord(
                rank=offset + index,
                display_name="Learner",
                avatar_url=None,
                progress=row.progress,
                is_current_user=False,
            )
            for index, row in enumerate(selected, start=1)
        )
        next_offset = offset + len(items)
        return LeaderboardPage(
            items=items,
            next_cursor=str(next_offset) if next_offset < len(rows) else None,
            updated_at=query.at.astimezone(UTC),
        )


def _content_record(row) -> ContentRecord:
    assets = tuple(_asset_record(asset) for asset in row.assets)
    covers = tuple(asset for asset, source in zip(assets, row.assets) if source.role == "cover")
    body_assets = tuple(
        asset for asset, source in zip(assets, row.assets) if source.role == "body"
    )
    if row.publish_at is None:
        raise ValueError("Published and offline content must have publish_at")
    return ContentRecord(
        id=str(row.id),
        slug=row.slug,
        type=row.content_type,
        title=row.title,
        summary=row.summary,
        illustration_template=row.illustration_template,
        illustration_config=dict(row.illustration_config),
        cover=covers[0] if covers else None,
        publisher_name=row.publisher_name,
        published_at=row.publish_at,
        content_version=row.content_version,
        body_markdown=row.body_markdown,
        body_assets=body_assets,
        status=row.status,
    )


def _asset_record(row) -> AssetRecord:
    if row.width is None or row.height is None:
        raise ValueError("Published content assets require width and height")
    return AssetRecord(
        url=row.url,
        mime_type=row.mime_type,
        width=row.width,
        height=row.height,
        bytes=row.byte_size,
        sha256=row.sha256,
    )


def _activity_record(row) -> ActivityRecord:
    return ActivityRecord(
        id=row.slug,
        title=row.title,
        description=row.description,
        revision=row.revision,
        starts_at=row.starts_at,
        ends_at=row.ends_at,
        requires_online_confirmation=row.requires_online_confirmation,
        allows_deferred_progress=row.allows_deferred_progress,
        state=row.state,
        session_template_id=row.session_template_id,
    )


def _participation_record(row) -> ActivityParticipationRecord:
    return ActivityParticipationRecord(
        activity_id=row.activity_slug,
        user_id=str(row.account_id),
        joined=row.joined,
        progress=row.progress,
        revision=row.revision,
        state=row.state,
        idempotency_key=row.idempotency_key,
    )


def _account_id(value: str) -> UUID:
    try:
        return UUID(value)
    except ValueError as exc:
        raise ActivityPortConflict(
            "invalid_account_id",
            "Authenticated account ID is invalid",
        ) from exc


def _cursor_offset(cursor: str | None) -> int:
    try:
        return max(0, int(cursor or "0"))
    except ValueError as exc:
        raise ActivityPortConflict("invalid_cursor", "Cursor is invalid") from exc


def _revision_conflict(exc: ActivityRevisionConflict) -> ActivityPortConflict:
    return ActivityPortConflict(
        "revision_conflict",
        "Activity revision does not match the stored revision",
        details={"expectedRevision": exc.expected, "actualRevision": exc.actual},
    )


def _idempotency_conflict(exc: ActivityIdempotencyConflict) -> ActivityPortConflict:
    return ActivityPortConflict(
        "idempotency_key_reused",
        str(exc),
    )
