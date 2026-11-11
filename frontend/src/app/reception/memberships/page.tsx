"use client";

import controls from "@/components/CommercialControls.module.css";
import styles from "./memberships.module.css";
import { Search } from "lucide-react";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AppNotice } from "@/components/AppNotice";
import { AuthRequired } from "@/components/AuthRequired";
import { getStoredSession, searchClients, type ClientSummary, type StoredSession } from "@/lib/api";
import { contractErrorMessage } from "@/lib/contract-error";
import { canAccessRoute } from "@/lib/role-model";
import { operations, ReceptionOperationCard, type ReceptionOperation } from "@/components/ReceptionOperationCard";
import { resolveClientMembershipCapabilities } from "@/app/clients/[clientId]/client-membership-permissions";
import { ReceptionMemberOperations } from "./ReceptionMemberOperations";

export default function ReceptionMembershipsPage() {
  const [session, setSession] = useState<StoredSession | null>();
  useEffect(() => {
    const readSession = () => setSession(getStoredSession());
    const timer = window.setTimeout(readSession, 0);
    window.addEventListener("gymos.session-refreshed", readSession);
    return () => {
      window.clearTimeout(timer);
      window.removeEventListener("gymos.session-refreshed", readSession);
    };
  }, []);

  if (session === undefined) return <LoadingScreen text="Cargando membresías y paquetes…" />;
  if (!session) {
    return <AuthRequired title="Inicia sesión en recepción" message="Selecciona tu sucursal de trabajo." />;
  }

  const canAccess = canAccessRoute(session.user, "/reception/memberships");
  return (
    <AppShell
      eyebrow="Recepción"
      mode={session.user.branchId ? "operation" : undefined}
      session={session}
      subtitle="Busca al socio y registra la contratación en tu sucursal."
      title="Membresías y paquetes"
    >
      {!canAccess ? <InlineError message="Tu sesión no tiene permiso para gestionar contrataciones." />
        : !session.user.branchId ? <InlineError message="Selecciona una sucursal para continuar." />
        : <MemberSearch key={JSON.stringify([session.user.organizationId, session.user.branchId, session.user.userId])} session={session} />}
    </AppShell>
  );
}

function MemberSearch({ session }: { session: StoredSession }) {
  const [query, setQuery] = useState("");
  const [clients, setClients] = useState<ClientSummary[] | null>(null);
  const [selected, setSelected] = useState<ClientSummary | null>(null);
  const [operation, setOperation] = useState<ReceptionOperation | null>(null);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const input = useRef<HTMLInputElement>(null);
  const firstResult = useRef<HTMLButtonElement>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const searchVersion = useRef(0);
  const inFlightQuery = useRef<string | null>(null);
  const { canEnroll, canRenew, canChange } = resolveClientMembershipCapabilities(session.user);
  const allowed = { enroll: canEnroll, renew: canRenew, change: canChange, package: session.user.permissions.includes("sale.create") };

  useEffect(() => () => {
    clearTimeout(timer.current);
    searchVersion.current++;
  }, []);

  function cancelSearch() {
    clearTimeout(timer.current);
    searchVersion.current++;
    inFlightQuery.current = null;
  }

  async function search(term: string) {
    clearTimeout(timer.current);
    if (!term || inFlightQuery.current === term) return;
    const version = ++searchVersion.current;
    inFlightQuery.current = term;
    setClients(null);
    setError(null);
    setLoading(true);
    try {
      const matches = await searchClients(session.accessToken, term);
      if (version === searchVersion.current) setClients(matches);
    } catch (exception) {
      if (version === searchVersion.current) {
        setError(contractErrorMessage(exception, "No fue posible buscar al socio."));
      }
    } finally {
      if (version === searchVersion.current) {
        inFlightQuery.current = null;
        setLoading(false);
      }
    }
  }

  function changeQuery(value: string) {
    cancelSearch();
    setQuery(value);
    setSelected(null);
    setClients(null);
    setError(null);
    const term = value.trim();
    setLoading(Boolean(term));
    if (term) timer.current = setTimeout(() => void search(term), 300);
  }

  function selectClient(client: ClientSummary) {
    cancelSearch();
    setQuery("");
    setClients(null);
    setLoading(false);
    setSelected(client);
    input.current?.focus();
  }

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!busy && !clients) void search(query.trim());
  }

  return (
    <div className={`${styles.workspace} ${controls.controls}`}>
      <section className="app-card reception-toolbar">
        <div className="reception-step"><span>1</span><h2>Selecciona al socio</h2></div>
        <form aria-label="Búsqueda de socios" className="relative flex gap-2" onSubmit={submitSearch}>
          <div className="relative min-w-0 flex-1">
            <Search aria-hidden="true" className="pointer-events-none absolute left-3 top-1/2 size-5 -translate-y-1/2 text-[var(--brand-muted)]" />
            <input
              ref={input}
              aria-label="Nombre, teléfono o correo electrónico"
              className="app-input w-full"
              style={{ paddingInlineStart: "2.5rem" }}
              disabled={busy}
              onChange={(event) => changeQuery(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "ArrowDown" && firstResult.current) {
                  event.preventDefault();
                  firstResult.current.focus();
                }
                if (event.key === "Escape") {
                  cancelSearch();
                  setClients(null);
                  setLoading(false);
                }
              }}
              placeholder="Buscar socio por nombre, teléfono o correo..."
              value={query}
            />
            {clients && clients.length > 0 ? (
              <ul aria-label="Resultados de búsqueda" className="app-card absolute inset-x-0 top-full z-20 mt-1 grid max-h-60 gap-1 overflow-auto p-2"
                onKeyDown={(event) => {
                  if (event.key === "Escape") {
                    setClients(null);
                    input.current?.focus();
                  }
                }}>
                {clients.map((client, index) => (
                  <li key={client.id}>
                    <button
                      ref={index === 0 ? firstResult : undefined}
                      className="flex w-full flex-wrap items-center justify-between gap-2 rounded-md border border-[var(--brand-line)] p-3 text-left hover:bg-[var(--brand-surface)]"
                      onClick={() => selectClient(client)}
                      type="button"
                    >
                      <strong>{client.firstName} {client.lastName} {client.maternalLastName}</strong>
                      <span className="text-sm text-[var(--brand-muted)]">{client.phone || client.email}</span>
                    </button>
                  </li>
                ))}
              </ul>
            ) : null}
          </div>
          <button className="app-button shrink-0 sm:px-6" disabled={!query.trim() || busy} type="submit">Buscar socio</button>
        </form>
        {loading ? <p className="text-sm" role="status">Buscando socios…</p> : null}
        {error ? <AppNotice inline message={error} tone="error" /> : null}
        {clients?.length === 0 ? <p role="status" className="text-sm text-[var(--brand-muted)]">No se encontraron socios.</p> : null}
        <div className="reception-step"><span>2</span><h2>Elige una operación</h2></div>
        <div aria-label="Operaciones disponibles" role="group" className="grid gap-2 sm:grid-cols-2 xl:grid-cols-4">
          {(Object.keys(operations) as ReceptionOperation[]).map((action) => (
            <ReceptionOperationCard
              key={action}
              operation={action}
              disabled={busy || !allowed[action]}
              selected={operation === action}
              onSelect={() => {
                setOperation(action);
                if (!selected) input.current?.focus();
              }}
            />
          ))}
        </div>
        {operation && !selected ? <p role="status" className="text-sm text-[var(--brand-muted)]">Selecciona un socio para continuar.</p> : null}
      </section>
      {selected ? (
        <ReceptionMemberOperations
          key={selected.id}
          client={selected}
          operation={operation}
          onBusyChange={setBusy}
          onClear={() => { setSelected(null); input.current?.focus(); }}
          onSaleRecorded={() => setOperation(null)}
          session={session}
        />
      ) : null}
    </div>
  );
}
