# Reverse Tutor 接口契约索引

本目录是 `mobile-native` 原生 Android、FastAPI 在线服务和 Room 本地数据层的联调基准。

## 当前版本

- 契约版本：`1.0-draft`
- 主客户端：`mobile-native`
- 在线服务：FastAPI `/api/v1`
- 本地数据：Room + DataStore + SecretStore + 本地文件引用
- 旧 PWA/Capacitor：仅用于旧数据迁移和行为参考，不参与新接口设计

## 文件

- `mobile-native-integration-v1.md`：页面、UseCase、Repository、数据主权、错误和联调流程。
- `openapi-online-v1.yaml`：Android 与 FastAPI 之间的 Canonical V1 HTTP 契约。
- `mock-online-v1.json`：前端 Fake/Mock 开发可直接使用的响应示例。
- `world-tree-schema-v1.json`：世界树本地持久化与导入导出的 JSON Schema。
- `mock-world-tree-v1.json`：包含默认栏目和自定义栏目的完整草稿示例。

## 冻结规则

1. Kotlin domain 字段、HTTP JSON 字段和 Mock 字段必须一致。
2. HTTP JSON 统一使用 `camelCase`，枚举 wire value 使用文档中给出的 `snake_case`。
3. 时间统一为 UTC `epochMillis`，ID 统一为不透明字符串。
4. 后端不得返回固定 UI 文案；前端按稳定错误码和状态码映射中文文案。
5. 任何破坏性变化必须提升契约版本，并同时修改三份文件及契约测试。
6. 社区页尚未完成 Figma 定稿，对应 API 不属于 `1.0-draft` 冻结范围。

## 当前实现状态

- 已完成：FastAPI 与 Android `sync/push` 统一使用 `envelopeId + entityId + accepted`，Android 请求同时发送 `envelopeId`，旧 `status` 响应被拒绝。
- 已完成：世界树 Domain、版本化 payload codec、Room 三表、DAO、`WorldTreeRepository`、V3→V4 迁移及 Schema 4 导出。
- 已完成：核心 JVM 测试、Android instrumentation 编译、Room Schema 与迁移 SQL 静态一致性校验。
- 待真机补验：设备当前未连接，`WorldTreeRepositoryInstrumentedTest` 与 `ReverseTutorDatabaseMigration3To4Test` 尚未实际执行。
- 后续阶段：活动统一错误/鉴权、退出活动和 `/api/v1/content/*`。
