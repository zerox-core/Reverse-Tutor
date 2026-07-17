from __future__ import annotations

from .auth_routes import create_production_auth_service
from .auth_service import AuthService
from online_db.auth_store import SqlAlchemyAuthStore
from online_db.idempotency_store import SqlAlchemyIdempotencyStore
from online_db.schema_check import assert_online_schema_at_head
from online_db.session import build_online_session_factory


def build_postgres_auth_service(database_url: str) -> AuthService:
    assert_online_schema_at_head(database_url)
    session_factory = build_online_session_factory(database_url)
    return create_production_auth_service(
        auth_store=SqlAlchemyAuthStore(session_factory),
        idempotency_store=SqlAlchemyIdempotencyStore(session_factory),
    )
