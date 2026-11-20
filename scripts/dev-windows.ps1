$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root '.env'
$serverDir = Join-Path $root 'server'
$webDir = Join-Path $root 'web'
$logDir = Join-Path $root '.dev'

if (-not (Test-Path -LiteralPath $envFile)) {
    throw "Missing configuration file: $envFile"
}
foreach ($port in 3000, 8080) {
    if (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) {
        throw "Port $port is already in use. Stop the existing service before starting."
    }
}

# The project's local .env uses simple KEY=value entries.
foreach ($line in Get-Content -LiteralPath $envFile) {
    if ($line -match '^\s*(#|$)') { continue }
    if ($line -notmatch '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
        throw 'Invalid .env entry. Use simple KEY=value lines.'
    }
    [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
}

$java = (Get-Command java.exe -ErrorAction Stop).Source
$node = (Get-Command node.exe -ErrorAction Stop).Source
$vite = Join-Path $webDir 'node_modules/vite/bin/vite.js'
if (-not (Test-Path -LiteralPath $vite)) {
    throw 'Frontend dependencies are missing. Run pnpm install in the web directory first.'
}

Write-Host 'Building backend...'
Push-Location $serverDir
try {
    & .\mvnw.cmd -pl skillhub-app -am package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw 'Backend build failed.' }
} finally {
    Pop-Location
}

New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$server = Start-Process -FilePath $java -ArgumentList @('-jar', 'skillhub-app/target/skillhub-app-0.1.0.jar', '--spring.profiles.active=local') -WorkingDirectory $serverDir -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logDir 'server-windows.log') -RedirectStandardError (Join-Path $logDir 'server-windows.err') -PassThru
try {
    $web = Start-Process -FilePath $node -ArgumentList @('node_modules/vite/bin/vite.js', '--host', '127.0.0.1', '--strictPort') -WorkingDirectory $webDir -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logDir 'web-windows.log') -RedirectStandardError (Join-Path $logDir 'web-windows.err') -PassThru
} catch {
    Stop-Process -Id $server.Id -ErrorAction SilentlyContinue
    throw
}

Write-Host "Backend PID: $($server.Id); frontend PID: $($web.Id)"
Write-Host 'Starting: http://localhost:3000 (backend: http://localhost:8080)'
Write-Host "Logs: $logDir\server-windows.log and $logDir\web-windows.log"
Write-Host "Stop with: Stop-Process -Id $($server.Id),$($web.Id)"
