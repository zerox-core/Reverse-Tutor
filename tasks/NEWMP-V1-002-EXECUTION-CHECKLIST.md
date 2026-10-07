# NEWMP-V1-002 引导式学习算法执行清单

> 适用分支：`newmp`
> 目标：在现有真实会话闭环上接入完整引导式学习回合算法。
> 约束：本轮只执行本清单，不扩展分支窗口、heartbeat、最终视觉或其他新能力。

## 0. 总门禁

- [ ] 确认当前分支为 `newmp`，不合并 `main`。
- [ ] 确认 `mobile-native/local.properties` 只在本机存在且被 Git 忽略；不得把 API key 写入源码、测试、日志、文档或提交。
- [ ] 确认保护路径未改：`mobile-native/core/model`、`mobile-native/core/protocol`、`mobile-native/core/data/preferences`、`SecretStore.kt`。
- [ ] 确认 Worker 仍是唯一 assistant 写入者，Provider 仍只能经过现有 runtime/repository 路径。
- [ ] 确认前端只作为验收入口，不新增后端语义；没有稳定契约时不得先加按钮。
- [ ] 每个任务均按 Red → Green → Refactor → Acceptance 顺序执行。
- [ ] 任一测试失败原因不明、涉及保护路径、或需要伪造结果时立即停止并回报。

## 1. Task 2.1：状态与动作契约

### 允许修改

- `mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/SessionTurnContracts.kt`
- 新增纯 Kotlin 文件：`mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/GuidedLearningContracts.kt`
- `mobile-native/core/domain/src/test/java/com/reversetutor/core/domain/GuidedLearningContractsTest.kt`

### 禁止修改

- Android、Compose、Room、DAO、Repository、Worker、Provider、SecretStore。

### Red

- [ ] 写测试并断言以下类型存在且可归一化：`GuidedLearningTurnInput`、`ConceptLearningState`、`TeachingAction`、`TurnPlan`。
- [ ] 覆盖意图：`AnswerAttempt`、`AskQuestion`、`AskHint`、`AskExample`、`Reflect`、`GoalChange`、`OffTopic`、`ToolRequest`。
- [ ] 覆盖状态：`Unknown`、`Exploring`、`Fragile`、`Stable`、`Mastered`。
- [ ] 覆盖字段上限：`learningObjective ≤ 120`、`expectedUserMove ≤ 160`、`hintLevel 0..3`。
- [ ] 运行：

```powershell
$env:ANDROID_HOME='E:\Android\Sdk'; $env:ANDROID_SDK_ROOT='E:\Android\Sdk'; $env:GRADLE_USER_HOME='E:\Android\Gradle\newmp'
.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.GuidedLearningContractsTest" --console=plain --no-daemon
```

预期：先失败，失败原因必须是契约或归一化逻辑缺失。

### Green / Refactor

- [ ] 实现纯 Kotlin 数据类型和 `normalized()`，不依赖 Android 或 LLM。
- [ ] 对未知枚举、空 concept、负数计数、超长文本提供安全默认值。
- [ ] 重新运行同一命令，预期全部通过。
- [ ] 检查 `rg -n "android\\.|androidx\\.|Room|Dao|Repository|SecretStore|LlmGenerationRuntime"` 对新增领域文件无命中。

## 2. Task 2.2：确定性教学动作选择器

### 允许修改

- 新增：`mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/TeachingActionSelector.kt`
- 新增：`mobile-native/core/domain/src/test/java/com/reversetutor/core/domain/TeachingActionSelectorTest.kt`

### Red

- [ ] 测试相同输入产生相同动作和相同 `TurnPlan`。
- [ ] 测试连续两轮 `AskHint` 后不得继续给同等级提示，必须转为 `SocraticQuestion` 或 `WorkedExample`。
- [ ] 测试 `Fragile + failureStreak > 0` 优先 `Diagnose` 或 `WorkedExample`。
- [ ] 测试 `Stable + successStreak` 优先 `Practice` 或 `Reflect`。
- [ ] 测试 `GoalChange` 优先 `ClarifyGoal`，不得直接覆盖模板。
- [ ] 测试 `OffTopic` 不产生学习事实动作。
- [ ] 运行：

```powershell
.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.TeachingActionSelectorTest" --console=plain --no-daemon
```

预期：先失败，失败原因必须是选择器未实现或规则缺失。

### Green / Refactor

- [ ] 实现候选动作白名单和确定性评分：意图适配、学习状态、失败历史、模板策略、动作新颖度、格式适配。
- [ ] 评分相同时使用固定优先级，不使用随机数或当前时间。
- [ ] 只输出一个主动作和最多一个辅助动作。
- [ ] 重新运行定向测试，预期通过。

## 3. Task 2.3：统一可见上下文和结构化记忆

### 允许修改

- `mobile-native/app/src/main/java/com/reversetutor/preview/wiring/session/TopologyAwareMessageContextPort.kt`
- `mobile-native/app/src/main/java/com/reversetutor/preview/wiring/session/WindowVisibleHistoryReader.kt`
- `mobile-native/app/src/main/java/com/reversetutor/preview/wiring/session/SessionPolicyInputMapper.kt`
- 相关 app 测试文件

### Red

- [ ] 断言时间线和生成上下文使用完全相同的可见消息 id 顺序。
- [ ] 断言子窗口不包含 fork 之后的父消息。
- [ ] 断言兄弟窗口消息永不出现。
- [ ] 断言 `spaceId` 过滤不会把另一空间的消息带入上下文。
- [ ] 断言 message/gap/review evidence id 使用真实插值，不是字面 `$` 文本。
- [ ] 运行：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*.WindowVisibleHistoryReaderTest" --tests "*.BackgroundTurnPreparationCoordinatorTest" --tests "*.SessionPolicyInputMapperTest" --console=plain --no-daemon
```

预期：缺口测试先失败；如果直接通过，记录为“现有接线已满足”，不得为了制造 Red 改坏生产代码。

### Green / Refactor

- [ ] 只修复可见历史投影、证据映射或实例复用问题。
- [ ] 记忆读取仅使用结构化状态，不把 raw transcript 写入 companion memory。
- [ ] 重新运行定向测试，预期通过。

## 4. Task 2.4：TurnPlan 接入 LLM 表达

### 允许修改

- `mobile-native/core/llm/src/main/java/com/reversetutor/core/llm/LlmGenerationLifecycle.kt`
- `mobile-native/core/llm/src/main/java/com/reversetutor/core/llm/LlmGenerationRequest.kt`（如实际文件名不同，先按现有类型定位）
- `mobile-native/core/data/src/main/java/com/reversetutor/core/data/llm/ChatGenerationRepository.kt`
- `mobile-native/app/src/main/java/com/reversetutor/preview/wiring/session/SessionPolicyInputMapper.kt`
- 对应测试文件

### Red

- [ ] 模型返回未知动作、超长目标、URL、Authorization、Bearer 或 secret-like 字符串时，测试断言被拒绝或归一化。
- [ ] 测试无 `TurnPlan` 时保持旧调用兼容。
- [ ] 测试 `TurnPlan` 只作为结构化上下文进入 runtime，不改写 user message。
- [ ] 运行：

```powershell
.\gradlew.bat :core:llm:testDebugUnitTest --tests "*.GuidedLearning*" --tests "*.LlmGeneration*" :core:data:testDebugUnitTest --tests "*.ChatGeneration*" --console=plain --no-daemon
```

### Green / Refactor

- [ ] 将有界 `TurnPlan` 映射到现有 LLM 请求；保持向后兼容默认值。
- [ ] 不允许模型直接提交 mastery、correctness、depth 或 learning ledger receipt。
- [ ] Provider 原始失败统一映射安全错误码。
- [ ] 重新运行定向测试，预期通过。

## 5. Task 2.5：本地证据验证与学习台账

### 允许修改

- `mobile-native/app/src/main/java/com/reversetutor/preview/wiring/session/PostTurnProjector.kt`
- 新增或修改：`mobile-native/app/src/main/java/com/reversetutor/preview/wiring/session/LocalLearningEvidenceVerifier.kt`
- `mobile-native/core/data/src/main/java/com/reversetutor/core/data/learning/LearningLedgerRepository.kt`（仅在现有契约不足时，先记录能力申请）
- 对应测试文件

### Red

- [ ] 模型自评结果不能写入 mastery。
- [ ] 缺少 `windowId`、`knowledgePoint` 或 evidence 时零写入。
- [ ] verifier 产生的 normalized 结果只投影一次。
- [ ] 重启后重复处理同一个 job 不重复写台账。
- [ ] 运行：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*.PostTurnProjectorTest" --console=plain --no-daemon
.\gradlew.bat :core:data:testDebugUnitTest --tests "*.LearningLedgerRepositoryTest" --console=plain --no-daemon
```

### Green / Refactor

- [ ] 默认 verifier 拒绝未经本地验证的模型候选。
- [ ] 只写入 verifier-owned、长度受限、无敏感信息的字段。
- [ ] 不从 assistant 普通文案猜掌握度。
- [ ] 重新运行定向测试，预期通过。

## 6. Task 2.6：真实 Qwen 设备验收

### 前置

- [ ] 设备在线且已确认 serial；若无设备，只记 `not_run`，不以 JVM 代替。
- [ ] Debug 配置来自本机被忽略的 `local.properties`；不在报告中打印地址、模型或 key。
- [ ] 测试消息使用非敏感内容，例如“请讲解一元二次方程的判别式”。

### 验收场景

- [ ] 通过模板创建学习会话。
- [ ] 提出一个问题，确认回复包含结构化步骤或反问，而不是单纯结论。
- [ ] 故意给出错误答案，确认下一轮先诊断或提示，不直接覆盖完整答案。
- [ ] 连续请求提示，确认提示层级变化。
- [ ] 正确回答后，确认进入练习、迁移或复盘动作。
- [ ] 离开并重新进入会话，确认消息、当前回合状态和学习结果仍可读取。
- [ ] 发起一次普通闲聊，确认不会写入学习事实。
- [ ] 记录只包含行为结果、测试时间、设备 serial/API 和安全错误码；禁止记录 raw transcript、Provider 原文或凭据。

## 7. 统一回归

- [ ] 定向领域测试通过：

```powershell
.\gradlew.bat :core:domain:testDebugUnitTest --console=plain --no-daemon
```

- [ ] 受影响模块通过：

```powershell
.\gradlew.bat :core:llm:testDebugUnitTest :core:data:testDebugUnitTest :feature:chat:testDebugUnitTest :app:testDebugUnitTest --console=plain --no-daemon
```

- [ ] 全量 Android 回归通过：

```powershell
.\gradlew.bat test :app:lint :app:assembleDebug --console=plain --no-daemon
```

- [ ] 如本轮未修改 Python，不强制重复 Python；若跨线文件有改动，再运行：

```powershell
py -m pytest -q --ignore=tests/test_project_homepage.py
```

- [ ] `git diff --check` 通过。
- [ ] 冻结路径 diff 为空或只有已批准能力申请范围。
- [ ] 工作树中没有临时日志、截图、数据库导出、密钥或测试 APK。

## 8. 交付报告格式

Aily 完成后必须回报：

1. 每个 Task 的 Red 命令和真实失败原因；
2. Green 修改文件和最小实现说明；
3. 定向、模块、全量测试的真实输出摘要；
4. 设备 serial/API、实际执行测试数和最终结果；
5. `git diff --check`、保护路径检查和工作树状态；
6. 未完成项、阻塞原因和下一项最小判别实验；
7. 明确声明是否 commit/push（默认不做）。

## 完成判定

只有当 2.1–2.6 全部完成、真实 Qwen 回合通过、学习事实经过本地 verifier、重进会话状态可恢复，才能将 `NEWMP-V1-002` 标记为完成并进入工具/文档任务。
