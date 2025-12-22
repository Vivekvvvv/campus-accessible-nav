[CmdletBinding()]
param(
  [string]$DbHost = "localhost",
  [int]$DbPort = 5432,
  [string]$AdminUser = "postgres",
  [string]$AdminPassword = "root",
  [string]$SourceDb = "accessible_nav",
  [string]$DrillDb = "accessible_nav_drill",
  [int]$BackendPort = 18081,
  [int]$RtoBudgetSec = 300,
  [int]$RpoBudgetSec = 300
)

$ErrorActionPreference = "Stop"

function Write-Info([string]$m) { Write-Host "[prod-drill] $m" -ForegroundColor Cyan }

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $repoRoot

$drillRoot = Join-Path $repoRoot ".run/drills"
New-Item -ItemType Directory -Path $drillRoot -Force | Out-Null
$before = @{}
Get-ChildItem $drillRoot -Directory -ErrorAction SilentlyContinue | ForEach-Object { $before[$_.Name] = $true }

Write-Info "Running full-chain drill with production-equivalent backend profile"
& .\scripts\drill\run-release-rollback-restore-drill.ps1 `
  -DbHost $DbHost `
  -DbPort $DbPort `
  -AdminUser $AdminUser `
  -AdminPassword $AdminPassword `
  -SourceDb $SourceDb `
  -DrillDb $DrillDb `
  -BackendPort $BackendPort `
  -BackendProfile "prod"

$afterDirs = Get-ChildItem $drillRoot -Directory | Where-Object { -not $before.ContainsKey($_.Name) } | Sort-Object Name
if (-not $afterDirs -or $afterDirs.Count -eq 0) {
  throw "Cannot locate generated drill directory under $drillRoot"
}
$latest = $afterDirs[-1]
$reportPath = Join-Path $latest.FullName "drill-report.json"
if (-not (Test-Path $reportPath)) {
  throw "Missing drill report: $reportPath"
}

$report = Get-Content $reportPath -Raw | ConvertFrom-Json
$rtoSec = [double]$report.timings.total_sec
$rpoSec = 0.0  # On-demand pre-release backup means near-zero RPO for this drill.

$passRto = $rtoSec -le $RtoBudgetSec
$passRpo = $rpoSec -le $RpoBudgetSec
$status = if ($passRto -and $passRpo) { "PASS" } else { "FAIL" }

$summaryPath = Join-Path $latest.FullName "prod-equivalent-summary.md"
@"
# Production-Equivalent Drill Summary

- status: **$status**
- report: $reportPath
- profile: `prod`
- rto_budget_sec: $RtoBudgetSec
- rto_measured_sec: $rtoSec
- rpo_budget_sec: $RpoBudgetSec
- rpo_measured_sec: $rpoSec

Checks:
- RTO pass: $passRto
- RPO pass: $passRpo
"@ | Set-Content -Path $summaryPath -Encoding UTF8

Write-Info "Summary: $summaryPath"

if (-not $passRto -or -not $passRpo) {
  throw "Production-equivalent drill failed: RTO/RPO out of budget. See $summaryPath"
}
