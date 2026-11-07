package com.vivificavit.shortstack.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vivificavit.shortstack.R
import com.vivificavit.shortstack.data.model.Frequency
import com.vivificavit.shortstack.data.model.GoalMoveReason
import com.vivificavit.shortstack.data.model.GoalSplit
import com.vivificavit.shortstack.data.model.RecurringEntry
import com.vivificavit.shortstack.data.model.Transaction
import com.vivificavit.shortstack.data.model.TransactionType
import com.vivificavit.shortstack.ui.theme.CreditContainer
import com.vivificavit.shortstack.ui.theme.CreditGreen
import com.vivificavit.shortstack.ui.theme.DebitContainer
import com.vivificavit.shortstack.ui.theme.DebitRed
import com.vivificavit.shortstack.ui.theme.OnCreditContainer
import com.vivificavit.shortstack.ui.theme.OnDebitContainer
import com.vivificavit.shortstack.ui.viewmodel.GoalProgress
import com.vivificavit.shortstack.ui.viewmodel.GoalsState
import com.vivificavit.shortstack.ui.viewmodel.LedgerState
import com.vivificavit.shortstack.util.DateRanges
import com.vivificavit.shortstack.util.GoalMath
import com.vivificavit.shortstack.util.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What the entry sheet saves. */
data class EntryDraft(
    val title: String,
    val amountCents: Long,
    val type: TransactionType,
    val category: String,
    val timestamp: Long,
    /** Income set aside for a goal. */
    val split: GoalSplit? = null,
    /** For spending more than pocket money: the goal to take the rest from first. */
    val coverGoalId: Long? = null,
    /** How often it repeats; null for a one-off entry. */
    val repeat: Frequency? = null
)

/**
 * A kid's money as it would be without the entry being edited (and whatever it
 * set aside or took from goals), so the sheet can show what saving it will do.
 */
data class EntryMoney(
    val balanceCents: Long,
    /** Goals still being saved for, holding what they would without this entry. */
    val goals: List<GoalProgress>,
    /** This entry's split, when it's income that set some aside for a goal still being saved for. */
    val existingSplit: GoalSplit? = null,
    /** This entry's split to a goal that's since been bought; it can't be changed. */
    val lockedSplit: LockedSplit? = null
) {
    val savedCents: Long get() = goals.sumOf { it.savedCents }
    val pocketCents: Long get() = balanceCents - savedCents

    companion object {
        fun from(ledger: LedgerState, goals: GoalsState?, editing: Transaction?): EntryMoney {
            val active = goals?.active.orEmpty()
            val activeIds = active.map { it.goal.id }.toSet()
            val linked = editing?.let { ledger.movesByEntry[it.id] }.orEmpty()
            // The moves saving this entry again replaces: its cover, and its split to a goal still open.
            val replaced = linked.filter {
                it.reason == GoalMoveReason.COVER || (it.reason == GoalMoveReason.SPLIT && it.goalId in activeIds)
            }
            val replacedByGoal = replaced.groupBy { it.goalId }.mapValues { (_, moves) -> moves.sumOf { it.amountCents } }
            val signedAmount = when (editing?.type) {
                null -> 0L
                TransactionType.CREDIT -> editing.amountCents
                TransactionType.DEBIT -> -editing.amountCents
            }
            val lockedMove = linked.firstOrNull { it.reason == GoalMoveReason.SPLIT && it.goalId !in activeIds }
            return EntryMoney(
                balanceCents = ledger.balanceCents - signedAmount,
                goals = active.map { it.copy(savedCents = it.savedCents - (replacedByGoal[it.goal.id] ?: 0L)) },
                existingSplit = replaced
                    .firstOrNull { it.reason == GoalMoveReason.SPLIT }
                    ?.let { GoalSplit(it.goalId, it.amountCents) },
                lockedSplit = lockedMove?.let { move ->
                    goals?.done?.firstOrNull { it.goal.id == move.goalId }
                        ?.let { LockedSplit(it.goal.name, move.amountCents) }
                }
            )
        }
    }
}

/** What an income entry set aside for a goal that's since been bought. */
data class LockedSplit(val goalName: String, val amountCents: Long)

/** The Repeat choices, with null for an entry that doesn't repeat. */
private val RepeatChoices = listOf(
    null to R.string.repeat_never,
    Frequency.WEEKLY to R.string.repeat_weekly,
    Frequency.EVERY_2_WEEKS to R.string.repeat_every_2_weeks,
    Frequency.MONTHLY to R.string.repeat_monthly
)

/** How often [frequency] repeats, in words, for notes like "Repeats every week". */
@StringRes
fun repeatsRes(frequency: Frequency): Int = when (frequency) {
    Frequency.WEEKLY -> R.string.repeats_weekly
    Frequency.EVERY_2_WEEKS -> R.string.repeats_every_2_weeks
    Frequency.MONTHLY -> R.string.repeats_monthly
}

/** Quick picks for how much of an income entry to set aside. */
private val SplitPercents = listOf(10, 20, 50, 100)

/**
 * Adds a new entry, or edits [existing] when it's given (which also offers Delete).
 * With [money], income can set some aside for a goal, and spending more than
 * pocket money says which goal pays the rest. With [buyingGoal], it records
 * buying that goal. A new entry can repeat; [schedule] edits a repeating entry
 * instead, and [existingRepeat] says how often the schedule [existing] came from
 * repeats. [onSave] gets the entry; the caller decides what to do with it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryBottomSheet(
    kidName: String,
    categories: List<String>,
    lastCategoryByType: Map<TransactionType, String>,
    existing: Transaction?,
    money: EntryMoney?,
    buyingGoal: GoalProgress?,
    onDismiss: () -> Unit,
    onAddCategory: (String) -> Unit,
    onSave: (EntryDraft) -> Unit,
    onDelete: () -> Unit,
    schedule: RecurringEntry? = null,
    existingRepeat: Frequency? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val today = LocalToday.current
    val initialType = existing?.type ?: schedule?.type
        ?: if (buyingGoal != null) TransactionType.DEBIT else TransactionType.CREDIT
    var selectedType by rememberSaveable { mutableStateOf(initialType) }
    // Suggest the last category used for this type, if it still exists.
    fun suggestedCategory(type: TransactionType): String? =
        lastCategoryByType[type]?.takeIf { it in categories }
    var selectedCategory by rememberSaveable {
        mutableStateOf(existing?.category ?: schedule?.category ?: suggestedCategory(initialType))
    }
    // Once the user picks a category (or is editing one), switching type no longer replaces it.
    var categoryChosenByUser by rememberSaveable { mutableStateOf(existing != null || schedule != null) }
    var amountInput by rememberSaveable {
        mutableStateOf(
            (existing?.amountCents ?: schedule?.amountCents)?.let { Money.toInput(it) }
                ?: buyingGoal?.let { Money.toInput(it.goal.targetCents) }
                ?: ""
        )
    }
    var noteInput by rememberSaveable {
        mutableStateOf(existing?.title ?: schedule?.title ?: buyingGoal?.goal?.name ?: "")
    }
    // A new entry can repeat; a schedule being edited always does.
    val repeatShown = existing == null && buyingGoal == null
    var repeat by rememberSaveable { mutableStateOf(schedule?.frequency) }

    // Setting part of an income entry aside for one goal.
    val openGoals = money?.goals.orEmpty()
    // A schedule's fixed set-aside, while its goal is still being saved for.
    val scheduleSplit = schedule?.goalId?.takeIf { id -> openGoals.any { it.goal.id == id } }
        ?.let { id -> schedule.goalCents?.let { GoalSplit(id, it) } }
    val initialSplit = money?.existingSplit ?: scheduleSplit
    var splitOn by rememberSaveable { mutableStateOf(initialSplit != null) }
    var splitGoalId by rememberSaveable {
        mutableStateOf(initialSplit?.goalId ?: openGoals.firstOrNull()?.goal?.id)
    }
    var splitInput by rememberSaveable { mutableStateOf(initialSplit?.let { Money.toInput(it.amountCents) } ?: "") }
    // Which goal pays first when spending is more than pocket money; null until the kid picks one.
    var coverGoalChoice by rememberSaveable { mutableStateOf<Long?>(null) }
    val goalsWithMoney = openGoals.filter { it.savedCents > 0L }
    val coverGoalId = coverGoalChoice?.takeIf { id -> goalsWithMoney.any { it.goal.id == id } }
        ?: goalsWithMoney.maxByOrNull { it.savedCents }?.goal?.id
    var entryDate by rememberSaveable {
        mutableStateOf(existing?.let { DateRanges.timestampToLocalDate(it.timestamp) } ?: today)
    }
    var inputError by remember { mutableStateOf<Int?>(null) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var addingCategory by rememberSaveable { mutableStateOf(false) }

    val parsedAmount = Money.parseToCents(amountInput)
    val splitShown = buyingGoal == null && selectedType == TransactionType.CREDIT && openGoals.isNotEmpty()
    val splitCents = Money.parseToCents(splitInput)

    // Shared by the Save button and the keyboard's Done key on the description field.
    fun trySave() {
        val category = selectedCategory
        val splitting = splitShown && splitOn && splitGoalId != null
        when {
            parsedAmount == null -> inputError = R.string.entry_amount_error
            noteInput.isBlank() -> inputError = R.string.entry_note_error
            category == null -> inputError = R.string.entry_category_error
            splitting && (splitCents == null || splitCents > parsedAmount) -> inputError = R.string.entry_split_error
            else -> {
                // Keep the original time of day (or now, for a new entry) on the chosen date.
                val baseTimestamp = existing?.timestamp ?: System.currentTimeMillis()
                val timestamp = DateRanges.moveToDate(baseTimestamp, entryDate)
                onSave(
                    EntryDraft(
                        title = noteInput.trim(),
                        amountCents = parsedAmount,
                        type = selectedType,
                        category = category,
                        timestamp = timestamp,
                        split = if (splitting && splitCents != null) GoalSplit(splitGoalId!!, splitCents) else null,
                        coverGoalId = coverGoalId.takeIf { selectedType == TransactionType.DEBIT },
                        repeat = repeat.takeIf { repeatShown }
                    )
                )
            }
        }
    }

    // A new entry opens with the cursor in Amount and the number keyboard up.
    val amountFocus = remember { FocusRequester() }
    if (existing == null && schedule == null) {
        LaunchedEffect(Unit) { amountFocus.requestFocus() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        // Scrolls so Save stays reachable in landscape, with large text or with many categories.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = when {
                    buyingGoal != null -> stringResource(R.string.entry_buy_title, buyingGoal.goal.name)
                    schedule != null -> stringResource(R.string.recurring_edit_title, kidName)
                    existing == null -> stringResource(R.string.entry_title, kidName)
                    else -> stringResource(R.string.entry_edit_title, kidName)
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // Buying a goal is always spending, so there's no choice to make.
            if (buyingGoal == null) SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                val segments = listOf(
                    TransactionType.CREDIT to R.string.entry_type_income,
                    TransactionType.DEBIT to R.string.entry_type_expense
                )
                segments.forEachIndexed { index, (type, label) ->
                    SegmentedButton(
                        selected = selectedType == type,
                        onClick = {
                            selectedType = type
                            if (!categoryChosenByUser) selectedCategory = suggestedCategory(type)
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = segments.size
                        ),
                        colors = if (type == TransactionType.CREDIT) {
                            SegmentedButtonDefaults.colors(
                                activeContainerColor = CreditContainer,
                                activeContentColor = OnCreditContainer
                            )
                        } else {
                            SegmentedButtonDefaults.colors(
                                activeContainerColor = DebitContainer,
                                activeContentColor = OnDebitContainer
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(label),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    )
                }
            }

            OutlinedTextField(
                value = amountInput,
                onValueChange = { newValue ->
                    if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                        amountInput = newValue
                        inputError = null
                    }
                },
                label = { Text(stringResource(R.string.entry_amount_label)) },
                placeholder = { Text(stringResource(R.string.entry_amount_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(amountFocus)
            )

            OutlinedTextField(
                value = noteInput,
                onValueChange = {
                    noteInput = it
                    inputError = null
                },
                label = { Text(stringResource(R.string.entry_note_label)) },
                placeholder = {
                    Text(
                        if (selectedType == TransactionType.CREDIT) {
                            stringResource(R.string.entry_note_hint_income)
                        } else {
                            stringResource(R.string.entry_note_hint_expense)
                        }
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { trySave() }),
                modifier = Modifier.fillMaxWidth()
            )

            when {
                buyingGoal != null -> PurchaseSummary(buyingGoal, parsedAmount)
                splitShown -> GoalSplitSection(
                    goals = openGoals,
                    amountCents = parsedAmount,
                    on = splitOn,
                    onToggle = { on ->
                        splitOn = on
                        inputError = null
                        // Start from a fifth, so turning it on shows how a split works.
                        if (on && splitInput.isBlank() && parsedAmount != null) {
                            splitInput = Money.toInput(GoalMath.splitCents(parsedAmount, 20))
                        }
                    },
                    goalId = splitGoalId,
                    onPickGoal = { splitGoalId = it },
                    splitInput = splitInput,
                    onSplitInput = {
                        splitInput = it
                        inputError = null
                    }
                )
                selectedType == TransactionType.CREDIT && money?.lockedSplit != null -> {
                    val locked = money.lockedSplit
                    Text(
                        text = stringResource(
                            R.string.entry_split_locked,
                            Money.format(locked.amountCents),
                            locked.goalName
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Editing a schedule changes future entries, so there's no goal to cover yet.
                selectedType == TransactionType.DEBIT && money != null && parsedAmount != null && schedule == null -> {
                    // What the goals would have to give up so the kid never has less than they've set aside.
                    val fromGoals = (money.savedCents - (money.balanceCents - parsedAmount).coerceAtLeast(0L))
                        .coerceIn(0L, money.savedCents)
                    if (fromGoals > 0L) {
                        CoverWarning(
                            fromGoalsCents = fromGoals,
                            goals = goalsWithMoney,
                            chosenGoalId = coverGoalId,
                            onPick = { coverGoalChoice = it }
                        )
                    }
                }
            }

            if (repeatShown) {
                RepeatRow(
                    repeat = repeat,
                    // A schedule being edited stops from the Repeating entries list, not here.
                    allowNever = schedule == null,
                    onPick = {
                        repeat = it
                        // Only a repeating entry can start in the future; a one-off records what already happened.
                        if (it == null && entryDate.isAfter(today)) entryDate = today
                    }
                )
            }
            existingRepeat?.let { frequency ->
                Text(
                    text = stringResource(R.string.entry_from_schedule, stringResource(repeatsRes(frequency))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // A schedule keeps its start date; edits apply from the next entry on.
            if (schedule == null) OutlinedButton(
                onClick = { pickingDate = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        if (repeat != null) R.string.entry_date_first else R.string.entry_date,
                        if (entryDate == today) {
                            stringResource(R.string.entry_date_today)
                        } else {
                            entryDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                        }
                    )
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.entry_category_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Show a just-added category right away, before the saved list catches up.
                    val chipCategories = selectedCategory
                        ?.takeIf { picked -> categories.none { it.equals(picked, ignoreCase = true) } }
                        ?.let { categories + it }
                        ?: categories
                    chipCategories.forEach { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = {
                                selectedCategory = category
                                categoryChosenByUser = true
                                inputError = null
                            },
                            label = { Text(category) }
                        )
                    }
                    AssistChip(
                        onClick = { addingCategory = true },
                        label = { Text(stringResource(R.string.entry_new_category)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(AssistChipDefaults.IconSize)
                            )
                        }
                    )
                }
            }

            inputError?.let { errorRes ->
                // A split can be at most the entry itself; anything else up to the largest entry.
                val maxCents = if (errorRes == R.string.entry_split_error) parsedAmount else null
                Text(
                    text = stringResource(errorRes, Money.format(1L), Money.format(maxCents ?: Money.MAX_ENTRY_CENTS)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = { trySave() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedType == TransactionType.CREDIT) CreditGreen else DebitRed
                )
            ) {
                Text(
                    text = stringResource(R.string.entry_save),
                    modifier = Modifier.padding(vertical = 4.dp),
                    fontWeight = FontWeight.Bold
                )
            }

            if (existing != null) {
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.entry_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (pickingDate) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = DateRanges.localDateToPickerMillis(entryDate),
            // No future dates for a one-off entry, which records something that already
            // happened; a repeating one can start on a later day, like next payday.
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    repeat != null || !DateRanges.pickerMillisToLocalDate(utcTimeMillis).isAfter(today)

                override fun isSelectableYear(year: Int): Boolean = repeat != null || year <= today.year
            }
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let {
                            entryDate = DateRanges.pickerMillisToLocalDate(it)
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

    if (addingCategory) {
        NewCategoryDialog(
            onDismiss = { addingCategory = false },
            onConfirm = { name ->
                val match = categories.firstOrNull { it.equals(name, ignoreCase = true) }
                if (match == null) onAddCategory(name)
                selectedCategory = match ?: name
                categoryChosenByUser = true
                inputError = null
                addingCategory = false
            }
        )
    }
}

@Composable
private fun NewCategoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var nameInput by rememberSaveable { mutableStateOf("") }
    fun confirm() {
        if (nameInput.isNotBlank()) onConfirm(nameInput.trim())
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manage_categories_new), fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
            )
        },
        confirmButton = {
            Button(onClick = { confirm() }, enabled = nameInput.isNotBlank()) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/** For income: an on/off switch, then which goal, how much, and what the split leaves. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalSplitSection(
    goals: List<GoalProgress>,
    amountCents: Long?,
    on: Boolean,
    onToggle: (Boolean) -> Unit,
    goalId: Long?,
    onPickGoal: (Long) -> Unit,
    splitInput: String,
    onSplitInput: (String) -> Unit
) {
    val splitCents = Money.parseToCents(splitInput)
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, if (on) CreditGreen else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = on, role = Role.Switch, onValueChange = onToggle),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Savings,
                    contentDescription = null,
                    tint = CreditGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.entry_split_title),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                // The whole row toggles, so the switch itself doesn't take clicks.
                Switch(checked = on, onCheckedChange = null)
            }
            if (on) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    goals.forEach { progress ->
                        FilterChip(
                            selected = progress.goal.id == goalId,
                            onClick = { onPickGoal(progress.goal.id) },
                            label = { Text(progress.goal.name) }
                        )
                    }
                }
                OutlinedTextField(
                    value = splitInput,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d{0,2}$"))) onSplitInput(newValue)
                    },
                    label = { Text(stringResource(R.string.entry_split_amount_label)) },
                    placeholder = { Text(stringResource(R.string.entry_amount_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
                if (amountCents != null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SplitPercents.forEach { percent ->
                            val cents = GoalMath.splitCents(amountCents, percent)
                            FilterChip(
                                selected = splitCents == cents,
                                onClick = { onSplitInput(Money.toInput(cents)) },
                                label = {
                                    Text(
                                        if (percent == 100) {
                                            stringResource(R.string.move_all)
                                        } else {
                                            stringResource(R.string.entry_split_percent, percent)
                                        }
                                    )
                                }
                            )
                        }
                    }
                    val goalName = goals.firstOrNull { it.goal.id == goalId }?.goal?.name
                    if (splitCents != null && splitCents <= amountCents && goalName != null) {
                        Text(
                            text = stringResource(
                                R.string.entry_split_summary,
                                Money.format(amountCents - splitCents),
                                Money.format(splitCents),
                                goalName
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** For spending more than pocket money: how much comes out of goals, and which goal goes first. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoverWarning(
    fromGoalsCents: Long,
    goals: List<GoalProgress>,
    chosenGoalId: Long?,
    onPick: (Long) -> Unit
) {
    val chosenSaved = goals.firstOrNull { it.goal.id == chosenGoalId }?.savedCents ?: 0L
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.entry_cover_warning, Money.format(fromGoalsCents)),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                goals.forEach { progress ->
                    FilterChip(
                        selected = progress.goal.id == chosenGoalId,
                        onClick = { onPick(progress.goal.id) },
                        label = {
                            Text(
                                stringResource(
                                    R.string.move_place,
                                    progress.goal.name,
                                    Money.format(progress.savedCents)
                                )
                            )
                        }
                    )
                }
            }
            if (fromGoalsCents > chosenSaved) {
                Text(
                    text = stringResource(R.string.entry_cover_more),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/** For buying a goal: how much of the price its savings cover, and what pocket money gives or gets back. */
@Composable
private fun PurchaseSummary(goal: GoalProgress, priceCents: Long?) {
    val fromSavings = minOf(goal.savedCents, priceCents ?: goal.savedCents)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.entry_buy_uses, Money.format(fromSavings), goal.goal.name),
            style = MaterialTheme.typography.bodyMedium,
            color = CreditGreen,
            fontWeight = FontWeight.SemiBold
        )
        if (priceCents != null && priceCents > goal.savedCents) {
            Text(
                text = stringResource(R.string.entry_buy_extra, Money.format(priceCents - goal.savedCents)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (priceCents != null && priceCents < goal.savedCents) {
            Text(
                text = stringResource(R.string.entry_buy_left, Money.format(goal.savedCents - priceCents)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** How often a new entry repeats: Never, Weekly, Every 2 weeks or Monthly. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatRow(repeat: Frequency?, allowNever: Boolean, onPick: (Frequency?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.repeat_label),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RepeatChoices.filter { (frequency, _) -> allowNever || frequency != null }.forEach { (frequency, label) ->
                FilterChip(
                    selected = repeat == frequency,
                    onClick = { onPick(frequency) },
                    label = { Text(stringResource(label)) }
                )
            }
        }
    }
}
