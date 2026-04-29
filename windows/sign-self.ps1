# Self-signs the APiX TV MSI/EXE installers using the cert generated per
# windows/SIGNING.md. Safe to call repeatedly — silently no-ops if the cert
# or signtool is missing.
$ErrorActionPreference = "Continue"

$thumbprintFile = Join-Path $HOME "apix-thumbprint.txt"
if (-not (Test-Path $thumbprintFile)) {
    Write-Host "[sign-self] thumbprint not found — skipping (run cert setup first)."
    exit 0
}
$thumbprint = (Get-Content $thumbprintFile).Trim()
if (-not $thumbprint) { Write-Host "[sign-self] empty thumbprint — skipping."; exit 0 }

# Locate signtool.exe (Windows SDK)
$signtool = Get-ChildItem "C:\Program Files (x86)\Windows Kits\10\bin" -Recurse -Filter signtool.exe -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -match "x64" } | Select-Object -First 1
if (-not $signtool) {
    Write-Host "[sign-self] signtool.exe not found in Windows SDK — install it from https://developer.microsoft.com/windows/downloads/windows-sdk/"
    exit 0
}

$buildDir = Join-Path $PSScriptRoot "app\build\compose\binaries\main\msi"
if (-not (Test-Path $buildDir)) {
    Write-Host "[sign-self] no MSI output yet at $buildDir — skipping."
    exit 0
}

Get-ChildItem $buildDir -Filter "*.msi" | ForEach-Object {
    $file = $_.FullName
    Write-Host "[sign-self] signing $file"
    & $signtool.FullName sign /sha1 $thumbprint /fd SHA256 /tr http://timestamp.digicert.com /td SHA256 $file
}
