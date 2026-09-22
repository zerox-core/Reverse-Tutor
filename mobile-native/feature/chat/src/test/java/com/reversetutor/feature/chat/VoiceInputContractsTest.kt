package com.reversetutor.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1a 语音输入状态机契约测试。 */
class VoiceInputContractsTest {

    private fun controller(): Pair<VoiceInputController, MutableList<VoiceInputState>> {
        val published = mutableListOf<VoiceInputState>()
        val controller = VoiceInputController { next -> published += next }
        return controller to published
    }

    @Test
    fun `start request opens a fresh session in Starting`() {
        val (controller, _) = controller()
        controller.onStartRequested()
        assertEquals(VoiceInputPhase.Starting, controller.state.phase)
        assertTrue(controller.state.active)
        assertEquals("", controller.state.transcript)
    }

    @Test
    fun `ready moves Starting to Listening`() {
        val (controller, _) = controller()
        controller.onStartRequested()
        controller.onReadyForSpeech()
        assertEquals(VoiceInputPhase.Listening, controller.state.phase)
    }

    @Test
    fun `ready without start is ignored`() {
        val (controller, published) = controller()
        controller.onReadyForSpeech()
        assertEquals(VoiceInputPhase.Idle, controller.state.phase)
        assertTrue(published.isEmpty())
    }

    @Test
    fun `partial replaces previous partial and feeds transcript`() {
        val (controller, _) = controller()
        controller.onStartRequested()
        controller.onReadyForSpeech()
        controller.onPartialTranscript("牛顿")
        controller.onPartialTranscript("牛顿第")
        assertEquals("牛顿第", controller.state.partialText)
        assertEquals("牛顿第", controller.state.transcript)
    }

    @Test
    fun `partial while idle is ignored`() {
        val (controller, published) = controller()
        controller.onPartialTranscript("幽灵文本")
        assertEquals("", controller.state.transcript)
        assertTrue(published.isEmpty())
    }

    @Test
    fun `final commits text, clears partial and returns to Idle`() {
        val (controller, _) = controller()
        controller.onStartRequested()
        controller.onReadyForSpeech()
        controller.onPartialTranscript("牛顿第一")
        controller.onFinalTranscript("牛顿第一定律")
        assertEquals(VoiceInputPhase.Idle, controller.state.phase)
        assertEquals("牛顿第一定律", controller.state.committedText)
        assertEquals("", controller.state.partialText)
        assertEquals("牛顿第一定律", controller.state.transcript)
        assertFalse(controller.state.active)
    }

    @Test
    fun `stop request from Listening enters Stopping and final still commits`() {
        val (controller, _) = controller()
        controller.onStartRequested()
        controller.onReadyForSpeech()
        controller.onStopRequested()
        assertEquals(VoiceInputPhase.Stopping, controller.state.phase)
        controller.onFinalTranscript("惯性")
        assertEquals(VoiceInputPhase.Idle, controller.state.phase)
        assertEquals("惯性", controller.state.committedText)
    }

    @Test
    fun `stop request while idle is a no-op`() {
        val (controller, published) = controller()
        controller.onStopRequested()
        assertEquals(VoiceInputPhase.Idle, controller.state.phase)
        assertTrue(published.isEmpty())
    }

    @Test
    fun `failure keeps committed text and surfaces message`() {
        val (controller, _) = controller()
        controller.onStartRequested()
        controller.onReadyForSpeech()
        controller.onPartialTranscript("半句")
        controller.onFailure("网络异常，语音识别暂不可用")
        assertEquals(VoiceInputPhase.Failed, controller.state.phase)
        assertEquals("网络异常，语音识别暂不可用", controller.state.errorMessage)
        assertEquals("", controller.state.partialText)
        assertFalse(controller.state.active)
    }

    @Test
    fun `failure before start is ignored`() {
        val (controller, published) = controller()
        controller.onFailure("不该出现")
        assertEquals(VoiceInputPhase.Idle, controller.state.phase)
        assertTrue(published.isEmpty())
    }

    @Test
    fun `unavailable surfaces message even from idle`() {
        val (controller, _) = controller()
        controller.onUnavailable("这台设备没有可用的语音识别服务")
        assertEquals(VoiceInputPhase.Failed, controller.state.phase)
        assertEquals("这台设备没有可用的语音识别服务", controller.state.errorMessage)
    }

    @Test
    fun `restart after failure clears error and text`() {
        val (controller, _) = controller()
        controller.onStartRequested()
        controller.onReadyForSpeech()
        controller.onFailure("没听清")
        controller.onStartRequested()
        assertEquals(VoiceInputPhase.Starting, controller.state.phase)
        assertNull(controller.state.errorMessage)
        assertEquals("", controller.state.committedText)
        assertEquals("", controller.state.partialText)
    }

    @Test
    fun `reset returns to pristine idle`() {
        val (controller, _) = controller()
        controller.onStartRequested()
        controller.onReadyForSpeech()
        controller.onFinalTranscript("提交段")
        controller.reset()
        assertEquals(VoiceInputState(), controller.state)
    }
}
