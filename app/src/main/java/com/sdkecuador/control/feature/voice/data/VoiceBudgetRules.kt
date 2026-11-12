package com.sdkecuador.control.feature.voice.data
import com.sdkecuador.control.feature.expenses.data.ExpenseRules

import java.text.Normalizer
import java.util.Locale

/** Reads a budget from the recognized words, never from a model-generated amount. */
object VoiceBudgetRules {
    private val marker = Regex("\\bpresupuesto(?:\\s+(?:de|por|para|es|es de))?\\s*", RegexOption.IGNORE_CASE)
    private val numeric = Regex("^([0-9][0-9., ]*)(?:\\s*(?:d[oó]lares|euros|soles|pesos|usd|eur|pen|cop))?", RegexOption.IGNORE_CASE)
    private val units = mapOf("un" to 1, "uno" to 1, "una" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5, "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9, "diez" to 10, "once" to 11, "doce" to 12, "trece" to 13, "catorce" to 14, "quince" to 15, "dieciseis" to 16, "diecisiete" to 17, "dieciocho" to 18, "diecinueve" to 19, "veinte" to 20, "treinta" to 30, "cuarenta" to 40, "cincuenta" to 50, "sesenta" to 60, "setenta" to 70, "ochenta" to 80, "noventa" to 90)
    private val hundreds = mapOf("cien" to 100, "ciento" to 100, "doscientos" to 200, "trescientos" to 300, "cuatrocientos" to 400, "quinientos" to 500, "seiscientos" to 600, "setecientos" to 700, "ochocientos" to 800, "novecientos" to 900)

    fun mentioned(transcript: String): Boolean = marker.containsMatchIn(transcript)

    fun amountMinor(transcript: String): Long? {
        val tail = marker.find(transcript)?.let { transcript.substring(it.range.last + 1).trimStart() } ?: return null
        val digits = numeric.find(tail)?.groupValues?.get(1)?.trim()
        if (!digits.isNullOrEmpty()) {
            val compact = digits.replace(" ", "")
            val lastSeparator = maxOf(compact.lastIndexOf('.'), compact.lastIndexOf(','))
            val normalized = when {
                lastSeparator < 0 -> compact
                compact.length - lastSeparator - 1 == 3 -> compact.replace(".", "").replace(",", "")
                else -> compact.substring(0, lastSeparator).replace(".", "").replace(",", "") + "." + compact.substring(lastSeparator + 1)
            }
            val minor = ExpenseRules.amountMinor(normalized) ?: return null
            return if (Regex("^mil\\b", RegexOption.IGNORE_CASE).containsMatchIn(tail.substring(numeric.find(tail)!!.range.last + 1).trimStart())) {
                runCatching { Math.multiplyExact(minor, 1000L) }.getOrNull()
            } else minor
        }
        val words = Normalizer.normalize(tail.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .split(Regex("[^a-z]+"))
        var total = 0L
        var group = 0L
        var found = false
        for (word in words) {
            val value = units[word] ?: hundreds[word]
            when {
                value != null -> { group += value; found = true }
                word.startsWith("veinti") && units[word.removePrefix("veinti")] != null -> { group += 20 + units.getValue(word.removePrefix("veinti")); found = true }
                word == "mil" -> { total += (if (group == 0L) 1L else group) * 1000; group = 0; found = true }
                word == "y" || word == "de" -> Unit
                else -> break
            }
        }
        return if (found) ExpenseRules.amountMinor((total + group).toString()) else null
    }
}
