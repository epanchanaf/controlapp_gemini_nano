package com.sdkecuador.control

import com.sdkecuador.control.feature.expenses.data.ExpenseEntity
import com.sdkecuador.control.feature.trips.data.TripEntity
import com.sdkecuador.control.feature.ai.data.balanceAnswer
import com.sdkecuador.control.feature.ai.data.resolveTrip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripBalanceTest {
    private val lima = TripEntity(id = 1, name = "Lima", startDate = "2026-01-01", baseCurrency = "PEN", budgetMinor = 100_00)
    private val cartagena = TripEntity(id = 2, name = "Cartagena", startDate = "2026-01-01", baseCurrency = "COP", budgetMinor = 900_00)

    @Test fun resolvesOnlyNamedTrip() {
        assertEquals(listOf(lima), resolveTrip("¿Cuánto me queda en Lima?", listOf(lima, cartagena)))
    }

    @Test fun balanceUsesOnlySelectedTripAndCurrency() {
        val expenses = listOf(
            ExpenseEntity(tripId = lima.id, amountMinor = 25_00, currency = "PEN", date = "2026-01-02", category = "food", description = "Comida"),
            ExpenseEntity(tripId = lima.id, amountMinor = 10_00, currency = "USD", date = "2026-01-02", category = "other", description = "Otro")
        )
        val answer = balanceAnswer(lima, expenses)
        assertTrue(answer.contains("PEN 75.00"))
        assertTrue(answer.contains("PEN 25.00"))
        assertTrue(answer.contains("USD 10.00"))
        assertTrue(!answer.contains("Cartagena"))
    }
}
