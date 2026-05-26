package com.tarnlabs.allergybuster.data.upgrade

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransactionWithoutReturn
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import com.tarnlabs.allergybuster.data.local.datastore.AppSettingsDataStore
import com.tarnlabs.allergybuster.data.local.db.AllergyBusterDatabase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AppUpgradeManagerTest {

    private lateinit var settings: AppSettingsDataStore
    private lateinit var database: AllergyBusterDatabase
    private lateinit var driver: RecordingSqlDriver
    private lateinit var manager: AppUpgradeManager

    @Before
    fun setUp() {
        settings = mockk(relaxed = true)
        database = mockk()
        driver = RecordingSqlDriver()
        manager = AppUpgradeManager(settings, database, driver)

        every {
            database.transaction(any(), captureLambda<TransactionWithoutReturn.() -> Unit>())
        } answers {
            lambda<TransactionWithoutReturn.() -> Unit>().captured.invoke(mockk(relaxed = true))
        }
    }

    @Test
    fun `fresh install records current version and does not wipe`() = runTest {
        coEvery { settings.getLastAppVersionCode() } returns null

        val t = manager.detectTransition(currentVersionCode = 3)
        manager.runUpgradeMigrations(t)

        assertEquals(null, t.from)
        assertEquals(3, t.to)
        assertEquals(emptyList<String>(), driver.executedSql)
        coVerify { settings.setLastAppVersionCode(3) }
        coVerify(exactly = 0) { settings.clearRoomMigrationFlag() }
    }

    @Test
    fun `2 to 3 transition wipes cache tables and clears Room migration flag`() = runTest {
        coEvery { settings.getLastAppVersionCode() } returns 2

        val t = manager.detectTransition(currentVersionCode = 3)
        manager.runUpgradeMigrations(t)

        assertEquals(
            listOf("DELETE FROM recommendation", "DELETE FROM pollen_forecast"),
            driver.executedSql
        )
        coVerify { settings.clearRoomMigrationFlag() }
        coVerify { settings.setLastAppVersionCode(3) }
    }

    @Test
    fun `same version is a no-op`() = runTest {
        coEvery { settings.getLastAppVersionCode() } returns 3

        val t = manager.detectTransition(currentVersionCode = 3)
        manager.runUpgradeMigrations(t)

        assertEquals(emptyList<String>(), driver.executedSql)
        coVerify(exactly = 0) { settings.clearRoomMigrationFlag() }
        coVerify { settings.setLastAppVersionCode(3) }
    }

    @Test
    fun `unknown future jump does not wipe but records version`() = runTest {
        coEvery { settings.getLastAppVersionCode() } returns 3

        val t = manager.detectTransition(currentVersionCode = 4)
        manager.runUpgradeMigrations(t)

        assertEquals(emptyList<String>(), driver.executedSql)
        coVerify(exactly = 0) { settings.clearRoomMigrationFlag() }
        coVerify { settings.setLastAppVersionCode(4) }
    }

    @Test
    fun `wipe failure still bumps version to avoid infinite retry loop`() = runTest {
        coEvery { settings.getLastAppVersionCode() } returns 2
        driver.throwOnExecute = RuntimeException("boom")

        val t = manager.detectTransition(currentVersionCode = 3)
        manager.runUpgradeMigrations(t)

        coVerify { settings.setLastAppVersionCode(3) }
    }
}

/**
 * Minimal SqlDriver fake that captures executed SQL. Manual implementation avoids
 * mockK's trouble with SQLDelight's QueryResult value class.
 */
private class RecordingSqlDriver : SqlDriver {
    val executedSql = mutableListOf<String>()
    var throwOnExecute: Throwable? = null

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?
    ): QueryResult<Long> {
        throwOnExecute?.let { throw it }
        executedSql += sql
        return QueryResult.Value(0L)
    }

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?
    ): QueryResult<R> = error("not used")

    override fun newTransaction(): QueryResult<Transacter.Transaction> =
        error("not used")

    override fun currentTransaction(): Transacter.Transaction? = null

    override fun addListener(vararg queryKeys: String, listener: Query.Listener) {}
    override fun removeListener(vararg queryKeys: String, listener: Query.Listener) {}
    override fun notifyListeners(vararg queryKeys: String) {}
    override fun close() {}
}
