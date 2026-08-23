import { useState, type FormEvent } from "react";
import type {
  AccessDecision,
  AccessDevice,
  AccessDevicePayload,
  AccessLog,
  ClientSummary,
  PhysicalAccessBenefit,
  PhysicalClientState,
  PhysicalIntegration,
} from "@/lib/api";

export function AccessOperationSection({
  availableBenefits,
  busy,
  clients,
  deviceCode,
  deviceRequiredBenefitIds,
  devices,
  identifier,
  lastDecision,
  manualReason,
  maxUses,
  query,
  selectedClient,
  selectedClientId,
  temporaryHours,
  temporaryReason,
  onDeviceCodeChange,
  onEvaluate,
  onIdentifierChange,
  onManualReasonChange,
  onOpenManually,
  onCreatePass,
  onMaxUsesChange,
  onQueryChange,
  onSearch,
  onSelectClient,
  onTemporaryHoursChange,
  onTemporaryReasonChange,
}: {
  availableBenefits: PhysicalAccessBenefit[];
  busy: boolean;
  clients: ClientSummary[];
  deviceCode: string;
  deviceRequiredBenefitIds: string[];
  devices: AccessDevice[];
  identifier: string;
  lastDecision: AccessDecision | null;
  manualReason: string;
  maxUses: number;
  query: string;
  selectedClient: ClientSummary | null;
  selectedClientId: string;
  temporaryHours: number;
  temporaryReason: string;
  onDeviceCodeChange: (value: string) => void;
  onEvaluate: () => void;
  onIdentifierChange: (value: string) => void;
  onManualReasonChange: (value: string) => void;
  onOpenManually: () => void;
  onCreatePass: () => void;
  onMaxUsesChange: (value: number) => void;
  onQueryChange: (value: string) => void;
  onSearch: (event: FormEvent<HTMLFormElement>) => void;
  onSelectClient: (client: ClientSummary) => void;
  onTemporaryHoursChange: (value: number) => void;
  onTemporaryReasonChange: (value: string) => void;
}) {
  const selectedDevice =
    devices.find((device) => device.deviceCode === deviceCode) ?? null;
  const requiredBenefits = availableBenefits.filter((benefit) =>
    deviceRequiredBenefitIds.includes(benefit.id),
  );

  return (
    <section className="grid gap-6">
      <section className="overflow-hidden rounded-2xl border border-[var(--brand-primary-border)] bg-[var(--brand-primary)] text-white shadow-sm">
        <div className="grid gap-5 p-6 lg:grid-cols-[minmax(0,1fr)_auto] lg:items-end">
          <div>
            <p className="text-xs font-black uppercase tracking-[0.18em] text-[var(--brand-accent)]">
              Operación en recepción
            </p>
            <h2 className="mt-2 text-2xl font-black tracking-tight">
              Validar una entrada
            </h2>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-white/75">
              Elige el punto físico, identifica al socio y valida sus derechos.
              Cada resultado queda registrado en la bitácora.
            </p>
          </div>
          <div className="grid grid-cols-3 gap-2 text-center text-xs">
            <WorkflowPill
              label="1"
              text="Punto"
              value={selectedDevice ? "Listo" : "Pendiente"}
            />
            <WorkflowPill
              label="2"
              text="Socio"
              value={selectedClient ? "Listo" : "Busca"}
            />
            <WorkflowPill label="3" text="Validar" value="Registrar" />
          </div>
        </div>
      </section>

      <div className="grid gap-6 xl:grid-cols-[minmax(18rem,0.78fr)_minmax(24rem,1.22fr)]">
        <div className="grid content-start gap-6">
          <section className="rounded-2xl border border-[var(--brand-line)] bg-white p-5 shadow-sm">
            <SectionKicker number="1" text="Punto de acceso" />
            <h3 className="mt-2 text-xl font-bold">
              ¿Dónde se solicitará el acceso?
            </h3>
            <label className="mt-4 grid gap-2 text-sm font-semibold text-[var(--brand-muted)]">
              Puerta, torniquete o zona
              <select
                className="h-11 rounded-lg border border-[var(--brand-line)] bg-white px-3 text-sm text-[var(--brand-ink)]"
                onChange={(event) => onDeviceCodeChange(event.target.value)}
                value={deviceCode}
              >
                {devices.length === 0 ? (
                  <option value="">No hay puntos configurados</option>
                ) : null}
                {devices.map((device) => (
                  <option key={device.id} value={device.deviceCode}>
                    {device.name} ·{" "}
                    {device.status === "ACTIVE" ? "Activo" : device.status}
                  </option>
                ))}
              </select>
            </label>

            {selectedDevice ? (
              <div className="mt-4 rounded-xl border border-[var(--brand-line)] bg-[var(--brand-primary-soft)] p-4">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="font-bold text-[var(--brand-ink)]">
                      {selectedDevice.name}
                    </p>
                    <p className="mt-1 text-xs text-[var(--brand-muted)]">
                      {selectedDevice.deviceType} ·{" "}
                      {selectedDevice.provider || "Proveedor por definir"}
                    </p>
                  </div>
                  <StatusBadge
                    tone={
                      selectedDevice.status === "ACTIVE" ? "success" : "neutral"
                    }
                  >
                    {selectedDevice.status === "ACTIVE"
                      ? "Activo"
                      : selectedDevice.status}
                  </StatusBadge>
                </div>
                <div className="mt-4 border-t border-[var(--brand-primary-border)] pt-3">
                  <p className="text-xs font-bold uppercase tracking-wide text-[var(--brand-muted)]">
                    Derechos requeridos
                  </p>
                  {requiredBenefits.length > 0 ? (
                    <div className="mt-2 flex flex-wrap gap-2">
                      {requiredBenefits.map((benefit) => (
                        <BenefitBadge benefit={benefit} key={benefit.id} />
                      ))}
                    </div>
                  ) : (
                    <p className="mt-2 text-sm text-[#925b21]">
                      Sin requisito configurado. Antes de operar un torniquete
                      real, define al menos un beneficio ACCESS en Configuración
                      física.
                    </p>
                  )}
                </div>
              </div>
            ) : null}
          </section>

          <section className="rounded-2xl border border-[var(--brand-line)] bg-white p-5 shadow-sm">
            <SectionKicker number="2" text="Socio" />
            <h3 className="mt-2 text-xl font-bold">Encuentra y selecciona</h3>
            <form className="mt-4 flex gap-2" onSubmit={onSearch}>
              <input
                className="h-11 min-w-0 flex-1 rounded-lg border border-[var(--brand-line)] px-3 text-sm"
                onChange={(event) => onQueryChange(event.target.value)}
                placeholder="Nombre, correo o teléfono"
                value={query}
              />
              <button
                className="h-11 rounded-lg bg-[var(--brand-primary)] px-4 text-sm font-bold text-white transition hover:brightness-110 disabled:opacity-60"
                disabled={busy}
                type="submit"
              >
                Buscar
              </button>
            </form>
            <div className="mt-3 grid gap-2">
              {clients.slice(0, 5).map((client) => (
                <button
                  className={`rounded-xl border px-3 py-3 text-left transition ${
                    client.id === selectedClientId
                      ? "border-[var(--brand-accent)] bg-[var(--brand-accent-soft)]"
                      : "border-[var(--brand-line)] bg-white hover:border-[var(--brand-primary-border)]"
                  }`}
                  key={client.id}
                  onClick={() => onSelectClient(client)}
                  type="button"
                >
                  <span className="block text-sm font-bold text-[var(--brand-ink)]">
                    {client.firstName} {client.lastName}
                  </span>
                  <span className="mt-1 block text-xs text-[var(--brand-muted)]">
                    {client.email ?? client.phone ?? "Sin contacto"} ·{" "}
                    {client.status}
                  </span>
                </button>
              ))}
            </div>
            <label className="mt-4 grid gap-2 text-sm font-semibold text-[var(--brand-muted)]">
              Identificador alterno
              <input
                className="h-10 rounded-lg border border-[var(--brand-line)] px-3 text-sm"
                onChange={(event) => onIdentifierChange(event.target.value)}
                placeholder="Correo o teléfono, si no seleccionas un socio"
                value={identifier}
              />
            </label>
          </section>
        </div>

        <section className="rounded-2xl border border-[var(--brand-line)] bg-white p-5 shadow-sm">
          <SectionKicker number="3" text="Decisión" />
          <div className="mt-2 flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
            <div>
              <h3 className="text-xl font-bold">Revisar antes de abrir</h3>
              <p className="mt-2 text-sm leading-6 text-[var(--brand-muted)]">
                {selectedClient
                  ? `${selectedClient.firstName} ${selectedClient.lastName} será evaluado contra los derechos del punto seleccionado.`
                  : "Selecciona un socio o escribe un identificador para solicitar la evaluación."}
              </p>
            </div>
            <button
              className="h-11 shrink-0 rounded-lg bg-[var(--brand-accent)] px-5 text-sm font-black text-[#04231f] transition hover:brightness-105 disabled:cursor-not-allowed disabled:opacity-60"
              disabled={
                busy ||
                !selectedDevice ||
                (!selectedClientId && !identifier.trim())
              }
              onClick={onEvaluate}
              type="button"
            >
              Validar acceso
            </button>
          </div>

          <div className="mt-5 grid gap-3 sm:grid-cols-3">
            <DecisionDetail
              label="Punto"
              value={selectedDevice?.name ?? "Selecciona un punto"}
            />
            <DecisionDetail
              label="Socio"
              value={
                selectedClient
                  ? `${selectedClient.firstName} ${selectedClient.lastName}`
                  : "Sin seleccionar"
              }
            />
            <DecisionDetail
              label="Reglas"
              value={
                requiredBenefits.length > 0
                  ? `${requiredBenefits.length} derecho(s)`
                  : "Por configurar"
              }
            />
          </div>

          {lastDecision ? (
            <AccessDecisionResult decision={lastDecision} />
          ) : (
            <div className="mt-6 rounded-xl border border-dashed border-[var(--brand-line)] bg-[var(--brand-surface)] px-4 py-8 text-center">
              <p className="font-semibold text-[var(--brand-ink)]">
                Aún no hay una decisión en esta sesión.
              </p>
              <p className="mt-2 text-sm text-[var(--brand-muted)]">
                La respuesta explicará si el socio puede entrar y dejará la
                evidencia en bitácora.
              </p>
            </div>
          )}

          <details className="mt-6 rounded-xl border border-[var(--brand-line)] bg-[var(--brand-surface)]">
            <summary className="cursor-pointer list-none px-4 py-4 text-sm font-bold text-[var(--brand-ink)]">
              <span className="flex items-center justify-between gap-4">
                Excepciones controladas{" "}
                <span className="text-xs font-semibold text-[var(--brand-muted)]">
                  Apertura manual y autorización temporal
                </span>
              </span>
            </summary>
            <div className="grid gap-4 border-t border-[var(--brand-line)] p-4 lg:grid-cols-2">
              <ManualAccessSection
                busy={busy}
                manualReason={manualReason}
                onManualReasonChange={onManualReasonChange}
                onOpenManually={onOpenManually}
              />
              <TemporaryPassSection
                busy={busy}
                maxUses={maxUses}
                onCreatePass={onCreatePass}
                onMaxUsesChange={onMaxUsesChange}
                onReasonChange={onTemporaryReasonChange}
                onTemporaryHoursChange={onTemporaryHoursChange}
                reason={temporaryReason}
                selectedClientId={selectedClientId}
                temporaryHours={temporaryHours}
              />
            </div>
          </details>
        </section>
      </div>
    </section>
  );
}

export function PhysicalAccessIntegrationSection({
  availableDeviceBenefits,
  busy,
  connectionName,
  connectorKey,
  device,
  deviceCode,
  deviceRequiredBenefitIds,
  devices,
  externalPersonRef,
  integration,
  mappingDrafts,
  physicalClientState,
  selectedClient,
  syncEnabled,
  onConnectionNameChange,
  onConnectorKeyChange,
  onCreateDevice,
  onDeviceCodeChange,
  onExternalPersonRefChange,
  onLinkClient,
  onMappingChange,
  onQueueSynchronization,
  onSaveDeviceRequiredBenefits,
  onSaveIntegration,
  onSyncEnabledChange,
}: {
  availableDeviceBenefits: PhysicalAccessBenefit[];
  busy: boolean;
  connectionName: string;
  connectorKey: string;
  device: AccessDevice | null;
  deviceCode: string;
  deviceRequiredBenefitIds: string[];
  devices: AccessDevice[];
  externalPersonRef: string;
  integration: PhysicalIntegration | null;
  mappingDrafts: Record<string, { reference: string; name: string }>;
  physicalClientState: PhysicalClientState | null;
  selectedClient: ClientSummary | null;
  syncEnabled: boolean;
  onConnectionNameChange: (value: string) => void;
  onConnectorKeyChange: (value: string) => void;
  onCreateDevice: (payload: AccessDevicePayload) => Promise<void>;
  onDeviceCodeChange: (value: string) => void;
  onExternalPersonRefChange: (value: string) => void;
  onLinkClient: () => void;
  onMappingChange: (
    benefitId: string,
    field: "reference" | "name",
    value: string,
  ) => void;
  onQueueSynchronization: () => void;
  onSaveDeviceRequiredBenefits: (benefitIds: string[]) => void;
  onSaveIntegration: () => void;
  onSyncEnabledChange: (value: boolean) => void;
}) {
  if (!integration) return null;

  const connection = integration.connection;
  const mappedCount = integration.availableBenefits.filter((benefit) =>
    mappingDrafts[benefit.id]?.reference.trim(),
  ).length;
  const syncState = physicalClientState?.syncState;

  return (
    <section className="grid gap-6">
      <section className="rounded-2xl border border-[var(--brand-primary-border)] bg-[var(--brand-primary)] p-6 text-white shadow-sm">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <p className="text-xs font-black uppercase tracking-[0.18em] text-[var(--brand-accent)]">
              Configuración física por sucursal
            </p>
            <h2 className="mt-2 text-2xl font-black tracking-tight">
              HikCentral y puntos de acceso
            </h2>
            <p className="mt-2 max-w-3xl text-sm leading-6 text-white/75">
              GymFort calcula quién puede entrar. El conector local aplica esos
              derechos en HikCentral sin guardar contraseñas, URL internas ni
              biometría en la nube.
            </p>
          </div>
          <ConnectionStatus connection={connection} />
        </div>

        <div className="mt-6 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <SetupStep
            complete={Boolean(connection.id)}
            label="1. Conexión"
            text={connection.id ? "Guardada" : "Pendiente"}
          />
          <SetupStep
            complete={Boolean(device && deviceRequiredBenefitIds.length)}
            label="2. Puntos"
            text={
              device
                ? `${deviceRequiredBenefitIds.length} derecho(s)`
                : "Elige un punto"
            }
          />
          <SetupStep
            complete={
              integration.availableBenefits.length > 0 &&
              mappedCount === integration.availableBenefits.length
            }
            label="3. Niveles"
            text={`${mappedCount}/${integration.availableBenefits.length} mapeados`}
          />
          <SetupStep
            complete={Boolean(physicalClientState?.externalPersonRef)}
            label="4. Socios"
            text={
              physicalClientState?.externalPersonRef
                ? "Vinculado"
                : "Por vincular"
            }
          />
        </div>
      </section>

      <details className="rounded-2xl border border-[var(--brand-line)] bg-white shadow-sm">
        <summary className="cursor-pointer list-none px-5 py-5">
          <SummaryHeading
            detail="Identificador seguro del GymFort Local Connector"
            title="1. Conecta esta sucursal"
            status={connection.id ? "Configurada" : "Requerida"}
          />
        </summary>
        <div className="border-t border-[var(--brand-line)] p-5">
          <div className="grid gap-4 md:grid-cols-[1fr_1fr_auto] md:items-end">
            <label className="grid gap-2 text-sm font-semibold text-[var(--brand-muted)]">
              Nombre para reconocer la conexión
              <input
                className="h-11 rounded-lg border border-[var(--brand-line)] px-3 text-sm"
                maxLength={160}
                onChange={(event) => onConnectionNameChange(event.target.value)}
                placeholder="Ej. HikCentral · Centro"
                value={connectionName}
              />
            </label>
            <label className="grid gap-2 text-sm font-semibold text-[var(--brand-muted)]">
              ID del conector local
              <input
                className="h-11 rounded-lg border border-[var(--brand-line)] px-3 text-sm"
                maxLength={120}
                onChange={(event) => onConnectorKeyChange(event.target.value)}
                placeholder="Ej. hikcentral-centro-01"
                value={connectorKey}
              />
            </label>
            <label className="flex h-11 items-center gap-2 rounded-lg border border-[var(--brand-line)] bg-[var(--brand-surface)] px-3 text-sm font-bold text-[var(--brand-ink)]">
              <input
                checked={syncEnabled}
                onChange={(event) => onSyncEnabledChange(event.target.checked)}
                type="checkbox"
              />
              Activar sincronización
            </label>
          </div>
          <p className="mt-4 rounded-xl border border-[var(--brand-line)] bg-[var(--brand-surface)] px-4 py-3 text-sm text-[var(--brand-muted)]">
            El conector se instalará dentro de la red del gimnasio. Aquí sólo se
            registra su ID, no la dirección, credenciales ni información
            biométrica de HikCentral.
          </p>
          <button
            className="mt-4 h-11 rounded-lg bg-[var(--brand-primary)] px-5 text-sm font-bold text-white transition hover:brightness-110 disabled:opacity-60"
            disabled={
              busy ||
              !connectionName.trim() ||
              (syncEnabled && !connectorKey.trim())
            }
            onClick={onSaveIntegration}
            type="button"
          >
            Guardar conexión
          </button>
        </div>
      </details>

      <details className="rounded-2xl border border-[var(--brand-line)] bg-white shadow-sm">
        <summary className="cursor-pointer list-none px-5 py-5">
          <SummaryHeading
            detail="Define qué derecho comercial exige cada punto"
            title="2. Configura los puntos de acceso"
            status={
              deviceRequiredBenefitIds.length > 0 ? "Revisado" : "Pendiente"
            }
          />
        </summary>
        <div className="border-t border-[var(--brand-line)] p-5">
          <label className="grid max-w-xl gap-2 text-sm font-semibold text-[var(--brand-muted)]">
            Punto físico a configurar
            <select
              className="h-11 rounded-lg border border-[var(--brand-line)] bg-white px-3 text-sm text-[var(--brand-ink)]"
              onChange={(event) => onDeviceCodeChange(event.target.value)}
              value={deviceCode}
            >
              {devices.length === 0 ? (
                <option value="">No hay puntos configurados</option>
              ) : null}
              {devices.map((candidate) => (
                <option key={candidate.id} value={candidate.deviceCode}>
                  {candidate.name} · {candidate.deviceCode}
                </option>
              ))}
            </select>
          </label>
          {!device ? (
            <div className="mt-4">
              <EmptyState text="Registra o activa un punto de acceso para asignar sus derechos requeridos." />
            </div>
          ) : (
            <div className="mt-4">
              <AccessPointRequirements
                availableBenefits={availableDeviceBenefits}
                busy={busy}
                device={device}
                key={`${device.id}:${deviceRequiredBenefitIds.join(",")}`}
                requiredBenefitIds={deviceRequiredBenefitIds}
                onSave={onSaveDeviceRequiredBenefits}
              />
            </div>
          )}
          <AccessPointRegistration busy={busy} onCreate={onCreateDevice} />
        </div>
      </details>

      <details className="rounded-2xl border border-[var(--brand-line)] bg-white shadow-sm">
        <summary className="cursor-pointer list-none px-5 py-5">
          <SummaryHeading
            detail="Relaciona derechos GymFort con Access Levels existentes"
            title="3. Mapea niveles de HikCentral"
            status={
              integration.availableBenefits.length === 0
                ? "Sin beneficios"
                : `${mappedCount}/${integration.availableBenefits.length} listos`
            }
          />
        </summary>
        <div className="border-t border-[var(--brand-line)] p-5">
          {integration.availableBenefits.length === 0 ? (
            <EmptyState text="No hay beneficios activos de tipo ACCESS en esta sucursal. Crea los derechos desde Beneficios y asígnalos a los planes que correspondan." />
          ) : (
            <BenefitMappings
              availableBenefits={integration.availableBenefits}
              busy={busy}
              connectionName={connectionName}
              connectorKey={connectorKey}
              mappingDrafts={mappingDrafts}
              syncEnabled={syncEnabled}
              onMappingChange={onMappingChange}
              onSaveIntegration={onSaveIntegration}
            />
          )}
        </div>
      </details>

      <section className="rounded-2xl border border-[var(--brand-line)] bg-white p-5 shadow-sm">
        <SummaryHeading
          detail="Vincula una persona de HikCentral y prepara su estado deseado"
          title="4. Sincroniza un socio"
          status={syncState?.status ?? "Sin preparar"}
        />
        {!selectedClient ? (
          <div className="mt-4">
            <EmptyState text="Selecciona un socio desde Operar acceso para revisar sus derechos efectivos y vincular su referencia opaca de HikCentral." />
          </div>
        ) : (
          <div className="mt-5 grid gap-5">
            <div className="rounded-xl border border-[var(--brand-line)] bg-[var(--brand-surface)] p-4">
              <p className="font-bold text-[var(--brand-ink)]">
                {selectedClient.firstName} {selectedClient.lastName}
              </p>
              <p className="mt-1 text-sm text-[var(--brand-muted)]">
                La referencia se conserva como identificador del proveedor; no
                se copia rostro, huella ni plantilla biométrica.
              </p>
              <div className="mt-4 grid gap-3 md:grid-cols-[minmax(0,1fr)_auto] md:items-end">
                <label className="grid gap-2 text-sm font-semibold text-[var(--brand-muted)]">
                  Referencia de persona en HikCentral
                  <input
                    className="h-11 rounded-lg border border-[var(--brand-line)] bg-white px-3 text-sm"
                    maxLength={180}
                    onChange={(event) =>
                      onExternalPersonRefChange(event.target.value)
                    }
                    placeholder="ID de persona del proveedor"
                    value={externalPersonRef}
                  />
                </label>
                <button
                  className="h-11 rounded-lg border border-[var(--brand-primary-border)] bg-white px-4 text-sm font-bold text-[var(--brand-primary)] transition hover:bg-[var(--brand-primary-soft)] disabled:opacity-60"
                  disabled={busy || !externalPersonRef.trim() || !connection.id}
                  onClick={onLinkClient}
                  type="button"
                >
                  Guardar vínculo
                </button>
              </div>
            </div>
            {physicalClientState ? (
              <PhysicalClientSyncState
                busy={busy}
                connectionEnabled={connection.enabled}
                state={physicalClientState}
                syncState={syncState ?? null}
                onQueueSynchronization={onQueueSynchronization}
              />
            ) : null}
          </div>
        )}
      </section>
    </section>
  );
}

export function AccessLogsSection({
  logs,
  onRefreshLogs,
}: {
  logs: AccessLog[];
  onRefreshLogs: () => void;
}) {
  const granted = logs.filter((log) => log.decision === "GRANTED").length;
  const denied = logs.filter((log) => log.decision === "DENIED").length;
  return (
    <section className="rounded-2xl border border-[var(--brand-line)] bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
        <div>
          <p className="text-xs font-black uppercase tracking-[0.16em] text-[var(--brand-primary)]">
            Auditoría operativa
          </p>
          <h2 className="mt-1 text-2xl font-black tracking-tight">
            Bitácora reciente
          </h2>
          <p className="mt-2 text-sm text-[var(--brand-muted)]">
            Muestra los últimos {logs.length} eventos de esta sucursal. El panel
            tiene desplazamiento propio para no extender toda la página.
          </p>
        </div>
        <button
          className="h-11 rounded-lg border border-[var(--brand-primary-border)] bg-white px-4 text-sm font-bold text-[var(--brand-primary)] transition hover:bg-[var(--brand-primary-soft)]"
          onClick={onRefreshLogs}
          type="button"
        >
          Actualizar
        </button>
      </div>
      <div className="mt-5 grid gap-3 sm:grid-cols-3">
        <LogMetric label="Eventos" value={String(logs.length)} />
        <LogMetric label="Concedidos" tone="success" value={String(granted)} />
        <LogMetric
          label="Denegados"
          tone={denied > 0 ? "danger" : "neutral"}
          value={String(denied)}
        />
      </div>
      <div className="mt-5 max-h-[34rem] overflow-auto rounded-xl border border-[var(--brand-line)]">
        <table className="w-full min-w-[780px] border-collapse text-sm">
          <thead className="sticky top-0 z-10 bg-[var(--brand-surface)] shadow-[0_1px_0_var(--brand-line)]">
            <tr className="text-left text-xs font-black uppercase tracking-wide text-[var(--brand-muted)]">
              <th className="px-4 py-3">Hora</th>
              <th className="px-4 py-3">Socio</th>
              <th className="px-4 py-3">Resultado</th>
              <th className="px-4 py-3">Punto</th>
              <th className="px-4 py-3">Motivo</th>
            </tr>
          </thead>
          <tbody>
            {logs.length === 0 ? (
              <tr>
                <td
                  className="px-4 py-10 text-center text-[var(--brand-muted)]"
                  colSpan={5}
                >
                  Aún no hay eventos de acceso para esta sucursal.
                </td>
              </tr>
            ) : (
              logs.map((log) => (
                <tr
                  className="border-t border-[var(--brand-line)]"
                  key={log.id}
                >
                  <td className="whitespace-nowrap px-4 py-3 text-[var(--brand-muted)]">
                    {formatDateTime(log.occurredAt)}
                  </td>
                  <td className="px-4 py-3 font-semibold text-[var(--brand-ink)]">
                    {log.clientName ?? "Sin socio asociado"}
                  </td>
                  <td className="px-4 py-3">
                    <StatusBadge
                      tone={log.decision === "GRANTED" ? "success" : "danger"}
                    >
                      {log.decision === "GRANTED" ? "Concedido" : "Denegado"}
                    </StatusBadge>
                  </td>
                  <td className="px-4 py-3 text-[var(--brand-muted)]">
                    <span className="block font-semibold text-[var(--brand-ink)]">
                      {log.deviceName ?? "Sin punto"}
                    </span>
                    <span className="text-xs">{humanize(log.accessType)}</span>
                  </td>
                  <td className="max-w-sm px-4 py-3 text-[var(--brand-muted)]">
                    {log.reason ?? "Sin detalle"}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function BenefitMappings({
  availableBenefits,
  busy,
  connectionName,
  connectorKey,
  mappingDrafts,
  syncEnabled,
  onMappingChange,
  onSaveIntegration,
}: {
  availableBenefits: PhysicalAccessBenefit[];
  busy: boolean;
  connectionName: string;
  connectorKey: string;
  mappingDrafts: Record<string, { reference: string; name: string }>;
  syncEnabled: boolean;
  onMappingChange: (
    benefitId: string,
    field: "reference" | "name",
    value: string,
  ) => void;
  onSaveIntegration: () => void;
}) {
  return (
    <>
      <p className="text-sm text-[var(--brand-muted)]">
        Usa la referencia del Access Level que ya existe en HikCentral. Un
        beneficio sin referencia impedirá preparar sincronizaciones para socios
        que lo requieran.
      </p>
      <div className="mt-4 grid gap-3">
        {availableBenefits.map((benefit) => {
          const draft = mappingDrafts[benefit.id] ?? {
            reference: "",
            name: "",
          };
          return (
            <div
              className="grid gap-3 rounded-xl border border-[var(--brand-line)] bg-[var(--brand-surface)] p-4 md:grid-cols-[0.75fr_1fr_1fr]"
              key={benefit.id}
            >
              <div>
                <BenefitBadge benefit={benefit} />
                <p className="mt-2 text-sm font-bold text-[var(--brand-ink)]">
                  {benefit.name}
                </p>
              </div>
              <label className="grid gap-1 text-xs font-bold uppercase tracking-wide text-[var(--brand-muted)]">
                Referencia del Access Level
                <input
                  className="h-10 rounded-lg border border-[var(--brand-line)] bg-white px-3 text-sm font-normal normal-case tracking-normal text-[var(--brand-ink)]"
                  maxLength={180}
                  onChange={(event) =>
                    onMappingChange(benefit.id, "reference", event.target.value)
                  }
                  placeholder="ID o código local"
                  value={draft.reference}
                />
              </label>
              <label className="grid gap-1 text-xs font-bold uppercase tracking-wide text-[var(--brand-muted)]">
                Nombre visible (opcional)
                <input
                  className="h-10 rounded-lg border border-[var(--brand-line)] bg-white px-3 text-sm font-normal normal-case tracking-normal text-[var(--brand-ink)]"
                  maxLength={180}
                  onChange={(event) =>
                    onMappingChange(benefit.id, "name", event.target.value)
                  }
                  placeholder="Ej. Acceso general"
                  value={draft.name}
                />
              </label>
            </div>
          );
        })}
      </div>
      <button
        className="mt-4 h-11 rounded-lg bg-[var(--brand-primary)] px-5 text-sm font-bold text-white transition hover:brightness-110 disabled:opacity-60"
        disabled={
          busy ||
          !connectionName.trim() ||
          (syncEnabled && !connectorKey.trim())
        }
        onClick={onSaveIntegration}
        type="button"
      >
        Guardar mapeo de niveles
      </button>
    </>
  );
}

function PhysicalClientSyncState({
  busy,
  connectionEnabled,
  state,
  syncState,
  onQueueSynchronization,
}: {
  busy: boolean;
  connectionEnabled: boolean;
  state: PhysicalClientState;
  syncState: PhysicalClientState["syncState"];
  onQueueSynchronization: () => void;
}) {
  return (
    <div className="rounded-xl border border-[var(--brand-line)] p-4">
      <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
        <div>
          <p className="text-sm font-bold text-[var(--brand-ink)]">
            Derechos calculados por GymFort
          </p>
          <div className="mt-2 flex flex-wrap gap-2">
            {state.effectiveBenefits.length > 0 ? (
              state.effectiveBenefits.map((benefit) => (
                <BenefitBadge benefit={benefit} key={benefit.id} />
              ))
            ) : (
              <span className="text-sm text-[var(--brand-muted)]">
                No tiene derechos ACCESS efectivos; se preparará una revocación.
              </span>
            )}
          </div>
        </div>
        {syncState ? <SyncBadge status={syncState.status} /> : null}
      </div>
      {state.effectiveMappings.some(
        (mapping) => !mapping.externalAccessLevelRef,
      ) ? (
        <p className="mt-4 rounded-lg bg-[#fff6e4] px-3 py-3 text-sm text-[#865319]">
          Hay derechos sin Access Level configurado. Completa el paso 3 antes de
          preparar la sincronización.
        </p>
      ) : null}
      {syncState?.lastError ? (
        <p className="mt-4 rounded-lg bg-[#fff0f0] px-3 py-3 text-sm text-[#922f2f]">
          {syncState.lastError}
        </p>
      ) : null}
      <button
        className="mt-4 h-11 rounded-lg bg-[var(--brand-accent)] px-5 text-sm font-black text-[#04231f] transition hover:brightness-105 disabled:opacity-60"
        disabled={busy || !connectionEnabled || !state.externalPersonRef}
        onClick={onQueueSynchronization}
        type="button"
      >
        Preparar sincronización local
      </button>
      {!connectionEnabled ? (
        <p className="mt-2 text-xs text-[var(--brand-muted)]">
          Activa y guarda la conexión para preparar cambios de acceso.
        </p>
      ) : null}
    </div>
  );
}

function AccessPointRequirements({
  availableBenefits,
  busy,
  device,
  requiredBenefitIds,
  onSave,
}: {
  availableBenefits: PhysicalAccessBenefit[];
  busy: boolean;
  device: AccessDevice;
  requiredBenefitIds: string[];
  onSave: (benefitIds: string[]) => void;
}) {
  const [draft, setDraft] = useState<string[]>(requiredBenefitIds);
  const hasChanges = !sameSet(draft, requiredBenefitIds);
  function toggle(benefitId: string) {
    setDraft((current) =>
      current.includes(benefitId)
        ? current.filter((id) => id !== benefitId)
        : [...current, benefitId],
    );
  }
  if (availableBenefits.length === 0)
    return (
      <EmptyState text="Crea primero beneficios activos de tipo ACCESS. Por ejemplo: GYM_ACCESS, SAUNA_ACCESS o CLASES_ACCESS." />
    );
  return (
    <div>
      <p className="font-bold text-[var(--brand-ink)]">{device.name}</p>
      <p className="mt-1 text-sm text-[var(--brand-muted)]">
        Selecciona todos los derechos que un socio debe tener para utilizar este
        punto. No dejes un torniquete real sin requisitos.
      </p>
      <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
        {availableBenefits.map((benefit) => {
          const checked = draft.includes(benefit.id);
          return (
            <label
              className={`flex cursor-pointer items-start gap-3 rounded-xl border p-4 transition ${checked ? "border-[var(--brand-accent)] bg-[var(--brand-accent-soft)]" : "border-[var(--brand-line)] bg-white hover:border-[var(--brand-primary-border)]"}`}
              key={benefit.id}
            >
              <input
                checked={checked}
                className="mt-1"
                onChange={() => toggle(benefit.id)}
                type="checkbox"
              />
              <span>
                <BenefitBadge benefit={benefit} />
                <span className="mt-2 block text-sm font-bold text-[var(--brand-ink)]">
                  {benefit.name}
                </span>
              </span>
            </label>
          );
        })}
      </div>
      {draft.length === 0 ? (
        <p className="mt-4 text-sm text-[#925b21]">
          Este punto no exigirá ningún derecho y sólo debe mantenerse así si es
          intencional para pruebas.
        </p>
      ) : null}
      <button
        className="mt-4 h-11 rounded-lg bg-[var(--brand-primary)] px-5 text-sm font-bold text-white transition hover:brightness-110 disabled:opacity-60"
        disabled={busy || !hasChanges}
        onClick={() => onSave(draft)}
        type="button"
      >
        Guardar requisitos de {device.name}
      </button>
    </div>
  );
}

function AccessPointRegistration({
  busy,
  onCreate,
}: {
  busy: boolean;
  onCreate: (payload: AccessDevicePayload) => Promise<void>;
}) {
  const [name, setName] = useState("");
  const [deviceCode, setDeviceCode] = useState("");
  const [deviceType, setDeviceType] =
    useState<AccessDevicePayload["deviceType"]>("TURNSTILE");
  const [provider, setProvider] =
    useState<NonNullable<AccessDevicePayload["provider"]>>("HIKCENTRAL");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!name.trim() || !deviceCode.trim()) {
      return;
    }
    await onCreate({
      name: name.trim(),
      deviceCode: deviceCode.trim().toUpperCase(),
      deviceType,
      provider,
    });
    setName("");
    setDeviceCode("");
  }

  return (
    <details className="mt-4 rounded-xl border border-dashed border-[var(--brand-line)] bg-[var(--brand-surface)]">
      <summary className="cursor-pointer list-none px-4 py-3 text-sm font-bold text-[var(--brand-primary)]">
        Registrar otro punto de acceso
      </summary>
      <form
        className="grid gap-3 border-t border-[var(--brand-line)] p-4 md:grid-cols-2"
        onSubmit={submit}
      >
        <label className="grid gap-2 text-sm font-semibold text-[var(--brand-muted)]">
          Nombre visible
          <input
            className="h-10 rounded-lg border border-[var(--brand-line)] bg-white px-3 text-sm"
            maxLength={120}
            onChange={(event) => setName(event.target.value)}
            placeholder="Ej. Torniquete recepción"
            value={name}
          />
        </label>
        <label className="grid gap-2 text-sm font-semibold text-[var(--brand-muted)]">
          Código único
          <input
            className="h-10 rounded-lg border border-[var(--brand-line)] bg-white px-3 text-sm uppercase"
            maxLength={80}
            onChange={(event) => setDeviceCode(event.target.value)}
            placeholder="Ej. FRONT-TURNSTILE-01"
            value={deviceCode}
          />
        </label>
        <label className="grid gap-2 text-sm font-semibold text-[var(--brand-muted)]">
          Tipo de punto
          <select
            className="h-10 rounded-lg border border-[var(--brand-line)] bg-white px-3 text-sm"
            onChange={(event) =>
              setDeviceType(
                event.target.value as AccessDevicePayload["deviceType"],
              )
            }
            value={deviceType}
          >
            <option value="TURNSTILE">Torniquete</option>
            <option value="DOOR">Puerta</option>
            <option value="GATE">Portón</option>
            <option value="ZONE">Zona restringida</option>
            <option value="BIOMETRIC">Terminal biométrica</option>
          </select>
        </label>
        <label className="grid gap-2 text-sm font-semibold text-[var(--brand-muted)]">
          Operación actual
          <select
            className="h-10 rounded-lg border border-[var(--brand-line)] bg-white px-3 text-sm"
            onChange={(event) =>
              setProvider(
                event.target.value as NonNullable<
                  AccessDevicePayload["provider"]
                >,
              )
            }
            value={provider}
          >
            <option value="HIKCENTRAL">HikCentral (conector local)</option>
            <option value="SIMULATED">Simulado (sin apertura física)</option>
          </select>
        </label>
        <div className="md:col-span-2">
          <p className="text-xs leading-5 text-[var(--brand-muted)]">
            El punto se crea activo para poder configurar sus derechos ahora. La
            apertura física seguirá pendiente hasta que el conector local se
            conecte a HikCentral.
          </p>
          <button
            className="mt-3 h-10 rounded-lg bg-[var(--brand-primary)] px-4 text-sm font-bold text-white disabled:opacity-60"
            disabled={busy || !name.trim() || !deviceCode.trim()}
            type="submit"
          >
            Registrar punto
          </button>
        </div>
      </form>
    </details>
  );
}

function AccessDecisionResult({ decision }: { decision: AccessDecision }) {
  const granted = decision.decision === "GRANTED";
  return (
    <div
      className={`mt-6 rounded-2xl border p-5 ${granted ? "border-[#92d3ae] bg-[#edf9f1]" : "border-[#efb3b3] bg-[#fff3f3]"}`}
    >
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <p
            className={`text-xs font-black uppercase tracking-[0.16em] ${granted ? "text-[#23683b]" : "text-[#992f2f]"}`}
          >
            {granted ? "Acceso concedido" : "Acceso denegado"}
          </p>
          <h4 className="mt-2 text-2xl font-black text-[var(--brand-ink)]">
            {decision.clientName ?? "Sin socio asociado"}
          </h4>
          <p className="mt-2 text-sm text-[var(--brand-muted)]">
            {decision.reason ?? "La decisión fue registrada."}
          </p>
        </div>
        <StatusBadge tone={granted ? "success" : "danger"}>
          {humanize(decision.commandStatus ?? "NOT_SENT")}
        </StatusBadge>
      </div>
      <dl className="mt-5 grid gap-3 border-t border-black/10 pt-4 text-sm sm:grid-cols-3">
        <DecisionDetail
          label="Punto"
          value={decision.deviceName ?? "No especificado"}
        />
        <DecisionDetail label="Plan" value={decision.planName ?? "No aplica"} />
        <DecisionDetail
          label="Hora"
          value={formatDateTime(decision.occurredAt)}
        />
      </dl>
    </div>
  );
}

function ManualAccessSection({
  busy,
  manualReason,
  onManualReasonChange,
  onOpenManually,
}: {
  busy: boolean;
  manualReason: string;
  onManualReasonChange: (value: string) => void;
  onOpenManually: () => void;
}) {
  return (
    <article className="rounded-xl border border-[#e7c9a8] bg-[#fffaf2] p-4">
      <p className="text-xs font-black uppercase tracking-[0.14em] text-[#8c5a19]">
        Override excepcional
      </p>
      <h4 className="mt-2 font-bold text-[var(--brand-ink)]">
        Apertura manual
      </h4>
      <p className="mt-1 text-sm text-[var(--brand-muted)]">
        Sólo para incidencias, emergencia o autorización explícita. Queda
        auditada.
      </p>
      <label className="mt-3 grid gap-2 text-sm font-semibold text-[var(--brand-muted)]">
        Motivo
        <textarea
          className="min-h-20 rounded-lg border border-[var(--brand-line)] bg-white px-3 py-2 text-sm"
          onChange={(event) => onManualReasonChange(event.target.value)}
          value={manualReason}
        />
      </label>
      <button
        className="mt-3 h-10 rounded-lg border border-[#b16a1e] bg-white px-4 text-sm font-bold text-[#805111] disabled:opacity-60"
        disabled={busy || !manualReason.trim()}
        onClick={onOpenManually}
        type="button"
      >
        Registrar apertura manual
      </button>
    </article>
  );
}

function TemporaryPassSection({
  busy,
  maxUses,
  reason,
  selectedClientId,
  temporaryHours,
  onCreatePass,
  onMaxUsesChange,
  onReasonChange,
  onTemporaryHoursChange,
}: {
  busy: boolean;
  maxUses: number;
  reason: string;
  selectedClientId: string;
  temporaryHours: number;
  onCreatePass: () => void;
  onMaxUsesChange: (value: number) => void;
  onReasonChange: (value: string) => void;
  onTemporaryHoursChange: (value: number) => void;
}) {
  return (
    <article className="rounded-xl border border-[var(--brand-line)] bg-white p-4">
      <p className="text-xs font-black uppercase tracking-[0.14em] text-[var(--brand-primary)]">
        Excepción limitada
      </p>
      <h4 className="mt-2 font-bold text-[var(--brand-ink)]">
        Autorización temporal
      </h4>
      <p className="mt-1 text-sm text-[var(--brand-muted)]">
        No sustituye una membresía ni otorga zonas especiales no incluidas en el
        plan.
      </p>
      <div className="mt-3 grid gap-3 sm:grid-cols-2">
        <NumberInput
          label="Horas de vigencia"
          min={1}
          onChange={onTemporaryHoursChange}
          value={temporaryHours}
        />
        <NumberInput
          label="Usos permitidos"
          min={1}
          onChange={onMaxUsesChange}
          value={maxUses}
        />
      </div>
      <label className="mt-3 grid gap-2 text-sm font-semibold text-[var(--brand-muted)]">
        Motivo
        <textarea
          className="min-h-20 rounded-lg border border-[var(--brand-line)] px-3 py-2 text-sm"
          onChange={(event) => onReasonChange(event.target.value)}
          value={reason}
        />
      </label>
      <button
        className="mt-3 h-10 rounded-lg border border-[var(--brand-primary-border)] bg-white px-4 text-sm font-bold text-[var(--brand-primary)] disabled:opacity-60"
        disabled={busy || !selectedClientId || !reason.trim()}
        onClick={onCreatePass}
        type="button"
      >
        Crear autorización
      </button>
    </article>
  );
}

function ConnectionStatus({
  connection,
}: {
  connection: PhysicalIntegration["connection"];
}) {
  const copy =
    connection.syncStatus === "HEALTHY"
      ? { tone: "success" as const, text: "Conector activo" }
      : connection.enabled
        ? { tone: "warning" as const, text: "Esperando conector" }
        : { tone: "neutral" as const, text: "Sincronización desactivada" };
  return <StatusBadge tone={copy.tone}>{copy.text}</StatusBadge>;
}
function SummaryHeading({
  detail,
  status,
  title,
}: {
  detail: string;
  status: string;
  title: string;
}) {
  const tone =
    status === "Configurada" || status === "Revisado" || status === "SYNCED"
      ? "success"
      : status === "Requerida" ||
          status === "Pendiente" ||
          status === "Sin preparar"
        ? "warning"
        : "neutral";
  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <h3 className="text-lg font-black text-[var(--brand-ink)]">{title}</h3>
        <p className="mt-1 text-sm text-[var(--brand-muted)]">{detail}</p>
      </div>
      <StatusBadge tone={tone}>{status}</StatusBadge>
    </div>
  );
}
function SetupStep({
  complete,
  label,
  text,
}: {
  complete: boolean;
  label: string;
  text: string;
}) {
  return (
    <div
      className={`rounded-xl border px-4 py-3 ${complete ? "border-[var(--brand-accent)] bg-white/10" : "border-white/15 bg-black/10"}`}
    >
      <p className="text-xs font-black uppercase tracking-wide text-white/70">
        {label}
      </p>
      <p className="mt-1 font-bold">{text}</p>
    </div>
  );
}
function WorkflowPill({
  label,
  text,
  value,
}: {
  label: string;
  text: string;
  value: string;
}) {
  return (
    <div className="rounded-xl border border-white/15 bg-black/10 px-3 py-2">
      <span className="text-[var(--brand-accent)]">{label}</span>
      <p className="mt-1 font-bold">{text}</p>
      <small className="text-white/60">{value}</small>
    </div>
  );
}
function SectionKicker({ number, text }: { number: string; text: string }) {
  return (
    <p className="text-xs font-black uppercase tracking-[0.16em] text-[var(--brand-primary)]">
      <span className="mr-2 inline-flex h-5 w-5 items-center justify-center rounded-full bg-[var(--brand-accent)] text-[10px] text-[#04231f]">
        {number}
      </span>
      {text}
    </p>
  );
}
function DecisionDetail({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-bold uppercase tracking-wide text-[var(--brand-muted)]">
        {label}
      </dt>
      <dd className="mt-1 font-semibold text-[var(--brand-ink)]">{value}</dd>
    </div>
  );
}
function LogMetric({
  label,
  tone = "neutral",
  value,
}: {
  label: string;
  tone?: "success" | "danger" | "neutral";
  value: string;
}) {
  const palette =
    tone === "success"
      ? "border-[#b7dfc4] bg-[#f0fbf3]"
      : tone === "danger"
        ? "border-[#efd0d0] bg-[#fff6f6]"
        : "border-[var(--brand-line)] bg-[var(--brand-surface)]";
  return (
    <div className={`rounded-xl border px-4 py-3 ${palette}`}>
      <p className="text-xs font-bold uppercase tracking-wide text-[var(--brand-muted)]">
        {label}
      </p>
      <p className="mt-1 text-xl font-black text-[var(--brand-ink)]">{value}</p>
    </div>
  );
}
function SyncBadge({ status }: { status: string }) {
  const tone =
    status === "SYNCED"
      ? "success"
      : status === "BLOCKED" || status === "FAILED"
        ? "danger"
        : "warning";
  return <StatusBadge tone={tone}>{humanize(status)}</StatusBadge>;
}
function StatusBadge({
  children,
  tone,
}: {
  children: string;
  tone: "success" | "danger" | "warning" | "neutral";
}) {
  const classes = {
    success: "bg-[#dff6e7] text-[#24683b]",
    danger: "bg-[#fff0f0] text-[#982f2f]",
    warning: "bg-[#fff1cf] text-[#805500]",
    neutral: "bg-[#e9f0ee] text-[#40554e]",
  }[tone];
  return (
    <span
      className={`inline-flex w-fit rounded-full px-3 py-1 text-xs font-black ${classes}`}
    >
      {children}
    </span>
  );
}
function BenefitBadge({ benefit }: { benefit: PhysicalAccessBenefit }) {
  return (
    <span className="inline-flex rounded-md bg-[var(--brand-primary)] px-2 py-1 text-xs font-bold text-white">
      {benefit.code}
    </span>
  );
}
function EmptyState({ text }: { text: string }) {
  return (
    <p className="rounded-xl border border-dashed border-[var(--brand-line)] bg-[var(--brand-surface)] px-4 py-5 text-sm leading-6 text-[var(--brand-muted)]">
      {text}
    </p>
  );
}
function NumberInput({
  label,
  min,
  onChange,
  value,
}: {
  label: string;
  min: number;
  onChange: (value: number) => void;
  value: number;
}) {
  return (
    <label className="grid gap-2 text-sm font-semibold text-[var(--brand-muted)]">
      {label}
      <input
        className="h-10 rounded-lg border border-[var(--brand-line)] px-3 text-sm"
        min={min}
        onChange={(event) =>
          onChange(Math.max(min, Number(event.target.value) || min))
        }
        type="number"
        value={value}
      />
    </label>
  );
}
function sameSet(left: string[], right: string[]) {
  return (
    left.length === right.length && left.every((value) => right.includes(value))
  );
}
function humanize(value: string) {
  return value
    .replaceAll("_", " ")
    .toLowerCase()
    .replace(/(^|\s)\S/g, (letter) => letter.toUpperCase());
}
function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("es-MX", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
}
