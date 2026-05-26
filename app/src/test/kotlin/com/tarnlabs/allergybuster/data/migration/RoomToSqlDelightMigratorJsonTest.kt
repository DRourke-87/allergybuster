package com.tarnlabs.allergybuster.data.migration

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import com.tarnlabs.allergybuster.data.local.datastore.AppSettingsDataStore
import com.tarnlabs.allergybuster.data.local.db.AllergyBusterDatabase
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Validates the JSON sanitization helper used during the Room→SQLDelight migration
 * so corrupted `topContributors` JSON in the old DB can never poison the new DB.
 */
class RoomToSqlDelightMigratorJsonTest {

    private val migrator = RoomToSqlDelightMigrator(
        context  = mockk<Context>(relaxed = true),
        database = mockk<AllergyBusterDatabase>(relaxed = true),
        driver   = mockk<SqlDriver>(relaxed = true),
        settings = mockk<AppSettingsDataStore>(relaxed = true),
    )

    @Test
    fun `valid JSON array passes through unchanged`() {
        assertEquals("[\"birch\",\"grass\"]", migrator.sanitizeContributorsJson("[\"birch\",\"grass\"]"))
    }

    @Test
    fun `empty array passes through`() {
        assertEquals("[]", migrator.sanitizeContributorsJson("[]"))
    }

    @Test
    fun `null becomes empty array`() {
        assertEquals("[]", migrator.sanitizeContributorsJson(null))
    }

    @Test
    fun `blank string becomes empty array`() {
        assertEquals("[]", migrator.sanitizeContributorsJson(""))
        assertEquals("[]", migrator.sanitizeContributorsJson("   "))
    }

    @Test
    fun `malformed JSON becomes empty array`() {
        assertEquals("[]", migrator.sanitizeContributorsJson("{not json"))
        assertEquals("[]", migrator.sanitizeContributorsJson("undefined"))
    }

    @Test
    fun `JSON with wrong shape becomes empty array`() {
        // An object where a List<String> was expected.
        assertEquals("[]", migrator.sanitizeContributorsJson("{\"foo\":\"bar\"}"))
        // List of objects rather than List<String>.
        assertEquals("[]", migrator.sanitizeContributorsJson("[{\"a\":1}]"))
    }
}
