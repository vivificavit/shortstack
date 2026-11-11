package com.vivificavit.shortstack.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vivificavit.shortstack.data.model.CategoryEntity
import com.vivificavit.shortstack.data.model.KidProfile
import com.vivificavit.shortstack.data.model.Transaction
import com.vivificavit.shortstack.data.model.TransactionType
import kotlinx.coroutines.flow.Flow

data class CategorySum(
    @ColumnInfo(name = "category")
    val category: String,
    @ColumnInfo(name = "totalCents")
    val totalCents: Long
)

/** An entry type with a category used for it. */
data class TypeCategory(
    val type: TransactionType,
    val category: String
)

/** One entry's category and note. */
data class CategoryNote(
    val category: String,
    val title: String
)

@Dao
interface TransactionDao {

    /** Kids with at least one entry. */
    @Query("SELECT DISTINCT kidName FROM transactions")
    fun getKidsWithTransactions(): Flow<List<String>>

    /** Every category an entry uses. */
    @Query("SELECT DISTINCT category FROM transactions")
    fun getTransactionCategories(): Flow<List<String>>

    /** The category of the newest entry of each type, across all kids: at most one row per type. */
    @Query(
        """
        SELECT type, category FROM transactions AS newest
        WHERE id = (
            SELECT id FROM transactions
            WHERE type = newest.type
            ORDER BY timestamp DESC, id DESC
            LIMIT 1
        )
        """
    )
    fun getLastCategoryByType(): Flow<List<TypeCategory>>

    /** Every entry's category and note, newest first. */
    @Query("SELECT category, title FROM transactions ORDER BY timestamp DESC, id DESC")
    fun getCategoryNotes(): Flow<List<CategoryNote>>

    @Query("SELECT name FROM kids ORDER BY createdAt ASC, id ASC")
    fun getAllKidNames(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertKid(kid: KidProfile): Long

    @Query("SELECT * FROM kids ORDER BY createdAt ASC, id ASC")
    suspend fun getAllKidsOnce(): List<KidProfile>

    @Query("DELETE FROM kids WHERE name = :name")
    suspend fun deleteKid(name: String): Int

    @Query("DELETE FROM transactions WHERE kidName = :kidName")
    suspend fun deleteTransactionsForKid(kidName: String): Int

    @Query("SELECT name FROM categories ORDER BY id ASC")
    fun getAllCategoryNames(): Flow<List<String>>

    @Query("SELECT name FROM categories ORDER BY id ASC")
    suspend fun getAllCategoryNamesOnce(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Query("SELECT * FROM categories ORDER BY id ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY id ASC")
    suspend fun getAllCategoriesOnce(): List<CategoryEntity>

    @Query("UPDATE categories SET icon = :icon WHERE LOWER(name) = LOWER(:name)")
    suspend fun setCategoryIcon(name: String, icon: String?)

    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE LOWER(category) = LOWER(:name)
        """
    )
    suspend fun countTransactionsUsingCategory(name: String): Int

    @Query(
        """
        DELETE FROM categories
        WHERE LOWER(name) = LOWER(:name)
          AND NOT EXISTS (
              SELECT 1 FROM transactions
              WHERE LOWER(category) = LOWER(:name)
          )
          AND NOT EXISTS (
              SELECT 1 FROM recurring_entries
              WHERE LOWER(category) = LOWER(:name)
          )
        """
    )
    suspend fun deleteUnusedCategory(name: String): Int

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC, id DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE kidName = :kidName ORDER BY timestamp DESC, id DESC")
    fun getTransactionsForKid(kidName: String): Flow<List<Transaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: Transaction): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<Transaction>)

    @Delete
    suspend fun delete(transaction: Transaction)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("DELETE FROM kids")
    suspend fun deleteAllKids()

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()

    @Query(
        """
        SELECT 
            COALESCE(SUM(CASE WHEN type = 'CREDIT' THEN amountCents ELSE 0 END), 0) -
            COALESCE(SUM(CASE WHEN type = 'DEBIT' THEN amountCents ELSE 0 END), 0)
        FROM transactions
        """
    )
    fun getRunningBalance(): Flow<Long?>

    @Query(
        """
        SELECT 
            COALESCE(SUM(CASE WHEN type = 'CREDIT' THEN amountCents ELSE 0 END), 0) -
            COALESCE(SUM(CASE WHEN type = 'DEBIT' THEN amountCents ELSE 0 END), 0)
        FROM transactions
        WHERE kidName = :kidName
        """
    )
    fun getRunningBalanceForKid(kidName: String): Flow<Long?>

    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN type = 'CREDIT' THEN amountCents ELSE -amountCents END), 0)
        FROM transactions
        WHERE kidName = :kidName
        """
    )
    suspend fun getBalanceForKidOnce(kidName: String): Long

    @Query("SELECT * FROM transactions WHERE kidName = :kidName ORDER BY timestamp DESC, id DESC")
    suspend fun getTransactionsForKidOnce(kidName: String): List<Transaction>

    @Query(
        """
        SELECT category, SUM(amountCents) AS totalCents
        FROM transactions
        WHERE type = 'DEBIT'
        GROUP BY category
        HAVING totalCents > 0
        ORDER BY totalCents DESC
        """
    )
    fun getDebitTotalsByCategory(): Flow<List<CategorySum>>

    @Query(
        """
        SELECT category, SUM(amountCents) AS totalCents
        FROM transactions
        WHERE type = 'CREDIT'
          AND kidName = :kidName
          AND timestamp >= :startTimestamp
          AND timestamp <= :endTimestamp
        GROUP BY category
        HAVING totalCents > 0
        ORDER BY totalCents DESC
        """
    )
    fun getCreditTotalsByCategoryForKidInRange(
        kidName: String,
        startTimestamp: Long,
        endTimestamp: Long
    ): Flow<List<CategorySum>>

    @Query(
        """
        SELECT category, SUM(amountCents) AS totalCents
        FROM transactions
        WHERE type = 'DEBIT'
          AND kidName = :kidName
          AND timestamp >= :startTimestamp
          AND timestamp <= :endTimestamp
        GROUP BY category
        HAVING totalCents > 0
        ORDER BY totalCents DESC
        """
    )
    fun getDebitTotalsByCategoryForKidInRange(
        kidName: String,
        startTimestamp: Long,
        endTimestamp: Long
    ): Flow<List<CategorySum>>
}
