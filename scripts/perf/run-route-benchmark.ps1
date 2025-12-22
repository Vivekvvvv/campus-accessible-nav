[CmdletBinding()]
param(
  [string]$BaseUrl = "http://localhost:8081",
  [string]$Path = "/api/route",
  [int]$DurationSec = 60,
  [int]$Concurrency = 10,
  [int]$TargetRps = 0,
  [int]$TimeoutMs = 8000,
  [string]$OutFile = "",
  [double]$MaxP95Ms = 1500,
  [double]$MaxErrorRate = 0.02,
  [switch]$FailOnThreshold
)

$ErrorActionPreference = "Stop"

function Write-Info([string]$m) {
  Write-Host "[perf] $m" -ForegroundColor Cyan
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $repoRoot

if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
  throw "node is required."
}

if ([string]::IsNullOrWhiteSpace($OutFile)) {
  $stamp = Get-Date -Format "yyyyMMdd_HHmmss"
  $outDir = Join-Path $repoRoot ".run/perf"
  New-Item -ItemType Directory -Path $outDir -Force | Out-Null
  $OutFile = Join-Path $outDir ("route-benchmark-{0}.json" -f $stamp)
}

$args = @(
  "scripts/perf/route-benchmark.mjs",
  "--base-url", $BaseUrl,
  "--path", $Path,
  "--duration-sec", "$DurationSec",
  "--concurrency", "$Concurrency",
  "--target-rps", "$TargetRps",
  "--timeout-ms", "$TimeoutMs",
  "--out", $OutFile,
  "--max-p95-ms", "$MaxP95Ms",
  "--max-error-rate", "$MaxErrorRate"
)
if ($FailOnThreshold) {
  $args += "--fail-on-threshold"
}

Write-Info ("run node {0}" -f ($args -join " "))
& node @args

Write-Info ("report: {0}" -f $OutFile)
