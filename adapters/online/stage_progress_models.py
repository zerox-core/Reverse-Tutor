from __future__ import annotations

from typing import Literal

from pydantic import ConfigDict, Field

from .models import CamelModel

# Mirrors online_db.models.stage.EVIDENCE_EVENT_KINDS; declared explicitly so
# the OpenAPI schema pins the allowed values.
EvidenceKind = Literal[
    "baseline_probe",
    "probe_recite",
    "probe_transfer",
    "probe_error",
    "artifact",
    "manual",
]


class StageEvidenceRecordRequest(CamelModel):
    model_config = ConfigDict(extra="forbid")

    device_id: str = Field(min_length=1, max_length=120)
    stage_index: int = Field(ge=1)
    evidence_key: str = Field(min_length=1, max_length=120)
    kind: EvidenceKind
    artifact_ref: str = Field(min_length=1, max_length=500)
    path_tag: str = Field(default="in_order", min_length=1, max_length=40)
    idempotency_key: str = Field(min_length=1, max_length=128)


class StageProgressItemModel(CamelModel):
    model_config = ConfigDict(extra="forbid")

    stage_index: int
    name: str
    required_keys: list[str]
    satisfied_keys: list[str]
    complete: bool


class StageProgressResponse(CamelModel):
    model_config = ConfigDict(extra="forbid")

    activity_slug: str
    stage_done: int
    current_stage_index: int
    stages: list[StageProgressItemModel]
    progress: int
    revision: int
    updated_at_epoch_millis: int
