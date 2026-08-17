# NATIVE-P6-005 · PWA/Capacitor 退出决策材料

> 创建时间：2026-08-17（周一）
> 执行人：本地开发搭档（feishu_mcp）
> 分支：Android | 基线提交：b6c3307 | 冻结层：零修改
> 参考：`tasks/native-p6-001-legacy-audit.md`、`tasks/native-p6-003-device-regression-matrix.md`、`tasks/native-p6-004-migration-guide.md`
> **结论状态：`blocked`** — 未获用户明确批准前保持 `proposed` 或 `blocked`。

---

## 1. 决策结论

**结论：`blocked`**

PWA/Capacitor 退出决策当前处于 `blocked` 状态。原因：
- 31 个 P0 LEG 中，3 项已验证（`verified`），28 项仍为 `blocked`
- 设备矩阵未完成（模拟器未运行、主力真机未提供）
- 迁移指南未经 fixture 导入演练
- TalkBack 人工听测未执行

退出条件未满足，不宣布 PWA/Capacitor 退出。

---

## 2. P0 遗留入口决策表（28 条）

状态词汇：`verified`（有代码、JVM、设备、契约和回归证据）、`waived`（有豁免原因和用户批准）、`blocked`（有缺口）。

### 2.1 已验证项（3 条）

| ID | 遗留入口 | 代码 | JVM | 设备 | 契约 | 状态 | 说明 |
|---|---|---|---|---|---|---|---|
| LEG-001 | 启动/闪屏 | ✅ | ✅ | ✅ P1-006 | ✅ | verified | `MainActivity.kt` 启动入口，`com.reversetutor.preview` |
| LEG-033 | 数据擦除 | ✅ | ✅ | ✅ P4-007 | ✅ | verified | `LocalDataWipeRepository.kt`，WIPE 确认 UI |
| LEG-039 | Android 返回行为 | ✅ | ✅ | ✅ P1-006 | ✅ | verified | `AppNavigation.kt` BackResult，`WorkspaceBackHandler.kt` |

> LEG-044（应用诊断/关于）在覆盖登记表中属于 P1，虽保持 `verified/closed`，但不计入本 P0 退出决策统计。

### 2.2 阻塞项（28 条）

| ID | 遗留入口 | 代码 | JVM | 设备 | 契约 | 状态 | 缺口 |
|---|---|---|---|---|---|---|---|
| LEG-002 | 顶部导航栏 | ✅ | ✅ | not_run | ✅ | blocked | 上下文动作完整性需设备验证 |
| LEG-005 | 会话首页 | ✅ | ✅ | not_run | ✅ | blocked | 未读计数与头像 parity 需设备验证 |
| LEG-006 | 会话卡片操作 | ✅ | ✅ | not_run | ✅ | blocked | swipe 手势、头像选择器、设备验证待补 |
| LEG-008 | 新建会话快速流程 | ✅ | ✅ | not_run | ✅ | blocked | Settings 侧预设/导出 parity 待补 |
| LEG-009 | 自定义会话配置 | ✅ | ✅ | not_run | ✅ | blocked | Runtime 中使用配置的 chat turns 待验证 |
| LEG-010 | 预设导入/导出 | ✅ | ✅ | not_run | ✅ | blocked | 完整预设管理 UI/设备证据待补 |
| LEG-012 | 聊天线程 | ✅ | ✅ | not_run | ✅ | blocked | 精确引用解析/可点击引用/高亮 parity 待补 |
| LEG-013 | 聊天编辑器 | ✅ | ✅ | not_run | ✅ | blocked | 真实图片选择器/附件管道待补 |
| LEG-014 | 引用回复 | ✅ | ✅ | not_run | ✅ | blocked | 更广泛 parity 审计待 P6 |
| LEG-015 | 消息操作 | ✅ | ✅ | not_run | ✅ | blocked | 真实 note/memory 副作用和 LLM regenerate 待补 |
| LEG-016 | 流式与队列 | ✅ | ✅ | not_run | ✅ | blocked | P6 设备矩阵待补 |
| LEG-017 | 后台回复 | ✅ | ✅ | not_run | ✅ | blocked | 设备矩阵、真实 Provider 调用待补 |
| LEG-019 | Context Hub 入口 | ✅ | ✅ | not_run | ✅ | blocked | 真实图谱/证据数据与最终 parity 待补 |
| LEG-020 | 上下文图谱 | ✅ | ✅ | not_run | ✅ | blocked | 会话图谱投影、语义卡片、手势 QA 待补 |
| LEG-021 | 上下文锚点 | ✅ | ✅ | not_run | ✅ | blocked | 来源到锚点管理、精确跳转高亮待补 |
| LEG-022 | 上下文笔记 | ✅ | ✅ | not_run | ✅ | blocked | 手动笔记管理 UI、同步与 parity 待补 |
| LEG-024 | 会话设置 | ✅ | ✅ | not_run | ✅ | blocked | 人格、策略、截止日期、头像、警告流编辑待补 |
| LEG-025 | 全局图谱/洞察 | ✅ | ✅ | not_run | ✅ | blocked | 全局洞察、聚合规则、语义审查卡片待补 |
| LEG-026 | LLM Provider 配置 | ✅ | ✅ | not_run | ✅ | blocked | 实时生成/runtime 消费待补 |
| LEG-027 | Profile 与密钥存储 | ✅ | ✅ | not_run | ✅ | blocked | 完整 key migration/release parity 待补 |
| LEG-028 | 连接测试与安全诊断 | ✅ | ✅ | not_run | ✅ | blocked | 真实外部 Provider 连通性仍属 P6 工作 |
| LEG-031 | 导出兼容性 | ✅ | ✅ | not_run | ✅ | blocked | 导出 parity 与投递矩阵待补 |
| LEG-032 | 导入兼容性 | ✅ | ✅ | not_run | ✅ | blocked | 真实迁移来源、fixture 演练与设备矩阵待补 |
| LEG-034 | 来源资料库与解析 | ✅ | ✅ | not_run | ✅ | blocked | 事务加固、复杂解析器、设备矩阵待补 |
| LEG-038 | 头像系统 | ✅ | ✅ | not_run | ✅ | blocked | per-session choose/clear/hide 未实现 |
| LEG-041 | 键盘处理 | ✅ | ✅ | not_run | ✅ | blocked | 更广泛设备矩阵待补 |
| LEG-042 | 无模型/Mock 行为 | ✅ | ✅ | not_run | ✅ | blocked | P6 parity 和双设备矩阵待补 |
| LEG-043 | 内置模板 | ✅ | ✅ | not_run | ✅ | blocked | 完整 fixture/export parity 待补 |

### 2.3 统计

| 状态 | 数量 |
|---|---:|
| verified | 3 |
| waived | 0 |
| blocked | 28 |
| **合计** | **31** |

---

## 3. 退出条件检查

只有以下条件全部满足，才可将结论提交给用户审批：

| 条件 | 状态 | 说明 |
|---|---|---|
| P0 全部 `verified` 或有明确 `waived` | ❌ 未满足 | 28/31 仍为 `blocked` |
| 模拟器矩阵完成 | ❌ 未满足 | 模拟器未运行，全部 `not_run` |
| 主力真机矩阵完成，或明确记录 `not_run` 并由用户批准风险 | ❌ 未满足 | 主力真机未提供 serial |
| 迁移指南经过至少一轮 fixture 导入演练 | ❌ 未满足 | 设备未运行，fixture 导入未执行 |
| 回滚和备份流程可执行 | ✅ 满足 | Runbook（P6-002）已编写，流程完整 |
| 签名、applicationId、PWA 生产路径无未授权改动 | ✅ 满足 | 冻结层零修改，签名/alias/applicationId 未动 |

---

## 4. 证据基线

### 4.1 代码与 JVM 测试（2026-08-17）

| 证据类型 | 覆盖 | 说明 |
|---|---|---|
| 代码证据 | 28/28 当前 blocker | P6-001 审计的全部未验证 P0 均有原生实现；3 条已验证 P0 单列于上表 |
| JVM 测试证据 | 28/28 当前 blocker | P6-001 审计的全部未验证 P0 均有对应 JVM 证据；3 条已验证 P0 单列于上表 |
| 契约映射 | 28/28 当前 blocker | P6-001 审计的全部未验证 P0 均有原生映射（部分 deferred/reduced） |
| 冻结层 | 零修改 | core/model, core/protocol, core/llm, core/data → 空 diff |

### 4.2 构建验证（2026-08-17 本轮）

| 任务 | 结果 |
|---|---|
| `:app:test` | BUILD SUCCESSFUL (28s, exitCode 0, all UP-TO-DATE) |
| `:app:lint` | BUILD SUCCESSFUL (8s, exitCode 0, 无 lint 错误) |
| `:app:assembleDebug` | BUILD SUCCESSFUL (6s, exitCode 0) |
| `:app:assembleDebugAndroidTest` | BUILD SUCCESSFUL (6s, exitCode 0) |
| `git diff --check` | 清洁 |
| 冻结层 diff | 空 |

### 4.3 设备证据

| 证据 | 来源 | 状态 |
|---|---|---|
| 14 device tests passed | 2026-08-17 P6-001 补测（emulator-5554） | 历史证据，本轮未重跑 |
| Phase2CoreLoopDeviceTest 3/3 | 同上 | 历史证据 |
| Phase5ContextHubDeviceTest 4/4 | 同上 | 历史证据 |
| Phase5GraphDeviceTest 5/5 | 同上 | 历史证据 |
| Phase5MemoryDeviceTest 1/1 | 同上 | 历史证据 |
| Phase5SourcesDeviceTest 1/1 | 同上 | 历史证据 |
| 本轮设备测试 | emulator-5554 未运行 | 全部 `not_run` |
| 主力真机 | serial 未提供 | 全部 `not_run` |
| TalkBack 人工听测 | 未执行 | `not_run` |

### 4.4 APK SHA-256

| APK | SHA-256 |
|---|---|
| app-debug.apk | `541E2669D54FA7B42F169EA4B9677353B750953891606FA109D3DD520008BD5C` |
| app-debug-androidTest.apk | `E74F8B97345C6800B3DF39EB20F6A2C03287460EB22641C7427912441255FA2B` |

---

## 5. 不得用于替代设备证据的声明

- 不得用"代码已实现"替代设备证据
- 不得用模拟器通过替代主力真机未执行
- 不得用 JVM 测试通过替代设备测试
- 不得用历史设备证据替代本轮矩阵（历史证据仅作参考）

---

## 6. 下一步

1. **用户启动 emulator-5554** → 重跑全部模拟器必测组（10 个测试类）
2. **用户提供主力真机 serial** → 执行主力真机必测组 + 手工检查 + TalkBack 听测
3. **设备测试通过后** → 重新评估 31 个 P0 LEG 状态
4. **fixture 导入演练** → 在模拟器和主力真机上各做一次小型 fixture 导入
5. **全部条件满足后** → 将结论从 `blocked` 改为 `proposed`，提交用户审批
6. **用户批准后** → 将结论改为 `approved`，方可执行 PWA/Capacitor 退出

---

## 7. 决策状态词汇

决策文档结论只能使用：`proposed`、`approved`、`rejected`、`blocked`。

当前结论：**`blocked`**

未获用户明确批准前必须保持 `proposed` 或 `blocked`。
