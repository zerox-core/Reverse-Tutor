package com.reversetutor.feature.reversetutor.retrieval

import org.json.JSONObject

/**
 * 外部检索提供方端口（对齐 spike `app/tools/retrieval.py` 的 ddg / cmd 两种 provider）。
 *
 * 与 spike 的差异及原因：
 * 1. spike 的 `cmd` provider 通过 `subprocess` 调用外部命令；Android 无此能力，
 *    故只保留端口，由接入层决定实现（如 OkHttp 直连、或走 native 侧既有在线服务）。
 * 2. 测试禁止打真实网络（AGENTS.md §2），因此**默认不注入任何 provider**——
 *    等价 spike 的 `WEB_SEARCH_PROVIDER=off`：工具如实返回 `available=false`
 *    的事实（非错误），LLM 自然绕过。
 * 3. spike 的 ddg 抓取依赖 HTML 正则解析；此处不做内置实现，避免把脆弱的
 *    抓取逻辑固化进算法内核。
 */
interface RTWebSearchProvider {

    /** 对齐 spike 的 provider 名（"ddg" / "cmd" 或接入层自定义）。 */
    val providerName: String

    /**
     * 返回 `[{title, snippet, url}]`（对齐 spike `_ddg_search` / `_cmd_search` 的输出）。
     * 失败时抛异常 → 工具结构化降级（`success=false`，重试 1 次）。
     */
    fun search(query: String, maxResults: Int): List<JSONObject>
}

/** 外部检索失败（网络不可达、命令失败、被限流等）。 */
class RTWebSearchException(message: String) : Exception(message)
