package com.watson.nutrilog.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [FoodEntry::class, CachedProduct::class],
    version = 1,
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
            ).build().also { instance = it }
        }
    }
}
