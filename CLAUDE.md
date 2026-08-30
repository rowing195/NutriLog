# NutriLog

Android 每日飲食營養素紀錄器（Kotlin + Compose）。這個 repo 的根目錄就是 gradle 專案。
功能與設計決策看 [README.md](README.md)，已知問題與修法的紀錄看
[GitHub Issues](https://github.com/rowing195/NutriLog/issues?q=is%3Aissue)（都關掉了，是當紀錄用的）。

跨專案的共用規則（模擬器、SDK 路徑）在上一層的 `../CLAUDE.md`，那份**不在版控裡**，
是這台機器的環境設定。

## 換機器接手

**clone 完就能建置，不需要任何額外檔案。** 實測過一次乾淨 clone：沒有
`local.properties`、沒有 keystore，`./gradlew assembleDebug` 22 秒成功，
`./gradlew test` 與 `./gradlew assembleRelease` 也都過。gradle wrapper（含
`gradle-wrapper.jar`）、version catalog、兩支內嵌字型、`tools/` 三支腳本都在版控裡。

要另外準備的只有機器層級的東西：

| 項目 | 說明 |
|---|---|
| JDK 17、Android SDK | 外部安裝，設好 `ANDROID_HOME`（或讓 Android Studio 產 `local.properties`）|
| 模擬器 AVD | `tools/emu.ps1` 預設 `localreader_api35`，換機器用 `-AvdName` 指別的即可 |
| 簽章 keystore | 只有**本機**要出正式版才需要，跑 `tools/setup-signing.sh` |

**發版不受影響。** 四個 secret（`KEYSTORE_BASE64` / `KEY_ALIAS` / `KEY_PASSWORD` /
`STORE_PASSWORD`）存在 GitHub repo 上，所以從任何機器推 `v*` tag 都能發出簽章正確
的 release，本機完全不需要 keystore。

### ⚠️ 本機的 `assembleRelease` 會出 debug 簽章的 APK

沒有 keystore 時 `build.gradle.kts` 會自動退回 debug 簽章，而且**建置照樣成功、
不會有任何警告**。實測那顆 APK 的簽章是：

```
Signer #1 certificate DN: C=US, O=Android, CN=Android Debug
```

這種 APK 裝到手機上之後**無法被正式版覆蓋更新**，使用者得先解除安裝 —— 而這個
app 沒有雲端備份，飲食紀錄會跟著全部消失。

CI 有 `Refuse to release without a signing key` 那一步擋著，但**本機手動跑
`assembleRelease` 沒有任何防護**。所以：交給使用者的一律是 `assembleDebug` 的
APK，正式版一律走推 tag 讓 CI 產生，不要在本機做。

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
- **朱紅 `#D8462A` 只給三件事：超標、聚焦、以及會關掉或刪掉東西的動作**
  （垃圾桶、確認面板的刪除鍵、設定頁的「停止自動備份」）。不要拿去畫「選中」——
  選中一律用墨色（`primary` 就是墨色，不是彩色強調色）。這條守住了，畫面上出現
  紅色就一定代表「看這裡」或「這顆要想一下」。
  **破壞性動作用空心紅、不要用實心紅塊**（`StampButton(destructive = true)`）：
  整顆填滿會變成畫面上最搶眼的東西，而它們幾乎都是次要動作。

三大營養素的固定色放在 `theme/NutrientColors`，不要在各畫面自己寫死色碼。
它們是 `@Composable` getter 而不是常數 —— 深淺兩套的值不一樣（深色底上要提亮），
正確的那一套只有在 composition 裡讀得到，所以**不能**在 top-level `val` 用它們。

中文與一般文字用**內嵌的 jf open 粉圓**（`res/font/jf_open_huninn.ttf`，OFL）。
它是圓體不是手寫體，這是刻意的：數字已經是手寫的 Neucha，中文再用手寫體兩種會
互相打架，而且中文手寫體筆畫一軟，在紀錄列第二行那種 10sp 就糊掉。圓體的圓頭
收筆給的是同一種柔和調性，但粗細均勻、小字級撐得住。

**套用方式是 `Base = Typography().withFamily(TextFontFamily)`。** M3 的 `Typography`
沒有 `defaultFontFamily`，不這樣做就得在每個 `.copy()` 裡各寫一次 `fontFamily`，
漏一個就是某一種元件字型不一致、而且很難發現。**新增文字樣式時不用管字型**，
從 `Base` 衍生就對了。

粉圓整支 4.7 MB 直接收進 APK，**沒有做子集化** —— 食物名稱是使用者自己打的，
事先不會知道要哪些字，子集化只會換來「某些字忽然變成另一種長相」。涵蓋 11,988
個字符（漢字 10,045），Big5 常用字涵蓋率 99.1%，常見食物名稱實測零缺字；落在
那 0.9% 之外的字會掉回系統字型。純數字／英文另外套 `theme/NumberFontFamily`，那是**內嵌的
Neucha**（`res/font/neucha.ttf`，OFL，Jovanny Lemonad）——一支手寫體。用內嵌
字型是因為 Android 沒有任何內建手寫體可以指定；挑手寫體是因為這個 app 是「每天
隨手記一筆」的東西，數字長得像手寫的比像印刷品更貼近它在做的事。

**這個字型沒有中文字符，只能套在確定不含中文的 `Text` 上**（大熱量數字、目標、
紀錄與搜尋的熱量、日期格、月曆、鍵盤按鍵、單位 kcal/g/mg）。套到中文會 fallback
回系統字型，同一句裡會出現兩種長相。中西文混排有兩種作法，看情況選：

- **並排的標籤＋數值**（「早 330」「目標 2000」）拆成兩個 `Text`，用
  `alignByBaseline()` 對齊，見 `TodayScreen.kt` 的 `LabelledNumber`。
- **其餘所有中文夾數字的字串**——一行摘要（「1.1 份 · 蛋白 30.8」）、提醒
  （「換算約 40 kcal，和你填的 250 差得有點多」）、說明文（「已匯出 5 筆紀錄」）、
  欄位提示（「例如 1 碗 (250 g)」）——一律用 `Common.kt` 的 `withNumerals()`，
  它用正規式把所有數字段換成數字字型。

  `NutriTextField` 的 placeholder 與 `StampButton` 的 label／helper 已經在元件
  內部套好了，呼叫端直接傳 `String` 就行，不用自己包。

**不要**用 `buildAnnotatedString` 去 `indexOf` 某個數值的位置——巧合的相同字串會
框錯段（要標熱量 330，結果框到份量裡的 330）。`withNumerals()` 沒有這個問題：它標
的是「所有數字段」而不是某個值，句子怎麼組都不會標錯。

**`res/font/neucha.ttf` 是改過的，不是 Google Fonts 原版。** 原版數字的側邊留白
很不平均：`0/6/8/9` 是 41 units，`1/2/3/4/5/7` 是 0（`2` 甚至 −1），句點只有 20，
而且 kern 還是負的（`0`+`.` 是 −52）。結果是 `11` 完全沒間隙、`0.2` 的點黏在兩邊
數字上，看起來「有些數字擠有些不擠」。已經把 `0-9` 與 `. , / + -` 全部補成
lsb = rsb = **41 units** —— 41 就是這支字型自己給 `0/6/8/9` 的值，等於把其餘字形
補到它原本的標準（`0/6/8` 的 advance 幾乎沒動），不是外加一套新節奏。

要重現這個處理：對上列字形做 `hmtx[g] = (41 + 墨跡寬 + 41, 41)`，輪廓平移
`41 - xMin`，最後更新 `hhea.advanceWidthMax`。**從 Google Fonts 重新下載會失去它。**
OFL 允許修改，且 Neucha 沒有宣告 Reserved Font Name，所以字型名不用改。

**`numeric()` 會關掉 kerning**（`fontFeatureSettings = "\"kern\" 0"`）。字型裡的
kern 是照原本過緊的 metrics 調的、幾乎全是負值，補完側邊留白後它們會把字又拉回去。
側邊留白既然已經整排一致，就不需要任何逐對微調。

**套用數字字型一律呼叫 `theme/Theme.kt` 的 `TextStyle.numeric()`，不要自己
`.copy(fontFamily = NumberFontFamily)`。** `numeric()` 除了換字型還會把
`letterSpacing` 覆寫成 `NumberTracking`（0.5sp）。覆寫是刻意的：基礎樣式各自帶著
給中文標題用的字距（`titleSmall` 4sp、`labelSmall` 2.4sp），不覆寫數字會跟著被
拉開。

**不要對數字字距寫死負值。** `displayLarge`／`displaySmall`（今日頁大熱量數字、
表單熱量格）曾經各自寫死 `letterSpacing = -1.5.sp` / `-0.5.sp`，是抄一般無襯線
標題「收緊變醒目」那招，但數字禁不起再往內壓，那正是使用者反應「數字細長難讀」
的原因。

### 字距的分工：側邊留白管比例，`NumberTracking` 管下限

這件事來回試了三輪才定案，結論值得記住：

- **側邊留白（字型裡的 41 units）負責比例。** 它天生隨字級縮放，任何字級都是 0.08em。
- **`NumberTracking`（固定 0.5sp）負責絕對下限。** 0.08em 在 10sp、420dpi 上換算
  只剩約 2px，抗鋸齒一吃就看不見；補上 0.5sp 之後是 3.4px。而 66sp 的大數字只從
  15px 變成 16.3px，看不出來。

**這兩件事不能互相取代，也不能重複做。** Instrument Serif 時代那套
「`fontSize × 比例 + 固定底量`」失敗，錯在比例項 —— 側邊留白已經處理過比例了，
再乘一次就是大字級被拉散、小字級仍然不夠。**所以 `NumberTracking` 永遠是固定值，
不要改成隨字級縮放。**

換字型時兩件都要重新確認：新字型的側邊留白是否均勻（見 [NumberFontFamily]），
以及固定下限在最小字級（`labelSmall` 10sp）上夠不夠。

小標的層級**靠字距而不是靠字重或另一支字型**（`labelSmall` 的 `letterSpacing = 2.4.sp`、
`titleSmall` 是 `4.sp`）。字距就是這套設計裡「這是標題」的唯一訊號，改字距等於
改階層。

## 文字顏色：`LocalContentColor` 的預設是純黑

**`Text` 沒寫 `color` 時拿到的是 `LocalContentColor`，而它的預設值是 `Color.Black`。**
只有 M3 的 `Surface` 會覆蓋它，而這個 app 不用 M3 成品容器 —— 畫面是
`Modifier.background()` 疊出來的。所以任何畫在 `Scaffold` 之外的東西（`AddMenu`
那類覆蓋層、`Dialog`）裡沒指定顏色的 `Text`，實際上都是黑字。

淺色模式下黑字配米底剛好是對的，所以這個 bug 只會在深色模式現形，而且是「整段
文字消失」等級的（實測 `#000000` 配 `#17150F`，對比 **1.15:1**）。

`NutriLogTheme` 已經在根部 `CompositionLocalProvider(LocalContentColor provides
scheme.onSurface)`，所以現在的預設是對的。**不要把它拿掉**，也不要因為「反正有預設」
就不寫 `color` —— 在反色底上（角落那顆章、確認按鍵）還是要自己指定
`inverseOnSurface`。

相關的兩條配色規則：

- **遮罩用 `scheme.scrim`，不要用 `inverseSurface`。** `inverseSurface` 的意思是
  「跟目前主題相反的表面」，深色模式下它是**亮的** —— 拿來當遮罩會把背景刷亮，
  面板反而變成畫面上最暗的一塊。`scrim` 兩個主題都指定成 `Paper.Ink`，永遠是壓暗。
- **浮在遮罩上的面板用 `surfaceContainerLow` 而不是 `background`。** 深色模式下
  `background` 跟壓暗後的背景幾乎同色，面板會讀成一個黑洞。

## 欄位與按鍵：形狀就是層級

所有輸入框走 `Common.kt` 的 **`NutriTextField`**：完整外框 ＋ 一條比其他三邊重的
3px 底規線，聚焦時外框轉墨色、底線轉朱紅。前一版是「只有底線、沒有外框」
（M3 第一版的樣式），Google 後來自己的研究把它改掉了 —— 沒有封閉邊界時使用者
辨認「哪裡可以打字」明顯變慢。新畫面直接套 `NutriTextField`，不要自己疊
`OutlinedTextField` 或 M3 的 `TextField`。

按鍵**不要全部做成同一種矩形**，形狀本身在講層級（全部在 `Common.kt`）：

| 元件 | 形狀 | 用在哪 |
|---|---|---|
| `StampButton` | 印章（滿版墨底＋內縮 4dp 細框） | 主要動作，一個畫面只有一顆（成對動作例外，見下）。`color` 可換底色、傳 `Color.Transparent` 得到空心章、`destructive = true` 得到空心紅章 |
| `PillButton` | 藥丸 | 就地確認（收鍵盤、查詢、重試） |
| `TextAction` | 純文字 | 次要出路（貼上、改成手動輸入） |
| `RoundKey` | 圓章 | 自製數字鍵盤、份數步進 |
| `BallotRow` / `MealPicker` | 圈選（圓） | **單**選（餐別、外觀、模型） |
| `SquareCheck` | 打勾框（方） | **複**選（AI 確認畫面） |
| `CircleIconButton` | 線框圈 | 圖示鈕 |

單選是圓的、複選是方的，這個對比要守住 —— 形狀本身就在講「能不能多選」。

**「一個畫面只有一顆章」的唯一例外是成對的動作**（設定頁的匯出／匯入）：它們是同一件
事的兩個方向，做成不同形狀反而像在說其中一個比較次要。那種情況兩顆都是章、共用一段
敘述、共用一行結果訊息，退一階的那顆傳 `color = NutrientColors.StampSecondary`
轉深灰。**不要用「灰掉」的手法去表達次要** —— 灰掉是保留給不能按的（那時候整顆退成
外框章）。深淺兩套的方向相反但關係一樣：淺色底上主章是墨黑、次要是暖深灰；深色底上
主章是亮米、次要退成中灰，兩邊都還壓得住 `inverseOnSurface` 的字。

## 有輸入的畫面一律能點空白處收鍵盤

搜尋、常吃／文字輸入、掃條碼、輸入營養素（編輯表單）、設定的每日目標 —— 只要畫面上
有欄位，點輸入框與鍵盤以外的空白處就要收鍵盤，回到沒在打字的樣子，**已經打的字與
數值都保留**（清掉的話誤觸一下就等於白打，比沒這個功能還糟）。不做的話唯一的出口是
系統返回鍵，而那會把整個畫面關掉；在編輯表單更嚴重，連填到一半的東西都會不見。

一律套 `Common.kt` 的 **`Modifier.dismissKeyboardOnTap()`**，**不要自己疊一層
`clickable`** —— `clickable` 會把整片區域都變成可點的、蓋掉底下每一列自己的點擊，
還會帶進 ripple 與無障礙焦點。它用的是 `detectTapGestures`，而手勢在 Main pass 是
子先父後：列、分頁標籤、按鍵與輸入框自己會先消費掉，能傳上來的就只剩真正的空白處，
所以鍵盤開著時清單照樣直接可點。捲動也不會誤觸發（`scrollable` 一拖就消費掉位移，
tap 會被取消）。

兩件會咬人的事：

- **掛在 `Scaffold` 的 modifier 上**，報頭那條空白才算數。**唯一的例外是編輯表單**：
  它底部那塊就是自製數字鍵盤，掛整張會連按鍵之間的縫都算成空白，瞄歪一點就把鍵盤
  關掉 —— 所以那裡掛在內容那層。
- **自製數字鍵盤不吃焦點，`clearFocus()` 收不到它**，要用 `onTap` 參數自己把
  `focused` 清掉（`dismissKeyboardOnTap { focused = null }`）。對使用者來說那和系統
  鍵盤是同一件事，收的方式不該分兩種。`onTap` 用 `rememberUpdatedState` 包著，因為
  `pointerInput(Unit)` 只會啟動一次，直接捕捉 lambda 會永遠停在第一次組成的那一份
  —— 和今日頁分頁器那個 `rememberUpdatedState` 是同一類陷阱。

驗收不要只看畫面像不像，對 `adb shell dumpsys input_method` 的 `mInputShown` 看
true / false；數字鍵盤看「正在填：<欄位>」那行在不在，並且要另外試**點按鍵之間的縫**
（不該收）與**鍵盤開著點清單列**（該照常進下一頁）。

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

**今日頁的日／週分頁器有四個好踩的坑：**

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
- **兩個分頁器都預設 `beyondViewportPageCount = 0`**，滑到還沒保留住的頁面
  就是全新的 composable。`DayPage`／`WeekRow` 讀資料庫的那行如果直接寫
  `collectAsState(initial = emptyList())`，每次重建都會先畫空狀態、等 Room
  的 Flow 真正吐出資料才跳回正確畫面——肉眼看就是換頁閃一下（`DayPage` 那邊
  還會因為 `LazyColumn` 的 `animateItem()` 把「空狀態換成真資料」誤判成新增，
  多播一次不該出現的淡入動畫）。修法是在**比分頁頁面長壽的那一層**
  （`TodayScreen` 對 `entriesCache`、`WeekStrip` 對 `weekTotalsCache`）養一個
  `mutableStateMapOf<LocalDate, T>()`，`DayPage`／`WeekRow` 改用
  `produceState(initialValue = cache[key] ?: emptyList())` 讀快取當第一畫面，
  收到新資料才更新快取——同一天／同一週只要被看過一次，之後不管從哪個角落
  （包含 `WeekPageContent` 借位畫的鄰週預覽）重新進來都不用再等資料庫。真正
  第一次看到的日期／週還是會空一下，那是誠實的「還沒查到」，不用修。
  這個快取只影響第一畫面，不是凍結的唯讀快照——底下的 Flow 訂閱照樣即時反映
  新增／編輯／刪除，改這段之後要跟著測「加一筆／刪一筆之後數字有沒有立刻更新」，
  不能只測「換頁不閃了」就算過。

## CSV 是一種對外介面，不是隨手產生的報表

匯出／匯入是這個 app **唯一**的備份路徑（沒有雲端），所以那個檔案的格式要當成
介面看待：

- **欄位名稱本身就是格式。** `CsvImport` 靠名字對應欄位而不是靠位置 —— 使用者
  可能在試算表裡調過欄序，也可能拿的是舊版匯出的檔。**改欄位名等於讓舊檔匯不
  回來**，要加欄位就往後加。`CsvRoundTripTest` 守著這件事。
- **去重鍵是「日期＋名稱＋份量＋記錄時間」**（`CsvImport.dedupeKey`）。時間那一項
  不能省：同一天吃兩份一模一樣的東西是完全合理的，少了它會把使用者真的記過兩次
  的東西吃掉一筆。而且比的是**格式化後的時間字串**不是 epoch 毫秒 —— CSV 只寫到
  秒，直接比毫秒的話匯出再匯入永遠對不上，去重等於沒做。
- **匯入一定要先停在確認面板。** 和 AI 辨識同一條規則：外部來的資料不直接入庫。
  差別是不逐筆勾選（CSV 的數字是使用者自己記的，不是模型猜的），要確認的只有
  「這一批是什麼、會加幾筆、略過幾筆」。
- **缺資料維持 `null`。** 匯入和資料庫同一條規則，空白欄的意思是「沒標示」，
  補 0 之後就再也分不出來了。
- **壞掉的資料列跳過並回報，不讓整份檔案失敗。** 手改過的檔常常只有一兩列有問題。
  但表頭缺「日期」或「食物名稱」是另一回事 —— 那根本不是這個 app 的檔，直接擋掉。
- **選檔的 MIME 用 `*/*`，不要收緊。** 各家檔案管理員回報 CSV 的 MIME 從
  `text/csv`、`text/plain` 到 `application/octet-stream` 都有，篩太緊會讓使用者
  看到自己的檔案是灰的、以為壞了。解析不出來時有明確訊息接住，那才是該擋的地方。

`CsvExport` / `CsvImport` 都是純函式、不碰 Android API：格式對不對用單元測試看就
知道，不必為了驗證跑一次完整的 SAF 流程。

## Google Drive 備份：授權、範圍、與那支精靈

雲端備份是**選配**：不連結的話 app 完全不碰網路，也不會排任何背景工作。連結之後
每天備份一次到 Drive 主頁的 `NutriLog/`，一天一個日期檔、只留最近 30 天
（`DriveBackup.namesToPrune`，有測試守著 —— 刪錯了使用者不會馬上發現，等到要還原
才發現備份不見就來不及了）。

**備份內容就是本地匯出的同一份 CSV**，不是另一種雲端格式。還原也走
`CsvImport` 同一條路、同一套去重、同一個確認面板。這樣使用者可以自己去 Drive 下載
那個檔、用試算表打開、或用本地匯入讀回來 —— 就算這個 app 哪天不在了，資料也不會被
鎖住。**不要為了雲端另外設計一種格式。**

四件會咬人的事：

- **範圍只能是 `drive.file`。** 它只碰得到 app 自己建立的檔案，而且**不是 Google
  定義的受限範圍**，不需要付費安全評估（`drive` / `drive.readonly` 需要，那要跑好
  幾週）。要加功能時先確認新需求能不能用 `drive.file` 做到，不要順手把範圍放寬。
- **授權走 `Identity.getAuthorizationClient`，不是 `GoogleSignIn`**（已淘汰）。
  差別不只新舊：這個 app 沒有帳號系統，要的從來不是「這個人是誰」而是「能不能寫進
  你的 Drive」。帳號 email 是事後跟 Drive 問的（`DriveClient.accountEmail`），
  不是登入拿到的。
- **app 裡沒有任何 client id，也不需要有。** Android 的 OAuth client 是靠
  「套件名 + 簽章 SHA-1」認的，所以設定全在 Google Cloud Console 那一側，
  跑 `tools/setup-google-drive.sh`（bash，同 setup-signing.sh）。
  **debug 與 release 兩組 SHA-1 都要註冊**，一個 client 只放得下一組，要建兩個。
  只註冊 debug 的話：自己測都正常，使用者裝了正式版一按就失敗。
- **release 的 SHA-1 沒辦法從 GitHub secret 讀回來** —— secret 是唯寫的。改成從
  已發佈的 APK 讀（`apksigner verify --print-certs`）。不要用
  `keytool -printcert -jarfile`：minSdk 26 讓 AGP 關掉了 v1(JAR) 簽章，那個指令
  會失敗。

背景排程用 **WorkManager 而不是 AlarmManager**：Doze 底下 alarm 會被延到不確定的
時間，而且開機後不會自己回來。代價是「一天一次」是大約值，對備份完全夠。失敗一律
`Result.retry()`（沒網路、權杖過期都會自己好），只有「需要重新授權」回 `failure`
—— 背景沒有畫面可以問，重試再多次也一樣。

**`BackupWorker.schedule()` 的 `setInitialDelay` 不能拿掉**，它扛著兩件事：

一、**擋掉「排程當下立刻跑一次」**。週期性工作預設會這樣，而排程發生在「連結
Drive」那一刻 —— 那時候使用者可能正看著還原的確認面板還沒決定。備份檔名是當天
日期，那一次立刻執行會把雲端那份完整的紀錄蓋成新手機上空空如也的狀態，**正好毀掉
他要救回來的東西**。這個 bug 真的發生過：logcat 的 `WM-WorkerWrapper` 顯示 worker
在按下連結的同一秒就跑完了一次備份。連結時該不該立刻備份由 `connectDrive` 自己
判斷（有東西可還原就不備份）。

二、**把週期對齊到凌晨 3 點**（`minutesUntilNextRun`，純函式、有測試）。固定延遲
一天的話，錨點會是「使用者按下連結的那個隨機時刻」—— 卡在傍晚的話，那份以當天
日期命名的檔案永遠只有半天的內容。對齊之後每個日期檔就是「前一天結束時的完整狀態」。

**但不要期待它準時。** 凌晨手機多半在 Doze 深睡，實際執行會被延到裝置下次醒來
（通常是早上第一次拿起手機）。WorkManager 保證的是「大約一天一次」而不是準點；
看到執行時間是早上八點不代表壞了。驗排程對不對不要等一天，看
`adb shell dumpsys jobscheduler` 裡那個 job 的 `Minimum latency`。

還有一個和程式無關但會讓人查很久的行為：**使用者在系統設定裡「強制停止」app 之後，
Android 會把它的排程一起取消**，要等下次手動打開 app 才恢復。某些廠商的省電管理也
會這樣做。

**「連結 Google Drive」順便做還原**，不是另外一顆按鈕。換手機時使用者按那顆鈕想要
的是「把紀錄接回來」，不是「開始備份」；而且還原走的是本地匯入同一條路
（`previewOf` 共用，含同一套去重），外部資料一樣要過確認面板。

**使用者按取消不是錯誤。** GMS 用狀態碼 `CommonStatusCodes.CANCELED` 表示，
要特別認出來把訊息清掉；照著丟 `Drive 出錯：User cancelled flow` 會讓人以為壞了。

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
