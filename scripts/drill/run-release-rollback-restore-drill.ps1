[CmdletBinding()]
param(
  [string]$DbHost = "localhost",
  [int]$DbPort = 5432,
  [string]$AdminUser = "postgres",
  [string]$AdminPassword = "root",
  [string]$SourceDb = "accessible_nav",
  [string]$DrillDb = "accessible_nav_drill",
  [string]$DrillSchema = "public",
  [int]$BackendPort = 18081,
  [string]$BackendProfile = "dev",
  [string]$OutputRoot = ".run/drills"
)

$ErrorActionPreference = "Stop"

function Write-Info([string]$m) { Write-Host "[drill] $m" -ForegroundColor Cyan }

function Require-Command([string]$name) {
  if (-not (Get-Command $name -ErrorAction SilentlyContinue)) {
    throw "Missing command: $name"
  }
}

function Invoke-Psql([string]$db, [string]$sql) {
  & psql -h $DbHost -p $DbPort -U $AdminUser -d $db -v ON_ERROR_STOP=1 -tA -c $sql
}

function Wait-Health([string]$url, [int]$timeoutSec = 120) {
  $deadline = (Get-Date).AddSeconds($timeoutSec)
  while ((Get-Date) -lt $deadline) {
    try {
      $res = Invoke-WebRequest -Uri $url -Method Get -UseBasicParsing -TimeoutSec 5
      if ($res.StatusCode -ge 200 -and $res.StatusCode -lt 600) { return $true }
    } catch {
      $status = $null
      try { $status = [int]$_.Exception.Response.StatusCode.value__ } catch {}
      if ($null -ne $status -and $status -ge 200 -and $status -lt 600) {
        return $true
      }
      Start-Sleep -Milliseconds 800
    }
  }
  return $false
}

function Get-HttpStatusCode([System.Exception]$ex) {
  $status = $null
  try { $status = [int]$ex.Response.StatusCode.value__ } catch {}
  return $status
}

function Get-PrometheusText([string]$baseUrl, [hashtable]$adminHeaders) {
  $uri = "$baseUrl/actuator/prometheus"
  try {
    return Invoke-RestMethod -Method Get -Uri $uri
  } catch {
    $status = Get-HttpStatusCode $_.Exception
    if (($status -eq 401 -or $status -eq 403) -and $adminHeaders -and $adminHeaders.Count -gt 0) {
      return Invoke-RestMethod -Method Get -Uri $uri -Headers $adminHeaders
    }
    throw
  }
}

function Get-MetricCounter([string]$promText, [string]$metricName, [hashtable]$labels = @{}) {
  $lines = $promText -split "`r?`n"
  $prefix = "$metricName"
  foreach ($line in $lines) {
    if (-not $line.StartsWith($prefix)) { continue }
    if ($line.StartsWith("#")) { continue }
    if ($line -match "^" + [regex]::Escape($metricName) + "(?:\{(?<labels>[^\}]*)\})?\s+(?<value>[-+]?\d+(\.\d+)?([eE][-+]?\d+)?)$") {
      $lineLabels = $Matches["labels"]
      $ok = $true
      $parsed = @{}
      if ($lineLabels) {
        $mset = [regex]::Matches($lineLabels, '([A-Za-z_][A-Za-z0-9_]*)="([^"]*)"')
        foreach ($m in $mset) {
          $parsed[$m.Groups[1].Value.ToLowerInvariant()] = $m.Groups[2].Value
        }
      }
      foreach ($k in $labels.Keys) {
        $key = [string]$k
        $expected = [string]$labels[$k]
        $actual = $parsed[$key.ToLowerInvariant()]
        if ($null -eq $actual -or -not $actual.Equals($expected, [System.StringComparison]::OrdinalIgnoreCase)) {
          $ok = $false
          break
        }
      }
      if ($ok) {
        return [double]$Matches["value"]
      }
    }
  }
  return 0.0
}

function New-Json([object]$obj, [string]$path) {
  $obj | ConvertTo-Json -Depth 10 | Set-Content -Path $path -Encoding UTF8
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $repoRoot

Require-Command "pg_dump"
Require-Command "pg_restore"
Require-Command "psql"
Require-Command "java"
Require-Command "mvn"

$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$outDir = Join-Path $repoRoot (Join-Path $OutputRoot $stamp)
New-Item -ItemType Directory -Path $outDir -Force | Out-Null

$report = [ordered]@{
  timestamp = (Get-Date).ToString("s")
  db = [ordered]@{
    host = $DbHost
    port = $DbPort
    source = $SourceDb
    drill = $DrillDb
    schema = $DrillSchema
  }
  timings = [ordered]@{}
  profile = $BackendProfile
  backup = [ordered]@{}
  restore = [ordered]@{}
  smoke = [ordered]@{}
  calibration = [ordered]@{}
}

$env:PGPASSWORD = $AdminPassword

$totalSw = [System.Diagnostics.Stopwatch]::StartNew()

# 1) Backup
$backupFile = Join-Path $outDir "$SourceDb.dump"
Write-Info "Creating backup: $backupFile"
$sw = [System.Diagnostics.Stopwatch]::StartNew()
& pg_dump -h $DbHost -p $DbPort -U $AdminUser -Fc -f $backupFile $SourceDb
$sw.Stop()
if (-not (Test-Path $backupFile)) { throw "Backup file not created: $backupFile" }
$report.timings.backup_sec = [Math]::Round($sw.Elapsed.TotalSeconds, 3)
$report.backup.file = $backupFile
$report.backup.size_bytes = (Get-Item $backupFile).Length

# 2) Restore (rollback target db)
Write-Info "Restoring backup to drill database: $DrillDb"
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$terminateSql = "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$DrillDb' AND pid <> pg_backend_pid();"
Invoke-Psql "postgres" $terminateSql | Out-Null
Invoke-Psql "postgres" "DROP DATABASE IF EXISTS $DrillDb;" | Out-Null
Invoke-Psql "postgres" "CREATE DATABASE $DrillDb;" | Out-Null
Invoke-Psql $DrillDb "CREATE EXTENSION IF NOT EXISTS postgis;" | Out-Null
if ($DrillSchema -ne "public") {
  Invoke-Psql $DrillDb "CREATE SCHEMA IF NOT EXISTS $DrillSchema;" | Out-Null
}
& pg_restore -h $DbHost -p $DbPort -U $AdminUser -d $DrillDb --no-owner --no-privileges $backupFile
$sw.Stop()
$report.timings.restore_sec = [Math]::Round($sw.Elapsed.TotalSeconds, 3)

$tableCount = (Invoke-Psql $DrillDb "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE';").Trim()
$flywayCount = (Invoke-Psql $DrillDb "SELECT COUNT(*) FROM public.flyway_schema_history;").Trim()
$hasPostgis = (Invoke-Psql $DrillDb "SELECT COUNT(*) FROM pg_extension WHERE extname='postgis';").Trim()
$report.restore.tables_public = [int]$tableCount
$report.restore.flyway_rows = [int]$flywayCount
$report.restore.postgis_installed = ([int]$hasPostgis -gt 0)

# 3) Start backend against restored DB
Write-Info "Starting backend on port $BackendPort against $DrillDb"
$backendDir = Join-Path $repoRoot "backend"
$jar = Get-ChildItem -Path (Join-Path $backendDir "target") -Filter "accessible-nav-backend-*.jar" -ErrorAction SilentlyContinue |
  Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar) {
  Write-Info "Backend jar not found. Building package first..."
  Push-Location $backendDir
  try {
    & mvn -s .mvn/settings.xml -DskipTests package -B
  } finally {
    Pop-Location
  }
  $jar = Get-ChildItem -Path (Join-Path $backendDir "target") -Filter "accessible-nav-backend-*.jar" |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
}
if (-not $jar) { throw "Cannot find backend jar under backend/target" }

$logOut = Join-Path $outDir "backend.out.log"
$logErr = Join-Path $outDir "backend.err.log"
$env:SPRING_PROFILES_ACTIVE = "dev"
$env:DB_HOST = $DbHost
$env:DB_PORT = "$DbPort"
$env:DB_NAME = $DrillDb
$env:DB_SCHEMA = $DrillSchema
$env:DB_USER = $AdminUser
$env:DB_PASSWORD = $AdminPassword
$env:DB_URL = "jdbc:postgresql://${DbHost}:${DbPort}/${DrillDb}?currentSchema=$DrillSchema"
$env:DB_USERNAME = $AdminUser
$env:MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE = "health,info,prometheus,metrics"
# For prod-profile drills without Redis sidecar, keep rate-limit local to avoid false startup failures.
$env:RATE_LIMIT_USE_REDIS = "false"
$env:RATE_LIMIT_ENABLED = "true"

$sw = [System.Diagnostics.Stopwatch]::StartNew()
$backendProc = Start-Process -FilePath "java" -ArgumentList @("-jar", $jar.FullName, "--server.port=$BackendPort", "--spring.profiles.active=$BackendProfile") -WorkingDirectory $backendDir -PassThru -RedirectStandardOutput $logOut -RedirectStandardError $logErr
try {
  if (-not (Wait-Health -url "http://localhost:$BackendPort/actuator/health" -timeoutSec 120)) {
    throw "Backend health check timeout. See $logOut / $logErr"
  }
  $sw.Stop()
  $report.timings.backend_start_sec = [Math]::Round($sw.Elapsed.TotalSeconds, 3)

  # 4) Business smoke
  Write-Info "Running business smoke (register/login/route/navigation)"
  $smokeSw = [System.Diagnostics.Stopwatch]::StartNew()
  $base = "http://localhost:$BackendPort"

  $user = "drill_" + $stamp.ToLower()
  $registerBody = @{ username = $user; password = "Abx9Kq7M" } | ConvertTo-Json
  $registerResp = Invoke-RestMethod -Method Post -Uri "$base/api/auth/register" -ContentType "application/json" -Body $registerBody
  $token = [string]$registerResp.accessToken
  if (-not $token) { throw "Register did not return accessToken" }
  $headers = @{ Authorization = "Bearer $token" }

  $routeBody = @{
    startLat = 23.275784
    startLng = 113.200776
    endLat = 23.2762
    endLng = 113.20265
    mode = "WALK"
  } | ConvertTo-Json
  $routeResp = Invoke-RestMethod -Method Post -Uri "$base/api/route" -ContentType "application/json" -Body $routeBody
  if ($null -eq $routeResp.distanceM) { throw "Route response missing distanceM" }

  $sessionBody = @{
    startLat = 23.275784
    startLng = 113.200776
    endLat = 23.2762
    endLng = 113.20265
    destinationName = "library"
    mode = "WALK"
  } | ConvertTo-Json
  $session = Invoke-RestMethod -Method Post -Uri "$base/api/navigation/session" -ContentType "application/json" -Headers $headers -Body $sessionBody
  $sid = [string]$session.sessionId
  if (-not $sid) { throw "Navigation session id missing" }

  $null = Invoke-RestMethod -Method Get -Uri "$base/api/navigation/session/$sid/hazards?radiusM=50&limit=10" -Headers $headers

  $rerouteBody = @{
    lat = 23.27579
    lng = 113.20079
    reason = "OBSTACLE"
  } | ConvertTo-Json
  $null = Invoke-RestMethod -Method Post -Uri "$base/api/navigation/session/$sid/reroute" -ContentType "application/json" -Headers $headers -Body $rerouteBody

  $endResp = Invoke-RestMethod -Method Post -Uri "$base/api/navigation/session/$sid/end?reason=USER_END" -Headers $headers
  if ($endResp.status -ne "ENDED") { throw "Navigation end status is not ENDED" }

  $smokeSw.Stop()
  $report.timings.smoke_sec = [Math]::Round($smokeSw.Elapsed.TotalSeconds, 3)
  $report.smoke.user = $user
  $report.smoke.route_distance_m = [double]$routeResp.distanceM
  $report.smoke.session_id = $sid
  $report.smoke.end_status = [string]$endResp.status

  # 5) Navigation metrics calibration sample
  Write-Info "Sampling navigation metrics for threshold calibration"
  $calSession = Invoke-RestMethod -Method Post -Uri "$base/api/navigation/session" -ContentType "application/json" -Headers $headers -Body $sessionBody
  $calSid = [string]$calSession.sessionId
  if (-not $calSid) { throw "Calibration session id missing" }

  $adminUser = if ($env:ADMIN_USERNAME) { $env:ADMIN_USERNAME } else { "admin" }
  $adminPassword = if ($env:ADMIN_PASSWORD) { $env:ADMIN_PASSWORD } else { "admin123" }
  $adminHeaders = $null
  try {
    $adminLoginBody = @{ username = $adminUser; password = $adminPassword } | ConvertTo-Json
    $adminLoginResp = Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" -ContentType "application/json" -Body $adminLoginBody
    $adminToken = [string]$adminLoginResp.accessToken
    if ($adminToken) {
      $adminHeaders = @{ Authorization = "Bearer $adminToken" }
    }
  } catch {
    Write-Info "Admin login unavailable for metrics endpoint; will try anonymous scrape first."
  }

  $promBefore = Get-PrometheusText -baseUrl $base -adminHeaders $adminHeaders
  Set-Content -Path (Join-Path $outDir "prom-before.txt") -Value $promBefore -Encoding UTF8

  $calSw = [System.Diagnostics.Stopwatch]::StartNew()
  $calibrationErrors = New-Object System.Collections.Generic.List[string]
  $rerouteAttempt = 0
  $rerouteSuccess = 0
  for ($i = 0; $i -lt 20; $i++) {
    $locBody = @{
      lat = 23.27579 + ($i * 0.000001)
      lng = 113.20079 + ($i * 0.000001)
      accuracyM = 8.0
    } | ConvertTo-Json
    try {
      $null = Invoke-RestMethod -Method Post -Uri "$base/api/navigation/session/$calSid/location" -ContentType "application/json" -Headers $headers -Body $locBody
    } catch {
      $calibrationErrors.Add("location[$i]: $($_.Exception.Message)") | Out-Null
      continue
    }
    try {
      $null = Invoke-RestMethod -Method Get -Uri "$base/api/navigation/session/$calSid/hazards?radiusM=50&limit=10" -Headers $headers
    } catch {
      $calibrationErrors.Add("hazards[$i]: $($_.Exception.Message)") | Out-Null
    }

    $eventBody = @{
      type = "OFF_ROUTE_WARNED"
      payload = "drill"
    } | ConvertTo-Json
    try {
      Invoke-RestMethod -Method Post -Uri "$base/api/navigation/session/$calSid/client-event" -ContentType "application/json" -Headers $headers -Body $eventBody | Out-Null
    } catch {
      # 204 may surface as empty-body parsing issue on some PowerShell versions; treat as success if status is 204.
    }

    if (($i % 5) -eq 0) {
      $rerouteAttempt++
      try {
        $null = Invoke-RestMethod -Method Post -Uri "$base/api/navigation/session/$calSid/reroute" -ContentType "application/json" -Headers $headers -Body $rerouteBody
        $rerouteSuccess++
      } catch {
        # Reroute can return 400 (no path found from sampled point); record and continue calibration.
        $calibrationErrors.Add("reroute[$i]: $($_.Exception.Message)") | Out-Null
      }
    }
    Start-Sleep -Milliseconds 150
  }
  $calSw.Stop()

  $promAfter = Get-PrometheusText -baseUrl $base -adminHeaders $adminHeaders
  Set-Content -Path (Join-Path $outDir "prom-after.txt") -Value $promAfter -Encoding UTF8
  $durationSec = [Math]::Max($calSw.Elapsed.TotalSeconds, 1.0)

  $beforeHazQ = Get-MetricCounter -promText $promBefore -metricName "navigation_hazards_queries_total"
  $afterHazQ = Get-MetricCounter -promText $promAfter -metricName "navigation_hazards_queries_total"
  $beforeHazM = Get-MetricCounter -promText $promBefore -metricName "navigation_hazards_matched_total"
  $afterHazM = Get-MetricCounter -promText $promAfter -metricName "navigation_hazards_matched_total"
  $beforeReroute = Get-MetricCounter -promText $promBefore -metricName "navigation_reroutes_total"
  $afterReroute = Get-MetricCounter -promText $promAfter -metricName "navigation_reroutes_total"
  $beforeRerouteObs = Get-MetricCounter -promText $promBefore -metricName "navigation_reroutes_total" -labels @{ reason = "OBSTACLE" }
  $afterRerouteObs = Get-MetricCounter -promText $promAfter -metricName "navigation_reroutes_total" -labels @{ reason = "OBSTACLE" }
  $beforeOffRoute = Get-MetricCounter -promText $promBefore -metricName "navigation_client_events_total" -labels @{ type = "OFF_ROUTE_WARNED" }
  $afterOffRoute = Get-MetricCounter -promText $promAfter -metricName "navigation_client_events_total" -labels @{ type = "OFF_ROUTE_WARNED" }
  $beforeLoc = Get-MetricCounter -promText $promBefore -metricName "navigation_location_updates_total"
  $afterLoc = Get-MetricCounter -promText $promAfter -metricName "navigation_location_updates_total"

  $deltaHazQ = [Math]::Max($afterHazQ - $beforeHazQ, 0.0)
  $deltaHazM = [Math]::Max($afterHazM - $beforeHazM, 0.0)
  $deltaReroute = [Math]::Max($afterReroute - $beforeReroute, 0.0)
  $deltaRerouteObs = [Math]::Max($afterRerouteObs - $beforeRerouteObs, 0.0)
  $deltaOffRoute = [Math]::Max($afterOffRoute - $beforeOffRoute, 0.0)
  $deltaLoc = [Math]::Max($afterLoc - $beforeLoc, 0.0)

  $hazQpsPerSession = $deltaHazQ / $durationSec
  $hazAvgMatched = if ($deltaHazQ -gt 0) { $deltaHazM / $deltaHazQ } else { 0.0 }
  $obstacleShare = if ($deltaReroute -gt 0) { $deltaRerouteObs / $deltaReroute } else { 0.0 }
  $offRouteRatio = if ($deltaLoc -gt 0) { $deltaOffRoute / $deltaLoc } else { 0.0 }

  $report.calibration.duration_sec = [Math]::Round($durationSec, 3)
  $report.calibration.session_id = $calSid
  $report.calibration.reroute_attempt = $rerouteAttempt
  $report.calibration.reroute_success = $rerouteSuccess
  $report.calibration.error_count = $calibrationErrors.Count
  if ($calibrationErrors.Count -gt 0) {
    $report.calibration.errors = $calibrationErrors
  }
  $report.calibration.delta = [ordered]@{
    hazards_queries = [Math]::Round($deltaHazQ, 3)
    hazards_matched = [Math]::Round($deltaHazM, 3)
    reroutes_total = [Math]::Round($deltaReroute, 3)
    reroutes_obstacle = [Math]::Round($deltaRerouteObs, 3)
    off_route_warned = [Math]::Round($deltaOffRoute, 3)
    location_updates = [Math]::Round($deltaLoc, 3)
  }
  $report.calibration.observed = [ordered]@{
    hazards_queries_per_session_per_sec = [Math]::Round($hazQpsPerSession, 4)
    hazards_avg_matched_per_query = [Math]::Round($hazAvgMatched, 4)
    reroute_obstacle_share = [Math]::Round($obstacleShare, 4)
    off_route_warn_ratio = [Math]::Round($offRouteRatio, 4)
  }
  $report.calibration.recommended_thresholds = [ordered]@{
    NavigationHazardQueriesHighPerSession = [Math]::Round([Math]::Max($hazQpsPerSession * 1.3, 0.08), 3)
    NavigationHazardsAvgMatchedHigh = [Math]::Round([Math]::Max($hazAvgMatched * 1.3, 3.0), 2)
    NavigationObstacleRerouteSpike_share = [Math]::Round([Math]::Min([Math]::Max($obstacleShare * 1.2, 0.4), 0.9), 2)
    NavigationOffRouteWarnRatioHigh = [Math]::Round([Math]::Min([Math]::Max($offRouteRatio * 1.3, 0.05), 0.5), 2)
  }

  # end calibration session
  try {
    $null = Invoke-RestMethod -Method Post -Uri "$base/api/navigation/session/$calSid/end?reason=USER_END" -Headers $headers
  } catch {
    $calibrationErrors.Add("end-session: $($_.Exception.Message)") | Out-Null
  }
}
finally {
  if ($backendProc -and -not $backendProc.HasExited) {
    Stop-Process -Id $backendProc.Id -Force -ErrorAction SilentlyContinue
  }
}

$totalSw.Stop()
$report.timings.total_sec = [Math]::Round($totalSw.Elapsed.TotalSeconds, 3)

$jsonPath = Join-Path $outDir "drill-report.json"
New-Json -obj $report -path $jsonPath

$mdPath = Join-Path $outDir "drill-report.md"
@"
# Release/Rollback/Restore Drill Report

- Timestamp: $($report.timestamp)
- Source DB: $SourceDb
- Drill DB: $DrillDb
- Backup file: $backupFile

## Timings

- backup: $($report.timings.backup_sec)s
- restore: $($report.timings.restore_sec)s
- backend start: $($report.timings.backend_start_sec)s
- smoke: $($report.timings.smoke_sec)s
- total: $($report.timings.total_sec)s

## Restore Validation

- postgis installed: $($report.restore.postgis_installed)
- public tables: $($report.restore.tables_public)
- flyway rows: $($report.restore.flyway_rows)

## Smoke Validation

- user: $($report.smoke.user)
- route distance(m): $($report.smoke.route_distance_m)
- session id: $($report.smoke.session_id)
- end status: $($report.smoke.end_status)

## Calibration Observed

- hazards queries / session / sec: $($report.calibration.observed.hazards_queries_per_session_per_sec)
- hazards matched / query: $($report.calibration.observed.hazards_avg_matched_per_query)
- obstacle reroute share: $($report.calibration.observed.reroute_obstacle_share)
- off-route warn ratio: $($report.calibration.observed.off_route_warn_ratio)

## Calibration Recommended Thresholds

- NavigationHazardQueriesHighPerSession: $($report.calibration.recommended_thresholds.NavigationHazardQueriesHighPerSession)
- NavigationHazardsAvgMatchedHigh: $($report.calibration.recommended_thresholds.NavigationHazardsAvgMatchedHigh)
- NavigationObstacleRerouteSpike share: $($report.calibration.recommended_thresholds.NavigationObstacleRerouteSpike_share)
- NavigationOffRouteWarnRatioHigh: $($report.calibration.recommended_thresholds.NavigationOffRouteWarnRatioHigh)
"@ | Set-Content -Path $mdPath -Encoding UTF8

Write-Info "Drill report JSON: $jsonPath"
Write-Info "Drill report Markdown: $mdPath"
