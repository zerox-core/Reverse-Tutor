def test_online_database_dependencies_are_importable():
    import alembic
    import cryptography
    import jwt
    import psycopg
    import yaml

    assert alembic.__version__
    assert cryptography.__version__
    assert jwt.__version__
    assert psycopg.__version__
    assert yaml.__version__


def test_postgres_content_activity_ports_share_the_configured_session_factory(
    monkeypatch,
):
    from adapters.online import dependencies
    from adapters.online.sqlalchemy_content_activity import (
        SqlAlchemyActivityPort,
        SqlAlchemyPublicContentPort,
    )

    session_factory = object()
    checked = []
    monkeypatch.setenv("ONLINE_AUTH_SIGNING_KEY", "s" * 32)
    monkeypatch.setenv("ONLINE_AUTH_REFRESH_PEPPER", "p" * 32)
    monkeypatch.setenv("ONLINE_AUTH_IDEMPOTENCY_SEALING_KEY", "e" * 32)
    monkeypatch.setattr(
        dependencies,
        "assert_online_schema_at_head",
        checked.append,
    )
    monkeypatch.setattr(
        dependencies,
        "build_online_session_factory",
        lambda database_url: session_factory,
    )

    services = dependencies.build_postgres_online_services(
        "postgresql+psycopg://online"
    )

    assert checked == ["postgresql+psycopg://online"]
    assert isinstance(services.content_port, SqlAlchemyPublicContentPort)
    assert isinstance(services.activity_port, SqlAlchemyActivityPort)
    assert services.content_port._store._session_factory is session_factory
    assert services.activity_port._store._session_factory is session_factory
    assert services.auth_service.auth_store._session_factory is session_factory
    assert services.auth_service.idempotency_store._session_factory is session_factory
