"use client";

import { FormEvent, useEffect, useState } from "react";
import Link from "next/link";
import { AppDrawer } from "@/components/AppDrawer";
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

type AutomationRecipe = {
  id: string;
  title: string;
  description: string;
  triggerType: string;
  channel: RuleChannel;
  subject: string;
  body: string;
  daysBefore?: number;
  minutesBefore?: number;
};

const AUTOMATION_RECIPES: AutomationRecipe[] = [
  {
    id: "membership-expiring-email",
    title: "Avisar que la membresía vencerá",
    description: "Tres días antes, envía un correo al socio con su fecha de vencimiento.",
    triggerType: "MEMBERSHIP_EXPIRING",
    channel: "EMAIL",
    subject: "Tu membresía vence pronto",
    body: "Hola {{recipientName}}, tu membresía vence el {{endsOn}}. Acércate a recepción para renovarla.",
    daysBefore: 3,
  },
  {
    id: "payment-pending-team",
    title: "Revisar un pago pendiente",
    description: "Avisa al equipo cuando un pago necesita validación en GymOS.",
    triggerType: "PAYMENT_PENDING_VALIDATION",
    channel: "IN_APP",
    subject: "Pago pendiente de validar",
    body: "Hay un pago pendiente de validación. Revisa el comprobante y confirma o rechaza el cobro.",
  },
  {
    id: "booking-reminder-email",
    title: "Recordar una reservación",
    description: "Un día antes, envía al socio la fecha y hora de su próxima reserva.",
    triggerType: "BOOKING_REMINDER",
    channel: "EMAIL",
    subject: "Te esperamos en tu reservación",
    body: "Hola {{recipientName}}, recuerda que tienes una reservación el {{startsAt}}. Si no puedes asistir, comunícate con recepción.",
    minutesBefore: 1440,
  },
  {
    id: "booking-cancelled-team",
    title: "Dar seguimiento a una cancelación",
    description: "Crea un aviso interno cuando se cancela una reserva incluida.",
    triggerType: "BOOKING_CANCELLED",
    channel: "IN_APP",
    subject: "Reserva cancelada",
    body: "Se canceló una reserva. Revisa si el socio necesita reagendar o recibir seguimiento.",
  },
  {
    id: "recurring-charge-failed-email",
    title: "Avisar que falló una domiciliación",
    description: "Informa al socio que debe actualizar su método de pago en el kiosco.",
    triggerType: "RECURRING_CHARGE_FAILED",
    channel: "EMAIL",
    subject: "No pudimos procesar tu renovación",
    body: "Hola {{recipientName}}, no pudimos procesar tu renovación. Abre el kiosco para actualizar tu tarjeta.",
  },
];

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
  const [ruleSubject, setRuleSubject] = useState("Recordatorio GymOS");
  const [ruleBody, setRuleBody] = useState("");
  const [ruleChannel, setRuleChannel] = useState<RuleChannel>("IN_APP");
  const [notificationBody, setNotificationBody] = useState("");
  const [notificationChannel, setNotificationChannel] = useState<"IN_APP" | "EMAIL">("IN_APP");
  const [notificationRecipient, setNotificationRecipient] = useState("");
  const [notificationSubject, setNotificationSubject] = useState("Mensaje de GymOS");
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

  function openRecipe(recipe?: AutomationRecipe) {
    if (recipe) {
      setName(recipe.title);
      setTriggerType(recipe.triggerType);
      setRuleChannel(recipe.channel);
      setRuleSubject(recipe.subject);
      setRuleBody(recipe.body);
      if (recipe.daysBefore) setDaysBefore(recipe.daysBefore);
      if (recipe.minutesBefore) setBookingReminderMinutes(recipe.minutesBefore);
    } else {
      setName("");
      setTriggerType("MEMBERSHIP_EXPIRING");
      setRuleChannel("IN_APP");
      setRuleSubject("Recordatorio GymOS");
      setRuleBody("");
      setDaysBefore(3);
      setBookingReminderMinutes(1440);
      setPaymentMethod("ALL");
      setPaymentContext("ALL");
      setMinimumAmountPesos("");
    }
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
      session={session}
      subtitle="Reglas, correos, ejecuciones y recuperación operativa del gimnasio."
      title="Automatizaciones"
    >
      <section className="catalog-toolbar app-card p-5">
        <div>
          <p>Centro de automatización</p>
          <h2>Trabajo que GymOS ejecuta por ti</h2>
          <span>Elige qué debe ocurrir, a quién avisar y el mensaje. GymOS repetirá la tarea automáticamente.</span>
        </div>
        <div className="flex flex-wrap gap-3">
          <button className="app-button-secondary" onClick={() => setShowOperationalDetails((current) => !current)} type="button">
            {showOperationalDetails ? "Ocultar monitoreo" : "Ver monitoreo avanzado"}
          </button>
          {hasPermission(session, "notification.manage") ? <button className="app-button-secondary" onClick={() => setShowNotificationDrawer(true)} type="button">
            Enviar aviso ahora
          </button> : null}
          {hasPermission(session, "automation.manage") ? <button className="app-button" onClick={() => openRecipe()} type="button">Crear automatización</button> : null}
        </div>
      </section>

      {hasPermission(session, "automation.manage") ? (
        <section className="app-card grid gap-4 p-5">
          <div>
            <p className="app-eyebrow">Recetas listas para adaptar</p>
            <h2 className="mt-1 text-xl font-semibold">Empieza con un caso común</h2>
            <p className="mt-1 text-sm text-[var(--brand-muted)]">En una organización nueva no se crean reglas automáticamente. Estas recetas sólo preparan el formulario: podrás revisar todo antes de crear y activar una.</p>
          </div>
          <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-5">
            {AUTOMATION_RECIPES.map((recipe) => (
              <AutomationRecipeCard key={recipe.id} onUse={() => openRecipe(recipe)} recipe={recipe} />
            ))}
          </div>
        </section>
      ) : null}

      <section className="rounded-lg border border-[var(--brand-line)] bg-white p-4 text-sm shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <strong>¿Quieres avisar por correo?</strong>
            <p className="mt-1 text-[var(--brand-muted)]">Configura el proveedor y el remitente antes de usar una receta de correo. Los avisos internos para el equipo no necesitan esta configuración.</p>
          </div>
          <Link className="app-button-secondary" href="/settings/organization#email-settings">Configurar correo</Link>
        </div>
      </section>

      {error ? <InlineError message={error} /> : null}
      {message ? <Notice tone="ok" text={message} /> : null}

      {operations && (showOperationalDetails || hasOperationalIssues) ? (
        <section className={`rounded-lg border p-5 ${operations.alertLevel === "CRITICAL" ? "border-[#e5b7b7] bg-[#fff7f7]" : "border-[var(--brand-line)] bg-white"}`}>
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <p className="app-eyebrow">Estado operativo</p>
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

      <section className="grid gap-4 md:grid-cols-3">
        <AutomationPurpose
          description="Selecciona un evento real: vencimiento, pago, reserva o domiciliación."
          eyebrow="Paso 1"
          title="Elige el momento"
        />
        <AutomationPurpose
          description="Usa un aviso interno para el equipo o un correo cuando el evento identifica al socio."
          eyebrow="Paso 2"
          title="Elige quién recibe"
        />
        <AutomationPurpose
          description="GymOS registra cada intento, reintenta fallos y conserva el historial para revisión."
          eyebrow="Paso 3"
          title="Revisa y activa"
        />
      </section>

      <Notice
        tone="ok"
        text="Puedes automatizar avisos sobre pagos, reservas incluidas, membresías y domiciliaciones. Los recursos reservables y servicios wellness con costo permanecen fuera del alcance definido."
      />

      {showOperationalDetails || deadLetterTotalElements > 0 ? <section className="rounded-lg border border-[var(--brand-line)] bg-white p-5 shadow-sm">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <p className="app-eyebrow">Recuperación operativa</p>
            <h2 className="mt-1 text-xl font-semibold">Fallos definitivos</h2>
            <p className="mt-1 text-sm text-[var(--brand-muted)]">
              Ocurrencias que agotaron sus reintentos automáticos y requieren una decisión humana.
            </p>
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

      <section className="grid gap-6 lg:grid-cols-[1fr_1fr]">
        <section className="rounded-lg border border-[var(--brand-line)] bg-white p-5 shadow-sm">
          <h2 className="text-xl font-semibold">Automatizaciones configuradas</h2>
          <div className="mt-4 grid gap-3">
            {rules.length === 0 ? (
              <p className="rounded-md border border-[#e4e8df] bg-[#fafbf8] px-3 py-3 text-sm text-[var(--brand-muted)]">
                Aún no hay automatizaciones. Elige una receta o crea una desde cero.
              </p>
            ) : null}
            {rules.map((rule) => (
              <article className="rounded-md border border-[var(--brand-line)] p-4" key={rule.id}>
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

        <section className="rounded-lg border border-[var(--brand-line)] bg-white p-5 shadow-sm">
          <h2 className="text-xl font-semibold">Entregas recientes</h2>
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
        <section className="rounded-lg border border-[var(--brand-line)] bg-white p-5 shadow-sm">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <p className="app-eyebrow">Historial operativo</p>
              <h2 className="mt-1 text-xl font-semibold">{selectedRule.name}</h2>
              <p className="mt-1 text-xs text-[var(--brand-muted)]">{executionTotalElements} ejecuciones registradas.</p>
              <p className="mt-1 text-sm text-[var(--brand-muted)]">Intentos, duración, referencia y último error sanitizado.</p>
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
        description="Completa tres pasos. Antes de guardar verás exactamente cuándo se ejecutará y qué mensaje se entregará."
        onClose={() => setShowRuleDrawer(false)}
        open={showRuleDrawer}
        title="Crear automatización"
      >
        <form className="grid gap-4" onSubmit={submitRule}>
          <div className="grid grid-cols-3 gap-2 text-center text-xs font-semibold text-[var(--brand-muted)]">
            <span className="rounded-md bg-[var(--brand-surface)] px-2 py-2">1. Cuándo</span>
            <span className="rounded-md bg-[var(--brand-surface)] px-2 py-2">2. A quién</span>
            <span className="rounded-md bg-[var(--brand-surface)] px-2 py-2">3. Mensaje</span>
          </div>
          <label className="grid gap-2 text-sm font-medium text-[#42514a]">
            Nombre para reconocerla
            <input className="h-10 rounded-md border border-[var(--brand-line)] px-3 text-sm" onChange={(event) => setName(event.target.value)} placeholder="Ej. Avisar membresías por vencer" required value={name} />
          </label>
          <label className="grid gap-2 text-sm font-medium text-[#42514a]">
            1. ¿Cuándo debe ejecutarse?
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
            2. ¿A quién debe avisar?
            <select className="h-10 rounded-md border border-[var(--brand-line)] bg-white px-3 text-sm" onChange={(event) => setRuleChannel(event.target.value as RuleChannel)} value={ruleChannel}>
              <option value="IN_APP">Notificación interna para el equipo</option>
              <option disabled={!supportsClientEmail(triggerType)} value="EMAIL">Correo al socio relacionado</option>
            </select>
            {!supportsClientEmail(triggerType) ? <small>Este evento no identifica automáticamente a un socio.</small> : null}
          </label>
          {ruleChannel === "EMAIL" ? <p className="rounded-md border border-[var(--brand-line)] bg-[var(--brand-surface)] p-3 text-xs text-[var(--brand-muted)]">El correo se clasifica como transaccional u operativo. Las campañas promocionales permanecen deshabilitadas hasta incorporar el flujo público de suscripción y baja.</p> : null}
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
              <small>Si el sistema estuvo detenido, enviará el recordatorio al recuperarse siempre que la reserva aún no haya comenzado.</small>
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
                  placeholder="500"
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
            3. Asunto
            <input
              className="h-10 rounded-md border border-[var(--brand-line)] px-3 text-sm"
              onChange={(event) => setRuleSubject(event.target.value)}
              placeholder="Asunto de la notificación"
              value={ruleSubject}
            />
          </label>
          <label className="grid gap-2 text-sm font-medium text-[#42514a]">
            {ruleChannel === "EMAIL" ? "3. Contenido del correo" : "3. Mensaje interno"}
            <textarea
              className="min-h-28 rounded-md border border-[var(--brand-line)] px-3 py-2 text-sm"
              onChange={(event) => setRuleBody(event.target.value)}
              placeholder={ruleChannel === "EMAIL" ? "Mensaje para el socio. Puedes usar {{recipientName}}, {{endsOn}} o {{amountCents}}." : "Describe qué debe revisar el equipo."}
              value={ruleBody}
            />
          </label>
          <section className="rounded-md border border-[var(--brand-line)] bg-[var(--brand-surface)] p-3 text-sm">
            <p className="font-semibold">Vista previa</p>
            <p className="mt-2 text-xs uppercase tracking-wide text-[var(--brand-muted)]">{triggerLabel(triggerType)}</p>
            <p className="mt-1 text-xs text-[var(--brand-muted)]">{conditionDescription({ bookingReminderMinutes, daysBefore, intervalMinutes, minimumAmountPesos, paymentContext, paymentMethod, triggerType })}</p>
            <p className="mt-2 font-semibold">{ruleSubject.trim() || name.trim() || "Sin asunto"}</p>
            <p className="mt-1 whitespace-pre-wrap text-[var(--brand-muted)]">{ruleBody.trim() || "Escribe el mensaje que recibirá el equipo."}</p>
            <p className="mt-2 text-xs text-[var(--brand-muted)]">La regla reintenta hasta 3 veces; si no se recupera, aparece en “Fallos definitivos”. Los correos conservan además su propia cola de entrega.</p>
          </section>
          <button className="form-primary-button" disabled={saving || !name.trim() || !ruleBody.trim()} type="submit">Guardar y activar</button>
        </form>
      </AppDrawer>

      <AppDrawer
        description="Envía una notificación interna o un correo transaccional sin crear una regla automática."
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
        description={recoveryAction === "retry"
          ? "La ocurrencia volverá a la cola durable conservando su identidad e historial."
          : "La ocurrencia dejará de reintentarse. La decisión y el motivo quedarán auditados."}
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
              placeholder={recoveryAction === "retry" ? "Ej. Se restableció el proveedor" : "Ej. Evento inválido confirmado"}
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

function conditionDescription(input: ConditionInput) {
  if (input.triggerType === "SCHEDULED") return `Cada ${input.intervalMinutes} minutos.`;
  if (input.triggerType === "MEMBERSHIP_EXPIRING") return `${input.daysBefore} días antes de la fecha de vencimiento.`;
  if (input.triggerType === "BOOKING_REMINDER") return bookingReminderDescription(input.bookingReminderMinutes);
  if (input.triggerType === "PAYMENT_COMPLETED") {
    return input.paymentContext === "ALL" ? "Para cualquier tipo de venta." : `Sólo para ${paymentContextLabel(input.paymentContext).toLowerCase()}.`;
  }
  if (input.triggerType === "PAYMENT_PENDING_VALIDATION") {
    const method = input.paymentMethod === "ALL" ? "cualquier método" : paymentMethodLabel(input.paymentMethod).toLowerCase();
    const amount = input.minimumAmountPesos.trim() ? ` desde $${input.minimumAmountPesos} MXN` : "";
    return `Pagos pendientes por ${method}${amount}.`;
  }
  return "Se ejecuta para todas las ocurrencias de este evento.";
}

function bookingReminderDescription(minutes: number) {
  if (minutes % 1440 === 0) {
    const days = minutes / 1440;
    return `${days} ${days === 1 ? "día" : "días"} antes de la reservación.`;
  }
  if (minutes % 60 === 0) {
    const hours = minutes / 60;
    return `${hours} ${hours === 1 ? "hora" : "horas"} antes de la reservación.`;
  }
  return `${minutes} minutos antes de la reservación.`;
}

function Notice({ tone, text }: { tone: "ok" | "error"; text: string }) {
  const styles = tone === "ok" ? "border-[var(--brand-line)] bg-[var(--brand-surface)] text-[#31563c]" : "border-[#e5b7b7] bg-[#fff4f4] text-[#8c2f2f]";
  return <p className={`rounded-md border px-3 py-2 text-sm ${styles}`}>{text}</p>;
}

function AutomationRecipeCard({ onUse, recipe }: { onUse: () => void; recipe: AutomationRecipe }) {
  return (
    <article className="flex min-h-56 flex-col rounded-lg border border-[var(--brand-line)] bg-[var(--brand-surface)] p-4">
      <span className="w-fit rounded-full bg-white px-2 py-1 text-xs font-semibold text-[var(--brand-muted)]">
        {recipe.channel === "EMAIL" ? "Correo al socio" : "Aviso al equipo"}
      </span>
      <h3 className="mt-3 font-semibold">{recipe.title}</h3>
      <p className="mt-2 flex-1 text-sm leading-6 text-[var(--brand-muted)]">{recipe.description}</p>
      <button aria-label={`Usar receta: ${recipe.title}`} className="app-button-secondary mt-4 w-full" onClick={onUse} type="button">Usar esta receta</button>
    </article>
  );
}

function AutomationPurpose({ description, eyebrow, title }: { description: string; eyebrow: string; title: string }) {
  return (
    <article className="rounded-lg border border-[var(--brand-line)] bg-white p-5 shadow-sm">
      <p className="app-eyebrow">{eyebrow}</p>
      <h2 className="mt-2 text-lg font-semibold">{title}</h2>
      <p className="mt-2 text-sm leading-6 text-[var(--brand-muted)]">{description}</p>
    </article>
  );
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

function paymentContextLabel(context: string) {
  return ({
    MEMBERSHIP_ENROLLMENT: "Contratación de membresía",
    MEMBERSHIP_PLAN_CHANGE: "Cambio de plan",
    MEMBERSHIP_RENEWAL: "Renovación de membresía",
    RETAIL_SALE: "Productos y servicios",
  } as Record<string, string>)[context] ?? context;
}

function paymentMethodLabel(method: string) {
  return ({
    CARD_MANUAL: "Terminal manual",
    CARD_MERCADO_PAGO: "Mercado Pago Point",
    CARD_ONLINE: "Stripe Checkout",
    CASH: "Efectivo",
    TRANSFER: "Transferencia",
  } as Record<string, string>)[method] ?? method;
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
