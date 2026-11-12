package com.sdkecuador.control

import com.sdkecuador.control.feature.expenses.data.ReceiptAmountProposal
import com.sdkecuador.control.feature.expenses.data.ReceiptAmountRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptAmountRulesTest {
    @Test fun readsTotalAndKeepsCurrencyExplicit() {
        assertEquals(ReceiptAmountProposal("24.50", "USD"), ReceiptAmountRules.propose("Taxi 20.00\nTOTAL USD 24.50"))
        assertEquals(ReceiptAmountProposal("1234.50", "EUR"), ReceiptAmountRules.propose("TOTAL € 1.234,50 IVA 2,50"))
    }

    @Test fun leavesUnlabelledNumbersForManualReview() {
        assertNull(ReceiptAmountRules.propose("Taxi 24.50\nIVA 2.50"))
    }
}
