# NEWMP-V1-006 Task 7 报告：设备人工流程验收

## 结论

**环境阻塞，非业务失败。** Debug APK 构建成功（产物就位、可直接安装），但设备侧人工流程（虚拟机 + 华为真机 9CN0223C27017326）无法经现有 MCP 通道自动执行——四条设备通道全部受阻（详见「设备通道状态」）。checklist 各设备步骤的**逻辑行为**已在 Task 1–6 由 JVM 测试固化（对应关系见下），**设备级证据**以人工验收清单交付，等待真机执行回贴后闭环。

## 构建证据（实际执行）

- 命令：`java_development gradle_assemble_debug`（workdir `F:\xw\reverse-tutor-newmp\mobile-native`，等效 `.\gradlew.bat :app:assembleDebug`）
- 结果：`ok=true, exitCode=0, BUILD SUCCESSFUL in 1m 37s, 346 actionable tasks: 40 executed, 306 up-to-date`
- 产物：`mobile-native/app/build/outputs/apk/debug/app-debug.apk`（与 `output-metadata.json` 并列，已用目录列举确认存在）
- 遵守约束：APK 保留于 build 输出目录未移动；未清理任何应用数据；未卸载宿主。

## 补充：Codex 虚拟机实际验证（2026-09-07）

- 设备：`emulator-5554`，Pixel_8_Pro AVD，API 36。
- 部署：模拟器启动后发现宿主应用未安装，因此使用现有 `app-debug.apk` 执行覆盖安装；未执行清数据或卸载。`MainActivity` 启动成功并保持前台。
- 通过：主页可显示既有会话；进入聊天页成功；聊天输入框与发送控件存在；附件菜单实际展开，显示“选择图片”“从手机选择资料”“查看本会话资料”等入口；选择手机资料动作可触发系统资料选择链路；选择图片动作可触发图片选择链路；强制结束后重新启动，应用恢复到会话主页，未出现崩溃或半截 assistant 气泡。
- 未判定：本机 `local.properties` 存在完整 Debug 模型配置，且本次构建的 APK 已包含非空配置字段；但本轮只完成了 UI/生命周期验证，没有发起真实模型请求，因此没有把流式片段、最终 assistant 单条落库、生成中首页热更新或系统通知标记为设备通过。真机当前未出现在 ADB 设备列表，需重新连接后做端到端确认。
- 收尾：宿主 APK 保留且 `com.reversetutor.preview/.MainActivity` 已恢复前台；没有清理应用数据。

## 设备通道状态（四次实测记录）

1. `android_development`（install_apk / list_devices / test_instrumented 等）：被 **ANDROID_TOOLCHAIN_UNAVAILABLE** 门禁拦截——catalog 期望 microsoft.openjdk.17 注册表 + Gradle 固定路径 + build-tools 35 于 %LOCALAPPDATA%，与项目实际使用的 `E:\Android\Sdk` 指向不一致（Task 0 实测）。
2. `plan_environment_changes`（解锁路径）：三种不同输入均返回服务端 **INTERNAL_ERROR retryable=false**（Task 0 实测，已按 fail-fast 停止重试）。
3. `staging_android_verify`：仅支持服务端注册 Profile（现仅 `zeroxcore`），deviceId 固定 `emulator-5554`，断言链为 zeroxcore 专属——不适用于 reverse-tutor，且无法触达华为真机。
4. 通用 shell：`execute_command` 不在新通道工具集（设计上显式移除），无 adb 入口。

## JVM 层对设备步骤的逻辑等价覆盖

| checklist 设备步骤 | 已固化测试（全绿） |
| --- | --- |
| 流式片段→最终只落一条 assistant | ChatGenerationRepositoryTest 14/14（流式单条落库）+ Task 2 取消路径清 partialStore（BgRepo 19/19） |
| 生成中返回首页，卡片显示生成中 | Task 5 SessionCardGenerationTest 3/3（queued/running→「生成中」，failed→安全失败文案，completed/取消→最新摘要） |
| 重新进入会话状态继续更新 | BackgroundGenerationRepositoryTest recoveryRequeues / reopeningSessionFindsItsNewestActiveBackgroundJob + ChatScreen 恢复 activeBackgroundJobId（既有实现） |
| 强杀后无半截 assistant | Task 2 修复（部分回复不落库）+ 上述恢复测试；LlmAssistantReplyEnvelopeTest 15/15（结构泄漏防护） |
| 切后台不崩溃、状态一致 | WorkManager 后台执行 + Task 6 通知策略测试 10/10（终态通知安全幂等） |
| 聊天内选择资料绑定当前会话 | Task 3 ChatSourcePickImportMapperTest 4/4（快照缺失失败提示，绑定与刷新在 AppShell 既有链路） |
| 图片选择入口（Qwen profile） | Task 4 ProductionLlmGenerationRuntimeTest 13/13（三协议 base64、URI 不外发、不支持时安全失败） |

## 人工验收清单（请在真机 Huawei BRA-AL00 · Android 12 执行，或 API≤34 AVD）

前置：安装 `mobile-native/app/build/outputs/apk/debug/app-debug.apk`（覆盖安装，保留数据，不卸载）。

1. 发送一条短文本，观察 Pending→流式片段→完成，最终聊天中只有一条 assistant 回复。
2. 生成中返回首页：会话卡片显示「生成中」；回到会话状态继续更新。
3. 生成中切后台再回前台：应用不崩溃，状态与消息一致。
4. 强制结束应用后重新打开：无半截 assistant；后台任务恢复显示最终消息或安全失败/可重试状态。
5. 从附件菜单「从手机选择资料」导入：出现「资料已加入本会话」提示，后续问答引用该资料。
6. 选择图片发送：qwen3.7-flash profile 进入生成流程；不支持时显示安全提示（非崩溃、无 URI 泄漏文案）。
7. 生成中/完成后观察系统通知：通用文案、无原问题正文；点击通知进入应用。
8. 虚拟机（如已建 API≤34 AVD）重复 1–6。

回贴格式：每步「通过 / 失败现象（含截图）」，失败时注明是应用行为问题还是环境问题。收到回贴后我更新本报告并纳入终局验收。

## 修改文件清单

- 本报告新增：`tasks/NEWMP-V1-006-TASK7-DEVICE-REPORT.md`。
- 无生产代码改动（本 Task 仅构建与通道验证）。

## 未完成项、根因、下一步

- 未完成：真机/虚拟机人工流程执行与截图证据。根因：四条设备通道受阻（见上）。最小判别实验（同 Task 0 Gate 报告二选一）：① 人工执行上述清单并回贴；② 修复通道（对齐 feishu-mcp catalog SDK 指向至 `E:\Android\Sdk` 并重启服务）后由我自动完成安装与启动验证。
