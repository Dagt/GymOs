"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
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
import {
  cancelAutomationWorkItem,
  getAutomationWorkItems,
  retryAutomationWorkItem,
} from "@/lib/api/automations";
import type {
  AutomationWorkItem,
  AutomationWorkItemStatus,
} from "@/lib/api/automation-types";

const WORK_ITEM_FILTERS: Array<{ label: string; value: "" | AutomationWorkItemStatus }> = [
  { label: "Todos", value: "" },
  { label: "Pendientes", value: "PENDING" },
  { label: "En proceso", value: "RUNNING" },
  { label: "Reintento programado", value: "RETRY_SCHEDULED" },
  { label: "Exitosos", value: "SUCCEEDED" },
  { label: "Dead letter", value: "DEAD_LETTER" },
  { label: "Cancelados", value: "CANCELLED" },
];

export default function AutomationsPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [rules, setRules] = useState<AutomationRule[]>([]);
  const [notifications, setNotifications] = useState<NotificationLog[]>([]);
  const [workItems, setWorkItems] = useState<AutomationWorkItem[]>([]);
  const [workStatus, setWorkStatus] = useState<"" | AutomationWorkItemStatus>("");
  const [name, setName] = useState("");
  const [triggerType, setTriggerType] = useState("MANUAL");
  const [intervalMinutes, setIntervalMinutes] = useState(1440);
  const [daysBeforeExpiry, setDaysBeforeExpiry] = useState(7);
  const [ruleSubject, setRuleSubject] = useState("Recordatorio GymOS");
  const [ruleBody, setRuleBody] = useState("");
  const [notificationBody, setNotificationBody] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [operatingWorkItem, setOperatingWorkItem] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [showRuleDrawer, setShowRuleDrawer] = useState(false);
  const [showNotificationDrawer, setShowNotificationDrawer] = useState(false);

  const ruleNames = useMemo(
    () => new Map(rules.map((rule) => [rule.id, rule.name])),
    [rules],
  );

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
        const [loadedRules, loadedNotifications, loadedWorkItems] = await Promise.all([
          getAutomations(stored.accessToken),
          hasPermission(stored, "notification.read")
            ? getNotifications(stored.accessToken)
            : Promise.resolve([]),
          getAutomationWorkItems(stored.accessToken),
        ]);
        if (active) {
          setRules(loadedRules);
          setNotifications(loadedNotifications);
          setWorkItems(loadedWorkItems);
        }
      } catch (exception) {
        if (active) {
          setError(exception instanceof Error ? exception.message : "No fue posible cargar automatizaciones.");
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    void load();
    return () => {
      active = false;
    };
  }, []);

  async function refresh(activeSession = session, status = workStatus) {
    if (!activeSession) return;
    const [loadedRules, loadedNotifications, loadedWorkItems] = await Promise.all([
      getAutomations(activeSession.accessToken),
      hasPermission(activeSession, "notification.read")
        ? getNotifications(activeSession.accessToken)
        : Promise.resolve([]),
      getAutomationWorkItems(activeSession.accessToken, {
        status: status || undefined,
      }),
    ]);
    setRules(loadedRules);
    setNotifications(loadedNotifications);
    setWorkItems(loadedWorkItems);
  }

  async function submitRule(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) return;
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const conditionJson = triggerType === "SCHEDULED"
        ? JSON.stringify({ intervalMinutes })
        : triggerType === "MEMBERSHIP_EXPIRING"
          ? JSON.stringify({ daysBeforeExpiry })
          : "{}";
      const created = await createAutomation(session.accessToken, {
        name,
        triggerType,
        conditionJson,
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
    if (!session) return;
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
    if (!session) return;
    setError(null);
    try {
      await setAutomationStatus(session.accessToken, rule.id, action);
      await refresh(session);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible cambiar la regla.");
    }
  }

  async function runRule(rule: AutomationRule) {
    if (!session) return;
    setError(null);
    try {
      await executeAutomation(session.accessToken, rule.id);
      setMessage(`${rule.name} fue enviada al motor de ejecucion.`);
      await refresh(session);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible ejecutar la regla.");
    }
  }

  async function changeWorkStatus(value: "" | AutomationWorkItemStatus) {
    setWorkStatus(value);
    if (!session) return;
    setError(null);
    try {
      setWorkItems(await getAutomationWorkItems(session.accessToken, { status: value || undefined }));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible filtrar las ocurrencias.");
    }
  }

  async function retryWorkItem(item: AutomationWorkItem) {
    if (!session) return;
    setOperatingWorkItem(item.id);
    setError(null);
    try {
      const result = await retryAutomationWorkItem(session.accessToken, item.id);
      setMessage(result.status === "SUCCEEDED"
        ? "La ocurrencia se reejecuto correctamente."
        : `La ocurrencia quedo en estado ${result.status}.`);
      await refresh(session);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible reintentar la ocurrencia.");
    } finally {
      setOperatingWorkItem(null);
    }
  }

  async function cancelWorkItem(item: AutomationWorkItem) {
    if (!session) return;
    setOperatingWorkItem(item.id);
    setError(null);
    try {
      await cancelAutomationWorkItem(session.accessToken, item.id);
      setMessage("La ocurrencia fue cancelada.");
      await refresh(session);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible cancelar la ocurrencia.");
    } finally {
      setOperatingWorkItem(null);
    }
  }

  if (loading) return <LoadingScreen text="Cargando automatizaciones..." />;

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
          {hasPermission(session, "notification.manage") ? (
            <button className="app-button-secondary" onClick={() => setShowNotificationDrawer(true)} type="button">
              Notificacion manual
            </button>
          ) : null}
          {hasPermission(session, "automation.manage") ? (
            <button className="app-button" onClick={() => setShowRuleDrawer(true)} type="button">Nueva regla</button>
          ) : null}
        </div>
      </section>

      {error ? <InlineError message={error} /> : null}
      {message ? <Notice tone="ok" text={message} /> : null}

      <section className="grid gap-4 md:grid-cols-3">
        <AutomationPurpose description="Avisar antes del vencimiento de membresías con una ocurrencia idempotente por socio y fecha." title="Vencimientos" />
        <AutomationPurpose description="Cuando exista domiciliación, programará el intento y reaccionará al resultado de la pasarela sin activar membresías por sí sola." title="Cobros recurrentes" />
        <AutomationPurpose description="Crear seguimiento después de pagos, reservas o incidencias sin depender de que alguien recuerde hacerlo." title="Dar seguimiento" />
      </section>

      <section className="grid gap-6 lg:grid-cols-[1fr_1fr]">
        <section className="rounded-lg border border-[var(--brand-line)] bg-white p-5 shadow-sm">
          <h2 className="text-xl font-semibold">Reglas</h2>
          <div className="mt-4 grid gap-3">
            {rules.length === 0 ? <EmptyState text="No hay reglas configuradas." /> : null}
            {rules.map((rule) => (
              <article className="rounded-md border border-[var(--brand-line)] p-4" key={rule.id}>
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="font-semibold">{rule.name}</p>
                    <p className="mt-1 text-sm text-[var(--brand-muted)]">{rule.triggerType} - {rule.actionType}</p>
                    {rule.nextRunAt ? <p className="mt-1 text-xs text-[#6f7a73]">Proxima ejecucion: {formatDateTime(rule.nextRunAt)}</p> : null}
                  </div>
                  <StatusBadge status={rule.status} />
                </div>
                {hasPermission(session, "automation.manage") ? (
                  <div className="mt-4 flex flex-wrap gap-2">
                    <button className="h-9 rounded-md border border-[var(--brand-line)] px-3 text-xs font-semibold" onClick={() => runRule(rule)} type="button">Ejecutar</button>
                    <button className="h-9 rounded-md border border-[var(--brand-line)] px-3 text-xs font-semibold" onClick={() => changeRule(rule, rule.status === "ENABLED" ? "disable" : "enable")} type="button">
                      {rule.status === "ENABLED" ? "Deshabilitar" : "Habilitar"}
                    </button>
                  </div>
                ) : null}
              </article>
            ))}
          </div>
        </section>

        <section className="rounded-lg border border-[var(--brand-line)] bg-white p-5 shadow-sm">
          <h2 className="text-xl font-semibold">Notificaciones</h2>
          <div className="mt-4 grid gap-3">
            {notifications.length === 0 ? <EmptyState text="No hay notificaciones registradas." /> : null}
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

      <section className="rounded-lg border border-[var(--brand-line)] bg-white p-5 shadow-sm">
        <div className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
          <div>
            <p className="app-eyebrow">Cola durable</p>
            <h2 className="mt-1 text-xl font-semibold">Ocurrencias e intentos</h2>
            <p className="mt-1 text-sm text-[var(--brand-muted)]">Consulta reintentos, fallos terminales y efectos generados por cada regla.</p>
          </div>
          <label className="grid gap-1 text-sm font-medium text-[#42514a]">
            Estado
            <select
              className="h-10 min-w-52 rounded-md border border-[var(--brand-line)] bg-white px-3 text-sm"
              onChange={(event) => changeWorkStatus(event.target.value as "" | AutomationWorkItemStatus)}
              value={workStatus}
            >
              {WORK_ITEM_FILTERS.map((filter) => <option key={filter.value || "all"} value={filter.value}>{filter.label}</option>)}
            </select>
          </label>
        </div>

        <div className="mt-5 grid gap-3">
          {workItems.length === 0 ? <EmptyState text="No hay ocurrencias para el filtro seleccionado." /> : null}
          {workItems.map((item) => {
            const busy = operatingWorkItem === item.id;
            return (
              <article className="rounded-md border border-[var(--brand-line)] p-4" key={item.id}>
                <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
                  <div>
                    <p className="font-semibold">{ruleNames.get(item.ruleId) ?? "Regla no disponible"}</p>
                    <p className="mt-1 text-xs text-[var(--brand-muted)]">Referencia: {item.triggerReference ?? "sin referencia"}</p>
                    <p className="mt-1 text-xs text-[var(--brand-muted)]">Intento {item.attemptCount} de {item.maxAttempts}</p>
                  </div>
                  <StatusBadge status={item.status} />
                </div>
                <div className="mt-3 grid gap-1 text-xs text-[#6f7a73] md:grid-cols-2">
                  <p>Programada: {formatDateTime(item.scheduledAt)}</p>
                  <p>Proximo intento: {formatDateTime(item.nextAttemptAt)}</p>
                  {item.finishedAt ? <p>Finalizada: {formatDateTime(item.finishedAt)}</p> : null}
                  {item.effectReferenceId ? <p>Efecto: {item.effectReferenceId}</p> : null}
                </div>
                {item.lastError ? (
                  <p className="mt-3 rounded-md border border-[#e5b7b7] bg-[#fff4f4] px-3 py-2 text-sm text-[#8c2f2f]">{item.lastError}</p>
                ) : null}
                {hasPermission(session, "automation.manage") && ["DEAD_LETTER", "PENDING", "RETRY_SCHEDULED"].includes(item.status) ? (
                  <div className="mt-4 flex flex-wrap gap-2">
                    {item.status === "DEAD_LETTER" ? (
                      <button className="app-button" disabled={busy} onClick={() => retryWorkItem(item)} type="button">Reintentar</button>
                    ) : null}
                    {["PENDING", "RETRY_SCHEDULED"].includes(item.status) ? (
                      <button className="app-button-secondary" disabled={busy} onClick={() => cancelWorkItem(item)} type="button">Cancelar</button>
                    ) : null}
                  </div>
                ) : null}
              </article>
            );
          })}
        </div>
      </section>

      <AppDrawer description="Define el disparador y la accion que GymOS ejecutara cuando se cumpla la condicion." onClose={() => setShowRuleDrawer(false)} open={showRuleDrawer} title="Nueva regla">
        <form className="grid gap-4" onSubmit={submitRule}>
          <input className="h-10 rounded-md border border-[var(--brand-line)] px-3 text-sm" onChange={(event) => setName(event.target.value)} placeholder="Nombre" value={name} />
          <select className="h-10 rounded-md border border-[var(--brand-line)] bg-white px-3 text-sm" onChange={(event) => setTriggerType(event.target.value)} value={triggerType}>
            <option value="MANUAL">Manual</option>
            <option value="SCHEDULED">Programada por intervalo</option>
            <option value="MEMBERSHIP_EXPIRING">Membresía próxima a vencer</option>
          </select>
          {triggerType === "SCHEDULED" ? (
            <label className="grid gap-2 text-sm font-medium text-[#42514a]">
              Repetir cada cuántos minutos
              <input className="h-10 rounded-md border border-[var(--brand-line)] px-3 text-sm" max={10080} min={1} onChange={(event) => setIntervalMinutes(Number(event.target.value))} type="number" value={intervalMinutes} />
              <small>Ejemplos: 60 = cada hora, 1440 = cada día, 10080 = cada semana.</small>
            </label>
          ) : null}
          {triggerType === "MEMBERSHIP_EXPIRING" ? (
            <label className="grid gap-2 text-sm font-medium text-[#42514a]">
              Avisar cuántos días antes del vencimiento
              <input className="h-10 rounded-md border border-[var(--brand-line)] px-3 text-sm" max={90} min={1} onChange={(event) => setDaysBeforeExpiry(Number(event.target.value))} type="number" value={daysBeforeExpiry} />
              <small>El motor crea una sola ocurrencia por regla, membresía y fecha de vencimiento.</small>
            </label>
          ) : null}
          <input className="h-10 rounded-md border border-[var(--brand-line)] px-3 text-sm" onChange={(event) => setRuleSubject(event.target.value)} placeholder="Asunto de la notificación" value={ruleSubject} />
          <label className="grid gap-2 text-sm font-medium text-[#42514a]">
            Mensaje interno
            <textarea className="min-h-28 rounded-md border border-[var(--brand-line)] px-3 py-2 text-sm" onChange={(event) => setRuleBody(event.target.value)} placeholder="Puedes usar {{endsOn}}, {{daysBeforeExpiry}}, {{membershipId}} o {{clientId}}." value={ruleBody} />
          </label>
          <button className="form-primary-button" disabled={saving || !name.trim() || !ruleBody.trim()} type="submit">Crear regla</button>
        </form>
      </AppDrawer>

      <AppDrawer description="Envia una notificacion interna sin crear una regla automatica." onClose={() => setShowNotificationDrawer(false)} open={showNotificationDrawer} title="Notificacion manual">
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

function EmptyState({ text }: { text: string }) {
  return <p className="rounded-md border border-[#e4e8df] bg-[#fafbf8] px-3 py-3 text-sm text-[var(--brand-muted)]">{text}</p>;
}

function StatusBadge({ status }: { status: string }) {
  const failure = status === "DEAD_LETTER";
  const pending = ["PENDING", "RUNNING", "RETRY_SCHEDULED"].includes(status);
  const styles = failure
    ? "bg-[#fff0f0] text-[#8c2f2f]"
    : pending
      ? "bg-[#fff7dd] text-[#765b09]"
      : "bg-[#eef7f0] text-[#31543d]";
  return <span className={`rounded-md px-2 py-1 text-xs font-semibold ${styles}`}>{status}</span>;
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
