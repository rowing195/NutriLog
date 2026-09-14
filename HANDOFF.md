# 接手備忘：模擬器在哪裡

給另一個 agent／另一台機器看的最短版本。專案本身的規則全部在 [CLAUDE.md](CLAUDE.md)，
這份只回答一件事：**Android SDK 與模擬器在哪、為什麼你找不到它。**

## 為什麼找不到

```
ANDROID_HOME      <未設>
ANDROID_SDK_ROOT  <未設>
```

這台機器**沒有設這兩個環境變數**。Gradle 找得到 SDK 是靠專案根目錄的
`local.properties`（`sdk.dir=...`，不在版控裡），**不是靠環境變數** —— 所以建置一直
正常，但任何用 `ANDROID_HOME` 去湊路徑的工具都會撲空。

## 路徑

| 東西 | 位置 |
|---|---|
| SDK 根目錄 | `C:\Users\Watson\AppData\Local\Android\Sdk` |
| emulator | `…\Sdk\emulator\emulator.exe` |
| adb | `…\Sdk\platform-tools\adb.exe` |
| avdmanager | `…\Sdk\cmdline-tools\latest\bin\avdmanager.bat` |
| 系統映像 | `…\Sdk\system-images\android-35\google_apis\x86_64` |
| JDK 17 | `C:\Program Files\Eclipse Adoptium\jdk-17.0.7.7-hotspot` |

**AVD 不在 SDK 底下**，這是最常找錯的地方：

```
C:\Users\Watson\.android\avd\localreader_api35.avd
```

AVD 名稱是 **`localreader_api35`**，整台機器只有這一個，各個 Android 專案共用
（它只是一個裝置映像，和 app 無關）。

## 怎麼開

**用 repo 裡的腳本，不要自己拼 `emulator.exe`：**

```powershell
& "C:\code\android app\NutriLog\tools\emu.ps1" start    # 開機並等到真的能用
& "C:\code\android app\NutriLog\tools\emu.ps1" deploy   # 建置 + 安裝 debug APK
& "C:\code\android app\NutriLog\tools\emu.ps1" stop
```

`start` 會等 `sys.boot_completed`、喚醒螢幕、解鎖 keyguard 才回傳。自己拼指令通常
只等到 `wait-for-device`，那只代表 adbd 回話了，**畫面還沒起來** —— 接著做的每一步
都會失敗得莫名其妙。

驅動 UI 用 `tools\ui.ps1`（依元件文字定位，不要盲點座標），用法見 CLAUDE.md。

### 看到 "Broken AVD system path" 的時候

```
WARNING | ...\system-images\android-35\google_apis\x86_64\ is not a valid directory
FATAL   | Broken AVD system path. Check your ANDROID_SDK_ROOT value [...\Sdk]!
```

**照字面讀：那個目錄真的不在。** 訊息第二行叫你去查 `ANDROID_SDK_ROOT`，會把人帶去
查環境變數和斜線 —— 那條路是死的。實測 `ANDROID_SDK_ROOT` 設得完全正確（`tools\emu.ps1`
本來就幫你設好了）也照樣出這則訊息。

第一件事是**在你自己的終端機視窗**確認目錄在不在：

```powershell
cmd /c dir /a "C:\Users\Watson\AppData\Local\Android\Sdk\system-images\android-35\google_apis\x86_64"
```

**不要拿 agent 的 `Test-Path` 當結論。** Claude 桌面版是 MSIX 封裝，它開出來的
process 對 `%LOCALAPPDATA%` 有重導向，看到的是「真實的 ＋ 套件私有的」合併視圖 ——
所以它可能看得到一個這台機器上根本沒有的 SDK 目錄，而它自己分辨不出來。
完整機制見 `..\CLAUDE.md` 的〈Claude 桌面版是 MSIX〉那節。

印「找不到檔案」就是答案，裝回去 —— **這一行要使用者在自己的終端機視窗跑，
不要讓 agent 跑**（agent 跑的話整包 3.5 GB 會被重導向進套件私有區，只有它自己用得到）：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.7.7-hotspot'
& "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat" "system-images;android-35;google_apis;x86_64"
```

`emu.ps1 start` 失敗時會把 emulator 自己印的東西留在 `%TEMP%\nutrilog-emu\`，
上面那兩行就是從那裡來的。

## 三個會浪費你半小時的坑

- **測試目標固定是 `emulator-5554`。不要 `adb install` 到實體手機。** 使用者自己裝 APK。
  手機沒出現在 `adb devices` 是正常的。
- **`tools\*.ps1` 只能用 ASCII。** PS 5.1 把沒有 BOM 的檔案當 ANSI 讀，某些中文位元組
  序列會直接 parse error。
- **在 Bash 裡不要用 `$LOCALAPPDATA` 拼路徑。** 它展開成反斜線的 Windows 路徑，接在 `/`
  後面會變成找不到檔案的怪路徑，而且錯誤訊息常被 grep 一起濾掉，看起來像「安靜地成功了」。
  用 `/c/Users/Watson/AppData/Local/Android/Sdk` 這種寫法。

## 如果你想一勞永逸

```powershell
setx ANDROID_HOME "%LOCALAPPDATA%\Android\Sdk"
```

PATH 也要加 `platform-tools` 與 `emulator` 兩個資料夾。**但不要用 `setx` 改 PATH** ——
它會把展開後的整串寫死，PATH 長的話會被截斷。改 PATH 請走「系統內容 → 環境變數」的 GUI。
設完要重開終端機才生效。
