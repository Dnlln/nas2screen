# Builds the APK with the toolchain bundled inside the repository.
# Usage:
#   powershell -ExecutionPolicy Bypass -File tools\build_apk.ps1
#   powershell -ExecutionPolicy Bypass -File tools\build_apk.ps1 -Tasks "test assembleRelease"
param(
    [string]$Tasks = "assembleRelease"
)

$ErrorActionPreference = 'Stop'
$ws = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$taskList = @($Tasks -split '[,\s]+' | Where-Object { $_ })

$env:JAVA_HOME = "$ws\.toolchain\jdk-17"
$env:ANDROID_HOME = "$ws\.android-sdk"
$env:ANDROID_SDK_ROOT = "$ws\.android-sdk"
$env:ANDROID_USER_HOME = "$ws\.android-home"
$env:GRADLE_USER_HOME = "$ws\.gradle-home"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:TMP = "$ws\.tmp"
$env:TEMP = "$ws\.tmp"
New-Item -ItemType Directory -Force -Path "$env:TMP" | Out-Null

$gradle = "$ws\.toolchain\gradle-8.7\bin\gradle.bat"
if (-not (Test-Path $gradle)) { throw "gradle not found: $gradle" }
if (-not (Test-Path "$env:JAVA_HOME\bin\java.exe")) { throw "JDK not found: $env:JAVA_HOME" }
if (-not (Test-Path "$env:ANDROID_HOME\platforms\android-34")) { throw "Android SDK platform 34 missing: $env:ANDROID_HOME" }

Write-Host "tasks: $($Tasks -join ', ')"
& $gradle -p $ws --no-daemon --console=plain @taskList
$exit = $LASTEXITCODE

$apk = "$ws\app\build\outputs\apk\release\app-release.apk"
if ($exit -eq 0 -and (Test-Path $apk)) {
    $version = '1.0.0'
    $match = Select-String -Path "$ws\app\build.gradle" -Pattern 'versionName\s+"([^"]+)"'
    if ($match) { $version = $match.Matches[0].Groups[1].Value }
    New-Item -ItemType Directory -Force -Path "$ws\dist" | Out-Null
    $target = "$ws\dist\nas2screen-$version-release.apk"
    Copy-Item $apk $target -Force
    Write-Host "APK: $target"
}
exit $exit
