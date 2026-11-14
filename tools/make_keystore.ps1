# Creates the signing key used for the release APK and writes keystore.properties.
# The key stays local (git-ignored): keep it to be able to update the installed app.
param(
    [string]$StorePassword = "nas2screen",
    [string]$Alias = "nas2screen"
)

$ErrorActionPreference = 'Stop'
$ws = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$keytool = "$ws\.toolchain\jdk-17\bin\keytool.exe"
if (-not (Test-Path $keytool)) {
    throw "JDK not found at $keytool - run: python tools\fetch_toolchain.py and tools\setup_sdk.ps1"
}

$store = "$ws\keystore\release.jks"
if (Test-Path $store) {
    Write-Host "keystore already exists: $store"
} else {
    New-Item -ItemType Directory -Force -Path "$ws\keystore" | Out-Null
    & $keytool -genkeypair -v -keystore $store -storetype JKS -alias $Alias `
        -keyalg RSA -keysize 2048 -validity 12000 `
        -storepass $StorePassword -keypass $StorePassword `
        -dname "CN=NAS Photo Screensaver, OU=Home, O=Home, L=Home, ST=Home, C=RU"
    if ($LASTEXITCODE -ne 0) { throw "keytool failed: $LASTEXITCODE" }
}

@"
storeFile=keystore/release.jks
storePassword=$StorePassword
keyAlias=$Alias
keyPassword=$StorePassword
"@ | Out-File -FilePath "$ws\keystore.properties" -Encoding ascii

Write-Host "keystore.properties written; build with: tools\build_apk.ps1"
