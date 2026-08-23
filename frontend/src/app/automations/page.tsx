"use client";

import Link from "next/link";
import { useEffect, useMemo, useState, type ReactNode } from "react";
import { AppDrawer } from "@/components/AppDrawer";
import { AppNotice } from "@/components/AppNotice";
import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import { ToggleSwitch } from "@/components/ToggleSwitch";
import {
  createAutomation,
  executeAutomation,
  getAutomationDeadLetters,
  getAutomationOperations,
  getAutomations,
  getEmailNotificationConfiguration,
  getNotifications,
  getStoredSession,
  recoverAutomationDeadLetter,
  retryNotificationEmail,
  searchClients,
  setAutomationStatus,
  type AutomationDeadLetter,
  type AutomationOperations,
  type AutomationRule,
  type ClientSummary,
  type EmailNotificationConfiguration,
  type NotificationLog,
  type StoredSession,
} from "@/lib/api";
import { testAutomationEmail } from "@/lib/api/automations";

type ViewTab = "AUTOMATIONS" | "ACTIVITY" | "PROBLEMS";
type RuleChannel = "IN_APP" | "EMAIL";

type Draft = {
  name: string;
  triggerType: string;
  channel: RuleChannel;
  subject: string;
  body: string;
  intervalMinutes: number;
  daysBefore: number;
  bookingReminderMinutes: number;
  paymentMethod: string;
  paymentContext: string;
  minimumAmountPesos: string;
};

const EMPTY_DRAFT: Draft = {
  name: "",
  triggerType: "MEMBERSHIP_EXPIRING",
  channel: "EMAIL",
  subject: "",
  body: "",
  intervalMinutes: 1440,
  daysBefore: 3,
  bookingReminderMinutes: 1440,
  paymentMethod: "ALL",
  paymentContext: "ALL",
  minimumAmountPesos: "",
};

export default function AutomationsPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [rules, setRules] = useState<AutomationRule[]>([]);
  const [notifications, setNotifications] = useState<NotificationLog[]>([]);
  const [deadLetters, setDeadLetters] = useState<AutomationDeadLetter[]>([]);
  const [operations, setOperations] = useState<AutomationOperations | null>(null);
  const [emailConfiguration, setEmailConfiguration] = useState<EmailNotificationConfiguration | null>(null);
  const [activeTab, setActiveTab] = useState<ViewTab>("AUTOMATIONS");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [showRuleDrawer, setShowRuleDrawer] = useState(false);
  const [wizardStep, setWizardStep] = useState(1);
  const [draft, setDraft] = useState<Draft>(EMPTY_DRAFT);
  const [testRule, setTestRule] = useState<AutomationRule | null>(null);
  const [testClientQuery, setTestClientQuery] = useState("");
  const [testClients, setTestClients] = useState<ClientSummary[]>([]);
  const [selectedTestClient, setSelectedTestClient] = useState<ClientSummary | null>(null);
  const [testClientsLoading, setTestClientsLoading] = useState(false);

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
        const data = await loadConsole(stored);
        if (!active) return;
        setRules(data.rules);
        setNotifications(data.notifications);
        setDeadLetters(data.deadLetters);
        setOperations(data.operations);
        setEmailConfiguration(data.emailConfiguration);
      } catch (exception) {
        if (active) setError(exception instanceof Error ? exception.message : "No fue posible cargar automatizaciones.");
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (!session || !testRule) return;
    const query = testClientQuery.trim();
    if (query.length < 2 || selectedTestClient) {
      return;
    }

    let active = true;
    const timer = window.setTimeout(async () => {
      setTestClientsLoading(true);
      try {
        const clients = await searchClients(session.accessToken, query, "ACTIVE");
        if (!active) return;
        setTestClients(clients.filter((client) => Boolean(client.email?.trim())).slice(0, 10));
      } catch (exception) {
        if (active) setError(exception instanceof Error ? exception.message : "No fue posible buscar socios.");
      } finally {
        if (active) setTestClientsLoading(false);
      }
    }, 300);

    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [session, testRule, testClientQuery, selectedTestClient]);

  const enabledRules = useMemo(() => rules.filter((rule) => rule.status === "ENABLED").length, [rules]);
  const problemCount = useMemo(() => {
    const failedEmails = notifications.filter((item) => item.status === "DEAD_LETTER" || item.status === "FAILED").length;
    return deadLetters.length + failedEmails;
  }, [deadLetters, notifications]);

  async function refresh() {
    if (!session) return;
    const data = await loadConsole(session);
    setRules(data.rules);
    setNotifications(data.notifications);
    setDeadLetters(data.deadLetters);
    setOperations(data.operations);
    setEmailConfiguration(data.emailConfiguration);
  }

  function openNewAutomation() {
    const channel: RuleChannel = emailConfiguration?.ready ? "EMAIL" : "IN_APP";
    setDraft({ ...EMPTY_DRAFT, channel });
    setWizardStep(1);
    setShowRuleDrawer(true);
    setError(null);
    setMessage(null);
  }

  function chooseTrigger(triggerType: string) {
    setDraft((current) => ({
      ...current,
      triggerType,
      channel: current.channel === "EMAIL" && !supportsClientEmail(triggerType) ? "IN_APP" : current.channel,
    }));
  }

  async function createRule() {
    if (!session || !draft.name.trim() || !draft.body.trim()) return;
    if (draft.channel === "EMAIL" && !emailConfiguration?.ready) {
      setError("Primero configura y verifica el canal de correo.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await createAutomation(session.accessToken, {
        name: draft.name.trim(),
        triggerType: draft.triggerType,
        conditionJson: JSON.stringify(buildCondition(draft)),
        actionType: "CREATE_NOTIFICATION",
        actionConfigJson: JSON.stringify({
          channel: draft.channel,
          category: "TRANSACTIONAL",
          subject: draft.subject.trim() || draft.name.trim(),
          body: draft.body.trim(),
        }),
        retryLimit: 3,
      });
      await refresh();
      setShowRuleDrawer(false);
      setWizardStep(1);
      setMessage("Automatización creada y activa.");
      setActiveTab("AUTOMATIONS");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear la automatización.");
    } finally {
      setSaving(false);
    }
  }

  async function toggleRule(rule: AutomationRule, checked: boolean) {
    if (!session) return;
    setSaving(true);
    setError(null);
    try {
      await setAutomationStatus(session.accessToken, rule.id, checked ? "enable" : "disable");
      await refresh();
      setMessage(checked ? "Automatización activada." : "Automatización pausada.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible cambiar el estado.");
    } finally {
      setSaving(false);
    }
  }

  async function runRule(rule: AutomationRule) {
    if (!session) return;
    if (isEmailRule(rule)) {
      openEmailTest(rule);
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await executeAutomation(session.accessToken, rule.id, `manual-ui:${crypto.randomUUID()}`);
      await refresh();
      setMessage("Prueba ejecutada. Revisa Actividad para confirmar el resultado.");
      setActiveTab("ACTIVITY");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible probar la automatización.");
    } finally {
      setSaving(false);
    }
  }

  function openEmailTest(rule: AutomationRule) {
    setTestRule(rule);
    setTestClientQuery("");
    setTestClients([]);
    setSelectedTestClient(null);
    setTestClientsLoading(false);
    setError(null);
  }

  function closeEmailTest() {
    setTestRule(null);
    setTestClientQuery("");
    setTestClients([]);
    setSelectedTestClient(null);
  }

  async function sendEmailTest() {
    if (!session || !testRule || !selectedTestClient) return;
    setSaving(true);
    setError(null);
    try {
      await testAutomationEmail(session.accessToken, testRule.id, selectedTestClient.id);
      closeEmailTest();
      await refresh();
      setMessage("Prueba enviada al socio seleccionado. Revisa Actividad para seguir la entrega.");
      setActiveTab("ACTIVITY");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible enviar la prueba.");
    } finally {
      setSaving(false);
    }
  }

  async function retryDeadLetter(item: AutomationDeadLetter) {
    if (!session) return;
    setSaving(true);
    setError(null);
    try {
      await recoverAutomationDeadLetter(
        session.accessToken,
        item.id,
        "retry",
        item.source,
        "Reintento solicitado desde la vista de Problemas",
      );
      await refresh();
      setMessage("El problema quedó programado para reintento.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible reintentar la automatización.");
    } finally {
      setSaving(false);
    }
  }

  async function retryEmail(notification: NotificationLog) {
    if (!session) return;
    setSaving(true);
    setError(null);
    try {
      await retryNotificationEmail(session.accessToken, notification.id);
      await refresh();
      setMessage("El correo volvió a la cola de entrega.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible reintentar el correo.");
    } finally {
      setSaving(false);
    }
  }

  if (loading) return <LoadingScreen text="Cargando automatizaciones..." />;
  if (!session) {
    return <AuthRequiredCard message="Las automatizaciones requieren acceso al gimnasio." title="Inicia sesión para continuar" />;
  }

  return (
    <AppShell
      eyebrow="Administración"
      session={session}
      subtitle="Automatiza recordatorios y avisos sin tener que supervisar el procesamiento técnico."
      title="Automatizaciones"
    >
      {error ? <InlineError message={error} /> : null}
      {message ? <AppNotice message={message} tone="success" /> : null}

      <section className="grid gap-4 md:grid-cols-3">
        <article className="app-card p-5">
          <p className="app-eyebrow">Activas</p>
          <strong className="mt-2 block text-3xl font-black text-[var(--brand-ink)]">{enabledRules}</strong>
          <span className="mt-1 block text-sm text-[var(--brand-muted)]">de {rules.length} configuradas</span>
        </article>
        <article className="app-card p-5">
          <p className="app-eyebrow">Correo</p>
          <strong className="mt-2 block text-xl font-black text-[var(--brand-ink)]">{emailConfiguration?.ready ? "Listo" : "Pendiente"}</strong>
          <span className="mt-1 block text-sm text-[var(--brand-muted)]">
            {emailConfiguration?.ready ? emailConfiguration.fromEmail : "Configura el canal antes de usar email"}
          </span>
          {hasAnyPermission(session, "notification.settings.manage", "platform.admin") ? (
            <Link className="mt-3 inline-block text-sm font-bold text-[var(--brand-primary)]" href="/settings/organization#email-settings">Revisar configuración</Link>
          ) : null}
        </article>
        <article className="app-card p-5">
          <p className="app-eyebrow">Estado</p>
          <strong className="mt-2 block text-xl font-black text-[var(--brand-ink)]">{problemCount === 0 ? "Todo en orden" : `${problemCount} por revisar`}</strong>
          <span className="mt-1 block text-sm text-[var(--brand-muted)]">
            {problemCount === 0 ? "No hay entregas ni automatizaciones con fallos." : "Abre Problemas para resolverlos."}
          </span>
        </article>
      </section>

      <section className="mt-5 flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap gap-2" role="tablist" aria-label="Secciones de automatizaciones">
          <TabButton active={activeTab === "AUTOMATIONS"} onClick={() => setActiveTab("AUTOMATIONS")}>Automatizaciones</TabButton>
          <TabButton active={activeTab === "ACTIVITY"} onClick={() => setActiveTab("ACTIVITY")}>Actividad</TabButton>
          <TabButton active={activeTab === "PROBLEMS"} onClick={() => setActiveTab("PROBLEMS")}>Problemas{problemCount > 0 ? ` (${problemCount})` : ""}</TabButton>
        </div>
        {hasPermission(session, "automation.manage") ? (
          <button className="app-button" onClick={openNewAutomation} type="button">+ Nueva automatización</button>
        ) : null}
      </section>

      {activeTab === "AUTOMATIONS" ? (
        <section className="mt-4 grid gap-3">
          {rules.length === 0 ? (
            <EmptyState title="Todavía no hay automatizaciones" copy="Crea la primera para que GymFort avise automáticamente a tus socios." />
          ) : rules.map((rule) => (
            <article className="app-card p-5" key={rule.id}>
              <div className="flex flex-wrap items-start justify-between gap-4">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <h2 className="app-card-title">{rule.name}</h2>
                    <span className="app-pill">{rule.status === "ENABLED" ? "Activa" : "Pausada"}</span>
                  </div>
                  <p className="app-card-copy mt-2">{ruleSummary(rule)}</p>
                  {rule.nextRunAt ? <p className="mt-2 text-xs text-[var(--brand-muted)]">Próxima ejecución: {formatDateTime(rule.nextRunAt)}</p> : null}
                </div>
                <div className="flex items-center gap-3">
                  {hasPermission(session, "automation.manage") ? (
                    <ToggleSwitch
                      checked={rule.status === "ENABLED"}
                      disabled={saving}
                      label={`${rule.status === "ENABLED" ? "Pausar" : "Activar"} ${rule.name}`}
                      onChange={(checked) => void toggleRule(rule, checked)}
                    />
                  ) : null}
                  {hasAnyPermission(session, "automation.execute", "automation.manage") ? (
                    <button
                      className="app-button-secondary"
                      disabled={saving || rule.status !== "ENABLED"}
                      onClick={() => void runRule(rule)}
                      type="button"
                    >
                      Probar ahora
                    </button>
                  ) : null}
                </div>
              </div>
            </article>
          ))}
        </section>
      ) : null}

      {activeTab === "ACTIVITY" ? (
        <section className="mt-4 grid gap-3">
          {notifications.length === 0 ? (
            <EmptyState title="Sin actividad todavía" copy="Aquí aparecerán los avisos y correos que genere GymFort." />
          ) : notifications.map((notification) => (
            <article className="app-card p-5" key={notification.id}>
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <h2 className="text-base font-black text-[var(--brand-ink)]">{notification.subject ?? triggerLabel(notification.triggerType ?? notification.channel)}</h2>
                  <p className="mt-1 text-sm text-[var(--brand-muted)]">{notification.body}</p>
                  <p className="mt-3 text-xs text-[var(--brand-muted)]">{activityDescription(notification)} · {formatDateTime(notification.sentAt ?? notification.createdAt)}</p>
                </div>
                <span className="app-pill">{notificationStatusLabel(notification.status)}</span>
              </div>
            </article>
          ))}
        </section>
      ) : null}

      {activeTab === "PROBLEMS" ? (
        <ProblemsView
          deadLetters={deadLetters}
          notifications={notifications}
          operations={operations}
          retryDeadLetter={retryDeadLetter}
          retryEmail={retryEmail}
          saving={saving}
          session={session}
        />
      ) : null}

      <AppDrawer onClose={() => setShowRuleDrawer(false)} open={showRuleDrawer} title="Nueva automatización">
        <AutomationWizard
          draft={draft}
          emailReady={Boolean(emailConfiguration?.ready)}
          onChooseTrigger={chooseTrigger}
          onDraftChange={setDraft}
          onFinish={() => void createRule()}
          onStepChange={setWizardStep}
          saving={saving}
          step={wizardStep}
        />
      </AppDrawer>

      <AppDrawer onClose={closeEmailTest} open={testRule !== null} title="Probar automatización">
        <section className="grid gap-4">
          <div>
            <p className="app-eyebrow">Prueba real de correo</p>
            <h3 className="app-card-title mt-1">{testRule?.name}</h3>
            <p className="app-card-copy mt-2">Busca un socio por nombre o correo. La prueba usará exactamente el asunto, mensaje y diseño configurados en esta regla.</p>
          </div>

          <div className="relative">
            <label className="form-field">
              <span>Socio destinatario</span>
              <input
                autoComplete="off"
                className="app-input"
                onChange={(event) => {
                  setTestClientQuery(event.target.value);
                  setSelectedTestClient(null);
                }}
                placeholder="Escribe nombre o correo..."
                value={testClientQuery}
              />
            </label>

            {testClientsLoading ? <p className="mt-2 text-sm text-[var(--brand-muted)]">Buscando socios...</p> : null}
            {!selectedTestClient && testClientQuery.trim().length > 0 && testClientQuery.trim().length < 2 ? (
              <p className="mt-2 text-xs text-[var(--brand-muted)]">Escribe al menos 2 caracteres.</p>
            ) : null}
            {!selectedTestClient && testClientQuery.trim().length >= 2 && !testClientsLoading && testClients.length === 0 ? (
              <p className="mt-2 text-sm text-[var(--brand-muted)]">No encontramos socios activos con correo que coincidan con la búsqueda.</p>
            ) : null}

            {!selectedTestClient && testClients.length > 0 ? (
              <div className="app-card absolute left-0 right-0 z-30 mt-2 max-h-64 overflow-y-auto p-2 shadow-xl">
                {testClients.map((client) => (
                  <button
                    className="block w-full rounded-xl px-3 py-3 text-left transition hover:bg-[var(--brand-primary-soft)]"
                    key={client.id}
                    onClick={() => {
                      setSelectedTestClient(client);
                      setTestClientQuery(`${clientDisplayName(client)} · ${client.email}`);
                      setTestClients([]);
                    }}
                    type="button"
                  >
                    <strong className="block text-sm font-black text-[var(--brand-ink)]">{clientDisplayName(client)}</strong>
                    <span className="mt-1 block text-xs text-[var(--brand-muted)]">{client.email}</span>
                  </button>
                ))}
              </div>
            ) : null}
          </div>

          {selectedTestClient ? (
            <article className="app-card p-4">
              <p className="app-eyebrow">Destinatario seleccionado</p>
              <strong className="mt-1 block text-sm font-black text-[var(--brand-ink)]">{clientDisplayName(selectedTestClient)}</strong>
              <span className="mt-1 block text-sm text-[var(--brand-muted)]">{selectedTestClient.email}</span>
            </article>
          ) : null}

          <div className="flex justify-end gap-2">
            <button className="app-button-secondary" onClick={closeEmailTest} type="button">Cancelar</button>
            <button className="app-button" disabled={saving || !selectedTestClient} onClick={() => void sendEmailTest()} type="button">Enviar prueba</button>
          </div>
        </section>
      </AppDrawer>
    </AppShell>
  );
}

async function loadConsole(session: StoredSession) {
  const [rules, notifications, deadLetters, operations, emailConfiguration] = await Promise.all([
    getAutomations(session.accessToken),
    hasPermission(session, "notification.read") ? getNotifications(session.accessToken, 50) : Promise.resolve([]),
    getAutomationDeadLetters(session.accessToken, 0, 25),
    getAutomationOperations(session.accessToken),
    hasAnyPermission(session, "notification.read", "notification.settings.manage", "platform.admin")
      ? getEmailNotificationConfiguration(session.accessToken)
      : Promise.resolve(null),
  ]);
  return { rules, notifications, deadLetters: deadLetters.items, operations, emailConfiguration };
}

function ProblemsView({
  deadLetters,
  notifications,
  operations,
  retryDeadLetter,
  retryEmail,
  saving,
  session,
}: {
  deadLetters: AutomationDeadLetter[];
  notifications: NotificationLog[];
  operations: AutomationOperations | null;
  retryDeadLetter: (item: AutomationDeadLetter) => Promise<void>;
  retryEmail: (item: NotificationLog) => Promise<void>;
  saving: boolean;
  session: StoredSession;
}) {
  const failedEmails = notifications.filter((item) => item.status === "DEAD_LETTER" || item.status === "FAILED");
  if (deadLetters.length === 0 && failedEmails.length === 0) {
    return (
      <section className="app-card mt-4 p-6">
        <p className="app-eyebrow">Estado</p>
        <h2 className="app-card-title mt-2">Todo funciona correctamente</h2>
        <p className="app-card-copy mt-2">No hay automatizaciones ni correos que requieran atención.</p>
        {operations?.schedulerPaused ? <p className="mt-3 text-sm font-bold text-[var(--brand-primary)]">El procesamiento global está pausado. Contacta a soporte si no fue intencional.</p> : null}
      </section>
    );
  }
  return (
    <section className="mt-4 grid gap-3">
      {deadLetters.map((item) => (
        <article className="app-card p-5" key={`${item.source}:${item.id}`}>
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div>
              <span className="app-pill">Automatización</span>
              <h2 className="mt-2 text-base font-black text-[var(--brand-ink)]">{item.ruleName ?? triggerLabel(item.triggerType)}</h2>
              <p className="mt-1 text-sm text-[var(--brand-muted)]">No pudo completarse después de varios intentos.</p>
              {item.errorMessage ? <p className="mt-2 text-sm text-[var(--brand-muted)]">{friendlyError(item.errorMessage)}</p> : null}
              <p className="mt-2 text-xs text-[var(--brand-muted)]">{formatDateTime(item.occurredAt)}</p>
            </div>
            {hasPermission(session, "automation.manage") ? (
              <button className="app-button-secondary" disabled={saving} onClick={() => void retryDeadLetter(item)} type="button">Reintentar</button>
            ) : null}
          </div>
        </article>
      ))}
      {failedEmails.map((item) => (
        <article className="app-card p-5" key={item.id}>
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div>
              <span className="app-pill">Correo</span>
              <h2 className="mt-2 text-base font-black text-[var(--brand-ink)]">{item.subject ?? "Correo no entregado"}</h2>
              <p className="mt-1 text-sm text-[var(--brand-muted)]">El correo no pudo entregarse al destinatario.</p>
              {item.errorMessage ? <p className="mt-2 text-sm text-[var(--brand-muted)]">{friendlyError(item.errorMessage)}</p> : null}
              <p className="mt-2 text-xs text-[var(--brand-muted)]">{formatDateTime(item.updatedAt)}</p>
            </div>
            {hasPermission(session, "notification.manage") ? (
              <button className="app-button-secondary" disabled={saving} onClick={() => void retryEmail(item)} type="button">Reintentar</button>
            ) : null}
          </div>
        </article>
      ))}
    </section>
  );
}

function AutomationWizard({
  draft,
  emailReady,
  onChooseTrigger,
  onDraftChange,
  onFinish,
  onStepChange,
  saving,
  step,
}: {
  draft: Draft;
  emailReady: boolean;
  onChooseTrigger: (triggerType: string) => void;
  onDraftChange: (draft: Draft) => void;
  onFinish: () => void;
  onStepChange: (step: number) => void;
  saving: boolean;
  step: number;
}) {
  return (
    <div className="grid gap-5">
      <div className="flex gap-2">
        {[1, 2, 3].map((number) => <span className="app-pill" key={number}>{number === step ? `Paso ${number}` : number}</span>)}
      </div>

      {step === 1 ? (
        <section className="grid gap-4">
          <div>
            <p className="app-eyebrow">Disparador</p>
            <h3 className="app-card-title mt-1">¿Cuándo debe ejecutarse?</h3>
            <p className="app-card-copy mt-2">Define la regla desde cero. Elige el evento y ajusta únicamente las condiciones que necesites.</p>
          </div>
          <label className="form-field">
            <span>Nombre de la automatización</span>
            <input
              className="app-input"
              maxLength={160}
              onChange={(event) => onDraftChange({ ...draft, name: event.target.value })}
              placeholder="Ej. Aviso de renovación pendiente"
              value={draft.name}
            />
          </label>
          <label className="form-field">
            <span>Evento</span>
            <select className="app-input" onChange={(event) => onChooseTrigger(event.target.value)} value={draft.triggerType}>
              {TRIGGERS.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
            </select>
          </label>
          <TriggerConditionFields draft={draft} onDraftChange={onDraftChange} />
          <div className="flex justify-end">
            <button className="app-button" disabled={!draft.name.trim()} onClick={() => onStepChange(2)} type="button">Continuar</button>
          </div>
        </section>
      ) : null}

      {step === 2 ? (
        <section className="grid gap-4">
          <div>
            <p className="app-eyebrow">Acción</p>
            <h3 className="app-card-title mt-1">¿Qué debe hacer GymFort?</h3>
          </div>
          <label className="form-field">
            <span>Canal</span>
            <select
              className="app-input"
              onChange={(event) => onDraftChange({ ...draft, channel: event.target.value as RuleChannel })}
              value={draft.channel}
            >
              <option value="IN_APP">Notificación interna</option>
              <option disabled={!supportsClientEmail(draft.triggerType) || !emailReady} value="EMAIL">Correo electrónico al socio</option>
            </select>
            {!emailReady && supportsClientEmail(draft.triggerType) ? <small>El correo está pendiente de configuración; puedes crearla como notificación interna.</small> : null}
          </label>
          <label className="form-field">
            <span>Asunto</span>
            <input
              className="app-input"
              maxLength={180}
              onChange={(event) => onDraftChange({ ...draft, subject: event.target.value })}
              placeholder="Escribe el asunto que verá el destinatario"
              value={draft.subject}
            />
          </label>
          <label className="form-field">
            <span>{draft.channel === "EMAIL" ? "Mensaje del correo" : "Mensaje"}</span>
            <textarea
              className="app-input min-h-40 py-3"
              onChange={(event) => onDraftChange({ ...draft, body: event.target.value })}
              placeholder="Escribe el mensaje..."
              value={draft.body}
            />
            <small>Usa {"{{recipientName}}"} para insertar el nombre del socio.</small>
          </label>
          <div className="flex justify-between gap-2">
            <button className="app-button-secondary" onClick={() => onStepChange(1)} type="button">Atrás</button>
            <button className="app-button" disabled={!draft.body.trim()} onClick={() => onStepChange(3)} type="button">Revisar</button>
          </div>
        </section>
      ) : null}

      {step === 3 ? (
        <section className="grid gap-4">
          <div>
            <p className="app-eyebrow">Revisión</p>
            <h3 className="app-card-title mt-1">Confirma la automatización</h3>
          </div>
          <article className="app-card p-4">
            <p className="text-sm font-black text-[var(--brand-ink)]">{draft.name}</p>
            <p className="mt-4 text-sm font-black text-[var(--brand-ink)]">Cuando</p>
            <p className="mt-1 text-sm text-[var(--brand-muted)]">{triggerLabel(draft.triggerType)}{conditionSummary(draft)}</p>
            <p className="mt-4 text-sm font-black text-[var(--brand-ink)]">Entonces</p>
            <p className="mt-1 text-sm text-[var(--brand-muted)]">{draft.channel === "EMAIL" ? "Enviar un correo al socio relacionado" : "Crear una notificación interna"}</p>
            <p className="mt-4 text-sm font-black text-[var(--brand-ink)]">Asunto</p>
            <p className="mt-1 text-sm text-[var(--brand-muted)]">{draft.subject || draft.name}</p>
          </article>
          <div className="flex justify-between gap-2">
            <button className="app-button-secondary" onClick={() => onStepChange(2)} type="button">Atrás</button>
            <button className="app-button" disabled={saving || !draft.name.trim() || !draft.body.trim()} onClick={onFinish} type="button">Activar automatización</button>
          </div>
        </section>
      ) : null}
    </div>
  );
}

function TriggerConditionFields({ draft, onDraftChange }: { draft: Draft; onDraftChange: (draft: Draft) => void }) {
  if (draft.triggerType === "MEMBERSHIP_EXPIRING") {
    return <label className="form-field"><span>Días antes del vencimiento</span><input className="app-input" max={90} min={1} onChange={(event) => onDraftChange({ ...draft, daysBefore: Number(event.target.value) })} type="number" value={draft.daysBefore} /></label>;
  }
  if (draft.triggerType === "BOOKING_REMINDER") {
    return <label className="form-field"><span>Anticipación</span><select className="app-input" onChange={(event) => onDraftChange({ ...draft, bookingReminderMinutes: Number(event.target.value) })} value={draft.bookingReminderMinutes}><option value={60}>1 hora antes</option><option value={180}>3 horas antes</option><option value={720}>12 horas antes</option><option value={1440}>1 día antes</option><option value={2880}>2 días antes</option><option value={10080}>7 días antes</option></select></label>;
  }
  if (draft.triggerType === "SCHEDULED") {
    return <label className="form-field"><span>Repetir cada cuántos minutos</span><input className="app-input" max={10080} min={1} onChange={(event) => onDraftChange({ ...draft, intervalMinutes: Number(event.target.value) })} type="number" value={draft.intervalMinutes} /></label>;
  }
  if (draft.triggerType === "PAYMENT_COMPLETED") {
    return <label className="form-field"><span>Contexto del pago</span><select className="app-input" onChange={(event) => onDraftChange({ ...draft, paymentContext: event.target.value })} value={draft.paymentContext}><option value="ALL">Cualquier pago</option><option value="RETAIL_SALE">Productos y servicios</option><option value="MEMBERSHIP_ENROLLMENT">Contratación de membresía</option><option value="MEMBERSHIP_RENEWAL">Renovación</option><option value="MEMBERSHIP_PLAN_CHANGE">Cambio de plan</option></select></label>;
  }
  if (draft.triggerType === "PAYMENT_PENDING_VALIDATION") {
    return <div className="grid gap-3"><label className="form-field"><span>Método de pago</span><select className="app-input" onChange={(event) => onDraftChange({ ...draft, paymentMethod: event.target.value })} value={draft.paymentMethod}><option value="ALL">Cualquier método</option><option value="CARD_MANUAL">Terminal manual</option><option value="CARD_MERCADO_PAGO">Mercado Pago Point</option><option value="CARD_ONLINE">Stripe Checkout</option><option value="TRANSFER">Transferencia</option><option value="CASH">Efectivo</option></select></label><label className="form-field"><span>Monto mínimo (opcional)</span><input className="app-input" min={0} onChange={(event) => onDraftChange({ ...draft, minimumAmountPesos: event.target.value })} step="0.01" type="number" value={draft.minimumAmountPesos} /></label></div>;
  }
  if (draft.triggerType === "RECURRING_CHARGE_DUE") {
    return <p className="app-card-copy rounded-xl border border-[var(--brand-line)] p-3">Se activa cuando llega la fecha programada del cobro recurrente y GymFort lo pone en proceso. No se activa al vincular por primera vez la tarjeta o crear la domiciliación.</p>;
  }
  return null;
}

function TabButton({ active, children, onClick }: { active: boolean; children: ReactNode; onClick: () => void }) {
  return <button aria-selected={active} className={active ? "app-button" : "app-button-secondary"} onClick={onClick} role="tab" type="button">{children}</button>;
}

function EmptyState({ title, copy }: { title: string; copy: string }) {
  return <section className="app-card p-7 text-center"><h2 className="app-card-title">{title}</h2><p className="app-card-copy mt-2">{copy}</p></section>;
}

function buildCondition(draft: Draft): Record<string, number | string> {
  if (draft.triggerType === "SCHEDULED") return { intervalMinutes: draft.intervalMinutes };
  if (draft.triggerType === "MEMBERSHIP_EXPIRING") return { daysBefore: draft.daysBefore };
  if (draft.triggerType === "BOOKING_REMINDER") return { minutesBefore: draft.bookingReminderMinutes };
  if (draft.triggerType === "PAYMENT_COMPLETED") return draft.paymentContext === "ALL" ? {} : { businessContext: draft.paymentContext };
  if (draft.triggerType === "PAYMENT_PENDING_VALIDATION") {
    const condition: Record<string, number | string> = {};
    if (draft.paymentMethod !== "ALL") condition.method = draft.paymentMethod;
    if (draft.minimumAmountPesos.trim()) condition.minimumAmountCents = Math.round(Number(draft.minimumAmountPesos) * 100);
    return condition;
  }
  return {};
}

function conditionSummary(draft: Draft) {
  if (draft.triggerType === "MEMBERSHIP_EXPIRING") return ` · ${draft.daysBefore} días antes`;
  if (draft.triggerType === "BOOKING_REMINDER") return ` · ${minutesLabel(draft.bookingReminderMinutes)}`;
  if (draft.triggerType === "SCHEDULED") return ` · cada ${draft.intervalMinutes} minutos`;
  return "";
}

function ruleSummary(rule: AutomationRule) {
  return `${triggerLabel(rule.triggerType)} → ${ruleDeliveryLabel(rule)}`;
}

function activityDescription(notification: NotificationLog) {
  return `${notificationChannelLabel(notification.channel)} · ${notificationStatusLabel(notification.status)}`;
}

function friendlyError(error: string) {
  const normalized = error.toLowerCase();
  if (normalized.includes("credenciales")) return "El proveedor de correo rechazó la conexión. Revisa la configuración de correo.";
  if (normalized.includes("destinatario") || normalized.includes("recipient")) return "No fue posible identificar o entregar al destinatario.";
  if (normalized.includes("timeout")) return "El proveedor tardó demasiado en responder. Puedes intentar nuevamente.";
  return error.length > 180 ? `${error.slice(0, 177)}...` : error;
}

function ruleDeliveryLabel(rule: AutomationRule) {
  try {
    const configuration = JSON.parse(rule.actionConfigJson || "{}") as { channel?: string };
    return notificationChannelLabel(configuration.channel ?? "IN_APP");
  } catch {
    return "Notificación";
  }
}

function isEmailRule(rule: AutomationRule) {
  try {
    const configuration = JSON.parse(rule.actionConfigJson || "{}") as { channel?: string };
    return configuration.channel === "EMAIL";
  } catch {
    return false;
  }
}

function notificationChannelLabel(channel: string) {
  return ({ EMAIL: "Correo al socio", IN_APP: "Notificación interna" } as Record<string, string>)[channel] ?? channel;
}

function notificationStatusLabel(status: string) {
  return ({
    DEAD_LETTER: "No entregado",
    FAILED: "Falló",
    PROCESSING: "Enviando",
    QUEUED: "Pendiente",
    RETRY_SCHEDULED: "Reintentando",
    SENT: "Enviado",
    SUPPRESSED: "No enviado por preferencia",
  } as Record<string, string>)[status] ?? status;
}

function triggerLabel(triggerType: string) {
  return ({
    MANUAL: "Ejecución manual",
    SCHEDULED: "Intervalo programado",
    PAYMENT_COMPLETED: "Pago completado",
    PAYMENT_PENDING_VALIDATION: "Pago pendiente de validar",
    MEMBERSHIP_EXPIRING: "Membresía por vencer",
    MEMBERSHIP_EXPIRED: "Membresía vencida",
    RECURRING_CHARGE_DUE: "Cobro domiciliado por procesar",
    RECURRING_CHARGE_SUCCEEDED: "Domiciliación cobrada",
    RECURRING_CHARGE_FAILED: "Domiciliación fallida",
    BOOKING_CREATED: "Reserva creada",
    BOOKING_CONFIRMED: "Reserva confirmada",
    BOOKING_REMINDER: "Recordatorio de reserva",
    BOOKING_CANCELLED: "Reserva cancelada",
  } as Record<string, string>)[triggerType] ?? triggerType;
}

function minutesLabel(minutes: number) {
  if (minutes === 60) return "1 hora antes";
  if (minutes % 1440 === 0) return `${minutes / 1440} día${minutes / 1440 === 1 ? "" : "s"} antes`;
  return `${minutes / 60} horas antes`;
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

function clientDisplayName(client: ClientSummary) {
  return [client.firstName, client.lastName, client.maternalLastName].filter(Boolean).join(" ") || "Socio";
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("es-MX", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function hasPermission(session: StoredSession, permission: string) {
  return session.user.permissions.includes(permission) || session.user.permissions.includes("platform.admin");
}

function hasAnyPermission(session: StoredSession, ...permissions: string[]) {
  return permissions.some((permission) => hasPermission(session, permission));
}

const TRIGGERS = [
  { value: "MEMBERSHIP_EXPIRING", label: "Una membresía está por vencer" },
  { value: "MEMBERSHIP_EXPIRED", label: "Una membresía venció" },
  { value: "PAYMENT_COMPLETED", label: "Se completa un pago" },
  { value: "PAYMENT_PENDING_VALIDATION", label: "Hay un pago pendiente de validar" },
  { value: "RECURRING_CHARGE_DUE", label: "Llega la fecha de un cobro domiciliado" },
  { value: "RECURRING_CHARGE_SUCCEEDED", label: "Una domiciliación se cobra" },
  { value: "RECURRING_CHARGE_FAILED", label: "Falla una domiciliación" },
  { value: "BOOKING_CREATED", label: "Se crea una reserva" },
  { value: "BOOKING_CONFIRMED", label: "Se confirma una reserva" },
  { value: "BOOKING_REMINDER", label: "Antes de una reserva" },
  { value: "BOOKING_CANCELLED", label: "Se cancela una reserva" },
  { value: "SCHEDULED", label: "En un intervalo programado" },
  { value: "MANUAL", label: "Manualmente" },
];
