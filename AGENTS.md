# AGENTS.md — Reverse Tutor（反转家教）协作硬规则

> 本文件被 Codex CLI 每次会话自动读取。**这里是不可违反的硬规则**；完整背景、技术细节与测试方向见 `docs/CODEX_HANDOFF.md`。

## 0. 不确定性停机规则（最高优先级）

- 对需求、范围、目标文件、设计、交互、协议、数据结构、默认值、兼容策略、删除或保留内容、测试范围、提交范围、Agent 分工存在任何不确定、冲突或多种解释时，必须立即停止。
- 必须向用户提出具体问题，并等待用户明确确认后再继续。
- 禁止自行猜测用户意图、补全未确认需求、选择未经确认的方案，或基于“通常如此”“应该如此”等假设继续执行。
- 在不确定项确认前，不得生成实现、修改代码、修改文档、改动协议或数据结构、执行提交、派发子 Agent，或宣称任务已经完成。
- 用户已经明确确认的部分可以保留，但不得用已确认内容推导未确认内容。

## 0. 一句话架构（最重要，先读）

本仓库当前是**三条线并存**：

1. **Python 后端**（`server.py` / `engine.py` / `db.py` / `retrieval.py` / `websearch.py` / `kg_*.py`）—— FastAPI + SQLite，**测试覆盖在这里**（`tests/`，320+ 用例）。
2. **客户端 PWA**（`static/app/index.html`，单文件 ~441KB）—— **内含一套独立的 JavaScript 引擎**（自己的 `chat_json`、评估/动作逻辑、知识图谱 canvas、直连 LLM）。**安卓 APK 打包的就是它**（经 Capacitor 包成 `mobile/`）。
3. **原生 Android 迁移线**（`mobile-native/`）—— 当前产品方向是继续开发 native Android。PWA/Capacitor 仍保留为迁移来源和行为参考，退出 PWA 必须等 Phase 6 并由用户明确批准。

> ⚠️ **不要把三条线混成一条。** Python 后端、PWA/Capacitor、native Android 是不同实现面。修 bug 或做 UI 前先判断目标在后端、PWA，还是 `mobile-native/`。当前 native UI 重构不能改动旧 PWA/Capacitor 未提交内容，也不能宣布 PWA 已退出。

## 0.1 Native Android 架构冻结规则

当前 native 前端可以重新设计，但后端协议接口层和数据库/数据层已经先行定型。完整约束见 `tasks/native-backend-protocol-data-contract-freeze.md`。

冻结范围：

- `mobile-native/core/model`
- `mobile-native/core/protocol`
- `mobile-native/core/llm`
- `mobile-native/core/data/*Repository`
- `mobile-native/core/data/local`
- `mobile-native/core/data/preferences`
- `mobile-native/core/data/llm/SecretStore.kt`
- Room schema exports and migrations

UI 重构主要允许改：

- `mobile-native/app/src/main/java/com/reversetutor/preview/theme`
- `mobile-native/app/src/main/java/com/reversetutor/preview/ui`
- `mobile-native/app/src/main/java/com/reversetutor/preview/shell`
- `mobile-native/feature/*`

硬规则：

- UI/feature 只能通过 Repository、domain model、protocol facade 调用业务能力。
- UI/feature 不得直接调用 Room DAO、Room Entity、`ReverseTutorDatabase`、`DatabaseSchema`、SQL、schema JSON、Keystore secret。
- UI 重构不得修改 `DatabaseSchema.version`、Room entities、DAO query、protocol schema、import/export payload、background-generation persistence、SecretStore 行为。
- 不得在 UI 任务中改 `BackgroundGenerationRepository` token/session 隔离、`NativeImportRepository` overwrite/new-space 语义、`NativeExportRepository` secret redaction、`SecretStore` 加密/存储策略。
- 可见 UI 文案优先中文，但中文标签在 UI 层映射；不要为了展示中文去改 domain enum 或 protocol wire value。

## 1. 环境硬规则（Windows / PowerShell）

- **Python 解释器只用 `py`**，绝不用 `python`（`python` 是坏掉的 WindowsApps stub）。例：`py -m pytest`。
- **不要创建任何 python shim 目录**去绕过上一条。
- 终端是 PowerShell，**命令里不要用 `cd`**；需要目录就用工具的 `cwd` 参数。
- 不要 `git push`、不要打 tag，除非用户明确要求。改完留在工作树等审查。

## 2. 测试硬规则

- 跑全量：`py -m pytest -q --ignore=tests/test_project_homepage.py` —— **必须保持绿**（基线 320 passed）。
- 跑单文件：`py -m pytest tests/test_xxx.py -v`。
- `pytest.ini`：`asyncio_mode = auto`（async 测试直接 `async def`，不用装饰器）。
- 测试通过 `tests/conftest.py` 强制 mock LLM（`llm._HAS_REAL=False`），**测试绝不许打真实网络/真实 LLM**。
- 改了行为先改/补测试；**不许删除或弱化已有测试**来"让它过"。
- 发现真实 production bug 时：补一个会失败的测试或 `xfail` 标注 + 在输出里说明，**不要静默改无关生产逻辑**。

## 3. 代码改动硬规则

- **最小上游修复优先**：先定位根因，能一行解决就别重构；不要下游打补丁掩盖症状。
- 新增函数/构造参数**必须带安全默认值**，保持向后兼容（例：`run_turn(..., retriever=None, web_search=None)`）。
- **不要增删注释/文档**，除非用户要求。
- import 一律放文件顶部。
- 不要碰签名：`mobile/android/app/release.jks`、alias `reverse-tutor`、`applicationId com.reversetutor.app` 一律不动（见 `mobile/BUILD_APK.md`）。

## 4. 协作收尾硬规则（踩过的坑）

- `codex exec` 跑完后**确认进程真的退出**再编辑它碰过的文件——残留的 `--auto` agent 会把你的修改回滚回它的版本。
- `CHANGELOG.md` 是 **GBK 编码**，PowerShell 直接 `Get-Content` 会乱码；读它用 `py -c "open(path,encoding='gbk')..."`。

## 5. 改完自检清单

1. 判断对了改后端还是改 `static/app/index.html`？
2. `py -m pytest -q --ignore=tests/test_project_homepage.py` 仍绿？
3. 新参数有默认值、向后兼容？
4. 没动签名/版本号（除非被要求）？
5. 在回复里写清楚：改了哪些文件、测试 pass 数、有没有发现新 bug。
