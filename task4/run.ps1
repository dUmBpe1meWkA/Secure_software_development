$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

. (Join-Path $PSScriptRoot 'start-db.ps1')
if (-not $env:PARKING_APP_PASSWORD) {
    $env:PARKING_APP_PASSWORD = Read-DatabaseValue 'Application database user password (parking_app)'
}
if (-not $env:PARKING_DEMO_PASSWORD) {
    $env:PARKING_DEMO_PASSWORD = Read-DatabaseValue 'Demo accounts password (first run only, press Enter on later runs)'
}

& .\mvnw.cmd spring-boot:run
exit $LASTEXITCODE
