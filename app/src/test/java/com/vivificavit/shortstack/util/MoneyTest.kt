package com.vivificavit.shortstack.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class MoneyTest {
    private fun locale(tag: String): Locale = Locale.forLanguageTag(tag)

    /** Some locales put a non-breaking space before the symbol; which one varies by Java version. */
    private fun String.withPlainSpaces() = replace(' ', ' ').replace(' ', ' ')

    @Test
    fun parsesValidAmounts() {
        assertEquals(500L, Money.parseToCents("5"))
        assertEquals(550L, Money.parseToCents("5.5"))
        assertEquals(1999L, Money.parseToCents("19.99"))
        assertEquals(1L, Money.parseToCents("0.01"))
        assertEquals(50L, Money.parseToCents(".5"))
        assertEquals(Money.MAX_ENTRY_CENTS, Money.parseToCents("1000000"))
    }

    @Test
    fun aCommaIsADecimalMarkToo() {
        assertEquals(550L, Money.parseToCents("5,5"))
        assertEquals(1999L, Money.parseToCents("19,99"))
        assertEquals(1L, Money.parseToCents("0,01"))
    }

    @Test
    fun rejectsInvalidAmounts() {
        listOf("", " ", "0", "0.00", "-5", "abc", "NaN", "Infinity", "1.001", "1000000.01", "1e100000", ".", ",")
            .forEach { assertNull("expected null for \"$it\"", Money.parseToCents(it)) }
        // Never guessed at: with a comma as a decimal mark, "1,234" could be read two ways.
        listOf("1,234", "1,234.56", "1.234,56", "5.5.5", "$5")
            .forEach { assertNull("expected null for \"$it\"", Money.parseToCents(it)) }
    }

    @Test
    fun amountFieldsTakeDigitsAndOneDecimalMark() {
        listOf("", "5", "5.", "5,", ".5", "12.34", "12,34").forEach { assertTrue(it, Money.isAmountInput(it)) }
        listOf("5.123", "1,2,3", "5.5.", "-5", "$5", "5 ", "abc").forEach { assertTrue(it, !Money.isAmountInput(it)) }
    }

    @Test
    fun convertsLegacyDollarsWithoutDrift() {
        assertEquals(30L, Money.dollarsToCents(0.1 + 0.2))
        assertEquals(115L, Money.dollarsToCents(1.15))
        assertEquals(1999L, Money.dollarsToCents(19.99))
    }

    @Test
    fun formatsInTheChosenCurrencyAndThePhonesNumberStyle() {
        val dollars = MoneyFormat(AppCurrency.USD, Locale.US)
        assertEquals("$1,234.56", dollars.format(123456L))
        assertEquals("-$0.05", dollars.format(-5L))
        assertEquals("£1,234.56", MoneyFormat(AppCurrency.GBP, Locale.UK).format(123456L))
        assertEquals("€1,234.56", MoneyFormat(AppCurrency.EUR, locale("en-IE")).format(123456L))
        assertEquals("1.234,56 €", MoneyFormat(AppCurrency.EUR, Locale.GERMANY).format(123456L).withPlainSpaces())
    }

    @Test
    fun showsThePlainSymbolWhereverThePhoneIsFrom() {
        // Left to the platform, these would read "CA$5.00", "US$5.00", "A$5.00" and "NZ$5.00".
        assertEquals("$5.00", MoneyFormat(AppCurrency.CAD, Locale.US).format(500L))
        assertEquals("$5.00", MoneyFormat(AppCurrency.USD, Locale.CANADA).format(500L))
        assertEquals("$5.00", MoneyFormat(AppCurrency.AUD, Locale.UK).format(500L))
        assertEquals("$5.00", MoneyFormat(AppCurrency.NZD, locale("en-AU")).format(500L))
        assertEquals("£5.00", MoneyFormat(AppCurrency.GBP, Locale.US).format(500L))
    }

    @Test
    fun chartAxesDropTheDecimalsFromWholeAmounts() {
        val dollars = MoneyFormat(AppCurrency.USD, Locale.US)
        assertEquals("$50", dollars.formatShort(5_000L))
        assertEquals("$2.50", dollars.formatShort(250L))
        assertEquals("$0", dollars.formatShort(0L))
        assertEquals("50 €", MoneyFormat(AppCurrency.EUR, Locale.GERMANY).formatShort(5_000L).withPlainSpaces())
    }

    @Test
    fun amountFieldsStartWithThePhonesDecimalMark() {
        assertEquals("5.00", MoneyFormat(AppCurrency.USD, Locale.US).toInput(500L))
        assertEquals("5,00", MoneyFormat(AppCurrency.EUR, Locale.GERMANY).toInput(500L))
        // Files always use a point, whatever the phone's region.
        assertEquals("5.00", Money.toPlain(500L))
        assertEquals("-12.34", Money.toPlain(-1234L))
    }

    @Test
    fun startsWithTheCurrencyOfThePhonesRegion() {
        assertEquals(AppCurrency.USD, AppCurrency.forLocale(Locale.US))
        assertEquals(AppCurrency.CAD, AppCurrency.forLocale(Locale.CANADA))
        assertEquals(AppCurrency.CAD, AppCurrency.forLocale(Locale.CANADA_FRENCH))
        assertEquals(AppCurrency.GBP, AppCurrency.forLocale(Locale.UK))
        assertEquals(AppCurrency.EUR, AppCurrency.forLocale(locale("en-IE")))
        assertEquals(AppCurrency.EUR, AppCurrency.forLocale(Locale.GERMANY))
        assertEquals(AppCurrency.AUD, AppCurrency.forLocale(locale("en-AU")))
        assertEquals(AppCurrency.NZD, AppCurrency.forLocale(locale("en-NZ")))
        // A region whose currency the app doesn't offer, and a language with no region at all.
        assertEquals(AppCurrency.USD, AppCurrency.forLocale(locale("en-IN")))
        assertEquals(AppCurrency.USD, AppCurrency.forLocale(Locale.ENGLISH))
    }

    @Test
    fun currencyCodesRoundTrip() {
        AppCurrency.entries.forEach { assertEquals(it, AppCurrency.fromCode(it.name)) }
        assertNull(AppCurrency.fromCode("JPY"))
        assertNull(AppCurrency.fromCode(null))
    }
}
