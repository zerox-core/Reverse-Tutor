# Reverse Tutor Native 后端协议稳定化设计

日期：2026-07-12  
任务代号：`NATIVE-BACKEND-PROTOCOL-STABILIZE-001`

## 1. 目标

在用户独立完成新前端设计期间，暂停所有前端实现，只稳定原生 Android 后端协议、领域契约和数据持久化边界。

本任务覆盖：

- 模型连接与 Provider Runtime 契约
- `TurnRun` 并发、重试、恢复和模型快照
- 全局图谱与单会话图谱的统一查询协议
- 小组件布局的本地事务性持久化协议
- 导入导出、同步和密钥隔离

## 2. 前端冻结

本任务禁止修改：

```text
mobile-native/app/
mobile-native/feature/
static/app/
mobile/
```

当前前端截图不作为新设计基准。首页挑战入口、侧栏、学习副屏、图谱画布和整体视觉将在用户提交新设计后单独落地。

本任务不得：

- 修改任何 Compose 页面、组件、主题或导航。
- 添加临时 UI、占位卡片或后端返回的固定 UI 文案。
- 根据当前前端结构反向污染后端契约。
- 将小组件布局写入同步 Outbox。

## 3. 图谱查询协议

### 3.1 查询范围

全局图谱和单会话图谱共用一个协议，通过查询范围区分：

```kotlin
sealed interface GraphScope {
    data class Global(val spaceId: String) : GraphScope
    data class Session(val sessionId: String) : GraphScope
}
```

调用方式：

```kotlin
graphRepository.snapshot(GraphScope.Global(spaceId))
graphRepository.snapshot(GraphScope.Session(sessionId))
```

### 3.2 返回结果

后端提供一次性挂起查询：

```kotlin
suspend fun snapshot(scope: GraphScope): GraphSnapshotResult
```

统一返回：

```kotlin
sealed interface GraphSnapshotResult {
    data class Empty(val reason: GraphEmptyReason) : GraphSnapshotResult
    data class Ready(val snapshot: GraphSnapshot) : GraphSnapshotResult
    data class Error(val error: DomainError) : GraphSnapshotResult
}
```

`Loading` 不属于后端结果，由前端自行管理。

### 3.3 空状态原因

节点数量为零时返回：

```kotlin
GraphEmptyReason.NoExtractedNodes
```

该枚举只表达“当前没有生成节点”，不判断聊天数量是否不足，也不判断抽取失败原因。

后端不得返回固定 UI 文案。前端未来可将该原因映射为：

```text
当前聊天记录过少，无法生成节点，请再聊会天吧
```

该文案不进入领域模型、数据库、协议 JSON 或诊断数据。

### 3.4 范围规则

- `Global(spaceId)` 返回指定空间内的全部可见节点和有效关系。
- `Session(sessionId)` 先解析会话及其 `spaceId`，再返回只属于该会话上下文的节点和关系。
- 会话不存在时返回稳定的 `DomainErrorCode.NotFound`。
- 数据读取失败时返回 `GraphSnapshotResult.Error`，不返回空图谱掩盖错误。
- 无效关系不能导致整个图谱失败；结果应保留有效节点和关系，并记录可诊断的无效关系数量。

## 4. 小组件布局协议

### 4.1 独立 Repository

小组件布局不并入 `LearningInsightRepository`，使用独立接口：

```kotlin
interface WidgetLayoutRepository {
    suspend fun load(spaceId: String): List<WidgetLayoutPreference>

    suspend fun saveLayout(
        spaceId: String,
        preferences: List<WidgetLayoutPreference>
    )

    suspend fun reset(spaceId: String)
}
```

### 4.2 保存规则

- 布局只保存在本地，不进入 Sync Outbox。
- 拖动过程只改变前端内存状态。
- 用户松手后提交完整布局列表。
- `saveLayout` 在一个 Room 事务中替换指定空间的完整布局。
- 保存前校验所有记录的 `spaceId` 与参数一致。
- 拒绝重复 `widgetId`。
- 拒绝重复或负数顺序。
- 保存后按 `order` 升序读取。
- `reset(spaceId)` 删除该空间的自定义布局，使调用方恢复默认排列。
- 空列表等价于清除自定义布局。

## 5. 模型连接协议

保持既有分层：

```text
ProviderConnection -> 连接、协议、Base URL、secretRef
ModelBinding       -> connectionId、模型 ID、能力和可用状态
SecretStore        -> 原始 API Key
```

规则：

- `ProviderConnection` 只保存 `secretRef`，不保存原始 API Key。
- `ModelBinding` 不复制 `secretRef` 或 API Key。
- 支持 OpenAI Compatible、Anthropic Compatible 和 Gemini Native。
- 保存连接不以测试成功为前提。
- 模型级错误只更新对应 `ModelBinding`。
- 未知能力不阻止调用。
- Provider 错误映射为稳定的 `ModelAvailability` 和 `DomainError`。
- 自动化测试不调用真实 Provider。

## 6. TurnRun 协议

保持并验证以下不变量：

- 独立问题可以同时处于 `Running`。
- 依赖未完成父 Turn 的追问处于 `Waiting`。
- `sequence` 按用户发送顺序生成。
- 上下文按逻辑发送顺序构建，不按模型完成顺序构建。
- 每个 Run 固化发送时的 `modelBindingId` 和 `contextVersion`。
- 重试保留 `turnId`、上下文快照和模型绑定，只增加 `attempt`。
- 旧 attempt 的迟到结果不能覆盖最新 attempt。
- 已取消、已丢弃或所属会话已删除的 Run 不可写入结果。
- 后台任务恢复后继续使用入队时保存的模型绑定。

## 7. 导入导出与同步

- 导出文件不得包含 API Key、`secretRef`、访问令牌、Sync Outbox、Sync Cursor 或诊断正文。
- 导入模型连接后 `secretRef` 必须为空，用户后续重新填写密钥。
- 同步只允许显式白名单实体。
- 小组件布局不在同步白名单中。
- 单个同步实体失败不阻塞其他实体。
- 文本冲突生成 `SyncConflict`，不得静默覆盖。
- 删除通过 tombstone 传播。
- 幂等键必须阻止重复活动进度或重复实体写入。

## 8. 文件范围

允许修改：

```text
mobile-native/core/model/
mobile-native/core/domain/
mobile-native/core/data/
mobile-native/core/protocol/
mobile-native/core/llm/
mobile-native/core/remote/
tests/
docs/superpowers/
.adworkflow/artifacts/NATIVE-BACKEND-PROTOCOL-STABILIZE-001/
```

禁止修改前端路径和旧 PWA/Capacitor。

## 9. 验收标准

### 图谱

- 全局和单会话查询共用 `GraphScope`。
- 节点为空返回 `NoExtractedNodes`。
- 会话不存在返回稳定错误。
- 后端没有固定 UI 文案。
- 有效节点和关系以 `Ready` 返回。

### 小组件

- 整套布局在单事务中保存。
- 重复组件、重复顺序、负数顺序被拒绝。
- 重启后顺序和隐藏状态保持。
- reset 后自定义布局为空。
- 不产生 Sync Outbox。

### 模型与 TurnRun

- 三种模型协议的请求和错误映射测试通过。
- 并发、依赖、重试、取消、迟到结果和进程恢复测试通过。
- 后台恢复继续使用原模型快照。

### 安全与迁移

- 导入导出密钥脱敏测试通过。
- 同步白名单和冲突测试通过。
- Room migration 和 schema 校验通过。
- 不修改任何前端文件。

## 10. 验证命令

```powershell
.\gradlew.bat :core:model:testDebugUnitTest :core:domain:testDebugUnitTest :core:protocol:testDebugUnitTest :core:llm:testDebugUnitTest :core:remote:testDebugUnitTest :core:data:testDebugUnitTest --no-daemon --stacktrace
.\gradlew.bat :core:data:compileDebugAndroidTestKotlin --no-daemon --stacktrace
py -m pytest tests/test_online_hybrid_api.py -q
py -m pytest -q --ignore=tests/test_project_homepage.py
git diff --check
```

自动化验证不得访问真实模型服务或使用用户密钥。
