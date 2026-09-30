"""engine.py 活动会话接线单测（探针计划注入 + 会话内证据写回）。

链路：会话 settings 带 "activity" 链接 + 已注册 hook 时——
1. run_turn 把 hook.render_block 的探针计划块注入系统提示（附证据判定输出指令）；
2. LLM 载荷里的 activity_evidence 经 hook.record_turn_evidence 写回；
hook 未注册 / 会话无链接 / hook 抛错时整轮照常完成（安全降级）。
"""
from __future__ import annotations

from uuid import uuid4

import pytest

import db
import engine


ACCOUNT_ID = str(uuid4())
ACTIVITY_SETTINGS = {"activity": {"slug": "challenge-01", "accountId": ACCOUNT_ID}}
PLAN_BLOCK = "# 我在这个挑战里想搞懂的东西\n## 阶段 1 · 基础概念\n- 关于「explain」，我打算这样向老师请教：讲清概念"


class FakeHook:
    def __init__(self, *, note="已记录证据 explain@阶段1（probe_recite）"):
        self.note = note
        self.render_calls: list[dict] = []
        self.record_calls: list[dict] = []

    def render_block(self, *, activity_slug, account_id):
        self.render_calls.append(
            {"activity_slug": activity_slug, "account_id": account_id}
        )
        return PLAN_BLOCK

    def record_turn_evidence(self, **kwargs):
        self.record_calls.append(kwargs)
        return self.note


@pytest.fixture
def hook():
    fake = FakeHook()
    engine.set_activity_hook(fake)
    try:
        yield fake
    finally:
        engine.reset_activity_hook()


def _payload(*, activity_evidence=None):
    payload = {
        "evaluation": {
            "correctness": 0.8,
            "depth": 0.6,
            "entry_status": "has_entry",
            "evidence_for_mastery": {
                "type": "explanation",
                "status": "passed",
                "error_type": "",
                "reason": "解释清楚",
            },
            "user_emotion": "neutral",
            "new_requirements": [],
        },
        "action": {
            "type": "probe",
            "student_role": "probing_student",
            "knowledge_point": "二次函数对称轴",
            "difficulty": 0.5,
            "note": "mocked",
        },
        "reply": "老师，那为什么对称轴是 x=-b/(2a)？",
        "anchor_updates": [],
    }
    if activity_evidence is not None:
        payload["activity_evidence"] = activity_evidence
    return payload


def _capture_chat_json(monkeypatch, captured, payload):
    async def fake_chat_json(system, messages, **kwargs):
        captured["system"] = system
        return payload

    monkeypatch.setattr(engine.llm, "chat_json", fake_chat_json)


async def test_run_turn_injects_probe_plan_block(db_sess, monkeypatch, hook):
    s = engine.create_session(
        db_sess, title="", role="高三生", goal="方法学习", settings=ACTIVITY_SETTINGS
    )
    captured: dict = {}
    _capture_chat_json(monkeypatch, captured, _payload())

    await engine.run_turn(db_sess, s.id, "因为对称轴是 x=-b/(2a)")

    assert hook.render_calls == [
        {"activity_slug": "challenge-01", "account_id": ACCOUNT_ID}
    ]
    assert PLAN_BLOCK in captured["system"]
    assert "activity_evidence" in captured["system"]  # 证据判定输出指令


async def test_run_turn_writes_back_activity_evidence(db_sess, monkeypatch, hook):
    s = engine.create_session(
        db_sess, title="", role="高三生", goal="方法学习", settings=ACTIVITY_SETTINGS
    )
    captured: dict = {}
    _capture_chat_json(
        monkeypatch,
        captured,
        _payload(activity_evidence={"stage_index": 1, "evidence_key": "explain"}),
    )

    result = await engine.run_turn(db_sess, s.id, "因为对称轴是 x=-b/(2a)")

    assert len(hook.record_calls) == 1
    call = hook.record_calls[0]
    assert call["activity_slug"] == "challenge-01"
    assert call["account_id"] == ACCOUNT_ID
    assert call["session_id"] == s.id
    assert isinstance(call["message_id"], int)
    assert call["stage_index"] == 1
    assert call["evidence_key"] == "explain"
    assert "[activity] 已记录证据 explain@阶段1（probe_recite）" in result.process_summary


async def test_run_turn_without_activity_link_skips_hook(db_sess, monkeypatch, hook):
    s = engine.create_session(db_sess, title="", role="高三生", goal="方法学习")
    captured: dict = {}
    _capture_chat_json(
        monkeypatch,
        captured,
        _payload(activity_evidence={"stage_index": 1, "evidence_key": "explain"}),
    )

    await engine.run_turn(db_sess, s.id, "因为对称轴是 x=-b/(2a)")

    assert hook.render_calls == []
    assert hook.record_calls == []
    assert PLAN_BLOCK not in captured["system"]


async def test_run_turn_without_hook_registered_is_noop(db_sess, monkeypatch):
    # 链接存在但未注册 hook：整轮照常，不注入不写回
    s = engine.create_session(
        db_sess, title="", role="高三生", goal="方法学习", settings=ACTIVITY_SETTINGS
    )
    captured: dict = {}
    _capture_chat_json(
        monkeypatch,
        captured,
        _payload(activity_evidence={"stage_index": 1, "evidence_key": "explain"}),
    )

    result = await engine.run_turn(db_sess, s.id, "因为对称轴是 x=-b/(2a)")

    assert PLAN_BLOCK not in captured["system"]
    assert "[warn]" not in result.process_summary


async def test_render_failure_degrades_with_warn(db_sess, monkeypatch):
    class BrokenRenderHook(FakeHook):
        def render_block(self, *, activity_slug, account_id):
            raise RuntimeError("store down")

    engine.set_activity_hook(BrokenRenderHook())
    try:
        s = engine.create_session(
            db_sess,
            title="",
            role="高三生",
            goal="方法学习",
            settings=ACTIVITY_SETTINGS,
        )
        captured: dict = {}
        _capture_chat_json(monkeypatch, captured, _payload())

        result = await engine.run_turn(db_sess, s.id, "因为对称轴是 x=-b/(2a)")

        assert PLAN_BLOCK not in captured["system"]
        assert "[warn] activity_probe_plan_unavailable" in result.process_summary
    finally:
        engine.reset_activity_hook()


async def test_record_failure_degrades_with_warn(db_sess, monkeypatch):
    class BrokenRecordHook(FakeHook):
        def record_turn_evidence(self, **kwargs):
            raise ValueError("evidence_key 'nope' is not in the probe plan")

    engine.set_activity_hook(BrokenRecordHook())
    try:
        s = engine.create_session(
            db_sess,
            title="",
            role="高三生",
            goal="方法学习",
            settings=ACTIVITY_SETTINGS,
        )
        captured: dict = {}
        _capture_chat_json(
            monkeypatch,
            captured,
            _payload(activity_evidence={"stage_index": 1, "evidence_key": "nope"}),
        )

        result = await engine.run_turn(db_sess, s.id, "因为对称轴是 x=-b/(2a)")

        assert "[warn] activity_evidence_not_recorded" in result.process_summary
    finally:
        engine.reset_activity_hook()


async def test_invalid_activity_evidence_shape_ignored(db_sess, monkeypatch, hook):
    s = engine.create_session(
        db_sess, title="", role="高三生", goal="方法学习", settings=ACTIVITY_SETTINGS
    )
    captured: dict = {}
    _capture_chat_json(
        monkeypatch,
        captured,
        _payload(activity_evidence={"stage_index": "abc", "evidence_key": None}),
    )

    result = await engine.run_turn(db_sess, s.id, "因为对称轴是 x=-b/(2a)")

    assert hook.record_calls == []
    assert "[warn] activity_evidence_not_recorded" in result.process_summary


async def test_activity_link_incomplete_settings_skips_hook(
    db_sess, monkeypatch, hook
):
    s = engine.create_session(
        db_sess,
        title="",
        role="高三生",
        goal="方法学习",
        settings={"activity": {"slug": "challenge-01"}},  # 缺 accountId
    )
    captured: dict = {}
    _capture_chat_json(monkeypatch, captured, _payload())

    await engine.run_turn(db_sess, s.id, "因为对称轴是 x=-b/(2a)")

    assert hook.render_calls == []
    assert hook.record_calls == []
