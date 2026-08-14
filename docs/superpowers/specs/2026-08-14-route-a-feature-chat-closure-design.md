# Route A Feature Chat 闭环设计

## 目标

让 `feature:chat` 以可测试的方式完成 Fake Runtime 教学闭环：用户消息发送成功后，界面进入生成中；生成成功时刷新消息；失败或超时时展示可恢复的错误；会话切换后旧请求不能改变当前会话的状态。

## 范围与边界

- 仅修改 `mobile-native/feature/chat` 的编排与测试，以及 `ChatScreen.kt` 的调用接线。
- 继续消费既有 `ChatGenerationRepository`、`MessageRepository` 与 `ChatGenerationOutcome`；不修改它们的签名或实现。
- 不改 `core:model`、`core:protocol`、`core:llm`、`core:data/*Repository`、Room、SecretStore、Gradle、PWA 或 Python。
- 本切片不接入真实 Provider，不新增后台任务协议，也不改变 Compose 视觉设计。

## 设计

新增一个 `feature:chat` 内部的 `ChatGenerationCoordinator`。它接收由 `ChatRoute` 注入的生成执行器和当前 token 的有效性判断，负责一次已成功发送消息之后的生成编排；`ChatRoute` 的执行器继续调用既有可选 `ChatGenerationRepository`。

它复用既有 UI 状态：`Idle`、`Pending`、`NoModel`、`Failure(message)`。每次启动生成时创建 token；Runtime 返回后只在 token 仍是当前 token 时发布终态。成功后回到 `Idle`，由重新加载的助手消息体现成功；失效结果不发布新状态。失败和超时继续使用既有 `ChatGenerationOutcome` 映射，不复制 Repository 的业务规则。

`ChatRoute` 保留草稿、附件、消息发送和 Compose 展示职责。它在 `ChatSendCoordinator` 返回 `Sent` 后调用 Coordinator，观察其状态刷新现有 `generation` 状态，并在终态触发 `reload()`。会话变化会重建 Coordinator；旧请求只会得到 `Stale`，不写入新会话状态。

## 测试

在 `feature/chat` 编写 Coordinator 单元测试，使用可控的生成执行器 fake；Repository 层的消息持久化仍由既有 Route A contract test 覆盖：

1. 成功：`Idle → Pending → Idle`，并刷新后可读取助手消息。
2. 失败与超时：`Idle → Pending → Failed(message)`，不写助手消息，后续新请求仍可开始。
3. 会话切换：session-1 Runtime 挂起，切换到 session-2 并成功生成后释放旧 Runtime；旧结果不覆盖 session-2 状态或写入 session-1。
4. 未配置模型：不调用 Runtime，状态为 `NoModel`，并保留原有用户消息。

验证命令为定向 `:feature:chat:testDebugUnitTest`，随后本地 `:core:data:testDebugUnitTest --tests "*.ChatGenerationClosureTest"` 与完整 `gradlew test`。

## 验收

- Feature 层测试覆盖上述四条状态与隔离语义。
- Route A 仍只使用 Fake Runtime，不发生真实网络请求。
- 冻结生产层没有变更。
- 完整 Native JVM 测试通过。
