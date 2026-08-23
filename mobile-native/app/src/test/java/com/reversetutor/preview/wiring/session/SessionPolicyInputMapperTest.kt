package com.reversetutor.preview.wiring.session

import com.reversetutor.core.domain.CorrectionPersistenceWire
import com.reversetutor.core.domain.SessionModeWire
import com.reversetutor.feature.chat.NewSessionConfiguration
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionPolicyInputMapperTest {

    @Test
    fun configuration_always_maps_to_study() {
        val input = NewSessionConfiguration(
            title = "\u4ee3\u6570",
            goal = "\u56e0\u5f0f\u5206\u89e3",
            dialogueStrategy = "\u966a\u4f34\u5f0f\u8bb2\u89e3",
            probingIntensity = 5,
            correctionPersistence = "\u4e25\u683c"
        ).toSessionPolicyInput("\u6211\u4e0d\u4f1a")
        assertEquals(SessionModeWire.STUDY, input.mode)
        assertEquals(5, input.settings.probingIntensity)
        assertEquals(CorrectionPersistenceWire.PERSISTENT, input.settings.correctionPersistence)
    }

    @Test
    fun null_snapshot_defaults_to_balanced() {
        val input = null.toSessionPolicyInput("\u95ee\u9898")
        assertEquals(SessionModeWire.STUDY, input.mode)
        assertEquals(3, input.settings.probingIntensity)
        assertEquals(CorrectionPersistenceWire.BALANCED, input.settings.correctionPersistence)
    }

    @Test
    fun goal_blanks_fall_back_to_title() {
        val input = NewSessionConfiguration(
            title = "\u51e0\u4f55",
            goal = ""
        ).toSessionPolicyInput("\u4ec0\u4e48\u662f\u89d2")
        assertEquals("\u51e0\u4f55", input.knowledgePoint)
    }
}
