[CmdletBinding()]
param(
  [string]$BaseUrl = "http://localhost:8081",
  [string]$DbHost = "localhost",
  [int]$DbPort = 5432,
  [string]$DbUser = "postgres",
  [string]$DbPassword = "root",
  [string]$DbName = "accessible_nav",
  [int]$DurationSec = 180,
  [int]$Concurrency = 20,
  [int]$TargetRps = 60,
  [double]$MaxP95Ms = 1500,
  [double]$MaxErrorRate = 0.02,
  [int]$HotSqlTopN = 30,
  [switch]$FailOnThreshold
)

$ErrorActionPreference = "Stop"

function Write-Info([string]$m) {
  Write-Host "[perf-baseline] $m" -ForegroundColor Cyan
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $repoRoot

$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$outDir = Join-Path $repoRoot ".run/perf/$stamp"
New-Item -ItemType Directory -Path $outDir -Force | Out-Null

$benchOut = Join-Path $outDir "route-benchmark.json"
$sqlOut = Join-Path $outDir "pg-hot-sql.csv"

$benchmarkArgs = @(
  "-BaseUrl", $BaseUrl,
  "-DurationSec", "$DurationSec",
  "-Concurrency", "$Concurrency",
  "-TargetRps", "$TargetRps",
  "-MaxP95Ms", "$MaxP95Ms",
  "-MaxErrorRate", "$MaxErrorRate",
  "-OutFile", $benchOut
)
if ($FailOnThreshold) {
  $benchmarkArgs += "-FailOnThreshold"
}

Write-Info "running route benchmark"
& (Join-Path $repoRoot "scripts/perf/run-route-benchmark.ps1") @benchmarkArgs

Write-Info "exporting pg_stat_statements hot SQL"
& (Join-Path $repoRoot "scripts/perf/export-pg-hot-sql.ps1") `
  -DbHost $DbHost `
  -DbPort $DbPort `
  -DbUser $DbUser `
  -DbPassword $DbPassword `
  -DbName $DbName `
  -TopN $HotSqlTopN `
  -OutFile $sqlOut

Write-Info ("done. benchmark: {0}" -f $benchOut)
Write-Info ("done. hot-sql: {0}" -f $sqlOut)
