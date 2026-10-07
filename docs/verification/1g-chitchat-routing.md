# 1g 闲聊兼容 · 意图分流验证记录

日期：2026-09-29 · 分支：Android · 提交：见 git log（1g chitchat routing）

## 分流规则

- 分流点：`BackgroundTurnPreparationCoordinator.prepareAndEnqueue`，在上下文装配之前调用 `GuidedLearningIntentClassifier.classify(userText)`（确定性纯内存规则，无网络/LLM 依赖）。
- OffTopic 且 `imageAttachments.isEmpty()` 且轻量入口已接线 → **轻装配**：`SessionConversationAssembly.assembleLightweightContext`，仅读最近消息（messageLimit=10、textCap=200，与重装配口径一致），跳过会话摘要（LLM 调用）、RAG/图谱/记忆/掌握度/易错/前史/窗口记忆端口；messagePort 失败安全降级为空列表 + ContextWarning("message","source_unavailable")。
- 保守边界（一律全装配）：GoalChange（换目标可能需要新主题 RAG）；带图片附件的回合；轻量入口为 null 的旧构造（向后兼容）。
- 证据链复用既有（2026-09-20 拍板）轻量证据模板：OffTopic/GoalChange 回合 prompt 证据只带最近 2 条消息。

## 单测

`BackgroundTurnPreparationChitchatRoutingTest`（5 条，2026-09-29）：

1. 「讲个笑话」→ 轻装配 1 次、重装配 0 次，证据只含 Message 类、只 2 条。
2. 「什么是二分查找？」→ 全装配。
3. 「我不学这个了，改学化学」（GoalChange）→ 全装配（保守）。
4. 「讲个笑话」+ 图片附件 → 全装配（兜底）。
5. 未接线轻量入口（旧构造）→ OffTopic 回落全装配。

定向 5/5 绿；随后全量 `gradlew test` BUILD SUCCESSFUL（4m14s），全仓库测试 XML 统计 **3755 tests / 0 failures / 0 errors / 0 skipped**。

## 模拟器 E2E（emulator-5554，memtest 包，本地 mock SSE 服务记录完整请求体）

环境：`reverseTutorDebugLlmBaseUrl` 临时指向本地 mock（8011），E2E 后已还原生产 hub 地址。同一既有会话内连发两回合，mock 完整落盘每回合 POST body：

| 回合 | 输入 | 分类预期 | 实测 prompt 模板 | 流式完成 |
|---|---|---|---|---|
| t1 | what is binary search and when use it（学习问题） | 非 OffTopic → 全装配 | Response format: steps；Evidence requirement: user_answer；完整学生契约 | 6s |
| t2 | hi（闲聊） | OffTopic → 轻装配 | Response format: plain；Evidence requirement: none；信息密度低（≤80 字、0 新概念、0 问题）；节奏信号「消息很短」 | 6s |

- 两回合均为单条 user message（1627/1416 字符），轻量路径 prompt 如预期显著精简。
- 闲聊回合无 RAG/记忆检索痕迹，回复正常落 UI（「本轮学习提示」卡渲染在场，用户消息 `hi` 可见）。
- 该既有会话无书源/记忆数据，故全装配回合亦无 embeddings 调用可对比；端口级差异由单测计数器覆盖（重装配 0 次断言）。

## 结论

1g 验收达标：闲聊不进重装配的单测 + 模拟器实测响应路径均通过。大阶段一 1a-1g 全部落定（1d 创建面板设计暂缓，等用户设计输入）。
