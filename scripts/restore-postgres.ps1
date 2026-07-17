param(
  [Parameter(Mandatory = $true)]
  [string]$BackupFile,
  [string]$Container = "gymos-postgres-1",
  [string]$Database = "gymos",
  [string]$Username = "gymos"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $BackupFile)) {
  throw "Backup file not found: $BackupFile"
}

Get-Content -Encoding Byte -Path $BackupFile | docker exec -i $Container pg_restore -U $Username -d $Database --clean --if-exists
Write-Host "Restore completed from: $BackupFile"
