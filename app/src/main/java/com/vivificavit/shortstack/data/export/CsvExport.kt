package com.vivificavit.shortstack.data.export

import com.vivificavit.shortstack.data.model.Transaction
import com.vivificavit.shortstack.data.model.TransactionType
import com.vivificavit.shortstack.util.DateRanges
import com.vivificavit.shortstack.util.Money
import java.time.ZoneId

/**
 * A spreadsheet-friendly copy of every entry, one row each. Export only:
 * restoring still uses the JSON backup, which keeps details CSV drops.
 */
object CsvExport {
    private val Header = listOf("Kid", "Date", "Note", "Category", "Type", "Amount")

    /** Characters that make Excel and Google Sheets treat a cell as a formula. */
    private val FormulaStarts = setOf('=', '+', '-', '@', '\t', '\r')

    fun encode(transactions: List<Transaction>, zone: ZoneId = ZoneId.systemDefault()): String {
        val rows = transactions
            .sortedWith(compareBy({ it.kidName.lowercase() }, { it.timestamp }, { it.id }))
            .map { tx ->
                val isIncome = tx.type == TransactionType.CREDIT
                listOf(
                    text(tx.kidName),
                    DateRanges.timestampToLocalDate(tx.timestamp, zone).toString(),
                    text(tx.title),
                    text(tx.category),
                    if (isIncome) "Income" else "Expense",
                    // Signed, so a SUM of the column gives the balance.
                    Money.toPlain(if (isIncome) tx.amountCents else -tx.amountCents)
                )
            }
        // The byte order mark tells Excel the file is UTF-8, so names like "Zoë" survive.
        // RFC 4180 rows end in CRLF.
        return "\uFEFF" + (listOf(Header) + rows).joinToString("") { it.joinToString(",") + "\r\n" }
    }

    /** Escapes text the user typed: defuses formulas, then quotes if needed. */
    private fun text(value: String): String {
        val safe = if (value.firstOrNull() in FormulaStarts) "'$value" else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + safe.replace("\"", "\"\"") + "\""
        } else {
            safe
        }
    }
}
