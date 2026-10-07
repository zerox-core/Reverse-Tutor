# P3-002 冻结层变更申请：后台生成通知偏好

- 变更 ID：P3-002-CR-01
- 提出轨道：Track A（app 装配 / Worker）
- 关联任务：P3-002 后台生成通知与后台设置
- 当前基线：分支 Android，HEAD 95be74d（P3-001 已合并），工作树干净
- 状态：**已批准并实施**（用户于 2026-08-15 批准；冻结层三处改动已落地）

## 1. 变更原因

P3-002 要求后台生成任务在「生成完成 / 生成失败」时发出可配置通知，且为显式 opt-in（默认关闭）。通知开关需要持久化，使关闭并重启应用后用户的选择仍生效。

当前 `AppPreferences` / `AppPreferenceKeys` / `AppPreferencesRepository`（core:data/preferences，冻结层）没有任何字段承载「后台生成通知是否开启」。因此需要一个新增的布尔偏好字段。

## 2. 新增偏好字段名、默认值和兼容策略

| 项 | 值 |
|---|---|
| 字段名（AppPreferences） | `backgroundGenerationNotificationEnabled: Boolean` |
| DataStore key（AppPreferenceKeys） | `booleanPreferencesKey("background_generation_notification_enabled")` |
| 默认值 | `false`（显式 opt-in，符合产品策略） |
| 兼容策略 | 纯新增字段，旧版本写入的 DataStore 不含此 key，读取时回落到默认值 `false`；`persistedNames` 列表追加该 key.name，使导出/清除逻辑覆盖新字段；`resetToDefaults()` 已 `clear()` 全部 key，无需额外改动 |
| Room migration | **不需要**——DataStore Preferences 不是 Room，无 schema 版本变化 |
| SecretStore / 密钥 | **不涉及**——该字段仅是布尔开关，不迁移、不读取、不输出任何密钥 |

## 3. 受影响的冻结层文件（需批准后修改）

| 文件 | 模块 | 改动 |
|---|---|---|
| `core/data/.../preferences/AppPreferenceKeys.kt` | core:data/preferences（冻结） | 新增 `backgroundGenerationNotificationEnabled` key；`persistedNames` 追加其 name |
| `core/data/.../preferences/AppPreferences.kt` | core:data/preferences（冻结） | data class 新增 `backgroundGenerationNotificationEnabled: Boolean = false` 字段；`defaults` 自动覆盖 |
| `core/data/.../preferences/AppPreferencesRepository.kt` | core:data/preferences（冻结） | `preferences` Flow 的 map 中读取新 key（回落默认）；新增 `suspend fun setBackgroundGenerationNotificationEnabled(enabled: Boolean)` |

冻结层改动仅此三处，全部是「新增只读字段 + 新增 setter」，不修改任何已有字段语义、不改既有方法签名、不动 Room/DAO/Entity/SecretStore/协议 DTO。

## 4. 受影响的非冻结层文件（app / feature，批准后实施）

| 文件 | 轨道 | 改动 |
|---|---|---|
| `app/.../preview/background/BackgroundGenerationNotifier.kt`（新增） | Track A | 通知接口 + Android 实现（NotificationManager / Channel / notificationId 派生） |
| `app/.../preview/background/BackgroundGenerationWorker.kt` | Track A | doWork() 终态后读取偏好 + 调用 notifier；Worker 不请求权限 |
| `app/.../preview/shell/AppShell.kt`（hotspot，临时 owner=Track A） | Track A | 设置页新增「后台生成通知」开关、权限缺失说明、跳转系统设置入口；权限请求只在此处发起 |
| `feature/settings/.../FormalSettingsScreen.kt` | Track B（UI） | 新增开关 UI、状态字段、回调参数 |
| `feature/settings/.../SettingsFoundationModels.kt` 或对应 UiState | Track B | 新增 UI 状态字段（如需） |

## 5. 不修改清单（硬约束）

不动 `core:model`、`core:protocol`、`core:llm`、Room schema / Entity / DAO / migration、`core:data/*Repository` 的生成语义、`SecretStore`、`BackgroundGenerationRepository`、PWA、Capacitor、Python 后端、签名。`BackgroundGenerationRepository.runGenerationJob()` 的返回值语义不变——通知完全在 app 层 Worker 消费 outcome，不改 Repository。

## 6. 通知策略（产品默认，如与意图冲突请告知）

1. 总开关默认关闭；不在首次启动或未主动开启时请求 POST_NOTIFICATIONS 权限。
2. 用户开启总开关时，说明通知用途后再请求权限。
3. 通知只用于 Completed / Failed；Cancelled / Discarded / Stale / MissingJob 不发。
4. 通知文本为通用文案，不含用户消息、会话标题、模型名、Provider、URL、错误原文、API Key 或任何密钥引用。
5. 点击通知只打开应用首页（PendingIntent → MainActivity），本阶段不精确跳转。
6. 用户拒绝权限后开关保持关闭并展示说明，不反复弹权限框。
7. Worker 不请求权限；权限请求只由前台设置页发起。
8. notificationId 由稳定 jobId 哈希派生，同一任务不重复通知。

## 7. 测试方案

### 单元测试（JVM，app/feature 模块）
- 通知策略：关闭开关→不发；未授权→不发；成功→发完成通知；失败→发失败通知；取消/丢弃/MissingJob→不发。
- 去重：同一 jobId 派生同一 notificationId，不产生两个有效通知。
- Worker：成功/失败调用 notifier；其余终态不调用。
- 设置状态：默认关闭；拒绝权限后关闭；授权后可启用。

### 设备测试（connectedAndroidTest）
- 开启后完成/失败各一条通知；拒绝权限无通知；通知内容不含用户输入或模型信息。

### 门禁
- `.\gradlew.bat test` 全量 JVM 测试。
- 相关 connected Android tests。
- `git diff --check`。

## 8. 回滚方案

冻结层三处改动均为纯新增字段。回滚 = 删除新增字段/key/setter + 删除 app/feature 新增文件 + 还原 Worker / AppShell / SettingsScreen 改动。由于不改 Room schema、不迁移数据，回滚不丢用户数据（仅丢失通知开关这一布尔偏好）。建议以单独 commit 提交冻结层改动，便于 `git revert`。

## 9. 审批

请用户明确批准后，我才修改 core:data/preferences 三处冻结文件。未批准前，我只完成 app/feature 层设计（本文档）与可独立编译的测试骨架，不触碰冻结层。
