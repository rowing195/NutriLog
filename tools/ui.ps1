# UI driver: always dump first, locate by text, never hardcode coordinates.
# Comments are ASCII on purpose -- PS 5.1 reads BOM-less files as ANSI and
# chokes on some CJK byte sequences.
#
#   ui.ps1 dump            list every node that has text, with center coords
#   ui.ps1 tap  <text>     tap the first node whose text contains <text>
#   ui.ps1 type <text>     type into the focused field (ASCII/digits only)
#   ui.ps1 has  <text>     FOUND / NOT-FOUND check
#   ui.ps1 back            press the back key
#   ui.ps1 scroll up|down  swipe the screen to reveal off-screen controls
#   ui.ps1 focus           show current foreground window
#   ui.ps1 devices         list attached devices
#
# With both a phone and an emulator attached, adb refuses to guess. Pick one:
#   ui.ps1 dump -Serial emulator-5554
#   $env:ANDROID_SERIAL = "emulator-5554"    # or set it once for the session

param(
    [Parameter(Mandatory = $true)][string]$Action,
    [string]$Target,
    [string]$Serial,
    # Regex the foreground app must match before a tap is allowed.
    # Default keeps taps inside NutriLog; pass '.' to allow anything.
    [string]$InApp = 'nutrilog'
)

$adbExe = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if ($Serial) { $env:ANDROID_SERIAL = $Serial }

# adb picks up ANDROID_SERIAL by itself, so this wrapper just keeps the
# call sites short and gives one place to add flags later.
function Adb { & $adbExe @args }

# Fail with something readable instead of adb's bare "more than one device".
function Assert-SingleTarget {
    if ($env:ANDROID_SERIAL) { return }
    $lines = (Adb devices) | Where-Object { $_ -match '\sdevice$' }
    if ($lines.Count -gt 1) {
        $names = ($lines | ForEach-Object { ($_ -split '\s+')[0] }) -join ', '
        throw "Multiple devices attached ($names). Pass -Serial <name> or set `$env:ANDROID_SERIAL."
    }
}

function Get-Focus {
    (Adb shell dumpsys window | Select-String "mCurrentFocus").ToString().Trim()
}

# Guard taps with this one: Compose DropdownMenu / ModalBottomSheet steal
# mCurrentFocus into a popup window, but mFocusedApp still names the Activity.
function Get-FocusedApp {
    (Adb shell dumpsys window | Select-String "mFocusedApp").ToString().Trim()
}

function Get-Nodes {
    # Delete the stale file first: dump fails while the screen is animating,
    # and reading last run's leftover XML means tapping stale coordinates --
    # exactly the blind-tap failure this script exists to prevent.
    Adb shell rm -f /sdcard/ui.xml | Out-Null
    $raw = $null
    foreach ($attempt in 1..4) {
        Adb shell uiautomator dump /sdcard/ui.xml 2>&1 | Out-Null
        $raw = Adb shell cat /sdcard/ui.xml 2>$null
        if ($raw -and $raw -match '<hierarchy') { break }
        Start-Sleep -Milliseconds 700
    }
    if (-not ($raw -and $raw -match '<hierarchy')) { throw "uiautomator dump failed (screen still animating?)" }
    $xml = [xml]$raw
    $nodes = @()
    foreach ($n in $xml.SelectNodes("//node")) {
        $t = $n.text
        $d = $n.'content-desc'
        if ([string]::IsNullOrWhiteSpace($t) -and [string]::IsNullOrWhiteSpace($d)) { continue }
        if ($n.bounds -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
            $nodes += [pscustomobject]@{
                Text = if ([string]::IsNullOrWhiteSpace($t)) { $d } else { $t }
                X    = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
                Y    = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
            }
        }
    }
    return $nodes
}

# Exact matches win over substring matches. Without this, tapping a button
# can land on a hint paragraph that quotes the button name instead of the
# button -- the hint is not clickable, so the tap silently does nothing.
function Find-Node {
    param([string]$Text)
    $nodes = Get-Nodes
    $exact = $nodes | Where-Object { $_.Text -eq $Text }
    if ($exact) { return @($exact)[0] }
    return ($nodes | Where-Object { $_.Text -like "*$Text*" } | Select-Object -First 1)
}

switch ($Action) {
    'devices' { Adb devices }

    'focus' { Assert-SingleTarget; Get-Focus }

    'back' { Assert-SingleTarget; Adb shell input keyevent KEYCODE_BACK | Out-Null; Write-Output "BACK" }

    'dump' {
        Assert-SingleTarget
        Write-Output "FOCUS: $(Get-Focus)"
        Get-Nodes | ForEach-Object { "{0,5},{1,5}  {2}" -f $_.X, $_.Y, $_.Text }
    }

    'has' {
        Assert-SingleTarget
        $hit = Get-Nodes | Where-Object { $_.Text -like "*$Target*" }
        if ($hit) { "FOUND: " + (($hit | ForEach-Object { $_.Text }) -join ' | ') }
        else { "NOT-FOUND: $Target" }
    }

    'type' {
        # 'adb shell input text' cannot send non-ASCII, so test data stays ASCII.
        # Spaces must be escaped as %s or the shell splits the argument.
        Assert-SingleTarget
        $escaped = $Target -replace ' ', '%s'
        Adb shell input text $escaped | Out-Null
        Write-Output "TYPE $Target"
    }

    'scroll' {
        # Target = up | down. Long forms scroll far enough to reach a save
        # button that the soft keyboard or an expanded section pushed off screen.
        Assert-SingleTarget
        $wm = (Adb shell wm size) -join ''
        if ($wm -notmatch '(\d+)x(\d+)') { throw "cannot read screen size: $wm" }
        $w = [int]$Matches[1]; $h = [int]$Matches[2]
        $x = [int]($w / 2)
        if ($Target -eq 'up') { $from = [int]($h * 0.30); $to = [int]($h * 0.75) }
        else                  { $from = [int]($h * 0.75); $to = [int]($h * 0.30) }
        Adb shell input swipe $x $from $x $to 300 | Out-Null
        Write-Output "SCROLL $Target"
    }

    'tapxy' {
        # Escape hatch for things with no text at all.
        # Coordinates must come from a fresh 'dump' in the same screen state.
        Assert-SingleTarget
        $parts = $Target -split ','
        Adb shell input tap $parts[0].Trim() $parts[1].Trim() | Out-Null
        Write-Output "TAP $Target"
    }

    'tap' {
        Assert-SingleTarget
        $focus = Get-FocusedApp
        # -InApp lets the caller drive another app on purpose, while the
        # default still refuses to tap blind if something else grabbed
        # the foreground.
        if ($focus -notmatch $InApp) {
            Write-Output "ABORT foreground does not match '$InApp': $focus"
            exit 1
        }
        $hit = Find-Node -Text $Target
        if (-not $hit) {
            Write-Output "ABORT no node matching: $Target"
            exit 1
        }
        Adb shell input tap $hit.X $hit.Y | Out-Null
        Write-Output ("TAP {0} @ {1},{2}" -f $hit.Text, $hit.X, $hit.Y)
    }
}
