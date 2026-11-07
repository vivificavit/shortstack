package com.vivificavit.shortstack.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vivificavit.shortstack.data.model.RecurringEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring_entries WHERE kidName = :kidName ORDER BY createdAt ASC, id ASC")
    fun getRecurringForKid(kidName: String): Flow<List<RecurringEntry>>

    @Query("SELECT * FROM recurring_entries ORDER BY createdAt ASC, id ASC")
    suspend fun getAllRecurringOnce(): List<RecurringEntry>

    /** Kids with at least one repeating entry. */
    @Query("SELECT DISTINCT kidName FROM recurring_entries")
    fun getKidsWithRecurring(): Flow<List<String>>

    /** Categories a repeating entry will use, so they count as in use. */
    @Query("SELECT DISTINCT category FROM recurring_entries")
    fun getRecurringCategories(): Flow<List<String>>

    @Query("SELECT * FROM recurring_entries WHERE id = :id")
    suspend fun getRecurring(id: Long): RecurringEntry?

    /** Adds a repeating entry, or replaces the one with the same id. Returns its id. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecurring(entry: RecurringEntry): Long

    @Query("DELETE FROM recurring_entries WHERE id = :id")
    suspend fun deleteRecurring(id: Long)

    @Query("DELETE FROM recurring_entries WHERE kidName = :kidName")
    suspend fun deleteRecurringForKid(kidName: String)

    @Query("DELETE FROM recurring_entries")
    suspend fun deleteAllRecurring()
}
