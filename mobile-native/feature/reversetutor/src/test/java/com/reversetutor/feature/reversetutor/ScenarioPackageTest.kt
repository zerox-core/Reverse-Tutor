package com.reversetutor.feature.reversetutor

import com.reversetutor.feature.reversetutor.retrieval.MaterialStore
import com.reversetutor.feature.reversetutor.scenario.ScenarioPackage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 场景包注入（challenge-02 打通端到端的第一切片）。
 *
 * 断言两件事：
 * 1. 解析/注入本身按契约工作（含容错）；
 * 2. 真实冻结产物（assets/scenarios/challenge-02-gamer-health.json）能被引擎消费——
 *    非数学场景的素材检索得到命中，且数学默认资料不受影响（向后兼容）。
 */
class ScenarioPackageTest {

    private val assetsDir: String
        get() = "${System.getProperty("user.dir")}/src/main/assets"

    private fun realPackage(): ScenarioPackage =
        ScenarioPackage.parse(
            File("$assetsDir/scenarios/challenge-02-gamer-health.json").readText()
        )

    // ---------- 解析与容错 ----------

    @Test
    fun parsesSourcesWithProvenance() {
        val pkg = ScenarioPackage.parse(
            """
            {"scenarioId":"demo","sources":[
              {"docId":"d1","title":"标题一","content":"正文一","source":"出处","url":"https://x","day":"D1"},
              {"docId":"d2","title":"标题二","content":"正文二"}
            ]}
            """.trimIndent()
        )
        assertEquals("demo", pkg.scenarioId)
        assertEquals(2, pkg.sources.size)
        assertEquals("出处", pkg.sources[0].source)
        assertEquals("https://x", pkg.sources[0].url)
        assertEquals("D1", pkg.sources[0].day)
        // 缺失字段按空串容错
        assertEquals("", pkg.sources[1].source)
        assertEquals("", pkg.sources[1].day)
    }

    @Test
    fun skipsEntriesWithoutDocIdOrContent() {
        val pkg = ScenarioPackage.parse(
            """
            {"scenarioId":"demo","sources":[
              {"docId":"ok","title":"t","content":"c"},
              {"docId":"","title":"无 id","content":"c"},
              {"docId":"no-body","title":"无正文","content":""}
            ]}
            """.trimIndent()
        )
        assertEquals(1, pkg.sources.size)
        assertEquals("ok", pkg.sources[0].docId)
    }

    @Test
    fun emptyOrMissingSourcesYieldsEmptyPackage() {
        assertEquals(0, ScenarioPackage.parse("""{"scenarioId":"demo"}""").sources.size)
        assertEquals(0, ScenarioPackage.parse("""{"scenarioId":"demo","sources":[]}""").sources.size)
    }

    // ---------- 注入进资料库 ----------

    @Test
    fun installIntoMakesSourcesSearchable() {
        val store = MaterialStore()
        val baseline = store.count()
        val pkg = ScenarioPackage.parse(
            """
            {"scenarioId":"demo","sources":[
              {"docId":"health-1","title":"久坐与碎片化微运动",
               "content":"久坐会让代谢变慢，碎片化微运动可以打断久坐带来的伤害。"}
            ]}
            """.trimIndent()
        )
        assertEquals(1, pkg.installInto(store))
        assertEquals(baseline + 1, store.count())

        val hits = store.search("久坐")
        assertTrue("场景素材应可检索命中", hits.any { it.docId == "health-1" })
    }

    @Test
    fun installIntoKeepsBuiltinMathDocs() {
        val store = MaterialStore()
        val baseline = store.count()
        realPackage().installInto(store)
        assertTrue("数学默认资料应保留（向后兼容）", store.count() > baseline)
        // 数学检索仍命中内置资料
        assertTrue(store.search("配方法").isNotEmpty())
    }

    // ---------- 真实冻结产物 conformance ----------

    @Test
    fun realPackageExportsAllElevenSources() {
        val pkg = realPackage()
        assertEquals("challenge-02-gamer-health", pkg.scenarioId)
        assertEquals(11, pkg.sources.size)
        for (doc in pkg.sources) {
            assertTrue("${doc.docId} 应有标题", doc.title.isNotBlank())
            assertTrue("${doc.docId} 正文字数过少: ${doc.content.length}", doc.content.length > 200)
            assertTrue("${doc.docId} front-matter 不应进正文", !doc.content.startsWith("---"))
            assertTrue("${doc.docId} 应保留来源", doc.source.isNotBlank())
        }
    }

    @Test
    fun realPackageServesNonMathRetrieval() {
        val store = MaterialStore()
        realPackage().installInto(store)

        // 健康主题（久坐/睡眠/膳食）此前在资料库中必然查空——数学资料不含这些词
        val sedentary = store.search("久坐")
        assertTrue("久坐素材应命中", sedentary.isNotEmpty())
        assertTrue(sedentary.first().title.isNotBlank())

        assertTrue("睡眠素材应命中", store.search("睡眠").isNotEmpty())
        assertTrue("膳食素材应命中", store.search("膳食").isNotEmpty())
    }
}
