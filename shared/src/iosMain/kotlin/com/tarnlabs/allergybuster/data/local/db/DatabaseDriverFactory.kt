package com.tarnlabs.allergybuster.data.local.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import platform.Foundation.NSFileManager

private const val APP_GROUP = "group.com.tarnlabs.allergybuster"
private const val DB_NAME   = "allergy_shared.db"

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        // Store in App Group container so the WidgetKit extension reads the same database
        val containerPath = NSFileManager.defaultManager
            .containerURLForSecurityApplicationGroupIdentifier(APP_GROUP)?.path
        val dbPath = if (containerPath != null) "$containerPath/$DB_NAME" else DB_NAME
        return NativeSqliteDriver(AllergyBusterDatabase.Schema, dbPath)
    }

    fun createDatabase(): AllergyBusterDatabase = AllergyBusterDatabase(createDriver())
}
