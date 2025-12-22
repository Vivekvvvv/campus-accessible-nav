Param(
  [int]$BackendPort = 8081,
  [string]$BackendProfile = "test",
  [int]$WaitSeconds = 60
)

$ErrorActionPreference = "Stop"

Set-Location (Split-Path -Parent $PSScriptRoot)

$backendDir = Join-Path (Get-Location) "backend"
$frontendDir = Join-Path (Get-Location) "frontend"
$npmCmd = Get-Command "npm.cmd" -ErrorAction SilentlyContinue
if ($null -eq $npmCmd) {
  $npmCmd = Get-Command "npm" -ErrorAction SilentlyContinue
}
if ($null -eq $npmCmd) {
  throw "Cannot find npm executable in PATH"
}
$npmPath = $npmCmd.Path

Write-Host "[openapi] building backend jar (skip tests)..."
& mvn -f (Join-Path $backendDir "pom.xml") -s (Join-Path $backendDir ".mvn/settings.xml") -DskipTests package -B
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$jar = Get-ChildItem -Path (Join-Path $backendDir "target") -Filter "accessible-nav-backend-*.jar" |
  Sort-Object LastWriteTime -Descending |
  Select-Object -First 1

if (-not $jar) {
  throw "Cannot find backend jar under backend/target (accessible-nav-backend-*.jar)"
}

$logPath = Join-Path (Get-Location) "backend-openapi.log"
$errPath = Join-Path (Get-Location) "backend-openapi.err.log"
$openapiUrl = "http://localhost:$BackendPort/v3/api-docs"

Write-Host "[openapi] starting backend on port $BackendPort (profile=$BackendProfile)..."
$p = Start-Process -FilePath "java" -ArgumentList @(
  "-jar", $jar.FullName,
  "--server.port=$BackendPort",
  "--spring.profiles.active=$BackendProfile"
) -RedirectStandardOutput $logPath -RedirectStandardError $errPath -PassThru -NoNewWindow

try {
  $deadline = (Get-Date).AddSeconds($WaitSeconds)
  $ready = $false
  while ((Get-Date) -lt $deadline) {
    try {
      Invoke-WebRequest -Uri $openapiUrl -Method Get -UseBasicParsing -TimeoutSec 2 | Out-Null
      $ready = $true
      break
    } catch {
      Start-Sleep -Seconds 1
    }
  }

  if (-not $ready) {
    throw "Backend did not become ready at $openapiUrl within ${WaitSeconds}s. See $logPath and $errPath"
  }

  Write-Host "[openapi] generating frontend types from $openapiUrl ..."
  $env:OPENAPI_URL = $openapiUrl
  & $npmPath --prefix $frontendDir run api:gen
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

  Write-Host "[openapi] done -> frontend/src/api/schema.d.ts"
} finally {
  if ($p -and -not $p.HasExited) {
    Write-Host "[openapi] stopping backend (pid=$($p.Id))..."
    try { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue } catch {}
  }
}
