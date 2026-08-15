# P3-003 冻结层变更申请：安全诊断错误记录

- 变更 ID：P3-003-CR-01
- 关联任务：P3-003 Diagnostics And Error Records
- 基线：`Android` / `63c1147`
- 状态：**已批准，待实施**（用户于 2026-08-15 批准方案 A）

## 目标

复用现有 `ErrorLog` / `error_logs`，持久化 Provider 失败和后台生成失败的可诊断记录，并只向诊断报告提供已脱敏的生成诊断。不得保存或导出用户输入、会话标题、模型名、Provider 地址、请求/响应体、Authorization、API Key 或异常原文。

## 冻结层改动

| 路径 | 改动 |
|---|---|
| `core/model/.../MemoryModels.kt` | 新增 `ErrorLogOrigin`；`ErrorLog` 增加 `origin` 和可空 `code`，默认保持既有学习记录语义。 |
| `core/data/.../local/entity/Entities.kt` | `ErrorLogEntity` 和 domain 映射增加同名字段及默认值；为诊断查询增加组合索引。 |
| `core/data/.../local/DatabaseSchema.kt` | Schema 4 → 5；迁移给旧记录写入 `origin = Learning`、`code = NULL`，新增索引。 |
| `core/data/.../local/dao/Daos.kt` | 增加删除单条错误记录及其 memory item 的接口，供保留策略使用。 |
| `core/data/.../memory/MemoryRepository.kt` | `logError()` 增加向后兼容的 `origin` / `code` 默认参数；生成来源记录插入后仅保留同空间最近 50 条。 |
| Room schema export / migration instrumentation test | 导出 v5 schema，覆盖 4 → 5 的默认值、数据保留和索引。 |

## 非冻结层改动

- `feature:chat`：只在 `ProviderFailed` 的前台生成路径回调 app 层；不传递原始错误字符串。
- `app`：前台回调和 `BackgroundGenerationWorker` 写入统一的固定安全文案；诊断路由仅读取 `origin = Generation` 的记录，并将时间、通用标题、固定详情写入已有报告/复制/导出。
- `feature:settings`：不新建第二套页面；复用诊断报告“最近事件”，修正告警状态的颜色。

## 数据与兼容策略

```text
ErrorLogOrigin = Learning | Generation
error_logs.origin TEXT NOT NULL DEFAULT 'Learning'
error_logs.code TEXT NULL
```

- 已有记录在迁移后全部是 `Learning`，不会进入诊断报告。
- P3-003 新记录使用 `Generation` 与固定错误码（如 `provider_request_failed`、`background_generation_failed`）。
- 每个 space 最多保留 50 条 `Generation` 记录；`Learning` 记录不受该清理影响。
- 不改已有 `logError` 调用的行为、返回类型或默认参数语义。

## 安全规则

1. 失败记录只使用固定中文标题和固定中文详情，绝不接受/拼接异常原文。
2. 只记录 `ProviderFailed` 以及后台任务的 `Failed`；取消、丢弃、过期、空输入、无模型和不支持图片不作为诊断错误写入。
3. `sourceMessageId` 仅作为本地关联字段；诊断 UI、复制内容和导出文件不显示它。
4. 诊断报告只读取 `Generation` 来源的记录；普通学习错误和 memory item 不进入报告。
5. 所有测试样例中都要包含伪 Authorization、伪 API Key、URL 和用户输入，并断言它们不出现在持久化诊断详情或导出文本。

## 回滚

保留 v5 已写入数据不影响应用运行。代码回滚时以兼容读取为前提；若需要完全回退到 v4，须另行设计降级迁移，不能通过删除用户数据库文件实现。

## 验收

- ProviderFailed 与后台 Failed 各写入一条 `Generation` 安全记录。
- 取消/丢弃/过期/无模型/空输入不写入记录。
- 诊断报告只显示生成记录，复制/导出不含敏感样例或用户正文。
- 第 51 条生成记录写入后只保留最近 50 条，且不删除 Learning 记录。
- 4 → 5 migration、相关 JVM、全量 JVM、Debug APK、设备 smoke、`git diff --check` 均通过。
