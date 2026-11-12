package com.sdkecuador.control

import com.sdkecuador.control.feature.voice.data.VoiceBudgetRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceBudgetRulesTest {
    @Test fun readsSpokenBudgets() {
        assertEquals(50_000L, VoiceBudgetRules.amountMinor("Crea un viaje a Lima con presupuesto de 500 dólares"))
        assertEquals(150_000L, VoiceBudgetRules.amountMinor("crea viaje con presupuesto de mil quinientos"))
        assertEquals(25_000_00L, VoiceBudgetRules.amountMinor("presupuesto de 25 mil pesos"))
        assertEquals(150_050L, VoiceBudgetRules.amountMinor("presupuesto de 1.500,50 euros"))
        assertEquals(150_050L, VoiceBudgetRules.amountMinor("presupuesto de 1,500.50 USD"))
    }

    @Test fun rejectsUnclearBudget() {
        assertNull(VoiceBudgetRules.amountMinor("presupuesto de muchos dólares"))
        assertNull(VoiceBudgetRules.amountMinor("crea un viaje a Quito"))
    }
}
