from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
from uuid import UUID, uuid4

from sqlalchemy import select
from sqlalchemy.orm import Session, sessionmaker

from online_db.models import ContentAsset, ContentItem


@dataclass(frozen=True)
class ContentAssetInput:
    role: str
    position: int
    url: str
    mime_type: str
    width: int | None = None
    height: int | None = None
    byte_size: int | None = None
    sha256: str | None = None


@dataclass(frozen=True)
class ContentItemInput:
    slug: str
    content_type: str
    title: str
    summary: str
    body_markdown: str
    illustration_template: str
    illustration_config: dict[str, object]
    publisher_name: str | None = None
    sort_order: int = 0
    assets: tuple[ContentAssetInput, ...] = field(default_factory=tuple)


@dataclass(frozen=True)
class ContentAssetRecord:
    id: UUID
    role: str
    position: int
    url: str
    mime_type: str
    width: int | None
    height: int | None
    byte_size: int | None
    sha256: str | None


@dataclass(frozen=True)
class ContentItemRecord:
    id: UUID
    slug: str
    content_type: str
    title: str
    summary: str
    body_markdown: str
    illustration_template: str
    illustration_config: dict[str, object]
    publisher_name: str | None
    status: str
    sort_order: int
    publish_at: datetime | None
    offline_at: datetime | None
    content_version: int
    created_at: datetime
    updated_at: datetime
    assets: tuple[ContentAssetRecord, ...]


class ContentItemNotFound(LookupError):
    pass


class SqlAlchemyContentStore:
    def __init__(self, session_factory: sessionmaker[Session]) -> None:
        self._session_factory = session_factory

    def close(self) -> None:
        bind = self._session_factory.kw.get("bind")
        if bind is not None:
            bind.dispose()

    def create_item(self, item: ContentItemInput, now: datetime) -> ContentItemRecord:
        now = _utc_input(now)
        with self._session_factory() as database, database.begin():
            row = ContentItem(
                id=uuid4(),
                slug=item.slug,
                content_type=item.content_type,
                title=item.title,
                summary=item.summary,
                body_markdown=item.body_markdown,
                illustration_template=item.illustration_template,
                illustration_config=item.illustration_config,
                publisher_name=item.publisher_name,
                status="draft",
                sort_order=item.sort_order,
                content_version=1,
                created_at=now,
                updated_at=now,
            )
            database.add(row)
            database.flush()
            for asset in item.assets:
                database.add(
                    ContentAsset(
                        id=uuid4(),
                        content_id=row.id,
                        role=asset.role,
                        position=asset.position,
                        url=asset.url,
                        mime_type=asset.mime_type,
                        width=asset.width,
                        height=asset.height,
                        byte_size=asset.byte_size,
                        sha256=asset.sha256,
                        created_at=now,
                    )
                )
            database.flush()
            return self._record(database, row)

    def get_by_slug(
        self, slug: str, *, published_only: bool = True
    ) -> ContentItemRecord | None:
        with self._session_factory() as database:
            statement = select(ContentItem).where(ContentItem.slug == slug)
            if published_only:
                statement = statement.where(ContentItem.status == "published")
            row = database.scalar(statement)
            return self._record(database, row) if row is not None else None

    def list_published(
        self,
        now: datetime,
        *,
        content_types: tuple[str, ...] | None = None,
        limit: int = 20,
        offset: int = 0,
    ) -> tuple[ContentItemRecord, ...]:
        now = _utc_input(now)
        if not 1 <= limit <= 50:
            raise ValueError("limit must be between 1 and 50")
        if offset < 0:
            raise ValueError("offset must be non-negative")
        statement = (
            select(ContentItem)
            .where(
                ContentItem.status == "published",
                ContentItem.publish_at.is_not(None),
                ContentItem.publish_at <= now,
            )
            .order_by(
                ContentItem.sort_order.asc(),
                ContentItem.publish_at.desc(),
                ContentItem.id.asc(),
            )
            .offset(offset)
            .limit(limit)
        )
        if content_types:
            statement = statement.where(ContentItem.content_type.in_(content_types))
        with self._session_factory() as database:
            return tuple(
                self._record(database, row)
                for row in database.scalars(statement).all()
            )

    def publish(self, content_id: UUID, now: datetime) -> ContentItemRecord:
        now = _utc_input(now)
        with self._session_factory() as database, database.begin():
            row = database.get(ContentItem, content_id, with_for_update=True)
            if row is None:
                raise ContentItemNotFound(str(content_id))
            row.status = "published"
            row.publish_at = now
            row.offline_at = None
            row.updated_at = now
            database.flush()
            return self._record(database, row)

    def schedule(
        self, content_id: UUID, publish_at: datetime, now: datetime
    ) -> ContentItemRecord:
        publish_at = _utc_input(publish_at)
        now = _utc_input(now)
        if publish_at <= now:
            raise ValueError("Scheduled publication must be in the future")
        with self._session_factory() as database, database.begin():
            row = database.get(ContentItem, content_id, with_for_update=True)
            if row is None:
                raise ContentItemNotFound(str(content_id))
            row.status = "scheduled"
            row.publish_at = publish_at
            row.offline_at = None
            row.updated_at = now
            database.flush()
            return self._record(database, row)

    def take_offline(self, content_id: UUID, now: datetime) -> ContentItemRecord:
        now = _utc_input(now)
        with self._session_factory() as database, database.begin():
            row = database.get(ContentItem, content_id, with_for_update=True)
            if row is None:
                raise ContentItemNotFound(str(content_id))
            if row.publish_at is None:
                raise ValueError("Draft content cannot be taken offline")
            row.status = "offline"
            row.offline_at = now
            row.updated_at = now
            database.flush()
            return self._record(database, row)

    @staticmethod
    def _record(database: Session, row: ContentItem) -> ContentItemRecord:
        assets = database.scalars(
            select(ContentAsset)
            .where(ContentAsset.content_id == row.id)
            .order_by(ContentAsset.position.asc(), ContentAsset.id.asc())
        ).all()
        return ContentItemRecord(
            id=row.id,
            slug=row.slug,
            content_type=row.content_type,
            title=row.title,
            summary=row.summary,
            body_markdown=row.body_markdown,
            illustration_template=row.illustration_template,
            illustration_config=dict(row.illustration_config),
            publisher_name=row.publisher_name,
            status=row.status,
            sort_order=row.sort_order,
            publish_at=_optional_utc(row.publish_at),
            offline_at=_optional_utc(row.offline_at),
            content_version=row.content_version,
            created_at=_as_utc(row.created_at),
            updated_at=_as_utc(row.updated_at),
            assets=tuple(
                ContentAssetRecord(
                    id=asset.id,
                    role=asset.role,
                    position=asset.position,
                    url=asset.url,
                    mime_type=asset.mime_type,
                    width=asset.width,
                    height=asset.height,
                    byte_size=asset.byte_size,
                    sha256=asset.sha256,
                )
                for asset in assets
            ),
        )


def _optional_utc(value: datetime | None) -> datetime | None:
    return _as_utc(value) if value is not None else None


def _as_utc(value: datetime) -> datetime:
    if value.tzinfo is None or value.utcoffset() is None:
        return value.replace(tzinfo=timezone.utc)
    return value.astimezone(timezone.utc)


def _utc_input(value: datetime) -> datetime:
    if value.tzinfo is None or value.utcoffset() is None:
        raise ValueError("datetime values must be timezone-aware")
    return value.astimezone(timezone.utc)
