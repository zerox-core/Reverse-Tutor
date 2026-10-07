# NEWMP-V1-006 开发交接报告

日期：2026-09-09
项目：F:/xw/reverse-tutor-newmp
分支：newmp
当前基线：e3c4632（工作树存在本阶段未提交改动）

## 一、阶段目标

本阶段目标是让聊天保持自然的学生式互动，同时完成流式回复、聊天内资料入口、多模态图片输入、离开聊天后的状态同步和后台通知。教学算法、检索结构、检查计划、学习台账和原始 JSON 均留在后台，用户只看到聊天内容。

## 二、已完成能力

1. 聊天气泡过滤内部教学字段、检查计划标签、结构化结果标签和 envelope 形状 JSON；普通 Markdown、代码块、表格和短资料摘要仍可显示。
2. Provider 支持流式片段；临时片段只存在进程内，最终只由既有 Worker 路径写入一条 assistant 消息；取消、失败、过期和重试会清理临时片段。
3. 聊天附件菜单已提供“从手机选择资料”，复用现有 SourceRepository 导入与会话绑定流程，并对快照缺失给出可重试失败。
4. Qwen 视觉能力识别、content:// 图片转有界 base64，以及 OpenAI-compatible、Anthropic、Gemini 三种图片请求字段已完成；不支持或过大时返回安全错误。
5. 首页会话卡片、聊天页和后台任务共用同一 job 状态来源，Queued/Running/Completed/Failed 有安全映射。
6. 后台通知策略已具备通用文案、稳定通知 id、幂等和权限拒绝不影响业务等行为。

## 三、自动化验证证据

已实际执行并通过：

- core:llm 的 LlmAssistantReplyEnvelopeTest 与 LlmGenerationLifecycleTest
- feature:chat 的 ChatUiStateTest
- 全量 Gradle test、app lint、app assembleDebug
- Python 回归：510 passed，28 skipped
- git diff --check：无空白错误和冲突标记
- 冻结路径检查：core/model、core/protocol、core/data/preferences、SecretStore 无改动

对应批次报告：

- tasks/NEWMP-V1-006-TASK0-REPORT.md 至 TASK7-DEVICE-REPORT.md
- tasks/NEWMP-V1-006-FINAL-ACCEPTANCE-REPORT.md

## 四、虚拟机验证

设备：Pixel_8_Pro，emulator-5554，API 36。

已完成：Debug APK 覆盖安装成功且未清理应用数据；MainActivity 启动成功并保持前台；主页、会话列表和聊天页可打开；附件菜单可见“选择图片”“从手机选择资料”“选择应用内资料”“查看本会话资料”；图片选择和手机资料选择入口可触发；应用强制结束后重新启动正常；APK 保留在 mobile-native/app/build/outputs/apk/debug/app-debug.apk。

未完成的设备证据：本轮没有形成真实模型请求的流式、生成中返回首页、后台通知点击和图片实际生成证据。原因是本轮只做了 UI/生命周期操作，未把一次真实请求结果作为通过依据。当前宿主应用已恢复到前台。

## 五、当前未解决问题

1. 真机 Huawei BRA-AL00（9CN0223C27017326）当前未出现在 ADB 列表，尚无真机人工流程证据。
2. 通知点击目前回到 MainActivity 首页，尚未精确打开产生通知的 session；需要后续增加安全的 sessionId 路由。
3. 真实 Qwen 端到端流式、图片请求和后台生成仍需在已配置模型的设备上实测。配置存在于本地 Debug 构建链路中，禁止把值写入代码、报告或日志。
4. 当前工作树未提交，且包含本阶段代码、测试和报告改动；接手者必须先盘点再决定提交，不得覆盖或重置。
5. 迁移类 instrumentation 仍受 API 36 target-23 安装限制，不能用 API 36 作为 Room migration 证据。

## 六、接手后的建议顺序

### P0：接管与基线

- 读取本报告及 tasks/NEWMP-V1-006-FULL-EXECUTION-CHECKLIST.md。
- 执行 git status --short --branch 和 git diff --stat，确认所有未提交文件。
- 不读取或打印 local.properties 的值。
- 保留现有 APK 和应用数据，不执行 reset、clean 或宿主卸载。

### P1：真实设备体验

- 连接 Huawei BRA-AL00 或已配置模型的 AVD。
- 安装当前 Debug APK（覆盖安装）。
- 按 TASK7 清单验证短消息、流式片段、返回首页、切后台、强杀恢复、资料导入、图片发送和通知。
- 记录每一步通过/失败，失败要区分业务问题和设备环境问题。

### P2：通知精确路由

- 先写失败测试：通知携带 job 对应 session 标识，点击后打开正确会话。
- 只改通知 Intent、MainActivity/AppShell 路由和对应测试。
- 不新增 assistant 写入路径，不把问题正文或资料正文放进通知。

### P3：交付收口

- 重新执行全量 JVM、lint、assemble 和 Python 回归。
- 更新 TASK7 与 FINAL 报告。
- 完成敏感信息和冻结路径检查。
- 由项目负责人决定是否 commit/push，本报告不代替提交授权。

## 七、禁止事项

- 不删除或弱化测试断言。
- 不把 Fake 文案当作真实模型证据。
- 不把流式片段写成多条 assistant。
- 不把教学算法、检索全文、JSON 或 Provider 原始诊断展示给用户。
- 不把 content:// URI 直接发送给 Provider。
- 不记录、打印、提交或推送 API key、真实 URL、Authorization、原始响应、文件正文或私人聊天内容。
- 不为通过 API 36 instrumentation 修改数据库迁移或 target SDK。

## 八、交接结论

代码层和自动化回归已完成，当前阶段可继续进入真实业务体验开发。最终产品验收还差两类证据：一是真机或已配置设备上的真实生成闭环，二是通知点击精确回到对应会话。其余能力已有代码和测试支撑。
