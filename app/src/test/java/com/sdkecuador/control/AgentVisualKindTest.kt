package com.sdkecuador.control

import com.sdkecuador.control.feature.ai.data.AgentVisualKind
import com.sdkecuador.control.feature.ai.data.visualKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgentVisualKindTest {
    @Test fun selectsDistinctVisualsForTripQuestions() {
        assertEquals(AgentVisualKind.BALANCE, visualKind("¿Cuánto me queda para Cartagena?"))
        assertEquals(AgentVisualKind.BUDGET, visualKind("¿Qué presupuesto tiene Cartagena?"))
        assertEquals(AgentVisualKind.EXPENSES, visualKind("Muéstrame los gastos de Cartagena"))
        assertEquals(AgentVisualKind.CATEGORIES, visualKind("¿Cómo van mis presupuestos por categoría en Cartagena?"))
    }

    @Test fun freeConversationHasNoStructuredVisual() {
        assertNull(visualKind("¿Qué recomiendas visitar en Cartagena?"))
    }
}
