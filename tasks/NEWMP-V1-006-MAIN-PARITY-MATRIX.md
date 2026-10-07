# NEWMP-V1-006 原分支体验对齐矩阵

目的：先恢复原分支已经验证过的用户体验，再继续扩展 newmp 的隐藏式教学算法。原分支是产品行为基线，newmp 的 Kotlin/Compose 代码是实现载体；不直接复制旧代码，而是保持行为等价。

## 基线证据

- 流式输出与多段消息：原分支 commit `5ce1e1d`（`static/app/index.html`）。
- 回复长度与聊天流程修正：原分支 commit `48f0346`。
- 图片草稿与多模态输入：原分支 commit `acd0aef`。
- 移动聊天流程和后台回复预览：原分支 commit `ce3ed1c`。

## 对照矩阵

| 能力 | 原分支行为 | newmp 当前状态 | 结论 |
|---|---|---|---|
| 流式输出 | 按 chunk 更新临时回复，结束后收敛为完整消息 | runtime 有 `Streamed`/`stream=true`，但 ChatScreen 等最终任务完成后才刷新 | 必须补齐 UI 与 Worker 的 chunk 链路 |
| 多段回复 | 长回复按段落/气泡拆分，用户可逐步阅读 | 当前通常是一条长 assistant 消息 | 必须恢复单回合分段与一个互动点 |
| 回复长度 | 有明确的可见长度限制和 fallback | 教学结构可能完整透出 | 必须隐藏内部字段并增加有界策略 |
| 图片输入 | 选择图片、预览、补充问题、取消/发送 | 有图片选择器和附件模型，但 Qwen 能力识别可能拒绝 | 改为显式 profile capability，并保留安全拒绝 |
| 手机文件导入 | 聊天/资料入口可直接选择本地文件 | 资料导入主要在资料库/会话设置，聊天附件没有完整文件入口 | 接入现有 SourceImport 管线，不复制解析逻辑 |
| 后台生成 | 后台完成后有安全通知和回复预览 | 有后台任务和部分通知类，但聊天首页状态体验未完成对齐 | 补 job 状态热更新与通知收敛 |
| 返回会话 | 返回后可重新看到生成状态和最终回复 | 持久化 job 可恢复，但首页缺轻量进行中状态 | 以 jobId/token 为唯一关联补 UI 状态 |
| 内部信息隐藏 | 用户只看学生式文本，内部状态不直接展示 | 截图确认出现长分点分析/诊断式结构 | 作为当前阶段阻塞项优先处理 |

## 执行顺序

### Task 0：基线盘点

- 核对原分支对应实现和测试，不修改旧分支。
- 为每项能力确定 newmp 的唯一生产接线点。
- 把“已存在底层类型但未接入 UI”与“完全缺失”分开记录。

### Task 1：可见文本与分段对齐

- 先写 envelope → visible chat text 的 Red 测试。
- 内部 `checkPlan`、`outcome`、evidence、action、correctness、mastery 和原始 JSON 不得进入气泡。
- 增加单回合一个教学动作、有限段数、单个互动问题的契约。
- 通过后再做真机人工验证。

### Task 2：流式输出对齐

- 先在 Fake runtime 中产生多个 chunk。
- UI 显示临时 chunk，最终只由 Worker 写入一条 assistant artifact。
- 退出、重试、进程回收不得写入半截消息或重复消息。
- 再接真实 Provider 的 SSE/stream 响应。

当前代码审计补充：`LlmGenerationResult.Streamed` 和请求体的 `stream=true` 已存在，但 `UrlConnectionProviderHttpTransport` 仍用一次性 `readText()` 读取响应；`ChatGenerationRepository` 只在 runtime 返回完整结果后持久化。因此当前“支持 stream”只是协议标志，不是用户可见流式输出。实现时必须新增受 token/jobId 约束的 chunk 回调或进度端口，并明确：临时 chunk 可被丢弃，最终 assistant artifact 仍只能由 Worker 写入一次。

### Task 3：图片与手机文件入口对齐

- 图片：显式 `supportsVision` 能力，补 Qwen-compatible profile 测试和 Fake multimodal 测试。
- 文件：从聊天附件菜单进入系统文件选择器，复用 `SourceImportInputFactory` 和现有 SourceRepository。
- 新资料只影响后续回合，旧排队任务保持快照。

### Task 4：后台状态与通知对齐

- 首页显示安全的“生成中/已完成/失败/可重试”状态。
- 通知只带通用文案，不带原始提问、资料正文、URL、key 或 Provider 诊断。
- 通知点击回到对应会话，同一 job 使用稳定 notification id。

### Task 5：统一验收

- JVM：core:domain、core:llm、core:data、feature:chat、app 受影响测试。
- Android：Huawei BRA-AL00（API 31）人工验证真实聊天、分段、文件、图片、切后台、重进和完全关闭后恢复。
- API 36 AVD 只作 app 行为验证，不作为 Room migration 证据。
- 任何体验能力未达到矩阵标准，不得以“底层已有类型”标记完成。

## 完成标准

用户看到的仍然只是自然聊天：回复逐步出现、每轮留有互动、内部算法不露出；图片和本地文件入口可发现；离开会话后任务状态可恢复；旧分支已经具备的体验不因 newmp 后端升级而倒退。
