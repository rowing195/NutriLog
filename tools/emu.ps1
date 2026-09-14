# Emulator lifecycle for NutriLog testing.
# ASCII-only comments on purpose (see ui.ps1 for why).
#
#   emu.ps1 start      boot it and wait until it is actually usable
#   emu.ps1 deploy     build + install the debug APK
#   emu.ps1 stop       shut it down
#
# The AVD is shared with the other Android projects on this machine: it is just
# a device image, nothing in it is app-specific, and a second one would cost
# another multi-GB userdata partition for no benefit. Override with -AvdName.
#
# After 'start', drive the UI with:
#   tools\ui.ps1 dump -Serial emulator-5554

param(
    [Parameter(Mandatory = $true)][string]$Action,
    [string]$AvdName = 'localreader_api35'
)

$ErrorActionPreference = 'Stop'

$Sdk     = "$env:LOCALAPPDATA\Android\Sdk"
$Adb     = "$Sdk\platform-tools\adb.exe"
$Emu     = "$Sdk\emulator\emulator.exe"
$AvdMgr  = "$Sdk\cmdline-tools\latest\bin\avdmanager.bat"
$Project = Split-Path -Parent $PSScriptRoot
$Gradle  = "$env:LOCALAPPDATA\Android\tools\gradle-8.11.1\bin\gradle.bat"

$Image  = 'system-images;android-35;google_apis;x86_64'
$Serial = 'emulator-5554'

$env:JAVA_HOME    = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.7.7-hotspot'
$env:ANDROID_HOME = $Sdk
# ANDROID_SDK_ROOT too, and with backslashes on purpose. config.ini stores the
# system image as a RELATIVE path (image.sysdir.1 = system-images\...), so the
# emulator has to work out an sdk root first. Left to itself it infers one from
# argv[0], and a caller that reached emulator.exe through a forward-slash path
# (or a forward-slash PATH entry) makes it infer a mixed-separator root like
#   C:\Users\Watson\AppData\Local/Android/Sdk
# which its own directory check then rejects:
#   WARNING | ...\system-images\android-35\google_apis\x86_64\ is not a valid directory
#   FATAL   | Cannot find AVD system path. Please define ANDROID_SDK_ROOT
# Setting it explicitly here means the inference never runs. This actually
# happened to another agent on this machine.
$env:ANDROID_SDK_ROOT = $Sdk

switch ($Action) {

    'create' {
        if (-not (Test-Path "$Sdk\system-images\android-35\google_apis\x86_64")) {
            throw "System image missing. Run: sdkmanager `"$Image`""
        }
        # 'no' declines the custom hardware profile prompt and keeps the defaults.
        'no' | & $AvdMgr create avd -n $AvdName -k $Image -d pixel_7 --force
        Write-Output "AVD created: $AvdName"
    }

    'start' {
        if ((& $Adb devices) -match [regex]::Escape($Serial)) {
            Write-Output "$Serial already running"
            break
        }
        # Detached: the emulator process must outlive this script.
        # -PassThru so we can tell "still booting" apart from "it died on startup".
        $proc = Start-Process -FilePath $Emu -PassThru -ArgumentList @('-avd', $AvdName, '-no-boot-anim')
        Write-Output "Booting $AvdName ..."

        # Deliberately NOT 'adb wait-for-device': it blocks forever when the
        # emulator process is already dead, and the real reason (which the
        # emulator printed and we never see) is lost. Poll instead, and give up
        # the moment the process exits.
        $deadline = (Get-Date).AddMinutes(5)
        do {
            Start-Sleep -Seconds 3

            if ($proc.HasExited) {
                throw ("emulator exited (code $($proc.ExitCode)) before the device came up. " +
                       "Run it in the foreground to see why:`n" +
                       "  & `"$Emu`" -avd $AvdName -verbose")
            }
            if ((Get-Date) -gt $deadline) {
                throw "timed out after 5 minutes waiting for $Serial to boot"
            }

            # Ask getprop only once the device is really 'device'. Matching the
            # serial alone is not enough: it shows up as 'offline' for the first
            # few seconds, and getprop against it prints 'device offline' on every
            # loop and leaves a non-zero exit code behind.
            $booted = ''
            if ((& $Adb devices) -match ([regex]::Escape($Serial) + '\s+device')) {
                $booted = (& $Adb -s $Serial shell getprop sys.boot_completed) -replace '\s', ''
            }
        } while ($booted -ne '1')

        & $Adb -s $Serial shell input keyevent KEYCODE_WAKEUP | Out-Null
        & $Adb -s $Serial shell wm dismiss-keyguard | Out-Null
        Write-Output "$Serial ready"
    }

    'deploy' {
        & $Gradle -p $Project assembleDebug --console=plain -q
        if ($LASTEXITCODE -ne 0) { throw "gradle assembleDebug failed" }

        # Check the install actually happened. Without this the script cheerfully
        # prints "deployed" after adb said "device not found", and the next few
        # minutes get spent debugging an app version that was never installed.
        & $Adb -s $Serial install -r "$Project\app\build\outputs\apk\debug\app-debug.apk"
        if ($LASTEXITCODE -ne 0) { throw "adb install failed (is $Serial running? try: emu.ps1 start)" }
        Write-Output "deployed to $Serial"
    }

    'stop' { & $Adb -s $Serial emu kill }
}
