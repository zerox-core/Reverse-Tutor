# 1f 记忆层端到端验证（V2-004 落地收官）

- **日期**：2026-09-23
- **环境**：emulator-5554（rt_test AVD）· com.reversetutor.preview.memtest · debug 构建（本地，分支 Android）
- **范围**：完整链路 = 轮次完成 → 摄取分派 → 滑窗策略 → 淘汰折叠 → 折叠摘要入库 → 下轮窗口注入 + token 计量表
- **状态**：**通过（4/4 阶段全绿）**

## 1. 结论

1f（记忆层 L1/L2/L3 端到端）在真实 App 轮次路径上完整跑通：消息滑窗在轮次完成后异步淘汰超限消息并折叠成摘要，摘要进入下轮窗口注入，窗口 token 计量表全程记录。生产链路摄取分派的最后缺口（生产轮次不触发 intake）已修复并实测验证。

## 2. 背景与根因

E2E 首轮跑完后发现 intake 管线零输出（watermark / 摘要 / 计量表全空），静态接线和单元测试却全绿。排查过程：

1. 重建重装排除旧 APK 因素——零输出依旧。
2. 回读源码定位：intake 分派原本接在 `SessionConversationAssembly.runTurn()`（line 189），**但生产路径从不调 runTurn**——真实轮次走 `BackgroundGenerationWorker`（WorkManager）→ `BackgroundGenerationRepository.runGenerationJob()`。接线「存在但不可达」。
3. 修复：在 Worker 的 Completed 分支接上 intake 分派（与 completionProcessor 同型，经 DataModule 构造依赖），intake 自吞异常不会拖垮已完成轮次（决策 #9）。
4. 重建 + E2E 复跑：全绿。

（此根因在 1f 代码审计文档中曾被判为「接线完整」，实际漏掉了 runTurn 无生产调用方这一点，见该文档附录修正。）

## 3. E2E 四阶段实测

测试脚本：`.aily_tmp_1f_e2e.py`（工作树 scratch，不入库）——清 DB → 种子会话（70 条消息超 60 上限）→ turn1 → Stage C 校验 → turn2 → Stage D 校验。

### Stage C（第 1 轮后：淘汰折叠 + 摘要 + 计量）

- 72 条消息，滑窗保留 41 / 淘汰 31，watermark = `e2e1f-m31`
- 折叠摘要入库：foldSummaryMessageId = e2e1f-m31（227 tokens）
- 观察（observation）入 memory_observations：`GOAL_STATEMENT/stated_goal/0.90/HIGH` ×1、`REMINDER_FEEDBACK/acknowledged_reminder/0.95/HIGH` ×2
- token 计量表：`window_kept = 5882`，detail `kept=41,evicted=31`

### Stage D（第 2 轮：窗口注入生效）

- 第 2 轮的注入计量：`injection = 237` tokens，`values=2, patterns=2, summary=true`，`injection_rows = 1`（第 2 轮窗口确实带上了记忆值 + 折叠摘要）
- 轮后再计量：`window_kept = 5955`，`kept=43,evicted=31`

### Stage A/B（构建产物）

- debug 构建 2 次成功（46s 增量 / 52s 含 compileDebugKotlin），安装成功。

## 4. 观察项（记录，不处理，待后续拍板）

- **O-1**：`RuleBasedMemoryExtractor` 对 KEY[15]（「我每天背20个单词」类）不产生 LEARNING_EVENT 观察——当前按规则设计如此，是否扩提取规则属产品决策。
- **O-2**：生产轮次不写 `turn_runs` 表而写 `turn_run_trajectories`（实测 turn_runs=0、trajectories=2）——1d-3 轮次持久化行为的延续记录，非本次缺陷。
- **O-3**：Stage C 首轮 watermark 为 m31 而非脚本预估的 m30——种子 70 条 + 用户 turn 消息 + 若干系统/助手消息共 72 条参与策略；实跑与预估差 1 属脚本预估误差，策略行为正确。

## 5. 缺口修复记录

`BackgroundGenerationWorker.kt`（app）：

- Completed 分支新增 intake 分派（`windowIntakeDispatcherForTurn(applicationContext).dispatch(job.sessionId)`），随轮次完成异步触发，失败不影响轮次。
- 新增 `windowIntakeDispatcherForTurn()` 工厂，从 DataModule 单例构造 `WindowMemoryIntakeCoordinator` + `WindowIntakeDispatcher`（与 HybridAppGraph 同型的依赖装配）。

`SessionConversationAssembly.runTurn()` 中的分派保留（测试/非生产路径仍可用）；生产路径以 Worker 为准。
