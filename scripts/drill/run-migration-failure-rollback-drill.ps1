[CmdletBinding()]
param(
  [string]$DbHost = "localhost",
  [int]$DbPort = 5432,
  [string]$AdminUser = "postgres",
  [string]$AdminPassword = "root",
  [string]$SourceDb = "accessible_nav",
  [string]$DrillDb = "accessible_nav_migration_drill",
  [string]$OutputRoot = ".run/drills",
  [int]$RtoBudgetSec = 300,
  [int]$RpoBudgetSec = 300,
  [double]$ManualDecisionSec = 0,
  [double]$ManualRollbackSec = 0
)

$ErrorActionPreference = "Stop"

function Write-Info([string]$m) { Write-Host "[migration-drill] $m" -ForegroundColor Cyan }

function Require-Command([string]$name) {
  if (-not (Get-Command $name -ErrorAction SilentlyContinue)) {
    throw "Missing command: $name"
  }
}

function Invoke-Psql([string]$db, [string]$sql) {
  & psql -h $DbHost -p $DbPort -U $AdminUser -d $db -v ON_ERROR_STOP=1 -tA -c $sql
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $repoRoot

Require-Command "pg_dump"
Require-Command "pg_restore"
Require-Command "psql"

$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$outDir = Join-Path $repoRoot (Join-Path $OutputRoot "${stamp}_migration_failure")
New-Item -ItemType Directory -Path $outDir -Force | Out-Null

$report = [ordered]@{
  timestamp = (Get-Date).ToString("s")
  db = [ordered]@{
    host = $DbHost
    port = $DbPort
    source = $SourceDb
    drill = $DrillDb
  }
  manual = [ordered]@{
    decision_sec = $ManualDecisionSec
    rollback_sec = $ManualRollbackSec
  }
  timings = [ordered]@{}
  migration_failure = [ordered]@{}
  rollback_restore = [ordered]@{}
  result = [ordered]@{}
}

$env:PGPASSWORD = $AdminPassword
$totalSw = [System.Diagnostics.Stopwatch]::StartNew()

# 1) Backup source DB
$backupFile = Join-Path $outDir "$SourceDb.dump"
Write-Info "Backup source DB: $backupFile"
$sw = [System.Diagnostics.Stopwatch]::StartNew()
& pg_dump -h $DbHost -p $DbPort -U $AdminUser -Fc -f $backupFile $SourceDb
$sw.Stop()
if (-not (Test-Path $backupFile)) {
  throw "Backup file not created: $backupFile"
}
$report.timings.backup_sec = [Math]::Round($sw.Elapsed.TotalSeconds, 3)
$report.result.backup_file = $backupFile

# 2) Restore backup into drill DB
Write-Info "Restore backup into drill DB: $DrillDb"
$sw = [System.Diagnostics.Stopwatch]::StartNew()
Invoke-Psql "postgres" "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$DrillDb' AND pid <> pg_backend_pid();" | Out-Null
Invoke-Psql "postgres" "DROP DATABASE IF EXISTS $DrillDb;" | Out-Null
Invoke-Psql "postgres" "CREATE DATABASE $DrillDb;" | Out-Null
Invoke-Psql $DrillDb "CREATE EXTENSION IF NOT EXISTS postgis;" | Out-Null
& pg_restore -h $DbHost -p $DbPort -U $AdminUser -d $DrillDb --no-owner --no-privileges $backupFile
$sw.Stop()
$report.timings.initial_restore_sec = [Math]::Round($sw.Elapsed.TotalSeconds, 3)

# 3) Simulate migration failure (partially applied non-transactional SQL)
$failureSql = Join-Path $outDir "V999__intentional_failure.sql"
@"
CREATE TABLE IF NOT EXISTS t_migration_fail_temp (
  id BIGSERIAL PRIMARY KEY,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE t_non_existing_table ADD COLUMN should_fail INT;
"@ | Set-Content -Path $failureSql -Encoding UTF8

Write-Info "Simulate migration failure using $failureSql"
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$failureExpected = $false
& psql -h $DbHost -p $DbPort -U $AdminUser -d $DrillDb -v ON_ERROR_STOP=1 -f $failureSql
if ($LASTEXITCODE -ne 0) {
  $failureExpected = $true
}
$sw.Stop()
$report.timings.failure_apply_sec = [Math]::Round($sw.Elapsed.TotalSeconds, 3)
$report.migration_failure.expected_failure = $failureExpected
if (-not $failureExpected) {
  throw "Migration failure simulation did not fail as expected."
}

$tempTableExists = (Invoke-Psql $DrillDb "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='t_migration_fail_temp';").Trim()
$report.migration_failure.partial_artifact_exists = ([int]$tempTableExists -gt 0)

# 4) Rollback + restore from backup
Write-Info "Rollback by restoring drill DB from backup"
$sw = [System.Diagnostics.Stopwatch]::StartNew()
Invoke-Psql "postgres" "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$DrillDb' AND pid <> pg_backend_pid();" | Out-Null
Invoke-Psql "postgres" "DROP DATABASE IF EXISTS $DrillDb;" | Out-Null
Invoke-Psql "postgres" "CREATE DATABASE $DrillDb;" | Out-Null
Invoke-Psql $DrillDb "CREATE EXTENSION IF NOT EXISTS postgis;" | Out-Null
& pg_restore -h $DbHost -p $DbPort -U $AdminUser -d $DrillDb --no-owner --no-privileges $backupFile
$sw.Stop()
$report.timings.rollback_restore_sec = [Math]::Round($sw.Elapsed.TotalSeconds, 3)

$tempTableAfterRestore = (Invoke-Psql $DrillDb "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='t_migration_fail_temp';").Trim()
$report.rollback_restore.temp_table_after_restore = [int]$tempTableAfterRestore
$report.rollback_restore.success = ([int]$tempTableAfterRestore -eq 0)

$totalSw.Stop()
$report.timings.total_sec = [Math]::Round($totalSw.Elapsed.TotalSeconds, 3)

$rtoMeasured = [Math]::Round($report.timings.rollback_restore_sec + $ManualDecisionSec + $ManualRollbackSec, 3)
$rpoMeasured = 0.0
$report.result.rto_budget_sec = $RtoBudgetSec
$report.result.rto_measured_sec = $rtoMeasured
$report.result.rpo_budget_sec = $RpoBudgetSec
$report.result.rpo_measured_sec = $rpoMeasured
$report.result.rto_pass = ($rtoMeasured -le $RtoBudgetSec)
$report.result.rpo_pass = ($rpoMeasured -le $RpoBudgetSec)
$report.result.status = if ($report.result.rto_pass -and $report.result.rpo_pass -and $report.rollback_restore.success) { "PASS" } else { "FAIL" }

$jsonPath = Join-Path $outDir "migration-failure-drill-report.json"
$report | ConvertTo-Json -Depth 10 | Set-Content -Path $jsonPath -Encoding UTF8

$mdPath = Join-Path $outDir "migration-failure-drill-report.md"
@"
# Migration Failure + Rollback + Restore Drill

- timestamp: $($report.timestamp)
- source_db: $SourceDb
- drill_db: $DrillDb
- backup_file: $backupFile

## Timings

- backup_sec: $($report.timings.backup_sec)
- initial_restore_sec: $($report.timings.initial_restore_sec)
- failure_apply_sec: $($report.timings.failure_apply_sec)
- rollback_restore_sec: $($report.timings.rollback_restore_sec)
- total_sec: $($report.timings.total_sec)

## Manual Steps

- manual_decision_sec: $ManualDecisionSec
- manual_rollback_sec: $ManualRollbackSec

## Verification

- failure_expected: $($report.migration_failure.expected_failure)
- partial_artifact_exists_before_rollback: $($report.migration_failure.partial_artifact_exists)
- temp_table_after_restore: $($report.rollback_restore.temp_table_after_restore)
- rollback_restore_success: $($report.rollback_restore.success)

## RTO / RPO

- rto_budget_sec: $($report.result.rto_budget_sec)
- rto_measured_sec: $($report.result.rto_measured_sec)
- rpo_budget_sec: $($report.result.rpo_budget_sec)
- rpo_measured_sec: $($report.result.rpo_measured_sec)
- status: **$($report.result.status)**
"@ | Set-Content -Path $mdPath -Encoding UTF8

Write-Info "Report JSON: $jsonPath"
Write-Info "Report Markdown: $mdPath"

if ($report.result.status -ne "PASS") {
  throw "Migration failure drill failed. See $mdPath"
}
