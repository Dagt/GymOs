"use client";

import { useRef, useState } from "react";
import { AppNotice } from "@/components/AppNotice";
import { ToggleSwitch } from "@/components/ToggleSwitch";
import type { MercadoPagoTerminal, PaymentConfiguration } from "@/lib/api";

export function PaymentConfigurationCard({
  canManage,
  branchName,
  canManageStripeAccount,
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
  branchName?: string;
  canManageStripeAccount: boolean;
  configuration: PaymentConfiguration;
  mercadoPagoTerminals: MercadoPagoTerminal[];
  saving: boolean;
  onChooseMercadoPagoTerminal: (terminalId: string) => Promise<void>;
  onConnectMercadoPago: () => Promise<void>;
  onConnectStripe: () => Promise<void>;
  onLoadMercadoPagoTerminals: () => Promise<void>;
  onRefreshStripe: () => Promise<void>;
  onSave: (configuration: PaymentConfiguration) => Promise<PaymentConfiguration>;
}) {
  const [draft, setDraft] = useState(configuration);
  const [saveStatus, setSaveStatus] = useState<"saving" | "saved" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const draftRef = useRef(configuration);
  const saveStateRef = useRef({
    revision: 0,
    queuedRevision: 0,
    completedRevision: 0,
    running: false,
    confirmed: configuration,
  });

  function setManualTerminal(checked: boolean) {
    const current = draftRef.current;
    changeToggle({
      ...current,
      receptionManualTerminalEnabled: checked,
      receptionMercadoPagoEnabled: checked ? false : current.receptionMercadoPagoEnabled,
    });
  }

  function setMercadoPago(checked: boolean) {
    const current = draftRef.current;
    changeToggle({
      ...current,
      receptionMercadoPagoEnabled: checked,
      receptionManualTerminalEnabled: checked ? false : current.receptionManualTerminalEnabled,
    });
  }

  function setStripeCheckout(checked: boolean) {
    const current = draftRef.current;
    changeToggle({
      ...current,
      stripeSelfCheckoutEnabled: checked,
      stripeRecurringEnabled: checked ? current.stripeRecurringEnabled : false,
    });
  }

  function changeToggle(next: PaymentConfiguration) {
    const state = saveStateRef.current;
    state.revision += 1;
    state.queuedRevision = state.revision;
    draftRef.current = next;
    setDraft(next);
    setError(null);
    setSaveStatus("saving");
    void persistQueuedChanges();
  }

  async function persistQueuedChanges() {
    const state = saveStateRef.current;
    if (state.running) return;
    state.running = true;
    try {
      while (state.completedRevision < state.queuedRevision) {
        const requestedRevision = state.queuedRevision;
        const desired = draftRef.current;
        try {
          const updated = await onSave(desired);
          state.confirmed = updated;
          state.completedRevision = requestedRevision;
          if (state.revision === requestedRevision) {
            draftRef.current = updated;
            setDraft(updated);
            setSaveStatus("saved");
          }
        } catch (exception) {
          state.completedRevision = state.queuedRevision;
          state.revision = state.queuedRevision;
          draftRef.current = state.confirmed;
          setDraft(state.confirmed);
          setSaveStatus(null);
          setError(exception instanceof Error ? exception.message : "No fue posible guardar los medios de cobro.");
          return;
        }
      }
    } finally {
      state.running = false;
    }
  }

  return (
    <section className="app-card settings-detail-panel grid scroll-mt-6 gap-5 p-5" id="payment-settings">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-xl font-semibold">Medios de cobro</h2>
          <p className="mt-2 max-w-3xl text-sm text-[var(--brand-muted)]">
            Configura cómo cobra {branchName ?? "la sucursal activa"}. Las cuentas conectadas se comparten, pero estas habilitaciones son independientes.
          </p>
        </div>
        <span aria-live="polite" className="min-h-5 text-xs font-semibold text-[var(--brand-muted)]">
          {saveStatus === "saving" ? "Guardando..." : saveStatus === "saved" ? "Guardado" : null}
        </span>
      </div>
      {error ? <AppNotice message={error} tone="error" /> : null}

      <div className="grid gap-8 xl:grid-cols-2">
        <div className="grid content-start gap-4">
          <div className="border-b border-[var(--brand-line)] pb-3">
            <h3 className="text-lg font-semibold">Mostrador</h3>
            <p className="mt-1 text-sm text-[var(--brand-muted)]">Terminal externa o Mercado Pago Point.</p>
          </div>

          <PaymentToggleRow
            checked={draft.receptionManualTerminalEnabled}
            description="Registra pagos realizados en una terminal externa."
            disabled={!canManage || saving}
            label="Terminal independiente"
            onChange={setManualTerminal}
          />

          <PaymentToggleRow
            checked={draft.receptionMercadoPagoEnabled}
            description={draft.mercadoPagoConnected
              ? `Terminal Point vinculada${draft.mercadoPagoTerminalId ? ` · ${draft.mercadoPagoTerminalId}` : " · selecciona una terminal"}`
              : "Vincula Mercado Pago para usar una terminal Point integrada."}
            disabled={!canManage || saving || !draft.mercadoPagoConnected}
            label="Mercado Pago Point"
            onChange={setMercadoPago}
          />

          {canManage ? (
            <div className="grid gap-3 pt-1">
              <div className="flex flex-wrap gap-2">
                <button className="app-button" disabled={saving} onClick={() => void onConnectMercadoPago()} type="button">
                  {draft.mercadoPagoConnected ? "Volver a vincular Mercado Pago" : "Vincular Mercado Pago"}
                </button>
                {draft.mercadoPagoConnected ? (
                  <button className="app-button" disabled={saving} onClick={() => void onLoadMercadoPagoTerminals()} type="button">
                    Consultar terminales Point
                  </button>
                ) : null}
              </div>
              {mercadoPagoTerminals.length > 0 ? (
                <label className="grid gap-2 text-sm font-semibold">
                  Terminal Point
                  <select
                    className="app-input"
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
                </label>
              ) : null}
            </div>
          ) : null}
        </div>

        <div className="grid content-start gap-4">
          <div className="flex flex-wrap items-start justify-between gap-3 border-b border-[var(--brand-line)] pb-3">
            <div>
              <h3 className="text-lg font-semibold">Stripe</h3>
              <p className="mt-1 text-sm text-[var(--brand-muted)]">Autoservicio y cobros recurrentes.</p>
            </div>
            <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${draft.stripeConnected ? "bg-[#eef7f0] text-[#31543d]" : "bg-[#fff7dd] text-[#745b00]"}`}>
              {draft.stripeConnected ? "Vinculado" : "Pendiente"}
            </span>
          </div>

          <PaymentToggleRow
            checked={draft.stripeSelfCheckoutEnabled}
            description={draft.stripeConnected
              ? "Compras, renovaciones y cambios de plan desde el kiosco."
              : "Vincula Stripe para habilitar pagos en autoservicio."}
            disabled={!canManage || saving || !draft.stripeConnected}
            label="Self checkout con Stripe"
            onChange={setStripeCheckout}
          />

          <PaymentToggleRow
            checked={draft.stripeRecurringEnabled}
            description={draft.stripeSelfCheckoutEnabled
              ? "Domiciliación y cobros recurrentes desde el kiosco."
              : "Primero habilita self checkout con Stripe."}
            disabled={!canManage || saving || !draft.stripeConnected || !draft.stripeSelfCheckoutEnabled}
            label="Domiciliación con Stripe"
            onChange={(checked) => changeToggle({ ...draftRef.current, stripeRecurringEnabled: checked })}
          />

          {canManageStripeAccount ? (
            <div className="flex flex-wrap gap-2 pt-1">
              <button className="app-button" disabled={saving} onClick={() => void onConnectStripe()} type="button">
                {draft.stripeAccountId ? "Continuar configuración de Stripe" : "Vincular Stripe"}
              </button>
              {draft.stripeAccountId ? (
                <button className="app-button" disabled={saving} onClick={() => void onRefreshStripe()} type="button">
                  Verificar conexión de Stripe
                </button>
              ) : null}
            </div>
          ) : (
            <p className="pt-1 text-sm text-[var(--brand-muted)]">
              La cuenta de Stripe sólo puede vincularla o modificarla el dueño del gimnasio.
            </p>
          )}
        </div>
      </div>

      {canManage ? <p className="border-t border-[var(--brand-line)] pt-4 text-right text-xs text-[var(--brand-muted)]">Los medios de cobro se guardan automáticamente al cambiar un toggle.</p> : null}
    </section>
  );
}

function PaymentToggleRow({
  checked,
  description,
  disabled,
  label,
  onChange,
}: {
  checked: boolean;
  description: string;
  disabled: boolean;
  label: string;
  onChange: (checked: boolean) => void;
}) {
  return (
    <article className={`bg-white flex items-start justify-between gap-4 rounded-xl border p-4 ${disabled ? "opacity-60" : ""}`}>
      <div className="min-w-0">
        <strong className="block text-sm">{label}</strong>
        <span className="mt-1 block text-xs leading-5 text-[var(--brand-muted)]">{description}</span>
      </div>
      <ToggleSwitch checked={checked} disabled={disabled} label={label} onChange={onChange} />
    </article>
  );
}
