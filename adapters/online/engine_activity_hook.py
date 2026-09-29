"""engine.py 会话侧的活动接线（探针计划注入 + 会话内证据写回）。

engine.py 只认鸭子类型 hook（``render_block`` / ``record_turn_evidence``），
本模块用 ``SqlAlchemyStageStore`` + ``SqlAlchemyProbePlanStore`` 实现它，
由 server.py 在 lifespan 里注册进 engine（``engine.set_activity_hook``）。

- ``render_block``：把该参与者的定稿探针计划渲染成系统提示注入块
  （复用 ``render_probe_plan_block`` 的存档形态）；
- ``record_turn_evidence``：把 LLM 判定的 (stage_index, evidence_key)
  对照存档计划校验后写回 ``activity_evidence_events``——kind 取自计划
  （不是 LLM 输出），幂等键按「会话 + 消息 + 阶段 + 证据键」确定，
  重复写同键或同内容安全返回；已存在不同内容的证据（StageEvidenceConflict）
  保留首记、不报错。
"""
from __future__ import annotations

from datetime import datetime, timezone
from typing import Callable
from uuid import UUID

from online_db.probe_plan_prompts import (
    ProbeItem,
    ProbePlan,
    ProbeStagePlan,
    render_probe_plan_block,
)
from online_db.probe_plan_store import SqlAlchemyProbePlanStore
from online_db.stage_store import SqlAlchemyStageStore, StageEvidenceConflict


class EngineActivitySessionHook:
    def __init__(
        self,
        stage_store: SqlAlchemyStageStore,
        probe_plan_store: SqlAlchemyProbePlanStore,
        *,
        clock: Callable[[], datetime] | None = None,
    ) -> None:
        self._stage_store = stage_store
        self._probe_plan_store = probe_plan_store
        self._clock = clock or (lambda: datetime.now(timezone.utc))

    def render_block(self, *, activity_slug: str, account_id: str) -> str:
        """渲染该参与者定稿探针计划的注入块；无计划时抛 ProbePlanNotFound。"""
        records = self._probe_plan_store.list_stage_plans(
            activity_slug, UUID(account_id)
        )
        stages = self._stage_store.list_activity_stages(activity_slug)
        plan = ProbePlan(
            stages=tuple(
                ProbeStagePlan(
                    stage_index=record.stage_index,
                    entry_question=record.entry_question,
                    probes=tuple(
                        ProbeItem(
                            evidence_key=probe.evidence_key,
                            kind=probe.kind,
                            question=probe.question,
                            rubric=probe.rubric,
                            followups=probe.followups,
                        )
                        for probe in record.probes
                    ),
                )
                for record in records
            )
        )
        return render_probe_plan_block(plan, stages)

    def record_turn_evidence(
        self,
        *,
        activity_slug: str,
        account_id: str,
        session_id: str,
        message_id: int,
        stage_index: int,
        evidence_key: str,
    ) -> str:
        """把一轮判定的阶段证据写回活动证据事件，返回简短说明。

        校验：(stage_index, evidence_key) 必须在存档探针计划内；kind 取自
        计划条目。已记录过同内容证据时幂等返回；存在不同内容的首记证据时
        保留首记（吞掉 StageEvidenceConflict）。
        """
        account = UUID(account_id)
        record = self._probe_plan_store.get_stage_plan(
            activity_slug, account, stage_index=stage_index
        )
        probe = next(
            (p for p in record.probes if p.evidence_key == evidence_key), None
        )
        if probe is None:
            raise ValueError(
                f"evidence_key {evidence_key!r} is not in the probe plan of"
                f" stage {stage_index}"
            )
        idempotency_key = (
            f"engine:{session_id}:{message_id}:{stage_index}:{evidence_key}"
        )[:255]
        try:
            self._stage_store.record_evidence(
                activity_slug,
                account,
                stage_index=stage_index,
                evidence_key=evidence_key,
                kind=probe.kind,
                artifact_ref=f"session:{session_id}:message:{message_id}",
                idempotency_key=idempotency_key,
                now=self._clock(),
            )
        except StageEvidenceConflict:
            return (
                f"证据 {evidence_key}@阶段{stage_index} 已有不同内容的首记，"
                "保留首记"
            )
        return f"已记录证据 {evidence_key}@阶段{stage_index}（{probe.kind}）"
