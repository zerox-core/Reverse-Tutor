from __future__ import annotations

from dataclasses import dataclass

from .content_activity_ports import ActivityPort, PublicContentPort
from .auth_routes import create_production_auth_service
from .auth_service import AuthService
from .sqlalchemy_content_activity import (
    SqlAlchemyActivityPort,
    SqlAlchemyPublicContentPort,
)
from online_db.activity_store import SqlAlchemyActivityStore
from online_db.auth_store import SqlAlchemyAuthStore
from online_db.content_store import SqlAlchemyContentStore
from online_db.idempotency_store import SqlAlchemyIdempotencyStore
from online_db.schema_check import assert_online_schema_at_head
from online_db.session import build_online_session_factory


@dataclass(frozen=True)
class PostgresOnlineServices:
    auth_service: AuthService
    content_port: PublicContentPort
    activity_port: ActivityPort


def build_postgres_auth_service(database_url: str) -> AuthService:
    assert_online_schema_at_head(database_url)
    session_factory = build_online_session_factory(database_url)
    return _auth_service(session_factory)


def build_postgres_content_activity_ports(
    database_url: str,
) -> tuple[PublicContentPort, ActivityPort]:
    assert_online_schema_at_head(database_url)
    session_factory = build_online_session_factory(database_url)
    return _content_activity_ports(session_factory)


def build_postgres_online_services(database_url: str) -> PostgresOnlineServices:
    assert_online_schema_at_head(database_url)
    session_factory = build_online_session_factory(database_url)
    content_port, activity_port = _content_activity_ports(session_factory)
    return PostgresOnlineServices(
        auth_service=_auth_service(session_factory),
        content_port=content_port,
        activity_port=activity_port,
    )


def _auth_service(session_factory) -> AuthService:
    return create_production_auth_service(
        auth_store=SqlAlchemyAuthStore(session_factory),
        idempotency_store=SqlAlchemyIdempotencyStore(session_factory),
    )


def _content_activity_ports(
    session_factory,
) -> tuple[PublicContentPort, ActivityPort]:
    return (
        SqlAlchemyPublicContentPort(SqlAlchemyContentStore(session_factory)),
        SqlAlchemyActivityPort(SqlAlchemyActivityStore(session_factory)),
    )
