<div align="center" id="top">

<!-- HEADER STYLE: CLASSIC -->
<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png" width="140" style="position: relative; top: 0; right: 0;" alt="Project Logo"/>

# NutriLog

<em>離線記錄每日營養，資料只留在你手機裡</em>

<a href="https://nutri-log-lake.vercel.app/"><strong>展示頁面 →</strong></a>

<!-- BADGES -->
<img src="https://img.shields.io/github/license/rowing195/NutriLog?style=flat&logo=opensourceinitiative&logoColor=white&color=0080ff" alt="license">
	<img src="https://img.shields.io/github/last-commit/rowing195/NutriLog?style=flat&logo=git&logoColor=white&color=0080ff" alt="last-commit">
	<img src="https://img.shields.io/github/languages/top/rowing195/NutriLog?style=flat&color=0080ff" alt="repo-top-language">
	<img src="https://img.shields.io/github/languages/count/rowing195/NutriLog?style=flat&color=0080ff" alt="repo-language-count">
	<img src="https://img.shields.io/github/v/release/rowing195/NutriLog?style=flat&logo=github&logoColor=white&color=0080ff" alt="release">

<em>Built with the tools and technologies:</em>

<img src="https://img.shields.io/badge/Kotlin-7F52FF.svg?style=flat&logo=kotlin&logoColor=white" alt="Kotlin">
	<img src="https://img.shields.io/badge/Android-34A853.svg?style=flat&logo=android&logoColor=white" alt="Android">
	<img src="https://img.shields.io/badge/Jetpack%20Compose-4285F4.svg?style=flat&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose">
	<img src="https://img.shields.io/badge/Gradle-02303A.svg?style=flat&logo=gradle&logoColor=white" alt="Gradle">
	<img src="https://img.shields.io/badge/SQLite-003B57.svg?style=flat&logo=sqlite&logoColor=white" alt="SQLite">
	<img src="https://img.shields.io/badge/Google%20Gemini-8E75B2.svg?style=flat&logo=googlegemini&logoColor=white" alt="Google Gemini">
	<img src="https://img.shields.io/badge/JUnit-25A162.svg?style=flat&logo=junit5&logoColor=white" alt="JUnit">
	<img src="https://img.shields.io/badge/GitHub%20Actions-2088FF.svg?style=flat&logo=githubactions&logoColor=white" alt="GitHub Actions">

</div>
<br>

---

### 目錄

- [總覽](#總覽)
- [特色](#特色)
- [專案結構](#專案結構)
    - [專案索引](#專案索引)
- [開始使用](#開始使用)
    - [需求](#需求)
    - [安裝](#安裝)
    - [使用](#使用)
    - [測試](#測試)
- [功能](#功能)
    - [今日](#今日)
    - [份數縮放](#份數縮放)
    - [四種輸入方式](#四種輸入方式)
    - [常吃頁：一個框，兩條路](#常吃頁一個框兩條路)
    - [查網路：一顆章 AI 估、一顆章 AI 查](#查網路一顆章-ai-估一顆章-ai-查)
    - [兩家 AI 供應商：拍照與文字各選各的](#兩家-ai-供應商拍照與文字各選各的)
    - [歷史（月曆）](#歷史月曆)
    - [搜尋與個人食物庫](#搜尋與個人食物庫)
    - [辨識失敗：換一家再試](#辨識失敗換一家再試)
    - [飲水：手動加減與飲料自動帶入](#飲水手動加減與飲料自動帶入)
    - [健康連線：運動消耗加進當天的額度](#健康連線運動消耗加進當天的額度)
    - [依身型計算每日目標](#依身型計算每日目標)
    - [AI 週報／月報](#ai-週報月報)
    - [App 圖示](#app-圖示)
    - [檢查更新](#檢查更新)
    - [匯出／匯入 CSV](#匯出匯入-csv)
    - [Google Drive 雲端備份](#google-drive-雲端備份)
- [設定：選單加子頁，三把 key](#設定選單加子頁三把-key)
- [設計決策](#設計決策)
- [外部 API](#外部-api)
- [發佈](#發佈)
- [技術規格](#技術規格)
- [貢獻](#貢獻)
- [授權](#授權)
- [致謝](#致謝)

---

## 總覽

Android 每日飲食營養素紀錄器（Kotlin + Compose）。app 顯示名稱是「肥胖日記」，
專案代號維持 NutriLog —— package、repo、APK 檔名與簽章都綁在它身上。

**Why NutriLog?** 市面上的飲食紀錄 app 幾乎都要你先開帳號、再把三餐上傳到別人的伺服器。
這支不用：沒有後端、沒有帳號，紀錄全部躺在你自己的手機裡。

- 🔒 **沒有後端、沒有帳號** — 對外連線只有你主動觸發的那幾種：AI 辨識（Gemini／OpenRouter）、「AI 查」的網路搜尋（Tavily）、條碼查詢（Open Food Facts）、選配的 Google Drive 每日備份，以及每天一次到 GitHub 查有沒有新版（可關）。
- 🍱 **四條輸入路徑** — 自己填數字、拍照或打一句話交給 AI 估（Gemini 或 OpenRouter）、掃商品條碼查 Open Food Facts。
- 🔎 **先搜自己吃過的，容錯** — 中文沒有空白可拆詞，改用單字＋相鄰兩字加權比對：「烤肉」找得到「煎烤豬肉排／五花肉」，而「咖啡」不會撈到咖哩飯。
- 🌐 **需要的時候才上網查** —— 打了店名就按「AI 查」，它先去找該店公布的官方營養標示再算；平常按「AI 估」就好。**要不要查是你按的，不是模型猜的。**
- 🔢 **五段雙速份數縮放** — 支援 `±1` 與 `±0.1` 步進，也可以直接打字（最多兩位小數，0.25 份打得進去），自動縮放公克/毫升/份量文字與所有營養素，具備基準持久化無損還原。
- 📰 **「紙與墨」出版物排版美學** — 內嵌 jf open 粉圓中文與 Neucha 手寫數字、自繪精準向量圖示、形狀即層級，無任何預設 Material 容器與色塊。
- 🔁 **辨識失敗就地換一家** — 失敗時面板從底下升上來：選供應商、挑模型、按重試，不用跑一趟設定頁再回來。選完的值就是設定裡的值。
- ✅ **AI 的數字一律要你點頭** — 模型給的是估算值，一定先經過確認畫面才入庫。
- ⌚ **手錶動得多就能多吃一點** — 連上健康連線之後，運動消耗會加進**當天的目標**（不是從吃掉的裡面扣），今日頁、週長條、月曆判斷超標時全部改用加上運動後的額度。預設只回補一半，因為手錶估的熱量普遍偏高。
- 🧮 **依身型算目標** — 填身高體重與活動量，用 Mifflin-St Jeor 算出基礎代謝、每日消耗與建議的三大營養素。**蛋白質只看體態目標**（減脂 1.6、維持 1.4、增肌 1.8 g/kg），活動量只影響熱量。
- 📏 **改目標不會改寫過去** — 每天的目標會留一份快照，改了熱量目標之後，以前的日子還是照當時的標準判斷超標。
- 💧 **喝的也算數** — 今日頁營養素底下一排加減鍵（一次 50 ml）記白開水，而飲料的水量跟著那一筆紀錄走：AI 估飲料時會一併給出容量，喝完就自動進當天的飲水量。
- 🗒️ **AI 週報／月報** — 每週每月的統計是本機算的、隨時看得到；要不要花一次 AI 呼叫請它寫成報告，由你按下去決定，不會自動送出。
- 🎨 **換 app 圖示** — 設定 → 外觀可以從八款內建圖示裡挑一款，桌面上的圖示跟著換。
- 🔔 **有新版會告訴你** — 每天到 GitHub 查一次，有新版時今日頁右上角的設定亮一個紅點，關於頁一鍵開那一版的下載頁。
- 📅 **看得出空白** — 月曆式歷史讓「哪幾天忘了記」一眼就有形狀，清單做不到這件事。
- 📤 **CSV 匯出／匯入** — 完整備份的格式：本地匯出與 Drive 每日備份用的都是同一份 CSV，數字照存的值原樣寫，換手機可以無損接回。
- 🔑 **不需要相機權限** — 拍照交給系統相機、掃碼交給 Play 服務。Manifest 只有 `INTERNET` 與 Health 連線的讀寫權限，後者要你在系統畫面同意才會生效。

---

## 特色

| | 元件 | 細節 |
|---|---|---|
| ⚙️ | **架構** | <ul><li>單一 activity-scoped `NutriViewModel` 串起所有畫面狀態與導航</li><li>`sealed interface Screen` + `when` 分派，刻意不引入複雜導航函式庫</li><li>畫面本身無狀態，只吃資料與 lambda</li></ul> |
| 🔩 | **程式品質** | <ul><li>KDoc 寫繁體中文，解釋「為什麼」而不是「做了什麼」</li><li>版本統一收在 `gradle/libs.versions.toml`</li><li>Compose BOM 管理所有 compose 函式庫版號</li></ul> |
| 📄 | **文件** | <ul><li>README（本檔）＋ `CLAUDE.md`（環境與慣例）</li><li>踩過的坑與設計考量寫在原地註解裡，不另開 wiki</li></ul> |
| 🔌 | **整合** | <ul><li>Google Gemini（照片／文字結構化輸出辨識）</li><li>OpenRouter（另一家供應商，照片與文字可各自切換）</li><li>Tavily（「AI 查」的網路搜尋來源）</li><li>Google Drive（每日自動備份，僅 <code>drive.file</code> 範圍）</li><li>Open Food Facts（條碼營養資訊查詢）</li><li>Health Connect（讀運動消耗，選配寫入飲食）</li><li>GitHub Releases API（檢查新版，不帶 token）</li><li>Play 服務 Code Scanner（免相機權限掃描 UI）</li><li>GitHub Actions 推 tag 自動發佈 Release APK</li></ul> |
| 🧩 | **模組化** | <ul><li>`data/db` Room、`data/net` 外部 API、`ui` 畫面、`ui/theme` 色票與字階</li><li>`PortionMultiplier` 份數縮放與無損還原演算法</li><li>`CsvExport` / `CsvImport` 是純函式、不碰 Android API</li><li>`BmrCalculator`、`ActivityEstimate` 與兩支 `*Aggregator` 同樣是純計算，測試不必開模擬器</li><li>`DriveClient` 手寫 REST，不引官方 Drive client 函式庫</li></ul> |
| 🧪 | **測試** | <ul><li>JUnit 單元測試 15 支共 107 條（`NutrientScalingTest`、`PortionInputTest`、`CsvRoundTripTest`、`WaterCsvTest`、`DriveBackupPruneTest`、`BackupScheduleTest`、`BackedUpProfileTest`、`FoodLibraryMatchTest`、`ActivityEstimateTest`、`CalorieTargetTest`、`BmrCalculatorTest`、`HydrationRecordTest`、`ImageLimitTest`、`PhotoPromptTest`、`UpdateCheckTest`）驗證份數縮放與兩位小數輸入、新版判斷（比數字不比字串、本機建置不比）、CSV 匯出／匯入來回一致、雲端備份保留規則、食物庫模糊比對、兩種手錶配戴方式各採用哪種活動資料與步數只算超出久坐的部分、運動熱量的回補比例、備份白名單不含金鑰與身型目標的營養素配比、飲水的兩個來源在 CSV 來回之後仍然分得開、寫進 Health 連線的飲水紀錄、照片大小上限與烹調備註</li><li>`tools/ui.ps1` 提供依元件文字定位的手動 UI 自動化驗證</li><li>核心回歸清單：新增→編輯→刪除、換日滑動無跳躍、force-stop 狀態持久化、一次滑動剛好只換一天／一週／一個月</li></ul> |
| ⚡️ | **效能** | <ul><li>每日／每月合計由 SQL `GROUP BY` 算，不把明細撈進記憶體</li><li>相片長邊壓到 1024 px 才送出，節省流量與辨識延遲</li><li>全 app 共用一個 `OkHttpClient` 連線池</li><li>條碼結果存 Room 本機快取</li></ul> |
| 🛡️ | **安全** | <ul><li>不宣告相機與儲存權限；除了 `INTERNET` 只有 Health 連線的讀寫權限，要使用者在系統畫面同意才生效</li><li>三家的 API key（Gemini／OpenRouter／Tavily）都存 DataStore，**不編進 APK**</li><li>key 一律走 header（Gemini 是 `x-goog-api-key`，其餘是 `Authorization: Bearer`）而非 query string</li><li>`keystore.properties` 與 `release.jks` 都在 gitignore</li></ul> |
| 📦 | **相依** | <ul><li>Room、DataStore、OkHttp、kotlinx-serialization、WorkManager、play-services-code-scanner、play-services-auth、androidx.health.connect</li><li>刻意不用 Retrofit —— 五個外部服務各自只用到一兩支端點，手寫 OkHttp 維持最精簡依賴</li></ul> |
| 🚀 | **擴充性** | <ul><li>Room 關聯式儲存，`date` 建立索引優化查詢</li><li>新增 `NutriSettings` 欄位一律給預設值，舊資料靠預設值相容</li><li>新增 Room 欄位提供清楚 migration 升級路徑</li></ul> |

---

## 專案結構

```sh
└── NutriLog/
    ├── .github/
    │   └── workflows/
    │       └── release.yml
    ├── app/
    │   ├── build.gradle.kts
    │   ├── proguard-rules.pro
    │   └── src/
    │       ├── main/
    │       │   ├── AndroidManifest.xml
    │       │   ├── java/com/watson/nutrilog/
    │       │   │   ├── MainActivity.kt
    │       │   │   ├── data/
    │       │   │   │   ├── ActivityEstimate.kt
    │       │   │   │   ├── AppIconSwitcher.kt
    │       │   │   │   ├── BackedUpProfile.kt
    │       │   │   │   ├── BmrCalculator.kt
    │       │   │   │   ├── CsvExport.kt
    │       │   │   │   ├── CsvImport.kt
    │       │   │   │   ├── DriveAuth.kt
    │       │   │   │   ├── DriveBackup.kt
    │       │   │   │   ├── HealthConnectSync.kt
    │       │   │   │   ├── MonthlyAggregator.kt
    │       │   │   │   ├── MonthlyReportStore.kt
    │       │   │   │   ├── SettingsStore.kt
    │       │   │   │   ├── UpdateChecker.kt
    │       │   │   │   ├── WeeklyAggregator.kt
    │       │   │   │   ├── WeeklyReportStore.kt
    │       │   │   │   ├── db/
    │       │   │   │   │   ├── CachedProduct.kt
    │       │   │   │   │   ├── DailyHealthMetric.kt
    │       │   │   │   │   ├── DailyTarget.kt
    │       │   │   │   │   ├── DailyWater.kt
    │       │   │   │   │   ├── FoodEntry.kt
    │       │   │   │   │   ├── FoodSuggestion.kt
    │       │   │   │   │   ├── NutriDao.kt
    │       │   │   │   │   └── NutriDatabase.kt
    │       │   │   │   └── net/
    │       │   │   │       ├── AiPrompts.kt
    │       │   │   │       ├── DriveClient.kt
    │       │   │   │       ├── GeminiClient.kt
    │       │   │   │       ├── GitHubReleaseClient.kt
    │       │   │   │       ├── ImageCompressor.kt
    │       │   │   │       ├── OpenFoodFactsClient.kt
    │       │   │   │       ├── OpenRouterClient.kt
    │       │   │   │       ├── SharedHttp.kt
    │       │   │   │       └── TavilyClient.kt
    │       │   │   ├── work/
    │       │   │   │   ├── BackupWorker.kt
    │       │   │   │   └── UpdateCheckWorker.kt
    │       │   │   └── ui/
    │       │   │       ├── App.kt
    │       │   │       ├── BarcodeScreen.kt
    │       │   │       ├── BmrCalculatorDialog.kt
    │       │   │       ├── Common.kt
    │       │   │       ├── EditEntryScreen.kt
    │       │   │       ├── ExerciseDetailSheet.kt
    │       │   │       ├── HistoryScreen.kt
    │       │   │       ├── NumberKeypad.kt
    │       │   │       ├── NutriViewModel.kt
    │       │   │       ├── PhotoConfirmationDialog.kt
    │       │   │       ├── PortionMultiplier.kt
    │       │   │       ├── ProviderSwitchSheet.kt
    │       │   │       ├── ReportScreen.kt
    │       │   │       ├── ReviewScreen.kt
    │       │   │       ├── SearchScreen.kt
    │       │   │       ├── SettingsScreen.kt
    │       │   │       ├── TextLookupScreen.kt
    │       │   │       ├── TodayScreen.kt
    │       │   │       ├── WaterDetailSheet.kt
    │       │   │       └── theme/
    │       │   │           └── Theme.kt
    │       │   └── res/
    │       │       ├── drawable/
    │       │       ├── font/
    │       │       │   ├── jf_open_huninn.ttf
    │       │       │   └── neucha.ttf
    │       │       ├── mipmap-*/
    │       │       ├── values/
    │       │       │   ├── colors.xml
    │       │       │   ├── ic_launcher_background.xml
    │       │       │   ├── strings.xml
    │       │       │   └── themes.xml
    │       │       ├── values-night/
    │       │       │   └── colors.xml
    │       │       └── xml/
    │       ├── debug/
    │       │   └── res/values/strings.xml
    │       └── test/
    │           └── java/com/watson/nutrilog/
    │               ├── ActivityEstimateTest.kt
    │               ├── BackedUpProfileTest.kt
    │               ├── BackupScheduleTest.kt
    │               ├── BmrCalculatorTest.kt
    │               ├── CalorieTargetTest.kt
    │               ├── CsvRoundTripTest.kt
    │               ├── DriveBackupPruneTest.kt
    │               ├── FoodLibraryMatchTest.kt
    │               ├── HydrationRecordTest.kt
    │               ├── ImageLimitTest.kt
    │               ├── NutrientScalingTest.kt
    │               ├── PhotoPromptTest.kt
    │               ├── PortionInputTest.kt
    │               ├── UpdateCheckTest.kt
    │               └── WaterCsvTest.kt
    ├── design/
    │   ├── Budget.dc.html
    │   ├── Journal.dc.html
    │   ├── Main.dc.html
    │   ├── Refined.dc.html
    │   ├── canvas.json
    │   └── v2/
    ├── gradle/
    │   ├── libs.versions.toml
    │   └── wrapper/
    ├── tools/
    │   ├── emu.ps1
    │   ├── setup-google-drive.sh
    │   ├── setup-signing.sh
    │   └── ui.ps1
    ├── build.gradle.kts
    ├── settings.gradle.kts
    ├── AGENTS.md
    ├── CLAUDE.md
    ├── LICENSE
    └── README.md
```

### 專案索引

<details open>
	<summary><b><code>NUTRILOG/</code></b></summary>
	<!-- __root__ Submodule -->
	<details>
		<summary><b>__root__</b></summary>
		<blockquote>
			<div class='directory-path' style='padding: 8px 0; color: #666;'>
				<code><b>⦿ __root__</b></code>
			<table style='width: 100%; border-collapse: collapse;'>
			<thead>
				<tr style='background-color: #f8f9fa;'>
					<th style='width: 30%; text-align: left; padding: 8px;'>檔案</th>
					<th style='text-align: left; padding: 8px;'>說明</th>
				</tr>
			</thead>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/build.gradle.kts'>app/build.gradle.kts</a></b></td>
					<td style='padding: 8px;'>模組建置設定。版號由 CI 從 tag 傳入的 property 覆蓋，本機建置才用預設值。<br>- 簽章讀 `keystore.properties`，檔案不存在就退回 debug 簽章，讓別人 clone 下來照樣建得起來。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/gradle/libs.versions.toml'>gradle/libs.versions.toml</a></b></td>
					<td style='padding: 8px;'>版本目錄，所有相依與外掛的版號單一來源。KSP 的版號前半段必須和 Kotlin 完全一致。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/CLAUDE.md'>CLAUDE.md</a></b></td>
					<td style='padding: 8px;'>這台機器的環境設定與專案慣例：建置指令、模擬器規則、配色與「紙與墨」版面語言、回歸清單。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/AGENTS.md'>AGENTS.md</a></b></td>
					<td style='padding: 8px;'>給 Codex 的摘要：本機 Android SDK、模擬器路徑與驗證流程。詳細內容以 `CLAUDE.md` 為準，這裡不整份複製。</td>
				</tr>
			</table>
		</blockquote>
	</details>
	<!-- app.src.main.java.com.watson.nutrilog Submodule -->
	<details>
		<summary><b>com.watson.nutrilog</b></summary>
		<blockquote>
			<div class='directory-path' style='padding: 8px 0; color: #666;'>
				<code><b>⦿ app/src/main/java/com/watson/nutrilog</b></code>
			<table style='width: 100%; border-collapse: collapse;'>
			<thead>
				<tr style='background-color: #f8f9fa;'>
					<th style='width: 30%; text-align: left; padding: 8px;'>檔案</th>
					<th style='text-align: left; padding: 8px;'>說明</th>
				</tr>
			</thead>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/MainActivity.kt'>MainActivity.kt</a></b></td>
					<td style='padding: 8px;'>唯一的 Activity。開啟 edge-to-edge、套上主題，並建立 activity-scoped 的 ViewModel 串接全域狀態。</td>
				</tr>
			</table>
		</blockquote>
	</details>
	<!-- data Submodule -->
	<details>
		<summary><b>data</b></summary>
		<blockquote>
			<div class='directory-path' style='padding: 8px 0; color: #666;'>
				<code><b>⦿ app/src/main/java/com/watson/nutrilog/data</b></code>
			<table style='width: 100%; border-collapse: collapse;'>
			<thead>
				<tr style='background-color: #f8f9fa;'>
					<th style='width: 30%; text-align: left; padding: 8px;'>檔案</th>
					<th style='text-align: left; padding: 8px;'>說明</th>
				</tr>
			</thead>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/ActivityEstimate.kt'>ActivityEstimate.kt</a></b></td>
					<td style='padding: 8px;'>把健康連線回報的東西換算成「今天動掉多少大卡」。<br>- 活動消耗、運動場次、步數三種來源，要用哪一種看手錶配戴方式：活動消耗有被採用（整天配戴）時步數不參與。<br>- 步數只算**超出久坐基準**的那一段，運動場次裡的步數先扣掉，不然同一批熱量會算兩次。<br>- 讀不到就回 NONE 並附原因，「讀不到」和「讀到了但沒超出」分開講；不拿總消耗扣基礎代謝生猜測值。<br>- 純計算，有測試涵蓋。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/AppIconSwitcher.kt'>AppIconSwitcher.kt</a></b></td>
					<td style='padding: 8px;'>換桌面圖示：把選到的 activity-alias 打開、其餘關掉。<br>- Android 不讓 app 在執行時把圖示換成任意圖片，只能切換事先放在 APK 裡的 alias。<br>- 先開新的再關舊的，中間不會有「完全沒有桌面入口」的空窗。<br>- 每次啟動對一次，系統那側被重設時會跟設定對回來。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/BackedUpProfile.kt'>BackedUpProfile.kt</a></b></td>
					<td style='padding: 8px;'>備份到 Drive 的身型與每日目標（CSV 裝不下的那一半）。<br>- 白名單而非整包設定：<b>API 金鑰永遠不會被備份</b>，有測試專門檢查。<br>- 還原時併進匯入的確認面板，不另外問一次。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/BmrCalculator.kt'>BmrCalculator.kt</a></b></td>
					<td style='padding: 8px;'>Mifflin-St Jeor 基礎代謝與每日目標、三大營養素、各餐配比。<br>- 蛋白質只看體態目標（減脂 1.6、維持 1.4、增肌 1.8 g/kg），不受活動量與手錶設定影響。<br>- 有在讀運動消耗時，熱量的底退到久坐係數，運動由手錶另外補。<br>- 碳水固定 55%、脂肪吃差額，脂肪守住 20% 下限。<br>- 算出來的只是建議，按「套用」才寫進設定。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/CsvExport.kt'>CsvExport.kt</a></b></td>
					<td style='padding: 8px;'>把飲食紀錄轉成 CSV，是把資料帶出手機的路徑。<br>- 純函式、不碰 Android API。<br>- 檔頭有 UTF-8 BOM，避免 Excel 中文亂碼。<br>- 欄位名稱本身就是格式：`CsvImport` 靠名字對應欄位。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/CsvImport.kt'>CsvImport.kt</a></b></td>
					<td style='padding: 8px;'>把匯出的 CSV 讀回資料庫，換手機或重裝之後接回原本的紀錄。<br>- 靠欄位名稱對應，舊版少兩欄的匯出檔也讀得回來。<br>- 依「日期＋名稱＋份量＋記錄時間」去重，同一份檔匯入兩次不會變兩份。<br>- 壞掉的資料列跳過並回報，不讓整份檔案失敗。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/DriveAuth.kt'>DriveAuth.kt</a></b></td>
					<td style='padding: 8px;'>Drive 授權（Identity AuthorizationClient，非已淘汰的 GoogleSignIn）。<br>- 只索取 <code>drive.file</code>：僅能存取本 app 自行建立的檔案，非受限範圍、免安全評估。<br>- app 內不含任何 client id：Android OAuth client 以套件名 + 簽章 SHA-1 辨識。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/DriveBackup.kt'>DriveBackup.kt</a></b></td>
					<td style='padding: 8px;'>備份與還原的流程編排：建立 Drive 主頁 <code>NutriLog/</code> 資料夾、上傳當日 CSV、保留最近 30 天。<br>- 備份內容與本地匯出完全相同，可自行下載或改用本地匯入讀回。<br>- 保留規則為純函式並有測試涵蓋。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/HealthConnectSync.kt'>HealthConnectSync.kt</a></b></td>
					<td style='padding: 8px;'>健康連線的讀與寫。<br>- 讀每日運動消耗；寫入為選配，只在新增／編輯／刪除當下寫。<br>- <code>diagnose()</code> 倒出原始讀值給設定頁的診斷區與 logcat。<br>- 以 <code>nutrilog_&lt;紀錄 id&gt;</code> 當 clientRecordId，改同一筆就是覆寫。<br>- 相依釘在 1.1.0-beta01（1.1.0 正式版要 compileSdk 36）。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/MonthlyAggregator.kt'>MonthlyAggregator.kt</a></b></td>
					<td style='padding: 8px;'>整月統計與月報 prompt 組裝。<br>- 統計在本機算，跟 AI 報告分開，沒產生報告也看得到數字。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/MonthlyReportStore.kt'>MonthlyReportStore.kt</a></b></td>
					<td style='padding: 8px;'>月報存取（<code>filesDir/monthly_reports/&lt;yyyy-MM&gt;.json</code>）。<br>- 不進 Drive 備份：報告隨時可以重新產生。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/SettingsStore.kt'>SettingsStore.kt</a></b></td>
					<td style='padding: 8px;'>使用者設定與每日目標。用 DataStore Preferences 儲存單份無關聯之輕量偏好設定。<br>- 檢查更新的結果存在同一個 DataStore 的另一個 key：背景工作寫的狀態不能和整包設定擠在一起，不然會被互相蓋掉。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/UpdateChecker.kt'>UpdateChecker.kt</a></b></td>
					<td style='padding: 8px;'>到 GitHub 查一次新版並存起來，關於頁的按鈕與每天的背景檢查共用。<br>- 版號逐段比數字（2.2.10 比 2.2.9 新）；本機建置「1.0-debug」比不了，不說有新版。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/WeeklyAggregator.kt'>WeeklyAggregator.kt</a></b></td>
					<td style='padding: 8px;'>整週統計與週報 prompt 組裝，並解析回應結尾的 <code>json:targets</code> 區塊。<br>- 那個區塊就是「建議下週每日目標」，改 prompt 時名稱不能動。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/WeeklyReportStore.kt'>WeeklyReportStore.kt</a></b></td>
					<td style='padding: 8px;'>週報存取（<code>filesDir/weekly_reports/&lt;週日&gt;.json</code>）。<br>- 一週從星期日開始，和今日頁的週長條一致。</td>
				</tr>
			</table>
		</blockquote>
	</details>
	<!-- data.db Submodule -->
	<details>
		<summary><b>data.db</b></summary>
		<blockquote>
			<div class='directory-path' style='padding: 8px 0; color: #666;'>
				<code><b>⦿ app/src/main/java/com/watson/nutrilog/data/db</b></code>
			<table style='width: 100%; border-collapse: collapse;'>
			<thead>
				<tr style='background-color: #f8f9fa;'>
					<th style='width: 30%; text-align: left; padding: 8px;'>檔案</th>
					<th style='text-align: left; padding: 8px;'>說明</th>
				</tr>
			</thead>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/db/DailyHealthMetric.kt'>DailyHealthMetric.kt</a></b></td>
					<td style='padding: 8px;'>每日運動消耗的本機快取（migration 2→3 新增）。<br>- 週長條與月曆一打開就要用，不能等健康連線慢慢回。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/db/DailyTarget.kt'>DailyTarget.kt</a></b></td>
					<td style='padding: 8px;'>每一天當時的四格目標快照（migration 3→4 新增）。<br>- 過去的日子用過去的標準判斷超標：今天每次設定變動都覆寫，過去的碰都不碰。<br>- 沒有快照的日子退回目前的設定，舊紀錄的行為和以前一樣。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/db/DailyWater.kt'>DailyWater.kt</a></b></td>
					<td style='padding: 8px;'>每天手動加減的飲水量（migration 4→5 新增）。<br>- 和飲料自己帶的水（`FoodEntry.waterMl`）分開存：白開水不在紀錄清單長出 0 大卡的列，飲料刪掉水量也跟著消失。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/db/FoodEntry.kt'>FoodEntry.kt</a></b></td>
					<td style='padding: 8px;'>一筆吃下去的飲食紀錄實體，包含份數倍率 `portionMultiplier`、延伸四項營養素與全天合計 `Totals`。日期以本地 YYYY-MM-DD 字串儲存。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/db/NutriDao.kt'>NutriDao.kt</a></b></td>
					<td style='padding: 8px;'>Room DAO。合計走 SQL `GROUP BY` 計算，不把龐大明細撈進記憶體。常吃／最近以「名稱＋份量文字」分組聚合。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/db/FoodSuggestion.kt'>FoodSuggestion.kt</a></b></td>
					<td style='padding: 8px;'>個人食物庫品項 —— 從既有紀錄聚合出來的品項模型，不額外建立實體表，提供快速一鍵帶入。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/db/CachedProduct.kt'>CachedProduct.kt</a></b></td>
					<td style='padding: 8px;'>查過的條碼商品快取表（每 100g 營養素），節省 OFF 頻率限制並支援離線再次掃碼。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/db/NutriDatabase.kt'>NutriDatabase.kt</a></b></td>
					<td style='padding: 8px;'>Room 資料庫單例與 Migrations（目前第 5 版，1→2 到 4→5 四段）。</td>
				</tr>
			</table>
		</blockquote>
	</details>
	<!-- data.net Submodule -->
	<details>
		<summary><b>data.net</b></summary>
		<blockquote>
			<div class='directory-path' style='padding: 8px 0; color: #666;'>
				<code><b>⦿ app/src/main/java/com/watson/nutrilog/data/net</b></code>
			<table style='width: 100%; border-collapse: collapse;'>
			<thead>
				<tr style='background-color: #f8f9fa;'>
					<th style='width: 30%; text-align: left; padding: 8px;'>檔案</th>
					<th style='text-align: left; padding: 8px;'>說明</th>
				</tr>
			</thead>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/net/GeminiClient.kt'>GeminiClient.kt</a></b></td>
					<td style='padding: 8px;'>照片與文字描述的營養估算。以 `responseSchema` 強制結構化 JSON 輸出，自動重試 5xx 與網路逾時。<br>- 四個進階營養素（糖／鈉／膳食纖維／飽和脂肪）列為 `required` 但仍可為 `null`：選填等於給模型一個整個略過的藉口。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/net/DriveClient.kt'>DriveClient.kt</a></b></td>
					<td style='padding: 8px;'>Google Drive REST v3，僅實作備份所需的四支端點（建資料夾、上傳／覆蓋、列檔、下載）。<br>- 以 OkHttp 手寫，不引官方 Drive client 函式庫（會拖進 google-api-client 與 guava）。<br>- 錯誤訊息帶上 Drive 回傳內容，權杖過期與配額不足才分得開。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/net/OpenFoodFactsClient.kt'>OpenFoodFactsClient.kt</a></b></td>
					<td style='padding: 8px;'>條碼查詢客戶端。自動附帶規範之自訂 User-Agent，並把鈉公克轉換為毫克。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/net/ImageCompressor.kt'>ImageCompressor.kt</a></b></td>
					<td style='padding: 8px;'>將原始照片等比例縮放到長邊 1024 px 並壓為 base64 JPEG，大幅降低頻寬與延遲。<br>- 解碼前先只讀檔頭檢查大小上限（2.5 億畫素、30 MB），擋掉會把記憶體撐爆的檔案。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/net/SharedHttp.kt'>SharedHttp.kt</a></b></td>
					<td style='padding: 8px;'>全 app 共用之 `OkHttpClient` 單例，維持高效連線池與執行緒管理。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/net/GitHubReleaseClient.kt'>GitHubReleaseClient.kt</a></b></td>
					<td style='padding: 8px;'>查 GitHub 上最新的正式版（`releases/latest`，會略過 draft 與 prerelease）。<br>- 不帶 token：公開 repo 每小時 60 次，一天查一次用不完。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/net/OpenRouterClient.kt'>OpenRouterClient.kt</a></b></td>
					<td style='padding: 8px;'>另一家供應商，拍照、文字辨識與報告都能選它。<br>- 拍照用 OpenAI 格式的 `image_url`（data URI）把圖片帶進去；拍照的模型另外一欄，沒填就直接擋下來，不拿文字那個純文字模型去送圖片。<br>- 以**強制函式呼叫**（`tool_choice`）鎖住 JSON，而不是 `response_format` —— 想用的免費模型不支援後者。<br>- 錯誤碼比 Gemini 多一種：**402 是餘額不足**（免費模型也需要帳號裡有額度）。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/net/TavilyClient.kt'>TavilyClient.kt</a></b></td>
					<td style='padding: 8px;'>「AI 查」按下去時的網路搜尋。把清洗過的頁面正文接到 prompt 前面，**與供應商無關**，兩家都適用。<br>- 不需要 tool calling，也不多消耗模型的請求次數。<br>- 搜尋失敗一律回 `null`：它只是輔助，不該因為搜尋壞掉讓整條辨識失敗。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/net/AiPrompts.kt'>AiPrompts.kt</a></b></td>
					<td style='padding: 8px;'>兩家供應商**共用的 prompt**。同一段話兩邊各抄一份遲早會漂，而漂掉的症狀是「換一家之後回來的東西長得不一樣」。</td>
				</tr>
			</table>
		</blockquote>
	</details>
	<!-- ui Submodule -->
	<details>
		<summary><b>ui</b></summary>
		<blockquote>
			<div class='directory-path' style='padding: 8px 0; color: #666;'>
				<code><b>⦿ app/src/main/java/com/watson/nutrilog/ui</b></code>
			<table style='width: 100%; border-collapse: collapse;'>
			<thead>
				<tr style='background-color: #f8f9fa;'>
					<th style='width: 30%; text-align: left; padding: 8px;'>檔案</th>
					<th style='text-align: left; padding: 8px;'>說明</th>
				</tr>
			</thead>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/ui/NutriViewModel.kt'>NutriViewModel.kt</a></b></td>
					<td style='padding: 8px;'>唯一的 ViewModel：管理全 App 狀態機、草稿狀態、辨識生命週期、搜尋與預設餐別。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/ui/App.kt'>App.kt</a></b></td>
					<td style='padding: 8px;'>根 Composable。分派畫面與管理相機、相簿、SAF 與條碼掃描之 ActivityResultLauncher。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/ui/TodayScreen.kt'>TodayScreen.kt</a></b></td>
					<td style='padding: 8px;'>今日主畫面：一週長條、已吃熱量計數器、餐別分段進度條、三大營養素組成與兩級超標警示、固定四餐清單與五合一懸浮選單。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/ui/PortionMultiplier.kt'>PortionMultiplier.kt</a></b></td>
					<td style='padding: 8px;'>五段純數字雙速步進列（`±1` 與 `±0.1` 圓章按鍵），中間那格可以直接打字（0.01～99、最多兩位小數），支援無損還原。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/ui/EditEntryScreen.kt'>EditEntryScreen.kt</a></b></td>
					<td style='padding: 8px;'>共用飲食編輯表單：熱量滿版一格＋三大營養素橫排三格＋水量、自繪圓章數字鍵盤（避免擋住儲存鈕）、份數縮放步進列、折疊進階營養素與熱量交叉檢驗。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/ui/NumberKeypad.kt'>NumberKeypad.kt</a></b></td>
					<td style='padding: 8px;'>自繪數字鍵盤，編輯表單與 AI 確認頁共用。<br>- 從螢幕底下推上來，上面的表單同時跟著變矮；收起時原路退回。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/ui/PhotoConfirmationDialog.kt'>PhotoConfirmationDialog.kt</a></b></td>
					<td style='padding: 8px;'>拍完或選完照片後的確認頁：先看一眼照片、可以加一句烹調備註（例如「水煮、沒加油」），按送出才去辨識。<br>- 太大的照片（超過 2.5 億畫素或 30 MB）在這裡就擋下來，不會解碼到一半閃退。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/ui/ProviderSwitchSheet.kt'>ProviderSwitchSheet.kt</a></b></td>
					<td style='padding: 8px;'>辨識失敗時從底下升上來的面板：換一家、換個模型，再按重試。<br>- 改的就是設定裡的那幾個欄位，拍照與文字各改各的。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/ui/ReportScreen.kt'>ReportScreen.kt</a></b></td>
					<td style='padding: 8px;'>AI 週報／月報。<br>- 上半是本機算的統計（含與上週／上個月比），隨時看得到。<br>- 下半是報告本文；還沒產生時給一顆「產生週報」。<br>- 本文只認標題、條列、段落三種，模型多給的粗體與表格降成純文字。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/ui/ReviewScreen.kt'>ReviewScreen.kt</a></b></td>
					<td style='padding: 8px;'>AI 辨識結果確認頁面：品項勾選、單品份數縮放、信心度指標與目標餐別預選。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/ui/SearchScreen.kt'>SearchScreen.kt</a></b></td>
					<td style='padding: 8px;'>搜尋與個人食物庫（90 天常吃／最近兩頁切換，即時多關鍵字全文搜尋，不限筆數、幾年前的紀錄也找得到，點擊直接進入編輯表單）。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/ui/TextLookupScreen.kt'>TextLookupScreen.kt</a></b></td>
					<td style='padding: 8px;'>常吃食物快捷與自然語言文字描述 AI 辨識合成頁面。<br>- 沒打字時列常吃／最近各 60 種，一打字就改篩所有吃過的品項。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/ui/ExerciseDetailSheet.kt'>ExerciseDetailSheet.kt</a></b></td>
					<td style='padding: 8px;'>今日頁「運動 +350 ›」點開的明細。<br>- 講清楚數字的來源（全日活動消耗、單場運動與步數涵蓋範圍不同）。<br>- 走路那一列就算加進目標的是 0 也會列出來，回答「我明明有走，為什麼是 +0」。<br>- 列出吃了、動了、淨攝取，以及今天的目標是怎麼加出來的。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/ui/WaterDetailSheet.kt'>WaterDetailSheet.kt</a></b></td>
					<td style='padding: 8px;'>今日頁點飲水量打開的明細：這個數字是哪幾杯飲料、再加上手動按了多少。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/ui/HistoryScreen.kt'>HistoryScreen.kt</a></b></td>
					<td style='padding: 8px;'>月曆式歷史視圖：熱量深淺與超標警示色塊、一眼辨識空白未記錄日，下方統計當月總覽。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/ui/BarcodeScreen.kt'>BarcodeScreen.kt</a></b></td>
					<td style='padding: 8px;'>條碼掃描與手動輸入條碼，支援自訂實際食用克數自動等比換算。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/ui/SettingsScreen.kt'>SettingsScreen.kt</a></b></td>
					<td style='padding: 8px;'>設定的選單與七個子頁：外觀（深淺模式、App 圖示）、每日目標（含依身型計算、進階營養素開關、運動熱量回補）、Health 連線、API 管理（三把 key、各條路走哪一家、模型攤開圈選而不用下拉選單）、雲端備份、本地資料管理（CSV 匯出／匯入）、關於（版本、檢查更新、每天自動檢查的開關）。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/ui/BmrCalculatorDialog.kt'>BmrCalculatorDialog.kt</a></b></td>
					<td style='padding: 8px;'>「依身型計算」的面板：填身型與目標，看到建議的熱量與三大營養素。<br>- 蛋白質標出每公斤幾克，看得出這個數字高不高。<br>- 按「套用」才寫進每日目標。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/ui/Common.kt'>Common.kt</a></b></td>
					<td style='padding: 8px;'>「紙與墨」設計系統元件：`Hairline`（1px）、`Rule`（2px）、`StampButton`、`PillButton`、`TextAction`、`RoundKey`、`BallotRow`、`SquareCheck`、`NutriTextField`、`dismissKeyboardOnTap`（點空白處收鍵盤）、`SwipeToReveal` / `UndoStamp`（左滑刪除與復原）與全自繪向量 `*Mark` 圖示。</td>
				</tr>
			</table>
		</blockquote>
	</details>
	<!-- ui.theme Submodule -->
	<details>
		<summary><b>ui.theme</b></summary>
		<blockquote>
			<div class='directory-path' style='padding: 8px 0; color: #666;'>
				<code><b>⦿ app/src/main/java/com/watson/nutrilog/ui/theme</b></code>
			<table style='width: 100%; border-collapse: collapse;'>
			<thead>
				<tr style='background-color: #f8f9fa;'>
					<th style='width: 30%; text-align: left; padding: 8px;'>檔案</th>
					<th style='text-align: left; padding: 8px;'>說明</th>
				</tr>
			</thead>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/ui/theme/Theme.kt'>Theme.kt</a></b></td>
					<td style='padding: 8px;'>「紙與墨」出版物色票（淺色米紙 `#F7F3E9`、深色暖黑 `#17150F`）、三大營養素色階、兩級超標警示（橘 `#B8791F` / 紅 `#D8462A`），以及內嵌的 jf open 粉圓中文字型與 Neucha 數字字型。</td>
				</tr>
			</table>
		</blockquote>
	</details>
	<!-- work Submodule -->
	<details>
		<summary><b>work</b></summary>
		<blockquote>
			<div class='directory-path' style='padding: 8px 0; color: #666;'>
				<code><b>⦿ app/src/main/java/com/watson/nutrilog/work</b></code>
			<table style='width: 100%; border-collapse: collapse;'>
			<thead>
				<tr style='background-color: #f8f9fa;'>
					<th style='width: 30%; text-align: left; padding: 8px;'>檔案</th>
					<th style='text-align: left; padding: 8px;'>說明</th>
				</tr>
			</thead>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/work/BackupWorker.kt'>BackupWorker.kt</a></b></td>
					<td style='padding: 8px;'>每日一次的 Drive 備份排程（WorkManager）。<br>- 選用 WorkManager 而非 AlarmManager：Doze 與重新開機後仍可靠。<br>- 網路類失敗一律 retry；僅「需重新授權」回 failure，因背景無畫面可詢問使用者。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/work/UpdateCheckWorker.kt'>UpdateCheckWorker.kt</a></b></td>
					<td style='padding: 8px;'>每天到 GitHub 查一次新版（WorkManager），和 Drive 備份是兩個獨立的排程。<br>- 查到新版只存起來、不發通知，今日頁右上角的設定圖示亮紅點。<br>- 只有連線問題才 retry；被限流就等明天。</td>
				</tr>
			</table>
		</blockquote>
	</details>
	<!-- test Submodule -->
	<details>
		<summary><b>test</b></summary>
		<blockquote>
			<div class='directory-path' style='padding: 8px 0; color: #666;'>
				<code><b>⦿ app/src/test/java/com/watson/nutrilog</b></code>
			<table style='width: 100%; border-collapse: collapse;'>
			<thead>
				<tr style='background-color: #f8f9fa;'>
					<th style='width: 30%; text-align: left; padding: 8px;'>檔案</th>
					<th style='text-align: left; padding: 8px;'>說明</th>
				</tr>
			</thead>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/test/java/com/watson/nutrilog/NutrientScalingTest.kt'>NutrientScalingTest.kt</a></b></td>
					<td style='padding: 8px;'>單元測試：驗證份量文字縮放（含 0.25、0.75 份）、DetectedFood 營養素等比計算、EntryDraft 基準導出與還原無損計算。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/test/java/com/watson/nutrilog/PortionInputTest.kt'>PortionInputTest.kt</a></b></td>
					<td style='padding: 8px;'>份數格直接打字的規則：整數最多兩位、小數最多兩位、超出的那一下無效、0 收到下限 0.01、空白維持原本的倍率。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/test/java/com/watson/nutrilog/ActivityEstimateTest.kt'>ActivityEstimateTest.kt</a></b></td>
					<td style='padding: 8px;'>兩種配戴方式各自該採用哪一種資料、步數只算超出久坐額度的那一段（運動場次裡的步數要扣掉）、讀不到時不生猜測值。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/test/java/com/watson/nutrilog/CalorieTargetTest.kt'>CalorieTargetTest.kt</a></b></td>
					<td style='padding: 8px;'>「今天可以吃多少」：運動熱量照回補比例加進目標，沒動的日子就是目標本身。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/test/java/com/watson/nutrilog/BackedUpProfileTest.kt'>BackedUpProfileTest.kt</a></b></td>
					<td style='padding: 8px;'>備份的身型 JSON 是白名單，裡面不會出現 API 金鑰。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/test/java/com/watson/nutrilog/BmrCalculatorTest.kt'>BmrCalculatorTest.kt</a></b></td>
					<td style='padding: 8px;'>蛋白質依體態目標固定倍率、不受活動量與手錶影響；交給手錶量時熱量用久坐係數；碳水固定 55%、脂肪 20% 下限。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/test/java/com/watson/nutrilog/CsvRoundTripTest.kt'>CsvRoundTripTest.kt</a></b></td>
					<td style='padding: 8px;'>單元測試：CSV 匯出→匯入來回逐欄一致（兩位以上的小數也不掉）、逗號／引號／換行跳脫、缺資料維持 null、舊版欄位相容、去重鍵與壞資料列跳過。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/test/java/com/watson/nutrilog/DriveBackupPruneTest.kt'>DriveBackupPruneTest.kt</a></b></td>
					<td style='padding: 8px;'>單元測試：雲端備份的 30 天保留規則 —— 只刪自己產生的日期檔、跨月跨年排序正確、使用者自行放入的檔案一律不動。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/test/java/com/watson/nutrilog/FoodLibraryMatchTest.kt'>FoodLibraryMatchTest.kt</a></b></td>
					<td style='padding: 8px;'>單元測試：食物庫的模糊比對 —— 描述比庫裡更細仍找得到、名稱裡被拆開的詞仍找得到、只共用一個字不算命中、整串命中排在近似之前、精選清單裝不下的舊品項打字仍找得到。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/test/java/com/watson/nutrilog/BackupScheduleTest.kt'>BackupScheduleTest.kt</a></b></td>
					<td style='padding: 8px;'>每日備份對齊到凌晨 3 點的延遲計算。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/test/java/com/watson/nutrilog/WaterCsvTest.kt'>WaterCsvTest.kt</a></b></td>
					<td style='padding: 8px;'>飲水的兩個來源（飲料的水量、手動加減）在 CSV 來回一趟之後仍然分得開，只喝水沒吃東西的日子也保得住。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/test/java/com/watson/nutrilog/HydrationRecordTest.kt'>HydrationRecordTest.kt</a></b></td>
					<td style='padding: 8px;'>寫進 Health 連線的飲水紀錄：一天一筆（`nutrilog_water_<日期>`）、改了就用同一個 id 加新版號蓋過去，0／負數／未來的日子不寫，日期邊界跨日光節約時間也對。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/test/java/com/watson/nutrilog/ImageLimitTest.kt'>ImageLimitTest.kt</a></b></td>
					<td style='padding: 8px;'>照片大小上限：手機拍的兩億畫素照片照樣通過、剛好在上限通過、超過一個位元組或一個像素就擋下、讀不到檔頭的另外講。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/test/java/com/watson/nutrilog/PhotoPromptTest.kt'>PhotoPromptTest.kt</a></b></td>
					<td style='padding: 8px;'>照片確認頁的烹調備註：不填也能直接辨識，填了就完整接在照片指示後面。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/test/java/com/watson/nutrilog/UpdateCheckTest.kt'>UpdateCheckTest.kt</a></b></td>
					<td style='padding: 8px;'>檢查更新：版號逐段比數字、本機建置與奇怪的 tag 比不了、GitHub 回應只取需要的三欄。</td>
				</tr>
			</table>
		</blockquote>
	</details>
	<!-- tools Submodule -->
	<details>
		<summary><b>tools</b></summary>
		<blockquote>
			<div class='directory-path' style='padding: 8px 0; color: #666;'>
				<code><b>⦿ tools</b></code>
			<table style='width: 100%; border-collapse: collapse;'>
			<thead>
				<tr style='background-color: #f8f9fa;'>
					<th style='width: 30%; text-align: left; padding: 8px;'>檔案</th>
					<th style='text-align: left; padding: 8px;'>說明</th>
				</tr>
			</thead>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/tools/emu.ps1'>emu.ps1</a></b></td>
					<td style='padding: 8px;'>Windows 模擬器輔助腳本：啟動 AVD 並等待 `boot_completed`，建置與部署。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/tools/ui.ps1'>ui.ps1</a></b></td>
					<td style='padding: 8px;'>UI 驗證工具：傾印畫面所有文字節點與座標，並以元件文字進行精準點擊測試。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/tools/setup-signing.sh'>setup-signing.sh</a></b></td>
					<td style='padding: 8px;'>一次性正式發佈簽章金鑰設定精靈（產金鑰 → 驗指紋 → 設 GitHub Secrets）。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/tools/setup-google-drive.sh'>setup-google-drive.sh</a></b></td>
					<td style='padding: 8px;'>一次性 Google Drive 備份設定精靈：在 Google Cloud 建 OAuth client，debug 與 release 兩組簽章 SHA-1 各一個。</td>
				</tr>
			</table>
		</blockquote>
	</details>
	<!-- .github.workflows Submodule -->
	<details>
		<summary><b>.github.workflows</b></summary>
		<blockquote>
			<div class='directory-path' style='padding: 8px 0; color: #666;'>
				<code><b>⦿ .github/workflows</b></code>
			<table style='width: 100%; border-collapse: collapse;'>
			<thead>
				<tr style='background-color: #f8f9fa;'>
					<th style='width: 30%; text-align: left; padding: 8px;'>檔案</th>
					<th style='text-align: left; padding: 8px;'>說明</th>
				</tr>
			</thead>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/.github/workflows/release.yml'>release.yml</a></b></td>
					<td style='padding: 8px;'>推 `v*` tag 自動觸發建置、覆寫版號、以正式簽章產出 APK 並發佈至 GitHub Release。</td>
				</tr>
			</table>
		</blockquote>
	</details>
</details>

---

## 開始使用

### 需求

- **語言：** Kotlin 2.0.21
- **建置工具：** repo 內附的 `./gradlew`（Gradle 8.11.1，不必另外安裝）
- **JDK：** 17
- **Android SDK：** compileSdk 35，最低支援 Android 8.0（minSdk 26）

只是想使用 app 的話不需要安裝上述環境 —— 直接至 [Releases](https://github.com/rowing195/NutriLog/releases) 下載最新 APK 安裝即可。

### 安裝

從原始碼編譯：

1. **Clone 專案：**

    ```sh
    ❯ git clone https://github.com/rowing195/NutriLog
    ```

2. **進入目錄：**

    ```sh
    ❯ cd NutriLog
    ```

3. **建置 Debug APK：**

    ```sh
    ❯ ./gradlew assembleDebug
    ```

APK 產出於 `app/build/outputs/apk/debug/app-debug.apk`。

debug 版的套件名是 `com.watson.nutrilog.debug`、名稱是「肥胖日記 測試版」，可以和正式版同時裝在同一支手機上，互不影響資料。

若本地無 `keystore.properties`，Gradle 將自動退回 debug 簽章以確保可順利編譯。

正式簽章的 `release.jks` 與 `keystore.properties` 的四個值備份在 KeePassXC 資料庫的「NutriLog 簽章金鑰」項目，這把金鑰只有本專案使用。GitHub Secrets 裡的值無法取回，換電腦時請從 KeePassXC 取出 `release.jks` 放回專案根目錄，並照項目內容重建 `keystore.properties`。

### 使用

安裝至已連線的實機或模擬器：

```sh
❯ ./gradlew installDebug
```

Windows 平台可使用隨附腳本：

```powershell
& ".\tools\emu.ps1" start    # 啟動模擬器並等待 boot_completed
& ".\tools\emu.ps1" deploy   # 自動編譯並安裝
```

### 測試

執行自動化單元測試套件：

```sh
❯ ./gradlew test
```

單元測試覆蓋：

[`NutrientScalingTest.kt`](app/src/test/java/com/watson/nutrilog/NutrientScalingTest.kt)
- 份量字串縮放演算法（克、毫升、碗、份）
- `DetectedFood` 浮點營養素精確度與可空欄位保持
- `EntryDraft` 基準值導出與無損還原（避免浮點進位累積漂移）
- 四分之一份：「1 碗」× 0.25 是 0.25 碗而不是 0.3 碗；重開 0.75 份的紀錄能反推回 1 份

[`PortionInputTest.kt`](app/src/test/java/com/watson/nutrilog/PortionInputTest.kt)
- 份數格直接打字：整數最多兩位、小數最多兩位，超出的那一下無效（不讓人先看到 150 再跳回 99）
- 離開格子時：空白維持原本的倍率、0 收到下限 0.01、浮點累加的尾巴收掉

[`DriveBackupPruneTest.kt`](app/src/test/java/com/watson/nutrilog/DriveBackupPruneTest.kt)
- 雲端備份的 30 天保留規則：只刪自己產生的日期檔、跨月跨年排序正確、使用者自行放入的檔案一律不動

[`FoodLibraryMatchTest.kt`](app/src/test/java/com/watson/nutrilog/FoodLibraryMatchTest.kt)
- 描述打得比食物庫裡更細仍找得到（「手沖藝妓黑咖啡」→「手沖黑咖啡」）
- 換一種說法仍找得到（「美式黑咖啡」→「手沖黑咖啡」）
- 名稱裡被拆開的詞仍找得到（「烤肉」→「煎烤豬肉排／五花肉」）
- 只共用一個字不算命中（「咖啡」不會撈到「咖哩飯」）
- 整串命中一定排在近似命中之前，篩掉不相干的並依相符程度排序
- 精選清單（常吃／最近各 60 種）裝不下的舊品項，打字時兩頁都找得到；沒打字時精選清單原樣不動

[`BackupScheduleTest.kt`](app/src/test/java/com/watson/nutrilog/BackupScheduleTest.kt)
- 每日備份對齊到凌晨 3 點的延遲計算（跨日、剛好 3 點、深夜與傍晚各一種）
- 這一項是純函式，因為它決定了「每一個日期檔是不是前一天結束時的完整狀態」

[`BmrCalculatorTest.kt`](app/src/test/java/com/watson/nutrilog/BmrCalculatorTest.kt)
- 蛋白質依體態目標固定倍率（減脂 1.6、維持 1.4、增肌 1.8 g/kg），所有活動量、手錶開或關都一樣
- 交給手錶量運動時，熱量的底退到久坐係數；久坐的人不管誰量都是同一個熱量
- 碳水固定佔 55%、脂肪吃差額；蛋白質高到塞不下時讓位的是碳水，脂肪守住 20% 下限
- 三大營養素加起來等於目標熱量

[`ActivityEstimateTest.kt`](app/src/test/java/com/watson/nutrilog/ActivityEstimateTest.kt)
- 整天配戴：採用全日活動消耗，運動場次比它多時改用場次；活動消耗有被採用時步數不參與
- 只有運動時戴：只採用運動場次，全日活動消耗再大也不算（它只涵蓋戴著的那幾小時），也不能擋掉步數
- 步數只算超出久坐額度的那一段（額度要扣掉食物熱效應），運動場次裡的步數先扣掉
- 讀不到時是 0 並說得出原因；步數沒超出額度時原因要說「沒超出」，不是「讀不到」

[`CalorieTargetTest.kt`](app/src/test/java/com/watson/nutrilog/CalorieTargetTest.kt)
- 運動熱量照回補比例加進目標（預設一半，可以調到全額），沒動的日子就是目標本身
- 沒在讀運動消耗時原樣返回；目標是 0 不會因為運動變成有目標

[`BackedUpProfileTest.kt`](app/src/test/java/com/watson/nutrilog/BackedUpProfileTest.kt)
- 備份的身型 JSON 是白名單：**裡面不會出現任何 API key**
- 還原後目標與身型逐欄一致，未知欄位不讓解析失敗

[`CsvRoundTripTest.kt`](app/src/test/java/com/watson/nutrilog/CsvRoundTripTest.kt)
- 匯出→匯入來回逐欄一致（含份數倍率與記錄時間）
- 食物名稱裡的逗號、引號與換行照 RFC 4180 跳脫與還原
- 缺資料維持 `null` 而不是變成 0
- 舊版（少「記錄時間」「份數倍率」兩欄）的匯出檔仍可匯入
- 去重鍵：同一筆重複匯入會撞在一起，但同名不同時間的兩筆不會
- 數字照存的值原樣寫：0.25 份、0.25 g、1.125 g 匯出再匯入都不變，整數照舊不帶小數點

[`WaterCsvTest.kt`](app/src/test/java/com/watson/nutrilog/WaterCsvTest.kt)
- 飲料的水量是每一列的欄位，來回一趟不變；沒有水量的食物匯回來還是 `null`
- 手動飲水走自己的列，不會變成一筆沒名字的食物；只喝水沒吃東西的日子也保得住

[`HydrationRecordTest.kt`](app/src/test/java/com/watson/nutrilog/HydrationRecordTest.kt)
- 寫進 Health 連線的飲水一天一筆，改了用同一個 id 加新版號蓋過去
- 0、負數、未來的日子不寫；過去的日子照當地的日期邊界，跨日光節約時間也對

[`ImageLimitTest.kt`](app/src/test/java/com/watson/nutrilog/ImageLimitTest.kt)
- 手機拍的兩億畫素照片照樣通過，剛好在上限也通過
- 超過 30 MB 一個位元組、或超過 2.5 億畫素一個像素就擋下；讀不到檔頭的另外講

[`PhotoPromptTest.kt`](app/src/test/java/com/watson/nutrilog/PhotoPromptTest.kt)
- 照片確認頁的烹調備註不填也能直接辨識，填了就完整接在照片指示後面

[`UpdateCheckTest.kt`](app/src/test/java/com/watson/nutrilog/UpdateCheckTest.kt)
- 版號逐段比數字不比字串：v2.2.10 比 2.2.9 新、v2.10.0 比 2.9.9 新，少一段當成 0
- 本機建置（「1.0-debug」）與不是版號形狀的 tag 比不了，不能說有新版
- GitHub 回應只取 tag、release 頁網址、發佈時間三欄；沒有 tag 就當作沒有正式版

UI 部分使用 [`tools/ui.ps1`](tools/ui.ps1) 依元件文字進行模擬器自動化操作：

```powershell
& ".\tools\ui.ps1" dump              # 列出畫面所有文字節點與中心座標
& ".\tools\ui.ps1" tap "記一筆"
& ".\tools\ui.ps1" type "Chicken"    # input text 只吃 ASCII，測試資料一律用英數
```

---

## 功能

### 今日

- **一週長條與日紀錄聯動**：上方為一週每日熱量達成率長條，滑動切換日期時自動維持同步，跨週時平滑換頁。
- **主數字顯示已吃熱量**：主視覺直接顯示當日已攝取總熱量，目標與剩餘額度退居次要輔助行。
- **餐別分段熱量條**：以早、午、晚、點心四色區段直觀呈現熱量攝取分佈結構。
- **三大營養素組成與兩級超標警示**：
  - 蛋白質、脂肪、碳水化合物轉換為熱量比例長條。
  - 圖例整合兩級警示邏輯：超標 10% 以內顯示暖橘（`Warning`），超過 10% 顯示朱紅（`Over`）。
  - 下方進階營養素（糖／鈉／膳食纖維／飽和脂肪）一行到底，**窄螢幕放不下時可以左右拖**，高度永遠固定（見 [issue #12](https://github.com/rowing195/NutriLog/issues/12)）。
- **固定四餐區塊**：早餐、午餐、晚餐、點心四格永遠列出，未記錄時提供直接補登入口，並自動預選該餐別。
- **五合一懸浮章印選單**：右下角自繪墨印按鈕展開拍照、相簿、常吃/文字、條碼與手動五大入口。
- **左滑刪除與復原**：紀錄列左滑時整張字卡跟著位移，放手後以彈簧回彈定位、刪除區留在原地，點擊後刪除，左下角滑出與「記一筆」同尺寸的深灰復原章，章體下沿墨線線性收縮呈現剩餘秒數，提供 5 秒復原視窗。復原以原 id 還原紀錄，備份與去重鍵均不受影響。

### 份數縮放

- 份數範圍為 **0.01～99 份、最多兩位小數**（可以打 0.25、0.75），辨識結果確認頁與手動編輯共用相同上限。
- **五段純數字雙速步進列**：提供 `−1`、`−0.1`、`+0.1`、`+1` 四顆自繪圓章按鍵，中間展示當前倍率與襯線數字。
- **中間那格也能直接打字**：點下去跳出自繪數字鍵盤，打字時整張表單的數字不動，**離開這格才換算** —— 不會打到一半就看到熱量亂跳。
- **基準值持久化與無損還原**：
  - 資料庫記錄 `portionMultiplier`。
  - 編輯已放大紀錄時，系統以 `deriveBase` 精確逆推原始 1.0x 基準，避免多次縮放產生的浮點數捨入漂移。
- **全自動字串與數值同步**：
  - 同步調整份量文字（例如 `1 碗 (250g)` 縮放為 `1.5 碗 (375g)`、`700ml` 縮放為 `1050ml`）。
  - 熱量取整數、三大營養素保留一位小數、可空進階營養素正確保持 `null`。

### 四種輸入方式

| 方式 | 運作流程 |
|---|---|
| **輸入營養素** | 熱量滿版一格、三大營養素橫排三格，搭配自繪圓章數字鍵盤與份數步進列，完全避免系統鍵盤遮擋儲存鈕問題。 |
| **拍照辨識** | 拍照或自相簿選取 → **照片確認頁**（可加一句烹調備註，例如「水煮、沒加油」）→ 壓縮長邊至 1024 px → Gemini 或 OpenRouter 結構化辨識 → **確認畫面**逐項勾選與微調後入庫。 |
| **常吃／文字輸入** | 同一個輸入框服務兩條路：打字即時模糊篩選個人食物庫，找到直接點；篩不到時才把那句描述（如「無糖綠茶 700ml」）交給 AI 估算。 |
| **掃條碼** | 掃描條碼或手動輸入 → 優先讀取本機快取，無快取則查詢 Open Food Facts → 輸入食用公克數自動換算。 |

所有有輸入的畫面（上表三條打字路徑 ＋ 搜尋 ＋ 設定的每日目標）共通一件事：**點輸入框與鍵盤以外的空白處即可收鍵盤**，回到沒在打字的版面，已經打的字與數值都保留。編輯表單裡自繪的數字鍵盤同樣照這個方式收 —— 對使用者而言那與系統鍵盤是同一件事。

常吃頁與搜尋頁另有第二個入口：**手指一開始捲清單，鍵盤就自己收起來**。捲清單本身就表示使用者不在打字、正在看結果，而鍵盤佔掉半個畫面時剩下的清單只有兩三列。左右滑換分頁與點分頁標籤刻意不收（前者可能只是看一眼另一頁就要繼續打字，後者是畫面自己在捲）；編輯表單也不掛這條 —— 那裡捲動是為了把儲存鈕拉回畫面上，收掉數字鍵盤正好相反。

### 常吃頁：一個框，兩條路

- 最上面一個搜尋框，**打字即時篩所有吃過的品項**，找到直接點那一列帶進編輯表單 —— 不必在清單裡慢慢翻，也不必跳去搜尋頁。
- 沒打字時兩頁是精選（常吃只看近 90 天，兩頁各 60 種），給「不打字直接點」用；一打字就改篩全部歷史，幾年前吃過的也找得到。打字時「常吃」那頁的次數是全部歷史累計，所以會比沒打字時多。
- 篩選是**模糊比對**（原理見〈[設計決策](#一個搜尋框服務兩條路以及中文為什麼不能用-contains)〉）：「烤肉」找得到「煎烤豬肉排／五花肉」，而「咖啡」不會把咖哩飯撈上來。
- 找得到的排在前面，同分的維持原本「常吃」的次數順序與「最近」的日期順序。
- 底下那行會看情況講話：上面還篩得到東西時是「不是上面這些？」，真的一筆都沒有才說「沒有『⋯』？」—— 上面明明列著相近的卻說沒有，等於這個 app 沒在看自己的清單。
- **底下是成對的兩顆章**：「AI 估」（憑模型印象，快）與「AI 查」（先上網找官方營養標示再算，慢一點）。兩個名字只差一個字，而那個字正好就是唯一真正的差別（見〈[要不要查網路，由使用者按鈕決定](#要不要查網路由使用者按鈕決定)〉）。
- **鍵盤一開，底下那區自動收到只剩兩顆章**，把高度讓給清單；真的篩不到時標題會留著，因為那時候它是畫面上唯一還在講話的東西。兩顆章不跟著收 —— 收掉說明是省版面，收掉動作本身會讓人以為按鈕不見了。
- **離開搜尋框時是兩段動畫：先落地，再長出來。** 等鍵盤真的退完、整區沉到定位，文字才從章的底邊往上長出來。兩件事一起做的話，畫面在同一段時間裡往兩個方向動，讀起來是彈一下而不是一個動作。**等多久是問系統鍵盤的，不是寫死的秒數**，所以各家輸入法快慢不一樣也都接得上。

### 查網路：一顆章 AI 估、一顆章 AI 查

連鎖店的品項網路上有官方營養標示，模型憑印象估的跟官方公布的差得不少。
所以常吃頁底下是兩顆章，打完描述自己選：

| 按哪一顆 | 發生什麼事 | 實測「麥當勞 大麥克」 |
|---|---|---|
| **AI 估**（主章） | 模型憑自己的知識估，快、不花搜尋額度 | 540 kcal（美國規格） |
| **AI 查**（次章，深灰） | 先上網找那個品項的營養標示，再把找到的表格交給模型讀 | **503 kcal**（台灣麥當勞官方頁） |

- 要用第二顆章得先到 **設定 → API 管理** 選一個搜尋來源；沒選的話那顆是外框章、
  按不下去，底下會講一句為什麼。
- **搜尋壞掉不會讓辨識失敗**：查不到就讓模型照原本的方式估，錯誤留在 logcat。
- 搜尋結果放在**使用者輸入前面**並明講它是參考資料：它是外部來的、可能過期或根本在講別的品項
  （實測結果裡混著部落格整理的表格，數字和官方差了將近 100 大卡）。

### 兩家 AI 供應商：拍照與文字各選各的

設定 → API 管理裡，**拍照辨識**與**文字辨識**各自選要送去 Gemini 還是 OpenRouter，
OpenRouter 的模型也分兩欄（拍照一欄、文字一欄）。不做成一個總開關，是因為拍照要吃得下圖片的模型，
而文字那條路上常用的 OpenRouter 免費模型是純文字的 —— 共用一個選擇的話，救了拍照就會弄壞文字。

- 拍照選了 OpenRouter 卻沒填拍照用的模型時，會直接擋下來並講原因，不會拿文字那個模型去送圖片。
  Gemini 的模型本來就看得懂圖片，不用另外挑。
- 缺 key 時訊息會指名是哪一家，兩把 key 不會填錯把。
- 週報／月報交給哪一家是第三個獨立的選擇，沿用那一家的金鑰與模型。

兩家共用同一份 prompt（`AiPrompts`），但傳輸格式、強制 JSON 的手法、錯誤訊息全都不一樣，
所以是兩個獨立的 client、沒有抽共同介面。

### 歷史（月曆）

- 一格一天的月曆視圖，格子內顯示當日熱量，並以背景深淺及超標朱紅色直觀呈現。
- 「看得出空白」設計：未記錄天數一眼即可辨識，避免清單模式造成的漏記遮蔽。
- **左右滑就換月**，拖的時候上方那個「2026 / 09」也跟著手指走，下一個月的月份從旁邊補進來
  —— 和今日頁的週長條同一種手感。兩側箭頭留著，兩條路做同一件事。**一次滑動就是一個月**，不管滑多快。
- 下方即時由 SQLite `GROUP BY` 計算當月總記錄天數、平均熱量與超標天數。
- **超標是照那一天當時的目標判斷的。** 每天的目標會留一份快照，改了熱量目標，上個月的格子不會集體變紅或變綠；
  這個功能出現之前的日子沒有快照，照目前的設定判斷。
- 不在本月時，畫面**最底下**會出現一顆空心章「回到本月」。它不在報頭裡：
  它是一個動作而不是某一個月的內容，放到分頁器外面的底部，它出現時吃掉的是月曆底下那塊
  本來就空的地方，格子一格都不會動。

### 搜尋與個人食物庫

- 點擊右上角放大鏡開啟。
- **未輸入關鍵字時**：展示個人食物庫，支援左右滑動切換「90 天常吃」與「全部最近」。
- **輸入關鍵字時**：切換為即時全文搜尋模式，支援多關鍵字空白分割比對（名稱 + 份量文字）。**不限筆數**，幾年前的紀錄也找得到；不是今年的日期會帶年份（「2024/3/14」）。
- 點擊任一項目直接帶入編輯表單，兼顧便捷與可編輯性。

這裡的搜尋與[常吃頁那一個](#常吃頁一個框兩條路)**搜的不是同一種東西**：這頁搜的是逐筆紀錄（每一筆帶日期），回答的是「我哪天吃過這個」；常吃頁搜的是聚合後的品項，回答的是「拿一個品項來記一筆」——日期在那裡是雜訊，而且同一樣東西會重複出現二十次。兩頁共用同一個食物庫元件，但主要工作不同，所以沒有合併成一個要切換模式的畫面。

### 辨識失敗：換一家再試

- 失敗時從底下升上來一張佔六成高的面板，上面留著失敗的原因 —— 那才是判斷「該換什麼」的依據。
- 供應商左右兩家（和設定頁同一種圈選），切換時底下的模型區跟著滑過去：Gemini 是固定型號的清單，OpenRouter 是自由填的模型路徑。
- **在這裡改的就是設定裡的那一組**，不是另一份副本。**拍照與文字各改各的** —— 拍照要看得懂圖片的模型，文字那邊常用的是純文字模型，混在一起會救了一邊弄壞另一邊。
- 選完按「重試」才會真的再送一次：每改一下就自動發一次請求會白白花掉額度。

### 飲水：手動加減與飲料自動帶入

- 今日頁營養素底下那一排：中間是當天的飲水量，兩側各一顆 **±50 ml**。
- **飲料的水量跟著那一筆紀錄走。** 編輯表單多一格「水量」，AI 辨識飲料時會自己填
  （700 ml 的珍奶就是 700），確認畫面看得到那個數字才入庫。刪掉那筆飲料，水量跟著消失。
- **手動那一段是獨立的**，存在自己的表裡，不會在紀錄清單長出一堆 0 大卡的白開水。
  兩者相加才是當天的量，而總量不會被減成負的。
- **點飲水量打開明細**：這個數字是哪幾杯飲料、再加上手動按了多少。
- 有開 Health 連線的寫入、而且給了飲水權限時，當天的飲水量也會寫過去（一天一筆，改了就覆寫）。飲水權限是選配，沒給就只寫餐點。
- **匯出的 CSV 兩種都帶得走**：飲料的水量是每一列的欄位，手動的那一段以日期為單位
  自己一列（食物名稱留空）—— 那天一筆食物都沒記也保得住。

### 健康連線：運動消耗加進當天的額度

- 於 **設定 → Health 連線** 打開「讀取運動消耗」後，今日頁「目標」底下多一行「運動 +350 ›」，
  當天的額度跟著變多。點那一行看明細：數字從哪裡讀來、吃了多少、今天的目標怎麼算出來的。
- **加進目標，不是從吃掉的裡面扣。** 你記的東西不該被改寫 —— 「吃了 1,800」就是 1,800，
  變的是那天能吃多少。今日頁、餐別長條、週長條、月曆格子與月摘要的超標判斷全部走同一個
  `effectiveCalorieTarget()`，不會出現「今日頁說還有 200、月曆卻把同一天標紅」。
- **打開它的時候，熱量目標的底會退到久坐基準**，運動改由手錶量。活動係數的定義本來
  就含運動（「輕度」＝每週運動 1–3 天），不退的話同一批熱量會算兩次。關掉就算回你
  填的活動係數。**蛋白質只看體態目標，不受這個開關影響。**
- **運動熱量預設只回補一半**（設定 → 每日目標 →「運動熱量回補」，可改 25/50/75/全額）。
  手錶估熱量普遍偏高，全額吃回去等於把高估的部分也吃掉。
- **手錶配戴方式**決定哪一種資料算數：整天戴就用全日活動消耗，只有運動時戴就只算
  運動場次（全日那個數字只涵蓋戴著的那幾小時，當一整天用會低估）。
- **步數只算超出久坐基準的那一段。** 久坐的熱量目標本來就含日常走動（走去買午餐那種），
  所以四千步左右以內是 +0，走得比那多才開始加；跑步那段已經算在運動場次裡，那些步數會先扣掉。
  活動消耗有被採用（整天配戴）時步數不參與，兩者估的是同一件事。
- **讀不到就講原因，不生猜的數字**：不會拿「總消耗扣基礎代謝」去推活動量（理由見
  〈[寧可說「讀不到」](#寧可說讀不到也不給一個猜出來的運動消耗)〉）。「讀不到」和「讀到了但沒超出」
  分開講 —— 一個要去調權限，一個是多走兩步。
- **寫入是選配、預設關閉**，而且只在新增、編輯、刪除當下寫，不在背景整批同步。餐點寫成營養紀錄，
  有給飲水權限的話當天的飲水量也一起寫。每一筆用 `nutrilog_<紀錄 id>` 當 clientRecordId，
  改同一筆就是覆寫，不會長出重複的紀錄。
- 每天的值快取在 Room 的 `daily_health_metrics`，週長條與月曆一打開就要用，不能等健康連線慢慢回。
- **數字和手錶的 app 對不上時，設定 → Health 連線最底下有「讀取診斷資訊」**：列出今天從
  健康連線讀到的原始值（活動大卡、總消耗、步數、運動場次、四個權限各有沒有）以及
  App 採用了哪一個。同一份會寫進 logcat（`adb logcat -s HealthDiagnostics`）。
  「無資料」和「0」是分開的 —— 前者是對方沒寫進來，後者是那天真的沒動。

### 依身型計算每日目標

- **設定 → 每日目標 → 依身型計算**：填性別、年齡、身高、體重、活動量與目標（減脂／維持／增肌），
  用 Mifflin-St Jeor 算出基礎代謝與每日消耗，並給出建議的熱量、三大營養素與各餐配比。
- **按「套用」才會寫進目標** —— 和 AI 辨識、週報推薦同一條規則：算出來的只是建議。
- **蛋白質只看體態目標**（減脂 1.6、維持 1.4、增肌 1.8 g/kg），活動量與手錶設定只影響熱量。
  結果會標出「蛋白質每公斤 N g」，這個數字高不高一眼看得出來。
- 身型本身會存下來：下次打開不必重填，週報也要用體重判斷蛋白質夠不夠。

### AI 週報／月報

- 入口在月曆月摘要底下那一列。返回鍵回月曆 —— 報表講的就是月曆上那段期間。
- **統計是本機算的，隨時都看得到**：記錄天數、平均攝取、運動消耗、每日消耗、熱量收支
  （換算成大約幾公斤），以及和上週（上個月）比的增減。
- **報告要花一次 AI 呼叫，按了才送出**，不會自動產生。一筆紀錄都沒有的期間不給產生 ——
  按下去只會換來一份在講「沒有資料」的報告。
- 週報結尾會附**建議的下週每日目標**，同樣是按「套用為每日目標」才寫進設定。
- 報告交給哪一家 AI 在 **設定 → API 管理 → AI 報告** 選，沿用那一家的金鑰與模型，不另外要金鑰。
- 報告存在 app 私有目錄，隨時可以重新產生，因此**不進 Drive 備份**（備份的是紀錄與設定）。

### App 圖示

- **設定 → 外觀 → APP 圖示**：八款可選 —— 糯糯（預設）、肥貓、菲比啾比、快樂牛馬、黑糯糯、紅糯糯、冰紅茶、牢大。
  點一下就換，桌面上的圖示會先消失一下再出現，有些桌面要重新整理才看得到。
- **只能從內建的款式挑，不能用自己的照片。** Android 不讓 app 在執行時把自己的桌面
  圖示換成任意圖片，理由見[設計決策](#換-app-圖示為什麼只能選內建的)。
- **從 v1.18.1 以前的版本更新上來時，桌面上原本那顆圖示可能會失效**，要從 app 抽屜
  重新拉一次到桌面。app 抽屜裡的入口、飲食紀錄與設定都不受影響。

### 檢查更新

- **設定 → 關於 → 檢查更新**：到 GitHub 查最新的正式版。有新版時寫「有新版 v2.2.8（10/5 發佈）」，
  底下一顆章「前往下載 v2.2.8」用瀏覽器開那一版的 release 頁（看得到更新內容，再自己點 APK）。
- **每天自動查一次**（關於頁的開關，預設開），和 Drive 備份無關，沒連雲端備份也會查。
  查到新版**不發通知**：今日頁右上角的設定圖示亮一個紅點，設定選單「關於」那列寫「2.2.7 · 有新版 2.2.8」。
  在關於頁看過之後紅點就收掉，下一個新版出來才會再亮。
- 自己從原始碼建的 debug 版（版號「1.0-debug」）比不了版號，只會講「這是開發版，最新正式版是 vX」，不亮紅點。
- 不需要任何新權限，也不帶 token（公開 repo 每小時 60 次，一天查一次用不完）。

### 匯出／匯入 CSV

- 經由 Android 儲存存取框架（Storage Access Framework, SAF）將全量飲食紀錄匯出為標準 CSV，或把匯出過的 CSV 讀回來。
- 檔案開頭內嵌 **UTF-8 BOM**，確保 Excel 與 Google 試算表正確辨識繁體中文。
- 缺失營養素輸出為空白欄位而非 0，匯入時也維持 `null`，忠實保留原始資料型態。
- **匯入前先停在確認面板**：會先算好「新增幾筆、日期範圍、略過幾筆重複、跳過幾列壞資料」再問要不要寫進去。
- **重複自動略過**：以「日期＋名稱＋份量＋記錄時間」辨識同一筆，同一份檔案匯入兩次不會變成兩份，也能把兩支手機的紀錄合併起來。
- 匯出→匯入→再匯出實測為完全相同的檔案，換手機可以無損接回。

### Google Drive 雲端備份

- 於設定頁連結 Google 帳號後，**每天自動**將紀錄備份至雲端硬碟主頁 `NutriLog/` 資料夾，一天一個日期檔、僅保留最近 30 天。
- 背景排程採用 **WorkManager**（非 AlarmManager），可於 Doze 省電模式與重新開機後維持運作。排程對齊至每日凌晨 3 時，因此每個日期檔即為「前一日結束時的完整狀態」；實際執行時間會受 Doze 影響而順延至裝置下次喚醒，WorkManager 保證的是頻率而非準點。
- 每份備份皆為**資料庫完整快照**而非當日增量，最新一份永遠包含全部紀錄。
- 授權範圍僅 **`drive.file`**：只能存取本 app 自行建立的檔案，讀不到雲端硬碟上的其他資料。此範圍非 Google 定義之受限範圍，無需安全評估審查。
- 備份內容與本地匯出**完全相同**，可直接於 Drive 下載、以試算表開啟，或改用本地匯入讀回 —— 資料不會被鎖在 app 裡。
- **唯一的例外是身型與每日目標**：它們是設定而不是紀錄，塞不進 CSV 的欄位，所以另外存一份 `nutrilog-profile-<日期>.json`。那是一份**白名單**（只有目標與身型欄位），**API 金鑰永遠不會被備份**，有測試專門守著。還原時併進同一個確認面板，不另外問一次。
- 「連結 Google Drive」會**順便把雲端的紀錄接回來**：換手機時自動比對雲端備份，走與本地匯入相同的確認面板（新增幾筆／略過幾筆重複），確認後才寫入資料庫。
- 此功能為選配。未連結時不會碰 Google Drive，也不會排入任何背景工作。
- 首次使用需自行於 Google Cloud 建立 OAuth client，可執行 [`tools/setup-google-drive.sh`](tools/setup-google-drive.sh) 精靈完成設定。

---

## 設定：選單加子頁，三把 key

設定分兩層：先是一排項目（外觀、每日目標、Health 連線、API 管理、雲端備份、本地資料管理、關於），
點進去才是內容。七段疊成一條長捲軸的話，找一個開關要捲很久；
選單每一列右邊直接寫著現在的值（深淺模式、熱量目標、健康連線讀寫狀態、key 設了沒、
Drive 連了沒），不用點進去就看得到自己設過什麼。
**子頁的返回鍵回選單，不是回今日頁** —— 不然每改一項設定都要重新點兩次進來。

三把 key 都在 **設定 → API 管理** 底下，各自一頁：

| key | 用在哪 | 怎麼拿 |
|---|---|---|
| **Gemini** | 拍照、文字辨識與報告的預設供應商 | [Google AI Studio](https://aistudio.google.com) 免費申請 |
| **OpenRouter** | 拍照、文字辨識或報告的另一家（選配；拍照要另外填看得懂圖片的模型）| [openrouter.ai](https://openrouter.ai) |
| **Tavily** | 「AI 查」的搜尋來源（選配）| [tavily.com](https://tavily.com)，免費層 1000 次/月 |

Key 僅安全儲存於本地 DataStore，**不會打包進 APK 或上傳第三方伺服器**。

同一頁還可以選 Gemini 模型（五個攤開圈選，預設 `gemini-3.7-flash`，要快一點可以選
`gemini-3.5-flash-lite`）、拍照與文字辨識各要走哪一家、「AI 查」要用誰查，以及
**週報／月報交給哪一家寫**（沿用那一家已經填好的金鑰與模型，不另外要一把）。
**三把都是選配**：不填的話手動輸入、常吃、條碼照常能用；要用 AI 辨識，至少要填你選的那一家。

---

## 設計決策

這一節是「為什麼這樣設計」；「什麼東西壞過、怎麼追出來的」在 [已關閉的 issues](https://github.com/rowing195/NutriLog/issues?q=is%3Aissue+is%3Aclosed)。

### 為什麼紀錄用 Room，設定用 DataStore

飲食紀錄具備日增長、關聯查詢（依日期範圍、餐別合計、分組統計）特性，採用具備索引的 Room SQLite 關聯式資料庫是最可靠做法。設定資料量極小且單一，採用 DataStore Preferences 即可滿足需求。

### 為什麼完全不需要相機權限

- **拍照**：使用 `ActivityResultContracts.TakePicture()` 委託系統相機 App 處理。
- **掃碼**：使用 Google Play 服務之 Google Code Scanner，掃描視窗獨立於 Google Play 服務行程執行。
- **相簿**：使用系統 `PickVisualMedia` 照片選擇器。

本 App 本身無需宣告 `CAMERA` 或儲存權限。Manifest 裡只有 `INTERNET`（外部查詢）與 Health 連線的讀寫權限；
後者要使用者在系統畫面同意才會生效，不開健康連線就用不到。

### 「紙與墨」出版物風格與內嵌字型

- **色票**：淺色米紙底色 `#F7F3E9`、深色暖黑 `#17150F`、朱紅焦點 `#D8462A`、琥珀警示 `#B8791F`。
- **規線取代色塊**：版面層次完全依靠 2px 墨線（`Rule`）與 1px 細線（`Hairline`）劃分，堅決不用 Material 浮凸色塊卡片。
- **字型**：純數字、日期、單位與按鍵採用內嵌 **Neucha**（`res/font/neucha.ttf`）手寫體，並已正規化數字與標點的側邊留白（原版 `1/2/3/4/5/7` 側邊留白為 0，導致 `11`、`0.2` 等組合會黏在一起） —— 每天隨手記一筆的東西，數字長得像手寫的比像印刷品更貼近它在做的事；中文採用內嵌 **jf open 粉圓**（`res/font/jf_open_huninn.ttf`）—— 圓體的柔和調性搭配手寫數字，而粗細均勻、小字級撐得住；標題輔以拉開字距（`letterSpacing`）建立清晰層級。

### 一個搜尋框服務兩條路，以及中文為什麼不能用 `contains`

常吃頁最上面只有一個輸入框，它同時是「篩自己的食物庫」與「把描述交給 AI」的入口。做成上下兩個框、或一個框配兩顆同級按鈕，都要使用者**在打字之前**先決定用哪一種搜尋，而選錯是安靜的：想篩清單卻送去 AI，等於白花一次 API 呼叫與數秒等待；想問 AI 卻打進篩選框，只會看到空清單、像是壞了。一個框則沒有東西要選 —— 打字時清單自己收斂，收斂到空的那一刻正好就是該問 AI 的時候。同理，鍵盤上的送出鍵只收鍵盤、不送 AI：那條要花錢也要等的路，一定要明確按下那顆章才走。

**比對不能只用 `contains`。** 中文沒有空白可以拆詞，而使用者為了讓 AI 估得準，打的往往比食物庫裡存的更細、或根本是另一種寫法。實際做法是**單字與相鄰兩字（bigram）各算一份重疊比例，相鄰兩字加權 2 倍**，門檻 0.3：

| 關鍵字 → 食物庫裡的 | 該不該中 | 只看相鄰兩字 | 只看單字 | 加權合分（現行）|
|---|---|---|---|---|
| 手沖藝妓黑咖啡 → 手沖黑咖啡 | 該中 | 0.50 ✅ | ✅ | 0.58 ✅ |
| 美式黑咖啡 → 手沖黑咖啡 | 該中 | 0.50 ✅ | ✅ | 0.54 ✅ |
| 烤肉 → 煎烤豬肉排／五花肉 | 該中 | **0.00 ❌** | ✅ | 0.50 ✅ |
| 咖啡 → 咖哩飯 | 不該中 | 0.00 ✅ | **1.00 ❌** | 0.25 ✅ |

兩種 n-gram 各自補對方的洞：只看相鄰兩字會漏掉在名稱裡被拆開的詞（「烤肉」在「煎烤豬肉排」裡是烤…肉，相鄰兩字一個都對不上），只看單字則會把「咖」對上咖哩、「肉」對上任何有肉的東西。加權相加之後兩件事同時成立，這也是 CJK 搜尋的標準形狀 —— 單字與相鄰兩字各建一份索引再加權合分。門檻 0.3 最好記的意義是「**兩個字的關鍵字，兩個字都要出現**」：只中一個是 0.25，剛好落在門檻外。整串命中另外給 2.0，因為近似分數的上限就是 1.0，撞在一起就無法保證「真的有這個」排在「長得有點像」前面。

**它沒有語意。**「拿鐵」與「牛奶咖啡」一個字都不共用，這裡就是配不起來，跨語言（latte／拿鐵）亦然。那正是底下那兩顆章存在的理由，不在這裡補同義詞表。規則由 `FoodLibraryMatchTest` 釘住 —— `adb shell input text` 只吃 ASCII，中文行為在模擬器上根本打不出來，只能靠單元測試驗。

### 要不要查網路，由使用者按鈕決定

不是設定裡的總開關，也不是讓模型自己判斷。理由很簡單：**使用者在打字的當下就已經
知道自己要哪一種了** —— 他在食物前面加店名，就是想要官方資料；打「兩顆蛋」那種東西本來
就沒有官方標示可查，多等十秒只是浪費。這個判斷在使用者腦裡，不在模型那邊。

三條路都實際試過，兩條失敗：

| 試過的做法 | 結果 |
|---|---|
| **Gemini 搜尋 grounding**（`tools: [{google_search:{}}]`）| 免費層的配額是 0，每一次文字辨識都變 429，整條路直接掛掉 |
| **讓模型自己下查詢**（tool calling）| 不強制就不收斂（三輪查詢後仍未回傳結果）；強制之後它自己下的查詢反而撈到美規數字 |
| **自己先查、把正文接進 prompt**（現行）| 拿到台灣官方頁的數字，而且模型仍然只收到**一個普通請求** |

第二條的失敗是結構性的：**多給一個工具就不能再強制 `tool_choice`**，而那正是 OpenRouter
那條路鎖住 JSON 的唯一手段。這個題目的搜尋意圖是恆定的（永遠在問營養標示），
沒有需要模型推敲的餘地，放手讓它推敲反而弄丟穩定性。實驗留在 `tool-calling-search`
分支當紀錄，不合併。

搜尋來源選 Tavily 而不是 Brave：**Brave 免費層回的是 SERP 片段**，實測同一個查詢四筆裡
三筆在講麥克雞塊和薯條，唯一有數字的是 2018 年的新聞稿，而它的 AI 摘要要付費方案；
Tavily 免費層回的就是清洗過的頁面正文。

### 兩份 JSON schema 的 `required` 不一樣，而且是故意的

糖、鈉、膳食纖維、飽和脂肪這四欄在 **Gemini 那份是 `required` 但仍可為 `null`**，
**OpenRouter 那份維持選填**。這是實測出來的，不是兩邊忘了同步。

選填等於給模型一個整個略過的藉口，改成必填之後 Gemini 那邊就填得出來了；
但同樣的改法在 OpenRouter 那個免費健康模型上，兩次都把**鈉 1092.5 毫克換算成 1.092 公克
填進膳食纖維**。逃不掉鍵之後它選了隨便找個欄位塞，而不是老實填 `null` —— 而
**錯的數字比空白更糟**：確認畫面只列出熱量與三大營養素，進階那四欄沒人看得到，
進去就是默默落地。詳細的追查過程見
[issue #11](https://github.com/rowing195/NutriLog/issues/11)。

### 蛋白質只看體態目標，活動量只管熱量

蛋白質的每公斤克數只由體態目標決定：**減脂 1.6、維持 1.4、增肌 1.8 g/kg**，
不受活動量與「讀取運動消耗」開關影響。活動量與手錶設定只拿來算熱量。

這段算法改過兩次。最早是維持一律 **每公斤 1.7 g**，那是運動員區間的上緣
（64 公斤、只想維持體重的人會算出 108 g）；之後改成跟著活動量走（久坐 1.0 → 非常高 1.8，
減脂與增肌各再加 0.2）。現在回到只看目標，但倍率比最早那版低，維持是 1.4。
結果畫面會標出「蛋白質每公斤 N g」，這個數字高不高一眼看得出來；
`BmrCalculatorTest` 釘住「所有活動量、手錶開或關，蛋白質都一樣」。

連帶的一件事：原本脂肪固定佔 25%、碳水吃剩下的差額，所以蛋白質一降，省下來的熱量
一克不剩全部跑到碳水（實測被推到 58.7%，建議範圍 50–65% 的上緣）。改成**碳水固定 55%、
脂肪吃差額**；蛋白質高到塞不下時讓位的是碳水，**脂肪守住 20% 下限** —— 脂肪太低會影響
荷爾蒙與脂溶性維生素吸收。

### 運動消耗加進目標，不從吃下去的扣回去

同樣一件事有兩種寫法：把運動消耗從「今天吃了多少」裡扣掉，或是加到「今天可以吃多少」上。
前者比較好寫，但它**改寫了使用者記的東西** —— 他明明吃了 1,800，畫面卻說 1,450。
紀錄是這支 app 唯一的事實來源，不能因為戴了手錶就變成另一個數字。

所以運動消耗只動目標那一側，而且全 app 只有一個 `effectiveCalorieTarget()`：
新增任何拿熱量去比目標的地方都得走它，否則就會出現今日頁與月曆對同一天有兩種說法。

### 寧可說「讀不到」，也不給一個猜出來的運動消耗

健康連線裡拿得到的東西不只一種，早期的版本排了三段退路：活動消耗 → 總消耗扣掉基礎
代謝 → 整天步數直接換算。**總消耗扣基礎代謝那一段已經移除**，因為它看起來像測量值，
其實是估算值：

- 它拿兩個一千五百多的大數字相減，去換一個一百多的小數字。三星寫進健康連線的總消耗
  含它自己算的靜態消耗，我們扣的是自己用 Mifflin 算的，兩邊差幾個百分點，誤差就和答案
  同一個量級 —— 實測手錶記 153 大卡，這條路算出 **39**。
- 總消耗是從午夜累加上來的，要扣對就得引進「今天過了幾成」，同一天在不同時刻讀會得到
  不同的數字。實測那支手機上的總消耗甚至沒有任何 app 寫過，是系統自己推出來的。

**步數則改成只算超出久坐基準的那一段**，不再整天直接換算。三星不把日常的活動消耗寫進
健康連線，沒開運動紀錄的日子裡步數是唯一真的量到的東西；但久坐的熱量目標本來就含日常
走動，整筆加進去就是同一批熱量算兩次。所以先扣掉久坐已經含的那一段（約四千步），
運動場次裡的步數也先扣掉，剩下的才換算。活動消耗有被採用時步數完全不參與。

兩種方式都沒有資料時就顯示讀不到並講原因；「讀不到」和「讀到了但沒超出」分開講。

**健康連線是共用池**：手機自己也會寫一份步數，沒在健康連線的資料來源排好優先順序的話，
讀到的會是兩份疊加。數字和手錶的 app 對不上時，先看設定 → Health 連線最底下的「讀取診斷資訊」。

### 活動係數與運動消耗只能取一個，而且運動只回補一半

活動係數的定義本身就含運動 —— 這個 app 的選項寫的就是「輕度（每週運動 1–3 天）」。
所以「係數目標 ＋ 今天的運動」是同一批熱量算兩次：64 kg／159 cm 的人輕度係數是 2087，
再加一趟 45 分鐘的跑步（約 408）就變成 2495，對一個只想維持體重的人偏高得離譜。

所以打開「讀取運動消耗」之後，熱量的底會退到久坐基準（同一個人是 1821），運動由手錶
另外補。關掉就算回係數那個數字。兩者不並存。

補的時候**預設只補一半**。穿戴裝置估能量消耗是它最不準的一項——它沒有直接測氣體交換，
只能從動作與心率推，系統性回顧給的誤差從 9% 到 40% 以上都有，某些裝置的 MAPE 甚至破百。
營養師的普遍做法也是把活動量設低、運動熱量只回補 25–50%。同一個人跑 45 分鐘，
回補一半之後是 2025，而不是 2495。

### 換 App 圖示為什麼只能選內建的

最初的需求是「從相簿挑一張照片當圖示」，但 **Android 沒有任何 API 讓 app 在執行時
換掉自己的桌面圖示**：圖示是編譯進 APK 的資源，系統只認資源 ID。

唯一的官方作法是 `activity-alias`：manifest 裡事先放好幾個 alias、各自指定 icon，
執行時用 `PackageManager.setComponentEnabledSetting` 開一個、關其他。所以能選的就是
APK 裡事先放好的那幾款。

真正能用到相簿照片的另一條路是「釘一個帶自訂圖片的捷徑到桌面」（`requestPinShortcut`），
但那是**多一顆**圖示、原本那顆還在，所以沒有採用。（有些手機可以換任何 app 的圖示 ——
那是 Samsung One UI、Nova 這類桌面自己的功能，不是 app 給的。）

這個做法有一個代價：`MainActivity` 不能再帶 MAIN/LAUNCHER（兩邊都有，桌面就會出現兩顆），
所以**從舊版更新上來時，之前釘在桌面的捷徑會失效** —— 它記的是 `MainActivity` 這個元件。

### 動畫要問系統，不要自己數毫秒

常吃頁離開搜尋框時同時有兩件事想發生：`imePadding()` 跟著鍵盤退場縮回去（整區往下沉），
以及剛剛收起來的說明文字要長回來。**兩件事一起做的話畫面在同一段時間裡往兩個方向動**，
讀起來就是彈一下；排成兩段之後是「先落地、再長出來」，那才讀得成一個動作。

第二段什麼時候開始，**問 `WindowInsets.ime` 的 bottom 是不是 0，不自己數毫秒**：
各家 IME 的退場長度不一樣（大約 200～300ms），寫死一個延遲在慢的機器上會提早搶拍、
在根本沒有鍵盤動畫的機器上則是乾等一段什麼都沒發生的空檔。

月曆報頭那個月份用的是**借位**：它兩側站著箭頭、做不成分頁器的一頁，所以改成讀分頁器的
即時位移自己位移，鄰月的字從旁邊補進來。**純視覺，從頭到尾不碰分頁器自己的捲動狀態** ——
反過來做（拿即時值去驅動另一個分頁器的位置）正是今日頁那兩個換頁 bug 的共同根源。

### 深淺主題：語意帶「inverse」的顏色角色會對調

不用 Material 成品容器就得自己承擔兩件事，兩者都曾經在深色模式下造成整段文字看不見。

- **`LocalContentColor` 的預設值是純黑**，只有 M3 的 `Surface` 會覆蓋它。本專案的畫面是 `Modifier.background()` 疊出來的，畫在 `Scaffold` 之外的覆蓋層（新增選單、`Dialog`）裡沒指定 `color` 的 `Text` 會一路吃到黑色 —— 淺色模式下黑字配米底剛好正確，所以只有深色模式會現形。現已於 `NutriLogTheme` 根部統一提供 `LocalContentColor = onSurface`。
- **遮罩用 `scrim` 而非 `inverseSurface`**。`inverseSurface` 的語意是「與目前主題相反的表面」，深色模式下它是亮色，拿來當遮罩會把背景刷亮、使面板成為畫面上最暗的一塊。`scrim` 於兩套配色皆明確指定為 `Paper.Ink`，永遠是壓暗。

### 形狀即層級：自繪向量元件

完全替換所有 M3 預設外觀元件：
- `StampButton`：墨色實心印章（主要確認動作）。成對的動作（匯出／匯入）維持相同形狀，靠退一階的深灰底色區分方向；次要動作用空心章，破壞性動作用空心朱紅章
- `PillButton`：圓角藥丸（就地確認、查詢）
- `TextAction`：純文字按鈕（次要切換）
- `RoundKey`：圓章按鍵（自製數字鍵盤、步進器）
- `BallotRow` / `MealPicker`：單選圓形圈選
- `SquareCheck`：複選方形打勾框
- `NutriTextField`：全封閉外框 + 3px 底部加重規線
- 全自繪 24 格 1.6dp 圓端點 `*Mark` 向量圖示，杜絕通用 Material 圖示造成的粗糙感。

---

## 外部 API

### Open Food Facts

```
GET https://world.openfoodfacts.org/api/v2/product/{barcode}.json
```

- 無需 API Key，請求需帶規範之 User-Agent。
- 每 IP 每分鐘限制 15 次，查詢結果自動寫入 `cached_products` 本機快取。

### Gemini API

```
POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent
```

- API Key 走 `x-goog-api-key` HTTP Header。
- 透過 `responseSchema` 鎖定純 JSON 結構化輸出。
- 遇到 5xx／逾時自動指數退避重試，最多試 3 次。
- **不加搜尋 grounding**（`tools: [{google_search:{}}]`）：實測免費層的 grounding 配額是 0，
  開了之後每一次文字辨識都變 429，而且那個 429 的 body **沒有 `QuotaFailure` 明細**，
  只能開關對照才分離得出來。

### OpenRouter

```
POST https://openrouter.ai/api/v1/chat/completions
```

- 另一家供應商，拍照、文字辨識與報告都能選它。文字的預設模型是 `inclusionai/ling-3.0-flash-sante:free`（純文字）；
  拍照的模型另外一欄、沒有預設值，要自己填一個看得懂圖片的模型。
- 圖片用 OpenAI 格式的多模態訊息帶進去（`image_url` 配 data URI）。
- **用強制函式呼叫鎖 JSON**（定義一個函式、參數就是那份 schema，再用 `tool_choice` 強制呼叫），
  因為那個模型的 `supported_parameters` 裡**沒有** `response_format`。
  回傳在 `choices[0].message.tool_calls[0].function.arguments`，而那是**字串包著的 JSON**。
- **402 是餘額不足**（免費模型也需要帳號裡有額度才跑得動），401 才是 key 的問題 ——
  Gemini 那邊沒有前者這種狀態。
- 換模型之前先查 `openrouter.ai/api/v1/models`，確認新模型的 `supported_parameters` 有 `tools`；
  拍照用的還要看 `input_modalities` 有沒有 `image`。

### Tavily

```
POST https://api.tavily.com/search
```

- 「AI 查」按下去時才呼叫，免費層 1000 次/月。
- **不開 `include_answer`**：那會回一段 AI 摘要，而摘要傾向給「約 500 至 550」這種跨地區區間；
  自己的 prompt 讀官方表格比讀別人的摘要準。
- `max_results = 3`、每筆正文截到 1500 字元；`search_depth` 維持 `basic`（理由見
  [issue #11](https://github.com/rowing195/NutriLog/issues/11)）。
- 失敗一律回 `null` 不拋例外：它只是輔助，不該因為搜尋壞掉讓整條辨識失敗。

### GitHub Releases

```
GET https://api.github.com/repos/rowing195/NutriLog/releases/latest
```

- 檢查更新用。公開 repo、**不帶 token**：每小時 60 次，每天一次加上偶爾手動按遠遠用不完；帶 token 反而得把金鑰編進 APK。
- `releases/latest` 本身就略過 draft 與 prerelease。只取 `tag_name`、`html_url`、`published_at` 三欄。
- 一個正式版都沒有時回 404，那不是錯誤；403／429 是被限流，背景檢查不重試、等明天。

### Health Connect（裝置端，非 HTTP）

- 相依 `androidx.health.connect:connect-client`，**釘在 `1.1.0-beta01`**：1.1.0 正式版要求
  compileSdk 36 與 AGP ≥ 8.9.1，本專案是 35 / 8.7.3，升上去會在 AAR metadata 檢查失敗。
- 讀全日活動消耗、運動場次、步數與總消耗，要用哪一種看手錶配戴方式；來源會顯示在明細面板上。
  總消耗只用在單場運動的時間窗內（幾十分鐘，誤差小），不拿來推一整天的活動量。
- 寫入營養紀錄與飲水，使用 `Metadata.manualEntry(clientRecordId, ...)`：餐點以 `nutrilog_<紀錄 id>`、
  飲水以 `nutrilog_water_<日期>` 作為 clientRecordId，同一筆重寫即為覆寫。
- 權限每次回到前景重查一次 —— 使用者隨時可以在系統設定收回，app 不會收到通知。

---

## 發佈

推動 `v*` 格式之 Git Tag 將自動觸發 GitHub Actions 進行正式 APK 編譯與 Release 建立：

```bash
git tag -a v1.10.0 -m "Release v1.10.0: 中文換成 jf open 粉圓"
git push origin v1.10.0
```

版號由 Tag 動態注入，確保發佈檔名與內部版本號完全一致。

---

## 技術規格

| 項目 | 規格值 |
|---|---|
| Kotlin / AGP / Gradle | 2.0.21 / 8.7.3 / 8.11.1 |
| minSdk / targetSdk / compileSdk | 26 / 35 / 35 |
| JDK | 17 |
| UI 框架 | Jetpack Compose (BOM 2024.10.01) + 自訂「紙與墨」元件庫 |
| 本地儲存 | Room 2.6.1 + DataStore Preferences 1.1.1 |
| 網路通訊 | OkHttp 4.12.0 + kotlinx-serialization 1.7.3 |
| 條碼辨識 | Google Play services Code Scanner 16.1.0 |
| 雲端備份 | Google Play services Auth 22.0.0（`drive.file`）+ WorkManager 2.10.0 |
| 健康連線 | androidx.health.connect `connect-client` 1.1.0-beta01 |
| 測試框架 | JUnit 4 |
| 內嵌字型 | jf open 粉圓 2.1（中文）+ Neucha（數字，已正規化側邊留白） |
| 發佈 APK 大小 | 約 15.3 MB（v2.2.7，其中內嵌字型壓縮後約 3.0 MB） |
| 應用權限 | `INTERNET`；Health 連線的 `READ_ACTIVE_CALORIES_BURNED`、`READ_TOTAL_CALORIES_BURNED`、`READ_STEPS`、`READ_EXERCISE`、`WRITE_NUTRITION`、`WRITE_HYDRATION`（使用者同意才生效） |

---

## 貢獻

- **🐛 [回報問題](https://github.com/rowing195/NutriLog/issues)**：提交 Bug 或功能建議。
- **📓 看以前踩過的坑**：[已關閉的 issues](https://github.com/rowing195/NutriLog/issues?q=is%3Aissue+is%3Aclosed) **是當紀錄用的**，不是待辦清單。每一篇都是「症狀 → 成因 → 修法」，包括猜錯的方向 —— 改到相關的地方之前先翻一下，有些看起來很合理的「簡化」前人已經試過並且壞過一次。
- **💡 提交 Pull Request**：Fork 專案 → 建立分支 → 完成修改與驗證 → 提交 PR。

開發時請遵循 [`CLAUDE.md`](CLAUDE.md) 規範：註解撰寫繁體中文說明決策原因、遵守無 M3 預設元件原則、修改 Room Entity 需提供 Migration 與版本升級。

---

## 授權

NutriLog 採用 [MIT License](LICENSE) 授權。

---

## 致謝

- [Open Food Facts](https://world.openfoodfacts.org) —— 開放食品條碼資料庫。
- [Google Gemini API](https://ai.google.dev) —— 多模態影像與自然語言營養估算。
- [Google Code Scanner](https://developers.google.com/ml-kit/vision/barcode-scanning/code-scanner) —— 免相機權限之系統級條碼掃描模組。
- [Neucha](https://fonts.google.com/specimen/Neucha) —— 手寫風格數字字型（OFL，Jovanny Lemonad）。
- [jf open 粉圓](https://github.com/justfont/open-huninn-font) —— 台灣在地化圓體中文字型（OFL，justfont）。
- [@waltwait](https://github.com/waltwait) —— 健康連線、身型計算與 AI 週報／月報的初版實作，以及「肥貓」這款圖示。

<div align="left"><a href="#top">回到頂端</a></div>

---
