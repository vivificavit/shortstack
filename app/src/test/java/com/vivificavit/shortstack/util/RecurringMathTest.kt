package com.vivificavit.shortstack.util

import com.vivificavit.shortstack.data.model.Frequency
import com.vivificavit.shortstack.data.model.RecurringEntry
import com.vivificavit.shortstack.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class RecurringMathTest {
    private val zone = ZoneOffset.UTC

    private fun millis(date: String, time: String = "09:00") =
        LocalDateTime.parse("${date}T$time").toInstant(zone).toEpochMilli()

    private fun entry(start: String, frequency: Frequency, occurrences: Int = 0) = RecurringEntry(
        kidName = "Sam", title = "Allowance", amountCents = 1_000, type = TransactionType.CREDIT,
        category = "Allowance", frequency = frequency, startTimestamp = millis(start), occurrences = occurrences
    )

    private fun dueDates(entry: RecurringEntry, now: String) =
        RecurringMath.dueTimestamps(entry, millis(now, "12:00"), zone).map {
            java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toString()
        }

    @Test
    fun monthlyOnThe31stUsesTheLastDayOfShortMonthsThenGoesBack() {
        val dates = (0..3).map { RecurringMath.occurrence(millis("2027-01-31"), Frequency.MONTHLY, it, zone).toLocalDate() }
        assertEquals(listOf("2027-01-31", "2027-02-28", "2027-03-31", "2027-04-30"), dates.map { it.toString() })
        // In a leap year February has its 29th.
        assertEquals(
            LocalDate.parse("2028-02-29"),
            RecurringMath.occurrence(millis("2028-01-31"), Frequency.MONTHLY, 1, zone).toLocalDate()
        )
    }

    @Test
    fun catchesUpOnEveryMissedDayButNotTomorrows() {
        val weekly = entry("2026-10-16", Frequency.WEEKLY)
        assertEquals(listOf("2026-10-16", "2026-10-23", "2026-10-30", "2026-11-06"), dueDates(weekly, "2026-11-06"))
        // Already added three: only the fourth is left, and nothing more until next week.
        assertEquals(listOf("2026-11-06"), dueDates(weekly.copy(occurrences = 3), "2026-11-06"))
        assertEquals(emptyList<String>(), dueDates(weekly.copy(occurrences = 4), "2026-11-12"))
        assertEquals(listOf("2026-10-16", "2026-10-30"), dueDates(entry("2026-10-16", Frequency.EVERY_2_WEEKS), "2026-11-12"))
    }

    @Test
    fun anEntryDueLaterTodayIsDatedNowAndCatchUpIsCapped() {
        val evening = entry("2026-11-06", Frequency.WEEKLY).copy(startTimestamp = millis("2026-11-06", "18:00"))
        assertEquals(listOf(millis("2026-11-06", "12:00")), RecurringMath.dueTimestamps(evening, millis("2026-11-06", "12:00"), zone))
        val ancient = entry("1990-01-05", Frequency.WEEKLY)
        assertEquals(RecurringMath.MAX_CATCH_UP, RecurringMath.dueTimestamps(ancient, millis("2026-11-06"), zone).size)
    }

    @Test
    fun countsWhatANewScheduleAddsStraightAway() {
        val today = LocalDate.parse("2026-11-14")
        assertEquals(0, RecurringMath.dueCount(LocalDate.parse("2026-11-15"), Frequency.WEEKLY, today))
        assertEquals(1, RecurringMath.dueCount(today, Frequency.WEEKLY, today))
        // Oct 31, Nov 7 and Nov 14; every two weeks skips the middle one.
        assertEquals(3, RecurringMath.dueCount(LocalDate.parse("2026-10-31"), Frequency.WEEKLY, today))
        assertEquals(2, RecurringMath.dueCount(LocalDate.parse("2026-10-31"), Frequency.EVERY_2_WEEKS, today))
        assertEquals(1, RecurringMath.dueCount(LocalDate.parse("2026-10-31"), Frequency.MONTHLY, today))
    }

    @Test
    fun aPickedDayOfTheMonthStartsTheNextTimeItComesAround() {
        val today = LocalDate.parse("2026-11-16")
        assertEquals(today, RecurringMath.nextOnDayOfMonth(today, 16))
        assertEquals(LocalDate.parse("2026-11-20"), RecurringMath.nextOnDayOfMonth(today, 20))
        assertEquals(LocalDate.parse("2026-12-15"), RecurringMath.nextOnDayOfMonth(today, 15))
        // November has no 31st, so it waits for December rather than settling on the 30th.
        assertEquals(LocalDate.parse("2026-12-31"), RecurringMath.nextOnDayOfMonth(today, 31))
        // And from late January, the 30th skips February altogether.
        assertEquals(LocalDate.parse("2027-03-30"), RecurringMath.nextOnDayOfMonth(LocalDate.parse("2027-01-31"), 30))
    }
}
