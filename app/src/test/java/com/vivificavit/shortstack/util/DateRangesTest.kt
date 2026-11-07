package com.vivificavit.shortstack.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class DateRangesTest {
    private val pacific = ZoneId.of("America/Los_Angeles")

    /** What Material3's DatePicker reports when the user taps [date]. */
    private fun pickerMillis(date: LocalDate) =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun selectedDayCoversThatWholeLocalDay() {
        val oct30 = LocalDate.of(2026, 10, 30)
        val range = DateRanges.toLocalRange(pickerMillis(oct30), pickerMillis(oct30), pacific)

        val start = ZonedDateTime.of(2026, 10, 30, 0, 0, 0, 0, pacific).toInstant().toEpochMilli()
        val nextDay = ZonedDateTime.of(2026, 10, 31, 0, 0, 0, 0, pacific).toInstant().toEpochMilli()
        assertEquals(start, range.first)
        assertEquals(nextDay - 1, range.last)
    }

    @Test
    fun eveningEntryOnEndDateIsIncluded() {
        val oct30 = LocalDate.of(2026, 10, 30)
        val range = DateRanges.toLocalRange(null, pickerMillis(oct30), pacific)
        val eightPm = ZonedDateTime.of(2026, 10, 30, 20, 0, 0, 0, pacific).toInstant().toEpochMilli()
        assert(eightPm in range)
    }

    @Test
    fun openEndsAndReversedRange() {
        assertEquals(0L..Long.MAX_VALUE, DateRanges.toLocalRange(null, null, pacific))
        val a = pickerMillis(LocalDate.of(2026, 1, 1))
        val b = pickerMillis(LocalDate.of(2026, 2, 1))
        assertEquals(
            DateRanges.toLocalRange(a, b, pacific),
            DateRanges.toLocalRange(b, a, pacific)
        )
    }

    @Test
    fun movingToAnotherDayKeepsTimeOfDay() {
        val evening = ZonedDateTime.of(2026, 10, 30, 19, 45, 0, 0, pacific).toInstant().toEpochMilli()
        val moved = DateRanges.moveToDate(evening, LocalDate.of(2026, 10, 2), pacific)
        assertEquals(
            ZonedDateTime.of(2026, 10, 2, 19, 45, 0, 0, pacific).toInstant().toEpochMilli(),
            moved
        )
        assertEquals(LocalDate.of(2026, 10, 2), DateRanges.timestampToLocalDate(moved, pacific))
    }

    @Test
    fun quickRangesCountBackFromTodayIncludingIt() {
        val today = LocalDate.of(2026, 11, 9)
        fun range(quick: DateRanges.QuickRange, on: LocalDate = today) = DateRanges.quickRange(quick, on)
        assertEquals(pickerMillis(LocalDate.of(2026, 11, 3)) to pickerMillis(today), range(DateRanges.QuickRange.LAST_WEEK))
        assertEquals(pickerMillis(LocalDate.of(2026, 10, 11)) to pickerMillis(today), range(DateRanges.QuickRange.LAST_MONTH))
        assertEquals(pickerMillis(LocalDate.of(2025, 11, 10)) to pickerMillis(today), range(DateRanges.QuickRange.LAST_YEAR))
        assertEquals(null to null, range(DateRanges.QuickRange.ALL_TIME))
        // Across a 29 February, the year back is 366 days.
        val afterLeapDay = LocalDate.of(2028, 3, 1)
        assertEquals(
            pickerMillis(LocalDate.of(2027, 3, 2)) to pickerMillis(afterLeapDay),
            range(DateRanges.QuickRange.LAST_YEAR, afterLeapDay)
        )
    }

    @Test
    fun timeUntilMidnightIsMeasuredInLocalTime() {
        val evening = java.time.ZonedDateTime.of(2026, 11, 6, 23, 30, 0, 0, ZoneOffset.UTC)
        assertEquals(30 * 60 * 1_000L, DateRanges.millisUntilNextDay(evening))
    }
}
