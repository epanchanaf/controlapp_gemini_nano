package com.sdkecuador.control.feature.ai.data

import android.content.Context
import com.google.mlkit.genai.agent.Agent
import com.google.mlkit.genai.agent.AgentRunner
import com.google.mlkit.genai.agent.textMessages
import com.google.mlkit.genai.prompt.Content
import com.sdkecuador.control.feature.trips.data.TravelDao
import com.sdkecuador.control.feature.expenses.data.ExpenseEntity
import java.util.UUID
import java.text.Normalizer
import java.util.Locale
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.toList
import javax.inject.Inject

class AgentRepository @Inject constructor(@ApplicationContext context: Context, private val dao: TravelDao) {
    private val runnerDelegate = lazy {
        AgentRunner(context, Agent.builder()
            .setName("AsistenteDeViaje")
            .setInstructions("Responde en español sobre presupuestos y gastos registrados. Para preguntas sobre un viaje por nombre, llama primero list_trips, luego trip_budgets y trip_expenses con el ID encontrado. Calcula el restante solo en la misma moneda: presupuesto general menos gastos de esa moneda. Si hay gastos en otras monedas, indícalos por separado sin convertirlos ni restarlos. Si hay varios viajes con el mismo nombre, pide aclaración. Si no hay presupuesto general, dilo; puedes informar presupuestos por categoría sin confundirlos con el general. No inventes cifras. Todas las herramientas son de solo lectura.")
            .addAppFunctionIds("list_trips", "trip_budgets", "trip_expenses")
            .setMaxTurns(8)
            .build())
    }
    private val runner by runnerDelegate
    suspend fun replyForUi(message: String): AgentReply {
        val kind = visualKind(message) ?: return AgentReply(reply(message))
        val matches = resolveTrip(message, dao.tripSnapshot())
        if (matches.size != 1) return AgentReply(reply(message))
        val trip = matches.single()
        val expenses = dao.expenseSnapshot(trip.id)
        if (kind == AgentVisualKind.BUDGET) {
            val spent = expenses.filter { it.currency == trip.baseCurrency }.sumOf { it.amountMinor }
            return AgentReply(
                trip.budgetMinor?.let { "Presupuesto general de ${trip.name}: ${trip.baseCurrency} ${String.format(Locale.US, "%.2f", it / 100.0)}." }
                    ?: "${trip.name} no tiene presupuesto general.",
                AgentVisual.Budget(trip.name, trip.baseCurrency, trip.budgetMinor, spent)
            )
        }
        if (kind == AgentVisualKind.EXPENSES) {
            val totals = expenses.groupBy { it.currency }.mapValues { (_, items) -> items.sumOf { it.amountMinor } }
            return AgentReply(
                "${trip.name}: ${expenses.size} gastos registrados.",
                visual = AgentVisual.Expenses(trip.name, expenses.size, totals, expenses.sortedWith(compareByDescending<ExpenseEntity> { it.date }.thenByDescending { it.id }).take(5))
            )
        }
        if (kind == AgentVisualKind.CATEGORIES) {
            val categories = dao.budgetSnapshot(trip.id).map { limit ->
                CategoryBudget(limit.category, limit.currency, limit.limitMinor,
                    expenses.filter { it.category == limit.category && it.currency == limit.currency }.sumOf { it.amountMinor })
            }
            return AgentReply(
                if (categories.isEmpty()) "${trip.name} no tiene presupuestos por categoría." else "Presupuestos por categoría de ${trip.name}.",
                visual = AgentVisual.Categories(trip.name, categories)
            )
        }
        val spent = expenses.filter { it.currency == trip.baseCurrency }.sumOf { it.amountMinor }
        val categories = expenses.filter { it.currency == trip.baseCurrency }
            .groupBy { it.category }
            .map { (category, entries) -> CategorySpending(category, entries.sumOf { it.amountMinor }) }
            .sortedByDescending { it.amountMinor }
        val snapshot = BudgetSnapshot(trip.name, trip.baseCurrency, trip.budgetMinor, spent, categories,
                expenses.filter { it.currency != trip.baseCurrency }.groupBy { it.currency }
                    .mapValues { (_, entries) -> entries.sumOf { it.amountMinor } })
        return AgentReply(balanceAnswer(trip, expenses), AgentVisual.Balance(snapshot))
    }
    suspend fun reply(message: String): String {
        val trips = dao.tripSnapshot()
        val matches = resolveTrip(message, trips)
        if (isBalanceQuestion(message)) {
            if (matches.size > 1) return "Encontré varios viajes con ese nombre. Indica cuál quieres consultar."
            val trip = matches.singleOrNull()
                ?: return "No pude identificar un viaje único. Menciona el nombre del viaje para consultar su saldo."
            return balanceAnswer(trip, dao.expenseSnapshot(trip.id))
        }
        val context = matches.singleOrNull()?.let { "Consulta únicamente el viaje ${it.name} con ID ${it.id}. " }.orEmpty()
        return cleanAgentResponse(
            runner.getOrCreateSession("trip_question_${UUID.randomUUID()}")
                .runAsync(Content.builder().text(context + message).build())
                .textMessages().toList().joinToString("\n")
        )
    }
    fun close() { if (runnerDelegate.isInitialized()) runner.close() }
}

data class CategorySpending(val category: String, val amountMinor: Long)
data class BudgetSnapshot(
    val tripName: String,
    val currency: String,
    val budgetMinor: Long?,
    val spentMinor: Long,
    val categories: List<CategorySpending>,
    val otherCurrencies: Map<String, Long>
)
data class CategoryBudget(val category: String, val currency: String, val limitMinor: Long, val spentMinor: Long)
sealed interface AgentVisual {
    data class Balance(val budget: BudgetSnapshot) : AgentVisual
    data class Budget(val tripName: String, val currency: String, val limitMinor: Long?, val spentMinor: Long) : AgentVisual
    data class Expenses(val tripName: String, val count: Int, val totals: Map<String, Long>, val recent: List<ExpenseEntity>) : AgentVisual
    data class Categories(val tripName: String, val budgets: List<CategoryBudget>) : AgentVisual
}
data class AgentReply(val text: String, val visual: AgentVisual? = null)

internal enum class AgentVisualKind { BALANCE, BUDGET, EXPENSES, CATEGORIES }

internal fun visualKind(question: String): AgentVisualKind? {
    val normalized = Normalizer.normalize(question.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
    return when {
        normalized.contains("categoria") && (normalized.contains("presupuesto") || normalized.contains("limite")) -> AgentVisualKind.CATEGORIES
        isBalanceQuestion(question) -> AgentVisualKind.BALANCE
        normalized.contains("gasto") || normalized.contains("gastado") -> AgentVisualKind.EXPENSES
        normalized.contains("presupuesto") -> AgentVisualKind.BUDGET
        else -> null
    }
}

internal fun cleanAgentResponse(response: String): String = response
    .replace(Regex("<ctrl42>.*?<ctrl43>", RegexOption.DOT_MATCHES_ALL), "")
    .trim()
