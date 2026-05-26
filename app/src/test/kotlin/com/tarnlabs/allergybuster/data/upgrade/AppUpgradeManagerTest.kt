package com.tarnlabs.allergybuster.data.upgrade

import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import com.tarnlabs.allergybuster.data.local.datastore.AppSettingsDataStore
import com.tarnlabs.allergybuster.data.local.db.AllergyBusterDatabase
import io.mockk.coEvery
import io.mockk.coVerify
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
    }

    @Test
    fun `fresh install never deletes data and records current version`() = runTest {
        coEvery { settings.getLastAppVersionCode() } returns null

        val t = manager.detectTransition(currentVersionCode = 5)
        manager.runUpgradeMigrations(t)

        assertEquals(emptyList<String>(), driver.executedSql)
        coVerify { settings.setLastAppVersionCode(5) }
        coVerify(exactly = 0) { settings.clearRoomMigrationFlag() }
    }

    @Test
    fun `upgrade from v2 never deletes data`() = runTest {
        coEvery { settings.getLastAppVersionCode() } returns 2

        val t = manager.detectTransition(currentVersionCode = 5)
        manager.runUpgradeMigrations(t)

        assertEquals(emptyList<String>(), driver.executedSql)
        coVerify(exactly = 0) { settings.clearRoomMigrationFlag() }
        coVerify { settings.setLastAppVersionCode(5) }
    }

    @Test
    fun `same version is a no-op`() = runTest {
        coEvery { settings.getLastAppVersionCode() } returns 5

        val t = manager.detectTransition(currentVersionCode = 5)
        manager.runUpgradeMigrations(t)

        assertEquals(emptyList<String>(), driver.executedSql)
        coVerify(exactly = 0) { settings.clearRoomMigrationFlag() }
        coVerify { settings.setLastAppVersionCode(5) }
    }

    @Test
    fun `forward jump records new version without deleting`() = runTest {
        coEvery { settings.getLastAppVersionCode() } returns 5

        val t = manager.detectTransition(currentVersionCode = 6)
        manager.runUpgradeMigrations(t)

        assertEquals(emptyList<String>(), driver.executedSql)
        coVerify(exactly = 0) { settings.clearRoomMigrationFlag() }
        coVerify { settings.setLastAppVersionCode(6) }
    }
}

private class RecordingSqlDriver : SqlDriver {
    val executedSql = mutableListOf<String>()

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?
    ): QueryResult<Long> {
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

    override fun newTransaction(): QueryResult<Transacter.Transaction> = error("not used")
    override fun currentTransaction(): Transacter.Transaction? = null
    override fun addListener(vararg queryKeys: String, listener: Query.Listener) {}
    override fun removeListener(vararg queryKeys: String, listener: Query.Listener) {}
    override fun notifyListeners(vararg queryKeys: String) {}
    override fun close() {}
}
