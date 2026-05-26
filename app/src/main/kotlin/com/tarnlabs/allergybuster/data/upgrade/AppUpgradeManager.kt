package com.tarnlabs.allergybuster.data.upgrade

import android.util.Log
import app.cash.sqldelight.db.SqlDriver
import com.tarnlabs.allergybuster.BuildConfig
import com.tarnlabs.allergybuster.data.local.datastore.AppSettingsDataStore
import com.tarnlabs.allergybuster.data.local.db.AllergyBusterDatabase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles per-version-code upgrade cleanups. Runs BEFORE the Room→SQLDelight
 * migrator so a wipe pre-empts a (potentially broken) copy of stale rows.
 *
 * Default policy for unknown future jumps: no-op. Only known ranges declare
 * cleanups; data loss is worse than a rare stuck state.
 */
@Singleton
class AppUpgradeManager @Inject constructor(
    private val settings: AppSettingsDataStore,
    private val database: AllergyBusterDatabase,
    private val driver: SqlDriver,
) {
    data class Transition(val from: Int?, val to: Int)

    suspend fun detectTransition(): Transition =
        detectTransition(currentVersionCode = BuildConfig.VERSION_CODE)

    internal suspend fun detectTransition(currentVersionCode: Int): Transition =
        withContext(Dispatchers.IO) {
            Transition(from = settings.getLastAppVersionCode(), to = currentVersionCode)
        }

    suspend fun runUpgradeMigrations(t: Transition): Unit = withContext(Dispatchers.IO) {
        try {
            // `from == null` means LAST_APP_VERSION_CODE has never been written.
            // Treat that as "pre-AppUpgradeManager" (anywhere up to and including
            // versionCode 3) — those users may have a poisoned forecast cache from
            // the 1.1→1.2 Room→SQLDelight jump and need the same cleanup.
            val effectiveFrom = t.from ?: PRE_UPGRADE_MANAGER_VERSION
            when {
                effectiveFrom == t.to -> {
                    // Same version — no-op.
                }
                effectiveFrom < FIRST_VERSION_WITH_UPGRADE_MANAGER &&
                    t.to >= FIRST_VERSION_WITH_UPGRADE_MANAGER -> {
                    Log.i(TAG, "Upgrade ${t.from}→${t.to}: wiping forecast cache, will re-run Room migration")
                    wipeForecastCache()
                    settings.clearRoomMigrationFlag()
                }
                else -> {
                    Log.i(TAG, "Upgrade ${t.from}→${t.to}: no cleanup registered")
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Upgrade migration failed; bumping version anyway to avoid loop", e)
        } finally {
            settings.setLastAppVersionCode(t.to)
        }
    }

    private fun wipeForecastCache() {
        database.transaction {
            driver.execute(null, "DELETE FROM recommendation", 0)
            driver.execute(null, "DELETE FROM pollen_forecast", 0)
        }
    }

    companion object {
        private const val TAG = "AppUpgradeManager"

        // versionCode 3 (1.2.0) was the last release without AppUpgradeManager.
        // Users on null/anything ≤ 3 may have a poisoned forecast cache.
        internal const val PRE_UPGRADE_MANAGER_VERSION = 3
        internal const val FIRST_VERSION_WITH_UPGRADE_MANAGER = 4
    }
}
