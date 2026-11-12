package com.sdkecuador.control.feature.voice.ui

import com.sdkecuador.control.feature.voice.data.VoiceRepository

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.sdkecuador.control.feature.ai.data.NanoRepository
import com.sdkecuador.control.feature.ai.data.AgentRepository
import com.sdkecuador.control.feature.ai.data.AgentVisual
import com.sdkecuador.control.feature.ai.data.isBalanceQuestion
import com.sdkecuador.control.feature.expenses.data.ExpenseRules
import com.sdkecuador.control.feature.voice.data.VoiceBudgetRules
import com.sdkecuador.control.feature.trips.data.TravelRepository
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TripVoiceMessage(val id: Long, val text: String, val fromUser: Boolean, val visual: AgentVisual? = null)

data class TripVoiceChatState(
    val messages: List<TripVoiceMessage> = emptyList(),
    val speechStatus: String = "Comprobando",
    val nanoStatus: String = "Comprobando",
    val listening: Boolean = false,
    val replying: Boolean = false,
    val error: String? = null,
    val pendingSpeechId: Long? = null,
    val pendingExpenseCommand: String? = null
)

@HiltViewModel
class TripVoiceChatViewModel @Inject constructor(
    private val voice: VoiceRepository,
    private val nano: NanoRepository,
    private val agent: AgentRepository,
    private val travel: TravelRepository
) : ViewModel() {
    private val _state = MutableStateFlow(TripVoiceChatState())
    val state = _state.asStateFlow()
    private var listenJob: Job? = null
    private var nextMessageId = 0L
    var currentTrip: Long = 0L

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        val speech = runCatching { label(voice.status()) }.getOrDefault("No disponible")
        val model = runCatching { label(nano.status()) }.getOrDefault("No disponible")
        _state.update { it.copy(speechStatus = speech, nanoStatus = model) }
    }

    fun downloadSpeech() = viewModelScope.launch {
        _state.update { it.copy(speechStatus = "Descargando", error = null) }
        runCatching { voice.download { _state.update { current -> current.copy(speechStatus = "Descargando") } } }
            .onFailure { showError(it.message ?: "No se pudo descargar el modelo de voz") }
        refresh()
    }

    fun downloadNano() = viewModelScope.launch {
        _state.update { it.copy(nanoStatus = "Descargando", error = null) }
        runCatching { nano.download { _state.update { current -> current.copy(nanoStatus = "Descargando") } } }
            .onFailure { showError(it.message ?: "No se pudo descargar Gemini Nano") }
        refresh()
    }

    fun listen() {
        if (state.value.listening || state.value.replying) return
        if (state.value.speechStatus != "Disponible" || state.value.nanoStatus != "Disponible") {
            showError("Activa la voz y Gemini Nano para conversar")
            return
        }
        listenJob = viewModelScope.launch {
            _state.update { it.copy(listening = true, error = null) }
            try {
                var retryDelayMs = 500L
                while (true) {
                    val message = try {
                        voice.listen().firstOrNull()?.trim()
                    } catch (e: GenAiException) {
                        if (e.errorCode == GenAiException.ErrorCode.REQUEST_TOO_SMALL) null else throw e
                    }
                    if (!message.isNullOrEmpty()) {
                        replyTo(message)
                        break
                    }
                    delay(retryDelayMs)
                    retryDelayMs = (retryDelayMs * 2).coerceAtMost(2_000L)
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) showError(e.message ?: "No se pudo reconocer la voz")
            } finally {
                _state.update { it.copy(listening = false) }
            }
        }
    }

    fun stopListening() { listenJob?.cancel() }
    fun showError(message: String) { _state.update { it.copy(error = message) } }
    fun markSpoken(id: Long) { _state.update { if (it.pendingSpeechId == id) it.copy(pendingSpeechId = null) else it } }
    fun consumeExpenseCommand() { _state.update { it.copy(pendingExpenseCommand = null) } }

    private suspend fun replyTo(message: String) {
        val userMessage = TripVoiceMessage(++nextMessageId, message, true)
        val history = state.value.messages.takeLast(8) + userMessage
        _state.update { it.copy(messages = it.messages + userMessage, listening = false, replying = true) }
        try {
            if (isBalanceQuestion(message)) {
                val answer = agent.replyForUi(message)
                val assistantMessage = TripVoiceMessage(++nextMessageId, answer.text, false, answer.visual)
                _state.update { it.copy(messages = it.messages + assistantMessage, pendingSpeechId = assistantMessage.id, error = null) }
                return
            }
            val conversation = history.joinToString("\n") {
                "${if (it.fromUser) "Usuario" else "Asistente"}: ${it.text.take(500)}"
            }
            val raw = nano.ask(
                "Eres un asistente de viajes. " +
                    "Responde SOLO un objeto JSON, sin Markdown, con action (create_trip, add_expense, budget_query o reply), " +
                    "name, currency, budget, startDate, endDate y reply. " +
                    "Usa create_trip únicamente si el ÚLTIMO mensaje del usuario ordena crear o registrar un viaje. " +
                    "Usa add_expense únicamente si el ÚLTIMO mensaje del usuario ordena registrar un gasto. " +
                    "Usa budget_query si el ÚLTIMO mensaje pregunta por presupuesto, saldo restante o gastos registrados de un viaje. " +
                    "Para create_trip, extrae el nombre del viaje; currency debe ser USD, EUR, PEN o COP; " +
                    "budget es decimal positivo sin símbolo o cadena vacía; fechas en AAAA-MM-DD o cadena vacía. " +
                    "No inventes nombre, presupuesto, moneda ni fechas. Si no hay moneda explícita usa USD. " +
                    "Si no hay fecha de inicio, deja startDate vacío. " +
                    "Para otros mensajes usa reply con una respuesta breve en español y deja los campos vacíos. " +
                    "Hoy es ${LocalDate.now()}. Conversación:\n$conversation\nJSON:"
            )
            val command = runCatching { JSONObject(raw.substringAfter('{').substringBeforeLast('}').let { "{$it}" }) }.getOrNull()
            var visual: AgentVisual? = null
            val answer = if (command?.optString("action") == "add_expense") {
                if (currentTrip == 0L) "Selecciona primero un viaje para agregar el gasto."
                else {
                    _state.update { it.copy(pendingExpenseCommand = message) }
                    "Preparé el gasto para el viaje seleccionado. Revisa los datos y confírmalo antes de guardarlo."
                }
            } else if (command?.optString("action") == "budget_query") {
                agent.replyForUi(message).also { visual = it.visual }.text.ifBlank { "No pude consultar el presupuesto. Inténtalo de nuevo." }
            } else if (command?.optString("action") == "create_trip") {
                val name = command.optString("name").trim()
                val currency = command.optString("currency").trim().uppercase().ifEmpty { "USD" }
                val budgetMentioned = VoiceBudgetRules.mentioned(message)
                val budget = if (budgetMentioned) VoiceBudgetRules.amountMinor(message) else null
                val startText = command.optString("startDate").trim()
                val endText = command.optString("endDate").trim()
                val start = if (startText.isEmpty()) LocalDate.now() else runCatching { LocalDate.parse(startText) }.getOrNull()
                val end = endText.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                when {
                    name.isBlank() -> "No pude identificar el nombre del viaje. Dime cómo quieres llamarlo."
                    name.length > 100 -> "El nombre del viaje es demasiado largo. Indica uno más corto."
                    currency !in ExpenseRules.currencies -> "La moneda no es válida. Usa USD, EUR, PEN o COP."
                    budgetMentioned && budget == null -> "No pude interpretar el presupuesto en la transcripción. Repítelo con un número, por ejemplo: presupuesto de 500 dólares."
                    start == null || (endText.isNotEmpty() && end == null) -> "No pude interpretar las fechas. Indícalas de nuevo."
                    end != null && end.isBefore(start) -> "La fecha de fin debe ser posterior a la fecha de inicio."
                    else -> {
                        travel.createTrip(name, start.toString(), currency, budget, end?.toString())
                        "He creado el viaje $name en $currency${if (budget != null) " con presupuesto de ${"%.2f".format(Locale.US, budget / 100.0)}" else ""}."
                    }
                }
            } else command?.optString("reply")?.trim().orEmpty()
                .ifEmpty { "No pude interpretar la orden. Inténtalo de nuevo." }
            val assistantMessage = TripVoiceMessage(++nextMessageId, answer, false, visual)
            _state.update {
                it.copy(messages = it.messages + assistantMessage, pendingSpeechId = assistantMessage.id, error = null)
            }
        } catch (e: Exception) {
            showError(e.message ?: "No se pudo obtener una respuesta de Gemini Nano")
        } finally {
            _state.update { it.copy(replying = false) }
        }
    }

    private fun label(status: Int) = when (status) {
        FeatureStatus.AVAILABLE -> "Disponible"
        FeatureStatus.DOWNLOADABLE -> "Descargable"
        FeatureStatus.DOWNLOADING -> "Descargando"
        else -> "No disponible"
    }

    override fun onCleared() { agent.close(); super.onCleared() }
}
