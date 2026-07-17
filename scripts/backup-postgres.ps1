param(
  [string]$Container = "gymos-postgres-1",
  [string]$Database = "gymos",
  [string]$Username = "gymos",
  [string]$OutputDirectory = "backups",
  [int]$RetentionDays = 14
)

$ErrorActionPreference = "Stop"

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$target = Join-Path $OutputDirectory "gymos-$timestamp.dump"

docker exec $Container pg_dump -U $Username -d $Database -Fc | Set-Content -Encoding Byte -Path $target

$cutoff = (Get-Date).AddDays(-$RetentionDays)
Get-ChildItem $OutputDirectory -Filter "gymos-*.dump" |
  Where-Object { $_.LastWriteTime -lt $cutoff } |
  Remove-Item -Force

Write-Host "Backup created: $target"
