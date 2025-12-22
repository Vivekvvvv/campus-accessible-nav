param(
  [string]$HostName = "localhost",
  [int]$Port = 5432,
  [string]$AdminUser = "postgres",
  [string]$AdminPassword = "",
  [string]$DbName = "accessible_nav_it",
  [string]$SchemaName = "it",
  [string]$OwnerUser = "postgres"
)

$ErrorActionPreference = "Stop"

function Require-Command([string]$name) {
  if (-not (Get-Command $name -ErrorAction SilentlyContinue)) {
    throw "Command not found: $name. Please install PostgreSQL client tools (psql)."
  }
}

Require-Command "psql"
Require-Command "createdb"

if ($AdminPassword -and $AdminPassword.Trim().Length -gt 0) {
  $env:PGPASSWORD = $AdminPassword
}

if (-not $env:PGPASSWORD -or $env:PGPASSWORD.Trim().Length -eq 0) {
  Write-Host "PGPASSWORD not set. You'll be prompted by psql if your server requires a password."
  Write-Host "Tip: set it once for this session, e.g.: `$env:PGPASSWORD = 'your_password'"
}

Write-Host "Checking connectivity to ${HostName}:${Port} as $AdminUser ..."
psql -h $HostName -p $Port -U $AdminUser -d postgres -w -c "select 1;" | Out-Null

Write-Host "Ensuring database '$DbName' exists ..."
$exists = psql -h $HostName -p $Port -U $AdminUser -d postgres -w -t -A -c "select 1 from pg_database where datname='$DbName';"
$existsText =
  if ($null -eq $exists) { "" }
  elseif ($exists -is [array]) { [string]($exists -join "`n") }
  else { [string]$exists }
$existsText = $existsText.Trim()
if ($existsText -ne "1") {
  # createdb doesn't support IF NOT EXISTS; we check first.
  createdb -h $HostName -p $Port -U $AdminUser $DbName
  Write-Host "Created database $DbName"
} else {
  Write-Host "Database $DbName already exists"
}

Write-Host "Ensuring PostGIS extension + schema '$SchemaName' exist in '$DbName' ..."
$sql = @"
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE SCHEMA IF NOT EXISTS $SchemaName AUTHORIZATION $OwnerUser;
GRANT USAGE, CREATE ON SCHEMA $SchemaName TO $OwnerUser;
"@

psql -h $HostName -p $Port -U $AdminUser -d $DbName -w -v ON_ERROR_STOP=1 -c $sql | Out-Null

Write-Host "Done."
Write-Host "You can now run:"
Write-Host "  cd backend"
Write-Host "  `$env:IT_DB_URL = 'jdbc:postgresql://${HostName}:${Port}/$DbName'"
Write-Host "  `$env:IT_DB_USERNAME = '$OwnerUser'"
Write-Host "  `$env:IT_DB_PASSWORD = '<your_password>'"
Write-Host "  `$env:IT_DB_SCHEMA = '$SchemaName'"
Write-Host "  mvn -s .mvn\\settings.xml -Pit verify"
