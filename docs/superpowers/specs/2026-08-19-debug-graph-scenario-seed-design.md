# Debug Structured Graph Scenario Seed Design

日期：2026-08-19  
状态：Approved for specification review  
目标构建：Debug / native preview

## 目标

为 Debug 版 Reverse Tutor 提供一组稳定、可重复、可追溯的结构化会话数据，使全局图谱在首次打开时具有真实的多会话关系网络。测试数据只用于本地人工验收和自动化测试，不进入正式构建，不替代真实图谱抽取算法。

## 约束

本任务只允许修改 `mobile-native/app` 的 Debug 组合根、测试和文档，不修改：

- `mobile-native/core/model`
- `mobile-native/core/protocol`
- `mobile-native/core/llm`
- `mobile-native/core/data`
- Room Entity、DAO、schema、migration
- Repository、Facade、Coordinator 的生产签名
- SecretStore、真实 Provider、API key、网络请求

种子器只能通过现有 Repository 能力写入数据，不能直接访问 DAO 或 Room 数据库。

## 方案

新增：

```text
mobile-native/app/src/main/java/com/reversetutor/preview/wiring/DebugGraphScenarioSeeder.kt
```

`DebugGraphScenarioSeeder` 接收 `HybridAppGraph` 暴露的现有 Repository，通过一个公开的 `suspend fun ensureSeeded(nowEpochMillis: Long)` 完成幂等写入。它属于 app/wiring 装配层，不属于图谱算法或数据层。

`MainActivity` 在 Debug 启动协程中调用种子器。调用不阻塞 `setContent`，图谱页面通过现有 Repository/状态流在数据落库后刷新。Release 构建不调用该逻辑。

## 数据场景

所有固定 ID 使用 `debug-graph-seed-v1` 前缀或等价的 `debug-*` 命名，避免与用户数据冲突。

### 会话

创建四个固定会话：

| ID | 标题 | 场景 |
|---|---|---|
| `debug-session-python` | Python 入门路线 | 基础语法、异步编程、数据处理 |
| `debug-session-graph` | 知识图谱算法 | 图布局、节点关系、语义缩放 |
| `debug-session-api` | 百炼 API 接入 | Provider、Qwen 模型、生成运行时 |
| `debug-session-review` | 阶段复盘与下一步 | 汇总前述会话并形成跨会话关系 |

每个会话至少包含一条用户消息和一条 Tutor 消息；消息使用固定 `sourceMessageId` 供记忆和图谱证据回跳。

### 记忆与证据

通过 `MemoryRepository.createAnchor` / `createNote` 写入结构化记忆。每条记忆关联一个已有消息 ID，图谱节点通过 `sourceMemoryId` 关联记忆，从而保留“图谱节点 → 记忆 → 聊天消息”的证据链。

### 图谱

种子器写入约 20 个节点和约 25 条边，覆盖 `GraphNodeKind` 的主要类型，并包含：

- 四个会话根节点；
- Python、图谱、API 三组主题节点；
- 跨会话共享概念节点；
- Provider、模型、来源和需求等节点类型；
- 跨会话边，例如 `Python 基础 → 数据处理`、`图布局 → 节点关系`、`Qwen 模型 → Provider 配置`。

边只引用已写入的节点 ID，关系文本为安全的固定中文短语，不包含 URL、密钥或用户隐私。

## 幂等与生命周期

种子器以固定根节点 `debug-graph-seed-v1` 作为存在性检查：

1. 查询当前默认空间的图谱快照；
2. 若根节点已存在，直接返回 `AlreadySeeded`，不覆盖、不追加重复数据；
3. 若根节点不存在，按“会话 → 消息 → 记忆 → 节点 → 边”顺序写入；
4. 任一步失败时抛出受控错误，由 Debug 日志记录固定安全代码，不能记录原始文本或凭据；
5. 清空本地数据后根节点消失，下次 Debug 启动会重新写入。

种子器不使用持久化的独立 SharedPreferences 标记作为唯一依据，避免清库后标记残留导致图谱为空。

## 启动与契约边界

`MainActivity` 只负责在 `BuildConfig.DEBUG` 下调用种子器；`AppShell`、图谱 UI 和 Repository 不新增测试专用分支。图谱页面继续消费既有 `KnowledgeGraphUiState`、`GraphLayoutNode` 和 `GraphLayoutEdge`。

测试数据不会改变真实 API 接入路径，也不会把 Fake Runtime 的回复写入生产 Provider 配置。

## 验证策略

### JVM

为种子器增加测试，使用现有 Repository fake 或测试数据库验证：

- 首次调用写入四个会话、固定消息、记忆、节点和边；
- 第二次调用不重复写入；
- 边的两端都存在；
- 证据链的 `sourceMemoryId` / `sourceMessageId` 完整；
- 任何敏感字段都不出现在种子文本和日志中。

### Android

在模拟器和主力真机上：

- 安装 Debug APK 后首次启动等待图谱数据刷新；
- 验证全局图谱有多个会话根节点和跨会话边；
- 点击节点打开详情并验证证据入口；
- 重启后数量不增加；
- 清空本地数据后重新启动，种子再次出现。

设备测试结束后必须重新启动 `com.reversetutor.preview/.MainActivity` 并保持主应用前台，不能将测试 Activity 的退出误报为应用打不开。

## 不在范围内

- 修改图谱抽取或布局算法；
- 添加新的协议字段或数据库表；
- 把参考源码的 demo graph 写入生产；
- 自动调用真实 Provider；
- 让 Release 构建携带测试场景。
