package com.watson.nutrilog.work

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.watson.nutrilog.data.DriveBackup
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * 每天把飲食紀錄備份到 Drive 一次。
 *
 * 用 WorkManager 而不是 AlarmManager：Doze 底下 alarm 會被延後到不確定的時間，
 * 而且開機後不會自己回來（要另外註冊 BOOT_COMPLETED 接收器）。WorkManager 兩件事
 * 都幫忙處理掉了，代價是「一天一次」是大約值不是準點 —— 對備份來說完全夠。
 *
 * **失敗一律 retry 而不是 failure**：最常見的失敗是當下沒網路或權杖過期，
 * 兩者都會自己好。真的壞掉（使用者收回授權）的情況會在設定頁看得出來 ——
 * 「上次備份」的時間會停住不動。
 */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return DriveBackup(applicationContext).backupNow().fold(
            onSuccess = { Result.success() },
            onFailure = { cause ->
                // 需要使用者同意時重試沒有意義 —— 背景沒有畫面可以問，
                // 等使用者下次打開設定頁自然會看到「上次備份」停在舊時間。
                if (cause is DriveBackup.NeedsConsent) {
                    Log.w(TAG, "備份需要重新授權，先停下來等使用者處理")
                    Result.failure()
                } else {
                    Log.w(TAG, "備份失敗，稍後重試", cause)
                    Result.retry()
                }
            },
        )
    }

    companion object {
        private const val TAG = "BackupWorker"
        private const val WORK_NAME = "drive-daily-backup"

        /**
         * 排在凌晨而不是整點的隨便一個時間：這時候手機通常在充電、有 Wi-Fi，
         * 而且不會跟使用者搶頻寬。
         *
         * 但**不要期待它準時**。凌晨手機多半在 Doze 深睡，實際執行會被延到裝置
         * 下次醒來（通常是早上第一次拿起手機）。WorkManager 保證的是「大約一天
         * 一次」，不是準點 —— 對備份來說夠了，寫在這裡是免得日後有人看到執行
         * 時間是早上八點就以為壞了。
         */
        private const val BACKUP_HOUR = 3

        /**
         * 到下一個當地 [BACKUP_HOUR] 點還有幾分鐘。
         *
         * 純函式、吃得到 now，這樣不必等一整天也測得到。用 [LocalDateTime] 而不是
         * 時區換算：這個 app 整套日期模型都是本地日期（見 FoodEntry.date 的註解），
         * 對齊的對象也該是使用者牆上那個鐘。
         */
        internal fun minutesUntilNextRun(now: LocalDateTime = LocalDateTime.now()): Long {
            val today = now.toLocalDate().atTime(BACKUP_HOUR, 0)
            val next = if (today.isAfter(now)) today else today.plusDays(1)
            return Duration.between(now, next).toMinutes()
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                // **這個延遲不能拿掉，理由有兩個。**
                //
                // 一、週期性工作預設會在排程當下就先跑一次，而排程發生在「連結
                // Drive」的那一刻 —— 那時候使用者可能正看著還原的確認面板還沒決定。
                // 備份檔名是當天日期，那一次立刻執行會把雲端那份完整的紀錄蓋成這支
                // 新手機上空空如也的狀態，正好毀掉他要救回來的東西。連結時該不該
                // 立刻備份由 connectDrive 自己判斷，排程只負責「之後每天一次」。
                //
                // 二、對齊到凌晨，週期才有意義。固定延遲一天的話，錨點會是「使用者
                // 按下連結的那個隨機時刻」—— 卡在傍晚的話，那份以當天日期命名的檔案
                // 永遠只有半天的內容。對齊凌晨之後，每個日期檔就是「前一天結束時的
                // 完整狀態」。
                .setInitialDelay(minutesUntilNextRun(), TimeUnit.MINUTES)
                .build()
            // KEEP 而不是 UPDATE：每次進設定頁都重排的話，週期會一直從頭算，
            // 常開設定的人反而永遠等不到那一次備份。
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
