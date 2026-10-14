"""场景包导出（activities → mobile-native assets）的一致性测试。

范式与 `build_challenge02_tasks.py` 一致：活动包目录是唯一真源，
mobile-native 侧的 JSON 是冻结生成产物，必须有测试断言两者同步。
"""
from __future__ import annotations

import json

from scripts.build_scenario_packages import (
    DEFAULT_SLUGS,
    load_sources,
    render_package,
)
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
ACTIVITIES_DIR = REPO_ROOT / "activities"
OUTPUT_DIR = REPO_ROOT / "mobile-native/feature/reversetutor/src/main/assets/scenarios"
CHALLENGE02 = "challenge-02-gamer-health"


def test_frozen_package_in_sync_with_activity_sources():
    """冻结产物必须与 activities/ 真源逐字节一致（改真源就要重新生成）。"""
    output = OUTPUT_DIR / f"{CHALLENGE02}.json"
    assert output.is_file(), f"frozen scenario package missing: {output}"
    assert output.read_text(encoding="utf-8") == render_package(
        ACTIVITIES_DIR, CHALLENGE02
    )


def test_package_exports_every_source_document():
    package_dir = ACTIVITIES_DIR / CHALLENGE02
    expected = sorted((package_dir / "sources").glob("src-*.md"))
    payload = json.loads(render_package(ACTIVITIES_DIR, CHALLENGE02))
    assert payload["scenarioId"] == CHALLENGE02
    assert len(payload["sources"]) == len(expected) == 11
    assert [doc["docId"] for doc in payload["sources"]] == [
        path.stem for path in expected
    ]


def test_source_documents_carry_title_and_nonempty_body():
    for doc in json.loads(render_package(ACTIVITIES_DIR, CHALLENGE02))["sources"]:
        assert doc["title"].strip(), doc["docId"]
        assert len(doc["content"]) > 200, doc["docId"]
        # front-matter 不进正文：标题字段与分隔线都不应残留
        assert not doc["content"].startswith("---"), doc["docId"]
        assert "原始URL:" not in doc["content"], doc["docId"]


def test_source_documents_keep_provenance_fields():
    docs = {d["docId"]: d for d in load_sources(ACTIVITIES_DIR / CHALLENGE02)}
    first = docs["src-01-体育总局-久坐与碎片化微运动"]
    assert first["source"]
    assert first["url"].startswith("http")
    assert first["day"].startswith("D")


def test_default_slugs_cover_challenge02():
    assert CHALLENGE02 in DEFAULT_SLUGS
