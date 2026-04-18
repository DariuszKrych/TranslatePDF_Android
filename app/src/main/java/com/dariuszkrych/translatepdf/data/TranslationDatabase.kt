package com.dariuszkrych.translatepdf.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * A single completed-translation row stored in the local SQLite database.
 * Room generates the "translations" table from this data class at compile time.
 */
@Entity(tableName = "translations")
data class TranslationRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, // Auto-incrementing row id.
    val fileName: String,       // Original PDF name shown to the user (e.g. "notes.pdf").
    val sourceLang: String,     // BCP-47 code the PDF was translated FROM (e.g. "en").
    val targetLang: String,     // BCP-47 code the PDF was translated TO (e.g. "es").
    val timestamp: Long,        // When the translation finished — epoch milliseconds.
    val outputPath: String      // Absolute path to the generated PDF in app-private storage.
)

/**
 * Data-access interface. Room generates an implementation with the actual SQL work
 * offloaded to a background-safe coroutine dispatcher (all methods are `suspend`).
 */
@Dao
interface TranslationDao {
    // All records, newest first — used to populate the History screen.
    @Query("SELECT * FROM translations ORDER BY timestamp DESC")
    suspend fun getAll(): List<TranslationRecord>

    // Case-insensitive fuzzy match on file name; powers the History search bar.
    @Query("SELECT * FROM translations WHERE fileName LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    suspend fun search(query: String): List<TranslationRecord>

    // Called once per successful translation to persist the record.
    @Insert
    suspend fun insert(record: TranslationRecord)

    // Removes a history entry. The on-disk PDF is deleted separately by the caller.
    @Delete
    suspend fun delete(record: TranslationRecord)
}

/**
 * Room database wrapper. Declared `abstract` — Room generates the concrete subclass
 * that actually opens the underlying SQLite file ("translations.db").
 */
@Database(entities = [TranslationRecord::class], version = 1, exportSchema = false)
abstract class TranslationDatabase : RoomDatabase() {
    // Expose the DAO so the rest of the app can query/insert without touching Room internals.
    abstract fun translationDao(): TranslationDao

    companion object {
        // Double-checked-locked singleton instance. `@Volatile` ensures other threads see
        // the assignment immediately — prevents two threads from each opening a DB.
        @Volatile
        private var instance: TranslationDatabase? = null

        /**
         * Returns the process-wide database instance, creating it on first use.
         * The app-context avoids leaking any Activity/Fragment context into Room.
         */
        fun get(context: Context): TranslationDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TranslationDatabase::class.java,
                    "translations.db"   // Physical SQLite file stored in app-private data dir.
                ).build().also { instance = it }
            }
        }
    }
}
