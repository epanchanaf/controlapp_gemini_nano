package com.sdkecuador.control

import com.sdkecuador.control.feature.voice.data.VoiceDateRules
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class VoiceDateRulesTest {
    private val today = LocalDate.of(2026, 11, 7)
    @Test fun relativeDatesUseDeviceCalendar() {
        assertEquals("2026-11-07", VoiceDateRules.resolve("Gasté 24 dólares en taxi hoy", today))
        assertEquals("2026-11-06", VoiceDateRules.resolve("Ayer pagué 5", today))
        assertEquals("2026-11-05", VoiceDateRules.resolve("Anteayer compré agua", today))
    }
    @Test fun missingDateRequiresReview() {
        assertEquals("", VoiceDateRules.resolve("Gasté 24 dólares en taxi", today))
        assertEquals("2026-11-02", VoiceDateRules.resolve("Pagué 4 el 02/11/2026", today))
    }
}
