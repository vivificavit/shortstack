package com.vivificavit.shortstack.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Material3's DatePicker reports the chosen day as midnight UTC. Reading
 * that value in the device's time zone shifts it to the previous day
 * anywhere west of UTC, so always convert it via its UTC calendar date.
 */
object DateRanges {
    fun pickerMillisToLocalDate(pickerUtcMillis: Long): LocalDate =
        Instant.ofEpochMilli(pickerUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()

    /** The value the DatePicker uses for [date]. */
    fun localDateToPickerMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun timestampToLocalDate(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()

    /** Moves [timestamp] to [date], keeping its local time of day so same-day entries stay in order. */
    fun moveToDate(timestamp: Long, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long {
        val timeOfDay = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalTime()
        return date.atTime(timeOfDay).atZone(zone).toInstant().toEpochMilli()
    }

    /** Quick choices for the chart date range. */
    /** How long until the next local midnight, so work can wait for the day to change. */
    fun millisUntilNextDay(now: ZonedDateTime = ZonedDateTime.now()): Long =
        Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(now.zone)).toMillis()

    /** Quick picks for the Trends date range, each ending today. */
    enum class QuickRange { LAST_WEEK, LAST_MONTH, LAST_YEAR, ALL_TIME }

    /**
     * The picker start/end values for [range], counting back from [today] and
     * including it: the last 7 days, the last 30 days (whatever the month), or
     * from the day after this date a year ago, which is 366 days across a 29 February.
     * All time is open at both ends.
     */
    fun quickRange(range: QuickRange, today: LocalDate): Pair<Long?, Long?> {
        val start = when (range) {
            QuickRange.LAST_WEEK -> today.minusDays(6)
            QuickRange.LAST_MONTH -> today.minusDays(29)
            QuickRange.LAST_YEAR -> today.minusYears(1).plusDays(1)
            QuickRange.ALL_TIME -> return null to null
        }
        return localDateToPickerMillis(start) to localDateToPickerMillis(today)
    }

    /**
     * Turns the picker's start/end selections into an inclusive range of
     * epoch millis covering whole local days. Missing ends are open-ended,
     * and a reversed range is swapped.
     */
    fun toLocalRange(
        startPickerMillis: Long?,
        endPickerMillis: Long?,
        zone: ZoneId = ZoneId.systemDefault()
    ): LongRange {
        var startDate = startPickerMillis?.let(::pickerMillisToLocalDate)
        var endDate = endPickerMillis?.let(::pickerMillisToLocalDate)
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            startDate = endDate.also { endDate = startDate }
        }
        val start = startDate?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: 0L
        val end = endDate?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()?.minus(1)
            ?: Long.MAX_VALUE
        return start..end
    }
}
