package com.sdkecuador.control.feature.voice.data

import android.os.Build
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.audio.AudioSource
import com.google.mlkit.genai.speechrecognition.SpeechRecognition
import com.google.mlkit.genai.speechrecognition.SpeechRecognizer
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerResponse
import com.google.mlkit.genai.speechrecognition.speechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.speechRecognizerRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import java.util.Locale
import javax.inject.Inject

class VoiceRepository @Inject constructor() {
    private fun client(mode: Int): SpeechRecognizer = SpeechRecognition.getClient(speechRecognizerOptions { locale = Locale.forLanguageTag("es-ES"); preferredMode = mode })
    private suspend fun supportedClient(): SpeechRecognizer {
        val advanced = client(SpeechRecognizerOptions.Mode.MODE_ADVANCED)
        val advancedStatus = runCatching { advanced.checkStatus() }.getOrDefault(FeatureStatus.UNAVAILABLE)
        if (advancedStatus != FeatureStatus.UNAVAILABLE) return advanced
        advanced.close()
        return client(SpeechRecognizerOptions.Mode.MODE_BASIC)
    }
    suspend fun status(): Int = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) FeatureStatus.UNAVAILABLE else supportedClient().use { it.checkStatus() }
    suspend fun download(onProgress: (String) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) error("La voz requiere Android 12 o posterior")
        supportedClient().use { recognizer -> recognizer.download().collect { onProgress(it.javaClass.simpleName) } }
    }
    fun listen(): Flow<String> = flow {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) error("La voz requiere Android 12 o posterior")
        supportedClient().use { recognizer ->
            check(recognizer.checkStatus() == FeatureStatus.AVAILABLE) { "El modelo de voz no está disponible" }
            val source = AudioSource.fromMic()
            try {
                val request = speechRecognizerRequest { audioSource = source }
                when (val response = recognizer.startRecognition(request).firstOrNull {
                    (it is SpeechRecognizerResponse.FinalTextResponse && it.text.isNotBlank()) ||
                        it is SpeechRecognizerResponse.ErrorResponse ||
                        it is SpeechRecognizerResponse.CompletedResponse
                }) {
                    is SpeechRecognizerResponse.FinalTextResponse -> emit(response.text)
                    is SpeechRecognizerResponse.ErrorResponse -> throw response.e
                    else -> Unit
                }
            } finally { source.close() }
        }
    }
}
