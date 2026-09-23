# 大阶段一开发文档：创建会话与会话交互闭环（方向一+二主体）

> 2026-09-23 按用户拍板的长线编排补写（1a 已先行完成）。路线总纲见 docs/V1-DEVELOPMENT-PLAN.md §11。
> 流程铁律：小阶段垂直开发（纯 Kotlin 核心 → Android 适配 → UI 接线 → 单测 → 模拟器验证 → 提交推送），每个小阶段完成后立即 commit + push origin/Android。

## 阶段目标

会话的创建与交互形成完整闭环：能创建会话、能说话（语音/图片/富文本）、模型回复可读（富文本渲染）、上下文有记忆、资料能进上下文（RAG）、闲聊不打架。

## 小阶段路线

### 1a 语音输入 ✅ 已完成（2026-09-23，commit ff7d14a）
系统 SpeechRecognizer 语音转文字进输入框。状态机 VoiceInputController + 引擎 SpeechRecognizerVoiceInputEngine + 路由层接线 + 麦克风按钮 + RECORD_AUDIO 权限。13 单测 + 模拟器 E2E（权限授权→聆听态→回落空闲）。不做 TTS 播报（第一版明确排除）。

### 1b 图片上传端到端验证 ✅ 已完成（2026-09-23，commit 152f032）
验收结果：相册选图 chip / 移除 / 20MB 拒绝 / 权限流全通；拍照入口模拟器不可达，记 O1 待产品确认。
代码链路此前已确认完整：ChatComposerContracts（Image/Camera，单张 ≤20MB，每条 ≤9 附件）→ core:model MessageAttachment → LlmGenerationLifecycle 组 OpenAI image_url 载荷（base64/URL 两种形态），主代码与单测均在。本小阶段不新写产品代码，做端到端实证：
- 相册选图 → 附件 chip 进输入框 → 发送 → 抓载荷确认 image_url 形态 → 模型回包正常渲染
- 拍照入口可达性（模拟器相机可用性受限，不可达则记录不强行）
- 边界：>20MB 拒绝提示、9 附件上限提示
- 产出：E2E 脚本与验证记录（docs/verification/1b-image-e2e.md），测试资产随仓库提交
- 发现的 bug 只记录不修复（留给修复阶段）

### 1c 富文本渲染 ✅ 已完成（2026-09-23，c1 commit f196205 / c2 commit deda30b）
验收结果：c1 七语言纯 Kotlin 分词器 + 流式安全回退（350 单测绿）；c2 LaTeX 纯 Kotlin 子集解析 + Compose 自绘块级/行内（396/396 绿）；均过模拟器双截图实测。
现状：RichReplyContracts.CodeBlock 存在但仅纯文本块，无语法高亮、无 LaTeX。
- c1 代码块语法高亮（常见语言：kotlin/java/python/json/xml/sql/bash；纯 Kotlin 分词器优先，不引重型原生依赖）
- c2 LaTeX 公式渲染（行内 $...$ 与块级 $$...$$；选型在动工时定：轻量解析自绘优先，WebView 兜底方案记录权衡）
- 验收：渲染正确性单测 + 模拟器截图实测；流式输出中途的半完整块不炸屏

### 1d 创建面板设计定稿 ⏸ 暂定（依赖用户设计输入）
创建会话面板的视觉/交互定稿需要用户设计稿或风格拍板，无明确标准不主观抉择。用户给设计输入后插队执行。

### 1e RAG 调优与内置向量兜底 ✅ 已完成（2026-09-23，e1b+e2 commit dad4e7f / e3 commit 76d1e6b）
验收结果：e1 核查确认服务器无 /v1/embeddings 能力，最小转发方案见 docs/dev/PHASE1-1E-EMBEDDING-FALLBACK.md；e2 降级链（用户渠道→内置 bge-m3 匿名转发→关键词回退）+ DB v19 embeddingModel；e3 8 篇资料 16 查询回归，chunk 500/50/80 维持，MinVectorScore 分布与中文关键词回退两项记暂定。
拍板背景（2026-09-23 §11）：内置硅基流动 bge-m3 免费档做兜底 + 用户自配渠道优先；key 不明文进 APK，走服务器转发；不自托管（4C4G 生产机内存/算力/运维均不成立）。
- e1 服务器转发端点：核查服务器侧现状（nginx/转发服务是否已具备 OpenAI 兼容 /v1/embeddings 转发能力），不具备则设计最小转发方案；服务器侧改动超出现有明确标准时记入暂定项
- e2 端上内置渠道：内置渠道配置（无 key 时走内置兜底顺序：用户渠道 → 内置 bge-m3 → 关键词回退），EmbeddingModelDiscovery 已支持自动识别，补内置渠道的默认配置与降级链
- e3 RAG 调优：相似度下限、chunk 参数、关键词回退质量的实测调参（用真实资料集回归）
- 验收：检索命中率对比记录（调参前后），降级链单测

### 1f 记忆层 L1/L2/L3 ✅ 已完成（2026-09-23，审计 + 生产链路 E2E 收官）
审计结论：「L1/L2/L3 缺失」的现状判断不成立——窗口记忆四层（L0-L3）与掌握度台账在 NEWMP-V2 时代已建成（8402fd8 等）。逐层映射与证据见 docs/dev/PHASE1-1F-MEMORY-AUDIT.md：全量单测 1578 用例 0 失败（记忆相关 18 套件 117 用例全绿）。
E2E 实测发现审计漏判：intake 分派原接在 SessionConversationAssembly.runTurn()，而生产轮次走 BackgroundGenerationWorker（runTurn 无生产调用方，接线「存在但不可达」）→ 修复为在 Worker Completed 分支派发（DataModule 装配，intake 自吞异常不拖垮已完成轮次，决策 #9）。修复后模拟器 E2E 全绿：72 条消息滑窗 kept=41/evicted=31、折叠摘要 227 tokens 入库、下轮窗口注入 237 tokens（injection_rows=1）、window_kept 计量表全程记录。详见 docs/verification/1f-memory-intake-e2e.md。
- 与遗忘曲线的对接属后端算法层（用户拍板：等后端完整设计后按接口做投影），维持暂定
- 暂定：1f-t1 事件类型四分+图谱 Node/Event 投影（属大阶段三任务线，届时直接采用 spec 阈值）；1f-t2 BYOK 提取器（V2 决策#5 后期项）；1f-t4 分身窗口内置不可删契约落点（NEWMP-V2 待定）
- 观察（记录不处理）：RuleBasedMemoryExtractor 对 KEY[15] 类句式不产 LEARNING_EVENT；生产轮次写 turn_run_trajectories 而非 turn_runs

### 1g 闲聊兼容
意图分流：闲聊类输入不进重装配管线（或走轻量路径），保证随意对话体验不被 RAG/记忆装配拖慢或污染。
- 分流规则需有明确判定标准（规则/小模型/提示词），动工时若标准不明确记入暂定项
- 验收：闲聊不进装配的单测 + 模拟器实测响应路径

## 大阶段一完成定义
1a-1g（除暂定项外）全部完成并各自提交推送 → 派遣分支窗口跑整体验证（全量构建 + 全量单测 + 模拟器 E2E）→ 测试文档存 docs/verification/ → 进入大阶段二。
