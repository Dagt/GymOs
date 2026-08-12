"use client";

import Link from "next/link";
import { FormEvent, useEffect, useLayoutEffect, useRef, useState } from "react";
import { AppShell, LoadingScreen } from "@/components/AppShell";
import { type FieldErrorMap } from "@/components/FormFields";
import {
  beginMercadoPagoOnboarding,
  beginStripeOnboarding,
  createBranch,
  getBranches,
  getCurrentOrganization,
  getMercadoPagoTerminals,
  getPaymentConfiguration,
  getStoredSession,
  refreshStripeConnection,
  selectMercadoPagoTerminal,
  storeCurrentUser,
  updateBranch,
  updateOrganization,
  updatePaymentConfiguration,
  type BranchSummary,
  type BranchUpdatePayload,
  type CreateBranchPayload,
  type MercadoPagoTerminal,
  type OrganizationSummary,
  type OrganizationUpdatePayload,
  type PaymentConfiguration,
  type StoredSession,
} from "@/lib/api";
import {
  emptyBranchForm,
  normalizeBranchForm,
  normalizeOrganizationForm,
  toBranchForm,
  toOrganizationForm,
  validateBranchForm,
  validateOrganizationForm,
} from "./OrganizationSettingsHelpers";
import {
  BranchesOverviewCard,
  EditBranchDrawer,
  NewBranchDrawer,
  OrganizationDrawer,
  OrganizationOverviewCard,
  SettingsSummary,
  TeamAccessCard,
} from "./OrganizationSettingsSections";
import { PaymentOperationsHistoryCard } from "./PaymentOperationsHistoryCard";
import { KioskBranchSettingsCard } from "./KioskBranchSettingsCard";
import { NotificationEmailSettingsCard } from "./NotificationEmailSettingsCard";

type OperationalSection = "guide" | "payments" | "kiosk" | "email" | "history";

export default function OrganizationSettingsPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [organization, setOrganization] = useState<OrganizationSummary | null>(null);
  const [branches, setBranches] = useState<BranchSummary[]>([]);
  const [selectedBranchId, setSelectedBranchId] = useState("");
  const [organizationForm, setOrganizationForm] = useState<OrganizationUpdatePayload | null>(null);
  const [branchForm, setBranchForm] = useState<BranchUpdatePayload | null>(null);
  const [newBranchForm, setNewBranchForm] = useState<CreateBranchPayload>(emptyBranchForm);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<FieldErrorMap>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [showOrganizationDrawer, setShowOrganizationDrawer] = useState(false);
  const [showNewBranchDrawer, setShowNewBranchDrawer] = useState(false);
  const [showEditBranchDrawer, setShowEditBranchDrawer] = useState(false);
  const [branchPanelHeight, setBranchPanelHeight] = useState<number | null>(null);
  const [paymentConfiguration, setPaymentConfiguration] = useState<PaymentConfiguration | null>(null);
  const [mercadoPagoTerminals, setMercadoPagoTerminals] = useState<MercadoPagoTerminal[]>([]);
  const [operationalSection, setOperationalSection] = useState<OperationalSection>("guide");
  const settingsColumnRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    let active = true;

    async function loadInitial() {
      const storedSession = getStoredSession();
      if (!storedSession) {
        setLoading(false);
        return;
      }

      setSession(storedSession);
      setLoading(true);
      setError(null);
      try {
        const canReadPaymentSettings = storedSession.user.permissions.includes("payment.settings.read")
          || storedSession.user.permissions.includes("payment.settings.manage")
          || storedSession.user.permissions.includes("platform.admin");
        const [currentOrganization, availableBranches, currentPaymentConfiguration] = await Promise.all([
          getCurrentOrganization(storedSession.accessToken),
          getBranches(storedSession.accessToken),
          canReadPaymentSettings ? getPaymentConfiguration(storedSession.accessToken) : Promise.resolve(null),
        ]);
        if (!active) return;

        setOrganization(currentOrganization);
        setBranches(availableBranches);
        let resolvedPaymentConfiguration = currentPaymentConfiguration;
        const paymentReturn = new URLSearchParams(window.location.search).get("payment");
        if (canReadPaymentSettings && paymentReturn === "stripe-return") {
          resolvedPaymentConfiguration = await refreshStripeConnection(storedSession.accessToken);
          window.history.replaceState({}, "", window.location.pathname);
        }
        if (canReadPaymentSettings && paymentReturn === "stripe-refresh") {
          const link = await beginStripeOnboarding(storedSession.accessToken);
          window.location.assign(link.url);
          return;
        }
        setPaymentConfiguration(resolvedPaymentConfiguration);
        setOrganizationForm(toOrganizationForm(currentOrganization));
        const branch = availableBranches.find((item) => item.id === storedSession.user.branchId) ?? availableBranches[0];
        if (branch) {
          setSelectedBranchId(branch.id);
          setBranchForm(toBranchForm(branch));
        }
      } catch (exception) {
        if (active) {
          setError(exception instanceof Error ? exception.message : "No fue posible cargar configuracion.");
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    void loadInitial();
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    const synchronizeSection = () => setOperationalSection(operationalSectionFromHash(window.location.hash));
    synchronizeSection();
    window.addEventListener("hashchange", synchronizeSection);
    return () => window.removeEventListener("hashchange", synchronizeSection);
  }, []);

  useLayoutEffect(() => {
    const settingsColumn = settingsColumnRef.current;
    if (!settingsColumn) return;

    const desktopQuery = window.matchMedia("(min-width: 1280px)");
    const updateHeight = () => {
      setBranchPanelHeight(
        desktopQuery.matches ? Math.ceil(settingsColumn.getBoundingClientRect().height) : null,
      );
    };
    const resizeObserver = new ResizeObserver(updateHeight);

    resizeObserver.observe(settingsColumn);
    desktopQuery.addEventListener("change", updateHeight);
    updateHeight();

    return () => {
      resizeObserver.disconnect();
      desktopQuery.removeEventListener("change", updateHeight);
    };
  }, [organization]);

  const activeBranch = branches.find((branch) => branch.id === session?.user.branchId) ?? null;
  const persistedOrganizationForm = organization ? toOrganizationForm(organization) : organizationForm;
  const canManageOrganization = Boolean(session?.user.permissions.includes("organization.update") || session?.user.permissions.includes("platform.admin"));
  const canManageBranches = Boolean(session?.user.permissions.includes("branch.update") || session?.user.permissions.includes("platform.admin"));

  function selectOperationalSection(section: OperationalSection) {
    setOperationalSection(section);
    const hash = operationalSectionHash(section);
    window.history.replaceState({}, "", `${window.location.pathname}${window.location.search}${hash}`);
  }

  async function submitOrganization(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || !organizationForm) return;
    const errors = validateOrganizationForm(organizationForm);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const response = await updateOrganization(session.accessToken, normalizeOrganizationForm(organizationForm));
      setOrganization(response);
      setOrganizationForm(toOrganizationForm(response));
      setShowOrganizationDrawer(false);
      setMessage("Datos del gimnasio actualizados.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible actualizar el gimnasio.");
    } finally {
      setSaving(false);
    }
  }

  async function submitBranch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || !branchForm || !selectedBranchId) return;
    const errors = validateBranchForm(branchForm, "branch");
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const response = await updateBranch(session.accessToken, selectedBranchId, normalizeBranchForm(branchForm));
      setBranches((current) => current.map((branch) => (branch.id === response.id ? response : branch)));
      setBranchForm(toBranchForm(response));
      setShowEditBranchDrawer(false);
      setMessage("Sucursal actualizada.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible actualizar la sucursal.");
    } finally {
      setSaving(false);
    }
  }

  async function submitNewBranch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) return;
    const errors = validateBranchForm(newBranchForm, "newBranch");
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const response = await createBranch(session.accessToken, normalizeBranchForm(newBranchForm));
      setBranches((current) => [...current, response].sort((left, right) => left.name.localeCompare(right.name)));
      setSelectedBranchId(response.id);
      setBranchForm(toBranchForm(response));
      setNewBranchForm(emptyBranchForm);
      setShowNewBranchDrawer(false);
      setMessage("Sucursal creada. Ya puedes asignarla a usuarios o seleccionarla para operar.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear la sucursal.");
    } finally {
      setSaving(false);
    }
  }

  function selectEditableBranch(branchId: string) {
    setSelectedBranchId(branchId);
    const branch = branches.find((item) => item.id === branchId);
    setBranchForm(branch ? toBranchForm(branch) : null);
  }

  function openOrganizationDrawer() {
    if (organization) setOrganizationForm(toOrganizationForm(organization));
    setFieldErrors({});
    setShowOrganizationDrawer(true);
  }

  function closeOrganizationDrawer() {
    if (organization) setOrganizationForm(toOrganizationForm(organization));
    setFieldErrors({});
    setShowOrganizationDrawer(false);
  }

  function applyUploadedLogo(logoUrl: string) {
    setOrganization((current) => current ? { ...current, logoUrl } : current);
    const currentSession = getStoredSession() ?? session;
    if (currentSession) {
      const nextSession = {
        ...currentSession,
        user: {
          ...currentSession.user,
          logoUrl,
        },
      };
      storeCurrentUser(nextSession.user);
      setSession(nextSession);
    }
    setMessage("Logo del gimnasio actualizado.");
  }

  function openNewBranchDrawer() {
    setNewBranchForm(emptyBranchForm);
    setFieldErrors({});
    setShowNewBranchDrawer(true);
  }

  function closeNewBranchDrawer() {
    setNewBranchForm(emptyBranchForm);
    setFieldErrors({});
    setShowNewBranchDrawer(false);
  }

  function openEditBranch(branchId: string) {
    selectEditableBranch(branchId);
    setFieldErrors({});
    setShowEditBranchDrawer(true);
  }

  function closeEditBranchDrawer() {
    const branch = branches.find((item) => item.id === selectedBranchId);
    setBranchForm(branch ? toBranchForm(branch) : null);
    setFieldErrors({});
    setShowEditBranchDrawer(false);
  }

  async function savePaymentConfiguration(next: PaymentConfiguration) {
    if (!session) return;
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const updated = await updatePaymentConfiguration(session.accessToken, {
        receptionManualTerminalEnabled: next.receptionManualTerminalEnabled,
        receptionMercadoPagoEnabled: next.receptionMercadoPagoEnabled,
        mercadoPagoTerminalId: next.mercadoPagoTerminalId,
        stripeSelfCheckoutEnabled: next.stripeSelfCheckoutEnabled,
        stripeRecurringEnabled: next.stripeRecurringEnabled,
      });
      setPaymentConfiguration(updated);
      setMessage("Configuracion de cobros actualizada.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible actualizar los cobros.");
    } finally {
      setSaving(false);
    }
  }

  async function connectStripe() {
    if (!session) return;
    setSaving(true);
    setError(null);
    try {
      const link = await beginStripeOnboarding(session.accessToken);
      window.location.assign(link.url);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible iniciar la vinculacion de Stripe.");
      setSaving(false);
    }
  }

  async function refreshStripe() {
    if (!session) return;
    setSaving(true);
    setError(null);
    try {
      setPaymentConfiguration(await refreshStripeConnection(session.accessToken));
      setMessage("Estado de Stripe actualizado.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible verificar Stripe.");
    } finally {
      setSaving(false);
    }
  }

  async function connectMercadoPago() {
    if (!session) return;
    setSaving(true);
    setError(null);
    try {
      const link = await beginMercadoPagoOnboarding(session.accessToken);
      window.location.assign(link.url);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible iniciar la vinculacion de Mercado Pago.");
      setSaving(false);
    }
  }

  async function loadMercadoPagoTerminals() {
    if (!session) return;
    setSaving(true);
    setError(null);
    try {
      const terminals = await getMercadoPagoTerminals(session.accessToken);
      setMercadoPagoTerminals(terminals);
      setMessage(terminals.length > 0 ? "Terminales Point actualizadas." : "Mercado Pago no devolvio terminales Point.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible consultar las terminales Point.");
    } finally {
      setSaving(false);
    }
  }

  async function chooseMercadoPagoTerminal(terminalId: string) {
    if (!session || !terminalId) return;
    setSaving(true);
    setError(null);
    try {
      setPaymentConfiguration(await selectMercadoPagoTerminal(session.accessToken, terminalId));
      setMessage("Terminal Point seleccionada y configurada en modo PDV.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible configurar la terminal Point.");
    } finally {
      setSaving(false);
    }
  }

  if (!session) return <AuthRequired />;
  if (loading) return <LoadingScreen text="Cargando datos y sucursales..." />;

  if (!organizationForm || !persistedOrganizationForm) {
    return (
      <AppShell
        branchName={activeBranch?.name}
        eyebrow="Administracion"
        mode={activeBranch ? "operation" : "setup"}
        organizationName={session.user.organizationName}
        session={session}
        subtitle="Administra la cadena o gimnasio, crea varias sucursales y prepara el acceso del equipo."
        title="Configuración del gimnasio"
      >
        <p className="form-message error">{error ?? "No fue posible cargar los datos del gimnasio."}</p>
      </AppShell>
    );
  }

  return (
    <AppShell
      branchName={activeBranch?.name}
      eyebrow="Administracion"
      mode={activeBranch ? "operation" : "setup"}
      organizationName={organization?.tradeName}
      session={session}
      subtitle="Marca, sucursales, medios de cobro, kiosco y canales de comunicación en un solo lugar."
      title="Configuración del gimnasio"
    >
      <SettingsSummary
        activeBranchName={activeBranch?.name}
        branchCount={branches.length}
        legalName={organization?.legalName}
        tradeName={organization?.tradeName}
      />

      {error ? <p className="form-message error">{error}</p> : null}
      {message ? <p className="form-message success">{message}</p> : null}

      {canManageOrganization || canManageBranches ? (
        <section className="grid items-start gap-5 xl:grid-cols-[minmax(0,0.95fr)_minmax(0,1.05fr)]">
          {canManageOrganization ? (
            <div className="grid min-w-0 gap-5" ref={settingsColumnRef}>
              <OrganizationOverviewCard form={persistedOrganizationForm} onEdit={openOrganizationDrawer} />
              <TeamAccessCard />
            </div>
          ) : null}
          {canManageBranches ? (
            <div
              className="min-h-0 min-w-0 overflow-hidden"
              style={branchPanelHeight ? { height: `${branchPanelHeight}px` } : undefined}
            >
              <BranchesOverviewCard branches={branches} onEditBranch={openEditBranch} onNewBranch={openNewBranchDrawer} />
            </div>
          ) : null}
        </section>
      ) : null}

      <OperationalSettingsNavigation
        activeSection={operationalSection}
        showEmail={session.user.permissions.includes("notification.settings.manage") || session.user.permissions.includes("platform.admin")}
        showKiosk={session.user.permissions.includes("selfservice.settings.manage") || session.user.permissions.includes("platform.admin")}
        showPayments={Boolean(paymentConfiguration)}
        onSelect={selectOperationalSection}
      />

      {operationalSection === "guide" ? (
        <OperationalSettingsGuide
          branchCount={branches.length}
          configuration={paymentConfiguration}
          showEmail={session.user.permissions.includes("notification.settings.manage") || session.user.permissions.includes("platform.admin")}
          showKiosk={session.user.permissions.includes("selfservice.settings.manage") || session.user.permissions.includes("platform.admin")}
          showPayments={Boolean(paymentConfiguration)}
          onSelect={selectOperationalSection}
        />
      ) : null}

      {operationalSection === "payments" && paymentConfiguration ? (
        <PaymentConfigurationCard
          key={`${paymentConfiguration.receptionManualTerminalEnabled}-${paymentConfiguration.receptionMercadoPagoEnabled}-${paymentConfiguration.mercadoPagoConnected}-${paymentConfiguration.mercadoPagoTerminalId ?? "none"}-${paymentConfiguration.stripeSelfCheckoutEnabled}-${paymentConfiguration.stripeRecurringEnabled}-${paymentConfiguration.stripeConnected}-${paymentConfiguration.stripeAccountId ?? "none"}`}
          canManage={session.user.permissions.includes("payment.settings.manage") || session.user.permissions.includes("platform.admin")}
          configuration={paymentConfiguration}
          mercadoPagoTerminals={mercadoPagoTerminals}
          saving={saving}
          onChooseMercadoPagoTerminal={chooseMercadoPagoTerminal}
          onConnectMercadoPago={connectMercadoPago}
          onConnectStripe={connectStripe}
          onLoadMercadoPagoTerminals={loadMercadoPagoTerminals}
          onRefreshStripe={refreshStripe}
          onSave={savePaymentConfiguration}
        />
      ) : null}

      {operationalSection === "kiosk" && (session.user.permissions.includes("selfservice.settings.manage") || session.user.permissions.includes("platform.admin")) ? (
        <KioskBranchSettingsCard
          accessToken={session.accessToken}
          activeBranchId={activeBranch?.id}
        />
      ) : null}

      {operationalSection === "email" && (session.user.permissions.includes("notification.settings.manage") || session.user.permissions.includes("platform.admin")) ? (
        <NotificationEmailSettingsCard accessToken={session.accessToken} />
      ) : null}

      {operationalSection === "history" && paymentConfiguration ? <PaymentOperationsHistoryCard accessToken={session.accessToken} /> : null}

      {canManageOrganization ? (
        <OrganizationDrawer
          accessToken={session.accessToken}
          errors={fieldErrors}
          form={organizationForm}
          open={showOrganizationDrawer}
          organizationId={organization?.id ?? session.user.organizationId}
          saving={saving}
          onChange={setOrganizationForm}
          onClose={closeOrganizationDrawer}
          onLogoChange={applyUploadedLogo}
          onSubmit={submitOrganization}
        />
      ) : null}
      {canManageBranches ? (
        <>
          <NewBranchDrawer errors={fieldErrors} form={newBranchForm} open={showNewBranchDrawer} saving={saving} onChange={setNewBranchForm} onClose={closeNewBranchDrawer} onSubmit={submitNewBranch} />
          <EditBranchDrawer branchId={selectedBranchId} branches={branches} errors={fieldErrors} form={branchForm} open={showEditBranchDrawer} saving={saving} onBranchSelect={selectEditableBranch} onChange={setBranchForm} onClose={closeEditBranchDrawer} onSubmit={submitBranch} />
        </>
      ) : null}
    </AppShell>
  );
}

function OperationalSettingsNavigation({
  activeSection,
  showEmail,
  showKiosk,
  showPayments,
  onSelect,
}: {
  activeSection: OperationalSection;
  showEmail: boolean;
  showKiosk: boolean;
  showPayments: boolean;
  onSelect: (section: OperationalSection) => void;
}) {
  if (!showEmail && !showKiosk && !showPayments) return null;
  return (
    <nav aria-label="Configuración operativa" className="app-card grid gap-4 p-5">
      <div>
        <p className="app-eyebrow">Configuración operativa</p>
        <h2 className="mt-1 text-xl font-semibold">¿Qué quieres configurar?</h2>
        <p className="mt-1 text-sm text-[var(--brand-muted)]">
          Elige una tarea. Mostraremos sólo ese bloque y los cambios se aplicarán al gimnasio activo.
        </p>
      </div>
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
        <SettingsSectionButton active={activeSection === "guide"} description="Orden recomendado y dependencias." label="Empezar aquí" onClick={() => onSelect("guide")} />
        {showPayments ? <SettingsSectionButton active={activeSection === "payments"} description="Terminal manual, Mercado Pago y Stripe." label="Medios de cobro" onClick={() => onSelect("payments")} /> : null}
        {showKiosk ? <SettingsSectionButton active={activeSection === "kiosk"} description="Funciones permitidas por sucursal." label="Kiosco web" onClick={() => onSelect("kiosk")} /> : null}
        {showEmail ? <SettingsSectionButton active={activeSection === "email"} description="Remitente y prueba del proveedor." label="Correo electrónico" onClick={() => onSelect("email")} /> : null}
        {showPayments ? <SettingsSectionButton active={activeSection === "history"} description="Sólo diagnóstico de Stripe." label="Historial técnico" onClick={() => onSelect("history")} /> : null}
      </div>
    </nav>
  );
}

function SettingsSectionButton({
  active,
  description,
  label,
  onClick,
}: {
  active: boolean;
  description: string;
  label: string;
  onClick: () => void;
}) {
  return (
    <button
      aria-pressed={active}
      className={`rounded-lg border p-4 text-left transition ${active ? "border-[var(--brand-primary)] bg-[#edf5ef]" : "border-[var(--brand-line)] bg-[var(--brand-surface)] hover:border-[var(--brand-primary)]"}`}
      onClick={onClick}
      type="button"
    >
      <strong className="block text-sm">{label}</strong>
      <span className="mt-1 block text-xs leading-5 text-[var(--brand-muted)]">{description}</span>
    </button>
  );
}

function OperationalSettingsGuide({
  branchCount,
  configuration,
  showEmail,
  showKiosk,
  showPayments,
  onSelect,
}: {
  branchCount: number;
  configuration: PaymentConfiguration | null;
  showEmail: boolean;
  showKiosk: boolean;
  showPayments: boolean;
  onSelect: (section: OperationalSection) => void;
}) {
  const receptionStatus = configuration?.receptionManualTerminalEnabled
    ? "La terminal independiente está habilitada."
    : "Aún no se eligió cómo registrar pagos con tarjeta en recepción.";
  const stripeStatus = configuration?.stripeConnected
    ? "Stripe está vinculado; revisa qué funciones deseas activar."
    : "Stripe es opcional y sólo se necesita para pagos y domiciliaciones del kiosco.";

  return (
    <section className="app-card grid gap-5 p-5" id="operational-guide">
      <div>
        <p className="app-eyebrow">Ruta recomendada</p>
        <h2 className="mt-1 text-xl font-semibold">Prepara la operación en este orden</h2>
        <p className="mt-1 text-sm text-[var(--brand-muted)]">
          No necesitas activar todo. La terminal manual puede funcionar sin Mercado Pago, Stripe ni correo.
        </p>
      </div>
      <div className="grid gap-4 lg:grid-cols-3">
        {showPayments ? (
          <SetupStep description={`${receptionStatus} ${stripeStatus}`} number="1" onClick={() => onSelect("payments")} title="Elige cómo cobrar" />
        ) : null}
        {showKiosk ? (
          <SetupStep
            description={`Activa por cada una de tus ${branchCount} sucursales sólo las acciones que verá el socio. No se registran computadoras.`}
            number="2"
            onClick={() => onSelect("kiosk")}
            title="Define el kiosco"
          />
        ) : null}
        {showEmail ? (
          <SetupStep
            description="Configúralo únicamente si quieres enviar correos. Las notificaciones internas funcionan sin proveedor SMTP."
            number="3"
            onClick={() => onSelect("email")}
            title="Conecta el correo"
          />
        ) : null}
      </div>
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-[var(--brand-line)] bg-[var(--brand-surface)] p-4">
        <div>
          <strong className="text-sm">Después crea las automatizaciones</strong>
          <p className="mt-1 text-xs text-[var(--brand-muted)]">Encontrarás recetas para avisos de membresías, pagos, reservas y domiciliaciones.</p>
        </div>
        <Link className="app-button" href="/automations">Ir a automatizaciones</Link>
      </div>
    </section>
  );
}

function SetupStep({
  description,
  number,
  onClick,
  title,
}: {
  description: string;
  number: string;
  onClick: () => void;
  title: string;
}) {
  return (
    <article className="rounded-lg border border-[var(--brand-line)] p-4">
      <span className="inline-grid h-8 w-8 place-items-center rounded-full bg-[var(--brand-primary)] text-sm font-bold text-white">{number}</span>
      <h3 className="mt-3 font-semibold">{title}</h3>
      <p className="mt-2 min-h-20 text-sm leading-6 text-[var(--brand-muted)]">{description}</p>
      <button aria-label={`Abrir configuración: ${title}`} className="app-button-secondary mt-4" onClick={onClick} type="button">Abrir configuración</button>
    </article>
  );
}

function operationalSectionFromHash(hash: string): OperationalSection {
  return ({
    "#payment-settings": "payments",
    "#kiosk-settings": "kiosk",
    "#email-settings": "email",
    "#payment-operations": "history",
  } as Record<string, OperationalSection>)[hash] ?? "guide";
}

function operationalSectionHash(section: OperationalSection) {
  return ({
    guide: "#operational-guide",
    payments: "#payment-settings",
    kiosk: "#kiosk-settings",
    email: "#email-settings",
    history: "#payment-operations",
  } as Record<OperationalSection, string>)[section];
}

function PaymentConfigurationCard({
  canManage,
  configuration,
  mercadoPagoTerminals,
  saving,
  onChooseMercadoPagoTerminal,
  onConnectMercadoPago,
  onConnectStripe,
  onLoadMercadoPagoTerminals,
  onRefreshStripe,
  onSave,
}: {
  canManage: boolean;
  configuration: PaymentConfiguration;
  mercadoPagoTerminals: MercadoPagoTerminal[];
  saving: boolean;
  onChooseMercadoPagoTerminal: (terminalId: string) => Promise<void>;
  onConnectMercadoPago: () => Promise<void>;
  onConnectStripe: () => Promise<void>;
  onLoadMercadoPagoTerminals: () => Promise<void>;
  onRefreshStripe: () => Promise<void>;
  onSave: (configuration: PaymentConfiguration) => Promise<void>;
}) {
  const [draft, setDraft] = useState(configuration);

  return (
    <section className="app-card grid scroll-mt-6 gap-5 p-5" id="payment-settings">
      <div>
        <p className="app-eyebrow">Cobros</p>
        <h2 className="text-xl font-semibold">Recepción y pagos del kiosco</h2>
        <p className="mt-2 text-sm text-[var(--brand-muted)]">
          El kiosco funciona en cualquier navegador y no se registra por computadora. Aquí sólo eliges cómo cobra recepción y si Stripe está disponible para el autoservicio.
        </p>
      </div>
      <label className="flex items-start gap-3 rounded-md border border-[var(--brand-line)] p-4">
        <input
          checked={draft.receptionManualTerminalEnabled}
          disabled={!canManage}
          onChange={(event) => setDraft({ ...draft, receptionManualTerminalEnabled: event.target.checked })}
          type="checkbox"
        />
        <span><strong>Terminal independiente en recepción</strong><small className="mt-1 block">Recepción captura el monto en su terminal física y después valida el pago manualmente en GymOS.</small></span>
      </label>
      <label className="flex items-start gap-3 rounded-md border border-[var(--brand-line)] p-4">
        <input
          checked={draft.receptionMercadoPagoEnabled}
          disabled={!canManage || !draft.mercadoPagoConnected}
          onChange={(event) => setDraft({ ...draft, receptionMercadoPagoEnabled: event.target.checked })}
          type="checkbox"
        />
        <span><strong>Mercado Pago Point integrado</strong><small className="mt-1 block">{draft.mercadoPagoConnected ? `Cuenta vinculada. Terminal: ${draft.mercadoPagoTerminalId ?? "por seleccionar"}.` : "Pendiente de vincular la cuenta de Mercado Pago mediante OAuth."}</small></span>
      </label>
      {canManage ? (
        <div className="flex flex-wrap items-center gap-3">
          <button className="app-button-secondary" disabled={saving} onClick={() => void onConnectMercadoPago()} type="button">
            {draft.mercadoPagoConnected ? "Volver a vincular Mercado Pago" : "Vincular Mercado Pago"}
          </button>
          {draft.mercadoPagoConnected ? (
            <button className="app-button-secondary" disabled={saving} onClick={() => void onLoadMercadoPagoTerminals()} type="button">
              Consultar terminales Point
            </button>
          ) : null}
          {mercadoPagoTerminals.length > 0 ? (
            <select
              className="app-input min-w-64"
              disabled={saving}
              onChange={(event) => void onChooseMercadoPagoTerminal(event.target.value)}
              value={draft.mercadoPagoTerminalId ?? ""}
            >
              <option value="">Selecciona una terminal</option>
              {mercadoPagoTerminals.map((terminal) => (
                <option key={terminal.id} value={terminal.id}>
                  {terminal.name ?? terminal.id}{terminal.status ? ` (${terminal.status})` : ""}
                </option>
              ))}
            </select>
          ) : null}
        </div>
      ) : null}
      <label className="flex items-start gap-3 rounded-md border border-[var(--brand-line)] p-4">
        <input
          checked={draft.stripeSelfCheckoutEnabled}
          disabled={!canManage || !draft.stripeConnected}
          onChange={(event) => setDraft({ ...draft, stripeSelfCheckoutEnabled: event.target.checked, stripeRecurringEnabled: event.target.checked ? draft.stripeRecurringEnabled : false })}
          type="checkbox"
        />
        <span><strong>Stripe para self checkout</strong><small className="mt-1 block">{draft.stripeConnected ? "Cuenta vinculada para Checkout alojado." : "Pendiente de vincular Stripe; GymOS no almacenará números de tarjeta ni CVV."}</small></span>
      </label>
      {canManage ? (
        <div className="flex flex-wrap gap-3">
          <button className="app-button-secondary" disabled={saving} onClick={() => void onConnectStripe()} type="button">
            {draft.stripeAccountId ? "Continuar configuración de Stripe" : "Vincular Stripe"}
          </button>
          {draft.stripeAccountId ? (
            <button className="app-button-secondary" disabled={saving} onClick={() => void onRefreshStripe()} type="button">
              Verificar conexión de Stripe
            </button>
          ) : null}
        </div>
      ) : null}
      <label className="flex items-start gap-3 rounded-md border border-[var(--brand-line)] p-4">
        <input
          checked={draft.stripeRecurringEnabled}
          disabled={!canManage || !draft.stripeConnected || !draft.stripeSelfCheckoutEnabled}
          onChange={(event) => setDraft({ ...draft, stripeRecurringEnabled: event.target.checked })}
          type="checkbox"
        />
        <span><strong>Domiciliación con Stripe</strong><small className="mt-1 block">El socio tokeniza su tarjeta y acepta los términos en el kiosco. GymOS crea una renovación por vigencia y sólo la extiende después de conciliar el PaymentIntent de Stripe.</small></span>
      </label>
      {canManage ? <button className="app-button w-fit" disabled={saving} onClick={() => void onSave(draft)} type="button">{saving ? "Guardando..." : "Guardar configuración de cobro"}</button> : null}
    </section>
  );
}

function AuthRequired() {
  return (
    <main className="grid min-h-screen place-items-center bg-[var(--brand-surface)] px-6 text-[#111815]">
      <section className="w-full max-w-md rounded-lg border border-[var(--brand-line)] bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-semibold">Necesitas iniciar sesion</h1>
        <Link className="mt-5 inline-flex h-11 items-center justify-center rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white transition hover:bg-[#243d30]" href="/login">
          Iniciar sesion
        </Link>
      </section>
    </main>
  );
}
