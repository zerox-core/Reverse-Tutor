from __future__ import annotations

from datetime import datetime
from uuid import UUID, uuid4

from sqlalchemy import (
    JSON,
    CheckConstraint,
    DateTime,
    ForeignKey,
    Index,
    Integer,
    String,
    Text,
    UniqueConstraint,
    Uuid,
    func,
    text,
)
from sqlalchemy.orm import Mapped, mapped_column

from online_db.base import OnlineBase


class ContentItem(OnlineBase):
    __tablename__ = "content_items"

    id: Mapped[UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid4)
    slug: Mapped[str] = mapped_column(String(255), nullable=False)
    content_type: Mapped[str] = mapped_column(String(32), nullable=False)
    title: Mapped[str] = mapped_column(String(80), nullable=False)
    summary: Mapped[str] = mapped_column(String(180), nullable=False)
    body_markdown: Mapped[str] = mapped_column(Text, nullable=False)
    illustration_template: Mapped[str] = mapped_column(String(128), nullable=False)
    illustration_config: Mapped[dict[str, object]] = mapped_column(
        JSON, nullable=False, default=dict
    )
    publisher_name: Mapped[str | None] = mapped_column(String(120))
    status: Mapped[str] = mapped_column(
        String(24), nullable=False, server_default=text("'draft'")
    )
    sort_order: Mapped[int] = mapped_column(
        Integer, nullable=False, server_default=text("0")
    )
    publish_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    offline_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    content_version: Mapped[int] = mapped_column(
        Integer, nullable=False, server_default=text("1")
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )

    __table_args__ = (
        UniqueConstraint("slug", name="uq_content_items_slug"),
        CheckConstraint(
            "content_type IN ('public_interest','announcement')",
            name="content_type",
        ),
        CheckConstraint(
            "status IN ('draft','scheduled','published','offline')",
            name="content_status",
        ),
        CheckConstraint("sort_order >= 0", name="sort_order"),
        CheckConstraint("content_version >= 1", name="content_version"),
        CheckConstraint(
            "status = 'draft' OR publish_at IS NOT NULL",
            name="publish_timestamp",
        ),
        CheckConstraint(
            "(status = 'offline' AND offline_at IS NOT NULL) OR "
            "(status <> 'offline' AND offline_at IS NULL)",
            name="offline_timestamp",
        ),
        CheckConstraint(
            "offline_at IS NULL OR publish_at IS NULL OR offline_at >= publish_at",
            name="offline_after_publish",
        ),
        Index(
            "ix_content_items_status_publish_at_sort_order",
            "status",
            "publish_at",
            "sort_order",
        ),
    )


class ContentAsset(OnlineBase):
    __tablename__ = "content_assets"

    id: Mapped[UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid4)
    content_id: Mapped[UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey(
            "content_items.id",
            name="fk_content_assets_content_id_content_items",
            ondelete="CASCADE",
        ),
        nullable=False,
    )
    role: Mapped[str] = mapped_column(String(24), nullable=False)
    position: Mapped[int] = mapped_column(
        Integer, nullable=False, server_default=text("0")
    )
    url: Mapped[str] = mapped_column(String(2048), nullable=False)
    mime_type: Mapped[str] = mapped_column(String(255), nullable=False)
    width: Mapped[int | None] = mapped_column(Integer)
    height: Mapped[int | None] = mapped_column(Integer)
    byte_size: Mapped[int | None] = mapped_column(Integer)
    sha256: Mapped[str | None] = mapped_column(String(64))
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )

    __table_args__ = (
        UniqueConstraint(
            "content_id",
            "role",
            "position",
            name="uq_content_assets_content_id_role_position",
        ),
        CheckConstraint("role IN ('cover','body')", name="asset_role"),
        CheckConstraint("position >= 0", name="asset_position"),
        CheckConstraint("width IS NULL OR width > 0", name="asset_width"),
        CheckConstraint("height IS NULL OR height > 0", name="asset_height"),
        CheckConstraint("byte_size IS NULL OR byte_size >= 0", name="asset_byte_size"),
        CheckConstraint(
            "sha256 IS NULL OR length(sha256) = 64",
            name="asset_sha256_length",
        ),
        Index("ix_content_assets_content_id_position", "content_id", "position"),
    )
