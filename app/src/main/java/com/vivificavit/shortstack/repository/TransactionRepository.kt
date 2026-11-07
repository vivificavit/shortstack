package com.vivificavit.shortstack.repository

import androidx.room.withTransaction
import com.vivificavit.shortstack.data.backup.BackupCodec
import com.vivificavit.shortstack.data.export.CsvExport
import com.vivificavit.shortstack.data.local.AppDatabase
import com.vivificavit.shortstack.data.local.CategorySum
import com.vivificavit.shortstack.data.model.CategoryEntity
import com.vivificavit.shortstack.data.model.DefaultCategories
import com.vivificavit.shortstack.data.model.Goal
import com.vivificavit.shortstack.data.model.GoalMove
import com.vivificavit.shortstack.data.model.GoalMoveReason
import com.vivificavit.shortstack.data.model.GoalSplit
import com.vivificavit.shortstack.data.model.KidProfile
import com.vivificavit.shortstack.data.model.RecurringEntry
import com.vivificavit.shortstack.data.model.Transaction
import com.vivificavit.shortstack.data.model.TransactionType
import com.vivificavit.shortstack.util.GoalMath
import com.vivificavit.shortstack.util.RecurringMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.ZoneId

/** What deleting an entry removed or changed, so Undo can put it back exactly. */
data class DeletedEntry(
    val transaction: Transaction,
    val moves: List<GoalMove>,
    /** Goals the entry had paid for, as they were before they reopened. */
    val boughtGoals: List<Goal>,
    /** Moves the delete added to keep goals in line with the balance. */
    val addedMoveIds: List<Long>
)

/** One kid's entries and moves (newest first) and goals, read together so they always agree. */
data class KidMoneySnapshot(
    val transactions: List<Transaction>,
    val goals: List<Goal>,
    val moves: List<GoalMove>
)

/** How many entries a repeating entry just added. */
data class AddedRecurring(val entry: RecurringEntry, val count: Int)

/** What deleting a goal removed, so Undo can put it back exactly. */
data class DeletedGoal(
    val goal: Goal,
    val moves: List<GoalMove>
)

class TransactionRepository(
    private val database: AppDatabase
) {
    private val transactionDao = database.transactionDao()
    private val goalDao = database.goalDao()
    private val recurringDao = database.recurringDao()

    val kidNames: Flow<List<String>> = transactionDao.getAllKidNames()

    /** Kids who have at least one goal. */
    val kidsWithGoals: Flow<List<String>> = goalDao.getKidsWithGoals()

    fun getGoalsForKid(kidName: String): Flow<List<Goal>> = goalDao.getGoalsForKid(kidName)

    /** Newest first. */
    fun getGoalMovesForKid(kidName: String): Flow<List<GoalMove>> = goalDao.getMovesForKid(kidName)

    /**
     * Re-reads everything about a kid's money in one database transaction whenever
     * any of it changes. Separate flows per table could briefly disagree (an entry
     * gone but its goal split still there), which would flash wrong totals.
     */
    fun getMoneyForKid(kidName: String): Flow<KidMoneySnapshot> =
        database.invalidationTracker.createFlow("transactions", "goals", "goal_moves")
            .conflate()
            .map {
                database.withTransaction {
                    KidMoneySnapshot(
                        transactions = transactionDao.getTransactionsForKidOnce(kidName),
                        goals = goalDao.getGoalsForKidOnce(kidName),
                        moves = goalDao.getMovesForKidOnce(kidName)
                    )
                }
            }
            .flowOn(Dispatchers.IO)

    /** Kids with at least one repeating entry. */
    val kidsWithRecurring: Flow<List<String>> = recurringDao.getKidsWithRecurring()

    /** Categories that repeating entries will use. */
    val recurringCategories: Flow<List<String>> = recurringDao.getRecurringCategories()

    /** A kid's repeating entries, oldest first. */
    fun getRecurringForKid(kidName: String): Flow<List<RecurringEntry>> = recurringDao.getRecurringForKid(kidName)

    val categories: Flow<List<String>> = transactionDao.getAllCategoryNames()

    /** Icons the user picked, keyed by lower-cased category name. */
    val categoryIcons: Flow<Map<String, String>> = transactionDao.getAllCategories().map { list ->
        list.mapNotNull { category -> category.icon?.let { category.name.lowercase() to it } }.toMap()
    }

    val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions()

    fun getTransactionsForKid(kidName: String): Flow<List<Transaction>> {
        return transactionDao.getTransactionsForKid(kidName)
    }

    fun getCreditTotalsByCategoryForKidInRange(
        kidName: String,
        startTimestamp: Long,
        endTimestamp: Long
    ): Flow<List<CategorySum>> {
        return transactionDao.getCreditTotalsByCategoryForKidInRange(
            kidName = kidName,
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp
        )
    }

    fun getDebitTotalsByCategoryForKidInRange(
        kidName: String,
        startTimestamp: Long,
        endTimestamp: Long
    ): Flow<List<CategorySum>> {
        return transactionDao.getDebitTotalsByCategoryForKidInRange(
            kidName = kidName,
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp
        )
    }

    suspend fun seedDefaultsIfNeeded() {
        withContext(Dispatchers.IO) {
            if (transactionDao.getAllCategoryNames().first().isEmpty()) {
                DefaultCategories.DEFAULT_CATEGORIES.forEach { name ->
                    transactionDao.insertCategory(CategoryEntity(name = name))
                }
            }
        }
    }

    suspend fun insertKid(name: String) {
        withContext(Dispatchers.IO) {
            transactionDao.insertKid(KidProfile(name = name.trim()))
        }
    }

    /** Permanently deletes a kid with all of their entries and goals, together or not at all. */
    suspend fun deleteKidAndEntries(name: String) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                transactionDao.deleteTransactionsForKid(name)
                goalDao.deleteMovesForKid(name)
                goalDao.deleteGoalsForKid(name)
                recurringDao.deleteRecurringForKid(name)
                transactionDao.deleteKid(name)
            }
        }
    }

    suspend fun addCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        withContext(Dispatchers.IO) {
            transactionDao.insertCategory(CategoryEntity(name = trimmed))
        }
    }

    /** Sets the icon for [name]; null goes back to picking one automatically. */
    suspend fun setCategoryIcon(name: String, icon: String?) {
        withContext(Dispatchers.IO) {
            transactionDao.setCategoryIcon(name.trim(), icon)
        }
    }

    suspend fun deleteUnusedCategory(name: String): Boolean {
        return withContext(Dispatchers.IO) {
            val deletedRows = transactionDao.deleteUnusedCategory(name.trim())
            deletedRows > 0
        }
    }

    /**
     * Adds an entry, or replaces the one with the same id. For income, [split]
     * sets part of it aside for a goal. If the kid ends up with less than their
     * goals hold, the difference comes out of [coverGoalId] first. Returns the entry's id.
     */
    suspend fun insertTransaction(
        transaction: Transaction,
        split: GoalSplit? = null,
        coverGoalId: Long? = null
    ): Long = withContext(Dispatchers.IO) {
        database.withTransaction {
            transactionDao.insertKid(KidProfile(name = transaction.kidName))
            transactionDao.insertCategory(CategoryEntity(name = transaction.category))
            val id = transactionDao.insert(transaction)

            // Start over on this entry's split and cover. A split to a goal that's
            // since been bought stays, because that money has already been spent.
            val goals = goalDao.getGoalsForKidOnce(transaction.kidName).associateBy { it.id }
            val stale = goalDao.getMovesForTransaction(id).filter { move ->
                move.reason == GoalMoveReason.COVER ||
                    (move.reason == GoalMoveReason.SPLIT && goals[move.goalId]?.completedAt == null)
            }
            goalDao.deleteMoves(stale.map { it.id })

            val splitGoal = split?.let { goals[it.goalId] }
            if (split != null && splitGoal != null && splitGoal.completedAt == null &&
                transaction.type == TransactionType.CREDIT && split.amountCents in 1..transaction.amountCents
            ) {
                goalDao.insertMoves(
                    listOf(
                        GoalMove(
                            kidName = transaction.kidName,
                            goalId = splitGoal.id,
                            amountCents = split.amountCents,
                            reason = GoalMoveReason.SPLIT,
                            timestamp = transaction.timestamp,
                            transactionId = id
                        )
                    )
                )
            }
            rebalance(transaction.kidName, preferredGoalId = coverGoalId, transactionId = id)
            id
        }
    }

    /**
     * Deletes an entry with its goal split and cover. A goal it paid for
     * goes back to being saved for, with its money.
     */
    suspend fun deleteTransaction(transaction: Transaction): DeletedEntry = withContext(Dispatchers.IO) {
        database.withTransaction {
            val moves = goalDao.getMovesForTransaction(transaction.id)
            val boughtGoals = moves
                .filter { it.reason == GoalMoveReason.PURCHASE }
                .mapNotNull { goalDao.getGoal(it.goalId) }
                .filter { it.completedAt != null }
            transactionDao.delete(transaction)
            goalDao.deleteMoves(moves.map { it.id })
            boughtGoals.forEach { goalDao.upsertGoal(it.copy(completedAt = null)) }
            DeletedEntry(
                transaction = transaction,
                moves = moves,
                boughtGoals = boughtGoals,
                addedMoveIds = rebalance(transaction.kidName)
            )
        }
    }

    /** Undoes [deleteTransaction], leaving out any goal deleted since. */
    suspend fun restoreEntry(deleted: DeletedEntry) {
        val kidName = deleted.transaction.kidName
        withContext(Dispatchers.IO) {
            database.withTransaction {
                goalDao.deleteMoves(deleted.addedMoveIds)
                transactionDao.insertKid(KidProfile(name = kidName))
                transactionDao.insertCategory(CategoryEntity(name = deleted.transaction.category))
                transactionDao.insert(deleted.transaction)
                deleted.boughtGoals.forEach { bought ->
                    goalDao.getGoal(bought.id)?.let { goalDao.upsertGoal(it.copy(completedAt = bought.completedAt)) }
                }
                val goalIds = goalDao.getGoalsForKidOnce(kidName).map { it.id }.toSet()
                goalDao.insertMoves(deleted.moves.filter { it.goalId in goalIds })
                rebalance(kidName)
            }
        }
    }

    /**
     * Starts a repeating entry and adds the ones already due: the first, when
     * it's dated today or earlier, and any missed since. A start in the future
     * adds nothing yet. For spending, [coverGoalId] pays first if the first
     * entry needs goal money. Returns how many it added.
     */
    suspend fun addRecurring(
        entry: RecurringEntry,
        coverGoalId: Long? = null,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Int = withContext(Dispatchers.IO) {
        database.withTransaction {
            transactionDao.insertKid(KidProfile(name = entry.kidName))
            transactionDao.insertCategory(CategoryEntity(name = entry.category))
            val id = recurringDao.upsertRecurring(entry.copy(occurrences = 0))
            addDue(entry.copy(id = id, occurrences = 0), now, zone, firstCoverGoalId = coverGoalId)
        }
    }

    /**
     * Saves changes to a repeating entry. They apply to the entries it adds from
     * now on. A new frequency restarts the schedule from its next due date, so
     * it neither skips ahead nor fills in entries that were never due.
     */
    suspend fun saveRecurring(entry: RecurringEntry, zone: ZoneId = ZoneId.systemDefault()) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                transactionDao.insertCategory(CategoryEntity(name = entry.category))
                // Keep the count from the database, in case entries were added while this was being edited.
                val current = recurringDao.getRecurring(entry.id) ?: return@withTransaction
                val saved = if (entry.frequency == current.frequency) {
                    entry.copy(occurrences = current.occurrences)
                } else {
                    val next = RecurringMath.occurrence(current.startTimestamp, current.frequency, current.occurrences, zone)
                    entry.copy(startTimestamp = next.toInstant().toEpochMilli(), occurrences = 0)
                }
                recurringDao.upsertRecurring(saved)
            }
        }
    }

    /** Stops a repeating entry. Entries it already added stay in the ledger. */
    suspend fun deleteRecurring(entry: RecurringEntry): RecurringEntry = withContext(Dispatchers.IO) {
        val current = recurringDao.getRecurring(entry.id) ?: entry
        recurringDao.deleteRecurring(entry.id)
        current
    }

    /** Undoes [deleteRecurring]; anything that came due in between is added on the next catch-up. */
    suspend fun restoreRecurring(entry: RecurringEntry) {
        withContext(Dispatchers.IO) { recurringDao.upsertRecurring(entry) }
    }

    /** Adds every repeating entry that has come due, for every kid. Each is only ever added once. */
    suspend fun addDueRecurring(
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): List<AddedRecurring> = withContext(Dispatchers.IO) {
        database.withTransaction {
            // Read inside the transaction, so two catch-ups at once can't both add the same entry.
            recurringDao.getAllRecurringOnce().mapNotNull { entry ->
                addDue(entry, now, zone).takeIf { it > 0 }?.let { AddedRecurring(entry, it) }
            }
        }
    }

    private suspend fun addDue(entry: RecurringEntry, now: Long, zone: ZoneId, firstCoverGoalId: Long? = null): Int {
        val due = RecurringMath.dueTimestamps(entry, now, zone)
        due.forEachIndexed { index, timestamp ->
            insertTransaction(
                Transaction(
                    kidName = entry.kidName,
                    title = entry.title,
                    amountCents = entry.amountCents,
                    type = entry.type,
                    category = entry.category,
                    timestamp = timestamp,
                    recurringId = entry.id
                ),
                split = recurringSplit(entry),
                coverGoalId = firstCoverGoalId.takeIf { index == 0 }
            )
        }
        if (due.isNotEmpty()) recurringDao.upsertRecurring(entry.copy(occurrences = entry.occurrences + due.size))
        return due.size
    }

    /**
     * What a repeating income sets aside this time: its fixed amount, but no more
     * than the goal still needs, so the rest stays pocket money. Nothing once the
     * goal is reached, bought or deleted.
     */
    private suspend fun recurringSplit(entry: RecurringEntry): GoalSplit? {
        if (entry.type != TransactionType.CREDIT) return null
        val goalId = entry.goalId ?: return null
        val cents = entry.goalCents ?: return null
        val goal = goalDao.getGoal(goalId)?.takeIf { it.completedAt == null } ?: return null
        val saved = goalDao.getMovesForGoal(goalId).sumOf { it.amountCents }
        val amount = minOf(cents, entry.amountCents, GoalMath.remainingCents(goal, saved))
        return if (amount > 0L) GoalSplit(goalId, amount) else null
    }

    /** Adds a goal, or saves changes to one (matched by id). Returns its id. */
    suspend fun saveGoal(goal: Goal): Long = withContext(Dispatchers.IO) {
        database.withTransaction {
            transactionDao.insertKid(KidProfile(name = goal.kidName))
            goalDao.upsertGoal(goal)
        }
    }

    /** Deletes a goal; anything saved for it goes back to pocket money. */
    suspend fun deleteGoal(goal: Goal): DeletedGoal = withContext(Dispatchers.IO) {
        database.withTransaction {
            val moves = goalDao.getMovesForGoal(goal.id)
            goalDao.deleteMovesForGoal(goal.id)
            goalDao.deleteGoal(goal.id)
            DeletedGoal(goal, moves)
        }
    }

    /** Undoes [deleteGoal]. If the kid has spent the money since, the goal comes back with less. */
    suspend fun restoreGoal(deleted: DeletedGoal) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                goalDao.upsertGoal(deleted.goal)
                goalDao.insertMoves(deleted.moves)
                rebalance(deleted.goal.kidName, preferredGoalId = deleted.goal.id)
            }
        }
    }

    /**
     * Moves money between two of a kid's goals, or between a goal and pocket
     * money (null). Returns false, changing nothing, if there isn't that much to move.
     */
    suspend fun moveMoney(kidName: String, fromGoalId: Long?, toGoalId: Long?, amountCents: Long): Boolean =
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val goals = goalDao.getGoalsForKidOnce(kidName).filter { it.completedAt == null }
                val goalIds = goals.map { it.id }.toSet()
                if (amountCents <= 0L || fromGoalId == toGoalId ||
                    (fromGoalId != null && fromGoalId !in goalIds) ||
                    (toGoalId != null && toGoalId !in goalIds)
                ) {
                    return@withTransaction false
                }
                val saved = GoalMath.savedByGoal(goalDao.getMovesForKidOnce(kidName))
                val available = if (fromGoalId == null) {
                    transactionDao.getBalanceForKidOnce(kidName) - GoalMath.savedCents(goals, saved)
                } else {
                    saved[fromGoalId] ?: 0L
                }
                if (amountCents > available) return@withTransaction false
                val now = System.currentTimeMillis()
                fun move(goalId: Long, cents: Long) = GoalMove(
                    kidName = kidName,
                    goalId = goalId,
                    amountCents = cents,
                    reason = GoalMoveReason.MANUAL,
                    timestamp = now
                )
                goalDao.insertMoves(
                    listOfNotNull(fromGoalId?.let { move(it, -amountCents) }, toGoalId?.let { move(it, amountCents) })
                )
                true
            }
        }

    /**
     * Records buying [goal] as [expense], lets go of everything saved for it and
     * marks it bought. Pocket money pays anything the savings don't. Returns the
     * expense's id, or null if the goal no longer exists.
     */
    suspend fun buyGoal(goal: Goal, expense: Transaction): Long? = withContext(Dispatchers.IO) {
        database.withTransaction {
            val current = goalDao.getGoal(goal.id) ?: return@withTransaction null
            transactionDao.insertKid(KidProfile(name = expense.kidName))
            transactionDao.insertCategory(CategoryEntity(name = expense.category))
            val id = transactionDao.insert(expense)
            val saved = goalDao.getMovesForGoal(current.id).sumOf { it.amountCents }
            if (saved != 0L) {
                goalDao.insertMoves(
                    listOf(
                        GoalMove(
                            kidName = current.kidName,
                            goalId = current.id,
                            amountCents = -saved,
                            reason = GoalMoveReason.PURCHASE,
                            timestamp = expense.timestamp,
                            transactionId = id
                        )
                    )
                )
            }
            goalDao.upsertGoal(current.copy(completedAt = expense.timestamp))
            rebalance(current.kidName, transactionId = id)
            id
        }
    }

    /**
     * Adds whatever moves keep [kidName]'s goals in line with their balance (see
     * [GoalMath.rebalance]). Call inside a database transaction, after every
     * change to money. Returns the ids of the moves added.
     */
    private suspend fun rebalance(
        kidName: String,
        preferredGoalId: Long? = null,
        transactionId: Long? = null
    ): List<Long> {
        val fixes = GoalMath.rebalance(
            kidName = kidName,
            balanceCents = transactionDao.getBalanceForKidOnce(kidName),
            goals = goalDao.getGoalsForKidOnce(kidName),
            moves = goalDao.getMovesForKidOnce(kidName),
            preferredGoalId = preferredGoalId,
            transactionId = transactionId
        )
        return if (fixes.isEmpty()) emptyList() else goalDao.insertMoves(fixes)
    }

    /** Writes every kid, category and transaction. Closes [outputStream]. */
    suspend fun exportToJson(openStream: () -> OutputStream?): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            open(openStream).bufferedWriter(Charsets.UTF_8).use { writer ->
                val payload = database.withTransaction {
                    val transactions = transactionDao.getAllTransactions().first()
                    val categories = transactionDao.getAllCategoriesOnce()
                    BackupCodec.encode(
                        kids = transactionDao.getAllKidsOnce(),
                        categories = categories.map { it.name },
                        transactions = transactions,
                        categoryIcons = categories.mapNotNull { c -> c.icon?.let { c.name to it } }.toMap(),
                        goals = goalDao.getAllGoalsOnce(),
                        goalMoves = goalDao.getAllMovesOnce(),
                        recurring = recurringDao.getAllRecurringOnce()
                    ) to transactions.size
                }
                writer.write(payload.first)
                payload.second
            }
        }
    }

    /** Writes every entry as a spreadsheet to the stream [openStream] gives, and closes it. */
    suspend fun exportToCsv(openStream: () -> OutputStream?): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val transactions = transactionDao.getAllTransactions().first()
            open(openStream).bufferedWriter(Charsets.UTF_8).use { it.write(CsvExport.encode(transactions)) }
            transactions.size
        }
    }

    /**
     * Restores a backup. The file is fully validated before the database is
     * touched, and the replace runs in one database transaction, so a failure
     * leaves existing data exactly as it was. Closes the stream [openStream] gives.
     */
    suspend fun importFromJson(
        openStream: () -> InputStream?,
        replaceExisting: Boolean = false
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val rawJson = open(openStream).use { readAtMost(it, MAX_BACKUP_BYTES) }.toString(Charsets.UTF_8)
            val backup = BackupCodec.decode(rawJson)
            database.withTransaction {
                if (replaceExisting) {
                    // Everything goes, so kids and categories missing from the backup don't linger.
                    transactionDao.deleteAll()
                    transactionDao.deleteAllKids()
                    transactionDao.deleteAllCategories()
                    goalDao.deleteAllMoves()
                    goalDao.deleteAllGoals()
                    recurringDao.deleteAllRecurring()
                }
                backup.kids.forEach { transactionDao.insertKid(it) }
                backup.categories.forEach { name ->
                    transactionDao.insertCategory(CategoryEntity(name = name, icon = backup.categoryIcons[name.lowercase()]))
                }
                // Goals, repeating entries and entries get new ids here, so links are pointed at those.
                val goalIds = backup.goals.mapValues { (_, goal) -> goalDao.upsertGoal(goal) }
                val recurringIds = backup.recurring.mapValues { (_, entry) ->
                    recurringDao.upsertRecurring(entry.copy(goalId = entry.goalId?.let(goalIds::get)))
                }
                val entryIds = mutableMapOf<Long, Long>()
                backup.transactions.forEachIndexed { index, transaction ->
                    val recurringId = backup.transactionRecurringRefs.getOrNull(index)?.let(recurringIds::get)
                    val id = transactionDao.insert(transaction.copy(recurringId = recurringId))
                    backup.transactionRefs.getOrNull(index)?.let { ref -> entryIds[ref] = id }
                }
                goalDao.insertMoves(
                    backup.goalMoves.map { move ->
                        GoalMove(
                            kidName = backup.goals.getValue(move.goalRef).kidName,
                            goalId = goalIds.getValue(move.goalRef),
                            amountCents = move.amountCents,
                            reason = move.reason,
                            timestamp = move.timestamp,
                            transactionId = move.transactionRef?.let(entryIds::get)
                        )
                    }
                )
                // A hand-edited or partial file could leave goals holding more than the kid has.
                backup.kids.forEach { rebalance(it.name) }
            }
            backup.transactions.size
        }
    }

    companion object {
        /** Real backups are a few hundred KB at most; this stops a wrong pick from exhausting memory. */
        const val MAX_BACKUP_BYTES = 10 * 1024 * 1024

        /**
         * Opens a picked file here, on the IO thread and inside the caller's runCatching,
         * so a provider that fails (Drive offline, file gone) reports an error, not a crash.
         */
        private fun <T> open(openStream: () -> T?): T =
            openStream() ?: throw IOException("Couldn't open the file.")

        private fun readAtMost(input: InputStream, limit: Int): ByteArray {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                require(out.size() <= limit) { "The file is too large to be a backup." }
            }
            return out.toByteArray()
        }
    }
}
