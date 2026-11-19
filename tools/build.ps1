# ============================================================
#  DeuClient - full build (run: tools/build.ps1)
#  1. Build the DivineCore mods (1.21.4 and 26.3, Gradle)
#  2. Build the launcher (icons, embed mods, Electron, installer)
#  Result: release\DeuClient-Setup.exe
# ============================================================
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

foreach ($mod in Get-ChildItem "$root\mod" -Directory -Filter 'mc*') {
    Write-Host "`nBuilding DivineCore ($($mod.Name)) ..." -ForegroundColor Green
    Push-Location $mod.FullName
    .\gradlew.bat build --no-daemon
    if ($LASTEXITCODE -ne 0) { Pop-Location; throw "Mod build $($mod.Name) failed" }
    Pop-Location
}

Write-Host "`nBuilding DeuClient ..." -ForegroundColor Green
Push-Location $root
if (-not (Test-Path node_modules)) { npm install; if ($LASTEXITCODE -ne 0) { Pop-Location; throw 'npm install failed' } }
npm run build
if ($LASTEXITCODE -ne 0) { Pop-Location; throw 'Launcher build failed' }
Pop-Location

Write-Host "`nDone: $root\release\DeuClient-Setup.exe" -ForegroundColor Yellow
