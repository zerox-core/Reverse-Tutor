# 冻结层能力申请：学习概览掌握度读模型

> 状态：**proposed，未批准，不得实施**
>
> 对应切片：`newmp` 学习概览 Track A / A3
> 申请日期：2026-08-22

## 1. 能力缺口（源码事实）

学习概览的 `LearningProgressContract` 需要总知识点、已掌握数量、掌握率与周变化；但当前原生数据层没有一个可按 `spaceId` 与可选 `sessionIds` 查询的结构化掌握度读模型。

- `mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/SessionTurnContracts.kt` 中的 `topMasteryLevel`、`hasMatchingMastery` 只是每轮策略的输入，并非可持久化、可全局聚合的知识点记录。
- `mobile-native/core/data/src/main/java/com/reversetutor/core/data/learning/LearningRepositoryImpl.kt` 当前只暴露学习计划、周总结和 token 使用的读取；没有 mastery 查询。
- `mobile-native/core/data/src/main/java/com/reversetutor/core/data/local/dao/HybridDaos.kt` 没有 mastery 表或聚合 query。
- `WeeklySummary.summary` 是文本，缺少稳定的知识点 id、进度值和会话范围，不能通过字符串解析为 `LearningThreadContract`。

因此，现有 `LearningOverviewProgressPortAdapter` 继续返回默认值；前端必须显示空/无数据，而不是展示模拟掌握度。

## 2. 最小提案（待审批后再细化）

新增一个仅供读取的、结构化的掌握度聚合能力，输入为：

```kotlin
data class LearningProgressQuery(
    val spaceId: String,
    val sessionIds: List<String>? = null,
    val nowEpochMillis: Long
)
```

输出必须能明确表达：总知识点数量、已掌握数量、`0f..1f` 掌握率、相对上周的变化；每一项均需能回溯到持久化的知识点级证据。该能力不得由 Compose、ViewModel 或文本周总结推断。

具体表结构、Repository 名称、写入时机和算法阈值在审批后另开设计与测试方案；本申请不预设 schema 方案，也不授权修改 Room。

## 3. 冻结范围与影响

可能受影响的冻结区域：

| 区域 | 可能变更 | 当前状态 |
|---|---|---|
| `core:model` | 结构化 mastery 领域模型 | 未批准 |
| `core:data/local` | Room entity、DAO、schema、migration | 未批准 |
| `core:data/*Repository` | 读取接口或实现 | 未批准 |

不涉及：LLM Provider、SecretStore、导入/导出协议、后台生成持久化语义。

## 4. 兼容、测试与回滚要求

- 新字段/表必须向后兼容，并提供 migration 与 schema export 验证。
- 按全局、指定会话、空范围、已删除会话、重复证据、跨周边界分别写 Repository contract test。
- 聚合结果不能泄露用户消息、Provider、URL、Authorization、密钥或原始错误。
- 需扩展 `SchemaPolicyTest` 与相应 Repository contract test；通过全量 Android/Python 回归后才允许接入 `LearningOverviewProgressPortAdapter`。
- 回滚必须可通过 revert 恢复；若引入数据迁移，需先说明不可逆风险与降级读取策略。

## 5. 审批门禁

- [ ] 用户批准该冻结层变更方向
- [ ] 单独提交完整变更说明（受影响文件、schema、兼容、测试、回滚）
- [ ] 审批后才开始实现

在上述勾选完成前，`LearningOverviewProgressPortAdapter` 与 `LearningOverviewThreadPortAdapter` 保持现有的空/默认实现。
