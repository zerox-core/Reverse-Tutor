package com.reversetutor.preview.wiring.session

import com.reversetutor.feature.chat.BackgroundTurnPreparationPort
import com.reversetutor.feature.chat.BackgroundTurnPreparationRequest
import com.reversetutor.feature.chat.BackgroundTurnPreparationResult
import com.reversetutor.feature.chat.NewSessionConfiguration
import com.reversetutor.feature.chat.SessionConversationContract
import com.reversetutor.feature.chat.TurnMonologuePort
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * RoutingTurnPreparationPort 三项验收：
 * 1. 分派（2026-10-18 用户拍板「应用内新建会话一律进内核」）：有落盘快照（= 新建漏斗
 *    建出的会话）→ reversePort；无快照（首启欢迎/演示、空间导入）→ defaultPort。
 *    旧键 `builtInPresetId == "reverse-tutor"` 已废弃：只有 1/8 张预设卡写得出该值，
 *    其余入口（AI 创建窗口、预设副本、空白草稿、挑战 prefill）会静默掉进普通链路。
 * 2. 流式桥接（2026-10-11 拍板）：TurnMonologuePort 读口转发给 reversePort；
 *    reversePort 未实现时返回 null（调用方回退仓库直读）。
 * 3. 连发合并钩子（智能插队，2026-10-11 拍板升级）：仅普通会话每条入队前以会话 id
 *    调用一次（Queued 取消式合并）；RT 会话不跑钩子——其连发由协调器内部智能插队
 *    吸收（activeJobs/pendingInjections），钩子异常不得阻断正常回合入队。
 */
class RoutingTurnPreparationPortTest {

    /**
     * @param createdInApp true = 应用内新建（新建漏斗 `promoteDraft` 落了快照）；
     *   false = 无快照的会话（首启欢迎/演示会话、空间导入的历史会话）。
     * @param builtInPresetId 预设身份，仅作数据字段，**不再参与路由判定**。
     */
    private fun request(
        sessionId: String,
        createdInApp: Boolean,
        builtInPresetId: String? = null
    ) = BackgroundTurnPreparationRequest(
        spaceId = "space-1",
        sessionId = sessionId,
        userMessageId = "u-1",
        userText = "这道题怎么解",
        token = "tok-1",
        quoteExcerpt = null,
        imageAttachments = emptyList(),
        sessionSnapshot = if (createdInApp) {
            NewSessionConfiguration(title = "会话", builtInPresetId = builtInPresetId)
        } else {
            null
        }
    )

    private class RecordingReversePort : BackgroundTurnPreparationPort, TurnMonologuePort {
        val sessions = mutableListOf<String>()
        override suspend fun prepareAndEnqueue(
            request: BackgroundTurnPreparationRequest
        ): BackgroundTurnPreparationResult {
            sessions.add(request.sessionId)
            return BackgroundTurnPreparationResult.Queued(
                jobId = "job-rt",
                contract = SessionConversationContract.empty(request.sessionId)
            )
        }
        override fun getMonologue(jobId: String, token: String): String? =
            if (jobId == "job-rt") "独白-$token" else null
        override fun getPreview(jobId: String, token: String): String? =
            if (jobId == "job-rt") "预览-$token" else null
    }

    @Test
    fun `created session goes to reversePort`() = runBlocking {
        val reversePort = RecordingReversePort()
        val defaultCalls = mutableListOf<String>()
        val port = RoutingTurnPreparationPort(
            reversePort = reversePort,
            defaultPort = BackgroundTurnPreparationPort { request ->
                defaultCalls.add(request.sessionId)
                BackgroundTurnPreparationResult.Unavailable
            }
        )
        val result = port.prepareAndEnqueue(
            request("session-rt", createdInApp = true, builtInPresetId = REVERSE_TUTOR_PRESET_ID)
        )
        assertEquals(listOf("session-rt"), reversePort.sessions)
        assertEquals(emptyList<String>(), defaultCalls)
        assertEquals(
            BackgroundTurnPreparationResult.Queued(
                jobId = "job-rt",
                contract = SessionConversationContract.empty("session-rt")
            ),
            result
        )
    }

    @Test
    fun `non reverse tutor preset card session also goes to reversePort`() = runBlocking {
        // 2026-10-18 新键的核心断言：另外 7 张预设卡（如 formal-math-sprint）、预设「· 副本」、
        // 空白自定义草稿、挑战 prefill 建出的会话都带落盘快照 → 必须进内核，不得按预设 id 分家。
        val reversePort = RecordingReversePort()
        val defaultCalls = mutableListOf<String>()
        val port = RoutingTurnPreparationPort(
            reversePort = reversePort,
            defaultPort = BackgroundTurnPreparationPort { request ->
                defaultCalls.add(request.sessionId)
                BackgroundTurnPreparationResult.Unavailable
            }
        )

        port.prepareAndEnqueue(
            request("session-preset", createdInApp = true, builtInPresetId = "formal-math-sprint")
        )
        port.prepareAndEnqueue(request("session-copy", createdInApp = true, builtInPresetId = null))
        port.prepareAndEnqueue(request("session-blank", createdInApp = true, builtInPresetId = null))
        port.prepareAndEnqueue(
            request("session-challenge", createdInApp = true, builtInPresetId = null)
        )

        assertEquals(
            listOf("session-preset", "session-copy", "session-blank", "session-challenge"),
            reversePort.sessions
        )
        assertEquals(emptyList<String>(), defaultCalls)
    }

    @Test
    fun `session without snapshot goes to defaultPort`() = runBlocking {
        val defaultCalls = mutableListOf<String>()
        val port = RoutingTurnPreparationPort(
            reversePort = RecordingReversePort(),
            defaultPort = BackgroundTurnPreparationPort { request ->
                defaultCalls.add(request.sessionId)
                BackgroundTurnPreparationResult.Queued(
                    jobId = "job-default",
                    contract = SessionConversationContract.empty(request.sessionId)
                )
            }
        )
        val result = port.prepareAndEnqueue(request("session-normal", createdInApp = false))
        assertEquals(listOf("session-normal"), defaultCalls)
        assertEquals(BackgroundTurnPreparationResult.Queued(
                    jobId = "job-default",
                    contract = SessionConversationContract.empty("session-normal")
                ), result)
    }

    @Test
    fun `monologue port forwards to reversePort when implemented`() {
        val port = RoutingTurnPreparationPort(
            reversePort = RecordingReversePort(),
            defaultPort = BackgroundTurnPreparationPort { BackgroundTurnPreparationResult.Unavailable }
        )
        assertEquals("独白-tok-1", port.getMonologue("job-rt", "tok-1"))
        assertEquals("预览-tok-1", port.getPreview("job-rt", "tok-1"))
        // 未知 jobId（如普通会话任务）不在 RT store 中 → null，调用方回退仓库。
        assertNull(port.getMonologue("job-other", "tok-1"))
        assertNull(port.getPreview("job-other", "tok-1"))
    }

    @Test
    fun `monologue port returns null when reversePort does not implement it`() {
        val port = RoutingTurnPreparationPort(
            reversePort = BackgroundTurnPreparationPort { request ->
                BackgroundTurnPreparationResult.Queued(
                    jobId = "job-x",
                    contract = SessionConversationContract.empty(request.sessionId)
                )
            },
            defaultPort = BackgroundTurnPreparationPort { BackgroundTurnPreparationResult.Unavailable }
        )
        assertNull(port.getMonologue("job-rt", "tok-1"))
        assertNull(port.getPreview("job-rt", "tok-1"))
    }

    @Test
    fun `coalesce hook runs once per enqueue with session id`() = runBlocking {
        val hookedSessions = mutableListOf<String>()
        val port = RoutingTurnPreparationPort(
            reversePort = RecordingReversePort(),
            defaultPort = BackgroundTurnPreparationPort { BackgroundTurnPreparationResult.Unavailable },
            coalesceQueuedGeneration = { sessionId -> hookedSessions.add(sessionId) }
        )
        // RT 会话不触发钩子（连发合并下沉到 RT 协调器的智能插队），普通会话触发
        port.prepareAndEnqueue(request("session-rt-1", createdInApp = true))
        port.prepareAndEnqueue(request("session-rt-2", createdInApp = true))
        port.prepareAndEnqueue(request("session-normal-1", createdInApp = false))
        port.prepareAndEnqueue(request("session-normal-2", createdInApp = false))
        assertEquals(listOf("session-normal-1", "session-normal-2"), hookedSessions)
    }

    @Test
    fun `coalesce hook failure does not block enqueue`() = runBlocking {
        val port = RoutingTurnPreparationPort(
            reversePort = RecordingReversePort(),
            defaultPort = BackgroundTurnPreparationPort { BackgroundTurnPreparationResult.Unavailable },
            coalesceQueuedGeneration = { error("合并钩子故障") }
        )
        // 钩子异常被吞掉（runCatching），普通会话照常走 defaultPort 入队；
        // RT 会话则根本不跑钩子（上面的 once-per-enqueue 用例覆盖）。
        val result = port.prepareAndEnqueue(request("session-normal", createdInApp = false))
        assertEquals(BackgroundTurnPreparationResult.Unavailable, result)
    }
}
