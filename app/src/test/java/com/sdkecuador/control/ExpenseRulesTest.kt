package com.sdkecuador.control

import com.sdkecuador.control.feature.expenses.data.ExpenseRules
import com.sdkecuador.control.feature.expenses.data.ExpenseEntity
import com.sdkecuador.control.feature.budgets.data.BudgetEntity
import org.junit.Assert.*
import org.junit.Test

class ExpenseRulesTest {
    @Test fun rejectsInvalidValues() {
        assertNull(ExpenseRules.validate("12.50", "USD", "2026-11-07", "food"))
        assertNotNull(ExpenseRules.validate("-5", "USD", "2026-11-07", "food"))
        assertNotNull(ExpenseRules.validate("1.234", "USD", "2026-11-07", "food"))
        assertNotNull(ExpenseRules.validate("12", "XYZ", "2026-11-07", "food"))
        assertNotNull(ExpenseRules.validate("12", "USD", "2026-02-30", "food"))
        assertNotNull(ExpenseRules.validate("12", "USD", "2026-11-07", "wrong"))
    }
    @Test fun amountUsesMinorUnits() { assertEquals(1250L, ExpenseRules.amountMinor("12,50")) }

    @Test fun budgetAssessmentChangesWhenLimitOrOtherExpensesChange() {
        val first = ExpenseEntity(id = 1, tripId = 1, amountMinor = 6_000, currency = "USD", date = "2026-11-07", category = "food", description = "Comida")
        val second = first.copy(id = 2, amountMinor = 5_000, description = "Cena")
        val budget = BudgetEntity(tripId = 1, category = "food", currency = "USD", limitMinor = 10_000)

        assertTrue(ExpenseRules.currentAssessments(listOf(first, second), listOf(budget)).all { it.assessment.startsWith("Malo") })
        assertTrue(ExpenseRules.currentAssessments(listOf(first, second), listOf(budget.copy(limitMinor = 12_000))).all { it.assessment.startsWith("Bueno") })
        assertTrue(ExpenseRules.currentAssessments(listOf(first), listOf(budget)).all { it.assessment.startsWith("Bueno") })
    }
}
