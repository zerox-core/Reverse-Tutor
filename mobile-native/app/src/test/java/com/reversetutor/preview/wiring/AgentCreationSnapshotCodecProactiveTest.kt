package com.reversetutor.preview.wiring

import com.reversetutor.feature.chat.AgentCreationPlannerState
import com.reversetutor.feature.chat.AgentCreationSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R87：创建快照编解码——主动问标记（要资料 / 路径确认）持久化 + 旧 11 字段快照兼容。 */
class AgentCreationSnapshotCodecProactiveTest {

    @Test
    fun codecRoundTripsProactiveFlags() {
        val snapshot = AgentCreationSnapshot(
            planner = AgentCreationPlannerState(
                rounds = 3,
                askedCounts = mapOf("学习目标" to 1),
                converged = false,
                documentAsked = true,
                pathConfirmAsked = true
            )
        )
        val decoded = AgentCreationSnapshotCodec.decode(AgentCreationSnapshotCodec.encode(snapshot))
        assertNotNull(decoded)
        assertTrue(decoded!!.planner.documentAsked)
        assertTrue(decoded.planner.pathConfirmAsked)
        assertEquals(3, decoded.planner.rounds)
    }

    @Test
    fun legacyElevenFieldSnapshotDecodesWithFlagsFalse() {
        val snapshot = AgentCreationSnapshot(
            planner = AgentCreationPlannerState(rounds = 2)
        )
        val encoded = AgentCreationSnapshotCodec.encode(snapshot)
        // 截掉末尾两个新字段（各为 3 字符 "1:0"），回到 11 字段旧格式。
        val legacy = encoded.dropLast(6)
        val decoded = AgentCreationSnapshotCodec.decode(legacy)
        assertNotNull(decoded)
        assertFalse(decoded!!.planner.documentAsked)
        assertFalse(decoded.planner.pathConfirmAsked)
        assertEquals(2, decoded.planner.rounds)
    }
}
