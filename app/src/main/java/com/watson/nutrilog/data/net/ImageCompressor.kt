package com.watson.nutrilog.data.net

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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

    fun toBase64Jpeg(context: Context, uri: Uri): Result<String> = runCatching {
        // 先只讀尺寸。直接 decode 一張 12 MP 的圖到記憶體再縮，很容易 OOM。
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("無法讀取圖片")

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        val decoded = context.contentResolver.openInputStream(uri)
            .use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("無法解碼圖片")

        // inSampleSize 只能是 2 的次方，所以還會偏大一點，這裡再精準縮一次
        val scaled = scaleToMaxEdge(decoded)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()

        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
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
