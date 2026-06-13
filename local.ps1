param(
    [ValidateSet("up", "start", "down", "stop", "restart", "logs", "status", "ps")]
    [string]$Action = "up",
    [ValidateSet("all", "backend", "frontend", "keycloak")]
    [string]$Service = "all"
)

$ErrorActionPreference = "Stop"

trap {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
}

$root = $PSScriptRoot
$runtimeDir = Join-Path $root ".local-dev"
$composeFile = Join-Path $root "docker-compose.dev-auth.yml"
$envFile = Join-Path $root ".env"

$services = @{
    backend = @{
        PidFile = Join-Path $runtimeDir "backend.pid"
        OutLog  = Join-Path $runtimeDir "backend.log"
        ErrLog  = Join-Path $runtimeDir "backend.error.log"
    }
    frontend = @{
        PidFile = Join-Path $runtimeDir "frontend.pid"
        OutLog  = Join-Path $runtimeDir "frontend.log"
        ErrLog  = Join-Path $runtimeDir "frontend.error.log"
    }
}

function Import-DotEnv {
    if (-not (Test-Path -LiteralPath $envFile)) {
        throw ".env bulunamadi: $envFile"
    }

    foreach ($rawLine in Get-Content -LiteralPath $envFile) {
        $line = $rawLine.Trim()
        if (-not $line -or $line.StartsWith("#")) {
            continue
        }

        $separator = $line.IndexOf("=")
        if ($separator -lt 1) {
            continue
        }

        $name = $line.Substring(0, $separator).Trim()
        $value = $line.Substring($separator + 1).Trim()
        if ($value.Length -ge 2 -and (
            ($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))
        )) {
            $value = $value.Substring(1, $value.Length - 2)
        }

        Set-Item -Path "Env:$name" -Value $value
    }

    $env:SPRING_PROFILES_ACTIVE = "local"
    $env:OIDC_ISSUER_URI = "http://localhost:8081/realms/localys-realm"
}

function Assert-Command {
    param([string]$Name, [string]$Help)

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "'$Name' bulunamadi. $Help"
    }
}

function Assert-Environment {
    $required = @(
        "SPRING_DATASOURCE_URL",
        "SPRING_DATASOURCE_USERNAME",
        "SPRING_DATASOURCE_PASSWORD",
        "KEYCLOAK_DB_URL",
        "KEYCLOAK_DB_USERNAME",
        "KEYCLOAK_DB_PASSWORD"
    )
    $missing = @($required | Where-Object { -not (Get-Item "Env:$_" -ErrorAction SilentlyContinue).Value })
    if ($missing.Count -gt 0) {
        throw ".env icinde eksik veya bos degiskenler: $($missing -join ', ')"
    }
}

function Assert-DockerRunning {
    & cmd.exe /d /c "docker info >nul 2>&1"
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Desktop calismiyor. Docker Desktop'i baslatip tekrar deneyin."
    }
}

function Invoke-Compose {
    param([string[]]$ComposeArgs)

    & docker compose --project-directory $root -f $composeFile @ComposeArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose komutu basarisiz oldu."
    }
}

function Get-ManagedProcess {
    param([string]$Name)

    $pidFile = $services[$Name].PidFile
    if (-not (Test-Path -LiteralPath $pidFile)) {
        return $null
    }

    $savedPid = (Get-Content -LiteralPath $pidFile -Raw).Trim()
    if ($savedPid -notmatch "^\d+$") {
        Remove-Item -LiteralPath $pidFile -Force
        return $null
    }

    $process = Get-Process -Id ([int]$savedPid) -ErrorAction SilentlyContinue
    if (-not $process) {
        Remove-Item -LiteralPath $pidFile -Force
    }
    return $process
}

function Start-ManagedProcess {
    param(
        [string]$Name,
        [string]$Command,
        [string]$WorkingDirectory
    )

    $existing = Get-ManagedProcess $Name
    if ($existing) {
        Write-Host "$Name zaten calisiyor (PID $($existing.Id))."
        return
    }

    $config = $services[$Name]
    New-Item -ItemType Directory -Path $runtimeDir -Force | Out-Null
    Remove-Item -LiteralPath $config.OutLog, $config.ErrLog -Force -ErrorAction SilentlyContinue

    $process = Start-Process `
        -FilePath "cmd.exe" `
        -ArgumentList @("/d", "/s", "/c", $Command) `
        -WorkingDirectory $WorkingDirectory `
        -RedirectStandardOutput $config.OutLog `
        -RedirectStandardError $config.ErrLog `
        -WindowStyle Hidden `
        -PassThru

    Set-Content -LiteralPath $config.PidFile -Value $process.Id
    Start-Sleep -Seconds 2
    if (-not (Get-Process -Id $process.Id -ErrorAction SilentlyContinue)) {
        Remove-Item -LiteralPath $config.PidFile -Force -ErrorAction SilentlyContinue
        throw "$Name baslatilamadi. Log: $($config.ErrLog)"
    }

    Write-Host "$Name baslatildi (PID $($process.Id))."
}

function Stop-ManagedProcess {
    param([string]$Name)

    $process = Get-ManagedProcess $Name
    if (-not $process) {
        Write-Host "$Name calismiyor."
        return
    }

    & taskkill.exe /PID $process.Id /T /F *> $null
    Remove-Item -LiteralPath $services[$Name].PidFile -Force -ErrorAction SilentlyContinue
    Write-Host "$Name durduruldu."
}

function Start-Keycloak {
    Invoke-Compose -ComposeArgs @("up", "-d", "keycloak")
    Write-Host "Keycloak Docker container'i baslatildi."
}

function Stop-Keycloak {
    Invoke-Compose -ComposeArgs @("down")
}

function Show-Status {
    foreach ($name in @("backend", "frontend")) {
        $process = Get-ManagedProcess $name
        if ($process) {
            Write-Host ("{0,-10} calisiyor (PID {1})" -f $name, $process.Id)
        } else {
            Write-Host ("{0,-10} durdu" -f $name)
        }
    }

    Write-Host ""
    & cmd.exe /d /c "docker info >nul 2>&1"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "keycloak   bilinmiyor (Docker Desktop calismiyor)"
        return
    }
    Invoke-Compose -ComposeArgs @("ps")
}

function Show-Logs {
    param([string]$Name)

    if ($Name -eq "keycloak") {
        Invoke-Compose -ComposeArgs @("logs", "-f", "--tail=100", "keycloak")
        return
    }

    $names = if ($Name -eq "all") { @("backend", "frontend") } else { @($Name) }
    $logFiles = foreach ($item in $names) {
        $services[$item].OutLog
        $services[$item].ErrLog
    }
    $existingLogs = @($logFiles | Where-Object { Test-Path -LiteralPath $_ })
    if ($existingLogs.Count -eq 0) {
        Write-Host "Henuz log dosyasi yok. Once '.\local.ps1 up' calistirin."
        return
    }

    Get-Content -LiteralPath $existingLogs -Tail 100 -Wait
}

Import-DotEnv

switch ($Action) {
    { $_ -in "up", "start" } {
        Assert-Environment
        Assert-Command "docker" "Docker Desktop'i kurup calistirin."
        Assert-Command "mvn" "Maven'i kurun ve PATH'e ekleyin."
        Assert-Command "npm" "Node.js ve npm'i kurun."

        & docker compose version *> $null
        if ($LASTEXITCODE -ne 0) {
            throw "'docker compose' kullanilamiyor."
        }
        Assert-DockerRunning

        Start-Keycloak
        Start-ManagedProcess "backend" "mvn spring-boot:run" (Join-Path $root "backend")
        Start-ManagedProcess "frontend" "npm start" (Join-Path $root "frontend")

        Write-Host ""
        Write-Host "Local gelistirme ortami hazir:"
        Write-Host "  Frontend: http://localhost:4200"
        Write-Host "  Backend:  http://localhost:8080"
        Write-Host "  Keycloak: http://localhost:8081"
        Write-Host "  Database: .env icindeki uzak PostgreSQL"
        Write-Host ""
        Write-Host "Durum:  .\local.cmd status"
        Write-Host "Loglar: .\local.cmd logs backend"
        Write-Host "Kapat:  .\local.cmd down"
    }
    { $_ -in "down", "stop" } {
        Stop-ManagedProcess "frontend"
        Stop-ManagedProcess "backend"
        Stop-Keycloak
    }
    "restart" {
        Assert-Command "docker" "Docker Desktop'i kurup calistirin."
        Assert-DockerRunning
        Stop-ManagedProcess "frontend"
        Stop-ManagedProcess "backend"
        Stop-Keycloak
        Start-Keycloak
        Start-ManagedProcess "backend" "mvn spring-boot:run" (Join-Path $root "backend")
        Start-ManagedProcess "frontend" "npm start" (Join-Path $root "frontend")
    }
    { $_ -in "status", "ps" } {
        Assert-Command "docker" "Docker Desktop'i kurup calistirin."
        Show-Status
    }
    "logs" {
        Show-Logs $Service
    }
}
