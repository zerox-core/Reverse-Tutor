# Aily 回报：Task 0 · Gate — v10 → v11 真实迁移设备验收

> 依据：`tasks/AILY-NEWMP-NEXT-STAGES.md` Task 0 与文末「Aily 最终回报模板」。
> 日期：2026-09-02。执行者：Aily（本地开发搭档，经 Cloudflare MCP 通道）。

## 回报模板

```text
任务编号：Task 0 · Gate：v10 → v11 真实迁移设备验收
Gate 状态：blocked
实际修改文件：无（未修改任何现有文件；仅新增本报告文件 tasks/AILY-NEWMP-TASK0-GATE-REPORT.md）
Red 测试与实际结果：未执行（设备证据无法产出，见阻塞原因）
Green 实现与实际结果：不适用（未进入实现环节）
定向/模块/全量测试：未执行
设备证据（serial、API、场景、结果、清理）：无——Aily 通道无法枚举设备，亦无法运行任何 adb/设备命令
冻结范围审计：未触碰冻结层；本次仅只读访问了文档、Gradle 构建配置与 E:\Android\Sdk 目录清单
工作树状态：分支 newmp，工作树保持交接时原样（含 Codex/Aily 在途改动，未做任何 git 写操作）
未解决问题：见「阻塞原因」与「下一项最小判别实验」
下一项最小判别实验：见下文第 4 节
明确声明：未 commit、未 push、未 tag、未 reset、未 checkout、未 clean、未 rebase。
```

## 1. 已完成的前置准备（只读）

- 通读 `AGENTS.md`、`docs/PROJECT_DEVELOPMENT_MAINLINE.md`、`F:\CodexHome\skills\fixed-io-encoding\SKILL.md`、`F:\CodexHome\skills\reverse-tutor-development-guard\SKILL.md` 及其 `references/known-issues.md`。
- 确认迁移测试判据：RT-2026-031 记录 `core:data` instrumentation APK（target/min 23）在 API 36 上被平台拒绝（`INSTALL_FAILED_DEPRECATED_SDK_VERSION`），兼容设备只能是 Android 12/13 或 API 34 及以下；JVM 通过、零测试、安装被拒均不算设备证据。
- 确认项目构建环境（只读核实）：Gradle wrapper 8.2.1（`E:\Android\Gradle\newmp\wrapper\dists\gradle-8.2.1-bin` 已缓存）、AGP 8.2.1、Kotlin 1.9.22、`core:data` compileSdk 34 / minSdk 23；`E:\Android\Sdk` 具备 build-tools 34.0.0/35.0.0 与 platform android-34/35，但 system-images 仅有 android-36（无兼容 AVD 镜像）。

## 2. 阻塞原因（Aily 通道无法产出设备证据）

1. MCP 通道（`ms_4kv9r0jh8r306`）无 `execute_command`，无任何通用 shell 入口。
2. `android_development` 全部 action（含 `list_devices`/`test_instrumented`/`uninstall`/`start_app`）被 `ANDROID_TOOLCHAIN_UNAVAILABLE: ENVIRONMENT_MISSING microsoft.openjdk.17, org.gradle.distribution, google.android.build-tools.35` 门禁拦截。另：catalog 指向的 SDK 与项目实际使用的 `E:\Android\Sdk` 不一致——E: 下 build-tools 35.0.0 与 android-35 platform 实际已就位，catalog 却判定为 missing，说明 catalog 解析到的是另一处 SDK（疑似 %LOCALAPPDATA% 下默认位置）。
3. 解锁路径 `plan_environment_changes` 服务端故障：三种不同输入（三组件全集 / 单 JDK / 单 build-tools）均返回 `INTERNAL_ERROR: Tool execution failed`（retryable=false），按 fail-fast 已停止重试。`apply_environment_plan` 因此无从获得 planId。
4. `java_development` 仅提供 JVM 侧固定动作（`gradle_test`/`gradle_build`/`gradle_assemble_debug`），不含 `connectedDebugAndroidTest`，且按 Task 0 规则 JVM 结果本就不算设备证据。
5. 无法枚举已连接设备：真机 `9CN0223C27017326`（Android 12, API 31，历史上为兼容主力设备）当前是否在线未知；E: 上唯一模拟器镜像为 API 36（不兼容，RT-2026-031 已实锤），无 API≤34 的 AVD 可用。

## 3. 禁止的假修复（按主线宪章 §10 与 Task 0 约束，Aily 未做也不应做）

- 不得以 JVM 迁移/仓库回归结果冒充设备证据；
- 不得绕过平台 target-SDK 保护、复用他项目模拟器，或为适配安装错误修改迁移语义、target/min SDK、schema/entity/DAO、现有 AVD 配置；
- 不得在无法证明设备在线的情况下臆断「无兼容设备」——现状是「无法探测」，与「无设备」不同。

## 4. 下一项最小判别实验（交给 Codex / 用户）

任选其一即可解除阻塞：

1. **人工执行（最快、确定性最高）**：在 Windows 连接真机 `9CN0223C27017326`（或先创建 API≤34 的 Reverse Tutor AVD），按 `tasks/AILY-NEWMP-NEXT-STAGES.md` Task 0 步骤 2 的 PowerShell 命令原样运行 `:core:data:connectedDebugAndroidTest`（class 过滤 `ReverseTutorDatabaseMigration10To11Test`），再执行步骤 4 的设备清理；将完整输出回贴给 Aily，由 Aily 核验证据并更新本报告。
2. **修复通道（可让 Aily 后续自动执行）**：对齐 feishu-mcp catalog 的 SDK 指向至 `E:\Android\Sdk`（或按 catalog 期望位置安装 Microsoft OpenJDK 17、Gradle 8.10.2、build-tools 35.0.0）并重启服务，同时排查 `plan_environment_changes` 的 INTERNAL_ERROR；Aily 随后经 `android_development`（`list_devices` → `test_instrumented` → 清理）自动完成验收。注意：即便解锁，仍需先确认存在 API≤34 的可用设备。

## 5. 停止声明

按 `tasks/AILY-NEWMP-NEXT-STAGES.md`「迁移设备门通过前必须停止」与主线宪章 §10「设备证据无法获得时停止」：Task 0 标记 `blocked`，未进入 Task 1–6，无任何代码、测试或文档的实质性修改，工作树保持交接时状态，等待 Codex 审计。

## 6. Codex 验证附录（2026-09-02）

- Gate 状态更新为：`passed`。
- 设备：Huawei BRA-AL00，serial `9CN0223C27017326`，Android 12 / API 31。
- 实际命令：`:core:data:connectedDebugAndroidTest`，class filter 为 `com.reversetutor.core.data.ReverseTutorDatabaseMigration10To11Test`。
- 真机 XML 报告：`migratesTenToElevenWithoutLosingExistingConversationRows`，1 项、0 failures、0 errors。
- API 36 模拟器的 XML 报告仍为 0 项，不作为迁移通过依据。
- 清理：真机与模拟器均确认 `com.reversetutor.core.data.test`、`com.reversetutor.preview.test` 不存在；真机 `com.reversetutor.preview/.MainActivity` 已成功冷启动。
- 本附录未读取密钥、未调用真实 Provider、未记录用户会话或 Provider 原文。
