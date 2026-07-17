# Figma Formal Screen Registry

Source of truth: Figma file `VPV0vclAhALpF8QIGvxYlo`, overview page `714:10`.

This registry includes only the 56 formal phone boards. Nested scrims, dimmed parent copies,
typography samples, and global layout patterns are excluded. `Verified on emulator-5554` means a
formal Compose route or explicit UI state exists and its node-specific `390 x 884dp` fixture passed
the Android 16 emulator screenshot gate on 2026-07-17.

| # | Figma node | Formal board | Compose route or state | Status |
|---:|---|---|---|---|
| 1 | `716:13` | 会话首页 / 默认 | `FormalHomeScreen(Default)` | Verified on emulator-5554 |
| 2 | `716:72` | 会话首页 / 新建抽屉 | `FormalHomeScreen(NewSessionSheet)` | Verified on emulator-5554 |
| 3 | `716:159` | 会话首页 / 已加入挑战 | `FormalHomeScreen(JoinedChallenge)` | Verified on emulator-5554 |
| 4 | `716:237` | 挑战活动页 | `ChallengeRoute(List)` | Verified on emulator-5554 |
| 5 | `716:379` | 挑战详情 | `ChallengeRoute(Detail)` | Verified on emulator-5554 |
| 6 | `716:470` | 活动发布提示 | `ActivityAnnouncementDialog` | Verified on emulator-5554 |
| 7 | `716:611` | 学习预设库 | `FigmaNewSessionScreen(PresetLibrary)` | Verified on emulator-5554 |
| 8 | `716:684` | 高三数学预设 | `FigmaNewSessionScreen(PresetDetail.Math)` | Verified on emulator-5554 |
| 9 | `716:494` | 自定义世界树字段目录 | `FigmaNewSessionScreen(CustomWorldTree)` | Verified on emulator-5554 |
| 10 | `716:779` | Python 预设详情 | `FigmaNewSessionScreen(PresetDetail.Python)` | Verified on emulator-5554 |
| 11 | `716:871` | 雅思预设详情 | `FigmaNewSessionScreen(PresetDetail.Ielts)` | Verified on emulator-5554 |
| 12 | `716:963` | 演讲预设详情 | `FigmaNewSessionScreen(PresetDetail.Speech)` | Verified on emulator-5554 |
| 13 | `716:1055` | 行测预设详情 | `FigmaNewSessionScreen(PresetDetail.Aptitude)` | Verified on emulator-5554 |
| 14 | `716:1147` | 前端预设详情 | `FigmaNewSessionScreen(PresetDetail.Frontend)` | Verified on emulator-5554 |
| 15 | `716:1239` | 化学预设详情 | `FigmaNewSessionScreen(PresetDetail.Chemistry)` | Verified on emulator-5554 |
| 16 | `716:1331` | 机器学习预设详情 | `FigmaNewSessionScreen(PresetDetail.MachineLearning)` | Verified on emulator-5554 |
| 17 | `716:1424` | 单会话聊天 | `ChatScreen` / `ReverseTeachingChatScreen` | Verified on emulator-5554 |
| 18 | `717:378` | 全局图谱 / 缩略态 | `FormalKnowledgeGraphScreen(GlobalOverview)` | Verified on emulator-5554 |
| 19 | `717:555` | 全局图谱 / 缩略节点详情 | `FormalKnowledgeGraphScreen(GlobalOverviewDetail)` | Verified on emulator-5554 |
| 20 | `717:167` | 全局图谱 / 近景态 | `FormalKnowledgeGraphScreen(GlobalClose)` | Verified on emulator-5554 |
| 21 | `717:268` | 全局图谱 / 近景节点详情 | `FormalKnowledgeGraphScreen(GlobalCloseDetail)` | Verified on emulator-5554 |
| 22 | `717:782` | 单会话世界树 / 卡片态 | `FormalSessionWorldTreeScreen(Default)` | Verified on emulator-5554 |
| 23 | `717:935` | 单会话世界树 / 未解锁详情 | `FormalSessionWorldTreeScreen(LockedDetail)` | Verified on emulator-5554 |
| 24 | `800:52` | 单会话世界树 / 隐藏未解锁 | `FormalSessionWorldTreeScreen(HideLocked)` | Verified on emulator-5554 |
| 25 | `717:1130` | 本周学习 / 首屏 | `FormalWeeklyScreen(Top)` | Verified on emulator-5554 |
| 26 | `717:1197` | 本周学习 / 中段 | `FormalWeeklyScreen(Middle)` | Verified on emulator-5554 |
| 27 | `717:1278` | 本周学习 / 下段 | `FormalWeeklyScreen(Bottom)` | Verified on emulator-5554 |
| 28 | `717:1366` | 本周范围 / 手动多选 | `FormalWeeklyScreen(ManualSelection)` | Verified on emulator-5554 |
| 29 | `809:52` | 本周范围 / 已选四个 | `FormalWeeklyScreen(FourSelected)` | Verified on emulator-5554 |
| 30 | `717:1426` | 全局搜索 / 最近 | `FormalSearchScreen(Recent)` | Verified on emulator-5554 |
| 31 | `717:1503` | 全局搜索 / GDP 结果 | `FormalSearchScreen(Results)` | Verified on emulator-5554 |
| 32 | `717:1587` | 公益文章 / 首屏 | `FormalPublicArticleScreen(Top)` | Verified on emulator-5554 |
| 33 | `717:1651` | 公益文章 / 阅读中段 | `FormalPublicArticleScreen(Middle)` | Verified on emulator-5554 |
| 34 | `717:1730` | 单会话设置 / 资料库 | `SessionLibrarySettingsScreen` | Verified on emulator-5554 |
| 35 | `717:1895` | 角色与目标 / 默认 | `FormalRoleGoalScreen(Default)` | Verified on emulator-5554 |
| 36 | `717:1961` | 角色与目标 / 高影响确认 | `FormalRoleGoalScreen(HighImpactConfirm)` | Verified on emulator-5554 |
| 37 | `717:2056` | 角色与目标 / 自动演化提示 | `FormalRoleGoalScreen(AutoEvolutionNotice)` | Verified on emulator-5554 |
| 38 | `717:2140` | 个性化 / 默认 | `FormalPersonalizationScreen` | Verified on emulator-5554 |
| 39 | `718:11` | 全局设置 | `FormalSettingsScreen` | Verified on emulator-5554 |
| 40 | `718:114` | LLM 配置 / DeepSeek | `FormalLlmConfigurationScreen(DeepSeek)` | Verified on emulator-5554 |
| 41 | `718:199` | LLM 配置 / Kimi | `FormalLlmConfigurationScreen(Kimi)` | Verified on emulator-5554 |
| 42 | `718:273` | LLM 配置 / 服务预设列表 | `FormalLlmConfigurationScreen(PresetList)` | Verified on emulator-5554 |
| 43 | `718:438` | 社区 / 静态占位 | `CommunityRoute` | Verified on emulator-5554 |
| 44 | `718:473` | 导入与导出 / 默认 | `FormalImportExportScreen(Default)` | Verified on emulator-5554 |
| 45 | `718:548` | 导入备份 / 预览与冲突 | `FormalImportExportScreen(ImportPreview)` | Verified on emulator-5554 |
| 46 | `718:661` | 选择会话 / 单选多选 | `FormalImportExportScreen(SessionSelection)` | Verified on emulator-5554 |
| 47 | `718:756` | 关于与诊断 / 运行概览 | `FormalDiagnosticsScreen(Overview)` | Verified on emulator-5554 |
| 48 | `718:869` | 诊断报告 / 脱敏预览 | `FormalDiagnosticsScreen(Report)` | Verified on emulator-5554 |
| 49 | `718:993` | 清空本地数据 / 确认 | `FormalDiagnosticsScreen(WipeConfirmation)` | Verified on emulator-5554 |
| 50 | `718:1123` | Token 统计 / 总览 | `FormalTokenScreen(Overview)` | Verified on emulator-5554 |
| 51 | `718:1208` | Token 统计 / 按模型 | `FormalTokenScreen(ByModel)` | Verified on emulator-5554 |
| 52 | `718:1291` | Token 统计 / 按会话 | `FormalTokenScreen(BySession)` | Verified on emulator-5554 |
| 53 | `718:1392` | 应用更新 / 当前版本 | `FormalUpdateScreen(Current)` | Verified on emulator-5554 |
| 54 | `718:1460` | 应用更新 / 发现新版 | `FormalUpdateScreen(UpdateAvailable)` | Verified on emulator-5554 |
| 55 | `718:1556` | 同步冲突 / 总览 | `FormalSyncConflictScreen(Overview)` | Verified on emulator-5554 |
| 56 | `718:1636` | 同步冲突 / 选择保留内容 | `FormalSyncConflictScreen(ChooseContent)` | Verified on emulator-5554 |

## Spatial Navigation

- Horizontal order: `WeeklyDashboard <- Sessions -> GlobalGraph -> Community`.
- Initial page: `Sessions`.
- Vertical page above `Sessions`: `Challenge`.
- Drawer edge gestures remain disabled while the drawer is closed so they cannot intercept the
  horizontal workspace pager.

## Verification Rule

Each row is complete only after its formal state compiles, its intended navigation/state transition
is covered, and a `390 x 884dp` emulator fixture has been compared with the corresponding Figma node.

Evidence is stored in `mobile-native/qa/figma-tuning/formal-batch1..6/emulator`. The six independent
instrumentation runs passed `10 + 13 + 1 + 7 + 8 + 17 = 56` tests. All 56 PNG files are
`1170 x 2652px` at 480dpi, contain sampled visual content, and have no duplicate SHA-256 hashes.
Huawei Mate 60 physical-device validation remains a separate pending gate.
