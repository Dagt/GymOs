[CmdletBinding()]
param(
    [string]$InstallRoot = "$env:ProgramData\GymFort\Connector",
    [string]$GymFortApiUrl,
    [string]$GymFortWebUrl,
    [string]$ConnectorKey = "acceso-centro-01",
    [ValidateSet("HIKCENTRAL", "SIMULATOR")]
    [string]$Provider = "HIKCENTRAL",
    [ValidateSet("BASIC", "ONLINE")]
    [string]$HikCentralMode,
    [string]$HikCentralBaseUrl,
    [string]$HikCentralAppKey,
    [string[]]$ManagedAccessLevelRefs,
    [string]$HikCentralTrustedCertificate,
    [string]$HikCentralCapabilityProfile,
    [switch]$NoBrowser,
    [switch]$AcceptPresentedCertificate,
    [switch]$NonInteractive,
    [switch]$SkipNetworkChecks,
    [switch]$SkipActivation,
    [switch]$SkipProviderSecret,
    [switch]$SkipDoctor,
    [switch]$SkipScheduledTask
)

$ErrorActionPreference = "Stop"
$utf8 = [Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = $utf8
$OutputEncoding = $utf8

function Write-Step {
    param([int]$Number, [string]$Title)
    Write-Host ""
    Write-Host "[$Number] $Title" -ForegroundColor Cyan
}

function Read-RequiredValue {
    param([string]$Current, [string]$Prompt)
    if (-not [string]::IsNullOrWhiteSpace($Current)) {
        return $Current.Trim()
    }
    if ($NonInteractive) {
        throw "$Prompt es obligatorio en modo no interactivo."
    }
    $value = Read-Host $Prompt
    if ([string]::IsNullOrWhiteSpace($value) -or $value.Contains("`r") -or $value.Contains("`n")) {
        throw "$Prompt es obligatorio y debe ocupar una sola linea."
    }
    return $value.Trim()
}

function Read-YesNo {
    param([string]$Prompt, [bool]$Default = $true)
    if ($NonInteractive) {
        return $Default
    }
    $suffix = if ($Default) { "[S/n]" } else { "[s/N]" }
    $answer = (Read-Host "$Prompt $suffix").Trim().ToLowerInvariant()
    if ([string]::IsNullOrWhiteSpace($answer)) {
        return $Default
    }
    return $answer -in @("s", "si", "sí", "y", "yes")
}

function Normalize-GymFortApiUrl {
    param([string]$Value)
    $normalized = $Value.Trim().TrimEnd('/')
    if ($normalized.EndsWith("/api/v1", [StringComparison]::OrdinalIgnoreCase)) {
        $normalized = $normalized.Substring(0, $normalized.Length - 7)
    }
    return $normalized
}

function Test-HttpEndpoint {
    param([string]$Url, [int]$TimeoutSeconds = 3)
    try {
        $request = [Net.HttpWebRequest]::Create($Url)
        $request.Method = "GET"
        $request.Timeout = $TimeoutSeconds * 1000
        $request.ReadWriteTimeout = $TimeoutSeconds * 1000
        $request.AllowAutoRedirect = $false
        $response = $request.GetResponse()
        try {
            return [int]$response.StatusCode -ge 200 -and [int]$response.StatusCode -lt 500
        } finally {
            $response.Dispose()
        }
    } catch [Net.WebException] {
        if ($null -ne $_.Exception.Response) {
            $_.Exception.Response.Dispose()
            return $true
        }
        return $false
    } catch {
        return $false
    }
}

function Find-LocalGymFortApi {
    foreach ($candidate in @("http://127.0.0.1:8080", "http://localhost:8080")) {
        if (Test-HttpEndpoint "$candidate/actuator/health") {
            return $candidate
        }
    }
    return $null
}

function Find-LocalGymFortWeb {
    foreach ($candidate in @("http://127.0.0.1:3000", "http://localhost:3000")) {
        if (Test-HttpEndpoint $candidate) {
            return $candidate
        }
    }
    return $null
}

function Assert-TcpEndpoint {
    param([Uri]$Uri, [string]$Description)
    $client = [Net.Sockets.TcpClient]::new()
    try {
        $operation = $client.ConnectAsync($Uri.DnsSafeHost, $Uri.Port)
        if (-not $operation.Wait([TimeSpan]::FromSeconds(5)) -or -not $client.Connected) {
            throw "No fue posible conectar."
        }
    } catch {
        throw "$Description no responde en $($Uri.DnsSafeHost):$($Uri.Port). Revisa red, DNS y firewall."
    } finally {
        $client.Dispose()
    }
}

function Export-PresentedCertificate {
    param([Uri]$Uri, [string]$Destination)
    $holder = [Collections.Generic.List[Security.Cryptography.X509Certificates.X509Certificate2]]::new()
    $callback = [Net.Security.RemoteCertificateValidationCallback]{
        param($sender, $certificate, $chain, $policyErrors)
        if ($null -ne $certificate -and $holder.Count -eq 0) {
            $holder.Add([Security.Cryptography.X509Certificates.X509Certificate2]::new($certificate))
        }
        return $true
    }
    $client = [Net.Sockets.TcpClient]::new()
    $stream = $null
    try {
        $client.Connect($Uri.DnsSafeHost, $Uri.Port)
        $stream = [Net.Security.SslStream]::new($client.GetStream(), $false, $callback)
        $stream.AuthenticateAsClient($Uri.DnsSafeHost)
        if ($holder.Count -eq 0) {
            throw "HikCentral no presento un certificado TLS."
        }
        $certificate = $holder[0]
        $sha = [Security.Cryptography.SHA256]::Create()
        try {
            $fingerprint = -join ($sha.ComputeHash($certificate.RawData) | ForEach-Object { $_.ToString("X2") })
        } finally {
            $sha.Dispose()
        }
        Write-Host "Certificado presentado por HikCentral:" -ForegroundColor Yellow
        Write-Host "  Subject:    $($certificate.Subject)"
        Write-Host "  Issuer:     $($certificate.Issuer)"
        Write-Host "  Valido:     $($certificate.NotBefore) a $($certificate.NotAfter)"
        Write-Host "  SHA-256:    $fingerprint"
        if (-not $AcceptPresentedCertificate -and
            -not (Read-YesNo "Confirmas con el administrador que este certificado es el correcto?" $false)) {
            throw "El certificado no fue confirmado. No se modifico la confianza TLS."
        }
        [IO.File]::WriteAllBytes(
            $Destination,
            $certificate.Export([Security.Cryptography.X509Certificates.X509ContentType]::Cert))
        return $Destination
    } finally {
        if ($null -ne $stream) { $stream.Dispose() }
        $client.Dispose()
        foreach ($certificate in $holder) { $certificate.Dispose() }
    }
}

function Test-TlsTrusted {
    param([Uri]$Uri)
    $client = [Net.Sockets.TcpClient]::new()
    $stream = $null
    try {
        $client.Connect($Uri.DnsSafeHost, $Uri.Port)
        $stream = [Net.Security.SslStream]::new($client.GetStream(), $false)
        $stream.AuthenticateAsClient($Uri.DnsSafeHost)
        return $true
    } catch {
        return $false
    } finally {
        if ($null -ne $stream) { $stream.Dispose() }
        $client.Dispose()
    }
}

function Assert-ProfileReady {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "No existe el perfil HikCentral: $Path"
    }
    $content = Get-Content -Raw -LiteralPath $Path
    try {
        $content | ConvertFrom-Json | Out-Null
    } catch {
        throw "El perfil HikCentral no contiene JSON valido."
    }
    if ($content.Contains("REPLACE_FROM_INSTALLED_OPENAPI")) {
        throw "El perfil todavia contiene REPLACE_FROM_INSTALLED_OPENAPI. Completa los endpoints antes del modo ONLINE."
    }
}

if (-not $IsWindows -and $PSVersionTable.PSVersion.Major -ge 6) {
    throw "El asistente local actual solo admite Windows."
}

$bundleRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$installer = Join-Path $PSScriptRoot "install-pilot.ps1"
$control = Join-Path $PSScriptRoot "connector-control.ps1"
foreach ($required in @($installer, $control, (Join-Path $bundleRoot "app\GymFortConnector.exe"))) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "El paquete esta incompleto: $required"
    }
}

Write-Host "============================================================" -ForegroundColor DarkCyan
Write-Host " GymFort Connector - asistente para prueba local" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor DarkCyan
Write-Host "Este asistente supone que GymFort corre en esta misma laptop."

$temporaryCertificate = $null
try {
    Write-Step 1 "Detectar GymFort local"
    if ([string]::IsNullOrWhiteSpace($GymFortApiUrl) -and -not $SkipNetworkChecks) {
        $GymFortApiUrl = Find-LocalGymFortApi
    }
    if ([string]::IsNullOrWhiteSpace($GymFortApiUrl)) {
        $GymFortApiUrl = Read-RequiredValue $null "URL local del backend GymFort (normalmente http://127.0.0.1:8080)"
    }
    $GymFortApiUrl = Normalize-GymFortApiUrl $GymFortApiUrl
    if (-not $SkipNetworkChecks -and -not (Test-HttpEndpoint "$GymFortApiUrl/actuator/health")) {
        throw "GymFort no responde en $GymFortApiUrl. Inicia primero el backend local."
    }
    Write-Host "GymFort API detectada: $GymFortApiUrl" -ForegroundColor Green

    if ([string]::IsNullOrWhiteSpace($GymFortWebUrl) -and -not $SkipNetworkChecks) {
        $GymFortWebUrl = Find-LocalGymFortWeb
    }
    if ([string]::IsNullOrWhiteSpace($GymFortWebUrl)) {
        $GymFortWebUrl = "http://127.0.0.1:3000"
    }
    $GymFortWebUrl = $GymFortWebUrl.Trim().TrimEnd('/')
    if (-not $SkipNetworkChecks -and -not (Test-HttpEndpoint $GymFortWebUrl)) {
        Write-Warning "La interfaz no respondio en $GymFortWebUrl. Puedes continuar, pero deberas abrirla manualmente."
    }

    Write-Step 2 "Preparar la conexion en GymFort"
    $ConnectorKey = Read-RequiredValue $ConnectorKey "ID publico del Connector"
    Write-Host "En GymFort selecciona Centro y abre Accesos > Configuracion fisica."
    Write-Host "Guarda una conexion HikCentral con el ID: $ConnectorKey"
    Write-Host "Despues pulsa 'Generar activacion'. El instalador pedira ese codigo una sola vez."
    if (-not $NoBrowser -and -not $NonInteractive) {
        Start-Process "$GymFortWebUrl/access"
        [void](Read-Host "Presiona ENTER cuando la conexion este guardada y tengas visible el codigo")
    }

    $installerArguments = @{
        InstallRoot = $InstallRoot
        CloudBaseUrl = $GymFortApiUrl
        ConnectorKey = $ConnectorKey
        Provider = $Provider
    }

    if ($Provider -eq "HIKCENTRAL") {
        Write-Step 3 "Comprobar HikCentral"
        $HikCentralBaseUrl = Read-RequiredValue $HikCentralBaseUrl "URL HTTPS de HikCentral OpenAPI"
        $parsedHikCentralUrl = $null
        if (-not [Uri]::TryCreate($HikCentralBaseUrl, [UriKind]::Absolute, [ref]$parsedHikCentralUrl) -or
            $parsedHikCentralUrl.Scheme -ne "https") {
            throw "HikCentral debe utilizar una URL HTTPS valida."
        }
        if (-not $SkipNetworkChecks) {
            Assert-TcpEndpoint $parsedHikCentralUrl "HikCentral"
            Write-Host "HikCentral responde por TCP." -ForegroundColor Green
        }
        $HikCentralAppKey = Read-RequiredValue $HikCentralAppKey "App Key de HikCentral"

        if ([string]::IsNullOrWhiteSpace($HikCentralMode)) {
            if ($NonInteractive) {
                $HikCentralMode = "BASIC"
            } else {
                Write-Host "1) BASICA: membresias y Access Levels; recomendada para la primera prueba."
                Write-Host "2) ONLINE: paquetes, eventos y apertura remota; requiere perfil OpenAPI terminado."
                $modeChoice = (Read-Host "Elige 1 o 2 [1]").Trim()
                $HikCentralMode = if ($modeChoice -eq "2") { "ONLINE" } else { "BASIC" }
            }
        }

        if ($HikCentralMode -eq "BASIC" -and $null -eq $ManagedAccessLevelRefs) {
            if ($NonInteractive) {
                $ManagedAccessLevelRefs = @()
            } else {
                $rawLevels = Read-Host "IDs de Access Levels administrados por GymFort, separados por coma (puede quedar vacio)"
                $ManagedAccessLevelRefs = @($rawLevels.Split(',') | ForEach-Object { $_.Trim() } | Where-Object { $_ })
            }
        }
        if ($HikCentralMode -eq "ONLINE") {
            $HikCentralCapabilityProfile = Read-RequiredValue $HikCentralCapabilityProfile "Ruta del perfil HikCentral terminado"
            Assert-ProfileReady $HikCentralCapabilityProfile
            if ($null -eq $ManagedAccessLevelRefs) { $ManagedAccessLevelRefs = @() }
        } elseif (-not [string]::IsNullOrWhiteSpace($HikCentralCapabilityProfile)) {
            Assert-ProfileReady $HikCentralCapabilityProfile
        }

        if ([string]::IsNullOrWhiteSpace($HikCentralTrustedCertificate) -and -not $SkipNetworkChecks) {
            $alreadyTrusted = -not $AcceptPresentedCertificate -and (Test-TlsTrusted $parsedHikCentralUrl)
            if ($alreadyTrusted) {
                Write-Host "El certificado TLS de HikCentral ya es confiable para Windows." -ForegroundColor Green
            }
            $captureCertificate = -not $alreadyTrusted -and (
                $AcceptPresentedCertificate -or
                (Read-YesNo "La cadena TLS no es confiable. Quieres capturar y confirmar el certificado publico presentado?" $true))
            if ($captureCertificate) {
                $temporaryCertificate = Join-Path $env:TEMP "gymfort-hikcentral-$([Guid]::NewGuid().ToString('N')).cer"
                $HikCentralTrustedCertificate = Export-PresentedCertificate $parsedHikCentralUrl $temporaryCertificate
            }
        }

        $installerArguments.HikCentralBaseUrl = $HikCentralBaseUrl
        $installerArguments.HikCentralAppKey = $HikCentralAppKey
        $installerArguments.ManagedAccessLevelRefs = @($ManagedAccessLevelRefs)
        if (-not [string]::IsNullOrWhiteSpace($HikCentralTrustedCertificate)) {
            $installerArguments.HikCentralTrustedCertificate = $HikCentralTrustedCertificate
        }
        if (-not [string]::IsNullOrWhiteSpace($HikCentralCapabilityProfile)) {
            $installerArguments.HikCentralCapabilityProfile = $HikCentralCapabilityProfile
        }
    }

    foreach ($switchName in @(
        "SkipActivation", "SkipProviderSecret", "SkipDoctor", "SkipScheduledTask")) {
        if ((Get-Variable -Name $switchName -ValueOnly)) {
            $installerArguments[$switchName] = $true
        }
    }

    Write-Step 4 "Instalar y diagnosticar"
    & $installer @installerArguments

    if (-not $SkipScheduledTask) {
        & $control -Action Status -InstallRoot $InstallRoot
    }

    Write-Step 5 "Resultado"
    Write-Host "Connector instalado correctamente." -ForegroundColor Green
    Write-Host "Ahora vuelve a GymFort > Accesos > Configuracion fisica."
    Write-Host "Comprueba que exista un heartbeat reciente y vincula el torniquete descubierto."
    if ($Provider -eq "HIKCENTRAL" -and $HikCentralMode -eq "BASIC") {
        Write-Host "La primera etapa queda en modo BASICO. No actives GYMFORT_ONLINE todavia." -ForegroundColor Yellow
    }
    Write-Host "Diagnostico posterior: .\scripts\connector-control.ps1 -Action Doctor"
    Write-Host "Logs:                  .\scripts\connector-control.ps1 -Action Logs"
} finally {
    if ($null -ne $temporaryCertificate -and (Test-Path -LiteralPath $temporaryCertificate)) {
        Remove-Item -LiteralPath $temporaryCertificate -Force
    }
}
