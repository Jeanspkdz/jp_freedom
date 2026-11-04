package com.jp.freedom.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.jp.freedom.domain.AccessProfile
import com.jp.freedom.domain.BlockValidation
import com.jp.freedom.domain.CompletionStatus
import com.jp.freedom.domain.DayPlanPolicy
import com.jp.freedom.domain.FocusSettings
import com.jp.freedom.domain.PlannedBlock
import java.time.LocalDate
import java.time.LocalTime

class FocusRepository(context: Context) {
    private val database = FocusDatabase(context.applicationContext)

    fun blocksForDate(date: LocalDate): List<PlannedBlock> =
        queryBlocks(database.readableDatabase, "date = ?", arrayOf(date.toString()))

    fun allBlocks(): List<PlannedBlock> =
        queryBlocks(database.readableDatabase, null, null)

    fun settings(): FocusSettings {
        val db = database.readableDatabase
        return db.query(
            TABLE_SETTINGS,
            arrayOf(COLUMN_REQUIRED_COUNT, COLUMN_CUTOFF_MINUTE),
            "$COLUMN_SETTINGS_ID = 1",
            null,
            null,
            null,
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use FocusSettings()
            runCatching {
                FocusSettings(
                requiredBlockCount = cursor.getInt(0).coerceAtLeast(1),
                eveningCutoff = LocalTime.of(cursor.getInt(1) / 60, cursor.getInt(1) % 60),
                )
            }.getOrDefault(FocusSettings())
        }
    }

    fun saveSettings(settings: FocusSettings) {
        val values = ContentValues().apply {
            put(COLUMN_SETTINGS_ID, 1)
            put(COLUMN_REQUIRED_COUNT, settings.requiredBlockCount)
            put(COLUMN_CUTOFF_MINUTE, settings.eveningCutoff.toMinuteOfDay())
        }
        database.writableDatabase.insertWithOnConflict(
            TABLE_SETTINGS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun saveBlock(block: PlannedBlock): BlockValidation {
        val db = database.writableDatabase
        var validation: BlockValidation = BlockValidation.Valid
        db.beginTransaction()
        try {
            val otherBlocks = queryBlocks(db, "date = ? AND id != ?", arrayOf(block.date.toString(), block.id))
            validation = DayPlanPolicy.validateBlocks(otherBlocks + block)
            if (validation == BlockValidation.Valid) {
                db.insertWithOnConflict(
                    TABLE_BLOCKS,
                    null,
                    block.toContentValues(),
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
                db.setTransactionSuccessful()
            }
        } finally {
            db.endTransaction()
        }
        return validation
    }

    fun deleteBlock(id: String) {
        database.writableDatabase.delete(TABLE_BLOCKS, "$COLUMN_ID = ?", arrayOf(id))
    }

    fun updateCompletionStatus(id: String, status: CompletionStatus): Boolean {
        val values = ContentValues().apply { put(COLUMN_COMPLETION_STATUS, status.name) }
        return database.writableDatabase.update(TABLE_BLOCKS, values, "$COLUMN_ID = ?", arrayOf(id)) == 1
    }

    private fun queryBlocks(
        db: SQLiteDatabase,
        selection: String?,
        selectionArgs: Array<String>?,
    ): List<PlannedBlock> = db.query(
        TABLE_BLOCKS,
        BLOCK_COLUMNS,
        selection,
        selectionArgs,
        null,
        null,
        "$COLUMN_DATE ASC, $COLUMN_START_MINUTE ASC",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    PlannedBlock(
                        id = cursor.getString(0),
                        date = LocalDate.parse(cursor.getString(1)),
                        name = cursor.getString(2),
                        startTime = cursor.getInt(3).toLocalTime(),
                        endTime = cursor.getInt(4).toLocalTime(),
                        accessProfile = AccessProfile.valueOf(cursor.getString(5)),
                        completionStatus = CompletionStatus.valueOf(cursor.getString(6)),
                    ),
                )
            }
        }
    }

    private fun PlannedBlock.toContentValues() = ContentValues().apply {
        put(COLUMN_ID, id)
        put(COLUMN_DATE, date.toString())
        put(COLUMN_NAME, name.trim())
        put(COLUMN_START_MINUTE, startTime.toMinuteOfDay())
        put(COLUMN_END_MINUTE, endTime.toMinuteOfDay())
        put(COLUMN_ACCESS_PROFILE, accessProfile.name)
        put(COLUMN_COMPLETION_STATUS, completionStatus.name)
    }

    private fun LocalTime.toMinuteOfDay() = hour * 60 + minute

    private fun Int.toLocalTime() = LocalTime.of(this / 60, this % 60)

    private class FocusDatabase(context: Context) : SQLiteOpenHelper(
        context,
        DATABASE_NAME,
        null,
        DATABASE_VERSION,
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE $TABLE_BLOCKS (
                    $COLUMN_ID TEXT PRIMARY KEY NOT NULL,
                    $COLUMN_DATE TEXT NOT NULL,
                    $COLUMN_NAME TEXT NOT NULL,
                    $COLUMN_START_MINUTE INTEGER NOT NULL,
                    $COLUMN_END_MINUTE INTEGER NOT NULL,
                    $COLUMN_ACCESS_PROFILE TEXT NOT NULL,
                    $COLUMN_COMPLETION_STATUS TEXT NOT NULL
                )""".trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX index_planned_blocks_date_start ON $TABLE_BLOCKS($COLUMN_DATE, $COLUMN_START_MINUTE)",
            )
            db.execSQL(
                """CREATE TABLE $TABLE_SETTINGS (
                    $COLUMN_SETTINGS_ID INTEGER PRIMARY KEY NOT NULL,
                    $COLUMN_REQUIRED_COUNT INTEGER NOT NULL,
                    $COLUMN_CUTOFF_MINUTE INTEGER NOT NULL
                )""".trimIndent(),
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

        companion object {
            private const val DATABASE_NAME = "jp-freedom.db"
            private const val DATABASE_VERSION = 1
        }
    }

    private companion object {
        const val TABLE_BLOCKS = "planned_blocks"
        const val TABLE_SETTINGS = "focus_settings"
        const val COLUMN_ID = "id"
        const val COLUMN_DATE = "date"
        const val COLUMN_NAME = "name"
        const val COLUMN_START_MINUTE = "start_minute"
        const val COLUMN_END_MINUTE = "end_minute"
        const val COLUMN_ACCESS_PROFILE = "access_profile"
        const val COLUMN_COMPLETION_STATUS = "completion_status"
        const val COLUMN_SETTINGS_ID = "id"
        const val COLUMN_REQUIRED_COUNT = "required_count"
        const val COLUMN_CUTOFF_MINUTE = "cutoff_minute"
        val BLOCK_COLUMNS = arrayOf(
            COLUMN_ID,
            COLUMN_DATE,
            COLUMN_NAME,
            COLUMN_START_MINUTE,
            COLUMN_END_MINUTE,
            COLUMN_ACCESS_PROFILE,
            COLUMN_COMPLETION_STATUS,
        )
    }
}
