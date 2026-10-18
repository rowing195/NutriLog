package com.watson.nutrilog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.watson.nutrilog.R
import com.watson.nutrilog.data.AiProvider
import com.watson.nutrilog.data.NutriSettings
import kotlinx.coroutines.launch

/**
 * 辨識失敗時從底下升上來的那張面板：換一家、換個模型，再按重試。
 *
 * 送出之前也打得開（常吃頁與確認照片頁右上角的設定圖示），那時 [onRetry] 是 null：
 * 還沒有東西可以重試，章改成「完成」只把面板收掉，標題與說明也不提「重試」。
 *
 * **為什麼不是把人丟去設定頁**：失敗的當下使用者手上還捏著那張照片或那句描述，
 * 去設定頁改完再走回來要四五步，而且回來時那次辨識早就沒了。這張面板改的就是
 * 設定裡同樣那幾個欄位（同一份資料，不是另一份副本），所以在這裡選完，設定頁
 * 打開看到的就是新的值。
 *
 * **拍照與文字改的是不同的一組**（見 [NutriSettings.photoProvider]）：拍照要吃得下
 * 圖片的模型，文字那邊常用的是純文字模型，共用一組的話在這裡救了拍照就會弄壞文字。
 *
 * 不用 M3 的 ModalBottomSheet：那是圓角、陰影、Material 底色的成品容器，在這套方角
 * 紙面上一眼就看得出是外來的。這裡就是一塊貼著底的紙，上面一條粗墨線收邊。
 */
@Composable
fun ProviderSwitchSheet(
    /**
     * 呼叫端要一直把它放在畫面上、只切這個值（同 [NutriOverlay]）：用 `if` 包起來的話，
     * 一關就整個從組合裡拿掉，退場動畫根本沒有機會播。
     */
    visible: Boolean,
    settings: NutriSettings,
    /** 這次失敗的是拍照還是文字描述。決定改哪一組設定，也決定標題怎麼寫。 */
    isPhoto: Boolean,
    onChange: (NutriSettings) -> Unit,
    /** 失敗後打開的才有；送出之前打開的傳 null。 */
    onRetry: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    // 從 false 起跑：失敗畫面一出來就要開的話（第一次組成時 visible 已經是 true），
    // 也要看得到它升上來，而不是憑空出現。
    val shown = remember { MutableTransitionState(false) }.apply { targetState = visible }
    // 遮罩與面板各自動（一個淡、一個滑），但共用這一個進出場：兩個都退完了內容才離開組合。
    AnimatedVisibility(shown, enter = EnterTransition.None, exit = ExitTransition.None) {
    // 顯示時才登記返回鍵：比畫面自己的晚登記，就一定先輪到它（同 NutriOverlay）。
    // 退場途中就交還給畫面，那時再按返回是在操作底下那一頁。
    BackHandler(enabled = visible, onBack = onDismiss)
    val scheme = MaterialTheme.colorScheme
    val provider = if (isPhoto) settings.photoProvider else settings.textProvider
    val pager = rememberPagerState(initialPage = provider.ordinal, pageCount = { AiProvider.entries.size })
    val scope = rememberCoroutineScope()
    val latestSettings = rememberUpdatedState(settings)
    val latestOnChange = rememberUpdatedState(onChange)
    LaunchedEffect(provider) {
        if (pager.currentPage != provider.ordinal) pager.animateScrollToPage(provider.ordinal)
    }
    LaunchedEffect(pager, isPhoto) {
        snapshotFlow { pager.settledPage }.collect { page ->
            val current = latestSettings.value
            val picked = AiProvider.entries[page]
            if (picked != if (isPhoto) current.photoProvider else current.textProvider) {
                latestOnChange.value(if (isPhoto) current.copy(photoProvider = picked) else current.copy(textProvider = picked))
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        // 壓暗用 scrim：它兩個主題都是墨色，永遠是壓暗。inverseSurface 在深色模式下
        // 是亮的，拿來當遮罩會把背景刷亮。
        Box(
            Modifier
                .fillMaxSize()
                .animateEnterExit(enter = fadeIn(tween(200)), exit = fadeOut(tween(200)))
                .background(scheme.scrim.copy(alpha = 0.32f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
        )
        // 關閉就原路退回去：往下滑出、遮罩淡掉，時間和升上來時一樣。
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .animateEnterExit(
                    enter = slideInVertically(tween(280)) { it },
                    exit = slideOutVertically(tween(280)) { it },
                )
                .fillMaxWidth()
                // 六成：底下放得下供應商、模型與那顆章，上面還留得住失敗的原因 ——
                // 那句話才是使用者判斷「該換什麼」的依據，蓋掉它等於要他憑記憶選。
                .fillMaxHeight(0.6f)
                .background(scheme.surfaceContainerLow)
                .dismissKeyboardOnTap()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp),
        ) {
            Rule(Modifier.padding(bottom = 14.dp))
            Text(
                stringResource(
                    if (onRetry != null) R.string.switch_provider_title else R.string.search_settings_title
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(
                    when {
                        onRetry != null && isPhoto -> R.string.switch_provider_photo_help
                        onRetry != null -> R.string.switch_provider_text_help
                        isPhoto -> R.string.search_settings_photo_help
                        else -> R.string.search_settings_text_help
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            BallotRow(
                labels = AiProvider.entries.map { it.label },
                selectedIndex = pager.currentPage,
                onSelect = { index ->
                    scope.launch { pager.animateScrollToPage(index) }
                },
            )
            Hairline(Modifier.padding(vertical = 12.dp))

            // 換供應商時底下這塊整個滑過去：兩家的模型是兩份不同的東西
            // （Gemini 是幾個固定型號，OpenRouter 是自由文字），淡入淡出會讓人以為
            // 是同一份清單在換內容。方向跟著圈選的左右位置 —— 右邊那家從右邊進來。
            HorizontalPager(
                state = pager,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.weight(1f),
            ) { page ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    when (AiProvider.entries[page]) {
                        AiProvider.GEMINI -> ModelField(
                            value = settings.geminiModel,
                            onChange = { onChange(settings.copy(geminiModel = it)) },
                        )
                        AiProvider.OPENROUTER -> OpenRouterModelField(
                            value = if (isPhoto) settings.openRouterPhotoModel else settings.openRouterModel,
                            onChange = { model ->
                                onChange(
                                    if (isPhoto) settings.copy(openRouterPhotoModel = model)
                                    else settings.copy(openRouterModel = model)
                                )
                            },
                            forPhoto = isPhoto,
                        )
                    }
                }
            }

            Rule(Modifier.padding(top = 10.dp, bottom = 12.dp))
            StampButton(
                label = stringResource(if (onRetry != null) R.string.retry else R.string.done),
                enabled = !pager.isScrollInProgress && pager.settledPage == provider.ordinal,
                onClick = onRetry ?: onDismiss,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }
    }
}
