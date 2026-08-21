# NutriLog 交接筆記

最後更新：2026-08-19

## 現況

第一版功能齊全，可以正常使用。手動輸入、條碼查詢、歷史、設定都已在模擬器上跑過完整流程。
拍照辨識的整條管線（壓縮 → base64 → HTTPS → 錯誤對應）已驗證，
**但還沒有用真的 API key 跑過一次成功的辨識**（見下方待驗證）。

## 已驗證（emulator-5554，AVD `localreader_api35`，Android 15 / API 35）

| 項目 | 結果 |
|---|---|
| 手動新增一筆 | 出現在當天清單，頂部四條進度條同步更新 |
| 編輯既有紀錄 | `@Upsert` 保留原 id，總和不變（沒有變成新增一筆） |
| 刪除 | 確認對話框 → 刪除 → 回到今天並顯示空狀態 |
| 換日 | 前一天為空、總和歸零；「回到今天」資料回來 |
| force-stop 後重開 | 紀錄與設定都還在 |
| 設定：每日目標 | 改成 1500 後進度條分母立刻變 |
| 設定：進階營養素開關 | 開啟後今日摘要多出糖／鈉／纖維／飽和脂肪一行 |
| 條碼線上查詢 | `3017624010701` → Nutella / Ferrero / 539 kcal per 100 g |
| 條碼份量換算 | 填 30 g → 161.7 kcal、蛋白 1.9、脂肪 9.3、碳水 17.3、鈉 12.9 mg |
| 條碼缺欄位 | Nutella 沒有 fiber → 膳食纖維欄留空（不是 0） |
| 條碼離線快取 | 關掉 wifi + data 後查同一組條碼仍帶得出來，顯示「本機已存的資料」 |
| 條碼查無商品 | `0000000000000` → 「查無此商品，改用手動輸入吧」＋手動輸入按鈕 |
| 沒設 API key 就拍照 | **不會開相機**，直接顯示提示與「去設定」按鈕 |
| Gemini 錯誤對應 | 填假 key → Google 回 400 → 畫面顯示「請求被拒絕，API key 可能不正確」，原始 JSON 只進 logcat |
| 資料落地 | `nutrilog.db-wal` 內含 food_entries / cached_products 與實際資料；DataStore 內含設定 JSON |
| 月曆歷史 | 12 天測試資料正確落格，空白日、超標紅色、今日外框、未來日壓淡都符合預期 |
| 月曆連動 | 新增一筆 150 kcal 後，該格由 2180 變 2330 |
| 月曆點日 | 點 8/3 跳回今日頁並顯示該天；「回到今天」可返回 |
| 文字辨識 | 範例晶片填入 → 送出 → 走 Gemini（假 key 驗到 400 與錯誤處理）|
| 文字辨識重試 | 「重試」重跑的是文字查詢，不是照片（retryAnalysis 依 lastSource 分派）|
| 深色模式 | 卡片與背景有層次，不再是預設紫 |
| 四餐固定顯示 | 空的日子四格都在、皆為 0 kcal，各有可點的空白列 |
| 空區塊預選餐別 | 從「晚餐」空格點進去存檔，落在晚餐而非 guessMeal() 的早餐 |
| AI 確認畫面選餐別 | 選「點心」後兩筆假結果落在點心（450 kcal），晚餐不受影響 |
| CSV 匯出 | SAF 存檔 → 「已匯出 15 筆紀錄」；檔案前 3 bytes 是 EF BB BF（BOM）|
| CSV 內容 | 缺資料欄位為空、餐別與來源翻成中文、依日期與時間排序 |
| CSV 跳脫 | 名稱含逗號與雙引號的紀錄，用 Python csv 模組回讀後每列都是 14 欄，沒被拆開 |
| 乾淨 clone | 從 GitHub clone 後只設 JAVA_HOME/ANDROID_HOME 即建置成功，不需 local.properties |

## 待驗證（需要使用者提供的東西）

1. **真的 Gemini API key 跑一次成功辨識。**
   目前只驗到「請求組裝正確、Google 收到並拒絕假 key」。
   還沒實際跑過的部分是：`responseSchema` 結構化輸出解析、確認畫面的資料呈現、
   勾選後批次入庫。填好 key 後拍一張食物照走一遍即可。

2. **Google Code Scanner 在實機上的掃描 UI。**
   模擬器上叫不出來（見下方已知限制），只驗到「失敗時顯示正確訊息並導向手動輸入」。

## 已知限制

### Code Scanner 在模擬器上不能用

`GmsBarcodeScanning` 要從 Play 服務**動態下載**掃描模組，而測試用的
`localreader_api35` 是 `google_apis` 映像（有 Play 服務、沒有 Play 商店），
下載會失敗。畫面會顯示「這台裝置無法叫出掃描器，請用手動輸入條碼」。

這正是**手動輸入條碼被列為必要功能而不是備案**的原因 ——
它走完全相同的查詢與入庫路徑，所以在模擬器上仍能完整驗證條碼功能。
實機上掃描 UI 應該正常，請自行確認一次。

### 發佈前一定要先設好簽章 secret

沒有 `keystore.properties` 時，本機 `assembleRelease` 會退回 debug 簽章
（已驗證：`apksigner verify` 顯示 `CN=Android Debug`）。本機與 CI 的 debug 簽章
還彼此不同，所以兩邊的 APK 連互相覆蓋都做不到。

CI 已經加了防呆：**推 tag 但沒設 `KEYSTORE_BASE64` 時 workflow 直接失敗**，
不會產出 debug 簽章的 Release。理由是這個錯誤實質不可逆 ——
之後補上正式金鑰要更新就得先解除安裝，而本 app 的紀錄全在本地、沒有雲端備份，
解除安裝就是整份飲食歷史消失。

產金鑰與設 secret 的步驟見 README 發佈章節。

### 429 的真正成因是 API 沒啟用，不是配額

實際踩到：帳號有 9000+ 美元的 GCP 贈送額度，第一次呼叫就回 429。
**最後解法是到 GCP Console 把 Generative Language API 啟用起來**，
啟用後就正常了。專案沒啟用該 API 時的拒絕也會以 429 呈現，
所以「第一次用就 429」要先去 Console 確認 API 有沒有開，再談配額。

順帶一提，贈送額度本來就幫不上忙：

Google 明文把 Gemini API 排除在贈送額度之外 ——
「Gemini API usage costs are specifically excluded from the $300 Google Cloud
Free Trial」，而 2026-03-02 之後開的帳號連 $300 Welcome credit 也不能付
Gemini API。所以掛著一大筆 GCP 額度的專案**仍然停在 Free tier**，
撞的是 Free tier 的每日請求上限，跟「額度剩多少」無關。

要離開 Free tier 只有一條路：連結帳單帳戶並**預付至少 $10**。
在 AI Studio 的 Projects 頁 Billing Tier 欄可以看目前層級。

這也是為什麼 `GeminiClient.explain()` 要把 Google 的 `error.message` 與
`quotaId` 原文帶到畫面上：只說「已達用量上限」會讓人以為是自己用太多，
而真正的資訊在 quotaId（例如 GenerateRequestsPerDayPerProjectPerModel-FreeTier）。

### Gemini 3.x 預設開 thinking，會把請求拖到逾時

實際踩到：換成 gemini-3.7-flash 之後一直斷線。3.7-flash 的預設是
thinking **medium**，而「看照片認食物」根本不需要推理，
純粹是白等 —— 原本 60 秒的 readTimeout 撐不住。

`GeminiClient.thinkingConfigFor()` 把它壓到最低，但**兩個系列的參數不能混用**：

| 模型 | 參數 | 值 |
|---|---|---|
| gemini-3.x | `thinkingLevel` | `minimal`（flash 系列無法完全關閉） |
| gemini-2.5-flash | `thinkingBudget` | `0`（真的完全關閉） |
| gemini-2.5-pro | 不送 | 下限是 128，送 0 會回 400 |
| 其他/認不得 | 不送 | 亂送不支援的欄位一樣是 400 |

模型名稱是使用者可自由編輯的，所以這個判斷必須容錯。
已用矩陣驗證八種名稱（含大小寫與前後空白）都落在正確分支。

readTimeout 也從 60 秒拉到 120 秒留餘裕。

### 503 model overloaded：Gemini 自己忙不過來

實際踩到：gemini-3.7-flash 回 503。這跟 key、配額、模型名稱都無關，
是 Google 那邊當下負載太高，剛推出的熱門模型特別容易遇到。

`GeminiClient.sendWithRetry()` 對這類暫時性失敗自動重試（最多 3 次，
退避 1.5s、3s）。**只重試 5xx 與連線層 IOException／逾時**；
4xx 一律不重試 —— key 錯、模型名錯、配額不足重試幾次都一樣，
只會讓使用者多等好幾秒才看到同一則錯誤。

退避刻意保守：使用者正盯著「辨識中」的轉圈，拖太久還不如早點說失敗。

已驗證：假 key（400）只試 1 次就失敗；斷網時三次嘗試的時間戳為
59.191 / 00.696 / 03.699，退避間隔正好是 1.5s 與 3.0s。

### Open Food Facts 對台灣商品收錄不完整

查無資料是常態而不是例外，所以「查無 → 手動輸入」那條路必須保持順暢。
另外 OFF 讀取端點限制每個 IP 每分鐘 15 次，這就是 `cached_products` 存在的理由。

## 踩過的坑

### 只蓋 primary，整個 app 會是紫的

Material3 的預設 baseline 是紫色系。第一版的 `Theme.kt` 只覆蓋了
`primary`/`secondary`，結果背景、卡片、進度條軌道全是淡紫灰，
跟綠色主色互相打架 —— 截圖出來一看就知道不對。

修法是把 `surfaceContainer*` 整族、`surfaceVariant`、`outline*`、
`background`/`surface` 全部指定。**少蓋一個就會有元件固執地維持預設紫。**

### ui.ps1 的 back 不一定只是收鍵盤

`& $ui back` 在鍵盤開著時收鍵盤，鍵盤沒開時會觸發 `BackHandler`，
把整個編輯畫面關掉、草稿一起丟掉。測試時看起來就像「儲存沒反應」。

**填表單後要按儲存，用 `scroll down` 比 `back` 可靠。**

### 大量測試資料用 sqlite3 灌，不要用 UI

模擬器有 `/system/bin/sqlite3`，可以直接寫進 app 沙箱：

```bash
adb shell am force-stop com.watson.nutrilog   # Room 開著 WAL，兩邊同時寫會壞
adb push seed.sql /data/local/tmp/seed.sql
adb shell "cat /data/local/tmp/seed.sql | run-as com.watson.nutrilog sqlite3 databases/nutrilog.db"
```

`adb push` 的**本機路徑要用 Windows 格式**（`C:/...`）——
在 Git Bash 裡設了 `MSYS_NO_PATHCONV=1` 之後，`/c/...` 不會被轉換，
adb 是 Windows 執行檔，看不懂那個路徑。

### 換 app 圖示：自適應圖示的安全區

圖示是 `mipmap-*/ic_launcher_foreground.png`（五種密度）+
`values/ic_launcher_background.xml` 的底色，由
`mipmap-anydpi-v26/ic_launcher*.xml` 組起來。

**內容佔 108dp 畫布的比例是 0.62**，這個數字是試出來的：
啟動器只顯示中央 72/108，而圓形遮罩會再削掉四角。
0.68 時角色的頭髮兩側會被圓形遮罩切掉，0.56 又太小顯得空。
換圖時務必先套圓形遮罩看過再定案，別只看方形預覽。

原圖是去背 PNG（500x500 RGBA），內容幾乎填滿整張、四邊沒有留白，
所以縮放前要先 getbbox() 裁到實際內容再置中，否則邊距會不對稱。

底色從原本的深綠改成暖奶油 `#F8EDE1`：角色是淺膚色配藍灰頭髮，
壓在深綠上整個糊在一起。

放 mipmap 而不是 drawable 是因為啟動器可能取用其他密度的圖示，
mipmap 不會被密度裁切最佳化砍掉。

### emu.ps1 deploy 曾經會謊報成功

模擬器掛掉時 `adb install` 印 "device not found"，但腳本照樣印
"deployed to emulator-5554" —— 接下來幾分鐘都在對著一個根本沒安裝上去的版本除錯。
已加上 `$LASTEXITCODE` 檢查。

### 截圖一定要在 Bash 重導向

`adb exec-out screencap -p > x.png` 在 PowerShell 會被加上 BOM 並改寫位元組，
產出的檔案不是合法 PNG。和撈 DataStore/DB 是同一個坑。


### 鍵盤蓋住儲存鈕，而且點下去是打在鍵盤上

`enableEdgeToEdge()` + `Scaffold` 的 innerPadding **不包含 IME**。
一開始表單沒有 `imePadding()`，測試時「儲存」的座標正好落在鍵盤上，
結果那一下把數字打進了上一個聚焦的欄位（碳水 70 變成 706），
看起來卻像是「儲存沒反應」。已在 `EditEntryScreen` / `SettingsScreen` /
`BarcodeScreen` 的捲動容器加上 `.imePadding()`。

用 `tools/ui.ps1` 測表單時仍然要先 `back` 收鍵盤或 `scroll down`，
再點儲存。

### 掃描器失敗卻叫使用者去檢查網路

`BarcodeState.Failed` 原本只帶技術原因，由畫面統一加上「連線失敗，請檢查網路」前綴，
於是掃描器叫不出來時也會顯示網路錯誤。改成 `Failed(message)` 帶**完整句子**，
由失敗方自己把話講完。

### `sodium_100g` 的單位是公克

OFF 的鈉是公克，營養標示與使用者的直覺都是毫克。`OpenFoodFactsClient` 裡 ×1000。
漏掉的話 Nutella 會顯示「鈉 0.043 mg」。

### OFF 的營養素欄位型別不固定

同一個欄位有時是數字、有時是字串。宣告成 `Double?` 會讓**整筆商品**解析失敗，
所以 `nutriments` 收成 `Map<String, JsonElement>`，再用 helper 兩種都吃。

### 模擬器是 GMT

`guessMeal()` 用 `LocalTime.now()` 判斷餐別。模擬器跑 GMT，
台北下午一點在模擬器上是早上六點，所以預設餐別會是「早餐」。
**這不是 bug**，實機上會依裝置時區正確判斷。

### Room 主檔看起來是空的

Room 預設開 WAL。`nutrilog.db` 停在 4096 bytes（只有 header）是正常的，
資料在 `nutrilog.db-wal` 裡。驗證落地要抓 `-wal`。

### 版本相容

`ksp` 的版號前半段必須和 `kotlin` 完全一致（`2.0.21-1.0.28`），對不上直接建置失敗。

## 回歸清單

動到閱讀以外任何畫面後跑這幾項：

1. 手動新增 → 顯示 → 編輯（總和不能翻倍）→ 刪除
2. 換到前一天（應為空）→ 回到今天（資料還在）
3. `adb shell am force-stop com.watson.nutrilog` 後重開，紀錄與設定都還在
4. 條碼 `3017624010701` 查得到 Nutella
5. 沒設 key 時點「拍照辨識」不會開相機，而是顯示「去設定」
