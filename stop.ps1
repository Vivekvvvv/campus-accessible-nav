[CmdletBinding()]
param(
  [switch]$Force
)

$ErrorActionPreference = "Continue"

function Info([string]$msg) { Write-Host "[stop] $msg" -ForegroundColor Cyan }

function Stop-ByPidFile([string]$pidFile, [string]$name) {
  if (-not (Test-Path $pidFile)) {
    Info "${name}: pid file not found, skip."
    return
  }

  $procId = (Get-Content $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1)
  if (-not $procId) {
    Info "${name}: empty pid file, skip."
    return
  }

  $p = Get-Process -Id $procId -ErrorAction SilentlyContinue
  if ($null -eq $p) {
    Info "${name}: PID=$procId not running. Cleaning pid file."
    Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
    return
  }

  Info "${name}: stopping PID=$procId"
  try {
    Stop-Process -Id $procId -Force:$Force -ErrorAction Stop
  } catch {
    Info "${name}: stop failed: $($_.Exception.Message)"
  }

  Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
}

$repoRoot = Split-Path -Parent $PSCommandPath
$runDir = Join-Path $repoRoot ".run"

Stop-ByPidFile -pidFile (Join-Path $runDir "frontend.pid") -name "frontend"
Stop-ByPidFile -pidFile (Join-Path $runDir "backend.pid") -name "backend"

Info "Done."
