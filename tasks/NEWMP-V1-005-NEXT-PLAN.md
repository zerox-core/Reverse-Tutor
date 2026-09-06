# NEWMP-V1-005 下一阶段执行清单

目标：把资料驱动检查从“候选可验证”推进到“真实资料回合可稳定运行”，同时保持算法隐藏、聊天自然、资料版本不串线。

## Task 0：兼容设备迁移验收

- 使用 Android 12/13 真机或 API≤34 AVD 执行 `ReverseTutorDatabaseMigration11To12Test`。
- 记录 serial、API、实际测试数和结果。
- 测试后卸载 instrumentation 包，保留宿主并恢复 `MainActivity` 前台。

## Task 1：真实资料回合编排

- 从当前窗口资料中选择有限 source handles。
- 为资料回合生成受限检查目标；禁止把整段资料直接放进聊天气泡。
- 新资料只影响新回合；已入队任务继续使用旧快照。
- 多资料检查必须逐 handle 校验 revision。
- 资料删除、重处理或不可读时整体返回 Unverified。

## Task 2：模型候选与本地验证联动

- 继续允许模型提出 checkPlan，但模型自评不具备可信状态。
- ExactText、NumericTolerance、RequiredConcepts 由本地验证器决定。
- Rubric、版本漂移、未知规则不写学习事实。
- 验证结果只进入后台台账，不显示在会话页面。
- 增加真实 Qwen 结构化回复兼容测试，不能记录 key、URL 或 raw transcript。

## Task 3：重启、重试和并发安全

- 完成后重启可恢复 artifact 和 checkPlan。
- 重试不重复 assistant 消息、工具 receipt 或学习记录。
- 同一 session 同时只有一个有效生成任务。
- 退出应用或系统回收进程后，任务能安全恢复或标记为可重试。

## Task 4：最小工具体验

- 完善文档创建、读取、局部替换和表格写入。
- 工具调用保持白名单、session 隔离、参数限长和幂等 receipt。
- 引用资料只展示摘要或跳转目标，允许用户关闭资料提示。
- 工具失败不能覆盖正常学生式回复。

## Task 5：统一验收

- 运行四模块 JVM 全量测试、Lint、assembleDebug。
- 在兼容设备执行迁移测试。
- 在虚拟机执行一次应用级资料回合、重启恢复和重复提交场景。
- 输出 `NEWMP-V1-005-EXECUTION-REPORT.md`，区分实际运行、编译、静态检查和环境阻塞。

## 完成标准

用户始终只看到自然的学生式聊天；资料可热更新；旧任务不受新资料影响；模型不能自行宣布掌握；本地验证和台账写入可恢复、可幂等、可追踪。
