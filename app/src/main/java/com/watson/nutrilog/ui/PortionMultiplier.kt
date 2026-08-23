package com.watson.nutrilog.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watson.nutrilog.R
import com.watson.nutrilog.ui.theme.NumberFontFamily
import com.watson.nutrilog.ui.theme.NutrientColors
import kotlin.math.roundToInt

private val QUICK_MULTIPLIERS = listOf(0.5, 1.0, 1.5, 2.0)

/**
 * 份數縮放控制元件（樣式 C：快捷膠囊排 ＋ 步進微調）。
 *
 * 契合 NutriLog「紙與墨」設計語言：
 * - 選中態：墨綠實色底（[NutrientColors.Calories]），白色高亮文字。
 * - 未選態：淡紙色容器底，細線外框。
 * - 右側步進微調器以 0.1 為單位加減，支援精細小數調整。
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

    // 格式化倍數顯示，如 1.0 -> "1x", 1.5 -> "1.5x"
    val formattedMultiplier = formatMultiplierText(multiplier)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // 左側：快捷膠囊列
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
            modifier = Modifier.weight(1f, fill = false),
        ) {
            QUICK_MULTIPLIERS.forEach { targetMult ->
                val isSelected = (multiplier * 10).roundToInt() == (targetMult * 10).roundToInt()
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) activeColor else inactiveBg,
                    animationSpec = tween(150),
                    label = "pillBg",
                )
                val textColor = if (isSelected) scheme.surface else scheme.onSurface

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(bgColor)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) activeColor else borderColor,
                            shape = RoundedCornerShape(8.dp),
                        )
                        .clickable { onMultiplierChange(targetMult) }
                        .padding(
                            horizontal = if (compact) 8.dp else 10.dp,
                            vertical = if (compact) 5.dp else 6.dp,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (targetMult % 1.0 == 0.0) "${targetMult.toInt()}x" else "${targetMult}x",
                        fontSize = if (compact) 12.sp else 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = NumberFontFamily,
                        color = textColor,
                    )
                }
            }
        }

        // 右側：步進微調器
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(inactiveBg)
                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                .padding(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // 減少按鈕 (-)
            Box(
                modifier = Modifier
                    .size(if (compact) 26.dp else 30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(scheme.surface)
                    .border(1.dp, borderColor, RoundedCornerShape(6.dp))
                    .clickable {
                        val next = (multiplier - 0.1).coerceIn(0.1, 5.0)
                        onMultiplierChange((next * 10).roundToInt() / 10.0)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "−",
                    fontSize = if (compact) 14.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                )
            }

            // 目前倍數數值
            Box(
                modifier = Modifier.padding(horizontal = if (compact) 6.dp else 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = formattedMultiplier,
                    fontSize = if (compact) 12.sp else 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = NumberFontFamily,
                    color = if (multiplier != 1.0) activeColor else scheme.onSurface,
                )
            }

            // 增加按鈕 (+)
            Box(
                modifier = Modifier
                    .size(if (compact) 26.dp else 30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(scheme.surface)
                    .border(1.dp, borderColor, RoundedCornerShape(6.dp))
                    .clickable {
                        val next = (multiplier + 0.1).coerceIn(0.1, 5.0)
                        onMultiplierChange((next * 10).roundToInt() / 10.0)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+",
                    fontSize = if (compact) 14.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                )
            }
        }
    }
}

private fun formatMultiplierText(multiplier: Double): String {
    val rounded = (multiplier * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) "${rounded.toInt()}x" else "${rounded}x"
}
