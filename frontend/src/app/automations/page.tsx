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

export default function AutomationsPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [rules, setRules] = useState<AutomationRule[]>([]);
  const [notifications, setNotifications] = useState<NotificationLog[]>([]);
  const [name, setName] = useState("");
  const [triggerType, setTriggerType] = useState("MANUAL");
  const [intervalMinutes, setIntervalMinutes] = useState(1440);
  const [ruleSubject, setRuleSubject] = useState("Recordatorio GymOS");
  const [ruleBody, setRuleBody] = useState("");
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
          hasPermission(stored, "notification.read")
            ? getNotifications(stored.accessToken)
            : Promise.resolve([]),
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
      hasPermission(activeSession, "notification.read")
        ? getNotifications(activeSession.accessToken)
        : Promise.resolve([]),
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
        conditionJson: triggerType === "SCHEDULED"
          ? JSON.stringify({ intervalMinutes })
          : "{}",
        actionType: "CREATE_NOTIFICATION",
        actionConfigJson: JSON.stringify({
          channel: "IN_APP",
          subject: ruleSubject.trim() || name.trim(),
          body: ruleBody.trim(),
        }),
        retryLimit: 3,
      });
      setName("");
      setRuleBody("");
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
          <h2>Trabajo que GymOS ejecuta por ti</h2>
          <span>Una regla detecta un momento, comprueba condiciones y realiza una acción con historial.</span>
        </div>
        <div className="flex flex-wrap gap-3">
          <button className="app-button-secondary" onClick={() => refresh()} type="button">Actualizar</button>
          {hasPermission(session, "notification.manage") ? <button className="app-button-secondary" onClick={() => setShowNotificationDrawer(true)} type="button">
            Notificacion manual
          </button> : null}
          {hasPermission(session, "automation.manage") ? <button className="app-button" onClick={() => setShowRuleDrawer(true)} type="button">Nueva regla</button> : null}
        </div>
      </section>

      {error ? <InlineError message={error} /> : null}
      {message ? <Notice tone="ok" text={message} /> : null}

      <section className="grid gap-4 md:grid-cols-3">
        <AutomationPurpose
          description="Avisar al equipo o al socio antes de un vencimiento, una cita o una tarea pendiente."
          title="Recordar y avisar"
        />
        <AutomationPurpose
          description="Cuando exista domiciliación, programará el intento y reaccionará al resultado de la pasarela sin activar membresías por sí sola."
          title="Cobros recurrentes"
        />
        <AutomationPurpose
          description="Crear seguimiento después de pagos, reservas o incidencias sin depender de que alguien recuerde hacerlo."
          title="Dar seguimiento"
        />
      </section>

      <Notice
        tone="ok"
        text="Primera etapa: están habilitadas reglas manuales y programadas que crean notificaciones internas. Los eventos de membresías, pagos y reservas se activarán cuando tengan entrega durable e idempotente."
      />

      <section className="grid gap-6 lg:grid-cols-[1fr_1fr]">
        <section className="rounded-lg border border-[var(--brand-line)] bg-white p-5 shadow-sm">
          <h2 className="text-xl font-semibold">Reglas</h2>
          <div className="mt-4 grid gap-3">
            {rules.length === 0 ? (
              <p className="rounded-md border border-[#e4e8df] bg-[#fafbf8] px-3 py-3 text-sm text-[var(--brand-muted)]">
                No hay reglas configuradas.
              </p>
            ) : null}
            {rules.map((rule) => (
              <article className="rounded-md border border-[var(--brand-line)] p-4" key={rule.id}>
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="font-semibold">{rule.name}</p>
                    <p className="mt-1 text-sm text-[var(--brand-muted)]">{rule.triggerType} - {rule.actionType}</p>
                    {rule.nextRunAt ? (
                      <p className="mt-1 text-xs text-[#6f7a73]">
                        Proxima ejecucion: {formatDateTime(rule.nextRunAt)}
                      </p>
                    ) : null}
                  </div>
                  <span className="rounded-md bg-[#eef7f0] px-2 py-1 text-xs font-semibold text-[#31543d]">{rule.status}</span>
                </div>
                {hasPermission(session, "automation.manage") ? <div className="mt-4 flex flex-wrap gap-2">
                  <button className="h-9 rounded-md border border-[var(--brand-line)] px-3 text-xs font-semibold" onClick={() => runRule(rule)} type="button">Ejecutar</button>
                  <button className="h-9 rounded-md border border-[var(--brand-line)] px-3 text-xs font-semibold" onClick={() => changeRule(rule, rule.status === "ENABLED" ? "disable" : "enable")} type="button">
                    {rule.status === "ENABLED" ? "Deshabilitar" : "Habilitar"}
                  </button>
                </div> : null}
              </article>
            ))}
          </div>
        </section>

        <section className="rounded-lg border border-[var(--brand-line)] bg-white p-5 shadow-sm">
          <h2 className="text-xl font-semibold">Notificaciones</h2>
          <div className="mt-4 grid gap-3">
            {notifications.length === 0 ? (
              <p className="rounded-md border border-[#e4e8df] bg-[#fafbf8] px-3 py-3 text-sm text-[var(--brand-muted)]">
                No hay notificaciones registradas.
              </p>
            ) : null}
            {notifications.map((notification) => (
              <article className="rounded-md border border-[var(--brand-line)] p-4" key={notification.id}>
                <p className="font-semibold">{notification.subject ?? notification.channel}</p>
                <p className="mt-1 text-sm text-[var(--brand-muted)]">{notification.body}</p>
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
          <input className="h-10 rounded-md border border-[var(--brand-line)] px-3 text-sm" onChange={(event) => setName(event.target.value)} placeholder="Nombre" value={name} />
          <select className="h-10 rounded-md border border-[var(--brand-line)] bg-white px-3 text-sm" onChange={(event) => setTriggerType(event.target.value)} value={triggerType}>
            <option value="MANUAL">Manual</option>
            <option value="SCHEDULED">Programada por intervalo</option>
          </select>
          {triggerType === "SCHEDULED" ? (
            <label className="grid gap-2 text-sm font-medium text-[#42514a]">
              Repetir cada cuántos minutos
              <input
                className="h-10 rounded-md border border-[var(--brand-line)] px-3 text-sm"
                max={10080}
                min={1}
                onChange={(event) => setIntervalMinutes(Number(event.target.value))}
                type="number"
                value={intervalMinutes}
              />
              <small>Ejemplos: 60 = cada hora, 1440 = cada día, 10080 = cada semana.</small>
            </label>
          ) : null}
          <input
            className="h-10 rounded-md border border-[var(--brand-line)] px-3 text-sm"
            onChange={(event) => setRuleSubject(event.target.value)}
            placeholder="Asunto de la notificación"
            value={ruleSubject}
          />
          <label className="grid gap-2 text-sm font-medium text-[#42514a]">
            Mensaje interno
            <textarea
              className="min-h-28 rounded-md border border-[var(--brand-line)] px-3 py-2 text-sm"
              onChange={(event) => setRuleBody(event.target.value)}
              placeholder="Describe qué debe revisar el equipo."
              value={ruleBody}
            />
          </label>
          <button className="form-primary-button" disabled={saving || !name.trim() || !ruleBody.trim()} type="submit">Crear regla</button>
        </form>
      </AppDrawer>

      <AppDrawer
        description="Envia una notificacion interna sin crear una regla automatica."
        onClose={() => setShowNotificationDrawer(false)}
        open={showNotificationDrawer}
        title="Notificacion manual"
      >
        <form className="grid gap-4" onSubmit={submitNotification}>
          <textarea className="min-h-28 w-full rounded-md border border-[var(--brand-line)] px-3 py-2 text-sm" onChange={(event) => setNotificationBody(event.target.value)} placeholder="Mensaje" value={notificationBody} />
          <button className="form-primary-button" disabled={saving || !notificationBody.trim()} type="submit">Encolar</button>
        </form>
      </AppDrawer>
    </AppShell>
  );
}

function Notice({ tone, text }: { tone: "ok" | "error"; text: string }) {
  const styles = tone === "ok" ? "border-[var(--brand-line)] bg-[var(--brand-surface)] text-[#31563c]" : "border-[#e5b7b7] bg-[#fff4f4] text-[#8c2f2f]";
  return <p className={`rounded-md border px-3 py-2 text-sm ${styles}`}>{text}</p>;
}

function AutomationPurpose({ description, title }: { description: string; title: string }) {
  return (
    <article className="rounded-lg border border-[var(--brand-line)] bg-white p-5 shadow-sm">
      <p className="app-eyebrow">Para qué sirve</p>
      <h2 className="mt-2 text-lg font-semibold">{title}</h2>
      <p className="mt-2 text-sm leading-6 text-[var(--brand-muted)]">{description}</p>
    </article>
  );
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("es-MX", { dateStyle: "short", timeStyle: "short" }).format(new Date(value));
}

function hasPermission(session: StoredSession, permission: string) {
  return session.user.permissions.includes(permission)
    || session.user.permissions.includes("platform.admin");
}
