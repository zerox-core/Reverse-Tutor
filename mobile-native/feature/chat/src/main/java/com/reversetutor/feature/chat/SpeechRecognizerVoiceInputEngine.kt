package com.reversetutor.feature.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * 系统 SpeechRecognizer 引擎实现（1a：语音转文字进输入框）。
 * 所有识别回调由系统投递到主线程。用户主动停止后的错误回调（如未匹配）
 * 视为正常收尾，不再上报失败。
 */
class SpeechRecognizerVoiceInputEngine(
    context: Context,
    private val locale: Locale = Locale.getDefault()
) : VoiceInputEngine {

    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var listener: VoiceInputEngine.Listener? = null
    private var stopRequested = false

    override fun start(listener: VoiceInputEngine.Listener) {
        this.listener = listener
        stopRequested = false
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            listener.onUnavailable("这台设备没有可用的语音识别服务")
            return
        }
        releaseRecognizer()
        val next = SpeechRecognizer.createSpeechRecognizer(appContext)
        next.setRecognitionListener(Bridge())
        recognizer = next
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale.toLanguageTag())
        }
        next.startListening(intent)
    }

    override fun stop() {
        stopRequested = true
        recognizer?.stopListening()
    }

    override fun cancel() {
        stopRequested = true
        recognizer?.cancel()
    }

    override fun release() {
        releaseRecognizer()
        listener = null
    }

    private fun releaseRecognizer() {
        recognizer?.destroy()
        recognizer = null
    }

    private inner class Bridge : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            listener?.onReadyForSpeech()
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            val current = listener ?: return
            if (stopRequested) {
                // 用户主动停止后的错误（未匹配 / 超时等）按空结果收尾，不打扰。
                current.onFinalTranscript("")
            } else {
                current.onFailure(errorMessage(error))
            }
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            listener?.onFinalTranscript(text)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (text.isNotEmpty()) {
                listener?.onPartialTranscript(text)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    companion object {
        fun errorMessage(error: Int): String = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "录音失败，请重试"
            SpeechRecognizer.ERROR_CLIENT -> "语音识别出错，请重试"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "缺少麦克风权限"
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络异常，语音识别暂不可用"
            SpeechRecognizer.ERROR_NO_MATCH -> "没听清，请再说一次"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "语音识别忙，请稍候"
            SpeechRecognizer.ERROR_SERVER -> "语音服务异常，请稍后再试"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有听到声音，请再试一次"
            else -> "语音识别失败（错误码 $error）"
        }
    }
}
