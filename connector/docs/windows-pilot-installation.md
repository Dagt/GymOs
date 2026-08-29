# Windows pilot installation

This runbook installs a self-contained Connector on the Windows PC that can
reach HikCentral. Java, Gradle and the GymFort source repository are not needed
on that PC.

## 1. Prepare GymFort

In Centro, as Owner or Manager:

1. create/enable the `HIKCENTRAL` physical connection;
2. map every GymFort ACCESS benefit to its real HikCentral Access Level;
3. link the Centro turnstile/device to its external HikCentral resource;
4. generate a one-time Connector activation code, but do not send it through
   chat or store it in a text file.

The Connector connection belongs to Centro. A membership sold in Tecnológica
can still produce Centro rights when its GymFort coverage includes Centro.

## 2. Prepare HikCentral

Obtain from the HikCentral administrator:

- HikCentral Professional version and matching OpenAPI package version;
- HTTPS OpenAPI URL using the DNS name present in its certificate;
- partner App Key and App Secret with only the required access-control scope;
- IDs of the Access Levels that GymFort may manage;
- exported public X.509 certificate (`.cer`) when the server uses a private CA.

Hikvision publishes the [HikCentral Professional integration
portal](https://tpp.hikvision.com/eu/HCPIntegration) and its [OpenAPI getting
started guide](https://tpp.hikvision.com/pd/GettingStartedGuide.pdf). The
portal explicitly distributes OpenAPI packages by HikCentral release; use the
package matching the installed HCP version instead of assuming that every
release exposes an identical contract.

Do not export a private key. Do not use `https://127.0.0.1` unless the
certificate explicitly contains that IP as a Subject Alternative Name.

## 3. Build the pilot ZIP

On a trusted Windows build machine, from the repository root:

```powershell
backend\gradlew.bat -p connector clean test pilotChecksum
```

The result is created under `connector/build/distributions`. The ZIP contains a
Windows application image with Java 25, scripts, example configuration and this
documentation. Compare the copied ZIP with the adjacent `.sha256` file before
extracting it on the gym PC.

```powershell
Get-FileHash .\GymFortConnector-0.1.0-Windows-11-amd64.zip -Algorithm SHA256
Get-Content .\GymFortConnector-0.1.0-Windows-11-amd64.zip.sha256
```

The hexadecimal values must be identical (letter case is irrelevant).

The pilot ZIP has an integrity checksum but is not yet Authenticode-signed. A
production release still needs a protected code-signing pipeline. Do not bypass
SmartScreen or execute a copy whose hash differs from the published checksum.

## 4. Install on the HikCentral LAN PC

Extract the ZIP locally. Do not execute it from inside the compressed archive.
Open PowerShell as the Windows account that will keep the Connector running:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
cd .\GymFortConnector-pilot

.\scripts\install-pilot.ps1 `
  -CloudBaseUrl "https://api.gymfort.mx" `
  -ConnectorKey "access-centro-01" `
  -Provider HIKCENTRAL `
  -HikCentralBaseUrl "https://hikcentral-centro:8443" `
  -HikCentralAppKey "APP_KEY" `
  -ManagedAccessLevelRefs "CENTRO_GENERAL","CENTRO_SAUNA" `
  -HikCentralTrustedCertificate "C:\Temp\hikcentral-centro.cer"
```

The installer interactively requests the App Secret and then the one-time
GymFort activation code. Both are protected with Windows DPAPI and are not
written to the properties file. `doctor` must pass before the automatic task is
registered.

The pilot task runs at logon under that same Windows account and restarts after
failure. For the test period that account must remain logged in. A later
production service wrapper is required for operation before logon.

## 5. Operate and diagnose

```powershell
.\scripts\connector-control.ps1 -Action Status
.\scripts\connector-control.ps1 -Action Doctor
.\scripts\connector-control.ps1 -Action Restart
.\scripts\connector-control.ps1 -Action Logs
```

`doctor` verifies the Cloud secret, HikCentral signature/authentication, live
version and all configured managed Access Levels. It must be run after changing
HikCentral, its certificate or its OpenAPI package.

Create a recoverable backup before an upgrade:

```powershell
.\scripts\backup-connector.ps1
```

Re-running `install-pilot.ps1` replaces the application image and supplied
configuration, preserves DPAPI secrets/state and keeps timestamped backups of
the previous image and configuration directory. If `doctor` rejects the new
version or configuration, the installer restores both automatically and
restarts the previous task.

## 6. Link the first test member

Current pilot flow:

1. create or locate the person in HikCentral and capture the face there;
2. obtain the opaque HikCentral person ID;
3. in GymFort, open Access → Physical integration → Person and paste that
   external reference for the exact member;
4. force reconciliation once or wait for the queued synchronization;
5. confirm in GymFort that desired state is `SYNCED` and in HikCentral that only
   the expected managed Access Levels were assigned;
6. test recognition at the closed turnstile with reception present.

Never link by name alone. Automatic person creation/candidate import remains a
subsequent increment.

## 7. Stop or remove the pilot

```powershell
.\scripts\connector-control.ps1 -Action Stop
.\scripts\uninstall-pilot.ps1
```

Uninstall is recoverable by default: it unregisters the task and moves the
installation to a timestamped sibling directory. `-PurgeData` permanently
deletes configuration, DPAPI secrets and the local ledger and must only be used
after a verified backup.

## 8. Test boundary

The pilot can reconcile long-lived membership Access Levels. It does not yet
consume a visit package from a face event or remotely open the turnstile. Do not
assign a permanent Access Level for a metered package: that would bypass
GymFort's balance and idempotency rules. Execute the site matrix and keep the
physical system attended until the remaining event/opening path is built and
accepted.
