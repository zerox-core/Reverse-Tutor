# Reverse Tutor Figma 原生前端落地设计

日期：2026-07-10

## 目标与范围

以 Figma 文件 `VPV0vclAhALpF8QIGvxYlo`、交付包 `D:\暂时\Reverse-Tutor-Figma-Handoff` 及其中的 `DESIGN_SPEC.md`、`UX_FLOW.md`、`design-tokens.json` 为唯一产品设计基准，在 `mobile-native/` 中完成 15 个正式页面与状态的 Jetpack Compose 原生实现。目标设备为华为 Mate 60，视觉基准为 390 × 884，主要布局偏差控制在 2px 等价范围内。

旧 PWA/Capacitor 保留为迁移与行为参考，不在本次任务中修改或宣布退出。Figma 旧稿节点 `64:2` 不实现。

## 方案选择

采用“现有原生工程内的 UI 层重构”方案：复用现有 app shell、feature 模块、Repository 和 domain model，在允许的 Compose UI 范围内替换视觉层、补齐页面与状态、连接现有能力。

未采用以下方案：

- 改造旧 PWA：会偏离 Native Android 当前主线。
- 新建第二套客户端：会复制导航、状态和数据适配逻辑，增加长期维护成本。

## 架构边界

允许修改：

- `mobile-native/app/src/main/java/com/reversetutor/preview/theme`
- `mobile-native/app/src/main/java/com/reversetutor/preview/ui`
- `mobile-native/app/src/main/java/com/reversetutor/preview/shell`
- `mobile-native/feature/*`
- 对应 UI 单元测试和 instrumentation 测试

禁止在本次 UI 落地中修改：

- `mobile-native/core/model`
- `mobile-native/core/protocol`
- `mobile-native/core/llm`
- `mobile-native/core/data` 中的 Repository 行为、Room schema、Entity、DAO、迁移和 SecretStore
- 旧 PWA/Capacitor 业务实现、签名和正式 applicationId

UI 通过既有 Repository、domain model 和 protocol facade 读取或提交数据。设计稿中暂时没有真实能力的交互由类型完整的 UI mock/state adapter 表达，不伪造已完成的后端能力，不在前端保存 API Key 明文。

## 页面与导航

正式页面和状态：

- 会话首页与挑战已加入首页
- 聊天页
- 新建会话：模板与导入、自定义设定
- 新建页活动发布提示弹窗
- 挑战活动页与挑战详情弹窗
- 会话设置：资料库、图谱、人格目标、个性化
- 学习大脑
- 社区空状态
- 全局设置

导航使用 Compose 内部路由状态，与现有 Android 返回栈衔接。系统返回优先关闭最上层 Dialog/Sheet，其次返回上一页；聊天返回会话首页；会话设置页签保持当前 sessionId 和未保存表单状态。加入挑战后更新本地 UI 状态并返回挑战已加入首页。

## 视觉系统与组件

以交付令牌为基础建立 Compose 语义主题：

- 品牌色 `#575CE6`
- 页面背景 `#EEF0F6`
- 卡片表面 `#FCFCFF`
- Noto Sans SC Regular / Medium / Bold，缺失时使用 Android 中文系统字体回退
- 卡片圆角 18dp，胶囊控件 12–16dp
- 页面水平边距 16dp，顶栏 56dp，最小触控区域 44dp

共享组件包括 AppShell、TopBar、卡片、胶囊按钮、状态标签、聊天气泡、ChatComposer、SegmentTabs、ToggleRow、SourceItem、ChallengeCard、ProgressBar、KnowledgeGraph 和两类业务弹窗。组件使用流式宽度、WindowInsets、安全区和 IME padding；不把 884dp 写成固定页面高度，不直接复制 Figma 的绝对定位代码。

## 状态与错误处理

所有表单和动作提供 idle、loading、success、error 状态。导入资料展示校验和进度；社区未开放按钮保持 disabled；API Key 默认遮罩；图片或资源加载失败提供稳定占位。活动弹窗只在首次进入新建页且未关闭时出现。

设计资源从 Figma 临时地址下载到项目本地并以语义化名称保存，源码不得保留临时 MCP URL。矢量图标优先本地 VectorDrawable 或 Compose ImageVector，照片保留原始位图格式。

## 验证

验证分为四层：

1. Compose 单元测试：导航、挑战加入状态、会话设置页签、弹窗返回优先级、表单状态。
2. Android instrumentation：新建会话、进入聊天、设置切换、活动弹窗、挑战加入和系统返回。
3. 构建验证：相关模块测试、`compileDebugKotlin`、`assembleDebug`。
4. 视觉验证：按 390px 等价视口逐页截图，与对应 Figma 节点比较；检查主要间距、字体、圆角、阴影、滚动、IME 和安全区。

## 完成标准

- 15 个正式页面/状态均可从应用内进入并完成 UX Flow 的核心交互。
- 设计资源全部本地化，源码无临时 Figma URL。
- UI 改动不突破被冻结的数据与协议边界。
- 中文源码编码正确，Gradle 编译与相关测试通过。
- 生成可安装的 debug APK 和逐页验收截图；最终汇报仍待后端接入的能力清单。
