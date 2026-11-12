package com.sdkecuador.control.feature.ai.data

import com.sdkecuador.control.feature.expenses.data.ExpenseEntity
import com.sdkecuador.control.feature.trips.data.TripEntity
import java.text.Normalizer
import java.util.Locale

internal fun resolveTrip(question: String, trips: List<TripEntity>): List<TripEntity> {
    val normalizedQuestion = normalize(question)
    return trips.filter { trip ->
        val name = normalize(trip.name)
        name.isNotBlank() && Regex("(^|\\s)${Regex.escape(name)}($|\\s)").containsMatchIn(normalizedQuestion)
    }
}

internal fun isBalanceQuestion(question: String): Boolean {
    val text = normalize(question)
    return listOf("cuanto me queda", "cuanto queda", "saldo", "presupuesto restante", "disponible", "cuanto resta").any(text::contains)
}

internal fun balanceAnswer(trip: TripEntity, expenses: List<ExpenseEntity>): String {
    val budget = trip.budgetMinor ?: return "${trip.name} no tiene un presupuesto general registrado."
    val sameCurrency = expenses.filter { it.currency == trip.baseCurrency }.sumOf { it.amountMinor }
    val remaining = budget - sameCurrency
    val other = expenses.filter { it.currency != trip.baseCurrency }.groupBy { it.currency }
        .entries.joinToString("; ") { (currency, items) -> "${money(items.sumOf { it.amountMinor }, currency)} en gastos" }
    return buildString {
        append("En ${trip.name} te quedan ${money(remaining, trip.baseCurrency)} de ${money(budget, trip.baseCurrency)}. ")
        append("Llevas ${money(sameCurrency, trip.baseCurrency)} en gastos.")
        if (other.isNotEmpty()) append(" También hay $other en otras monedas; no se restan del saldo.")
    }
}

private fun normalize(text: String): String = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()

private fun money(minor: Long, currency: String): String =
    "$currency ${String.format(Locale.US, "%.2f", minor / 100.0)}"
