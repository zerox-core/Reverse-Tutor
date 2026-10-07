package com.reversetutor.feature.chat

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R86：创建链路学习路径——LLM 补丁优先、文档「建议路径」兜底、解析不丢字段。 */
class AgentCreationLearningPathTest {

    private class ScriptedGateway(
        private val result: AgentCreationTurnResult,
        private val analysis: AgentCreationDocAnalysis = AgentCreationDocAnalysis(materialTitle = "讲义")
    ) : AgentCreationGateway {
        override suspend fun converse(
            history: List<AgentCreationHistoryTurn>,
            userText: String,
            currentDraft: NewSessionConfiguration,
            docAnalysis: AgentCreationDocAnalysis?,
            strategy: AgentCreationTurnStrategy
        ): AgentCreationTurnResult = result

        override suspend fun analyzeDocument(fileName: String): AgentCreationDocAnalysis = analysis
    }

    private fun clock(): () -> Long {
        var tick = 0L
        return { tick++ }
    }

    // ---- 补丁叠加语义 ----

    @Test
    fun patchAppliesLearningPathWhenPresent() {
        val base = NewSessionConfiguration(goal = "学物理")
        val patch = AgentCreationDraftPatch(learningPath = listOf("密度", "浮力", "压强"))
        assertEquals(listOf("密度", "浮力", "压强"), patch.applyTo(base).learningPath)
    }

    @Test
    fun patchWithoutLearningPathKeepsBaseValue() {
        val base = NewSessionConfiguration(learningPath = listOf("密度", "浮力"))
        val patch = AgentCreationDraftPatch(goal = "换个目标")
        assertEquals(listOf("密度", "浮力"), patch.applyTo(base).learningPath)
    }

    // ---- 解析 ----

    @Test
    fun parserReadsLearningPathArray() {
        val raw = """{"understanding":50,"assistantNote":"好","draft":{"goal":"学物理","learningPath":["密度","浮力","压强"]}}"""
        val turn = AgentCreationParser.parseTurnResult(raw)
        assertEquals(listOf("密度", "浮力", "压强"), turn?.draft?.learningPath)
    }

    @Test
    fun parserLeavesLearningPathNullWhenAbsent() {
        val raw = """{"understanding":50,"assistantNote":"好","draft":{"goal":"学物理"}}"""
        val turn = AgentCreationParser.parseTurnResult(raw)
        assertNull(turn?.draft?.learningPath)
    }

    @Test
    fun parserLeavesLearningPathNullWhenEmptyArray() {
        val raw = """{"understanding":50,"assistantNote":"好","draft":{"goal":"学物理","learningPath":[]}}"""
        val turn = AgentCreationParser.parseTurnResult(raw)
        assertNull(turn?.draft?.learningPath)
    }

    // ---- 协调器：LLM 优先、文档兜底 ----

    @Test
    fun llmPatchPathWinsOverDocumentSuggestion() = runBlocking {
        val gateway = ScriptedGateway(
            result = AgentCreationTurnResult(
                understanding = 60,
                draft = AgentCreationDraftPatch(
                    goal = "学物理",
                    learningPath = listOf("LLM分解一", "LLM分解二", "LLM分解三")
                )
            ),
            analysis = AgentCreationDocAnalysis(
                materialTitle = "物理讲义",
                suggestedPath = listOf("教材第一章", "教材第二章")
            )
        )
        val coordinator = AgentCreationCoordinator(gateway, clock())
        coordinator.attachDocument("物理讲义.pdf", "12 KB")
        coordinator.sendUserText("我想学物理")

        assertEquals(listOf("LLM分解一", "LLM分解二", "LLM分解三"), coordinator.state.draft.learningPath)
    }

    @Test
    fun documentSuggestedPathFillsWhenLlmSilent() = runBlocking {
        val gateway = ScriptedGateway(
            result = AgentCreationTurnResult(
                understanding = 60,
                draft = AgentCreationDraftPatch(goal = "学物理")
            ),
            analysis = AgentCreationDocAnalysis(
                materialTitle = "物理讲义",
                suggestedPath = listOf(" 教材第一章 ", "", "教材第二章", "教材第一章", "教材第三章")
            )
        )
        val coordinator = AgentCreationCoordinator(gateway, clock())
        coordinator.attachDocument("物理讲义.pdf", "12 KB")
        coordinator.sendUserText("我想学物理")

        // 兜底路径：trim、去空、去重后按目录顺序。
        assertEquals(listOf("教材第一章", "教材第二章", "教材第三章"), coordinator.state.draft.learningPath)
    }

    @Test
    fun noLlmPathAndNoDocumentLeavesPathEmpty() = runBlocking {
        val gateway = ScriptedGateway(
            result = AgentCreationTurnResult(
                understanding = 60,
                draft = AgentCreationDraftPatch(goal = "学物理")
            )
        )
        val coordinator = AgentCreationCoordinator(gateway, clock())
        coordinator.start()
        coordinator.sendUserText("我想学物理")

        assertTrue(coordinator.state.draft.learningPath.isEmpty())
    }
}
