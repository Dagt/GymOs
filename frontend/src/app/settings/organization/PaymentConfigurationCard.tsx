"use client";

import { useState } from "react";
import { ToggleSwitch } from "@/components/ToggleSwitch";
import type { MercadoPagoTerminal, PaymentConfiguration } from "@/lib/api";

export function PaymentConfigurationCard({
  canManage,
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
  canManageStripeAccount: boolean;
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

  function setManualTerminal(checked: boolean) {
    setDraft((current) => ({
      ...current,
      receptionManualTerminalEnabled: checked,
      receptionMercadoPagoEnabled: checked ? false : current.receptionMercadoPagoEnabled,
    }));
  }

  function setMercadoPago(checked: boolean) {
    setDraft((current) => ({
      ...current,
      receptionMercadoPagoEnabled: checked,
      receptionManualTerminalEnabled: checked ? false : current.receptionManualTerminalEnabled,
    }));
  }

  function setStripeCheckout(checked: boolean) {
    setDraft((current) => ({
      ...current,
      stripeSelfCheckoutEnabled: checked,
      stripeRecurringEnabled: checked ? current.stripeRecurringEnabled : false,
    }));
  }

  return (
    <section className="app-card settings-detail-panel grid scroll-mt-6 gap-5 p-5" id="payment-settings">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="app-eyebrow">Medios de cobro</p>
          <h2 className="mt-2 text-xl font-semibold">Recepción y pagos digitales</h2>
          <p className="mt-2 max-w-3xl text-sm text-[var(--brand-muted)]">
            Separa cómo cobras en mostrador de los servicios de Stripe que habilitan pagos y domiciliación en el kiosco.
          </p>
        </div>
        <span className="rounded-full border border-[var(--brand-line)] bg-[var(--brand-surface)] px-3 py-1.5 text-xs font-semibold text-[var(--brand-muted)]">
          Configuración del gimnasio
        </span>
      </div>

      <div className="grid gap-4 xl:grid-cols-2">
        <article className="grid content-start gap-4 rounded-xl border border-[var(--brand-line)] bg-white p-5">
          <div className="border-b border-[var(--brand-line)] pb-4">
            <p className="app-eyebrow">Mostrador</p>
            <h3 className="mt-2 text-lg font-semibold">Cobro con terminal en recepción</h3>
            <p className="mt-1 text-sm text-[var(--brand-muted)]">
              Elige una sola modalidad. La terminal independiente y Mercado Pago Point son excluyentes.
            </p>
          </div>

          <PaymentToggleRow
            checked={draft.receptionManualTerminalEnabled}
            description="Registra en GymFort un cobro realizado en una terminal externa, sin integración automática."
            disabled={!canManage}
            label="Terminal independiente"
            onChange={setManualTerminal}
          />

          <PaymentToggleRow
            checked={draft.receptionMercadoPagoEnabled}
            description={draft.mercadoPagoConnected
              ? `Cuenta vinculada${draft.mercadoPagoTerminalId ? ` · terminal ${draft.mercadoPagoTerminalId}` : " · selecciona una terminal Point"}`
              : "Vincula Mercado Pago antes de habilitar el cobro integrado."}
            disabled={!canManage || !draft.mercadoPagoConnected}
            label="Mercado Pago Point integrado"
            onChange={setMercadoPago}
          />

          {canManage ? (
            <div className="grid gap-3 rounded-lg bg-[var(--brand-surface)] p-4">
              <div className="flex flex-wrap gap-2">
                <button className="app-button-secondary" disabled={saving} onClick={() => void onConnectMercadoPago()} type="button">
                  {draft.mercadoPagoConnected ? "Volver a vincular Mercado Pago" : "Vincular Mercado Pago"}
                </button>
                {draft.mercadoPagoConnected ? (
                  <button className="app-button-secondary" disabled={saving} onClick={() => void onLoadMercadoPagoTerminals()} type="button">
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
        </article>

        <article className="grid content-start gap-4 rounded-xl border border-[var(--brand-line)] bg-white p-5">
          <div className="flex flex-wrap items-start justify-between gap-3 border-b border-[var(--brand-line)] pb-4">
            <div>
              <p className="app-eyebrow">Stripe</p>
              <h3 className="mt-2 text-lg font-semibold">Pagos del kiosco y domiciliación</h3>
              <p className="mt-1 text-sm text-[var(--brand-muted)]">
                Estas opciones gobiernan qué capacidades de pago pueden activarse después en cada kiosco.
              </p>
            </div>
            <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${draft.stripeConnected ? "bg-[#eef7f0] text-[#31543d]" : "bg-[#fff7dd] text-[#745b00]"}`}>
              {draft.stripeConnected ? "Stripe vinculado" : "Stripe pendiente"}
            </span>
          </div>

          <PaymentToggleRow
            checked={draft.stripeSelfCheckoutEnabled}
            description={draft.stripeConnected
              ? "Permite habilitar compras, renovaciones y cambios de plan pagados con Stripe desde el kiosco."
              : "Vincula Stripe antes de habilitar pagos en autoservicio."}
            disabled={!canManage || !draft.stripeConnected}
            label="Stripe para self checkout"
            onChange={setStripeCheckout}
          />

          <PaymentToggleRow
            checked={draft.stripeRecurringEnabled}
            description={draft.stripeSelfCheckoutEnabled
              ? "Permite que el kiosco ofrezca alta y administración de domiciliación con Stripe."
              : "Primero habilita Stripe para self checkout."}
            disabled={!canManage || !draft.stripeConnected || !draft.stripeSelfCheckoutEnabled}
            label="Domiciliación con Stripe"
            onChange={(checked) => setDraft((current) => ({ ...current, stripeRecurringEnabled: checked }))}
          />

          {canManageStripeAccount ? (
            <div className="flex flex-wrap gap-2 rounded-lg bg-[var(--brand-surface)] p-4">
              <button className="app-button-secondary" disabled={saving} onClick={() => void onConnectStripe()} type="button">
                {draft.stripeAccountId ? "Continuar configuración de Stripe" : "Vincular Stripe"}
              </button>
              {draft.stripeAccountId ? (
                <button className="app-button-secondary" disabled={saving} onClick={() => void onRefreshStripe()} type="button">
                  Verificar conexión de Stripe
                </button>
              ) : null}
            </div>
          ) : (
            <p className="rounded-lg border border-[var(--brand-line)] bg-[var(--brand-surface)] px-4 py-3 text-sm text-[var(--brand-muted)]">
              La cuenta de Stripe sólo puede vincularla o modificarla el dueño del gimnasio. Cuando esté conectada podrás administrar su uso operativo.
            </p>
          )}

          <div className="rounded-lg border border-[var(--brand-line)] bg-[var(--brand-surface)] p-4 text-sm text-[var(--brand-muted)]">
            <strong className="block text-[var(--brand-ink)]">Dependencia con Kiosco web</strong>
            <span className="mt-1 block">
              Si self checkout o domiciliación están apagados aquí, sus toggles dependientes aparecerán deshabilitados en la configuración del kiosco.
            </span>
          </div>
        </article>
      </div>

      {canManage ? (
        <div className="flex justify-end border-t border-[var(--brand-line)] pt-5">
          <button className="app-button" disabled={saving} onClick={() => void onSave(draft)} type="button">
            {saving ? "Guardando..." : "Guardar configuración de cobro"}
          </button>
        </div>
      ) : null}
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
    <div className={`flex items-start justify-between gap-4 rounded-lg border border-[var(--brand-line)] p-4 ${disabled ? "bg-[#f7f9f8]" : "bg-white"}`}>
      <div className="min-w-0">
        <strong className="block text-sm">{label}</strong>
        <span className="mt-1 block text-xs leading-5 text-[var(--brand-muted)]">{description}</span>
      </div>
      <ToggleSwitch checked={checked} disabled={disabled} label={label} onChange={onChange} />
    </div>
  );
}
