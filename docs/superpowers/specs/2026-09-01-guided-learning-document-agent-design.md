# 引导式学习与会话学习笔记 Agent 设计

> 状态：已确认设计，待能力申请与实施计划。
> 分支：`newmp`
> 主线位置：V1 普通单会话闭环的后端能力扩展；不以现有前端为设计依据。

## 1. 目标

把普通会话从“模型生成一段文字”升级为可验证的引导式学习回合：系统独立决定学习动作，模型返回可渲染的结构化内容；资料引用与工具结果不再混入正文。模型可在当前会话内自动维护一份轻量学习笔记，并返回可由任意前端呈现的结构化结果。

本设计借鉴但不嵌入外部运行时：

- Nous Hermes Agent：记忆分区、演进和拓扑隔离的长期参考。它不定义本期工具运行时，也不作为模型微调依赖。
- DeepSeek Harness：工具定义、注册、策略、执行、结果与展示意图分离的架构参考。应用不直接运行 DSH、不加载第三方代码、不开放 shell、文件系统或网络工具。

## 2. 现有问题与结果

### 2.1 资料混入助手正文

当前检索证据可被拼接为正文中的 `Sources:` 文本，破坏聊天感受，也使前端无法独立决定资料是否显示。

改为三路结果：

```text
AssistantContent     正常对话内容
EvidenceReferences   独立资料引用
ToolResults          独立工具调用结果
```

`EvidenceReferences` 只包含稳定引用 ID、标题、受限摘要、资料类型和可打开目标；不把检索原文、系统提示词、URL、密钥、Authorization 或 Provider 诊断写入助手正文。前端可以把它呈现为可关闭的资料抽屉、卡片或跳转入口，但关闭仅影响显示，不影响后端学习判断。

### 2.2 模型只输出文案

模型不能自行决定学习是否已完成，也不能靠“用户说懂了”更新掌握度。每轮必须有独立的学习计划和结构化评估候选。

### 2.3 文档与表格缺少安全工具边界

“像飞书多维表格”的体验属于未来前端呈现；后端先提供会话内文档、区块和轻量表格的稳定工具契约，不实现 Word 兼容、任意文件编辑或外部应用控制。

## 3. 引导式学习规则：从 main 迁移规则，不复制引擎

旧 `main:engine.py` 的有效规则拆入纯领域策略，不迁移其 Python 单体 Prompt/数据库耦合实现。

| 学习信号 | 领域动作 | 约束 |
|---|---|---|
| 回答正确但浅 | `Probe` | 追问理由、例子或边界；不直接长讲。 |
| 明确断言错误规则 | `Challenge` | 反例 → 指出矛盾 → 请求验证；坚持度决定升级速度。 |
| 没有切入点 | `Clue` 或 `ScaffoldExample` | 提供一个线索或小脚手架，不直接替用户完成学习。 |
| 用户声称已懂 | `ExaminerVerify` | 要求解释、迁移或检验；没有证据不得增加掌握度。 |
| 到期复习 | `Recap` 或 `DelayedRetrieval` | 从已到期的结构化学习事实生成，不从原始聊天猜测。 |
| 学习完成或阶段转换 | `Next` / `Recap` | 给出下一步，不自动伪造掌握结论。 |

学习模式才允许产生掌握证据。目标模式与陪伴模式可以有规划或文档工具结果，但必须输出 `evidenceType = none`、`evidenceStatus = none`，不得写学习台账。

### 3.1 策略输入与输出

```text
GuidedLearningInput
  = 当前窗口可见历史
  + SessionPolicyOutput
  + 已验证学习事实摘要
  + 未解决错误模式摘要
  + 到期复习摘要
  + 当前会话模式

GuidedLearningPlan
  = actionType
  + learnerRole
  + targetKnowledgePoint?
  + correctionLevel?
  + requiredEvidence?
  + nextQuestionConstraint
```

`GuidedLearningPolicy` 是纯 Kotlin 规则：它不读取 Room、不调用模型、不包含 UI 类型、不拼接资料正文。模型收到的是已规范化的 `GuidedLearningPlan`，模型返回后仍要经结构校验，不能信任模型自行声明的学习结论。

## 4. 结构化助手回复

### 4.1 内容块

助手正文由有界的 `RichContentBlock` 列表表达，而不是仅用一段字符串。第一期块类型：

```text
Heading(level 1..3, text)
Paragraph(text)
BulletList(items)
NumberedList(items)
CodeBlock(language?, code)
Callout(kind, text)
SimpleTable(columns, rows)
```

所有文本、代码、表格单元格、总块数和总字节数都有限制。未知块、畸形块或超限结果必须安全降级为普通 `Paragraph`，不能导致整轮失败。

### 4.2 回复信封

```text
AssistantReplyEnvelope
  contentBlocks: List<RichContentBlock>
  evidenceReferences: List<EvidenceReference>
  learningPlan: GuidedLearningPlan
  structuredOutcome: StructuredTurnOutcome
  toolCalls: List<ToolCall>
  toolResults: List<ToolResult>
```

持久化的助手聊天记录保留一个适合时间线的正文投影；完整信封用于回放、学习台账和工具结果展示。资料引用和工具结果不得被重新拼入聊天正文。前端只消费已发布的内容块、引用和展示意图。

## 5. DSH 式工具能力层

本项目借鉴 DeepSeek Harness 的“工具定义与执行分离”而非其 Node/Cordis 运行时。

```text
ToolDefinition
  名称、描述、输入 Schema、输出 Schema、并发属性、展示意图
          ↓
ToolRegistry
  按会话模式及白名单公开给模型
          ↓
ToolPolicy
  作用域、参数、大小、写入与幂等校验；只能拒绝，不能绕过拒绝
          ↓
ToolExecutor
  调用受限的 Repository Port
          ↓
ToolResult
  成功/安全失败码/规范化结果/展示意图/调用回执
```

模型可见的只有名称、描述、输入参数和输出 Schema。执行函数、权限、超时、内部错误、持久化实现和 UI 回调都不进入模型请求。

每次调用都有 `callId` 和会话/窗口身份；同一 `callId` 的重试返回同一回执，不重复写入。工具结果使用安全错误码，绝不返回数据库、文件路径、堆栈、URL、密钥或 Authorization。

### 5.1 作用域和自动写入

第一期允许自动写入，但只允许当前会话：

- 只能读写 `sessionId == 当前会话` 的文档与表格。
- 不允许跨会话、跨 space、设备文件、网络、系统命令、密钥、任意插件代码或整份文档删除。
- 所有写入有数量、大小和超时限制；超限失败不部分写入。
- 内部保留不可变操作回执，用于幂等、诊断和审计；不要求普通用户提供回退界面。

未来“可配置插件”指安装包内、受审核的 Kotlin Provider 通过清单注册，不指用户下载并执行任意代码。插件可按会话模式声明可见性；学习模式、目标模式和陪伴模式的允许工具集合可以不同。

## 6. 第一条最小垂直切片：会话学习笔记 Agent

第一期只交付一个有价值的端到端能力，不一次实现完整文档平台。

### 6.1 用户结果

学习对话后，系统可以自动创建或更新该会话唯一的“学习笔记”。笔记可包含标题、段落、重点提示、代码块和简单表格。聊天正文仍保持自然对话；工具结果仅表明笔记是否更新以及笔记标识，前端可选择显示、隐藏或跳转查看。

### 6.2 第一批工具

```text
session_document.create
session_document.read
session_document.replace_block
session_table.create
session_table.upsert_row
reference.open
```

`reference.open` 不读取或输出原始资料全文；它只返回已授权资料的稳定定位目标。前端负责实际导航。`session_table.*` 仅支持少量列、少量行和标量单元格，不实现公式、多人协作、筛选、权限表、Word 导入或飞书 API。

### 6.3 文档自动更新决策

只有 `GuidedLearningPlan` 明确要求沉淀内容时，模型才可提出工具调用；`ToolPolicy` 再判断该调用是否为当前会话、允许的工具、有效参数和未超限。模型没有资格直接写数据库。

工具失败不阻塞助手正常回复；回复信封记录安全失败码，前端可以安静地不展示或显示“笔记暂未更新”。

## 7. 长期手机 Agent 演进

手机 Agent 采用逐层扩展，不把桌面 Agent 的高权限能力搬到移动端：

```text
会话学习笔记 Agent
→ 会话内表格 Agent
→ 已授权资料引用 Agent
→ 会话模式插件清单
→ heartbeat 触发的受限主动任务
→ companion / learning / branch 记忆拓扑协作
```

heartbeat 只可提交已有 `InitiativePlan`，仍由既有 Worker 作为 assistant 唯一写入者。工具调用不是 assistant 写入的第二条路径。

## 8. 冻结边界与审批门

本设计需要新增 P3/P6 能力申请后才能写生产代码：

- P3：`AssistantReplyEnvelope`、富内容块、证据引用、模型工具 Schema、调用与结果 wire 载荷。
- P6：会话文档、文档区块、轻量表格、表格行、工具回执的持久化/Repository/migration；禁止 raw Provider 信息进入持久层。

未获批准前，只可做纯领域策略、非冻结测试、文档与能力申请；不得改 `core:llm`、`core:data`、Room、DAO、migration、Provider 或 SecretStore。

## 9. 验收标准

1. 助手正文不含系统 `Sources:`、原始检索片段、URL、密钥或 Provider 错误。
2. 相同输入下，纯领域策略能确定浅答追问、错误纠正、线索、检验和复盘动作。
3. “我懂了”但没有通过结构化证据时不写掌握度。
4. 代码块、表格和普通文本可以通过统一内容块安全传递；畸形块安全降级。
5. 自动文档写入只能修改当前会话；同一 `callId` 重试不重复写。
6. 工具失败不影响助手回复，不泄露内部细节。
7. 关闭资料展示只影响前端显示，不影响聊天正文、学习证据或持久化。
8. JVM 定向测试、受影响模块测试、全量 Android test/lint/build、Python 回归和一次主力真机会话测试均有真实证据。

## 10. 非目标

- 本期不微调模型、不训练 DeepSeek/Hermes。
- 本期不运行 DeepSeek Harness 或其第三方插件。
- 本期不复刻飞书多维表格 UI、不接入飞书 API、不做协作文档。
- 本期不开放文件系统、shell、网络、外部账号、系统设置或任意代码执行。
- 本期不进入 V2 全局 companion 记忆演进或 V4 heartbeat 扩展实现。
