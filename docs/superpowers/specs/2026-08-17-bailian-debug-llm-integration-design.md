# 百炼 Debug LLM 真实接入设计

## 目标

让原生 Android 的 Debug APK 通过本机私密配置调用百炼 OpenAI 兼容接口；默认使用 `qwen3.7-flash`，用户可在现有设置页面手动切换至 `qwen3.6-flash` 或 `deepseek-v4-flash`。

## 范围与边界

- 仅修改 `mobile-native/app` 的装配选择与本机 Debug 配置的模型顺序。
- 不修改 `core:model`、`core:protocol`、`core:llm`、`core:data`、Room、SecretStore 或导入导出协议。
- API Key 仅保存在已被 Git 忽略的 `mobile-native/local.properties`，不写入源码、文档、测试、日志或提交。
- Release 构建继续注入空的 Debug 配置；不得打入测试 Key，不自动启用真实 Provider。

## 当前事实与根因

`app/build.gradle.kts` 已把本机 `local.properties` 中的 Debug LLM 配置编译给 Debug 变体；`DebugLlmProfileBootstrapper` 会将默认模型与备用模型保存为可选择的 OpenAI-compatible profile。

但 `HybridAppGraph.create()` 无条件创建 `FakeLlmGenerationRuntime` 并传给聊天与后台生成 Repository。因此即使 profile 已配置，预览仍只返回 Mock，不会发起真实网络请求。

## 设计

### 运行时选择

新增仅位于 app 装配层的 `HybridLlmRuntimeConfiguration`：

- Debug 配置完整（Key、Base URL、默认模型均非空）时，调用 `DataModule` 的默认 Runtime，使用既有生产 `CompositeLlmGenerationRuntime` 与 Keystore SecretStore。
- Debug 配置不完整时，继续显式使用 `FakeLlmGenerationRuntime`，保证未配置的开发环境与现有测试不依赖网络。
- Release 传入的不完整 Debug 配置始终进入 Fake 路径。

配置判断只决定 app 的装配行为，不能改变 LLM 协议、Repository 签名或 secret 保存策略。

### 模型与切换

Debug bootstrap 创建三份 profile：

1. 默认：`qwen3.7-flash`
2. 备用：`qwen3.6-flash`
3. 备用：`deepseek-v4-flash`

三份 profile 使用同一个百炼 OpenAI-compatible Base URL 与同一份存入 Keystore 的 Key。用户通过已有设置页激活其中一个 profile；本轮不做网络失败后的自动重试或自动模型降级。

### 真实请求验收

在 Debug APK、模拟器上，通过已保存的默认 profile 发出一条短文本请求。验收为：生成状态完成、界面显示非 Mock 的提供方回复、没有泄露 Base URL/Key/Authorization 到可见错误文案或日志。

自动化测试保持离线：新增或调整 JVM 测试仅验证运行时选择、bootstrap 模型顺序和 Release 安全默认值；不得在 Gradle/JVM 测试调用网络。

## 故障处理

- Key/Base URL/模型缺失：继续使用 Fake Runtime；用户可在设置页补全 profile。
- 网络、鉴权或提供方错误：复用既有安全失败语义，仅显示安全摘要；不自动切换模型，不重试以避免意外消费。
- 真实请求验收失败：记录不含敏感信息的错误类别、HTTP 状态或安全消息；不把完整响应或请求头写入仓库。

## 验收标准

- Debug APK 在完整本机配置下不再使用 Fake Runtime。
- 默认 profile 是 `qwen3.7-flash`，两份备用 profile 可在设置中激活。
- 空配置与 Release 都不调用真实 Provider。
- 受影响 JVM 测试、`assembleDebug`、`git diff --check` 通过；冻结路径 diff 为空。
- 模拟器完成一条真实短请求，或记录可复现且无敏感信息的阻塞证据。
