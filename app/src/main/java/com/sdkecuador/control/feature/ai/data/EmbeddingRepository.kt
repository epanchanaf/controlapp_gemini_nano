package com.sdkecuador.control.feature.ai.data

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.embedding.*
import com.sdkecuador.control.feature.expenses.data.ExpenseEntity
import kotlinx.coroutines.flow.collect
import javax.inject.Inject
import kotlin.math.sqrt

class EmbeddingRepository @Inject constructor() {
    private suspend fun model(): EmbeddingModel = EmbeddingGeneration.getEmbeddingModels(EmbeddingOptions()).firstOrNull() ?: error("Embeddings no disponible en este dispositivo o cuenta")
    suspend fun status(): Int = model().let { try { it.checkStatus() } finally { it.close() } }
    suspend fun download(onProgress: (String) -> Unit) { model().let { try { it.download().collect { s -> onProgress(s.javaClass.simpleName) } } finally { it.close() } } }
    suspend fun search(query: String, expenses: List<ExpenseEntity>, limit: Int = 50): List<Pair<ExpenseEntity, Float>> {
        val model = model()
        try {
            check(model.checkStatus() == FeatureStatus.AVAILABLE)
            val signature = model.getModelSignature()
            val queryVector = model.generateEmbedding(EmbeddingRequest(Content(listOf(TextPart(query.take(1000)))), TaskConfig.RetrievalQuery())).embedding.vector
            return expenses.take(limit).map { expense ->
                val text = "${expense.description} ${expense.notes} ${expense.category}".take(1000)
                val result = model.generateEmbedding(EmbeddingRequest(Content(listOf(TextPart(text))), TaskConfig.RetrievalDocument()))
                expense to if (result.modelSignature == signature) cosine(queryVector, result.embedding.vector) else 0f
            }.sortedByDescending { it.second }
        } finally { model.close() }
    }
    fun possibleDuplicates(candidate: ExpenseEntity, expenses: List<ExpenseEntity>): List<ExpenseEntity> = expenses.filter {
        it.id != candidate.id && it.currency == candidate.currency && it.amountMinor == candidate.amountMinor && it.description.equals(candidate.description, ignoreCase = true)
    }
    private fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0.0; var aa = 0.0; var bb = 0.0
        for (i in 0 until minOf(a.size, b.size)) { dot += a[i] * b[i]; aa += a[i] * a[i]; bb += b[i] * b[i] }
        return if (aa == 0.0 || bb == 0.0) 0f else (dot / sqrt(aa * bb)).toFloat()
    }
}
