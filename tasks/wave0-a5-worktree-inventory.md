# Wave 0 · A5 Worktree 盘点与归档方案

> 生成时间：2026-08-14（周五）
> 执行人：本地开发搭档（feishu_mcp）
> 总纲：v2.1 审计结论与落地计划（`tasks/audit-conclusion-and-plan.md`）
> 验收标准：列出全部 worktree 及其分支和脏文件状态；**不删，先列清单等用户确认**。
> 事实来源：`git worktree list --porcelain` 等价物——因 feishu_mcp 无 `execute_command`，worktree 注册表直接读取自主仓 `.git/worktrees/` 元数据（`gitdir` + `HEAD`），与 `git worktree list --porcelain` 读取同一数据源；分支 tip 取自 `.git/refs/heads` 与 `.git/packed-refs`；脏状态由 `git_status` 逐 worktree 取得。**建议在本地 PowerShell 跑一次 `git -C F:\xw\reverse-tutor worktree list --porcelain` 与下表交叉核对。**

## 0. 主仓库状态

- 主仓 `F:\xw\reverse-tutor`，分支 `Android`，HEAD `9625298587bc7158b22be7cab141eb9a44ad71c3`（= `9625298`，与 A1 基线一致，origin/Android 同步 ahead=0/behind=0）。
- 工作树脏文件（A1/A2 产物 + AGENTS.md 更正，无业务代码改动）：
  - `M  AGENTS.md`
  - `?? tasks/audit-conclusion-and-plan.md`
  - `?? tasks/wave0-a1-baseline.md`
  - `?? tasks/wave0-a2-test-baseline.md`
  - `?? tasks/wave0-a3-freeze-boundary.md`（本次新增）
  - `?? tasks/wave0-a4-ownership-table.md`（本次新增）
  - `?? tasks/wave0-a5-worktree-inventory.md`（本次新增）

## 1. Worktree 全量盘点（15 个，取自注册表）

> `F:\xw\worktrees\audit` 只是分类目录，**不是 Git worktree**，不在注册表中。其下的 `reverse-tutor-index-check` 才是已登记的 detached 审计参考快照（见 #5），**必须保留**，不得列为「非 Git 仓库 / 可归档 / 可删除」。

### 1.1 主仓 F:\ 下 worktree（13 个）

| # | worktree 路径 | HEAD / 分支 | 分支 tip | 脏状态 | 未提交文件 | 建议动作 |
|---|---|---|---|---|---|---|
| 1 | `F:\xw\worktrees\reverse-tutor-exp` | `work/product-experience` | `0c8b95b8` | 干净 (0) | — | 已合并；待用户确认后可归档 |
| 2 | `F:\xw\worktrees\reverse-tutor-feishu-design` | `feat/ux-polish-2026-07-27` | `1c02d7a5` | 干净 (0) | — | 保留；归档前需 merge-base 验证 |
| 3 | `F:\xw\worktrees\audit\reverse-tutor-index-check` | **detached @ `918c814`** | — | 干净 (0) | — | **必须保留**（历史索引/审计参考快照，detached，不可归档/删除） |
| 4 | `F:\xw\worktrees\reverse-tutor-memory` | `work/memory-architecture` | `32a9fdcb` | 脏 (3) | `M mobile/android/app/capacitor.build.gradle`、`M mobile/android/capacitor.settings.gradle`、`M mobile/package-lock.json` | 已合并；需先处理未提交改动（均为 Capacitor/PWA 侧） |
| 5 | `F:\xw\worktrees\reverse-tutor-task-2b2a` | `task/2b2a-tags-columns` | `f167a354` | 脏 (3) | `?? .superpowers/sdd/task_plan/task-2b2a-{fix1,fix2,}-report.md` | 仅未跟踪报告；代码干净，保留 |
| 6 | `F:\xw\worktrees\reverse-tutor-task-2b2b` | `task/2b2b-session-settings` | `c7dc82f1` | 脏 (5) | `?? .superpowers/sdd/task_plan/task-2b2b-{fix1..fix4,}-report.md` | 仅未跟踪报告；代码干净，分支尚未合并，保留 |
| 7 | `F:\xw\worktrees\reverse-tutor-task-3a` | `task/3a-chat-composer` | `d28b3dd9` | 干净 (0) | — | 保留；归档前需 merge-base 验证 |
| 8 | `F:\xw\worktrees\reverse-tutor-task-3b` | `task/3b-message-actions` | `c856e351` | 脏 (1) | `?? .superpowers/sdd/task_plan/task-3b-report.md` | 仅未跟踪报告；保留 |
| 9 | `F:\xw\worktrees\reverse-tutor-task-4a` | `task/4a-weekly` | `7614fd9a` | 脏 (1) | `?? .superpowers/sdd/task_plan/task-4a-report.md` | 仅未跟踪报告；保留 |
| 10 | `F:\xw\worktrees\reverse-tutor-task-4b` | `task/4b-graph` | `cf506632` | 脏 (1) | `?? .superpowers/sdd/task_plan/task-4b-report.md` | 仅未跟踪报告；保留 |
| 11 | `F:\xw\worktrees\reverse-tutor-task-4c` | `task/4c-challenge` | `42c6eb25` | 脏 (1) | `?? .superpowers/sdd/task_plan/task-4c-report.md` | 仅未跟踪报告；保留 |
| 12 | `F:\xw\worktrees\reverse-tutor-task2b1` | `codex/task2b1-implementation` | `8fb6bcde` | 干净 (0) | — | 保留；归档前需 merge-base 验证 |
| 13 | `F:\xw\worktrees\reverse-tutor-task4-integrate` | `task/integrate-task4` | `c7a9bfbf` | 干净 (0) | — | 保留；归档前需 merge-base 验证 |

### 1.2 外部路径 Codex worktree（2 个，单独列出）

> 这两个 worktree 位于 `C:\Users\Lenovo\.config\superpowers\worktrees\reverse-tutor\`，**超出 feishu_mcp 授权目录 F:\**，`git_status` 返回 `DIRECTORY_APPROVAL_REQUIRED`，脏状态**待用户授权后补查**。分支与 HEAD 已从主仓注册表确认。

| # | worktree 路径 | HEAD / 分支 | 分支 tip | 脏状态 | 建议动作 |
|---|---|---|---|---|---|
| 14 | `C:\Users\Lenovo\.config\superpowers\worktrees\reverse-tutor\codex-v4-image-card` | `codex/v4-image-card` | `4f5e7d5b` | 脏 (4) | 已合并；先处理 `static/app/index.html`、`static/app/sw.js`、`tests/test_image_memory.py`、`tests/test_mobile_persistence.py` 再决定归档 |
| 15 | `C:\Users\Lenovo\.config\superpowers\worktrees\reverse-tutor\knowledge-node-panel` | `codex/knowledge-node-panel` | `e5e73467` | 脏 (2) | 未合并；保留，脏文件为两个 Capacitor Gradle 配置 |

## 2. 分支合并状态（必须由 merge-base 验证，不凭名称推断）

> 已由本地 Git 在 2026-08-14 验证：`git merge-base --is-ancestor <branch> Android` 返回 0 表示 branch tip 已包含在 Android；返回 1 表示存在未合并提交。以下命令保留为后续复核入口（Android tip = `9625298587bc7158b22be7cab141eb9a44ad71c3`）：

```powershell
cd F:\xw\reverse-tutor
# 对每个分支：输出 0 表示已合并进 Android，输出 1 表示有未合并 commit
foreach ($b in @(
  'work/product-experience','feat/ux-polish-2026-07-27','work/memory-architecture',
  'task/2b2a-tags-columns','task/2b2b-session-settings','task/3a-chat-composer',
  'task/3b-message-actions','task/4a-weekly','task/4b-graph','task/4c-challenge',
  'codex/task2b1-implementation','task/integrate-task4',
  'codex/v4-image-card','codex/knowledge-node-panel'
)) {
  git merge-base --is-ancestor $b Android; Write-Host "$b -> $LASTEXITCODE"
}
```

| 分支 | tip | 合并状态 |
|---|---|---|
| `work/product-experience` | `0c8b95b8` | 已合并（exit 0） |
| `feat/ux-polish-2026-07-27` | `1c02d7a5` | 未合并（exit 1） |
| `work/memory-architecture` | `32a9fdcb` | 已合并（exit 0；仍有 3 个未提交改动） |
| `task/2b2a-tags-columns` | `f167a354` | 未合并（exit 1） |
| `task/2b2b-session-settings` | `c7dc82f1` | 未合并（exit 1） |
| `task/3a-chat-composer` | `d28b3dd9` | 未合并（exit 1） |
| `task/3b-message-actions` | `c856e351` | 未合并（exit 1） |
| `task/4a-weekly` | `7614fd9a` | 未合并（exit 1） |
| `task/4b-graph` | `cf506632` | 未合并（exit 1） |
| `task/4c-challenge` | `42c6eb25` | 未合并（exit 1） |
| `codex/task2b1-implementation` | `8fb6bcde` | 未合并（exit 1） |
| `task/integrate-task4` | `c7a9bfbf` | 未合并（exit 1） |
| `codex/v4-image-card` | `4f5e7d5b` | 已合并（exit 0；仍有 4 个未提交改动） |
| `codex/knowledge-node-panel` | `e5e73467` | 未合并（exit 1；仍有 2 个未提交改动） |

> `reverse-tutor-index-check` 为 detached（`918c814`），无分支，不适用合并判断——它是审计参考快照，**必须保留**。

## 3. 归档方案（不删，等用户拍板）

> 以下仅给建议，**不执行任何删除/移动**。归档属不可逆操作，需用户逐条确认，且前置条件是上节 merge-base 验证通过。

### 3.1 可安全归档候选（已验证合并，但仍需用户逐条确认）

| worktree | 分支 | 前置条件 |
|---|---|---|
| `reverse-tutor-exp` | `work/product-experience` | 已合并且工作树干净；用户确认后可归档 |

### 3.2 需先处理脏文件再判断

| worktree | 分支 | 脏文件 | 建议 |
|---|---|---|---|
| `reverse-tutor-memory` | `work/memory-architecture` | 3 个 Capacitor/PWA 文件（M） | 分支已合并；先 `git stash` / `git checkout -- <files>` / 评估是否保留，再由用户确认是否归档 |
| `codex-v4-image-card`（外部） | `codex/v4-image-card` | 4 个 PWA/测试文件（M） | 分支已合并；先处理未提交改动，再由用户确认是否归档 |

### 3.3 需单独处理 / 必须保留

| worktree | 情况 | 建议 |
|---|---|---|
| `audit\reverse-tutor-index-check` | detached 审计参考快照 `918c814` | **必须保留**，不归档不删除 |
| `reverse-tutor-feishu-design` | `feat/ux-polish-2026-07-27` | 未合并，保留 |
| `task-*` 与 `task/integrate-task4` | 对应 task 分支 | 均有未合并提交，保留；不可归档 |
| `knowledge-node-panel`（外部） | `codex/knowledge-node-panel` | 未合并且有 2 个未提交改动，保留 |

> `F:\xw\worktrees\audit` 目录本身非 worktree，无需 git 操作；其内 `reverse-tutor-index-check` 必须保留。

## 4. 归档操作模板（需用户逐条确认后方可执行）

```powershell
# 1. 确认分支已合并到 Android（输出空 = 已合并）
git -C F:\xw\reverse-tutor log Android..<分支名> --oneline

# 2. 如已合并，移除 worktree（不可逆，逐条确认）
git -C F:\xw\reverse-tutor worktree remove F:\xw\worktrees\<worktree名>

# 3. 可选：删除已合并的本地分支
git -C F:\xw\reverse-tutor branch -d <分支名>
```

> 外部 Codex worktree 的 `worktree remove` 需用其完整 C:\ 路径。

## 5. A5 完成自检

- [x] 列出全部 15 个注册 worktree（13 个 F:\ + 2 个外部 Codex），逐个记路径/HEAD/分支/detached/脏状态/未提交文件/建议。
- [x] `F:\xw\worktrees\audit` 标注为分类目录（非 worktree），`reverse-tutor-index-check` 标注为 detached 审计快照 `918c814` 且必须保留。
- [x] 外部 Codex worktree 单独列出（#14/#15），不遗漏。
- [x] 不删任何 worktree，只列清单与归档方案，操作模板标注需用户逐条确认。
- [x] 分支合并状态已由本地 `merge-base --is-ancestor` 验证，并保留复核命令。
- [x] 未改业务代码（仅新增本文档）。

## 6. 遗留与下一步

- **reverse-tutor-memory 脏文件**：需用户决定 stash / commit / discard。
- **外部 Codex worktree 脏文件**：`codex-v4-image-card` 与 `knowledge-node-panel` 均有未提交改动，需用户决定保留、提交或丢弃。
- **工具局限说明**：feishu_mcp 不具备 `git diff --check` 与 `git merge-base`；本报告的最终校验已由本地 Git 补齐。`git_status` 对 detached HEAD 的 `reverse-tutor-index-check` 返回过错误分支名，现以注册表 HEAD `918c814` detached 为准。
- A2 测试基线已由用户提供并落盘 `tasks/wave0-a2-test-baseline.md`（Native JVM 1101 passed / 0 failure/error；Python 510 passed / 28 skipped / 356.62s），Wave 0 全部完成。
