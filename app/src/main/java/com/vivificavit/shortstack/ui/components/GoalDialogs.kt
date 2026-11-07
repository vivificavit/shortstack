package com.vivificavit.shortstack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vivificavit.shortstack.R
import com.vivificavit.shortstack.data.model.Goal
import com.vivificavit.shortstack.ui.icons.EntryIcons
import com.vivificavit.shortstack.ui.screens.formatShortDate
import com.vivificavit.shortstack.util.DateRanges
import com.vivificavit.shortstack.util.GoalMath
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.vivificavit.shortstack.ui.theme.CreditGreen
import com.vivificavit.shortstack.ui.viewmodel.GoalHistoryItem
import com.vivificavit.shortstack.ui.viewmodel.GoalProgress
import com.vivificavit.shortstack.ui.viewmodel.GoalsState
import com.vivificavit.shortstack.util.Money

/** What can be done to a goal, from its card, its long-press menu or its details sheet. */
interface GoalActions {
    fun open(goal: Goal)
    fun addMoney(goal: Goal)
    fun moveMoney(goal: Goal)
    fun edit(goal: Goal)
    fun buy(goal: Goal)
    fun delete(goal: Goal)
}

/** Amount fields accept digits with up to two decimal places. */
private val AmountInput = Regex("^\\d*\\.?\\d{0,2}$")

/** The goal's icon on a soft green circle; [muted] once it's been bought. */
@Composable
fun GoalIcon(icon: ImageVector, modifier: Modifier = Modifier, muted: Boolean = false) {
    val tint = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else CreditGreen
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun GoalProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { fraction },
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp),
        color = CreditGreen,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {}
    )
}

/** Adds a goal, or edits [existing]: its name, what it costs and its icon. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalEditDialog(
    existing: Goal?,
    onDismiss: () -> Unit,
    onSave: (name: String, targetCents: Long, icon: String?, targetDay: Long?) -> Unit
) {
    var nameInput by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var targetInput by rememberSaveable { mutableStateOf(existing?.let { Money.toInput(it.targetCents) } ?: "") }
    var iconKey by rememberSaveable { mutableStateOf(existing?.icon) }
    var pickingIcon by rememberSaveable { mutableStateOf(false) }
    var targetDay by rememberSaveable { mutableStateOf(existing?.targetDay) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var inputError by remember { mutableStateOf<Int?>(null) }

    fun trySave() {
        val cents = Money.parseToCents(targetInput)
        when {
            nameInput.isBlank() -> inputError = R.string.goal_name_error
            cents == null -> inputError = R.string.entry_amount_error
            else -> onSave(nameInput.trim(), cents, iconKey, targetDay)
        }
    }

    val nameFocus = remember { FocusRequester() }
    if (existing == null) {
        LaunchedEffect(Unit) { nameFocus.requestFocus() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(if (existing == null) R.string.goal_new_title else R.string.goal_edit_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = {
                        nameInput = it
                        inputError = null
                    },
                    label = { Text(stringResource(R.string.goal_name_label)) },
                    placeholder = { Text(stringResource(R.string.goal_name_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nameFocus)
                )
                OutlinedTextField(
                    value = targetInput,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.matches(AmountInput)) {
                            targetInput = newValue
                            inputError = null
                        }
                    },
                    label = { Text(stringResource(R.string.goal_target_label)) },
                    placeholder = { Text(stringResource(R.string.entry_amount_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { trySave() }),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { pickingDate = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = targetDay?.let { stringResource(R.string.goal_buy_by_date, formatBuyByDate(it)) }
                                ?: stringResource(R.string.goal_buy_by_label)
                        )
                    }
                    if (targetDay != null) {
                        IconButton(onClick = { targetDay = null }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.cd_clear_buy_by)
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GoalIcon(EntryIcons.forGoal(nameInput, iconKey))
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedButton(onClick = { pickingIcon = true }) {
                        Text(stringResource(R.string.goal_choose_icon))
                    }
                }
                inputError?.let { errorRes ->
                    Text(
                        text = stringResource(errorRes, Money.format(1L), Money.format(Money.MAX_ENTRY_CENTS)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { trySave() }) {
                Text(stringResource(if (existing == null) R.string.goals_add else R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )

    if (pickingDate) {
        val tomorrow = LocalToday.current.plusDays(1)
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = targetDay?.let { DateRanges.localDateToPickerMillis(LocalDate.ofEpochDay(it)) },
            selectableDates = remember(tomorrow) {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) =
                        !DateRanges.pickerMillisToLocalDate(utcTimeMillis).isBefore(tomorrow)

                    override fun isSelectableYear(year: Int) = year >= tomorrow.year
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let {
                            targetDay = DateRanges.pickerMillisToLocalDate(it).toEpochDay()
                        }
                        pickingDate = false
                    }
                ) {
                    Text(stringResource(R.string.action_apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { pickingDate = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (pickingIcon) {
        IconPickerDialog(
            title = stringResource(R.string.goal_icon_title),
            hint = stringResource(R.string.goal_icon_hint),
            chosenKey = iconKey,
            suggested = remember(nameInput) { EntryIcons.suggestedFor(nameInput, emptyList()) },
            onPick = { key ->
                iconKey = key
                pickingIcon = false
            },
            onDismiss = { pickingIcon = false }
        )
    }
}

/** Somewhere money can be moved from or to: a goal, or pocket money when [goalId] is null. */
private data class MoneyPlace(val goalId: Long?, val name: String, val availableCents: Long)

/**
 * Moves money between pocket money and goals, or from one goal to another.
 * [onMove] gets null for pocket money.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoveMoneyDialog(
    goals: GoalsState,
    initialFromGoalId: Long?,
    initialToGoalId: Long?,
    onDismiss: () -> Unit,
    onMove: (fromGoalId: Long?, toGoalId: Long?, amountCents: Long) -> Unit
) {
    val pocketName = stringResource(R.string.goals_pocket_money_short)
    val places = listOf(MoneyPlace(null, pocketName, goals.pocketCents.coerceAtLeast(0L))) +
        goals.active.map { MoneyPlace(it.goal.id, it.goal.name, it.savedCents) }
    var fromId by rememberSaveable { mutableStateOf(initialFromGoalId) }
    var toId by rememberSaveable { mutableStateOf(initialToGoalId) }
    var amountInput by rememberSaveable { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    val available = places.firstOrNull { it.goalId == fromId }?.availableCents ?: 0L

    val amountFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { amountFocus.requestFocus() }

    val sameError = stringResource(R.string.move_same_error)
    val amountError = stringResource(R.string.entry_amount_error, Money.format(1L), Money.format(available))
    fun tryMove() {
        val cents = Money.parseToCents(amountInput)
        when {
            fromId == toId -> inputError = sameError
            cents == null || cents > available -> inputError = amountError
            else -> onMove(fromId, toId, cents)
        }
    }

    // Ids can't say "none disabled", since null already means pocket money.
    @Composable
    fun PlaceChips(selectedId: Long?, isDisabled: (MoneyPlace) -> Boolean, onPick: (Long?) -> Unit) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            places.forEach { place ->
                FilterChip(
                    selected = place.goalId == selectedId,
                    enabled = !isDisabled(place),
                    onClick = {
                        onPick(place.goalId)
                        inputError = null
                    },
                    label = {
                        Text(stringResource(R.string.move_place, place.name, Money.format(place.availableCents)))
                    }
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.move_from), style = MaterialTheme.typography.labelLarge)
                PlaceChips(selectedId = fromId, isDisabled = { false }) { picked ->
                    fromId = picked
                    if (toId == picked) toId = places.firstOrNull { it.goalId != picked }?.goalId
                }
                Text(stringResource(R.string.move_to), style = MaterialTheme.typography.labelLarge)
                PlaceChips(selectedId = toId, isDisabled = { it.goalId == fromId }) { toId = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { newValue ->
                            if (newValue.isEmpty() || newValue.matches(AmountInput)) {
                                amountInput = newValue
                                inputError = null
                            }
                        },
                        label = { Text(stringResource(R.string.entry_amount_label)) },
                        placeholder = { Text(stringResource(R.string.entry_amount_hint)) },
                        supportingText = { Text(stringResource(R.string.move_up_to, Money.format(available))) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { tryMove() }),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(amountFocus)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    AssistChip(
                        onClick = {
                            amountInput = Money.toInput(available)
                            inputError = null
                        },
                        label = { Text(stringResource(R.string.move_all)) }
                    )
                }
                inputError?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { tryMove() }) {
                Text(stringResource(R.string.move_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/** A goal's progress, what can be done with it, and where its money came from and went. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GoalDetailSheet(
    progress: GoalProgress,
    history: List<GoalHistoryItem>,
    actions: GoalActions,
    onDismiss: () -> Unit
) {
    val goal = progress.goal
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Every action opens something else, so the sheet steps aside first.
    fun then(action: (Goal) -> Unit) {
        onDismiss()
        action(goal)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GoalIcon(EntryIcons.forGoal(goal), muted = progress.isBought)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = goal.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            if (progress.isBought) {
                val boughtOn = goal.completedAt?.let(::formatShortDate).orEmpty()
                Text(
                    text = progress.paidCents?.let {
                        stringResource(R.string.goal_bought_for, Money.format(it), boughtOn)
                    } ?: stringResource(R.string.goal_bought_on, boughtOn),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                Text(
                    text = stringResource(
                        R.string.goal_progress_saved,
                        Money.format(progress.savedCents),
                        Money.format(goal.targetCents)
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
                GoalProgressBar(progress.fraction)
                Text(
                    text = if (progress.isReached) {
                        stringResource(R.string.goal_reached)
                    } else {
                        goalSaveMoreText(progress)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (progress.isReached) CreditGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!progress.isBought) {
                    SheetAction(R.string.goal_action_add_money, Icons.Default.Add) { then(actions::addMoney) }
                    SheetAction(R.string.goal_action_move_money, Icons.Default.SwapHoriz) { then(actions::moveMoney) }
                    SheetAction(R.string.goal_action_buy, Icons.Default.ShoppingBag) { then(actions::buy) }
                    SheetAction(R.string.goal_action_edit, Icons.Default.Edit) { then(actions::edit) }
                }
                TextButton(onClick = { then(actions::delete) }) {
                    Text(
                        text = stringResource(R.string.goal_action_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            HorizontalDivider()
            Text(
                text = stringResource(R.string.goal_history_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() }
            )
            if (history.isEmpty()) {
                Text(
                    text = stringResource(R.string.goal_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            history.forEach { item -> GoalHistoryRow(item) }
        }
    }
}

@Composable
private fun SheetAction(labelRes: Int, icon: ImageVector, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(stringResource(labelRes))
    }
}

@Composable
private fun GoalHistoryRow(item: GoalHistoryItem) {
    val detail = item.detail
    val text = when (item.kind) {
        GoalHistoryItem.Kind.ADDED -> stringResource(R.string.goal_history_added)
        GoalHistoryItem.Kind.TAKEN_OUT -> stringResource(R.string.goal_history_taken_out)
        GoalHistoryItem.Kind.MOVED_IN -> stringResource(R.string.goal_history_moved_in, detail.orEmpty())
        GoalHistoryItem.Kind.MOVED_OUT -> stringResource(R.string.goal_history_moved_out, detail.orEmpty())
        GoalHistoryItem.Kind.SPLIT -> detail?.let { stringResource(R.string.goal_history_split, it) }
            ?: stringResource(R.string.goal_history_split_unknown)
        GoalHistoryItem.Kind.BOUGHT -> detail?.let { stringResource(R.string.goal_history_bought, it) }
            ?: stringResource(R.string.goal_history_bought_unknown)
        GoalHistoryItem.Kind.COVERED -> detail?.let { stringResource(R.string.goal_history_covered, it) }
            ?: stringResource(R.string.goal_history_covered_unknown)
        GoalHistoryItem.Kind.ADJUSTED -> stringResource(R.string.goal_history_adjusted)
    }
    val incoming = item.amountCents > 0L
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = remember(item.timestamp) { formatShortDate(item.timestamp) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = (if (incoming) "+" else "-") + Money.format(kotlin.math.abs(item.amountCents)),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (incoming) CreditGreen else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** "Dec 20, 2026": the buy-by day, written out so it can't be mistaken for another year. */
internal fun formatBuyByDate(targetDay: Long): String =
    LocalDate.ofEpochDay(targetDay).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

/**
 * The line under a goal's progress bar while it's still short: how much more to
 * save, and, with a buy-by day, by when and about how much a week.
 */
@Composable
internal fun goalSaveMoreText(progress: GoalProgress): String {
    val remaining = Money.format(progress.remainingCents)
    val targetDay = progress.goal.targetDay ?: return stringResource(R.string.goal_save_more, remaining)
    val today = LocalToday.current.toEpochDay()
    return when (val pace = GoalMath.pace(progress.remainingCents, today, targetDay)) {
        is GoalMath.Pace.Weekly -> stringResource(
            R.string.goal_save_more_weekly, remaining, formatBuyByDate(targetDay), Money.format(pace.cents)
        )
        GoalMath.Pace.ThisWeek -> stringResource(
            R.string.goal_save_more_by,
            remaining,
            LocalDate.ofEpochDay(targetDay).format(DateTimeFormatter.ofPattern("EEEE"))
        )
        GoalMath.Pace.Today -> stringResource(R.string.goal_buy_by_today, remaining)
        GoalMath.Pace.Passed -> stringResource(R.string.goal_buy_by_passed, formatBuyByDate(targetDay), remaining)
    }
}
