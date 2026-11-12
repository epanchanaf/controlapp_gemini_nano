package com.sdkecuador.control

import com.sdkecuador.control.feature.ai.data.externalExpenseValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalExpenseValidationTest {
    @Test fun acceptsACompleteExternalExpense() {
        assertNull(externalExpenseValidation(2_450, "USD", "2026-11-12", "transport", "Taxi al aeropuerto"))
    }

    @Test fun rejectsInvalidExternalExpenseFields() {
        assertEquals("el importe debe ser positivo y estar expresado en centavos", externalExpenseValidation(0, "USD", "2026-11-12", "transport", "Taxi"))
        assertEquals("la moneda debe ser USD, EUR, PEN o COP", externalExpenseValidation(100, "GBP", "2026-11-12", "transport", "Taxi"))
        assertEquals("la fecha debe usar AAAA-MM-DD", externalExpenseValidation(100, "USD", "12/11/2026", "transport", "Taxi"))
        assertEquals("falta la descripción del gasto", externalExpenseValidation(100, "USD", "2026-11-12", "transport", " "))
    }
}
