$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

. (Join-Path $PSScriptRoot 'start-db.ps1')
if (-not $env:PARKING_APP_PASSWORD) {
    $env:PARKING_APP_PASSWORD = Read-DatabaseValue 'Application database user password (parking_app)'
}

& .\mvnw.cmd spring-boot:run
exit $LASTEXITCODE
