"""探针计划的 prompt、校验与会话策略注入块（spec §11.2 / §11.3 / §11.4）。

落地形态（朱曦策拍板「全自动证据判定」）：探针计划 + 判定 rubric
在**会话创建时**由 LLM 生成、随会话策略一起定稿——证据不事后补判，
而是提前编入会话策略。本模块提供：

1. ``PROBE_PLAN_SYSTEM_PROMPT`` + ``build_probe_plan_user_prompt``：生成探针计划的提示词；
2. ``validate_probe_plan`` / ``validate_stage_plan``：LLM 输出的严格校验（越界即抛 ``ProbePlanValidationError``）；
3. ``render_probe_plan_block``：把定稿计划渲染成可注入会话策略的文本块；
4. ``generate_probe_plan``：以注入 ``chat_json`` 可调用的方式产出已校验计划（不在本层 import llm）。

探针三型与 ``online_db.models.stage.EVIDENCE_EVENT_KINDS`` 对齐：
``probe_recite``（复述）/ ``probe_transfer``（迁移）/ ``probe_error``（挑错）；
入口问题（baseline）走 ``baseline_probe`` 证据 kind，体现在每个阶段的
``entry_question`` 字段。
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Awaitable, Callable, Mapping, Sequence

from online_db.stage_store import ActivityStageRecord


PROBE_KINDS: tuple[str, ...] = ("probe_recite", "probe_transfer", "probe_error")
MAX_FOLLOWUP_PROBES = 3

ChatJsonCallable = Callable[..., Awaitable[dict[str, Any]]]


PROBE_PLAN_SYSTEM_PROMPT = """你是一个课程设计师，为一个分阶段学习活动生成「探针计划」与判定口径（rubric）。这份计划会在会话创建时随会话策略一起定稿，之后由会话算法按学生缺口驱动、一次只取一个探针使用——它是一个备选库，不是按顺序抛出的题库。

# 你的任务
1. 为每个阶段写一个「入口问题」(entry_question)：场景化的开场问题，不是知识宣讲；它要在学生未经教学时暴露其原始状态（baseline），并自然引出阶段内容。
2. 为该阶段的每个验收证据键 (evidence_key) 至少设计一个探针 (probes)。探针三型只能选其一：
   - "probe_recite" 复述型：让学生用自己的话把内容讲回来；
   - "probe_transfer" 迁移型：换一个新场景让学生把方法再用一遍；
   - "probe_error" 挑错型：给出一段你故意埋了错的解答，让学生指出错在哪、为什么错。
3. 每个探针附「判定口径」rubric（这是全自动证据判定的依据，必须机器可判）：
   - "pass"：通过判据——学生回答必须覆盖的具体要点 / 关键概念 / 逻辑步骤；
   - "partial"：部分通过判据——答对了核心但明显缺一块时的认定口径；
   - "fail_signals"：判定不过的具体信号，3~5 条，每条写成一个可识别的模式（如"只重复题面没有自己的话"）。
4. 每个探针附至多 3 条「追问」(followups)：当 rubric 判定存疑时逐条使用，3 轮仍存疑则挂待人工确认、不阻塞阶段推进。

# 规则
- 探针缺口驱动、一次一个：rubric 之外的追问只是备选，不代表连发。
- 问题必须具体到能自动判定：避免"谈谈你的理解""说说你的想法"这类空泛问法。
- 挑错型探针必须在 question 中把埋错的解答完整写出来（错误要真实常见，不要低级笔误）。
- 严格遵守各阶段给出的 evidence_key，不许编造新的证据键。

# 输出格式（严格 JSON，禁止 markdown、注释与额外文字）
{
  "stages": [
    {
      "stage_index": 1,
      "entry_question": "场景化入口问题原文",
      "probes": [
        {
          "evidence_key": "explain",
          "kind": "probe_recite",
          "question": "探针问题原文",
          "rubric": {"pass": "...", "partial": "...", "fail_signals": ["...", "..."]},
          "followups": ["..."]
        }
      ]
    }
  ]
}
"""


@dataclass(frozen=True)
class ProbeRubric:
    pass_criteria: str
    partial_criteria: str
    fail_signals: tuple[str, ...]


@dataclass(frozen=True)
class ProbeItem:
    evidence_key: str
    kind: str
    question: str
    rubric: ProbeRubric
    followups: tuple[str, ...]


@dataclass(frozen=True)
class ProbeStagePlan:
    stage_index: int
    entry_question: str
    probes: tuple[ProbeItem, ...]


@dataclass(frozen=True)
class ProbePlan:
    stages: tuple[ProbeStagePlan, ...]


class ProbePlanValidationError(ValueError):
    pass


def build_probe_plan_user_prompt(
    stages: Sequence[ActivityStageRecord],
    *,
    activity_title: str,
    activity_description: str = "",
) -> str:
    """构造探针计划生成的 user prompt（输入 = 已定稿的阶段阶梯）。"""
    lines: list[str] = [f"活动：{activity_title}"]
    if activity_description and activity_description.strip():
        lines.append(activity_description.strip())
    lines.append("")
    lines.append("阶段定义：")
    for stage in stages:
        keys = ", ".join(stage.evidence_keys)
        lines.append(f"## 阶段 {stage.stage_index} · {stage.name}")
        lines.append(f"能力目标：{stage.capability}")
        lines.append(f"验收证据键：{keys}")
    lines.append("")
    lines.append("请按系统指令输出覆盖以上全部阶段的完整探针计划 JSON。")
    return "\n".join(lines)


async def generate_probe_plan(
    stages: Sequence[ActivityStageRecord],
    *,
    activity_title: str,
    activity_description: str = "",
    chat_json: ChatJsonCallable,
    temperature: float = 0.3,
    max_tokens: int = 6000,
) -> ProbePlan:
    """调用注入的 chat_json 生成并校验探针计划。

    生产侧注入 ``llm.chat_json``（online_db 保持纯持久层、不 import llm）。
    """
    user_prompt = build_probe_plan_user_prompt(
        stages,
        activity_title=activity_title,
        activity_description=activity_description,
    )
    payload = await chat_json(
        PROBE_PLAN_SYSTEM_PROMPT,
        [{"role": "user", "content": user_prompt}],
        temperature=temperature,
        max_tokens=max_tokens,
    )
    return validate_probe_plan(payload, stages)


def validate_probe_plan(
    payload: Any, stages: Sequence[ActivityStageRecord]
) -> ProbePlan:
    """校验整份 LLM 输出：必须不多不少覆盖全部已定义阶段。"""
    if not isinstance(payload, Mapping):
        raise ProbePlanValidationError("probe plan payload must be a JSON object")
    raw_stages = payload.get("stages")
    if not isinstance(raw_stages, list) or not raw_stages:
        raise ProbePlanValidationError('probe plan payload needs a "stages" list')
    defined = {stage.stage_index: stage for stage in stages}
    seen: dict[int, ProbeStagePlan] = {}
    for raw in raw_stages:
        if not isinstance(raw, Mapping):
            raise ProbePlanValidationError("each stage plan must be a JSON object")
        stage_index = raw.get("stage_index")
        if not isinstance(stage_index, int) or isinstance(stage_index, bool):
            raise ProbePlanValidationError("stage_index must be an integer")
        stage_record = defined.get(stage_index)
        if stage_record is None:
            raise ProbePlanValidationError(f"unknown stage_index {stage_index}")
        if stage_index in seen:
            raise ProbePlanValidationError(f"duplicate stage_index {stage_index}")
        seen[stage_index] = validate_stage_plan(raw, stage_record)
    missing = sorted(set(defined) - set(seen))
    if missing:
        raise ProbePlanValidationError(f"probe plan misses stages: {missing}")
    return ProbePlan(
        stages=tuple(seen[index] for index in sorted(seen)),
    )


def validate_stage_plan(payload: Any, stage: ActivityStageRecord) -> ProbeStagePlan:
    """校验单个阶段的计划：入口问题 + 每个证据键至少一个探针。"""
    if not isinstance(payload, Mapping):
        raise ProbePlanValidationError("stage plan must be a JSON object")
    stage_index = payload.get("stage_index")
    if stage_index != stage.stage_index:
        raise ProbePlanValidationError(
            f"stage plan stage_index {stage_index!r} does not match stage"
            f" {stage.stage_index}"
        )
    entry_question = payload.get("entry_question")
    if not isinstance(entry_question, str) or not entry_question.strip():
        raise ProbePlanValidationError(
            f"stage {stage.stage_index} needs a non-empty entry_question"
        )
    raw_probes = payload.get("probes")
    if not isinstance(raw_probes, list) or not raw_probes:
        raise ProbePlanValidationError(
            f"stage {stage.stage_index} needs at least one probe"
        )
    covered: set[str] = set()
    probes: list[ProbeItem] = []
    for raw in raw_probes:
        if not isinstance(raw, Mapping):
            raise ProbePlanValidationError("each probe must be a JSON object")
        evidence_key = raw.get("evidence_key")
        if not isinstance(evidence_key, str) or not evidence_key.strip():
            raise ProbePlanValidationError("probe evidence_key must be a non-empty string")
        if evidence_key not in stage.evidence_keys:
            raise ProbePlanValidationError(
                f"probe evidence_key {evidence_key!r} is not an acceptance"
                f" criterion of stage {stage.stage_index}"
            )
        kind = raw.get("kind")
        if kind not in PROBE_KINDS:
            raise ProbePlanValidationError(
                f"probe kind must be one of {PROBE_KINDS}, got {kind!r}"
            )
        question = raw.get("question")
        if not isinstance(question, str) or not question.strip():
            raise ProbePlanValidationError("probe question must be a non-empty string")
        raw_rubric = raw.get("rubric")
        if not isinstance(raw_rubric, Mapping):
            raise ProbePlanValidationError("probe rubric must be a JSON object")
        pass_criteria = raw_rubric.get("pass")
        partial_criteria = raw_rubric.get("partial")
        if not isinstance(pass_criteria, str) or not pass_criteria.strip():
            raise ProbePlanValidationError("rubric pass criteria must be non-empty")
        if not isinstance(partial_criteria, str) or not partial_criteria.strip():
            raise ProbePlanValidationError("rubric partial criteria must be non-empty")
        raw_fail_signals = raw_rubric.get("fail_signals")
        if (
            not isinstance(raw_fail_signals, list)
            or not raw_fail_signals
            or not all(
                isinstance(item, str) and item.strip() for item in raw_fail_signals
            )
        ):
            raise ProbePlanValidationError(
                "rubric fail_signals must be a non-empty list of non-empty strings"
            )
        raw_followups = raw.get("followups", [])
        if not isinstance(raw_followups, list):
            raise ProbePlanValidationError("followups must be a list of strings")
        if len(raw_followups) > MAX_FOLLOWUP_PROBES:
            raise ProbePlanValidationError(
                f"at most {MAX_FOLLOWUP_PROBES} followup probes per probe"
            )
        if not all(
            isinstance(item, str) and item.strip() for item in raw_followups
        ):
            raise ProbePlanValidationError("followups must be non-empty strings")
        covered.add(evidence_key)
        probes.append(
            ProbeItem(
                evidence_key=evidence_key,
                kind=kind,
                question=question.strip(),
                rubric=ProbeRubric(
                    pass_criteria=pass_criteria.strip(),
                    partial_criteria=partial_criteria.strip(),
                    fail_signals=tuple(item.strip() for item in raw_fail_signals),
                ),
                followups=tuple(item.strip() for item in raw_followups),
            )
        )
    missing_keys = [key for key in stage.evidence_keys if key not in covered]
    if missing_keys:
        raise ProbePlanValidationError(
            f"stage {stage.stage_index} evidence keys without a probe:"
            f" {missing_keys}"
        )
    return ProbeStagePlan(
        stage_index=stage.stage_index,
        entry_question=entry_question.strip(),
        probes=tuple(probes),
    )


def stage_plan_to_payload(stage_plan: ProbeStagePlan) -> dict[str, Any]:
    return {
        "stage_index": stage_plan.stage_index,
        "entry_question": stage_plan.entry_question,
        "probes": [
            {
                "evidence_key": probe.evidence_key,
                "kind": probe.kind,
                "question": probe.question,
                "rubric": {
                    "pass": probe.rubric.pass_criteria,
                    "partial": probe.rubric.partial_criteria,
                    "fail_signals": list(probe.rubric.fail_signals),
                },
                "followups": list(probe.followups),
            }
            for probe in stage_plan.probes
        ],
    }


def plan_to_payload(plan: ProbePlan) -> list[dict[str, Any]]:
    return [stage_plan_to_payload(stage_plan) for stage_plan in plan.stages]


def render_probe_plan_block(
    plan: ProbePlan, stages: Sequence[ActivityStageRecord]
) -> str:
    """把定稿计划渲染成注入会话策略的文本块（存档形态）。"""
    names = {stage.stage_index: stage.name for stage in stages}
    lines = [
        "# 阶段探针计划（随会话策略定稿；缺口驱动、一次只出一个探针）"
    ]
    for stage_plan in plan.stages:
        lines.append(
            f"## 阶段 {stage_plan.stage_index} · {names.get(stage_plan.stage_index, '')}"
        )
        lines.append(f"- 入口问题（baseline_probe）：{stage_plan.entry_question}")
        for probe in stage_plan.probes:
            lines.append(
                f"- 证据 {probe.evidence_key} · {probe.kind}：{probe.question}"
            )
            lines.append(f"  判定：过={probe.rubric.pass_criteria}")
            lines.append(f"  部分过={probe.rubric.partial_criteria}")
            lines.append(
                f"  不过信号={'；'.join(probe.rubric.fail_signals)}"
            )
            if probe.followups:
                lines.append(f"  存疑追问（≤{MAX_FOLLOWUP_PROBES}）：{'；'.join(probe.followups)}")
    return "\n".join(lines)
