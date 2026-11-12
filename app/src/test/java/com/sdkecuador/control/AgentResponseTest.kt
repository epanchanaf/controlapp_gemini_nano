package com.sdkecuador.control

import com.sdkecuador.control.feature.ai.data.cleanAgentResponse
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentResponseTest {
    @Test fun hidesToolCallMarkersFromConversation() {
        assertEquals(
            "No hay presupuestos para este viaje.",
            cleanAgentResponse("<ctrl42>call:budget_overview{tripId:0}<ctrl43>\nNo hay presupuestos para este viaje.")
        )
    }
}
