# Route A · 核心教学闭环最小垂直切片 — 任务简报（v1）

> 面向 codex 的执行简报。背景：Wave 0（A1–A5）与 Wave 1 B0 已完成，审计结论与落地计划已定稿（v2.1）。现在进入第一条真实垂直切片：用 Fake Runtime 验证并跑通最小产品闭环。这份简报把目标、阶段、边界和完成标准写清楚；如果你对其中的顺序、范围或冻结层判断有不同看法，欢迎先提出来对齐，再动手。

## 一、基线与先读材料

- 工作目录：`F:\xw\reverse-tutor`
- 分支：`Android`，HEAD：`93a858a`（已核对：分支正确、工作树干净）
- Wave 0：A1–A5 完成；Wave 1：B0 完成

动手前请先阅读：

1. `AGENTS.md`
2. `tasks/audit-conclusion-and-plan.md`
3. `tasks/wave0-a2-test-baseline.md`
4. `tasks/wave0-a3-freeze-boundary.md`
5. `tasks/wave0-a4-ownership-table.md`
6. `tasks/native-architecture-decision-record.md`
7. `tasks/native-backend-protocol-data-contract-freeze.md`

## 二、本次目标

不是新增审计文档，而是验证并跑通最小产品闭环：

```
配置模型 → 创建 Session → 发送消息 → Fake Runtime 生成回复
→ 持久化消息 → 重启后恢复 → Session 切换/删除不串消息
```

范围限定：不继续做全量审计，不开始 Route B–F，不接入真实 Provider。

## 三、阶段一：定向事实检查

只检查 Route A 相关代码：

- `mobile-native/core/model/`
- `mobile-native/core/domain/`
- `mobile-native/core/data/`
- `mobile-native/core/llm/`
- `mobile-native/feature/chat/`
- `mobile-native/app/src/main/java/com/reversetutor/preview/`

重点查找的符号：

`TutorSession`、`Message`、`LlmProfile`、`ConversationRunCoordinator`、`SessionRepository`、`ConversationRunRepository`、`ChatGenerationRepository`、`BackgroundGenerationRepository`、`LlmGenerationPlanner`、`LlmGenerationRuntime`、`LlmGenerationResult`、`ChatUiState`、`ChatRunsViewModel`、`SessionHomeViewModel`

产出：任务内事实清单即可，不新增大篇幅审计文档。每项记录：

- 入口文件
- 现有实现
- 现有测试
- Fake Runtime 是否可运行
- 缺失能力
- 是否触及冻结层

## 四、阶段二：先跑现有 Route A 测试

先执行能覆盖以下模块的最小 Gradle 测试任务：

- `core:model`、`core:domain`、`core:data`、`core:llm`、`feature:chat`、`app`

如果模块任务名称不同，以 `.\gradlew.bat tasks` 和现有 Gradle 配置为准，不要猜任务名。

记录：通过数量、失败数量、跳过数量、失败测试文件、失败是否由当前工作树造成。

## 五、阶段三：补最小失败测试

只有在确认缺口后才新增测试；先写测试，再改实现。至少覆盖：

1. 无模型配置时，Planner 返回 NoModelConfigured。
2. Fake Runtime 成功生成回复时，Chat 状态从 pending 进入 success。
3. Fake Runtime 失败/超时时，Chat 状态可恢复且保留错误语义。
4. Session 删除后，迟到结果不能写入消息。
5. Session 切换后，旧 Session 的结果不能写入当前 Session。
6. 重启后，已持久化消息仍能恢复到正确 Session。
7. 重试同一 generation attempt 不重复完成。

优先扩展现有测试，不另建平行测试体系。

## 六、阶段四：最小实现

仅实现让上述 Fake Runtime 测试通过所必需的代码。

允许优先修改：

- `feature/chat` 的 state/event/ViewModel/Facade
- `app` 的装配或非冻结表现层
- 测试 fixture 和 fake adapter

禁止直接修改：

- `core:model`、`core:protocol`、`core:llm`
- `core:data/*Repository`、`core:data/local`、`core:data/preferences`
- `SecretStore`
- Room schema / Entity / DAO / migration

如果现有冻结接口无法支撑闭环：

1. 不要自行修改冻结层；
2. 创建一个 capability request，记录缺失接口、调用方、兼容方案和测试；
3. 停在该缺口，不要继续猜测实现；
4. 最终报告中明确列出需要批准的具体冻结变更。

本次暂不接入：真实 OpenAI/Anthropic HTTP、真实 API Key、真实 Provider transport、Room schema 新 migration、PWA/Capacitor、视觉重构。

## 七、阶段五：验证

实现完成后运行 `.\gradlew.bat test`。如果全量测试耗时过长，至少先运行 Route A 相关模块测试，再运行全量测试作为最终门禁。

同时执行边界检查：

- `git diff --check`
- `git status --short --branch`
- 检查 Feature/App 是否出现以下直接依赖：DAO、Entity、ReverseTutorDatabase、DatabaseSchema、SQL、SecretStore、Keystore、协议 DTO

## 八、完成标准

只有同时满足以下条件才算 Route A 完成：

- [ ] Fake Runtime 核心闭环通过
- [ ] Session 切换/删除隔离测试通过
- [ ] 重启恢复测试通过
- [ ] 重试、失败、超时语义通过
- [ ] 未修改冻结层；或已提交明确 capability request 并停在审批门禁
- [ ] 相关模块测试通过
- [ ] 全量 Native JVM 测试通过，或明确记录阻塞原因
- [ ] 没有修改 Python 后端、PWA/Capacitor、签名和数据库 schema
- [ ] 没有执行 commit、push、tag
- [ ] 未开始真实 Provider，未开始 Route B–F

## 九、最终报告格式

只需报告：

1. 实际修改的文件
2. 新增/修改的测试
3. 测试命令与结果
4. Route A 是否真正跑通
5. 是否发现冻结层能力缺口
6. 如果需要批准，给出一条明确的 capability request

---

*简报由「本地开发搭档」根据用户指令整理，2026-08-14。有任何范围或顺序上的异议，建议先在对齐后再动手。*
