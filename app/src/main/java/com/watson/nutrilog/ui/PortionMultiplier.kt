package com.watson.nutrilog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watson.nutrilog.R
import com.watson.nutrilog.ui.theme.numeric
import com.watson.nutrilog.ui.theme.NutrientColors
import kotlin.math.roundToInt

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
 */
@Composable
fun PortionMultiplierBar(
    multiplier: Double,
    onMultiplierChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val keySize: Dp = if (compact) 34.dp else 40.dp

    fun applyStep(delta: Double) {
        val next = (multiplier + delta).coerceIn(0.1, 10.0)
        // 浮點累加會跑出 1.7000000000000002 這種值，每一步都收斂到一位小數
        onMultiplierChange((next * 10).roundToInt() / 10.0)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionLabel(stringResource(R.string.portion_multiplier_label), Modifier.padding(end = 2.dp))
        StepKey("−1", keySize) { applyStep(-1.0) }
        StepKey("−0.1", keySize) { applyStep(-0.1) }

        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    formatMultiplierValue(multiplier),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = if (compact) 20.sp else 24.sp,
                    ).numeric(),
                    // 不是 1 份就上朱紅：這是「你動過它」的提示，跟聚焦同色
                    color = if (multiplier != 1.0) NutrientColors.Accent else scheme.onSurface,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    "份",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }

        StepKey("+0.1", keySize) { applyStep(0.1) }
        StepKey("+1", keySize) { applyStep(1.0) }
    }
}

@Composable
private fun StepKey(text: String, size: Dp, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(scheme.surfaceContainerLow)
            .border(1.5.dp, NutrientColors.FieldBorder, CircleShape)
            .clickable(onClick = onClick),
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

private fun formatMultiplierValue(multiplier: Double): String {
    val rounded = (multiplier * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}
