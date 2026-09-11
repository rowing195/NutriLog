package com.watson.nutrilog.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE food_entries ADD COLUMN portionMultiplier REAL NOT NULL DEFAULT 1.0")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS daily_health_metrics (
                date TEXT NOT NULL PRIMARY KEY,
                activeCalories REAL NOT NULL,
                steps INTEGER NOT NULL,
                workoutCalories REAL NOT NULL,
                lastSyncedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

@Database(
    entities = [FoodEntry::class, CachedProduct::class, DailyHealthMetric::class],
    version = 3,
    exportSchema = false,
)
abstract class NutriDatabase : RoomDatabase() {
    abstract fun dao(): NutriDao

    companion object {
        @Volatile private var instance: NutriDatabase? = null

        fun get(context: Context): NutriDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                NutriDatabase::class.java,
                "nutrilog.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build().also { instance = it }
        }
    }
}
