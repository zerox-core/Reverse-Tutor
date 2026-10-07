# LLM 服务预设与接口格式

更新时间：2026-07-15

本文件用于全局设置中的“LLM API”厂家预设列表。预设必须以“服务商 + 地域 + 接口格式”为唯一单位，不能只按服务商名称判断请求结构。

## 接口格式枚举

- `openai_chat_completions`：OpenAI Chat Completions 兼容格式。
- `anthropic_messages`：Anthropic Messages 格式。
- `gemini_generate_content`：Gemini 原生 GenerateContent 格式。
- `custom`：用户手动填写地址、鉴权头、模型和接口格式。

同一个厂家支持多种格式时，必须显示为不同预设，例如“MiniMax / OpenAI”和“MiniMax / Anthropic”。密钥可归属同一厂家，但 Base URL、请求路径和请求体转换规则必须跟随具体预设。

## 已核对预设

| 显示名称 | 格式 | Base URL | 备注 |
| --- | --- | --- | --- |
| DeepSeek / OpenAI | `openai_chat_completions` | `https://api.deepseek.com` | 官方文档确认 |
| DeepSeek / Anthropic | `anthropic_messages` | `https://api.deepseek.com/anthropic` | 官方文档确认 |
| Kimi / Moonshot CN | `openai_chat_completions` | `https://api.moonshot.cn/v1` | 官方文档确认 |
| 百炼 / Qwen | `openai_chat_completions` | `https://{WorkspaceId}.cn-beijing.maas.aliyuncs.com/compatible-mode/v1` | 北京业务空间域名；旧 DashScope 域名仍可用 |
| 智谱 GLM / OpenAI | `openai_chat_completions` | `https://open.bigmodel.cn/api/paas/v4` | 官方文档确认 |
| MiniMax CN / OpenAI | `openai_chat_completions` | `https://api.minimaxi.com/v1` | 官方文档确认支持 OpenAI SDK；地址按国内开放平台预设 |
| MiniMax CN / Anthropic | `anthropic_messages` | `https://api.minimaxi.com/anthropic` | 官方文档确认支持 Anthropic SDK |
| MiMo | `openai_chat_completions` | `https://api.xiaomimimo.com/v1` | 小米官方仓库配置示例；默认模型 `xiaomi/mimo-v2.5` |
| Claude | `anthropic_messages` | `https://api.anthropic.com` | 请求路径 `/v1/messages` |
| 豆包 / 火山方舟 | `openai_chat_completions` | `https://ark.cn-beijing.volces.com/api/v3` | 官方文档确认兼容 OpenAI SDK |
| OpenAI | `openai_chat_completions` | `https://api.openai.com/v1` | 官方服务 |
| 手动填写 | `custom` | 用户输入 | 必须显式选择格式 |

## 后续补充候选

以下厂家应进入“全部厂家”列表，但 Base URL 与地域差异需要在正式写入内置预设前再次核对官方文档：

- 硅基流动 SiliconCloud
- 百度智能云千帆
- 腾讯混元
- Google Gemini
- Kimi / Moonshot Global
- MiniMax Global

## 移动端交互

- 顶部厂家 Dock 只展示最近使用或已保存的厂家，不承担完整目录功能。
- “全部”打开可搜索的服务预设列表，支持按 OpenAI、Anthropic、国内和自定义筛选。
- 预设行显示厂家、地域、格式和域名；模型在连接成功后单独选择，不固化在接口预设中。
- 点击预设后进入新增配置页，自动填充格式和 Base URL，用户只需填写名称、API Key，并在连接成功后选择模型。
- 每个厂家可保存多个账户；切换当前配置不能覆盖其他厂家或账户的密钥。
- API Key 使用 Android Keystore 加密，仅显示尾号，不参与同步和日志。

## 资料来源

- DeepSeek API Docs: https://api-docs.deepseek.com/
- Kimi API 开放平台: https://platform.moonshot.cn/docs/api/chat
- 阿里云百炼 OpenAI 兼容: https://help.aliyun.com/zh/model-studio/compatibility-of-openai-with-dashscope
- 智谱 OpenAI API 兼容: https://docs.bigmodel.cn/cn/guide/develop/openai/introduction
- MiniMax OpenAI SDK: https://platform.minimaxi.com/docs/api-reference/text-openai-api
- MiniMax Anthropic SDK: https://platform.minimaxi.com/docs/api-reference/text-anthropic-api
- Anthropic Messages API: https://docs.anthropic.com/en/api/messages
- 火山方舟 OpenAI SDK 兼容: https://www.volcengine.com/docs/82379/1330626
- 小米 MiMo 官方仓库配置示例: https://github.com/XiaoMi/xiaomi-miloco
- LLM 品牌 SVG: https://github.com/lobehub/lobe-icons
