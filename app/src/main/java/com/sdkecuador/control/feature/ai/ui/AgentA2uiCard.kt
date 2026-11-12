package com.sdkecuador.control.feature.ai.ui

import androidx.a2ui.compose.ui.A2uiMessageProcessor
import androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1
import androidx.a2ui.model.catalog.functions.A2uiLocaleProvider
import androidx.a2ui.model.protocol.A2uiComponentPayload
import androidx.a2ui.model.protocol.A2uiCreateSurfaceMessage
import androidx.a2ui.model.protocol.A2uiExecutionContext
import androidx.a2ui.model.protocol.A2uiUpdateComponentsMessage
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.a2ui.A2uiSurface
import androidx.compose.material3.a2ui.catalog.MaterialA2uiBasicCatalogV1Defaults
import androidx.compose.material3.a2ui.catalog.materialA2uiBasicCatalogV1
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sdkecuador.control.ui.theme.TravelColors
import com.sdkecuador.control.feature.ai.data.BudgetSnapshot
import com.sdkecuador.control.feature.ai.data.AgentVisual
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Builds a read-only A2UI surface from a validated assistant response. */
@Composable fun AgentA2uiCard(visual: AgentVisual, modifier: Modifier = Modifier) {
    val catalog = remember {
        materialA2uiBasicCatalogV1(
            image = MaterialA2uiBasicCatalogV1Defaults.image { _, _, _, _, _ -> },
            video = MaterialA2uiBasicCatalogV1Defaults.video { _, _, _ -> },
            audioPlayer = MaterialA2uiBasicCatalogV1Defaults.audioPlayer { _, _, _, _ -> },
            functions = object : A2uiBasicCatalogV1.Functions {
                override fun openUrl(url: String, executionContext: A2uiExecutionContext) = Unit
            },
        )
    }
    val processor = remember(catalog) { A2uiMessageProcessor(catalogs = listOf(catalog)) }
    LaunchedEffect(processor) {
        launch(Dispatchers.Default) { processor.collectMessages() }
        processor.processMessage(A2uiCreateSurfaceMessage("agent_answer", catalog.id))
    }
    LaunchedEffect(processor, visual) {
        val components = when (visual) {
            is AgentVisual.Balance -> budgetComponents(visual.budget)
            is AgentVisual.Budget -> overviewComponents(visual)
            is AgentVisual.Expenses -> expenseComponents(visual)
            is AgentVisual.Categories -> categoryComponents(visual)
        }
        processor.processMessage(A2uiUpdateComponentsMessage("agent_answer", components))
    }
    val surfaces by processor.activeSurfaces.collectAsState()
    surfaces.firstOrNull()?.let { surface ->
        val budget = (visual as? AgentVisual.Balance)?.budget
        val overBudget = budget?.let { it.budgetMinor != null && it.spentMinor > it.budgetMinor } == true
        val accent = when {
            overBudget -> TravelColors.amber
            visual is AgentVisual.Expenses -> androidx.compose.ui.graphics.Color(0xFFAA9BFF)
            visual is AgentVisual.Categories -> TravelColors.amber
            visual is AgentVisual.Budget -> androidx.compose.ui.graphics.Color(0xFF8FE0A5)
            else -> TravelColors.cyan
        }
        val background = when {
            overBudget -> androidx.compose.ui.graphics.Color(0xFF342B1B)
            visual is AgentVisual.Expenses -> androidx.compose.ui.graphics.Color(0xFF27233D)
            visual is AgentVisual.Categories -> androidx.compose.ui.graphics.Color(0xFF332B1C)
            visual is AgentVisual.Budget -> androidx.compose.ui.graphics.Color(0xFF1B3228)
            else -> androidx.compose.ui.graphics.Color(0xFF123139)
        }
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = background, contentColor = TravelColors.text),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.65f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(10.dp)) {
                MaterialTheme(typography = MaterialTheme.typography.copy(
                    bodyLarge = MaterialTheme.typography.bodyMedium,
                    titleMedium = MaterialTheme.typography.titleSmall,
                    labelMedium = MaterialTheme.typography.labelSmall
                )) {
                    A2uiSurface(surfaceModel = surface)
                }
                if (budget?.budgetMinor != null && budget.budgetMinor > 0) {
                    LinearProgressIndicator(
                        progress = { (budget.spentMinor.toFloat() / budget.budgetMinor).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = accent,
                        trackColor = accent.copy(alpha = 0.18f)
                    )
                    Text("${(budget.spentMinor * 100 / budget.budgetMinor)} % del presupuesto utilizado", color = accent, style = MaterialTheme.typography.labelSmall)
                }
                if (visual is AgentVisual.Categories) {
                    visual.budgets.forEach { category ->
                        if (category.limitMinor > 0) {
                            val excess = category.spentMinor > category.limitMinor
                            Text(categoryLabel(category.category), style = MaterialTheme.typography.labelSmall,
                                color = if (excess) TravelColors.amber else TravelColors.muted)
                            LinearProgressIndicator(
                                progress = { (category.spentMinor.toFloat() / category.limitMinor).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                                color = if (excess) TravelColors.amber else TravelColors.cyan,
                                trackColor = TravelColors.border
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun budgetComponents(budget: BudgetSnapshot): List<A2uiComponentPayload> {
    val money: (Long, String) -> String = { minor, currency ->
        "$currency ${String.format(Locale.US, "%.2f", minor / 100.0)}"
    }
    val children = mutableListOf("title", "remaining", "summary", "divider", "categories_title")
    val components = mutableListOf(
        A2uiComponentPayload("title", "Text", mapOf("text" to budget.tripName, "variant" to "caption")),
        A2uiComponentPayload("remaining", "Text", mapOf(
            "text" to (budget.budgetMinor?.let { money(it - budget.spentMinor, budget.currency) } ?: "Sin presupuesto general"),
            "variant" to "h5"
        )),
        A2uiComponentPayload("summary", "Text", mapOf("text" to (
            budget.budgetMinor?.let { "Disponible de ${money(it, budget.currency)} · Gastado ${money(budget.spentMinor, budget.currency)}" }
                ?: "Gastado: ${money(budget.spentMinor, budget.currency)}"
        ))),
        A2uiComponentPayload("divider", "Divider", emptyMap()),
        A2uiComponentPayload("categories_title", "Text", mapOf("text" to "Gasto por categoría", "variant" to "caption"))
    )
    budget.categories.forEachIndexed { index, category ->
        val id = "category_$index"
        children += id
        components += A2uiComponentPayload(id, "Text", mapOf(
            "text" to "${categoryLabel(category.category)}  ·  ${money(category.amountMinor, budget.currency)}"
        ))
    }
    if (budget.categories.isEmpty()) {
        children += "empty"
        components += A2uiComponentPayload("empty", "Text", mapOf("text" to "Aún no hay gastos en ${budget.currency}"))
    }
    if (budget.otherCurrencies.isNotEmpty()) {
        children += "other"
        components += A2uiComponentPayload("other", "Text", mapOf(
            "text" to "Otras monedas (sin convertir): " + budget.otherCurrencies.entries.joinToString(" · ") { (currency, amount) -> money(amount, currency) },
            "variant" to "caption"
        ))
    }
    return listOf(A2uiComponentPayload("root", "Column", mapOf("children" to children))) + components
}

private fun expenseComponents(expenses: AgentVisual.Expenses): List<A2uiComponentPayload> {
    val children = mutableListOf("heading", "count", "totals", "divider", "recent")
    val components = mutableListOf(
        A2uiComponentPayload("heading", "Text", mapOf("text" to "Gastos · ${expenses.tripName}", "variant" to "caption")),
        A2uiComponentPayload("count", "Text", mapOf("text" to "${expenses.count} movimientos", "variant" to "h5")),
        A2uiComponentPayload("totals", "Text", mapOf("text" to expenses.totals.entries.joinToString("  ·  ") { (currency, amount) -> money(amount, currency) }.ifBlank { "Sin gastos registrados" })),
        A2uiComponentPayload("divider", "Divider", emptyMap()),
        A2uiComponentPayload("recent", "Text", mapOf("text" to "Últimos gastos", "variant" to "caption"))
    )
    expenses.recent.forEachIndexed { index, expense ->
        val id = "expense_$index"
        children += id
        components += A2uiComponentPayload(id, "Text", mapOf(
            "text" to "${expense.date} · ${expense.description.ifBlank { categoryLabel(expense.category) }} · ${money(expense.amountMinor, expense.currency)}"
        ))
    }
    if (expenses.recent.isEmpty()) {
        children += "empty"
        components += A2uiComponentPayload("empty", "Text", mapOf("text" to "No hay movimientos para mostrar"))
    }
    return listOf(A2uiComponentPayload("root", "Column", mapOf("children" to children))) + components
}

private fun overviewComponents(budget: AgentVisual.Budget): List<A2uiComponentPayload> = listOf(
    A2uiComponentPayload("root", "Column", mapOf("children" to listOf("heading", "amount", "spent", "remaining"))),
    A2uiComponentPayload("heading", "Text", mapOf("text" to "Presupuesto general · ${budget.tripName}", "variant" to "caption")),
    A2uiComponentPayload("amount", "Text", mapOf("text" to (budget.limitMinor?.let { money(it, budget.currency) } ?: "Sin límite general"), "variant" to "h5")),
    A2uiComponentPayload("spent", "Text", mapOf("text" to "Gastado en ${budget.currency}: ${money(budget.spentMinor, budget.currency)}")),
    A2uiComponentPayload("remaining", "Text", mapOf("text" to (budget.limitMinor?.let { "Disponible: ${money(it - budget.spentMinor, budget.currency)}" } ?: "Puedes crear un límite en Plan")))
)

private fun categoryComponents(categories: AgentVisual.Categories): List<A2uiComponentPayload> {
    val children = mutableListOf("heading", "count", "divider")
    val components = mutableListOf(
        A2uiComponentPayload("heading", "Text", mapOf("text" to "Plan · ${categories.tripName}", "variant" to "caption")),
        A2uiComponentPayload("count", "Text", mapOf("text" to "${categories.budgets.size} categorías con límite", "variant" to "h5")),
        A2uiComponentPayload("divider", "Divider", emptyMap())
    )
    categories.budgets.forEachIndexed { index, category ->
        val id = "category_$index"
        children += id
        val left = category.limitMinor - category.spentMinor
        components += A2uiComponentPayload(id, "Text", mapOf(
            "text" to "${categoryLabel(category.category)} · ${money(category.spentMinor, category.currency)} de ${money(category.limitMinor, category.currency)} · ${if (left < 0) "Exceso ${money(-left, category.currency)}" else "Restan ${money(left, category.currency)}"}"
        ))
    }
    if (categories.budgets.isEmpty()) {
        children += "empty"
        components += A2uiComponentPayload("empty", "Text", mapOf("text" to "Aún no hay presupuestos por categoría"))
    }
    return listOf(A2uiComponentPayload("root", "Column", mapOf("children" to children))) + components
}

private fun money(minor: Long, currency: String): String =
    "$currency ${String.format(Locale.US, "%.2f", minor / 100.0)}"

private fun categoryLabel(code: String): String = when (code) {
    "food" -> "Comida"
    "transport" -> "Transporte"
    "lodging" -> "Alojamiento"
    "activities" -> "Actividades"
    "shopping" -> "Compras"
    "other" -> "Otros"
    else -> code
}
