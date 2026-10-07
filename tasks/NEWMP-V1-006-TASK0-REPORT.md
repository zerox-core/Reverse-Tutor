# NEWMP-V1-006 Task 0 报告：执行前盘点与边界确认

- 日期：2026-09-06
- 执行者：Aily（本地开发搭档，Cloudflare MCP 通道）
- 依据：`tasks/NEWMP-V1-006-FULL-EXECUTION-CHECKLIST.md` Task 0

## 1. 基线与工作树状态

- 分支：`newmp`，与 `origin/newmp` 同步（无 ahead/behind 标记）。
- 工作树：**干净**。唯一未跟踪文件为本清单自身 `tasks/NEWMP-V1-006-FULL-EXECUTION-CHECKLIST.md`（主控创建，保留不删）。
- 结论：上一阶段（V1-005 及在途改动）已由主控提交完毕，本阶段不存在未说明的并行改动，无覆盖风险。
- HEAD 哈希：MCP 通道 `read_file` 禁止读取 `.git` 内部文件，无法直接取回哈希值；以「分支 newmp = origin/newmp、工作树干净」作为基线锚点。

## 2. 执行环境与设备状态（实测）

| 能力 | 状态 | 证据 |
|---|---|---|
| JVM 全量测试（`gradle test`） | ✅ 可用 | `java_development gradle_test` 实跑 `BUILD SUCCESSFUL in 27s`（465 actionable tasks，exitCode 0），覆盖 app/core:*/feature:* 全部模块 debug+release 单元测试 |
| Android 设备操作（install/instrument/screenshot/adb） | ❌ 阻塞 | `android_development` 全部 action 仍被 `ANDROID_TOOLCHAIN_UNAVAILABLE: ENVIRONMENT_MISSING microsoft.openjdk.17, org.gradle.distribution, google.android.build-tools.35` 门禁拦截（2026-09-06 复测） |
| 环境组件自助安装 | ❌ 阻塞 | `plan_environment_changes` 仍返回服务端 `INTERNAL_ERROR`（retryable=false），`apply_environment_plan` 无从获得 planId |
| 通用 shell（adb / `py -m pytest`） | ❌ 不可用 | 通道无 `execute_command`；本机 PowerShell/adb/Python 无法直接调用 |

设备清单（按清单声明，通道无法实测枚举）：
- 主真机：Huawei BRA-AL00，Android 12 / API 31，`9CN0223C27017326`。
- 虚拟机：`emulator-5554`（API 36，仅作 App 行为验证，不作 Room migration 证据）。

已知影响：Task 7 的设备人工流程与 Task 8 的 Python 回归存在通道级阻塞；JVM 侧（Task 1–6 的 Red/Green、Task 8 的 `gradlew test`）可真实执行。设备与 Python 项将在对应报告中如实标注环境阻塞，不用 JVM 结果冒充。

## 3. 冻结路径确认

以下路径本阶段不改（未经明确批准）：
- `mobile-native/core/model`
- `mobile-native/core/protocol`
- `mobile-native/core/data/preferences`
- `mobile-native/core/llm/SecretStore.kt`
- Room schema/DAO/migration、`core:data/local`

注意：Task 2/4 允许修改 `core/data` 的 ChatGenerationRepository、BackgroundGenerationRepository 与 `core/llm` 的 runtime/transport（清单明确授权），但不触碰 preferences/SecretStore/schema。Task 4 若需持久化 `supportsVision` 字段必须先单独写迁移设计与能力申请——本阶段用保守 profile 识别，不改表。

## 4. 相关已知问题（known-issues，2026-09-06 读，共 39 条）

与本期直接相关的 open 项：
- **RT-2026-037**（open）：真机聊天气泡暴露长分点结构化教学输出 → Task 1 主目标。
- **RT-2026-038**（open）：多模态能力检测拒绝了已批准的 Qwen vision profile → Task 4 主目标（LlmProfileCapabilityResolver 现按模型名标记 `gpt-4o`/`vision` 等识别）。
- **RT-2026-039**（open）：聊天内本地资料入口不可发现 → Task 3 主目标。

相关的 resolved 项（避免踩回坑）：
- RT-2026-032：重开聊天须恢复活动后台任务（`findActiveJobForSession`）。
- RT-2026-033：模型自评证据必须经 `LocalLearningEvidenceVerifier` 才能进台账。
- RT-2026-034：设备测试须用 `createEmptyComposeRule` + 显式 `ActivityScenario`；重启断言用 assistant 消息计数，不用 Fake 文案。
- RT-2026-036：TurnPlan 须经持久化 job 快照传递，不能在 Worker 内从原文重算。
- RT-2026-031：API 36 拒绝 core:data target-23 测试包（`INSTALL_FAILED_DEPRECATED_SDK_VERSION`）——migration 证据只能来自 API≤34 设备。

## 5. 本阶段允许修改范围（按清单）

- Task 1：`core/llm` 的 LlmGenerationLifecycle/LlmAssistantReplyEnvelopeParser、`feature/chat` 的 ChatUiState/ChatScreen 及测试。
- Task 2：`core/llm` generation runtime/transport、`core:data` 的两个 Repository 与临时状态组件、`feature/chat` UI、`app` wiring 及测试。
- Task 3：`app/shell/AppShell.kt`、`feature/chat` 两个 Screen、现有 SourceImport/SessionSettings 适配层及测试。
- Task 4：capability resolver、runtime、payload 构造及测试（保守 profile 识别，不改表）。
- Task 5：`feature/chat` 会话列表模型/状态映射、两个 Screen、`app` 后台任务查询 wiring。
- Task 6：通知策略与 handler（`app`）及测试。

## 6. 停止条件检查结果

- 文件与清单冲突：未发现（PLAN/PARITY-MATRIX 与清单方向一致，PARITY-MATRIX 的 Task 编号与清单不同但能力项一一对应，以清单 Task 0–8 编号为准执行）。
- 未说明的并行改动：无（工作树干净）。
- 设备操作会清理用户数据：本阶段不做任何卸载宿主/清数据的操作；测试包清理仅发生在 instrumentation 之后（本通道当前无法执行 instrumentation）。

## 7. 敏感信息与 git 检查

- `git diff --check`：工作树无修改，无 whitespace 错误可查（后续每 Task 报告再各自执行）。
- 本 Task 未读取、打印任何 key、URL、Authorization、Provider 原始响应或聊天正文。

## 8. 结论

Task 0 通过（盘点完成，无冲突、无并行改动风险）。进入 Task 1。设备侧阻塞已如实登记，将在 Task 7/8 报告中单独标注，不冒充通过。
