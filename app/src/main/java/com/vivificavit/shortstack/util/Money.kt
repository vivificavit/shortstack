package com.vivificavit.shortstack.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Amounts are stored as whole cents (Long) so balances never pick up
 * floating-point rounding errors. Convert to dollars only for display.
 * "Cents" means the hundredth of whichever [AppCurrency] is in use.
 */
object Money {
    /** Largest single entry accepted: 1,000,000.00. */
    const val MAX_ENTRY_CENTS = 100_000_000L

    /** Digits with at most one decimal mark, a point or a comma, and two decimals. */
    private val AmountInput = Regex("^\\d*[.,]?\\d{0,2}$")

    /**
     * Whether [text] is what an amount field accepts as it's typed, including
     * nothing yet. A comma counts as the decimal mark, since that's the only
     * one some keyboards offer outside English-speaking regions.
     */
    fun isAmountInput(text: String): Boolean = AmountInput.matches(text)

    /**
     * Parses user input like "5", "5.5" or "5,50" into cents. Returns null for
     * blank, non-numeric, non-positive, too large, or more than two decimal
     * places. There's no thousands separator, so "1,234" is never read as 1234.
     */
    fun parseToCents(input: String): Long? {
        val cleaned = input.trim()
        if (!isAmountInput(cleaned) || cleaned.none { it.isDigit() }) return null
        val value = try {
            BigDecimal(cleaned.replace(',', '.'))
        } catch (e: NumberFormatException) {
            return null
        }
        if (value.signum() <= 0) return null
        val cents = value.movePointRight(2)
        if (cents > BigDecimal.valueOf(MAX_ENTRY_CENTS)) return null
        return cents.longValueExact()
    }

    /** Converts a legacy dollar amount (Double) to cents, rounding half-up. */
    fun dollarsToCents(dollars: Double): Long =
        BigDecimal.valueOf(dollars)
            .setScale(2, RoundingMode.HALF_UP)
            .movePointRight(2)
            .longValueExact()

    /** An amount for files and error messages, the same everywhere: 500 -> "5.00". */
    fun toPlain(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString()
}

/**
 * The currencies the app can show amounts in. Each has two decimal places, so
 * amounts stay whole cents whichever is chosen, and choosing another changes
 * only the symbol: nothing is converted. Stored and backed up by [name].
 */
enum class AppCurrency(val symbol: String) {
    USD("$"),
    CAD("$"),
    GBP("£"),
    EUR("€"),
    AUD("$"),
    NZD("$");

    companion object {
        fun fromCode(code: String?): AppCurrency? = entries.firstOrNull { it.name == code }

        /** The currency of [locale]'s region when the app offers it; otherwise US dollars. */
        fun forLocale(locale: Locale): AppCurrency =
            // A locale with no region, or a region with no currency, has none to give.
            fromCode(runCatching { Currency.getInstance(locale)?.currencyCode }.getOrNull()) ?: USD
    }
}

/**
 * Writes amounts in [currency] the way [locale] writes money: "$1,234.56" in
 * the US, "1.234,56 €" in Germany. Screens get one from LocalMoneyFormat.
 */
class MoneyFormat(val currency: AppCurrency, val locale: Locale) {
    private val javaCurrency: Currency = checkNotNull(Currency.getInstance(currency.name))

    /**
     * What the platform calls the currency in this locale, such as "CA$" or
     * "US$". The app shows one currency at a time, so its plain symbol says
     * enough and reads better; [format] swaps it in.
     */
    private val platformSymbol = javaCurrency.getSymbol(locale)

    private val twoDecimals = formatter(decimals = 2)
    private val noDecimals = formatter(decimals = 0)
    private val decimalMark = if (DecimalFormatSymbols.getInstance(locale).decimalSeparator == ',') ',' else '.'

    private fun formatter(decimals: Int): NumberFormat {
        val format = NumberFormat.getCurrencyInstance(locale)
        format.currency = javaCurrency
        format.minimumFractionDigits = decimals
        format.maximumFractionDigits = decimals
        return format
    }

    // NumberFormat isn't safe to share between threads, hence the lock.
    private fun write(amount: BigDecimal, with: NumberFormat): String =
        synchronized(with) { with.format(amount) }.replace(platformSymbol, currency.symbol)

    /** 123456 -> "$1,234.56". */
    fun format(cents: Long): String = write(BigDecimal.valueOf(cents, 2), twoDecimals)

    /** For chart axes: "$50" for a whole amount, "$2.50" otherwise. */
    fun formatShort(cents: Long): String =
        if (cents % 100L == 0L) write(BigDecimal.valueOf(cents / 100L), noDecimals) else format(cents)

    /** Plain text for an amount field, with this locale's decimal mark: 500 -> "5.00" or "5,00". */
    fun toInput(cents: Long): String = Money.toPlain(cents).replace('.', decimalMark)
}
