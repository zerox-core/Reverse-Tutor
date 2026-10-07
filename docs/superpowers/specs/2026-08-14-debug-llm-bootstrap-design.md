# Debug LLM 本地引导设计

## 目标

让 Native Debug APK 在首次启动时读取仅本机存在的测试配置，创建一个默认 LLM Profile 与三个可切换备用 Profile；密钥、网关 URL 与模型清单不进入 Git，也不进入 Release APK。

## 本机配置

配置文件为 `mobile-native/local.properties`，已被该模块的 `.gitignore` 忽略。它包含以下四个键：

- `reverseTutorDebugLlmApiKey`：测试密钥。
- `reverseTutorDebugLlmBaseUrl`：兼容 OpenAI 的网关 URL。
- `reverseTutorDebugLlmDefaultModel`：`deepseek-v4-flash-0731`。
- `reverseTutorDebugLlmFallbackModels`：`qwen3.6-flash,deepseek-v4-flash,qwen3.7-flash`。

Gradle 只在 `debug` build type 将这些值注入 `BuildConfig`；`release` build type 对应字段恒为空。构建和日志不得输出密钥。

## 启动行为

`MainActivity` 在图初始化后启动一个 app 生命周期协程。若 Debug 配置完整，调用现有 `LlmProfileRepository.saveProfile` 创建缺失的四个 Profile：默认 Profile 启用，三个备用 Profile 保留但不启用。每个 Profile 使用既有 `SecretStore` 写入密钥，因此现有 `DataModule`、`CompositeLlmGenerationRuntime` 和 Provider transport 均不需要修改。

Profile 使用固定的 debug-local ID。引导器只创建缺失项；已存在的 Profile 不覆盖，用户在设置页所作的修改保留。配置缺少密钥、URL 或默认模型时，引导器无操作，不显示或记录密钥。

## 范围

- 允许修改 `mobile-native/app` 的 Debug Gradle 配置、启动引导代码及其测试。
- 禁止修改 `core:llm`、`core:data`、`SecretStore`、Repository 签名、Room schema、PWA、Python 和 Release 签名配置。
- 不实现模型发现 API；备用列表只使用用户指定的本地模型 ID。

## 验收

1. `local.properties` 缺失或配置不完整时，Debug APK 保持现有行为。
2. 配置完整时，首次启动创建默认与备用 Profile，默认项启用且密钥仅写入 Android SecretStore。
3. 再次启动不覆盖已有 Profile。
4. Debug 构建可在 `Pixel_8_Pro` 上安装运行；Release BuildConfig 不含测试值。
5. 完整 Native JVM 测试通过。
