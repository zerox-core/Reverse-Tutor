"""素材驱动阶段生成器（docs/specs/material-driven-stage-generator.md）的
prompt、校验与 StageDefinition 转换。

设计稿口径（v0.1，朱曦策拍板启动）：
- 双源链路：有书目录按章节树切阶段；无书素材按主题自拆草稿；
- 单双数日成对验收结构（challenge-01 人工精编基准）：奇数日「学生能说清」、
  偶数日「出题验证 + 误解纠错闭环」；
- 天数不重不漏覆盖 1..total_days（§2.1 章节覆盖完整性：不跳章、不重叠）；
- 置信标注如实：``evidenced``（≥2 个独立来源支撑）/ ``inferred``（仅自拆或
  单源），全流程不掩盖推断等级；冷门主题线性拆解（基础→进阶→综合）且
  全部标 ``inferred``；
- 人审定稿在接线侧（会话创建流程），本层只保证产物 schema、置信标注与
  持久化对齐（``SqlAlchemyStageStore.define_activity_stages``）。

生成用注入式 ``chat_json``（online_db 保持纯持久层、不 import llm）。
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Awaitable, Callable, Mapping, Sequence

from online_db.models.stage import STAGE_EVIDENCE_LEVELS
from online_db.stage_store import StageDefinition

ChatJsonCallable = Callable[..., Awaitable[dict[str, Any]]]

LEVEL_EVIDENCED = "evidenced"
LEVEL_INFERRED = "inferred"

STAGE_GENERATION_SYSTEM_PROMPT = """你是一个课程设计师。输入是活动标题、总天数与若干素材摘要（可能是书目录 / 教学大纲，也可能是零散素材），你要把它们转成「粗分类阶段划分」——大阶段定框架、上面板；阶段内的知识点级细分不在你的任务里（那是会话中增量发现的）。

# 阶段划分规则
1. 每个阶段写清：名称（name）、能力目标（capability，一句话说清「学完能干什么」）、验收证据键（evidence_keys）、覆盖的天数（task_day_numbers）。
2. 天数约束（硬约束，违反即作废）：task_day_numbers 合起来必须不重不漏恰好覆盖 1..总天数；阶段按天数先后排序，stage_index 从 1 连续编号。
3. 验收结构向成对模式靠拢（基准样例：奇数日「学生能说清 X」+ 偶数日「出题验证 + 误解纠错闭环」，证据键通常取 explain / verify；综合收尾阶段可加 final_exam）。素材结构不同时按素材实际结构组织，但每个阶段至少一个可判定的验收证据键。
4. 有书目录 / 大纲时按章节树切，不跳章、不重叠；无目录素材按主题依赖顺序自拆（先基础、再进阶、后综合）。
5. 阶段数量合理：参考 17 天≈6 个大阶段的粒度，不要按天切碎，也不要全程一段。

# 置信标注（必须如实，不许掩盖）
- "evidenced"：该阶段的顺序与内容有 ≥2 个独立来源（素材内多处 / 素材与公认学习路径）支撑；
- "inferred"：仅由你自拆或只有单一来源支撑；
- 素材太冷门、找不到公认学习路径时：按「基础→进阶→综合」线性拆解，全部阶段标 "inferred"。宁可明确说「这是推断」，不假装有据。
- evidence_refs 列出支撑该阶段的素材标题 / 章节名；inferred 阶段没有支撑来源时给空列表。

# 输出格式（严格 JSON，禁止 markdown、注释与额外文字）
{
  "stages": [
    {
      "stage_index": 1,
      "name": "S1 阶段名",
      "capability": "学完能干什么（一句话）",
      "evidence_keys": ["explain", "verify"],
      "task_day_numbers": [1, 2],
      "evidence_level": "evidenced",
      "evidence_refs": ["素材标题或章节"]
    }
  ]
}
"""


@dataclass(frozen=True)
class MaterialDigest:
    """生成器的素材输入：标题 + 正文摘要 + 可选引用标识。"""

    title: str
    text: str
    ref: str = ""


@dataclass(frozen=True)
class StageDraft:
    stage_index: int
    name: str
    capability: str
    evidence_keys: tuple[str, ...]
    task_day_numbers: tuple[int, ...]
    evidence_level: str
    evidence_refs: tuple[str, ...]


@dataclass(frozen=True)
class StagePlan:
    stages: tuple[StageDraft, ...]


class StagePlanValidationError(ValueError):
    pass


def build_stage_generation_user_prompt(
    materials: Sequence[MaterialDigest],
    *,
    activity_title: str,
    activity_description: str = "",
    total_days: int,
) -> str:
    """构造阶段生成的 user prompt（输入 = 素材摘要 + 天数预算）。"""
    lines: list[str] = [f"活动：{activity_title}"]
    if activity_description and activity_description.strip():
        lines.append(activity_description.strip())
    lines.append(f"总天数：{total_days} 天（task_day_numbers 必须不重不漏恰好覆盖 1..{total_days}）")
    lines.append("")
    lines.append("素材：")
    for index, material in enumerate(materials, start=1):
        title = material.title.strip() if material.title else f"素材{index}"
        lines.append(f"## 素材{index} · {title}")
        lines.append(material.text.strip())
    lines.append("")
    lines.append("请按系统指令输出覆盖全部天数的阶段划分 JSON。")
    return "\n".join(lines)


async def generate_stage_plan(
    materials: Sequence[MaterialDigest],
    *,
    activity_title: str,
    activity_description: str = "",
    total_days: int,
    chat_json: ChatJsonCallable,
    temperature: float = 0.3,
    max_tokens: int = 4000,
) -> StagePlan:
    """调用注入的 chat_json 生成并校验阶段划分草稿。"""
    user_prompt = build_stage_generation_user_prompt(
        materials,
        activity_title=activity_title,
        activity_description=activity_description,
        total_days=total_days,
    )
    payload = await chat_json(
        STAGE_GENERATION_SYSTEM_PROMPT,
        [{"role": "user", "content": user_prompt}],
        temperature=temperature,
        max_tokens=max_tokens,
    )
    return validate_stage_plan(payload, total_days=total_days)


def validate_stage_plan(payload: Any, *, total_days: int) -> StagePlan:
    """校验 LLM 输出的阶段划分：schema、连续性、覆盖完整性、置信标注。"""
    if not isinstance(total_days, int) or isinstance(total_days, bool) or total_days < 1:
        raise StagePlanValidationError("total_days must be a positive integer")
    if not isinstance(payload, Mapping):
        raise StagePlanValidationError("stage plan payload must be a JSON object")
    raw_stages = payload.get("stages")
    if not isinstance(raw_stages, list) or not raw_stages:
        raise StagePlanValidationError('stage plan payload needs a "stages" list')

    drafts: list[StageDraft] = []
    seen_days: set[int] = set()
    for raw in raw_stages:
        if not isinstance(raw, Mapping):
            raise StagePlanValidationError("each stage draft must be a JSON object")
        stage_index = raw.get("stage_index")
        if not isinstance(stage_index, int) or isinstance(stage_index, bool):
            raise StagePlanValidationError("stage_index must be an integer")
        if stage_index != len(drafts) + 1:
            raise StagePlanValidationError(
                "stage indices must be contiguous starting at 1"
            )
        name = raw.get("name")
        if not isinstance(name, str) or not name.strip():
            raise StagePlanValidationError(
                f"stage {stage_index} name must be a non-empty string"
            )
        capability = raw.get("capability")
        if not isinstance(capability, str) or not capability.strip():
            raise StagePlanValidationError(
                f"stage {stage_index} capability must be a non-empty string"
            )
        evidence_keys = raw.get("evidence_keys")
        if not isinstance(evidence_keys, list) or not evidence_keys:
            raise StagePlanValidationError(
                f"stage {stage_index} needs at least one evidence key"
            )
        if not all(
            isinstance(key, str) and key.strip() for key in evidence_keys
        ):
            raise StagePlanValidationError("evidence keys must be non-empty strings")
        normalized_keys = tuple(key.strip() for key in evidence_keys)
        if len(set(normalized_keys)) != len(normalized_keys):
            raise StagePlanValidationError(
                f"stage {stage_index} has duplicate evidence keys"
            )
        raw_days = raw.get("task_day_numbers")
        if not isinstance(raw_days, list) or not raw_days:
            raise StagePlanValidationError(
                f"stage {stage_index} needs at least one task day number"
            )
        days: list[int] = []
        for day in raw_days:
            if not isinstance(day, int) or isinstance(day, bool) or day < 1:
                raise StagePlanValidationError(
                    "task day numbers must be positive integers"
                )
            if day > total_days:
                raise StagePlanValidationError(
                    f"task day {day} exceeds total_days {total_days}"
                )
            if day in seen_days:
                raise StagePlanValidationError(
                    f"duplicate task day number across stages: {day}"
                )
            seen_days.add(day)
            days.append(day)
        evidence_level = raw.get("evidence_level")
        if evidence_level not in STAGE_EVIDENCE_LEVELS:
            raise StagePlanValidationError(
                f"stage {stage_index} evidence_level must be one of"
                f" {STAGE_EVIDENCE_LEVELS}, got {evidence_level!r}"
            )
        raw_refs = raw.get("evidence_refs", [])
        if not isinstance(raw_refs, list):
            raise StagePlanValidationError("evidence_refs must be a list of strings")
        if not all(isinstance(ref, str) and ref.strip() for ref in raw_refs):
            raise StagePlanValidationError(
                "evidence_refs must be non-empty strings"
            )
        drafts.append(
            StageDraft(
                stage_index=stage_index,
                name=name.strip(),
                capability=capability.strip(),
                evidence_keys=normalized_keys,
                task_day_numbers=tuple(days),
                evidence_level=evidence_level,
                evidence_refs=tuple(ref.strip() for ref in raw_refs),
            )
        )

    # 覆盖完整性：天数不重之外还必须不漏（§2.1 不跳章）
    expected = set(range(1, total_days + 1))
    missing = sorted(expected - seen_days)
    if missing:
        raise StagePlanValidationError(
            f"stage plan does not cover all days; missing: {missing}"
        )
    return StagePlan(stages=tuple(drafts))


def stage_plan_to_definitions(plan: StagePlan) -> tuple[StageDefinition, ...]:
    """把校验过的草稿转成可落档的 StageDefinition 元组。"""
    return tuple(
        StageDefinition(
            stage_index=draft.stage_index,
            name=draft.name,
            capability=draft.capability,
            evidence_keys=draft.evidence_keys,
            task_day_numbers=draft.task_day_numbers,
            evidence_level=draft.evidence_level,
            evidence_refs=draft.evidence_refs,
        )
        for draft in plan.stages
    )
