# 1e RAG 调优与内置向量兜底 — 设计文档

> 阶段：1e（大阶段一「方向一+二主体」子阶段）
> 日期：2026-09-23
> 状态：e1 核查完成（本文）；e2 实现中；e3 待做

## 一、e1 服务器转发端点核查结论

**结论：服务器侧不具备 OpenAI 兼容 /v1/embeddings 转发能力。**

核查证据（2026-09-23 实查）：

1. **nginx 无独立 embeddings 规则**：`/etc/nginx/sites-enabled/hub-zeroxcore` 中 `location /v1/` 整块无鉴权转发到 `127.0.0.1:8787`（hub 自身校验 sk- key），`location /` 走 basic auth。无 `/v1/embeddings` 专门 location。
2. **hub 无 embeddings 路由**：本地 `F:\llm_hub\server.py` 与云端 `~/llm-hub/hub/server.py` 路由清单一致——`/v1/models`、`/v1/chat/completions`、`/v1/messages`、`/v1/messages/count_tokens`、`/v1/harness/heartbeat` 等，**无 `/v1/embeddings`**。
3. **无硅基流动 API key**：服务器与本地均无硅基流动 key 配置。docker-compose 中 `r7embed` 备份项是 dsh 面板嵌入服务，与本任务无关。

## 二、最小转发方案（设计，未部署）

在 hub `server.py` 新增 `POST /v1/embeddings` 路由：

- 转发目标：`https://api.siliconflow.cn/v1/embeddings`
- model 限定白名单：仅放行 `BAAI/bge-m3`（防滥用）
- 鉴权：硅基流动 key 走服务器环境变量（`SILICONFLOW_API_KEY`），**key 不进 APK、不进仓库**
- 端上调用：`https://hub.zeroxcore.tech/v1/embeddings`，走现有 `/v1/` nginx 转发规则，无需改 nginx

## 三、端上降级链设计（e2 实现依据）

调用顺序：

1. **用户自配渠道**（现有逻辑保留）：`EmbeddingModelDiscovery.discover()` 自动识别 → `OpenAiCompatibleEmbeddingRuntime.embed()` → 成功则返回 `EmbeddingVectorSet(modelKey=<解析模型名>, channelKind=UserConfigured)`
2. **内置兜底渠道**：固定 `BaseUrl=https://hub.zeroxcore.tech/v1`、`Model=BAAI/bge-m3`、`secretRef=null` + `allowAnonymous=true`（不带 Authorization 头）、跳过 discovery → 成功返回 `EmbeddingVectorSet(modelKey="BAAI/bge-m3", channelKind=BuiltIn)`
3. **关键词回退**：两路均失败返回 `null`，上层 `SourceContextPortAdapter.vectorRankedEntries` 返回 null → `keywordRankedEntries` 词项计数兜底（现有逻辑不变）

### 新增类型（core/llm）

```kotlin
object BuiltInEmbeddingChannel {
    const val BaseUrl = "https://hub.zeroxcore.tech/v1"
    const val Model = "BAAI/bge-m3"
}
enum class EmbeddingChannelKind { UserConfigured, BuiltIn }
data class EmbeddingVectorSet(
    val modelKey: String,
    val channelKind: EmbeddingChannelKind,
    val vectors: List<FloatArray>,
)
```

### OpenAiCompatibleEmbeddingRuntime 改动

`embed(...)` 新增参数 `allowAnonymous: Boolean = false`：为 true 且 secretRef 解析为空时，不带 `Authorization` 头发送请求（现有默认行为完全不变：secretRef 空 → Failed 不发请求）。

### 向量归属正确性（本阶段一并解决）

**问题**：现有 `source_chunks.embedding` BLOB 不记录生成模型。降级链引入后，查询向量与存储向量可能来自不同模型；同维度（1024）向量跨模型算余弦是垃圾值，且可能误超 `MinVectorScore=0.30` 阈值。

**方案**：

- **DB v19 迁移**：`ALTER TABLE source_chunks ADD COLUMN embeddingModel TEXT`；`SourceChunkEntity` 加 `embeddingModel: String? = null`
- **写入侧**：`SourceDao.updateChunkEmbedding(chunkId, embedding, embeddingModel)`；`SourceRepository.updateChunkEmbeddings(chunkIds, vectors, modelKey)` 统一落模型身份
- **读取侧**：`listChunkEmbeddings` 返回 `Map<String, StoredChunkEmbedding>`（`data class StoredChunkEmbedding(vector: FloatArray, modelKey: String?)`）
- **查询侧匹配规则**（SourceContextPortAdapter.vectorRankedEntries）：
  - `stored.modelKey` 非空 → 必须等于 `query.modelKey`，不等则跳过该 chunk
  - `stored.modelKey` 为 null（旧数据）→ 仅当 `query.channelKind == UserConfigured` 时兼容参与计算（保持旧行为：旧向量由用户渠道生成）
- **调用点适配**：`AppShell.indexSourceAsync`、`SessionConversationAssembly.sourcePort` 适配 `EmbeddingVectorSet` 新类型

### 测试计划

- `ChatGenerationRepository` 降级链：用户渠道成功不走内置 / 用户失败走内置且匿名（无 Authorization 头）/ 双失败返回 null
- `SourceContextPortAdapter`：模型匹配命中 / 不匹配过滤 / 旧数据（null modelKey）兼容
- 三处 fake DAO 签名同步：`SourceRepositoryTest`、`SourceContextPortAdapterTest`、`ChatContextEvidenceTest`

## 四、e3 实测计划（待做）

- 额度：百炼 `qwen3.7-text-embedding` 免费余量 ≈100 万 tokens（2026-10-13 过期，2026-09-19 快照确认），走 dashscope compatible-mode OpenAI 兼容端点，符合「不烧付费额度」铁律
- 调参对象：`MinVectorScore=0.30`、`SourceChunker`（TargetChunkChars=500/OverlapChars=50/MinTailChunkChars=80）、关键词回退质量
- 产出：真实资料集回归 + 命中率对比记录

## 五、暂定项（无明确标准，按流程记录跳过，不主观抉择）

| # | 暂定项 | 原因 | 解锁条件 |
|---|--------|------|----------|
| 1 | 硅基流动 API key | 注册/拿 key 需用户账号（needsUser） | 用户提供 key 或授权代注册 |
| 2 | 转发端点鉴权策略 | A 方案（免鉴权+nginx 限流仅放行 bge-m3）vs B 方案（受限 token 进 APK）无明确拍板标准 | 用户拍板 |
| 3 | 云端 hub 加 /v1/embeddings 路由并重启生产网关 | 需部署窗口，影响线上服务 | 用户确认部署时机 |

**说明**：暂定项 1 未解锁前，内置渠道调用会失败（服务器 500/上游 401），降级链自动落到关键词回退——端上行为安全可发布，e2 代码不依赖暂定项先行落地。
