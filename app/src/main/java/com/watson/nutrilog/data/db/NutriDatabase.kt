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

@Database(
    entities = [FoodEntry::class, CachedProduct::class],
    version = 2,
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
            ).addMigrations(MIGRATION_1_2)
            .build().also { instance = it }
        }
    }
}
