package com.watson.nutrilog.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 那一天**手動**加減的飲水量（ml）。
 *
 * 它和食物帶的水（[FoodEntry.waterMl]）是兩件事，所以分開存：喝白開水按今日頁那排
 * 加減鍵，不該在紀錄清單裡長出一堆 0 大卡的「飲水」；而飲料的水是那一筆食物的屬性，
 * 刪掉那筆飲料水量就該跟著消失。當天的飲水量是兩者相加。
 *
 * 可以是負的：喝的比食物帶的少（例如把湯的水量填太滿）時要扣得回來。相加之後不會
 * 低於 0，那道門在 `NutriViewModel.adjustWater`。
 */
@Entity(tableName = "daily_water")
data class DailyWater(
    @PrimaryKey val date: String,
    val manualMl: Int,
)
