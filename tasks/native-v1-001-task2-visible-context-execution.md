# NEWMP-V1-001 Task 2：窗口可见上下文统一投影执行清单

> 交接对象：Aily
> 所属主线：`newmp` 唯一主线 → V1 普通单会话最小垂直闭环
> 目标：证明 UI 时间线与生成上下文使用同一份 fork-bound 可见历史，且不跨窗口、跨空间泄露消息。

## 基线与边界

必须复用：

- `F:\xw\reverse-tutor-newmp\mobile-native\app\src\main\java\com\reversetutor\preview\wiring\session\WindowVisibleHistoryReader.kt`
- `F:\xw\reverse-tutor-newmp\mobile-native\app\src\main\java\com\reversetutor\preview\wiring\session\TopologyAwareMessageContextPort.kt`
- `F:\xw\reverse-tutor-newmp\mobile-native\app\src\main\java\com\reversetutor\preview\wiring\session\WindowVisibleTimelinePortAdapter.kt`
- `F:\xw\reverse-tutor-newmp\mobile-native\app\src\main\java\com\reversetutor\preview\wiring\HybridAppGraph.kt`

允许修改：上述 app wiring 文件及 `mobile-native/app/src/test` 对应测试。

禁止修改：`mobile-native/core/model`、`core/protocol`、`core/llm`、`core/data`、Room schema/entity/DAO/migration、SecretStore、Provider、Worker、最终视觉组件。若必须改变 feature API 才能实现空间过滤，先提交能力申请并停止。

## Task 1 — Red 测试

在 `F:\xw\reverse-tutor-newmp\mobile-native\app\src\test\java\com\reversetutor\preview\wiring\session\WindowVisibleHistoryReaderTest.kt` 增加测试 `timeline_and_generation_context_share_the_same_visible_ids`：使用现有 child-2 fixture，分别调用 `WindowVisibleTimelinePortAdapter(reader).load("child-2")` 与 `TopologyAwareMessageContextPort(reader).listRecentMessages("space-a", "child-2", 10)`，断言两者 id 顺序完全一致，并断言结果不含 `root-after-child-1` 与 `sibling-local`。

在 `F:\xw\reverse-tutor-newmp\mobile-native\app\src\test\java\com\reversetutor\preview\wiring\session\BackgroundTurnPreparationCoordinatorTest.kt` 增加测试 `prepared_generation_contains_only_the_window_visible_message_ids`：构造 root fork 前消息 `root-before`、fork 后父消息 `root-after-child-1` 和 child 本地消息 `child-2-local`，让 `assembleContext` 返回前两条可见消息，断言入队 `BackgroundGenerationInput.contextEvidence` 中 `kind == "Message"` 的 `sourceMessageId` 只能是 `root-before` 与 `child-2-local`。

若新增测试只缺 import，补 import 即可；不得先改生产代码。

执行命令（工作目录 `F:\xw\reverse-tutor-newmp\mobile-native`）：设置 `ANDROID_HOME=E:\Android\Sdk`、`ANDROID_SDK_ROOT=E:\Android\Sdk`、`GRADLE_USER_HOME=E:\Android\Gradle\newmp`，运行 `.\gradlew.bat :app:testDebugUnitTest --tests "*.WindowVisibleHistoryReaderTest" --tests "*.BackgroundTurnPreparationCoordinatorTest" --console=plain --no-daemon`。

判定：失败必须属于 fork 截断、排序、空间过滤或 evidence 映射；记录首个失败方法和断言。如果新增测试直接通过，记录“现有接线已满足 Task 2”，不要为了制造 Red 改坏代码。

## Task 2 — 最小 Green 修复（仅在 Red 证明缺口时执行）

- fork 截断错误：只修 `WindowVisibleHistoryReader.recordsFor`，保留祖先只读投影与稳定排序。
- reader 实例不一致：只修 `HybridAppGraph.kt`，使 timeline/context 共享同一 reader 实例。
- evidence 丢失或顺序不同：只修 `TopologyAwareMessageContextPort.kt` 或 app mapper，保持最近条数限制。
- 空间边界不一致：先用不同 `spaceId` fixture 重现；确认需要改变 `WindowVisibleTimelinePort.load` 签名时，停止并申请能力，不得自行改 feature API。

禁止删除断言、放宽 fork 时间条件、复制全量父历史、引入 sibling、吞异常或单纯增加超时。

## Task 3 — Green 与回归

生产修复后再次运行同一条定向 Gradle 命令，再运行 `.\gradlew.bat :app:testDebugUnitTest --console=plain --no-daemon`；预期均为 `BUILD SUCCESSFUL`，新增测试和 app 全量测试零失败。

## Task 4 — 验收证据

运行 `git diff --check`、`git diff --name-only -- mobile-native/core/model mobile-native/core/protocol mobile-native/core/llm mobile-native/core/data`、`git status --short --branch`，把原始结果写入交付报告。不得自行 commit/push。

设备测试不是 Task 2 前置；若运行主力真机，记录 serial/API/测试类/真实结果，未连接写 `not_run`。结束后执行 `adb -s 9CN0223C27017326 uninstall com.reversetutor.preview.test`、重新 enable 宿主并启动 `com.reversetutor.preview/.MainActivity`。

交付必须包含：任务编号、Red 结果、修改文件绝对路径（或 0 个生产文件）、Green 与 app 回归结果、冻结层检查、设备状态、未解决阻塞及下一项判别实验。

## 本轮主控验收记录（2026-08-30）

- 定向命令：`F:\xw\reverse-tutor-newmp\mobile-native\gradlew.bat :app:testDebugUnitTest --tests "*.WindowVisibleHistoryReaderTest" --tests "*.BackgroundTurnPreparationCoordinatorTest" --console=plain --no-daemon`
- 结果：`BUILD SUCCESSFUL`；窗口 fork 边界、同一可见 ID、生成 evidence 边界测试均通过。
- 额外修复：`SessionPolicyInputMapper.kt` 中三个错误的转义 `$` 已改为真实 Kotlin 插值；新增 `SessionPolicyInputMapperTest.context_evidence_ids_are_derived_from_their_source_values`，验证消息、缺口和复习 evidence ID 不再是字面常量。
- 加固修复：循环父链不再重复拼接本地消息；模板目标字段也经过敏感模式清理及 320 字符上限。新增循环拓扑和目标字段安全回归测试。
- 回归命令：`F:\xw\reverse-tutor-newmp\mobile-native\gradlew.bat :app:testDebugUnitTest --console=plain --no-daemon`
- 回归结果：`BUILD SUCCESSFUL`。
- 冻结路径 diff：空；`git diff --check`：通过。
- 设备：本 Task 未运行，状态 `not_run`；没有用 JVM 结果替代设备证据。
- 提交状态：未 commit、未 push，等待主控将本轮与既有在途改动一起审计。
- 观察项：正常契约要求消息 ID 全局唯一；若未来发现父子窗口持有相同消息 ID 的脏数据，需要另立任务定义“本地优先”的去重规则，当前不擅自改变消息身份语义。
