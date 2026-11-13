$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

. (Join-Path $PSScriptRoot 'start-db.ps1')
if (-not $env:PARKING_APP_PASSWORD) {
    $env:PARKING_APP_PASSWORD = Read-DatabaseValue 'Application database user password (parking_app)'
}
if (-not $env:PARKING_DEMO_PASSWORD) {
    $env:PARKING_DEMO_PASSWORD = Read-DatabaseValue 'Demo accounts password (first run only, press Enter on later runs)'
}

if (-not $env:PARKING_JWT_SECRET) {
    $env:PARKING_JWT_SECRET = Read-DatabaseValue 'JWT signing secret (at least 32 UTF-8 bytes)'
}

& .\mvnw.cmd spring-boot:run
exit $LASTEXITCODE
