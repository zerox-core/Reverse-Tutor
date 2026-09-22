# 图谱页简化重设计分支（feat/graph-lite）

> 建分支日期：2026-09-21。基线：feat/graph-blackhole-v1 @ 07a92cf（黑洞版封存点）。
> 注：仓库 origin/main 是后端 Python 树（无 mobile-native），图谱 Android 代码只存在于
> 黑洞分支一脉，故本分支从黑洞封存点拉出，在其上做减法。

## 这条分支做什么

反转家教「图谱页」的简化重设计。在黑洞方案（feat/graph-blackhole-v1）基础上**降低难度**：

- **去除黑洞**：不再有引力 / 公转 / 净空带 / Dying 漩涡 / 吞噬门 / 吸入动画。
- **保留 Obsidian 式节点效果**：力导向布局（斥力 + 弹簧）＋ R92 交互高亮规格——
  边默认近隐形（alpha 0.10），点选或拖动节点时其关联边增亮（alpha ×4、线宽 ×1.6），
  非关联边与非邻居节点压暗（×0.22）。
- **遗忘 = 节点闪烁消失**：节点到期先闪烁呼吸（即将遗忘警告），随后淡出消失；
  不再坠入任何核心。闪烁期点按 = 抢救（沿用 R73 语义：复习完成、遗忘清零）。

## 旧方案（黑洞版）存档在哪

- Git 分支：`feat/graph-blackhole-v1`，停更点 commit `07a92cf`。
  停更原因与内容清单见该分支根目录 `BRANCH-ARCHIVE.md`。**该分支只读存档，不再迭代。**
- 飞书云盘离线包：`graph-blackhole-archive-2026-09-21.zip`
  （引擎 / 界面 / 测试 / 演示壳 / 两份设计文档，含 MANIFEST）。
- 两份设计文档同样保留在本仓库 docs/specs/ 历史提交中：
  《图谱物理法则》《图谱投影 × 记忆层决策记录》。

## 分支管理规则（本线长期有效）

1. **不切换共享检出**：主检出 `F:\xw\reverse-tutor-newmp` 停在 newmp（Agent 会话创建线在用）。
   本线所有操作走 `git worktree`（当前 worktree：`F:\xw\graph-lite-wt`）。
2. **提交边界**：只提交图谱线文件；工作树里其他线的改动不管、不提交。commit 前 `git branch -vv` 确认分支。
3. **每次任务完成即 commit + push 到 origin 本分支**，不等拍板。
4. 仓库：github.com/zerox-core/Reverse-Tutor（旧 zhuxice-ctrl URL 重定向可推）。
5. 已知坑：仓库配了 external diff driver，`git diff <ref> <ref>` 会报 cannot spawn——
   用 `git diff --stat` 或 `git show ref:path`；MCP execute_command 的 timeout 单位是毫秒；
   Windows 侧 subprocess 读 git 输出要 encoding="utf-8", errors="replace"（默认 GBK 会炸）。

## 交接给下一位开发者

- 运行环境：Windows + Cloudflare MCP（execute_command 走 py 子进程预授权通道；
  git push 直接跑会被拦，必须经 py subprocess）。
- 验证方式：引擎纯 Kotlin 单测（mobile-native/feature/memory 模块）
  ＋ 模拟器截图像素取证；真机（192.168.0.101:5555）只 install -r，不冷启动抢前台。
- 演示壳：mobile-native 内 app-graphtest 模块（注意选中态必须
  `graphState.copy(selectedNodeId = ...)` 回填，否则点按无反馈——R74 坑）。
- 待后端输入：真实遗忘曲线函数（替换占位时长参数）、AI 输出问题定义（黑洞版 R73 已定边界，沿用）。
