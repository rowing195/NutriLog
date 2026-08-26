# NutriLog

Android 每日飲食營養素紀錄器（Kotlin + Compose）。這個 repo 的根目錄就是 gradle 專案。
狀態與已驗證項目看 [HANDOFF.md](HANDOFF.md)，功能與設計決策看 [README.md](README.md)。

跨專案的共用規則（模擬器、SDK 路徑）在上一層的 `../CLAUDE.md`，那份**不在版控裡**，
是這台機器的環境設定。

## 建置與部署

```powershell
& "$env:LOCALAPPDATA\Android\tools\gradle-8.11.1\bin\gradle.bat" -p "C:\code\android app\NutriLog" assembleDebug
& "C:\code\android app\NutriLog\tools\emu.ps1" start    # 開模擬器並等 boot_completed
& "C:\code\android app\NutriLog\tools\emu.ps1" deploy   # build + 安裝
```

APK 在 `app/build/outputs/apk/debug/app-debug.apk`。**驗證完就把路徑交給使用者，不要自己裝到實機。**

模擬器 AVD 沿用 `localreader_api35` —— 那只是一個裝置映像，和 app 無關，
另外再開一個只是多佔幾 GB 的 userdata。

## UI 測試用 tools/ui.ps1，不要盲點座標

```powershell
$ui = "C:\code\android app\NutriLog\tools\ui.ps1"
& $ui dump                        # 列出畫面所有文字節點與中心座標
& $ui tap "手動輸入"
& $ui type "Chicken Rice"         # input text 只吃 ASCII，測試資料一律用英數
& $ui scroll down                 # 儲存鈕被展開的區塊推到畫面外時用
& $ui back                        # 也用來收鍵盤
& $ui tapxy "540,1586"            # 只有沒有文字的元件才用座標
```

**輸入表單的儲存鈕常常需要先 `back` 收鍵盤或 `scroll down` 才點得到。**
直接照 dump 的座標點下去會打在鍵盤上 —— 那不是「沒反應」，
而是把數字打進上一個聚焦的欄位，事故現場很難看出來。

**模擬器的系統鍵盤是浮動小面板，貼在畫面左緣中段。** 它會蓋住編輯表單左側
（熱量格的標籤正好在它下面），照 dump 的座標點過去會點在鍵盤面板上而不是格子上，
看起來像「這個元件壞了點不動」。要點左半邊的東西**先確認鍵盤收了**，
或者改點同一個元件靠右的位置（`tapxy "600,1150"` 而不是 `tap "熱量"`）。

`tap` 是部分字串比對，撞名會點錯：`"1"` 會撞到份數倍率那個 `1`、
`"食物名稱"` 會撞到錯誤提示「請先填食物名稱」。鍵盤按鍵一律用 `tapxy` 配 dump 的座標。

腳本本身**只能用 ASCII**：PS 5.1 把沒有 BOM 的檔案當 ANSI 讀，某些中文位元組序列會直接 parse error。

## tools/setup-signing.sh 是 bash，不是 PowerShell

一次性的簽章金鑰設定精靈（產金鑰 → 設 GitHub secret），**要用 Git Bash 跑**：

```bash
./tools/setup-signing.sh
```

它是 bash 而不是 .ps1，因為要處理 `base64` 的二進位管線 —— PS 5.1 會把二進位
當文字轉換而弄壞內容。`.gitattributes` 有 `*.sh text eol=lf`，
少了它在 `core.autocrlf=true` 的機器上會被簽出成 CRLF，
然後 bash 在 shebang 就死，錯誤訊息是看不懂的 `$'\r': command not found`。

## 查資料有沒有真的落地

Room 預設開 WAL，所以**資料通常在 `-wal` 檔裡，主檔只有 4096 bytes 是正常的**：

```bash
export MSYS_NO_PATHCONV=1
adb exec-out run-as com.watson.nutrilog cat databases/nutrilog.db-wal > wal.bin
adb exec-out run-as com.watson.nutrilog cat files/datastore/nutri_prefs.preferences_pb > p.pb
```

`run-as` **不能**寫進 `/sdcard`（會產生空檔），一定要走 `exec-out` 接 stdout，
而且要在 Bash 而非 PowerShell 重導向（PS 會把二進位轉成文字弄壞）。

## 配色與版面語言

Material3 預設 baseline 是紫色系。**新增顏色角色時整族都要蓋**
（`surfaceContainerLowest/Low/_/High/Highest`、`surfaceVariant`、`outline*`），
少蓋一個就會有元件固執地維持預設紫。第一版只蓋了 `primary`，
結果整個 app 的背景與卡片都是淡紫灰。

現在的色票是「紙與墨（刊物）」，有三件會咬人的事：

- **版面靠線分隔，不靠卡片色塊**，而線分**兩級**：`Rule()` 是 2px 的墨線
  （報頭底下、鍵盤與表單之間、區段之間），`Hairline()` 是 1px 的細線（列與列之間）。
  兩者不要混用 —— 版面的節奏就是靠這兩級撐的。新畫面不要用 `Card`。
- **深色不是純黑而是暖黑** `#17150F`。純黑會讓襯線數字看起來發灰，細線也會整條消失。
- **朱紅 `#D8462A` 只給兩件事：超標與聚焦。** 不要拿去畫「選中」——
  選中一律用墨色（`primary` 就是墨色，不是彩色強調色）。這條守住了，
  畫面上出現紅色就一定代表「看這裡」。

三大營養素的固定色放在 `theme/NutrientColors`，不要在各畫面自己寫死色碼。
它們是 `@Composable` getter 而不是常數 —— 深淺兩套的值不一樣（深色底上要提亮），
正確的那一套只有在 composition 裡讀得到，所以**不能**在 top-level `val` 用它們。

中文一律無襯線（`Base = Typography()` 沒覆寫 `fontFamily`，Roboto + 中文落到
Noto Sans CJK）。純數字／英文另外套 `theme/NumberFontFamily`，那是**內嵌的
Instrument Serif**（`res/font/instrument_serif.ttf`，OFL）。換成內嵌字型而不是
系統別名 `FontFamily.Serif`，是因為系統襯線在多數 Android 上就是 Noto Serif，
字面接近 Times，看起來是「沒挑過字型」而不是「挑了襯線」。

**這個字型沒有中文字符，只能套在確定不含中文的 `Text` 上**（大熱量數字、目標、
紀錄與搜尋的熱量、日期格、月曆、鍵盤按鍵、單位 kcal/g/mg）。套到中文會 fallback
回系統襯線，那正是要避開的東西。中西文混排**不要**用 `buildAnnotatedString` 去
`indexOf` 數字的位置（數字剛好等於年份之類的巧合會框錯段）——拆成兩個 `Text` 並排，
用 `alignByBaseline()` 對齊，見 `TodayScreen.kt` 的 `LabelledNumber`。

設計稿上的小標原本是襯線中文，Android 上做不到（全字集動輒十幾 MB，落到系統襯線
又會踩上面那個坑），所以**小標改用無襯線＋拉開字距**（`labelSmall` 的
`letterSpacing = 2.4.sp`、`titleSmall` 是 `4.sp`）。字距就是這套設計裡「這是標題」
的唯一訊號，改字距等於改階層。

## 欄位與按鍵：形狀就是層級

所有輸入框走 `Common.kt` 的 **`NutriTextField`**：完整外框 ＋ 一條比其他三邊重的
3px 底規線，聚焦時外框轉墨色、底線轉朱紅。前一版是「只有底線、沒有外框」
（M3 第一版的樣式），Google 後來自己的研究把它改掉了 —— 沒有封閉邊界時使用者
辨認「哪裡可以打字」明顯變慢。新畫面直接套 `NutriTextField`，不要自己疊
`OutlinedTextField` 或 M3 的 `TextField`。

按鍵**不要全部做成同一種矩形**，形狀本身在講層級（全部在 `Common.kt`）：

| 元件 | 形狀 | 用在哪 |
|---|---|---|
| `StampButton` | 印章（滿版墨底＋內縮 4dp 細框） | 主要動作，一個畫面只有一顆 |
| `PillButton` | 藥丸 | 就地確認（收鍵盤、查詢、重試） |
| `TextAction` | 純文字 | 次要出路（貼上、改成手動輸入） |
| `RoundKey` | 圓章 | 自製數字鍵盤、份數步進 |
| `BallotRow` / `MealPicker` | 圈選（圓） | **單**選（餐別、外觀、模型） |
| `SquareCheck` | 打勾框（方） | **複**選（AI 確認畫面） |
| `CircleIconButton` | 線框圈 | 圖示鈕 |

單選是圓的、複選是方的，這個對比要守住 —— 形狀本身就在講「能不能多選」。

## 不要用 M3 的成品元件

整個 app 已經沒有任何 Material 的現成外觀元件了。要加東西時先看 `Common.kt` 有沒有，
沒有就照這套語言做一個，**不要圖快抓一個 M3 元件回來** —— 它們自帶容器色、圓角、
陰影與 ripple，在這套方角紙面上一眼看得出是外來的。已經換掉的對照表：

| 不要用 | 改用 | 為什麼 |
|---|---|---|
| `TopAppBar` | `ScreenTopBar` | 容器色與標題字級整組要覆寫，還會跟內建 padding 打架 |
| `TextField` / `OutlinedTextField` | `NutriTextField` | 見上一節 |
| `Button` / `OutlinedButton` / `TextButton` | `StampButton` / `PillButton` / `TextAction` | 形狀就是層級 |
| `Switch` | `NutriSwitch` | M3 是膠囊軌道加圓球，這裡唯一的圓頭 |
| `SegmentedButton` | `BallotRow` | 它靠容器色分辨選中，低對比色票上看不出來 |
| `TabRow` / `Tab` | `NutriTabs` | 指示器是圓角膠囊，還自帶 ripple |
| `AlertDialog` | `NutriDialog` | 圓角 28dp、按鈕擠在右下角，整個是 Material 的長相 |
| `Card` | `Box` ＋ `border` | 這套版面靠線分隔不靠色塊，填色等於沒填 |
| `CircularProgressIndicator` | `IndeterminateRule` | 圓形在滿是規線的版面上很突兀 |
| `HorizontalDivider` | `Hairline` / `Rule` | 線分兩級，M3 只有一種 |
| `Icons.Default.*` | `Common.kt` 裡自己畫的 `*Mark` | 最容易讓 app 看起來沒設計過的一件事 |
| `ExposedDropdownMenu` | 直接把選項攤開 | 彈出的浮層是 app 裡唯一一個 Material 容器 |

**兩個實作陷阱**（都真的踩過）：

- `NutriTabs` 那種「文字底下一條線」的元件，底線不能直接 `fillMaxWidth()` ——
  它會吃到 Row 傳下來的最大寬度，第一個分頁就把整列撐滿、後面的被擠出畫面。
  要給 Column `width(IntrinsicSize.Max)` 把寬度收到文字本身。
- `StampButton` / `PillButton` / `TextAction` 的 `onClick` **不是最後一個參數**
  （後面還有 modifier、filled、color），所以**不能用尾隨 lambda**，
  一定要寫 `onClick = { ... }`。用尾隨 lambda 會安靜地綁到錯的參數上，
  編譯錯誤訊息看起來跟型別不合無關。

今日頁的「記一筆」是 `TodayScreen.AddMenu`：角落一顆 60dp 的印章（跟 `StampButton`
同一個長相的收合狀態），點下去把五個入口逐列滑出來。**那顆按鈕整個是畫出來的、
沒有任何文字節點，所以一定要自己掛 `contentDescription`** —— 不然讀螢幕的人和
`tools/ui.ps1` 都摸不到它。

五個容易忘的細節：

- **不能按的 `StampButton` 退成外框章**，不是灰掉的實心塊 —— 灰掉的看起來像壞了。
  理由用它的 `helper` 參數講一句，不要彈 dialog。
- **鍵盤內部自己還有三級**：數字有圈、小數點圈變淡、刪除完全沒有圈。
- **圖示全部自己畫**（`SearchMark` / `CalendarMark` / `SlidersMark` / `TrashMark` /
  `PlusMark` / `ChevronMark` / `BackspaceMark`…），規格是 24 格、1.6dp 線寬、圓端點。
  **不要用 `Icons.Default.*`** —— 一眼認得出是 Material 預設圖示，是最容易讓 app
  看起來沒設計過的一件事。
- **點數字格一定要先 `focusManager.clearFocus()`**（`EditEntryScreen.focusNumber`）。
  少了它，系統鍵盤和自製數字鍵盤會同時佔著畫面底部，使用者以為在按數字、
  其實按在系統鍵盤上，數字進了上一個聚焦的文字欄位 —— 就是本檔開頭記的那個事故。
  反方向（點文字欄位收掉數字鍵盤）走 `NutriTextField` 的 `onFocusChanged`。
- 進度用 `IndeterminateRule()`，不要用 `CircularProgressIndicator` ——
  圓形在這個滿是規線的版面上很突兀，而且那顆轉圈是所有 app 都一樣的那顆。
- 選單展開時後面那片用 `Modifier.blur()` 模糊。**它只在 API 31+ 有效、minSdk 是 26**，
  Android 8～11 上那行等於沒作用 —— 所以半透明遮罩那層一定要留著，那才是共通的退路。
  兩層都下重手會糊成一片濁色，所以有模糊之後遮罩降到 0.32。

## 新增紀錄要落在哪一餐：`pendingMeal`

五個入口（常吃／手動／拍照／相簿／條碼）最後都會匯流到 `guessMeal()`——
分別是 `startNewEntry()`、`startPrefilled()` 和 `startAnalysis()` 這三個地方。
所以「從某一餐的『還沒記』點進來」不能只改其中一條路，而是設
`viewModel.setPendingMeal(meal)` 讓它蓋掉那三處的猜測，不管使用者最後選哪一條，
紀錄都會落在他點的那一餐。

**`pendingMeal` 在 `backToToday()` 與存檔成功後會清掉。** 不清的話，下次從角落那顆章
（沒指定餐別）進來還會沿用上一次那一餐，而使用者根本沒指定過 —— 改動這一段之後
一定要回歸這件事：先從「晚餐」的還沒記進來取消，再從角落進來，餐別要回到照時間猜的那一個。

## 常吃／最近點一列直接進編輯表單

原本中間隔了一張細節面板（`SuggestionSheet`，攤開營養素、按「加入」才進表單），
已經移除。理由：表單本身就把完整營養素攤開了，而且要按「儲存」才真的入庫，
那才是確認步驟；中間那一層等於同一件事確認兩次，而點錯的代價只是按個取消。
`SearchScreen` 和 `TextLookupScreen` 共用 `FoodLibrary`，兩邊都是直接 `onReuseSuggestion`。

熱量／蛋白質／脂肪／碳水的超標顏色一律用 `overSeverity()`（`Common.kt`）：
超過目標 10% 以內是 `NutrientColors.Warning`（橘），超過 10% 才是
`NutrientColors.Over`（紅）。不要自己寫 `value > target` 的二分法紅／不紅。

**自己拼的 `topBar` / `bottomBar` 要自己加 `statusBarsPadding()` / `navigationBarsPadding()`。**
`MainActivity` 開了 `enableEdgeToEdge()`，M3 的 `TopAppBar` 會自己處理，
但用 `Column`/`Row` 拼的不會 —— 標題會直接畫到狀態列的時鐘上面。

**今日頁的日／週分頁器有三個好踩的坑：**

- `HorizontalPager` 的預設 fling 行為會看滑動速度決定跳幾頁，快速一撥可能
  一次跳十幾頁 —— 完全違反「一次滑動＝換一天／一週」的直覺。兩個分頁器都要用
  `PagerDefaults.flingBehavior(state, pagerSnapDistance = PagerSnapDistance.atMost(1))`
  鎖成最多一頁，不管滑多快。
- `LaunchedEffect(pagerState) { snapshotFlow { pagerState.settledPage }.collect { ... } }`
  這個 collector 只在第一次組成時啟動一次（`pagerState`這個 key 整個生命週期
  都不會變），裡面**不能**直接讀外面會變動的 `date`/`weekStart` 參數 —— 抓到的
  永遠是啟動當下那個舊值，之後即使外部真的變了也不會更新，會造成錯誤地重複
  觸發換日／換週，一路滾雪球疊加下去。要用 `rememberUpdatedState` 包起來，
  每次比對才會拿到當下真正的值。這個 bug 曾經真實發生過：從今天單純滑一次
  「前一天」，會直接跳掉十幾天。
- 日分頁拖過週界時，`WeekPageContent` 會用借位 overlay（純視覺，讀
  `dayPagerState.currentPage`/`currentPageOffsetFraction` 但不拿去 commit）把新的
  一週滑到畫面上；放開手指、`weekStart` 真的改變後，如果又讓 `weekPagerState`
  自己 `animateScrollToPage` 一次，等於把使用者剛看過的位移動畫重播一次，
  肉眼是「換了兩次週」。要用一個旗標記住「這次 `weekStart` 改變是不是日分頁器
  拖曳造成的」——是的話 `weekPagerState` 只要 `scrollToPage`（不動畫）悄悄接上
  真正狀態；其他來源（週橫條箭頭、回到今天、月曆跳頁）事前沒有 overlay 動畫
  可看，才需要保留 `animateScrollToPage`。細節見 `TodayScreen.kt` 裡
  `weekChangeFromDaySwipe` 那段的長註解。

## 改動慣例

- 註解寫**繁體中文**，解釋「為什麼」而不是「做了什麼」。
- 新增 `NutriSettings` 欄位一律給預設值 —— 舊的 DataStore 資料靠預設值相容，不做遷移。
- 動到 Room entity 就要**加 migration 並升 version**，這裡和 DataStore 不一樣，
  沒有「靠預設值相容」這回事。
- 營養素缺資料一律 `null`，不要補 0 ——「沒標示」和「真的是 0」必須分得開。
- 模型或外部 API 回來的數字**永遠要經過使用者確認畫面**才入庫。
- 解析外部資料（OFF、Gemini、DataStore）一律 `runCatching` 包起來給安全退路。
- 動到閱讀以外的畫面後，回歸這幾項：**手動新增→編輯→刪除**、
  **換日**（前一天應為空、回今天資料還在）、**force-stop 後資料與設定都還在**。
  動到今日頁的分頁器另外要測**單純滑動一次剛好只換一天／一週**，而不是只看
  「滑得動」就算過——上面記的那個滾雪球 bug 連滑一次、滑完等它完全 settle
  再檢查畫面都會重現，光看有沒有反應看不出來，要對日期數字。
