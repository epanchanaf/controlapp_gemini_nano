package com.sdkecuador.control.feature.expenses.data
import com.sdkecuador.control.feature.budgets.data.BudgetEntity

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

object ExpenseRules {
    val categories = setOf("food", "transport", "lodging", "activities", "shopping", "other")
    val currencies = setOf("USD", "EUR", "PEN", "COP")
    fun amountMinor(input: String): Long? = runCatching {
        val value = BigDecimal(input.trim().replace(',', '.'))
        require(value > BigDecimal.ZERO && value.scale() <= 2)
        value.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
    }.getOrNull()
    fun validate(amount: String, currency: String, date: String, category: String): String? = when {
        amountMinor(amount) == null -> "Importe inválido: usa un número positivo con máximo dos decimales"
        currency !in currencies -> "Moneda no admitida"
        runCatching { LocalDate.parse(date) }.isFailure -> "Fecha inválida: usa AAAA-MM-DD"
        category !in categories -> "Selecciona una categoría válida"
        else -> null
    }
    fun level(amountMinor: Long, budgetMinor: Long?): String {
        val threshold = budgetMinor?.takeIf { it > 0 } ?: 10_000L
        return when { amountMinor >= threshold / 2 -> "Alto"; amountMinor >= threshold / 5 -> "Medio"; else -> "Bajo" }
    }
    fun assessment(category: String, amountMinor: Long, budgetMinor: Long?): String = when {
        budgetMinor != null && amountMinor > budgetMinor -> "Malo: supera el presupuesto"
        budgetMinor != null -> "Bueno: dentro del presupuesto"
        category in setOf("food", "transport", "lodging") -> "Bueno: gasto esencial"
        else -> "Sin criterio: fija un presupuesto"
    }

    fun currentAssessments(expenses: List<ExpenseEntity>, budgets: List<BudgetEntity>): List<ExpenseEntity> {
        val limits = budgets.associate { (it.category to it.currency) to it.limitMinor }
        val totals = expenses.groupBy { it.category to it.currency }
            .mapValues { (_, group) -> group.sumOf { it.amountMinor } }
        return expenses.map { expense ->
            val key = expense.category to expense.currency
            val limit = limits[key]
            val spentWithoutExpense = (totals[key] ?: 0L) - expense.amountMinor
            expense.copy(
                level = level(expense.amountMinor, limit),
                assessment = assessment(expense.category, expense.amountMinor, limit?.minus(spentWithoutExpense))
            )
        }
    }
}
