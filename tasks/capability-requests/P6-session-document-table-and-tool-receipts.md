# P6 能力申请：会话文档、轻量表格、回复工件与工具回执持久化

> 状态：**proposed，待单独批准**。本申请只描述受控的 `10 -> 11` 前向迁移与会话范围存储；在获得用户明确批准前，不得修改 Entity、DAO、Room schema、migration 或仓库生产代码。
>
> 对应任务：`NEWMP-V1-002` Task 0 / Task 4。

## 1. 目标和边界

每个会话可维护一份或多份本地学习文档及轻量表格；助手富回复的已验证工件、工具结果和资料引用可独立恢复。所有内容严格归属于当前 `spaceId + sessionId`，不得被其他会话读取或写入。

用户可见文档内容是明确的本地会话内容，不是长期 companion memory，也不是 raw conversation/providor 日志。

## 2. 请求批准的 10 -> 11 聚合

```text
assistant_reply_artifacts(
  assistantMessageId PK, sessionId, blocksPayload,
  evidenceReferencesPayload, toolResultsPayload, createdAtEpochMillis
)
session_documents(
  id PK, spaceId, sessionId, title, kind,
  createdAtEpochMillis, updatedAtEpochMillis
)
session_document_blocks(
  id PK, documentId, ordinal, kind, payload, updatedAtEpochMillis
)
session_tables(
  id PK, documentId, title, createdAtEpochMillis, updatedAtEpochMillis
)
session_table_columns(
  id PK, tableId, ordinal, name, valueType
)
session_table_rows(
  id PK, tableId, rowKey, cellsPayload, updatedAtEpochMillis
)
tool_call_receipts(
  callId PK, sessionId, toolName, status, safeResultPayload, completedAtEpochMillis
)
```

`callId` 是唯一幂等键。相同 `callId` 的重试必须先读取既有回执，不能重复创建文档、block、表格或行。

## 3. 数据最小化与访问控制

- 每一项会话拥有数据均带 `sessionId`，文档根记录额外带 `spaceId`；所有读取、更新和删除查询都必须绑定当前会话身份。
- `blocksPayload` 仅保存已验证的用户可见富文本块；`evidenceReferencesPayload` 仅保存稳定引用 ID；`toolResultsPayload`/`safeResultPayload` 仅保存稳定 document/table ID、行数与安全错误码。
- 禁止列名和载荷中保存 `rawTranscript`、`providerText`、`authorization`、`secret`、`url`、system prompt、检索正文、文件路径、工具原始参数或跨会话内容。
- 工具只限会话文档、会话表格和引用打开意图；不能访问设备文件、网络、系统命令、密钥、DAO 裸接口或 assistant 消息写入接口。

## 4. 迁移与兼容策略

- 仅允许 forward-only `10 -> 11`，保留并继续注册全部旧迁移；禁止 destructive migration 和任何版本回退。
- 迁移必须保持已有 session、message、background job、窗口拓扑和学习台账记录可读。
- schema 11 必须导出；测试要验证新表存在且没有禁用字段名。
- 旧数据库或没有工件的会话读取时返回空工件/空文档列表，不把普通聊天文本伪造成结构化文档。

## 5. 对外仓库契约

仓库只发布领域安全快照，例如：

```kotlin
data class SessionDocumentSnapshot(
    val id: String,
    val sessionId: String,
    val title: String,
    val blocks: List<RichDocumentBlock>
)

data class ToolCallReceipt(
    val callId: String,
    val sessionId: String,
    val toolName: String,
    val status: String,
    val safeResult: ToolSafeResult
)
```

UI/feature 不得接触 Room Entity、DAO、Database、SQL、raw arguments 或 Provider 类型。工具执行失败只产生安全结果，不回滚或篡改已完成的 assistant 消息。

## 6. 不在本申请中的内容

- 不修改 `core:model`、`core:protocol`、SecretStore、导入导出、签名、PWA/Capacitor。
- 不改变 Worker 的唯一 Provider 调用和唯一 assistant 写入职责。
- 不提供飞书多维表格、第三方插件、文件系统、网络或 shell 能力。
- 不提供跨会话文档共享；长期记忆继续由既有窗口/companion 拓扑单独管理。

## 7. 验证与回滚

验收必须包括：同 `callId` 幂等、跨会话拒绝、普通会话无工件时的安全空值、10 -> 11 迁移保留旧数据、JVM 仓库回归和一次真实设备迁移。工具失败不得阻断聊天，也不得泄露内部信息。

回滚只允许停止使用新能力，读取端保持兼容；不得降低 Room 版本或删除用户会话内容。

## 8. 审批记录

- [x] 用户已于 2026-09-01 单独批准 P6：允许上述 schema、DAO、migration、仓库和工具回执持久化能力。
- [ ] 批准后先提交受影响表、迁移顺序、兼容性、测试与回滚说明，再进入 Task 4 实现。
