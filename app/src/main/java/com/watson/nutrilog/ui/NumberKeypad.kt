package com.watson.nutrilog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watson.nutrilog.R
import com.watson.nutrilog.ui.theme.numeric

/**
 * 自己畫的數字鍵盤。
 *
 * 只有十二顆鍵，所以比系統鍵盤矮得多 —— 表單本身還看得見，
 * 填完一格直接點下一格，不必為了看清楚而先收鍵盤。
 */
@Composable
internal fun NumberKeypad(label: String, onKey: (String) -> Unit, onDone: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .background(scheme.surfaceVariant)
            .navigationBarsPadding()
    ) {
        // 鍵盤是浮在表單上的另一層，用 2px 重規線把它跟表單切開，
        // 不是用細線 —— 細線在這裡看起來像表單自己的一列。
        Rule()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 9.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.keypad_editing, label),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            PillButton(stringResource(R.string.keypad_done), onClick = onDone)
        }
        // 圓的，因為整頁其他東西都是方的與線性的 —— 一片全是方格的鍵盤在紙感
        // 版面上會像試算表。三級：數字有圈、小數點圈變淡（還能按，但不是主角）、
        // 刪除完全沒有圈，不看標籤也分得出哪個是破壞性的。
        Column(
            Modifier.padding(start = 22.dp, end = 22.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KEYS.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { key ->
                        RoundKey(
                            onClick = { onKey(key) },
                            modifier = Modifier.weight(1f),
                            ringed = key != BACKSPACE,
                            dimmed = key == ".",
                        ) {
                            if (key == BACKSPACE) {
                                BackspaceMark(scheme.onSurfaceVariant)
                            } else {
                                Text(
                                    key,
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontSize = 28.sp,
                                    ).numeric(),
                                    color = if (key == ".") scheme.onSurfaceVariant else scheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 把一次按鍵套到目前的字串上。
 *
 * 這裡刻意還是操作**字串**而不是數字：打到一半的 "12." 不是合法的 Double，
 * 每次按鍵都轉一次會把中間狀態吃掉。真正的解析留到儲存那一刻。
 */
internal fun applyKey(current: String, key: String, fresh: Boolean): String {
    if (key == BACKSPACE) return current.dropLast(1)
    val base = if (fresh) "" else current
    return when {
        key == "." -> if (base.contains('.')) base else if (base.isEmpty()) "0." else base + "."
        // 開頭的 0 沒有意義，除非後面接小數點
        base == "0" -> key
        else -> base + key
    }
}

private const val BACKSPACE = "⌫"
private val KEYS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", BACKSPACE)
