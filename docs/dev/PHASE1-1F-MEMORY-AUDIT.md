# 1f 记忆层 L1/L2/L3 现状核查与验收记录

> 2026-09-23 心跳任务执行。结论先行：**大阶段一开发文档 1f 节「L1/L2/L3 缺失」的现状判断不成立**——窗口记忆四层（L0-L3）与掌握度台账在 NEWMP-V2 时代已建成并接入生产链路（标志性提交 8402fd8 等），1f 无需新写产品代码，本子阶段以核查 + 验收收官。

## 核查依据

- 大阶段一开发文档 1f 节指定：定义以 docs/specs/graph-memory-projection-decisions.md（d5cf8ef）为准；该 spec 的接口对齐节又指向 docs/NEWMP-V2-memory-decisions.md（窗口记忆四层拍板）。
- 逐层比对两份文档的锁定决策与现有代码，结果如下。

## 层级映射（决策 → 实现 → 测试）

### L0 原始轮次（滑动窗口 + 递归摘要）
- 决策：token 预算 6000 + 条数保险 60；滑出批次递归摘要开启。
- 实现：`WindowMemoryPolicy.evict`（core/domain/WindowMemoryContracts.kt，DEFAULT_TOKEN_BUDGET=6000 / DEFAULT_MESSAGE_CAP=60）；摘要折叠 `windowIntakeFoldSummary`（app/wiring/session/WindowMemoryIntakeWiring.kt，走 SessionSummary LLM 路径，失败返回 null 跳过不阻塞）；独立 SessionSummarizer（NEWMP-V1-017）同时在役。
- 测试：WindowMemoryContractTest（9）、SessionSummarizerTest（8）、WindowMemoryIntakeCoordinatorTest（9 含水印/折叠）。

### L1 观测（六变量、每批 0~3 条、不逐句）
- 决策：类别/值/来源类别/置信度/时间/来源句柄六变量；每批 0~3 条；规则提取器先行。
- 实现：`RuleBasedMemoryExtractor`（core/domain，MAX_CANDIDATES_PER_BATCH=3，确定性关键词规则，值归一化不存原文）；持久化 `WindowMemoryRepository`（core/data/windowmemory）；`MemoryObservation.ALLOWED_PERSISTED_FIELDS` 白名单禁存原文（CompanionMemoryContracts.kt）。
- 测试：RuleBasedExtractorContractTest（7）、MemoryIntakeContractTest（4）、WindowMemoryRepositoryTest（6）、CompanionMemoryRepositoryTest（3）。

### L2 模式（同类聚类、不存原文）
- 决策：同类观测聚类（次数/时段分布/首尾时间），不存原文。
- 实现：`WindowMemoryPolicy.aggregatePatterns` 读时聚合（WindowMemoryContracts.kt），无额外存储，符合「Patterns stay read-time aggregates」设计。
- 测试：WindowMemoryContractTest 覆盖；注入侧 WindowMemoryContextSelectorTest（6）。

### L3 窗口级生效值（槽位制、唯一回流全局投影）
- 决策：槽位制当前认知；唯一有资格回流全局投影。
- 实现：`WindowActiveValuePolicy`（core/domain/WindowActiveValueContracts.kt，CREATE/REINFORCE/SUPERSEDE/CONFLICT/IGNORE 五决策，REINFORCE_WEIGHT_STEP=0.15、SUPERSEDE 置信阈值 0.8）；全局投影闸门 `GlobalProjectionPolicy`（GlobalProjectionContracts.kt）。
- 测试：WindowActiveValueContractTest（7）、GlobalProjectionContractTest（7）、CompanionMemoryEvolutionPolicyTest（7）。

### 提取时机与装配（决策 #9 + lorebook 规则）
- 决策：消息滑出窗口时批量提取，异步后台，绝不阻塞聊天回路；注入永不全量、排序后按 token 预算截断。
- 实现：`WindowIntakeDispatcher`（异步派发、失败吞咽、水印保证下轮重试）；`SessionConversationAssembly` 每轮结束 dispatch（line 189）；`WindowMemoryContextSelector`（注入预算 600 tokens，恒高类别 > 查询相关 > 权重 > 新近排序截断）；`WindowMemoryContextPortAdapter` 注入并计量（WindowTokenMeterRepository，V2-006 埋点）。
- 测试：WindowMemoryIntakeWiringTest（5）、WindowMemoryContextWiringTest（4）、WindowMemoryContextPortAssemblerTest（3）。

### 掌握度台账（graph-memory spec D2 端上对齐）
- spec：effectiveness 证据闸门 explanation=0.35 / retrieval=0.55 / transfer=0.72 / delayed_retrieval=0.82 / correction=0.90，partial ×0.75，failed 回退 0.08，alpha=0.35 EMA。
- 实现：`MasteryLedgerProjection`（core/domain）与 upsert_mastery 平价：EvidenceTargetScores 35/55/72/82/90（百分制）、EmaAlpha=0.35、PartialEvidenceFactor=0.75、失败回退 8 分（>50 才回退）重置间隔 1 天、复习阶梯 [1,3,7,14]；`LearningLedgerRepository` append-only + `appendLearningFactIfAbsent` 幂等防重。
- 接线：写入 `BackgroundGenerationWorker`（line 157，后台任务幂等落账）；读取 `ConversationContextPortAdapters`（projectDue 等到期复习投影进装配）、`GraphContextPortAdapter`。
- 测试：MasteryLedgerProjectionTest（15）、LearningLedgerRepositoryTest（5）、GraphRepositoryTest（8）、GraphContextPortAdapterTest（4）。

## 验收证据（2026-09-23）

- 全量单测：`java_development gradle_test`（workdir mobile-native）→ **BUILD SUCCESSFUL**；测试报告逐项解析：**12 模块共 1578 用例，0 失败 0 错误 0 跳过**。
- 记忆相关套件单列：18 个套件共 **117 用例全绿**（清单见上方各层括注数字之和）。
- 本轮无代码改动（UP-TO-DATE 构建），以上结果为既有代码的验收确认。

## 核查发现与暂定项

1. **开发文档 1f 节现状描述修正**：「L1/L2/L3 缺失」→ 实际已建成并接线；1f 无新代码产出属正常收官，不是跳过。
2. **1f-t1 事件类型四分（讲解/问答/复盘/测验）+ 图谱 Node/Event 双层投影**：spec D1-D6 标准明确，但属「图谱接真数据」范畴 = 大阶段三任务线，按不并行跨阶段铁律不在 1f 动工。记入暂定、大阶段三开工时直接采用 spec 阈值（建节点 ≥3 轮实质讲解/问答、晋升 ≥3 卫星 + ≥2 天/会话 + ≥6 轮等）。
3. **1f-t2 BYOK 云端便宜模型提取器**：NEWMP-V2 决策 #5 明确「后期用户配置 API key 再做」，早期规则提取器为主——当前实现符合决策，BYOK 属后期项，不建。
4. **1f-t3 遗忘曲线对接**：graph-memory spec 未决项（等后端完整设计），维持既有拍板，端上不动。
5. **1f-t4 分身窗口「内置不可删」标记契约落点**：NEWMP-V2 待定项（倾向契约层），无拍板结论，记入暂定。

## 结论

1f 记忆层 L1/L2/L3 验收通过：四层窗口记忆 + 掌握度台账的实现与两份决策文档逐条对齐，1578 用例全绿。本子阶段无代码改动，以本核查文档收官并提交推送。大阶段一整体验证（全量构建 + 全量单测 + 模拟器 E2E）仍在 1g 完成后由分支窗口统一执行，窗口记忆的模拟器 E2E 一并纳入该验证。
