from __future__ import annotations

import pytest

from online_db.schema_check import assert_online_schema_at_head, online_schema_head


def test_online_schema_head_is_single_frozen_revision():
    assert online_schema_head() == "0003_content_and_activities"


def test_online_schema_check_rejects_unversioned_database():
    with pytest.raises(RuntimeError, match="expected 0003_content_and_activities"):
        assert_online_schema_at_head("sqlite+pysqlite:///:memory:")
