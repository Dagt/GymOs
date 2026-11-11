"use client";

import Link from "next/link";
import { sameUserContext } from "@/lib/api/session";
import { FormEvent, useEffect, useLayoutEffect, useRef, useState } from "react";
import { AppShell, LoadingScreen } from "@/components/AppShell";
import { AppNotice } from "@/components/AppNotice";
import { type FieldErrorMap } from "@/components/FormFields";
import {
  beginMercadoPagoOnboarding,
  beginStripeOnboarding,
  createBranch,
  getBranches,
  getCurrentOrganization,
  getCurrentUser,
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
  type CurrentUser,
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
  TeamAccessCard,
} from "./OrganizationSettingsSections";
import { PaymentConfigurationCard } from "./PaymentConfigurationCard";
import { PaymentOperationsHistoryCard } from "./PaymentOperationsHistoryCard";
import { KioskBranchSettingsCard } from "./KioskBranchSettingsCard";
import { NotificationEmailSettingsCard } from "./NotificationEmailSettingsCard";
import { ClientLifecycleSettingsCard } from "./ClientLifecycleSettingsCard";

type OperationalSection = "clients" | "payments" | "kiosk" | "email" | "history";

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
  const [operationalSection, setOperationalSection] = useState<OperationalSection>("payments");
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
        const canManageOrganizationProfile = storedSession.user.permissions.includes("organization.update")
          || storedSession.user.permissions.includes("platform.admin");
        const canReadPaymentSettings = storedSession.user.permissions.includes("payment.settings.read")
          || storedSession.user.permissions.includes("payment.settings.manage")
          || storedSession.user.permissions.includes("platform.admin");
        const canManageStripeProvider = storedSession.user.permissions.includes("payment.provider.stripe.manage")
          || storedSession.user.permissions.includes("platform.admin");
        const [currentOrganization, availableBranches, currentPaymentConfiguration] = await Promise.all([
          canManageOrganizationProfile
            ? getCurrentOrganization(storedSession.accessToken)
            : Promise.resolve(null),
          getBranches(storedSession.accessToken),
          canReadPaymentSettings ? getPaymentConfiguration(storedSession.accessToken) : Promise.resolve(null),
        ]);
        if (!active) return;

        setOrganization(currentOrganization);
        setBranches(availableBranches);
        let resolvedPaymentConfiguration = currentPaymentConfiguration;
        const paymentReturn = new URLSearchParams(window.location.search).get("payment");
        if (canManageStripeProvider && paymentReturn === "stripe-return") {
          resolvedPaymentConfiguration = await refreshStripeConnection(storedSession.accessToken);
          window.history.replaceState({}, "", window.location.pathname);
        }
        if (canManageStripeProvider && paymentReturn === "stripe-refresh") {
          const link = await beginStripeOnboarding(storedSession.accessToken);
          window.location.assign(link.url);
          return;
        }
        setPaymentConfiguration(resolvedPaymentConfiguration);
        if (currentOrganization) {
          setOrganizationForm(toOrganizationForm(currentOrganization));
        }
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
  const canManageKioskSettings = Boolean(session?.user.permissions.includes("selfservice.settings.manage") || session?.user.permissions.includes("platform.admin"));
  const canManageEmailSettings = Boolean(session?.user.permissions.includes("notification.settings.manage") || session?.user.permissions.includes("platform.admin"));
  const canManageStripeAccount = Boolean(session?.user.permissions.includes("payment.provider.stripe.manage") || session?.user.permissions.includes("platform.admin"));
  const availableOperationalSections: OperationalSection[] = [
    ...(canManageOrganization ? ["clients"] as OperationalSection[] : []),
    ...(paymentConfiguration ? ["payments", "history"] as OperationalSection[] : []),
    ...(canManageKioskSettings ? ["kiosk"] as OperationalSection[] : []),
    ...(canManageEmailSettings ? ["email"] as OperationalSection[] : []),
  ];
  const activeOperationalSection = availableOperationalSections.includes(operationalSection)
    ? operationalSection
    : availableOperationalSections[0] ?? operationalSection;

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
      const expected = getStoredSession();
      if (!expected) return;
      const response = await updateOrganization(expected.accessToken, normalizeOrganizationForm(organizationForm));
      let target = expected;
      let confirmedUser: CurrentUser = {
        ...expected.user,
        organizationName: response.tradeName,
        brandName: response.brandName,
        logoUrl: response.logoUrl,
        primaryColor: response.primaryColor,
        accentColor: response.accentColor,
        surfaceColor: response.surfaceColor,
      };
      const active = getStoredSession();
      if (active !== expected) {
        if (!active || !sameUserContext(expected.user, active.user)) return;
        // The request may have renewed the token. Read the authoritative profile instead of applying an old response.
        target = active;
        confirmedUser = { ...confirmedUser, ...await getCurrentUser(active.accessToken) };
      }
      if (!storeCurrentUser(confirmedUser, target)) return;
      setSession(getStoredSession());
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
    const currentSession = getStoredSession();
    if (!session || !currentSession || !sameUserContext(session.user, currentSession.user)) return;
    setOrganization((current) => current ? { ...current, logoUrl } : current);
    if (currentSession) {
      const nextSession = {
        ...currentSession,
        user: {
          ...currentSession.user,
          logoUrl,
        },
      };
      storeCurrentUser(nextSession.user, currentSession);
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
    if (!session) throw new Error("La sesión ya no está disponible.");
    const updated = await updatePaymentConfiguration(session.accessToken, {
      receptionManualTerminalEnabled: next.receptionManualTerminalEnabled,
      receptionMercadoPagoEnabled: next.receptionMercadoPagoEnabled,
      mercadoPagoTerminalId: next.mercadoPagoTerminalId,
      stripeSelfCheckoutEnabled: next.stripeSelfCheckoutEnabled,
      stripeRecurringEnabled: next.stripeRecurringEnabled,
    });
    setPaymentConfiguration(updated);
    return updated;
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

  if (canManageOrganization && (!organizationForm || !persistedOrganizationForm)) {
    return (
      <AppShell
        branchName={activeBranch?.name}
        eyebrow="Administracion"
        mode={activeBranch ? "operation" : "setup"}
        organizationName={session.user.organizationName}
        pageClassName="settings-console"
        session={session}
        title="Configuración del gimnasio"
      >
        <AppNotice message={error ?? "No fue posible cargar los datos del gimnasio."} tone="error" />
      </AppShell>
    );
  }

  return (
    <AppShell
      branchName={activeBranch?.name}
      eyebrow="Administracion"
      mode={activeBranch ? "operation" : "setup"}
      organizationName={organization?.tradeName ?? session.user.organizationName}
      pageClassName="settings-console"
      session={session}
      subtitle="Organiza identidad, sucursales y servicios conectados desde un solo centro de control."
      title="Configuración del gimnasio"
    >
      {error ? <AppNotice message={error} tone="error" /> : null}
      {message ? <AppNotice message={message} tone="success" /> : null}

      {canManageOrganization || canManageBranches ? (
        <section className="settings-overview-grid">
          {canManageOrganization && persistedOrganizationForm ? (
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
        activeSection={activeOperationalSection}
        showClients={canManageOrganization}
        showEmail={canManageEmailSettings}
        showKiosk={canManageKioskSettings}
        showPayments={Boolean(paymentConfiguration)}
        onSelect={selectOperationalSection}
      />

      {activeOperationalSection === "clients" && canManageOrganization ? (
        <ClientLifecycleSettingsCard key={activeBranch?.id ?? "no-branch"} accessToken={session.accessToken} branchName={activeBranch?.name} />
      ) : null}

      {activeOperationalSection === "payments" && paymentConfiguration ? (
        <PaymentConfigurationCard
          branchName={activeBranch?.name}
          key={`${activeBranch?.id ?? "no-branch"}-${paymentConfiguration.mercadoPagoConnected}-${paymentConfiguration.mercadoPagoAccountId ?? "none"}-${paymentConfiguration.mercadoPagoTerminalId ?? "none"}-${paymentConfiguration.stripeConnected}-${paymentConfiguration.stripeAccountId ?? "none"}`}
          canManage={session.user.permissions.includes("payment.settings.manage") || session.user.permissions.includes("platform.admin")}
          canManageStripeAccount={canManageStripeAccount}
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

      {activeOperationalSection === "kiosk" && canManageKioskSettings ? (
        <KioskBranchSettingsCard
          accessToken={session.accessToken}
          activeBranchId={activeBranch?.id}
        />
      ) : null}

      {activeOperationalSection === "email" && canManageEmailSettings ? (
        <NotificationEmailSettingsCard accessToken={session.accessToken} />
      ) : null}

      {activeOperationalSection === "history" && paymentConfiguration ? <PaymentOperationsHistoryCard accessToken={session.accessToken} /> : null}

      {canManageOrganization && organizationForm ? (
        <OrganizationDrawer
          accessToken={session.accessToken}
          errors={fieldErrors}
          form={organizationForm}
          open={showOrganizationDrawer}
		  organizationId={organization?.id ?? session.user.organizationId ?? ""}
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
  showClients,
  showEmail,
  showKiosk,
  showPayments,
  onSelect,
}: {
  activeSection: OperationalSection;
  showClients: boolean;
  showEmail: boolean;
  showKiosk: boolean;
  showPayments: boolean;
  onSelect: (section: OperationalSection) => void;
}) {
  if (!showClients && !showEmail && !showKiosk && !showPayments) return null;
  return (
    <nav aria-label="Configuración operativa" className="settings-section-nav">
      <div>
        <p>Servicios conectados</p>
        <span>Selecciona el bloque que deseas configurar.</span>
      </div>
      <div className="settings-section-tabs">
        {showClients ? <SettingsSectionButton active={activeSection === "clients"} label="Inscripciones" onClick={() => onSelect("clients")} /> : null}
        {showPayments ? <SettingsSectionButton active={activeSection === "payments"} label="Medios de cobro" onClick={() => onSelect("payments")} /> : null}
        {showKiosk ? <SettingsSectionButton active={activeSection === "kiosk"} label="Kiosco web" onClick={() => onSelect("kiosk")} /> : null}
        {showEmail ? <SettingsSectionButton active={activeSection === "email"} label="Correo electrónico" onClick={() => onSelect("email")} /> : null}
        {showPayments ? <SettingsSectionButton active={activeSection === "history"} label="Historial técnico" onClick={() => onSelect("history")} /> : null}
      </div>
    </nav>
  );
}

function SettingsSectionButton({
  active,
  label,
  onClick,
}: {
  active: boolean;
  label: string;
  onClick: () => void;
}) {
  return (
    <button
      aria-pressed={active}
      className={`settings-section-tab ${active ? "active" : ""}`}
      onClick={onClick}
      type="button"
    >
      {label}
    </button>
  );
}

function operationalSectionFromHash(hash: string): OperationalSection {
  return ({
    "#client-lifecycle-settings": "clients",
    "#payment-settings": "payments",
    "#kiosk-settings": "kiosk",
    "#email-settings": "email",
    "#payment-operations": "history",
  } as Record<string, OperationalSection>)[hash] ?? "payments";
}

function operationalSectionHash(section: OperationalSection) {
  return ({
    clients: "#client-lifecycle-settings",
    payments: "#payment-settings",
    kiosk: "#kiosk-settings",
    email: "#email-settings",
    history: "#payment-operations",
  } as Record<OperationalSection, string>)[section];
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
