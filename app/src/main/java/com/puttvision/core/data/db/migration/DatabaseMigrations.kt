package com.puttvision.core.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database migration definitions for PuttVision.
 *
 * Rule: never use fallbackToDestructiveMigration() in production builds.
 * All schema changes must have a corresponding migration here.
 *
 * Current schema version: 1
 *
 * How to add a migration:
 *   1. Increment the `version` in [PuttVisionDatabase]
 *   2. Add a MIGRATION_X_Y object here
 *   3. Register it in [DatabaseModule.provideDatabase]
 *   4. Run `./gradlew :app:exportSchema` to verify (requires exportSchema=true in @Database)
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * Version history:
 *   1 → Initial schema: sessions, putt_records, putt_metrics, user_config
 * ─────────────────────────────────────────────────────────────────────────────
 */
object DatabaseMigrations {

    /**
     * Placeholder for future migration from v1 → v2.
     *
     * Example: if we add a `distance_m` column to putt_records:
     *
     * val MIGRATION_1_2 = object : Migration(1, 2) {
     *     override fun migrate(database: SupportSQLiteDatabase) {
     *         database.execSQL(
     *             "ALTER TABLE putt_records ADD COLUMN distance_m REAL"
     *         )
     *     }
     * }
     *
     * Then in DatabaseModule:
     *   Room.databaseBuilder(...)
     *       .addMigrations(MIGRATION_1_2)
     *       .build()
     */

    // No migrations yet — schema is at v1.
    // List here when the schema changes:
    val ALL: Array<Migration> = emptyArray()
}
