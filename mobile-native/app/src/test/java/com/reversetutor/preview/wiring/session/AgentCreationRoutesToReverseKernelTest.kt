package com.reversetutor.preview.wiring.session

import com.reversetutor.feature.chat.BackgroundTurnPreparationPort
import com.reversetutor.feature.chat.BackgroundTurnPreparationRequest
import com.reversetutor.feature.chat.BackgroundTurnPreparationResult
import com.reversetutor.feature.chat.CreateSessionOutcome
import com.reversetutor.feature.chat.FormalLearningPresets
import com.reversetutor.feature.chat.NewSessionConfiguration
import com.reversetutor.feature.chat.NewSessionCreatePort
import com.reversetutor.feature.chat.NewSessionCreated
import com.reversetutor.feature.chat.NewSessionDraftRecord
import com.reversetutor.feature.chat.NewSessionFavorite
import com.reversetutor.feature.chat.NewSessionLifecycleCoordinator
import com.reversetutor.feature.chat.NewSessionPersistence
import com.reversetutor.feature.chat.SessionConversationContract
import com.reversetutor.feature.chat.SessionListItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 端到端回归门禁：**应用内创建流程建出的会话必须进反转家教算法内核**。
 *
 * 2026-10-18 真机反馈「上下文没了」的根因是这里断了：路由键曾是
 * `builtInPresetId == "reverse-tutor"`，但只有 1/8 张预设卡写得出该值——AI 创建窗口、
 * 预设「· 副本」、空白自定义草稿、挑战 prefill 建出的会话预设 id 均为空，于是全部被
 * 甩给普通 RAG 链路：没有 31 个教学工具、没有内核状态机，每轮只带 2 条消息，聊一天也
 * 不记得上一句说了什么（真机 25 条消息的 prompt tokens 恒定 1300~1400 即为铁证）。
 *
 * 现在路由键是「新建漏斗有没有落盘快照」。本用例真实跑一遍生命周期创建流程，再把
 * 落盘快照喂给路由端口，避免「两处各自正确、拼起来失效」。
 */
class AgentCreationRoutesToReverseKernelTest {

    @Test
    fun blankCustomDraftCreatedInAppRoutesToTheReversePort() = runBlocking {
        val harness = CreationHarness()
        harness.coordinator.startBlankDraft()
        harness.coordinator.updateConfiguration { draft ->
            draft.copy(
                title = "趣味心理学基础",
                learnerRole = "对日常生活现象充满好奇的大一学生",
                learnerProfile = "喜欢用生活里的例子提问",
                goal = "日常生活中的趣味心理学",
                openingMessage = "老师，先讲讲你最近观察到的一个现象吧。"
            )
        }
        assertTrue(harness.coordinator.createSession() is CreateSessionOutcome.Success)

        val snapshot = harness.promotedSnapshot()
        assertNotNull("新建漏斗必须落盘快照（路由键）", snapshot)
        assertNull("空白草稿本就没有预设 id —— 旧路由键正是被它坑掉", snapshot!!.builtInPresetId)
        assertEquals("趣味心理学基础", snapshot.title)

        val result = harness.route("session-blank", snapshot)
        assertEquals(listOf("session-blank"), harness.reverseSessions)
        assertTrue("应用内创建的会话不得落到默认链路", harness.defaultSessions.isEmpty())
        assertTrue(result is BackgroundTurnPreparationResult.Queued)
    }

    @Test
    fun presetCopyCreatedInAppRoutesToTheReversePort() = runBlocking {
        // 预设卡 → 改任一字段 → 既有设计把 preset 清空并改名「· 副本」；这类会话
        // 恰恰是最容易被旧路由键漏掉的一种。
        val harness = CreationHarness()
        val preset = FormalLearningPresets.byId(FormalLearningPresets.REVERSE_TUTOR_PRESET_ID)!!

        harness.coordinator.useBuiltInPreset(preset)
        harness.coordinator.updateConfiguration { it.copy(goal = "改过的目标") }
        assertTrue(harness.coordinator.createSession() is CreateSessionOutcome.Success)

        val snapshot = harness.promotedSnapshot()
        assertNotNull(snapshot)
        assertEquals("${preset.title} · 副本", snapshot!!.title)
        assertNull("「· 副本」按既有设计清空 preset id，但仍必须进内核", snapshot.builtInPresetId)

        harness.route("session-copy", snapshot)
        assertEquals(listOf("session-copy"), harness.reverseSessions)
        assertTrue(harness.defaultSessions.isEmpty())
    }

    @Test
    fun nonReverseTutorPresetIdentitySurvivesAndStillEntersTheKernel() = runBlocking {
        // 全量收紧**不写任何数据**：另外 7 张预设卡的 id 原样保留（它兼作 templateId／
        // 导出标签），路由改由「有无快照」决定，预设身份不再承担路由职责。
        val harness = CreationHarness()
        val other = FormalLearningPresets.all
            .first { it.id != FormalLearningPresets.REVERSE_TUTOR_PRESET_ID }

        harness.coordinator.useBuiltInPreset(other)
        assertTrue(harness.coordinator.createSession() is CreateSessionOutcome.Success)

        val snapshot = harness.promotedSnapshot()
        assertNotNull(snapshot)
        assertEquals(other.id, snapshot!!.builtInPresetId)

        harness.route("session-preset", snapshot)
        assertEquals(listOf("session-preset"), harness.reverseSessions)
        assertTrue(harness.defaultSessions.isEmpty())
    }

    @Test
    fun routingConstantAndPresetCardShareOneSourceOfTruth() {
        assertEquals(
            FormalLearningPresets.REVERSE_TUTOR_PRESET_ID,
            REVERSE_TUTOR_PRESET_ID
        )
        assertEquals(
            FormalLearningPresets.REVERSE_TUTOR_PRESET_ID,
            FormalLearningPresets.byId(REVERSE_TUTOR_PRESET_ID)?.id
        )
    }

    /** 真实生命周期协调器 + 内存持久化 + 记录型路由端口。 */
    private class CreationHarness {
        val reverseSessions = mutableListOf<String>()
        val defaultSessions = mutableListOf<String>()

        private val promoted = LinkedHashMap<String, NewSessionConfiguration>()
        private var now = 1_000L
        private var seq = 0

        private val persistence = object : NewSessionPersistence {
            private var drafts: List<NewSessionDraftRecord> = emptyList()
            private var favorites: List<NewSessionFavorite> = emptyList()

            override fun loadDrafts(): List<NewSessionDraftRecord> = drafts

            override fun replaceDrafts(drafts: List<NewSessionDraftRecord>) {
                this.drafts = drafts
            }

            override fun loadFavorites(): List<NewSessionFavorite> = favorites

            override fun replaceFavorites(favorites: List<NewSessionFavorite>) {
                this.favorites = favorites
            }

            override fun promoteDraft(
                draftId: String,
                sessionId: String,
                snapshot: NewSessionConfiguration
            ) {
                drafts = drafts.filterNot { it.id == draftId }
                promoted[sessionId] = snapshot.copy()
            }

            override fun loadSessionSnapshot(sessionId: String): NewSessionConfiguration? =
                promoted[sessionId]?.copy()
        }

        private val createPort = NewSessionCreatePort { request ->
            NewSessionCreated(
                session = SessionListItem(
                    id = request.attemptId,
                    title = request.snapshot.title,
                    updatedAtEpochMillis = now,
                    pinned = false,
                    statusLabel = "",
                    unreadCount = 0,
                    avatarLabel = request.snapshot.title.take(1),
                    learnerRole = request.snapshot.learnerRole
                ),
                learnerRole = request.snapshot.learnerRole,
                openingMessage = request.snapshot.openingMessage
            )
        }

        val coordinator = NewSessionLifecycleCoordinator(
            persistence = persistence,
            createPort = createPort,
            nowEpochMillis = { ++now },
            idFactory = { "id-${++seq}" }
        ).also { it.load() }

        private val port = RoutingTurnPreparationPort(
            reversePort = BackgroundTurnPreparationPort { request ->
                reverseSessions.add(request.sessionId)
                BackgroundTurnPreparationResult.Queued(
                    jobId = "job-rt",
                    contract = SessionConversationContract.empty(request.sessionId)
                )
            },
            defaultPort = BackgroundTurnPreparationPort { request ->
                defaultSessions.add(request.sessionId)
                BackgroundTurnPreparationResult.Unavailable
            }
        )

        fun promotedSnapshot(): NewSessionConfiguration? = promoted.values.lastOrNull()?.copy()

        suspend fun route(
            sessionId: String,
            snapshot: NewSessionConfiguration
        ): BackgroundTurnPreparationResult = port.prepareAndEnqueue(
            BackgroundTurnPreparationRequest(
                spaceId = "space-1",
                sessionId = sessionId,
                userMessageId = "u-1",
                userText = "我想学点日常生活中的心理学",
                token = "tok-1",
                quoteExcerpt = null,
                imageAttachments = emptyList(),
                sessionSnapshot = snapshot
            )
        )
    }
}
