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
 * Records app-version transitions in DataStore so future schema changes can
 * declare per-range cleanup hooks. Default policy is no-op — the Flow-level
 * resilience in the repositories (per-row runCatching + .catch { emit(empty) })
 * is sufficient to prevent the home screen from getting stuck, so we should
 * NOT delete user data on upgrade.
 *
 * If a future schema change does need a cleanup, register it inside
 * runUpgradeMigrations with a precise (from, to) range guard.
 */
@Singleton
class AppUpgradeManager @Inject constructor(
    private val settings: AppSettingsDataStore,
    @Suppress("unused") private val database: AllergyBusterDatabase,
    @Suppress("unused") private val driver: SqlDriver,
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
            // No cleanups currently registered. The wipe that shipped in
            // versionCode 4 was destructive (it dropped 90 days of recommendation
            // history) and unnecessary: the repository-level Flow resilience
            // already drops poisoned rows without taking the whole UI down.
            //
            // Future per-version cleanups can be added here with explicit
            // (from, to) guards, but data loss must always be the last resort.
            Log.i(TAG, "Upgrade ${t.from}→${t.to}: no cleanup registered")
        } catch (e: Throwable) {
            Log.e(TAG, "Upgrade migration failed; bumping version anyway to avoid loop", e)
        } finally {
            settings.setLastAppVersionCode(t.to)
        }
    }

    companion object {
        private const val TAG = "AppUpgradeManager"
    }
}
