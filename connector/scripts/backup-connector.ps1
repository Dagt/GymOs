[CmdletBinding()]
param(
    [string]$InstallRoot = "$env:ProgramData\GymFort\Connector",
    [string]$DestinationDirectory = "$env:ProgramData\GymFort\Backups"
)

$ErrorActionPreference = "Stop"
$utf8 = [Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = $utf8
$OutputEncoding = $utf8
$taskName = "GymFort Local Connector"
$resolvedInstallRoot = [IO.Path]::GetFullPath($InstallRoot)
$resolvedDestination = [IO.Path]::GetFullPath($DestinationDirectory)
if (-not (Test-Path -LiteralPath $resolvedInstallRoot -PathType Container)) {
    throw "No existe la instalacion $resolvedInstallRoot."
}
New-Item -ItemType Directory -Path $resolvedDestination -Force | Out-Null
$archive = Join-Path $resolvedDestination "GymFortConnector-$(Get-Date -Format 'yyyyMMddHHmmss').zip"
$taskExists = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
try {
    if ($taskExists) {
        Stop-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 1
    }
    $paths = @("config", "state", "runtime") |
        ForEach-Object { Join-Path $resolvedInstallRoot $_ } |
        Where-Object { Test-Path -LiteralPath $_ }
    if ($paths.Count -eq 0) {
        throw "No existen datos locales para respaldar."
    }
    Compress-Archive -LiteralPath $paths -DestinationPath $archive -CompressionLevel Optimal
} finally {
    if ($taskExists) {
        Start-ScheduledTask -TaskName $taskName
    }
}
Write-Host "Respaldo creado: $archive"
