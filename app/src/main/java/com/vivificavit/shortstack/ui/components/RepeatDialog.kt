package com.vivificavit.shortstack.ui.components

import android.icu.text.MessageFormat
import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vivificavit.shortstack.R
import com.vivificavit.shortstack.data.model.Frequency
import com.vivificavit.shortstack.data.model.RecurringEntry
import com.vivificavit.shortstack.data.model.TransactionType
import com.vivificavit.shortstack.util.DateRanges
import com.vivificavit.shortstack.util.RecurringMath
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

private val FrequencyChoices = listOf(
    Frequency.WEEKLY to R.string.repeat_weekly,
    Frequency.EVERY_2_WEEKS to R.string.repeat_every_2_weeks,
    Frequency.MONTHLY to R.string.repeat_monthly
)

/**
 * A schedule in words: "Every Saturday", "Every other Saturday" or "Every month
 * on the 14th", for one whose entries count from [date].
 */
@Composable
fun repeatSummary(frequency: Frequency, date: LocalDate): String {
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    return when (frequency) {
        Frequency.WEEKLY -> stringResource(R.string.repeat_summary_weekly, weekday)
        Frequency.EVERY_2_WEEKS -> stringResource(R.string.repeat_summary_2_weeks, weekday)
        Frequency.MONTHLY -> stringResource(R.string.repeat_summary_monthly, ordinal(date.dayOfMonth))
    }
}

/** [entry]'s schedule in words. It counts from its start, so a monthly one keeps saying "the 31st". */
@Composable
fun repeatSummary(entry: RecurringEntry, zone: ZoneId = ZoneId.systemDefault()): String =
    repeatSummary(entry.frequency, DateRanges.timestampToLocalDate(entry.startTimestamp, zone))

/** A date with its weekday, like "Sat, Nov 14"; the year is added when it isn't [today]'s. */
fun formatRepeatDate(date: LocalDate, today: LocalDate): String {
    val locale = Locale.getDefault()
    val skeleton = if (date.year == today.year) "EEEMMMd" else "EEEyMMMd"
    return date.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale))
}

/**
 * The Repeat part of the entry sheet. A new entry has a switch, which asks how
 * it repeats when turned on ([onToggle]); once it repeats, or for a schedule
 * being edited ([editingSchedule]), it says how and when, and [onChange] asks again.
 * [date] is its first or next entry and [repeatsFrom] the date its days count
 * from, which differ only for a monthly schedule in a month too short for its day.
 */
@Composable
fun RepeatSection(
    type: TransactionType,
    repeat: Frequency?,
    date: LocalDate,
    repeatsFrom: LocalDate,
    editingSchedule: Boolean,
    onToggle: (Boolean) -> Unit,
    onChange: () -> Unit
) {
    val today = LocalToday.current
    val on = repeat != null
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(
            1.dp,
            if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        // A schedule always repeats; it stops from the Repeating entries list.
                        if (editingSchedule) {
                            Modifier
                        } else {
                            Modifier.toggleable(value = on, role = Role.Switch, onValueChange = onToggle)
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Repeat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        when {
                            editingSchedule -> R.string.repeat_label
                            type == TransactionType.CREDIT -> R.string.repeat_toggle_income
                            else -> R.string.repeat_toggle_expense
                        }
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                // The whole row toggles, so the switch itself doesn't take clicks.
                if (!editingSchedule) Switch(checked = on, onCheckedChange = null)
            }
            if (repeat != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.repeat_change),
                            onClick = onChange
                        )
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = repeatSummary(repeat, if (repeat == Frequency.MONTHLY) repeatsFrom else date),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(
                                if (editingSchedule) R.string.repeat_next_one else R.string.repeat_first_one,
                                if (date == today) {
                                    stringResource(R.string.entry_date_today)
                                } else {
                                    formatRepeatDate(date, today)
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = stringResource(R.string.repeat_change),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Asks how an entry repeats: how often, on which day, and when the first one
 * is. For a schedule being edited ([editingSchedule]) the date is its next
 * entry instead, which can't be moved into the past. The day of the week and
 * the date are the same choice seen two ways, so picking either moves the other.
 * [initialRepeatsFrom] is the date a monthly schedule's day comes from, when
 * [initialDate] is in a month too short for it; [onConfirm] gets it back, or
 * the chosen date once the day has been changed.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RepeatDialog(
    initialFrequency: Frequency?,
    initialDate: LocalDate,
    editingSchedule: Boolean,
    onConfirm: (frequency: Frequency, date: LocalDate, repeatsFrom: LocalDate) -> Unit,
    initialRepeatsFrom: LocalDate = initialDate,
    onDismiss: () -> Unit
) {
    val today = LocalToday.current
    var frequency by rememberSaveable { mutableStateOf(initialFrequency ?: Frequency.WEEKLY) }
    var date by rememberSaveable { mutableStateOf(initialDate) }
    var repeatsFrom by rememberSaveable { mutableStateOf(initialRepeatsFrom) }
    // The day of the month is kept while the date is left alone, so trying another
    // frequency and coming back to monthly doesn't lose a schedule's 31st.
    val monthlyFrom = if (frequency == Frequency.MONTHLY) repeatsFrom else date
    val moveTo = { day: LocalDate ->
        date = day
        repeatsFrom = day
    }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    val locale = Locale.getDefault()
    val weekdays = remember(locale) {
        val first = WeekFields.of(locale).firstDayOfWeek
        (0L..6L).map { first.plus(it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.repeat_dialog_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.selectableGroup()) {
                    FrequencyChoices.forEach { (choice, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = frequency == choice,
                                    role = Role.RadioButton,
                                    onClick = { frequency = choice }
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = frequency == choice, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(label),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                if (frequency == Frequency.MONTHLY) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.repeat_on_day_label),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        DayOfMonthGrid(
                            selected = monthlyFrom.dayOfMonth,
                            // Picking a day moves the date to the next one, counting today.
                            onSelect = { day ->
                                if (monthlyFrom.dayOfMonth != day) moveTo(RecurringMath.nextOnDayOfMonth(today, day))
                            }
                        )
                        if (monthlyFrom.dayOfMonth > 28) {
                            Text(
                                text = stringResource(R.string.repeat_short_months),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.repeat_on_label),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            weekdays.forEach { day ->
                                FilterChip(
                                    selected = date.dayOfWeek == day,
                                    // Picking a day moves the date to the next one, counting today.
                                    onClick = { if (date.dayOfWeek != day) moveTo(nextOrSame(today, day)) },
                                    label = { Text(day.getDisplayName(TextStyle.SHORT, locale)) }
                                )
                            }
                        }
                    }
                }

                OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            if (editingSchedule) R.string.repeat_next_one else R.string.repeat_first_one,
                            if (date == today) stringResource(R.string.entry_date_today) else formatRepeatDate(date, today)
                        )
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = repeatSummary(frequency, monthlyFrom),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when {
                            editingSchedule -> stringResource(R.string.repeat_note_editing)
                            date == today -> stringResource(R.string.repeat_note_today)
                            date.isAfter(today) -> stringResource(R.string.repeat_note_future)
                            else -> {
                                val count = RecurringMath.dueCount(date, frequency, today)
                                pluralStringResource(R.plurals.repeat_note_catch_up, count, count)
                            }
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(frequency, date, repeatsFrom) }) { Text(stringResource(R.string.action_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )

    if (pickingDate) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = DateRanges.localDateToPickerMillis(date),
            selectableDates = object : SelectableDates {
                // A new schedule can start any day: back-dated ones catch up. One already
                // running only moves forward, so it never fills in entries that weren't due.
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    !editingSchedule || !DateRanges.pickerMillisToLocalDate(utcTimeMillis).isBefore(today)

                override fun isSelectableYear(year: Int): Boolean = !editingSchedule || year >= today.year
            }
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { moveTo(DateRanges.pickerMillisToLocalDate(it)) }
                        pickingDate = false
                    }
                ) {
                    Text(stringResource(R.string.action_apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { pickingDate = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** The smallest a day in [DayOfMonthGrid] gets, so each is easy to tap. */
private val DayCellSize = 48.dp

/**
 * The days of a month, 1 to 31, with [selected] filled in. A row holds as many
 * as fit at a comfortable size to tap, up to seven: five in a phone's dialog.
 */
@Composable
private fun DayOfMonthGrid(selected: Int, onSelect: (Int) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = (maxWidth / DayCellSize).toInt().coerceIn(1, 7)
        Column(modifier = Modifier.selectableGroup()) {
            (1..31).chunked(columns).forEach { days ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    days.forEach { day ->
                        val isSelected = day == selected
                        val label = stringResource(R.string.repeat_day_of_month, day)
                        // The whole cell takes the tap; the circle inside only marks the choice.
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(DayCellSize)
                                .clip(MaterialTheme.shapes.small)
                                .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(day) })
                                .semantics { contentDescription = label },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = day.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                    // Keeps the last, short row lined up under the others.
                    repeat(columns - days.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** [day] as the locale says it in a date: "15th" in English. */
private fun ordinal(day: Int): String = MessageFormat("{0,ordinal}", Locale.getDefault()).format(arrayOf(day))

private fun nextOrSame(from: LocalDate, day: DayOfWeek): LocalDate = from.with(TemporalAdjusters.nextOrSame(day))
