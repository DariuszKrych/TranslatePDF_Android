package com.dariuszkrych.translatepdf.`data`

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class TranslationDatabase_Impl : TranslationDatabase() {
  private val _translationDao: Lazy<TranslationDao> = lazy {
    TranslationDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(1,
        "3c84e189c4b847c16b09c630897f6599", "640736d90cdff88e4c8258b91fe73090") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `translations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fileName` TEXT NOT NULL, `sourceLang` TEXT NOT NULL, `targetLang` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `outputPath` TEXT NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '3c84e189c4b847c16b09c630897f6599')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `translations`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection):
          RoomOpenDelegate.ValidationResult {
        val _columnsTranslations: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsTranslations.put("id", TableInfo.Column("id", "INTEGER", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsTranslations.put("fileName", TableInfo.Column("fileName", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsTranslations.put("sourceLang", TableInfo.Column("sourceLang", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsTranslations.put("targetLang", TableInfo.Column("targetLang", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsTranslations.put("timestamp", TableInfo.Column("timestamp", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTranslations.put("outputPath", TableInfo.Column("outputPath", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysTranslations: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesTranslations: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoTranslations: TableInfo = TableInfo("translations", _columnsTranslations,
            _foreignKeysTranslations, _indicesTranslations)
        val _existingTranslations: TableInfo = read(connection, "translations")
        if (!_infoTranslations.equals(_existingTranslations)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |translations(com.dariuszkrych.translatepdf.data.TranslationRecord).
              | Expected:
              |""".trimMargin() + _infoTranslations + """
              |
              | Found:
              |""".trimMargin() + _existingTranslations)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "translations")
  }

  public override fun clearAllTables() {
    super.performClear(false, "translations")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(TranslationDao::class, TranslationDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override
      fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>):
      List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun translationDao(): TranslationDao = _translationDao.value
}
