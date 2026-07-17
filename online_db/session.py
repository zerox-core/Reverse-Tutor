from __future__ import annotations

from sqlalchemy import Engine, create_engine
from sqlalchemy.orm import Session, sessionmaker


def build_online_engine(database_url: str) -> Engine:
    return create_engine(database_url, pool_pre_ping=True, future=True)


def build_online_session_factory(database_url: str) -> sessionmaker[Session]:
    return sessionmaker(
        bind=build_online_engine(database_url),
        class_=Session,
        autoflush=False,
        expire_on_commit=False,
    )
