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

      <section className="grid items-start gap-5 xl:grid-cols-[minmax(0,0.95fr)_minmax(0,1.05fr)]">
        <div className="grid min-w-0 gap-5" ref={settingsColumnRef}>
          <OrganizationOverviewCard form={persistedOrganizationForm} onEdit={openOrganizationDrawer} />
          <TeamAccessCard />
        </div>
        <div
          className="min-h-0 min-w-0 overflow-hidden"
          style={branchPanelHeight ? { height: `${branchPanelHeight}px` } : undefined}
        >
          <BranchesOverviewCard branches={branches} onEditBranch={openEditBranch} onNewBranch={openNewBranchDrawer} />
        </div>
      </section>

      <OperationalSettingsNavigation
        showEmail={session.user.permissions.includes("notification.settings.manage") || session.user.permissions.includes("platform.admin")}
        showKiosk={session.user.permissions.includes("selfservice.settings.manage") || session.user.permissions.includes("platform.admin")}
        showPayments={Boolean(paymentConfiguration)}
      />

      {paymentConfiguration ? (
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

      {session.user.permissions.includes("selfservice.settings.manage") || session.user.permissions.includes("platform.admin") ? (
        <KioskBranchSettingsCard accessToken={session.accessToken} />
      ) : null}

      {session.user.permissions.includes("notification.settings.manage") || session.user.permissions.includes("platform.admin") ? (
        <NotificationEmailSettingsCard accessToken={session.accessToken} />
      ) : null}

      {paymentConfiguration ? <PaymentOperationsHistoryCard accessToken={session.accessToken} /> : null}

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
      <NewBranchDrawer errors={fieldErrors} form={newBranchForm} open={showNewBranchDrawer} saving={saving} onChange={setNewBranchForm} onClose={closeNewBranchDrawer} onSubmit={submitNewBranch} />
      <EditBranchDrawer branchId={selectedBranchId} branches={branches} errors={fieldErrors} form={branchForm} open={showEditBranchDrawer} saving={saving} onBranchSelect={selectEditableBranch} onChange={setBranchForm} onClose={closeEditBranchDrawer} onSubmit={submitBranch} />
    </AppShell>
  );
}

function OperationalSettingsNavigation({
  showEmail,
  showKiosk,
  showPayments,
}: {
  showEmail: boolean;
  showKiosk: boolean;
  showPayments: boolean;
}) {
  if (!showEmail && !showKiosk && !showPayments) return null;
  return (
    <nav aria-label="Configuración operativa" className="app-card grid gap-4 p-5">
      <div>
        <p className="app-eyebrow">Configuración operativa</p>
        <h2 className="mt-1 text-xl font-semibold">Cobros, kiosco y comunicaciones</h2>
        <p className="mt-1 text-sm text-[var(--brand-muted)]">
          Usa estos accesos para llegar directamente a cada bloque. Los cambios se aplican sólo al gimnasio activo.
        </p>
      </div>
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {showPayments ? <SettingsAnchor description="Terminal manual, Mercado Pago y Stripe." href="#payment-settings" label="Medios de cobro" /> : null}
        {showKiosk ? <SettingsAnchor description="Funciones permitidas por sucursal." href="#kiosk-settings" label="Kiosco web" /> : null}
        {showEmail ? <SettingsAnchor description="Remitente y prueba del proveedor." href="#email-settings" label="Correo electrónico" /> : null}
        {showPayments ? <SettingsAnchor description="Cargos, consentimientos y webhooks." href="#payment-operations" label="Historial de Stripe" /> : null}
      </div>
    </nav>
  );
}

function SettingsAnchor({ description, href, label }: { description: string; href: string; label: string }) {
  return (
    <a className="rounded-lg border border-[var(--brand-line)] bg-[var(--brand-surface)] p-4 transition hover:border-[var(--brand-primary)]" href={href}>
      <strong className="block text-sm">{label}</strong>
      <span className="mt-1 block text-xs leading-5 text-[var(--brand-muted)]">{description}</span>
    </a>
  );
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
