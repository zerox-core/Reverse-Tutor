# NEWMP Task 0 验收清单：v10 → v11 真实迁移设备证据

> 用途：Task 0 执行结束后，由 Codex 在最终统一验收中逐项核对。
>
> 范围：仅验证 Room 数据库从 v10 前向迁移到 v11；不代替 V1 会话、富回复、工具、分支或 heartbeat 的功能验收。

## 通过条件

- [ ] 使用 Android 12 / API 31、Android 13 / API 33，或 API 34 以下的 Reverse Tutor 专用设备；设备 serial 与 API 已记录。
- [ ] 未以 API 36 设备、JVM 测试、零测试 UTP 报告或安装前拒绝充当设备迁移通过证据。
- [ ] 以下命令在兼容设备上实际执行，且进程退出码为 0：

```powershell
[Console]::OutputEncoding=[System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding=[System.Text.UTF8Encoding]::new($false)
$OutputEncoding=[System.Text.UTF8Encoding]::new($false)
chcp 65001 | Out-Null
$env:ANDROID_HOME='E:\Android\Sdk'
$env:ANDROID_SDK_ROOT='E:\Android\Sdk'
$env:GRADLE_USER_HOME='E:\Android\Gradle\newmp'
.\gradlew.bat :core:data:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.reversetutor.core.data.ReverseTutorDatabaseMigration10To11Test' --console=plain --no-daemon
```

- [ ] 测试报告明确显示 `ReverseTutorDatabaseMigration10To11Test` 至少执行 1 个测试方法并且 0 失败、0 错误；不得只引用“BUILD SUCCESSFUL”而缺少执行数量。
- [ ] v10 原有 session、message、background job、窗口拓扑和学习台账在迁移后仍可读；v11 新表可创建/读取，且没有 destructive migration。
- [ ] schema `mobile-native/core/data/schemas/com.reversetutor.core.data.local.ReverseTutorDatabase/11.json` 存在并与 `DatabaseSchema.version == 11`、完整 `1→…→11` 迁移链一致。
- [ ] 测试全程未读取 API key、未调用真实 Provider、未记录用户会话原文或 Provider 原文。
- [ ] 设备清理完成：卸载 instrumentation package，重新启用宿主应用并启动 `com.reversetutor.preview/.MainActivity`。

## 必须记录的证据

| 项目 | 最终报告必须包含 |
| --- | --- |
| 设备 | serial、品牌/型号、Android API |
| 命令 | 完整 class-filtered instrumentation 命令 |
| 执行 | 已运行的测试类/方法、测试数量、失败数、错误数 |
| 迁移 | v10 输入事实与 v11 后保留/新建事实 |
| 清理 | 测试包卸载结果、宿主重新启用/前台启动结果 |
| 安全 | Fake/Static runtime、无密钥/无真实 Provider 声明 |

## 阻塞判定

以下任何一项发生时，Task 0 只能标记 `blocked`，不能标记通过：

- 没有 Android 12/13 或 API≤34 可用设备；
- API 36 拒绝 target/min SDK 23 instrumentation APK（RT-2026-031）；
- 安装、UTP 或测试 runner 在执行测试方法前失败；
- 设备未授权、离线、存储不足或宿主应用无法在清理后恢复；
- 仅拥有 JVM/schema/仓库测试结果；
- 无法确认测试真正执行的数量与结果。

## 禁止的假通过

- 不得修改生产 target/min SDK、Room schema、Entity、DAO 或迁移语义，只为让 API 36 安装测试 APK。
- 不得删除迁移断言、使用 destructive migration、扩大超时、或重复安装同一失败 APK 来制造结果。
- 不得使用别的项目模拟器作为 Reverse Tutor 证据。
- 不得以设备已能启动 Debug 宿主 APK 推断 `core:data` migration AndroidTest 已通过。

## 当前状态（2026-09-02）

`passed`：Huawei BRA-AL00（serial `9CN0223C27017326`，Android 12 / API 31）实际执行 `migratesTenToElevenWithoutLosingExistingConversationRows`，共 1 项、0 失败、0 错误。`com.reversetutor.core.data.test` 与 `com.reversetutor.preview.test` 已在真机和模拟器上确认不存在；真机宿主 `com.reversetutor.preview/.MainActivity` 已重新启动。API 36 限制仍记录于 RT-2026-031，但不再阻塞本次迁移 Gate。
