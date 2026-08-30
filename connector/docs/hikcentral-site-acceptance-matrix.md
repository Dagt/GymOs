# HikCentral site acceptance matrix

Status legend: `PASS-AUTO` automated without hardware, `PENDING-SITE` requires
the real installation, `BLOCKED-NEXT` needs a later connector capability.

Test topology:

- Centro: General turnstile, Sauna, Cryotherapy and Premium Lockers.
- Tecnológica: General access only.
- HikCentral and the facial turnstile are physically located at Centro.

| ID | Scenario | Expected result | Current evidence/status |
|---|---|---|---|
| HC-COMP-01 | Query HikCentral version with configured AK/SK | Version returned; no credentials logged | Signed HTTP contract `PASS-AUTO`; live `PENDING-SITE` |
| HC-COMP-02 | OpenAPI package does not match the installed release | `doctor` is degraded; mutations remain disabled | `PENDING-SITE` |
| HC-COMP-03 | Site uses a private/self-signed TLS chain | Works only with its explicit public certificate; no insecure bypass | Policy implemented; `PENDING-SITE` |
| HC-CFG-01 | A configured GymFort Access Level is absent | `doctor` lists it and stays degraded | `PASS-AUTO` |
| HC-CFG-02 | Cloud requests an unmanaged Access Level | Job fails terminally before provider mutation | `PASS-AUTO` |
| HC-CFG-03 | Person has an unrelated manual HikCentral privilege | Reconciliation preserves it | `PASS-AUTO` |
| HC-ID-01 | Centro member already exists in HikCentral | Connector imports candidate; human confirms exact link; desired levels synchronize | Candidate/discovery + provider sync `PASS-AUTO`; live `PENDING-SITE` |
| HC-ID-02 | Person name is similar but email/phone are ambiguous | Never auto-link by fuzzy name | Cloud rule implemented; `PENDING-SITE` |
| HC-ID-03 | New GymFort client has no HikCentral person | No rights job is applied until enrollment/link exists | Fail-closed and candidates `PASS-AUTO`; local face/person creation `PENDING-SITE` |
| HC-ID-04 | Client bought at Tecnológica but has valid Centro coverage | Same organization client links once and receives Centro rights | Cloud semantics implemented; end-to-end `PENDING-SITE` |
| HC-ID-05 | Facial template is captured | Template stays in HikCentral; GymFort stores only opaque person ref | Privacy design; `PENDING-SITE` |
| HC-MEM-01 | Centro-only General membership becomes active | Assign Centro General only | Driver exact-set `PASS-AUTO`; live `PENDING-SITE` |
| HC-MEM-02 | Multi-branch plan sold at Tecnológica grants Centro General | Assign Centro General despite sale/home branch | Cloud calculation covered; live `PENDING-SITE` |
| HC-MEM-03 | Plan grants Centro Sauna but not Tecnológica | Assign only the Access Level mapped to Centro Sauna | Cloud coverage covered; live `PENDING-SITE` |
| HC-MEM-04 | Membership renews without changing rights | No duplicate person or privilege | Idempotent reconcile `PASS-AUTO`; live `PENDING-SITE` |
| HC-MEM-05 | Plan changes and loses Sauna | Remove managed Sauna, retain remaining/general rights | `PASS-AUTO`; live `PENDING-SITE` |
| HC-MEM-06 | Membership expires or client becomes inactive | Remove all GymFort-managed rights | REVOKE contract `PASS-AUTO`; live `PENDING-SITE` |
| HC-PKG-01 | Five standalone Sauna sessions, Sauna consumes a visit | Face event consumes exactly one visit, then opens once | Online authorization/actuation `PASS-AUTO`; live `PENDING-SITE` |
| HC-PKG-02 | General consumes; Sauna is included same day at Centro | General consumes once; Sauna opens without second decrement | Domain + online path `PASS-AUTO`; live `PENDING-SITE` |
| HC-PKG-03 | Included Sauna attempted before General | Denied and turnstile/door stays closed | Domain + no-actuation path `PASS-AUTO`; live `PENDING-SITE` |
| HC-PKG-04 | Centro-only Sauna attempted at Tecnológica | Denied; no Sauna right/device is fabricated there | Coverage + online path `PASS-AUTO`; live `PENDING-SITE` |
| HC-PKG-05 | User also has membership General | Membership entry does not consume package; strict included rule remains | Membership priority + online path `PASS-AUTO`; live `PENDING-SITE` |
| HC-DEL-01 | Same cloud job is delivered twice | Provider mutation happens once/equivalent exact state | `PASS-AUTO` |
| HC-DEL-02 | Provider succeeds and Cloud completion fails | Restart/reclaim completes Cloud without repeating provider call | `PASS-AUTO` |
| HC-DEL-03 | Connector stops while holding a lease | Lease expires; a new token reclaims safely | Cloud covered; local/live `PENDING-SITE` |
| HC-DEL-04 | Two desired revisions exist for one person | Final provider state equals newest exact set | Cloud serialization covered; live `PENDING-SITE` |
| HC-OUT-01 | Internet/Cloud unavailable | Existing local HikCentral policy remains; Connector never opens by default | Daemon fail-safe implemented; `PENDING-SITE` |
| HC-OUT-02 | HikCentral unavailable | Heartbeat becomes degraded; jobs retry with bounded delay | Local behavior implemented; `PENDING-SITE` |
| HC-OUT-03 | Connector restarts after Windows logon | Durable ledger resumes; pilot task restarts under the installation account | Ledger/package smoke `PASS-AUTO`; live `PENDING-SITE` |
| HC-EVT-01 | Duplicate HikCentral event arrives | One cloud access log, no duplicate visit consumption | Cloud + SQLite event idempotency `PASS-AUTO`; live `PENDING-SITE` |
| HC-EVT-02 | Windows restarts while remote open is in flight | Connector does not open twice; marks actuation uncertain for review | Fail-safe recovery `PASS-AUTO`; live `PENDING-SITE` |
| HC-SEC-01 | Secret files/config/logs are inspected | No plaintext Connector/App Secret or biometric payload | DPAPI/config/log policy implemented; deployment `PENDING-SITE` |
| HC-SEC-02 | Connector credential is revoked | Cloud mutations stop and surface authentication failure | Cloud covered; live `PENDING-SITE` |
| HC-SEC-03 | Cross-organization reference is attempted | No job/person/device escapes authenticated tenant/branch | Cloud integration covered; live negative test `PENDING-SITE` |
| HC-SEC-04 | Pilot ZIP is modified after build | SHA-256 verification fails; modified copy is not installed | Checksum generation/verification `PASS-AUTO`; Authenticode signing `BLOCKED-NEXT` |

Production approval requires every relevant `PENDING-SITE` row to pass and every
`BLOCKED-NEXT` row used by the client to be implemented first. Passing the local
test suite alone is not production certification.
