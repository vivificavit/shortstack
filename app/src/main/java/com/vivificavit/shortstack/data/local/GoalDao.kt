package com.vivificavit.shortstack.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vivificavit.shortstack.data.model.Goal
import com.vivificavit.shortstack.data.model.GoalMove
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {

    @Query("SELECT * FROM goals WHERE kidName = :kidName ORDER BY createdAt ASC, id ASC")
    fun getGoalsForKid(kidName: String): Flow<List<Goal>>

    @Query("SELECT * FROM goals WHERE kidName = :kidName ORDER BY createdAt ASC, id ASC")
    suspend fun getGoalsForKidOnce(kidName: String): List<Goal>

    @Query("SELECT * FROM goals ORDER BY createdAt ASC, id ASC")
    suspend fun getAllGoalsOnce(): List<Goal>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun getGoal(id: Long): Goal?

    @Query("SELECT DISTINCT kidName FROM goals")
    fun getKidsWithGoals(): Flow<List<String>>

    /** Inserts a new goal, or replaces the one with the same id. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoal(goal: Goal): Long

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteGoal(id: Long)

    @Query("DELETE FROM goals WHERE kidName = :kidName")
    suspend fun deleteGoalsForKid(kidName: String)

    @Query("DELETE FROM goals")
    suspend fun deleteAllGoals()

    @Query("UPDATE goals SET kidName = :newName WHERE kidName = :oldName")
    suspend fun renameKidInGoals(oldName: String, newName: String)

    @Query("UPDATE goal_moves SET kidName = :newName WHERE kidName = :oldName")
    suspend fun renameKidInMoves(oldName: String, newName: String)

    @Query("SELECT * FROM goal_moves WHERE kidName = :kidName ORDER BY timestamp DESC, id DESC")
    fun getMovesForKid(kidName: String): Flow<List<GoalMove>>

    @Query("SELECT * FROM goal_moves WHERE kidName = :kidName ORDER BY timestamp DESC, id DESC")
    suspend fun getMovesForKidOnce(kidName: String): List<GoalMove>

    @Query("SELECT * FROM goal_moves ORDER BY timestamp ASC, id ASC")
    suspend fun getAllMovesOnce(): List<GoalMove>

    @Query("SELECT * FROM goal_moves WHERE goalId = :goalId")
    suspend fun getMovesForGoal(goalId: Long): List<GoalMove>

    @Query("SELECT * FROM goal_moves WHERE transactionId = :transactionId")
    suspend fun getMovesForTransaction(transactionId: Long): List<GoalMove>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMoves(moves: List<GoalMove>): List<Long>

    @Query("DELETE FROM goal_moves WHERE id IN (:ids)")
    suspend fun deleteMoves(ids: List<Long>)

    @Query("DELETE FROM goal_moves WHERE goalId = :goalId")
    suspend fun deleteMovesForGoal(goalId: Long)

    @Query("DELETE FROM goal_moves WHERE kidName = :kidName")
    suspend fun deleteMovesForKid(kidName: String)

    @Query("DELETE FROM goal_moves")
    suspend fun deleteAllMoves()
}
