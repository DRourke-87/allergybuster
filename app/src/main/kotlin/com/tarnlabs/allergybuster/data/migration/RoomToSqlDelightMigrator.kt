package com.tarnlabs.allergybuster.data.migration

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import app.cash.sqldelight.db.SqlDriver
import com.tarnlabs.allergybuster.data.local.datastore.AppSettingsDataStore
import com.tarnlabs.allergybuster.data.local.db.AllergyBusterDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

@Singleton
class RoomToSqlDelightMigrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AllergyBusterDatabase,
    private val driver: SqlDriver,
    private val settings: AppSettingsDataStore
) {
    suspend fun migrateIfNeeded(): MigrationResult = withContext(Dispatchers.IO) {
        if (settings.roomMigrationDoneFlow.first()) {
            return@withContext MigrationResult.Skipped
        }

        val oldPath = context.getDatabasePath(OLD_DB_NAME)
        if (!oldPath.exists()) {
            settings.markRoomMigrationDone()
            Log.i(TAG, "No old database found; treating as clean install")
            return@withContext MigrationResult.CleanInstall
        }

        val old = try {
            SQLiteDatabase.openDatabase(oldPath.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        } catch (t: Throwable) {
            Log.w(TAG, "Old database unreadable; leaving flag unset for retry", t)
            return@withContext MigrationResult.Failed(t, emptyMap())
        }

        val counts = mutableMapOf<String, Int>()
        try {
            database.transaction {
                counts["daily_feedback"]  = migrateDailyFeedback(old)
                counts["pollen_forecast"] = migratePollenForecast(old)
                counts["recommendation"]  = migrateRecommendation(old)
                counts["user_weights"]    = migrateUserWeights(old)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Migration transaction rolled back", t)
            return@withContext MigrationResult.Failed(t, counts)
        } finally {
            runCatching { old.close() }
        }

        settings.markRoomMigrationDone()
        runCatching { SQLiteDatabase.deleteDatabase(oldPath) }

        Log.i(TAG, "Migrated counts: $counts")
        MigrationResult.Migrated(counts)
    }

    private fun migrateDailyFeedback(old: SQLiteDatabase): Int {
        if (!tableExists(old, "daily_feedback")) return 0
        var n = 0
        old.rawQuery(
            "SELECT date, severity, recordedAt, bayesianApplied FROM daily_feedback",
            null
        ).use { c ->
            while (c.moveToNext()) {
                driver.execute(
                    null,
                    "INSERT OR IGNORE INTO daily_feedback(date, severity, recordedAt, bayesianApplied) " +
                        "VALUES (?, ?, ?, ?)",
                    4
                ) {
                    bindString(0, c.getString(0))
                    bindLong(1, c.getLong(1))
                    bindLong(2, c.getLong(2))
                    bindLong(3, c.getLong(3))
                }
                n++
            }
        }
        return n
    }

    private fun migratePollenForecast(old: SQLiteDatabase): Int {
        if (!tableExists(old, "pollen_forecast")) return 0
        var n = 0
        old.rawQuery(
            "SELECT date, alderMax, birchMax, grassMax, mugwortMax, oliveMax, ragweedMax, fetchedAt " +
                "FROM pollen_forecast",
            null
        ).use { c ->
            while (c.moveToNext()) {
                driver.execute(
                    null,
                    "INSERT OR IGNORE INTO pollen_forecast(" +
                        "date, alderMax, birchMax, grassMax, mugwortMax, oliveMax, ragweedMax, fetchedAt" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    8
                ) {
                    bindString(0, c.getString(0))
                    bindDouble(1, c.getDouble(1))
                    bindDouble(2, c.getDouble(2))
                    bindDouble(3, c.getDouble(3))
                    bindDouble(4, c.getDouble(4))
                    bindDouble(5, c.getDouble(5))
                    bindDouble(6, c.getDouble(6))
                    bindLong(7, c.getLong(7))
                }
                n++
            }
        }
        return n
    }

    private fun migrateRecommendation(old: SQLiteDatabase): Int {
        if (!tableExists(old, "recommendation")) return 0
        var n = 0
        old.rawQuery(
            "SELECT date, level, score, advice, topContributors, computedAt, isStale, locationName " +
                "FROM recommendation",
            null
        ).use { c ->
            while (c.moveToNext()) {
                driver.execute(
                    null,
                    "INSERT OR IGNORE INTO recommendation(" +
                        "date, level, score, advice, topContributors, computedAt, isStale, locationName" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    8
                ) {
                    bindString(0, c.getString(0))
                    bindLong(1, c.getLong(1))
                    bindDouble(2, c.getDouble(2))
                    bindString(3, c.getString(3))
                    bindString(4, sanitizeContributorsJson(c.getString(4)))
                    bindLong(5, c.getLong(5))
                    bindLong(6, c.getLong(6))
                    bindString(7, c.getString(7) ?: "")
                }
                n++
            }
        }
        return n
    }

    private fun migrateUserWeights(old: SQLiteDatabase): Int {
        if (!tableExists(old, "user_weights")) return 0
        var n = 0
        old.rawQuery(
            "SELECT alderWeight, birchWeight, grassWeight, mugwortWeight, oliveWeight, ragweedWeight, updatedAt " +
                "FROM user_weights LIMIT 1",
            null
        ).use { c ->
            while (c.moveToNext()) {
                driver.execute(
                    null,
                    "INSERT OR IGNORE INTO user_weights(" +
                        "id, alderWeight, birchWeight, grassWeight, mugwortWeight, oliveWeight, ragweedWeight, updatedAt" +
                        ") VALUES (1, ?, ?, ?, ?, ?, ?, ?)",
                    7
                ) {
                    bindDouble(0, c.getDouble(0))
                    bindDouble(1, c.getDouble(1))
                    bindDouble(2, c.getDouble(2))
                    bindDouble(3, c.getDouble(3))
                    bindDouble(4, c.getDouble(4))
                    bindDouble(5, c.getDouble(5))
                    bindLong(6, c.getLong(6))
                }
                n++
            }
        }
        return n
    }

    internal fun sanitizeContributorsJson(raw: String?): String {
        if (raw.isNullOrBlank()) return "[]"
        return try {
            Json.decodeFromString<List<String>>(raw)
            raw
        } catch (_: Throwable) {
            Log.w(TAG, "Dropping malformed topContributors during migration")
            "[]"
        }
    }

    private fun tableExists(db: SQLiteDatabase, name: String): Boolean =
        db.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(name)
        ).use { it.moveToFirst() }

    companion object {
        private const val TAG = "RoomToSqlDelightMigrator"
        private const val OLD_DB_NAME = "allergy.db"
    }
}
