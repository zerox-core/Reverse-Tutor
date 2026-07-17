# Native Stitch UI Initial Draft

日期：2026-07-04

## 目标

用全局 Stitch MCP 和 UI/UX skill 为 Reverse Tutor native Android 重做前端 UI/UX 的第一版视觉初稿。此稿只作为设计输入，不改 Compose，不改后端协议接口层，不改数据库/数据层。

## 已配置

- Stitch MCP user-level config: `C:/Users/Lenovo/.codex/config.toml`
- Stitch project: `projects/3132646461749921577`
- Stitch design system: `assets/11909194833056449697`

## 设计系统

方向：中文移动端学习工具，不是营销页。

- 风格：工具型、清晰、信息密度适中、适合反复使用。
- 主色：muted teal `#2F6F5E`
- 证据/提醒色：amber `#D79A2B`
- 结构色：slate / neutral gray
- 背景：light mode first，暖灰和白色表面。
- 圆角：8dp。
- 触控：48dp minimum target。
- 导航：底部 5 项以内，`会话 / 聊天 / 上下文 / 资料 / 设置`。
- 文案：可见文案优先中文。
- 禁止：欢迎页、营销式 hero、装饰渐变、纯英文按钮、卡片套卡片、为了好看牺牲信息密度。

## 已生成屏幕

本地截图目录：

```text
mobile-native/build/stitch-screens/
```

| Screen | Stitch resource | Local screenshot |
|---|---|---|
| App Shell + 会话列表 | `projects/3132646461749921577/screens/ef11463bf6b04c39a1095f20e7e66147` | `mobile-native/build/stitch-screens/01-sessions.png` |
| 聊天学习页 | `projects/3132646461749921577/screens/80a8ce85c81b4e0ca7d8bdbbad12d594` | `mobile-native/build/stitch-screens/02-chat.png` |
| 上下文中心 / 知识图谱 | `projects/3132646461749921577/screens/654ded9b658d4fbf84f5859bd17dad34` | `mobile-native/build/stitch-screens/03-context-graph.png` |
| 资料库 | `projects/3132646461749921577/screens/4230c752536148d9b7b3d32a0faec796` | `mobile-native/build/stitch-screens/04-sources.png` |
| 设置 | `projects/3132646461749921577/screens/94d2a07664ec4e2abe438d28d8e96e5b` | `mobile-native/build/stitch-screens/05-settings.png` |

Machine-readable screen manifest:

```text
mobile-native/build/stitch-screens/screens.json
```

## 初步评审

### 可保留方向

- 第一屏直接进入会话列表，符合“工具第一屏”要求。
- 底部导航收敛为五个核心入口。
- 会话页和资料库页的信息密度接近目标，不是营销页。
- 聊天页已经覆盖消息流、上下文注入、引用、图片、资料、发送和后台生成状态。
- 设置页把 LLM 配置、导入导出、本地数据擦除、偏好设置分区清楚，并强调 API Key 不导出。

### 需要下一轮优化

- 上下文/图谱页需要进一步控制图谱节点密度，避免移动端节点过散或文字过小。
- 底部导航图标风格需要在 Compose 实现前统一到同一套 icon。
- 会话页的 FAB 与底部导航距离需要在真实 Android safe area 下复核。
- 资料库页的展开片段面板可保留，但实现时避免嵌套卡片。
- 设置页的危险操作需要真实二次确认，不只靠红色文案。

## 后续接入约束

实现阶段只允许改 UI 层：

```text
mobile-native/app/src/main/java/com/reversetutor/preview/theme
mobile-native/app/src/main/java/com/reversetutor/preview/ui
mobile-native/app/src/main/java/com/reversetutor/preview/shell
mobile-native/feature/*
```

不得在 UI 实现中修改：

```text
mobile-native/core/model
mobile-native/core/protocol
mobile-native/core/llm
mobile-native/core/data/*Repository
mobile-native/core/data/local
Room schema / Entity / DAO / migrations
SecretStore
NativeImportRepository / NativeExportRepository
BackgroundGenerationRepository
```

完整冻结规则见：

```text
tasks/native-backend-protocol-data-contract-freeze.md
```
