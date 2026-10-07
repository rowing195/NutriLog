# 網站截圖：示範資料與重拍

`site/public/shots/` 的截圖全部是本機模擬器上灌示範資料拍的。雲端 session 拍不了：容器沒有 `/dev/kvm`
（模擬器跑不起來），`dl.google.com` 也被網路政策擋住。操作模擬器用 `tools/emu.ps1` 與 `tools/ui.ps1`
（見根目錄 CLAUDE.md）。

## 規格

- 原圖 1080×2400，切掉上方 118px 狀態列與下方 60px 手勢條，縮成 **720×1481 WebP**。
- **檔名一字不差**放進 `site/public/shots/` 就會換上，程式不用改；缺圖時網頁顯示「虛線框＋檔名」。
- 系統狀態列是網頁自己畫的（手機框裡的 `.statusbar`，時間跟報頭時鐘同步），所以截圖不留狀態列。
- 畫面上不能出現帳號 email 或 API 金鑰。

## 示範資料：文案裡的數字都從這裡來

資料在模擬器的 **`com.watson.nutrilog`**（不是 `.debug`）。「今天」＝2026-10-06（週二），每日熱量目標 1944：

| 時間 | 餐 | 品項 | kcal | 累計 |
|---|---|---|---|---|
| 07:40 | 早 | 無糖豆漿 150、茶葉蛋 75 | 225 | **225** |
| 12:30 | 午 | 雞腿便當 850、無糖綠茶 0（蘋果 104 在確認畫面**取消勾選**，沒入庫） | 850 | **1075** |
| 15:10 | 點心 | 麥當勞大麥克（真的按了「AI 查」：503，蛋白 26、脂肪 25、碳水 43） | 503 | **1578** |
| 19:20 | 晚 | 白飯 1.5 碗 (375 g) 525、燙青菜 80 | 605 | **2183** |
| 21:00 | — | 跑步：量到 600，回補 50% → **目標 +300 → 2244，還有 61** | | |

- 運動 +300 是 `daily_health_metrics` 的 10-06 那一列（模擬器上不要真的授權健康連線，見 CLAUDE.md〈健康連線〉）。
  九月另有四次跑步：9/8 340、9/15 360、9/22 330、9/29 355。
- 九月月曆：記錄 25 天、平均 1761、超標 3 天（12、17、25 號），沒記的是 6、13、19、20、27 號。
- 「AI 估 540」不是在模擬器上測的，是 README〈查網路〉記的實測值（美國規格）。

## 開拍前

1. **先備份 app 資料**（`adb exec-out run-as com.watson.nutrilog tar cf - databases files > bak.tar`）。
   下面〈會咬人的事〉有兩件會動到它。
2. **模擬器日期撥回 2026-10-06**，不然今日頁是空的一天。`adb shell settings put global auto_time 0`，
   再 `adb root` 後 `adb shell date 100619302026.00`（google_apis 映像可以 root）。
3. 確認今日頁是 2183 kcal、目標 1944、運動 +300、還有 61。

## 每一張

深淺用系統的深淺模式切（`adb shell cmd uimode night yes|no`），app 的外觀設定是「跟隨系統」。

| 檔名 | 深淺 | 畫面 | 怎麼到／注意 |
|---|---|---|---|
| today-morning | 淺 | 今日頁，四餐都「還沒記」 | 拍的時候當天還沒有紀錄；要重拍得先刪掉 10-06 的紀錄，拍完從備份還原 |
| library | 淺 | 常吃頁，未輸入 | 記一筆 → 常吃／文字輸入 |
| library-query | 淺 | 常吃頁打「烤肉」，只剩煎烤豬肉排 | 中文打不進 `input text`：主機 `Set-Clipboard "烤肉"`（模擬器同步主機剪貼簿），長按輸入框貼上。**等「pasted from your clipboard」提示消失、點空白處失焦再拍**（聚焦時底下那段說明會收起來） |
| review-lunch | 淺 | 確認辨識結果：雞腿便當✓、無糖綠茶✓、蘋果☐，午餐 | 真的拍照辨識過 |
| ai-stamps | 淺 | 常吃頁打「麥當勞 大麥克」、找不到，底下 AI 估／AI 查兩顆章 | 同 library-query 用剪貼簿輸入 |
| review-bigmac | 淺 | 確認辨識結果：麥當勞大麥克 503，點心 | 真的按了「AI 查」 |
| portion | 淺 | 編輯表單：白飯 1.5 份、1.5 碗 (375 g)、525 kcal、晚餐 | |
| swipe | 淺 | 今日頁，「無糖綠茶」往左滑開露出刪除塊 | 往下捲到營養素那一區貼著週長條（**不要捲到底**：刪一列頁面變短，捲動位置會被夾回、上緣切到半截）。`adb shell input swipe 800 Y 350 Y 400`。選 0 kcal 的綠茶，熱量才維持 2183 |
| undo | 淺 | 同一頁按下刪除後，左下角的復原章 | 同一個捲動位置。`adb shell "input tap 刪除; sleep 2.3; screencap -p /data/local/tmp/u.png; input tap 復原"`：倒數線剩一半時拍，**拍完立刻復原** |
| today-night | 深 | 今日頁 2183、目標 1944、運動 +300、還有 61 | |
| exercise | 深 | 點「運動 +300」打開的運動消耗明細 | 沒授權健康連線時只有吃了／運動／回補那幾列，這樣就對 |
| water | 深 | 點「飲水」打開的飲水明細 | |
| calendar-today | 深 | 十月月曆，6 號那格 2183 | |
| calendar-month | 深 | 九月月曆，底下有「回到本月」章 | |
| report | 深 | 九月月報 | 自己寫一份 `files/monthly_reports/2026-09.json` 塞進去，不打 API（內文照 `MonthlyAggregator` 的 prompt 規則寫，使用者看過）。**統計那塊要帶運動**：拍的時候暫時把 DataStore 的 `readExerciseCalories` 改成 false，報表才會讀快取裡那四次跑步（運動消耗 每天 46／每日消耗 1,666／熱量收支 +2,139）。UI 改不了：沒授權時開關本來就顯示關 |
| settings | 深 | 設定選單 | 雲端備份那列要是「未連結」，連著的話會顯示 email |
| barcode | 深 | 掃條碼：手動輸入條碼查到一項商品 | 用台灣比菲多「質立希臘式優格（無加糖）」`4710784965544`。很多台灣商品在 Open Food Facts 的 `product_name` 是機器翻的英文（光泉鮮乳是「Frankincense Family High Quality Pure Milk」），換商品前先查 API |
| provider-switch | 深 | 辨識失敗後升上來的「換一家再試」面板 | 走「從相簿選」→ 斷網（`svc wifi disable`、`svc data disable`）→ 送出辨識，讓它是**拍照**失敗：說明句才是「拍照要用看得懂圖片的模型…」。失敗畫面不顯示照片，用哪張都行。**拍完把網路開回來** |
| csv-import | 深 | 「要匯入這些紀錄嗎？」確認面板 | CSV 用九月的紀錄（146 筆，會略過）＋同一批往前推一個月當八月（146 筆，會新增），面板才同時有兩種數字。`adb push` 到 `/sdcard/Download` 後要 `MEDIA_SCANNER_SCAN_FILE` 才選得到。**按取消**，不要真的匯入 |

## 會咬人的事

- **要用目前的程式拍，得建一顆同套件名的版本。** 示範資料在 `com.watson.nutrilog`，debug 版卻是 `.debug` 而且標題是
  「肥胖日記 測試版」。暫時註解掉 `app/build.gradle.kts` 的 `applicationIdSuffix`／`versionNameSuffix`、把
  `app/src/debug/res/values/strings.xml` 的 `app_name` 改成「肥胖日記」，`assembleDebug -PappVersionName=<最新版號>`
  後 `adb install -r -d` 蓋上去（同 debug 簽章，資料保留）。**建完立刻 `git checkout` 那兩個檔、再正常 `assembleDebug`
  一次**，不然 `app-debug.apk` 是這顆不帶 `.debug` 的，交給使用者會出事（見 CLAUDE.md 開頭那段）。
- **打開 AI 報告頁會把 `daily_health_metrics` 寫成 0。** 沒授權讀取時 `readDailyActivity` 回 0 而不是 null，
  週報／月報的統計照寫進快取，整個月（加上比較的上個月）都被蓋掉。開報表頁之前先備份，拍完還原。
- **寫二進位檔進 app 的沙盒不要用 `adb shell run-as … 'cat > 檔' < 檔`，會被截斷**（77 KB 的資料庫只寫進 21 KB）。
  資料庫、DataStore 一律 `adb push` 到 `/data/local/tmp`，`adb root` 後 `cp`、`chown u0_a211:u0_a211`、`restorecon`。
  讀出來用 `exec-out` 沒問題。

## 拍完

1. App 資料換回備份（資料庫與 `files/datastore/nutri_prefs.preferences_pb`），刪掉塞進去的月報與 CSV。
2. 模擬器改回自動時間（`auto_time 1`）、網路打開、`adb unroot`。
3. 瀏覽器看手機寬度（375）與桌面寬度（1440），**往下捲再倒捲回去**：每張都換掉了虛線框、比例沒被拉長、
   桌面版右邊手機換畫面的方向對（`index.html` 裡 `.shot` 的 `data-from`）。
