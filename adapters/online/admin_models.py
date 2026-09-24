from __future__ import annotations

from typing import Literal

from pydantic import ConfigDict, Field, model_validator

from .models import CamelModel


class AdminActivityTaskInput(CamelModel):
    model_config = ConfigDict(extra="forbid")

    day_number: int = Field(ge=1)
    title: str = Field(min_length=1, max_length=120)
    task_markdown: str = Field(min_length=1, max_length=20000)
    stage_goal: str | None = Field(default=None, max_length=500)


class AdminActivityCreateRequest(CamelModel):
    model_config = ConfigDict(extra="forbid")

    slug: str = Field(min_length=1, max_length=80, pattern=r"^[a-z0-9][a-z0-9-]*$")
    title: str = Field(min_length=1, max_length=120)
    description: str = Field(min_length=1, max_length=5000)
    total_days: int = Field(ge=1, le=365)
    starts_at_epoch_millis: int = Field(ge=0)
    ends_at_epoch_millis: int = Field(ge=0)
    requires_online_confirmation: bool = True
    allows_deferred_progress: bool = True
    session_template_id: str | None = Field(default=None, max_length=120)
    public_feedback_summary: str | None = Field(default=None, max_length=2000)
    tasks: list[AdminActivityTaskInput] = Field(default_factory=list, max_length=365)

    @model_validator(mode="after")
    def _validate_window_and_tasks(self) -> "AdminActivityCreateRequest":
        if self.ends_at_epoch_millis <= self.starts_at_epoch_millis:
            raise ValueError(
                "endsAtEpochMillis must be greater than startsAtEpochMillis"
            )
        day_numbers = [task.day_number for task in self.tasks]
        if len(set(day_numbers)) != len(day_numbers):
            raise ValueError("Task dayNumber values must be unique")
        if any(day > self.total_days for day in day_numbers):
            raise ValueError("Task dayNumber cannot exceed totalDays")
        return self


class AdminActivityTask(CamelModel):
    model_config = ConfigDict(extra="forbid")

    day_number: int
    title: str
    task_markdown: str
    stage_goal: str | None


class AdminActivity(CamelModel):
    model_config = ConfigDict(extra="forbid")

    id: str
    slug: str
    title: str
    description: str
    revision: int
    rule_version: int
    total_days: int
    starts_at_epoch_millis: int
    ends_at_epoch_millis: int
    requires_online_confirmation: bool
    allows_deferred_progress: bool
    state: Literal["scheduled", "active", "closed", "offline"]
    session_template_id: str | None
    public_feedback_summary: str | None
    tasks: list[AdminActivityTask]


class AdminActivityListResponse(CamelModel):
    model_config = ConfigDict(extra="forbid")

    items: list[AdminActivity]
    next_cursor: str | None
    updated_at_epoch_millis: int
