package com.tarnlabs.allergybuster.data.local.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

private const val DB_NAME = "allergy_shared.db"

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver = NativeSqliteDriver(AllergyBusterDatabase.Schema, DB_NAME)

    fun createDatabase(): AllergyBusterDatabase = AllergyBusterDatabase(createDriver())
}
