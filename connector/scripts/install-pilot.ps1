[CmdletBinding()]
param(
    [string]$InstallRoot = "$env:ProgramData\GymFort\Connector",
    [string]$CloudBaseUrl,
    [string]$ConnectorKey,
    [ValidateSet("HIKCENTRAL", "SIMULATOR")]
    [string]$Provider = "HIKCENTRAL",
    [string]$HikCentralBaseUrl,
    [string]$HikCentralAppKey,
    [string[]]$ManagedAccessLevelRefs,
    [string]$HikCentralTrustedCertificate,
    [string]$HikCentralCapabilityProfile,
    [switch]$SkipActivation,
    [switch]$SkipProviderSecret,
    [switch]$SkipDoctor,
    [switch]$SkipScheduledTask
)

$ErrorActionPreference = "Stop"
$utf8 = [Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = $utf8
$OutputEncoding = $utf8
$taskName = "GymFort Local Connector"

function Read-RequiredValue {
    param([string]$Current, [string]$Prompt)
    $value = $Current
    if ([string]::IsNullOrWhiteSpace($value)) {
        $value = Read-Host $Prompt
    }
    if ([string]::IsNullOrWhiteSpace($value) -or $value.Contains("`r") -or $value.Contains("`n")) {
        throw "$Prompt es obligatorio y debe ocupar una sola linea."
    }
    return $value.Trim()
}

function Assert-HttpUrl {
    param([string]$Value, [string]$Name)
    $parsed = $null
    if (-not [Uri]::TryCreate($Value, [UriKind]::Absolute, [ref]$parsed) -or
        $parsed.Scheme -notin @("http", "https") -or
        [string]::IsNullOrWhiteSpace($parsed.Host) -or
        -not [string]::IsNullOrWhiteSpace($parsed.UserInfo)) {
        throw "$Name debe ser una URL http/https sin credenciales embebidas."
    }
}

function Invoke-Connector {
    param([string[]]$Arguments)
    & $script:connectorExecutable @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "GymFort Connector termino con codigo $LASTEXITCODE."
    }
}

if (-not $IsWindows -and $PSVersionTable.PSVersion.Major -ge 6) {
    throw "El instalador piloto actual solo admite Windows."
}

$bundleRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$sourceApp = Join-Path $bundleRoot "app"
$sourceExecutable = Join-Path $sourceApp "GymFortConnector.exe"
if (-not (Test-Path -LiteralPath $sourceExecutable -PathType Leaf)) {
    throw "El paquete no contiene app\GymFortConnector.exe. Usa el ZIP generado en Windows."
}

$CloudBaseUrl = Read-RequiredValue $CloudBaseUrl "URL de GymFort Cloud"
$ConnectorKey = Read-RequiredValue $ConnectorKey "Connector key creada en GymFort"
Assert-HttpUrl $CloudBaseUrl "CloudBaseUrl"
$cloudUri = [Uri]$CloudBaseUrl
if ($cloudUri.Scheme -ne "https" -and -not $cloudUri.IsLoopback) {
    throw "CloudBaseUrl debe utilizar https fuera de pruebas locales."
}

$trustedCertificateBytes = $null
$capabilityProfileBytes = $null
if ($Provider -eq "HIKCENTRAL") {
    $HikCentralBaseUrl = Read-RequiredValue $HikCentralBaseUrl "URL LAN de HikCentral OpenAPI"
    $HikCentralAppKey = Read-RequiredValue $HikCentralAppKey "App Key de HikCentral"
    Assert-HttpUrl $HikCentralBaseUrl "HikCentralBaseUrl"
    $hikCentralUri = [Uri]$HikCentralBaseUrl
    if ($hikCentralUri.Scheme -ne "https") {
        throw "HikCentralBaseUrl debe utilizar https."
    }
    if ($null -eq $ManagedAccessLevelRefs) {
        $ManagedAccessLevelRefs = @()
    }
    $ManagedAccessLevelRefs = @($ManagedAccessLevelRefs | ForEach-Object { $_.Trim() } | Where-Object { $_ })
    if (-not [string]::IsNullOrWhiteSpace($HikCentralTrustedCertificate) -and
        -not (Test-Path -LiteralPath $HikCentralTrustedCertificate -PathType Leaf)) {
        throw "No existe el certificado indicado: $HikCentralTrustedCertificate"
    }
    if (-not [string]::IsNullOrWhiteSpace($HikCentralTrustedCertificate)) {
        $trustedCertificateBytes = [IO.File]::ReadAllBytes(
            (Resolve-Path -LiteralPath $HikCentralTrustedCertificate).Path)
    }
    if (-not [string]::IsNullOrWhiteSpace($HikCentralCapabilityProfile) -and
        -not (Test-Path -LiteralPath $HikCentralCapabilityProfile -PathType Leaf)) {
        throw "No existe el perfil HikCentral indicado: $HikCentralCapabilityProfile"
    }
    if (-not [string]::IsNullOrWhiteSpace($HikCentralCapabilityProfile)) {
        $capabilityProfileBytes = [IO.File]::ReadAllBytes(
            (Resolve-Path -LiteralPath $HikCentralCapabilityProfile).Path)
        try {
            [Text.Encoding]::UTF8.GetString($capabilityProfileBytes) | ConvertFrom-Json | Out-Null
        } catch {
            throw "El perfil HikCentral no contiene JSON valido."
        }
    }
}

$resolvedInstallRoot = [IO.Path]::GetFullPath($InstallRoot)
if ($resolvedInstallRoot.Length -lt 12 -or $resolvedInstallRoot -eq [IO.Path]::GetPathRoot($resolvedInstallRoot)) {
    throw "InstallRoot es demasiado amplio o inseguro."
}
$appDirectory = Join-Path $resolvedInstallRoot "app"
$configDirectory = Join-Path $resolvedInstallRoot "config"
$stateDirectory = Join-Path $resolvedInstallRoot "state"
$configPath = Join-Path $configDirectory "connector.properties"

$existingTask = $false
if (-not $SkipScheduledTask -and (Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue)) {
    $existingTask = $true
    Stop-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 1
}

$backupApp = $null
$backupConfigDirectory = $null

try {
    New-Item -ItemType Directory -Path $resolvedInstallRoot, $stateDirectory -Force | Out-Null
    $backupStamp = Get-Date -Format 'yyyyMMddHHmmss'
    if (Test-Path -LiteralPath $appDirectory) {
        $backupApp = "$appDirectory.backup.$backupStamp"
        Move-Item -LiteralPath $appDirectory -Destination $backupApp
    }
    if (Test-Path -LiteralPath $configDirectory) {
        $backupConfigDirectory = "$configDirectory.backup.$backupStamp"
        Move-Item -LiteralPath $configDirectory -Destination $backupConfigDirectory
    }
    New-Item -ItemType Directory -Path $configDirectory -Force | Out-Null
    Copy-Item -LiteralPath $sourceApp -Destination $appDirectory -Recurse
    $script:connectorExecutable = Join-Path $appDirectory "GymFortConnector.exe"

    $normalizedState = $stateDirectory.Replace("\", "/")
    $configuration = @(
        "cloud.baseUrl=$($CloudBaseUrl.TrimEnd('/'))"
        "connector.key=$ConnectorKey"
        "connector.provider=$Provider"
        "connector.instanceId=$env:COMPUTERNAME"
        "connector.stateDirectory=$normalizedState"
        "connector.pollInterval=PT2S"
        "connector.heartbeatInterval=PT1M"
        "connector.discoveryInterval=PT15M"
        "connector.claimLimit=20"
        "connector.leaseSeconds=90"
    )
    if ($Provider -eq "HIKCENTRAL") {
        $configuration += "hikcentral.baseUrl=$($HikCentralBaseUrl.TrimEnd('/'))"
        $configuration += "hikcentral.appKey=$HikCentralAppKey"
        $configuration += "hikcentral.managedAccessLevelRefs=$($ManagedAccessLevelRefs -join ',')"
        if ($null -ne $capabilityProfileBytes) {
            $installedProfile = Join-Path $configDirectory "hikcentral-profile.json"
            [IO.File]::WriteAllBytes($installedProfile, $capabilityProfileBytes)
            $configuration += "hikcentral.capabilityProfilePath=$($installedProfile.Replace('\', '/'))"
        }
        if ($null -ne $trustedCertificateBytes) {
            $certificateDirectory = Join-Path $configDirectory "certificates"
            New-Item -ItemType Directory -Path $certificateDirectory -Force | Out-Null
            $installedCertificate = Join-Path $certificateDirectory "hikcentral.cer"
            [IO.File]::WriteAllBytes($installedCertificate, $trustedCertificateBytes)
            $configuration += "hikcentral.trustedCertificatePath=$($installedCertificate.Replace('\', '/'))"
        }
    }
    [IO.File]::WriteAllText(
        $configPath,
        ($configuration -join [Environment]::NewLine) + [Environment]::NewLine,
        [Text.UTF8Encoding]::new($false))

    $secretDirectory = Join-Path $stateDirectory "secrets"
    if ($Provider -eq "HIKCENTRAL" -and -not $SkipProviderSecret -and
        -not (Test-Path -LiteralPath (Join-Path $secretDirectory "hikcentral.appSecret.dpapi"))) {
        Write-Host "Captura el App Secret de HikCentral. No se mostrara ni se guardara en la configuracion."
        Invoke-Connector @("--config", $configPath, "set-secret", "hikcentral.appSecret")
    }
    if (-not $SkipActivation -and
        -not (Test-Path -LiteralPath (Join-Path $secretDirectory "cloud.connectorSecret.dpapi"))) {
        Write-Host "Captura el codigo de activacion de un solo uso generado en GymFort."
        Invoke-Connector @("--config", $configPath, "activate")
    }
    if (-not $SkipDoctor) {
        Invoke-Connector @("--config", $configPath, "doctor")
    }

    if (-not $SkipScheduledTask) {
        $currentUser = [Security.Principal.WindowsIdentity]::GetCurrent().Name
        $arguments = "--config `"$configPath`" run"
        $action = New-ScheduledTaskAction -Execute $script:connectorExecutable -Argument $arguments -WorkingDirectory $resolvedInstallRoot
        $trigger = New-ScheduledTaskTrigger -AtLogOn -User $currentUser
        $principal = New-ScheduledTaskPrincipal -UserId $currentUser -LogonType Interactive -RunLevel Limited
        $settings = New-ScheduledTaskSettingsSet `
            -StartWhenAvailable `
            -RestartCount 999 `
            -RestartInterval (New-TimeSpan -Minutes 1) `
            -ExecutionTimeLimit ([TimeSpan]::Zero) `
            -MultipleInstances IgnoreNew
        Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger `
            -Principal $principal -Settings $settings -Description "GymFort connector for physical access" -Force | Out-Null
        Start-ScheduledTask -TaskName $taskName
    }

    Write-Host "GymFort Connector instalado en $resolvedInstallRoot"
    Write-Host "Configuracion: $configPath"
    if ($SkipScheduledTask) {
        Write-Host "La tarea automatica fue omitida."
    } else {
        Write-Host "Tarea '$taskName' registrada para la cuenta actual y arrancada."
    }
} catch {
    if (-not $SkipScheduledTask) {
        Stop-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
    }
    if (Test-Path -LiteralPath $appDirectory) {
        $failedApp = "$appDirectory.failed.$(Get-Date -Format 'yyyyMMddHHmmss')"
        Move-Item -LiteralPath $appDirectory -Destination $failedApp
    }
    if ($backupApp -and (Test-Path -LiteralPath $backupApp)) {
        Move-Item -LiteralPath $backupApp -Destination $appDirectory
    }
    if (Test-Path -LiteralPath $configDirectory) {
        $failedConfig = "$configDirectory.failed.$(Get-Date -Format 'yyyyMMddHHmmss')"
        Move-Item -LiteralPath $configDirectory -Destination $failedConfig
    }
    if ($backupConfigDirectory -and (Test-Path -LiteralPath $backupConfigDirectory)) {
        Move-Item -LiteralPath $backupConfigDirectory -Destination $configDirectory
    }
    if ($existingTask) {
        Start-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
    }
    throw
}
