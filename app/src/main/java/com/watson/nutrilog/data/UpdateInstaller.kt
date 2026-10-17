package com.watson.nutrilog.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.IntentCompat
import com.watson.nutrilog.data.net.SharedHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest

/**
 * 在 app 裡把新版裝起來：下載 APK、寫進 PackageInstaller 的 session、核對 SHA-256、commit。
 *
 * 一般 app 不能自己安靜地裝東西：commit 之後系統回的是一個「要使用者確認」的畫面
 * （[InstallResult.NeedsConfirmation]），按下「更新」才真的裝。這一步省不掉。
 *
 * **邊下載邊寫進 session，不落地成檔案。** 沒有暫存檔就沒有「什麼時候刪」的問題，也不必
 * 經過 FileProvider。代價是在確認畫面按了取消，再按一次就得重新下載 —— 少數情況，
 * 換掉一整套暫存檔管理划算。
 *
 * SHA-256 不是唯一的防線：簽章跟手機上那顆不同的 APK，系統本來就會拒裝。這裡擋的是
 * 下載到一半壞掉的檔案 —— 那種會在使用者按了「更新」之後才失敗，訊息也看不懂。
 */
class UpdateInstaller(private val context: Context, private val client: OkHttpClient = SharedHttp.client) {

    /** [onProgress] 收的是已經下載的 bytes。成功只代表交給系統了，結果從 [InstallResults] 回來。 */
    suspend fun install(apkUrl: String, size: Long, sha256: String?, onProgress: (Long) -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val installer = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                    // 只准裝成自己的更新：萬一網址指到別的 app，系統會在這裡就擋掉
                    .apply { setAppPackageName(context.packageName) }
                val sessionId = installer.createSession(params)
                val session = installer.openSession(sessionId)
                try {
                    val digest = MessageDigest.getInstance("SHA-256")
                    client.newCall(Request.Builder().url(apkUrl).build()).execute().use { response ->
                        if (!response.isSuccessful) throw IOException("GitHub " + response.code)
                        val input = response.body?.byteStream() ?: throw IOException("empty body")
                        session.openWrite("base.apk", 0, size).use { out ->
                            val buffer = ByteArray(64 * 1024)
                            var written = 0L
                            var reported = 0L
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val n = input.read(buffer)
                                if (n < 0) break
                                out.write(buffer, 0, n)
                                digest.update(buffer, 0, n)
                                written += n
                                // 網路一次只給幾 KB，每次都回報等於整段畫面重組上千次
                                if (written - reported >= PROGRESS_STEP) {
                                    reported = written
                                    onProgress(written)
                                }
                            }
                            onProgress(written)
                            session.fsync(out)
                        }
                    }
                    if (sha256 != null && digest.digest().toHex() != sha256) throw CorruptDownload()
                    session.commit(resultIntent(sessionId).intentSender)
                    session.close()
                } catch (e: Throwable) {
                    session.abandon()
                    throw e
                }
            }
        }

    private fun resultIntent(sessionId: Int): PendingIntent {
        val intent = Intent(context, InstallResultReceiver::class.java)
        // 要 MUTABLE：系統得往裡面填 EXTRA_STATUS 與確認畫面的 intent。31 以前沒有這個旗標
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(context, sessionId, intent, PendingIntent.FLAG_UPDATE_CURRENT or mutable)
    }

    /** 下載下來的內容和 GitHub 給的 SHA-256 對不上。 */
    class CorruptDownload : IOException("APK digest mismatch")

    private companion object {
        const val PROGRESS_STEP = 128 * 1024L
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

/** 系統裝完（或沒裝）之後回來的結果。 */
sealed interface InstallResult {
    /** 系統要使用者按「更新」。這個 intent 要從 Activity 開，ViewModel 開不了（見 App.kt）。 */
    data class NeedsConfirmation(val intent: Intent) : InstallResult
    /** 在確認畫面按了取消。不是錯誤，不要講成失敗。 */
    data object Cancelled : InstallResult
    /** [status] 是 PackageInstaller.STATUS_FAILURE_*。 */
    data class Failed(val status: Int) : InstallResult
}

/**
 * 接收端和 ViewModel 之間的那條線。接收端是系統叫起來的，拿不到 ViewModel；
 * ViewModel 不在（Activity 已經關掉）的話這個結果就掉了，使用者回來再按一次就好。
 */
object InstallResults {
    private val flow = MutableSharedFlow<InstallResult>(extraBufferCapacity = 1)
    val events: SharedFlow<InstallResult> = flow
    fun emit(result: InstallResult) { flow.tryEmit(result) }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION ->
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                    ?.let { InstallResult.NeedsConfirmation(it) }
                    ?: InstallResult.Failed(status)
            // 裝好了的話舊的行程已經被換掉，這裡是新版被叫起來，沒有東西要做
            PackageInstaller.STATUS_SUCCESS -> return
            PackageInstaller.STATUS_FAILURE_ABORTED -> InstallResult.Cancelled
            else -> InstallResult.Failed(status)
        }
        InstallResults.emit(result)
    }
}
