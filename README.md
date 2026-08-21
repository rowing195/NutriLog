# 肥胖日記（NutriLog）

Android 每日飲食營養素紀錄器（Kotlin + Compose）。app 顯示名稱是「肥胖日記」，
專案代號維持 NutriLog —— package、repo、APK 檔名與簽章都綁在它身上。

四種輸入方式：自己輸入營養素、
拍食物照或打一句文字交給 Gemini 估算、掃商品條碼查 Open Food Facts。

**所有紀錄都存在手機本地**，沒有後端伺服器、沒有帳號。唯一的對外連線是
影像辨識與條碼查詢兩支公開 API。

---

## 功能

### 今日
- 上方可左右換日，回看或補登任何一天。
- 熱量做成環形儀表，中間顯示的是**還可以吃多少**而不是已經吃多少 ——
  餐前打開 app 想知道的是剩餘額度，「已攝取 1850」還要自己減一次。
  超標就改顯示超出多少並整圈轉紅（停在某個角度看起來像還沒吃完，意思正好相反）。
- 三大營養素並排成三個小卡，各自有細進度條，超標同樣轉紅。
- **早／午／晚／點心四格一律都顯示**，空的也留著並寫 0。
  只列有紀錄的餐別時，「今天還沒吃早餐」和「今天忘了記早餐」在畫面上
  長得一模一樣（兩者都是不存在）。固定四格之後空的那格本身就是提醒，
  點下去還能直接補登該餐、餐別已預選好。
- 點任一筆紀錄進去可以編輯或刪除。
- 開啟「顯示進階營養素」後，另外顯示糖、鈉、膳食纖維、飽和脂肪的合計。

### 四種輸入方式

| 方式 | 怎麼運作 |
|---|---|
| **輸入營養素** | 自己填數字。核心四項（熱量／蛋白質／脂肪／碳水）永遠可見，進階四項收在展開區。 |
| **拍照辨識** | 拍照或從相簿選 → 壓縮成 1024 px → Gemini 估算 → **確認畫面**逐項勾選後才入庫。 |
| **文字輸入辨識** | 打一句「CoCo 珍珠奶茶 大杯半糖」→ Gemini 估算 → 同一個確認畫面。規格沒講清楚時會回幾個常見選項讓你挑。 |
| **掃條碼** | 掃描或手動輸入條碼 → 先查本機快取，沒有才連 Open Food Facts → 填實際公克數自動換算。 |

所有路徑最後都匯流到同一張編輯表單或確認畫面，入庫前一定看得到、改得動，
**而且都能自己選要記進哪一餐**。AI 確認畫面原本是存檔當下才依時間猜餐別，
補登昨天的晚餐時會全部掉進點心 —— 現在猜一個當預設，但使用者可以改。

選單裡叫「輸入營養素」而不是「手動輸入」：後者和「文字輸入辨識」讀起來太像，
但兩者差很多 —— 一個是自己填數字，另一個是打食物名稱讓 AI 估。

### 歷史（月曆）
一格一天的月曆，格子裡直接寫當天熱量，底色深淺代表吃了多少、超標轉紅。

用月曆而不是清單，是因為月曆**看得出空白**：哪幾天忘了記、連續幾天超標，
一眼就有形狀。清單只會讓有紀錄的日子擠在一起，反而看不出中間漏了幾天。
未來的日期會壓淡 —— 它們永遠是空的，不該看起來像「忘了記錄」。

下方顯示當月記錄天數、平均熱量與超標天數。合計由資料庫 `GROUP BY` 算，
不把明細撈進記憶體。

### 搜尋與個人食物庫
右上角放大鏡，或新增選單的「從常吃的選」，都打開同一個畫面。

**沒輸入時**是從你自己的紀錄長出來的食物庫，兩頁可左右滑動切換：

| | 常吃 | 最近 |
|---|---|---|
| 排序 | 90 天內的次數 | 最後一次吃的時間 |
| 期間 | 只算 90 天 | 不限 |

聚合鍵是**名稱 ＋ 份量文字**，不是只看名稱 —— 份量文字正是規格所在
（大杯／中杯／半糖），而且它是文字、不會像 AI 每次估的數字那樣抖動。
用數字當鍵的話，同一杯中杯珍奶會因為三次估算值略有出入而散成三列。

點一項會跳出面板攤開完整營養素，按「加入」帶進**預填好的編輯表單**，
份量與餐別都還能改。

**有輸入時**整個換成逐筆搜尋結果，日期新到舊：

- 比對**名稱 ＋ 份量文字**，空白拆成多個關鍵字全部要命中。
  「珍奶 大杯」找得到 —— 兩個字分別落在名稱與份量欄位。
- 點一列**跳到那天的今日頁**（看得到當天全貌，而不只是那一筆）。
- 右側「＋」照這筆再記一筆。

加入的紀錄會落在**目前選的那一天**，跟其他新增路徑一致。不是今天的時候
畫面上會明講「加入的紀錄會記到 8月18日」—— 搜尋畫面看不到日期列，
不講的話使用者無從得知自己正在補登哪一天。

### 設定
Gemini API key、模型名稱、每日四項目標、進階營養素開關，以及 **CSV 匯出**。

### 匯出 CSV
把全部紀錄存成 CSV，用 Excel 或 Google 試算表打得開。
**這是唯一能把資料帶出手機的方式** —— 紀錄只在本機、沒有雲端備份，
換手機或誤刪 app 就沒了，所以匯出的定位是備份，預設全部匯出、不做日期篩選。

兩個細節值得知道：

- 檔頭有 **UTF-8 BOM**。少了它 Excel 會用系統 ANSI 解讀，中文全變亂碼。
- 缺資料的營養素是**空欄不是 0**。到了試算表更沒機會分辨
  「沒標示」和「真的是 0」。

存檔位置由系統選擇器決定（SAF），所以不需要任何儲存權限，
檔案也落在你自己看得到的地方，不會跟著 app 被解除安裝一起刪掉。

---

## 設定 Gemini API key

拍照與文字辨識需要你自己的 key：

1. 到 [aistudio.google.com](https://aistudio.google.com) 免費申請一把 API key。
2. 開 app → 右上角 **設定** → **Gemini API key** 貼上。

key 只存在這支手機的 DataStore 裡，**不會編進 APK**，也不會傳到 Google 以外的地方。
所以這個 APK 可以直接分享給別人，對方填自己的 key 就能用。

預設模型是 `gemini-3.7-flash`。想更省、更不容易遇到 503 可以改成 `gemini-3.5-flash-lite`。
（注意 `gemini-2.0-flash` 已經下架，填了會回 404。）

---

## 設計決策

### 為什麼紀錄用 Room，設定用 DataStore

飲食紀錄**逐日無上限累積**，而且核心查詢是「某一天的所有紀錄」與
「某個月的每日合計」—— 這是關聯式查詢。把幾年份紀錄序列化成一個 JSON 字串、
每新增一筆就整包重寫，是明確的錯誤選擇。

設定則只有一份、不需要查詢，用 DataStore 就好。兩種儲存方式共存是刻意的。

### 為什麼完全不需要相機權限

Manifest 裡**只有 `INTERNET` 一個權限**：

- 拍照 → `ActivityResultContracts.TakePicture()`，取景在系統相機 App 裡完成。
- 掃條碼 → Play 服務的 Google Code Scanner，掃描 UI 跑在 Play 服務的行程裡。
- 選相簿 → `PickVisualMedia`，本來就不需要讀取儲存空間的權限。

三者都是「別的行程取像，本 app 只拿結果」，所以一次執行階段權限請求都不用。

### 配色：整族 surface 都要自己指定

Material3 的預設 baseline 是**紫色系**的。只覆蓋 `primary` 的話，
背景與卡片仍然是淡紫灰，跟綠色主色互相打架 —— 第一版就是這樣，
整個 app 看起來是紫的。`Theme.kt` 因此把 `surfaceContainer*` 整族、
`surfaceVariant`、`outline` 全部指定成帶綠的中性灰。

同理，這裡刻意不用 Material You 動態取色：顏色在這個 app 裡有語意
（三大營養素各有固定色、超標轉紅），讓桌布決定色相會直接破壞那層意義。

### 為什麼模型結果一定要經過確認畫面

Gemini 給的是**估算值**。直接寫進資料庫等於在使用者的飲食紀錄裡塞模型自己編的數字。
確認畫面會顯示每一項的把握度，可以逐項取消勾選；存進去之後仍然可以點進去逐欄修改。

### 為什麼手動輸入條碼是必要功能而不是備案

Google Code Scanner 要從 Play 服務**動態下載**掃描模組，不是每台裝置都成功
（模擬器尤其常失敗）。手動輸入走的是完全相同的查詢與入庫路徑，
所以掃描器叫不出來時功能不會斷掉，只是多打幾個數字。

### 為什麼沒有 Retrofit

只有兩個服務。OkHttp + 既有的 kotlinx-serialization 手寫約 200 行就夠，
比拉進一整套 Retrofit + converter 單純。

### 沒有導航函式庫

畫面只有七個，而且除了「今天」以外都是「開一個、按返回就關掉」。
`sealed interface Screen` + `when` 分派就夠了，不需要真正的返回堆疊。

---

## 外部 API

### Open Food Facts

```
GET https://world.openfoodfacts.org/api/v2/product/{barcode}.json
```

- 讀取**不需要** API key，但**一定要帶自訂 `User-Agent`**，這是 OFF 明文要求的。
- 讀取端點限制 **每個 IP 每分鐘 15 次** → 這就是本機要存 `cached_products` 的原因。
- 營養素欄位（`energy-kcal_100g`、`proteins_100g`…）**經常缺漏**，
  缺的一律留 `null` 而不是補 0 ——「沒資料」和「真的是 0」必須分得開。
- `sodium_100g` 的單位是**公克**，存進資料庫時要 ×1000 換成毫克。
- 台灣本地商品收錄不完整，查無資料是正常情況。

### Gemini

```
POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent
```

- key 走 `x-goog-api-key` header 而不是 `?key=`：query string 會被各層代理與日誌記下來。
- 用 `generationConfig.responseMimeType` + `responseSchema` **強制結構化輸出**，
  否則模型會回夾著說明文字的 markdown code fence，就得自己剝字串而且隨時會變。
- 送出前一定要壓縮。原圖 12 MP base64 之後是 4 MB 起跳的請求，又慢又貴，
  而且對辨識準確度毫無幫助。
- **Gemini 3.x 預設開 thinking（medium）**，認食物根本不需要推理，卻會拖到逾時。
  `thinkingConfig` 把它壓到最低，但 3.x 用 `thinkingLevel`、2.5 Flash 用
  `thinkingBudget`，**混用直接 400**。
- 5xx 與連線逾時會自動重試 3 次（退避 1.5s、3s）；4xx 不重試，
  因為 key 錯、模型名錯、配額不足重試幾次都一樣。

---

## 建置

```powershell
$env:JAVA_HOME  = "C:\Program Files\Eclipse Adoptium\jdk-17.0.7.7-hotspot"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"

& "$env:LOCALAPPDATA\Android\tools\gradle-8.11.1\bin\gradle.bat" -p "C:\code\android app\NutriLog" assembleDebug
```

APK 在 `app/build/outputs/apk/debug/app-debug.apk`。

模擬器與部署：

```powershell
& "C:\code\android app\NutriLog\tools\emu.ps1" start    # 開模擬器並等 boot_completed
& "C:\code\android app\NutriLog\tools\emu.ps1" deploy   # build + 安裝
```

## 發佈

推一個 `v` 開頭的 tag，GitHub Actions 會建置並發佈 Release：

```bash
git tag -a v1.0 -m "第一版" && git push origin v1.0
```

**版號由 tag 決定**，不必手動改 `build.gradle.kts`：CI 會用
`-PappVersionName=<去掉 v 的 tag>` 和 `-PappVersionCode=<run number>` 覆蓋。
手動維護版號遲早會忘記，而症狀是「檔名是新的，裝置卻默默不更新」，很難查。

### 簽章是發佈的前提

repo 必須先設好四個 secret：`KEYSTORE_BASE64`、`STORE_PASSWORD`、
`KEY_ALIAS`、`KEY_PASSWORD`。**沒設的話推 tag 會直接讓 workflow 失敗**，
這是刻意的 —— 靜默退回 debug 簽章比失敗糟得多：

> Android 拒絕安裝簽章不同的更新。一旦發出 debug 簽章的版本，
> 之後補上正式金鑰要更新就必須先解除安裝，
> 而這個 app 的紀錄全部只在本地、沒有雲端備份，解除安裝等於整份飲食歷史消失。

### 產生金鑰：tools/setup-signing.sh

[`tools/setup-signing.sh`](tools/setup-signing.sh) 會一步步帶你走完六個階段：
產生金鑰 → 驗指紋 → 寫本機 `keystore.properties` → 設四個 GitHub secret → 備份提醒。
密碼你自己輸入（隱藏顯示），透過環境變數傳給 `keytool` 而不是命令列參數
（命令列參數在 process list 裡是全機器可見的）。

**這是 bash 腳本，要用 Git Bash 跑，cmd 和 PowerShell 都不能直接執行。**

Git Bash 視窗裡：

```bash
cd "/c/code/android app/NutriLog" && ./tools/setup-signing.sh
```

**注意 `./` 是相對路徑，得先 `cd` 進專案。** 路徑有空格，引號不能省。
不想換目錄就用絕對路徑，腳本會自己從所在位置推導出專案根目錄：

```bash
"/c/code/android app/NutriLog/tools/setup-signing.sh"
```

從 PowerShell 呼叫：

```powershell
& "C:\Program Files\Git\bin\bash.exe" "C:\code\android app\NutriLog\tools\setup-signing.sh"
```

從 cmd 呼叫：

```bat
"C:\Program Files\Git\bin\bash.exe" "C:\code\android app\NutriLog\tools\setup-signing.sh"
```

後兩者能跑，但主控台的 codepage 通常不是 UTF-8，框線字元可能會歪掉
（腳本輸出刻意全用 ASCII 英文就是為了這個）。**建議直接開 Git Bash。**

不想跑腳本就手動：

```bash
keytool -genkeypair -keystore release.jks -alias nutrilog \
        -keyalg RSA -keysize 2048 -validity 10000
base64 -w 0 release.jks | gh secret set KEYSTORE_BASE64
gh secret set STORE_PASSWORD; gh secret set KEY_ALIAS; gh secret set KEY_PASSWORD
```

**`release.jks` 一定要另外備份。** 弄丟它就再也發不出可以覆蓋更新的版本了。
GitHub secret 是唯讀不回的，不算備份。

## 技術規格

| 項目 | 值 |
|---|---|
| Kotlin / AGP / Gradle | 2.0.21 / 8.7.3 / 8.11.1 |
| minSdk / targetSdk | 26 / 35 |
| UI | Compose（BOM 2024.10.01）+ Material 3 |
| 資料 | Room 2.6.1（紀錄）+ DataStore Preferences（設定） |
| 網路 | OkHttp 4.12.0 + kotlinx-serialization |
| 條碼 | play-services-code-scanner 16.1.0 |
| 權限 | 只有 `INTERNET` |
