package com.vivificavit.shortstack.util

import com.vivificavit.shortstack.data.model.Frequency
import com.vivificavit.shortstack.data.model.RecurringEntry
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

/** When repeating entries come due. */
object RecurringMath {

    /** A safety cap on how many entries one schedule adds at once, however long the app sat unopened. */
    const val MAX_CATCH_UP = 120

    /**
     * When entry number [index] (from 0) of a schedule falls. Each one counts
     * from the start, so a monthly entry on the 31st lands on the last day of
     * shorter months and goes back to the 31st after.
     */
    fun occurrence(startTimestamp: Long, frequency: Frequency, index: Int, zone: ZoneId): ZonedDateTime {
        return Instant.ofEpochMilli(startTimestamp).atZone(zone).plus(span(frequency, index))
    }

    /** How long after a schedule's start its entry number [index] falls. */
    private fun span(frequency: Frequency, index: Int): Period = when (frequency) {
        Frequency.WEEKLY -> Period.ofWeeks(index)
        Frequency.EVERY_2_WEEKS -> Period.ofWeeks(2 * index)
        Frequency.MONTHLY -> Period.ofMonths(index)
    }

    /**
     * The timestamps of the entries [entry] should add by [now]: every one not
     * added yet whose day has come. Each is dated its own day, at the start's
     * time of day, but never later than [now].
     */
    fun dueTimestamps(entry: RecurringEntry, now: Long, zone: ZoneId): List<Long> {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return generateSequence(entry.occurrences) { it + 1 }
            .map { occurrence(entry.startTimestamp, entry.frequency, it, zone) }
            .takeWhile { !it.toLocalDate().isAfter(today) }
            .take(MAX_CATCH_UP)
            .map { minOf(it.toInstant().toEpochMilli(), now) }
            .toList()
    }

    /**
     * How many entries a new schedule first dated [start] adds straight away on
     * [today]: none for a start still to come, several for one in the past.
     */
    fun dueCount(start: LocalDate, frequency: Frequency, today: LocalDate): Int =
        generateSequence(0) { it + 1 }
            .map { start.plus(span(frequency, it)) }
            .takeWhile { !it.isAfter(today) }
            .take(MAX_CATCH_UP)
            .count()

    /**
     * The next date that is day [day] of its month, counting [from]. Months too
     * short to have one are skipped, so day 31 picked in November starts in December.
     */
    fun nextOnDayOfMonth(from: LocalDate, day: Int): LocalDate =
        generateSequence(YearMonth.from(from)) { it.plusMonths(1) }
            .filter { it.lengthOfMonth() >= day }
            .map { it.atDay(day) }
            .first { !it.isBefore(from) }

    /** The day the next entry will be added. */
    fun nextDate(entry: RecurringEntry, zone: ZoneId): LocalDate =
        occurrence(entry.startTimestamp, entry.frequency, entry.occurrences, zone).toLocalDate()
}
