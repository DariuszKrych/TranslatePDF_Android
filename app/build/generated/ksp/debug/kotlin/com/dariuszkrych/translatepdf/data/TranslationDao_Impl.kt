package com.dariuszkrych.translatepdf.`data`

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class TranslationDao_Impl(
  __db: RoomDatabase,
) : TranslationDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfTranslationRecord: EntityInsertAdapter<TranslationRecord>

  private val __deleteAdapterOfTranslationRecord: EntityDeleteOrUpdateAdapter<TranslationRecord>
  init {
    this.__db = __db
    this.__insertAdapterOfTranslationRecord = object : EntityInsertAdapter<TranslationRecord>() {
      protected override fun createQuery(): String =
          "INSERT OR ABORT INTO `translations` (`id`,`fileName`,`sourceLang`,`targetLang`,`timestamp`,`outputPath`) VALUES (nullif(?, 0),?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: TranslationRecord) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.fileName)
        statement.bindText(3, entity.sourceLang)
        statement.bindText(4, entity.targetLang)
        statement.bindLong(5, entity.timestamp)
        statement.bindText(6, entity.outputPath)
      }
    }
    this.__deleteAdapterOfTranslationRecord = object :
        EntityDeleteOrUpdateAdapter<TranslationRecord>() {
      protected override fun createQuery(): String = "DELETE FROM `translations` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: TranslationRecord) {
        statement.bindLong(1, entity.id)
      }
    }
  }

  public override suspend fun insert(record: TranslationRecord): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfTranslationRecord.insert(_connection, record)
  }

  public override suspend fun delete(record: TranslationRecord): Unit = performSuspending(__db,
      false, true) { _connection ->
    __deleteAdapterOfTranslationRecord.handle(_connection, record)
  }

  public override suspend fun getAll(): List<TranslationRecord> {
    val _sql: String = "SELECT * FROM translations ORDER BY timestamp DESC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfFileName: Int = getColumnIndexOrThrow(_stmt, "fileName")
        val _columnIndexOfSourceLang: Int = getColumnIndexOrThrow(_stmt, "sourceLang")
        val _columnIndexOfTargetLang: Int = getColumnIndexOrThrow(_stmt, "targetLang")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _columnIndexOfOutputPath: Int = getColumnIndexOrThrow(_stmt, "outputPath")
        val _result: MutableList<TranslationRecord> = mutableListOf()
        while (_stmt.step()) {
          val _item: TranslationRecord
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpFileName: String
          _tmpFileName = _stmt.getText(_columnIndexOfFileName)
          val _tmpSourceLang: String
          _tmpSourceLang = _stmt.getText(_columnIndexOfSourceLang)
          val _tmpTargetLang: String
          _tmpTargetLang = _stmt.getText(_columnIndexOfTargetLang)
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          val _tmpOutputPath: String
          _tmpOutputPath = _stmt.getText(_columnIndexOfOutputPath)
          _item =
              TranslationRecord(_tmpId,_tmpFileName,_tmpSourceLang,_tmpTargetLang,_tmpTimestamp,_tmpOutputPath)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun search(query: String): List<TranslationRecord> {
    val _sql: String =
        "SELECT * FROM translations WHERE fileName LIKE '%' || ? || '%' ORDER BY timestamp DESC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, query)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfFileName: Int = getColumnIndexOrThrow(_stmt, "fileName")
        val _columnIndexOfSourceLang: Int = getColumnIndexOrThrow(_stmt, "sourceLang")
        val _columnIndexOfTargetLang: Int = getColumnIndexOrThrow(_stmt, "targetLang")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _columnIndexOfOutputPath: Int = getColumnIndexOrThrow(_stmt, "outputPath")
        val _result: MutableList<TranslationRecord> = mutableListOf()
        while (_stmt.step()) {
          val _item: TranslationRecord
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpFileName: String
          _tmpFileName = _stmt.getText(_columnIndexOfFileName)
          val _tmpSourceLang: String
          _tmpSourceLang = _stmt.getText(_columnIndexOfSourceLang)
          val _tmpTargetLang: String
          _tmpTargetLang = _stmt.getText(_columnIndexOfTargetLang)
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          val _tmpOutputPath: String
          _tmpOutputPath = _stmt.getText(_columnIndexOfOutputPath)
          _item =
              TranslationRecord(_tmpId,_tmpFileName,_tmpSourceLang,_tmpTargetLang,_tmpTimestamp,_tmpOutputPath)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
