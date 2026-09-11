package com.watson.nutrilog.data

import android.content.Context
import com.watson.nutrilog.data.db.NutriDatabase
import com.watson.nutrilog.data.net.DriveClient
import okhttp3.MediaType.Companion.toMediaType
import java.time.LocalDate

/**
 * 把飲食紀錄備份到 Drive，以及把最新的一份讀回來。
 *
 * 備份的內容就是**本地匯出的同一份 CSV**（[CsvExport]），不是另一種雲端專用格式。
 * 這是刻意的：Drive 上那個檔案可以直接下載、用試算表打開、或者用本地匯入讀回來，
 * 就算這個 app 哪天不在了，資料也不會被鎖在裡面。還原走的也是同一支
 * [CsvImport]，連去重都是同一套邏輯。
 *
 * 一天一個日期檔、只留最近 [KEEP_DAYS] 天。固定一個檔覆蓋的話，誤刪紀錄而沒發現時，
 * 隔天的備份就把唯一那份蓋掉了；全部留著則是一年 365 個檔，資料夾會變得沒法看。
 */
class DriveBackup(
    context: Context,
    private val auth: DriveAuth = DriveAuth(context),
    private val drive: DriveClient = DriveClient(),
) {

    private val appContext = context.applicationContext
    private val dao = NutriDatabase.get(context).dao()
    private val settingsStore = SettingsStore(context)

    /** 需要使用者同意時回傳這個，呼叫端（Activity）負責把同意畫面叫出來。 */
    class NeedsConsent(val pendingIntent: android.app.PendingIntent) : Exception("需要 Google 授權")

    /**
     * 跑一次備份。回傳備份的日期（不是檔名 —— 呼叫端要顯示的是「備份到哪一天」，
     * 整串 nutrilog-2026-08-30.csv 塞進訊息裡只會換行）。
     *
     * 順手把帳號 email 與時間記進設定 —— 設定頁要講「備份到哪個帳號、上次是什麼時候」，
     * 少了這兩個，使用者沒辦法確認備份到底有沒有在動。
     */
    suspend fun backupNow(token: String? = null): Result<String> = runCatching {
        val accessToken = token ?: requireToken()
        val folderId = drive.ensureFolder(accessToken, FOLDER_NAME).getOrThrow()
        val today = LocalDate.now()
        val name = CsvExport.fileName(today)
        val csv = CsvExport.build(dao.allEntries())
        drive.upload(accessToken, folderId, name, csv).getOrThrow()

        // 目標與身型另外一份。一天一份、和 CSV 一樣留 30 天：只存一份的話，只要被覆蓋一次
        // （例如新手機還沒還原就先備份），雲端那份就回不來了。
        val profile = BackedUpProfile.from(settingsStore.current())
        if (profile != BackedUpProfile.DEFAULT) {
            drive.upload(
                accessToken, folderId, BackedUpProfile.fileName(today), profile.toJson(), JSON_MEDIA,
            ).getOrThrow()
        }

        prune(accessToken, folderId)

        val email = drive.accountEmail(accessToken).getOrNull().orEmpty()
        val current = settingsStore.current()
        settingsStore.save(
            current.copy(
                driveAccount = email.ifBlank { current.driveAccount },
                lastBackupAt = System.currentTimeMillis(),
            )
        )
        today.toString()
    }

    /** 雲端最新那一份的內容。沒有任何備份時回傳 null。 */
    suspend fun latestBackupCsv(token: String? = null): Result<String?> = runCatching {
        val accessToken = token ?: requireToken()
        val folderId = drive.ensureFolder(accessToken, FOLDER_NAME).getOrThrow()
        val newest = drive.list(accessToken, folderId).getOrThrow()
            .filter { it.name.endsWith(".csv") }
            .maxByOrNull { it.name }
            ?: return@runCatching null
        drive.download(accessToken, newest.id).getOrThrow()
    }

    /** 雲端最新那一份目標與身型。沒有備份過、或檔案讀不懂時回傳 null。 */
    suspend fun latestBackupProfile(token: String? = null): Result<BackedUpProfile?> = runCatching {
        val accessToken = token ?: requireToken()
        val folderId = drive.ensureFolder(accessToken, FOLDER_NAME).getOrThrow()
        val newest = drive.list(accessToken, folderId).getOrThrow()
            .filter { it.name.matches(PROFILE_NAME) }
            .maxByOrNull { it.name }
            ?: return@runCatching null
        BackedUpProfile.fromJson(drive.download(accessToken, newest.id).getOrThrow())
    }

    /** 已經授權過就直接拿權杖；還沒的話丟 [NeedsConsent] 讓 UI 去問。 */
    private suspend fun requireToken(): String =
        when (val outcome = auth.authorize().getOrThrow()) {
            is DriveAuth.Outcome.Token -> outcome.accessToken
            is DriveAuth.Outcome.NeedsConsent -> throw NeedsConsent(outcome.pendingIntent)
        }

    private suspend fun prune(token: String, folderId: String) {
        val files = drive.list(token, folderId).getOrNull() ?: return
        val names = files.map { it.name }
        // 兩種檔各自算 30 天，混在一起算的話一天兩個檔，實際只會留 15 天。
        val doomed = (namesToPrune(names, KEEP_DAYS) + namesToPrune(names, KEEP_DAYS, PROFILE_NAME)).toSet()
        files.filter { it.name in doomed }.forEach { drive.delete(token, it.id) }
    }

    companion object {
        const val FOLDER_NAME = "NutriLog"
        const val KEEP_DAYS = 30

        /**
         * 超過保留天數、該刪掉的檔名。
         *
         * 只認得自己產生的那種檔名（nutrilog-YYYY-MM-DD.csv）—— 使用者自己丟進
         * 這個資料夾的東西一律不碰。檔名的日期是固定寬度的，所以字串排序就是日期排序，
         * 不必真的去 parse 日期。
         */
        fun namesToPrune(names: List<String>, keep: Int, pattern: Regex = BACKUP_NAME): List<String> =
            names.filter { it.matches(pattern) }
                .sortedDescending()
                .drop(keep)

        private val BACKUP_NAME = Regex("""nutrilog-\d{4}-\d{2}-\d{2}\.csv""")

        /** 目標與身型那份的檔名，見 [BackedUpProfile.fileName]。 */
        val PROFILE_NAME = Regex("""nutrilog-profile-\d{4}-\d{2}-\d{2}\.json""")

        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
