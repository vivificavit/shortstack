package com.vivificavit.shortstack.ui.viewmodel

import com.vivificavit.shortstack.data.model.GoalMove
import com.vivificavit.shortstack.data.model.Transaction
import com.vivificavit.shortstack.repository.KidMoneySnapshot

/** What the Ledger tab shows for one kid. */
data class LedgerState(
    /** Everything the kid has: income minus spending, including money set aside for goals. */
    val balanceCents: Long,
    val transactions: List<Transaction>,
    /** The balance minus what's set aside for goals still being saved for. */
    val pocketCents: Long = balanceCents,
    val savedCents: Long = 0L,
    val activeGoalCount: Int = 0,
    /** The goal moves each entry made (its split, purchase or cover), by entry id. */
    val movesByEntry: Map<Long, List<GoalMove>> = emptyMap()
)

/** How many of a kid's newest entries their tile shows. */
const val KidTileEntryCount = 3

/** One kid's tile in the all-kids overview: their headline amount and newest entries. */
data class KidTileState(
    val kidName: String,
    /** The kid's place in the kid list, which picks their color. */
    val colorIndex: Int,
    /** Pocket money when [hasGoals], otherwise the balance, as on the kid's own ledger. */
    val headlineCents: Long,
    val hasGoals: Boolean,
    /** The newest entries, at most [KidTileEntryCount]. */
    val recent: List<Transaction>,
    /** How many entries the kid has in all. */
    val entryCount: Int
)

/** Tiles in kid-list order, with the same numbers each kid's own ledger shows. */
internal fun buildKidTiles(money: List<Pair<String, KidMoneySnapshot>>): List<KidTileState> =
    money.mapIndexed { index, (kidName, snapshot) ->
        val ledger = buildMoneyStates(snapshot.transactions, snapshot.goals, snapshot.moves).first
        val hasGoals = ledger.activeGoalCount > 0
        KidTileState(
            kidName = kidName,
            colorIndex = index,
            headlineCents = if (hasGoals) ledger.pocketCents else ledger.balanceCents,
            hasGoals = hasGoals,
            recent = ledger.transactions.take(KidTileEntryCount),
            entryCount = ledger.transactions.size
        )
    }
