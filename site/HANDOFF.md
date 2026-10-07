# 展示網站交接（本機 session ⇄ 雲端 session）

> 寫於 2026-10-06，2026-10-07 雲端 session 更新、同日本機 session 補完截圖。網站做完、合併前**刪掉這個檔**，它不是給使用者看的文件。

**現況（先讀這段）**：最新的網站在 **`claude/site-showcase-main-js-0b8uh8`** 分支，不是 `site-showcase`
（那條停在 main.js 還沒寫的時候）。網站已經寫完、在雲端用 Playwright 驗過；**截圖 19 張都拍好了**
（2026-10-07 本機補拍，使用者看過，見〈補拍的截圖〉）。剩下〈部署〉與〈收尾〉。
下面〈`main.js` 第一版規格〉是第一版的紀錄，現在的行為以程式和〈雲端 session 的改版〉為準。

## 使用者要的

為 NutriLog（app 顯示名稱「肥胖日記」）做一個**敘事風格的展示網站**：
GSAP ScrollTrigger ＋ Lenis 平滑捲動，放 Vercel、接 GitHub 自動部署。

### 已經由使用者拍板的決策（不要重新討論）

| 項目 | 決定 |
|---|---|
| 放哪裡 | 這個 repo 的 `site/`，Vercel 的 Root Directory 設 `site` |
| 技術 | Vite ＋ 原生 JS ＋ `gsap` ＋ `lenis`（npm） |
| 畫面素材 | 模擬器實拍截圖（在 `site/public/shots/`，19 張，見〈補拍的截圖〉） |
| 視覺 | 沿用 app 的「紙與墨」（見根目錄 `CLAUDE.md` 的〈配色與版面語言〉） |
| 語言 | 只有繁體中文 |
| 敘事 | 「一天」時間軸（下面〈分鏡〉） |
| app 圖示 | **網站上只放「肥貓」**。牢大（Kobe 本人＋康師傅商標）、冰紅茶（商品照）、菲比啾比／快樂牛馬（遊戲角色同人圖）不放；糯糯也不放。favicon 用的也是肥貓 |

### 使用者的工作習慣（他在本機 session 裡交代過的）

- 回覆一律繁體中文（台灣用語）。
- **UI 文字與設計方向的改動，先把要改的東西印出來給他看，他確定了才改。**
- 需要他決策的事用選項讓他選（AskUserQuestion），推薦的放第一個。
- 沒有要求就不要 commit／push；在 `main` 上要先開分支。

## 目前進度

| 檔案 | 狀態 |
|---|---|
| `site/package.json` | ✅ gsap ^3.15.0、lenis ^1.3.26、vite ^8.3.3（Vite 8 要 Node ≥ 20.19） |
| `site/vercel.json` | ✅ `framework: vite`，`ignoreCommand` 讓只改 Android 的 commit 不觸發部署 |
| `site/vite.config.js` | ✅ 把 `%SITE_URL%` 換成 `VERCEL_PROJECT_PRODUCTION_URL`（og:image 要完整網址） |
| `site/.gitignore` | ✅ |
| `site/index.html` | ✅ 完整結構與文案（新配文使用者確認過） |
| `site/src/style.css` | ✅ 寫完，瀏覽器驗證過 |
| `site/src/main.js` | ✅ 已寫，改版內容見〈雲端 session 的改版〉 |
| `site/public/` | ✅ 19 張截圖、`fonts/neucha.woff2`、`icon-cat.webp`、`favicon.png`、`apple-touch-icon.png`、`og.png` |
| `npm install` / build / 瀏覽器驗證 | ✅ 雲端用 Playwright 驗過桌面、手機、減少動態；補完截圖後本機再驗過桌面與手機寬度 |
| Vercel 連線 | ❌ 要使用者自己在 Vercel 後台做（見〈部署〉） |
| `CLAUDE.md` 加一節講 `site/` | ❌ |

## 分鏡（使用者核可過的版本）

右邊手機釘住、左邊章節捲過去；報頭的時鐘跟著捲動走、今日熱量逐章累加；
入夜時整頁從米紙轉暖黑，天亮再轉回來。

| # | 時間 | 章節 | 標題／內文 | 手機畫面 | 網頁小動畫 |
|---|---|---|---|---|---|
| 00 | 07:00 | 封面 | 肥胖日記／每天隨手記一筆。紀錄只留在你的手機裡。［下載］Android 8.0 以上・免費・開源 | today-morning | 標題逐字升起、墨線畫出 |
| 01 | 07:40 | 早餐 — 常吃 | 打兩個字，昨天那份就回來了。／吃過的東西自己變成食物庫。「烤肉」找得到「煎烤豬肉排」，「咖啡」不會撈到咖哩飯。 | library → library-query | 「烤」「肉」底線逐字畫出；咖啡那列只中「咖」→ 變淡、標「不算」 |
| 02 | 12:30 | 午餐 — 拍照 | 拍一張，AI 估，你點頭才算數。／模型給的是估算值，一定先經過確認畫面才入庫。 | review-lunch | — |
| 03 | 15:10 | 下午 — AI 估／AI 查 | 要不要上網查，是你按的。／麥當勞 大麥克：AI 估 540 ／ AI 查 503（台灣官方標示） | ai-stamps → review-bigmac | 兩顆章，AI 查那列的數字從 540 滾到 503 |
| 04 | 19:20 | 晚餐 — 份數 | 吃了一碗半，就按一碗半。／1 碗 (250 g) → 1.5 碗 (375 g)，熱量與營養素一起換算。 | portion | 份數步進 1 → 1.5（每 0.1 一格），份量文字與熱量跟著換 |
| 05 | 21:00 | 跑步 — 健康連線 | 動得多，就能多吃一點。／運動消耗加進今天的目標，不從吃掉的扣。手錶估得偏高，預設只回補一半。 | today-night | 熱量條：超出目標的紅段 → 目標線往右推 +300、紅段退回墨色 |
| 06 | 23:30 | 月曆 | 一天，收進一格。／哪幾天忘了記，一眼就看得出形狀。改了目標，過去的日子還是照當時的標準。 | calendar-today → calendar-month | — |
| 07 | 23:58 | 沒有帳號，沒有後端 | 紀錄全部在你手機裡。對外連線只有你按下去的那幾種。CSV 匯出／匯入，資料不會被鎖在 app 裡。 | （無手機） | 大標逐行升起、清單細線畫出 |
| 08 | 06:30 | 紙與墨（隔天） | 粉圓＋手寫數字、兩級規線、朱紅只給「看這裡」、形狀就是層級；肥貓圖示 | （無手機） | — |
| 09 | — | 下載 | ［下載 vX］GitHub・MIT；AI 功能要自己申請 Gemini key（免費） | — | — |

**分鏡沒寫到、是本機 session 自己補的文案**（`index.html` 裡已經寫上去了，完成後要請使用者過目）：
07 的五列對外連線說明、08 四個字樣各自的說明句與「桌面圖示也能換」、09 的三條備註、
各章 demo 的小標（「憑模型的印象」「先找該店公布的營養標示」「手錶量到 600，回補一半」）、頁尾三句。

## 示範資料：文案裡的數字都從這裡來

截圖是在本機模擬器上用 sqlite 灌示範資料拍的（使用者同意清掉模擬器上原本的資料）。
**雲端沒有模擬器，截圖要重拍只能回本機。**

今天＝2026-10-06（週二），每日熱量目標 1944：

| 時間 | 餐 | 品項 | kcal | 累計 |
|---|---|---|---|---|
| 07:40 | 早 | 無糖豆漿 150、茶葉蛋 75 | 225 | **225** |
| 12:30 | 午 | 雞腿便當 850、無糖綠茶 0（蘋果 104 在確認畫面**取消勾選**，沒入庫） | 850 | **1075** |
| 15:10 | 點心 | 麥當勞大麥克（真的按了「AI 查」：503，蛋白 26、脂肪 25、碳水 43） | 503 | **1578** |
| 19:20 | 晚 | 白飯 1.5 碗 (375 g) 525、燙青菜 80 | 605 | **2183** |
| 21:00 | — | 跑步：量到 600，回補 50% → **目標 +300 → 2244，還有 61** | | |

- 19:20 那一刻 2183 > 1944，報頭的今日熱量要轉**朱紅**（超標）；21:00 加了 +300 之後轉回墨色 —— 這是第 05 章的重點，網頁本身就在示範「動得多就能多吃」。
- 「AI 估 540」沒有在這次重測，是 README〈查網路〉那節記的實測值（美國規格）。
- 九月的月曆：記錄 25 天、平均 1761、超標 3 天（12、17、25 號），沒記的是 6、13、19、20、27 號。
- 過了 06:30（第 08 章，隔天）今日熱量歸零、運動額度歸零。

### 截圖對照（`site/public/shots/`，720×1481 WebP）

原圖 1080×2400，切掉上方 118px 狀態列與下方 60px 手勢條後縮到 720 寬。
**系統狀態列是網頁自己畫的**（手機框裡的 `.statusbar`，時間跟報頭時鐘同步）。

| 檔案 | 內容 | 深淺 |
|---|---|---|
| today-morning | 今日頁，四餐都「還沒記」 | 淺 |
| library | 常吃頁，未輸入 | 淺 |
| library-query | 常吃頁，輸入「烤肉」→ 只剩煎烤豬肉排 | 淺 |
| review-lunch | 確認辨識結果：雞腿便當✓、無糖綠茶✓、蘋果☐，午餐 | 淺 |
| ai-stamps | 常吃頁，輸入「麥當勞 大麥克」、找不到，底下 AI 估／AI 查兩顆章 | 淺 |
| review-bigmac | 確認辨識結果：麥當勞大麥克 503，點心 | 淺 |
| portion | 編輯表單：白飯 1.5 份、1.5 碗 (375 g)、525 kcal、晚餐 | 淺 |
| today-night | 今日頁 2183、目標 1944、運動 +300、還有 61 | 深 |
| calendar-today | 十月月曆，6 號那格 2183 | 深 |
| calendar-month | 九月月曆（底下有「回到本月」章） | 深 |

## `main.js` 第一版規格（已實作，之後被改版取代，留著當紀錄）

第一版照這份寫的。第 3、4、5、6、8 點後來都被〈雲端 session 的改版〉改掉了，現在以程式為準。

**原則**：捲動狀態（時鐘、熱量、入夜程度）**用一個函式從捲動位置直接算**，不要每章各掛一個
ScrollTrigger 去改同一個值 —— 那樣倒捲回去時會互相蓋掉。換畫面與小動畫才用 scrub 的 ScrollTrigger。

1. **Lenis**：`new Lenis({ anchors: true })`；`lenis.on('scroll', ScrollTrigger.update)`、
   `gsap.ticker.add(t => lenis.raf(t * 1000))`、`gsap.ticker.lagSmoothing(0)`。
   `prefers-reduced-motion: reduce` 時不開 Lenis。另外 `import 'lenis/dist/lenis.css'`。
2. **捲動狀態**（`ScrollTrigger.create({ start: 0, end: 'max', onUpdate, onRefresh })` 裡呼叫）：
   - 錨點＝所有 `[data-time]`（hero、六章、privacy、paper），refresh 時量好每個的頁面 y。
   - 判斷位置用 `scrollY + innerHeight / 2`。
   - **時鐘**：在相鄰兩錨點之間線性內插分鐘數；後一個比前一個小就 +1440（23:58 → 隔天 06:30 跨過午夜）。
     寫進所有 `[data-clock]`（報頭與手機狀態列），格式 `HH:MM`。
   - **熱量**：取最後一個「已經過」且有 `data-kcal` 的錨點；沒寫 `data-kcal` 的沿用前一個。
     目標值變了才用 `gsap.to` 補間顯示（約 0.9s），寫進 `[data-kcal]`，整數、不加千分位（app 也不加）。
   - **運動額度**：同上取 `data-bonus`；> 0 時 `.target` 加 `has-bonus`、`[data-bonus]` 寫 `+300`。
   - **超標**：顯示值 > 1944 + bonus 時 `.today` 加 `over`（CSS 已經會轉朱紅）。
   - **入夜程度 n**：`[data-dusk]`（第 05 章）頂端從視窗 85% 走到 25% 時 n 0→1；
     `[data-dawn]`（第 08 章）頂端從 90% 走到 35% 時 n 1→0。
     用 `gsap.utils.interpolate(淺色, 深色)` 對每個色票內插，寫回 `document.documentElement.style`
     的 `--bg` `--raised` `--container` `--track` `--ink` `--ink2` `--muted` `--faint` `--hairline`
     `--field` `--vermilion` `--ochre`。深色那套是 `Theme.kt` 的 `Paper.Dark*`：
     `#17150F #1E1B14 #232016 #2C2820 #EFE9DC #BDB5A2 #A8A08C #7C7565 #2F2B21 #4A4636 #F2705A #D9A24E`。
     順便更新 `<meta name="theme-color">`。
3. **手機換畫面**（只在 `gsap.matchMedia('(min-width: 900px)')` 底下）：每個 `.reveal-mark[data-reveal]`
   對應 `.screen img[data-shot]`；`data-from="top"` 從 `inset(0% 0% 100% 0%)`、`bottom` 從
   `inset(100% 0% 0% 0%)` 收到 `inset(0% 0% 0% 0%)`，`fade` 是 opacity 0→1。
   `scrollTrigger: { trigger: mark, start: 'top 80%', end: 'top 45%', scrub: true }`。
   方向照 app 的規則：「記一筆」開出來的由上往下蓋，報頭圖示（月曆）開出來的由下往上。
4. **demo**（全部 scrub，trigger 用 demo 本身，大約 `top 80%` → `top 35%`）：
   - `match`：`hit` 列的兩個 `<b>` 依序把 CSS 變數 `--u` 0→1（底線），接著 `.verdict` 淡入；
     `miss` 列的 `<b>` 畫完後整列加 `is-dim`、`.verdict` 淡入。
   - `stamps`：`[data-row="search"]` 那列淡入，`[data-count]` 從 540 滾到 503（取整數）。
   - `portion`：進度 p → 倍率 `1 + round(p*5)/10`；`[data-mult]` 顯示倍率（1、1.1…1.5），
     `[data-serving]` 為 `${倍率} 碗 (${250*倍率} g)`，`[data-portion-kcal]` 為 `round(350*倍率)`；
     倍率每跳一格就讓 `[data-plus]` 加 `press` 約 150ms。
   - `budget`：`.bar` 的 `--target` 81 → 93.5、`--red` 1 → 0（目標線往右推、紅段消失）。
     比例尺是 2400 kcal：1944→81%、2183→91%、2244→93.5%。
5. **封面進場**（載入時跑一次，不綁捲動）：`.title .ch` 由 `yPercent: 110` 逐字升起、
   `.hero-rule` `scaleX` 0→1、`.lede` `.cta` 淡入上移、`.day-phone .phone` 由下浮上。
6. **一般進場**：各章的 `.when` `h2` `p` `.demo` 進畫面時淡入上移；`.privacy .big .line > span`
   從 `yPercent: 100` 升起；`.ledger .hair` `scaleX` 0→1 逐列；`.specimen` 淡入；
   手機版的 `.inline-shot` 進畫面時淡入上移。
7. **下載鈕**：`fetch('https://api.github.com/repos/rowing195/NutriLog/releases/latest')`，
   找 `.apk` 結尾的 asset → 所有 `[data-apk]` 的 href 換成直接下載網址、`[data-apk-label]` 寫
   `下載 ${tag_name}`、`[data-apk-meta]` 寫檔案大小（MB），`[data-release-notes]` 的 href 換成 `html_url`。
   失敗就什麼都不做 —— HTML 裡預設的連結已經指向 releases/latest 頁。
   **不要把版號寫死在 HTML**：網站只在 `site/` 有改動時才重新部署，寫死會過期。
8. **reduced motion**：不開 Lenis、不做位移；換畫面改成到點直接切換。時鐘、熱量、入夜配色照樣跑（那是內容，不是動態）。

## 雲端 session 的改版（2026-10-06，使用者選過的）

使用者看過第一版之後要求：手機不能在字出現的時候移動、大標不能被切掉、多放幾個畫面與配文、
動畫要像 `rowing195/html-games` 的 `fhibichubi-nono` 那樣（全部 scrub、往回捲會倒帶）。
他用選項選定了下面這些，**上面〈`main.js` 第一版規格〉第 3、4、5、6、8 點已經被這一節取代**
（第 5 點：封面那支手機只淡入、不再由下浮上；第 8 點：減少動態時換畫面與字改成淡入，不是到點直接切換）：

- **桌面版整天是一個釘住的舞台**：左欄與手機都釘住，捲動只推進 `main.js` 的 `stageDay` 那條時間軸。
  `data-screen` 是那一幕／那一行出現時手機要換的畫面。節奏常數在 `SCENE`，`.day` 的高度依時間軸總長算。
  最後手機在原地淡出才解除釘住。
- **字是逐字彈進來的**（`sceneTimeline`，桌面與手機共用）：每一幕的字照閱讀順序一個一個彈起來、帶一點
  旋轉與縮放，停著讓人讀，再照閱讀順序縮小飄走。小示範整塊進出不拆字。這是在示範頁
  （claude.ai 上的「逐字掃法比較」）和使用者比過「照閱讀順序 vs 照水平位置」「釘住／固定速度／大動作」
  之後選的「釘住＋大動作」。原本整行飄進來的版本在手機上一滑就過，使用者說看不出有進出場。
- **手機版每一幕的字也釘住**：`.scene-stage` 給捲動距離、`.scene-copy` 釘在畫面中間，截圖接在後面捲上來。
- **拆字不要用 GSAP 的 SplitText**：它的 `specialChars` 把字黏回去時只比長度不比內容，實測把「煎烤豬肉排」
  改成「烤烤豬肉排」、「咖啡」改成「烤啡」。`splitUnits` 只包 span，標點黏前一個字、數字英文整段一起；
  原文另外放一份 `.sr-only` 給讀螢幕軟體（`<p>` 不能用 aria-label 命名）。
- 新增：報頭底下的進度線、「沒有帳號，沒有後端。」逐字點亮、封面往上淡出、隱私那段之後的橫向藝廊。
- 減少動態：照參考站的作法，淡入淡出保留、位移歸零、換畫面改淡入、藝廊改成一般排版。
- 大標字級上限 156 → 136px（寬螢幕時會切掉「記」）。
- **停手吸附**（2026-10-07 本機 session 加的，使用者選的）：全部 scrub 的話停在哪裡看各人捲動習慣，字常停在飛到一半。
  現在停手 `SETTLE.DELAY`（300ms、手指也離開）之後，停在兩站之間就往剛剛捲的方向、以 `SETTLE.RATE`（1.5 倍時間軸秒數）
  捲到下一站（`settleOnRest`／`restTarget`）。站＝每一幕 `sceneTimeline` 回傳的 `rest`（字與小示範到齊、還沒飄走），
  手機版另外加「後面那張截圖整張露出來」一站（不然截圖一閃而過）；桌面版最後接到 `.day` 底下那段的開頭。
  站的位置由 `stageDay`／`stageMobile` 填進 `layout.rests`。一碰滾輪或手指一動就交還控制（Lenis 本身的行為）；
  減少動態時不吸附（只掛在有 Lenis 的那一支）。比較過「一次手勢翻一幕」與「改成時間驅動」，前者綁架捲動、後者推翻倒帶，沒選。

**新配文使用者確認過了（2026-10-07），照現在的用字**：四個章節後的 `.panel`
（左滑刪除、復原、運動消耗明細、飲水明細、AI 週報／月報）與藝廊的四段說明（設定、掃條碼、換一家再試、CSV 匯入）。

### 補拍的截圖（2026-10-07 本機拍完，使用者看過）

下面這 10 張已經拍好放進 `site/public/shots/`，使用者看過才 commit。這一節留著給**要重拍的時候**用。
**雲端拍不了**：容器沒有 `/dev/kvm`（模擬器跑不起來），`dl.google.com` 也被網路政策擋住
（連 SDK 都裝不了）。所以要在本機用 `tools/emu.ps1` 開模擬器、`tools/ui.ps1` 操作來拍。

網頁上缺的圖會顯示「待拍截圖＋檔名」的虛線框，**檔名一字不差**放進 `site/public/shots/` 就自動換掉，程式不用改。
裁切同上（1080×2400 切掉上 118px、下 60px，縮到 720 寬 WebP），示範資料同〈示範資料〉那一節。

**這次的作法（使用者選的或看過的）**

- **用目前的程式拍，不是模擬器上原本 9/25 那顆舊版**（使用者選的）。示範資料在 `com.watson.nutrilog`（不是 `.debug`），
  所以是暫時註解掉 `app/build.gradle.kts` 的 `applicationIdSuffix`／`versionNameSuffix`、把 `app/src/debug/res/values/strings.xml`
  的「肥胖日記 測試版」改成「肥胖日記」，`assembleDebug -PappVersionName=2.2.8` 後 `adb install -r -d` 蓋上去（同 debug 簽章，資料保留），
  建完立刻 `git checkout` 那兩個檔、再正常 `assembleDebug` 一次，讓 `app-debug.apk` 回到 `.debug` 那顆。
  先前那 9 張是舊版拍的，那幾頁外觀沒變。
- swipe／undo：今日頁往下捲到營養素那一區貼著週長條，四餐都在畫面裡；兩張同一個捲動位置，淡入切換時上半部對得齊。
  刪掉一列後頁面變短，**捲到底的話捲動位置會被夾回、上緣切到半截**，所以不要捲到底。
- library-query：主機 `Set-Clipboard "烤肉"`（模擬器會同步主機剪貼簿），長按輸入框貼上，等提示消失、點空白處失焦再拍
  （聚焦時底下那段說明會收起來，和原圖不一樣）。
- csv-import：九月的紀錄（146 筆，重複）＋同一批往前推一個月當八月（146 筆，新的），面板同時有「會新增」與「會略過」。
- barcode：台灣比菲多「質立希臘式優格（無加糖）」`4710784965544`（名稱是中文、每 100 g）。很多台灣商品在 OFF 的
  `product_name` 是機器翻的英文（光泉鮮乳是「Frankincense Family High Quality Pure Milk」），挑之前先查 API。
- provider-switch：走「從相簿選」→ 斷網 → 送出辨識，讓它是**拍照**失敗：面板的說明句才是「拍照要用看得懂圖片的模型…」，
  對得上配文的「同一張照片直接重試」。失敗畫面不顯示照片，用哪張圖都可以。
- report：月報內文是照 `MonthlyAggregator` 的 prompt 規則手寫的，使用者看過。**統計那塊要帶運動**：拍的時候暫時把
  DataStore 的 `readExerciseCalories` 改成 false（UI 改不了：沒授權時開關本來就顯示關），報表才會讀快取裡九月那四次跑步
  （運動消耗 每天 46／每日消耗 1,666／熱量收支 +2,139）；拍完把設定檔與資料庫換回備份。

**會咬人的事**

- **打開 AI 報告頁會把 `daily_health_metrics` 寫成 0。** 沒授權讀取時 `readDailyActivity` 回 0（不是 null），
  `MonthlyAggregator`／`WeeklyAggregator` 照寫進快取，整個月（加上比較的上個月）都被蓋掉。這是 app 的 bug，另開任務修。
  修好之前，**開報表頁之前先備份資料庫**。
- **用 `adb shell run-as … 'cat > 檔' < 檔` 寫二進位會被截斷**（77 KB 的資料庫只寫進 21 KB）。資料庫、DataStore 一律
  `adb push` 到 `/data/local/tmp`，`adb root` 後 `cp`、`chown u0_a211:u0_a211`、`restorecon`。讀出來用 `exec-out` 沒問題。

**開拍前**

1. `git checkout claude/site-showcase-main-js-0b8uh8`（見最上面〈現況〉）。
2. **模擬器的日期要撥回 2026-10-06。** 示範資料的「今天」是 10/6，現在已經過了；不撥回去的話今日頁是空的一天，
   運動明細標題也不會是「10 月 6 日（今天）」。先關掉自動時間（`adb shell settings put global auto_time 0`），
   再到系統設定手動改（`adb shell am start -a android.settings.DATE_SETTINGS`）；非 Play 映像也可以
   `adb root` 後 `adb shell date 100619302026.00`。**拍完要改回自動時間。**
3. 確認示範資料還在：今日頁 2183 kcal、目標 1944、運動 +300、還有 61（同 today-night 那張）。
   模擬器被清過的話，照〈示範資料〉重灌；運動 +300 是 `daily_health_metrics` 那一列（根目錄 CLAUDE.md
   〈健康連線〉：模擬器上不要真的授權，用 `run-as` 塞假資料）。

**每一張**

| 檔名 | 深淺 | 畫面 | 怎麼到那個畫面／注意 |
|---|---|---|---|
| library-query | 淺 | **重拍**，同原本：常吃頁打「烤肉」只剩煎烤豬肉排 | 原圖底部有系統提示「肥胖日記 pasted from your clipboard」蓋住兩顆章。拍之前先等提示消失，或不要經過剪貼簿輸入 |
| swipe | 淺 | 19:20 之後的今日頁，「無糖綠茶」那一列往左滑開，露出紅色刪除塊 | 選 0 kcal 的綠茶，熱量才會維持 2183。`ui.ps1` 沒有 swipe，用 `adb shell input swipe x1 y x2 y 300` |
| undo | 淺 | 同一頁按下刪除之後，左下角的復原章 | 復原只有 5 秒，倒數線大約剩一半時拍；**拍完按復原**，不然綠茶就真的刪掉了 |
| exercise | 深 | 21:00 今日頁點「運動 +300」打開的「運動消耗明細」 | 沒授權健康連線時面板不會有「來源／走路」那幾列，只有吃了 2183、運動 −600、運動回補 50% +300 —— 這樣就對了，配文講的就是這幾個數字 |
| water | 深 | 今日頁點「飲水」打開的「飲水明細」 | 列出每杯飲料的水、手動加減、合計 |
| report | 深 | 九月月報（記錄 25 天、平均 1761） | 照根目錄 CLAUDE.md〈AI 週報／月報〉塞一份 `monthly_reports/2026-09.json`，不要真的打 API。入口在月曆九月月摘要底下 |
| settings | 深 | 設定選單 | 每一列右邊有摘要。**畫面上不能出現帳號 email 或金鑰**（Drive 那列連著的話會顯示 email） |
| barcode | 深 | 掃條碼：手動輸入條碼查到一項商品，顯示每 100 g 與「實際份量 (g)」 | 模擬器叫不出掃描器，改手動輸入條碼；要連網查 Open Food Facts。挑查得到的商品就好 |
| provider-switch | 深 | 辨識失敗後升上來的「換一家再試」面板 | 要先讓辨識失敗：例如暫時關掉網路（`adb shell svc wifi disable` 與 `svc data disable`）再按「AI 估」。**拍完把網路開回來**；不要改壞真的金鑰 |
| csv-import | 深 | 「要匯入這些紀錄嗎？」確認面板 | 要有一個 CSV 可選：設定裡先匯出一份，或 `adb push` 一份到 `/sdcard/Download`。停在確認面板拍，**按取消**，不要真的匯入（會動到示範資料） |

**拍完**

1. 照〈驗證清單〉用瀏覽器看手機寬度與桌面寬度：每一張都換掉了虛線框、手機版截圖比例對
   （`style.css` 的 `img { height: auto }`）、桌面版右邊手機換畫面的方向對（`index.html` 裡 `.shot` 的 `data-from`）。
2. 模擬器改回自動時間、網路打開，示範資料沒被動到（今日頁還是 2183）。
3. 先給使用者看，他同意再 commit／push 到同一個分支。推上去之後雲端 session 可以再用 Playwright 驗一次。

2026-10-07 這次三項都做了：手機寬度 16 張比例正確、無橫向捲軸；桌面寬度用 headless Chrome 往下捲再倒捲回去，
15 個畫面順序兩個方向都對、`bottom` 由下往上蓋、`fade` 淡入；模擬器資料庫與設定檔換回開始前的備份（逐位元組相同）。

## 驗證清單

- `cd site && npm install && npm run build`，再 `npm run dev` 用瀏覽器看。
- 桌面（約 1440×900）與手機（375×812）各從頭捲到尾、**再倒捲回去**：
  手機有沒有釘住、換畫面兩個方向都對、時鐘連續、19:20 轉紅 21:00 轉回、入夜與天亮、沒有橫向捲軸。
- 字型：粉圓是 Google Fonts 的 `Huninn`（瀏覽器會拿到依 unicode-range 切片的 woff2）；
  數字是本地的 `fonts/neucha.woff2` —— **那是 app 裡改過側邊留白的版本轉出來的，不要換成 Google Fonts 的 Neucha**。
- `.phone` 的尺寸是用 `--sw` 從視窗高度反推的（`style.css` 的 `.phone`），矮螢幕要確認手機不會超出畫面。
- 開 reduced motion 再看一次。

## 部署（要使用者自己動手）

Vercel 後台：Add New → Project → Import `rowing195/NutriLog` → **Root Directory 選 `site`** →
Framework 會自動認成 Vite → Deploy。之後推到 `main` 就自動部署；`vercel.json` 的 `ignoreCommand`
讓沒動到 `site/` 的 commit 直接略過。`.github/workflows/release.yml` 只吃 `v*` tag，兩者互不影響。

## 收尾

- 在根目錄 `CLAUDE.md` 補一小節講 `site/`：色票是 `Theme.kt` 的複本（改色票要改三處：
  `Theme.kt`、`values*/colors.xml`、`site/src/style.css` 與 `main.js` 的深色表）、
  `neucha.woff2` 是改過的那支轉的、截圖來自模擬器示範資料、Vercel Root Directory 是 `site`。
  寫之前先給使用者看草稿。
- 刪掉這個 `HANDOFF.md`。
