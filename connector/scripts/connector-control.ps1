[CmdletBinding()]
param(
    [ValidateSet("Start", "Stop", "Restart", "Status", "Doctor", "Logs")]
    [string]$Action = "Status",
    [string]$InstallRoot = "$env:ProgramData\GymFort\Connector"
)

$ErrorActionPreference = "Stop"
$utf8 = [Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = $utf8
$OutputEncoding = $utf8
$taskName = "GymFort Local Connector"
$resolvedInstallRoot = [IO.Path]::GetFullPath($InstallRoot)
$executable = Join-Path $resolvedInstallRoot "app\GymFortConnector.exe"
$config = Join-Path $resolvedInstallRoot "config\connector.properties"

switch ($Action) {
    "Start" { Start-ScheduledTask -TaskName $taskName }
    "Stop" { Stop-ScheduledTask -TaskName $taskName }
    "Restart" {
        Stop-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 1
        Start-ScheduledTask -TaskName $taskName
    }
    "Status" {
        $task = Get-ScheduledTask -TaskName $taskName -ErrorAction Stop
        $info = Get-ScheduledTaskInfo -TaskName $taskName
        [pscustomobject]@{
            State = $task.State
            LastRunTime = $info.LastRunTime
            LastTaskResult = $info.LastTaskResult
            NextRunTime = $info.NextRunTime
        } | Format-List
    }
    "Doctor" {
        & $executable --config $config doctor
        exit $LASTEXITCODE
    }
    "Logs" {
        $log = Join-Path $resolvedInstallRoot "runtime\logs\connector.log"
        if (-not (Test-Path -LiteralPath $log)) {
            throw "Todavia no existe $log."
        }
        Get-Content -LiteralPath $log -Tail 100 -Wait
    }
}
