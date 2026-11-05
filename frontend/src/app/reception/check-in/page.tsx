
"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AuthRequired } from "@/components/AuthRequired";
import { getStoredSession, type StoredSession } from "@/lib/api";
import { canRegisterCheckIn } from "@/lib/role-model";
import {
  getCheckInHistory, previewCheckIn, registerCheckIn, reverseCheckIn, searchCheckInClients,
  type CheckInClient, type CheckInEntry, type CheckInHistory, type CheckInPreview, type CheckInRequest,
} from "@/lib/api/manual-check-in";

const resultLabel = { GRANTED: "Entrada autorizada", DENIED: "Entrada rechazada", DUPLICATE: "Entrada ya registrada" };
const packageStatus: Record<string, string> = {
  ACTIVE: "Activo", CONSUMED: "Agotado", EXPIRED: "Vencido", CANCELLED: "Cancelado",
  PENDING_PAYMENT: "Pendiente de pago", NOT_STARTED: "Aún no vigente",
};

export default function ManualCheckInPage() {
  const [session, setSession] = useState<StoredSession | null>();
  useEffect(() => { setSession(getStoredSession()); }, []);

  if (session === undefined) return <LoadingScreen text="Cargando registro de entradas…" />;
  if (!session) return <AuthRequired title="Inicia sesión para registrar entradas" message="Selecciona tu sucursal de trabajo." />;
  if (!canRegisterCheckIn(session.user)) {
    return <AppShell session={session} eyebrow="Recepción" title="Registro de entradas"><InlineError message="Tu sesión no tiene permiso para registrar entradas." /></AppShell>;
  }
  if (!session.user.branchId) {
    return <AppShell session={session} eyebrow="Recepción" title="Registro de entradas"><InlineError message="Selecciona una sucursal para continuar." /></AppShell>;
  }
  const branchId = session.user.branchId;
  const storageKey = ["gymfort.check-in", session.user.organizationId, branchId, session.user.userId].join(":");
  return <CheckInDesk key={storageKey} session={session} branchId={branchId} storageKey={storageKey} />;
}

function CheckInDesk({ session, branchId, storageKey }: { session: StoredSession; branchId: string; storageKey: string }) {
  const token = session.accessToken;
  const [query, setQuery] = useState("");
  const [matches, setMatches] = useState<CheckInClient[] | null>(null);
  const [preview, setPreview] = useState<CheckInPreview | null>(null);
  const [benefitId, setBenefitId] = useState("");
  const [history, setHistory] = useState<CheckInHistory | null>(null);
  const [historyError, setHistoryError] = useState(false);
  const [historyVersion, setHistoryVersion] = useState(0);
  const [result, setResult] = useState<CheckInEntry | null>(null);
  const [pending, setPending] = useState<CheckInRequest | null>(() => readPending(storageKey, branchId));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [override, setOverride] = useState<string | null>(null);
  const [reversal, setReversal] = useState<{ id: string; reason: string } | null>(null);
  const inFlight = useRef(false);
  const searchInput = useRef<HTMLInputElement>(null);
  const canOverride = session.user.permissions.includes("access.check-in.override");
  const canReverse = session.user.permissions.includes("access.check-in.reverse");

  useEffect(() => {
    let active = true;
    getCheckInHistory(token).then((data) => {
      if (!active) return;
      if (data.branchId !== branchId) {
        setHistoryError(true);
        return;
      }
      setHistory(data);
      setHistoryError(false);
    }).catch(() => { if (active) setHistoryError(true); });
    return () => { active = false; };
  }, [token, branchId, historyVersion]);

  async function run(operation: () => Promise<void>) {
    if (inFlight.current) return;
    inFlight.current = true;
    setBusy(true);
    setError("");
    try {
      await operation();
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible completar la operación.");
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  }

  function resetSelection() {
    setPreview(null);
    setBenefitId("");
    setResult(null);
    setOverride(null);
  }

  async function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await run(async () => {
      resetSelection();
      setMatches(null);
      setMatches(await searchCheckInClients(token, query.trim()));
    });
  }

  async function select(clientId: string) {
    await run(async () => {
      resetSelection();
      const data = await previewCheckIn(token, clientId);
      setPreview(data);
      setBenefitId(data.options.length === 1 ? data.options[0].id : "");
    });
  }

  async function submit(request: CheckInRequest) {
    await run(async () => {
      sessionStorage.setItem(storageKey, JSON.stringify(request));
      setPending(request);
      let entry: CheckInEntry;
      try {
        entry = await registerCheckIn(token, request);
      } catch (exception) {
        if (exception instanceof Error && exception.message.includes("Código: CHECK_IN_OVERRIDE_STALE.")) {
          sessionStorage.removeItem(storageKey);
          setPending(null);
          resetSelection();
        }
        throw exception;
      }
      sessionStorage.removeItem(storageKey);
      setPending(null);
      setResult(entry);
      setOverride(null);
      setHistoryVersion((version) => version + 1);
      if (entry.decision === "DUPLICATE") {
        const data = await previewCheckIn(token, entry.clientId);
        setPreview(data);
        setBenefitId(data.options.some((option) => option.id === request.benefitId)
          ? request.benefitId ?? "" : data.options.length === 1 ? data.options[0].id : "");
      } else {
        setPreview(null);
        setMatches(null);
        setQuery("");
      }
    });
    if (!inFlight.current) searchInput.current?.focus();
  }

  function confirm(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!preview || pending || (preview.options.length > 0 && !benefitId)) return;
    void submit({
      key: crypto.randomUUID(), branchId, clientId: preview.client.id, benefitId: benefitId || null,
      ...(override !== null && result?.duplicateOf ? { overrideOf: result.duplicateOf, overrideReason: override.trim() } : {}),
    });
  }

  async function reverse(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!reversal) return;
    await run(async () => {
      const entry = await reverseCheckIn(token, reversal.id, branchId, reversal.reason.trim());
      setResult(entry);
      setReversal(null);
      setHistoryVersion((version) => version + 1);
      setPreview(null);
    });
  }

  return (
    <AppShell session={session} branchName={history?.branchName} eyebrow="Recepción" title="Registro de entradas"
      subtitle="Consulta los derechos del socio antes de confirmar su entrada." mode="operation">
      <div className="grid min-w-0 gap-5 lg:grid-cols-2">
        <section className="admin-panel space-y-4" aria-labelledby="check-in-client-heading">
          <h2 id="check-in-client-heading" className="text-lg font-bold">Buscar socio</h2>
          <form onSubmit={search} className="flex flex-wrap items-end gap-2">
            <label className="flex min-w-0 grow flex-col gap-1">
              Nombre, teléfono o identificador
              <input className="app-input min-w-0" ref={searchInput} value={query} maxLength={100} minLength={2}
                disabled={busy || !!pending} onChange={(event) => setQuery(event.target.value)} required />
            </label>
            <button className="app-button" disabled={busy || !!pending || query.trim().length < 2}>Buscar</button>
          </form>
          {busy && <p role="status">Procesando…</p>}
          {error && <InlineError message={error} />}
          {pending && (
            <div className="space-y-2">
              <p role="status">La confirmación está pendiente. Reintenta la misma operación para recuperar su resultado.</p>
              <button type="button" className="app-button" disabled={busy} onClick={() => void submit(pending)}>Reintentar entrada</button>
            </div>
          )}
          {result && <div className="rounded-xl border border-[var(--brand-line)] p-3" role="status">
            <strong>{result.reversal ? "Entrada revertida" : resultLabel[result.decision]}</strong>
            <p>{result.clientName} · {result.reversal?.reason ?? result.reason}</p>
            {(result.reversal?.remainingAfter ?? result.remainingAfter) !== null && (
              <p>{result.reversal?.remainingAfter ?? result.remainingAfter} visitas restantes tras la operación</p>
            )}
          </div>}
          {matches?.length === 0 && <p role="status">No se encontraron socios en esta sucursal.</p>}
          {!!matches?.length && (
            <ul className="grid gap-2">
              {matches.map((client) => (
                <li key={client.id}>
                  <button type="button" className="app-button app-button-secondary w-full text-left"
                    disabled={busy || !!pending} onClick={() => void select(client.id)}>
                    {client.name}{client.phone ? " · " + client.phone : " · " + client.id.slice(0, 8)}
                  </button>
                </li>
              ))}
            </ul>
          )}
          {preview && (
            <form onSubmit={confirm} className="space-y-3 border-t border-[var(--brand-line)] pt-4">
              <h3 className="font-bold">{preview.client.name}</h3>
              <p>{preview.membership
                ? preview.membership.planName + " · vence " + preview.membership.endsOn
                  + (preview.membership.graceEndsOn > preview.membership.endsOn ? " · gracia hasta " + preview.membership.graceEndsOn : "")
                : "Sin membresía vigente en esta sucursal"}</p>
              {preview.visitPackage && <div>
                <p>{preview.visitPackage.name} · {packageStatus[preview.visitPackage.status]} · {preview.visitPackage.remainingVisits} visitas restantes</p>
                {preview.visitPackage.validUntil && <p>Vence {preview.visitPackage.validUntil}</p>}
                {!preview.visitPackage.branchCovered && <p>El paquete no cubre esta sucursal.</p>}
                {preview.visitPackage.benefits.filter((benefit) => benefit.mode === "INCLUDED_WITH_VISIT").map((benefit) => (
                  <p key={benefit.id}>{benefit.name} · incluido con la visita del paquete</p>
                ))}
              </div>}
              {preview.denialReason ? <p>{preview.denialReason}</p> : (
                <label className="flex flex-col gap-1">
                  Derecho de acceso
                  <select className="app-input" disabled={busy || !!pending || (result?.decision === "DUPLICATE" && override === null)}
                    value={benefitId} onChange={(event) => setBenefitId(event.target.value)} required>
                    <option value="">Selecciona el derecho</option>
                    {preview.options.map((option) => <option key={option.id} value={option.id}>
                      {option.name} · {option.source === "MEMBERSHIP" ? "membresía" : "consume una visita"}
                    </option>)}
                  </select>
                </label>
              )}
              {result?.decision === "DUPLICATE" && canOverride && preview.options.length > 0 && override === null && (
                <button className="app-button app-button-secondary" type="button" disabled={busy || !!pending}
                  onClick={() => setOverride("")}>Es una entrada distinta</button>
              )}
              {override !== null && <label className="flex flex-col gap-1">
                Motivo de la entrada distinta
                <textarea className="app-input" maxLength={300} required value={override}
                  onChange={(event) => setOverride(event.target.value)} disabled={busy || !!pending} />
              </label>}
              {(result?.decision !== "DUPLICATE" || override !== null) && (
                <button className="app-button" disabled={busy || !!pending || (!!preview.options.length && !benefitId)
                  || (override !== null && !override.trim())}>
                  {override !== null ? "Confirmar entrada distinta" : preview.denialReason ? "Registrar rechazo" : "Confirmar entrada"}
                </button>
              )}
            </form>
          )}
        </section>
        <section className="admin-panel space-y-3" aria-labelledby="check-in-history-heading">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h2 id="check-in-history-heading" className="text-lg font-bold">Entradas recientes</h2>
            <button type="button" className="app-button app-button-secondary"
              onClick={() => setHistoryVersion((version) => version + 1)}>Actualizar</button>
          </div>
          {historyError && <InlineError message="No se pudo actualizar el historial. Vuelve a intentarlo." />}
          {!history && !historyError && <p role="status">Cargando entradas…</p>}
          {history?.entries.length === 0 && <p>Aún no hay entradas manuales en esta sucursal.</p>}
          {history && <ul className="divide-y divide-[var(--brand-line)]">
            {history.entries.map((entry) => <li className="space-y-2 py-3" key={entry.id}>
              <p><strong>{entry.clientName}</strong> · {entry.reversal ? "Revertida" : resultLabel[entry.decision]}</p>
              <p>{new Date(entry.occurredAt).toLocaleString("es-MX", { timeZone: history.timezone, dateStyle: "short", timeStyle: "short" })}
                {entry.benefitName && " · " + entry.benefitName}</p>
              <p>{entry.reversal?.reason ?? entry.reason}</p>
              {canReverse && entry.decision === "GRANTED" && !entry.reversal && reversal?.id !== entry.id && (
                <button type="button" className="app-button app-button-secondary" disabled={busy || !!pending}
                  onClick={() => setReversal({ id: entry.id, reason: "" })}>Revertir entrada</button>
              )}
              {reversal?.id === entry.id && <form onSubmit={reverse} className="space-y-2">
                <label className="flex flex-col gap-1">
                  Motivo de reversión
                  <textarea className="app-input" maxLength={300} required value={reversal.reason} disabled={busy}
                    onChange={(event) => setReversal({ id: entry.id, reason: event.target.value })} />
                </label>
                <div className="flex flex-wrap gap-2">
                  <button className="app-button" disabled={busy || !!pending || !reversal.reason.trim()}>Confirmar reversión</button>
                  <button type="button" className="app-button app-button-secondary" disabled={busy}
                    onClick={() => setReversal(null)}>Cancelar</button>
                </div>
              </form>}
            </li>)}
          </ul>}
        </section>
      </div>
    </AppShell>
  );
}

function readPending(storageKey: string, branchId: string): CheckInRequest | null {
  try {
    const request = JSON.parse(sessionStorage.getItem(storageKey) ?? "null") as CheckInRequest | null;
    return request?.branchId === branchId && typeof request.key === "string" && typeof request.clientId === "string"
      ? request : null;
  } catch {
    return null;
  }
}
