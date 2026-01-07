# PSScriptAnalyzer suppression for development script
[Diagnostics.CodeAnalysis.SuppressMessageAttribute('PSAvoidUsingPlainTextForPassword', '')]
[CmdletBinding()]
param(
  [int]$FrontendPort = 5173,
  [int]$BackendPort  = 8081,

  # Spring profile(s): e.g. "dev", "prod", "h2"
  [string]$BackendProfile = "dev",

  # DB Config (Defaults to Localhost Postgres)
  [string]$DbHost    = "localhost",
  [int]$DbPort       = 5432,
  [string]$DbName    = "accessible_nav",
  [string]$DbUser    = "postgres",
  [string]$DbPassword= "postgres",
  [string]$DbSchema  = "public",

  # Log format: json|plain (maps to Spring profile json-logging)
  [ValidateSet('json', 'plain')]
  [string]$LogFormat = "",

  [switch]$ForceKill
)

$ErrorActionPreference = "Stop"

function Info([string]$msg) { Write-Host "[start] $msg" -ForegroundColor Cyan }
function Warn([string]$msg) { Write-Host "[start] $msg" -ForegroundColor Yellow }

function Require([string]$name) {
  if (-not (Get-Command $name -ErrorAction SilentlyContinue)) {
    throw "Missing command: $name (install it and ensure it's in PATH)."
  }
}

function Get-EnvValueFromFile([string]$path, [string]$key) {
  if (-not (Test-Path $path)) { return $null }
  $escaped = [regex]::Escape($key)
  $pattern = "^\s*${escaped}\s*=\s*(.*)\s*$"
  foreach ($line in Get-Content -Path $path) {
    if ($line -match '^\s*#') { continue }
    if ($line -match $pattern) {
      $value = $Matches[1].Trim()
      if ($value.StartsWith('"') -and $value.EndsWith('"')) {
        return $value.Substring(1, $value.Length - 2)
      }
      if ($value.StartsWith("'") -and $value.EndsWith("'")) {
        return $value.Substring(1, $value.Length - 2)
      }
      return $value
    }
  }
  return $null
}

function Get-ListeningPid([int]$port) {
  try {
    $c = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction Stop | Select-Object -First 1
    return $c.OwningProcess
  } catch {
    return $null
  }
}

function Assert-PortFree([int]$port, [string]$name) {
  $processId = Get-ListeningPid $port
  if ($null -ne $processId) {
    if ($ForceKill) {
      Warn "$name port $port is used by PID=$processId. Stopping it (ForceKill)."
      Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
      Start-Sleep -Milliseconds 600
    } else {
      throw "$name port $port is already in use by PID=$processId. Close it or re-run with -ForceKill."
    }
  }
}

function Wait-HttpOk([string]$url, [int]$timeoutSec = 60) {
  $deadline = (Get-Date).AddSeconds($timeoutSec)
  while ((Get-Date) -lt $deadline) {
    try {
      $r = Invoke-WebRequest -UseBasicParsing -TimeoutSec 5 $url
      if ($r.StatusCode -ge 200 -and $r.StatusCode -lt 500) { return $true }
    } catch {
      # In local dev, /actuator/health may return 503 when optional deps (e.g. Redis) are down.
      # As long as HTTP endpoint is reachable, backend process is considered started.
      if ($null -ne $_.Exception.Response) {
        try {
          $status = [int]$_.Exception.Response.StatusCode
          if ($status -ge 200 -and $status -lt 600) { return $true }
        } catch {
          # ignore and continue waiting
        }
      }
      Start-Sleep -Milliseconds 700
    }
  }
  return $false
}

$repoRoot = Split-Path -Parent $PSCommandPath
$runDir = Join-Path $repoRoot ".run"
$logDir = Join-Path $repoRoot ".logs"
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

# Optional: load defaults from repo-local .env.
$envFile = Join-Path $repoRoot ".env"
if (Test-Path $envFile) {
  if (-not $PSBoundParameters.ContainsKey('BackendProfile')) {
    $v = Get-EnvValueFromFile $envFile "SPRING_PROFILES_ACTIVE"
    if ($v) { $BackendProfile = $v }
  }
  if (-not $PSBoundParameters.ContainsKey('FrontendPort')) {
    $v = Get-EnvValueFromFile $envFile "FRONTEND_PORT"
    if ($v) { $FrontendPort = [int]$v }
  }
  if (-not $PSBoundParameters.ContainsKey('BackendPort')) {
    $v = Get-EnvValueFromFile $envFile "BACKEND_PORT"
    if ($v) { $BackendPort = [int]$v }
  }
  if (-not $PSBoundParameters.ContainsKey('DbHost')) {
    $v = Get-EnvValueFromFile $envFile "DB_HOST"
    if ($v) { $DbHost = $v }
  }
  if (-not $PSBoundParameters.ContainsKey('DbPort')) {
    $v = Get-EnvValueFromFile $envFile "DB_PORT"
    if ($v) { $DbPort = [int]$v }
  }
  if (-not $PSBoundParameters.ContainsKey('DbName')) {
    $v = Get-EnvValueFromFile $envFile "DB_NAME"
    if ($v) { $DbName = $v }
  }
  if (-not $PSBoundParameters.ContainsKey('DbSchema')) {
    $v = Get-EnvValueFromFile $envFile "DB_SCHEMA"
    if ($v) { $DbSchema = $v }
  }
  if (-not $PSBoundParameters.ContainsKey('DbUser')) {
    $v = Get-EnvValueFromFile $envFile "DB_USER"
    if ($v) { $DbUser = $v }
  }
  if (-not $PSBoundParameters.ContainsKey('DbUser')) {
    $v = Get-EnvValueFromFile $envFile "DB_USERNAME"
    if ($v) { $DbUser = $v }
  }
  if (-not $PSBoundParameters.ContainsKey('DbPassword')) {
    $v = Get-EnvValueFromFile $envFile "DB_PASSWORD"
    if ($v) { $DbPassword = $v }
  }
  if (-not $PSBoundParameters.ContainsKey('LogFormat')) {
    $v = Get-EnvValueFromFile $envFile "LOG_FORMAT"
    if ($v) { $LogFormat = $v }
  }

  $v = Get-EnvValueFromFile $envFile "MANAGEMENT_HEALTH_REDIS_ENABLED"
  if ($v) { $env:MANAGEMENT_HEALTH_REDIS_ENABLED = $v }

  $v = Get-EnvValueFromFile $envFile "MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE"
  if ($v) { $env:MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE = $v }

  # Backward-compatible: allow legacy POSTGRES_* defaults if DB_* is not set.
  if (-not $PSBoundParameters.ContainsKey('DbPort')) {
    $v = Get-EnvValueFromFile $envFile "POSTGRES_PORT"
    if ($v) { $DbPort = [int]$v }
  }
  if (-not $PSBoundParameters.ContainsKey('DbName')) {
    $v = Get-EnvValueFromFile $envFile "POSTGRES_DB"
    if ($v) { $DbName = $v }
  }
  if (-not $PSBoundParameters.ContainsKey('DbUser')) {
    $v = Get-EnvValueFromFile $envFile "POSTGRES_USER"
    if ($v) { $DbUser = $v }
  }
  if (-not $PSBoundParameters.ContainsKey('DbPassword')) {
    $v = Get-EnvValueFromFile $envFile "POSTGRES_PASSWORD"
    if ($v) { $DbPassword = $v }
  }
}

Info "Repo: $repoRoot"
Info "Ports: frontend=$FrontendPort backend=$BackendPort"
Info "Backend profile(s): $BackendProfile"
Info "DB: ${DbHost}:${DbPort} db=$DbName schema=$DbSchema"

Require "java"
Require "mvn"
Require "node"
$npmCmd = Get-Command "npm.cmd" -ErrorAction SilentlyContinue
if ($null -eq $npmCmd) {
  $npmCmd = Get-Command "npm" -ErrorAction SilentlyContinue
}
if ($null -eq $npmCmd) {
  throw "Missing command: npm (install Node.js and ensure npm is in PATH)."
}
$npmPath = $npmCmd.Path

function Get-JavaMajorVersion {
  try {
    $out = (& java -version 2>&1) | Select-Object -First 1
    if (-not $out) { return $null }
    # Examples:
    # - openjdk version "17.0.16" 2025-07-15 LTS
    # - java version "1.8.0_321"
    if ($out -match 'version\s+"(?<ver>[^"]+)"') {
      $ver = $Matches['ver']
      if ($ver -match '^1\.(\d+)\.') { return [int]$Matches[1] }
      if ($ver -match '^(\\d+)') { return [int]$Matches[1] }
    }
    return $null
  } catch {
    return $null
  }
}

$javaMajor = Get-JavaMajorVersion
if ($null -eq $javaMajor) {
  Warn "Could not detect Java version from 'java -version'. Continuing..."
} elseif ($javaMajor -lt 21) {
  throw "Java 21+ is required (project baseline). Current detected major version: $javaMajor. Fix JAVA_HOME/PATH and retry."
}

Assert-PortFree -port $BackendPort -name "backend"
Assert-PortFree -port $FrontendPort -name "frontend"

# Build backend jar
Info "Building backend (mvn package -DskipTests)..."
$backendDir = Join-Path $repoRoot "backend"
Push-Location $backendDir
try {
  & mvn -q -s .mvn\settings.xml -DskipTests package
} finally {
  Pop-Location
}

$jar = Get-ChildItem -Path (Join-Path $backendDir "target") -Filter "accessible-nav-backend-*.jar" |
  Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar) { throw "Backend jar not found under backend/target." }

# Start backend
Info "Starting backend: $($jar.Name)"
$backendPidFile = Join-Path $runDir "backend.pid"
$backendOutLog = Join-Path $logDir "backend.out.log"
$backendErrLog = Join-Path $logDir "backend.err.log"

if ($BackendProfile -and $BackendProfile.Trim().Length -gt 0) {
  $env:SPRING_PROFILES_ACTIVE = $BackendProfile
}

$profileTokens = @()
if ($BackendProfile) {
  $profileTokens = @($BackendProfile.Split(',') | ForEach-Object { $_.Trim().ToLowerInvariant() } | Where-Object { $_ -ne "" })
}

if ($LogFormat -and $LogFormat.Trim().Length -gt 0) {
  $fmt = $LogFormat.Trim().ToLowerInvariant()
  if ($fmt -eq 'json') {
    if (-not ($profileTokens -contains 'json-logging')) {
      $profileTokens += 'json-logging'
    }
  } elseif ($fmt -eq 'plain') {
    $profileTokens = @($profileTokens | Where-Object { $_ -ne 'json-logging' })
  }
}

$profileTokens = @($profileTokens | Select-Object -Unique)
if ($profileTokens.Count -gt 0) {
  $BackendProfile = ($profileTokens -join ',')
  $env:SPRING_PROFILES_ACTIVE = $BackendProfile
}

$useH2 = $profileTokens -contains "h2" -or $profileTokens -contains "test"
if (-not $useH2) {
  Info "Mode: PostgreSQL (PostGIS)"
  # Configure Environment for Postgres
  $schema = if ($null -eq $DbSchema) { "" } else { [string]$DbSchema }
  $schema = $schema.Trim()
  if (-not $schema) { $schema = "public" }
  $currentSchema = if ($schema -and $schema -ne "public") { "$schema,public" } else { "public" }
  $env:DB_HOST = $DbHost
  $env:DB_PORT = "$DbPort"
  $env:DB_NAME = $DbName
  $env:DB_SCHEMA = $schema
  $env:DB_USER = $DbUser
  $env:DB_PASSWORD = $DbPassword
  $env:DB_URL      = "jdbc:postgresql://${DbHost}:${DbPort}/${DbName}?currentSchema=$currentSchema"
  $env:DB_USERNAME = $DbUser
  $env:DB_PASSWORD = $DbPassword
  $env:SPRING_DATASOURCE_URL = $env:DB_URL
  $env:SPRING_DATASOURCE_USERNAME = $DbUser
  $env:SPRING_DATASOURCE_PASSWORD = $DbPassword

  # Check if DB is reachable (Simple TCP check)
  Info "Checking DB Connection (${DbHost}:${DbPort})..."
  try {
      $tcp = New-Object System.Net.Sockets.TcpClient
      $connect = $tcp.BeginConnect($DbHost, $DbPort, $null, $null)
      $success = $connect.AsyncWaitHandle.WaitOne(2000, $false)
      if (-not $success) {
           Throw "Timeout connecting"
      }
      $tcp.Close()
  } catch {
      Warn "Warning: Could not connect to PostgreSQL at ${DbHost}:${DbPort}"
      Warn "Please ensure PostgreSQL/PostGIS is running and reachable."
      Start-Sleep -Seconds 3
  }
} else {
  Info "Mode: H2 (no PostgreSQL)"
}

$env:CORS_ALLOWED_ORIGINS = "http://localhost:$FrontendPort"
if (-not $env:MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE -or [string]::IsNullOrWhiteSpace($env:MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE)) {
  $env:MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE = "health,info,prometheus"
}

$backendProc = Start-Process -FilePath "java" -ArgumentList @(
  "-jar", $jar.FullName,
  "--server.port=$BackendPort"
) -WorkingDirectory $backendDir -PassThru -RedirectStandardOutput $backendOutLog -RedirectStandardError $backendErrLog

Set-Content -Path $backendPidFile -Value $backendProc.Id -Encoding ASCII

$backendLivenessUrl = "http://localhost:$BackendPort/actuator/health/liveness"
$backendHealthUrl = "http://localhost:$BackendPort/actuator/health"
Info "Waiting backend liveness: $backendLivenessUrl"
if (-not (Wait-HttpOk -url $backendLivenessUrl -timeoutSec 90)) {
  Info "Liveness endpoint not ready, fallback to health: $backendHealthUrl"
  if (-not (Wait-HttpOk -url $backendHealthUrl -timeoutSec 30)) {
    throw "Backend not ready. Check logs: $backendOutLog , $backendErrLog"
  }
}

# Frontend deps
$frontendDir = Join-Path $repoRoot "frontend"
if (-not (Test-Path (Join-Path $frontendDir "node_modules"))) {
  Info "Installing frontend deps (npm install)..."
  Push-Location $frontendDir
  try { & $npmPath install } finally { Pop-Location }
}

# Start frontend
Info "Starting frontend (vite dev)..."
$frontendPidFile = Join-Path $runDir "frontend.pid"
$frontendOutLog = Join-Path $logDir "frontend.out.log"
$frontendErrLog = Join-Path $logDir "frontend.err.log"
$env:VITE_API_BASE_URL = "http://localhost:$BackendPort"

$frontendProc = Start-Process -FilePath $npmPath -ArgumentList @(
  "run", "dev", "--", "--port", "$FrontendPort", "--strictPort"
) -WorkingDirectory $frontendDir -PassThru -RedirectStandardOutput $frontendOutLog -RedirectStandardError $frontendErrLog

Set-Content -Path $frontendPidFile -Value $frontendProc.Id -Encoding ASCII

Info "Waiting frontend: http://localhost:$FrontendPort/"
if (-not (Wait-HttpOk -url "http://localhost:$FrontendPort/" -timeoutSec 60)) {
  throw "Frontend not ready. Check logs: $frontendOutLog , $frontendErrLog"
}

$ui = "http://localhost:$FrontendPort/"
$backendBase = "http://localhost:$BackendPort"
Info "READY: $ui"
Info "Swagger: $backendBase/swagger-ui/index.html"
Info "Health:  $backendBase/actuator/health"
Info "Metrics: $backendBase/actuator/prometheus"

Start-Process $ui | Out-Null
Info "Browser opened. To stop: .\\stop.ps1"
