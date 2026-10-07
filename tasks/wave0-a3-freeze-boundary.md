# Wave 0 · A3 冻结层审批边界

> 生成时间：2026-08-14（周五）
> 执行人：本地开发搭档（feishu_mcp）
> 总纲：v2.1 审计结论与落地计划（`tasks/audit-conclusion-and-plan.md`）
> 依据：`tasks/native-backend-protocol-data-contract-freeze.md`（2026-07-04）
> 验收标准：冻结层清单与 freeze 文档一致；变更说明模板可直接复制使用；不改业务代码。

## 1. 冻结层完整清单

冻结层分两层，均需走变更审批门禁，不得在 UI 重构或 feature 开发中顺手修改。

### 1.1 后端协议接口层（冻结）

| 模块 | 路径 | 冻结内容 |
|---|---|---|
| `core:model` | `mobile-native/core/model/src/main/java/com/reversetutor/core/model/` | 14 个 domain model 文件的类型签名、枚举值（`ConversationModels.kt`、`LlmProfile.kt`、`MemoryModels.kt`、`GraphModels.kt`、`SourceModels.kt`、`OperationalModels.kt`、`Space.kt`） |
| `core:protocol` | `mobile-native/core/protocol/src/main/java/com/reversetutor/core/protocol/` | 7 种版本化 Schema（`reverse_tutor_export_v1` 等）、`ProtocolExportPayloadBuilder`、`ProtocolExportPayloadValidator`、`ProtocolImportReader`、secret 脱敏策略 |
| `core:llm` | `mobile-native/core/llm/src/main/java/com/reversetutor/core/llm/` | 三阶段（Planner→Runtime→Result）、三 Provider 协议（`LlmCapabilities`、`LlmProfileValidator`、`LlmProfileCapabilityResolver`、`LlmGenerationPlanner`、`LlmGenerationRuntime`） |
| `core:data/*Repository` | `mobile-native/core/data/src/main/java/com/reversetutor/core/data/` 下各子包 | 12 个 Repository 接口签名（`SessionRepository`、`MessageRepository`、`LlmProfileRepository`、`ChatGenerationRepository`、`BackgroundGenerationRepository`、`LocalDataWipeRepository`、`NativeImportRepository`、`NativeExportRepository`、`SourceRepository`、`MemoryRepository`、`GraphRepository`、`AppPreferencesRepository`） |

### 1.2 数据库/数据层（冻结）

| 模块 | 路径 | 冻结内容 |
|---|---|---|
| `core:data/local` | `mobile-native/core/data/src/main/java/com/reversetutor/core/data/local/` | 当前 Room schema（`DatabaseSchema.version=4`）、18 张表（`SessionEntity`/`MessageEntity` 等）、13 个 DAO、`migration1To2`/`migration2To3`/`migration3To4`、exported schema JSON。2026-07-04 freeze 文档中的 v2 是历史冻结基线，不是当前版本。 |
| `core:data/preferences` | `mobile-native/core/data/src/main/java/com/reversetutor/core/data/preferences/` | DataStore key（`theme`/`global_avatar_visible`/`memo_*`）、`AppPreferencesRepository` 实现 |
| `core:data/llm/SecretStore.kt` | `mobile-native/core/data/src/main/java/com/reversetutor/core/data/llm/SecretStore.kt` | `SecretStore` 接口、`AndroidKeystoreSecretStore` 实现（alias、加密算法、SharedPreferences 名） |

### 1.3 暂不冻结（可自由重构，仍须遵守边界规则）

| 层 | 路径 | 规则 |
|---|---|---|
| 前端 UI 设计层 | `app/.../theme/`、`app/.../ui/`、`app/.../shell/`（纯导航表现部分）、`feature/*` 的 Compose 页面 | 可重做页面、导航、状态组织和文案；只经 Repository/domain model/protocol facade 调用业务能力；不得直碰 DAO/Entity/SQL/Keystore/协议 DTO |

### 1.4 冻结范围覆盖 Wave 2

以下内容虽排进 Wave 2 但**仍属冻结范围**，每个切片开始前走「变更说明 + 测试方案 + 用户批准」门禁，不因排期而豁免：

- P3-001（后台可靠性 / WorkManager 持久化 job 补差距）
- 真实 LLM Provider transport 切换
- Room migration（如有新 schema 版本）
- `core:llm` 模块改动

## 2. 不允许混在 UI 重构中的改动

以下改动必须单独提交变更说明，不得混在任何 UI/feature 分支中：

1. 修改 Room Entity 字段、表名、索引
2. 修改 DAO query
3. 修改 `DatabaseSchema.version` 或 migrations
4. 修改 exported Room schema JSON
5. 修改 protocol schema required/allowed fields
6. 修改 import/export secret redaction 策略
7. 修改 `NativeImportRepository` overwrite/new-space 语义
8. 修改 `BackgroundGenerationRepository` token/session 校验
9. 修改 `SecretStore` 存储位置、alias、加密策略
10. 修改任何 `core:*Repository` 的方法签名或输入/输出类型
11. 新增/删除/修改 `core:model` 的枚举值
12. 修改 `DataModule` 的 Repository 装配方式

## 3. 冻结层变更说明模板

以下模板用于冻结层变更申请。每个切片开始前填写并提交用户审批。

```markdown
# 冻结层变更申请：[变更标题]

## 基本信息
- 申请日期：YYYY-MM-DD
- 申请分支：[分支名]
- 对应 Wave / 切片：[如 Wave 2B / P3-001]
- 冻结层类型：[后端协议接口层 / 数据库数据层]

## 1. 变更原因
[为什么需要改冻结层？当前能力缺口或 bug 是什么？]

## 2. 受影响文件
| 文件路径 | 冻结模块 | 变更类型 |
|---|---|---|
| `mobile-native/core/data/src/.../xxx.kt` | `core:data/local` | [新增字段 / 修改 query / 修改签名 / ...] |

## 3. 受影响 Repository / API
[列出受影响的 Repository 方法、输入/输出类型变化]

## 4. 受影响 protocol schema 或 Room table
- Protocol schema：[受影响的 schema 名和 version]
- Room table：[受影响的表名、字段、索引变化]
- Migration：[是否需要新 migration？version 从 X→Y]

## 5. 兼容策略
[向前兼容性分析：旧安装升级是否受影响？导出/导入 JSON 兼容性？]
- 新字段是否 nullable 或有默认值？
- 是否影响已有的 exported schema JSON？
- 是否影响 import/export 的 secret 脱敏？

## 6. 新增 / 修改测试
| 测试文件 | 测试类 | 覆盖点 |
|---|---|---|
| `core/data/src/test/.../XxxTest.kt` | `XxxTest` | [断言内容] |

至少包含：
- [ ] Migration test（如涉及 Room schema 变更）
- [ ] SchemaPolicyTest 扩展（如涉及表/字段变化）
- [ ] Repository contract test（如涉及接口签名变化）
- [ ] Protocol validator test（如涉及 schema 变化）

## 7. 回滚方案
[如果变更出问题，如何回滚？是否可 revert commit？是否有数据迁移不可逆风险？]

## 8. 审批
- [ ] 用户已批准（日期 + 签名/确认）
- [ ] 测试方案已确认
- [ ] 变更说明已归档至 `tasks/` 下
```

## 4. 审批流程

```
切片开始前
  └→ 填写变更说明（用上方模板）
       └→ 提交用户审批
            ├→ 批准 → 执行变更 → 补测试 → 验证全绿 → 归档变更说明
            └→ 不批准 → 不改冻结层，寻找替代方案（UI adapter / feature 内 coordinator）
```

- 变更说明归档路径：`tasks/change-requests/` 下按 `cr-YYYYMMDD-简述.md` 命名
- 未获批准前不得修改冻结层代码
- 已获批准的变更仍须在对应切片的 PR/commit 中引用变更说明文件名

## 5. 验证基线（受影响测试入口）

冻结层变更后，以下测试必须全绿，否则说明变更越界或有回归：

```
mobile-native/core/protocol/src/test/java/com/reversetutor/core/protocol/*
mobile-native/core/data/src/test/java/com/reversetutor/core/data/*
mobile-native/core/data/src/androidTest/java/com/reversetutor/core/data/*
mobile-native/core/llm/src/test/java/com/reversetutor/core/llm/*
mobile-native/core/model/src/test/java/com/reversetutor/core/model/*
```

关键测试清单（freeze 文档 §8 原文）：

- `SchemaPolicyTest`
- `ReverseTutorDatabaseMigrationTest`
- `VersionedProtocolSchemaValidatorTest`
- `ProtocolImportReaderTest`
- `ProtocolExportPayloadBuilderTest`
- `NativeImportRepositoryTest`
- `NativeExportRepositoryTest`
- `BackgroundGenerationRepositoryTest`
- `ChatGenerationRepositoryTest`
- `LlmProfileRepositoryTest`
- `MemoryRepositoryTest`
- `GraphRepositoryTest`
- `SourceRepositoryTest`
- `LocalDataWipeRepositoryTest`
- `AppPreferencesPolicyTest`

## 6. A3 完成自检

- [x] 冻结层清单与 freeze 文档（§1-§5）一致，按模块列出完整路径和冻结内容。
- [x] 变更说明模板可直接复制使用，包含 freeze 文档 §7 要求的全部字段。
- [x] 冻结范围覆盖 Wave 2 内容（P3-001、真实 LLM、Room migration、core:llm）。
- [x] 审批流程明确：未获批准不改冻结层；已获批准的变更须引用变更说明文件名。
- [x] 未改业务代码（仅新增本文档）。

## 7. 遗留与下一步

- 变更说明归档目录 `tasks/change-requests/` 需在实际首次申请时创建。
- 下一项：**A4 物理文件所有权表**——按双轨分工（Track A 领域能力与状态流 / Track B 表现层与交互）为每个源码文件指定唯一责任人和可修改类型。
