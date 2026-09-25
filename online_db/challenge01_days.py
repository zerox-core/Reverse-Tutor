"""Parse challenge-01 daily task markdown files into structured day records.

Single source of truth for the 17-day challenge content:
activities/challenge-01-agent-app-dev/days/day-*.md
"""
from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path

_DAY_FILE = re.compile(r"^day-(\d{2})\.md$")
_FRONT_MATTER = re.compile(r"^---\r?\n(.*?)\r?\n---\r?\n(.*)$", re.S)
_FIELD = re.compile(r"^([a-z_]+):\s*(.*)$")


@dataclass(frozen=True)
class ChallengeDay:
    day_number: int
    title: str
    task_markdown: str
    stage_goal: str


def load_challenge01_days(days_dir: Path | str) -> tuple[ChallengeDay, ...]:
    days_dir = Path(days_dir)
    if not days_dir.is_dir():
        raise FileNotFoundError(f"days directory not found: {days_dir}")
    days: list[ChallengeDay] = []
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
        for required in ("day_number", "title", "stage_goal"):
            if not fields.get(required):
                raise ValueError(f"{path.name}: front-matter missing {required}")
        day_number = int(fields["day_number"])
        if day_number != int(name_match.group(1)):
            raise ValueError(
                f"{path.name}: day_number {day_number} does not match filename"
            )
        body = fm.group(2).strip()
        if not body:
            raise ValueError(f"{path.name}: empty task_markdown body")
        days.append(
            ChallengeDay(
                day_number=day_number,
                title=fields["title"],
                task_markdown=body,
                stage_goal=fields["stage_goal"],
            )
        )
    days.sort(key=lambda day: day.day_number)
    numbers = [day.day_number for day in days]
    if numbers != list(range(1, len(days) + 1)):
        raise ValueError(f"day numbers must be contiguous from 1, got {numbers}")
    return tuple(days)
