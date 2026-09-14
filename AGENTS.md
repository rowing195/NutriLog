# 本機 Android 環境

- 工作前閱讀本專案的 `CLAUDE.md`、上一層 `..\CLAUDE.md` 與 `HANDOFF.md`，共用專案慣例與最新環境排錯紀錄；本檔只保留 Codex 需要立即注意的摘要，避免整份複製後內容不同步。
- 使用者指定的 Android SDK 路徑：`C:\Users\Watson\AppData\Local\Android\Sdk`。路徑末尾沒有空白；訊息中的 `&#x20;` 不是路徑的一部分。
- ADB：`C:\Users\Watson\AppData\Local\Android\Sdk\platform-tools\adb.exe`。
- 模擬器：`C:\Users\Watson\AppData\Local\Android\Sdk\emulator\emulator.exe`。
- AVD 名稱：`localreader_api35`；操作目標固定為 `emulator-5554`，不要操作實體手機。
- 操作前閱讀 `HANDOFF.md`；啟動使用 `tools\emu.ps1 start`。若需設定目前程序的 `ANDROID_HOME` 與 `ANDROID_SDK_ROOT`，使用上述完整 SDK 路徑。
- 啟動後用 ADB 確認裝置連線及 `sys.boot_completed`，只有完成開機才能回報啟動成功。
- 啟動失敗時讀取腳本產生的 log，回報實際錯誤。不要自行下載、安裝系統映像或重建 AVD；使用者已明確要求不要下載。
- 修改程式後，在模擬器驗證前先安裝新建置的 APK，避免測到舊版。

## MSIX 檔案系統重導向

- 依 `..\CLAUDE.md` 的 2026-09-14 排錯紀錄，Claude 桌面版的 MSIX 套件身分會讓子程序看到不同的 `%LOCALAPPDATA%` 視圖。系統映像曾被安裝到 `C:\Users\Watson\AppData\Local\Packages\Claude_pzs8sxrjxfjjc\LocalCache\Local\Android\Sdk\system-images`，導致 Claude 能啟動，但其他程序不能。
- 遇到不同程序對同一路徑的結果不一致時，先考慮重導向，不要單憑 agent 的 `Test-Path` 或 `dir` 推斷全機器的檔案狀態，也不要只反覆修改斜線或環境變數。需要確認真實使用者視圖時，請使用者在自己的終端機執行指定路徑的 `cmd /c dir /a`。
- 不由 agent 執行安裝到 `%LOCALAPPDATA%` 的 SDK／系統映像安裝指令；如需補裝，由使用者在自己的終端機處理。仍遵守使用者禁止自行下載的要求。
- 啟動錯誤的完整 log 位於 `%TEMP%\nutrilog-emu\`；保留並讀取該次 log，不以後續重試覆蓋當次證據。

## 建置與 UI 驗證

- 使用專案內的 `gradlew.bat` 建置與測試，不使用舊的獨立 Gradle 安裝路徑。
- UI 操作使用 `tools\ui.ps1`，先 dump 再依文字定位；只有無文字元件才使用當次 dump 的座標。
- PowerShell 腳本保持 ASCII，程式註解依專案慣例使用繁體中文。

## 授權

使用者已授權記錄上述路徑及操作模擬器。此文件僅記錄工作約定，不會變更工具或作業系統權限；若執行環境另有權限限制，仍須遵守。
