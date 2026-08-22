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
- ✅ **AI 的數字一律要你點頭** — 模型給的是估算值，一定先經過確認畫面才入庫。
- 📅 **看得出空白** — 月曆式歷史讓「哪幾天忘了記」一眼就有形狀，清單做不到這件事。
- 📤 **CSV 匯出** — 唯一能把資料帶出手機的路徑，所以定位是備份，預設全部匯出。
- 🔑 **權限只有一個** — Manifest 裡只有 `INTERNET`，連相機權限都不需要。

---

## 特色

| | 元件 | 細節 |
|---|---|---|
| ⚙️ | **架構** | <ul><li>單一 activity-scoped `NutriViewModel` 串起所有畫面狀態</li><li>`sealed interface Screen` + `when` 分派，刻意不引入導航函式庫</li><li>畫面本身無狀態，只吃資料與 lambda</li></ul> |
| 🔩 | **程式品質** | <ul><li>KDoc 寫繁體中文，解釋「為什麼」而不是「做了什麼」</li><li>版本統一收在 `gradle/libs.versions.toml`</li><li>Compose BOM 管理所有 compose 函式庫版號</li></ul> |
| 📄 | **文件** | <ul><li>README（本檔）＋ `CLAUDE.md`（環境與慣例）＋ `HANDOFF.md`（狀態）</li><li>踩過的坑寫在原地註解裡，不另開 wiki</li></ul> |
| 🔌 | **整合** | <ul><li>Google Gemini（照片／文字辨識）</li><li>Open Food Facts（條碼查詢）</li><li>Play 服務 Code Scanner（掃描 UI）</li><li>GitHub Actions 推 tag 自動發 Release</li></ul> |
| 🧩 | **模組化** | <ul><li>`data/db` Room、`data/net` 外部 API、`ui` 畫面、`ui/theme` 色票與字階</li><li>`CsvExport` 是純函式、不碰 Android API</li></ul> |
| 🧪 | **測試** | <ul><li>**沒有自動化測試套件**</li><li>`tools/ui.ps1` 提供依元件文字定位的手動 UI 驗證</li><li>回歸清單記在 `CLAUDE.md`：新增→編輯→刪除、換日、force-stop</li></ul> |
| ⚡️ | **效能** | <ul><li>每日／每月合計由 SQL `GROUP BY` 算，不把明細撈進記憶體</li><li>相片壓到長邊 1024 px 才送出</li><li>全 app 共用一個 `OkHttpClient`</li><li>條碼結果存本機快取</li></ul> |
| 🛡️ | **安全** | <ul><li>只有 `INTERNET` 權限</li><li>API key 存 DataStore，**不編進 APK**</li><li>key 走 `x-goog-api-key` header 而不是 query string</li><li>`keystore.properties` 與 `release.jks` 都在 gitignore</li></ul> |
| 📦 | **相依** | <ul><li>Room、DataStore、OkHttp、kotlinx-serialization、play-services-code-scanner</li><li>刻意不用 Retrofit —— 只有兩支端點</li></ul> |
| 🚀 | **擴充性** | <ul><li>Room 關聯式儲存，`date` 有索引</li><li>新增 `NutriSettings` 欄位一律給預設值，舊資料靠預設值相容</li></ul> |

---

## 專案結構

```sh
└── NutriLog/
    ├── .github/
    │   └── workflows/
    ├── app/
    │   ├── build.gradle.kts
    │   ├── proguard-rules.pro
    │   └── src/
    ├── design/
    │   ├── Budget.dc.html
    │   ├── Journal.dc.html
    │   ├── Main.dc.html
    │   ├── Refined.dc.html
    │   └── canvas.json
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
    ├── HANDOFF.md
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
					<td style='padding: 8px;'>版本目錄，所有相依與外掛的版號單一來源。KSP 的版號前半段必須和 Kotlin 完全一致，對不上會直接建置失敗。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/CLAUDE.md'>CLAUDE.md</a></b></td>
					<td style='padding: 8px;'>這台機器的環境設定與專案慣例：建置指令、模擬器規則、配色與版面語言、回歸清單。</td>
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
					<td style='padding: 8px;'>唯一的 Activity。開 edge-to-edge、套上主題，並建立 activity-scoped 的 ViewModel —— 各畫面之間的狀態（草稿、選到的日期）就靠它串起來。</td>
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
					<td style='padding: 8px;'>把飲食紀錄轉成 CSV，是唯一能把資料帶出手機的路徑。<br>- 純函式、不碰 Android API，格式對不對用眼睛看就知道。<br>- 檔頭有 UTF-8 BOM，少了它 Excel 會用系統 ANSI 解讀，中文全變亂碼。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/SettingsStore.kt'>SettingsStore.kt</a></b></td>
					<td style='padding: 8px;'>使用者設定與每日目標。用 DataStore 而不是 Room，因為它就只有一份、不需要查詢。</td>
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
					<td style='padding: 8px;'>一筆吃下去的東西，以及全天合計 `Totals`。日期存本地字串而不是 timestamp —— 跨時區時「今天」該是使用者當下的今天。延伸四項可為 null，`0.0` 會讓「沒資料」和「真的是 0」分不出來。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/db/NutriDao.kt'>NutriDao.kt</a></b></td>
					<td style='padding: 8px;'>所有查詢。合計走 SQL `GROUP BY`，不把明細撈進記憶體。常吃／最近以「名稱＋份量文字」分組，靠 SQLite 的 `MAX()` 保證裸欄位取自最後一次那筆。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/db/FoodSuggestion.kt'>FoodSuggestion.kt</a></b></td>
					<td style='padding: 8px;'>個人食物庫的一列 —— 從既有紀錄聚合出來的品項，不是資料表，所以不需要動 schema。這是唯一不用打字也不花 API 額度就能記一筆的來源。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/db/CachedProduct.kt'>CachedProduct.kt</a></b></td>
					<td style='padding: 8px;'>查過的條碼商品，每 100 g 的營養值。存在理由是 OFF 每分鐘 15 次的查詢上限，以及常吃的東西會一直重複掃到 —— 查過一次之後沒網路也帶得出來。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/db/NutriDatabase.kt'>NutriDatabase.kt</a></b></td>
					<td style='padding: 8px;'>Room 資料庫本體與單例。兩張表：`food_entries` 與 `cached_products`。</td>
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
					<td style='padding: 8px;'>照片與文字描述的營養估算。用 `responseSchema` 強制結構化輸出，否則模型會回夾著說明文字的 markdown。5xx 與逾時自動重試三次，4xx 不重試。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/net/OpenFoodFactsClient.kt'>OpenFoodFactsClient.kt</a></b></td>
					<td style='padding: 8px;'>條碼查詢。讀取不需要 key，但一定要帶自訂 User-Agent，這是 OFF 明文要求的；用預設 UA 會被擋掉。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/net/ImageCompressor.kt'>ImageCompressor.kt</a></b></td>
					<td style='padding: 8px;'>把相片壓成可以塞進請求的 base64 JPEG。原圖 12 MP base64 後是 4 MB 起跳，又慢又貴，而且對辨識準確度毫無幫助 —— 模型看的是盤子裡有什麼，不是毛孔。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/data/net/SharedHttp.kt'>SharedHttp.kt</a></b></td>
					<td style='padding: 8px;'>全 app 共用的 `OkHttpClient`。連線池與執行緒池都掛在實例上，每次呼叫 new 一個等於重新握手還會漏執行緒。</td>
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
					<td style='padding: 8px;'>唯一的 ViewModel：畫面分派、選到的日期、編輯草稿、辨識狀態、搜尋與食物庫。數字欄位在草稿裡存 String —— 打到一半的 `12.` 不是合法的 Double，解析留到儲存那一刻。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/ui/App.kt'>App.kt</a></b></td>
					<td style='padding: 8px;'>根 composable。把狀態分派到各畫面，並持有相機、相簿、SAF、條碼掃描這些跨 App 的啟動器。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/ui/TodayScreen.kt'>TodayScreen.kt</a></b></td>
					<td style='padding: 8px;'>主畫面：一週長條、已吃熱量、依餐別分段的額度條、三大營養素組成、常吃快捷，以及固定四餐的紀錄清單。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/ui/EditEntryScreen.kt'>EditEntryScreen.kt</a></b></td>
					<td style='padding: 8px;'>共用輸入表單，四條輸入路徑最後都匯流到這裡。核心四項排成 2×2，數字用自己畫的鍵盤 —— 系統鍵盤會蓋住儲存鈕。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/ui/ReviewScreen.kt'>ReviewScreen.kt</a></b></td>
					<td style='padding: 8px;'>模型辨識結果的確認畫面。這一步不能省：直接寫進資料庫等於在使用者的飲食紀錄裡塞模型自己編的數字。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/ui/SearchScreen.kt'>SearchScreen.kt</a></b></td>
					<td style='padding: 8px;'>搜尋與個人食物庫。沒輸入時是常吃／最近兩頁，有輸入就整個換成逐筆搜尋結果 —— 兩種列的性質不同，所以是切換而不是並排。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/ui/TextLookupScreen.kt'>TextLookupScreen.kt</a></b></td>
					<td style='padding: 8px;'>常吃清單與文字辨識合成一頁。忘了拍照事後補登時，第一反應通常是「這不是常吃的那個嗎」，找不到才用文字描述交給 AI。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/ui/HistoryScreen.kt'>HistoryScreen.kt</a></b></td>
					<td style='padding: 8px;'>月曆式歷史。用月曆而不是清單，是因為月曆看得出空白：哪幾天忘了記、連續幾天超標，一眼就有形狀。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/ui/BarcodeScreen.kt'>BarcodeScreen.kt</a></b></td>
					<td style='padding: 8px;'>條碼掃描與手動輸入。手動輸入不是備案而是必要功能 —— 掃描模組要從 Play 服務下載，不是每台裝置都成功。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/ui/SettingsScreen.kt'>SettingsScreen.kt</a></b></td>
					<td style='padding: 8px;'>Gemini API key、模型名稱、每日四項目標、進階營養素開關，以及 CSV 匯出。key 用密碼樣式顯示，截圖或旁人看到就等於外流。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/app/src/main/java/com/watson/nutrilog/ui/Common.kt'>Common.kt</a></b></td>
					<td style='padding: 8px;'>跨畫面共用的小元件與格式化：`Hairline()`、`SectionLabel()`、餐別選擇、數字顯示格式、日期標籤。</td>
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
					<td style='padding: 8px;'>「紙與墨」色票（明／暗）、襯線字階，以及三大營養素的語意色 `NutriPalette`。表面之間刻意幾乎沒有對比 —— 版面靠細線分隔而不是卡片色塊。</td>
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
					<td style='padding: 8px;'>開模擬器並等 `boot_completed`，以及 build＋安裝。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/tools/ui.ps1'>ui.ps1</a></b></td>
					<td style='padding: 8px;'>UI 驗證：列出畫面所有文字節點與座標，並依**元件文字**（而不是寫死座標）點擊。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/tools/setup-signing.sh'>setup-signing.sh</a></b></td>
					<td style='padding: 8px;'>一次性的簽章金鑰設定精靈：產金鑰 → 驗指紋 → 寫本機設定 → 設四個 GitHub secret。是 bash 不是 PowerShell，要用 Git Bash 跑。</td>
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
					<td style='padding: 8px;'>推 `v` 開頭的 tag 就建置並發佈 Release。缺簽章 secret 時**故意讓 workflow 失敗**，而不是靜默退回 debug 簽章。</td>
				</tr>
			</table>
		</blockquote>
	</details>
	<!-- design Submodule -->
	<details>
		<summary><b>design</b></summary>
		<blockquote>
			<div class='directory-path' style='padding: 8px 0; color: #666;'>
				<code><b>⦿ design</b></code>
			<table style='width: 100%; border-collapse: collapse;'>
			<thead>
				<tr style='background-color: #f8f9fa;'>
					<th style='width: 30%; text-align: left; padding: 8px;'>檔案</th>
					<th style='text-align: left; padding: 8px;'>說明</th>
				</tr>
			</thead>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/design/Main.dc.html'>Main.dc.html</a></b></td>
					<td style='padding: 8px;'>改版的互動原型：今日頁與編輯表單，含深色開關與每日目標拉桿。是實作前用來確認方向的稿子，不是 app 的一部分。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/NutriLog/blob/main/design/canvas.json'>canvas.json</a></b></td>
					<td style='padding: 8px;'>畫布版面：原型一頁、當初探索的三個方向（精修／額度優先／日記）另一頁。</td>
				</tr>
			</table>
		</blockquote>
	</details>
</details>

---

## 開始使用

### 需求

- **語言：** Kotlin 2.0.21
- **建置工具：** Gradle 8.11.1（或用 repo 內的 `gradlew`）
- **JDK：** 17
- **Android SDK：** compileSdk 35，最低支援 Android 8.0（minSdk 26）

只是想用 app 的話不需要以上任何一項 —— 直接到
[Releases](https://github.com/rowing195/NutriLog/releases) 下載 APK 裝上就好。

### 安裝

從原始碼建置：

1. **Clone：**

    ```sh
    ❯ git clone https://github.com/rowing195/NutriLog
    ```

2. **進到專案目錄：**

    ```sh
    ❯ cd NutriLog
    ```

3. **建置：**

    ```sh
    ❯ ./gradlew assembleDebug
    ```

APK 產生在 `app/build/outputs/apk/debug/app-debug.apk`。

沒有 `keystore.properties` 也建得起來 —— release 會自動退回 debug 簽章。

### 使用

裝到已連線的裝置或模擬器：

```sh
❯ ./gradlew installDebug
```

Windows 上可以用附的腳本一次開模擬器並部署：

```powershell
& ".\tools\emu.ps1" start    # 開模擬器並等 boot_completed
& ".\tools\emu.ps1" deploy   # build + 安裝
```

### 測試

**這個專案沒有自動化測試套件** —— `src/test` 與 `src/androidTest` 都不存在，
所以沒有可以跑的 `gradlew test`。

驗證靠模擬器上的手動回歸，用 [`tools/ui.ps1`](tools/ui.ps1) 依元件文字定位（不寫死座標）：

```powershell
& ".\tools\ui.ps1" dump              # 列出畫面所有文字節點與中心座標
& ".\tools\ui.ps1" tap "記一筆"
& ".\tools\ui.ps1" type "Chicken"    # input text 只吃 ASCII，測試資料一律用英數
```

動到閱讀以外的畫面後要跑的三項回歸：**手動新增→編輯→刪除**、
**換日**（前一天應為空、回今天資料還在）、**force-stop 後資料與設定都還在**。

---

## 功能

### 今日

- 上方是**一週長條**：換日和「這幾天吃得鬆或緊」用同一個元件解決。
  長條高度是當天熱量佔目標的比例，超標整條轉紅，空白的那幾天一眼看得出來。
  兩側的箭頭跨週，再遠就開月曆。
- 主數字是**已經吃多少**，目標與剩餘額度退到下面一行。
  （早期版本主打「還可以吃」，但實際用下來，打開 app 最先想確認的
  是「我今天吃了什麼程度」——剩餘是從那個數字推出來的第二個問題。）
- 熱量條**依餐別分段**。同樣是吃掉 1500 kcal，「午餐一次吃掉一大半」
  和「三餐平均」是完全不同的一天，看形狀就分得出來 —— 這是環做不到的。
- 三大營養素畫成**組成**（三者換算成熱量後的佔比），圖例才講目標達成率。
  兩個問題一個元件回答：今天吃的結構長怎樣、以及蛋白質夠不夠。
- **常吃快捷**：忘了拍照、事後才想補登時最短的一條路，一點就記進今天。
  這裡不繞確認畫面 —— 數字是使用者自己吃過、自己存過的，不是模型估的。
- **早／午／晚／點心四格一律都顯示**，空的也留著。
  只列有紀錄的餐別時，「今天還沒吃早餐」和「今天忘了記早餐」在畫面上
  長得一模一樣（兩者都是不存在）。固定四格之後空的那格本身就是提醒，
  點下去還能直接補登該餐、餐別已預選好。
- 點任一筆紀錄進去可以編輯或刪除。
- 開啟「顯示進階營養素」後，另外顯示糖、鈉、膳食纖維、飽和脂肪的合計。

### 四種輸入方式

| 方式 | 怎麼運作 |
|---|---|
| **輸入營養素** | 自己填數字。核心四項（熱量／蛋白質／脂肪／碳水）排成 2×2，進階四項收在展開區。 |
| **拍照辨識** | 拍照或從相簿選 → 壓縮成 1024 px → Gemini 估算 → **確認畫面**逐項勾選後才入庫。 |
| **常吃／文字輸入** | 上半是常吃清單（點了直接帶進表單），找不到才用一句「CoCo 珍珠奶茶 大杯半糖」交給 Gemini 估算 → 同一個確認畫面。 |
| **掃條碼** | 掃描或手動輸入條碼 → 先查本機快取，沒有才連 Open Food Facts → 填實際公克數自動換算。 |

所有路徑最後都匯流到同一張編輯表單或確認畫面，入庫前一定看得到、改得動，
**而且都能自己選要記進哪一餐**。AI 確認畫面原本是存檔當下才依時間猜餐別，
補登昨天的晚餐時會全部掉進點心 —— 現在猜一個當預設，但使用者可以改。

選單裡叫「輸入營養素」而不是「手動輸入」：後者和「常吃／文字輸入」讀起來太像，
但兩者差很多 —— 一個是自己填數字，另一個是打食物名稱讓 AI 估。

編輯表單的四個數字用**自己畫的鍵盤**，不叫系統鍵盤。系統鍵盤會蓋住儲存鈕，
而被蓋住的地方點下去不是沒反應，是把數字打進上一個聚焦的欄位，事故現場很難看出來。
表單下方還會把三大營養素換算回熱量跟你填的對照，差超過兩成就講一聲 ——
AI 估的數字最常在這裡出錯。不擋儲存，真實食物本來就有誤差，這只是提醒。

### 歷史（月曆）

一格一天的月曆，格子裡直接寫當天熱量，底色深淺代表吃了多少、超標轉紅。

用月曆而不是清單，是因為月曆**看得出空白**：哪幾天忘了記、連續幾天超標，
一眼就有形狀。清單只會讓有紀錄的日子擠在一起，反而看不出中間漏了幾天。
未來的日期會壓淡 —— 它們永遠是空的，不該看起來像「忘了記錄」。

下方顯示當月記錄天數、平均熱量與超標天數。合計由資料庫 `GROUP BY` 算，
不把明細撈進記憶體。

### 搜尋與個人食物庫

右上角放大鏡打開。同一份食物庫也出現在新增選單的「常吃／文字輸入」上半，
以及今日頁的常吃快捷 —— 三個入口，同一份從你自己的紀錄長出來的清單。

**沒輸入時**是食物庫，兩頁可左右滑動切換：

| | 常吃 | 最近 |
|---|---|---|
| 排序 | 90 天內的次數 | 最後一次吃的時間 |
| 期間 | 只算 90 天 | 不限 |

聚合鍵是**名稱 ＋ 份量文字**，不是只看名稱 —— 份量文字正是規格所在
（大杯／中杯／半糖），而且它是文字、不會像 AI 每次估的數字那樣抖動。
用數字當鍵的話，同一杯中杯珍奶會因為三次估算值略有出入而散成三列。

**有輸入時**整個換成逐筆搜尋結果，日期新到舊。比對名稱＋份量文字，
空白拆成多個關鍵字全部要命中 ——「珍奶 大杯」找得到，兩個字分別落在名稱與份量欄位。

### 匯出 CSV

把全部紀錄存成 CSV，用 Excel 或 Google 試算表打得開。
**這是唯一能把資料帶出手機的方式** —— 紀錄只在本機、沒有雲端備份，
換手機或誤刪 app 就沒了，所以匯出的定位是備份，預設全部匯出、不做日期篩選。

兩個細節值得知道：檔頭有 **UTF-8 BOM**（少了它 Excel 會用系統 ANSI 解讀，
中文全變亂碼）；缺資料的營養素是**空欄不是 0**（到了試算表更沒機會分辨
「沒標示」和「真的是 0」）。

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

### 配色：用線分隔，不用色塊

視覺基底是「紙與墨」：暖中性底色、襯線給數字與食物名稱。有兩件事會咬人：

- **表面之間幾乎沒有對比**（底 `#F8F7F2` vs 浮起 `#FDFCF9` 只差 3%）。
  這是故意的 —— 版面靠**細線**分隔，不是靠卡片色塊。不要為了「看得出是一張卡」
  去加深 `surfaceContainer`，那會把整個設計拉回舊樣子。要分隔就畫線。
- **深色不是純黑而是暖灰 `#191813`**。純黑會讓襯線字看起來發灰，細線也會整條消失。

Material3 的預設 baseline 是紫色系，**新增顏色角色時整族都要蓋**
（`surfaceContainer*`、`surfaceVariant`、`outline*`），少蓋一個就會有元件固執地維持預設紫。

刻意不用 Material You 動態取色：顏色在這個 app 裡有語意（三大營養素各有固定色、
超標轉紅），讓桌布決定色相會直接破壞那層意義。三大營養素的色是
`@Composable` getter 而不是常數 —— 深淺兩套的值不一樣（深色底上要提亮）。

字體用系統的 `FontFamily.Serif` 而不是打包字型檔：中文會落到 Noto Serif CJK，
而打包一套中文襯線要多好幾 MB，這支 app 的 APK 是直接分享給人裝的，不值得。

### 為什麼模型結果一定要經過確認畫面

Gemini 給的是**估算值**。直接寫進資料庫等於在使用者的飲食紀錄裡塞模型自己編的數字。
確認畫面會顯示每一項的把握度，可以逐項取消勾選；存進去之後仍然可以點進去逐欄修改。

常吃快捷是**唯一**不繞確認畫面的路徑，因為那組數字是使用者自己吃過、自己存過的。

### 為什麼手動輸入條碼是必要功能而不是備案

Google Code Scanner 要從 Play 服務**動態下載**掃描模組，不是每台裝置都成功
（模擬器尤其常失敗）。手動輸入走的是完全相同的查詢與入庫路徑，
所以掃描器叫不出來時功能不會斷掉，只是多打幾個數字。

### 為什麼沒有 Retrofit

只有兩個服務。OkHttp + 既有的 kotlinx-serialization 手寫約 200 行就夠，
比拉進一整套 Retrofit + converter 單純。

### 沒有導航函式庫

畫面只有八個，而且除了「今天」以外都是「開一個、按返回就關掉」。
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

```bash
cd "/c/code/android app/NutriLog" && ./tools/setup-signing.sh
```

**注意 `./` 是相對路徑，得先 `cd` 進專案。** 路徑有空格，引號不能省。
從 PowerShell 或 cmd 呼叫也能跑：

```powershell
& "C:\Program Files\Git\bin\bash.exe" "C:\code\android app\NutriLog\tools\setup-signing.sh"
```

但主控台的 codepage 通常不是 UTF-8，框線字元可能會歪掉
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

---

## 技術規格

| 項目 | 值 |
|---|---|
| Kotlin / AGP / Gradle | 2.0.21 / 8.7.3 / 8.11.1 |
| minSdk / targetSdk / compileSdk | 26 / 35 / 35 |
| JDK | 17 |
| UI | Compose（BOM 2024.10.01）+ Material 3 |
| 資料 | Room 2.6.1（紀錄）+ DataStore Preferences 1.1.1（設定） |
| 網路 | OkHttp 4.12.0 + kotlinx-serialization 1.7.3 |
| 條碼 | play-services-code-scanner 16.1.0 |
| 權限 | 只有 `INTERNET` |

---

## 貢獻

- **🐛 [回報問題](https://github.com/rowing195/NutriLog/issues)**：回報 bug 或提出功能建議。
- **💡 送 Pull Request**：fork → 開分支 → 改 → 送 PR。

改動前請先看 [`CLAUDE.md`](CLAUDE.md)：註解寫繁體中文並解釋「為什麼」、
PowerShell 腳本只能用 ASCII、動到 Room entity 就要加 migration 並升 version、
營養素缺資料一律 `null` 不要補 0。

---

## 授權

NutriLog 採用 [MIT License](LICENSE) 授權。

---

## 致謝

- [Open Food Facts](https://world.openfoodfacts.org) —— 群眾貢獻的開放食品資料庫，條碼查詢的資料來源。
- [Google Gemini API](https://ai.google.dev) —— 照片與文字的營養估算。
- [Google Code Scanner](https://developers.google.com/ml-kit/vision/barcode-scanning/code-scanner) —— 不需要相機權限的掃描 UI。

<div align="left"><a href="#top">回到頂端</a></div>

---
