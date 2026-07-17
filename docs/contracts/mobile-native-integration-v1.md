# Reverse Tutor Mobile Native 前后端与数据层联调契约 V1

状态：`1.0-draft`

日期：`2026-07-13`

适用范围：`mobile-native` + FastAPI + Room

## 1. 目标

本契约用于让前端、业务后端和数据层并行开发，并在 Figma 页面落地后直接替换 Fake Repository 完成联调。

本契约不要求 Python 服务才能使用应用。会话、消息、世界树、资料、知识图谱和模型直连均本地优先；在线服务只承担活动、公益内容、可选周报增强、版本更新和明确授权的同步。

## 2. 依赖边界

```text
Compose Page
  -> ViewModel / UiState / UiAction
  -> UseCase / Coordinator
  -> Repository interface
  -> Local/Remote Repository implementation
  -> Room / DataStore / SecretStore / Files / FastAPI
```

禁止：

- Compose 或 feature 模块直接依赖 DAO、Entity、SQL、HTTP Client 或 SecretStore。
- FastAPI DTO 直接进入 Compose。
- Room Entity 直接作为 Repository 返回值。
- 为了显示中文而修改 domain enum 或 HTTP wire value。
- 默认上传聊天正文、资料正文、世界树正文或 API Key。

## 3. 数据主权

| 数据 | 主权 | 默认位置 | 是否进入在线同步 |
|---|---|---|---|
| 会话、消息、引用、附件 | 本地 | Room + 文件 URI | 否 |
| 世界树配置、学生角色、画像、剧情 | 本地 | Room | 否 |
| 学生上传资料与解析文本 | 本地 | Room + 本地文件 | 否 |
| 全局/单会话图谱 | 本地 | Room | 否 |
| 模型连接与密钥 | 本地 | Room 元数据 + SecretStore | 否 |
| 学习计划 | 本地/共享 | Room | 用户开启同步后允许 |
| 公益内容、公告 | 服务端 | FastAPI + CDN，客户端缓存 | 服务端下发 |
| 挑战定义、规则、开放时间 | 服务端 | FastAPI，客户端缓存 | 服务端下发 |
| 挑战参与和进度 | 共享 | Room Outbox + FastAPI | 是 |
| 社区公开内容 | 服务端 | 待社区页定稿 | 暂未冻结 |
| 跨设备设置与同步摘要 | 共享 | Room Outbox + FastAPI | 显式白名单 |

## 4. 通用约定

### 4.1 HTTP

- Base URL：`https://<host>/api/v1`
- JSON：UTF-8，字段统一 `camelCase`
- 时间：UTC `epochMillis`
- 鉴权：生产环境 `Authorization: Bearer <token>`；本地开发可使用显式 dev identity
- 请求追踪：客户端可发送 `X-Request-Id`，服务端响应必须回传
- 写接口：必须包含 `deviceId`、`revision`、`idempotencyKey`
- 分页：游标分页，响应返回 `nextCursor`；不得使用不稳定页码处理动态内容
- 未知响应字段：客户端忽略，以支持向后兼容
- 缺失 required 字段：客户端返回 `protocol_error`，不得静默填充错误业务值

### 4.2 标准错误

所有非 2xx JSON 响应使用：

```json
{
  "error": {
    "code": "activity_not_found",
    "message": "Activity not found",
    "retryable": false,
    "userAction": "none",
    "requestId": "req_01J...",
    "details": {}
  }
}
```

`message` 是安全诊断文本，不直接作为最终 UI 文案。前端按 `code` 映射中文。

| HTTP | 稳定 code | retryable | 前端处理 |
|---|---|---:|---|
| 400 | `invalid_request` | false | 保留表单并标出字段 |
| 401 | `unauthorized` | false | 重新登录 |
| 403 | `forbidden` | false | 显示无权限，不自动重试 |
| 404 | `not_found` | false | 返回上级或刷新缓存 |
| 409 | `revision_conflict` | false | 进入冲突处理 |
| 410 | `content_offline` | false | 退出已下线详情 |
| 413 | `payload_too_large` | false | 提示缩小文件或批次 |
| 429 | `rate_limited` | true | 遵守 `Retry-After` |
| 500-599 | `server_error` | true | 展示缓存并允许重试 |

### 4.3 枚举兼容

- 新增枚举值属于向后兼容，但客户端必须提供 `Unknown` 映射。
- 删除、改名或改变枚举语义属于破坏性变化。
- Kotlin enum 与 HTTP wire value在 adapter 层转换，例如 `PublicInterest <-> public_interest`。

## 5. 页面到能力映射

| 页面 | 前端入口 | 本地 Repository | 在线接口 |
|---|---|---|---|
| 会话首页 | `ObserveSessionHomeUseCase` | `SessionRepository` | `GET /content/feed` |
| 挑战页 | `ObserveActivitiesUseCase` | 活动缓存 + Outbox | `/activities/*` |
| 单会话窗口 | `ConversationRunCoordinator` | Session/Message/Run/Source | 默认无 Python 依赖 |
| 学习预设 | `ObserveWorldTreeTemplatesUseCase` | `WorldTreeRepository` | V1 内置模板；在线更新暂不冻结 |
| 自定义世界树 | `EditWorldTreeDraftUseCase` | `WorldTreeRepository` | 无 |
| 全屏图谱 | `ObserveGraphSnapshotUseCase` | `GraphRepository` | 无 |
| 副屏/周报 | `ObserveWeeklyDashboardUseCase` | Insight/Plan/Token | 可选 `POST /insights/weekly` |
| 设置页 | Settings ViewModel | Preferences/Model/Import/Export | `GET /app/releases/latest` |
| 单会话设置 | Session Settings ViewModel | Session/WorldTree/Model | 无 |
| 社区页 | 尚未冻结 | 尚未冻结 | Figma 定稿后追加 |

## 6. 世界树本地契约

现有 `SessionCreationInput(role, goal, profileText)` 只能作为旧数据兼容入口。新前端不得把完整世界树重新拼成三个不可编辑字符串。

### 6.1 Domain model

```kotlin
data class WorldTreeDraft(
    val id: String,
    val spaceId: String,
    val sessionId: String?,
    val templateId: String?,
    val title: String,
    val mode: WorldTreeMode,
    val schemaVersion: Int,
    val sections: List<WorldTreeSection>,
    val sourceIds: List<String>,
    val state: WorldTreeDraftState,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

enum class WorldTreeMode { Learning, Review, Companion }
enum class WorldTreeDraftState { Draft, Ready, Archived }

data class WorldTreeSection(
    val id: String,
    val type: WorldTreeSectionType,
    val title: String,
    val order: Int,
    val payload: WorldTreeSectionPayload,
    val required: Boolean,
    val completed: Boolean
)

enum class WorldTreeSectionType {
    StudentRole,
    LearningGoal,
    StudySchedule,
    PortraitSystem,
    StoryPlot,
    SourceLibrary,
    Custom
}
```

`WorldTreeSectionPayload` 使用 Kotlin sealed interface，至少提供：

- `StudentRolePayload`：头像引用、姓名、性格、当前认知、互动习惯。
- `LearningGoalPayload`：用户自由填写的目标正文和可选验收描述。
- `StudySchedulePayload`：开始/结束时间、每周节奏、阶段节点。
- `PortraitSystemPayload`：可自由增删的画像维度 `key/title/content/order`。
- `StoryPlotPayload`：背景、人物关系、可自由增删的剧情阶段。
- `SourceLibraryPayload`：只保存 `sourceId` 引用，不复制资料正文。
- `CustomPayload`：用户命名标题和自由文本内容。

### 6.2 Repository

```kotlin
interface WorldTreeRepository {
    fun observeDraft(draftId: String): Flow<WorldTreeDraft?>
    suspend fun createDraft(command: CreateWorldTreeDraftCommand): WorldTreeDraft
    suspend fun updateTitle(draftId: String, title: String): WorldTreeDraft
    suspend fun upsertSection(draftId: String, section: WorldTreeSection): WorldTreeDraft
    suspend fun reorderSections(draftId: String, sectionIds: List<String>): WorldTreeDraft
    suspend fun removeCustomSection(draftId: String, sectionId: String): WorldTreeDraft
    suspend fun replaceSourceLinks(draftId: String, sourceIds: List<String>): WorldTreeDraft
    suspend fun attachToSession(draftId: String, sessionId: String): WorldTreeDraft
    suspend fun archive(draftId: String)
}
```

规则：

- 所有编辑先写本地事务，页面不等待网络。
- 信息不完整允许保存 `Draft` 和预览；只有创建正式会话时校验模式所需最低字段。
- `StudentRole` 等默认栏目可清空但不删除；只有 `Custom` 栏目允许删除。
- `reorderSections` 必须拒绝重复、未知或缺失的 section ID。
- `attachToSession` 必须保证一个世界树草稿最多绑定一个活动会话。
- 传给 LLM 的 system prompt 由业务层 compiler 生成，不存为唯一事实源。

### 6.3 Room 目标结构

```text
world_tree_drafts
- id PK
- space_id INDEX
- session_id UNIQUE NULLABLE
- template_id NULLABLE
- title
- mode
- schema_version
- state
- created_at_epoch_millis
- updated_at_epoch_millis

world_tree_sections
- id (与 draft_id 组成复合主键)
- draft_id FK INDEX、复合主键
- space_id INDEX
- type
- title
- order_index
- payload_json
- required
- completed
- updated_at_epoch_millis

world_tree_source_cross_ref
- draft_id FK
- space_id INDEX
- source_id FK
- order_index
- PRIMARY KEY(draft_id, source_id)
```

`payload_json` 必须由 `schemaVersion + type` 的版本化 codec 读写；不得在 UI 中手工拼 JSON。

## 7. 会话创建与编辑

新会话创建命令：

```kotlin
data class CreateSessionFromWorldTreeCommand(
    val draftId: String,
    val titleOverride: String? = null,
    val modelBindingId: String? = null,
    val nowEpochMillis: Long
)
```

业务事务：

```text
校验 draft 存在且可创建
-> 创建 TutorSession + SessionSettings
-> 绑定 WorldTreeDraft.sessionId
-> 建立 Source 引用
-> 编译当前 systemPrompt 快照
-> 返回 CreatedSession
```

规则：

- 失败时保留草稿和资料引用。
- 后续编辑世界树需要显式“应用到后续对话”；不得改写历史 TurnRun 的 `contextVersion`。
- 切换模型只影响新 Run，进行中和重试 Run 继续使用创建时的 `modelBindingId`。

## 8. 图谱契约

继续使用已实现的：

```kotlin
graphRepository.snapshot(GraphScope.Global(spaceId))
graphRepository.snapshot(GraphScope.Session(sessionId))
```

返回：`Empty(NoExtractedNodes)`、`Ready(GraphSnapshot)` 或 `Error(DomainError)`。

前端负责 Loading；后端不得返回 UI 文案。选中节点详情所需的关联目标、资料和剧情应由业务层聚合成 `GraphNodeDetail`，不能让 Compose 直接查询多个 DAO。

建议新增只读接口：

```kotlin
interface GraphDetailRepository {
    suspend fun nodeDetail(scope: GraphScope, nodeId: String): GraphNodeDetailResult
}
```

V1 不提供节点/边删除，不允许 UI 假装删除已经成功。

## 9. 资料契约

- Android 系统 Picker 返回 URI，Repository 负责持久化权限和解析状态。
- `Text/Markdown/Html` 可本地解析；`Pdf/Docx/Pptx/Epub/Image` 当前允许 `FutureAssisted`。
- 世界树只关联 `sourceId`，不复制 `extractedText`。
- V1 不提供学生资料云上传接口；后端不得默认接收学生资料正文。
- 公益内容图片走对象存储/CDN，不进入 Room 和应用服务器硬盘。

## 10. 在线内容

公益内容使用 `/content/feed` 和 `/content/{slug}`。Feed 只返回标题、摘要、模板配置和缩略图元数据，不返回 Markdown 正文。

缓存规则：

- Feed 支持 `ETag` / `If-None-Match`，无变化返回 `304`。
- 客户端启动后检查一次，后台每 6-12 小时最多更新一次。
- 客户端只预加载当前轮播项和下一项。
- 未识别 `illustrationTemplate` 时使用内置静态默认插画。
- 已下线详情返回 `410 content_offline`。

管理发布接口不放入移动客户端 OpenAPI；单独鉴权、单独契约。

## 11. 挑战活动

现有活动 list/detail/join/progress/leaderboard 继续保留，并增加退出参与：

```text
DELETE /activities/{activityId}/participation
```

加入成功后，本地创建挑战会话窗口；退出成功或本地确认退出后移除。重复 join/progress/leave 必须按 `idempotencyKey` 返回同一结果。

活动定义是服务端事实；参与状态和待提交进度是共享数据。离线允许缓存浏览，是否允许离线加入和延迟提交由活动字段明确声明。

## 12. 同步契约

V1 白名单：

```text
activity_progress
study_plan
sync_summary
user_setting
```

禁止同步：

```text
session
message
message_attachment
world_tree
source_body
graph
provider_connection
model_binding
secret
widget_layout
```

Canonical push item result：

```json
{
  "envelopeId": "env_01J...",
  "entityId": "task_01J...",
  "accepted": true,
  "remoteRevision": 4,
  "errorCode": null,
  "retryable": false
}
```

单项失败不能阻塞批次其他项。文本冲突返回 `409 revision_conflict` 或 item-level `errorCode=revision_conflict`，并写入本地 `SyncConflict`，不得静默覆盖。

## 13. 设置和密钥

- ProviderConnection 只保存 `secretRef`；原始密钥只进入 SecretStore。
- 导出、同步、日志、诊断和崩溃上报不得包含 API Key 或 `secretRef`。
- 保存连接不以测试成功为前提。
- 模型发现或测试失败只更新对应连接/模型状态，不删除用户输入。
- 设置页尚未重新定稿，但现有 Repository 边界继续有效。

## 14. 社区契约状态

社区页尚未完成需求和 Figma 定稿，因此 V1 暂不冻结帖子、评论、点赞、关注、发布、审核或媒体上传接口。

后端可以提前完成：

- 通用鉴权、请求追踪、游标分页和标准错误。
- 服务端内容缓存基础设施。
- 对象存储和 CDN 抽象。

不得提前假设社区一定包含评论、私信、关注或用户上传媒体。社区页面定稿后，本目录提升到 `1.1-draft` 并追加接口。

## 15. Fake 与并行开发

前端在真实实现接入前使用 Fake Repository，Fake 必须覆盖：

- 正常、空、加载、离线、协议错误、权限错误和可重试错误。
- 世界树未填写、部分填写、完整和包含自定义栏目。
- 资料 `FullyLocal/PartiallyLocal/FutureAssisted/Unsupported/Failed`。
- 图谱 `Empty/Ready/Error`。
- 活动未加入、已加入、待同步、已退出和活动下线。
- 公益 Feed 缓存、304、新版本、未知插画模板和下线详情。

Mock 值只能用于测试和预览，不进入生产 seed。

## 16. 联调顺序

1. 后端和 Android 先通过 OpenAPI/Mock 对齐字段、枚举和错误。
2. 数据层实现 WorldTree Repository 与迁移，并通过 Repository contract test。
3. 前端接 Fake Repository 完成页面与状态测试。
4. Remote 层接 FastAPI，运行活动、内容、同步和更新契约测试。
5. 前端从 Fake 切换真实 Repository，执行断网、重试、进程恢复和缓存测试。
6. 最后接真实登录和对象存储，不用真实用户资料做自动化测试。

## 17. 验收清单

- [ ] `openapi-online-v1.yaml` 可以被 YAML 解析。
- [ ] FastAPI OpenAPI 与 Canonical 文件无字段漂移。
- [ ] Android decoder 对 Mock 文件全部通过。
- [ ] Kotlin enum 对所有 wire value 有明确映射和 Unknown 策略。
- [ ] Room migration、导出 schema 和回滚策略完整。
- [ ] 世界树草稿在进程重启后不丢失，信息不完整仍可预览。
- [ ] 学生资料正文没有进入 HTTP、同步和日志。
- [ ] API Key、secretRef 没有进入 Room 普通表、导出、同步和诊断。
- [ ] Sync push 返回 `envelopeId + entityId + accepted`。
- [ ] 写接口幂等测试通过。
- [ ] Feed 304、下线 410 和未知模板降级通过。
- [ ] Python 服务不可用时，本地聊天、世界树、资料和图谱仍可使用。

## 18. 变更流程

任何契约变化必须同时提交：

```text
变更原因
受影响页面/UseCase/Repository
受影响 Kotlin domain 或 HTTP schema
数据迁移和兼容策略
Mock 更新
新增或修改的契约测试
回滚方案
```

破坏性变化提升 major；新增可选字段或新接口提升 minor；仅文档澄清提升 patch。
