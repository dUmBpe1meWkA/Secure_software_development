param(
    [string]$ContainerName = 'task3-postgres',
    [int]$Port = 55432
)

$ErrorActionPreference = 'Stop'

function Read-DatabaseValue([string]$prompt) {
    $value = Read-Host $prompt -AsSecureString
    return [System.Net.NetworkCredential]::new('', $value).Password
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Docker is not installed. Install and start Docker Desktop.'
}
docker info --format '{{.ServerVersion}}' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Start Docker Desktop, then run this script again.' }

if (-not $env:PARKING_ADMIN_PASSWORD) {
    $env:PARKING_ADMIN_PASSWORD = Read-DatabaseValue 'PostgreSQL administrator password (choose it on the first run)'
}
if ([string]::IsNullOrWhiteSpace($env:PARKING_ADMIN_PASSWORD)) { throw 'The administrator password must not be empty.' }

$existing = @(docker ps -a --filter "name=^/${ContainerName}$" --format '{{.Names}}')
if ($LASTEXITCODE -ne 0) { throw 'Cannot list Docker containers.' }
if ($existing -contains $ContainerName) {
    $owner = docker container inspect --format '{{.Config.Labels.parking_task3}}' $ContainerName
    if ($LASTEXITCODE -ne 0 -or $owner -ne 'true') { throw 'A container with this name exists but does not belong to task3.' }
    docker start $ContainerName | Out-Null
} else {
    $previousPostgresValue = $env:POSTGRES_PASSWORD
    try {
        $env:POSTGRES_PASSWORD = $env:PARKING_ADMIN_PASSWORD
        docker run -d --name $ContainerName --label parking_task3=true `
            -p "127.0.0.1:${Port}:5432" `
            -e POSTGRES_PASSWORD -e POSTGRES_DB=parking `
            -v "${ContainerName}-data:/var/lib/postgresql/data" postgres:17-alpine | Out-Null
    } finally {
        $env:POSTGRES_PASSWORD = $previousPostgresValue
    }
}
if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL container could not be started.' }

$ready = $false
for ($attempt = 0; $attempt -lt 60; $attempt++) {
    docker exec $ContainerName pg_isready -h 127.0.0.1 -U postgres -d parking -q
    if ($LASTEXITCODE -eq 0) { $ready = $true; break }
    Start-Sleep -Seconds 1
}
if (-not $ready) { throw 'PostgreSQL did not become ready. Check Docker Desktop.' }

if (-not $env:PARKING_DB_URL) { $env:PARKING_DB_URL = "jdbc:postgresql://localhost:${Port}/parking" }
Write-Host "PostgreSQL is ready: localhost:${Port}, database parking, container ${ContainerName}"
