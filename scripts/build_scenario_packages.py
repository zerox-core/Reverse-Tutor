"""Regenerate mobile-native scenario packages from the activity content packages.

Single source of truth: ``activities/<slug>/``（与 build_challenge02_tasks.py 同范式）
Output (frozen, generated): ``mobile-native/feature/reversetutor/src/main/assets/scenarios/<slug>.json``

Run from the repository root::

    py -m scripts.build_scenario_packages
    py -m scripts.build_scenario_packages --check   # 只校验冻结产物是否与真源同步

当前只导出「素材注入」所需的字段（标题/正文/来源/对应天数），供 native
MaterialStore 消费；学生卡与 rubric 尚未有确认的映射规则，暂不导出。
"""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Sequence

DEFAULT_ACTIVITIES_DIR = Path("activities")
DEFAULT_OUTPUT_DIR = Path(
    "mobile-native/feature/reversetutor/src/main/assets/scenarios"
)

# 首个打通端到端的场景包（2026-10-14 用户拍板：challenge-02 打头）
DEFAULT_SLUGS = ("challenge-02-gamer-health",)

_FRONT_MATTER = re.compile(r"^---\n(.*?)\n---\n", re.S)
_FIELD = re.compile(r"^([^:]+):\s*(.*)$")


def _parse_front_matter(text: str) -> dict[str, str]:
    match = _FRONT_MATTER.match(text)
    if match is None:
        return {}
    fields: dict[str, str] = {}
    for line in match.group(1).splitlines():
        field_match = _FIELD.match(line.strip())
        if field_match is not None:
            fields[field_match.group(1).strip()] = field_match.group(2).strip()
    return fields


def load_sources(package_dir: Path) -> list[dict[str, str]]:
    """读取 ``sources/src-*.md``：正文取 front-matter 之后的全部内容。"""
    sources_dir = package_dir / "sources"
    if not sources_dir.is_dir():
        return []
    docs: list[dict[str, str]] = []
    for path in sorted(sources_dir.glob("src-*.md")):
        text = path.read_text(encoding="utf-8")
        fields = _parse_front_matter(text)
        front_matter = _FRONT_MATTER.match(text)
        body = text[front_matter.end():] if front_matter else text
        body = body.strip()
        if not body:
            continue
        docs.append(
            {
                "docId": path.stem,
                "title": fields.get("标题", path.stem),
                "content": body,
                "source": fields.get("来源", ""),
                "url": fields.get("原始URL", ""),
                "day": fields.get("对应挑战天数", ""),
            }
        )
    return docs


def render_package(activities_dir: Path, slug: str) -> str:
    package_dir = activities_dir / slug
    if not package_dir.is_dir():
        raise FileNotFoundError(f"activity package not found: {package_dir}")
    payload = {
        "scenarioId": slug,
        "sources": load_sources(package_dir),
    }
    return json.dumps(payload, ensure_ascii=False, indent=2) + "\n"


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--activities-dir", type=Path, default=DEFAULT_ACTIVITIES_DIR)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--slug", action="append", dest="slugs", default=None)
    parser.add_argument(
        "--check",
        action="store_true",
        help="only verify frozen packages are in sync, do not write",
    )
    args = parser.parse_args(argv)

    slugs = tuple(args.slugs) if args.slugs else DEFAULT_SLUGS
    exit_code = 0
    for slug in slugs:
        output = args.output_dir / f"{slug}.json"
        rendered = render_package(args.activities_dir, slug)
        if args.check:
            if not output.is_file():
                print(f"{output} is missing")
                exit_code = 1
            elif output.read_text(encoding="utf-8") != rendered:
                print(f"{output} is out of sync with activities/{slug}")
                exit_code = 1
            else:
                print(f"{output} is in sync")
            continue
        args.output_dir.mkdir(parents=True, exist_ok=True)
        output.write_text(rendered, encoding="utf-8", newline="\n")
        print(f"wrote {output} from activities/{slug}")
    return exit_code


if __name__ == "__main__":  # pragma: no cover
    raise SystemExit(main())
