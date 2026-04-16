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

@Entity(tableName = "translations")
data class TranslationRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val sourceLang: String,
    val targetLang: String,
    val timestamp: Long,
    val outputPath: String
)

@Dao
interface TranslationDao {
    @Query("SELECT * FROM translations ORDER BY timestamp DESC")
    suspend fun getAll(): List<TranslationRecord>

    @Query("SELECT * FROM translations WHERE fileName LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    suspend fun search(query: String): List<TranslationRecord>

    @Insert
    suspend fun insert(record: TranslationRecord)

    @Delete
    suspend fun delete(record: TranslationRecord)
}

@Database(entities = [TranslationRecord::class], version = 1, exportSchema = false)
abstract class TranslationDatabase : RoomDatabase() {
    abstract fun translationDao(): TranslationDao

    companion object {
        @Volatile
        private var instance: TranslationDatabase? = null

        fun get(context: Context): TranslationDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TranslationDatabase::class.java,
                    "translations.db"
                ).build().also { instance = it }
            }
        }
    }
}
