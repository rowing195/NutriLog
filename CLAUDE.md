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

## 配色

Material3 預設 baseline 是紫色系。**新增顏色角色時整族都要蓋**
（`surfaceContainerLowest/Low/_/High/Highest`、`surfaceVariant`、`outline*`），
少蓋一個就會有元件固執地維持預設紫。第一版只蓋了 `primary`，
結果整個 app 的背景與卡片都是淡紫灰。

三大營養素的固定色放在 `theme/NutrientColors`，不要在各畫面自己寫死色碼。

## 改動慣例

- 註解寫**繁體中文**，解釋「為什麼」而不是「做了什麼」。
- 新增 `NutriSettings` 欄位一律給預設值 —— 舊的 DataStore 資料靠預設值相容，不做遷移。
- 動到 Room entity 就要**加 migration 並升 version**，這裡和 DataStore 不一樣，
  沒有「靠預設值相容」這回事。
- 營養素缺資料一律 `null`，不要補 0 ——「沒標示」和「真的是 0」必須分得開。
- 模型或外部 API 回來的數字**永遠要經過使用者確認畫面**才入庫。
- 解析外部資料（OFF、Gemini、DataStore）一律 `runCatching` 包起來給安全退路。
- 動到閱讀以外的畫面後，回歸這三項：**手動新增→編輯→刪除**、
  **換日**（前一天應為空、回今天資料還在）、**force-stop 後資料與設定都還在**。
