package com.watson.nutrilog.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.watson.nutrilog.R
import com.watson.nutrilog.data.db.FoodSuggestion
import com.watson.nutrilog.ui.theme.NutrientColors
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
 *
 * **收起來與長回來是兩段、不是同一段**（見 [GuideEnter]）：離開搜尋框時先讓鍵盤退完、
 * 整區沉到定位，文字才從章的底邊往上長出來。兩件事一起做的話，畫面在同一段時間裡
 * 往兩個方向動，讀起來是彈一下而不是一個動作。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TextLookupScreen(
    targetDate: LocalDate,
    frequent: List<FoodSuggestion>,
    recent: List<FoodSuggestion>,
    onReuseSuggestion: (FoodSuggestion) -> Unit,
    onLookup: (String, Boolean) -> Unit,
    searchAvailable: Boolean,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val submit = { useSearch: Boolean -> if (query.isNotBlank()) onLookup(query, useSearch) }

    val shownFrequent = remember(query, frequent) { frequent.filterByQuery(query) }
    val shownRecent = remember(query, recent) { recent.filterByQuery(query) }
    val hasMatch = shownFrequent.isNotEmpty() || shownRecent.isNotEmpty()

    // **「鍵盤收完了沒」問系統，不要自己數毫秒。** 說明文字要等整區沉到定位才長出來
    // （見 [GuideEnter]），而那段時間就是 IME 的退場動畫 —— 各家鍵盤不一樣長，
    // 寫死一個延遲在慢的機器上會提早搶拍、在沒有鍵盤動畫的機器上則是乾等。
    //
    // 包在 derivedStateOf 裡是為了**把重組關在這個布林值上**：`getBottom()` 在鍵盤
    // 動畫的每一幀都會變，直接讀等於整個畫面（含底下那份清單）每幀重組一次；
    // 包起來之後只有 true/false 真的翻面時才會通知讀它的人。
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val keyboardGone by remember(density, imeInsets) {
        derivedStateOf { imeInsets.getBottom(density) == 0 }
    }
    val guideSettled = !focused && keyboardGone

    Scaffold(
        modifier = Modifier.dismissKeyboardOnTap().dismissKeyboardOnScroll(),
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

            // 欄位在畫面最上面，離這裡很遠 —— 不講一句的話，使用者看到的
            // 就只是兩顆按不下去的鈕。
            val stampHelper = when {
                query.isBlank() -> stringResource(R.string.text_lookup_need_query)
                !searchAvailable -> stringResource(R.string.text_lookup_search_off)
                else -> null
            }

            // **這一區不用 spacedBy，間距各自寫在會動的內容裡面。**
            // spacedBy 是照「有幾個子項」算的，而 AnimatedVisibility 收起來之後
            // 節點還在（高度 0）—— 間距照樣算進去，收合狀態會多出兩段 12dp 的空白。
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                // 這一行要接得上使用者剛做完的動作，而那有三種：還沒打字、打了但
                // 上面篩得到東西、打了而且什麼都沒有。**篩得到的時候不能說「沒有」**
                // —— 上面明明就列著幾筆相近的，那句話會顯得這個 app 沒在看自己的清單。
                //
                // 鍵盤開著而且上面有東西可看時整行收掉，把高度讓給清單。
                // **只有「篩得到東西才收起來」那一段要等鍵盤。** 打字打到一筆都篩不到
                // 時這行要立刻接上（那時候鍵盤還開著、根本沒有要等的東西），
                // 慢半拍會讀成卡頓。
                AnimatedVisibility(
                    visible = !hasMatch || guideSettled,
                    enter = GuideEnter,
                    exit = GuideExit,
                ) {
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
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                // 這段是「還沒開始打」時的指引，打字中沒有人在讀它，而它就是兩行。
                AnimatedVisibility(
                    visible = guideSettled,
                    enter = GuideEnter,
                    exit = GuideExit,
                ) {
                    Text(
                        stringResource(R.string.text_lookup_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }

                // **成對的兩顆章**（「一個畫面只有一顆章」的既有例外）：同一件事的兩種
                // 做法，差別只在要不要先查網路。做成兩顆是因為**使用者在打字的當下
                // 就知道自己要哪一種** —— 他在食物前面加店名，就是想要官方資料。
                //
                // 試過讓模型自己判斷（tool-calling-search 分支），它自己下的查詢比
                // 固定字尾還差。知道答案的人是使用者，不是模型。
                StampButton(
                    label = stringResource(R.string.text_lookup_go),
                    enabled = query.isNotBlank(),
                    onClick = { submit(false) },
                )
                Spacer(Modifier.height(12.dp))
                // 退一階的深灰章：形狀一致才讀得出「這兩顆是一對」，顏色負責講
                // 「這一顆比較慢、而且會花掉一次搜尋額度」。
                //
                // **helper 掛在這一顆、不掛第一顆**：兩顆是一對，說明夾在中間會
                // 把它們切成兩件事。成對動作共用一行訊息（同設定頁的匯出／匯入）。
                StampButton(
                    label = stringResource(R.string.text_lookup_search),
                    enabled = query.isNotBlank() && searchAvailable,
                    // helper 不交給章自己畫：它要和上面兩段一起長出來，
                    // 而章內建的那一份是說有就有。畫的是同一個 StampHelperText。
                    color = NutrientColors.StampSecondary,
                    onClick = { submit(true) },
                )
                AnimatedVisibility(
                    visible = guideSettled && stampHelper != null,
                    enter = GuideEnter,
                    exit = GuideExit,
                ) {
                    StampHelperText(stampHelper.orEmpty())
                }
            }
        }
    }

}

private const val GUIDE_GROW_MS = 260
private const val GUIDE_FADE_MS = 200
private const val GUIDE_FADE_LAG_MS = 60
private const val GUIDE_HIDE_MS = 130

/**
 * 說明文字的進場：**從底邊往上長**（`expandFrom = Bottom`），而不是整段淡進來。
 *
 * 這一區的底邊貼著那兩顆章，而這一區在 Column 裡是最後一個子項、底邊釘在畫面底部
 * —— 所以它長高的時候章不會動，只有上緣往上推。底邊錨定的展開配上這個版面，
 * 看起來就是「文字從章底下被抽出來」，有一個明確的來源。
 *
 * 淡入晚 [GUIDE_FADE_LAG_MS] 起跑、和展開同時結束：**先看到形狀再看到字**，
 * 才像長出來而不是浮現。
 *
 * 什麼時候開始長由 `guideSettled` 決定（問系統鍵盤收完了沒），不是靠延遲。
 */
private val GuideEnter: EnterTransition =
    expandVertically(
        animationSpec = tween(GUIDE_GROW_MS, easing = LinearOutSlowInEasing),
        expandFrom = Alignment.Bottom,
    ) + fadeIn(
        animationSpec = tween(GUIDE_FADE_MS, GUIDE_FADE_LAG_MS, LinearOutSlowInEasing),
    )

/**
 * 退場刻意比進場快一倍，而且沒有延遲：點進搜尋框的當下鍵盤正在升起來，
 * 這一區慢吞吞地收會和鍵盤搶同一塊空間，看起來像被鍵盤推走的。
 */
private val GuideExit: ExitTransition =
    fadeOut(tween(GUIDE_HIDE_MS / 2, easing = FastOutLinearInEasing)) +
        shrinkVertically(
            animationSpec = tween(GUIDE_HIDE_MS, easing = FastOutLinearInEasing),
            shrinkTowards = Alignment.Bottom,
        )
