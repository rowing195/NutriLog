package com.watson.nutrilog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.watson.nutrilog.R
import com.watson.nutrilog.data.db.FoodSuggestion
import java.time.LocalDate

/**
 * 常吃清單 ＋ 文字辨識，合成一個畫面。
 *
 * 這條路的典型情境是「忘記拍照，事後想到才補登」——這時候第一反應通常是
 * 「這不是常吃的那個嗎」，而不是想打字給 AI 猜。所以常吃清單佔畫面主體，
 * 點了就直接帶進表單；真的找不到才往下用文字描述、交給 AI 估算。
 *
 * **兩條路共用同一個輸入框，而且程式內搜尋不是一個動作。** 打字就即時篩常吃／最近
 * （純記憶體，見 [filterByQuery]），找得到直接點那一列；篩到空的時候，底下那顆章
 * 就是出路。
 *
 * 一個框同時服務兩件事，代價是使用者為了讓 AI 估得準會打得很細（「手沖藝妓黑咖啡」），
 * 而那種字串用整串比對必定篩不到自己庫裡的「手沖黑咖啡」。所以篩選是模糊的
 * （見 [matchScore]），近似的也留著並排在後面 —— 這個畫面最不該做的事，
 * 就是在庫裡明明有相近品項時還理直氣壯地叫使用者去問 AI。
 *
 * 刻意不做成上下兩個框、也不做成兩顆同級的按鍵：那兩種都要使用者**在打字之前**
 * 先決定要用哪一種搜尋，而選錯是安靜的 —— 只想篩清單卻送去 AI，等於白花一次
 * API 呼叫和好幾秒；想問 AI 卻打進篩選框，只會看到空清單、像是壞了。一個框則
 * 沒有東西要選：清單自己收斂，收斂到空的那一刻正好就是該問 AI 的時候。
 *
 * 同理，鍵盤上的送出鍵**不送去 AI**，只收鍵盤（清單早就邊打邊篩完了）。
 * AI 要花錢也要等，那條路一定要是明確按下那顆章才走。
 *
 * **打字時底下那一區會自己縮起來。** 鍵盤一開就吃掉半個畫面，這一區原本
 * 三層（標題＋說明＋章）比清單本身還高，實測只剩兩列看得到 —— 而使用者正在看的
 * 就是那個收斂中的清單。所以聚焦時只留那顆章；除非連一筆都篩不到，那時候
 * 「沒有『⋯』？」是畫面上唯一還在講話的東西，要留著。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextLookupScreen(
    targetDate: LocalDate,
    frequent: List<FoodSuggestion>,
    recent: List<FoodSuggestion>,
    onReuseSuggestion: (FoodSuggestion) -> Unit,
    onLookup: (String) -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val submit = { if (query.isNotBlank()) onLookup(query) }

    val shownFrequent = remember(query, frequent) { frequent.filterByQuery(query) }
    val shownRecent = remember(query, recent) { recent.filterByQuery(query) }
    val hasMatch = shownFrequent.isNotEmpty() || shownRecent.isNotEmpty()

    Scaffold(
        modifier = Modifier.dismissKeyboardOnTap(),
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.text_lookup_title),
                closeLabel = stringResource(R.string.close),
                onClose = onClose,
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .imePadding(),
        ) {
            NutriTextField(
                value = query,
                onValueChange = { query = it },
                label = stringResource(R.string.text_lookup_label),
                placeholder = stringResource(R.string.text_lookup_placeholder),
                leading = { SearchMark(MaterialTheme.colorScheme.onSurfaceVariant) },
                // 清除鈕只在有內容時出現：手機上要使用者自己選取後刪掉太費事
                trailing = if (query.isEmpty()) null else {
                    {
                        CircleIconButton(
                            onClick = { query = "" },
                            size = 24.dp,
                            borderColor = MaterialTheme.colorScheme.outlineVariant,
                            borderWidth = 1.dp,
                        ) { CloseMark(MaterialTheme.colorScheme.onSurfaceVariant, size = 12.dp) }
                    }
                },
                // 送出鍵只收鍵盤：篩選是邊打邊做的，這裡沒有「送出」這件事，
                // 而收掉鍵盤正好把被蓋住的清單讓出來。
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                onFocusChanged = { focused = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 8.dp),
            )

            // 跟搜尋頁一樣：可能是從別天跳回來才補登，不講的話不知道會記到哪天。
            if (targetDate != LocalDate.now()) {
                Text(
                    withNumerals(stringResource(R.string.search_target_date, targetDate.displayLabel())),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
                )
            }

            // FoodLibrary 內部是 TabRow + HorizontalPager 兩個手足元件，
            // 得放進 Column（而不是 Box）才會上下疊放而不是互相蓋住。
            Column(Modifier.weight(1f).fillMaxWidth()) {
                FoodLibrary(shownFrequent, shownRecent, query.isNotBlank(), onReuseSuggestion)
            }

            Hairline()

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 這一行要接得上使用者剛做完的動作，而那有三種：還沒打字、打了但
                // 上面篩得到東西、打了而且什麼都沒有。**篩得到的時候不能說「沒有」**
                // —— 上面明明就列著幾筆相近的，那句話會顯得這個 app 沒在看自己的清單。
                //
                // 鍵盤開著而且上面有東西可看時整行收掉，把高度讓給清單。
                if (!focused || !hasMatch) {
                    Text(
                        withNumerals(
                            when {
                                query.isBlank() -> stringResource(R.string.text_lookup_divider)
                                hasMatch -> stringResource(R.string.text_lookup_divider_near)
                                else -> stringResource(R.string.text_lookup_divider_query, query.trim())
                            }
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                // 這段是「還沒開始打」時的指引，打字中沒有人在讀它，而它就是兩行。
                if (!focused) {
                    Text(
                        stringResource(R.string.text_lookup_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                StampButton(
                    label = stringResource(R.string.text_lookup_go),
                    enabled = query.isNotBlank(),
                    // 欄位現在在畫面最上面，離這顆章很遠 —— 不講一句的話，
                    // 使用者看到的就只是一顆按不下去的鈕。
                    helper = if (query.isBlank()) stringResource(R.string.text_lookup_need_query) else null,
                    onClick = submit,
                )
            }
        }
    }

}
