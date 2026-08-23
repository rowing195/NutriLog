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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watson.nutrilog.ui.theme.NumberFontFamily
import com.watson.nutrilog.ui.theme.NutrientColors
import kotlin.math.roundToInt

/**
 * 份數縮放五段式控制列（樣式 1：大加減 ±1.0 ＋ 小加減 ±0.1，純數字按鍵）。
 *
 * 契合 NutriLog「紙與墨」設計語言：
 * - 五段對稱排版，整合於單一細線容器內。
 * - 左右兩側為純數字加減按鍵：[ −1 ] [ −0.1 ] | 目前倍率 | [ +0.1 ] [ +1 ]
 * - 中間數值區高亮顯示目前份數。
 */
@Composable
fun PortionMultiplierBar(
    multiplier: Double,
    onMultiplierChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val activeColor = NutrientColors.Calories
    val inactiveBg = scheme.surfaceContainerLow
    val borderColor = scheme.outlineVariant

    fun applyStep(delta: Double) {
        val next = (multiplier + delta).coerceIn(0.1, 10.0)
        onMultiplierChange((next * 10).roundToInt() / 10.0)
    }

    val formattedMultiplier = formatMultiplierText(multiplier)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(inactiveBg)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        // 大減 (-1)
        StepButton(
            text = "−1",
            compact = compact,
            modifier = Modifier.weight(1f),
            onClick = { applyStep(-1.0) },
        )

        // 小減 (-0.1)
        StepButton(
            text = "−0.1",
            compact = compact,
            modifier = Modifier.weight(1.1f),
            onClick = { applyStep(-0.1) },
        )

        // 中間倍率顯示
        Box(
            modifier = Modifier
                .weight(1.6f)
                .height(if (compact) 30.dp else 36.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = formattedMultiplier,
                fontSize = if (compact) 13.sp else 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = NumberFontFamily,
                color = if (multiplier != 1.0) activeColor else scheme.onSurface,
            )
        }

        // 小加 (+0.1)
        StepButton(
            text = "+0.1",
            compact = compact,
            modifier = Modifier.weight(1.1f),
            onClick = { applyStep(0.1) },
        )

        // 大加 (+1)
        StepButton(
            text = "+1",
            compact = compact,
            modifier = Modifier.weight(1f),
            onClick = { applyStep(1.0) },
        )
    }
}

@Composable
private fun StepButton(
    text: String,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val borderColor = scheme.outlineVariant

    Box(
        modifier = modifier
            .height(if (compact) 30.dp else 36.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(scheme.surface)
            .border(1.dp, borderColor, RoundedCornerShape(7.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = if (compact) 11.sp else 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = NumberFontFamily,
            color = scheme.onSurface,
        )
    }
}

private fun formatMultiplierText(multiplier: Double): String {
    val rounded = (multiplier * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) "${rounded.toInt()} 份" else "$rounded 份"
}
