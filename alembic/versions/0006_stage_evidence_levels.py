# -*- coding: utf-8 -*-
"""Persist stage confidence levels on activity stages (design draft v0.1 §2.3).

The material-driven stage generator marks every stage ``evidenced`` or
``inferred`` and records its supporting references so later edits can follow
the principle of adjusting inferred stages first.  Existing seed rows (the
hand-authored challenge-01 baseline) default to ``evidenced``.

Revision ID: 0006_stage_evidence_levels
Revises: 0005_probe_plan_and_profiles
Create Date: 2026-09-29
"""

from __future__ import annotations

from typing import Sequence

from alembic import op
import sqlalchemy as sa


revision: str = "0006_stage_evidence_levels"
down_revision: str | None = "0005_probe_plan_and_profiles"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


_STAGE_EVIDENCE_LEVELS = "'evidenced','inferred'"


def upgrade() -> None:
    with op.batch_alter_table("activity_stages") as batch:
        batch.add_column(
            sa.Column(
                "evidence_level",
                sa.String(length=16),
                server_default=sa.text("'evidenced'"),
                nullable=False,
            )
        )
        batch.add_column(
            sa.Column(
                "evidence_refs",
                sa.JSON(),
                server_default=sa.text("'[]'"),
                nullable=False,
            )
        )
        batch.create_check_constraint(
            "ck_activity_stages_stage_evidence_level",
            f"evidence_level IN ({_STAGE_EVIDENCE_LEVELS})",
        )


def downgrade() -> None:
    with op.batch_alter_table("activity_stages") as batch:
        batch.drop_constraint("ck_activity_stages_stage_evidence_level", type_="check")
        batch.drop_column("evidence_refs")
        batch.drop_column("evidence_level")
