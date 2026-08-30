package com.watson.nutrilog

import com.watson.nutrilog.work.BackupWorker
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

/**
 * 每日備份對齊凌晨那段。不測的話要等一整天才知道排錯了，
 * 而排錯的症狀是「備份時間每天往後飄」——很久之後才會被發現。
 */
class BackupScheduleTest {

    private fun minutesFrom(text: String) =
        BackupWorker.minutesUntilNextRun(LocalDateTime.parse(text))

    @Test
    fun `afternoon waits until the small hours of the next day`() {
        // 15:11 → 隔天 03:00 是 11 小時 49 分
        assertEquals(11 * 60 + 49, minutesFrom("2026-08-30T15:11:00"))
    }

    @Test
    fun `before the backup hour it runs later the same night`() {
        assertEquals(120, minutesFrom("2026-08-30T01:00:00"))
    }

    /** 剛好卡在整點時往後推一天，不要排成 0 分鐘後立刻執行。 */
    @Test
    fun `exactly on the hour waits a full day`() {
        assertEquals(24 * 60, minutesFrom("2026-08-30T03:00:00"))
    }

    @Test
    fun `crossing into a new month and year still works`() {
        assertEquals(180, minutesFrom("2026-12-31T00:00:00"))
        assertEquals(23 * 60 + 59, minutesFrom("2026-12-31T03:01:00"))
    }

    /** 永遠落在合理範圍內：不會是負的，也不會超過一天。 */
    @Test
    fun `delay always lands inside one day`() {
        (0..23).forEach { hour ->
            val minutes = minutesFrom("2026-08-30T%02d:30:00".format(hour))
            assert(minutes in 1..(24 * 60)) { "hour $hour gave $minutes" }
        }
    }
}
