package com.sdkecuador.control.feature.expenses.data

import java.math.BigDecimal

data class ReceiptAmountProposal(val amount: String, val currency: String?)

object ReceiptAmountRules {
    private val amountPattern = Regex("(?:\\d{1,3}(?:[.,]\\d{3})+|\\d+)(?:[.,]\\d{2})?")
    private val totalPattern = Regex("(?i)\\b(?:total|importe\\s+total|amount\\s+due|a\\s+pagar)\\b")

    fun propose(ocrText: String): ReceiptAmountProposal? {
        val line = ocrText.lineSequence().lastOrNull { totalPattern.containsMatchIn(it) } ?: return null
        val total = totalPattern.find(line) ?: return null
        val value = (amountPattern.find(line.substring(total.range.last + 1))
            ?: amountPattern.findAll(line.substring(0, total.range.first)).lastOrNull())?.value ?: return null
        val decimalSeparator = value.lastOrNull { it == '.' || it == ',' }
            ?.takeIf { value.length - value.lastIndexOf(it) - 1 == 2 }
        val normalized = if (decimalSeparator == null) value.filter(Char::isDigit)
        else value.substringBeforeLast(decimalSeparator).filter(Char::isDigit) + "." + value.substringAfterLast(decimalSeparator)
        val amount = runCatching { BigDecimal(normalized).setScale(2).toPlainString() }.getOrNull() ?: return null
        if (ExpenseRules.amountMinor(amount) == null) return null
        val currency = when {
            Regex("(?i)\\bEUR\\b|€").containsMatchIn(line) -> "EUR"
            Regex("(?i)\\bPEN\\b|S/").containsMatchIn(line) -> "PEN"
            Regex("(?i)\\bCOP\\b").containsMatchIn(line) -> "COP"
            Regex("(?i)\\bUSD\\b").containsMatchIn(line) -> "USD"
            else -> null
        }
        return ReceiptAmountProposal(amount, currency)
    }
}
