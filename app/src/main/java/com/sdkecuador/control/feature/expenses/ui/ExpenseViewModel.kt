package com.sdkecuador.control.feature.expenses.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.genai.common.FeatureStatus
import com.sdkecuador.control.feature.expenses.data.ExpenseRules
import com.sdkecuador.control.feature.expenses.data.ReceiptAmountRules
import com.sdkecuador.control.feature.expenses.data.ReceiptAmountProposal
import com.sdkecuador.control.feature.expenses.data.ExpenseEntity
import com.sdkecuador.control.feature.trips.data.TripEntity
import com.sdkecuador.control.feature.trips.data.TravelRepository
import com.sdkecuador.control.feature.ai.data.EmbeddingRepository
import com.sdkecuador.control.feature.ai.data.DedicatedAiRepository
import com.sdkecuador.control.feature.ai.data.NanoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

data class ExpenseForm(val id: Long = 0, val amount: String = "", val currency: String = "USD", val date: String = LocalDate.now().toString(), val category: String = "food", val description: String = "", val notes: String = "", val receiptUri: String? = null)
data class TripEditForm(val id: Long, val name: String, val startDate: String, val endDate: String, val baseCurrency: String, val budgetAmount: String)
data class TextImprovement(val field: String, val original: String, val proposed: String)
data class ExpenseUiState(val trips: List<TripEntity> = emptyList(), val tripId: Long = 0, val expenses: List<ExpenseEntity> = emptyList(), val form: ExpenseForm = ExpenseForm(), val preview: Boolean = false, val saving: Boolean = false, val tripEdit: TripEditForm? = null, val tripEditSaving: Boolean = false, val tripEditError: String? = null, val deleteCandidate: ExpenseEntity? = null, val error: String? = null, val ocrText: String? = null, val ocrProposal: ReceiptAmountProposal? = null, val receiptBusy: Boolean = false, val receiptMessage: String? = null, val receiptNanoStatus: String? = null, val receiptEntry: Boolean = false, val improvement: TextImprovement? = null, val improvementBusy: Boolean = false, val improvementError: String? = null, val improvementDownloadFeature: String? = null, val improvementPendingField: String? = null, val searchQuery: String = "", val searchResults: List<ExpenseEntity> = emptyList(), val searchPerformed: Boolean = false, val searchBusy: Boolean = false, val searchStatus: String? = null, val searchError: String? = null, val globalSearchQuery: String = "", val globalSearchResults: List<ExpenseEntity> = emptyList(), val globalSearchPerformed: Boolean = false, val globalSearchBusy: Boolean = false, val globalSearchStatus: String? = null, val globalSearchError: String? = null)

@HiltViewModel class ExpenseViewModel @Inject constructor(private val repo: TravelRepository, private val embeddings: EmbeddingRepository, private val nano: NanoRepository, private val dedicated: DedicatedAiRepository, @ApplicationContext private val context: Context) : ViewModel() {
    private val _state = MutableStateFlow(ExpenseUiState())
    val state = _state.asStateFlow()
    private var receiptGeneration = 0L
    private var receiptJob: Job? = null
    init {
        viewModelScope.launch { repo.seed(); repo.trips().collect { trips -> _state.update { s -> s.copy(trips = trips, tripId = s.tripId.takeIf { id -> trips.any { it.id == id } } ?: trips.firstOrNull()?.id ?: 0) } } }
        viewModelScope.launch { state.map { it.tripId }.distinctUntilChanged().flatMapLatest { id ->
            if (id == 0L) flowOf(emptyList<ExpenseEntity>())
            else combine(repo.expenses(id), repo.budgets(id)) { expenses, budgets -> ExpenseRules.currentAssessments(expenses, budgets) }
        }.collect { expenses -> _state.update { current -> current.copy(expenses = expenses, searchResults = current.searchResults.mapNotNull { result -> expenses.find { it.id == result.id } }) } } }
        viewModelScope.launch { repo.allExpenses().collect { expenses -> _state.update { current -> current.copy(globalSearchResults = current.globalSearchResults.mapNotNull { result -> expenses.find { it.id == result.id } }) } } }
    }
    fun globalSearchQuery(value: String) { _state.update { it.copy(globalSearchQuery = value, globalSearchResults = emptyList(), globalSearchPerformed = false, globalSearchError = null) } }
    fun clearGlobalSearch() { globalSearchQuery("") }
    fun searchAllExpenses() = viewModelScope.launch {
        val query = state.value.globalSearchQuery.trim()
        if (query.isEmpty()) return@launch
        _state.update { it.copy(globalSearchBusy = true, globalSearchError = null, globalSearchPerformed = false) }
        try {
            val status = embeddings.status()
            if (status != FeatureStatus.AVAILABLE) {
                _state.update { it.copy(globalSearchStatus = if (status == FeatureStatus.DOWNLOADABLE) "Descargable" else "No disponible", globalSearchError = if (status == FeatureStatus.DOWNLOADABLE) "Activa la búsqueda para usarla en este dispositivo" else "La búsqueda por significado no está disponible en este dispositivo") }
                return@launch
            }
            val matches = embeddings.search(query, repo.allExpenses().first(), Int.MAX_VALUE).take(10).map { it.first }
            _state.update { if (it.globalSearchQuery.trim() == query) it.copy(globalSearchResults = matches, globalSearchPerformed = true, globalSearchStatus = "Disponible") else it }
        } catch (e: Exception) { _state.update { it.copy(globalSearchError = e.message ?: "No se pudo buscar") } }
        finally { _state.update { it.copy(globalSearchBusy = false) } }
    }
    fun downloadGlobalSearchModel() = viewModelScope.launch {
        _state.update { it.copy(globalSearchBusy = true, globalSearchError = null) }
        try {
            embeddings.download { _state.update { current -> current.copy(globalSearchStatus = "Descargando") } }
            _state.update { it.copy(globalSearchStatus = if (embeddings.status() == FeatureStatus.AVAILABLE) "Disponible" else "No disponible") }
        } catch (e: Exception) { _state.update { it.copy(globalSearchError = e.message ?: "No se pudo descargar") } }
        finally { _state.update { it.copy(globalSearchBusy = false) } }
    }
    fun selectTrip(id: Long) { _state.update { if (it.tripId == id) it else it.copy(tripId = id, expenses = emptyList(), tripEdit = null, tripEditError = null, searchQuery = "", searchResults = emptyList(), searchPerformed = false, searchError = null, searchStatus = null) } }
    fun searchQuery(value: String) { _state.update { it.copy(searchQuery = value, searchResults = emptyList(), searchPerformed = false, searchError = null) } }
    fun clearSearch() { searchQuery("") }
    fun searchExpenses() = viewModelScope.launch {
        val query = state.value.searchQuery.trim()
        val tripId = state.value.tripId
        if (query.isEmpty() || tripId == 0L) return@launch
        _state.update { it.copy(searchBusy = true, searchError = null, searchPerformed = false) }
        try {
            val status = embeddings.status()
            if (status != FeatureStatus.AVAILABLE) {
                _state.update { it.copy(searchStatus = if (status == FeatureStatus.DOWNLOADABLE) "Descargable" else "No disponible", searchError = if (status == FeatureStatus.DOWNLOADABLE) "Descarga la búsqueda para usarla en este dispositivo" else "La búsqueda por significado no está disponible en este dispositivo") }
                return@launch
            }
            val expenses = repo.expenses(tripId).first()
            val matches = embeddings.search(query, expenses).take(10).map { it.first }
            _state.update { if (it.tripId == tripId && it.searchQuery.trim() == query) it.copy(searchResults = matches, searchPerformed = true, searchStatus = "Disponible") else it }
        } catch (e: Exception) { _state.update { it.copy(searchError = e.message ?: "No se pudo buscar") } }
        finally { _state.update { it.copy(searchBusy = false) } }
    }
    fun downloadSearchModel() = viewModelScope.launch {
        _state.update { it.copy(searchBusy = true, searchError = null) }
        try {
            embeddings.download { _state.update { state -> state.copy(searchStatus = "Descargando") } }
            _state.update { it.copy(searchStatus = if (embeddings.status() == FeatureStatus.AVAILABLE) "Disponible" else "No disponible") }
        } catch (e: Exception) { _state.update { it.copy(searchError = e.message ?: "No se pudo descargar") } }
        finally { _state.update { it.copy(searchBusy = false) } }
    }
    fun createTrip(name: String, currency: String, budgetMinor: Long?) = viewModelScope.launch {
        if (name.isBlank()) { _state.update { it.copy(error = "Escribe el nombre del viaje") }; return@launch }
        if (currency !in ExpenseRules.currencies || (budgetMinor != null && budgetMinor <= 0)) { _state.update { it.copy(error = "Presupuesto inválido") }; return@launch }
        val id = repo.createTrip(name, LocalDate.now().toString(), currency, budgetMinor)
        _state.update { it.copy(tripId = id, expenses = emptyList()) }
    }
    fun beginEditTrip() {
        _state.update { current ->
            val trip = current.trips.find { it.id == current.tripId } ?: return@update current
            current.copy(
                tripEdit = TripEditForm(
                    trip.id, trip.name, trip.startDate, trip.endDate.orEmpty(), trip.baseCurrency,
                    trip.budgetMinor?.let { "%.2f".format(java.util.Locale.US, it / 100.0) }.orEmpty()
                ),
                tripEditError = null
            )
        }
    }
    fun changeTripEdit(form: TripEditForm) { _state.update { if (it.tripEdit?.id == form.id && !it.tripEditSaving) it.copy(tripEdit = form, tripEditError = null) else it } }
    fun cancelTripEdit() { _state.update { if (it.tripEditSaving) it else it.copy(tripEdit = null, tripEditError = null) } }
    fun saveTripEdit() = viewModelScope.launch {
        val form = state.value.tripEdit ?: return@launch
        if (state.value.tripEditSaving) return@launch
        val start = runCatching { LocalDate.parse(form.startDate) }.getOrNull()
        val end = form.endDate.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val budget = form.budgetAmount.takeIf { it.isNotBlank() }?.let(ExpenseRules::amountMinor)
        val error = when {
            form.name.isBlank() -> "Escribe el nombre del viaje"
            start == null -> "Selecciona una fecha de inicio válida"
            form.endDate.isNotBlank() && end == null -> "Selecciona una fecha de fin válida"
            end != null && end.isBefore(start) -> "La fecha de fin debe ser igual o posterior al inicio"
            form.baseCurrency !in ExpenseRules.currencies -> "Selecciona una moneda válida"
            form.budgetAmount.isNotBlank() && budget == null -> "Usa un presupuesto positivo con máximo dos decimales"
            else -> null
        }
        if (error != null) { _state.update { it.copy(tripEditError = error) }; return@launch }
        _state.update { it.copy(tripEditSaving = true, tripEditError = null) }
        try {
            val saved = repo.updateTrip(form.id, form.name, form.startDate, form.endDate.takeIf { it.isNotBlank() }, form.baseCurrency, budget)
            _state.update { if (it.tripEdit?.id == form.id) it.copy(tripEdit = if (saved) null else it.tripEdit, tripEditError = if (saved) null else "El viaje ya no existe") else it }
        } catch (e: Exception) {
            _state.update { if (it.tripEdit?.id == form.id) it.copy(tripEditError = e.message ?: "No se pudo guardar el viaje") else it }
        } finally { _state.update { it.copy(tripEditSaving = false) } }
    }
    fun form(value: ExpenseForm) { _state.update { it.copy(form = value, error = null) } }
    fun improveText(field: String, feature: String) = viewModelScope.launch { runImprovement(field, feature) }
    private suspend fun runImprovement(field: String, feature: String) {
        if (field !in listOf("description", "notes") || feature !in listOf("Proofreading", "Rewriting")) return
        val original = if (field == "description") state.value.form.description else state.value.form.notes
        if (original.isBlank()) { _state.update { it.copy(improvementError = "Escribe texto antes de mejorarlo") }; return }
        _state.update { it.copy(improvementBusy = true, improvementError = null, improvement = null, improvementPendingField = field, improvementDownloadFeature = null) }
        try {
            val status = dedicated.status(feature)
            if (status != FeatureStatus.AVAILABLE) {
                _state.update { it.copy(improvementDownloadFeature = if (status == FeatureStatus.DOWNLOADABLE) feature else null, improvementError = if (status == FeatureStatus.DOWNLOADABLE) "Activa el modelo para mejorar el texto" else "Esta mejora no está disponible en el dispositivo") }
                return
            }
            val proposed = dedicated.runText(feature, original).lineSequence().firstOrNull()?.trim().orEmpty()
            if (proposed.isBlank()) error("No se obtuvo una sugerencia")
            _state.update { current -> current.copy(improvement = TextImprovement(field, original, proposed), improvementPendingField = null) }
        } catch (e: Exception) { _state.update { it.copy(improvementError = e.message ?: "No se pudo mejorar el texto") } }
        finally { _state.update { it.copy(improvementBusy = false) } }
    }
    fun downloadImprovementModel() = viewModelScope.launch {
        val feature = state.value.improvementDownloadFeature ?: return@launch
        val field = state.value.improvementPendingField ?: return@launch
        _state.update { it.copy(improvementBusy = true, improvementError = null) }
        try {
            dedicated.download(feature) { }
            runImprovement(field, feature)
        } catch (e: Exception) { _state.update { it.copy(improvementError = e.message ?: "No se pudo activar el modelo") } }
        finally { _state.update { it.copy(improvementBusy = false) } }
    }
    fun dismissImprovement() { _state.update { it.copy(improvement = null) } }
    fun acceptImprovement() {
        _state.update { current ->
            val suggestion = current.improvement ?: return@update current
            val existing = if (suggestion.field == "description") current.form.description else current.form.notes
            if (existing != suggestion.original) current.copy(improvement = null, improvementError = "El texto cambió. Vuelve a solicitar la mejora.")
            else current.copy(form = if (suggestion.field == "description") current.form.copy(description = suggestion.proposed) else current.form.copy(notes = suggestion.proposed), improvement = null)
        }
    }
    fun edit(expense: ExpenseEntity) { cancelReceiptAnalysis(); _state.update { it.copy(form = ExpenseForm(expense.id, "%.2f".format(java.util.Locale.US, expense.amountMinor / 100.0), expense.currency, expense.date, expense.category, expense.description, expense.notes, expense.receiptUri), preview = false, ocrText = null, ocrProposal = null, receiptEntry = false, receiptMessage = null, receiptNanoStatus = null, improvement = null, improvementError = null, improvementDownloadFeature = null, improvementPendingField = null) } }
    fun new() { cancelReceiptAnalysis(); _state.update { it.copy(form = ExpenseForm(), preview = false, ocrText = null, ocrProposal = null, receiptEntry = false, receiptMessage = null, receiptNanoStatus = null, improvement = null, improvementError = null, improvementDownloadFeature = null, improvementPendingField = null) } }
    fun cancelReceiptAnalysis() {
        receiptGeneration++
        receiptJob?.cancel()
        receiptJob = null
        _state.update { it.copy(receiptBusy = false) }
    }
    fun receiptForNewExpense(uri: Uri) {
        new()
        _state.update { it.copy(receiptEntry = true) }
        analyzeNewReceipt(uri, receiptGeneration)
    }
    fun downloadReceiptNano() = viewModelScope.launch {
        _state.update { it.copy(receiptBusy = true, receiptMessage = null) }
        try {
            nano.download { _state.update { current -> current.copy(receiptNanoStatus = "Descargando") } }
            _state.update { it.copy(receiptNanoStatus = if (nano.status() == FeatureStatus.AVAILABLE) "Disponible" else "No disponible") }
            if (state.value.receiptNanoStatus == "Disponible") analyzeReceipt()
        } catch (e: Exception) { _state.update { it.copy(receiptMessage = e.message ?: "No se pudo descargar Gemini Nano") } }
        finally { _state.update { it.copy(receiptBusy = false) } }
    }
    fun analyzeReceiptWithNano() = viewModelScope.launch { analyzeReceipt() }
    private suspend fun analyzeReceipt() {
        val snapshot = state.value
        val path = snapshot.form.receiptUri ?: return
        val ocr = snapshot.ocrText ?: return
        _state.update { it.copy(receiptBusy = true, receiptMessage = null) }
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            val sample = (maxOf(options.outWidth, options.outHeight) / 1600).coerceAtLeast(1)
            val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("No se pudo abrir la imagen")
            val parsed = try { nano.extractReceipt(bitmap, ocr) } finally { bitmap.recycle() }
            val ocrAmount = snapshot.ocrProposal?.amount
            val nanoAmount = parsed.amount.takeIf { ExpenseRules.amountMinor(it) != null }
            val conflict = ocrAmount != null && nanoAmount != null && ExpenseRules.amountMinor(ocrAmount) != ExpenseRules.amountMinor(nanoAmount)
            val amount = if (conflict) "" else ocrAmount ?: nanoAmount.orEmpty()
            val currency = snapshot.ocrProposal?.currency ?: parsed.currency.takeIf { it in ExpenseRules.currencies }.orEmpty()
            val date = parsed.date.takeIf { runCatching { LocalDate.parse(it) }.isSuccess }.orEmpty()
            val category = parsed.category.takeIf { it in ExpenseRules.categories } ?: "other"
            _state.update { current -> if (current.form.receiptUri == path) current.copy(form = current.form.copy(amount = amount, currency = currency, date = date, category = category, description = parsed.description, notes = parsed.notes), receiptMessage = when { conflict -> "Importes distintos: OCR $ocrAmount y Gemini Nano $nanoAmount. Revisa el comprobante y escribe el total."; amount.isBlank() -> "No se pudo identificar el total. Escríbelo tras revisar el comprobante."; else -> "Propuesta del comprobante: revisa importe, moneda, fecha y concepto antes de guardar." }, receiptNanoStatus = "Disponible") else current }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { _state.update { current -> if (current.form.receiptUri == path) current.copy(receiptMessage = "No se pudo analizar con Gemini Nano: ${e.message}. Revisa los datos OCR y completa el gasto.") else current } }
        finally { _state.update { current -> if (current.form.receiptUri == path) current.copy(receiptBusy = false) else current } }
    }
    fun preview() {
        val f = state.value.form
        val error = ExpenseRules.validate(f.amount, f.currency, f.date, f.category)
        _state.update { it.copy(error = error ?: if (it.tripId == 0L) "Crea un viaje primero" else null, preview = error == null && it.tripId != 0L) }
    }
    fun dismissPreview() { _state.update { it.copy(preview = false) } }
    fun save(onSaved: () -> Unit) = viewModelScope.launch {
        val s = state.value; val f = s.form
        val error = ExpenseRules.validate(f.amount, f.currency, f.date, f.category)
        if (s.saving) return@launch
        if (!s.preview || error != null || s.tripId == 0L) {
            _state.update { it.copy(error = error ?: "Revisa la vista previa", preview = false) }
            return@launch
        }
        _state.update { it.copy(saving = true, error = null) }
        try {
            val amount = ExpenseRules.amountMinor(f.amount)!!
            val budget = repo.budgets(s.tripId).first().find { it.category == f.category && it.currency == f.currency }?.limitMinor
            val spent = repo.expenses(s.tripId).first().filter { it.category == f.category && it.currency == f.currency && it.id != f.id }.sumOf { it.amountMinor }
            repo.saveExpense(ExpenseEntity(f.id, s.tripId, amount, f.currency, f.date, f.category, f.description.trim(), f.notes.trim(), f.receiptUri, ExpenseRules.level(amount, budget), ExpenseRules.assessment(f.category, amount, budget?.minus(spent))))
            _state.update { it.copy(form = ExpenseForm(), preview = false, saving = false) }
            onSaved()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { _state.update { it.copy(error = e.message ?: "No se pudo guardar el gasto", preview = false) } }
        finally { _state.update { it.copy(saving = false) } }
    }
    fun askDelete(expense: ExpenseEntity) { _state.update { it.copy(deleteCandidate = expense) } }
    fun dismissDelete() { _state.update { it.copy(deleteCandidate = null) } }
    fun confirmDelete() = viewModelScope.launch { state.value.deleteCandidate?.let { repo.deleteExpense(it) }; dismissDelete() }
    private fun analyzeNewReceipt(uri: Uri, requestId: Long) {
      receiptJob = viewModelScope.launch {
        _state.update { it.copy(receiptBusy = true, receiptMessage = null) }
        var file: File? = null
        var attached = false
        try {
            val receiptFile = File(context.filesDir, "receipt_${System.currentTimeMillis()}.jpg")
            file = receiptFile
            context.contentResolver.openInputStream(uri)?.use { input -> receiptFile.outputStream().use { input.copyTo(it) } } ?: error("No se pudo abrir el comprobante")
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val text = try { recognizer.process(InputImage.fromFilePath(context, Uri.fromFile(receiptFile))).await().text } finally { recognizer.close() }
            if (requestId != receiptGeneration) return@launch
            val proposal = ReceiptAmountRules.propose(text)
            _state.update { s -> s.copy(form = s.form.copy(receiptUri = receiptFile.absolutePath, amount = proposal?.amount.orEmpty(), currency = proposal?.currency.orEmpty()), ocrText = text, ocrProposal = proposal, error = null) }
            attached = true
            val status = runCatching { nano.status() }.getOrDefault(FeatureStatus.UNAVAILABLE)
            if (requestId != receiptGeneration) return@launch
            _state.update { it.copy(receiptNanoStatus = when (status) { FeatureStatus.AVAILABLE -> "Disponible"; FeatureStatus.DOWNLOADABLE -> "Descargable"; FeatureStatus.DOWNLOADING -> "Descargando"; else -> "No disponible" }) }
            if (status == FeatureStatus.AVAILABLE) analyzeReceipt()
            else _state.update { it.copy(receiptMessage = if (status == FeatureStatus.DOWNLOADABLE) "Activa Gemini Nano para obtener una propuesta completa. El importe OCR ya está disponible para revisar." else "Gemini Nano no está disponible. Revisa el importe OCR y completa los demás campos.") }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (requestId == receiptGeneration) _state.update { it.copy(error = e.message, receiptMessage = e.message) } }
        finally {
            if (!attached) file?.delete()
            if (requestId == receiptGeneration) _state.update { it.copy(receiptBusy = false) }
        }
      }
    }
}
