<div align="center" id="top">

<!-- HEADER STYLE: CLASSIC -->
<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png" width="140" style="position: relative; top: 0; right: 0;" alt="Project Logo"/>

# NutriLog

<em>離線記錄每日營養，資料只留在你手機裡</em>

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
    - [歷史（月曆）](#歷史月曆)
    - [搜尋與個人食物庫](#搜尋與個人食物庫)
    - [匯出／匯入 CSV](#匯出匯入-csv)
    - [Google Drive 雲端備份](#google-drive-雲端備份)
- [設定 Gemini API key](#設定-gemini-api-key)
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

- 🔒 **完全離線** — 唯一的對外連線是影像辨識與條碼查詢兩支公開 API，兩者都是你主動觸發才會發生。
- 🍱 **四條輸入路徑** — 自己填數字、拍照或打一句話交給 Gemini 估、掃商品條碼查 Open Food Facts。
- 🔢 **五段雙速份數縮放** — 支援 `±1` 與 `±0.1` 步進，自動縮放公克/毫升/份量文字與所有營養素，具備基準持久化無損還原。
- 📰 **「紙與墨」出版物排版美學** — 內嵌 jf open 粉圓中文與 Neucha 手寫數字、自繪精準向量圖示、形狀即層級，無任何預設 Material 容器與色塊。
- ✅ **AI 的數字一律要你點頭** — 模型給的是估算值，一定先經過確認畫面才入庫。
- 📅 **看得出空白** — 月曆式歷史讓「哪幾天忘了記」一眼就有形狀，清單做不到這件事。
- 📤 **CSV 匯出** — 唯一能把資料帶出手機的路徑，定位是完整備份，預設全部匯出。
- 🔑 **權限只有一個** — Manifest 裡只有 `INTERNET`，相機在系統相機與 Play 服務中執行，連相機權限都不需要。

---

## 特色

| | 元件 | 細節 |
|---|---|---|
| ⚙️ | **架構** | <ul><li>單一 activity-scoped `NutriViewModel` 串起所有畫面狀態與導航</li><li>`sealed interface Screen` + `when` 分派，刻意不引入複雜導航函式庫</li><li>畫面本身無狀態，只吃資料與 lambda</li></ul> |
| 🔩 | **程式品質** | <ul><li>KDoc 寫繁體中文，解釋「為什麼」而不是「做了什麼」</li><li>版本統一收在 `gradle/libs.versions.toml`</li><li>Compose BOM 管理所有 compose 函式庫版號</li></ul> |
| 📄 | **文件** | <ul><li>README（本檔）＋ `CLAUDE.md`（環境與慣例）</li><li>踩過的坑與設計考量寫在原地註解裡，不另開 wiki</li></ul> |
| 🔌 | **整合** | <ul><li>Google Gemini（照片／文字結構化輸出辨識）</li><li>Google Drive（每日自動備份，僅 <code>drive.file</code> 範圍）</li><li>Open Food Facts（條碼營養資訊查詢）</li><li>Play 服務 Code Scanner（免相機權限掃描 UI）</li><li>GitHub Actions 推 tag 自動發佈 Release APK</li></ul> |
| 🧩 | **模組化** | <ul><li>`data/db` Room、`data/net` 外部 API、`ui` 畫面、`ui/theme` 色票與字階</li><li>`PortionMultiplier` 份數縮放與無損還原演算法</li><li>`CsvExport` / `CsvImport` 是純函式、不碰 Android API</li><li>`DriveClient` 手寫 REST，不引官方 Drive client 函式庫</li></ul> |
| 🧪 | **測試** | <ul><li>JUnit 單元測試套件（`NutrientScalingTest.kt`、`CsvRoundTripTest.kt`、`DriveBackupPruneTest.kt`）驗證份數縮放無損計算、CSV 匯出／匯入來回一致與雲端備份保留規則</li><li>`tools/ui.ps1` 提供依元件文字定位的手動 UI 自動化驗證</li><li>核心回歸清單：新增→編輯→刪除、換日滑動無跳躍、force-stop 狀態持久化</li></ul> |
| ⚡️ | **效能** | <ul><li>每日／每月合計由 SQL `GROUP BY` 算，不把明細撈進記憶體</li><li>相片長邊壓到 1024 px 才送出，節省流量與辨識延遲</li><li>全 app 共用一個 `OkHttpClient` 連線池</li><li>條碼結果存 Room 本機快取</li></ul> |
| 🛡️ | **安全** | <ul><li>只有 `INTERNET` 權限</li><li>Gemini API key 存 DataStore，**不編進 APK**</li><li>key 走 `x-goog-api-key` header 而非 query string</li><li>`keystore.properties` 與 `release.jks` 都在 gitignore</li></ul> |
| 📦 | **相依** | <ul><li>Room、DataStore、OkHttp、kotlinx-serialization、play-services-code-scanner</li><li>刻意不用 Retrofit —— 只有兩支端點，手寫維持最精簡依賴</li></ul> |
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
    │       │   │   │   ├── CsvExport.kt
    │       │   │   │   ├── CsvImport.kt
    │       │   │   │   ├── DriveAuth.kt
    │       │   │   │   ├── DriveBackup.kt
    │       │   │   │   ├── SettingsStore.kt
    │       │   │   │   ├── db/
    │       │   │   │   │   ├── CachedProduct.kt
    │       │   │   │   │   ├── FoodEntry.kt
    │       │   │   │   │   ├── FoodSuggestion.kt
    │       │   │   │   │   ├── NutriDao.kt
    │       │   │   │   │   └── NutriDatabase.kt
    │       │   │   │   └── net/
    │       │   │   │       ├── DriveClient.kt
    │       │   │   │       ├── GeminiClient.kt
    │       │   │   │       ├── ImageCompressor.kt
    │       │   │   │       ├── OpenFoodFactsClient.kt
    │       │   │   │       └── SharedHttp.kt
    │       │   │   ├── work/
    │       │   │   │   └── BackupWorker.kt
    │       │   │   └── ui/
    │       │   │       ├── App.kt
    │       │   │       ├── BarcodeScreen.kt
    │       │   │       ├── Common.kt
    │       │   │       ├── EditEntryScreen.kt
    │       │   │       ├── HistoryScreen.kt
    │       │   │       ├── NutriViewModel.kt
    │       │   │       ├── PortionMultiplier.kt
    │       │   │       ├── ReviewScreen.kt
    │       │   │       ├── SearchScreen.kt
    │       │   │       ├── SettingsScreen.kt
    │       │   │       ├── TextLookupScreen.kt
    │       │   │       ├── TodayScreen.kt
    │       │   │       └── theme/
    │       │   │           └── Theme.kt
    │       │   └── res/
    │       │       ├── font/
    │       │       │   ├── jf_open_huninn.ttf
│       │       │   └── neucha.ttf
    │       │       └── values/
    │       │           ├── strings.xml
    │       │           └── themes.xml
    │       └── test/
    │           └── java/com/watson/nutrilog/
    │               ├── CsvRoundTripTest.kt
    │               ├── DriveBackupPruneTest.kt
    │               └── NutrientScalingTest.kt
    ├── design/
    │   ├── canvas.json
    │   ├── Main.dc.html
    │   └── v2/
    ├── gradle/
    │   ├── libs.versions.toml
    │   └── wrapper/
    ├── tools/
    │   ├── emu.ps1
    │   ├── setup-signing.sh
    │   └── ui.ps1
    ├── build.gradle.kts
    ├── settings.gradle.kts
    ├── CLAUDE.md
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
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/SettingsStore.kt'>SettingsStore.kt</a></b></td>
					<td style='padding: 8px;'>使用者設定與每日目標。用 DataStore Preferences 儲存單份無關聯之輕量偏好設定。</td>
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
					<td style='padding: 8px;'>Room 資料庫單例與 Migrations。</td>
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
					<td style='padding: 8px;'>照片與文字描述的營養估算。以 `responseSchema` 強制結構化 JSON 輸出，自動重試 5xx 與網路逾時。</td>
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
					<td style='padding: 8px;'>將原始照片等比例縮放到長邊 1024 px 並壓為 base64 JPEG，大幅降低頻寬與延遲。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/net/SharedHttp.kt'>SharedHttp.kt</a></b></td>
					<td style='padding: 8px;'>全 app 共用之 `OkHttpClient` 單例，維持高效連線池與執行緒管理。</td>
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
					<td style='padding: 8px;'>五段純數字雙速步進列（`±1` 與 `±0.1` 圓章按鍵），中間顯示倍率與襯線數字，支援基線對齊與無損還原。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/ui/EditEntryScreen.kt'>EditEntryScreen.kt</a></b></td>
					<td style='padding: 8px;'>共用飲食編輯表單：2×2 核心營養素網格、自繪圓章數字鍵盤（避免擋住儲存鈕）、份數縮放步進列、折疊進階營養素與熱量交叉檢驗。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/ui/ReviewScreen.kt'>ReviewScreen.kt</a></b></td>
					<td style='padding: 8px;'>AI 辨識結果確認頁面：品項勾選、單品份數縮放、信心度指標與目標餐別預選。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/ui/SearchScreen.kt'>SearchScreen.kt</a></b></td>
					<td style='padding: 8px;'>搜尋與個人食物庫（90 天常吃／最近兩頁切換，即時多關鍵字全文搜尋，點擊直接進入編輯表單）。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/ui/TextLookupScreen.kt'>TextLookupScreen.kt</a></b></td>
					<td style='padding: 8px;'>常吃食物快捷與自然語言文字描述 AI 辨識合成頁面。</td>
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
					<td style='padding: 8px;'>外觀模式（系統/淺色/深色）、Gemini API Key 與模型攤開圈選（刻意不用下拉選單）、每日營養目標數字欄位、進階營養素開關與 CSV 備份匯出。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/ui/Common.kt'>Common.kt</a></b></td>
					<td style='padding: 8px;'>「紙與墨」設計系統元件：`Hairline`（1px）、`Rule`（2px）、`StampButton`、`PillButton`、`TextAction`、`RoundKey`、`BallotRow`、`SquareCheck`、`NutriTextField`、`dismissKeyboardOnTap`（點空白處收鍵盤）與全自繪向量 `*Mark` 圖示。</td>
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
					<td style='padding: 8px;'>單元測試：驗證份量文字縮放、DetectedFood 營養素等比計算、EntryDraft 基準導出與還原無損計算。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/test/java/com/watson/nutrilog/CsvRoundTripTest.kt'>CsvRoundTripTest.kt</a></b></td>
					<td style='padding: 8px;'>單元測試：CSV 匯出→匯入來回逐欄一致、逗號／引號／換行跳脫、缺資料維持 null、舊版欄位相容、去重鍵與壞資料列跳過。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/test/java/com/watson/nutrilog/DriveBackupPruneTest.kt'>DriveBackupPruneTest.kt</a></b></td>
					<td style='padding: 8px;'>單元測試：雲端備份的 30 天保留規則 —— 只刪自己產生的日期檔、跨月跨年排序正確、使用者自行放入的檔案一律不動。</td>
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
- **建置工具：** Gradle 8.11.1（或使用 repo 內之 `./gradlew`）
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

若本地無 `keystore.properties`，Gradle 將自動退回 debug 簽章以確保可順利編譯。

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

[`DriveBackupPruneTest.kt`](app/src/test/java/com/watson/nutrilog/DriveBackupPruneTest.kt)
- 雲端備份的 30 天保留規則：只刪自己產生的日期檔、跨月跨年排序正確、使用者自行放入的檔案一律不動

[`CsvRoundTripTest.kt`](app/src/test/java/com/watson/nutrilog/CsvRoundTripTest.kt)
- 匯出→匯入來回逐欄一致（含份數倍率與記錄時間）
- 食物名稱裡的逗號、引號與換行照 RFC 4180 跳脫與還原
- 缺資料維持 `null` 而不是變成 0
- 舊版（少「記錄時間」「份數倍率」兩欄）的匯出檔仍可匯入
- 去重鍵：同一筆重複匯入會撞在一起，但同名不同時間的兩筆不會

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
  - 下方進階營養素（糖／鈉／膳食纖維／飽和脂肪）支援橫向滑動檢視。
- **固定四餐區塊**：早餐、午餐、晚餐、點心四格永遠列出，未記錄時提供直接補登入口，並自動預選該餐別。
- **五合一懸浮章印選單**：右下角自繪墨印按鈕展開拍照、相簿、常吃/文字、條碼與手動五大入口。

### 份數縮放

- **五段純數字雙速步進列**：提供 `−1`、`−0.1`、`+0.1`、`+1` 四顆自繪圓章按鍵，中間展示當前倍率與襯線數字。
- **基準值持久化與無損還原**：
  - 資料庫記錄 `portionMultiplier`。
  - 編輯已放大紀錄時，系統以 `deriveBase` 精確逆推原始 1.0x 基準，避免多次縮放產生的浮點數捨入漂移。
- **全自動字串與數值同步**：
  - 同步調整份量文字（例如 `1 碗 (250g)` 縮放為 `1.5 碗 (375g)`、`700ml` 縮放為 `1050ml`）。
  - 熱量取整數、三大營養素保留一位小數、可空進階營養素正確保持 `null`。

### 四種輸入方式

| 方式 | 運作流程 |
|---|---|
| **輸入營養素** | 2×2 核心營養素網格，搭配自繪圓章數字鍵盤與份數步進列，完全避免系統鍵盤遮擋儲存鈕問題。 |
| **拍照辨識** | 拍照或自相簿選取 → 壓縮長邊至 1024 px → Gemini 結構化辨識 → **確認畫面**逐項勾選與微調後入庫。 |
| **常吃／文字輸入** | 上半部為個人食物庫快捷帶入；下半部支援自然語言描述（如「無糖綠茶 700ml」）由 Gemini 解析估算。 |
| **掃條碼** | 掃描條碼或手動輸入 → 優先讀取本機快取，無快取則查詢 Open Food Facts → 輸入食用公克數自動換算。 |

所有有輸入的畫面（上表三條打字路徑 ＋ 搜尋 ＋ 設定的每日目標）共通一件事：**點輸入框與鍵盤以外的空白處即可收鍵盤**，回到沒在打字的版面，已經打的字與數值都保留。編輯表單裡自繪的數字鍵盤同樣照這個方式收 —— 對使用者而言那與系統鍵盤是同一件事。

### 歷史（月曆）

- 一格一天的月曆視圖，格子內顯示當日熱量，並以背景深淺及超標朱紅色直觀呈現。
- 「看得出空白」設計：未記錄天數一眼即可辨識，避免清單模式造成的漏記遮蔽。
- 下方即時由 SQLite `GROUP BY` 計算當月總記錄天數、平均熱量與超標天數。

### 搜尋與個人食物庫

- 點擊右上角放大鏡開啟。
- **未輸入關鍵字時**：展示個人食物庫，支援左右滑動切換「90 天常吃」與「全部最近」。
- **輸入關鍵字時**：切換為即時全文搜尋模式，支援多關鍵字空白分割比對（名稱 + 份量文字）。
- 點擊任一項目直接帶入編輯表單，兼顧便捷與可編輯性。

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
- 「連結 Google Drive」會**順便把雲端的紀錄接回來**：換手機時自動比對雲端備份，走與本地匯入相同的確認面板（新增幾筆／略過幾筆重複），確認後才寫入資料庫。
- 此功能為選配。未連結時 app 不會存取網路，也不會排入任何背景工作。
- 首次使用需自行於 Google Cloud 建立 OAuth client，可執行 [`tools/setup-google-drive.sh`](tools/setup-google-drive.sh) 精靈完成設定。

---

## 設定 Gemini API key

拍照與自然語言文字辨識需配置使用者自備 API Key：

1. 前往 [Google AI Studio](https://aistudio.google.com) 免費申請 API key。
2. 開啟 App → 右上角 **設定** → 貼入 **Gemini API key**。

Key 僅安全儲存於本地 DataStore，**不會打包進 APK 或上傳第三方伺服器**。

可於設定中切換支援模型（預設推薦 `gemini-3.7-flash`，亦可選用 `gemini-3.5-flash-lite`）。

---

## 設計決策

### 為什麼紀錄用 Room，設定用 DataStore

飲食紀錄具備日增長、關聯查詢（依日期範圍、餐別合計、分組統計）特性，採用具備索引的 Room SQLite 關聯式資料庫是最可靠做法。設定資料量極小且單一，採用 DataStore Preferences 即可滿足需求。

### 為什麼完全不需要相機權限

- **拍照**：使用 `ActivityResultContracts.TakePicture()` 委託系統相機 App 處理。
- **掃碼**：使用 Google Play 服務之 Google Code Scanner，掃描視窗獨立於 Google Play 服務行程執行。
- **相簿**：使用系統 `PickVisualMedia` 照片選擇器。

本 App 本身無需宣告 `CAMERA` 或儲存權限，僅需 `INTERNET` 權限進行外部查詢。

### 「紙與墨」出版物風格與內嵌字型

- **色票**：淺色米紙底色 `#F7F3E9`、深色暖黑 `#17150F`、朱紅焦點 `#D8462A`、琥珀警示 `#B8791F`。
- **規線取代色塊**：版面層次完全依靠 2px 墨線（`Rule`）與 1px 細線（`Hairline`）劃分，堅決不用 Material 浮凸色塊卡片。
- **字型**：純數字、日期、單位與按鍵採用內嵌 **Neucha**（`res/font/neucha.ttf`）手寫體，並已正規化數字與標點的側邊留白（原版 `1/2/3/4/5/7` 側邊留白為 0，導致 `11`、`0.2` 等組合會黏在一起） —— 每天隨手記一筆的東西，數字長得像手寫的比像印刷品更貼近它在做的事；中文採用內嵌 **jf open 粉圓**（`res/font/jf_open_huninn.ttf`）—— 圓體的柔和調性搭配手寫數字，而粗細均勻、小字級撐得住；標題輔以拉開字距（`letterSpacing`）建立清晰層級。

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
- 內建 5xx / 逾時自動指數退避重試 3 次。

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
| 測試框架 | JUnit 4 + Kotlin Test |
| 內嵌字型 | jf open 粉圓 2.1（中文）+ Neucha（數字，已正規化側邊留白） |
| 發佈 APK 大小 | 約 11.4 MB（其中內嵌字型約 2.9 MB） |
| 應用權限 | `android.permission.INTERNET` |

---

## 貢獻

- **🐛 [回報問題](https://github.com/rowing195/NutriLog/issues)**：提交 Bug 或功能建議。
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

<div align="left"><a href="#top">回到頂端</a></div>

---
