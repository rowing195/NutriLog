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

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS daily_targets (
                date TEXT NOT NULL PRIMARY KEY,
                calorieTarget INTEGER NOT NULL,
                proteinTargetG INTEGER NOT NULL,
                fatTargetG INTEGER NOT NULL,
                carbsTargetG INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

/**
 * 水量：食物多一欄（飲料才有值），另外一張表放手動加減的那一段。
 * 兩者分開的理由見 [DailyWater]。
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 既有的紀錄沒有人知道當初喝了多少水，所以是 NULL（沒標示）而不是 0
        db.execSQL("ALTER TABLE food_entries ADD COLUMN waterMl REAL")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS daily_water (
                date TEXT NOT NULL PRIMARY KEY,
                manualMl INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

@Database(
    entities = [
        FoodEntry::class, CachedProduct::class, DailyHealthMetric::class,
        DailyTarget::class, DailyWater::class,
    ],
    version = 5,
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
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build().also { instance = it }
        }
    }
}
