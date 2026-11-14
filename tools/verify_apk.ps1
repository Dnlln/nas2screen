# Inspects the built APK: manifest components, permissions, signature, dex.
param(
    [string]$Apk = ""
)

$ErrorActionPreference = 'Stop'
$ws = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$bt = "$ws\.android-sdk\build-tools\34.0.0"

if (-not $Apk) {
    $candidates = @("$ws\dist\nas2screen-release.apk", "$ws\app\build\outputs\apk\release\app-release.apk")
    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) { $Apk = $candidate; break }
    }
}
if (-not $Apk -or -not (Test-Path $Apk)) { throw "APK not found" }

Write-Host "=== APK: $Apk"
Get-Item $Apk | Select-Object Name, Length, LastWriteTime | Format-List | Out-String | Write-Host

$env:JAVA_HOME = "$ws\.toolchain\jdk-17"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

Write-Host "=== badging"
& "$bt\aapt2.exe" dump badging $Apk | Select-String -Pattern '^package:|^sdkVersion|^targetSdkVersion|^uses-permission|^application-label|^launchable-activity|^application:|^feature|^native-code|^supports-screens' | ForEach-Object { $_.Line }

Write-Host "=== services / dream"
& "$bt\aapt2.exe" dump xmltree --file AndroidManifest.xml $Apk | Select-String -Pattern 'service|DreamService|activity|meta-data|name=' | ForEach-Object { $_.Line.Trim() } | Select-Object -First 40

Write-Host "=== signature"
& "$bt\apksigner.bat" verify --print-certs --verbose $Apk | Select-String -Pattern 'Verified|Signer|DN|digest' | ForEach-Object { $_.Line }

Write-Host "=== dex / resources"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($Apk)
$entries = $zip.Entries | Sort-Object FullName
Write-Host ("entries: " + $entries.Count)
$entries | Where-Object { $_.FullName -match '^(classes|resources|AndroidManifest|META-INF/[A-Z]|lib/)' } |
    Select-Object FullName, Length | Format-Table -AutoSize | Out-String | Write-Host
Write-Host ("uncompressed total: {0:N1} MB" -f (($entries | Measure-Object Length -Sum).Sum / 1MB))
$zip.Dispose()
