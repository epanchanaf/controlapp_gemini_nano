package com.sdkecuador.control.feature.voice.data

import java.time.LocalDate
import java.util.Locale

/** Resolves only dates evidenced by the spoken text; never trusts an invented model date. */
object VoiceDateRules {
    fun resolve(transcript: String, today: LocalDate = LocalDate.now()): String {
        val text = transcript.lowercase(Locale.ROOT)
        return when {
            Regex("\\banteayer\\b").containsMatchIn(text) -> today.minusDays(2).toString()
            Regex("\\bayer\\b").containsMatchIn(text) -> today.minusDays(1).toString()
            Regex("\\bhoy\\b").containsMatchIn(text) -> today.toString()
            else -> {
                val iso = Regex("\\b\\d{4}-\\d{2}-\\d{2}\\b").find(text)?.value
                if (iso != null && runCatching { LocalDate.parse(iso) }.isSuccess) iso else {
                    val local = Regex("\\b(\\d{1,2})/(\\d{1,2})/(\\d{4})\\b").find(text)
                    local?.let { match -> runCatching { LocalDate.of(match.groupValues[3].toInt(), match.groupValues[2].toInt(), match.groupValues[1].toInt()).toString() }.getOrNull() }.orEmpty()
                }
            }
        }
    }
}
