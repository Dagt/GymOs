"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import {
  createAutomation,
  createNotification,
  executeAutomation,
  getAutomations,
  getNotifications,
  getStoredSession,
  setAutomationStatus,
  type AutomationRule,
  type NotificationLog,
  type StoredSession,
} from "@/lib/api";

const defaultActionConfig = JSON.stringify({
  channel: "IN_APP",
  subject: "Recordatorio GymOS",
  body: "Tienes una accion pendiente en GymOS.",
}, null, 2);

export default function AutomationsPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [rules, setRules] = useState<AutomationRule[]>([]);
  const [notifications, setNotifications] = useState<NotificationLog[]>([]);
  const [name, setName] = useState("");
  const [triggerType, setTriggerType] = useState("MEMBERSHIP_EXPIRING");
  const [conditionJson, setConditionJson] = useState("{\"daysBefore\":3}");
  const [actionConfigJson, setActionConfigJson] = useState(defaultActionConfig);
  const [notificationBody, setNotificationBody] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function load() {
      const stored = getStoredSession();
      if (!active) {
        return;
      }
      if (!stored) {
        setLoading(false);
        return;
      }
      setSession(stored);
      try {
        const [loadedRules, loadedNotifications] = await Promise.all([
          getAutomations(stored.accessToken),
          getNotifications(stored.accessToken),
        ]);
        if (active) {
          setRules(loadedRules);
          setNotifications(loadedNotifications);
        }
      } catch (exception) {
        if (active) {
          setError(exception instanceof Error ? exception.message : "No fue posible cargar automatizaciones.");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void load();
    return () => {
      active = false;
    };
  }, []);

  async function refresh(activeSession = session) {
    if (!activeSession) {
      return;
    }
    const [loadedRules, loadedNotifications] = await Promise.all([
      getAutomations(activeSession.accessToken),
      getNotifications(activeSession.accessToken),
    ]);
    setRules(loadedRules);
    setNotifications(loadedNotifications);
  }

  async function submitRule(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) {
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const created = await createAutomation(session.accessToken, {
        name,
        triggerType,
        conditionJson,
        actionType: "CREATE_NOTIFICATION",
        actionConfigJson,
        retryLimit: 3,
      });
      setName("");
      setMessage(`${created.name} creada.`);
      await refresh(session);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear automatizacion.");
    } finally {
      setSaving(false);
    }
  }

  async function submitNotification(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) {
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      await createNotification(session.accessToken, {
        channel: "IN_APP",
        subject: "Mensaje interno",
        body: notificationBody,
        triggerType: "MANUAL",
      });
      setNotificationBody("");
      setMessage("Notificacion en cola.");
      await refresh(session);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear notificacion.");
    } finally {
      setSaving(false);
    }
  }

  async function changeRule(rule: AutomationRule, action: "enable" | "disable") {
    if (!session) {
      return;
    }
    await setAutomationStatus(session.accessToken, rule.id, action);
    await refresh(session);
  }

  async function runRule(rule: AutomationRule) {
    if (!session) {
      return;
    }
    await executeAutomation(session.accessToken, rule.id, "manual-ui");
    await refresh(session);
  }

  if (loading) {
    return <Centered text="Cargando automatizaciones..." />;
  }

  if (!session) {
    return <AuthRequired />;
  }

  return (
    <main className="min-h-screen bg-[#f6f7f2] text-[#111815]">
      <header className="border-b border-[#d8ddd2] bg-white">
        <div className="mx-auto flex max-w-7xl flex-col gap-3 px-6 py-5 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <Link className="text-sm font-semibold text-[#51735f]" href="/reception">Recepcion</Link>
            <h1 className="mt-1 text-3xl font-semibold">Automatizaciones</h1>
            <p className="mt-1 text-sm text-[#5d6b63]">Reglas configurables, ejecuciones y notificaciones internas.</p>
          </div>
          <button className="h-10 rounded-md border border-[#cdd5ca] bg-white px-4 text-sm font-semibold text-[#42514a]" onClick={() => refresh()} type="button">Actualizar</button>
        </div>
      </header>

      <section className="mx-auto grid max-w-7xl gap-6 px-6 py-6 lg:grid-cols-[1fr_1fr]">
        <section className="grid gap-6">
          {error ? <Notice tone="error" text={error} /> : null}
          {message ? <Notice tone="ok" text={message} /> : null}
          <form className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm" onSubmit={submitRule}>
            <h2 className="text-xl font-semibold">Nueva regla</h2>
            <div className="mt-4 grid gap-3">
              <input className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm" onChange={(event) => setName(event.target.value)} placeholder="Nombre" value={name} />
              <select className="h-10 rounded-md border border-[#cdd5ca] bg-white px-3 text-sm" onChange={(event) => setTriggerType(event.target.value)} value={triggerType}>
                <option value="MEMBERSHIP_EXPIRING">Membresia por vencer</option>
                <option value="PAYMENT_COMPLETED">Pago completado</option>
                <option value="BOOKING_CONFIRMED">Reserva confirmada</option>
                <option value="SCHEDULED">Programada</option>
                <option value="MANUAL">Manual</option>
              </select>
              <textarea className="min-h-24 rounded-md border border-[#cdd5ca] px-3 py-2 font-mono text-xs" onChange={(event) => setConditionJson(event.target.value)} value={conditionJson} />
              <textarea className="min-h-32 rounded-md border border-[#cdd5ca] px-3 py-2 font-mono text-xs" onChange={(event) => setActionConfigJson(event.target.value)} value={actionConfigJson} />
            </div>
            <button className="mt-4 h-10 rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white disabled:opacity-60" disabled={saving || !name.trim()} type="submit">Crear regla</button>
          </form>

          <form className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm" onSubmit={submitNotification}>
            <h2 className="text-xl font-semibold">Notificacion manual</h2>
            <textarea className="mt-4 min-h-28 w-full rounded-md border border-[#cdd5ca] px-3 py-2 text-sm" onChange={(event) => setNotificationBody(event.target.value)} placeholder="Mensaje" value={notificationBody} />
            <button className="mt-4 h-10 rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white disabled:opacity-60" disabled={saving || !notificationBody.trim()} type="submit">Encolar</button>
          </form>
        </section>

        <section className="grid gap-6">
          <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
            <h2 className="text-xl font-semibold">Reglas</h2>
            <div className="mt-4 grid gap-3">
              {rules.map((rule) => (
                <article className="rounded-md border border-[#d8ddd2] p-4" key={rule.id}>
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <p className="font-semibold">{rule.name}</p>
                      <p className="mt-1 text-sm text-[#5d6b63]">{rule.triggerType} - {rule.actionType}</p>
                      {rule.nextRunAt ? (
                        <p className="mt-1 text-xs text-[#6f7a73]">
                          Proxima ejecucion: {formatDateTime(rule.nextRunAt)}
                        </p>
                      ) : null}
                    </div>
                    <span className="rounded-md bg-[#eef7f0] px-2 py-1 text-xs font-semibold text-[#31543d]">{rule.status}</span>
                  </div>
                  <div className="mt-4 flex flex-wrap gap-2">
                    <button className="h-9 rounded-md border border-[#cdd5ca] px-3 text-xs font-semibold" onClick={() => runRule(rule)} type="button">Ejecutar</button>
                    <button className="h-9 rounded-md border border-[#cdd5ca] px-3 text-xs font-semibold" onClick={() => changeRule(rule, rule.status === "ENABLED" ? "disable" : "enable")} type="button">
                      {rule.status === "ENABLED" ? "Deshabilitar" : "Habilitar"}
                    </button>
                  </div>
                </article>
              ))}
            </div>
          </section>

          <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
            <h2 className="text-xl font-semibold">Notificaciones</h2>
            <div className="mt-4 grid gap-3">
              {notifications.map((notification) => (
                <article className="rounded-md border border-[#d8ddd2] p-4" key={notification.id}>
                  <p className="font-semibold">{notification.subject ?? notification.channel}</p>
                  <p className="mt-1 text-sm text-[#5d6b63]">{notification.body}</p>
                  <p className="mt-3 text-xs text-[#6f7a73]">{notification.status} - {formatDateTime(notification.createdAt)}</p>
                </article>
              ))}
            </div>
          </section>
        </section>
      </section>
    </main>
  );
}

function Notice({ tone, text }: { tone: "ok" | "error"; text: string }) {
  const styles = tone === "ok" ? "border-[#bad9c4] bg-[#eff8f1] text-[#31563c]" : "border-[#e5b7b7] bg-[#fff4f4] text-[#8c2f2f]";
  return <p className={`rounded-md border px-3 py-2 text-sm ${styles}`}>{text}</p>;
}

function Centered({ text }: { text: string }) {
  return <main className="grid min-h-screen place-items-center bg-[#f6f7f2] px-6 text-[#111815]"><p className="rounded-md border border-[#d8ddd2] bg-white px-4 py-3 text-sm text-[#42514a]">{text}</p></main>;
}

function AuthRequired() {
  return <main className="grid min-h-screen place-items-center bg-[#f6f7f2] px-6 text-[#111815]"><Link className="rounded-md bg-[#13251c] px-4 py-3 text-sm font-semibold text-white" href="/login">Iniciar sesion</Link></main>;
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("es-MX", { dateStyle: "short", timeStyle: "short" }).format(new Date(value));
}
