package com.reversetutor.feature.chat

/** 语音输入（1a：系统 SpeechRecognizer 语音转文字进输入框）。 */

enum class VoiceInputPhase {
    /** 未在录音。 */
    Idle,
    /** 已请求启动，等待引擎就绪。 */
    Starting,
    /** 收音中，识别结果实时流入输入框。 */
    Listening,
    /** 用户点了停止，等待最终识别结果。 */
    Stopping,
    /** 本次失败，errorMessage 有值；已提交的文本不丢。 */
    Failed
}

data class VoiceInputState(
    val phase: VoiceInputPhase = VoiceInputPhase.Idle,
    /** 本次语音会话已提交（最终结果）的累计文本。 */
    val committedText: String = "",
    /** 当前未提交的实时部分结果。 */
    val partialText: String = "",
    val errorMessage: String? = null
) {
    val active: Boolean
        get() = phase == VoiceInputPhase.Starting ||
            phase == VoiceInputPhase.Listening ||
            phase == VoiceInputPhase.Stopping

    /** 本次语音会话的累计转写 = 已提交段 + 实时段。 */
    val transcript: String get() = committedText + partialText
}

/** 语音识别引擎抽象：Android 实现见 [SpeechRecognizerVoiceInputEngine]，测试可注入假实现。 */
interface VoiceInputEngine {
    interface Listener {
        fun onReadyForSpeech()
        fun onPartialTranscript(text: String)
        fun onFinalTranscript(text: String)
        fun onFailure(message: String)
        fun onUnavailable(message: String)
    }

    fun start(listener: Listener)

    /** 停止收音并等待最终结果（最终结果仍会通过 Listener 回调）。 */
    fun stop()

    /** 立即取消，丢弃本次所有结果。 */
    fun cancel()

    fun release()
}

/**
 * 语音输入状态机：纯 Kotlin，不依赖 Android，便于单测。
 * 语义：每次点麦克风开启一段新的语音会话（committed/partial 清零）；
 * 最终结果可以分多段提交（committed 累加）；失败不清空已提交文本。
 */
class VoiceInputController(
    private val publish: (VoiceInputState) -> Unit
) : VoiceInputEngine.Listener {

    var state: VoiceInputState = VoiceInputState()
        private set

    /** 用户点下麦克风（空闲态）：开启新会话。 */
    fun onStartRequested() {
        state = VoiceInputState(phase = VoiceInputPhase.Starting)
        publish(state)
    }

    /** 用户在收音中再次点麦克风：进入停止等待。 */
    fun onStopRequested() {
        if (state.phase == VoiceInputPhase.Starting || state.phase == VoiceInputPhase.Listening) {
            state = state.copy(phase = VoiceInputPhase.Stopping)
            publish(state)
        }
    }

    /** 会话收尾后重置回初始空闲态。 */
    fun reset() {
        state = VoiceInputState()
        publish(state)
    }

    override fun onReadyForSpeech() {
        if (state.phase != VoiceInputPhase.Starting) return
        state = state.copy(phase = VoiceInputPhase.Listening)
        publish(state)
    }

    override fun onPartialTranscript(text: String) {
        if (!state.active) return
        state = state.copy(partialText = text)
        publish(state)
    }

    override fun onFinalTranscript(text: String) {
        if (!state.active) return
        state = state.copy(
            phase = VoiceInputPhase.Idle,
            committedText = state.committedText + text,
            partialText = ""
        )
        publish(state)
    }

    override fun onFailure(message: String) {
        if (!state.active) return
        state = state.copy(
            phase = VoiceInputPhase.Failed,
            partialText = "",
            errorMessage = message
        )
        publish(state)
    }

    override fun onUnavailable(message: String) {
        state = state.copy(
            phase = VoiceInputPhase.Failed,
            partialText = "",
            errorMessage = message
        )
        publish(state)
    }
}
