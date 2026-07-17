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
