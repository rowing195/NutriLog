package com.watson.nutrilog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.watson.nutrilog.R
import com.watson.nutrilog.data.AiProvider
import com.watson.nutrilog.data.NutriSettings

/**
 * 辨識失敗時從底下升上來的那張面板：換一家、換個模型，再按重試。
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
    settings: NutriSettings,
    /** 這次失敗的是拍照還是文字描述。決定改哪一組設定，也決定標題怎麼寫。 */
    isPhoto: Boolean,
    onChange: (NutriSettings) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val scheme = MaterialTheme.colorScheme
    val provider = if (isPhoto) settings.photoProvider else settings.textProvider
    // 第一次組成就往 true 跑，面板才會「升上來」而不是憑空出現。
    // 收起來沒有退場動畫：那要父層留著它等動畫播完，而這張面板收起來的下一步
    // 不是重試就是回到失敗畫面，多留那 200ms 只會擋著使用者。
    val shown = remember { MutableTransitionState(false) }.apply { targetState = true }

    Box(Modifier.fillMaxSize()) {
        // 壓暗用 scrim：它兩個主題都是墨色，永遠是壓暗。inverseSurface 在深色模式下
        // 是亮的，拿來當遮罩會把背景刷亮。
        AnimatedVisibility(shown, enter = fadeIn(tween(200))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(scheme.scrim.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    )
            )
        }
        AnimatedVisibility(
            shown,
            enter = slideInVertically(tween(280)) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
        Column(
            Modifier
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
                stringResource(R.string.switch_provider_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(
                    if (isPhoto) R.string.switch_provider_photo_help
                    else R.string.switch_provider_text_help
                ),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            BallotRow(
                labels = AiProvider.entries.map { it.label },
                selectedIndex = AiProvider.entries.indexOf(provider),
                onSelect = { index ->
                    val picked = AiProvider.entries[index]
                    onChange(
                        if (isPhoto) settings.copy(photoProvider = picked)
                        else settings.copy(textProvider = picked)
                    )
                },
            )
            Hairline(Modifier.padding(vertical = 12.dp))

            // 換供應商時底下這塊整個滑過去：兩家的模型是兩份不同的東西
            // （Gemini 是幾個固定型號，OpenRouter 是自由文字），淡入淡出會讓人以為
            // 是同一份清單在換內容。方向跟著圈選的左右位置 —— 右邊那家從右邊進來。
            AnimatedContent(
                targetState = provider,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val width = { full: Int -> if (forward) full else -full }
                    (slideInHorizontally(tween(260)) { width(it) } + fadeIn(tween(160)))
                        .togetherWith(
                            slideOutHorizontally(tween(260)) { -width(it) } + fadeOut(tween(160))
                        )
                        .using(SizeTransform(clip = false))
                },
                label = "provider-models",
                modifier = Modifier.weight(1f),
            ) { current ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    when (current) {
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
                label = stringResource(R.string.retry),
                onClick = onRetry,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
        }
    }
}
