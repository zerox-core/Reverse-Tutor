# NATIVE-P6-004 · 数据迁移指南

> 创建时间：2026-08-17（周一）
> 执行人：本地开发搭档（feishu_mcp）
> 分支：Android | 基线提交：b6c3307 | 冻结层：零修改
> 参考：`tasks/native-first-launch-import-prompt-runbook.md`、`tasks/native-legacy-entry-inventory.md`、`tasks/native-backend-protocol-data-contract-freeze.md`
> **声明：本指南完成不代表替换就绪。迁移须经至少一轮 fixture 导入演练后方可用于正式替换。**

---

## 1. 支持的导出版本和协议 Schema

支持的迁移路径：从现有 PWA/Capacitor 版本导出 JSON → 在原生 Android 中导入 JSON。

### 1.1 协议 Schema（精确引用 `core:protocol` 源码）

协议定义位于 `mobile-native/core/protocol/src/main/java/com/reversetutor/core/protocol/`，以下为版本化 Schema 概要（签名以源码为准，不手抄 Kotlin）：

| Schema 名称 | 版本 | 用途 | 冻结状态 |
|---|---|---|---|
| `reverse_tutor_export_v1` | v1 | 全量会话/消息/来源/记忆/图谱导出 | 冻结（core:protocol） |
| `reverse_tutor_preset_v1` | v1 | 会话预设导入/导出 | 冻结（core:protocol） |

关键组件：
- `ProtocolExportPayloadBuilder` — 构建导出载荷
- `ProtocolExportPayloadValidator` — 验证导出载荷
- `ProtocolImportReader` — 读取并解析导入 JSON
- `NativeSessionPreset` — 预设协议模型
- `VersionedProtocolSchemas` — 版本化 Schema 注册

### 1.2 不支持的迁移路径

- **不使用直接 IndexedDB 抓取**作为主要迁移路径
- **不导入 API key** — 导入 JSON 中的 secret/key 字段在 `ProtocolImportReader` 中被拒绝
- 不支持非 JSON 格式的迁移源

---

## 2. 迁移前准备

### 2.1 备份

1. 在当前版本中导出全量备份 JSON
2. 保存到安全位置，记录文件名和校验值

```powershell
Get-FileHash F:\backups\reverse-tutor\export-YYYYMMDD.json -Algorithm SHA256
```

### 2.2 文件校验

导入前检查文件完整性：
- 文件存在且非空
- JSON 格式有效（`ProtocolImportReader` 会验证）
- 文件大小合理（异常大的文件可能是损坏或伪造）

### 2.3 版本识别

`ProtocolImportReader` 自动识别导出 JSON 的 Schema 版本。不支持的版本会被拒绝并显示错误信息。

### 2.4 无效 JSON 处理

- JSON 解析失败 → 显示"文件格式无效"错误，不执行任何写入
- Schema 版本不支持 → 显示"不支持的导出版本"错误
- 必填字段缺失 → 显示具体缺失字段列表
- 包含 secret/key 的字段 → 自动拒绝并脱敏，不写入

---

## 3. Dry-Run 输出

Dry-run 预览不执行任何写入操作，仅分析导入 JSON 并报告：

| 输出项 | 说明 |
|---|---|
| 可导入 | 可以正常导入的条目数（按类型：会话/消息/来源/记忆/图谱） |
| 跳过 | 因重复或冲突将跳过的条目数 |
| 冲突 | 与现有数据冲突的条目数（append 模式下标记，overwrite 模式下将被覆盖） |
| 错误 | 无法解析或不合规的条目数 |
| 预计写入数量 | 实际将写入的条目总数 |

Dry-run 结果页让用户在确认前了解导入影响。

---

## 4. 导入模式

### 4.1 Append（追加）

- 将导入数据追加到现有数据之后
- 重复的 source/message/session 不产生不可控重复（幂等规则）
- 不修改已有数据
- 适用于：在不同设备间合并数据

### 4.2 Overwrite（覆盖）

- 用导入数据替换当前空间的所有数据
- 执行前必须已有全量备份
- 适用于：设备迁移、数据恢复

### 4.3 New-space（新空间）

- 将导入数据创建到新空间，不影响当前空间
- 适用于：导入历史归档数据

### 4.4 确认文案

每种模式在确认前显示：
- 模式名称和说明
- 预计影响的条目数
- 不可逆操作警告（overwrite 模式）
- 确认/取消按钮

---

## 5. 幂等规则

重复 source/message/session 不产生不可控重复：

- **Source 幂等**：相同 `sourceId` 的来源不重复创建，更新已有记录
- **Message 幂等**：相同 `messageId` 的消息不重复写入
- **Session 幂等**：相同 `sessionId` 的会话不重复创建
- 重复导入同一 JSON 文件不会产生数据膨胀

幂等规则由 `NativeImportRepository` 实现，语义为冻结层，不得修改。

---

## 6. Room 写入失败回滚和 Partial Import 报告

### 6.1 Room 写入失败

- 单条写入失败不中断整体导入
- 失败条目记录在结果报告中
- 已成功写入的条目保留（不回滚已写入数据）
- `NativeImportRepository` 在 Room 事务内执行写入，单条失败时该条回滚但整体不中断

### 6.2 Partial Import 报告

部分成功时结果页显示：

| 结果项 | 说明 |
|---|---|
| 成功导入 | 实际写入的条目数 |
| 跳过 | 因幂等规则跳过的条目数 |
| 失败 | 写入失败的条目数及原因 |
| 建议 | 建议的后续操作（重试/检查文件/联系支持） |

---

## 7. 密钥安全

以下字段**不迁移、不导出、不进日志**：

| 字段类型 | 处理方式 |
|---|---|
| API key | 导入时拒绝，导出时脱敏为 `secretRef` |
| Authorization header | 不导出 |
| URL 中的敏感参数 | 导出时脱敏 |
| SecretStore 内容 | 不导出，使用 Android Keystore 独立存储 |

- 导入 JSON 中的 secret/key 字段在 `ProtocolImportReader` 中被拒绝
- 导出时 `ProtocolExportPayloadBuilder` 对 secret 字段执行脱敏
- 日志、截图、诊断文本中不含密钥
- `FormalDiagnosticsScreens` 的剪贴板脱敏策略确保密钥不出现在诊断文本中
- SecretStore 的 alias、加密算法、SharedPreferences 名为冻结层，不得修改

---

## 8. 迁移后验证顺序

导入完成后，按以下顺序验证数据完整性：

1. **会话列表** — 导入的会话是否全部显示，排序和 pin 状态是否正确
2. **消息时间线** — 每个会话的消息是否按时间排列，内容是否完整
3. **来源** — 来源记录是否存在，状态是否正确（FullyLocal/PartiallyLocal/Failed 等）
4. **记忆** — 锚点和笔记是否存在，是否关联到正确的会话
5. **图谱** — 图谱节点和边是否存在，空间范围是否正确
6. **设置** — 主题、头像可见性等偏好是否正确

---

## 9. 失败恢复动作

每种失败情况的用户可执行恢复动作：

| 失败情况 | 恢复动作 |
|---|---|
| JSON 文件无效 | 检查文件是否完整导出，重新导出 |
| Schema 版本不支持 | 升级到支持的版本，或联系支持 |
| 导入中断（应用崩溃） | 重新启动应用，检查已有数据，使用 append 模式重试 |
| Partial import（部分失败） | 查看结果报告，修复失败条目后使用 append 模式重试 |
| Overwrite 后数据丢失 | 使用备份 JSON 以 overwrite 模式恢复 |
| New-space 创建失败 | 原空间数据未受影响，可删除失败的新空间后重试 |
| Room 写入失败 | 查看诊断页错误日志，检查存储空间，重试 |
| 密钥字段被拒绝 | 这是预期行为，需在 LLM Profiles 中重新输入 API key |

---

## 10. Fixture 测试计划

### 10.1 Fixture 类型

| Fixture | 说明 | 预期结果 |
|---|---|---|
| valid | 合法的全量导出 JSON | 导入成功，所有条目正确写入 |
| invalid | 格式损坏的 JSON | 导入失败，显示错误，不写入 |
| duplicate | 包含重复条目的 JSON | 幂等规则生效，不产生重复 |
| secret-containing | 包含 API key 字段的 JSON | secret 字段被拒绝，其余正常导入 |
| large-but-valid | 大量数据的合法 JSON | 导入成功，性能可接受 |

### 10.2 JVM 测试覆盖

`NativeImportRepositoryTest`（9 @Test）覆盖：
- dryRun 预览
- import 执行
- overwrite 模式
- newSpace 模式
- append 幂等
- 无效 JSON 处理
- 安全验证（secret 拒绝）

`NativeExportRepositoryTest`（3 @Test）覆盖：
- 当前会话导出
- 全量备份导出
- 图谱快照导出

`ProtocolExportPayloadBuilderTest` / `ProtocolExportPayloadValidatorTest` / `NativeSessionPresetValidatorTest` 覆盖协议层验证。

### 10.3 设备测试覆盖

设备测试覆盖导入提示、确认、结果页、失败恢复和 wipe 邻接行为：

| 设备测试 | 状态 | 说明 |
|---|---|---|
| `Phase4ImportExportDeviceTest` | not_run | emulator-5554 未运行；导入 dry-run/append/overwrite/new-space/导出/wipe 待补 |
| 主力真机 fixture 导入 | not_run | 主力真机 serial 未提供 |

### 10.4 验收

- [ ] 至少准备 valid、invalid、duplicate、secret-containing、large-but-valid 五类 fixture
- [ ] JVM 测试覆盖 validator、dry-run、append/overwrite/new-space、幂等和脱敏
- [ ] 设备测试覆盖导入提示、确认、结果页、失败恢复和 wipe 邻接行为
- [ ] 模拟器和主力真机各做一次小型 fixture 导入（无真机时标 `not_run`）
