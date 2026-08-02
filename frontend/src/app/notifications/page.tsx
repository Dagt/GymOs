"use client";

import { useEffect, useMemo, useState } from "react";
import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AuthRequired } from "@/components/AuthRequired";
import {
  getNotifications,
  getStoredSession,
  type NotificationLog,
  type StoredSession,
} from "@/lib/api";

type NotificationFilter = "ALL" | "ATTENTION" | "COMPLETED";

export default function NotificationsPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [notifications, setNotifications] = useState<NotificationLog[]>([]);
  const [filter, setFilter] = useState<NotificationFilter>("ALL");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    async function load() {
      const stored = getStoredSession();
      if (!active) return;
      if (!stored) {
        setLoading(false);
        return;
      }
      setSession(stored);
      try {
        const items = await getNotifications(stored.accessToken, 200);
        if (active) setNotifications(items);
      } catch (exception) {
        if (active) setError(exception instanceof Error ? exception.message : "No fue posible cargar los avisos.");
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => { active = false; };
  }, []);

  const visible = useMemo(() => notifications.filter((item) => {
    if (filter === "ATTENTION") return requiresAttention(item.status);
    if (filter === "COMPLETED") return !requiresAttention(item.status);
    return true;
  }), [filter, notifications]);

  const grouped = useMemo(() => {
    const groups = new Map<string, NotificationLog[]>();
    visible.forEach((item) => {
      const key = item.triggerType || "MANUAL";
      groups.set(key, [...(groups.get(key) ?? []), item]);
    });
    return [...groups.entries()];
  }, [visible]);

  if (loading) return <LoadingScreen text="Cargando centro de avisos..." />;
  if (!session) return <AuthRequired message="Inicia sesión para consultar los avisos del gimnasio." title="Centro de avisos" />;

  return (
    <AppShell
      eyebrow="Operacion"
      session={session}
      subtitle="Mensajes internos creados manualmente o por reglas automáticas."
      title="Centro de avisos"
    >
      {error ? <InlineError message={error} /> : null}

      <section className="app-card p-5">
        <div className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
          <div>
            <p className="app-eyebrow">Bandeja interna</p>
            <h2 className="mt-1 text-xl font-semibold">Alertas y notificaciones</h2>
            <p className="mt-1 text-sm text-[var(--brand-muted)]">
              Aquí aparecen los efectos de las reglas configuradas en Automatizaciones y los mensajes creados por el personal.
            </p>
          </div>
          <label className="grid gap-1 text-sm font-medium">
            Mostrar
            <select className="app-select min-w-52" onChange={(event) => setFilter(event.target.value as NotificationFilter)} value={filter}>
              <option value="ALL">Todos</option>
              <option value="ATTENTION">Requieren atención</option>
              <option value="COMPLETED">Procesados</option>
            </select>
          </label>
        </div>

        <div className="mt-5 grid gap-3 sm:grid-cols-3">
          <Metric label="Total" value={notifications.length} />
          <Metric label="Requieren atención" value={notifications.filter((item) => requiresAttention(item.status)).length} />
          <Metric label="Procesados" value={notifications.filter((item) => !requiresAttention(item.status)).length} />
        </div>
      </section>

      <section className="grid gap-5">
        {grouped.length === 0 ? (
          <section className="app-card p-8 text-center text-sm text-[var(--brand-muted)]">No hay avisos para el filtro seleccionado.</section>
        ) : null}
        {grouped.map(([trigger, items]) => (
          <section className="app-card p-5" key={trigger}>
            <div className="flex items-center justify-between gap-3">
              <div>
                <p className="app-eyebrow">Origen</p>
                <h2 className="mt-1 text-lg font-semibold">{humanize(trigger)}</h2>
              </div>
              <span className="rounded-full bg-[#edf4ef] px-3 py-1 text-xs font-semibold text-[#174f37]">{items.length}</span>
            </div>
            <div className="mt-4 grid gap-3">
              {items.map((notification) => (
                <article className="rounded-md border border-[var(--brand-line)] p-4" key={notification.id}>
                  <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                    <div>
                      <h3 className="font-semibold">{notification.subject || "Aviso interno"}</h3>
                      <p className="mt-1 text-sm text-[var(--brand-muted)]">{notification.body}</p>
                    </div>
                    <Status status={notification.status} />
                  </div>
                  <dl className="mt-4 grid gap-2 text-xs text-[#66726b] sm:grid-cols-3">
                    <div><dt>Creado</dt><dd>{formatDateTime(notification.createdAt)}</dd></div>
                    <div><dt>Canal</dt><dd>{notification.channel}</dd></div>
                    <div><dt>Referencia</dt><dd>{notification.referenceType || "—"}{notification.referenceId ? ` · ${notification.referenceId}` : ""}</dd></div>
                  </dl>
                  {notification.errorMessage ? <p className="mt-3 rounded-md bg-red-50 p-3 text-sm text-red-800">{notification.errorMessage}</p> : null}
                </article>
              ))}
            </div>
          </section>
        ))}
      </section>
    </AppShell>
  );
}

function Metric({ label, value }: { label: string; value: number }) {
  return <div className="rounded-md border border-[var(--brand-line)] p-4"><span className="text-sm text-[var(--brand-muted)]">{label}</span><strong className="mt-1 block text-2xl">{value}</strong></div>;
}

function Status({ status }: { status: string }) {
  const attention = requiresAttention(status);
  return <span className={`w-fit rounded-full px-3 py-1 text-xs font-semibold ${attention ? "bg-amber-100 text-amber-900" : "bg-emerald-100 text-emerald-900"}`}>{humanize(status)}</span>;
}

function requiresAttention(status: string) {
  return ["FAILED", "ERROR", "PENDING", "QUEUED", "RETRY_SCHEDULED"].includes(status.toUpperCase());
}

function humanize(value: string) {
  return value.toLowerCase().replaceAll("_", " ").replace(/^./, (letter) => letter.toUpperCase());
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("es-MX", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}
