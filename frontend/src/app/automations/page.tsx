"use client";

import { FormEvent, useEffect, useState } from "react";
import { AppDrawer } from "@/components/AppDrawer";
import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
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
  const [showRuleDrawer, setShowRuleDrawer] = useState(false);
  const [showNotificationDrawer, setShowNotificationDrawer] = useState(false);

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
      setShowRuleDrawer(false);
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
      setShowNotificationDrawer(false);
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
    return <LoadingScreen text="Cargando automatizaciones..." />;
  }

  if (!session) {
    return (
      <AuthRequiredCard
        message="Las automatizaciones requieren permisos administrativos del gimnasio."
        title="Inicia sesion para ver automatizaciones"
      />
    );
  }

  return (
    <AppShell
      eyebrow="Administracion"
      session={session}
      subtitle="Reglas configurables, ejecuciones y notificaciones internas."
      title="Automatizaciones"
    >
      <section className="catalog-toolbar app-card p-5">
        <div>
          <p>Centro de automatizacion</p>
          <h2>Reglas y notificaciones</h2>
          <span>Gestiona automatizaciones sin saturar la pantalla principal.</span>
        </div>
        <div className="flex flex-wrap gap-3">
          <button className="app-button-secondary" onClick={() => refresh()} type="button">Actualizar</button>
          <button className="app-button-secondary" onClick={() => setShowNotificationDrawer(true)} type="button">
            Notificacion manual
          </button>
          <button className="app-button" onClick={() => setShowRuleDrawer(true)} type="button">Nueva regla</button>
        </div>
      </section>

      {error ? <InlineError message={error} /> : null}
      {message ? <Notice tone="ok" text={message} /> : null}

      <section className="grid gap-6 lg:grid-cols-[1fr_1fr]">
        <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
          <h2 className="text-xl font-semibold">Reglas</h2>
          <div className="mt-4 grid gap-3">
            {rules.length === 0 ? (
              <p className="rounded-md border border-[#e4e8df] bg-[#fafbf8] px-3 py-3 text-sm text-[#5d6b63]">
                No hay reglas configuradas.
              </p>
            ) : null}
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
            {notifications.length === 0 ? (
              <p className="rounded-md border border-[#e4e8df] bg-[#fafbf8] px-3 py-3 text-sm text-[#5d6b63]">
                No hay notificaciones registradas.
              </p>
            ) : null}
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

      <AppDrawer
        description="Define el disparador y la accion que GymOS ejecutara cuando se cumpla la condicion."
        onClose={() => setShowRuleDrawer(false)}
        open={showRuleDrawer}
        title="Nueva regla"
      >
        <form className="grid gap-4" onSubmit={submitRule}>
          <input className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm" onChange={(event) => setName(event.target.value)} placeholder="Nombre" value={name} />
          <select className="h-10 rounded-md border border-[#cdd5ca] bg-white px-3 text-sm" onChange={(event) => setTriggerType(event.target.value)} value={triggerType}>
            <option value="MEMBERSHIP_EXPIRING">Membresia por vencer</option>
            <option value="PAYMENT_COMPLETED">Pago completado</option>
            <option value="BOOKING_CONFIRMED">Reserva confirmada</option>
            <option value="SCHEDULED">Programada</option>
            <option value="MANUAL">Manual</option>
          </select>
          <label className="grid gap-2 text-sm font-medium text-[#42514a]">
            Condicion JSON
            <textarea className="min-h-24 rounded-md border border-[#cdd5ca] px-3 py-2 font-mono text-xs" onChange={(event) => setConditionJson(event.target.value)} value={conditionJson} />
          </label>
          <label className="grid gap-2 text-sm font-medium text-[#42514a]">
            Accion JSON
            <textarea className="min-h-32 rounded-md border border-[#cdd5ca] px-3 py-2 font-mono text-xs" onChange={(event) => setActionConfigJson(event.target.value)} value={actionConfigJson} />
          </label>
          <button className="form-primary-button" disabled={saving || !name.trim()} type="submit">Crear regla</button>
        </form>
      </AppDrawer>

      <AppDrawer
        description="Envia una notificacion interna sin crear una regla automatica."
        onClose={() => setShowNotificationDrawer(false)}
        open={showNotificationDrawer}
        title="Notificacion manual"
      >
        <form className="grid gap-4" onSubmit={submitNotification}>
          <textarea className="min-h-28 w-full rounded-md border border-[#cdd5ca] px-3 py-2 text-sm" onChange={(event) => setNotificationBody(event.target.value)} placeholder="Mensaje" value={notificationBody} />
          <button className="form-primary-button" disabled={saving || !notificationBody.trim()} type="submit">Encolar</button>
        </form>
      </AppDrawer>
    </AppShell>
  );
}

function Notice({ tone, text }: { tone: "ok" | "error"; text: string }) {
  const styles = tone === "ok" ? "border-[#bad9c4] bg-[#eff8f1] text-[#31563c]" : "border-[#e5b7b7] bg-[#fff4f4] text-[#8c2f2f]";
  return <p className={`rounded-md border px-3 py-2 text-sm ${styles}`}>{text}</p>;
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("es-MX", { dateStyle: "short", timeStyle: "short" }).format(new Date(value));
}
