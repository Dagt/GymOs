"use client";

import { useEffect, useState } from "react";
import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AuthRequired } from "@/components/AuthRequired";
import {
  cancelOutboundDelivery,
  getOutboundDeliveries,
  getStoredSession,
  retryOutboundDelivery,
  type OutboundDelivery,
  type StoredSession,
} from "@/lib/api";

export default function DeliveriesPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [items, setItems] = useState<OutboundDelivery[]>([]);
  const [channel, setChannel] = useState("");
  const [status, setStatus] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  async function load(activeSession: StoredSession, nextChannel = channel, nextStatus = status) {
    setLoading(true);
    setError(null);
    try {
      setItems(await getOutboundDeliveries(activeSession.accessToken, { channel: nextChannel, status: nextStatus, limit: 200 }));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible cargar las entregas.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    const stored = getStoredSession();
    setSession(stored);
    if (stored) void load(stored, "", "");
    else setLoading(false);
  }, []);

  async function operate(id: string, action: "retry" | "cancel") {
    if (!session) return;
    try {
      if (action === "retry") await retryOutboundDelivery(session.accessToken, id);
      else await cancelOutboundDelivery(session.accessToken, id);
      await load(session);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible operar la entrega.");
    }
  }

  if (loading && !session) return <LoadingScreen text="Cargando entregas..." />;
  if (!session) return <AuthRequired title="Entregas de comunicación" message="Inicia sesión para continuar." />;

  return (
    <AppShell eyebrow="Automatizaciones" title="Entregas de comunicación" subtitle="Seguimiento técnico por correo y WhatsApp." session={session}>
      {error ? <InlineError message={error} /> : null}
      <section className="app-card p-5">
        <div className="grid gap-3 md:grid-cols-3">
          <label className="grid gap-1 text-sm">Canal
            <select className="app-select" value={channel} onChange={(event) => setChannel(event.target.value)}>
              <option value="">Todos</option><option value="EMAIL">Correo</option><option value="WHATSAPP">WhatsApp</option>
            </select>
          </label>
          <label className="grid gap-1 text-sm">Estado
            <select className="app-select" value={status} onChange={(event) => setStatus(event.target.value)}>
              <option value="">Todos</option>{["PENDING","SENDING","ACCEPTED","DELIVERED","FAILED","UNDELIVERABLE","CANCELLED"].map((value) => <option key={value}>{value}</option>)}
            </select>
          </label>
          <button className="app-button self-end" type="button" onClick={() => void load(session)}>Aplicar filtros</button>
        </div>
      </section>
      <section className="grid gap-3">
        {loading ? <LoadingScreen text="Actualizando entregas..." /> : null}
        {!loading && items.length === 0 ? <section className="app-card p-8 text-center">No hay entregas para los filtros seleccionados.</section> : null}
        {items.map((item) => (
          <article className="app-card p-5" key={item.id}>
            <div className="flex flex-col gap-3 md:flex-row md:justify-between">
              <div><strong>{item.channel} · {item.status}</strong><p className="mt-1 text-sm text-[var(--brand-muted)]">{item.subject || item.body}</p></div>
              <div className="flex gap-2">
                {["FAILED", "UNDELIVERABLE"].includes(item.status) && item.recipient ? <button className="app-button" onClick={() => void operate(item.id, "retry")}>Reintentar</button> : null}
                {["PENDING", "FAILED"].includes(item.status) ? <button className="app-button-secondary" onClick={() => void operate(item.id, "cancel")}>Cancelar</button> : null}
              </div>
            </div>
            <dl className="mt-4 grid gap-2 text-xs md:grid-cols-4">
              <div><dt>Destinatario</dt><dd>{item.recipient || "No disponible"}</dd></div>
              <div><dt>Intentos</dt><dd>{item.attemptCount}</dd></div>
              <div><dt>Plantilla</dt><dd>{item.templateCode || "Embebida"} v{item.templateVersion}</dd></div>
              <div><dt>Creada</dt><dd>{new Date(item.createdAt).toLocaleString("es-MX")}</dd></div>
            </dl>
            {item.lastError ? <p className="mt-3 rounded-md bg-red-50 p-3 text-sm text-red-800">{item.lastError}</p> : null}
          </article>
        ))}
      </section>
    </AppShell>
  );
}
