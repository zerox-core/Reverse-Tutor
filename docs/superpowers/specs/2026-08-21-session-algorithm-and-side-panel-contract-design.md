# 会话算法与副屏数据契约设计规格

> 状态：待用户审阅
> 适用分支：`newmp`
> 目标实现面：`mobile-native/core/domain`、`mobile-native/feature/chat` 的契约与编排层
> 明确排除：Compose/UI 视觉、图谱 renderer、Room schema、冻结协议与真实 Provider 接入

## 1. 目标

把旧 `main:engine.py` 的“评估 → 决策 → 行动”核心规则迁移为可测试、可替换的原生 Android 领域能力，并为两类前端提供稳定数据入口：

1. 会话内辅助面板：只展示当前这一轮的 action、证据、前置缺口、记忆和来源，不规定气泡、弹窗或抽屉的视觉形式。
2. 首页学习副屏：提供本周学习、今日计划、本周主线、薄弱点、Token 用量等结构化数据，不规定页面布局。

前端只能消费契约，不得直接访问 DAO、Entity、Database、SQL、SecretStore 或协议 DTO。

## 2. 权威来源与迁移范围

本规格服从以下文件：

- `F:\xw\reverse-tutor-newmp\AGENTS.md`
- `F:\xw\reverse-tutor-newmp\tasks\native-architecture-decision-record.md`
- `F:\xw\reverse-tutor-newmp\tasks\native-backend-protocol-data-contract-freeze.md`
- `F:\xw\reverse-tutor-newmp\tasks\wave0-a3-freeze-boundary.md`
- 旧分支 `main:engine.py`、`main:kg_extractor.py`、`main:kg_retriever.py`

本阶段只迁移 `study` 模式的确定性策略规则，并保留 `goal`、`companion` 的兼容分支。策略层不调用 LLM，不持有 Android/Compose/Room 依赖。

旧引擎中以下行为属于本规格的迁移目标：

- `entry_status`、`correctness`、`depth`、`evidence_for_mastery`、`error_pattern`、`misconception`、`user_emotion`；
- `ask`、`probe`、`challenge`、`clue`、`scaffold_example`、`small_lecture`、`examiner_verify`、`recap`、`next` 等 action；
- `student_role` 纪律、动作白名单和 fallback；
- probing intensity、correction timing、correction persistence；
- 会话上下文、摘要、掌握度、错误记录、知识图谱上下文、资料引用的只读汇聚；
- 生成结果的安全映射、token/session 隔离和终态持久化。

## 3. 分层边界

```text
Chat/首页前端
  ↓ 只消费不可变 contract
SessionConversationFacade / LearningOverviewFacade
  ↓
ConversationSessionCoordinator / LearningOverviewCoordinator
  ↓
SessionTurnPolicy（纯函数策略）
  ↓                         ↘
ChatGenerationRepository       Session/Message/Memory/Graph/Plan/Token Repository
```

### 3.1 SessionTurnPolicy

纯 Kotlin、无 Android/Room/Compose 依赖。只负责把输入上下文标准化为策略输出，不落库、不调用 Provider。

输入至少包含：

- `mode: SessionModeWire`（字符串 wire value，默认 `study`）；
- 当前用户输入与最近用户输入；
- 当前知识点及可选 mastery/error 只读快照；
- 策略设置快照；
- 是否强制 probe；
- 当前会话是否首轮。

输出至少包含：

- `SessionEvaluationContract`；
- `SessionActionContract`；
- `processSummary`；
- `normalizationWarnings`（只记录规则修正，不暴露内部异常）。

### 3.2 ConversationSessionCoordinator

负责一轮会话的跨边界编排：

1. 读取会话与当前上下文；
2. 调用 `SessionTurnPolicy`；
3. 把策略结果映射到现有 `ChatGenerationInput`；
4. 调用 `ChatGenerationRepository`；
5. 仅在 token、session、attempt 仍有效时接受结果；
6. 将 assistant outcome 映射为前端契约和持久化副作用；
7. 在失败、过期、会话删除时返回安全状态，不泄露 Provider、URL、Authorization、密钥或原始异常。

它不实现 LLM transport，也不修改 `ChatGenerationRepository` 的生成协议。

### 3.3 Facade

Facade 是前端唯一稳定入口。前端不得通过多个 Repository 自行拼装一轮会话，也不得把 `SessionTurnPolicy` 直接暴露给 UI。

## 4. 会话策略契约

以下类型优先放在 `core:domain`，避免修改冻结 `core:model`。精确 Kotlin 签名以最终源码为准。

### 4.1 评估

```text
SessionEvaluationContract {
  correctness: Float      // 0.0..1.0，越界归一化
  depth: Float            // 0.0..1.0，越界归一化
  entryStatus: EntryStatusWire // has_entry | no_entry | recall_decay
  evidence: MasteryEvidenceContract
  errorPattern: String    // 最多 12 个字符或等价安全长度限制
  misconception: String   // 最多 20 个字符或等价安全长度限制
  userEmotion: UserEmotionWire
  newRequirements: List<String>
}
```

`MasteryEvidenceContract`：

```text
type: none | explanation | retrieval | transfer | delayed_retrieval | correction
status: none | passed | partial | failed
errorType: String
reason: String
```

`goal`、`companion` 模式必须返回 `type=none`、`status=none`，不得更新 study mastery。

### 4.2 行动

```text
SessionActionContract {
  type: ActionTypeWire
  studentRole: StudentRoleWire
  knowledgePoint: String
  difficulty: Float       // 0.0..1.0
  note: String
}
```

study action wire 值：

`ask`、`probe`、`challenge`、`clue`、`scaffold_example`、`small_lecture`、`examiner_verify`、`emote`、`persuade`、`next`、`recap`。

goal action wire 值：

`decompose`、`advance`、`verify_done`、`unblock`。

companion action wire 值：

`empathize`、`observe`、`soft_guide`。

规则层必须执行：

- 非法 action 按模式 fallback；
- 用户声明“懂了/明白了”时，study → `examiner_verify`，goal → `verify_done`；
- `entryStatus=no_entry` 且 action 为 ask/probe/next/recap 时改为 `clue`；
- `entryStatus=has_entry` 且 action 为 clue/scaffold_example 时改为 `probe`；
- 高 probing intensity + 已有入口 + ask 时改为 probe；
- 活跃错误且正确性低时可改为 `small_lecture`；
- `correction_timing=summary_only` 时，将部分纠错行动降为 `recap`；
- action 改写后重新计算合法 student role；
- 不能由策略层生成老师式完整解答。

### 4.3 首轮

首轮只输出首轮策略约束，不依赖用户输入：

- study：action 必须为 `ask`，evaluation 数值为 0；
- goal：action 为 `decompose` 或 `advance`；
- companion：action 为 `observe` 或 `empathize`。

## 5. 会话辅助面板数据契约

该契约不规定 UI 形态，可被气泡、弹窗、半屏抽屉或其他表现层消费。

```text
SessionConversationContract {
  sessionId: String
  turnId: String?
  messages: List<ConversationMessageContract>
  generation: GenerationUiContract
  evaluation: SessionEvaluationContract?
  action: SessionActionContract?
  processSummary: String?
  currentKnowledgePoint: String?
  nextStep: NextStepContract?
  context: ConversationContextContract
  events: List<ConversationUiEvent>
}
```

`ConversationContextContract` 只包含可安全展示的 domain 数据：

- `prerequisiteGaps: List<String>`；
- `relatedMemory: List<MemoryReferenceContract>`；
- `sourceEvidence: List<SourceReferenceContract>`；
- `historicalErrors: List<ErrorReferenceContract>`；
- `pendingReviewKnowledgePoints: List<String>`。

禁止包含：API key、SecretStore 引用、原始 Authorization、Provider URL、未脱敏异常、内部 DAO/Entity、完整协议 DTO。

前端应能在不改变此契约的情况下：

- 将 action 渲染成学生气泡；
- 将 evaluation 渲染成轻量提示；
- 将 context 渲染成弹窗或抽屉；
- 关闭辅助面板而不影响生成流程。

## 6. 首页学习副屏数据契约

首页副屏是独立的读模型，不复用会话辅助面板的 UI 状态，也不要求前端知道 Repository 细节。

```text
LearningOverviewContract {
  scope: LearningOverviewScope
  generatedAtEpochMillis: Long
  activeSessionCount: Int
  progress: LearningProgressContract
  todayPlan: TodayPlanContract
  weeklyMainline: List<LearningThreadContract>
  weakPoints: List<WeakPointContract>
  tokenUsage: TokenUsageOverviewContract
}
```

约束：

- `scope` 支持全部会话或指定会话集合；
- 无数据必须返回空集合和明确的 `NoData` 状态，不使用假数据；
- “本周主线”和“薄弱点”来自现有学习/错误/记忆读模型，不在 UI 端重新推导；
- 计划新增、编辑、完成通过独立的 `StudyPlanRepository`/Coordinator 命令，不允许 UI 修改 overview 快照；
- Token 用量只返回聚合值与 estimated 标记，不返回密钥或原始 provider payload；
- 周报生成属于独立任务，不阻塞当前聊天回合。

## 7. 共享上下文读取规则

`ConversationContextAssembler` 和 `LearningOverviewCoordinator` 只读调用已有能力：

- Session/Message：会话与消息边界；
- Memory/Error/Mastery：掌握度、错误和复习信息；
- Graph/Source：相关节点、前置关系、资料证据；
- StudyPlan：今日计划与主线；
- TokenUsage：聚合用量。

所有汇聚器必须：

- 按 `spaceId/sessionId` 隔离；
- 对单个来源失败采用局部降级并记录安全 warning，不阻塞聊天主流程；
- 限制每类返回数量和文本长度；
- 保持稳定排序，避免 UI 每次刷新跳动；
- 不在前端二次执行 mastery、KG 或 action 推导。

## 8. 测试契约

第一阶段必须先写失败测试，再实现：

1. `SessionTurnPolicyTest`：模式 action 白名单、entry 状态改写、理解声明、probe 强度、纠错时机、非法值归一化、首轮规则。
2. `ConversationContextAssemblerTest`：session 隔离、数量上限、局部失败降级、前置缺口与来源安全字段。
3. `ConversationSessionCoordinatorTest`：token stale、session deleted、provider failure、成功落库顺序、无模型配置。
4. `SessionConversationContractTest`：前端契约字段完整、无敏感字段、事件稳定。
5. `LearningOverviewCoordinatorTest`：范围过滤、无数据状态、主线/薄弱点/token 聚合、局部失败降级。

测试不得调用真实网络或真实 Provider；Python 回归继续使用 `py`，Android 回归按模块执行。

## 9. 明确不在本阶段

- 不修改 `mobile-native/core/model`、`core/protocol`、`core/llm`、`core:data/*Repository` 签名、Room、SecretStore；
- 不新增 domain enum 到冻结模型；wire 值先作为 domain contract 字符串或非冻结类型处理；
- 不做 Compose 页面、动效、布局或视觉 token；
- 不做图谱 renderer 或节点交互；
- 不接入真实 Provider/API key；
- 不实现 Android 多窗口/外接屏；本规格中的“副屏”是应用内数据面板契约。

## 10. 验收标准

只有以下条件全部满足，第一阶段才可进入 UI 适配：

- 策略层可脱离 Android/Room 独立测试；
- 旧 `main` 的 study 关键规则有逐条映射和测试证据；
- Coordinator 不把策略、Repository、Provider 责任混在一起；
- 会话辅助契约与首页 overview 契约互不耦合；
- 前端无需读取任何冻结层内部类型即可渲染；
- token/session 隔离、失败安全、无模型和局部降级均有测试；
- 冻结层 diff 为空，`git diff --check` 清洁。
