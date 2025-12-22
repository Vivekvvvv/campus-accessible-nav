[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)]
  [string]$TraceId,
  [string]$Service = "accessible-nav-backend",
  [ValidateSet("kibana", "loki", "auto")]
  [string]$Provider = "auto",
  [int]$FromMinutesAgo = 30,
  [int]$ToMinutesAgo = 0,
  [string]$LogRoot = "backend/logs",
  [string]$QueryTemplate = $env:TRACE_QUERY_URL_TEMPLATE,
  [switch]$OpenBrowser
)

$ErrorActionPreference = "Stop"

function Write-Info([string]$m) {
  Write-Host "[trace] $m" -ForegroundColor Cyan
}

function To-IsoUtc([datetime]$dt) {
  return $dt.ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
}

function Resolve-Template([string]$InputTemplate, [string]$InputProvider) {
  if (-not [string]::IsNullOrWhiteSpace($InputTemplate)) {
    return $InputTemplate
  }

  $provider = $InputProvider
  if ($provider -eq "auto") {
    if (-not [string]::IsNullOrWhiteSpace($env:TRACE_QUERY_PROVIDER)) {
      $provider = $env:TRACE_QUERY_PROVIDER.ToLowerInvariant()
    } elseif (-not [string]::IsNullOrWhiteSpace($env:TRACE_QUERY_URL_TEMPLATE_KIBANA)) {
      $provider = "kibana"
    } elseif (-not [string]::IsNullOrWhiteSpace($env:TRACE_QUERY_URL_TEMPLATE_LOKI)) {
      $provider = "loki"
    }
  }

  switch ($provider) {
    "kibana" {
      return $env:TRACE_QUERY_URL_TEMPLATE_KIBANA
    }
    "loki" {
      return $env:TRACE_QUERY_URL_TEMPLATE_LOKI
    }
    default {
      return ""
    }
  }
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $repoRoot

$toTime = (Get-Date).AddMinutes(-1 * $ToMinutesAgo)
$fromTime = (Get-Date).AddMinutes(-1 * $FromMinutesAgo)
$fromIso = To-IsoUtc $fromTime
$toIso = To-IsoUtc $toTime

Write-Info ("traceId={0}" -f $TraceId)
Write-Info ("window={0} -> {1}" -f $fromIso, $toIso)
$resolvedTemplate = Resolve-Template -InputTemplate $QueryTemplate -InputProvider $Provider

if (-not [string]::IsNullOrWhiteSpace($resolvedTemplate)) {
  $url = $resolvedTemplate
  $url = $url.Replace("{traceId}", $TraceId)
  $url = $url.Replace("{service}", $Service)
  $url = $url.Replace("{fromIso}", $fromIso)
  $url = $url.Replace("{toIso}", $toIso)
  $url = $url.Replace("{traceIdUrl}", [uri]::EscapeDataString($TraceId))
  $url = $url.Replace("{serviceUrl}", [uri]::EscapeDataString($Service))
  $url = $url.Replace("{fromIsoUrl}", [uri]::EscapeDataString($fromIso))
  $url = $url.Replace("{toIsoUrl}", [uri]::EscapeDataString($toIso))

  Write-Info ("query-url={0}" -f $url)
  if ($OpenBrowser) {
    Start-Process $url | Out-Null
    Write-Info "opened in browser."
  }
} else {
  Write-Info "Trace query template is not configured; set TRACE_QUERY_URL_TEMPLATE or TRACE_QUERY_PROVIDER + provider template."
}

$absLogRoot = Join-Path $repoRoot $LogRoot
if (Test-Path $absLogRoot) {
  Write-Info ("local grep from {0}" -f $absLogRoot)
  $files = Get-ChildItem -Path $absLogRoot -File | Where-Object { $_.Extension -in @(".log", ".txt", ".json") }
  if ($files.Count -eq 0) {
    Write-Info "no plain text log files found."
  } else {
    $matches = $files | Select-String -Pattern $TraceId
    if ($matches) {
      $matches | Select-Object -First 30 | ForEach-Object {
        Write-Host ("{0}:{1}: {2}" -f $_.Path, $_.LineNumber, $_.Line)
      }
      if ($matches.Count -gt 30) {
        Write-Info ("truncated local matches: {0}/{1}" -f 30, $matches.Count)
      }
    } else {
      Write-Info "no local match found in plain logs."
    }
  }
} else {
  Write-Info ("log root not found: {0}" -f $absLogRoot)
}

Write-Host ""
Write-Host "Template examples:"
Write-Host '$env:TRACE_QUERY_PROVIDER = "kibana"'
Write-Host '$env:TRACE_QUERY_URL_TEMPLATE_KIBANA = "https://kibana.example/app/discover#/?_a=%28query:%28language:kuery,query:%27traceId:{traceId}%20and%20service.name:{service}%27%29%29&_g=%28time:%28from:%27{fromIso}%27,to:%27{toIso}%27%29%29"'
Write-Host '$env:TRACE_QUERY_PROVIDER = "loki"'
Write-Host '$env:TRACE_QUERY_URL_TEMPLATE_LOKI = "https://grafana.example/explore?left=%7B%22datasource%22%3A%22Loki%22%2C%22queries%22%3A%5B%7B%22expr%22%3A%22%7Bapp%3D%5C%22{service}%5C%22%7D%20%7C%3D%20%5C%22{traceId}%5C%22%22%2C%22queryType%22%3A%22range%22%7D%5D%2C%22range%22%3A%7B%22from%22%3A%22{fromIso}%22%2C%22to%22%3A%22{toIso}%22%7D%7D"'
