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

現在的色票是「紙與墨」，有兩件會咬人的事：

- **表面之間幾乎沒有對比**（底 `#F8F7F2` vs 浮起 `#FDFCF9` 只差 3%）。
  這是故意的：版面靠**細線**（`Hairline()` / `outlineVariant`）分隔，不是靠卡片色塊。
  不要為了「看得出是一張卡」去加深 `surfaceContainer` —— 那會把整個設計拉回舊樣子。
  **要分隔就畫線，不要填色。** 新畫面請直接用 `Hairline()`，不要用 `Card`。
- **深色不是純黑而是暖灰** `#191813`。純黑會讓文字看起來發灰，細線也會整條消失。

三大營養素的固定色放在 `theme/NutrientColors`，不要在各畫面自己寫死色碼。
它們是 `@Composable` getter 而不是常數 —— 深淺兩套的值不一樣（深色底上要提亮），
正確的那一套只有在 composition 裡讀得到，所以**不能**在 top-level `val` 用它們。

預設字體是無襯線（`Base = Typography()` 沒覆寫 `fontFamily`，Roboto + 中文落到
Noto Sans CJK）。純數字／英文（大熱量數字、目標欄位、紀錄與搜尋的熱量、日期格、
月曆）另外套 `theme/NumberFontFamily`（系統襯線別名 `FontFamily.Serif`），
跟中文字做出區隔。**這個字體只能套在確定不含中文的 `Text` 上**——早期版本把
`FontFamily.Serif` 套進整個 `Typography`，連食物名稱這種中文字串也一起吃到，
中文襯線在大部分裝置上會落到偏傳統印刷體的字重，讀起來像新細明體，
才會改成現在「逐一挑純數字/英文的 `Text` 套用」的做法。中西文混排在同一句
（例如「目標 2000 · 還有 200 的空間」）要用 `buildAnnotatedString` 只框住
數字部分，不能整個 `Text` 一起套。

所有輸入框共用 `theme/NutriFieldShape` + `theme/nutriFieldColors()`（圓角頂角＋
下橫線，聚焦時線變粗變綠）——這是取代 `OutlinedTextField` 整圈外框的統一樣式，
新畫面要加輸入框直接套這兩個，不要自己疊 `OutlinedTextField` 或另外設計一套外框。

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
