"use client";

import { FormEvent, useEffect, useState } from "react";
import { AppDrawer } from "@/components/AppDrawer";
import { AppNotice } from "@/components/AppNotice";
import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import {
  createAutomation,
  createNotification,
  executeAutomation,
  getAutomationDeadLetters,
  getAutomationExecutions,
  getAutomationOperations,
  getAutomationScheduler,
  getAutomations,
  getNotifications,
  getStoredSession,
  recoverAutomationDeadLetter,
  retryNotificationEmail,
  setAutomationScheduler,
  setAutomationStatus,
  type AutomationDeadLetter,
  type AutomationRule,
  type AutomationExecution,
  type AutomationOperations,
  type AutomationSchedulerStatus,
  type NotificationLog,
  type StoredSession,
} from "@/lib/api";

type RuleChannel = "IN_APP" | "EMAIL";

export default function AutomationsPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [rules, setRules] = useState<AutomationRule[]>([]);
  const [notifications, setNotifications] = useState<NotificationLog[]>([]);
  const [deadLetters, setDeadLetters] = useState<AutomationDeadLetter[]>([]);
  const [deadLetterPage, setDeadLetterPage] = useState(0);
  const [deadLetterTotalPages, setDeadLetterTotalPages] = useState(0);
  const [deadLetterTotalElements, setDeadLetterTotalElements] = useState(0);
  const [operations, setOperations] = useState<AutomationOperations | null>(null);
  const [scheduler, setScheduler] = useState<AutomationSchedulerStatus | null>(null);
  const [executions, setExecutions] = useState<AutomationExecution[]>([]);
  const [executionPage, setExecutionPage] = useState(0);
  const [executionTotalPages, setExecutionTotalPages] = useState(0);
  const [executionTotalElements, setExecutionTotalElements] = useState(0);
  const [selectedRuleId, setSelectedRuleId] = useState<string | null>(null);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [name, setName] = useState("");
  const [triggerType, setTriggerType] = useState("MEMBERSHIP_EXPIRING");
  const [intervalMinutes, setIntervalMinutes] = useState(1440);
  const [daysBefore, setDaysBefore] = useState(3);
  const [bookingReminderMinutes, setBookingReminderMinutes] = useState(1440);
  const [paymentMethod, setPaymentMethod] = useState("ALL");
  const [minimumAmountPesos, setMinimumAmountPesos] = useState("");
  const [paymentContext, setPaymentContext] = useState("ALL");
  const [ruleSubject, setRuleSubject] = useState("Recordatorio GymFort");
  const [ruleBody, setRuleBody] = useState("");
  const [ruleChannel, setRuleChannel] = useState<RuleChannel>("IN_APP");
  const [notificationBody, setNotificationBody] = useState("");
  const [notificationChannel, setNotificationChannel] = useState<"IN_APP" | "EMAIL">("IN_APP");
  const [notificationRecipient, setNotificationRecipient] = useState("");
  const [notificationSubject, setNotificationSubject] = useState("Mensaje de GymFort");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [showRuleDrawer, setShowRuleDrawer] = useState(false);
  const [showNotificationDrawer, setShowNotificationDrawer] = useState(false);
  const [showOperationalDetails, setShowOperationalDetails] = useState(false);
  const [recoveryTarget, setRecoveryTarget] = useState<AutomationDeadLetter | null>(null);
  const [recoveryAction, setRecoveryAction] = useState<"retry" | "discard">("retry");
  const [recoveryReason, setRecoveryReason] = useState("");

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
        const [loadedRules, loadedNotifications, loadedDeadLetters, loadedOperations, loadedScheduler] = await Promise.all([
          getAutomations(stored.accessToken),
          hasPermission(stored, "notification.read")
            ? getNotifications(stored.accessToken)
            : Promise.resolve([]),
          getAutomationDeadLetters(stored.accessToken),
          getAutomationOperations(stored.accessToken),
          getAutomationScheduler(stored.accessToken),
        ]);
        if (active) {
          setRules(loadedRules);
          setNotifications(loadedNotifications);
          setDeadLetters(loadedDeadLetters.items);
          setDeadLetterTotalPages(loadedDeadLetters.totalPages);
          setDeadLetterTotalElements(loadedDeadLetters.totalElements);
          setOperations(loadedOperations);
          setScheduler(loadedScheduler);
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
    const [loadedRules, loadedNotifications, loadedDeadLetters, loadedOperations, loadedScheduler] = await Promise.all([
      getAutomations(activeSession.accessToken),
      hasPermission(activeSession, "notification.read")
        ? getNotifications(activeSession.accessToken)
        : Promise.resolve([]),
      getAutomationDeadLetters(activeSession.accessToken),
      getAutomationOperations(activeSession.accessToken),
      getAutomationScheduler(activeSession.accessToken),
    ]);
    setRules(loadedRules);
    setNotifications(loadedNotifications);
    setDeadLetters(loadedDeadLetters.items);
    setDeadLetterPage(0);
    setDeadLetterTotalPages(loadedDeadLetters.totalPages);
    setDeadLetterTotalElements(loadedDeadLetters.totalElements);
    setOperations(loadedOperations);
    setScheduler(loadedScheduler);
  }

  async function loadDeadLetters(page: number) {
    if (!session) return;
    const loaded = await getAutomationDeadLetters(session.accessToken, page);
    setDeadLetters(loaded.items);
    setDeadLetterPage(loaded.page);
    setDeadLetterTotalPages(loaded.totalPages);
    setDeadLetterTotalElements(loaded.totalElements);
  }

  async function toggleScheduler() {
    if (!session || !scheduler) return;
    const reason = scheduler.paused ? "Reanudado desde el centro de automatizaciones" : window.prompt("Motivo de la pausa operativa:");
    if (!scheduler.paused && !reason) return;
    try {
      setSaving(true);
      const updated = await setAutomationScheduler(session.accessToken, !scheduler.paused, reason ?? undefined);
      setScheduler(updated);
      setOperations(await getAutomationOperations(session.accessToken));
      setMessage(updated.paused ? "Procesamiento pausado para todo el gimnasio." : "Procesamiento reanudado.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible cambiar el scheduler.");
    } finally {
      setSaving(false);
    }
  }

  function openRuleDrawer() {
    setName("");
    setTriggerType("MEMBERSHIP_EXPIRING");
    setRuleChannel("IN_APP");
    setRuleSubject("");
    setRuleBody("");
    setDaysBefore(3);
    setBookingReminderMinutes(1440);
    setPaymentMethod("ALL");
    setPaymentContext("ALL");
    setMinimumAmountPesos("");
    setShowRuleDrawer(true);
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
        conditionJson: JSON.stringify(buildCondition({
          daysBefore,
          bookingReminderMinutes,
          intervalMinutes,
          minimumAmountPesos,
          paymentContext,
          paymentMethod,
          triggerType,
        })),
        actionType: "CREATE_NOTIFICATION",
        actionConfigJson: JSON.stringify({
          channel: ruleChannel,
          category: "TRANSACTIONAL",
          subject: ruleSubject.trim() || name.trim(),
          body: ruleBody.trim(),
        }),
        retryLimit: 3,
      });
      setName("");
      setRuleBody("");
      setMessage(`Automatización “${created.name}” creada y activa.`);
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
        channel: notificationChannel,
        recipient: notificationChannel === "EMAIL" ? notificationRecipient : null,
        recipientType: notificationChannel === "EMAIL" ? "DIRECT" : null,
        subject: notificationChannel === "EMAIL" ? notificationSubject : "Mensaje interno",
        body: notificationBody,
        category: "TRANSACTIONAL",
        triggerType: "MANUAL",
      });
      setNotificationBody("");
      setNotificationRecipient("");
      setMessage("Aviso agregado a la cola de entrega.");
      await refresh(session);
      setShowNotificationDrawer(false);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear notificacion.");
    } finally {
      setSaving(false);
    }
  }

  async function retryEmail(notification: NotificationLog) {
    if (!session) return;
    setError(null);
    try {
      await retryNotificationEmail(session.accessToken, notification.id);
      await refresh(session);
      setMessage("Correo devuelto a la cola de entrega.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible reintentar el correo.");
    }
  }

  async function changeRule(rule: AutomationRule, action: "enable" | "disable") {
    if (!session) {
      return;
    }
    setError(null);
    setMessage(null);
    try {
      await setAutomationStatus(session.accessToken, rule.id, action);
      await refresh(session);
      setMessage(`${rule.name} ${action === "enable" ? "habilitada" : "deshabilitada"}.`);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible cambiar el estado de la regla.");
    }
  }

  async function runRule(rule: AutomationRule) {
    if (!session) {
      return;
    }
    setError(null);
    try {
      await executeAutomation(session.accessToken, rule.id, `manual-ui:${crypto.randomUUID()}`);
      await refresh(session);
      await loadHistory(rule);
      setMessage(`Ejecucion de prueba registrada para ${rule.name}.`);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible ejecutar la regla.");
    }
  }

  async function loadHistory(rule: AutomationRule, page = 0) {
    if (!session) return;
    setSelectedRuleId(rule.id);
    setHistoryLoading(true);
    setError(null);
    try {
      const history = await getAutomationExecutions(session.accessToken, rule.id, page, 25);
      setExecutions(history.items);
      setExecutionPage(history.page);
      setExecutionTotalPages(history.totalPages);
      setExecutionTotalElements(history.totalElements);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible cargar el historial.");
    } finally {
      setHistoryLoading(false);
    }
  }

  function openRecovery(deadLetter: AutomationDeadLetter, action: "retry" | "discard") {
    setRecoveryTarget(deadLetter);
    setRecoveryAction(action);
    setRecoveryReason("");
  }

  async function submitRecovery(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || !recoveryTarget || !recoveryReason.trim()) return;
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const result = await recoverAutomationDeadLetter(
        session.accessToken,
        recoveryTarget.id,
        recoveryAction,
        recoveryTarget.source,
        recoveryReason.trim(),
      );
      await refresh(session);
      setRecoveryTarget(null);
      setRecoveryReason("");
      setMessage(result.status === "DISCARDED"
        ? "El fallo fue descartado y quedo auditado."
        : "El fallo quedo programado para reintento.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible recuperar la automatizacion.");
    } finally {
      setSaving(false);
    }
  }

  const selectedRule = rules.find((rule) => rule.id === selectedRuleId) ?? null;
  const hasOperationalIssues = Boolean(
    operations && (operations.alertLevel !== "HEALTHY" || operations.deadLetterEvents + operations.deadLetterExecutions > 0),
  );

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
      pageClassName="automations-console"
      session={session}
      subtitle="Supervisa reglas, avisos y recuperaciones sin perder de vista la salud del procesamiento."
      title="Automatizaciones"
    >
      <section className="automation-control-bar">
        <div>
          <p>Centro de control</p>
          <h2>Reglas, entregas y salud operativa</h2>
          <span>{rules.length} reglas configuradas · {notifications.length} entregas recientes</span>
        </div>
        <div className="automation-control-actions">
          <button className="app-button-secondary" onClick={() => setShowOperationalDetails((current) => !current)} type="button">
            {showOperationalDetails ? "Ocultar monitoreo" : "Ver monitoreo"}
          </button>
          {hasPermission(session, "notification.manage") ? <button className="app-button-secondary" onClick={() => setShowNotificationDrawer(true)} type="button">
            Enviar aviso
          </button> : null}
          {hasPermission(session, "automation.manage") ? <button className="app-button" onClick={openRuleDrawer} type="button">Crear automatización</button> : null}
        </div>
      </section>

      {error ? <InlineError message={error} /> : null}
      {message ? <AppNotice message={message} tone="success" /> : null}

      {operations && (showOperationalDetails || hasOperationalIssues) ? (
        <section className={`automation-health-panel ${operations.alertLevel === "CRITICAL" ? "critical" : "healthy"}`}>
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <h2 className="mt-1 text-xl font-semibold">{operations.alertLevel === "HEALTHY" ? "Procesamiento saludable" : "Automatizaciones requieren atención"}</h2>
              <p className="mt-1 text-sm text-[var(--brand-muted)]">
                {operations.pendingEvents} pendientes · {operations.retryEvents} en reintento · {operations.expiredLeases} trabajos con bloqueo vencido · {operations.deadLetterEvents + operations.deadLetterExecutions} fallos definitivos
              </p>
              {scheduler?.reason ? <p className="mt-2 text-sm">Motivo de pausa: {scheduler.reason}</p> : null}
            </div>
            <div className="flex flex-wrap gap-2">
              <button className="app-button-secondary" onClick={() => void refresh()} type="button">Actualizar estado</button>
              {hasPermission(session, "automation.scheduler.manage") || hasPermission(session, "platform.admin") ? (
                <button className={scheduler?.paused ? "app-button" : "app-button-secondary"} disabled={saving} onClick={() => void toggleScheduler()} type="button">
                  {scheduler?.paused ? "Reanudar todo" : "Pausar todas"}
                </button>
              ) : null}
            </div>
          </div>
        </section>
      ) : null}

      {showOperationalDetails || deadLetterTotalElements > 0 ? <section className="automation-panel p-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h2 className="mt-1 text-xl font-semibold">Fallos definitivos</h2>
          </div>
          <span className="rounded-md bg-[#fff0f0] px-3 py-1 text-sm font-semibold text-[#8c2f2f]">
            {deadLetterTotalElements} pendientes
          </span>
        </div>
        {deadLetters.length === 0 ? (
          <p className="mt-4 rounded-md border border-[var(--brand-line)] bg-[var(--brand-surface)] p-3 text-sm text-[var(--brand-muted)]">
            No hay fallos definitivos pendientes.
          </p>
        ) : (
          <div className="mt-4 overflow-x-auto">
            <table className="min-w-full text-left text-sm">
              <thead className="border-b border-[var(--brand-line)] text-xs uppercase text-[var(--brand-muted)]">
                <tr><th className="px-3 py-2">Regla / evento</th><th className="px-3 py-2">Intentos</th><th className="px-3 py-2">Error</th><th className="px-3 py-2">Fecha</th><th className="px-3 py-2">Acciones</th></tr>
              </thead>
              <tbody>{deadLetters.map((deadLetter) => (
                <tr className="border-b border-[var(--brand-line)]/70" key={`${deadLetter.source}:${deadLetter.id}`}>
                  <td className="px-3 py-3">
                    <span className="font-semibold">{deadLetter.ruleName ?? triggerLabel(deadLetter.triggerType)}</span>
                    <small className="mt-1 block max-w-72 truncate text-[var(--brand-muted)]" title={deadLetter.triggerReference}>
                      {deadLetterSourceLabel(deadLetter.source)} · {deadLetter.triggerReference}
                    </small>
                  </td>
                  <td className="px-3 py-3 font-semibold">{deadLetter.attemptNumber}</td>
                  <td className="max-w-md px-3 py-3 text-[#8c2f2f]">{deadLetter.errorMessage ?? "Sin detalle"}</td>
                  <td className="px-3 py-3">{formatDateTime(deadLetter.occurredAt)}</td>
                  <td className="px-3 py-3">
                    {hasPermission(session, "automation.manage") ? (
                      <div className="flex gap-2">
                        <button className="h-9 rounded-md border border-[var(--brand-line)] px-3 text-xs font-semibold" onClick={() => openRecovery(deadLetter, "retry")} type="button">Reintentar</button>
                        <button className="h-9 rounded-md border border-[#e5b7b7] px-3 text-xs font-semibold text-[#8c2f2f]" onClick={() => openRecovery(deadLetter, "discard")} type="button">Descartar</button>
                      </div>
                    ) : <span className="text-xs text-[var(--brand-muted)]">Requiere administración</span>}
                  </td>
                </tr>
              ))}</tbody>
            </table>
            {deadLetterTotalPages > 1 ? <div className="mt-4 flex items-center justify-end gap-3 text-sm">
              <button className="app-button-secondary" disabled={deadLetterPage === 0} onClick={() => void loadDeadLetters(deadLetterPage - 1)} type="button">Anterior</button>
              <span>Página {deadLetterPage + 1} de {deadLetterTotalPages}</span>
              <button className="app-button-secondary" disabled={deadLetterPage + 1 >= deadLetterTotalPages} onClick={() => void loadDeadLetters(deadLetterPage + 1)} type="button">Siguiente</button>
            </div> : null}
          </div>
        )}
      </section> : null}

      <section className="automation-primary-grid">
        <section className="automation-panel p-5">
          <h2 className="text-xl font-semibold">Automatizaciones configuradas</h2>
          <div className="mt-4 grid gap-3">
            {rules.length === 0 ? (
              <p className="rounded-md border border-[#e4e8df] bg-[#fafbf8] px-3 py-3 text-sm text-[var(--brand-muted)]">
                No hay automatizaciones configuradas.
              </p>
            ) : null}
            {rules.map((rule) => (
              <article className="automation-record p-4" key={rule.id}>
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="font-semibold">{rule.name}</p>
                    <p className="mt-1 text-sm text-[var(--brand-muted)]">{triggerLabel(rule.triggerType)} · {ruleDeliveryLabel(rule)}</p>
                    {!isSupportedTrigger(rule.triggerType) ? <p className="mt-1 text-xs font-semibold text-[#8c2f2f]">Definición heredada: falta implementar este disparador.</p> : null}
                    {rule.nextRunAt ? (
                      <p className="mt-1 text-xs text-[#6f7a73]">
                        Proxima ejecucion: {formatDateTime(rule.nextRunAt)}
                      </p>
                    ) : null}
                  </div>
                  <RuleStatus status={rule.status} />
                </div>
                <div className="mt-4 flex flex-wrap gap-2">
                  <button className="h-9 rounded-md border border-[var(--brand-line)] px-3 text-xs font-semibold" onClick={() => loadHistory(rule)} type="button">Ver actividad</button>
                  {isSupportedTrigger(rule.triggerType) && hasAnyPermission(session, "automation.execute", "automation.manage") ? <button className="h-9 rounded-md border border-[var(--brand-line)] px-3 text-xs font-semibold disabled:cursor-not-allowed disabled:opacity-50" disabled={rule.status !== "ENABLED"} onClick={() => runRule(rule)} type="button">Probar ahora</button> : null}
                  {hasPermission(session, "automation.manage") ? <>
                  <button className="h-9 rounded-md border border-[var(--brand-line)] px-3 text-xs font-semibold disabled:cursor-not-allowed disabled:opacity-50" disabled={!isSupportedTrigger(rule.triggerType) && rule.status !== "ENABLED"} onClick={() => changeRule(rule, rule.status === "ENABLED" ? "disable" : "enable")} type="button">
                    {rule.status === "ENABLED" ? "Deshabilitar" : "Habilitar"}
                  </button>
                  </> : null}
                </div>
              </article>
            ))}
          </div>
        </section>

        <section className="automation-panel p-5">
          <h2 className="text-xl font-semibold">Entregas recientes</h2>
          <div className="mt-4 grid gap-3">
            {notifications.length === 0 ? (
              <p className="rounded-md border border-[#e4e8df] bg-[#fafbf8] px-3 py-3 text-sm text-[var(--brand-muted)]">
                No hay notificaciones registradas.
              </p>
            ) : null}
            {notifications.map((notification) => (
              <article className="automation-record p-4" key={notification.id}>
                <p className="font-semibold">{notification.subject ?? notification.channel}</p>
                <p className="mt-1 text-sm text-[var(--brand-muted)]">{notification.body}</p>
                <p className="mt-3 text-xs text-[#6f7a73]">
                  {notificationChannelLabel(notification.channel)} · {notificationStatusLabel(notification.status)} · intento {notification.attemptCount} · {formatDateTime(notification.createdAt)}
                </p>
                {notification.errorMessage ? <p className="mt-2 text-xs text-[#8c2f2f]">{notification.errorMessage}</p> : null}
                {notification.channel === "EMAIL" && notification.status === "DEAD_LETTER" && hasPermission(session, "notification.manage") ? (
                  <button className="app-button-secondary mt-3" onClick={() => void retryEmail(notification)} type="button">Reintentar correo</button>
                ) : null}
              </article>
            ))}
          </div>
        </section>
      </section>

      {selectedRule ? (
        <section className="automation-panel p-5">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <h2 className="mt-1 text-xl font-semibold">{selectedRule.name}</h2>
              <p className="mt-1 text-xs text-[var(--brand-muted)]">{executionTotalElements} ejecuciones registradas.</p>
            </div>
            <button className="app-button-secondary" onClick={() => loadHistory(selectedRule)} type="button">Actualizar historial</button>
          </div>
          {historyLoading ? <p className="mt-4 text-sm text-[var(--brand-muted)]">Cargando ejecuciones...</p> : null}
          {!historyLoading && executions.length === 0 ? <p className="mt-4 rounded-md border border-[var(--brand-line)] bg-[var(--brand-surface)] p-3 text-sm text-[var(--brand-muted)]">Esta regla todavía no tiene ejecuciones.</p> : null}
          <div className="mt-4 overflow-x-auto">
            {executions.length > 0 ? <table className="min-w-full text-left text-sm">
              <thead className="border-b border-[var(--brand-line)] text-xs uppercase text-[var(--brand-muted)]"><tr><th className="px-3 py-2">Estado</th><th className="px-3 py-2">Intento</th><th className="px-3 py-2">Ocurrencia</th><th className="px-3 py-2">Duración</th><th className="px-3 py-2">Error</th></tr></thead>
              <tbody>{executions.map((execution) => <tr className="border-b border-[var(--brand-line)]/70" key={execution.id}>
                <td className="px-3 py-3"><ExecutionStatus status={execution.status} /></td>
                <td className="px-3 py-3 font-semibold">{execution.attemptNumber}</td>
                <td className="px-3 py-3"><span>{formatDateTime(execution.occurredAt)}</span><small className="mt-1 block max-w-80 truncate text-[var(--brand-muted)]" title={execution.triggerReference ?? undefined}>{execution.triggerReference ?? "Sin referencia"}</small></td>
                <td className="px-3 py-3">{formatDuration(execution.startedAt, execution.finishedAt)}</td>
                <td className="max-w-md px-3 py-3 text-[#8c2f2f]">{execution.errorMessage ?? "—"}</td>
              </tr>)}</tbody>
            </table> : null}
          </div>
          {executionTotalPages > 1 ? (
            <div className="mt-4 flex items-center justify-between gap-3 text-sm">
              <button
                className="app-button-secondary"
                disabled={historyLoading || executionPage === 0}
                onClick={() => void loadHistory(selectedRule, executionPage - 1)}
                type="button"
              >Anterior</button>
              <span className="text-[var(--brand-muted)]">Página {executionPage + 1} de {executionTotalPages}</span>
              <button
                className="app-button-secondary"
                disabled={historyLoading || executionPage + 1 >= executionTotalPages}
                onClick={() => void loadHistory(selectedRule, executionPage + 1)}
                type="button"
              >Siguiente</button>
            </div>
          ) : null}
        </section>
      ) : null}

      <AppDrawer
        onClose={() => setShowRuleDrawer(false)}
        open={showRuleDrawer}
        title="Crear automatización"
      >
        <form className="grid gap-4" onSubmit={submitRule}>
          <label className="grid gap-2 text-sm font-medium text-[#42514a]">
            Nombre
            <input className="h-10 rounded-md border border-[var(--brand-line)] px-3 text-sm" onChange={(event) => setName(event.target.value)} required value={name} />
          </label>
          <label className="grid gap-2 text-sm font-medium text-[#42514a]">
            Evento
            <select className="h-10 rounded-md border border-[var(--brand-line)] bg-white px-3 text-sm" onChange={(event) => {
              const nextTrigger = event.target.value;
              setTriggerType(nextTrigger);
              if (!supportsClientEmail(nextTrigger)) setRuleChannel("IN_APP");
            }} value={triggerType}>
              <option value="MANUAL">Manual</option>
              <option value="SCHEDULED">Programada por intervalo</option>
              <option value="PAYMENT_COMPLETED">Cuando se completa un pago</option>
              <option value="PAYMENT_PENDING_VALIDATION">Pago pendiente de validar</option>
              <option value="MEMBERSHIP_EXPIRING">Cuando una membresía está por vencer</option>
              <option value="MEMBERSHIP_EXPIRED">Cuando una membresía vence</option>
              <option value="RECURRING_CHARGE_DUE">Cuando inicia un cobro domiciliado</option>
              <option value="RECURRING_CHARGE_SUCCEEDED">Cuando una domiciliación se cobra</option>
              <option value="RECURRING_CHARGE_FAILED">Cuando falla una domiciliación</option>
              <option value="BOOKING_CREATED">Cuando se crea una reserva</option>
              <option value="BOOKING_CONFIRMED">Cuando se confirma una reserva</option>
              <option value="BOOKING_REMINDER">Antes de una reservación</option>
              <option value="BOOKING_CANCELLED">Cuando se cancela una reserva</option>
            </select>
          </label>
          <label className="grid gap-2 text-sm font-medium text-[#42514a]">
            Canal
            <select className="h-10 rounded-md border border-[var(--brand-line)] bg-white px-3 text-sm" onChange={(event) => setRuleChannel(event.target.value as RuleChannel)} value={ruleChannel}>
              <option value="IN_APP">Notificación interna para el equipo</option>
              <option disabled={!supportsClientEmail(triggerType)} value="EMAIL">Correo al socio relacionado</option>
            </select>
          </label>
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
            </label>
          ) : null}
          {triggerType === "MEMBERSHIP_EXPIRING" ? (
            <label className="grid gap-2 text-sm font-medium text-[#42514a]">
              Avisar cuántos días antes del vencimiento
              <input
                className="h-10 rounded-md border border-[var(--brand-line)] px-3 text-sm"
                max={90}
                min={1}
                onChange={(event) => setDaysBefore(Number(event.target.value))}
                type="number"
                value={daysBefore}
              />
            </label>
          ) : null}
          {triggerType === "BOOKING_REMINDER" ? (
            <label className="grid gap-2 text-sm font-medium text-[#42514a]">
              ¿Con cuánta anticipación debe avisar?
              <select
                className="h-10 rounded-md border border-[var(--brand-line)] bg-white px-3 text-sm"
                onChange={(event) => setBookingReminderMinutes(Number(event.target.value))}
                value={bookingReminderMinutes}
              >
                <option value={60}>1 hora antes</option>
                <option value={180}>3 horas antes</option>
                <option value={720}>12 horas antes</option>
                <option value={1440}>1 día antes</option>
                <option value={2880}>2 días antes</option>
                <option value={10080}>7 días antes</option>
              </select>
            </label>
          ) : null}
          {triggerType === "PAYMENT_PENDING_VALIDATION" ? (
            <>
              <label className="grid gap-2 text-sm font-medium text-[#42514a]">
                Método de pago
                <select className="h-10 rounded-md border border-[var(--brand-line)] bg-white px-3 text-sm" onChange={(event) => setPaymentMethod(event.target.value)} value={paymentMethod}>
                  <option value="ALL">Cualquier método</option>
                  <option value="CARD_MANUAL">Terminal manual</option>
                  <option value="CARD_MERCADO_PAGO">Mercado Pago Point</option>
                  <option value="CARD_ONLINE">Stripe Checkout</option>
                  <option value="TRANSFER">Transferencia</option>
                  <option value="CASH">Efectivo</option>
                </select>
              </label>
              <label className="grid gap-2 text-sm font-medium text-[#42514a]">
                Monto mínimo (MXN, opcional)
                <input
                  className="h-10 rounded-md border border-[var(--brand-line)] px-3 text-sm"
                  min={0}
                  onChange={(event) => setMinimumAmountPesos(event.target.value)}
                  step="0.01"
                  type="number"
                  value={minimumAmountPesos}
                />
              </label>
            </>
          ) : null}
          {triggerType === "PAYMENT_COMPLETED" ? (
            <label className="grid gap-2 text-sm font-medium text-[#42514a]">
              Contexto del pago
              <select className="h-10 rounded-md border border-[var(--brand-line)] bg-white px-3 text-sm" onChange={(event) => setPaymentContext(event.target.value)} value={paymentContext}>
                <option value="ALL">Cualquier contexto</option>
                <option value="RETAIL_SALE">Productos y servicios</option>
                <option value="MEMBERSHIP_ENROLLMENT">Contratación de membresía</option>
                <option value="MEMBERSHIP_RENEWAL">Renovación de membresía</option>
                <option value="MEMBERSHIP_PLAN_CHANGE">Cambio de plan</option>
              </select>
            </label>
          ) : null}
          <label className="grid gap-2 text-sm font-medium text-[#42514a]">
            Asunto
            <input
              className="h-10 rounded-md border border-[var(--brand-line)] px-3 text-sm"
              onChange={(event) => setRuleSubject(event.target.value)}
              placeholder="Asunto de la notificación"
              value={ruleSubject}
            />
          </label>
          <label className="grid gap-2 text-sm font-medium text-[#42514a]">
            {ruleChannel === "EMAIL" ? "Contenido del correo" : "Mensaje interno"}
            <textarea
              className="min-h-28 rounded-md border border-[var(--brand-line)] px-3 py-2 text-sm"
              onChange={(event) => setRuleBody(event.target.value)}
              value={ruleBody}
            />
          </label>
          <button className="form-primary-button" disabled={saving || !name.trim() || !ruleBody.trim()} type="submit">Guardar y activar</button>
        </form>
      </AppDrawer>

      <AppDrawer
        onClose={() => setShowNotificationDrawer(false)}
        open={showNotificationDrawer}
        title="Enviar aviso ahora"
      >
        <form className="grid gap-4" onSubmit={submitNotification}>
          <label className="grid gap-2 text-sm font-medium">
            Canal de entrega
            <select className="app-input" onChange={(event) => setNotificationChannel(event.target.value as "IN_APP" | "EMAIL")} value={notificationChannel}>
              <option value="IN_APP">Notificación interna</option>
              <option value="EMAIL">Correo electrónico</option>
            </select>
          </label>
          {notificationChannel === "EMAIL" ? (
            <>
              <label className="grid gap-2 text-sm font-medium">Destinatario<input className="app-input" onChange={(event) => setNotificationRecipient(event.target.value)} placeholder="socio@ejemplo.com" required type="email" value={notificationRecipient} /></label>
              <label className="grid gap-2 text-sm font-medium">Asunto<input className="app-input" maxLength={180} onChange={(event) => setNotificationSubject(event.target.value)} placeholder="Asunto" required value={notificationSubject} /></label>
            </>
          ) : null}
          <label className="grid gap-2 text-sm font-medium">Mensaje<textarea className="min-h-28 w-full rounded-md border border-[var(--brand-line)] px-3 py-2 text-sm" onChange={(event) => setNotificationBody(event.target.value)} placeholder="Mensaje" value={notificationBody} /></label>
          <button className="form-primary-button" disabled={saving || !notificationBody.trim() || (notificationChannel === "EMAIL" && (!notificationRecipient || !notificationSubject))} type="submit">Enviar aviso</button>
        </form>
      </AppDrawer>

      <AppDrawer
        onClose={() => setRecoveryTarget(null)}
        open={recoveryTarget !== null}
        title={recoveryAction === "retry" ? "Reintentar automatización" : "Descartar fallo"}
      >
        <form className="grid gap-4" onSubmit={submitRecovery}>
          <section className="rounded-md border border-[var(--brand-line)] bg-[var(--brand-surface)] p-3 text-sm">
            <p className="font-semibold">{recoveryTarget?.ruleName ?? triggerLabel(recoveryTarget?.triggerType ?? "")}</p>
            <p className="mt-1 break-all text-xs text-[var(--brand-muted)]">{recoveryTarget?.triggerReference}</p>
            {recoveryTarget?.errorMessage ? <p className="mt-2 text-sm text-[#8c2f2f]">{recoveryTarget.errorMessage}</p> : null}
          </section>
          <label className="grid gap-2 text-sm font-medium text-[#42514a]">
            Motivo de la decisión
            <textarea
              className="min-h-28 rounded-md border border-[var(--brand-line)] px-3 py-2 text-sm"
              maxLength={500}
              onChange={(event) => setRecoveryReason(event.target.value)}
              value={recoveryReason}
            />
          </label>
          <button className="form-primary-button" disabled={saving || !recoveryReason.trim()} type="submit">
            {recoveryAction === "retry" ? "Programar reintento" : "Descartar definitivamente"}
          </button>
        </form>
      </AppDrawer>
    </AppShell>
  );
}

type ConditionInput = {
  bookingReminderMinutes: number;
  daysBefore: number;
  intervalMinutes: number;
  minimumAmountPesos: string;
  paymentContext: string;
  paymentMethod: string;
  triggerType: string;
};

function buildCondition(input: ConditionInput): Record<string, number | string> {
  if (input.triggerType === "SCHEDULED") return { intervalMinutes: input.intervalMinutes };
  if (input.triggerType === "MEMBERSHIP_EXPIRING") return { daysBefore: input.daysBefore };
  if (input.triggerType === "BOOKING_REMINDER") return { minutesBefore: input.bookingReminderMinutes };
  if (input.triggerType === "PAYMENT_COMPLETED") {
    return input.paymentContext === "ALL" ? {} : { businessContext: input.paymentContext };
  }
  if (input.triggerType === "PAYMENT_PENDING_VALIDATION") {
    const condition: Record<string, number | string> = {};
    if (input.paymentMethod !== "ALL") condition.method = input.paymentMethod;
    if (input.minimumAmountPesos.trim()) {
      condition.minimumAmountCents = Math.round(Number(input.minimumAmountPesos) * 100);
    }
    return condition;
  }
  return {};
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("es-MX", { dateStyle: "short", timeStyle: "short" }).format(new Date(value));
}

function formatDuration(startedAt: string | null, finishedAt: string | null) {
  if (!startedAt || !finishedAt) return "En curso";
  const milliseconds = Math.max(0, new Date(finishedAt).getTime() - new Date(startedAt).getTime());
  if (milliseconds < 1000) return `${milliseconds} ms`;
  return `${(milliseconds / 1000).toFixed(1)} s`;
}

function triggerLabel(triggerType: string) {
  return ({
    MANUAL: "Ejecución manual",
    SCHEDULED: "Intervalo programado",
    PAYMENT_COMPLETED: "Pago completado",
    PAYMENT_PENDING_VALIDATION: "Pago pendiente de validar",
    MEMBERSHIP_EXPIRING: "Membresía por vencer",
    MEMBERSHIP_EXPIRED: "Membresía vencida",
    RECURRING_CHARGE_DUE: "Cobro domiciliado iniciado",
    RECURRING_CHARGE_SUCCEEDED: "Domiciliación cobrada",
    RECURRING_CHARGE_FAILED: "Domiciliación fallida",
    BOOKING_CREATED: "Reserva creada",
    BOOKING_CONFIRMED: "Reserva confirmada",
    BOOKING_REMINDER: "Recordatorio de reservación",
    BOOKING_CANCELLED: "Reserva cancelada",
  } as Record<string, string>)[triggerType] ?? triggerType;
}

function ruleDeliveryLabel(rule: AutomationRule) {
  try {
    const configuration = JSON.parse(rule.actionConfigJson || "{}") as { channel?: string };
    return notificationChannelLabel(configuration.channel ?? "IN_APP");
  } catch {
    return "Canal no reconocido";
  }
}

function notificationChannelLabel(channel: string) {
  return ({ EMAIL: "Correo al socio", IN_APP: "Notificación interna" } as Record<string, string>)[channel] ?? channel;
}

function notificationStatusLabel(status: string) {
  return ({
    DEAD_LETTER: "Entrega fallida",
    FAILED: "Fallida",
    PROCESSING: "En proceso",
    QUEUED: "En cola",
    RETRY_SCHEDULED: "Reintento programado",
    SENT: "Enviada",
  } as Record<string, string>)[status] ?? status;
}

function deadLetterSourceLabel(source: AutomationDeadLetter["source"]) {
  return source === "EVENT" ? "Evento de dominio" : "Ejecución de regla";
}

function isSupportedTrigger(triggerType: string) {
  return [
    "MANUAL",
    "SCHEDULED",
    "PAYMENT_COMPLETED",
    "PAYMENT_PENDING_VALIDATION",
    "MEMBERSHIP_EXPIRING",
    "MEMBERSHIP_EXPIRED",
    "RECURRING_CHARGE_DUE",
    "RECURRING_CHARGE_SUCCEEDED",
    "RECURRING_CHARGE_FAILED",
    "BOOKING_CREATED",
    "BOOKING_CONFIRMED",
    "BOOKING_REMINDER",
    "BOOKING_CANCELLED",
  ].includes(triggerType);
}

function supportsClientEmail(triggerType: string) {
  return [
    "PAYMENT_COMPLETED",
    "MEMBERSHIP_EXPIRING",
    "MEMBERSHIP_EXPIRED",
    "RECURRING_CHARGE_DUE",
    "RECURRING_CHARGE_SUCCEEDED",
    "RECURRING_CHARGE_FAILED",
    "BOOKING_CREATED",
    "BOOKING_CONFIRMED",
    "BOOKING_REMINDER",
    "BOOKING_CANCELLED",
  ].includes(triggerType);
}

function ExecutionStatus({ status }: { status: string }) {
  const tone = status === "SUCCEEDED"
    ? "bg-[#eef7f0] text-[#31543d]"
    : status === "RETRY_SCHEDULED"
      ? "bg-[#fff7dd] text-[#745b00]"
      : status === "DEAD_LETTER" || status === "FAILED"
        ? "bg-[#fff0f0] text-[#8c2f2f]"
        : "bg-[#eef1f3] text-[#42514a]";
  return <span className={`rounded-md px-2 py-1 text-xs font-semibold ${tone}`}>{executionStatusLabel(status)}</span>;
}

function RuleStatus({ status }: { status: string }) {
  const tone = status === "ENABLED"
    ? "bg-[#eef7f0] text-[#31543d]"
    : "bg-[#eef1f3] text-[#42514a]";
  return <span className={`rounded-md px-2 py-1 text-xs font-semibold ${tone}`}>{status === "ENABLED" ? "Habilitada" : "Deshabilitada"}</span>;
}

function executionStatusLabel(status: string) {
  return ({
    DEAD_LETTER: "Fallo definitivo",
    FAILED: "Fallida",
    PROCESSING: "En proceso",
    RETRY_SCHEDULED: "Reintento programado",
    SUCCEEDED: "Completada",
  } as Record<string, string>)[status] ?? status;
}

function hasPermission(session: StoredSession, permission: string) {
  return session.user.permissions.includes(permission)
    || session.user.permissions.includes("platform.admin");
}

function hasAnyPermission(session: StoredSession, ...permissions: string[]) {
  return permissions.some((permission) => hasPermission(session, permission));
}
