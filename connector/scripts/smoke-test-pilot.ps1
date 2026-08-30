[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$BundleRoot,
    [string]$WorkingRoot = $env:TEMP
)

$ErrorActionPreference = "Stop"
$bundle = (Resolve-Path -LiteralPath $BundleRoot).Path
$installer = Join-Path $bundle "scripts\install-pilot.ps1"
$backupScript = Join-Path $bundle "scripts\backup-connector.ps1"
$uninstaller = Join-Path $bundle "scripts\uninstall-pilot.ps1"
foreach ($required in @($installer, $backupScript, $uninstaller, (Join-Path $bundle "app\GymFortConnector.exe"))) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Pilot bundle is incomplete: $required"
    }
}

$testRoot = Join-Path ([IO.Path]::GetFullPath($WorkingRoot)) "GymFortConnectorSmoke-$([Guid]::NewGuid().ToString('N'))"
$installRoot = Join-Path $testRoot "installed"
$backupRoot = Join-Path $testRoot "backups"
New-Item -ItemType Directory -Path $testRoot -Force | Out-Null

try {
    $rejectedRoot = Join-Path $testRoot "insecure-cloud"
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $installer `
        -InstallRoot $rejectedRoot `
        -CloudBaseUrl "http://gymfort.example" `
        -ConnectorKey "security-smoke" `
        -Provider SIMULATOR `
        -SkipActivation -SkipProviderSecret -SkipDoctor -SkipScheduledTask
    if ($LASTEXITCODE -eq 0) {
        throw "Non-loopback Cloud HTTP was unexpectedly accepted."
    }
    if (Test-Path -LiteralPath $rejectedRoot) {
        throw "Rejected insecure installation changed its target directory."
    }

    $profileInstallRoot = Join-Path $testRoot "profile-install"
    $profileSource = Join-Path $bundle "config\hikcentral-profile.example.json"
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $installer `
        -InstallRoot $profileInstallRoot `
        -CloudBaseUrl "http://localhost:8080" `
        -ConnectorKey "profile-smoke" `
        -Provider HIKCENTRAL `
        -HikCentralBaseUrl "https://hikcentral.invalid:8443" `
        -HikCentralAppKey "profile-app-key" `
        -ManagedAccessLevelRefs "CENTRO_GENERAL" `
        -HikCentralCapabilityProfile $profileSource `
        -SkipActivation -SkipProviderSecret -SkipDoctor -SkipScheduledTask
    if ($LASTEXITCODE -ne 0) {
        throw "HikCentral profile installation failed with code $LASTEXITCODE."
    }
    $profileConfiguration = Get-Content -LiteralPath (
        Join-Path $profileInstallRoot "config\connector.properties") -Raw
    if (-not $profileConfiguration.Contains("hikcentral.capabilityProfilePath=")) {
        throw "Installer did not activate the supplied HikCentral capability profile."
    }
    $installedProfile = Join-Path $profileInstallRoot "config\hikcentral-profile.json"
    if (-not (Test-Path -LiteralPath $installedProfile -PathType Leaf) -or
        (Get-FileHash -LiteralPath $installedProfile -Algorithm SHA256).Hash -ne
        (Get-FileHash -LiteralPath $profileSource -Algorithm SHA256).Hash) {
        throw "Installed HikCentral capability profile differs from the supplied file."
    }
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $uninstaller `
        -InstallRoot $profileInstallRoot
    if ($LASTEXITCODE -ne 0) {
        throw "Profile smoke uninstall failed with code $LASTEXITCODE."
    }

    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $installer `
        -InstallRoot $installRoot `
        -CloudBaseUrl "http://localhost:8080" `
        -ConnectorKey "smoke-connector" `
        -Provider SIMULATOR `
        -SkipActivation -SkipProviderSecret -SkipDoctor -SkipScheduledTask
    if ($LASTEXITCODE -ne 0) {
        throw "Fresh installation failed with code $LASTEXITCODE."
    }

    $configPath = Join-Path $installRoot "config\connector.properties"
    $executable = Join-Path $installRoot "app\GymFortConnector.exe"
    $expectedConfiguration = Get-Content -LiteralPath $configPath -Raw
    $previousCertificate = Join-Path $installRoot "config\certificates\hikcentral.cer"
    New-Item -ItemType Directory -Path (Split-Path $previousCertificate) -Force | Out-Null
    [IO.File]::WriteAllBytes($previousCertificate, [Text.Encoding]::ASCII.GetBytes("previous-certificate"))
    $expectedCertificate = [IO.File]::ReadAllBytes($previousCertificate)
    $env:GYMFORT_CONNECTOR_SECRET = "smoke-secret"
    $env:GYMFORT_CONNECTOR_LOG_DIR = Join-Path $installRoot "runtime\logs"
    & $executable --config $configPath doctor
    if ($LASTEXITCODE -ne 0) {
        throw "Installed simulator doctor failed with code $LASTEXITCODE."
    }

    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $backupScript `
        -InstallRoot $installRoot -DestinationDirectory $backupRoot
    if ($LASTEXITCODE -ne 0) {
        throw "Backup failed with code $LASTEXITCODE."
    }
    $backup = Get-ChildItem -LiteralPath $backupRoot -Filter "*.zip" | Select-Object -First 1
    if (-not $backup -or $backup.Length -le 0) {
        throw "Backup did not produce a non-empty ZIP."
    }

    Remove-Item Env:GYMFORT_HIKCENTRAL_APP_SECRET -ErrorAction SilentlyContinue
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $installer `
        -InstallRoot $installRoot `
        -CloudBaseUrl "http://localhost:8080" `
        -ConnectorKey "smoke-connector" `
        -Provider HIKCENTRAL `
        -HikCentralBaseUrl "https://hikcentral.invalid:8443" `
        -HikCentralAppKey "smoke-app-key" `
        -ManagedAccessLevelRefs "CENTRO_GENERAL" `
        -HikCentralTrustedCertificate $previousCertificate `
        -SkipActivation -SkipProviderSecret -SkipScheduledTask
    if ($LASTEXITCODE -eq 0) {
        throw "Invalid upgrade unexpectedly succeeded."
    }

    $restoredConfiguration = Get-Content -LiteralPath $configPath -Raw
    if ($restoredConfiguration -ne $expectedConfiguration) {
        throw "Installer rollback did not restore the previous configuration."
    }
    if (-not (Test-Path -LiteralPath $executable -PathType Leaf)) {
        throw "Installer rollback did not restore the previous application image."
    }
    $restoredCertificate = [IO.File]::ReadAllBytes($previousCertificate)
    if ([Convert]::ToBase64String($restoredCertificate) -ne [Convert]::ToBase64String($expectedCertificate)) {
        throw "Installer rollback did not restore the previous certificate directory."
    }
    & $executable --config $configPath doctor
    if ($LASTEXITCODE -ne 0) {
        throw "Restored installation is not healthy."
    }

    Write-Host "Pilot package smoke test passed: $testRoot"
} finally {
    Remove-Item Env:GYMFORT_CONNECTOR_SECRET -ErrorAction SilentlyContinue
    Remove-Item Env:GYMFORT_CONNECTOR_LOG_DIR -ErrorAction SilentlyContinue
    if (Test-Path -LiteralPath $installRoot) {
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $uninstaller -InstallRoot $installRoot
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "Recoverable uninstall failed with code $LASTEXITCODE."
        }
    }
}
