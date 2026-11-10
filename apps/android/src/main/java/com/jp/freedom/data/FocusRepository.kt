package com.jp.freedom.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.jp.freedom.domain.AccessProfile
import com.jp.freedom.domain.BlockValidation
import com.jp.freedom.domain.BlocklistValidation
import com.jp.freedom.domain.CompletionStatus
import com.jp.freedom.domain.DEFAULT_DESKTOP_BLOCKED_DOMAINS
import com.jp.freedom.domain.DayPlanPolicy
import com.jp.freedom.domain.DesktopSiteBlocklist
import com.jp.freedom.domain.FocusSettings
import com.jp.freedom.domain.PlannedBlock
import com.jp.freedom.domain.SyncSnapshot
import com.jp.freedom.sync.SyncScheduler
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class PendingSyncState(
    val revision: Long,
    val generation: Long,
    val pending: Boolean,
    val status: SyncStatus,
    val message: String?,
    val pendingPaths: Set<String>,
)

enum class SyncStatus {
    NOT_CONFIGURED,
    PENDING,
    SYNCING,
    UP_TO_DATE,
    CONFLICT_RESOLVED,
    ERROR,
}

data class LocalSyncBundle(val snapshot: SyncSnapshot, val state: PendingSyncState)

/**
 * Plan data and pending sync paths share SQLite transactions so a merge cannot
 * persist a server revision for a snapshot that missed a concurrent local edit.
 */
class FocusRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database = FocusDatabase(appContext)

    fun blocksForDate(date: LocalDate): List<PlannedBlock> =
        queryBlocks(database.readableDatabase, "date = ?", arrayOf(date.toString()))

    fun allBlocks(): List<PlannedBlock> = queryBlocks(database.readableDatabase, null, null)

    fun settings(): FocusSettings = readSettings(database.readableDatabase)

    fun desktopBlockedDomains(): List<String> = readDomains(database.readableDatabase)

    fun saveSettings(settings: FocusSettings) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            val previous = readSettings(db)
            val values = ContentValues().apply {
                put(COLUMN_SETTINGS_ID, 1)
                put(COLUMN_REQUIRED_COUNT, settings.requiredBlockCount)
                put(COLUMN_CUTOFF_MINUTE, settings.eveningCutoff.toMinuteOfDay())
            }
            db.insertWithOnConflict(TABLE_SETTINGS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            val paths = buildList {
                if (previous.requiredBlockCount != settings.requiredBlockCount) add("settings.requiredBlockCount")
                if (previous.eveningCutoff != settings.eveningCutoff) add("settings.eveningCutoff")
            }
            markDirty(db, paths)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        scheduleSync()
    }

    fun saveBlock(block: PlannedBlock): BlockValidation {
        val db = database.writableDatabase
        var validation: BlockValidation = BlockValidation.Valid
        var changed = false
        db.beginTransaction()
        try {
            val previous = queryBlocks(db, "$COLUMN_ID = ?", arrayOf(block.id)).firstOrNull()
            val otherBlocks = queryBlocks(db, "date = ? AND id != ?", arrayOf(block.date.toString(), block.id))
            validation = DayPlanPolicy.validateBlocks(otherBlocks + block)
            if (validation == BlockValidation.Valid) {
                db.insertWithOnConflict(TABLE_BLOCKS, null, block.toContentValues(), SQLiteDatabase.CONFLICT_REPLACE)
                val paths = changedBlockPaths(previous, block)
                if (previous == null || paths.isNotEmpty()) {
                    db.delete(TABLE_SYNC_CHANGES, "$COLUMN_PATH = ?", arrayOf("blocks.${block.id}.deleted"))
                    markDirty(db, paths)
                    changed = paths.isNotEmpty() || previous == null
                }
                db.setTransactionSuccessful()
            }
        } finally {
            db.endTransaction()
        }
        if (changed) scheduleSync()
        return validation
    }

    fun deleteBlock(id: String) {
        val db = database.writableDatabase
        var changed = false
        db.beginTransaction()
        try {
            changed = db.delete(TABLE_BLOCKS, "$COLUMN_ID = ?", arrayOf(id)) == 1
            if (changed) {
                val paths = db.query(TABLE_SYNC_CHANGES, arrayOf(COLUMN_PATH), null, null, null, null, null)
                    .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
                paths.filter { it.startsWith("blocks.$id.") }.forEach { path ->
                    db.delete(TABLE_SYNC_CHANGES, "$COLUMN_PATH = ?", arrayOf(path))
                }
                markDirty(db, listOf("blocks.$id.deleted"))
                db.setTransactionSuccessful()
            }
        } finally {
            db.endTransaction()
        }
        if (changed) scheduleSync()
    }

    fun updateCompletionStatus(id: String, status: CompletionStatus): Boolean {
        val db = database.writableDatabase
        var changed = false
        var exists = false
        db.beginTransaction()
        try {
            val current = queryBlocks(db, "$COLUMN_ID = ?", arrayOf(id)).firstOrNull()
            exists = current != null
            if (current != null) {
                changed = current.completionStatus != status
                if (changed) {
                    val values = ContentValues().apply { put(COLUMN_COMPLETION_STATUS, status.name) }
                    db.update(TABLE_BLOCKS, values, "$COLUMN_ID = ?", arrayOf(id))
                    markDirty(db, listOf("blocks.$id.completionStatus"))
                    db.setTransactionSuccessful()
                }
            }
        } finally {
            db.endTransaction()
        }
        if (changed) scheduleSync()
        return exists
    }

    fun saveDesktopBlockedDomains(input: List<String>): BlocklistValidation {
        val domains = input.map(DesktopSiteBlocklist::normalize)
        val validation = DesktopSiteBlocklist.validate(domains)
        if (validation != BlocklistValidation.Valid) return validation

        val db = database.writableDatabase
        var changed = false
        db.beginTransaction()
        try {
            val previous = readDomains(db).toSet()
            val updated = domains.toSet()
            val now = LocalDateTime.now()
            val activeBlock = queryBlocks(db, "$COLUMN_DATE = ?", arrayOf(now.toLocalDate().toString())).any { block ->
                block.completionStatus != CompletionStatus.FINISHED &&
                    !now.toLocalTime().isBefore(block.startTime) && now.toLocalTime().isBefore(block.endTime)
            }
            if (activeBlock && !updated.containsAll(previous)) return BlocklistValidation.ActiveBlockLocked
            val paths = ((previous - updated) + (updated - previous)).map { "desktopBlockedDomains.$it" }
            if (paths.isNotEmpty()) {
                db.delete(TABLE_DESKTOP_DOMAINS, null, null)
                domains.forEach { domain ->
                    db.insert(TABLE_DESKTOP_DOMAINS, null, ContentValues().apply { put(COLUMN_DOMAIN, domain) })
                }
                markDirty(db, paths)
                changed = true
                db.setTransactionSuccessful()
            }
        } finally {
            db.endTransaction()
        }
        if (changed) scheduleSync()
        return BlocklistValidation.Valid
    }

    fun syncBundle(): LocalSyncBundle {
        val db = database.readableDatabase
        return LocalSyncBundle(
            snapshot = readSnapshot(db),
            state = readSyncState(db),
        )
    }

    fun syncState(): PendingSyncState = readSyncState(database.readableDatabase)

    fun markSyncing() = updateSyncMetadata(status = SyncStatus.SYNCING, message = null)

    fun markSyncError(message: String) = updateSyncMetadata(status = SyncStatus.ERROR, message = message)

    fun markNotConfigured() = updateSyncMetadata(status = SyncStatus.NOT_CONFIGURED, message = null)

    fun resetSyncForEndpointChange() {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            markDirty(db, listOf("snapshot.replaceAll"))
            val values = ContentValues().apply {
                put(COLUMN_REVISION, -1)
                put(COLUMN_PENDING, 1)
                put(COLUMN_STATUS, SyncStatus.PENDING.name)
                putNull(COLUMN_MESSAGE)
            }
            db.update(TABLE_SYNC_STATE, values, "$COLUMN_SETTINGS_ID = 1", null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        scheduleSync()
    }

    fun requestSync() {
        val state = syncState()
        updateSyncMetadata(status = if (state.pending) SyncStatus.PENDING else SyncStatus.SYNCING, message = null)
        scheduleSync()
    }

    fun markSynced(revision: Long, sentGeneration: Long, conflictResolved: Boolean) {
        val db = database.writableDatabase
        var scheduleAgain = false
        db.beginTransaction()
        try {
            val current = readSyncState(db)
            val stillPending = current.generation != sentGeneration
            if (!stillPending) db.delete(TABLE_SYNC_CHANGES, null, null)
            val values = ContentValues().apply {
                put(COLUMN_REVISION, revision)
                put(COLUMN_PENDING, if (stillPending) 1 else 0)
                put(COLUMN_STATUS, when {
                    stillPending -> SyncStatus.PENDING.name
                    conflictResolved -> SyncStatus.CONFLICT_RESOLVED.name
                    else -> SyncStatus.UP_TO_DATE.name
                })
                if (stillPending) put(COLUMN_MESSAGE, "New changes are waiting to sync.") else putNull(COLUMN_MESSAGE)
            }
            db.update(TABLE_SYNC_STATE, values, "$COLUMN_SETTINGS_ID = 1", null)
            scheduleAgain = stillPending
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (scheduleAgain) scheduleSync()
    }

    fun setReconciledSnapshot(
        snapshot: SyncSnapshot,
        revision: Long,
        conflictResolved: Boolean,
        expectedGeneration: Long,
    ): Boolean {
        val db = database.writableDatabase
        var applied = false
        db.beginTransaction()
        try {
            if (readSyncState(db).generation != expectedGeneration) return false
            replaceSnapshot(db, snapshot)
            val values = ContentValues().apply {
                put(COLUMN_REVISION, revision)
                if (conflictResolved) put(COLUMN_STATUS, SyncStatus.CONFLICT_RESOLVED.name)
                else put(COLUMN_STATUS, SyncStatus.SYNCING.name)
                put(COLUMN_PENDING, if (readSyncState(db).pending) 1 else 0)
                putNull(COLUMN_MESSAGE)
            }
            db.update(TABLE_SYNC_STATE, values, "$COLUMN_SETTINGS_ID = 1", null)
            applied = true
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return applied
    }

    private fun scheduleSync() = SyncScheduler.schedule(appContext)

    private fun readSettings(db: SQLiteDatabase): FocusSettings = db.query(
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

    private fun readDomains(db: SQLiteDatabase): List<String> = db.query(
        TABLE_DESKTOP_DOMAINS,
        arrayOf(COLUMN_DOMAIN),
        null,
        null,
        null,
        null,
        "$COLUMN_DOMAIN ASC",
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    private fun readSnapshot(db: SQLiteDatabase) = SyncSnapshot(
        settings = readSettings(db),
        desktopBlockedDomains = readDomains(db),
        blocks = queryBlocks(db, null, null),
    )

    private fun readSyncState(db: SQLiteDatabase): PendingSyncState {
        val metadata = db.query(
            TABLE_SYNC_STATE,
            arrayOf(COLUMN_REVISION, COLUMN_GENERATION, COLUMN_PENDING),
            "$COLUMN_SETTINGS_ID = 1",
            null,
            null,
            null,
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use longArrayOf(-1, 0, 0)
            longArrayOf(cursor.getLong(0), cursor.getLong(1), cursor.getLong(2))
        }
        val statusAndMessage = db.query(
            TABLE_SYNC_STATE,
            arrayOf(COLUMN_STATUS, COLUMN_MESSAGE),
            "$COLUMN_SETTINGS_ID = 1",
            null,
            null,
            null,
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) SyncStatus.NOT_CONFIGURED to null
            else runCatching { SyncStatus.valueOf(cursor.getString(0)) }
                .getOrDefault(SyncStatus.ERROR) to cursor.nullableString(1)
        }
        val paths = db.query(TABLE_SYNC_CHANGES, arrayOf(COLUMN_PATH), null, null, null, null, null)
            .use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }
        return PendingSyncState(
            revision = metadata[0],
            generation = metadata[1],
            pending = metadata[2] != 0L,
            status = statusAndMessage.first,
            message = statusAndMessage.second,
            pendingPaths = paths,
        )
    }

    private fun updateSyncMetadata(status: SyncStatus, message: String?) {
        val values = ContentValues().apply {
            put(COLUMN_STATUS, status.name)
            if (message == null) putNull(COLUMN_MESSAGE) else put(COLUMN_MESSAGE, message)
        }
        database.writableDatabase.update(TABLE_SYNC_STATE, values, "$COLUMN_SETTINGS_ID = 1", null)
    }

    private fun markDirty(db: SQLiteDatabase, paths: List<String>) {
        val uniquePaths = paths.distinct()
        if (uniquePaths.isEmpty()) return
        uniquePaths.forEach { path ->
            db.insertWithOnConflict(
                TABLE_SYNC_CHANGES,
                null,
                ContentValues().apply { put(COLUMN_PATH, path) },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
        db.execSQL(
            "UPDATE $TABLE_SYNC_STATE SET $COLUMN_GENERATION = $COLUMN_GENERATION + 1, " +
                "$COLUMN_PENDING = 1, $COLUMN_STATUS = 'PENDING', $COLUMN_MESSAGE = NULL " +
                "WHERE $COLUMN_SETTINGS_ID = 1",
        )
    }

    private fun replaceSnapshot(db: SQLiteDatabase, snapshot: SyncSnapshot) {
        db.insertWithOnConflict(
            TABLE_SETTINGS,
            null,
            ContentValues().apply {
                put(COLUMN_SETTINGS_ID, 1)
                put(COLUMN_REQUIRED_COUNT, snapshot.settings.requiredBlockCount)
                put(COLUMN_CUTOFF_MINUTE, snapshot.settings.eveningCutoff.toMinuteOfDay())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        db.delete(TABLE_BLOCKS, null, null)
        snapshot.blocks.forEach { db.insertWithOnConflict(TABLE_BLOCKS, null, it.toContentValues(), SQLiteDatabase.CONFLICT_REPLACE) }
        db.delete(TABLE_DESKTOP_DOMAINS, null, null)
        snapshot.desktopBlockedDomains.forEach { domain ->
            db.insert(TABLE_DESKTOP_DOMAINS, null, ContentValues().apply { put(COLUMN_DOMAIN, domain) })
        }
    }

    private fun queryBlocks(db: SQLiteDatabase, selection: String?, selectionArgs: Array<String>?): List<PlannedBlock> =
        db.query(
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
                            note = cursor.nullableString(7),
                        ),
                    )
                }
            }
        }

    private fun changedBlockPaths(previous: PlannedBlock?, updated: PlannedBlock): List<String> {
        val values = listOf(
            "date" to (previous?.date != updated.date),
            "name" to (previous?.name != updated.name),
            "startTime" to (previous?.startTime != updated.startTime),
            "endTime" to (previous?.endTime != updated.endTime),
            "accessProfile" to (previous?.accessProfile != updated.accessProfile),
            "completionStatus" to (previous?.completionStatus != updated.completionStatus),
            "note" to (previous?.note != updated.note),
        )
        return values.filter { it.second }.map { "blocks.${updated.id}.${it.first}" }
    }

    private fun PlannedBlock.toContentValues() = ContentValues().apply {
        put(COLUMN_ID, id)
        put(COLUMN_DATE, date.toString())
        put(COLUMN_NAME, name.trim())
        put(COLUMN_START_MINUTE, startTime.toMinuteOfDay())
        put(COLUMN_END_MINUTE, endTime.toMinuteOfDay())
        put(COLUMN_ACCESS_PROFILE, accessProfile.name)
        put(COLUMN_COMPLETION_STATUS, completionStatus.name)
        if (note == null) putNull(COLUMN_NOTE) else put(COLUMN_NOTE, note)
    }

    private fun Int.toLocalTime() = LocalTime.of(this / 60, this % 60)
    private fun LocalTime.toMinuteOfDay() = hour * 60 + minute
    private fun android.database.Cursor.nullableString(column: Int) = if (isNull(column)) null else getString(column)

    private class FocusDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            createPlanTables(db)
            createSyncTables(db)
            seedDomains(db)
            insertInitialSyncState(db)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                createSyncTables(db)
                seedDomains(db)
                insertInitialSyncState(db)
                val paths = mutableListOf("settings.requiredBlockCount", "settings.eveningCutoff")
                db.query(TABLE_BLOCKS, arrayOf(COLUMN_ID), null, null, null, null, null).use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        paths += listOf("date", "name", "startTime", "endTime", "accessProfile", "completionStatus")
                            .map { "blocks.$id.$it" }
                    }
                }
                paths.forEach { path ->
                    db.insertWithOnConflict(TABLE_SYNC_CHANGES, null, ContentValues().apply { put(COLUMN_PATH, path) }, SQLiteDatabase.CONFLICT_REPLACE)
                }
                db.execSQL("UPDATE $TABLE_SYNC_STATE SET $COLUMN_GENERATION = 1, $COLUMN_PENDING = 1, $COLUMN_STATUS = 'PENDING' WHERE $COLUMN_SETTINGS_ID = 1")
            }
            if (oldVersion < 3) {
                db.execSQL("ALTER TABLE $TABLE_BLOCKS ADD COLUMN $COLUMN_NOTE TEXT")
            }
            if (oldVersion < 4) {
                val hadSyncState = db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
                    arrayOf(TABLE_SYNC_STATE),
                ).use { it.moveToFirst() }
                val hadNote = db.rawQuery("PRAGMA table_info($TABLE_BLOCKS)", null).use { cursor ->
                    val nameColumn = cursor.getColumnIndexOrThrow("name")
                    var found = false
                    while (cursor.moveToNext()) if (cursor.getString(nameColumn) == COLUMN_NOTE) found = true
                    found
                }
                if (!hadNote) db.execSQL("ALTER TABLE $TABLE_BLOCKS ADD COLUMN $COLUMN_NOTE TEXT")
                createSyncTables(db)
                seedDomains(db)
                insertInitialSyncState(db)
                if (!hadSyncState) {
                    val paths = mutableListOf("settings.requiredBlockCount", "settings.eveningCutoff")
                    db.query(TABLE_BLOCKS, arrayOf(COLUMN_ID), null, null, null, null, null).use { cursor ->
                        while (cursor.moveToNext()) {
                            val id = cursor.getString(0)
                            paths += listOf("date", "name", "startTime", "endTime", "accessProfile", "completionStatus")
                                .map { "blocks.$id.$it" }
                        }
                    }
                    paths.forEach { path ->
                        db.insertWithOnConflict(TABLE_SYNC_CHANGES, null, ContentValues().apply { put(COLUMN_PATH, path) }, SQLiteDatabase.CONFLICT_REPLACE)
                    }
                    db.execSQL("UPDATE $TABLE_SYNC_STATE SET $COLUMN_GENERATION = 1, $COLUMN_PENDING = 1, $COLUMN_STATUS = 'PENDING' WHERE $COLUMN_SETTINGS_ID = 1")
                }
            }
        }

        private fun createPlanTables(db: SQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS $TABLE_BLOCKS (
                    $COLUMN_ID TEXT PRIMARY KEY NOT NULL,
                    $COLUMN_DATE TEXT NOT NULL,
                    $COLUMN_NAME TEXT NOT NULL,
                    $COLUMN_START_MINUTE INTEGER NOT NULL,
                    $COLUMN_END_MINUTE INTEGER NOT NULL,
                    $COLUMN_ACCESS_PROFILE TEXT NOT NULL,
                    $COLUMN_COMPLETION_STATUS TEXT NOT NULL,
                    $COLUMN_NOTE TEXT
                )""".trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_planned_blocks_date_start ON $TABLE_BLOCKS($COLUMN_DATE, $COLUMN_START_MINUTE)")
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS $TABLE_SETTINGS (
                    $COLUMN_SETTINGS_ID INTEGER PRIMARY KEY NOT NULL,
                    $COLUMN_REQUIRED_COUNT INTEGER NOT NULL,
                    $COLUMN_CUTOFF_MINUTE INTEGER NOT NULL
                )""".trimIndent(),
            )
        }

        private fun createSyncTables(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_DESKTOP_DOMAINS ($COLUMN_DOMAIN TEXT PRIMARY KEY NOT NULL)")
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS $TABLE_SYNC_STATE (
                    $COLUMN_SETTINGS_ID INTEGER PRIMARY KEY NOT NULL,
                    $COLUMN_REVISION INTEGER NOT NULL,
                    $COLUMN_GENERATION INTEGER NOT NULL,
                    $COLUMN_PENDING INTEGER NOT NULL,
                    $COLUMN_STATUS TEXT NOT NULL,
                    $COLUMN_MESSAGE TEXT
                )""".trimIndent(),
            )
            db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_SYNC_CHANGES ($COLUMN_PATH TEXT PRIMARY KEY NOT NULL)")
        }

        private fun seedDomains(db: SQLiteDatabase) {
            DEFAULT_DESKTOP_BLOCKED_DOMAINS.forEach { domain ->
                db.insertWithOnConflict(TABLE_DESKTOP_DOMAINS, null, ContentValues().apply { put(COLUMN_DOMAIN, domain) }, SQLiteDatabase.CONFLICT_IGNORE)
            }
        }

        private fun insertInitialSyncState(db: SQLiteDatabase) {
            db.insertWithOnConflict(
                TABLE_SYNC_STATE,
                null,
                ContentValues().apply {
                    put(COLUMN_SETTINGS_ID, 1)
                    put(COLUMN_REVISION, -1)
                    put(COLUMN_GENERATION, 0)
                    put(COLUMN_PENDING, 0)
                    put(COLUMN_STATUS, "NOT_CONFIGURED")
                },
                SQLiteDatabase.CONFLICT_IGNORE,
            )
        }

        companion object {
            private const val DATABASE_NAME = "jp-freedom.db"
            private const val DATABASE_VERSION = 4
        }
    }

    private companion object {
        const val TABLE_BLOCKS = "planned_blocks"
        const val TABLE_SETTINGS = "focus_settings"
        const val TABLE_DESKTOP_DOMAINS = "desktop_blocked_domains"
        const val TABLE_SYNC_STATE = "sync_state"
        const val TABLE_SYNC_CHANGES = "sync_changes"
        const val COLUMN_ID = "id"
        const val COLUMN_DATE = "date"
        const val COLUMN_NAME = "name"
        const val COLUMN_START_MINUTE = "start_minute"
        const val COLUMN_END_MINUTE = "end_minute"
        const val COLUMN_ACCESS_PROFILE = "access_profile"
        const val COLUMN_COMPLETION_STATUS = "completion_status"
        const val COLUMN_NOTE = "note"
        const val COLUMN_SETTINGS_ID = "id"
        const val COLUMN_REQUIRED_COUNT = "required_count"
        const val COLUMN_CUTOFF_MINUTE = "cutoff_minute"
        const val COLUMN_DOMAIN = "domain"
        const val COLUMN_PATH = "path"
        const val COLUMN_REVISION = "revision"
        const val COLUMN_GENERATION = "generation"
        const val COLUMN_PENDING = "pending"
        const val COLUMN_STATUS = "status"
        const val COLUMN_MESSAGE = "message"
        val BLOCK_COLUMNS = arrayOf(
            COLUMN_ID,
            COLUMN_DATE,
            COLUMN_NAME,
            COLUMN_START_MINUTE,
            COLUMN_END_MINUTE,
            COLUMN_ACCESS_PROFILE,
            COLUMN_COMPLETION_STATUS,
            COLUMN_NOTE,
        )
    }
}
