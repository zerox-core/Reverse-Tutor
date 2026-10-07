# Reverse Tutor Native 三层混合架构设计

日期：2026-07-11
任务代号：`NATIVE-THREE-LAYER-HYBRID-001`

## 1. 目标

为 Reverse Tutor 原生 Android 新分支建立前端、业务后端和数据层三层架构，使应用以本地执行为主，同时能够接入 Python 在线增强服务、在线活动和跨设备同步。

本设计是新分支重构契约。新分支与旧版本在业务模型、接口、数据库或交互上发生冲突时，以本设计和新分支实现为准。

旧版本保留以下作用：

- 作为用户数据迁移和旧备份读取来源。
- 作为已验证产品行为和安全策略的参考。
- 作为迁移完整性、数据不丢失和回归验证的输入。

旧版本的内部类名、Repository 签名、Room 表结构和 UI 导航不构成新分支的冻结约束。

## 2. 架构原则

1. 本地优先：本地学习操作先完成本地事务，不等待网络。
2. 实体分权：本地学习数据、在线服务数据和共享同步数据采用不同的数据主权。
3. 单向依赖：前端依赖业务契约，业务后端依赖 Repository 契约，数据层实现 Repository。
4. 接口优先：三个开发层在共享 domain model 和接口冻结后并行开发。
5. 离线可用：网络不可用不阻塞本地会话、聊天记录、资料、计划、搜索和统计。
6. 显式同步：只有声明为可同步的实体进入 Outbox，不默认上传全部本地学习正文。
7. 安全隔离：API Key、访问令牌和密钥引用不进入普通 Room 表、日志、诊断正文或导出文件。
8. 新架构优先：不为保持旧代码签名而破坏新架构边界，但必须保留旧数据读取和迁移能力。

## 3. 总体结构

```text
Compose UI
  -> ViewModel / UiState / UiAction
  -> Application UseCase / Coordinator
  -> Repository Contract
  -> Local Repository + Remote Repository + Sync Engine
  -> Room / DataStore / SecretStore / Files / Python API
```

模块依赖方向：

```text
app + feature/*
        |
        v
core:domain + core:model
        |
        v
Repository contracts
        |
        +-------------------+
        v                   v
core:data               core:remote
        |                   |
        v                   v
Room/DataStore          Python/API
        \                   /
         \                 /
              core:sync
```

禁止反向依赖。Python 服务不直接依赖 Android 数据库结构，Compose 不直接依赖 DAO、HTTP Client 或 SecretStore。

## 4. 数据主权

### 4.1 本地主权

以下数据默认只保存在设备本地：

- 会话和消息正文
- 本地附件和资料正文
- Memory、随笔和错误记录
- 本地知识图谱
- 本地学习计划
- Token 明细
- 组件布局和浏览位置
- 本地搜索索引

这些数据只有在用户开启相应同步能力后，才生成可同步的摘要或实体包。

### 4.2 服务端主权

以下数据以服务器返回为准：

- 在线活动定义
- 活动开放时间和规则
- 社区公开内容
- 排行榜
- 服务端公告
- 最低兼容版本和更新元数据

客户端可以缓存这些数据，但不能离线修改服务端事实。

### 4.3 共享主权

以下数据允许双向同步：

- 活动参与状态
- 挑战进度和待提交结果
- 用户明确选择同步的学习计划
- 跨设备外观和功能设置
- 用户主动创建的同步摘要

共享实体必须包含：

```text
entityId
entityType
ownerId
deviceId
revision
updatedAt
deletedAt
idempotencyKey
```

默认冲突策略：

- 服务端规则和活动状态：服务器优先。
- 用户设置：最新 revision 优先。
- 计划完成状态：完成优先于未完成。
- 文本内容：不静默覆盖，产生 `SyncConflict` 等待用户选择。
- 删除：使用 tombstone 传播，不通过缺失记录推断删除。

## 5. 前端开发层

### 5.1 文件所有权

```text
mobile-native/app/
mobile-native/feature/*
```

前端 Agent 不修改 `core:data`、Room schema、协议 parser、Python 服务和 SecretStore。

### 5.2 责任

- App Shell、导航、横向工作区和返回链。
- 页面 `UiState`、`UiAction` 和一次性 `UiEffect`。
- 表单输入和即时格式校验。
- 加载、空状态、错误、重试、禁用、离线和同步状态展示。
- 使用 UseCase 或 Facade，不跨层组合 DAO 和远程接口。
- 使用 Fake Repository 完成独立 UI 开发和测试。

### 5.3 业务场景

#### 场景 F1：启动与工作区恢复

前端读取 `ObserveWorkspaceStateUseCase`，恢复默认页面、各页面滚动位置、组件顺序和首次手势提示。恢复失败时使用安全默认布局，不阻塞进入会话首页。

#### 场景 F2：首页会话工作台

前端订阅会话摘要，展示继续学习、全部/置顶筛选、生成状态和当前模型。重命名、置顶、模型切换、导出和删除均提交明确 Action。

#### 场景 F3：模型连接管理

前端编辑 Connection 表单、触发模型发现和连接测试。保存连接不以测试成功为前提。模型级错误只影响对应 ModelBinding。

#### 场景 F4：新建会话

前端维护模板创建和分步创建草稿。创建时提交 `CreateSessionCommand`，成功后进入新会话；失败时保留全部草稿和附件选择。

#### 场景 F5：并发聊天

每条用户消息提交后立即显示对应 TurnRun。多个独立 Run 可同时显示 waiting/running 状态，每个 Run 有独立停止和重试入口。回复固定渲染在原始用户消息之后。

#### 场景 F6：资料与附件

前端通过系统 Picker 获取 URI，将附件草稿交给业务层。解析中、不可解析、缺少权限和模型能力不支持必须有不同状态。

#### 场景 F7：本周副屏

本地统计始终可展示。在线或模型总结不可用时，只降级总结组件，不隐藏学习天数、活跃会话和本地 Token 统计。

#### 场景 F8：全局搜索

搜索结果按会话、消息、资料、Memory、图谱和计划分组。点击结果提交统一 `SearchTarget`，由导航协调器完成定位。

#### 场景 F9：在线活动

有缓存时先展示缓存并标记更新时间。无网络且活动要求在线确认时，前端显示不可提交原因；允许延迟提交的活动进入待同步状态。

#### 场景 F10：同步与冲突

前端显示同步摘要，不展示内部 Outbox 细节。文本冲突进入明确的本地版本/远程版本选择页面，不自动合并正文。

#### 场景 F11：诊断和更新

诊断页面只展示脱敏信息。更新页面展示检查、下载、校验、安装和失败恢复状态，不自行拼接下载地址。

### 5.4 前端验收

- UI 模块不存在 DAO、Entity、SQL、SecretStore 和 HTTP Client import。
- 页面旋转、进程恢复和网络变化不丢失用户草稿。
- Run、同步和解析状态变化不改变列表外部布局尺寸。
- 所有可见操作都连接真实 UseCase 或明确禁用原因。

## 6. 业务后端开发层

### 6.1 文件所有权

```text
mobile-native/core/model/
mobile-native/core/domain/
mobile-native/core/llm/
mobile-native/core/remote/
mobile-native/core/sync/contract/
server.py
adapters/online/
```

业务后端 Agent 定义和实现领域规则，但不直接编写 Compose 或 Room DAO。

### 6.2 责任

- 领域模型、UseCase、Coordinator 和 Repository 接口。
- 本地业务编排与在线增强路由。
- 模型协议、真实运行时、能力和错误分类。
- TurnRun 因果关系、上下文快照、重试和取消规则。
- 在线活动、同步和 Python API 契约。
- 幂等、授权、版本判断和业务级冲突策略。

### 6.3 业务场景

#### 场景 B1：模型发现和测试

`ModelConnectionCoordinator` 根据协议选择 Provider Adapter，执行模型发现或连接测试，将 HTTP 和厂商错误映射为稳定 `ModelConnectionErrorCode`。

#### 场景 B2：模型调用路由

普通聊天默认由 Android 本地 Provider Runtime 直连模型。只有用户启用 Python 增强、活动要求服务端执行或本地能力不足时，才路由到 Python。

#### 场景 B3：TurnRun 创建

`ConversationRunCoordinator` 为用户消息生成 sequence、parentTurnId、contextVersion、modelBindingId 和上下文快照。发送后的 Run 不受后续默认模型切换影响。

#### 场景 B4：因果感知并发

明确引用未完成 Turn 或被分类为连续追问的消息进入 waiting dependency。独立问题立即调度。依赖完成后，以逻辑发送顺序生成上下文，不按响应完成时间排序。

#### 场景 B5：Run 重试

重试创建新的 attempt，保留原始 turnId、用户文本、附件和模型快照。旧 attempt 的迟到结果不得覆盖新 attempt。

#### 场景 B6：周报和建议

`LearningInsightCoordinator` 先计算本地统计，再按缓存周期决定是否请求模型或 Python 增强。模型建议必须处于 Proposed 状态，用户确认后才能成为计划。

#### 场景 B7：在线活动

Python 服务提供活动列表、规则、参与、进度提交和排行榜接口。服务端使用幂等键处理重复提交，并校验活动时间、用户和规则版本。

#### 场景 B8：同步调度

`SyncCoordinator` 拉取服务端变更、处理本地 Outbox、应用实体级冲突策略并更新 SyncCursor。单项失败不阻塞其他实体同步。

#### 场景 B9：删除会话

`DeleteSessionUseCase` 先建立删除 tombstone，取消所有 TurnRun 和调度任务，再调用数据层事务删除。任何迟到回调必须通过会话删除标记和 attempt 校验。

#### 场景 B10：统一错误

业务层将网络、协议、权限、额度、存储和冲突错误映射为稳定 ErrorCode，并声明 `retryable`、`userAction` 和安全展示文本。

### 6.4 Python 在线接口

第一组接口：

```text
GET  /api/v1/activities
GET  /api/v1/activities/{id}
POST /api/v1/activities/{id}/join
POST /api/v1/activities/{id}/progress
GET  /api/v1/activities/{id}/leaderboard
POST /api/v1/sync/push
POST /api/v1/sync/pull
POST /api/v1/insights/weekly
GET  /api/v1/app/releases/latest
```

所有写接口要求用户身份、设备 ID、revision 和 idempotency key。同步接口只接受明确允许同步的实体类型。

### 6.5 后端验收

- 本地聊天在 Python 服务不可用时仍可运行。
- 重复在线提交不会产生重复活动记录。
- 业务错误不向客户端暴露 API Key、完整模型请求或服务器堆栈。
- 并发、依赖、取消、重试和迟到结果均有确定性测试。

## 7. 数据层开发层

### 7.1 文件所有权

```text
mobile-native/core/data/
mobile-native/core/protocol/
mobile-native/core/data/schemas/
```

数据层 Agent 实现已冻结的 Repository 接口，不修改 Compose 页面和 Python 业务规则。

### 7.2 责任

- Room 新 schema、Migration 和导入事务。
- DataStore、SecretStore 和本地文件引用。
- Local/Remote DataSource 和 Repository 实现。
- Outbox、SyncCursor、SyncConflict 和 tombstone。
- 搜索索引、周报缓存、Token 记录和组件偏好。
- 版本化导入导出和旧版本读取。

### 7.3 核心数据模型

```text
provider_connections
model_bindings
turn_runs
study_plan_tasks
weekly_summaries
token_usage_records
widget_layout_preferences
search_documents
sync_outbox
sync_cursors
sync_conflicts
entity_tombstones
```

旧 `llm_profiles` 每行迁移为一个 ProviderConnection 和一个 ModelBinding。旧 session/profile 引用迁移为 session/modelBinding 引用。

### 7.4 业务场景

#### 场景 D1：本地写入

用户创建消息、计划或设置时，在单个 Room 事务内写业务实体和必要的 Outbox。网络失败不回滚业务实体。

#### 场景 D2：连接与模型

Connection 保存 secretRef，ModelBinding 不复制 API Key。删除 Connection 时先检查会话绑定，再按业务命令执行解绑、替换或级联删除。

#### 场景 D3：TurnRun 持久化

TurnRun 保存发送时模型、sequence、parent、contextVersion、attempt、状态和结果消息引用。WorkManager 仅使用 turnRunId 获取执行数据。

#### 场景 D4：会话删除事务

删除事务清理会话、消息、附件、设置、Run、计划关联和搜索文档，同时写 tombstone。外部文件按引用计数决定是否删除。

#### 场景 D5：搜索索引

业务实体写入后同步更新本地搜索文档。索引失败不回滚原业务事务，而是写入待重建标记，由后台任务修复。

#### 场景 D6：Token 记录

每个已完成 attempt 最多生成一条 TokenUsageRecord。记录 provider 原始值、估算标记和输入、输出、缓存、推理分类。

#### 场景 D7：周报缓存

WeeklySummary 使用 `spaceId + weekStart + sourceRevision + generatorVersion` 作为缓存身份。相关数据 revision 改变后标记过期，不立即阻塞 UI 重新生成。

#### 场景 D8：同步 Outbox

Outbox 记录实体快照摘要、操作类型、revision、重试次数和下次重试时间。成功后删除或压缩，永久失败进入可诊断状态。

#### 场景 D9：导入导出

新协议写当前版本，Reader 保留旧版本读取。API Key、SecretRef、登录令牌、Outbox 和诊断正文不进入导出文件。

#### 场景 D10：远程缓存

活动和排行榜采用 stale-while-revalidate。服务端返回的 revision 低于本地缓存时不得覆盖较新缓存。

### 7.5 数据层验收

- 所有 schema 变化包含导出 JSON 和 Migration 测试。
- 旧版本备份和旧 llm profile 可迁移。
- Outbox 写入与本地业务写入保持事务一致。
- 会话删除后，任何 Run 结果都无法重新创建会话数据。
- Secret 不存在于 Room、DataStore、导出和诊断报告。

## 8. 共享契约

主控在并行开发前冻结：

```text
ProviderConnection
ModelBinding
ModelCapabilityState
TurnRun
TurnRunState
ContextSnapshot
StudyPlanTask
WeeklySummary
TokenUsageRecord
SyncEnvelope
SyncCursor
SyncConflict
SearchTarget
DomainError
```

Repository 接口按业务能力拆分：

```text
SessionRepository
ConversationRunRepository
ModelConnectionRepository
StudyPlanRepository
LearningInsightRepository
TokenUsageRepository
GlobalSearchRepository
ActivityRepository
SyncRepository
UpdateRepository
```

接口变更规则：

1. 子 Agent 不直接修改已冻结签名。
2. 确需变更时提交主控评审。
3. 主控先更新契约和 Fake，再通知三个 Agent 同步。
4. 数据层不得返回 Entity，后端不得向前端返回 HTTP DTO。

## 9. 三 Agent 编排

### 9.1 前端 Agent

输入：

- domain model
- UseCase/Repository interface
- Fake implementation
- 页面验收标准

输出：

- Compose 页面和组件
- ViewModel、UiState、UiAction、UiEffect
- UI 单元测试和设备测试

### 9.2 后端 Agent

输入：

- 产品规则
- 共享 domain contract
- 数据 Repository interface
- Python API 边界

输出：

- UseCase 和 Coordinator
- Provider Runtime
- Python 在线 API
- 领域和 API 测试

### 9.3 数据层 Agent

输入：

- domain model
- Repository interface
- migration policy
- sync ownership rules

输出：

- Room schema 和 Migration
- Repository 实现
- Remote DataSource
- 协议、迁移和事务测试

### 9.4 集成顺序

```text
主控冻结共享契约和 Fake
-> 三 Agent 并行
-> 数据层 Repository 实现接入
-> 后端 Coordinator 接入 Repository
-> 前端替换 Fake
-> 主控执行跨层验收
```

## 10. 跨层核心流程

### 10.1 本地聊天

```text
UI Send
-> SendMessageUseCase
-> MessageRepository 本地事务
-> ConversationRunCoordinator
-> TurnRunRepository
-> WorkManager
-> Local Provider Runtime
-> MessageRepository 保存回复
-> UI 观察状态刷新
```

### 10.2 Python 增强

```text
Coordinator 判断需要增强
-> Remote Insight/Activity API
-> Python 服务
-> 脱敏结果
-> 本地缓存
-> UI 展示
```

### 10.3 在线同步

```text
本地事务 + Outbox
-> WorkManager Sync
-> Python Sync API
-> 服务端幂等处理
-> 返回 revision/cursor/conflict
-> 本地应用结果
-> UI 展示同步摘要或冲突
```

## 11. 非目标

- 不默认上传全部聊天和资料正文。
- 不要求 Python 服务才能使用本地学习功能。
- 不保持旧分支内部 API 和 Room schema 的源码兼容。
- 不在首版实现实时多人协作编辑。
- 不使用最后写入覆盖所有文本冲突。
- 不允许三个 Agent 跨越文件所有权并行修改同一模块。

## 12. 完成标准

- 三层依赖方向通过模块依赖和静态检查验证。
- 本地核心流程在断网和 Python 服务不可用时通过。
- 在线活动和同步在重复提交、断网恢复和版本冲突下通过。
- 旧版本数据有明确迁移路径，但新实现不受旧内部契约限制。
- 前端、后端和数据层可以使用冻结契约独立测试。
- 集成测试覆盖配置模型、创建会话、并发聊天、后台恢复、计划周报、同步、冲突和更新流程。
