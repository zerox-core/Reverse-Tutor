# NEWMP-V1-006 聊天体验与多模态输入执行计划

> 目标：让真实会话保持自然的学生式节奏，同时补齐资料文件入口、多模态图片输入和后台状态可见性；教学算法、资料验证和持久化仍然隐藏在后台。

## 当前真机结论

- 设备：Huawei BRA-AL00，Android 12 / API 31。
- 基础会话、发送、退出到首页后继续生成、回到应用后继续生成：已观察可用。
- 当前截图确认：聊天气泡会一次性展示较长的分点分析，并夹带“请求诊断”式结构化表达；这不符合自然聊天节奏。
- 未确认：完全结束应用进程后，后台任务是否仍能恢复；本项留到后台恢复专门验收，不用猜测结果。
- 当前截图只作本轮诊断依据，不纳入仓库交付物。

## 阶段划分

### 阶段 A：当前 V1-005 收口前必须完成

这部分直接影响聊天是否像聊天，不能以“算法已经正确”替代。

#### Task A1：隐藏内部结构化内容

文件范围：

- `mobile-native/core/llm/src/main/java/com/reversetutor/core/llm/LlmGenerationLifecycle.kt`
- `mobile-native/core/llm/src/main/java/com/reversetutor/core/llm/LlmAssistantReplyEnvelopeParser.kt`
- `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/ChatUiState.kt`
- `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/ChatScreen.kt`
- 对应 core:llm、feature:chat、app 测试

规则：

1. `checkPlan`、`outcome`、教学动作、内部 evidence、资料 revision 只保留为后台结构化数据。
2. 聊天气泡只渲染面向用户的文本块、代码块、表格块和经过允许的资料摘要。
3. 不向用户显示 `Teaching policy`、`Action`、`Knowledge point`、`correctness`、`mastery`、原始 JSON 或内部诊断字段。
4. 解析失败时显示普通学生式回复，不把原始结构化 JSON 当作聊天正文。
5. 资料引用改为轻量摘要/可关闭卡片，点击后进入资料查看，不把资料全文插入消息正文。

验收：构造含完整 envelope、checkPlan、outcome 和内部字段的回复，断言气泡文本不含这些字段；普通 Markdown、代码块和有限资料摘要仍可见。

#### Task A2：单回合节奏与主动互动

文件范围：

- `mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/TeachingActionSelector.kt`
- `mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/GuidedLearningContracts.kt`
- `mobile-native/core/llm/src/main/java/com/reversetutor/core/llm/LlmGenerationLifecycle.kt`
- `mobile-native/app/src/main/java/com/reversetutor/preview/wiring/session/GuidedLearningInputMapper.kt`
- 对应 domain、llm、app 测试

规则：

1. 每次回复只完成一个教学动作：解释、诊断、提示、示例、练习或复盘之一。
2. 单回合正文设置有界长度和有界段数；超出部分不得继续堆在同一条气泡中。
3. 回复结尾最多保留一个明确的用户动作问题，例如“你先试着说说第一步？”。
4. 需要用户参与时，必须停在用户动作之后，不能一次性把后续答案全部说完。
5. 连续提示、错误答案、正确答案和普通闲聊继续沿用现有 selector 规则，不通过 UI 文案猜状态。

验收：错误答案只返回诊断加一个下一步问题；连续索要提示时每轮提示层级变化；正确答案后进入一个练习/迁移问题；单条 assistant 气泡不再出现完整小节式长答案。

### 阶段 B：V1-006 体验能力

#### Task B1：流式输出

先增加可测试的流式边界，不直接把现有后台最终写入路径改成多写入者。

- 新增只读的 generation chunk/partial state 端口，按 `jobId + generationToken` 关联。
- Provider/runtime 可以逐段报告文本；Worker 仍只负责一次最终 assistant 持久化。
- UI 只显示临时片段，最终完成时以唯一 assistant artifact 替换临时状态。
- 进程被杀或任务失败时，临时片段自动失效，不写入半截 assistant 消息。
- 首先为 Fake runtime 增加分段测试，再接真实 Provider；不把真实 key、URL 或原始流日志写入测试。

验收：用户看到“正在生成”的逐段内容；最终只保留一条完整 assistant 消息；重试、退出、重启不重复写入。

#### Task B2：退出会话后的工作状态热更新

- 会话列表/首页只显示安全状态：生成中、已完成、失败或可重试。
- 返回首页后，当前会话显示生成中的轻量标记；重新进入会话时恢复同一个 job 状态。
- 状态来源使用已有持久化 job 查询和 token 关联，不创建第二个生成任务。
- 完成后自动刷新消息；失败只显示安全错误码映射。

#### Task B3：后台通知

- 仅在应用进入后台且任务仍在运行时发送通用通知。
- 通知不得包含原始提问、资料正文、模型错误、URL 或密钥。
- 点击通知回到对应会话；同一 job 使用稳定通知 id，完成/失败时更新并收敛。
- 权限拒绝时不影响生成和会话内状态。

### 阶段 C：资料和多模态入口

#### Task C1：聊天内本地资料文件入口

- 在聊天附件菜单增加“从手机选择资料”，使用系统文件选择器。
- 选择后进入现有 SourceImport/解析管线，显示解析中、可用、失败三种安全状态。
- 成功资料绑定当前会话并推进 revision；只影响后续新回合。
- 不把本地文件路径或文件正文写入聊天气泡。
- 保留资料库入口作为批量管理入口，但不再要求用户离开聊天才能导入。

#### Task C2：多模态能力识别和图片输入

- 不再仅凭模型名是否包含 `vision` 判断能力；为 LLM profile 增加显式、可持久化且可导出的 `supportsVision` 能力。
- 对 OpenAI-compatible/Qwen profile 默认保持保守值；只有 profile 明确声明支持时才发送图片。
- 图片输入使用现有 `MessageAttachment`，本地 content URI 在请求前由 runtime 转换为协议所需的安全内容，不能把手机 URI 原样当作远端图片地址。
- 不支持图片时给出明确的安全提示；支持时在发送前显示图片缩略图和待发送状态。
- 添加 Fake multimodal runtime 测试，分别覆盖支持、拒绝、重启恢复和超大图片。

## 统一验收顺序

1. 先完成 A1/A2 的 JVM 测试和 app 回归，确认聊天不再泄露内部结构、单回合节奏受控。
2. 再做 B1/B2 的 Fake runtime 分段与重启测试，确保不产生第二个 assistant 写入者。
3. 再做 C1/C2 的真机入口测试：本地文件导入、图片选择、Qwen 多模态请求和安全失败。
4. 最后在 Huawei BRA-AL00 上执行人工流程：发送、返回首页、回到会话、切后台、恢复、结束进程后重开。
5. 每轮都记录实际运行结果；API 36 虚拟机只作 app 行为验证，不作为 Room migration 证据。

## 不在本轮直接改动的内容

- 不为“看起来更快”而删除后台 Worker、改成前台直连或增加第二条 assistant 写入路径。
- 不把内部教学算法面板暴露给用户。
- 不把模型自评直接当作学习事实。
- 不在未验证 Provider 流式协议前修改数据库 schema 或迁移。
