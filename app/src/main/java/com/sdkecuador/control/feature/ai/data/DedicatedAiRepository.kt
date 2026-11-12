package com.sdkecuador.control.feature.ai.data

import android.content.Context
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.proofreading.Proofreading
import com.google.mlkit.genai.proofreading.ProofreaderOptions
import com.google.mlkit.genai.proofreading.ProofreadingRequest
import com.google.mlkit.genai.rewriting.Rewriting
import com.google.mlkit.genai.rewriting.RewriterOptions
import com.google.mlkit.genai.rewriting.RewritingRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DedicatedAiRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private fun callback(onProgress: (String) -> Unit) = object : DownloadCallback {
        override fun onDownloadStarted(bytesToDownload: Long) { onProgress("Descargando") }
        override fun onDownloadProgress(totalBytesDownloaded: Long) { onProgress("Descargados $totalBytesDownloaded bytes") }
        override fun onDownloadCompleted() { onProgress("Disponible") }
        override fun onDownloadFailed(e: GenAiException) { onProgress("Error: ${e.message}") }
    }
    private fun proofreader() = Proofreading.getClient(ProofreaderOptions.builder(context).setInputType(ProofreaderOptions.InputType.KEYBOARD).setLanguage(ProofreaderOptions.Language.SPANISH).build())
    private fun voiceProofreader() = Proofreading.getClient(ProofreaderOptions.builder(context).setInputType(ProofreaderOptions.InputType.VOICE).setLanguage(ProofreaderOptions.Language.SPANISH).build())
    private fun rewriter() = Rewriting.getClient(RewriterOptions.builder(context).setOutputType(RewriterOptions.OutputType.REPHRASE).setLanguage(RewriterOptions.Language.SPANISH).build())
    suspend fun status(feature: String): Int = withContext(Dispatchers.IO) {
        when(feature) {
            "Proofreading" -> proofreader().let { try { it.checkFeatureStatus().get() } finally { it.close() } }
            "Rewriting" -> rewriter().let { try { it.checkFeatureStatus().get() } finally { it.close() } }
            else -> FeatureStatus.UNAVAILABLE
        }
    }
    suspend fun download(feature: String, onProgress: (String) -> Unit) = withContext(Dispatchers.IO) {
        when(feature) {
            "Proofreading" -> proofreader().let { try { it.downloadFeature(callback(onProgress)).get() } finally { it.close() } }
            "Rewriting" -> rewriter().let { try { it.downloadFeature(callback(onProgress)).get() } finally { it.close() } }
        }
    }
    suspend fun runText(feature: String, input: String): String = withContext(Dispatchers.IO) {
        when(feature) {
            "Proofreading" -> proofreader().let { try { check(it.checkFeatureStatus().get() == FeatureStatus.AVAILABLE); it.runInference(ProofreadingRequest.builder(input).build()).get().results.joinToString("\n") { suggestion -> suggestion.text } } finally { it.close() } }
            "Rewriting" -> rewriter().let { try { check(it.checkFeatureStatus().get() == FeatureStatus.AVAILABLE); it.runInference(RewritingRequest.builder(input).build()).get().results.joinToString("\n") { suggestion -> suggestion.text } } finally { it.close() } }
            else -> error("Capacidad de texto no disponible")
        }
    }

    suspend fun proofreadVoice(input: String): String = withContext(Dispatchers.IO) {
        val original = input.trim()
        if (original.isEmpty()) return@withContext original
        voiceProofreader().let { client ->
            try {
                when (client.checkFeatureStatus().get()) {
                    FeatureStatus.AVAILABLE -> Unit
                    FeatureStatus.DOWNLOADABLE -> client.downloadFeature(callback { }).get()
                    else -> return@withContext original
                }
                client.runInference(ProofreadingRequest.builder(original).build()).get()
                    .results.firstOrNull()?.text?.trim()?.takeIf { it.isNotEmpty() } ?: original
            } finally { client.close() }
        }
    }
}
