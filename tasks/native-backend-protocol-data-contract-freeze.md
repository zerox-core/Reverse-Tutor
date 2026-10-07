# Native Backend Protocol And Data Contract Freeze

日期：2026-07-04

目的：在重新制定前端 UI/UX 之前，先把 native Android 的后端协议接口层和数据库/数据层定型。后续前端可以完全重新设计，但必须以本文档为边界，不得在 UI 重构中顺手破坏业务协议、数据模型、Room schema、导入导出、后台生成和密钥存储。

本文以当前 `mobile-native/` 代码为准。旧 PWA/Capacitor 仍是迁移来源和行为参考，退出 PWA 必须等 Phase 6 并由用户明确批准。

## 1. 本次冻结结论

冻结的两层：

```text
后端逻辑 / 协议接口层
  mobile-native/core/model
  mobile-native/core/protocol
  mobile-native/core/llm
  mobile-native/core/data/*Repository

数据库 / 数据层
  mobile-native/core/data/local
  mobile-native/core/data/preferences
  mobile-native/core/data/llm/SecretStore.kt
  Room schema exports and migrations
```

暂不冻结的层：

```text
前端 UI 设计层
  mobile-native/app/src/main/java/com/reversetutor/preview/theme
  mobile-native/app/src/main/java/com/reversetutor/preview/ui
  mobile-native/app/src/main/java/com/reversetutor/preview/shell
  mobile-native/feature/*
```

前端设计稍后重新制定。之前 UI 设计方向不作为后续依据。

核心规则：

- UI 层只能通过 Repository、Domain model、protocol facade 调用业务能力。
- UI 层不得直接调用 Room DAO、直接读写 Entity、直接执行 SQL、直接操作 Keystore secret。
- UI 重构不得修改 `DatabaseSchema.version`、Room entities、DAO SQL、protocol schema、import/export payload、background generation persistence。
- 若前端设计发现业务能力缺口，先新增前端 UI state 或 adapter；确实需要底层能力时，必须先写后端/数据变更说明和测试，再改底层。

## 2. 模块边界

### 2.1 UI 层允许依赖

UI/feature 模块可以依赖：

- `core:model` 的 domain model 和 enum。
- `core:data` 暴露的 Repository。
- `core:llm` 的公开能力描述、校验、生成状态类型。
- `core:protocol` 的 preset/import/export validation facade。

UI/feature 模块不应依赖：

- `core:data/local/dao/*`
- `core:data/local/entity/*`
- `ReverseTutorDatabase`
- `DatabaseSchema`
- `RoomNativeImportStore` / `RoomNativeExportStore`
- `AndroidKeystoreSecretStore`
- 手写 SQL 或 schema JSON。

### 2.2 Repository 是 UI 的业务入口

当前稳定入口由 `DataModule` 统一装配：

```text
DataModule.appPreferencesRepository(context)
DataModule.sessionRepository(context)
DataModule.messageRepository(context)
DataModule.llmProfileRepository(context)
DataModule.chatGenerationRepository(context)
DataModule.backgroundGenerationRepository(context)
DataModule.localDataWipeRepository(context)
DataModule.nativeImportRepository(context)
DataModule.nativeExportRepository(context)
DataModule.sourceRepository(context)
DataModule.memoryRepository(context)
DataModule.graphRepository(context)
```

前端重构可以替换页面、导航、状态组织和文案，但这些 Repository 的职责边界保持不变。

## 3. 后端协议接口层定型

### 3.1 Domain model 定型

`core:model` 是 UI 与数据层之间的稳定类型边界。

稳定 domain 类型：

| 文件 | 冻结类型 |
|---|---|
| `ConversationModels.kt` | `TutorSession`, `Message`, `MessageRole`, `MessageAttachment`, `MessageQuote`, `SessionSettings` |
| `LlmProfile.kt` | `LlmProfile`, `LlmProviderKind` |
| `MemoryModels.kt` | `MemoryItem`, `MemoryItemKind`, `Anchor`, `Note`, `ErrorLog` |
| `GraphModels.kt` | `GraphNode`, `GraphNodeKind`, `GraphNodeStatus`, `GraphEdge` |
| `SourceModels.kt` | `SourceRecord`, `SourceType`, `SourceParserStatus`, `SourceChunk` |
| `OperationalModels.kt` | `BackgroundJob`, `BackgroundJobKind`, `BackgroundJobStatus`, `ImportBatch`, `ImportMode`, `ImportStatus`, `ExportRecord`, `ExportStatus` |
| `Space.kt` | `Space`, `SpaceKind` |

稳定枚举值：

```text
MessageRole = User, Assistant, System, Tool
LlmProviderKind = OpenAiCompatible, AnthropicCompatible, Gemini, DeepSeek, FreeGlm, Local, Custom
MemoryItemKind = Requirement, Note, Error, Summary, Fact
GraphNodeKind = Concept, Requirement, Source, Session, Person, Other
GraphNodeStatus = Active, NeedsReview, Approved, Hidden, Archived
SourceType = JsonExport, Pdf, Docx, Text, Markdown, Html, Pptx, Epub, Image, Other
SourceParserStatus = FullyLocal, PartiallyLocal, FutureAssisted, Unsupported, Failed
BackgroundJobStatus = Queued, Running, Completed, Failed, Cancelled, Discarded
SpaceKind = Default, Imported, Archive
```

规则：

- UI 显示中文文案时，应在 UI 层做 enum 到中文标签的映射，不要改 enum 名称。
- Entity 字段和 domain 字段之间的映射由 `core:data/local/entity/Entities.kt` 负责。
- 新增 enum 值会影响数据库字符串、协议兼容和 UI 文案，必须单独走后端协议变更。

### 3.2 Session 和 Chat 入口

稳定入口：`SessionRepository`

```text
ensurePreviewSeed(nowEpochMillis)
createSession(input, nowEpochMillis, sessionId)
getSessionSettings(sessionId)
saveSession(session)
getSession(id)
listSessions(spaceId)
renameSession(id, title, updatedAtEpochMillis)
setPinned(id, pinned, updatedAtEpochMillis)
archiveSession(id, updatedAtEpochMillis)
```

稳定输入/输出：

```text
SessionCreationInput(title, role, goal, profileText, templateId?, sourceHandoffRequested)
CreatedSession(session, settings)
```

前端规则：

- 创建会话必须提供 `title`、`role`、`goal`、`profileText`，空值会被拒绝。
- 默认空间 ID 为 `default-space`，前端不应自行构造其他空间 ID。
- `saveSession` 属于底层兼容入口，普通 UI 优先使用 create/rename/pin/archive。

稳定入口：`MessageRepository`

```text
saveMessage(message)
listMessages(sessionId)
listMessageRecords(sessionId)
sendUserMessage(sessionId, text, nowEpochMillis, messageId, quote, attachments)
deleteMessage(id)
```

稳定输入/输出：

```text
MessageRecord(message, attachments, quote)
MessageAttachmentDraft(name, mimeType?, uri?, sourceId?)
MessageQuoteDraft(quotedMessageId, excerpt)
```

前端规则：

- 聊天页渲染优先使用 `listMessageRecords`，不要分别拼 DAO 结果。
- 发送消息必须走 `sendUserMessage`，这样 quote 和 attachment 才会一起规范化和持久化。
- 空文本且无附件的消息不会被写入。

### 3.3 LLM 配置和生成入口

稳定入口：`LlmProfileRepository`

```text
saveProfile(input, nowEpochMillis, profileId)
listProfiles(spaceId)
activateProfile(profileId, nowEpochMillis)
deleteProfile(profileId)
getSecret(ref)
```

稳定输入：

```text
LlmProfileInput(name, provider, model, baseUrl?, apiKey?)
```

规则：

- API key 不进入 Room profile 明文字段，只通过 `SecretStore` 存储，并在 profile 中保存 `secretRef`。
- 删除 profile 时必须删除对应 secret。
- 导出 profile 时默认排除或脱敏 secret。

稳定能力：`core:llm`

```text
LlmCapabilities(supportsVision, supportsJsonMode)
LlmProfileValidator.validate(draft)
LlmProfileCapabilityResolver.infer(profile)
LlmGenerationPlanner.plan(...)
LlmGenerationRuntime.generate(request)
```

当前 runtime 状态：

- `DataModule` 仍使用 `FakeLlmGenerationRuntime()` 装配 preview 生成。
- `OpenAiCompatibleGenerationRuntime` 和 `AnthropicCompatibleGenerationRuntime` 已有 payload builder，但真实 provider call 在 preview 中返回禁用失败。
- UI 不应宣称已接入真实在线模型，除非后续单独完成 runtime 切换和验证。

稳定入口：`ChatGenerationRepository`

```text
generateReply(input, nowEpochMillis, isTokenCurrent, canPersistResult)
```

稳定输入/输出：

```text
ChatGenerationInput(sessionId, userMessageId, userText, token, capabilities?, quoteExcerpt?, imageAttachments, contextEvidence)
ChatGenerationOutcome = Generated | ProviderFailed | NoModelConfigured | UnsupportedVision | BlankPrompt | Stale
```

前端规则：

- 前台聊天 UI 可展示这些 outcome，但不应绕过 token/current-session 校验。
- 图片附件只有在 provider capability 支持 vision 时才允许生成，否则返回 `UnsupportedVision`。
- 上下文证据最多进入 6 条，生成结果会附带 Sources footer。

### 3.4 后台生成入口

稳定入口：`BackgroundGenerationRepository`

```text
enqueueGenerationJob(input, nowEpochMillis, jobId)
getJob(jobId)
recoverInterruptedGenerationJobs(nowEpochMillis)
cancelSessionGenerationJobs(sessionId, nowEpochMillis)
runGenerationJob(jobId, nowEpochMillis)
```

稳定输入/输出：

```text
BackgroundGenerationInput(spaceId, sessionId, userMessageId, userText, token, capabilities?, quoteExcerpt?, imageAttachments, contextEvidence)
BackgroundGenerationJob(...)
BackgroundGenerationOutcome = Completed | Failed | Discarded | Cancelled | MissingJob
```

规则：

- 生成 job 持久化后才执行 provider 调用。
- 结果写入前必须检查 session 仍存在且未归档。
- 结果写入前必须检查 generation token 仍是当前 session 最新 token。
- 被取消、过期、session 不可用的结果只能进入 Cancelled/Discarded，不得生成 assistant message。

### 3.5 Context、Memory、Graph 入口

稳定入口：`MemoryRepository`

```text
snapshot(spaceId)
createAnchor(input, nowEpochMillis, spaceId, anchorId)
createNote(input, nowEpochMillis, spaceId, noteId)
logError(input, nowEpochMillis, spaceId, errorId)
updateNote(id, title, body)
deleteNote(id)
deleteAnchor(id)
resolveError(id, resolved)
```

稳定输入：

```text
AnchorInput(title, body, sourceMessageId?, sourceId?)
NoteInput(title, body, sourceMessageId?)
ErrorLogInput(title, detail, sourceMessageId?)
```

规则：

- Anchor/Note/Error 写入时会同步生成 `memory_items` 聚合记录。
- 空标题或空正文的输入不写入。

稳定入口：`GraphRepository`

```text
snapshot(spaceId)
saveNode(input, nowEpochMillis, spaceId)
updateNode(input, nowEpochMillis, spaceId)
saveEdge(input, nowEpochMillis, spaceId)
```

稳定输入：

```text
GraphNodeInput(id, label, kind, status, sourceMemoryId?)
GraphNodeEditInput(id, label, kind, status, sourceMemoryId?)
GraphEdgeInput(id, fromNodeId, toNodeId, relation, sourceMemoryId?)
```

规则：

- 当前 graph 支持 snapshot、node 新增/编辑、edge 新增。
- 当前没有 node/edge 删除仓库入口，前端不得假装有删除能力。
- Graph UI 的布局、筛选、中文标签可以重构，但持久化仍是 `graph_nodes` / `graph_edges`。

### 3.6 Source Library 入口

稳定入口：`SourceRepository`

```text
listSourcesWithChunks(spaceId)
importSource(input, nowEpochMillis, spaceId)
reprocessSource(sourceId, nowEpochMillis)
```

稳定输入/输出：

```text
SourceImportInput(requestId, fileName, mimeType?, uri?, text?, sourceId?)
SourceImportResult(source, chunks, warnings, errors)
SourceWithChunks(source, chunks)
```

当前本地解析策略：

| 类型 | 状态 |
|---|---|
| TXT | `FullyLocal` |
| Markdown | `FullyLocal`，轻量去除 markdown 标记 |
| HTML | `PartiallyLocal`，本地去脚本/样式/标签 |
| JSON export | `FutureAssisted`，提示归属导入导出 |
| PDF/DOCX/PPTX/EPUB/Image | `FutureAssisted` |
| Other | `Unsupported` |

规则：

- 文件不能在 UI 中静默消失。即使无法解析，也要显示 parser status、warnings 或 errors。
- 当前 chunks 以约 900 字符分段，token 估计为 `text.length / 4`。
- Source Library UI 可以重新设计，但不能改 parser 语义来伪装能力。

### 3.7 导入导出入口

稳定入口：`NativeImportRepository`

```text
dryRun(json, sourceFileName, nowEpochMillis, mode)
importJson(json, sourceFileName, nowEpochMillis, mode, overwriteConfirmed, batchId)
```

稳定模式和状态：

```text
NativeImportMode = Append("append"), Overwrite("overwrite"), NewSpace("new_space")
NativeImportStatus = DryRun("dry_run"), Completed("completed"), Partial("partial"), Failed("failed")
```

规则：

- 导入必须先 dry run，再由用户选择 append/overwrite/new space。
- Overwrite 必须带 `overwriteConfirmed = true`，否则失败。
- 写入通过 `RoomNativeImportStore.writeImport` 在单个 Room transaction 内完成。
- 导入只写当前支持记录：sessions、session_settings、messages、llm_profiles、import_batches、space。
- 导入 LLM profile 不导入 raw secret。

稳定入口：`NativeExportRepository`

```text
currentSession(sessionId, createdAt, targetFileName)
fullBackup(createdAt, spaceId, targetFileName)
graphSnapshot(createdAt, spaceId, targetFileName)
```

稳定输出：

```text
NativeExportResult(kind, targetFileName, documentType, json, snapshot, validation, warnings, errors)
```

规则：

- 导出必须通过 `ProtocolExportPayloadBuilder` 生成 JSON，并通过 `ProtocolExportPayloadValidator` 验证。
- LLM profile secret 只允许 `excluded` / `redacted` / `none` 状态，不输出 `secretRef` 或 `apiKey`。
- 当前 `NativeExportRepository` 不暴露 preset export；`core:protocol` builder 保留 preset payload 能力。

### 3.8 偏好设置和本地擦除入口

稳定入口：`AppPreferencesRepository`

```text
preferences: Flow<AppPreferences>
setTheme(theme)
setGlobalAvatarVisible(visible)
updateMemo(slot, value)
resetToDefaults()
```

稳定 DataStore key：

```text
theme
global_avatar_visible
memo_primary
memo_secondary
memo_scratch
```

稳定 enum：

```text
ThemePreference = System, Light, Dark, Focus
MemoSlot = Primary, Secondary, Scratch
```

稳定入口：`LocalDataWipeRepository`

```text
wipeLocalData(nowEpochMillis)
```

规则：

- 本地擦除必须删除 secret refs。
- 本地擦除必须清空 Room 用户表。
- 本地擦除必须 reset DataStore preferences。
- 本地擦除后必须 reseed default preview data。

## 4. Protocol contract 定型

### 4.1 已验证 schema

`ProtocolModule` 中当前可验证的 schema：

| Schema | Type | Version | 用途 |
|---|---|---:|---|
| `reverse_tutor_export_v1` | `legacy_export` | 1 | 旧导出 wrapper |
| `reverse_tutor_full_backup_v1` | `full_backup` | 1 | native full backup |
| `reverse_tutor_session_export_v1` | `session_export` | 1 | 单会话导出 |
| `reverse_tutor_graph_snapshot_v1` | `graph_snapshot` | 1 | 图谱快照导出 |
| `reverse_tutor_preset_v1` | `preset` | 1 | 会话预设 |
| `native_llm_profile_v1` | `llm_profile` | 1 | LLM profile 元数据 |
| `native_import_result_v1` | `import_result` | 1 | native 导入结果 |

`ProtocolModule` 中保留但尚未纳入 `VersionedProtocolSchemas.all` 的常量：

```text
native_graph_node_v1
native_graph_edge_v1
native_turn_request_v1
```

这些属于预留协议名，不是当前可导入/导出的稳定 JSON 顶层文档。

### 4.2 顶层字段规则

所有已验证 schema 必须包含：

```text
schema: string
version: number
type: string
```

各文档额外必填字段：

| Type | 必填字段 |
|---|---|
| `legacy_export` | `created_at`, `payload` |
| `full_backup` | `created_at`, `sessions`, `llm_profiles`, `graph` |
| `session_export` | `created_at`, `session`, `messages` |
| `graph_snapshot` | `created_at`, `nodes`, `edges` |
| `preset` | `title`, `role`, `goal`, `profile` |
| `llm_profile` | `id`, `name`, `provider`, `api_type`, `model`, `secret_status` |
| `import_result` | `status`, `mode`, `inserted_counts`, `skipped_counts`, `warnings`, `errors` |

未知顶层字段：

- 不直接失败。
- 产生 deterministic warning。
- 不应被导入器盲目写入 unsafe 字段。

Secret 策略：

- JSON key 名看起来像 key/secret/token/password/credential/authorization 时拒绝。
- JSON value 看起来像 `sk-*`、bearer token、api key/secret/token/password 时拒绝。
- validation result 必须提供 `redactedJson`。

### 4.3 Import reader 当前写入能力

`ProtocolImportReader.read(json)` 当前行为：

| Type | 当前导入写入能力 |
|---|---|
| `session_export` | 读取 session 和 messages |
| `full_backup` | 读取 sessions 和 llm_profiles；graph payload 只给 warning |
| `preset` | 转为一个 session 和 system prompt |
| `llm_profile` | 读取 profile 元数据，不含 secret |
| `legacy_export` | 只验证 wrapper，当前不写入 records |
| `graph_snapshot` | 只验证，当前没有 writable import records |
| `import_result` | 只验证，当前没有 writable import records |

前端导入 UI 必须展示 dry-run counts、warnings、errors，不得只显示一个成功/失败 toast。

### 4.4 Export builder 当前能力

`ProtocolExportPayloadBuilder` 支持：

```text
currentSession(createdAt, session, messages)
fullBackup(createdAt, sessions, llmProfiles, graph)
graphSnapshot(snapshot)
preset(preset)
```

`NativeExportRepository` 当前 UI 可用能力：

```text
currentSession
fullBackup
graphSnapshot
```

所有导出 JSON 必须通过 `ProtocolExportPayloadValidator`。

## 5. 数据库/数据层定型

### 5.1 Room schema

当前数据库：

```text
databaseName = reverse_tutor_native.db
Room database = ReverseTutorDatabase
DatabaseSchema.version = 2
DatabaseSchema.exportSchema = true
migrations = [migration1To2]
```

当前迁移：

```text
1 -> 2:
  background_jobs.startedAtEpochMillis INTEGER NULL
  background_jobs.userMessageId TEXT NULL
  background_jobs.userText TEXT NULL
  background_jobs.generationToken TEXT NULL
  background_jobs.quoteExcerpt TEXT NULL
  background_jobs.imageAttachmentsPayload TEXT NULL
  background_jobs.contextEvidencePayload TEXT NULL
```

规则：

- schema v2 作为当前 native UI 重构前的冻结基线。
- 任何表结构变化必须 bump version，补 Migration，更新 exported schema JSON，补 migration test。
- 不允许为了 UI 重构使用 destructive migration。
- 不允许删除 v1->v2 migration。
- 新字段优先 nullable 或有默认值，避免破坏旧安装升级。

### 5.2 当前表

| Table | Entity | 用途 |
|---|---|---|
| `spaces` | `SpaceEntity` | 顶层用户可见数据分区 |
| `sessions` | `SessionEntity` | 会话列表和归档/置顶 |
| `messages` | `MessageEntity` | 聊天消息 |
| `message_attachments` | `MessageAttachmentEntity` | 消息附件，含图片/Source 引用 |
| `message_quotes` | `MessageQuoteEntity` | 引用消息片段 |
| `llm_profiles` | `LlmProfileEntity` | LLM profile 元数据和 `secretRef` |
| `session_settings` | `SessionSettingsEntity` | 会话 prompt/profile 设置 |
| `anchors` | `AnchorEntity` | Context Hub anchor |
| `notes` | `NoteEntity` | Context Hub note |
| `error_logs` | `ErrorLogEntity` | 错误/问题记录 |
| `memory_items` | `MemoryItemEntity` | Memory 聚合索引 |
| `graph_nodes` | `GraphNodeEntity` | Knowledge Graph 节点 |
| `graph_edges` | `GraphEdgeEntity` | Knowledge Graph 边 |
| `sources` | `SourceEntity` | Source Library 文件/资料记录 |
| `source_chunks` | `SourceChunkEntity` | Source 分段文本 |
| `background_jobs` | `BackgroundJobEntity` | 后台任务，当前重点是 Generation |
| `import_batches` | `ImportBatchEntity` | 导入审计记录 |
| `export_records` | `ExportRecordEntity` | 导出审计记录 |

所有用户数据表必须带 `spaceId`。`SchemaPolicyTest.userOwnedEntitiesCarrySpaceId` 已覆盖这一点。

### 5.3 DAO 是数据层内部接口

当前 DAO：

```text
SpaceDao
SessionDao
SessionSettingsDao
MessageDao
MessageAttachmentDao
MessageQuoteDao
LlmProfileDao
MemoryDao
GraphDao
SourceDao
BackgroundJobDao
ImportBatchDao
ExportRecordDao
```

规则：

- DAO 只给 Repository 使用。
- feature/UI 模块不直接调用 DAO。
- DAO SQL 变化视为数据层变更，必须带 Repository/test 变更说明。

### 5.4 Space 规则

当前默认空间：

```text
SessionRepository.defaultSpaceId = default-space
```

导入模式对 space 的影响：

| Mode | Space 行为 |
|---|---|
| Append | 写入 `default-space` |
| Overwrite | 清空目标 space 内相关表后写入 `default-space` |
| NewSpace | 根据源文件和记录生成稳定 `import-space-*` |

规则：

- UI 可以展示空间/导入来源，但不应自行计算导入 space ID。
- Overwrite 是破坏性操作，必须二次确认，并由 `NativeImportRepository` 执行。

### 5.5 Secret 存储

Secret 接口：

```text
SecretStore.put(ref, secret)
SecretStore.get(ref)
SecretStore.delete(ref)
```

Android 实现：

```text
AndroidKeystoreSecretStore
SharedPreferences name = reverse_tutor_llm_profile_secrets
Keystore alias = reverse_tutor_llm_profile_key
Algorithm = AES/GCM/NoPadding on API >= M
Legacy fallback = base64 string with legacy: prefix on older devices
```

规则：

- API key 不进入 DataStore。
- API key 不进入 Room 明文字段。
- API key 不进入 export JSON。
- UI 只能通过 profile 保存流程触发 secret 写入，不能直接管理 secret key/value。

### 5.6 Preferences 存储

DataStore：

```text
name = reverse_tutor_app_preferences
```

当前持久 key：

```text
theme
global_avatar_visible
memo_primary
memo_secondary
memo_scratch
```

规则：

- 新 UI 可以重新设计设置页，但必须保留现有偏好含义。
- 新增用户偏好必须新增 key，并补 policy test，不能复用旧 key 表达新语义。

## 6. 后续前端设计的接入规则

前端方案稍后重新制定时，必须按以下方式接入：

1. 页面和导航可以重做，但只改 `app/shell`、`app/ui`、`app/theme`、`feature/*`。
2. 中文文案、排版密度、移动端交互都在 UI 层解决，不通过改 domain enum/schema 解决。
3. Chat 页面取数用 `SessionRepository` + `MessageRepository` + `BackgroundGenerationRepository`。
4. Context Hub 用 `MemoryRepository.snapshot()` 和 `GraphRepository.snapshot()`。
5. Source Library 用 `SourceRepository.listSourcesWithChunks()`、`importSource()`、`reprocessSource()`。
6. Settings 用 `LlmProfileRepository`、`NativeImportRepository`、`NativeExportRepository`、`LocalDataWipeRepository`、`AppPreferencesRepository`。
7. 如果 UI 需要组合多个 Repository，优先在 feature module 内写 UI coordinator/ViewModel，不要把 UI 状态塞进 core/data。

## 7. 变更审批规则

以下改动不允许混在 UI 重构里：

- 修改 Room Entity 字段、表名、索引。
- 修改 DAO query。
- 修改 `DatabaseSchema.version` 或 migrations。
- 修改 exported Room schema JSON。
- 修改 protocol schema required/allowed fields。
- 修改 import/export secret redaction 策略。
- 修改 `NativeImportRepository` overwrite/new-space 语义。
- 修改 `BackgroundGenerationRepository` token/session 校验。
- 修改 `SecretStore` 存储位置、alias、加密策略。

如确实需要变更，必须单独提交后端/数据变更说明，至少包含：

```text
变更原因
受影响 Repository/API
受影响 protocol schema 或 Room table
兼容策略
新增/修改测试
回滚方案
```

## 8. 验证基线

当前后端协议/数据层的基线测试入口：

```text
mobile-native/core/protocol/src/test/java/com/reversetutor/core/protocol/*
mobile-native/core/data/src/test/java/com/reversetutor/core/data/*
mobile-native/core/data/src/androidTest/java/com/reversetutor/core/data/*
mobile-native/core/llm/src/test/java/com/reversetutor/core/llm/*
mobile-native/core/model/src/test/java/com/reversetutor/core/model/*
```

关键测试：

```text
SchemaPolicyTest
ReverseTutorDatabaseMigrationTest
VersionedProtocolSchemaValidatorTest
ProtocolImportReaderTest
ProtocolExportPayloadBuilderTest
NativeImportRepositoryTest
NativeExportRepositoryTest
BackgroundGenerationRepositoryTest
ChatGenerationRepositoryTest
LlmProfileRepositoryTest
MemoryRepositoryTest
GraphRepositoryTest
SourceRepositoryTest
LocalDataWipeRepositoryTest
AppPreferencesPolicyTest
```

若只改前端 UI，不应触发这些底层 contract 变化。若触发，则说明 UI 重构越界，需要先回到架构评审。

## 9. 当前已知限制

- Graph snapshot 可以导出，但 `ProtocolImportReader` 当前不把 graph snapshot 写入 graph tables。
- Source Library 对 PDF/DOCX/PPTX/EPUB/Image 仍是 future assisted 或附件元数据路径，不是完整本地解析。
- `DataModule` 当前 preview 生成使用 `FakeLlmGenerationRuntime`，真实 provider runtime 尚未切到生产调用。
- `NativeExportRepository` 当前暴露 session/full backup/graph snapshot，不暴露 preset export UI 仓库入口。
- `GraphRepository` 当前没有 delete node/edge 入口。

这些限制不应由前端 UI 文案掩盖。前端可以做清晰的状态展示，但不能把未完成能力表现成已完成能力。
