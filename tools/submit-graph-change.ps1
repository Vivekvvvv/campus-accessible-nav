param(
  [Parameter(Mandatory = $true)]
  [string]$Input,

  [string]$ApiBase = "http://localhost:8080",
  [string]$Username = "admin",
  [string]$Password = "admin123",
  [string]$Kind = "IMPORT",
  [string]$PayloadType = "IMPORT",
  [string]$Note = ""
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -Path $Input)) {
  throw "Input file not found: $Input"
}

$raw = Get-Content -Path $Input -Raw -Encoding utf8
$payload = $raw | ConvertFrom-Json

$loginBody = @{
  username = $Username
  password = $Password
} | ConvertTo-Json

$loginResp = Invoke-WebRequest -UseBasicParsing -Method POST -Uri "$ApiBase/api/auth/login" -ContentType "application/json" -Body $loginBody
$loginJson = $loginResp.Content | ConvertFrom-Json
if (-not $loginJson.token) {
  throw "Login failed: token missing"
}

$submitBody = @{
  kind = $Kind
  payloadType = $PayloadType
  payload = $payload
  note = $Note
} | ConvertTo-Json -Depth 12

$headers = @{ Authorization = "Bearer $($loginJson.token)" }
$submitResp = Invoke-WebRequest -UseBasicParsing -Method POST -Uri "$ApiBase/api/admin/graph/changes" -Headers $headers -ContentType "application/json" -Body $submitBody

$submitResp.Content
