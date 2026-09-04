# NEWMP-V1-002 引导式学习回合算法实施计划

## 目标

把“会话能回复”升级为“会话会教学”：每一轮根据模板、当前窗口可见历史、学习记忆和最近学习结果，选择下一步教学动作；模型只负责表达和候选建议，领域算法负责状态判断、动作选择、证据门禁和记忆更新。

本任务不是增加一段 system prompt，也不是让模型自行决定掌握度。它建立一个可替换、可测试、可解释的回合决策内核。

## 依赖与边界

- 上游依赖：`NEWMP-V1-001` 普通会话闭环、Qwen runtime、窗口可见历史投影、现有 `SessionPolicy` 与 `TurnPlan` 契约。
- 主分支：`newmp`；`main` 仅用于对照旧引导逻辑，不复制旧实现。
- UI 只消费 Feature contract；不得直接读取 DAO、Entity、Database、Worker、Provider 或 SecretStore。
- Worker 仍是后台 assistant 唯一写入者；模型自评不能直接写学习台账。
- 不保存 raw transcript 到 companion memory；上下文只使用当前窗口可见历史和结构化记忆。

## 算法总览

```text
TurnInput
  → VisibleContextProjector
  → LearnerStateReader
  → IntentClassifier（规则优先，模型候选可选）
  → TeachingActionSelector（确定性评分）
  → TurnPlanBuilder（有界计划）
  → LLM 表达
  → LocalEvidenceVerifier
  → LearnerStateUpdater / LearningLedgerProjector
```

## 一、领域输入与状态模型

### 1. `GuidedLearningTurnInput`

字段必须全部是领域值或已脱敏结构化值：

- `sessionId`、`windowId`、`spaceId`
- `sessionTemplate`: 学习目标、学科、水平、语气、交互策略、纠错时机
- `visibleContext`: 当前窗口在 fork 边界内的消息摘要和结构化证据；不得包含兄弟窗口或父窗口 fork 后消息
- `learnerProfile`: 学习者角色、已知基础、偏好难度、语言偏好
- `conceptStates`: 当前窗口和全局学习台账中与目标相关的概念状态
- `recentTurnSignals`: 最近轮次的动作、用户是否回答、是否请求提示、是否连续失败
- `userIntentHint`: 仅用于辅助，不得直接决定教学动作

### 2. `ConceptLearningState`

每个知识点保存以下有界状态：

- `status`: `Unknown / Exploring / Fragile / Stable / Mastered`
- `confidence`: 0..1
- `attemptCount`、`successStreak`、`failureStreak`
- `misconceptionTags`: 白名单标签，不保存整段答案
- `lastEvidenceAt`、`lastAction`
- `sourceWindowId`、`sourceTurnId`

状态变化必须由 `LocalLearningEvidenceVerifier` 的结构化证据驱动；没有证据时只能保持原状态。

## 二、回合状态机

每轮按以下顺序执行，任何一步失败都返回安全的 `TurnPlan` 或安全错误码：

1. `Observe`：读取当前窗口可见上下文和结构化记忆。
2. `ClassifyIntent`：识别用户是在提问、作答、请求提示、要求示例、复盘、改目标、闲聊还是请求工具。
3. `ResolveConcept`：将本轮目标绑定到已有知识点；无法确定时使用 `UnknownConcept`，不创建大量临时节点。
4. `ReadLearnerState`：读取该知识点最近状态、失败模式和动作历史。
5. `SelectTeachingAction`：使用确定性评分选择一个主动作，最多附带一个辅助动作。
6. `BuildTurnPlan`：生成本轮目标、期望用户下一步、输出格式和证据要求。
7. `Generate`：将有界 `TurnPlan` 和脱敏上下文交给现有 LLM runtime。
8. `Verify`：若存在本地可验证结果，运行 verifier；否则标记 `Unevaluated`。
9. `Persist`：由既有单写入者保存 assistant 回复；由 projector 幂等写学习台账。
10. `ScheduleNext`：根据结果决定下一轮继续追问、降低难度、切换示例或结束本节。

## 三、意图分类规则

先执行规则分类，再允许模型提供候选标签；模型标签必须经过白名单和置信度门槛。

- `AnswerAttempt`：用户给出计算、解释、代码或步骤，且与当前知识点相关。
- `AskQuestion`：用户提出新问题或要求解释。
- `AskHint`：出现“提示、不会、下一步、给点线索”等请求。
- `AskExample`：要求例题、反例、代码或可运行样例。
- `Reflect`：要求总结、复盘、比较或检查掌握情况。
- `GoalChange`：改变学科、难度、目标或学习方式；产生 scope signal，不直接覆盖模板。
- `OffTopic`：与当前学习目标无关的内容；只产生软提示，不伪造学习事实。
- `ToolRequest`：要求创建、更新、查询文档/表格或其他已注册工具。

规则分类优先级：明确工具请求 > 明确目标变更 > 作答/提示请求 > 普通问题 > 复盘 > 闲聊。冲突时保留 `ambiguous`，选择澄清动作。

## 四、教学动作选择算法

候选动作白名单：

- `Diagnose`：先问一个最小诊断问题。
- `SocraticQuestion`：通过一步问题让学习者自己推导。
- `Hint`：只给下一步线索，不直接给完整答案。
- `Explain`：对已确认的缺口做分层解释。
- `WorkedExample`：给一个完整但有边界的示例。
- `CounterExample`：展示反例并要求比较。
- `Practice`：生成一题相近练习。
- `Reflect`：要求学习者总结规则或迁移条件。
- `Summarize`：在阶段完成后压缩要点。
- `ClarifyGoal`：目标或范围变化时先确认。

确定性评分建议：

```text
score(action) =
  0.30 * intentFit
  + 0.25 * learnerStateFit
  + 0.15 * recentFailureFit
  + 0.15 * templateStrategyFit
  + 0.10 * novelty
  + 0.05 * formatFit
```

硬约束：

- 连续两轮 `AskHint` 不得直接升级为完整答案，先执行 `SocraticQuestion` 或最小示例。
- `Fragile` 且最近失败时，优先 `Diagnose`/`WorkedExample`，不直接提高难度。
- `Stable` 连续成功时，优先 `Practice`/`Reflect` 做迁移验证。
- 目标发生变化时，先 `ClarifyGoal`，不得由一句话覆盖模板和 companion 设定。
- `OffTopic` 不得生成学习台账事实；可用 `ClarifyGoal` 或温和回到当前目标。

## 五、TurnPlan 输出契约

`TurnPlan` 至少包含：

- `actionType`
- `learningObjective`（≤120 字符）
- `conceptKey`（白名单或 `unknown`）
- `expectedUserMove`（≤160 字符）
- `responseFormat`: `Plain / Steps / Code / Table / Checklist`
- `hintLevel`: 0..3
- `evidenceRequirement`: `None / LocalCheck / UserAnswer / ToolReceipt`
- `relatedness`: 0..1
- `nextActionOnSuccess`、`nextActionOnFailure`

所有文本字段必须 trim、限长、去除 URL、Authorization、Bearer 和 secret-like 片段。

## 六、记忆读取与更新

读取顺序：

1. 当前窗口局部 delta；
2. 当前窗口 fork 时刻以前的可见祖先历史；
3. 学习台账中的相关知识点状态；
4. companion root 的人格、交互频率和全局偏好摘要。

禁止读取：兄弟窗口消息、父窗口 fork 后消息、其他隔离窗口 raw transcript。

更新规则：

- 普通聊天只写会话消息，不写学习事实。
- 模型提出的掌握度、正确率和深度只作为候选，不能直接落库。
- 本地 verifier 产出结构化证据后，`LearningLedgerRepository` append-only 写入。
- 学习结果可以汇入全局学习档案，但不会反向改写下级分支历史。
- companion memory 只接收经策略筛选的偏好/人格/频率 delta，不接收完整答案文本。

## 七、真实模型与工具协作

- LLM 负责语言表达、候选解释和候选工具调用；不能越过 `TeachingActionSelector`。
- 工具调用必须经过工具白名单、参数 schema、大小限制和幂等 receipt。
- 需要代码时使用 `responseFormat=Code`，需要资料时使用 `responseFormat=Table/Checklist`；资料引用以可点击摘要呈现，不把检索原文混进聊天气泡。
- Provider 失败统一映射安全错误；不得把原始异常、URL 或密钥片段送回 UI 或持久化。

## 八、TDD 执行清单

### Task 2.1：建立状态与动作契约

- [ ] Red：覆盖五类意图、五种知识点状态和动作白名单。
- [ ] Green：实现纯 Kotlin `GuidedLearningTurnInput`、`ConceptLearningState`、`TeachingAction`、`TurnPlan`。
- [ ] 验证：`:core:domain:testDebugUnitTest` 定向测试通过。

### Task 2.2：实现确定性动作选择器

- [ ] Red：覆盖连续求提示、连续失败、稳定成功、目标变更和闲聊回流。
- [ ] Green：实现评分选择器和硬约束；不调用 Android、Room、LLM。
- [ ] 验证：动作选择在相同输入下完全确定，边界值有测试。

### Task 2.3：接入可见上下文与记忆读取

- [ ] Red：断言时间线和生成上下文使用同一组可见消息 id；兄弟和 fork 后消息不可见。
- [ ] Green：只接入已有 `WindowVisibleHistoryReader` 与结构化记忆端口。
- [ ] 验证：`WindowVisibleHistoryReaderTest`、`BackgroundTurnPreparationCoordinatorTest` 和新 mapper 测试通过。

### Task 2.4：接入 LLM TurnPlan 与安全表达

- [ ] Red：模型返回无效动作、超长字段、敏感字符串和自评掌握度时必须被拒绝或截断。
- [ ] Green：将 `TurnPlan` 作为可选结构化上下文传入现有 runtime；保留向后兼容。
- [ ] 验证：真实 Qwen 只验证表达质量；学习事实仍必须等待 verifier。

### Task 2.5：本地证据验证与学习台账投影

- [ ] Red：模型自评不能写 mastery；无 evidence 结果零写入；重复 turn 幂等。
- [ ] Green：接入 `LocalLearningEvidenceVerifier` 和既有 `PostTurnProjector`。
- [ ] 验证：`PostTurnProjectorTest`、`LearningLedgerRepositoryTest` 及重启恢复测试通过。

### Task 2.6：真实设备最小验收

- [ ] 新建学习会话，提出一个问题。
- [ ] 故意回答错误一次，确认先诊断/提示，不直接给完整答案。
- [ ] 请求提示，确认提示层级变化。
- [ ] 正确回答后，确认进入迁移练习或复盘。
- [ ] 离开并重新进入会话，确认动作状态和历史保留。
- [ ] 验收只记录行为结果，不记录 API key、Provider 原文或 raw transcript。

## 完成标准

- 相同输入产生稳定、可测试的教学动作。
- 模型不能单独改变学习状态或覆盖模板。
- 引导动作、上下文、记忆和学习台账有可追踪数据流。
- 普通聊天、目标变更、闲聊和工具请求互不误写学习事实。
- 真实 Qwen 只负责生成表达；失败可安全恢复；重启后状态可恢复。
- 前端只需根据 `TurnPlan`、`ChatUiState` 和安全结果契约决定按钮与布局。
