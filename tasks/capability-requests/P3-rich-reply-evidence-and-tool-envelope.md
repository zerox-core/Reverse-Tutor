# P3 能力申请：富回复、独立资料引用与受限工具调用信封

> 状态：**proposed，待单独批准**。本申请只定义生成协议侧的向后兼容扩展；在获得用户明确批准前，不得修改 `core:llm`、`core:data` 生成逻辑、Worker 或任何持久化代码。
>
> 对应任务：`NEWMP-V1-002` Task 0 / Task 3。

## 1. 已确认的问题

当前 `ChatGenerationRepository.withCitationFooter()` 会把已知上下文资料拼接为 `Sources:` 文本，随后与助手正文一起写入 assistant 消息。这会把系统检索信息混入会话正文。

修复目标是把资料改为独立的、可由前端决定是否展示的引用工件；不得只在 Compose 层隐藏该脚注，更不得把资料正文、URL、Provider 原始输出或密钥改存到另一处。

## 2. 请求批准的最小协议能力

在 `LlmGenerationRequest`、`ChatGenerationInput`、`BackgroundGenerationInput`、`BackgroundGenerationJob` 和完成结果中增加可选字段，旧调用保持 `null`/空集合语义：

```kotlin
data class LlmAssistantReplyEnvelope(
    val blocks: List<LlmRichContentBlock>,
    val evidenceReferenceIds: List<String> = emptyList(),
    val toolCalls: List<LlmToolCall> = emptyList(),
    val outcome: StructuredTurnOutcome = StructuredTurnOutcome.EMPTY
)

data class LlmToolCall(
    val callId: String,
    val name: String,
    val argumentsJson: String
)
```

`LlmRichContentBlock` 仅允许标题、段落、有序/无序列表、代码块、提示块与简单表格。它是给 renderer 的语义类型，不包含 Compose、Room、DAO、Provider 或网络类型。

## 3. 信封解析与安全规则

- Provider 输出如采用信封，必须是一个有界 JSON object；版本、block 类型、工具名和参数形状均使用白名单。
- 最大值：24 个 blocks、8 个工具调用；所有用户可见文本均经过长度限制与安全清理。
- 未知 block、未知工具、未知字段组合、畸形 JSON、越界长度或无效 JSON 参数一律 fail-closed：保留普通文本回复，返回空引用、空工具调用和 `StructuredTurnOutcome.EMPTY`。
- `evidenceReferenceIds` 必须与本次请求已知的 context-evidence ID 求交集。引用只暴露稳定 ID；不得携带资料正文、URL、文件路径或检索结果。
- 工具名只允许 `session_document.*`、`session_table.*`、`reference.open`；实际执行仍由 P6 批准后的会话范围策略决定。
- 不保存或下传 raw transcript、raw message text、raw Provider text、Authorization/Bearer、API key、URL、SecretStore 内容或内部异常诊断。

## 4. 兼容与单写入者

- 不支持信封的既有模型回复继续作为普通 assistant 文本保存。
- `ChatGenerationRepository` 仍是唯一 assistant 消息写入者；该变更只改变其完成结果携带的附加工件，不新增 assistant 写入路径。
- 已有调用者缺省新字段时，planner 不注入新内容，完成结果的 envelope 为 `null` 或安全空值。
- 资料脚注从 assistant timeline 文本中移除；资料引用以后作为 sideband artifact 交给 feature contract，前端可独立展示、打开或关闭。

## 5. 不在本申请中的内容

- 不修改 `core:model`、`core:protocol`、SecretStore、导入导出、签名、PWA/Capacitor。
- 不创建 Room entity、DAO、schema 或 migration；工件、文档、表格、工具回执的持久化仅由独立 P6 申请授权。
- 不接入 DeepSeek Harness 运行时、第三方插件、文件/网络/shell 工具，也不调用真实 Provider。

## 6. 验证与回滚

验收需覆盖普通文本兼容、合法信封、畸形信封回退、未知资料 ID 拒绝、工具白名单、边界长度和无 `Sources:` 正文。所有 `core:llm` / `core:data` 回归必须通过。

若需回滚，读取端继续接受缺省字段；已持久化的工件由 P6 的向前迁移策略管理，绝不通过降级 Room schema 回滚。

## 7. 审批记录

- [x] 用户已于 2026-09-01 单独批准 P3：允许上述生成协议、富回复、独立资料引用和工具调用信封能力。
- [ ] 批准后先提交受影响字段、兼容性、测试与回滚说明，再进入 Task 3 实现。
