package com.tarnlabs.allergybuster.data.migration

sealed interface MigrationResult {
    object Skipped : MigrationResult
    object CleanInstall : MigrationResult
    data class Migrated(val counts: Map<String, Int>) : MigrationResult
    data class Failed(val cause: Throwable, val partial: Map<String, Int>) : MigrationResult
}
