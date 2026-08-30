package com.watson.nutrilog

import com.watson.nutrilog.data.DriveBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 保留 30 天那段的規則。會刪雲端檔案的邏輯不該只靠「看起來對」——
 * 刪錯了使用者不會馬上發現，等到要還原時才發現備份不見就來不及了。
 */
class DriveBackupPruneTest {

    private fun backups(vararg days: String) = days.map { "nutrilog-$it.csv" }

    @Test
    fun `keeps the newest N and drops the rest`() {
        val names = (1..35).map { "nutrilog-2026-08-%02d.csv".format(it % 31 + 1) }.distinct()
        val pruned = DriveBackup.namesToPrune(names, 30)

        assertEquals(names.size - 30, pruned.size)
        // 留下來的一定比刪掉的新
        val kept = names - pruned.toSet()
        assertTrue(kept.min() > pruned.max())
    }

    @Test
    fun `nothing to prune while under the limit`() {
        assertEquals(emptyList<String>(), DriveBackup.namesToPrune(backups("2026-08-30", "2026-08-29"), 30))
    }

    /** 使用者自己丟進那個資料夾的東西一律不碰 —— 那是他的 Drive，不是我們的暫存區。 */
    @Test
    fun `files that are not our backups are never pruned`() {
        val names = listOf(
            "我的筆記.txt",
            "nutrilog.csv",
            "nutrilog-2026-08-30.csv",
            "nutrilog-2026-08-29.csv",
            "nutrilog-backup-final.csv",
        )
        val pruned = DriveBackup.namesToPrune(names, 1)

        assertEquals(listOf("nutrilog-2026-08-29.csv"), pruned)
    }

    @Test
    fun `date ordering comes from the fixed-width name, across months and years`() {
        val names = backups("2025-12-31", "2026-01-01", "2026-08-09", "2026-08-10")
        assertEquals(
            listOf("nutrilog-2025-12-31.csv", "nutrilog-2026-01-01.csv"),
            DriveBackup.namesToPrune(names, 2).sorted(),
        )
    }
}
