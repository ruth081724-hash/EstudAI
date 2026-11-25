package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "VoiceAssistant"

class VoiceAssistantManager(context: Context) {
    private val appContext = context.applicationContext

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _speakingMessageId = MutableStateFlow<String?>(null)
    val speakingMessageId: StateFlow<String?> = _speakingMessageId.asStateFlow()

    private var tts: TextToSpeech? = null

    @Volatile
    private var ttsReady = false

    @Volatile
    private var activeUtteranceId: String? = null

    @Volatile
    private var pendingSpeak: Pair<String, String?>? = null

    private var speechRecognizer: SpeechRecognizer? = null

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val spanishLocale = Locale.forLanguageTag("es-ES")
                val res = tts?.setLanguage(spanishLocale)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.forLanguageTag("es"))
                }
                tts?.setSpeechRate(1.02f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        if (utteranceId == activeUtteranceId) {
                            _isSpeaking.value = true
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == activeUtteranceId) {
                            _isSpeaking.value = false
                            _speakingMessageId.value = null
                        }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        if (utteranceId == activeUtteranceId) {
                            _isSpeaking.value = false
                            _speakingMessageId.value = null
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (utteranceId == activeUtteranceId) {
                            _isSpeaking.value = false
                            _speakingMessageId.value = null
                        }
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        if (utteranceId == activeUtteranceId) {
                            _isSpeaking.value = false
                            _speakingMessageId.value = null
                        }
                    }
                })
                ttsReady = true
                val pending = pendingSpeak
                pendingSpeak = null
                if (pending != null) {
                    speak(pending.first, pending.second)
                }
            } else {
                Log.w(TAG, "TextToSpeech initialization failed with status=$status")
            }
        }
    }

    fun startListening(
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onError("El dictado por voz no está disponible en este dispositivo.")
            return
        }
        stopSpeaking()
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null

            val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
            speechRecognizer = recognizer

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
                    _partialTranscript.value = ""
                }

                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    _isListening.value = false
                }

                override fun onError(error: Int) {
                    _isListening.value = false
                    _partialTranscript.value = ""
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No te escuché con claridad. Inténtalo de nuevo."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Tiempo de espera agotado. Toca el micrófono para hablar."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Se necesita permiso de micrófono para hablar con EstudIA."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "El micrófono estaba ocupado. Intenta tocar de nuevo."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "Error de red en el reconocimiento de voz."
                        else -> "No se pudo iniciar el reconocimiento de voz (código $error)."
                    }
                    onError(msg)
                }

                override fun onResults(results: Bundle?) {
                    _isListening.value = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim().orEmpty()
                    _partialTranscript.value = ""
                    if (text.isNotEmpty()) {
                        onFinalResult(text)
                    } else {
                        onError("No se detectó texto en el audio.")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    _partialTranscript.value = partial
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting SpeechRecognizer", e)
            _isListening.value = false
            speechRecognizer = null
            onError("No se pudo activar el micrófono: ${e.localizedMessage ?: "error desconocido"}")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {
        } finally {
            _isListening.value = false
        }
    }

    fun speak(text: String, messageId: String? = null) {
        val cleanText = text
            .replace(Regex("[*#`>_]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(3500)
        if (cleanText.isBlank()) return
        if (!ttsReady) {
            pendingSpeak = cleanText to messageId
            return
        }
        val utteranceId = "${messageId ?: "estudia_tts"}_${System.nanoTime()}"
        activeUtteranceId = utteranceId
        _speakingMessageId.value = messageId
        _isSpeaking.value = true
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stopSpeaking() {
        pendingSpeak = null
        activeUtteranceId = null
        tts?.stop()
        _isSpeaking.value = false
        _speakingMessageId.value = null
    }

    fun release() {
        pendingSpeak = null
        activeUtteranceId = null
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (_: Exception) {
        }
    }
}
