# Extracts the JDK and installs the Android SDK packages the project needs.
# Everything stays inside the repository so no system folders are touched.
$ErrorActionPreference = 'Stop'

$ws = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$jdk = "$ws\.toolchain\jdk-17"
$sdk = "$ws\.android-sdk"

if (-not (Test-Path "$jdk\bin\java.exe")) {
    Write-Host "extracting JDK..."
    $tmp = "$ws\.toolchain\jdk-tmp"
    New-Item -ItemType Directory -Force -Path $tmp | Out-Null
    tar.exe -xf "$ws\.toolchain\downloads\jdk17.zip" -C $tmp
    if ($LASTEXITCODE -ne 0) { throw "cannot extract JDK" }
    $inner = Get-ChildItem $tmp -Directory | Select-Object -First 1
    Move-Item $inner.FullName $jdk
    Remove-Item $tmp -Recurse -Force
}

Write-Host "JAVA_HOME=$jdk"

$env:JAVA_HOME = $jdk
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$env:ANDROID_USER_HOME = "$ws\.android-home"
New-Item -ItemType Directory -Force -Path $env:ANDROID_USER_HOME | Out-Null

$sdkmanager = "$sdk\cmdline-tools\latest\bin\sdkmanager.bat"
if (-not (Test-Path $sdkmanager)) { throw "sdkmanager missing" }

Write-Host "accepting licenses..."
1..40 | ForEach-Object { 'y' } | & $sdkmanager --sdk_root=$sdk --licenses | Out-Null

Write-Host "installing packages..."
& $sdkmanager --sdk_root=$sdk "platform-tools" "platforms;android-34" "build-tools;34.0.0"
if ($LASTEXITCODE -ne 0) { throw "sdkmanager failed: $LASTEXITCODE" }

Write-Host "installed:"
Get-ChildItem $sdk -Directory | Select-Object -ExpandProperty Name
Write-Host "done"
