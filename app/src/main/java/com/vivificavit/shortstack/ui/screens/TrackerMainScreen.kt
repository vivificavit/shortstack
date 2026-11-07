package com.vivificavit.shortstack.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.vivificavit.shortstack.R
import com.vivificavit.shortstack.data.model.Goal
import com.vivificavit.shortstack.ui.components.AddKidDialog
import com.vivificavit.shortstack.ui.components.EntryBottomSheet
import com.vivificavit.shortstack.ui.components.EntryMoney
import com.vivificavit.shortstack.ui.components.EditKidsDialog
import com.vivificavit.shortstack.ui.components.GoalActions
import com.vivificavit.shortstack.ui.components.GoalDetailSheet
import com.vivificavit.shortstack.ui.components.GoalEditDialog
import com.vivificavit.shortstack.ui.components.ManageCategoriesDialog
import com.vivificavit.shortstack.ui.components.MoveMoneyDialog
import com.vivificavit.shortstack.ui.components.RepeatingEntriesDialog
import com.vivificavit.shortstack.ui.components.RestoreBackupDialog
import com.vivificavit.shortstack.ui.theme.EntryButtonNavy
import com.vivificavit.shortstack.ui.viewmodel.TrackerViewModel
import com.vivificavit.shortstack.util.DateRanges
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class TrackerTab(@StringRes val titleRes: Int) {
    RUNNING_TALLY(R.string.tab_ledger),
    GOALS(R.string.tab_goals),
    TRENDS(R.string.tab_trends)
}

/** Machine-readable, so the backup file name sorts by date in any locale. */
private val BackupFileTimestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm", Locale.US)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerMainScreen(
    viewModel: TrackerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val kids by viewModel.kids.collectAsStateWithLifecycle()
    val selectedKid by viewModel.selectedKid.collectAsStateWithLifecycle()
    val hasNoKids by viewModel.hasNoKids.collectAsStateWithLifecycle()
    val ledger by viewModel.ledger.collectAsStateWithLifecycle()
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val incomeSums by viewModel.incomeSums.collectAsStateWithLifecycle()
    val spendingSums by viewModel.spendingSums.collectAsStateWithLifecycle()
    val balanceHistory by viewModel.balanceHistory.collectAsStateWithLifecycle()
    val hiddenCategories by viewModel.hiddenCategories.collectAsStateWithLifecycle()
    val categoryIcons by viewModel.categoryIcons.collectAsStateWithLifecycle()
    val notesByCategory by viewModel.notesByCategory.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val usedCategories by viewModel.usedCategories.collectAsStateWithLifecycle()
    val kidsWithEntries by viewModel.kidsWithEntries.collectAsStateWithLifecycle()
    val lastCategoryByType by viewModel.lastCategoryByType.collectAsStateWithLifecycle()
    val startDateMillis by viewModel.startDateMillis.collectAsStateWithLifecycle()
    val endDateMillis by viewModel.endDateMillis.collectAsStateWithLifecycle()
    val quickRange by viewModel.quickRange.collectAsStateWithLifecycle()
    val recurring by viewModel.recurring.collectAsStateWithLifecycle()

    // Add repeating entries that came due while the app was closed or in the background,
    // then again at each midnight for as long as it stays open.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, viewModel) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                viewModel.addDueRecurring()
                delay(DateRanges.millisUntilNextDay() + 1_000L)
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    var kidMenuExpanded by remember { mutableStateOf(false) }
    var showAddKidDialog by rememberSaveable { mutableStateOf(false) }
    var showEditKidsDialog by rememberSaveable { mutableStateOf(false) }
    var showManageCategoriesDialog by rememberSaveable { mutableStateOf(false) }
    var showRecurringDialog by rememberSaveable { mutableStateOf(false) }
    var showDateRangeDialog by rememberSaveable { mutableStateOf(false) }
    // The repeating entry open for editing in the entry sheet.
    var editingRecurringId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showRestoreConfirm by rememberSaveable { mutableStateOf(false) }
    var overflowExpanded by remember { mutableStateOf(false) }
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(TrackerTab.RUNNING_TALLY.ordinal) }
    var showEntrySheet by rememberSaveable { mutableStateOf(false) }
    // The id of the entry open for editing; null means the sheet adds a new one.
    var editingEntryId by rememberSaveable { mutableStateOf<Long?>(null) }
    // Set when the entry sheet is recording a goal being bought.
    var buyingGoalId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showGoalDialog by rememberSaveable { mutableStateOf(false) }
    // The id of the goal open for editing; null means the dialog adds a new one.
    var editingGoalId by rememberSaveable { mutableStateOf<Long?>(null) }
    var detailGoalId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showMoveDialog by rememberSaveable { mutableStateOf(false) }
    // Where Move money starts; null is pocket money.
    var moveFromGoalId by rememberSaveable { mutableStateOf<Long?>(null) }
    var moveToGoalId by rememberSaveable { mutableStateOf<Long?>(null) }

    val goalActions = remember(viewModel) {
        object : GoalActions {
            override fun open(goal: Goal) {
                detailGoalId = goal.id
            }

            override fun addMoney(goal: Goal) {
                moveFromGoalId = null
                moveToGoalId = goal.id
                showMoveDialog = true
            }

            override fun moveMoney(goal: Goal) {
                moveFromGoalId = goal.id
                moveToGoalId = null
                showMoveDialog = true
            }

            override fun edit(goal: Goal) {
                editingGoalId = goal.id
                showGoalDialog = true
            }

            override fun buy(goal: Goal) {
                editingEntryId = null
                buyingGoalId = goal.id
                showEntrySheet = true
            }

            override fun delete(goal: Goal) = viewModel.deleteGoal(goal)
        }
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { targetUri -> viewModel.exportToJson { context.contentResolver.openOutputStream(targetUri) } }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let { targetUri -> viewModel.exportToCsv { context.contentResolver.openOutputStream(targetUri) } }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { sourceUri -> viewModel.importFromJson { context.contentResolver.openInputStream(sourceUri) } }
    }

    LaunchedEffect(viewModel) {
        viewModel.uiMessages.collect { message ->
            val result = snackbarHostState.showSnackbar(
                message = if (message.pluralsRes != 0) {
                    resources.getQuantityString(message.pluralsRes, message.quantity, *message.args.toTypedArray())
                } else {
                    resources.getString(message.resId, *message.args.toTypedArray())
                },
                actionLabel = message.onUndo?.let { resources.getString(R.string.action_undo) },
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) message.onUndo?.invoke()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        Box(modifier = Modifier.padding(start = 8.dp)) {
                            // Capped so a long name truncates instead of crowding out the logo.
                            OutlinedButton(
                                onClick = { kidMenuExpanded = true },
                                modifier = Modifier.widthIn(max = 180.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    // Blank while loading, so "Add kid" only shows when there really are none.
                                    text = selectedKid
                                        ?: if (hasNoKids) stringResource(R.string.kid_menu_none) else "",
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = stringResource(R.string.cd_select_kid)
                                )
                            }

                            DropdownMenu(
                                expanded = kidMenuExpanded,
                                onDismissRequest = { kidMenuExpanded = false }
                            ) {
                                kids.forEach { kidName ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = kidName,
                                                fontWeight = if (kidName == selectedKid) {
                                                    FontWeight.Bold
                                                } else {
                                                    FontWeight.Normal
                                                }
                                            )
                                        },
                                        trailingIcon = {
                                            if (kidName == selectedKid) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = stringResource(R.string.cd_selected)
                                                )
                                            }
                                        },
                                        onClick = {
                                            kidMenuExpanded = false
                                            viewModel.selectKid(kidName)
                                        }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.menu_edit_kids),
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    onClick = {
                                        kidMenuExpanded = false
                                        showEditKidsDialog = true
                                    }
                                )
                            }
                        }
                    },
                    title = {
                        Image(
                            painter = painterResource(R.drawable.logo_shortstack),
                            contentDescription = stringResource(R.string.app_bar_title),
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .height(28.dp)
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    actions = {
                        IconButton(onClick = { overflowExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.cd_more_options)
                            )
                        }
                        DropdownMenu(
                            expanded = overflowExpanded,
                            onDismissRequest = { overflowExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_export_backup)) },
                                onClick = {
                                    overflowExpanded = false
                                    val timestamp = LocalDateTime.now().format(BackupFileTimestamp)
                                    exportBackupLauncher.launch("shortstack_backup_$timestamp.json")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_export_csv)) },
                                onClick = {
                                    overflowExpanded = false
                                    val timestamp = LocalDateTime.now().format(BackupFileTimestamp)
                                    exportCsvLauncher.launch("shortstack_entries_$timestamp.csv")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_restore_backup)) },
                                onClick = {
                                    overflowExpanded = false
                                    showRestoreConfirm = true
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_manage_categories)) },
                                onClick = {
                                    overflowExpanded = false
                                    showManageCategoriesDialog = true
                                }
                            )
                            if (selectedKid != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_recurring)) },
                                    onClick = {
                                        overflowExpanded = false
                                        showRecurringDialog = true
                                    }
                                )
                            }
                        }
                    }
                )

                PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
                    TrackerTab.entries.forEachIndexed { index, tab ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = {
                                Text(
                                    text = stringResource(tab.titleRes),
                                    fontWeight = if (selectedTabIndex == index) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Medium
                                    },
                                    maxLines = 1,
                                    // Shrinks a label that doesn't fit at large text sizes, rather than breaking the word.
                                    autoSize = TextAutoSize.StepBased(
                                        minFontSize = 10.sp,
                                        maxFontSize = MaterialTheme.typography.titleSmall.fontSize
                                    )
                                )
                            }
                        )
                    }
                }
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        floatingActionButton = {
            if (selectedKid != null) {
                // The button adds a goal on Goals, picks the date range on Trends, and adds an entry on Ledger.
                val tab = TrackerTab.entries[selectedTabIndex]
                ExtendedFloatingActionButton(
                    onClick = {
                        when (tab) {
                            TrackerTab.GOALS -> {
                                editingGoalId = null
                                showGoalDialog = true
                            }
                            TrackerTab.TRENDS -> showDateRangeDialog = true
                            TrackerTab.RUNNING_TALLY -> {
                                editingEntryId = null
                                buyingGoalId = null
                                showEntrySheet = true
                            }
                        }
                    },
                    expanded = true,
                    containerColor = EntryButtonNavy,
                    contentColor = Color.White,
                    icon = {
                        Icon(
                            imageVector = if (tab == TrackerTab.TRENDS) Icons.Default.DateRange else Icons.Default.Add,
                            contentDescription = null
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(
                                when (tab) {
                                    TrackerTab.GOALS -> R.string.fab_goal
                                    TrackerTab.TRENDS -> R.string.fab_dates
                                    TrackerTab.RUNNING_TALLY -> R.string.fab_entry
                                }
                            ),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                )
            }
        }
    ) { innerPadding ->
        val kidName = selectedKid
        if (kidName == null) {
            if (hasNoKids) {
                AddFirstKidContent(
                    onAddKid = { showAddKidDialog = true },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
            return@Scaffold
        }
        when (TrackerTab.entries[selectedTabIndex]) {
            TrackerTab.RUNNING_TALLY -> {
                RunningTallyTabContent(
                    kidName = kidName,
                    ledger = ledger,
                    categoryIcons = categoryIcons,
                    onEditTransaction = { transaction ->
                        editingEntryId = transaction.id
                        buyingGoalId = null
                        showEntrySheet = true
                    },
                    onDeleteTransaction = viewModel::deleteTransaction,
                    onShowGoals = { selectedTabIndex = TrackerTab.GOALS.ordinal },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }

            TrackerTab.GOALS -> {
                GoalsTabContent(
                    goals = goals,
                    actions = goalActions,
                    onAddGoal = {
                        editingGoalId = null
                        showGoalDialog = true
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }

            TrackerTab.TRENDS -> {
                TrendsTabContent(
                    balanceHistory = balanceHistory,
                    incomeSums = incomeSums,
                    spendingSums = spendingSums,
                    hiddenCategories = hiddenCategories,
                    onToggleCategory = viewModel::toggleCategorySelection,
                    allCategories = categories,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
        }
    }

    if (showAddKidDialog) {
        AddKidDialog(
            onDismiss = { showAddKidDialog = false },
            onConfirmKid = { newKidName ->
                viewModel.addKid(newKidName)
                showAddKidDialog = false
            }
        )
    }

    if (showEditKidsDialog) {
        EditKidsDialog(
            kids = kids,
            kidsWithEntries = kidsWithEntries,
            onAddKid = viewModel::addKid,
            onRemoveKid = viewModel::removeKid,
            onDismiss = { showEditKidsDialog = false }
        )
    }

    if (showRestoreConfirm) {
        RestoreBackupDialog(
            onConfirm = {
                showRestoreConfirm = false
                restoreBackupLauncher.launch(arrayOf("application/json", "*/*"))
            },
            onDismiss = { showRestoreConfirm = false }
        )
    }

    if (showManageCategoriesDialog) {
        ManageCategoriesDialog(
            categories = categories,
            usedCategories = usedCategories,
            categoryIcons = categoryIcons,
            notesByCategory = notesByCategory,
            onAddCategory = viewModel::addCategory,
            onSetCategoryIcon = viewModel::setCategoryIcon,
            onDeleteUnusedCategory = viewModel::deleteUnusedCategory,
            onDismiss = { showManageCategoriesDialog = false }
        )
    }

    val goalsState = goals
    val allGoals = goalsState?.let { it.active + it.done }.orEmpty()

    if (showGoalDialog) {
        val editingGoal = editingGoalId?.let { id -> allGoals.firstOrNull { it.goal.id == id }?.goal }
        fun closeGoalDialog() {
            showGoalDialog = false
            editingGoalId = null
        }
        if (editingGoalId == null || editingGoal != null) {
            GoalEditDialog(
                existing = editingGoal,
                onDismiss = ::closeGoalDialog,
                onSave = { name, targetCents, icon, targetDay ->
                    if (editingGoal == null) {
                        viewModel.addGoal(name, targetCents, icon, targetDay)
                    } else {
                        viewModel.updateGoal(
                            editingGoal.copy(name = name, targetCents = targetCents, icon = icon, targetDay = targetDay)
                        )
                    }
                    closeGoalDialog()
                }
            )
        }
    }

    if (showMoveDialog && goalsState != null) {
        MoveMoneyDialog(
            goals = goalsState,
            initialFromGoalId = moveFromGoalId,
            initialToGoalId = moveToGoalId,
            onDismiss = { showMoveDialog = false },
            onMove = { fromGoalId, toGoalId, amountCents ->
                viewModel.moveMoney(fromGoalId, toGoalId, amountCents)
                showMoveDialog = false
            }
        )
    }

    val detailGoal = detailGoalId?.let { id -> allGoals.firstOrNull { it.goal.id == id } }
    if (detailGoal != null && goalsState != null) {
        GoalDetailSheet(
            progress = detailGoal,
            history = goalsState.history[detailGoal.goal.id].orEmpty(),
            actions = goalActions,
            onDismiss = { detailGoalId = null }
        )
    }

    val entryKid = selectedKid
    // While editing, wait for the entry to be found in the loaded ledger.
    val editingEntry = editingEntryId?.let { id -> ledger?.transactions?.firstOrNull { it.id == id } }
    val buyingGoal = buyingGoalId?.let { id -> goalsState?.active?.firstOrNull { it.goal.id == id } }
    if (showEntrySheet && entryKid != null && (editingEntryId == null || editingEntry != null) &&
        (buyingGoalId == null || buyingGoal != null)
    ) {
        fun closeSheet() {
            showEntrySheet = false
            editingEntryId = null
            buyingGoalId = null
        }
        // Keyed so switching between entries starts the form fresh.
        key(editingEntryId, buyingGoalId) {
            EntryBottomSheet(
                kidName = entryKid,
                categories = categories,
                lastCategoryByType = lastCategoryByType,
                existing = editingEntry,
                money = ledger?.let { EntryMoney.from(it, goalsState, editingEntry) },
                buyingGoal = buyingGoal,
                onDismiss = ::closeSheet,
                onAddCategory = viewModel::addCategory,
                onSave = { draft ->
                    when {
                        buyingGoal != null -> viewModel.buyGoal(
                            goal = buyingGoal.goal,
                            title = draft.title,
                            amountCents = draft.amountCents,
                            category = draft.category,
                            timestamp = draft.timestamp
                        )
                        editingEntry == null -> viewModel.addTransaction(
                            title = draft.title,
                            amountCents = draft.amountCents,
                            type = draft.type,
                            category = draft.category,
                            timestamp = draft.timestamp,
                            split = draft.split,
                            coverGoalId = draft.coverGoalId,
                            repeat = draft.repeat
                        )
                        else -> viewModel.updateTransaction(
                            editingEntry.copy(
                                title = draft.title,
                                amountCents = draft.amountCents,
                                type = draft.type,
                                category = draft.category,
                                timestamp = draft.timestamp
                            ),
                            split = draft.split,
                            coverGoalId = draft.coverGoalId
                        )
                    }
                    closeSheet()
                },
                onDelete = {
                    editingEntry?.let(viewModel::deleteTransaction)
                    closeSheet()
                },
                existingRepeat = editingEntry?.recurringId
                    ?.let { id -> recurring.firstOrNull { it.id == id } }
                    ?.frequency
            )
        }
    }

    if (showDateRangeDialog) {
        TrendsDateRangeDialog(
            startDateMillis = startDateMillis,
            endDateMillis = endDateMillis,
            quickRange = quickRange,
            onDismiss = { showDateRangeDialog = false },
            onQuickRange = { range ->
                viewModel.setQuickRange(range)
                showDateRangeDialog = false
            },
            onApply = { start, end ->
                viewModel.setDateRange(start, end)
                showDateRangeDialog = false
            }
        )
    }

    val recurringKid = selectedKid
    if (showRecurringDialog && recurringKid != null) {
        RepeatingEntriesDialog(
            kidName = recurringKid,
            entries = recurring,
            goalNames = goalsState?.active.orEmpty().associate { it.goal.id to it.goal.name },
            onEdit = { editingRecurringId = it.id },
            onStop = viewModel::stopRecurring,
            onDismiss = { showRecurringDialog = false }
        )
    }

    val editingRecurring = editingRecurringId?.let { id -> recurring.firstOrNull { it.id == id } }
    if (editingRecurring != null && recurringKid != null) {
        key(editingRecurringId) {
            EntryBottomSheet(
                kidName = recurringKid,
                categories = categories,
                lastCategoryByType = lastCategoryByType,
                existing = null,
                money = ledger?.let { EntryMoney.from(it, goalsState, null) },
                buyingGoal = null,
                onDismiss = { editingRecurringId = null },
                onAddCategory = viewModel::addCategory,
                onSave = { draft ->
                    viewModel.updateRecurring(
                        editingRecurring.copy(
                            title = draft.title,
                            amountCents = draft.amountCents,
                            type = draft.type,
                            category = draft.category,
                            frequency = draft.repeat ?: editingRecurring.frequency,
                            goalId = draft.split?.goalId,
                            goalCents = draft.split?.amountCents
                        )
                    )
                    editingRecurringId = null
                },
                onDelete = { },
                schedule = editingRecurring
            )
        }
    }
}

@Composable
fun AddFirstKidContent(
    onAddKid: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onAddKid) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.welcome_add_kid))
        }
    }
}
