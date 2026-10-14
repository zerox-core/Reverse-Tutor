package com.reversetutor.feature.reversetutor.scenario

import com.reversetutor.feature.reversetutor.retrieval.MaterialStore
import org.json.JSONObject

/**
 * 学习场景包（2026-10-14 用户拍板：算法为基底引擎、场景为配置数据）。
 *
 * 规格源：`activities/<slug>/`（内容真源，人工为一个学习主题配好学生卡、资料、
 * 逐日编排与评测细则）；经 `py -m scripts.build_scenario_packages` 导出为
 * `assets/scenarios/<slug>.json`（冻结生成产物，勿手改）。
 *
 * 本切片（最小切片：只打通注入）只消费 `sources`：把活动包的 `sources/src-*.md`
 * 注入 [MaterialStore]，使学生资料检索（search_materials）在非数学场景下也能
 * 命中事实——此前 `addDoc` 没有任何生产调用点，场景资料处于空转。
 *
 * 未纳入本切片：学生卡映射（活动包 yaml 与学生卡 JSON 的 8 桶示例台词结构不同，
 * 且包内 yaml 自述为草案、权重待定，需先定映射规则）、课标与 rubric 注入。
 */
class ScenarioPackage(
    val scenarioId: String,
    val sources: List<SourceDoc>
) {

    /** 场景素材（一篇 `sources/src-*.md`）。 */
    data class SourceDoc(
        val docId: String,
        val title: String,
        val content: String,
        val source: String = "",
        val url: String = "",
        val day: String = ""
    )

    /**
     * 把场景素材注入资料库，返回注入条数。
     *
     * 只调 [MaterialStore] 公开 API；`grade` 留空（活动素材不按学段归档），
     * 检索时传 null 学段过滤即可命中。
     */
    fun installInto(store: MaterialStore): Int {
        var installed = 0
        for (doc in sources) {
            if (doc.content.isBlank()) continue
            store.addDoc(
                docId = doc.docId,
                title = doc.title,
                content = doc.content
            )
            installed++
        }
        return installed
    }

    companion object {

        /**
         * 解析导出产物。字段缺失按空串容错（生成器已保证 docId/title/content，
         * 但来源/URL/天数可能因包而异）。
         */
        fun parse(json: String): ScenarioPackage {
            val root = JSONObject(json)
            val scenarioId = root.optString("scenarioId", "")
            val arr = root.optJSONArray("sources") ?: return ScenarioPackage(scenarioId, emptyList())
            val docs = (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val docId = obj.optString("docId", "")
                val content = obj.optString("content", "")
                if (docId.isBlank() || content.isBlank()) return@mapNotNull null
                SourceDoc(
                    docId = docId,
                    title = obj.optString("title", docId),
                    content = content,
                    source = obj.optString("source", ""),
                    url = obj.optString("url", ""),
                    day = obj.optString("day", "")
                )
            }
            return ScenarioPackage(scenarioId, docs)
        }
    }
}
