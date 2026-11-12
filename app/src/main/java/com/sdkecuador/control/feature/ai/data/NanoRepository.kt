package com.sdkecuador.control.feature.ai.data

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import android.graphics.Bitmap
import kotlinx.coroutines.flow.collect
import org.json.JSONObject
import javax.inject.Inject

class NanoRepository @Inject constructor() {
    suspend fun status(): Int { val model = Generation.getClient(); return try { model.checkStatus() } finally { model.close() } }
    suspend fun download(onProgress: (String) -> Unit) {
        run { val model = Generation.getClient(); try { model.download().collect { onProgress(it.javaClass.simpleName) } } finally { model.close() } }
    }
    suspend fun ask(prompt: String): String { val model = Generation.getClient(); return try {
        check(model.checkStatus() == FeatureStatus.AVAILABLE) { "Gemini Nano no está disponible. Comprueba o descarga el modelo." }
        model.generateContent(prompt).candidates.firstOrNull()?.text.orEmpty()
    } finally { model.close() } }
    suspend fun askImage(prompt: String, bitmap: Bitmap): String { val model = Generation.getClient(); return try {
        check(model.checkStatus() == FeatureStatus.AVAILABLE)
        model.generateContent(generateContentRequest(ImagePart(bitmap), TextPart(prompt)) {}).candidates.firstOrNull()?.text.orEmpty()
    } finally { model.close() } }
    suspend fun extractExpense(transcript: String): ExtractedExpense {
        val answer = ask("""Extrae UN gasto de viaje del texto. Devuelve solo JSON válido con keys amount, currency, date, category, description, notes. amount debe ser decimal sin símbolo; currency uno de USD, EUR, PEN, COP; date formato AAAA-MM-DD; category uno de food, transport, lodging, activities, shopping, other. Si un dato falta, usa cadena vacía. No inventes importes. Fecha actual del dispositivo: ${java.time.LocalDate.now()}. Para hoy usa esa fecha. Texto: ${transcript.take(1000)}""")
        val json = JSONObject(answer.substringAfter('{', "").substringBeforeLast('}', "").let { "{$it}" })
        return ExtractedExpense(json.optString("amount"), json.optString("currency"), json.optString("date"), json.optString("category"), json.optString("description"), json.optString("notes"))
    }
    suspend fun extractReceipt(bitmap: Bitmap, ocrText: String): ExtractedExpense {
        val prompt = """Analiza este comprobante de un gasto de viaje junto con su texto OCR. Devuelve SOLO JSON válido con keys amount, currency, date, category, description, notes. amount debe ser el TOTAL final a pagar, decimal sin símbolo; nunca uses subtotal, impuesto ni cambio. currency: USD, EUR, PEN o COP. date: AAAA-MM-DD. category: food, transport, lodging, activities, shopping u other. description: concepto breve en español según el comercio o servicio. Si un dato no se distingue, usa cadena vacía; no inventes cifras ni fechas. Texto OCR: ${ocrText.take(3000)}"""
        val answer = askImage(prompt, bitmap)
        val json = JSONObject(answer.substringAfter('{', "").substringBeforeLast('}', "").let { "{$it}" })
        return ExtractedExpense(json.optString("amount"), json.optString("currency"), json.optString("date"), json.optString("category"), json.optString("description"), json.optString("notes"))
    }
}

data class ExtractedExpense(val amount: String, val currency: String, val date: String, val category: String, val description: String, val notes: String)
