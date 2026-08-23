import type { FormEvent } from "react";
import type {
  AccessDecision,
  AccessDevice,
  AccessLog,
  ClientSummary,
  PhysicalClientState,
  PhysicalIntegration,
  VisitPass,
} from "@/lib/api";

export type VisitPassType = "DROP_IN" | "VISIT_PACK" | "COURTESY" | "PROMOTION";

export function AccessDeviceSection({
  deviceCode,
  devices,
  onDeviceCodeChange,
}: {
  deviceCode: string;
  devices: AccessDevice[];
  onDeviceCodeChange: (value: string) => void;
}) {
  return (
    <section className="rounded-lg border border-[var(--brand-line)] bg-white p-5 shadow-sm">
      <h2 className="text-xl font-semibold">Dispositivo</h2>
      <label className="mt-4 grid gap-2 text-sm font-medium text-[#42514a]">
        Puerta
        <select
          className="h-10 rounded-md border border-[var(--brand-line)] bg-white px-3 text-sm"
          onChange={(event) => onDeviceCodeChange(event.target.value)}
          value={deviceCode}
        >
          {devices.map((device) => (
            <option key={device.id} value={device.deviceCode}>
              {device.name} - {device.provider}
            </option>
          ))}
        </select>
      </label>
    </section>
  );
}

export function AccessClientSection({
  busy,
  clients,
  identifier,
  query,
  selectedClientId,
  onIdentifierChange,
  onQueryChange,
  onSearch,
  onSelectClient,
}: {
  busy: boolean;
  clients: ClientSummary[];
  identifier: string;
  query: string;
  selectedClientId: string;
  onIdentifierChange: (value: string) => void;
  onQueryChange: (value: string) => void;
  onSearch: (event: FormEvent<HTMLFormElement>) => void;
  onSelectClient: (client: ClientSummary) => void;
}) {
  return (
    <section className="rounded-lg border border-[var(--brand-line)] bg-white p-5 shadow-sm">
      <h2 className="text-xl font-semibold">Cliente</h2>
      <form className="mt-4 flex gap-2" onSubmit={onSearch}>
        <input
          className="h-10 min-w-0 flex-1 rounded-md border border-[var(--brand-line)] px-3 text-sm"
          onChange={(event) => onQueryChange(event.target.value)}
          placeholder="Nombre, email o telefono"
          value={query}
        />
        <button
          className="h-10 rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white transition hover:bg-[#243d30] disabled:opacity-60"
          disabled={busy}
          type="submit"
        >
          Buscar
        </button>
      </form>

      <div className="mt-4 grid gap-2">
        {clients.slice(0, 6).map((client) => (
          <button
            className={`rounded-md border px-3 py-3 text-left text-sm transition ${
              client.id === selectedClientId
                ? "border-[#51735f] bg-[#eef7f0]"
                : "border-[var(--brand-line)] bg-white hover:border-[#51735f]"
            }`}
            key={client.id}
            onClick={() => onSelectClient(client)}
            type="button"
          >
            <span className="block font-semibold">{client.firstName} {client.lastName}</span>
            <span className="mt-1 block text-xs text-[#6f7a73]">
              {client.email ?? client.phone ?? "Sin contacto"} - {client.status}
            </span>
          </button>
        ))}
      </div>

      <label className="mt-4 grid gap-2 text-sm font-medium text-[#42514a]">
        Identificador directo
        <input
          className="h-10 rounded-md border border-[var(--brand-line)] px-3 text-sm"
          onChange={(event) => onIdentifierChange(event.target.value)}
          placeholder="Email o telefono"
          value={identifier}
        />
      </label>
    </section>
  );
}

export function AccessDecisionSection({
  busy,
  identifier,
  lastDecision,
  selectedClient,
  selectedClientId,
  onEvaluate,
}: {
  busy: boolean;
  identifier: string;
  lastDecision: AccessDecision | null;
  selectedClient: ClientSummary | null;
  selectedClientId: string;
  onEvaluate: () => void;
}) {
  return (
    <section className="rounded-lg border border-[var(--brand-line)] bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
        <div>
          <h2 className="text-xl font-semibold">Evaluacion</h2>
          <p className="mt-2 text-sm text-[var(--brand-muted)]">
            {selectedClient ? `${selectedClient.firstName} ${selectedClient.lastName}` : "Sin cliente seleccionado"}
          </p>
        </div>
        <button
          className="h-11 rounded-md bg-[#13251c] px-5 text-sm font-semibold text-white transition hover:bg-[#243d30] disabled:opacity-60"
          disabled={busy || (!selectedClientId && !identifier.trim())}
          onClick={onEvaluate}
          type="button"
        >
          Evaluar acceso
        </button>
      </div>

      {lastDecision ? <AccessDecisionResult decision={lastDecision} /> : null}
    </section>
  );
}

function AccessDecisionResult({ decision }: { decision: AccessDecision }) {
  return (
    <div className={`mt-5 rounded-lg border p-5 ${
      decision.decision === "GRANTED"
        ? "border-[#9dc5aa] bg-[#eef7f0]"
        : "border-[#e5b7b7] bg-[#fff4f4]"
    }`}>
      <p className="text-sm font-semibold uppercase tracking-wide">
        {decision.decision === "GRANTED" ? "Concedido" : "Denegado"}
      </p>
      <h3 className="mt-2 text-2xl font-semibold">
        {decision.clientName ?? "Apertura sin cliente"}
      </h3>
      <p className="mt-2 text-sm text-[#42514a]">{decision.reason}</p>
      <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-3">
        <Metric label="Puerta" value={decision.deviceName ?? "N/A"} />
        <Metric label="Comando" value={decision.commandStatus ?? "N/A"} />
        <Metric label="Plan" value={decision.planName ?? "N/A"} />
      </dl>
    </div>
  );
}

export function ManualAccessSection({
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
    <article className="rounded-lg border border-[var(--brand-line)] bg-white p-5 shadow-sm">
      <h2 className="text-xl font-semibold">Apertura manual</h2>
      <label className="mt-4 grid gap-2 text-sm font-medium text-[#42514a]">
        Motivo
        <textarea
          className="min-h-24 rounded-md border border-[var(--brand-line)] px-3 py-2 text-sm"
          onChange={(event) => onManualReasonChange(event.target.value)}
          value={manualReason}
        />
      </label>
      <button
        className="mt-4 h-10 rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white transition hover:bg-[#243d30] disabled:opacity-60"
        disabled={busy || !manualReason.trim()}
        onClick={onOpenManually}
        type="button"
      >
        Abrir
      </button>
    </article>
  );
}

export function TemporaryPassSection({
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
    <article className="rounded-lg border border-[var(--brand-line)] bg-white p-5 shadow-sm">
      <h2 className="text-xl font-semibold">Autorización temporal</h2>
      <p className="mt-1 text-sm text-[var(--brand-muted)]">
        Excepción administrativa limitada; no sustituye una membresía ni un beneficio de zona.
      </p>
      <div className="mt-4 grid gap-3 sm:grid-cols-2">
        <NumberInput label="Horas" min={1} value={temporaryHours} onChange={onTemporaryHoursChange} />
        <NumberInput label="Usos" min={1} value={maxUses} onChange={onMaxUsesChange} />
      </div>
      <label className="mt-3 grid gap-2 text-sm font-medium text-[#42514a]">
        Motivo
        <textarea
          className="min-h-20 rounded-md border border-[var(--brand-line)] px-3 py-2 text-sm"
          onChange={(event) => onReasonChange(event.target.value)}
          value={reason}
        />
      </label>
      <button
        className="mt-4 h-10 rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white transition hover:bg-[#243d30] disabled:opacity-60"
        disabled={busy || !selectedClientId || !reason.trim()}
        onClick={onCreatePass}
        type="button"
      >
        Crear autorización
      </button>
    </article>
  );
}

export function PhysicalAccessIntegrationSection({
  busy,
  connectionName,
  connectorKey,
  externalPersonRef,
  integration,
  mappingDrafts,
  physicalClientState,
  selectedClient,
  syncEnabled,
  onConnectionNameChange,
  onConnectorKeyChange,
  onExternalPersonRefChange,
  onMappingChange,
  onSaveIntegration,
  onSyncEnabledChange,
  onLinkClient,
  onQueueSynchronization,
}: {
  busy: boolean;
  connectionName: string;
  connectorKey: string;
  externalPersonRef: string;
  integration: PhysicalIntegration | null;
  mappingDrafts: Record<string, { reference: string; name: string }>;
  physicalClientState: PhysicalClientState | null;
  selectedClient: ClientSummary | null;
  syncEnabled: boolean;
  onConnectionNameChange: (value: string) => void;
  onConnectorKeyChange: (value: string) => void;
  onExternalPersonRefChange: (value: string) => void;
  onMappingChange: (benefitId: string, field: "reference" | "name", value: string) => void;
  onSaveIntegration: () => void;
  onSyncEnabledChange: (value: boolean) => void;
  onLinkClient: () => void;
  onQueueSynchronization: () => void;
}) {
  if (!integration) {
    return null;
  }

  const connection = integration.connection;
  const syncState = physicalClientState?.syncState;

  return (
    <section className="rounded-lg border border-[#b8c9bd] bg-[#f7fbf6] p-5 shadow-sm">
      <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
        <div>
          <p className="text-xs font-bold uppercase tracking-[0.16em] text-[#557360]">Control físico</p>
          <h2 className="mt-1 text-xl font-semibold">Sincronización con HikCentral</h2>
          <p className="mt-2 max-w-3xl text-sm text-[var(--brand-muted)]">
            GymFort calcula los derechos por membresía. El conector local aplicará esos niveles en HikCentral;
            esta pantalla no guarda contraseñas, puertos ni datos biométricos.
          </p>
        </div>
        <span className={`inline-flex w-fit rounded-full px-3 py-1 text-xs font-bold ${
          connection.syncStatus === "HEALTHY"
            ? "bg-[#dff4e5] text-[#245c37]"
            : connection.enabled
              ? "bg-[#fff0cb] text-[#805500]"
              : "bg-[#e7ece8] text-[#4f5f55]"
        }`}>
          {connection.syncStatus === "HEALTHY" ? "Conector activo" : connection.enabled ? "Esperando conector" : "Sin configurar"}
        </span>
      </div>

      <div className="mt-5 rounded-lg border border-[#d5e1d7] bg-white p-4">
        <div className="grid gap-4 md:grid-cols-[1fr_1fr_auto] md:items-end">
          <label className="grid gap-2 text-sm font-medium text-[#42514a]">
            Nombre de la conexión
            <input
              className="h-10 rounded-md border border-[var(--brand-line)] px-3 text-sm"
              maxLength={160}
              onChange={(event) => onConnectionNameChange(event.target.value)}
              value={connectionName}
            />
          </label>
          <label className="grid gap-2 text-sm font-medium text-[#42514a]">
            ID del conector local
            <input
              className="h-10 rounded-md border border-[var(--brand-line)] px-3 text-sm"
              maxLength={120}
              onChange={(event) => onConnectorKeyChange(event.target.value)}
              placeholder="ej. hikcentral-centro-01"
              value={connectorKey}
            />
          </label>
          <label className="flex h-10 items-center gap-2 rounded-md border border-[var(--brand-line)] bg-white px-3 text-sm font-semibold text-[#42514a]">
            <input
              checked={syncEnabled}
              onChange={(event) => onSyncEnabledChange(event.target.checked)}
              type="checkbox"
            />
            Activar sincronización
          </label>
        </div>

        <div className="mt-5 border-t border-[#e4ece5] pt-4">
          <h3 className="font-semibold">Mapeo de beneficios de acceso</h3>
          <p className="mt-1 text-sm text-[var(--brand-muted)]">
            Configura el identificador del Access Level ya existente en HikCentral para cada derecho comercial.
          </p>
          {integration.availableBenefits.length === 0 ? (
            <p className="mt-3 rounded-md border border-dashed border-[#c9d7cc] px-3 py-3 text-sm text-[var(--brand-muted)]">
              Aún no hay beneficios activos de tipo ACCESS en esta sucursal. Crea, por ejemplo, GYM_ACCESS desde Beneficios.
            </p>
          ) : (
            <div className="mt-4 grid gap-3">
              {integration.availableBenefits.map((benefit) => {
                const draft = mappingDrafts[benefit.id] ?? { reference: "", name: "" };
                return (
                  <div className="grid gap-3 rounded-md border border-[#e4ece5] bg-[#fbfdfb] p-3 md:grid-cols-[0.8fr_1fr_1fr]" key={benefit.id}>
                    <div>
                      <p className="text-sm font-semibold">{benefit.name}</p>
                      <p className="mt-1 text-xs text-[#6f7a73]">{benefit.code}</p>
                    </div>
                    <label className="grid gap-1 text-xs font-semibold text-[#526158]">
                      Referencia del Access Level
                      <input
                        className="h-9 rounded-md border border-[var(--brand-line)] bg-white px-2 text-sm"
                        maxLength={180}
                        onChange={(event) => onMappingChange(benefit.id, "reference", event.target.value)}
                        placeholder="ID o código local"
                        value={draft.reference}
                      />
                    </label>
                    <label className="grid gap-1 text-xs font-semibold text-[#526158]">
                      Nombre visible opcional
                      <input
                        className="h-9 rounded-md border border-[var(--brand-line)] bg-white px-2 text-sm"
                        maxLength={180}
                        onChange={(event) => onMappingChange(benefit.id, "name", event.target.value)}
                        placeholder="Acceso general"
                        value={draft.name}
                      />
                    </label>
                  </div>
                );
              })}
            </div>
          )}
          <button
            className="mt-4 h-10 rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white transition hover:bg-[#243d30] disabled:opacity-60"
            disabled={busy || !connectionName.trim() || (syncEnabled && !connectorKey.trim())}
            onClick={onSaveIntegration}
            type="button"
          >
            Guardar configuración física
          </button>
        </div>
      </div>

      <div className="mt-5 rounded-lg border border-[#d5e1d7] bg-white p-4">
        <h3 className="font-semibold">Socio y estado deseado</h3>
        {!selectedClient ? (
          <p className="mt-2 text-sm text-[var(--brand-muted)]">
            Selecciona un socio para vincular su referencia opaca de HikCentral y preparar su sincronización.
          </p>
        ) : (
          <div className="mt-3 grid gap-4">
            <div className="grid gap-3 md:grid-cols-[1fr_auto] md:items-end">
              <label className="grid gap-2 text-sm font-medium text-[#42514a]">
                Referencia de persona en HikCentral para {selectedClient.firstName} {selectedClient.lastName}
                <input
                  className="h-10 rounded-md border border-[var(--brand-line)] px-3 text-sm"
                  maxLength={180}
                  onChange={(event) => onExternalPersonRefChange(event.target.value)}
                  placeholder="ID de persona del proveedor"
                  value={externalPersonRef}
                />
              </label>
              <button
                className="h-10 rounded-md border border-[#557360] bg-white px-4 text-sm font-semibold text-[#31543d] transition hover:bg-[#edf6ee] disabled:opacity-60"
                disabled={busy || !externalPersonRef.trim() || !connection.id}
                onClick={onLinkClient}
                type="button"
              >
                Guardar vínculo
              </button>
            </div>

            {physicalClientState ? (
              <div className="rounded-md border border-[#e4ece5] bg-[#fbfdfb] p-3">
                <div className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
                  <div>
                    <p className="text-sm font-semibold">Derechos calculados por GymFort</p>
                    <p className="mt-1 text-sm text-[var(--brand-muted)]">
                      {physicalClientState.effectiveBenefits.length > 0
                        ? physicalClientState.effectiveBenefits.map((benefit) => benefit.name).join(", ")
                        : "No tiene beneficios ACCESS efectivos; la sincronización preparará una revocación."}
                    </p>
                  </div>
                  {syncState ? (
                    <span className={`rounded-full px-3 py-1 text-xs font-bold ${
                      syncState.status === "BLOCKED"
                        ? "bg-[#fff0f0] text-[#982f2f]"
                        : syncState.status === "PENDING"
                          ? "bg-[#fff0cb] text-[#805500]"
                          : "bg-[#e7ece8] text-[#4f5f55]"
                    }`}>
                      {syncState.status}
                    </span>
                  ) : null}
                </div>
                {physicalClientState.effectiveMappings.some((mapping) => !mapping.externalAccessLevelRef) ? (
                  <p className="mt-3 text-sm text-[#8c5420]">
                    Hay beneficios sin nivel externo configurado. La cola los marcará como bloqueados hasta completar el mapeo.
                  </p>
                ) : null}
                {syncState?.lastError ? <p className="mt-3 text-sm text-[#982f2f]">{syncState.lastError}</p> : null}
                <button
                  className="mt-4 h-10 rounded-md bg-[#d4a917] px-4 text-sm font-semibold text-[#1f260d] transition hover:bg-[#c59b12] disabled:opacity-60"
                  disabled={busy || !connection.enabled || !physicalClientState.externalPersonRef}
                  onClick={onQueueSynchronization}
                  type="button"
                >
                  Preparar sincronización local
                </button>
                {!connection.enabled ? (
                  <p className="mt-2 text-xs text-[var(--brand-muted)]">Activa y guarda la integración para poder preparar sincronizaciones.</p>
                ) : null}
              </div>
            ) : null}
          </div>
        )}
      </div>
    </section>
  );
}

export function VisitPassesSection({
  busy,
  selectedClientId,
  validDays,
  visitPasses,
  visitPassTotal,
  visitPassType,
  onConsumeVisit,
  onCreateVisitPack,
  onRefresh,
  onValidDaysChange,
  onVisitPassTotalChange,
  onVisitPassTypeChange,
}: {
  busy: boolean;
  selectedClientId: string;
  validDays: number;
  visitPasses: VisitPass[];
  visitPassTotal: number;
  visitPassType: VisitPassType;
  onConsumeVisit: (visitPassId: string) => void;
  onCreateVisitPack: () => void;
  onRefresh: () => void;
  onValidDaysChange: (value: number) => void;
  onVisitPassTotalChange: (value: number) => void;
  onVisitPassTypeChange: (value: VisitPassType) => void;
}) {
  return (
    <section className="rounded-lg border border-[var(--brand-line)] bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
        <div>
          <h2 className="text-xl font-semibold">Paquetes de visitas</h2>
          <p className="mt-1 text-sm text-[var(--brand-muted)]">
            Crea paquetes sencillos y descuenta visitas desde recepcion.
          </p>
        </div>
        <button
          className="h-10 rounded-md border border-[var(--brand-line)] bg-white px-4 text-sm font-semibold text-[#42514a] transition hover:border-[#51735f]"
          disabled={!selectedClientId}
          onClick={onRefresh}
          type="button"
        >
          Actualizar
        </button>
      </div>

      <div className="mt-5 grid gap-3 md:grid-cols-4">
        <label className="grid gap-2 text-sm font-medium text-[#42514a]">
          Tipo
          <select
            className="h-10 rounded-md border border-[var(--brand-line)] bg-white px-3 text-sm"
            onChange={(event) => onVisitPassTypeChange(event.target.value as VisitPassType)}
            value={visitPassType}
          >
            <option value="VISIT_PACK">Paquete</option>
            <option value="DROP_IN">Visita unica</option>
            <option value="COURTESY">Cortesia</option>
            <option value="PROMOTION">Promocion</option>
          </select>
        </label>
        <NumberInput label="Visitas" min={1} value={visitPassTotal} onChange={onVisitPassTotalChange} />
        <NumberInput label="Vigencia dias" min={1} value={validDays} onChange={onValidDaysChange} />
        <div className="flex items-end">
          <button
            className="h-10 w-full rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white transition hover:bg-[#243d30] disabled:opacity-60"
            disabled={busy || !selectedClientId || visitPassTotal < 1 || validDays < 1}
            onClick={onCreateVisitPack}
            type="button"
          >
            Crear paquete
          </button>
        </div>
      </div>

      <div className="mt-5 grid gap-3">
        {visitPasses.length === 0 ? (
          <p className="rounded-md border border-[#e4e8df] bg-[#fafbf8] px-3 py-3 text-sm text-[var(--brand-muted)]">
            Selecciona un cliente para ver sus paquetes.
          </p>
        ) : (
          visitPasses.map((pass) => (
            <article className="rounded-md border border-[#e4e8df] p-4" key={pass.id}>
              <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
                <div>
                  <p className="text-sm font-semibold">{pass.clientName}</p>
                  <p className="mt-1 text-xs text-[#6f7a73]">
                    {pass.passType} - {pass.remainingVisits}/{pass.totalVisits} visitas - vence {pass.validUntil ?? "sin fecha"}
                  </p>
                </div>
                <button
                  className="h-9 rounded-md border border-[var(--brand-line)] bg-white px-3 text-sm font-semibold text-[#42514a] transition hover:border-[#51735f] disabled:opacity-60"
                  disabled={busy || pass.status !== "ACTIVE" || pass.remainingVisits < 1}
                  onClick={() => onConsumeVisit(pass.id)}
                  type="button"
                >
                  Consumir visita
                </button>
              </div>
            </article>
          ))
        )}
      </div>
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
  return (
    <section className="rounded-lg border border-[var(--brand-line)] bg-white p-5 shadow-sm">
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-xl font-semibold">Bitacora reciente</h2>
        <button
          className="h-10 rounded-md border border-[var(--brand-line)] bg-white px-4 text-sm font-semibold text-[#42514a] transition hover:border-[#51735f]"
          onClick={onRefreshLogs}
          type="button"
        >
          Actualizar
        </button>
      </div>
      <div className="mt-4 overflow-x-auto">
        <table className="w-full min-w-[760px] border-collapse text-sm">
          <thead>
            <tr className="border-b border-[#e4e8df] text-left text-xs uppercase tracking-wide text-[#6f7a73]">
              <th className="py-3 pr-4">Hora</th>
              <th className="py-3 pr-4">Cliente</th>
              <th className="py-3 pr-4">Decision</th>
              <th className="py-3 pr-4">Tipo</th>
              <th className="py-3 pr-4">Puerta</th>
              <th className="py-3">Motivo</th>
            </tr>
          </thead>
          <tbody>
            {logs.map((log) => (
              <tr className="border-b border-[#eef0eb]" key={log.id}>
                <td className="py-3 pr-4">{formatDateTime(log.occurredAt)}</td>
                <td className="py-3 pr-4 font-medium">{log.clientName ?? "Sin cliente"}</td>
                <td className="py-3 pr-4">
                  <span className={`rounded-md px-2 py-1 text-xs font-semibold ${
                    log.decision === "GRANTED"
                      ? "bg-[#eef7f0] text-[#31543d]"
                      : "bg-[#fff4f4] text-[#8c2f2f]"
                  }`}>
                    {log.decision}
                  </span>
                </td>
                <td className="py-3 pr-4">{log.accessType}</td>
                <td className="py-3 pr-4">{log.deviceName}</td>
                <td className="py-3 text-[var(--brand-muted)]">{log.reason}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-[#6f7a73]">{label}</dt>
      <dd className="font-semibold">{value}</dd>
    </div>
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
    <label className="grid gap-2 text-sm font-medium text-[#42514a]">
      {label}
      <input
        className="h-10 rounded-md border border-[var(--brand-line)] px-3 text-sm"
        min={min}
        onChange={(event) => onChange(Number(event.target.value))}
        type="number"
        value={value}
      />
    </label>
  );
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("es-MX", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
}
