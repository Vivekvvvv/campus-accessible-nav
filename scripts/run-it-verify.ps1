param(
  [string]$HostName = "localhost",
  [int]$Port = 5432,
  [string]$AdminUser = "postgres",
  [string]$AdminPassword = "root",
  [string]$DbName = "accessible_nav_it",
  [string]$SchemaName = "it",
  [string]$ItDbUser = "postgres",
  [string]$ItDbPassword = "",
  [switch]$SkipEnsureDb,
  [switch]$NoQuiet
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if ([string]::IsNullOrWhiteSpace($ItDbPassword)) {
  $ItDbPassword = $AdminPassword
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$backendDir = Join-Path $repoRoot "backend"
$ensureScript = Join-Path $repoRoot "scripts/ensure-it-db.ps1"
$mvnSettings = ".mvn/settings.xml"

if (-not $SkipEnsureDb) {
  Write-Host "Preparing IT database/schema..."
  & $ensureScript `
    -HostName $HostName `
    -Port $Port `
    -AdminUser $AdminUser `
    -AdminPassword $AdminPassword `
    -DbName $DbName `
    -SchemaName $SchemaName `
    -OwnerUser $ItDbUser
}

$env:IT_DB_URL = "jdbc:postgresql://${HostName}:${Port}/${DbName}"
$env:IT_DB_USERNAME = $ItDbUser
$env:IT_DB_PASSWORD = $ItDbPassword
$env:IT_DB_SCHEMA = $SchemaName

Write-Host "Using IT_DB_URL=$($env:IT_DB_URL)"
Write-Host "Using IT_DB_USERNAME=$($env:IT_DB_USERNAME)"
Write-Host "Using IT_DB_SCHEMA=$($env:IT_DB_SCHEMA)"

Push-Location $backendDir
try {
  if ($NoQuiet) {
    mvn -s $mvnSettings -Pit verify
  } else {
    mvn -q -s $mvnSettings -Pit verify
  }
} finally {
  Pop-Location
}
