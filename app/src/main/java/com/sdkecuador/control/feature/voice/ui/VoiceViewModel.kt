package com.sdkecuador.control.feature.voice.ui

import com.sdkecuador.control.feature.voice.data.VoiceRepository

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdkecuador.control.feature.expenses.data.ExpenseRules
import com.sdkecuador.control.feature.expenses.data.ExpenseEntity
import com.sdkecuador.control.feature.voice.data.VoiceDateRules
import com.sdkecuador.control.feature.trips.data.TravelRepository
import com.sdkecuador.control.feature.ai.data.NanoRepository
import com.sdkecuador.control.feature.ai.data.DedicatedAiRepository
import com.google.mlkit.genai.common.FeatureStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class VoiceUiState(val transcript: String = "", val speechStatus: String = "Comprobando", val nanoStatus: String = "Comprobando", val listening: Boolean = false, val loading: Boolean = false, val draft: ExtractedDraft? = null, val error: String? = null, val message: String? = null, val duplicateWarning: String? = null)
data class ExtractedDraft(val amount: String, val currency: String, val date: String, val category: String, val description: String, val notes: String, val level: String, val assessment: String)

@HiltViewModel class VoiceViewModel @Inject constructor(private val voice: VoiceRepository, private val nano: NanoRepository, private val travel: TravelRepository, private val dedicated: DedicatedAiRepository) : ViewModel() {
    private val _state = MutableStateFlow(VoiceUiState())
    val state = _state.asStateFlow()
    private var listenJob: Job? = null
    init { refresh() }
    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(speechStatus = runCatching { label(voice.status()) }.getOrDefault("Error"), nanoStatus = runCatching { label(nano.status()) }.getOrDefault("Error")) }
    }
    fun downloadSpeech() = viewModelScope.launch { runCatching { voice.download { status -> _state.update { it.copy(speechStatus = status) } } }.onFailure { fail(it) }; refresh() }
    fun downloadNano() = viewModelScope.launch { runCatching { nano.download { status -> _state.update { it.copy(nanoStatus = status) } } }.onFailure { fail(it) }; refresh() }
    fun listen() {
        listenJob?.cancel()
        listenJob = viewModelScope.launch {
            _state.update { it.copy(listening = true, error = null) }
            try { voice.listen().collect { text -> _state.update { it.copy(transcript = text) }; analyze() } }
            catch (e: Exception) { fail(e) }
            finally { _state.update { it.copy(listening = false) } }
        }
    }
    fun stop() { listenJob?.cancel() }
    fun dismissDraft() { stop(); _state.update { it.copy(transcript = "", draft = null, error = null, message = null, duplicateWarning = null) } }
    fun clearMessage() { _state.update { it.copy(message = null) } }
    fun transcript(value: String) { _state.update { it.copy(transcript = value) } }
    fun analyze() = viewModelScope.launch {
        val input = state.value.transcript.trim()
        if (input.isEmpty()) return@launch
        _state.update { it.copy(loading = true, error = null, draft = null) }
        try {
            val parsed = nano.extractExpense(input)
            val description = correctText(parsed.description)
            val notes = correctText(parsed.notes)
            val date = VoiceDateRules.resolve(input)
            val amountMinor = ExpenseRules.amountMinor(parsed.amount)
            val budgets = travel.budgets(currentTrip).first()
            val budget = budgets.find { it.category == parsed.category && it.currency == parsed.currency }?.limitMinor
            val spent = travel.expenses(currentTrip).first().filter { it.category == parsed.category && it.currency == parsed.currency }.sumOf { it.amountMinor }
            _state.update { it.copy(draft = ExtractedDraft(parsed.amount, parsed.currency, date, parsed.category, description, notes, if (amountMinor == null) "Sin clasificar" else ExpenseRules.level(amountMinor, budget), if (amountMinor == null) "Revisar" else ExpenseRules.assessment(parsed.category, amountMinor, budget?.minus(spent))), loading = false, error = null) }
        } catch (e: Exception) { fail(e); _state.update { it.copy(loading = false) } }
    }
    private suspend fun correctText(original: String): String = try {
        dedicated.proofreadVoice(original)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        original
    }
    var currentTrip: Long = 0L
    fun updateDraft(draft: ExtractedDraft) { _state.update { it.copy(draft = draft, duplicateWarning = null) }
        viewModelScope.launch { if (currentTrip == 0L) return@launch; val amount = ExpenseRules.amountMinor(draft.amount) ?: return@launch; val budget = travel.budgets(currentTrip).first().find { it.category == draft.category && it.currency == draft.currency }?.limitMinor; val spent = travel.expenses(currentTrip).first().filter { it.category == draft.category && it.currency == draft.currency }.sumOf { it.amountMinor }; _state.update { it.copy(draft = it.draft?.takeIf { current -> current.amount == draft.amount && current.category == draft.category && current.currency == draft.currency }?.copy(level = ExpenseRules.level(amount, budget), assessment = ExpenseRules.assessment(draft.category, amount, budget?.minus(spent))) ?: it.draft) } }
    }
    fun confirm(forceDuplicate: Boolean = false) = viewModelScope.launch {
        val draft = state.value.draft ?: return@launch
        val error = ExpenseRules.validate(draft.amount, draft.currency, draft.date, draft.category)
        if (error != null || currentTrip == 0L) { _state.update { it.copy(error = error ?: "Crea un viaje primero") }; return@launch }
        val amount = ExpenseRules.amountMinor(draft.amount)!!
        val duplicates = travel.expenses(currentTrip).first().filter { it.currency == draft.currency && it.amountMinor == amount && it.description.equals(draft.description, true) }
        if (duplicates.isNotEmpty() && !forceDuplicate) { _state.update { it.copy(duplicateWarning = "Posible duplicado: ${duplicates.size} gasto(s) con el mismo importe y concepto") }; return@launch }
        val budget = travel.budgets(currentTrip).first().find { it.category == draft.category && it.currency == draft.currency }?.limitMinor
        val spent = travel.expenses(currentTrip).first().filter { it.category == draft.category && it.currency == draft.currency }.sumOf { it.amountMinor }
        travel.saveExpense(ExpenseEntity(tripId = currentTrip, amountMinor = amount, currency = draft.currency, date = draft.date, category = draft.category, description = draft.description, notes = draft.notes, level = ExpenseRules.level(amount, budget), assessment = ExpenseRules.assessment(draft.category, amount, budget?.minus(spent))))
        _state.update { it.copy(draft = null, transcript = "", message = "Gasto guardado", duplicateWarning = null) }
    }
    private fun fail(e: Throwable) { _state.update { it.copy(error = e.message ?: "Error inesperado") } }
    private fun label(status: Int) = when(status) { FeatureStatus.AVAILABLE -> "Disponible"; FeatureStatus.DOWNLOADABLE -> "Descargable"; FeatureStatus.DOWNLOADING -> "Descargando"; else -> "No disponible" }
}
