# Wave 0 · A1 事实基线报告

> 生成时间：2026-08-14（周五）
> 执行人：本地开发搭档（feishu_mcp）
> 总纲：v2.1 审计结论与落地计划（`tasks/audit-conclusion-and-plan.md`）
> 验收标准：基线报告与当前代码一致；不改业务代码。

## 1. Git 与工作树状态

| 项 | 值 |
|---|---|
| 主线分支 | `Android` |
| HEAD 提交 | `9625298`（已由本地 Git 复核） |
| 工作树 | dirty=3：已跟踪文档更正 `AGENTS.md`；未跟踪 `tasks/audit-conclusion-and-plan.md`、`tasks/wave0-a1-baseline.md` |
| 已跟踪改动 | `AGENTS.md` 1 处体积事实更正 |
| 远端同步状态 | `Android...origin/Android`，ahead=0 / behind=0（已由本地 Git 复核） |

结论：主线 `Android` 与远端同步；工作树仅包含 Wave 0 的治理文档变更，无业务代码改动，可直接作为后续切片的工作基线。不推送、不打 tag。

## 2. AGENTS.md 体积事实更正

| 项 | 旧值 | 真实值 | 来源 |
|---|---|---|---|
| `static/app/index.html` 体积 | 单文件 ~441KB | 单文件 ~676KB / 692236 字节 | `get_file_info` 实测（modified 2026-06-06） |

已在 `AGENTS.md` 「一句话架构」第 2 条就地更正（1 处替换成功）。这是文档事实更正，不涉及业务代码。

## 3. P0 替换基线（来源：`tasks/native-legacy-coverage-registry.md`，2026-07-02）

| 状态 | 计数 |
|---|---:|
| verified | 4 |
| in_progress | 38 |
| not_started | 2 |
| implemented | 0 |
| blocked | 0 |
| waived | 0 |

**当前 P0 replacement blockers：28。**

已 verified 的 4 条（P0 闭环）：
- LEG-001（`:app` 启动 / 包名 `com.reversetutor.preview`）
- LEG-033（WIPE 确认 / 本地数据清除）
- LEG-039（Back 栈行为）
- LEG-044（About 诊断 / PWA 安装提示缺失）

未启动的 2 条（均 P1·watch）：
- LEG-029（主动模式设置，产品决策未定）
- LEG-040（会话滑动手势 / 边缘手势 parity）

28 个 P0 blocker 清单（与 registry「Replacement Blocker Summary」一致）：
LEG-002, LEG-005, LEG-006, LEG-008, LEG-009, LEG-010, LEG-012, LEG-013, LEG-014, LEG-015, LEG-016, LEG-017, LEG-019, LEG-020, LEG-021, LEG-022, LEG-024, LEG-025, LEG-026, LEG-027, LEG-028, LEG-031, LEG-032, LEG-034, LEG-038, LEG-041, LEG-042, LEG-043。

> 维护规则沿用 registry：后续切片改动 native 行为时，同步更新对应行；P0 行仅在 `verified` 或显式 waiver 后方可 `closed`；waiver 同时记入 `.adworkflow/review_findings.json`。

## 4. 三条实现线边界

| 实现线 | 路径 | 技术栈 | 角色 |
|---|---|---|---|
| Python 后端 | `server.py` / `engine.py` / `db.py` / `retrieval.py` / `websearch.py` / `kg_*.py` | FastAPI + SQLite | 测试覆盖主体（`tests/`，320+ 用例，`pytest.ini` asyncio_mode=auto，conftest 强制 mock LLM） |
| PWA / Capacitor | `static/app/index.html`（单文件 ~676KB） | 内含独立 JS 引擎（自有 `chat_json`、评估/动作逻辑、知识图谱 canvas、直连 LLM） | 安卓 APK 打包来源（经 Capacitor 包成 `mobile/`）；迁移参考，退出须 Phase 6 + 用户批准 |
| 原生 Android 迁移 | `mobile-native/` | Kotlin / Compose / Room / 多模块 Gradle | 当前产品方向；native UI 可重设计，后端协议/数据层已冻结 |

边界硬规则（AGENTS.md §0.1）：
- 修 bug 或做 UI 前先判断目标在后端、PWA 还是 `mobile-native/`。
- native UI 重构不得改动旧 PWA/Capacitor 未提交内容，也不得宣布 PWA 已退出。
- 冻结层（`core/model`、`core/protocol`、`core/llm`、`core/data/*Repository`、`core/data/local`、`core/data/preferences`、`SecretStore.kt`、Room schema/migrations）未获批准不得改。
- UI/feature 只能经 Repository / domain model / protocol facade 调用业务能力；不得直接调 Room DAO/Entity/`ReverseTutorDatabase`/`DatabaseSchema`/SQL/schema JSON/Keystore secret。

## 5. A1 完成自检

- [x] 记录 Android HEAD、Git 状态、远端同步状态（本地 Git 已复核）。
- [x] 更正 AGENTS.md 中 index.html 体积事实（441KB → 676KB / 692236 字节）。
- [x] 记录 P0 基线（28 blockers / 4 verified / 38 in_progress / 2 not_started）。
- [x] 记录三条实现线边界。
- [x] 未改业务代码（仅 AGENTS.md 文档更正 + 新增本报告）。

## 6. 遗留与下一步

- 下一项：**A2 测试基线**——运行 `.\gradlew.bat test`（Native JVM 全量）与 `py -m pytest -q --ignore=tests/test_project_homepage.py`（Python 基线），记录失败项/环境性失败/xfail，形成可比对基线。受 feishu_mcp 能力限制，测试命令需在本地终端或经 `windows_development` 受信环境执行。
