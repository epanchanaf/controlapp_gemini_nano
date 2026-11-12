package com.sdkecuador.control

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sdkecuador.control.feature.expenses.data.ExpenseRules
import com.sdkecuador.control.feature.expenses.data.ExpenseEntity
import com.sdkecuador.control.feature.expenses.ui.*
import com.sdkecuador.control.feature.voice.ui.*
import com.sdkecuador.control.feature.budgets.ui.*
import com.sdkecuador.control.feature.ai.data.*
import com.sdkecuador.control.feature.ai.ui.TripAgentViewModel
import com.sdkecuador.control.ui.theme.ControlTheme
import com.sdkecuador.control.ui.theme.TravelColors
import com.sdkecuador.control.ui.theme.TravelStyles
import com.sdkecuador.control.feature.ai.ui.AgentA2uiCard
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

@AndroidEntryPoint class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { ControlTheme { TravelApp() } } }
}

@Composable fun TravelApp() {
    val expenseVm: ExpenseViewModel = hiltViewModel()
    val voiceVm: VoiceViewModel = hiltViewModel()
    val budgetVm: BudgetViewModel = hiltViewModel()
    val chatVm: TripVoiceChatViewModel = hiltViewModel()
    val agentVm: TripAgentViewModel = hiltViewModel()
    val chatState by chatVm.state.collectAsStateWithLifecycle()
    val expense by expenseVm.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val tripId = backStackEntry?.arguments?.getString("tripId")?.toLongOrNull()
    var creatingTrip by remember { mutableStateOf(false) }
    var openCreatedTrip by remember { mutableStateOf(false) }
    var tripName by remember { mutableStateOf("") }
    var tripBudget by remember { mutableStateOf("") }
    var tripCurrency by remember { mutableStateOf("USD") }
    var assistantOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(expense.tripId) {
        if (openCreatedTrip && expense.tripId != 0L) {
            openCreatedTrip = false
            navController.navigate("trip/${expense.tripId}/expenses")
        }
    }
    LaunchedEffect(tripId) {
        if (tripId != null) {
            expenseVm.selectTrip(tripId)
            voiceVm.currentTrip = tripId
            budgetVm.load(tripId)
        }
    }
    LaunchedEffect(expense.tripId) { chatVm.currentTrip = expense.tripId }
    LaunchedEffect(chatState.pendingExpenseCommand) {
        val command = chatState.pendingExpenseCommand ?: return@LaunchedEffect
        val selectedTrip = expense.tripId
        chatVm.consumeExpenseCommand()
        if (selectedTrip != 0L) {
            voiceVm.currentTrip = selectedTrip
            voiceVm.transcript(command)
            voiceVm.analyze()
            navController.navigate("trip/$selectedTrip/expenses") { launchSingleTop = true }
        }
    }
    Scaffold(modifier = Modifier.imePadding(), topBar = {
        Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(when (route) {
                    "trips" -> "Mis viajes"
                    else -> expense.trips.find { it.id == tripId }?.name ?: "Viaje"
                }, style = MaterialTheme.typography.headlineSmall)
            }
            if (tripId != null) {
                val selected = if (route == "trip/{tripId}/budgets") 1 else 0
                PrimaryTabRow(selectedTabIndex = selected) {
                    listOf("Gastos" to "expenses", "Plan" to "budgets").forEachIndexed { index, (label, destination) ->
                        Tab(selected = selected == index, onClick = {
                            navController.navigate("trip/$tripId/$destination") { launchSingleTop = true }
                        }, text = { Text(label) })
                    }
                }
            }
        }
    }, bottomBar = {
        if (!keyboardVisible) NavigationBar {
            NavigationBarItem(
                selected = !assistantOpen,
                onClick = {
                    assistantOpen = false
                    navController.navigate("trips") {
                            popUpTo("trips") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                    }
                },
                icon = { Icon(Icons.Default.Home, contentDescription = null) },
                label = { Text("Viajes") }
            )
            NavigationBarItem(
                selected = assistantOpen,
                onClick = { assistantOpen = !assistantOpen },
                icon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                label = { Text("Asistente") }
            )
        }
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
        if (!assistantOpen || !keyboardVisible) NavHost(navController = navController, startDestination = "trips", modifier = Modifier.weight(if (assistantOpen) 0.4f else 1f)) {
            composable("trips") {
                TripListScreen(expense, expenseVm, onCreate = { creatingTrip = true }, onOpen = { id -> expenseVm.selectTrip(id); navController.navigate("trip/$id/expenses") }, onOpenExpense = { found -> expenseVm.selectTrip(found.tripId); expenseVm.edit(found); navController.navigate("trip/${found.tripId}/expense/new") })
            }
            composable("trip/{tripId}/expenses") { entry ->
                val id = entry.arguments?.getString("tripId")?.toLongOrNull() ?: return@composable
                ExpenseScreen(expense, expenseVm, voiceVm, showForm = false, setShowForm = { show -> if (show) navController.navigate("trip/$id/expense/new") })
            }
            composable("trip/{tripId}/expense/new") {
                ExpenseScreen(expense, expenseVm, voiceVm, showForm = true, setShowForm = { show -> if (!show) navController.popBackStack() })
            }
            composable("trip/{tripId}/budgets") { entry ->
                val id = entry.arguments?.getString("tripId")?.toLongOrNull() ?: return@composable
                BudgetScreen(budgetVm, id)
            }
        }
        if (assistantOpen) AssistantScreen(expense, chatVm, agentVm, modifier = Modifier.weight(if (keyboardVisible) 1f else 0.6f))
        }
    }
    if (creatingTrip) {
        val budgetMinor = tripBudget.takeIf { it.isNotBlank() }?.let(ExpenseRules::amountMinor)
        val budgetInvalid = tripBudget.isNotBlank() && budgetMinor == null
        AlertDialog(
            onDismissRequest = { creatingTrip = false },
            title = { Text("Nuevo viaje") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(tripName, { tripName = it }, label = { Text("Nombre del viaje") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(
                        tripBudget, { tripBudget = it }, label = { Text("Presupuesto general (opcional)") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        isError = budgetInvalid, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    if (budgetInvalid) Text("Usa un importe positivo con máximo dos decimales", color = MaterialTheme.colorScheme.error)
                    Text("Moneda del viaje")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ExpenseRules.currencies.sorted().forEach { currency ->
                            FilterChip(selected = tripCurrency == currency, onClick = { tripCurrency = currency }, label = { Text(currency) })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    openCreatedTrip = true
                    expenseVm.createTrip(tripName, tripCurrency, budgetMinor)
                    tripName = ""
                    tripBudget = ""
                    tripCurrency = "USD"
                    creatingTrip = false
                }, enabled = tripName.isNotBlank() && !budgetInvalid) { Text("Crear") }
            },
            dismissButton = { TextButton(onClick = { creatingTrip = false }) { Text("Cancelar") } }
        )
    }
}

@Composable private fun TripListScreen(state: ExpenseUiState, vm: ExpenseViewModel, onCreate: () -> Unit, onOpen: (Long) -> Unit, onOpenExpense: (ExpenseEntity) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
      Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Viajes creados", style = MaterialTheme.typography.titleLarge)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
        ) {
            if (state.trips.isEmpty()) {
                item { Text("Aún no tienes viajes. Pulsa + Viaje para crear el primero.") }
            }
            if (state.globalSearchPerformed) {
                item { Text(if (state.globalSearchResults.isEmpty()) "No se encontraron gastos" else "Gastos encontrados", style = MaterialTheme.typography.titleMedium) }
                items(state.globalSearchResults, key = { it.id }) { found ->
                    val tripName = state.trips.find { it.id == found.tripId }?.name ?: "Viaje"
                    Card(onClick = { onOpenExpense(found) }, shape = TravelStyles.cardShape, border = TravelStyles.cardBorder, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp)) {
                            Text(found.description.ifBlank { categoryLabel(found.category) }, style = MaterialTheme.typography.titleMedium)
                            Text("$tripName · ${found.date}", color = TravelColors.muted)
                            Text("${found.currency} ${"%.2f".format(java.util.Locale.US, found.amountMinor / 100.0)}")
                        }
                    }
                }
            } else items(state.trips, key = { it.id }) { trip -> Card(onClick = { onOpen(trip.id) }, shape = TravelStyles.cardShape, border = TravelStyles.cardBorder, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) { Text(trip.name, style = MaterialTheme.typography.titleMedium); Text("Abrir gastos", color = TravelColors.muted) }
            } }
        }
        if (state.globalSearchStatus == "Descargable") TextButton(onClick = vm::downloadGlobalSearchModel, enabled = !state.globalSearchBusy) { Text("Activar búsqueda en este dispositivo") }
        if (state.globalSearchBusy) CircularProgressIndicator()
        state.globalSearchError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.globalSearchQuery.isNotEmpty()) TextButton(onClick = vm::clearGlobalSearch) { Text("Ver viajes") }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.globalSearchQuery,
                onValueChange = vm::globalSearchQuery,
                label = { Text("Buscar gastos") },
                placeholder = { Text("En todos los viajes") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = vm::searchAllExpenses,
                        enabled = state.globalSearchQuery.isNotBlank() && !state.globalSearchBusy && state.trips.isNotEmpty()
                    ) { Icon(Icons.Default.Search, contentDescription = "Buscar gastos en todos los viajes") }
                }
            )
            Button(
                onClick = onCreate,
                modifier = Modifier.size(56.dp).semantics { contentDescription = "Agregar viaje" },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(0.dp)
            ) { Text("+", style = MaterialTheme.typography.titleLarge) }

        }
      }
    }
}

@Composable private fun AssistantScreen(state: ExpenseUiState, chatVm: TripVoiceChatViewModel, agentVm: TripAgentViewModel, modifier: Modifier = Modifier) {
    val chat by chatVm.state.collectAsStateWithLifecycle()
    val agent by agentVm.state.collectAsStateWithLifecycle()
    var agentQuestion by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    var chatOpen by rememberSaveable { mutableStateOf(false) }
    var speechReady by remember { mutableStateOf(false) }
    var textToSpeech by remember { mutableStateOf<TextToSpeech?>(null) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) chatVm.listen() else chatVm.showError("Concede permiso al micrófono para conversar por voz")
    }
    DisposableEffect(chatVm) {
        onDispose { chatVm.stopListening() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { chatVm.stopListening() }
    DisposableEffect(chatOpen) {
        if (chatOpen) {
            val engine = TextToSpeech(context) { status ->
                speechReady = status == TextToSpeech.SUCCESS
                if (status != TextToSpeech.SUCCESS) chatVm.showError("La respuesta hablada no está disponible en este dispositivo")
            }
            textToSpeech = engine
            onDispose { engine.stop(); engine.shutdown(); textToSpeech = null; speechReady = false }
        } else onDispose { }
    }
    LaunchedEffect(chat.pendingSpeechId, speechReady, textToSpeech) {
        val id = chat.pendingSpeechId ?: return@LaunchedEffect
        val message = chat.messages.lastOrNull { it.id == id } ?: return@LaunchedEffect
        val engine = textToSpeech?.takeIf { speechReady } ?: return@LaunchedEffect
        val language = engine.setLanguage(Locale.forLanguageTag("es-ES"))
        if (language < 0 || engine.speak(message.text, TextToSpeech.QUEUE_FLUSH, null, "trip-voice-$id") == TextToSpeech.ERROR) {
            chatVm.showError("La respuesta hablada no está disponible en español")
        }
        chatVm.markSpoken(id)
    }
    fun startConversation() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) chatVm.listen()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    Card(modifier.fillMaxWidth(), shape = TravelStyles.cardShape, border = TravelStyles.cardBorder) {
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Asistente", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { chatOpen = false }) { Text("Texto") }
                TextButton(onClick = { chatOpen = true; chatVm.refresh() }) { Text("Voz") }
            }
            if (chatOpen) {
                TripVoiceChatPanel(
                    chat = chat,
                    onClose = { chatVm.stopListening(); textToSpeech?.stop(); chatOpen = false },
                    onDownloadSpeech = chatVm::downloadSpeech,
                    onDownloadNano = chatVm::downloadNano,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                Button(onClick = {
                    if (chat.listening) chatVm.stopListening()
                    else if (!chat.replying) startConversation()
                }, enabled = !chat.replying, modifier = Modifier.fillMaxWidth()) {
                    Text(if (chat.listening) "Detener voz" else "Hablar")
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(agent.messages.takeLast(10)) { message ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
                            if (message.fromUser) {
                                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.fillMaxWidth(0.85f)) {
                                    Text(message.text, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
                                }
                            } else if (message.visual != null) {
                                AgentA2uiCard(message.visual, modifier = Modifier.fillMaxWidth(0.9f))
                            } else {
                                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth(0.9f)) {
                                    Text(message.text, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    if (agent.replying) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    agent.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(agentQuestion, { agentQuestion = it }, label = { Text("Pregunta") },
                        modifier = Modifier.weight(1f), singleLine = true)
                    TextButton(onClick = { agentVm.ask(agentQuestion); agentQuestion = "" },
                        enabled = agentQuestion.isNotBlank() && !agent.replying) { Text("Enviar") }
                }
            }
        }
    }
}

@Composable private fun TripVoiceChatPanel(
    chat: TripVoiceChatState,
    onClose: () -> Unit,
    onDownloadSpeech: () -> Unit,
    onDownloadNano: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(chat.messages.size) {
        if (chat.messages.isNotEmpty()) listState.animateScrollToItem(chat.messages.lastIndex)
    }
    Card(modifier = modifier, shape = TravelStyles.cardShape, border = TravelStyles.cardBorder) {
        Column(Modifier.fillMaxSize().padding(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Gemini Nano", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Cerrar") }
            }
            Text("Voz: ${chat.speechStatus} · Nano: ${chat.nanoStatus}", style = MaterialTheme.typography.labelSmall, color = TravelColors.muted)
            if (chat.speechStatus == "Descargable" || chat.nanoStatus == "Descargable") {
                Row {
                    if (chat.speechStatus == "Descargable") TextButton(onClick = onDownloadSpeech) { Text("Activar voz") }
                    if (chat.nanoStatus == "Descargable") TextButton(onClick = onDownloadNano) { Text("Activar Nano") }
                }
            }
            LazyColumn(modifier = Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (chat.messages.isEmpty()) item { Text("Pulsa el micrófono para empezar a conversar.") }
                items(chat.messages, key = { it.id }) { message ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
                        if (message.fromUser) {
                            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.fillMaxWidth(0.85f)) {
                                Text(message.text, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
                            }
                        } else if (message.visual != null) {
                            AgentA2uiCard(message.visual, modifier = Modifier.fillMaxWidth(0.9f))
                        } else {
                            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth(0.9f)) {
                                Text(message.text, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (chat.listening) item { Text("Escuchando… habla cuando estés listo.", color = TravelColors.muted) }
                if (chat.replying) item { Text("Gemini Nano está respondiendo…", color = TravelColors.muted) }
                chat.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
            }
        }
    }
}

@Composable private fun ExpenseScreen(state: ExpenseUiState, vm: ExpenseViewModel, voiceVm: VoiceViewModel, showForm: Boolean, setShowForm: (Boolean) -> Unit) {
    val newReceiptPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { vm.receiptForNewExpense(it); setShowForm(true) } }
    val voiceState by voiceVm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed -> if (allowed) voiceVm.listen() }
    var voiceActive by remember { mutableStateOf(false) }
    var actionsExpanded by remember { mutableStateOf(false) }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(voiceState.message) {
        if (voiceState.message == "Gasto guardado") { voiceActive = false; voiceVm.clearMessage() }
    }
    if (showForm) DisposableEffect(vm) {
        onDispose { vm.cancelReceiptAnalysis() }
    }
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = if (showForm) 16.dp else 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showForm) {
            item { TextButton(onClick = { setShowForm(false); vm.new() }) { Text("Volver a gastos") }; Text(if (state.form.id == 0L) "Nuevo gasto" else "Editar gasto", style = MaterialTheme.typography.titleLarge) }
            if (state.receiptEntry) item {
                Text("Gasto desde comprobante", style = MaterialTheme.typography.titleMedium)
                if (state.receiptBusy) { CircularProgressIndicator(); Text("Analizando comprobante…") }
                state.receiptMessage?.let { Text(it) }
                if (state.receiptNanoStatus == "Descargable") TextButton(onClick = vm::downloadReceiptNano, enabled = !state.receiptBusy) { Text("Activar Gemini Nano") }
                if (state.receiptNanoStatus == "Disponible" && !state.receiptBusy && state.ocrText != null) TextButton(onClick = vm::analyzeReceiptWithNano) { Text("Volver a analizar") }
            }
            if (!state.receiptBusy) item { ExpenseFields(state.form, vm::form, onImprove = { field, feature -> vm.improveText(field, feature) }, improvementBusy = state.improvementBusy) }
            if (state.improvementBusy) item { CircularProgressIndicator() }
            state.improvementError?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            if (state.improvementDownloadFeature != null) item { TextButton(onClick = vm::downloadImprovementModel, enabled = !state.improvementBusy) { Text("Activar mejora de texto") } }
            item { state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }; Button(onClick = vm::preview, enabled = !state.receiptBusy) { Text("Vista previa") } }
            if (state.receiptEntry) state.ocrText?.let { ocr -> item { Card { Column(Modifier.padding(12.dp)) { Text("Texto OCR del comprobante"); Text(ocr.take(600)); state.ocrProposal?.let { proposal -> Text("Importe detectado en TOTAL: ${proposal.currency ?: state.form.currency} ${proposal.amount}") } } } } }
        } else {
            item { ExpenseSummary(state, onEditTrip = vm::beginEditTrip) }
            if (searchVisible || state.searchQuery.isNotBlank() || state.searchPerformed) item {
                OutlinedTextField(state.searchQuery, vm::searchQuery, label = { Text("Buscar gastos") }, placeholder = { Text("Ej.: taxi al aeropuerto") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::searchExpenses, enabled = state.searchQuery.isNotBlank() && !state.searchBusy && state.expenses.isNotEmpty()) { Text("Buscar") }
                    TextButton(onClick = { vm.clearSearch(); searchVisible = false }) { Text("Ver todos") }
                }
                if (state.searchStatus == "Descargable") TextButton(onClick = vm::downloadSearchModel, enabled = !state.searchBusy) { Text("Activar búsqueda en este dispositivo") }
                if (state.searchBusy) CircularProgressIndicator()
                state.searchError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.searchPerformed) Text(if (state.searchResults.isEmpty()) "No se encontraron gastos" else "Resultados para «${state.searchQuery}»", style = MaterialTheme.typography.titleSmall)
            }
            if (voiceActive) {
                item { Text("Voz: ${voiceState.speechStatus} · Gemini Nano: ${voiceState.nanoStatus}"); if (voiceState.listening) OutlinedButton(onClick = voiceVm::stop) { Text("Detener") }; if (voiceState.speechStatus == "Descargable") TextButton(onClick = voiceVm::downloadSpeech) { Text("Descargar voz") }; if (voiceState.nanoStatus == "Descargable") TextButton(onClick = voiceVm::downloadNano) { Text("Descargar Nano") } }
            if (voiceState.transcript.isNotBlank()) item { Card { Column(Modifier.padding(12.dp)) { Text("Texto transcrito", style = MaterialTheme.typography.titleSmall); Text(voiceState.transcript); if (!voiceState.loading && voiceState.draft == null) TextButton(onClick = voiceVm::analyze) { Text("Analizar con Gemini Nano") } } } }
            if (voiceState.loading) item { CircularProgressIndicator() }
            voiceState.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
            voiceState.draft?.let { draft -> item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Vista previa del gasto dictado", style = MaterialTheme.typography.titleMedium); Text("${draft.description}: ${draft.currency} ${draft.amount} · ${draft.level} · ${draft.assessment}"); VoiceFields(draft, voiceVm::updateDraft); Button(onClick = { voiceVm.confirm() }) { Text("Confirmar y agregar a gastos") } } } } }
            }
            item {
                Text("Gastos individuales", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (state.searchPerformed) "${state.searchResults.size} encontrados" else "${state.expenses.size} registrados",
                    style = MaterialTheme.typography.bodySmall,
                    color = TravelColors.muted
                )
            }
            if (state.expenses.isEmpty()) item { Text("Aún no hay gastos en este viaje. Abre el menú de acciones para añadir uno.", modifier = Modifier.padding(top = 24.dp)) }
            items(if (state.searchPerformed) state.searchResults else state.expenses, key = { it.id }) { expense ->
                Card(
                    shape = TravelStyles.cardShape,
                    border = if (expense.assessment.startsWith("Malo")) TravelStyles.warningBorder else TravelStyles.cardBorder,
                    colors = CardDefaults.cardColors(containerColor = TravelColors.surface)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                            Text(
                                expense.description.ifBlank { categoryLabel(expense.category) },
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "${expense.currency} ${"%.2f".format(java.util.Locale.US, expense.amountMinor / 100.0)}",
                                style = MaterialTheme.typography.titleMedium,
                                color = TravelColors.text
                            )
                        }
                        Text("${expense.date} · ${expense.level} · ${expense.assessment}", color = TravelColors.muted)
                        if (expense.assessment.startsWith("Malo")) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Warning, contentDescription = "Advertencia de presupuesto", tint = TravelColors.amber)
                            Text(expense.assessment)
                        }
                        Row {
                            TextButton(onClick = { vm.edit(expense); setShowForm(true) }) { Text("Editar") }
                            TextButton(onClick = { vm.askDelete(expense) }) { Text("Borrar") }
                        }
                    }
                }
            }
        }
        if (!showForm) state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
    }
    if (!showForm) {
        Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            FloatingActionButton(
                onClick = { actionsExpanded = true },
                modifier = Modifier.semantics { contentDescription = "Abrir opciones de gastos" }
            ) { Text("+", style = MaterialTheme.typography.headlineMedium) }
            DropdownMenu(expanded = actionsExpanded, onDismissRequest = { actionsExpanded = false }) {
                DropdownMenuItem(text = { Text("Buscar gastos") }, onClick = {
                    actionsExpanded = false
                    searchVisible = true
                })
                DropdownMenuItem(text = { Text("Agregar gasto manual") }, onClick = {
                    actionsExpanded = false
                    vm.new()
                    voiceActive = false
                    voiceVm.dismissDraft()
                    setShowForm(true)
                })
                DropdownMenuItem(text = { Text("Agregar gasto por voz") }, onClick = {
                    actionsExpanded = false
                    voiceVm.dismissDraft()
                    voiceActive = true
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) voiceVm.listen()
                    else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                })
                DropdownMenuItem(text = { Text("Agregar gasto por imagen") }, onClick = {
                    actionsExpanded = false
                    voiceActive = false
                    voiceVm.dismissDraft()
                    newReceiptPicker.launch("image/*")
                })
            }
        }
    }
    }
    if (!showForm) state.tripEdit?.let { form ->
        TripEditDialog(form, state.tripEditSaving, state.tripEditError, vm::changeTripEdit, vm::saveTripEdit, vm::cancelTripEdit)
    }
    if (state.preview) AlertDialog(onDismissRequest = { if (!state.saving) vm.dismissPreview() }, title = { Text("Confirmar gasto") }, text = { Text("${state.form.description}\n${state.form.amount} ${state.form.currency} · ${state.form.category} · ${state.form.date}\n${state.form.notes}") }, confirmButton = { TextButton(onClick = { vm.save { setShowForm(false) } }, enabled = !state.saving) { Text(if (state.saving) "Guardando…" else "Guardar") } }, dismissButton = { TextButton(onClick = vm::dismissPreview, enabled = !state.saving) { Text("Volver") } })
    state.deleteCandidate?.let { e -> AlertDialog(onDismissRequest = vm::dismissDelete, title = { Text("Confirmar borrado") }, text = { Text("¿Borrar ${e.description} por ${e.currency} ${"%.2f".format(java.util.Locale.US, e.amountMinor / 100.0)}?") }, confirmButton = { TextButton(onClick = vm::confirmDelete) { Text("Borrar") } }, dismissButton = { TextButton(onClick = vm::dismissDelete) { Text("Cancelar") } }) }
    voiceState.duplicateWarning?.let { warning -> AlertDialog(onDismissRequest = { voiceState.draft?.let(voiceVm::updateDraft) }, title = { Text("Posible duplicado") }, text = { Text(warning) }, confirmButton = { TextButton(onClick = { voiceVm.confirm(true) }) { Text("Guardar de todos modos") } }, dismissButton = { TextButton(onClick = { voiceState.draft?.let(voiceVm::updateDraft) }) { Text("Revisar") } }) }
    state.improvement?.let { suggestion -> AlertDialog(onDismissRequest = vm::dismissImprovement, title = { Text("Sugerencia de texto") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Original: ${suggestion.original}"); Text("Propuesta: ${suggestion.proposed}") } }, confirmButton = { TextButton(onClick = vm::acceptImprovement) { Text("Usar propuesta") } }, dismissButton = { TextButton(onClick = vm::dismissImprovement) { Text("Conservar original") } }) }
}

@Composable private fun ExpenseSummary(state: ExpenseUiState, onEditTrip: () -> Unit) {
    val trip = state.trips.firstOrNull { it.id == state.tripId }
    val currency = trip?.baseCurrency ?: "USD"
    val totals = state.expenses.groupBy { it.currency }.mapValues { (_, expenses) -> expenses.sumOf { it.amountMinor } }
    val currencies = (totals.keys + currency).distinct().sortedWith(compareBy<String> { it != currency }.thenBy { it })
    Card(
        shape = TravelStyles.cardShape,
        border = TravelStyles.activeBorder,
        colors = CardDefaults.cardColors(containerColor = TravelColors.surfaceRaised),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("RESUMEN DEL VIAJE", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = TravelColors.cyan)
                TextButton(onClick = onEditTrip, enabled = trip != null) { Text("Editar viaje") }
            }
            trip?.let {
                Text(it.name, style = MaterialTheme.typography.titleMedium)
                Text("${it.startDate}${it.endDate?.let { end -> " – $end" }.orEmpty()} · Moneda base: ${it.baseCurrency}", style = MaterialTheme.typography.bodySmall, color = TravelColors.muted)
            }
            Text("Gasto total", style = MaterialTheme.typography.titleMedium)
            currencies.forEach { code ->
                Text("$code ${"%.2f".format(java.util.Locale.US, (totals[code] ?: 0L) / 100.0)}", style = MaterialTheme.typography.displaySmall, color = TravelColors.cyan)
            }
            Text(
                trip?.budgetMinor?.let { "Presupuesto general: $currency ${"%.2f".format(java.util.Locale.US, it / 100.0)}" }
                    ?: "Presupuesto general: no definido",
                color = TravelColors.muted
            )
            if (currencies.size > 1) Text("Los importes de distintas monedas se muestran por separado.", color = TravelColors.muted)
        }
    }
}

@Composable private fun TripEditDialog(
    form: TripEditForm,
    saving: Boolean,
    error: String?,
    onChange: (TripEditForm) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Editar viaje") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(form.name, { onChange(form.copy(name = it)) }, label = { Text("Nombre del viaje") }, modifier = Modifier.fillMaxWidth(), enabled = !saving, singleLine = true)
                TravelDatePicker(form.startDate, label = "Inicio", enabled = !saving) { onChange(form.copy(startDate = it)) }
                TravelDatePicker(form.endDate, label = "Fin (opcional)", enabled = !saving) { onChange(form.copy(endDate = it)) }
                if (form.endDate.isNotBlank()) TextButton(onClick = { onChange(form.copy(endDate = "")) }, enabled = !saving) { Text("Quitar fecha de fin") }
                Text("Moneda base")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExpenseRules.currencies.sorted().forEach { currency ->
                        FilterChip(selected = form.baseCurrency == currency, onClick = { onChange(form.copy(baseCurrency = currency)) }, label = { Text(currency) }, enabled = !saving)
                    }
                }
                Text("Los gastos existentes conservan su moneda.", style = MaterialTheme.typography.bodySmall, color = TravelColors.muted)
                OutlinedTextField(
                    form.budgetAmount, { onChange(form.copy(budgetAmount = it)) },
                    label = { Text("Presupuesto general (opcional)") }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = !saving, singleLine = true
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = onSave, enabled = !saving) { Text(if (saving) "Guardando…" else "Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancelar") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun TravelDatePicker(date: String, label: String = "Fecha", enabled: Boolean = true, onDateSelected: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val initialMillis = remember(date) {
        runCatching { LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
    }
    if (open) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onDateSelected(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    open = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } },
        ) { DatePicker(state = pickerState) }
    }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth(), enabled = enabled) {
        Text("$label: ${date.ifBlank { "Seleccionar fecha" }}")
    }
}

@Composable private fun ExpenseFields(form: ExpenseForm, update: (ExpenseForm) -> Unit, onImprove: (String, String) -> Unit, improvementBusy: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(form.description, { update(form.copy(description = it)) }, label = { Text("Descripción") }, modifier = Modifier.fillMaxWidth())
        ImproveTextButton("description", enabled = form.description.isNotBlank() && !improvementBusy, onImprove = onImprove)
        OutlinedTextField(form.amount, { update(form.copy(amount = it)) }, label = { Text("Importe") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(form.currency, { update(form.copy(currency = it.uppercase())) }, label = { Text("Moneda: USD, EUR, PEN, COP") }, modifier = Modifier.fillMaxWidth())
        TravelDatePicker(form.date) { update(form.copy(date = it)) }
        Text("Categoría")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ExpenseRules.categories.forEach { cat -> FilterChip(selected = form.category == cat, onClick = { update(form.copy(category = cat)) }, label = { Text(categoryLabel(cat)) }) } }
        OutlinedTextField(form.notes, { update(form.copy(notes = it)) }, label = { Text("Notas") }, modifier = Modifier.fillMaxWidth())
        ImproveTextButton("notes", enabled = form.notes.isNotBlank() && !improvementBusy, onImprove = onImprove)
        if (form.receiptUri != null) Text("Comprobante adjunto")
    }
}

@Composable private fun ImproveTextButton(field: String, enabled: Boolean, onImprove: (String, String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, enabled = enabled) { Text("Mejorar") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Corregir ortografía") }, onClick = { expanded = false; onImprove(field, "Proofreading") })
            DropdownMenuItem(text = { Text("Reformular") }, onClick = { expanded = false; onImprove(field, "Rewriting") })
        }
    }
}

@Composable private fun VoiceFields(d: ExtractedDraft, update: (ExtractedDraft) -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedTextField(d.description, { update(d.copy(description = it)) }, label = { Text("Concepto") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(d.amount, { update(d.copy(amount = it)) }, label = { Text("Importe") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(d.currency, { update(d.copy(currency = it.uppercase())) }, label = { Text("Moneda") }, modifier = Modifier.fillMaxWidth())
    TravelDatePicker(d.date) { update(d.copy(date = it)) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ExpenseRules.categories.forEach { cat -> FilterChip(selected = d.category == cat, onClick = { update(d.copy(category = cat)) }, label = { Text(categoryLabel(cat)) }) } }
    OutlinedTextField(d.notes, { update(d.copy(notes = it)) }, label = { Text("Notas") }, modifier = Modifier.fillMaxWidth())
} }

@Composable private fun BudgetScreen(vm: BudgetViewModel, tripId: Long) {
    val state by vm.state.collectAsStateWithLifecycle(); var category by remember { mutableStateOf("food") }; var currency by remember { mutableStateOf("USD") }; var amount by remember { mutableStateOf("") }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Presupuestos por categoría", style = MaterialTheme.typography.titleLarge) }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ExpenseRules.categories.forEach { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(categoryLabel(c)) }) } } }
        item { OutlinedTextField(currency, { currency = it.uppercase() }, label = { Text("Moneda") }); OutlinedTextField(amount, { amount = it }, label = { Text("Límite") }); Button(onClick = { vm.save(tripId, category, currency, amount) }) { Text("Guardar presupuesto") } }
        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        items(state.budgets) { b -> val spent = state.expenses.filter { it.category == b.category && it.currency == b.currency }.sumOf { it.amountMinor }; Card(shape = TravelStyles.cardShape, border = if (spent > b.limitMinor) TravelStyles.warningBorder else TravelStyles.cardBorder) { Column(Modifier.fillMaxWidth().padding(16.dp)) { Text("${categoryLabel(b.category)}: ${b.currency} ${"%.2f".format(java.util.Locale.US, spent / 100.0)} / ${"%.2f".format(java.util.Locale.US, b.limitMinor / 100.0)}"); LinearProgressIndicator(progress = { (spent.toFloat() / b.limitMinor).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = if (spent > b.limitMinor) TravelColors.amber else TravelColors.cyan); if (spent > b.limitMinor) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Default.Warning, contentDescription = "Advertencia de presupuesto", tint = TravelColors.amber); Text("Alerta: presupuesto de ${categoryLabel(b.category)} superado") } } } }
    }
}

private fun categoryLabel(code: String): String = when (code) {
    "food" -> "Comida"; "transport" -> "Transporte"; "lodging" -> "Alojamiento"; "activities" -> "Actividades"; "shopping" -> "Compras"; "other" -> "Otros"; else -> code
}
