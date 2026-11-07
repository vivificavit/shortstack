package com.vivificavit.shortstack.ui.viewmodel

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vivificavit.shortstack.R
import com.vivificavit.shortstack.data.local.CategorySum
import com.vivificavit.shortstack.data.local.SelectedKidStore
import com.vivificavit.shortstack.data.model.DefaultCategories
import com.vivificavit.shortstack.data.model.Frequency
import com.vivificavit.shortstack.data.model.Goal
import com.vivificavit.shortstack.data.model.GoalSplit
import com.vivificavit.shortstack.data.model.RecurringEntry
import com.vivificavit.shortstack.data.model.Transaction
import com.vivificavit.shortstack.data.model.TransactionType
import com.vivificavit.shortstack.repository.KidMoneySnapshot
import com.vivificavit.shortstack.repository.TransactionRepository
import com.vivificavit.shortstack.util.BalanceHistory
import com.vivificavit.shortstack.util.BalancePoint
import com.vivificavit.shortstack.util.DateRanges
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class TrackerViewModel(
    private val repository: TransactionRepository,
    private val selectedKidStore: SelectedKidStore
) : ViewModel() {

    /** Wraps the stored choice so "not loaded yet" (null) differs from "nothing stored". */
    private data class PreferredKid(val name: String?)

    /** The kid the user last picked (may be stale if that kid was removed); null until loaded. */
    private val preferredKid: StateFlow<PreferredKid?> = selectedKidStore.lastSelectedKid
        .map { PreferredKid(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    /** Null until the database has loaded the kid list. */
    private val loadedKids: StateFlow<List<String>?> = repository.kidNames
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    val kids: StateFlow<List<String>> = loadedKids
        .map { it.orEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** True once loaded with no kids yet, so the UI can prompt to add one. */
    val hasNoKids: StateFlow<Boolean> = loadedKids
        .map { it != null && it.isEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    /** The preferred kid if they still exist, otherwise the first kid; null when there are none. */
    private val _selectedKid: StateFlow<String?> = combine(loadedKids, preferredKid) { names, preferred ->
        when {
            names.isNullOrEmpty() || preferred == null -> null
            preferred.name != null && preferred.name in names -> preferred.name
            else -> names.first()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )
    val selectedKid: StateFlow<String?> = _selectedKid

    val categories: StateFlow<List<String>> = repository.categories
        .map { list ->
            if (list.isEmpty()) DefaultCategories.DEFAULT_CATEGORIES else list
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DefaultCategories.DEFAULT_CATEGORIES
        )

    /** Icons the user picked, keyed by lower-cased category name. */
    val categoryIcons: StateFlow<Map<String, String>> = repository.categoryIcons
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap()
        )

    /**
     * The category of the most recent entry of each type (across all kids), used to
     * pre-select the category in the entry form. Types with no entries are absent.
     */
    val lastCategoryByType: StateFlow<Map<TransactionType, String>> =
        repository.allTransactions
            .map { list ->
                // allTransactions is newest first, so the first match per type wins.
                list.groupBy { it.type }.mapValues { (_, entries) -> entries.first().category }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyMap()
            )

    /** Kids who have at least one entry, goal or repeating entry, so removing them asks first. */
    val kidsWithEntries: StateFlow<Set<String>> =
        combine(
            repository.allTransactions,
            repository.kidsWithGoals,
            repository.kidsWithRecurring
        ) { list, kidsWithGoals, kidsWithRecurring ->
            list.map { tx -> tx.kidName }.toSet() + kidsWithGoals + kidsWithRecurring
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptySet()
            )

    /** Every entry's note, keyed by lower-cased category, for suggesting category icons. */
    val notesByCategory: StateFlow<Map<String, List<String>>> =
        repository.allTransactions
            .map { list -> list.groupBy({ it.category.lowercase() }, { it.title }) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyMap()
            )

    val usedCategories: StateFlow<Set<String>> =
        combine(repository.allTransactions, repository.recurringCategories) { list, recurringCategories ->
            (list.map { tx -> tx.category } + recurringCategories).map { it.lowercase() }.toSet()
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptySet()
            )

    /**
     * Lower-cased categories the user switched off in the charts. Tracking the
     * hidden ones (rather than the shown ones) means every category, including
     * ones added later, is shown by default.
     */
    private val _hiddenCategories = MutableStateFlow<Set<String>>(emptySet())
    val hiddenCategories: StateFlow<Set<String>> = _hiddenCategories

    private val _today = MutableStateFlow(LocalDate.now())

    /** Today's date, moved on when the app comes to the front and at midnight (see [addDueRecurring]). */
    val today: StateFlow<LocalDate> = _today

    // The Trends date range: a quick range, which moves with [today], or days the user picked.
    private val _quickRange = MutableStateFlow<DateRanges.QuickRange?>(null)
    val quickRange: StateFlow<DateRanges.QuickRange?> = _quickRange
    // Raw DatePicker selections (midnight UTC); see DateRanges.toLocalRange.
    private val _pickedStart = MutableStateFlow<Long?>(null)
    private val _pickedEnd = MutableStateFlow<Long?>(null)

    private val dateRange: StateFlow<Pair<Long?, Long?>> =
        combine(_quickRange, _pickedStart, _pickedEnd, _today) { quick, start, end, today ->
            quick?.let { DateRanges.quickRange(it, today) } ?: (start to end)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null to null)

    private val _startDateMillis: StateFlow<Long?> =
        dateRange.map { it.first }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val startDateMillis: StateFlow<Long?> = _startDateMillis
    private val _endDateMillis: StateFlow<Long?> =
        dateRange.map { it.second }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val endDateMillis: StateFlow<Long?> = _endDateMillis

    private val _uiMessages = MutableSharedFlow<UiMessage>()
    val uiMessages: SharedFlow<UiMessage> = _uiMessages.asSharedFlow()

    private suspend fun emitMessage(@StringRes resId: Int, vararg args: String) {
        _uiMessages.emit(UiMessage(resId, args.toList()))
    }

    /** A message that says "1 entry" or "3 entries"; [count] is also its first argument. */
    private suspend fun emitCountMessage(@PluralsRes resId: Int, count: Int, vararg args: String) {
        _uiMessages.emit(UiMessage(args = listOf(count.toString()) + args, pluralsRes = resId, quantity = count))
    }

    init {
        viewModelScope.launch {
            repository.seedDefaultsIfNeeded()
        }
    }

    private class KidMoney(
        val kidName: String,
        val ledger: LedgerState,
        val goals: GoalsState,
        val snapshot: KidMoneySnapshot
    )

    /** The selected kid's entries, goals and moves, worked out together so the two tabs always agree. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val kidMoney: StateFlow<KidMoney?> = _selectedKid
        .flatMapLatest { kidName ->
            if (kidName == null) {
                flowOf(null)
            } else {
                repository.getMoneyForKid(kidName).map { money ->
                    val (ledger, goalsState) = buildMoneyStates(money.transactions, money.goals, money.moves)
                    KidMoney(kidName, ledger, goalsState, money)
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    /** The selected kid's balance and entries; null until they have loaded. */
    val ledger: StateFlow<LedgerState?> = kidMoney
        .map { it?.ledger }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    /** The selected kid's balance and goal savings, day by day over the Trends date range. */
    val balanceHistory: StateFlow<List<BalancePoint>> = combine(kidMoney, dateRange, _today) { money, (start, end), today ->
        if (money == null) {
            emptyList()
        } else {
            BalanceHistory.build(
                transactions = money.snapshot.transactions,
                moves = money.snapshot.moves,
                startDate = start?.let(DateRanges::pickerMillisToLocalDate),
                endDate = end?.let(DateRanges::pickerMillisToLocalDate),
                today = today
            )
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** The selected kid's repeating entries, oldest first. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val recurring: StateFlow<List<RecurringEntry>> = _selectedKid
        .flatMapLatest { kidName -> if (kidName == null) flowOf(emptyList()) else repository.getRecurringForKid(kidName) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** The selected kid's goals; null until they have loaded. */
    val goals: StateFlow<GoalsState?> = kidMoney
        .map { it?.goals }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    init {
        // Cheer when a goal is reached, but not for ones already reached when a kid's goals load.
        viewModelScope.launch {
            var before: Pair<String, Set<Long>>? = null
            kidMoney.filterNotNull().collect { money ->
                val reached = money.goals.active.filter { it.isReached }.map { it.goal.id }.toSet()
                val previous = before
                if (previous != null && previous.first == money.kidName) {
                    money.goals.active
                        .filter { it.goal.id in reached && it.goal.id !in previous.second }
                        .forEach { emitMessage(R.string.message_goal_reached, it.goal.name) }
                }
                before = money.kidName to reached
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val rawCategoryIncome = combine(
        _selectedKid,
        _startDateMillis,
        _endDateMillis
    ) { kidName, start, end ->
        kidName to DateRanges.toLocalRange(start, end)
    }.flatMapLatest { (kidName, range) ->
        if (kidName == null) flowOf(emptyList())
        else repository.getCreditTotalsByCategoryForKidInRange(kidName, range.first, range.last)
    }

    /** Income per category in the date range, including categories hidden from the chart. */
    val incomeSums: StateFlow<List<CategorySum>> = rawCategoryIncome
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val rawCategorySpending = combine(
        _selectedKid,
        _startDateMillis,
        _endDateMillis
    ) { kidName, start, end ->
        kidName to DateRanges.toLocalRange(start, end)
    }.flatMapLatest { (kidName, range) ->
        if (kidName == null) flowOf(emptyList())
        else repository.getDebitTotalsByCategoryForKidInRange(kidName, range.first, range.last)
    }

    /** Spending per category in the date range, including categories hidden from the chart. */
    val spendingSums: StateFlow<List<CategorySum>> = rawCategorySpending
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun toggleCategorySelection(category: String) {
        val key = category.trim().lowercase()
        if (key.isBlank()) return
        _hiddenCategories.update { hidden ->
            if (key in hidden) hidden - key else hidden + key
        }
    }

    private fun showCategory(category: String) {
        _hiddenCategories.update { it - category.trim().lowercase() }
    }

    fun selectKid(kidName: String) {
        val trimmed = kidName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch { selectedKidStore.setLastSelectedKid(trimmed) }
    }

    fun addKid(kidName: String) {
        val trimmed = kidName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            repository.insertKid(trimmed)
            selectedKidStore.setLastSelectedKid(trimmed)
            emitMessage(R.string.message_switched_kid, trimmed)
        }
    }

    /** Removes the kid and all of their entries; the UI confirms first when they have entries. */
    fun removeKid(kidName: String) {
        viewModelScope.launch {
            repository.deleteKidAndEntries(kidName)
            emitMessage(R.string.message_kid_removed, kidName)
        }
    }

    fun addCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        showCategory(trimmed)
        viewModelScope.launch {
            repository.addCategory(trimmed)
            emitMessage(R.string.message_category_added, trimmed)
        }
    }

    /** Sets the icon for [category]; null goes back to picking one automatically. */
    fun setCategoryIcon(category: String, iconKey: String?) {
        viewModelScope.launch { repository.setCategoryIcon(category, iconKey) }
    }

    fun deleteUnusedCategory(name: String) {
        viewModelScope.launch {
            val deleted = repository.deleteUnusedCategory(name)
            if (deleted) {
                emitMessage(R.string.message_category_removed, name)
            } else {
                emitMessage(R.string.message_category_in_use, name)
            }
        }
    }

    /** Shows the Trends tab for days the user picked; null ends are open. */
    fun setDateRange(startMillis: Long?, endMillis: Long?) {
        _quickRange.value = null
        _pickedStart.value = startMillis
        _pickedEnd.value = endMillis
    }

    /** Shows a quick range, which keeps ending today as the days go by. All time clears the range. */
    fun setQuickRange(range: DateRanges.QuickRange) {
        setDateRange(null, null)
        _quickRange.value = range.takeIf { it != DateRanges.QuickRange.ALL_TIME }
    }

    /**
     * Adds an entry for the selected kid. Income can set [split] aside for a goal;
     * spending more than pocket money takes the rest from [coverGoalId] first.
     */
    fun addTransaction(
        title: String,
        amountCents: Long,
        type: TransactionType,
        category: String,
        timestamp: Long = System.currentTimeMillis(),
        split: GoalSplit? = null,
        coverGoalId: Long? = null,
        repeat: Frequency? = null
    ) {
        val trimmedCategory = category.trim()
        if (title.isBlank() || amountCents <= 0L || trimmedCategory.isBlank()) return
        val currentKid = _selectedKid.value ?: return
        showCategory(trimmedCategory)
        viewModelScope.launch {
            if (repeat != null) {
                // The schedule adds this first entry itself, along with any already due since its date.
                val added = repository.addRecurring(
                    RecurringEntry(
                        kidName = currentKid,
                        title = title.trim(),
                        amountCents = amountCents,
                        type = type,
                        category = trimmedCategory,
                        frequency = repeat,
                        startTimestamp = timestamp,
                        goalId = split?.goalId,
                        goalCents = split?.amountCents
                    ),
                    coverGoalId = coverGoalId
                )
                when {
                    // A start in the future: say when, since nothing appears in the ledger yet.
                    added == 0 -> emitMessage(
                        R.string.message_recurring_starts,
                        title.trim(),
                        DateRanges.timestampToLocalDate(timestamp).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                    )
                    added > 1 -> emitCountMessage(R.plurals.message_recurring_caught_up, added, title.trim())
                }
                return@launch
            }
            val transaction = Transaction(
                kidName = currentKid,
                title = title.trim(),
                amountCents = amountCents,
                type = type,
                category = trimmedCategory,
                timestamp = timestamp
            )
            repository.insertTransaction(transaction, split, coverGoalId)
        }
    }

    /**
     * Moves [today] on and adds every repeating entry that has come due. Runs when
     * the app comes to the front and at each midnight while it's open.
     */
    fun addDueRecurring() {
        _today.value = LocalDate.now()
        viewModelScope.launch {
            repository.addDueRecurring().forEach { (entry, count) ->
                if (count == 1) {
                    emitMessage(R.string.message_recurring_added, entry.title, entry.kidName)
                } else {
                    emitCountMessage(R.plurals.message_recurring_caught_up, count, entry.title)
                }
            }
        }
    }

    /** Saves changes to a repeating entry; they apply to the entries it adds from now on. */
    fun updateRecurring(entry: RecurringEntry) {
        showCategory(entry.category)
        viewModelScope.launch { repository.saveRecurring(entry) }
    }

    /** Stops a repeating entry, keeping what it already added; the snackbar offers Undo. */
    fun stopRecurring(entry: RecurringEntry) {
        viewModelScope.launch {
            val stopped = repository.deleteRecurring(entry)
            _uiMessages.emit(
                UiMessage(
                    resId = R.string.message_recurring_stopped,
                    args = listOf(entry.title),
                    onUndo = { viewModelScope.launch { repository.restoreRecurring(stopped) } }
                )
            )
        }
    }

    /** Saves changes to an existing entry (matched by id), replacing its goal split. */
    fun updateTransaction(transaction: Transaction, split: GoalSplit? = null, coverGoalId: Long? = null) {
        showCategory(transaction.category)
        viewModelScope.launch {
            repository.insertTransaction(transaction, split, coverGoalId)
        }
    }

    /** Deletes right away; the snackbar offers Undo, which puts the entry and its goal money back. */
    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            val deleted = repository.deleteTransaction(transaction)
            _uiMessages.emit(
                UiMessage(
                    resId = R.string.message_entry_deleted,
                    args = listOf(transaction.title),
                    onUndo = { viewModelScope.launch { repository.restoreEntry(deleted) } }
                )
            )
        }
    }

    fun addGoal(name: String, targetCents: Long, icon: String?, targetDay: Long? = null) {
        val trimmed = name.trim()
        if (trimmed.isBlank() || targetCents <= 0L) return
        val currentKid = _selectedKid.value ?: return
        viewModelScope.launch {
            repository.saveGoal(
                Goal(kidName = currentKid, name = trimmed, targetCents = targetCents, icon = icon, targetDay = targetDay)
            )
            emitMessage(R.string.message_goal_added, trimmed)
        }
    }

    /** Saves changes to a goal's name, target, icon or buy-by day. */
    fun updateGoal(goal: Goal) {
        viewModelScope.launch { repository.saveGoal(goal) }
    }

    /** Deletes right away, returning its money to pocket money; the snackbar offers Undo. */
    fun deleteGoal(goal: Goal) {
        viewModelScope.launch {
            val deleted = repository.deleteGoal(goal)
            _uiMessages.emit(
                UiMessage(
                    resId = R.string.message_goal_deleted,
                    args = listOf(goal.name),
                    onUndo = { viewModelScope.launch { repository.restoreGoal(deleted) } }
                )
            )
        }
    }

    /** Moves money between goals, or to and from pocket money (null). */
    fun moveMoney(fromGoalId: Long?, toGoalId: Long?, amountCents: Long) {
        val currentKid = _selectedKid.value ?: return
        viewModelScope.launch {
            if (!repository.moveMoney(currentKid, fromGoalId, toGoalId, amountCents)) {
                emitMessage(R.string.message_move_failed)
            }
        }
    }

    /** Records buying [goal] as an expense, paid for with what was saved for it. */
    fun buyGoal(goal: Goal, title: String, amountCents: Long, category: String, timestamp: Long) {
        val trimmedCategory = category.trim()
        if (title.isBlank() || amountCents <= 0L || trimmedCategory.isBlank()) return
        showCategory(trimmedCategory)
        viewModelScope.launch {
            val expense = Transaction(
                kidName = goal.kidName,
                title = title.trim(),
                amountCents = amountCents,
                type = TransactionType.DEBIT,
                category = trimmedCategory,
                timestamp = timestamp
            )
            if (repository.buyGoal(goal, expense) != null) {
                emitMessage(R.string.message_goal_bought, goal.name)
            }
        }
    }

    fun exportToJson(openStream: () -> OutputStream?) {
        viewModelScope.launch {
            repository.exportToJson(openStream)
                .onSuccess { count ->
                    emitCountMessage(R.plurals.message_export_succeeded, count)
                }
                .onFailure { error ->
                    emitMessage(R.string.message_export_failed, error.localizedMessage.orEmpty())
                }
        }
    }

    fun exportToCsv(openStream: () -> OutputStream?) {
        viewModelScope.launch {
            repository.exportToCsv(openStream)
                .onSuccess { count ->
                    emitCountMessage(R.plurals.message_csv_export_succeeded, count)
                }
                .onFailure { error ->
                    emitMessage(R.string.message_csv_export_failed, error.localizedMessage.orEmpty())
                }
        }
    }

    fun importFromJson(openStream: () -> InputStream?) {
        viewModelScope.launch {
            repository.importFromJson(openStream, replaceExisting = true)
                .onSuccess { count ->
                    emitCountMessage(R.plurals.message_restore_succeeded, count)
                    // Repeating entries in the backup may have come due since it was made.
                    addDueRecurring()
                }
                .onFailure { error ->
                    emitMessage(R.string.message_restore_failed, error.localizedMessage.orEmpty())
                }
        }
    }

    class Factory(
        private val repository: TransactionRepository,
        private val selectedKidStore: SelectedKidStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TrackerViewModel::class.java)) {
                return TrackerViewModel(repository, selectedKidStore) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
