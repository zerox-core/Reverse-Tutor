"""Parse challenge-02 daily task markdown files into structured day records.

Single source of truth for the 7-day challenge content:
activities/challenge-02-gamer-health/days/day-*.md
"""
from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path

_DAY_FILE = re.compile(r"^day-(\d{2})\.md$")
_FRONT_MATTER = re.compile(r"^---\r?\n(.*?)\r?\n---\r?\n(.*)$", re.S)
_FIELD = re.compile(r"^([a-z_]+):\s*(.*)$")

# module -> day numbers (stage ladder S1/S2/S3)
MODULE_DAYS = {"S1": (1, 2), "S2": (3, 4, 5), "S3": (6, 7)}


@dataclass(frozen=True)
class Challenge02Day:
    day_number: int
    title: str
    task_markdown: str
    stage_goal: str
    module: str


def load_challenge02_days(days_dir: Path | str) -> tuple[Challenge02Day, ...]:
    days_dir = Path(days_dir)
    if not days_dir.is_dir():
        raise FileNotFoundError(f"days directory not found: {days_dir}")
    days: list[Challenge02Day] = []
    for path in sorted(days_dir.glob("day-*.md")):
        name_match = _DAY_FILE.match(path.name)
        if name_match is None:
            continue
        text = path.read_text(encoding="utf-8")
        fm = _FRONT_MATTER.match(text)
        if fm is None:
            raise ValueError(f"{path.name}: missing front-matter")
        fields: dict[str, str] = {}
        for line in fm.group(1).splitlines():
            field_match = _FIELD.match(line.strip())
            if field_match is not None:
                fields[field_match.group(1)] = field_match.group(2).strip()
        for required in ("day_number", "title", "stage_goal", "module"):
            if not fields.get(required):
                raise ValueError(f"{path.name}: front-matter missing {required}")
        day_number = int(fields["day_number"])
        if day_number != int(name_match.group(1)):
            raise ValueError(
                f"{path.name}: day_number {day_number} does not match filename"
            )
        module = fields["module"]
        if module not in MODULE_DAYS:
            raise ValueError(f"{path.name}: unknown module {module!r}")
        if day_number not in MODULE_DAYS[module]:
            raise ValueError(
                f"{path.name}: day {day_number} does not belong to module {module}"
            )
        body = fm.group(2).strip()
        if not body:
            raise ValueError(f"{path.name}: empty task_markdown body")
        days.append(
            Challenge02Day(
                day_number=day_number,
                title=fields["title"],
                task_markdown=body,
                stage_goal=fields["stage_goal"],
                module=module,
            )
        )
    days.sort(key=lambda day: day.day_number)
    numbers = [day.day_number for day in days]
    if numbers != list(range(1, len(days) + 1)):
        raise ValueError(f"day numbers must be contiguous from 1, got {numbers}")
    return tuple(days)
