package com.watson.nutrilog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watson.nutrilog.R
import com.watson.nutrilog.ui.theme.numeric
import com.watson.nutrilog.ui.theme.NutrientColors

internal const val MAX_PORTION_MULTIPLIER = 99.0

/**
 * 收斂到 0.01～99、兩位小數。步進與手動輸入共用，兩邊的上下限才不會漂。
 *
 * 兩位小數是為了 0.25、0.75 這種四分之一份；下限跟著是 0.01，打得進去的值
 * 離開格子時就不會再被改掉。
 */
internal fun clampPortion(value: Double): Double =
    value.coerceIn(0.01, MAX_PORTION_MULTIPLIER).roundTo2()

/**
 * 份數格收不收這一下按鍵：整數最多兩位、小數最多兩位。超過的那一下直接無效，
 * 不讓人先看到 150 再在離開時跳回 99 —— 那讀起來像是數字自己變了。
 */
internal fun acceptsPortionText(text: String): Boolean = PORTION_TEXT.matches(text)

private val PORTION_TEXT = Regex("""\d{0,2}(\.\d{0,2})?""")

/**
 * 離開份數格時的倍率。空白（或只剩小數點）等於沒填，維持原本的倍率；
 * 0 收到下限 0.01。
 */
internal fun typedPortion(text: String, current: Double): Double =
    text.toDoubleOrNull()?.let(::clampPortion) ?: current

/**
 * 份數縮放：雙速步進（±1 與 ±0.1），中間顯示目前份數。
 *
 * 樣式跟自製鍵盤同一套 —— 步進鍵是**圓章**，因為它們就是數字鍵的近親（按下去
 * 改的是數字）。前一版是五個圓角方塊裝在一個有底色的容器裡，那讓它看起來像
 * 一個獨立的小工具列，而不是這張表單的一部分。
 *
 * 為什麼是步進而不是 0.5／1／1.5／2 四個預設：預設值只能涵蓋整齊的倍率，
 * 但「一碗半再多一點」這種實際吃法落不進去，而且四個預設一字排開看起來像
 * 四顆獨立的鈕，不像一個開關的四個檔位。
 *
 * 中間那格也能直接打字：點下去跳出自製數字鍵盤，[typing] 是正在打的字。
 * 打字時整張表單的數字不動，**離開這格才換算**（由呼叫端決定什麼叫離開）。
 */
@Composable
fun PortionMultiplierBar(
    multiplier: Double,
    onMultiplierChange: (Double) -> Unit,
    /** 正在用數字鍵盤打的字；null＝沒在打。 */
    typing: String?,
    onTapValue: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    /** false＝整排變淡、點不動（AI 確認畫面裡沒勾選的那一項）。 */
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val keySize: Dp = if (compact) 34.dp else 40.dp

    fun applyStep(delta: Double) {
        // 正在打字時按步進鍵，以打到一半的那個數字為準再加減 —— 打了 2 再按 +1
        // 要得到 3，而不是拿舊的倍率去加。浮點累加會跑出 1.7000000000000002，
        // clampPortion 每一步都收斂到兩位小數。
        val base = typing?.let { typedPortion(it, multiplier) } ?: multiplier
        onMultiplierChange(clampPortion(base + delta))
    }

    Row(
        // 取消勾選時整排是慢慢淡下去的，跟名稱變淡同一個節奏
        modifier = modifier.fillMaxWidth().alpha(feedbackFloat(if (enabled) 1f else 0.38f, "portionEnabled")),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionLabel(stringResource(R.string.portion_multiplier_label), Modifier.padding(end = 2.dp))
        StepKey("−1", keySize, enabled) { applyStep(-1.0) }
        StepKey("−0.1", keySize, enabled) { applyStep(-0.1) }

        // 外層只負責吃掉剩下的寬度、把格子擺正中間，本身沒有底色 —— 格子兩側露出的是
        // 紙的米色。以前整段剩餘寬度都是格子，寬螢幕上是一大塊白框。
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            // 格子本身跟編輯表單的數字格同一套：外框 ＋ 比其他三邊重的底規線，
            // 正在填時外框轉墨色、底線轉朱紅。
            val active = typing != null
            Box(
                Modifier
                    .clip(NutriFieldShape)
                    .background(feedbackColor(if (active) scheme.surfaceContainerLowest else scheme.surfaceContainerLow, "portionFill"))
                    .border(1.dp, feedbackColor(if (active) scheme.onSurface else NutrientColors.FieldBorder, "portionBorder"), NutriFieldShape)
                    .clickable(enabled = enabled, onClick = onTapValue),
            ) {
                Box(
                    Modifier.padding(horizontal = 8.dp, vertical = if (compact) 3.dp else 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // **寬度固定在「99.99 份」**（最寬的值）：用那串字本身撐出寬度，再把真正的
                    // 數字疊在正中間。寬度跟著字型與系統字體大小一起縮放，不寫死 dp；
                    // 打字時也不會一個字一個字地變寬。它看不見，也不能讓讀螢幕的人聽見。
                    Box(Modifier.clearAndSetSemantics { }) {
                        PortionValue("99.99", compact, Color.Transparent, Color.Transparent)
                    }
                    val shown = typing ?: formatMultiplierValue(multiplier)
                    PortionValue(
                        // 打到全部刪光時顯示破折號，跟其他數字格一樣
                        shown.ifEmpty { "—" },
                        compact,
                        // 正在填、或不是 1 份就上朱紅：後者是「你動過它」的提示，跟聚焦同色
                        numberColor = feedbackColor(
                            when {
                                shown.isEmpty() -> scheme.outline.copy(alpha = 0.6f)
                                active || multiplier != 1.0 -> NutrientColors.Accent
                                else -> scheme.onSurface
                            },
                            "portionValue",
                        ),
                        unitColor = scheme.onSurfaceVariant,
                    )
                }
                // 底規線放在跟格子一樣大的那一層裡，才會貼齊外框、又不會把格子撐寬
                Box(Modifier.matchParentSize(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(feedbackDp(if (active) 3.dp else 2.dp, "portionRuleHeight"))
                            .background(feedbackColor(if (active) NutrientColors.Accent else scheme.onSurface, "portionRule"))
                    )
                }
            }
        }

        StepKey("+0.1", keySize, enabled) { applyStep(0.1) }
        StepKey("+1", keySize, enabled) { applyStep(1.0) }
    }
}

/** 份數格裡的「數字 ＋ 份」。**不換行**：擠的時候寧可被裁掉，也不要整排忽然變高。 */
@Composable
private fun PortionValue(number: String, compact: Boolean, numberColor: Color, unitColor: Color) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            number,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = if (compact) 20.sp else 24.sp,
            ).numeric(),
            color = numberColor,
            softWrap = false,
            maxLines = 1,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            "份",
            style = MaterialTheme.typography.bodySmall,
            color = unitColor,
            softWrap = false,
            maxLines = 1,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

@Composable
private fun StepKey(text: String, size: Dp, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(scheme.surfaceContainerLow)
            .border(1.5.dp, NutrientColors.FieldBorder, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall.copy(
                // ±1 只有兩個字元，±0.1 有四個，同一個字級會讓後者撐爆圓圈
                fontSize = if (text.length > 2) 11.sp else 14.sp,
            ).numeric(),
            color = scheme.onSurface,
        )
    }
}

internal fun formatMultiplierValue(multiplier: Double): String = multiplier.roundTo2().asInputValue()
