[CmdletBinding()]
param(
  [string]$DbHost = "localhost",
  [int]$DbPort = 5432,
  [string]$DbUser = "postgres",
  [string]$DbPassword = "root",
  [string]$DbName = "accessible_nav",
  [int]$TopN = 30,
  [string]$OutFile = ""
)

$ErrorActionPreference = "Stop"

function Write-Info([string]$m) {
  Write-Host "[perf-sql] $m" -ForegroundColor Cyan
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $repoRoot

if (-not (Get-Command psql -ErrorAction SilentlyContinue)) {
  throw "psql is required."
}

if ([string]::IsNullOrWhiteSpace($OutFile)) {
  $stamp = Get-Date -Format "yyyyMMdd_HHmmss"
  $outDir = Join-Path $repoRoot ".run/perf"
  New-Item -ItemType Directory -Path $outDir -Force | Out-Null
  $OutFile = Join-Path $outDir ("pg-hot-sql-{0}.csv" -f $stamp)
}

$env:PGPASSWORD = $DbPassword

$extCheck = (& psql -h $DbHost -p $DbPort -U $DbUser -d $DbName -tA -c "SELECT COUNT(*) FROM pg_extension WHERE extname='pg_stat_statements';").Trim()
if ([int]$extCheck -eq 0) {
  throw "pg_stat_statements extension is not installed. Run: CREATE EXTENSION IF NOT EXISTS pg_stat_statements;"
}

$sql = @"
\copy (
  SELECT
    queryid,
    calls,
    ROUND(total_exec_time::numeric, 3) AS total_exec_ms,
    ROUND(mean_exec_time::numeric, 3) AS mean_exec_ms,
    rows,
    shared_blks_hit,
    shared_blks_read,
    LEFT(REPLACE(query, E'\n', ' '), 400) AS query
  FROM pg_stat_statements
  ORDER BY total_exec_time DESC
  LIMIT $TopN
) TO STDOUT WITH CSV HEADER
"@

Write-Info ("export top {0} hot SQL from {1} -> {2}" -f $TopN, $DbName, $OutFile)
& psql -h $DbHost -p $DbPort -U $DbUser -d $DbName -v ON_ERROR_STOP=1 -c $sql | Set-Content -Path $OutFile -Encoding UTF8

Write-Info ("done: {0}" -f $OutFile)
