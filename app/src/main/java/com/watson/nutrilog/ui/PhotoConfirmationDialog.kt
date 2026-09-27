package com.watson.nutrilog.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.watson.nutrilog.R
import com.watson.nutrilog.data.net.ImageCompressor
import com.watson.nutrilog.ui.theme.NutrientColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.ceil

/**
 * 拍完或選完照片後的確認頁，整頁蓋在今日頁上。
 *
 * 不用系統 `Dialog`（理由同 NutriDialog），改成跟換頁同一張紙：**從上方蓋下來**，
 * 和「記一筆」開出來的其他畫面同一個方向（見 App.kt 的 `coversDownward`）。
 * 取消就原路收回上方；**按送出則只淡掉** —— 接著要換到辨識畫面，那張紙也是從上方
 * 蓋下來的，這一層要是同時往上收，兩個方向相反的動作會疊在一起。
 *
 * 呼叫端要把它放在畫面最上層（App.kt 根部那個 Box 的最後），它才蓋得住整個畫面。
 */
@Composable
fun PhotoConfirmationDialog(visible: Boolean, uri: Uri?, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var sent by remember { mutableStateOf(false) }
    LaunchedEffect(visible) { if (visible) sent = false }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(COVER_MS, easing = CoverEasing)) { -it },
        exit = if (sent) {
            fadeOut(tween(150))
        } else {
            slideOutVertically(tween(COVER_MS, easing = CoverEasing)) { -it }
        },
    ) {
        // 在顯示時才登記返回鍵：比畫面自己的返回鍵晚登記，才會先輪到它
        BackHandler(enabled = visible, onBack = onDismiss)
        uri?.let {
            PhotoConfirmationPage(
                uri = it,
                onDismiss = onDismiss,
                onConfirm = { note ->
                    sent = true
                    onConfirm(note)
                },
            )
        }
    }
}

@Composable
private fun PhotoConfirmationPage(uri: Uri, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var note by rememberSaveable(uri.toString()) { mutableStateOf("") }
    val context = LocalContext.current
    Scaffold(
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.photo_confirm_title),
                closeLabel = stringResource(R.string.cancel),
                onClose = onDismiss,
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).imePadding()
                .dismissKeyboardOnTap()
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 縮圖後才畫，而且在背景執行緒解碼：原本的 ImageView.setImageURI 照原尺寸
            // 解碼，50 MP 的照片會超過 Android 能畫的上限而閃退，見 decodePreview。
            // 解碼完之前那一格照樣佔著 240dp，底下的欄位不會跳。
            //
            // 「還在解碼」和「不能送」要分開記：只看 preview 是不是 null 的話，
            // 解碼完成前那一瞬間也會說讀不出來。
            //
            // 先過 ImageCompressor.check（只查大小、讀檔頭，瞬間完成）才解碼：
            // 超過上限的圖連預覽都不解，不然一張解壓縮炸彈會在這裡跑上好幾分鐘。
            var preview by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
            var problem by remember(uri) { mutableStateOf<ImageCompressor.Problem?>(null) }
            LaunchedEffect(uri) {
                problem = withContext(Dispatchers.IO) { ImageCompressor.check(context, uri) }
                if (problem != null) return@LaunchedEffect
                val bitmap = withContext(Dispatchers.IO) { ImageCompressor.decodePreview(context, uri) }
                if (bitmap == null) problem = ImageCompressor.Problem.Unreadable
                else preview = bitmap.asImageBitmap()
            }
            val blocked = problem != null
            Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                // 解碼完才淡入，不要在空了一瞬間之後硬跳出來
                androidx.compose.animation.AnimatedVisibility(visible = preview != null, enter = fadeIn(tween(220))) {
                    preview?.let {
                        Image(
                            bitmap = it,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                // 不能送要在這裡就講，不要等按了送出才在失敗頁看到 —— 那一頁還會升起
                // 「換一家再試」的面板，而換供應商救不了一張讀不出來或太大的圖。
                androidx.compose.animation.AnimatedVisibility(visible = problem != null, enter = fadeIn(tween(220))) {
                problem?.let { p ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(
                                if (p is ImageCompressor.Problem.Unreadable) R.string.photo_unreadable
                                else R.string.photo_too_large
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            withNumerals(
                                when (p) {
                                    ImageCompressor.Problem.Unreadable ->
                                        stringResource(R.string.photo_unreadable_help)
                                    is ImageCompressor.Problem.TooManyPixels -> stringResource(
                                        R.string.photo_too_many_pixels,
                                        hundredMillionsLabel(p.pixels),
                                        hundredMillionsLabel(ImageCompressor.MAX_PIXELS),
                                    )
                                    is ImageCompressor.Problem.TooManyBytes -> stringResource(
                                        R.string.photo_too_many_bytes,
                                        megabytesLabel(p.bytes),
                                        megabytesLabel(ImageCompressor.MAX_BYTES),
                                    )
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                }
            }
            NutriTextField(
                value = note,
                onValueChange = { note = it },
                label = stringResource(R.string.photo_note_label),
                placeholder = stringResource(R.string.photo_note_hint),
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )
            // 送出與返回包成同一個子項：返回鍵是展開出來的，外層 spacedBy 會把
            // 收合時高度 0 的它也算一格間距（見 CLAUDE.md〈打字時底下那一區〉那條）。
            Column {
                // 不能送時送出退成外框章，理由已經寫在上面那格裡，不再用 helper 講一次。
                StampButton(
                    label = stringResource(R.string.photo_confirm_send),
                    onClick = { onConfirm(note) },
                    enabled = !blocked,
                    modifier = Modifier.fillMaxWidth(),
                )
                // 不能送時畫面上就只剩「離開」這件事能做，給一顆搆得到的章，
                // 不要讓人伸手去點左上角的「取消」。灰章＝退一階（見 CLAUDE.md 成對動作那條），
                // 不是灰掉。
                AnimatedVisibility(
                    visible = blocked,
                    enter = expandVertically(tween(220)) + fadeIn(tween(220, delayMillis = 60)),
                ) {
                    StampButton(
                        label = stringResource(R.string.photo_back),
                        onClick = onDismiss,
                        color = NutrientColors.StampSecondary,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    )
                }
            }
        }
    }
}

/**
 * 「3.2」億。**無條件進位到小數一位**：2.51 億照四捨五入會顯示成 2.5，
 * 和上限一樣大，讀起來像是沒超過卻被擋。
 */
internal fun hundredMillionsLabel(pixels: Long): String =
    String.format(Locale.ROOT, "%.1f", ceil(pixels / 10_000_000.0) / 10)

/** MB 一樣無條件進位，理由同上。 */
internal fun megabytesLabel(bytes: Long): Long = ceil(bytes / 1_000_000.0).toLong()
