# NEWMP-V2-001：会话结构化候选写入记忆层（线 C 第一切片）

任务编号：NEWMP-V2-001
所属主线节点：V2 记忆拓扑（接入 companion memory / learning memory / 局部任务记忆 / 演进版本）
上游依赖：V1 会话闭环（已闭环，2026-09-17 确认）；core:domain 记忆契约（CompanionMemoryContracts / CompanionMemoryEvolutionPolicy，已存在）；core:data 记忆仓库（CompanionMemoryRepository / LearningLedgerRepository，已存在但未接线）
允许修改路径：core/domain（新增 intake 契约）、feature/chat（会话编排接线）、app（DI 装配）、对应测试目录
禁止修改路径：core/model、core/protocol、core/llm、core/data 实体与 DAO（冻结层；本切片不改 schema）

## 现状盘点（2026-09-17 实查）

已存在：
- core:domain：MemoryDomain / CompanionMemoryPartition / MemoryObservation / ActiveMemoryVersion / CompanionMemoryEvolutionPolicy（含测试）
- core:data：CompanionMemoryRepository（伴侣域，provenance-only，窗口读写隔离）、LearningLedgerRepository（全局学习台账 append-only + scope 信号）、WindowTopologyRepository、GraphRepository
- 约束已内建：MemoryObservation.ALLOWED_PERSISTED_FIELDS 白名单，禁存 raw transcript

缺口（本切片目标）：
- CompanionMemoryRepository / LearningLedgerRepository 在 feature/ 与 app/ 零引用——记忆层与会话流程完全未接线
- 会话每轮产出的学习结果没有以「有界结构化候选」形式进入记忆层

## Red 测试命令与预期失败

- 新增契约测试：会话完成一轮后，intake 端口收到有界 MemoryObservation / LearningFactReceipt 候选（字段白名单校验、无原文、窗口域隔离）
- 预期失败：intake 端口不存在，编译/断言失败

## Green 实现范围

1. core:domain 新增 MemoryIntakePort（候选接收契约，纯接口无实现）
2. 会话编排（ConversationSessionCoordinator 下游）产出候选并调用 intake
3. app 层 DI 把 intake 接到 CompanionMemoryRepository / LearningLedgerRepository

## 回归命令

- java_development gradle_test（全工程，当前基线全绿 2026-09-17）

## 设备验收命令

- 一轮真实对话后，伴侣记忆/学习台账出现对应结构化记录且无原文（真机数据库检查，后续切片）

## 失败时停止条件

- 需要改冻结层（core/data schema、core/model、core/protocol、core/llm）→ 停止，回主控确认
- 候选必须携带原文才能成立 → 停止，违反禁存 raw transcript 红线

## 交付文件与证据

- 本任务卡、Red 测试文件、Green 实现、测试通过记录
