"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AppNotice } from "@/components/AppNotice";
import { AuthRequired } from "@/components/AuthRequired";
import { getStoredSession, searchClients, type ClientSummary, type StoredSession } from "@/lib/api";
import { contractErrorMessage } from "@/lib/contract-error";
import { canAccessRoute } from "@/lib/role-model";
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
        : <MemberSearch key={session.user.branchId} session={session} />}
    </AppShell>
  );
}

function MemberSearch({ session }: { session: StoredSession }) {
  const [query, setQuery] = useState("");
  const [clients, setClients] = useState<ClientSummary[] | null>(null);
  const [selected, setSelected] = useState<ClientSummary | null>(null);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const searching = useRef(false);

  async function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!query.trim() || searching.current || busy) return;
    searching.current = true;
    setLoading(true);
    setSelected(null);
    setClients(null);
    setError(null);
    try {
      setClients(await searchClients(session.accessToken, query.trim()));
    } catch (exception) {
      setError(contractErrorMessage(exception, "No fue posible buscar al socio."));
    } finally {
      searching.current = false;
      setLoading(false);
    }
  }

  return (
    <div className="mx-auto grid w-full max-w-5xl gap-4">
      <section className="app-card grid gap-3 p-5">
        <h2 className="app-card-title">Buscar socio</h2>
        <form className="flex flex-col gap-2 sm:flex-row" onSubmit={search}>
          <input
            aria-label="Nombre, teléfono o correo electrónico"
            className="app-input min-w-0 flex-1"
            disabled={loading || busy}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Nombre, teléfono o correo electrónico"
            value={query}
          />
          <button className="app-button sm:px-6" disabled={!query.trim() || loading || busy} type="submit">
            {loading ? "Buscando…" : "Buscar socio"}
          </button>
        </form>
        {error ? <AppNotice message={error} tone="error" /> : null}
        {clients?.length === 0 ? <p className="text-sm text-[var(--brand-muted)]">No se encontraron socios.</p> : null}
        {clients && clients.length > 0 && !selected ? (
          <ul className="grid max-h-60 gap-1 overflow-auto">
            {clients.map((client) => (
              <li key={client.id}>
                <button
                  className="flex w-full flex-wrap items-center justify-between gap-2 rounded-md border border-[var(--brand-line)] p-3 text-left hover:border-[var(--brand-primary)]"
                  onClick={() => setSelected(client)}
                  type="button"
                >
                  <strong>{client.firstName} {client.lastName} {client.maternalLastName}</strong>
                  <span className="text-sm text-[var(--brand-muted)]">{client.phone || client.email}</span>
                </button>
              </li>
            ))}
          </ul>
        ) : null}
      </section>
      {selected ? (
        <ReceptionMemberOperations
          key={selected.id}
          client={selected}
          onBusyChange={setBusy}
          onClear={() => setSelected(null)}
          session={session}
        />
      ) : null}
    </div>
  );
}
