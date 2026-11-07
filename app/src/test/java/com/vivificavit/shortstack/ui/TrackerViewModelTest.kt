package com.vivificavit.shortstack.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vivificavit.shortstack.R
import com.vivificavit.shortstack.data.local.AppDatabase
import com.vivificavit.shortstack.data.local.SelectedKidStore
import com.vivificavit.shortstack.data.model.Goal
import com.vivificavit.shortstack.data.model.GoalSplit
import com.vivificavit.shortstack.data.model.Transaction
import com.vivificavit.shortstack.data.model.TransactionType
import com.vivificavit.shortstack.repository.TransactionRepository
import com.vivificavit.shortstack.ui.viewmodel.TrackerViewModel
import com.vivificavit.shortstack.ui.viewmodel.UiMessage
import com.vivificavit.shortstack.util.DateRanges
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.FileNotFoundException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TrackerViewModelTest {
    private class FakeStore(initial: String? = null) : SelectedKidStore {
        private val state = MutableStateFlow(initial)
        override val lastSelectedKid: Flow<String?> = state
        override suspend fun setLastSelectedKid(name: String) {
            state.value = name
        }
    }

    private lateinit var db: AppDatabase
    private lateinit var repository: TransactionRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = TransactionRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    // Generous, because CI runners can be slow; a passing check returns as soon as the value arrives.
    private suspend fun <T> Flow<T>.awaitValue(predicate: (T) -> Boolean): T =
        withTimeout(20_000) { first(predicate) }

    @Test
    fun freshInstallHasNoKidsUntilOneIsAdded() = runBlocking {
        val store = FakeStore()
        val vm = TrackerViewModel(repository, store)

        vm.hasNoKids.awaitValue { it }
        assertEquals(null, vm.selectedKid.value)

        vm.addKid("Sam")
        vm.selectedKid.awaitValue { it == "Sam" }
        // The new kid shows as soon as it's saved; remembering the choice follows a moment later.
        store.lastSelectedKid.awaitValue { it == "Sam" }
        vm.hasNoKids.awaitValue { !it }
        Unit
    }

    @Test
    fun reopensOnLastSelectedKidAndFallsBackWhenRemoved() = runBlocking {
        repository.insertKid("Ada")
        repository.insertKid("Sam")

        val vm = TrackerViewModel(repository, FakeStore(initial = "Sam"))
        vm.selectedKid.awaitValue { it == "Sam" }

        vm.removeKid("Sam")
        vm.selectedKid.awaitValue { it == "Ada" }

        val staleVm = TrackerViewModel(repository, FakeStore(initial = "Ghost"))
        staleVm.selectedKid.awaitValue { it == "Ada" }
        Unit
    }

    @Test
    fun customCategoriesAreShownInChartsByDefault() = runBlocking {
        repository.insertTransaction(
            Transaction(
                kidName = "Sam",
                title = "Chips",
                amountCents = 250,
                type = TransactionType.DEBIT,
                category = "Snacks"
            )
        )
        // A new ViewModel stands in for the app being restarted.
        val vm = TrackerViewModel(repository, FakeStore(initial = "Sam"))

        vm.spendingSums.awaitValue { sums -> sums.map { it.category } == listOf("Snacks") }
        assertEquals(emptySet<String>(), vm.hiddenCategories.value)

        vm.toggleCategorySelection("Snacks")
        vm.hiddenCategories.awaitValue { it == setOf("snacks") }
        vm.toggleCategorySelection("snacks")
        vm.hiddenCategories.awaitValue { it.isEmpty() }
        Unit
    }

    @Test
    fun removingKidDeletesOnlyTheirEntries() = runBlocking {
        repository.insertTransaction(
            Transaction(kidName = "Sam", title = "Allowance", amountCents = 500, type = TransactionType.CREDIT)
        )
        repository.insertTransaction(
            Transaction(kidName = "Ada", title = "Gift", amountCents = 700, type = TransactionType.CREDIT)
        )
        val vm = TrackerViewModel(repository, FakeStore())
        vm.kids.awaitValue { it.toSet() == setOf("Sam", "Ada") }

        // Subscribe before acting: uiMessages doesn't replay past messages.
        val message = async(start = CoroutineStart.UNDISPATCHED) {
            vm.uiMessages.awaitValue { true }
        }
        vm.removeKid("Sam")
        assertEquals(UiMessage(R.string.message_kid_removed, listOf("Sam")), message.await())
        vm.kids.awaitValue { it == listOf("Ada") }
        assertEquals(listOf("Ada"), repository.allTransactions.first().map { it.kidName })
    }

    @Test
    fun suggestsMostRecentCategoryPerType() = runBlocking {
        val vm = TrackerViewModel(repository, FakeStore())
        vm.lastCategoryByType.awaitValue { it.isEmpty() }

        repository.insertTransaction(
            Transaction(kidName = "Sam", title = "Allowance", amountCents = 500, type = TransactionType.CREDIT, category = "Allowance", timestamp = 1_000)
        )
        repository.insertTransaction(
            Transaction(kidName = "Sam", title = "Lego", amountCents = 900, type = TransactionType.DEBIT, category = "Toy", timestamp = 2_000)
        )
        repository.insertTransaction(
            Transaction(kidName = "Ada", title = "Comic", amountCents = 300, type = TransactionType.DEBIT, category = "Book", timestamp = 3_000)
        )

        vm.lastCategoryByType.awaitValue {
            it == mapOf(TransactionType.CREDIT to "Allowance", TransactionType.DEBIT to "Book")
        }
        Unit
    }

    @Test
    fun ledgerIsNullUntilLoadedThenHasBalanceAndEntries() = runBlocking {
        repository.insertTransaction(
            Transaction(kidName = "Sam", title = "Allowance", amountCents = 500, type = TransactionType.CREDIT)
        )
        val vm = TrackerViewModel(repository, FakeStore(initial = "Sam"))
        assertEquals(null, vm.ledger.value)

        val ledger = vm.ledger.awaitValue { it != null }!!
        assertEquals(500L, ledger.balanceCents)
        assertEquals(listOf("Allowance"), ledger.transactions.map { it.title })
    }

    @Test
    fun editingAnEntryKeepsItsIdAndUpdatesBalance() = runBlocking {
        repository.insertTransaction(
            Transaction(kidName = "Sam", title = "Allowance", amountCents = 500, type = TransactionType.CREDIT)
        )
        val vm = TrackerViewModel(repository, FakeStore(initial = "Sam"))
        val original = vm.ledger.awaitValue { it != null }!!.transactions.single()

        vm.updateTransaction(original.copy(title = "Chores", amountCents = 750))
        val edited = vm.ledger.awaitValue { it?.balanceCents == 750L }!!.transactions.single()
        assertEquals(original.id, edited.id)
        assertEquals("Chores", edited.title)
    }

    @Test
    fun undoRestoresDeletedEntry() = runBlocking {
        repository.insertTransaction(
            Transaction(kidName = "Sam", title = "Comic", amountCents = 300, type = TransactionType.DEBIT)
        )
        val vm = TrackerViewModel(repository, FakeStore(initial = "Sam"))
        val entry = vm.ledger.awaitValue { it != null }!!.transactions.single()

        val message = async(start = CoroutineStart.UNDISPATCHED) {
            vm.uiMessages.awaitValue { true }
        }
        vm.deleteTransaction(entry)
        val deleted = message.await()
        assertEquals(R.string.message_entry_deleted, deleted.resId)
        vm.ledger.awaitValue { it?.transactions?.isEmpty() == true }

        deleted.onUndo!!.invoke()
        val restored = vm.ledger.awaitValue { it?.transactions?.size == 1 }!!
        assertEquals(entry, restored.transactions.single())
        assertEquals(-300L, restored.balanceCents)
    }

    @Test
    fun oversizedRestoreIsRejectedAndKeepsExistingData() = runBlocking {
        val existing = Transaction(kidName = "Sam", title = "Comic", amountCents = 300, type = TransactionType.DEBIT)
        repository.insertTransaction(existing)

        val tooBig = ByteArray(TransactionRepository.MAX_BACKUP_BYTES + 1) { ' '.code.toByte() }
        val result = repository.importFromJson({ tooBig.inputStream() }, replaceExisting = true)

        assertTrue(result.isFailure)
        assertEquals(listOf("Comic"), repository.getTransactionsForKid("Sam").first().map { it.title })
    }

    @Test
    fun restoreReplacesKidsAndCategoriesToo() = runBlocking {
        repository.insertKid("Max")
        repository.addCategory("Old category")
        repository.insertTransaction(
            Transaction(kidName = "Max", title = "Comic", amountCents = 300, type = TransactionType.DEBIT)
        )
        val backup = """{"kids":[{"name":"Sam"}],"categories":["Job"],"transactions":[
            {"kidName":"Sam","title":"Lawn","amountCents":500,"type":"CREDIT","category":"Job","timestamp":1}]}"""

        assertTrue(repository.importFromJson({ backup.byteInputStream() }, replaceExisting = true).isSuccess)

        assertEquals(listOf("Sam"), repository.kidNames.first())
        assertEquals(listOf("Job"), repository.categories.first())
        assertEquals(listOf("Lawn"), repository.allTransactions.first().map { it.title })
    }

    @Test
    fun fileThatCantBeOpenedFailsWithoutCrashingOrTouchingData() = runBlocking {
        repository.insertTransaction(
            Transaction(kidName = "Sam", title = "Comic", amountCents = 300, type = TransactionType.DEBIT)
        )

        assertTrue(repository.importFromJson({ throw FileNotFoundException("gone") }, replaceExisting = true).isFailure)
        assertTrue(repository.exportToJson { null }.isFailure)
        assertTrue(repository.exportToCsv { throw SecurityException("no access") }.isFailure)
        assertEquals(listOf("Comic"), repository.allTransactions.first().map { it.title })
    }

    @Test
    fun chosenIconsSurviveBackupAndRestore() = runBlocking {
        repository.addCategory("Snacks")
        repository.setCategoryIcon("snacks", "candy")
        assertEquals(mapOf("snacks" to "candy"), repository.categoryIcons.first())

        val file = java.io.ByteArrayOutputStream()
        assertTrue(repository.exportToJson { file }.isSuccess)
        repository.setCategoryIcon("Snacks", null)
        assertEquals(emptyMap<String, String>(), repository.categoryIcons.first())

        assertTrue(repository.importFromJson({ file.toByteArray().inputStream() }, replaceExisting = true).isSuccess)
        assertEquals(mapOf("snacks" to "candy"), repository.categoryIcons.first())
    }

    private suspend fun income(cents: Long, title: String = "Allowance") =
        repository.insertTransaction(
            Transaction(kidName = "Sam", title = title, amountCents = cents, type = TransactionType.CREDIT)
        )

    private suspend fun goal(name: String, targetCents: Long = 7_000, createdAt: Long = 1) =
        repository.saveGoal(Goal(kidName = "Sam", name = name, targetCents = targetCents, createdAt = createdAt))

    @Test
    fun settingMoneyAsideChangesPocketMoneyButNotTheTotal() = runBlocking {
        income(5_000)
        val vm = TrackerViewModel(repository, FakeStore(initial = "Sam"))
        vm.addGoal("New video game", 7_000, icon = null)
        val gameId = vm.goals.awaitValue { it?.active?.size == 1 }!!.active.single().goal.id

        vm.moveMoney(fromGoalId = null, toGoalId = gameId, amountCents = 2_000)
        val ledger = vm.ledger.awaitValue { it?.pocketCents == 3_000L }!!
        assertEquals(5_000L, ledger.balanceCents)
        assertEquals(2_000L, ledger.savedCents)
        assertEquals(5_000L, vm.goals.awaitValue { it != null }!!.active.single().remainingCents)

        // Only pocket money can be set aside.
        assertEquals(false, repository.moveMoney("Sam", null, gameId, 3_001))
    }

    @Test
    fun incomeSplitIsSetAsideAndUndoBringsItBack() = runBlocking {
        val gameId = goal("Game")
        val vm = TrackerViewModel(repository, FakeStore(initial = "Sam"))
        vm.goals.awaitValue { it != null }
        vm.addTransaction("Allowance", 1_000, TransactionType.CREDIT, "Allowance", split = GoalSplit(gameId, 200))
        val ledger = vm.ledger.awaitValue { it?.savedCents == 200L }!!
        assertEquals(800L, ledger.pocketCents)
        val entry = ledger.transactions.single()
        assertEquals(listOf(200L), ledger.movesByEntry[entry.id]?.map { it.amountCents })

        val message = async(start = CoroutineStart.UNDISPATCHED) {
            vm.uiMessages.awaitValue { it.resId == R.string.message_entry_deleted }
        }
        vm.deleteTransaction(entry)
        val deleted = message.await()
        vm.ledger.awaitValue { it?.transactions?.isEmpty() == true && it.savedCents == 0L }

        deleted.onUndo!!.invoke()
        val restored = vm.ledger.awaitValue { it?.savedCents == 200L }!!
        assertEquals(800L, restored.pocketCents)
        assertEquals(entry, restored.transactions.single())
    }

    @Test
    fun overspendingTakesFromTheChosenGoalAndDeletingGivesItBack() = runBlocking {
        income(5_000)
        val older = goal("Helmet", createdAt = 1)
        val newer = goal("Game", createdAt = 2)
        repository.moveMoney("Sam", null, older, 2_000)
        repository.moveMoney("Sam", null, newer, 1_000)
        val vm = TrackerViewModel(repository, FakeStore(initial = "Sam"))
        vm.ledger.awaitValue { it?.pocketCents == 2_000L }

        // $40 with $20 of pocket money: $10 from the chosen goal, then $10 from the other.
        vm.addTransaction("Lego set", 4_000, TransactionType.DEBIT, "Toy", coverGoalId = newer)
        val ledger = vm.ledger.awaitValue { it?.balanceCents == 1_000L && it.savedCents == 1_000L }!!
        assertEquals(0L, ledger.pocketCents)
        val saved = vm.goals.awaitValue { it != null }!!.active.associate { it.goal.id to it.savedCents }
        assertEquals(mapOf(older to 1_000L, newer to 0L), saved)

        vm.deleteTransaction(ledger.transactions.first { it.title == "Lego set" })
        val back = vm.goals.awaitValue { state -> state?.active?.sumOf { it.savedCents } == 3_000L }!!
        assertEquals(mapOf(older to 2_000L, newer to 1_000L), back.active.associate { it.goal.id to it.savedCents })
        assertEquals(2_000L, back.pocketCents)
    }

    @Test
    fun buyingAGoalLeavesTheChangeInPocketMoneyAndDeletingTheExpenseReopensIt() = runBlocking {
        income(8_000)
        val gameId = goal("Game")
        repository.moveMoney("Sam", null, gameId, 7_000)
        val vm = TrackerViewModel(repository, FakeStore(initial = "Sam"))
        val game = vm.goals.awaitValue { it?.active?.singleOrNull()?.isReached == true }!!.active.single().goal

        vm.buyGoal(game, "Game", 6_500, "Video Game", timestamp = 10)
        val goals = vm.goals.awaitValue { it?.done?.size == 1 }!!
        assertEquals(emptyList<Any>(), goals.active)
        assertEquals(6_500L, goals.done.single().paidCents)
        val ledger = vm.ledger.awaitValue { it?.balanceCents == 1_500L }!!
        assertEquals(1_500L, ledger.pocketCents)

        vm.deleteTransaction(ledger.transactions.first { it.title == "Game" })
        val reopened = vm.goals.awaitValue { it?.active?.size == 1 }!!.active.single()
        assertEquals(7_000L, reopened.savedCents)
        assertEquals(null, reopened.goal.completedAt)
    }

    @Test
    fun deletingAGoalReturnsItsMoneyAndUndoPutsItBack() = runBlocking {
        income(5_000)
        val gameId = goal("Game")
        repository.moveMoney("Sam", null, gameId, 2_000)
        val vm = TrackerViewModel(repository, FakeStore(initial = "Sam"))
        val game = vm.goals.awaitValue { it?.active?.size == 1 }!!.active.single().goal

        val message = async(start = CoroutineStart.UNDISPATCHED) {
            vm.uiMessages.awaitValue { it.resId == R.string.message_goal_deleted }
        }
        vm.deleteGoal(game)
        val deleted = message.await()
        vm.ledger.awaitValue { it?.pocketCents == 5_000L && it.activeGoalCount == 0 }

        deleted.onUndo!!.invoke()
        val restored = vm.goals.awaitValue { it?.active?.size == 1 }!!
        assertEquals(2_000L, restored.active.single().savedCents)
        assertEquals(3_000L, restored.pocketCents)
    }

    @Test
    fun reachingAGoalCheers() = runBlocking {
        income(5_000)
        val gameId = goal("Game", targetCents = 2_000)
        val vm = TrackerViewModel(repository, FakeStore(initial = "Sam"))
        vm.goals.awaitValue { it?.active?.size == 1 }

        val message = async(start = CoroutineStart.UNDISPATCHED) {
            vm.uiMessages.awaitValue { it.resId == R.string.message_goal_reached }
        }
        vm.moveMoney(null, gameId, 2_000)
        assertEquals(listOf("Game"), message.await().args)
    }

    @Test
    fun goalsAndSplitsSurviveBackupAndRestore() = runBlocking {
        income(5_000)
        val gameId = goal("Game")
        repository.insertTransaction(
            Transaction(kidName = "Sam", title = "Chores", amountCents = 1_000, type = TransactionType.CREDIT),
            split = GoalSplit(gameId, 200)
        )
        repository.moveMoney("Sam", null, gameId, 1_000)

        val file = java.io.ByteArrayOutputStream()
        assertTrue(repository.exportToJson { file }.isSuccess)
        repository.deleteKidAndEntries("Sam")
        assertTrue(repository.importFromJson({ file.toByteArray().inputStream() }, replaceExisting = true).isSuccess)

        val money = repository.getMoneyForKid("Sam").first()
        val restoredGoal = money.goals.single()
        assertEquals("Game", restoredGoal.name)
        assertEquals(1_200L, money.moves.filter { it.goalId == restoredGoal.id }.sumOf { it.amountCents })
        val chores = money.transactions.single { it.title == "Chores" }
        assertEquals(listOf(200L), money.moves.filter { it.transactionId == chores.id }.map { it.amountCents })
    }

    @Test
    fun removingKidRemovesTheirGoals() = runBlocking {
        income(5_000)
        val gameId = goal("Game")
        repository.moveMoney("Sam", null, gameId, 2_000)

        repository.deleteKidAndEntries("Sam")
        assertEquals(emptyList<Goal>(), repository.getGoalsForKid("Sam").first())
        assertEquals(emptyList<Any>(), repository.getGoalMovesForKid("Sam").first())
    }

    @Test
    fun quickRangesFollowTodayAndAllTimeClearsTheRange() = runBlocking {
        val vm = TrackerViewModel(repository, FakeStore())
        vm.setQuickRange(DateRanges.QuickRange.LAST_WEEK)
        val week = DateRanges.quickRange(DateRanges.QuickRange.LAST_WEEK, vm.today.value)
        vm.startDateMillis.awaitValue { it == week.first }
        vm.endDateMillis.awaitValue { it == week.second }
        assertEquals(DateRanges.QuickRange.LAST_WEEK, vm.quickRange.value)

        // Picking days by hand replaces the quick range; All time clears both.
        vm.setDateRange(1L, null)
        vm.startDateMillis.awaitValue { it == 1L }
        assertEquals(null, vm.quickRange.value)
        vm.setQuickRange(DateRanges.QuickRange.ALL_TIME)
        vm.startDateMillis.awaitValue { it == null }
        assertEquals(null, vm.quickRange.value)
    }
}

