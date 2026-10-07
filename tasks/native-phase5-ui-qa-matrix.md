# NATIVE-UX-010 · Phase 5 原生 UI QA 矩阵

验收日期：2026-08-16
验收对象：Context Hub、知识图谱、资料库、证据引用与返回闭环
验收设备：`Pixel_8_Pro` AVD（`emulator-5554`，Android 16，1344×2992）
范围边界：本次只验收 native Android；不改变 PWA、Capacitor、冻结层、图谱算法或 Repository 行为。

## 结论

自动化验收通过，未发现需要修复的 Phase 5 UI 问题。NATIVE-UX-010 的自动化与模拟器可验证项均为 Pass；TalkBack 的实际语音阅读顺序保留为人工听测项，不能由 Compose 语义或测试代码替代。

这不是 PWA 退出、原生替换就绪或 NATIVE-UX-006 最终门槛的批准。

## 设备测试

| 范围 | 结果 | 证据 |
|---|---|---|
| Context Hub 信息架构、证据跳转、无证据无死链接、返回聊天 | Pass | `Phase5ContextHubDeviceTest`，4 项通过 |
| 图谱 Canvas、空/错/大图状态、节点列表语义入口、可用证据动作 | Pass | `Phase5GraphDeviceTest`，5 项通过 |
| 图谱破坏性审核动作二次确认 | Pass | `Phase5GraphDeviceTest`：Archive/Hide 需确认；Approve 立即执行 |
| Memory 与 Context Hub 持久化联动 | Pass | `Phase5MemoryDeviceTest`，1 项通过 |
| 资料库五态、恢复动作边界、图片资料与聊天附件边界 | Pass | `Phase5SourcesDeviceTest`，1 项通过 |
| 合计 | Pass | 同一模拟器单次 instrumentation：`OK (11 tests)`，44.091 秒 |

执行方式：构建 `:app:assembleDebug :app:assembleDebugAndroidTest` 后，仅向 `emulator-5554` 安装 APK，并运行四个 `Phase5*DeviceTest` 类。验收期间未对连接的真机发送命令。

## 可视与适配检查

| 检查项 | 结果 | 证据 |
|---|---|---|
| 深色模式 + 1.3 倍字体：Context Hub | Pass | 已实际打开；语义树包含完整“学习脉络”与“图谱”内容，系统导航栏存在且未遮挡控件。见 `artifacts/ux010/context-phase5-dark-large.png` 与同名 XML。 |
| 深色模式 + 1.3 倍字体：图谱空状态 | Pass | 已实际打开；“当前会话信息过少，再多聊会天吧”和“去聊天中补充学习证据”均完整显示。见 `artifacts/ux010/graph-phase5-dark-large.png` 与同名 XML。 |
| 48dp 主要触达目标 | Pass | Context Hub、Graph、Sources 的主要操作和确认按钮均使用 `heightIn(min = 48.dp)`；对应设备测试已点击这些节点。 |
| Canvas 的无障碍替代入口 | Pass | 图谱存在 `graph-node-list`，大图与无效图状态设备测试均验证列表和详情可达。 |
| 图标说明与语义节点 | Pass（代码检查） | Canvas、导航和图谱操作提供 content description/testTag；本次自动化可定位所有关键操作。 |
| TalkBack 实际朗读顺序 | Blocked（待人工） | AVD 已安装 TalkBack，但本轮没有可记录语音输出的人工听测。需人工开启 TalkBack，按视觉顺序滑动焦点核对 Context Hub、图谱节点列表和资料卡。 |

## 关键契约与交互检查

| 契约 | 结果 | 证据 |
|---|---|---|
| Evidence 仅在 target 存在时展示对应聊天/资料跳转 | Pass | `ContextHubModelsTest` 与 `Phase5ContextHubDeviceTest`；无证据项显示“当前没有可跳转的证据。” |
| 图谱为原生 Compose Canvas，非 WebView 替代 | Pass | Phase 5 feature 路径静态检查无 `WebView` / `AndroidView`；设备测试定位 `knowledge-graph-canvas`。 |
| 图谱非 Canvas 访问路径 | Pass | `graph-node-list` 在无效/大图状态均经设备测试验证。 |
| Archive / Hide 需要确认，Approve 不需要 | Pass | `requiresConfirmation` 单测及模拟器设备测试。 |
| Sources 状态与恢复权限 | Pass | 设备测试覆盖 FullyLocal、PartiallyLocal、Failed、FutureAssisted、Unsupported；仅真实可恢复项目显示操作。 |
| 图片资料与聊天附件能力分离 | Pass | 状态文案与设备测试均验证“聊天附件能力”单独说明，图片资料不提供虚假的恢复动作。 |

## 回归与边界检查

| 检查 | 结果 |
|---|---|
| `:app:assembleDebug`、全量 Kotlin 单测、`:feature:memory:lint`、`:feature:sources:lint`、`:app:lint` | Pass，`BUILD SUCCESSFUL`（714 tasks） |
| Python 回归 `py -m pytest -q --ignore=tests/test_project_homepage.py` | Pass：510 passed、28 skipped（364.51 秒） |
| `git diff --check` | Pass（无输出） |
| 冻结层差异：`core/model`、`core/protocol`、`core/llm`、`core/data` | Pass（无差异） |
| 覆盖登记与 PWA 退出声明 | 未修改；本次验收不授权更新为 replacement ready。 |

## 后续人工项

1. 在同一 AVD 中开启 TalkBack，核对 Context Hub、图谱节点列表、资料卡的实际焦点朗读顺序。
2. 若最终替换级验收需要更广设备覆盖，按 `native-android-ui-acceptance-checklist.md` 补小屏和真机矩阵；不能以本次单一 AVD 结果替代。
