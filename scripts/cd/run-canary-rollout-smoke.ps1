[CmdletBinding()]
param(
  [string]$PrometheusBaseUrl = $env:PROMETHEUS_BASE_URL,
  [string]$CanarySetWeightCmd = $env:CANARY_SET_WEIGHT_CMD,
  [string]$CanaryPromoteCmd = $env:CANARY_PROMOTE_CMD,
  [string]$CanaryRollbackCmd = $env:CANARY_ROLLBACK_CMD,
  [string]$RateLimitTightenCmd = $env:RATE_LIMIT_TIGHTEN_CMD,
  [string]$RateLimitRelaxCmd = $env:RATE_LIMIT_RELAX_CMD,
  [string]$CanarySteps = $(if ($env:CANARY_STEPS) { $env:CANARY_STEPS } else { "5 25 50 100" }),
  [int]$CanaryPauseSeconds = $(if ($env:CANARY_PAUSE_SECONDS) { [int]$env:CANARY_PAUSE_SECONDS } else { 120 }),
  [double]$SloRouteAvailMin = $(if ($env:SLO_ROUTE_AVAIL_MIN) { [double]$env:SLO_ROUTE_AVAIL_MIN } else { 0.98 }),
  [double]$SloHttp5xxMax = $(if ($env:SLO_HTTP_5XX_MAX) { [double]$env:SLO_HTTP_5XX_MAX } else { 0.02 }),
  [double]$SloRouteP95MaxSec = $(if ($env:SLO_ROUTE_P95_MAX_SEC) { [double]$env:SLO_ROUTE_P95_MAX_SEC } else { 1.5 }),
  [double]$SloDegradeWeight = $(if ($env:SLO_DEGRADE_WEIGHT) { [double]$env:SLO_DEGRADE_WEIGHT } else { 10 }),
  [int]$SloRecheckSeconds = $(if ($env:SLO_RECHECK_SECONDS) { [int]$env:SLO_RECHECK_SECONDS } else { 60 }),
  [switch]$DryRun,
  [switch]$UseMockMetrics,
  [double]$MockRouteAvail = 0.995,
  [double]$MockHttp5xx = 0.005,
  [double]$MockRouteP95Sec = 0.9
)

$ErrorActionPreference = "Stop"

function Write-Info([string]$msg) {
  Write-Host "[canary-smoke] $msg" -ForegroundColor Cyan
}

function Read-PromValue([string]$query) {
  if ([string]::IsNullOrWhiteSpace($PrometheusBaseUrl)) {
    return $null
  }
  $encoded = [uri]::EscapeDataString($query)
  $uri = "{0}/api/v1/query?query={1}" -f $PrometheusBaseUrl.TrimEnd('/'), $encoded
  try {
    $resp = Invoke-RestMethod -Method Get -Uri $uri -TimeoutSec 20
    if ($resp.status -ne "success") {
      return $null
    }
    if (-not $resp.data -or -not $resp.data.result -or $resp.data.result.Count -eq 0) {
      return $null
    }
    $raw = $resp.data.result[0].value[1]
    if ($null -eq $raw -or $raw -eq "") {
      return $null
    }
    return [double]$raw
  } catch {
    Write-Info ("Prometheus query failed: {0}" -f $_.Exception.Message)
    return $null
  }
}

function Invoke-CanaryCmd([string]$label, [string]$cmd, [string]$weight = "") {
  if ([string]::IsNullOrWhiteSpace($cmd)) {
    if ($DryRun) {
      Write-Info ("[dry-run] {0}: <empty cmd>" -f $label)
      return
    }
    throw "Missing command for $label"
  }

  if (-not [string]::IsNullOrWhiteSpace($weight)) {
    $env:WEIGHT = $weight
  }

  if ($DryRun) {
    Write-Info ("[dry-run] {0}: {1}" -f $label, $cmd)
    return
  }

  Write-Info $label
  Invoke-Expression $cmd
}

function Invoke-OptionalCmd([string]$label, [string]$cmd) {
  if ([string]::IsNullOrWhiteSpace($cmd)) {
    Write-Info ("skip optional action '{0}' (cmd not configured)." -f $label)
    return
  }
  Invoke-CanaryCmd -label $label -cmd $cmd
}

function Test-SloGate {
  if ($DryRun -and -not $UseMockMetrics) {
    Write-Info "[dry-run] SLO gate skipped."
    return $true
  }

  if ($UseMockMetrics) {
    $routeAvail = $MockRouteAvail
    $http5xx = $MockHttp5xx
    $routeP95 = $MockRouteP95Sec
  } else {
    $qRouteAvail = 'sum(rate(route_requests_total{result="success"}[5m])) / clamp_min(sum(rate(route_requests_total[5m])), 1e-9)'
    $qHttp5xx = 'sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) / clamp_min(sum(rate(http_server_requests_seconds_count[5m])), 1e-9)'
    $qRouteP95 = 'histogram_quantile(0.95, sum(rate(route_calculation_duration_seconds_bucket[5m])) by (le))'

    $routeAvail = Read-PromValue -query $qRouteAvail
    $http5xx = Read-PromValue -query $qHttp5xx
    $routeP95 = Read-PromValue -query $qRouteP95
  }

  Write-Info ("SLO snapshot: route_avail={0}, http_5xx={1}, route_p95={2}s" -f $routeAvail, $http5xx, $routeP95)

  if ($null -eq $routeAvail -or $null -eq $http5xx -or $null -eq $routeP95) {
    Write-Info "Missing SLO metrics."
    return $false
  }
  if ($routeAvail -lt $SloRouteAvailMin) {
    Write-Info ("route availability below threshold: {0} < {1}" -f $routeAvail, $SloRouteAvailMin)
    return $false
  }
  if ($http5xx -gt $SloHttp5xxMax) {
    Write-Info ("http 5xx ratio above threshold: {0} > {1}" -f $http5xx, $SloHttp5xxMax)
    return $false
  }
  if ($routeP95 -gt $SloRouteP95MaxSec) {
    Write-Info ("route p95 above threshold: {0}s > {1}s" -f $routeP95, $SloRouteP95MaxSec)
    return $false
  }
  return $true
}

if ([string]::IsNullOrWhiteSpace($PrometheusBaseUrl) -or
    [string]::IsNullOrWhiteSpace($CanarySetWeightCmd) -or
    [string]::IsNullOrWhiteSpace($CanaryPromoteCmd) -or
    [string]::IsNullOrWhiteSpace($CanaryRollbackCmd)) {
  if (-not $DryRun -and -not $UseMockMetrics) {
    Write-Info "Required env is incomplete; falling back to -DryRun."
    $DryRun = $true
  }
}

$weights = $CanarySteps.Split(" ", [System.StringSplitOptions]::RemoveEmptyEntries)
if ($weights.Count -eq 0) {
  throw "CANARY_STEPS is empty."
}

$rateLimitTightened = $false

Write-Info ("Starting rollout steps: {0}" -f ($weights -join " "))

foreach ($weight in $weights) {
  Invoke-CanaryCmd -label ("set canary weight {0}%" -f $weight) -cmd $CanarySetWeightCmd -weight $weight

  if ($DryRun) {
    Write-Info ("[dry-run] skip wait ({0}s)" -f $CanaryPauseSeconds)
  } else {
    Write-Info ("Waiting {0}s before SLO check..." -f $CanaryPauseSeconds)
    Start-Sleep -Seconds $CanaryPauseSeconds
  }

  if (-not (Test-SloGate)) {
    Write-Info ("SLO gate failed at weight {0}%. Triggering auto actions..." -f $weight)
    Invoke-OptionalCmd -label "tighten rate limit" -cmd $RateLimitTightenCmd
    if (-not [string]::IsNullOrWhiteSpace($RateLimitTightenCmd)) {
      $rateLimitTightened = $true
    }

    if ([double]$weight -gt $SloDegradeWeight) {
      Invoke-CanaryCmd -label ("degrade canary weight to {0}%" -f $SloDegradeWeight) -cmd $CanarySetWeightCmd -weight ([string]$SloDegradeWeight)
    } else {
      Write-Info ("current weight ({0}%) <= degrade target ({1}%), skip degrade." -f $weight, $SloDegradeWeight)
    }

    if ($DryRun) {
      Write-Info ("[dry-run] skip recheck wait ({0}s)" -f $SloRecheckSeconds)
    } else {
      Write-Info ("Waiting {0}s before post-action SLO recheck..." -f $SloRecheckSeconds)
      Start-Sleep -Seconds $SloRecheckSeconds
    }

    if (-not (Test-SloGate)) {
      Write-Info ("Post-action SLO recheck failed at weight {0}%, rolling back." -f $weight)
      Invoke-CanaryCmd -label "rollback" -cmd $CanaryRollbackCmd
      exit 1
    }

    Write-Info "Post-action SLO recheck passed, continue rollout."
  } elseif ($rateLimitTightened) {
    Invoke-OptionalCmd -label "relax rate limit" -cmd $RateLimitRelaxCmd
    $rateLimitTightened = $false
  }
}

Invoke-CanaryCmd -label "promote canary to stable" -cmd $CanaryPromoteCmd
if ($rateLimitTightened) {
  Invoke-OptionalCmd -label "relax rate limit" -cmd $RateLimitRelaxCmd
}
Write-Info "Rollout succeeded."
