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
import com.watson.nutrilog.data.UpdateChecker
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 每天到 GitHub 查一次有沒有新版。查到了只把結果存起來，不發通知 ——
 * 下次打開 app 時，今日頁右上角的設定圖示會亮一個紅點。
 *
 * **和 [BackupWorker] 是兩個獨立的排程**：備份要先連結 Drive 才會排，
 * 檢查更新不該被這件事綁住，沒用雲端備份的人也要知道有新版。
 *
 * 不像備份那樣對齊凌晨：備份要對齊是因為檔名是日期、內容要是「一整天」；
 * 這裡哪個時間查都一樣，排程當下先跑一次反而剛好（更新完第一次開 app 就知道有沒有更新的）。
 */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result =
        UpdateChecker(applicationContext).check().fold(
            onSuccess = { Result.success() },
            onFailure = { cause ->
                // 只有連線問題值得重試（沒網路、逾時），那會自己好。被限流或 GitHub 回錯的話
                // 重試只會再撞一次牆，明天照常再查就好。
                if (cause is IOException) {
                    Log.w(TAG, "檢查更新失敗，稍後重試", cause)
                    Result.retry()
                } else {
                    Log.w(TAG, "檢查更新失敗，明天再查", cause)
                    Result.success()
                }
            },
        )

    companion object {
        private const val TAG = "UpdateCheckWorker"
        private const val WORK_NAME = "github-update-check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            // KEEP：每次開 app 都會呼叫一次（排程可能被「強制停止」清掉，見 BackupWorker），
            // 已經排好的就別動，不然週期會一直從頭算、還會每次開 app 都多查一次。
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
