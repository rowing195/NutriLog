package com.watson.nutrilog.data.net

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * 把相片壓成可以塞進 API 請求的 base64 JPEG。
 *
 * 手機原圖動輒 12 MP，base64 之後是 4 MB 起跳的請求 —— 上傳慢、費用高，
 * 而且對辨識準確度毫無幫助：模型看的是「盤子裡有什麼」，不是毛孔。
 * 縮到長邊 1024 px 已經遠超過辨識所需。
 *
 * 這裡不處理 EXIF 旋轉。食物辨識對方向不敏感，為了它多拉一個
 * exifinterface 依賴不划算。
 */
object ImageCompressor {

    private const val MAX_EDGE = 1024
    private const val QUALITY = 85

    /**
     * 照片的上限。縮圖解碼的記憶體有上限，**時間卻沒有**：解碼器還是得把整個檔案讀完，
     * 一張只有幾 MB、宣稱十萬乘十萬畫素的「解壓縮炸彈」會讓它跑上好幾分鐘。
     * 2.5 億畫素蓋得住手機最大的 2 億畫素照片（16320×12240）。
     */
    const val MAX_PIXELS = 250_000_000L

    /** 以 1 MB＝1,000,000 bytes 計，和 Android 相簿顯示的檔案大小同一種算法。 */
    const val MAX_BYTES = 30_000_000L

    /** 照片為什麼不能送。沒有問題時 [check] 回 null。 */
    sealed interface Problem {
        data object Unreadable : Problem
        data class TooManyPixels(val pixels: Long) : Problem
        data class TooManyBytes(val bytes: Long) : Problem
    }

    /**
     * 送出之前的檢查。**只查檔案大小、只讀檔頭拿尺寸，不把整張圖解出來**，
     * 所以不管檔案多大、宣稱多大，都是瞬間完成。
     */
    fun check(context: Context, uri: Uri): Problem? {
        val bytes = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
            }
        }.getOrNull()
        val bounds = runCatching { readBounds(context, uri) }.getOrNull()
        return classify(bytes, bounds?.outWidth ?: -1, bounds?.outHeight ?: -1)
    }

    /** [check] 的判斷本身。讀不到檔案大小（null）時只看畫素。 */
    fun classify(bytes: Long?, width: Int, height: Int): Problem? = when {
        bytes != null && bytes > MAX_BYTES -> Problem.TooManyBytes(bytes)
        width <= 0 || height <= 0 -> Problem.Unreadable
        width.toLong() * height > MAX_PIXELS -> Problem.TooManyPixels(width.toLong() * height)
        else -> null
    }

    fun toBase64Jpeg(context: Context, uri: Uri): Result<String> = runCatching {
        val decoded = decodeSampled(context, uri)

        // inSampleSize 只能是 2 的次方，所以還會偏大一點，這裡再精準縮一次
        val scaled = scaleToMaxEdge(decoded)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()

        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * 確認畫面的預覽圖，一樣縮到長邊 1024～2047 px 再解碼。
     *
     * 原本是 `ImageView.setImageURI`，它照原尺寸解碼：50 MP 的照片是 200 MB 的點陣圖，
     * 超過 Android 一次能畫的上限（約 100 MB），選完照片就直接閃退 —— 使用者回報的
     * 「20 MB 的圖會閃退」就是這個，還沒走到上傳。
     *
     * API 28+ 走 ImageDecoder 是為了照 EXIF 轉正：setImageURI 在這些版本上本來就會轉，
     * 直拍的照片不能因為換了解碼方式就躺下來。舊機器退回 BitmapFactory，
     * setImageURI 在那些版本上也不轉，行為不變。讀不出來就回 null，確認頁據此擋下送出。
     */
    fun decodePreview(context: Context, uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.setTargetSampleSize(sampleSizeFor(info.size.width, info.size.height))
            }
        } else {
            decodeSampled(context, uri)
        }
    }.getOrNull()

    private fun readBounds(context: Context, uri: Uri): BitmapFactory.Options {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        return bounds
    }

    private fun decodeSampled(context: Context, uri: Uri): Bitmap {
        // 先只讀尺寸。直接 decode 一張 12 MP 的圖到記憶體再縮，很容易 OOM。
        val bounds = readBounds(context, uri)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("無法讀取圖片")

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        return context.contentResolver.openInputStream(uri)
            .use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("無法解碼圖片")
    }

    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        while (max(width, height) / (sample * 2) >= MAX_EDGE) sample *= 2
        return sample
    }

    private fun scaleToMaxEdge(source: Bitmap): Bitmap {
        val longest = max(source.width, source.height)
        if (longest <= MAX_EDGE) return source
        val ratio = MAX_EDGE.toFloat() / longest
        return Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }
}
