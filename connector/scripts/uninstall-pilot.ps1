[CmdletBinding(SupportsShouldProcess)]
param(
    [string]$InstallRoot = "$env:ProgramData\GymFort\Connector",
    [switch]$PurgeData
)

$ErrorActionPreference = "Stop"
$utf8 = [Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = $utf8
$OutputEncoding = $utf8
$taskName = "GymFort Local Connector"
$resolvedInstallRoot = [IO.Path]::GetFullPath($InstallRoot)
if ($resolvedInstallRoot.Length -lt 12 -or $resolvedInstallRoot -eq [IO.Path]::GetPathRoot($resolvedInstallRoot)) {
    throw "InstallRoot es demasiado amplio o inseguro."
}

if (Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue) {
    Stop-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
    Unregister-ScheduledTask -TaskName $taskName -Confirm:$false
}
if (-not (Test-Path -LiteralPath $resolvedInstallRoot)) {
    Write-Host "La tarea fue retirada; el directorio ya no existe."
    return
}

if ($PurgeData) {
    if ($PSCmdlet.ShouldProcess($resolvedInstallRoot, "Eliminar instalacion y estado local")) {
        Remove-Item -LiteralPath $resolvedInstallRoot -Recurse -Force
        Write-Host "Instalacion y datos locales eliminados de forma permanente."
    }
} else {
    $recoverable = "$resolvedInstallRoot.uninstalled.$(Get-Date -Format 'yyyyMMddHHmmss')"
    if ($PSCmdlet.ShouldProcess($resolvedInstallRoot, "Mover instalacion a $recoverable")) {
        Move-Item -LiteralPath $resolvedInstallRoot -Destination $recoverable
        Write-Host "Instalacion retirada de forma recuperable: $recoverable"
    }
}
