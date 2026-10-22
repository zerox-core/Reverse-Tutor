package com.reversetutor.feature.chat

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-10-16 用户拍板：会话创建流程 = 模型自选工具。
 *
 * - 模型报 `action=direct_create`（[AgentCreationAction.DirectCreate]）→ 客户端用确定性兜底
 *   补全必填项后立即创建并进入会话窗口；
 * - 模型报 `action=interview` 或没报 → 走既有 R99/R100/R102 多轮访谈收尾，这里不直接创建。
 */
class AgentCreationOneLineCommandTest {

    private class CommandGateway(
        private val turns: List<AgentCreationTurnResult>
    ) : AgentCreationGateway {
        val userTexts = mutableListOf<String>()
        var calls = 0

        override suspend fun converse(
            history: List<AgentCreationHistoryTurn>,
            userText: String,
            currentDraft: NewSessionConfiguration,
            docAnalysis: AgentCreationDocAnalysis?,
            strategy: AgentCreationTurnStrategy
        ): AgentCreationTurnResult {
            calls++
            userTexts += userText
            return turns[(calls - 1).coerceAtMost(turns.size - 1)]
        }

        override suspend fun analyzeDocument(fileName: String): AgentCreationDocAnalysis =
            AgentCreationDocAnalysis(materialTitle = fileName)
    }

    private fun clock(): () -> Long {
        var tick = 0L
        return { tick++ }
    }

    private fun assistantsAfterOpening(coordinator: AgentCreationCoordinator) =
        coordinator.state.feed.filterIsInstance<AgentCreationFeedEntry.Assistant>().drop(1)

    /** 快车道实际补问的字段问题（去重前按出现顺序）——用来验证「只问一个」。 */
    private fun askedFallbackQuestions(coordinator: AgentCreationCoordinator): List<String> {
        val questions = AgentCreationFollowUpPlanner.Field.entries.map { it.fallbackQuestion }.toSet()
        return assistantsAfterOpening(coordinator).map { it.text }.filter { it in questions }
    }

    // (d) 模型调用「直接创建」工具 → 立即给出创建信号（UI 随后走既有 performCreate）。
    @Test
    fun directCreateToolGoesStraightToReadyAndRequestsCreate() = runBlocking {
        val command = "我想学高中物理的电磁感应，我是高三学生"
        val gateway = CommandGateway(
            listOf(
                AgentCreationTurnResult(
                    understanding = 90,
                    assistantNote = "好，高中物理电磁感应，我按高三的强度来安排。",
                    followUpQuestion = null,
                    action = AgentCreationAction.DirectCreate,
                    draft = AgentCreationDraftPatch(
                        goal = "高中物理电磁感应",
                        learnerRole = "高三学生",
                        title = "高中物理电磁感应 · 讲学练"
                    )
                )
            )
        )
        val coordinator = AgentCreationCoordinator(gateway, clock())
        coordinator.start()

        coordinator.sendUserText(command)

        val state = coordinator.state
        assertTrue(state.canCreate)
        assertTrue(state.draft.validationErrors().isEmpty())
        assertEquals("高中物理电磁感应", state.draft.goal)
        assertEquals("高三学生", state.draft.learnerRole)
        assertTrue(state.createRequested)
        // 模型没追问 → 客户端也不补任何字段问题。
        assertTrue(askedFallbackQuestions(coordinator).isEmpty())

        coordinator.consumeCreateRequest()
        assertFalse(coordinator.state.createRequested)
    }

    // (d2) 模型调用「多轮访谈」工具 → 不创建，交给既有访谈流程继续问。
    @Test
    fun interviewToolKeepsConversationInsteadOfCreating() = runBlocking {
        val gateway = CommandGateway(
            listOf(
                AgentCreationTurnResult(
                    understanding = 55,
                    assistantNote = "高中物理电磁感应，先记下。",
                    followUpQuestion = "你平时做题卡在哪一步？",
                    action = AgentCreationAction.Interview,
                    draft = AgentCreationDraftPatch(goal = "高中物理电磁感应")
                )
            )
        )
        val coordinator = AgentCreationCoordinator(gateway, clock())
        coordinator.start()

        coordinator.sendUserText("我想学高中物理电磁感应")

        assertFalse("访谈工具不该直接创建", coordinator.state.createRequested)
        val texts = assistantsAfterOpening(coordinator).map { it.text }
        assertTrue("访谈工具必须给出一个追问：$texts", texts.any { it.contains("？") })
    }

    // (e) 模型调用了「直接创建」但草案字段不全 → 确定性兜底补全后直接创建（绝不建半成品）。
    @Test
    fun directCreateToolFillsMissingFieldsWithDeterministicFallback() = runBlocking {
        val gateway = CommandGateway(
            listOf(
                AgentCreationTurnResult(
                    understanding = 70,
                    assistantNote = "好，就按高中物理电磁感应来建。",
                    action = AgentCreationAction.DirectCreate,
                    draft = AgentCreationDraftPatch(goal = "高中物理电磁感应")
                )
            )
        )
        val coordinator = AgentCreationCoordinator(gateway, clock())
        coordinator.start()

        coordinator.sendUserText("我想学高中物理的电磁感应")

        val state = coordinator.state
        assertTrue(state.createRequested)
        assertTrue(state.draft.validationErrors().isEmpty())
        assertEquals("高中物理电磁感应", state.draft.goal)
        assertEquals(AgentCreationOneLineCommand.DEFAULT_LEARNER_ROLE, state.draft.learnerRole)
        assertTrue(state.draft.title.isNotBlank())
    }

    // 模型没报 action（或报了不认识的词）→ 保守回落到已拍板的访谈流程，绝不擅自创建。
    @Test
    fun missingActionFallsBackToRatifiedInterviewBehavior() = runBlocking {
        val gateway = CommandGateway(
            listOf(
                AgentCreationTurnResult(
                    understanding = 70,
                    assistantNote = "记下了。",
                    followUpQuestion = null,
                    action = null,
                    draft = AgentCreationDraftPatch(
                        goal = "高中物理电磁感应",
                        learnerRole = "高一学生",
                        title = "高中物理电磁感应 · 讲学练"
                    )
                )
            )
        )
        val coordinator = AgentCreationCoordinator(gateway, clock())
        coordinator.start()

        coordinator.sendUserText("我想学高中物理的电磁感应，我是高一学生")

        assertFalse("没报 action 不该直接创建", coordinator.state.createRequested)
    }

    // 单问回答的字段落定（direct_create 兜底路径的最后安全网）：用户原话成为被问字段值，已有值不覆盖。
    @Test
    fun applyCommandAnswerKeepsUserWordingForTheAskedField() {
        val draft = NewSessionConfiguration(goal = "高中物理电磁感应")
        val answered = AgentCreationOneLineCommand.applyCommandAnswer(
            draft,
            AgentCreationFollowUpPlanner.Field.LearnerRole,
            "我基础一般"
        )
        assertEquals("我基础一般", answered.learnerRole)

        val filled = draft.copy(learnerRole = "高一学生")
        val untouched = AgentCreationOneLineCommand.applyCommandAnswer(
            filled,
            AgentCreationFollowUpPlanner.Field.LearnerRole,
            "随便"
        )
        assertEquals("高一学生", untouched.learnerRole)
    }

    // 模型调用了 direct_create 但一个字段都没给：用用户最初那句命令兜底成 goal/title 后直接创建。
    @Test
    fun directCreateToolUsesCommandTextWhenModelGivesNoFields() = runBlocking {
        val gateway = CommandGateway(
            listOf(
                AgentCreationTurnResult(
                    understanding = 40,
                    assistantNote = "收到。",
                    action = AgentCreationAction.DirectCreate
                )
            )
        )
        val coordinator = AgentCreationCoordinator(gateway, clock())
        coordinator.start()

        coordinator.sendUserText("我想学高中物理的电磁感应")

        assertTrue(coordinator.state.createRequested)
        assertEquals("我想学高中物理的电磁感应", coordinator.state.draft.goal)
        assertTrue(coordinator.state.draft.validationErrors().isEmpty())
    }

    // 辅助：CreateWindow 之外，快车道自己的确定性兜底（缺 title / learnerRole 时补成可创建）。
    @Test
    fun finalizeForCreateFillsMissingTitleAndLearnerRole() {
        val completed = AgentCreationOneLineCommand.finalizeForCreate(
            draft = NewSessionConfiguration(goal = "把高中物理电磁感应讲明白"),
            commandText = "我想学高中物理的电磁感应"
        )

        assertEquals("把高中物理电磁感应讲明白 · 讲学练", completed.title)
        assertEquals(AgentCreationOneLineCommand.DEFAULT_LEARNER_ROLE, completed.learnerRole)
        assertTrue(completed.validationErrors().isEmpty())
    }

    @Test
    fun finalizeForCreateUsesCommandTextWhenGoalMissing() {
        val completed = AgentCreationOneLineCommand.finalizeForCreate(
            draft = NewSessionConfiguration(),
            commandText = "我想学高中物理的电磁感应"
        )

        assertEquals("我想学高中物理的电磁感应", completed.goal)
        assertTrue(completed.title.isNotBlank())
        assertTrue(completed.validationErrors().isEmpty())
    }

    @Test
    fun finalizeForCreateKeepsValuesAlreadyPresent() {
        val draft = NewSessionConfiguration(
            title = "已有标题",
            learnerRole = "初三学生",
            goal = "浮力"
        )

        assertEquals(draft, AgentCreationOneLineCommand.finalizeForCreate(draft, "命令原文"))
        assertTrue(AgentCreationOneLineCommand.readyToCreate(draft))
        assertFalse(AgentCreationOneLineCommand.readyToCreate(NewSessionConfiguration(goal = "浮力")))
    }

    // (d) 收尾：Ready 之后走既有创建管道（UI 的 performCreate 路径），创建请求里就是命令成稿。
    @Test
    fun readyCommandCreatesThroughTheExistingLifecycleCreatePath() = runBlocking {
        val gateway = CommandGateway(
            listOf(
                AgentCreationTurnResult(
                    understanding = 80,
                    assistantNote = "好，直接建会话。",
                    action = AgentCreationAction.DirectCreate,
                    draft = AgentCreationDraftPatch(
                        goal = "高中物理电磁感应",
                        learnerRole = "高三学生",
                        title = "高中物理电磁感应 · 讲学练"
                    )
                )
            )
        )
        val coordinator = AgentCreationCoordinator(gateway, clock())
        coordinator.start()
        coordinator.sendUserText("我想学高中物理的电磁感应，我是高三学生")
        assertTrue(coordinator.state.createRequested)

        val persistence = RecordingSessionPersistence()
        var createdRequest: NewSessionCreateRequest? = null
        val lifecycle = NewSessionLifecycleCoordinator(
            persistence = persistence,
            createPort = NewSessionCreatePort { request ->
                createdRequest = request
                NewSessionCreated(
                    session = SessionListItem(
                        id = "session-1",
                        title = request.snapshot.title,
                        updatedAtEpochMillis = 1L,
                        pinned = false,
                        statusLabel = request.snapshot.openingMessage,
                        unreadCount = 0,
                        avatarLabel = "高",
                        learnerRole = request.snapshot.learnerRole
                    ),
                    learnerRole = request.snapshot.learnerRole,
                    openingMessage = request.snapshot.openingMessage
                )
            },
            nowEpochMillis = { 1L },
            idFactory = { "1" }
        )

        // UI performCreate()：确保有草稿记录 → 落协调器草案 → 边界保存 → 创建
        assertTrue(lifecycle.startBlankDraft())
        lifecycle.updateConfiguration { coordinator.configuration() }
        lifecycle.saveBoundary()
        val outcome = lifecycle.createSession()

        assertTrue(outcome is CreateSessionOutcome.Success)
        assertNotNull(createdRequest)
        assertEquals("高中物理电磁感应 · 讲学练", createdRequest?.snapshot?.title)
        assertEquals("高三学生", createdRequest?.snapshot?.learnerRole)
        assertEquals("高中物理电磁感应", createdRequest?.snapshot?.goal)
    }
}

/** 最小内存版 NewSessionPersistence（本文件自用，不依赖其他测试文件的私有 fake）。 */
private class RecordingSessionPersistence : NewSessionPersistence {
    private var drafts: List<NewSessionDraftRecord> = emptyList()
    private var favorites: List<NewSessionFavorite> = emptyList()
    private val promoted = mutableMapOf<String, NewSessionConfiguration>()

    override fun loadDrafts(): List<NewSessionDraftRecord> = drafts

    override fun replaceDrafts(drafts: List<NewSessionDraftRecord>) {
        this.drafts = drafts
    }

    override fun loadFavorites(): List<NewSessionFavorite> = favorites

    override fun replaceFavorites(favorites: List<NewSessionFavorite>) {
        this.favorites = favorites
    }

    override fun promoteDraft(draftId: String, sessionId: String, snapshot: NewSessionConfiguration) {
        drafts = drafts.filterNot { it.id == draftId }
        promoted[sessionId] = snapshot
    }

    override fun loadSessionSnapshot(sessionId: String): NewSessionConfiguration? = promoted[sessionId]
}
