# P3-003 安全诊断错误记录实施计划

> 执行方式：本窗口逐任务实现和验收；冻结层改动受 `P3-003-CR-01` 约束。

**目标：** 将 Provider 与后台生成失败保存为可检查、可导出且不泄露敏感信息的诊断记录。

**架构：** 在既有 `ErrorLog` 基础上通过来源和固定安全错误码区分生成诊断与学习记录。app 层将失败映射为固定文案；诊断页仅读取生成来源记录，复用现有报告、复制和导出流程。

**技术栈：** Kotlin、Room、Compose、WorkManager、JUnit、Android instrumentation。

---

## 变更文件

- `core/model/.../MemoryModels.kt`：诊断来源类型和 ErrorLog 字段。
- `core/data/.../local/entity/Entities.kt`、`dao/Daos.kt`、`local/DatabaseSchema.kt`、`memory/MemoryRepository.kt`：v5 持久化、迁移和 50 条保留策略。
- `app/.../background/GenerationDiagnosticPolicy.kt`：纯 Kotlin 安全映射，绝不携带原始失败文本。
- `feature/chat/.../ChatScreen.kt`：ProviderFailed 的无原文回调。
- `app/.../background/BackgroundGenerationWorker.kt`：后台 Failed 写入安全记录。
- `app/.../shell/AppShell.kt`、`FormalBatch6RuntimeRoutes.kt`、`feature/settings/.../FormalDiagnosticsScreens.kt`：接线、诊断报告事件和告警呈现。
- 相应 JVM / migration / device 测试及 v5 schema JSON。

## 任务顺序

1. 先写 4 → 5 migration 和 MemoryRepository 的失败测试：旧记录默认为 `Learning`；第 51 条 Generation 记录清除最旧一条而不影响 Learning。
2. 以最小改动加入 `ErrorLogOrigin`、`origin`、`code`、DAO 删除方法、Schema v5 和 Room migration，使测试转绿并导出 v5 schema。
3. 写 `GenerationDiagnosticPolicy` 测试：ProviderFailed / Background Failed 生成固定安全记录；伪 key、Authorization、URL、用户输入不出现在 `title` 或 `detail`；其余 outcome 返回 null。
4. 用该策略把前台 ProviderFailed 和 Worker 后台 Failed 接入 `MemoryRepository.logError`，只传固定字段与本地 message id。
5. 将 diagnostics coordinator 的报告事件替换为 Generation 来源 ErrorLog 的安全投影；复制/导出仅使用该投影，告警文字使用 warning 色。
6. 跑定向 JVM、`gradlew test`、`assembleDebug`、Python 回归、Room 4 → 5 instrumentation；安装到模拟器，触发失败路径确认页面、复制和导出内容。
7. 执行 diff 检查，提交并推送单一 P3-003 commit。

## 失败边界

- 写入诊断记录失败不能掩盖原有聊天或 WorkManager outcome。
- migration 不删除任何现有 ErrorLog；只补充默认字段。
- 诊断保留清理不删除 `Learning` 来源记录，也不影响其他 space。
- 不触及 PWA、Capacitor、Python、SecretStore、签名或真实 Provider transport。
