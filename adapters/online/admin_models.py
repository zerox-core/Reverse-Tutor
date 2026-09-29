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

class AdminStageMaterialInput(CamelModel):
    model_config = ConfigDict(extra="forbid")

    title: str = Field(min_length=1, max_length=200)
    text: str = Field(min_length=1, max_length=20000)
    ref: str | None = Field(default=None, max_length=500)


class AdminStagePlanGenerateRequest(CamelModel):
    model_config = ConfigDict(extra="forbid")

    materials: list[AdminStageMaterialInput] = Field(min_length=1, max_length=20)
    temperature: float | None = Field(default=None, ge=0.0, le=2.0)


class AdminStageDraft(CamelModel):
    model_config = ConfigDict(extra="forbid")

    stage_index: int = Field(ge=1)
    name: str = Field(min_length=1, max_length=200)
    capability: str = Field(min_length=1, max_length=500)
    evidence_keys: list[str] = Field(min_length=1, max_length=20)
    task_day_numbers: list[int] = Field(min_length=1, max_length=365)
    evidence_level: Literal["evidenced", "inferred"]
    evidence_refs: list[str] = Field(default_factory=list, max_length=20)


class AdminStagePlanGenerateResponse(CamelModel):
    model_config = ConfigDict(extra="forbid")

    activity_slug: str
    total_days: int
    stages: list[AdminStageDraft]
    inferred_stage_indexes: list[int]
    persisted: bool = False


class AdminStagesConfirmRequest(CamelModel):
    model_config = ConfigDict(extra="forbid")

    stages: list[AdminStageDraft] = Field(min_length=1, max_length=50)


class AdminStagesResponse(CamelModel):
    model_config = ConfigDict(extra="forbid")

    activity_slug: str
    stages: list[AdminStageDraft]


ProbeKind = Literal["probe_recite", "probe_transfer", "probe_error"]


class AdminProbeRubric(CamelModel):
    model_config = ConfigDict(extra="forbid")

    pass_criteria: str = Field(alias="pass", min_length=1, max_length=2000)
    partial_criteria: str = Field(alias="partial", min_length=1, max_length=2000)
    fail_signals: list[str] = Field(min_length=1, max_length=10)


class AdminProbeItem(CamelModel):
    model_config = ConfigDict(extra="forbid")

    evidence_key: str = Field(min_length=1, max_length=120)
    kind: ProbeKind
    question: str = Field(min_length=1, max_length=4000)
    rubric: AdminProbeRubric
    followups: list[str] = Field(default_factory=list, max_length=3)


class AdminProbeStagePlan(CamelModel):
    model_config = ConfigDict(extra="forbid")

    stage_index: int = Field(ge=1)
    entry_question: str = Field(min_length=1, max_length=4000)
    probes: list[AdminProbeItem] = Field(min_length=1, max_length=20)


class AdminProbePlanGenerateRequest(CamelModel):
    model_config = ConfigDict(extra="forbid")

    temperature: float | None = Field(default=None, ge=0.0, le=2.0)


class AdminProbePlanGenerateResponse(CamelModel):
    model_config = ConfigDict(extra="forbid")

    activity_slug: str
    account_id: str
    stages: list[AdminProbeStagePlan]
    persisted: bool = False


class AdminProbePlanConfirmRequest(CamelModel):
    model_config = ConfigDict(extra="forbid")

    model: str = Field(min_length=1, max_length=120)
    stages: list[AdminProbeStagePlan] = Field(min_length=1, max_length=50)


class AdminProbeStagePlanRecord(AdminProbeStagePlan):
    stage_name: str
    capability: str
    model: str
    updated_at_epoch_millis: int


class AdminProbePlanResponse(CamelModel):
    model_config = ConfigDict(extra="forbid")

    activity_slug: str
    account_id: str
    stages: list[AdminProbeStagePlanRecord]
