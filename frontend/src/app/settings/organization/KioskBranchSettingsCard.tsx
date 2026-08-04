"use client";

import { useEffect, useState } from "react";
import {
  getKioskBranchSettings,
  updateKioskBranchSettings,
  type KioskBranchSettings,
} from "@/lib/api";

type EditableKey = "membershipEnrollmentAllowed" | "membershipRenewalAllowed" |
  "membershipPlanChangeAllowed" | "resourceBookingAllowed" | "profileUpdateAllowed" | "autopayAllowed";

const options: Array<{ key: EditableKey; title: string; description: string }> = [
  { key: "membershipEnrollmentAllowed", title: "Comprar membresía", description: "Alta y pago en Stripe desde el navegador." },
  { key: "membershipRenewalAllowed", title: "Renovar membresía", description: "Cotiza, confirma y paga una renovación." },
  { key: "membershipPlanChangeAllowed", title: "Cambiar de plan", description: "Aplica la cotización final antes del pago." },
  { key: "autopayAllowed", title: "Domiciliación", description: "Consentimiento y tarjeta tokenizada con Stripe." },
  { key: "resourceBookingAllowed", title: "Reservas gratuitas", description: "No habilita recursos ni wellness con costo." },
  { key: "profileUpdateAllowed", title: "Actualizar contacto", description: "Correo y teléfono del socio verificado." },
];

export function KioskBranchSettingsCard({ accessToken }: { accessToken: string }) {
  const [items, setItems] = useState<KioskBranchSettings[]>([]);
  const [saving, setSaving] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    getKioskBranchSettings(accessToken)
      .then((loaded) => { if (active) setItems(loaded); })
      .catch((exception) => { if (active) setError(exception instanceof Error ? exception.message : "No fue posible cargar el kiosco."); });
    return () => { active = false; };
  }, [accessToken]);

  function toggle(branchId: string, key: EditableKey) {
    setItems((current) => current.map((item) => item.branchId === branchId
      ? { ...item, capabilities: { ...item.capabilities, [key]: !item.capabilities[key] } }
      : item));
  }

  async function save(item: KioskBranchSettings) {
    setSaving(item.branchId);
    setError(null);
    setMessage(null);
    try {
      const capabilities = item.capabilities;
      const updated = await updateKioskBranchSettings(accessToken, item.branchId, {
        membershipEnrollment: capabilities.membershipEnrollmentAllowed,
        membershipRenewal: capabilities.membershipRenewalAllowed,
        membershipPlanChange: capabilities.membershipPlanChangeAllowed,
        resourceBooking: capabilities.resourceBookingAllowed,
        profileUpdate: capabilities.profileUpdateAllowed,
        autopay: capabilities.autopayAllowed,
      });
      setItems((current) => current.map((entry) => entry.branchId === updated.branchId ? updated : entry));
      setMessage(`Kiosco de ${updated.branchName} actualizado.`);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible guardar el kiosco.");
    } finally {
      setSaving(null);
    }
  }

  return (
    <section className="app-card grid scroll-mt-6 gap-5 p-5" id="kiosk-settings">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
        <p className="app-eyebrow">Kiosco web por sucursal</p>
        <h2 className="mt-1 text-xl font-semibold">Funciones disponibles para el socio</h2>
        <p className="mt-1 text-sm text-[var(--brand-muted)]">
          Esta configuración es lógica: recepción abre el kiosco en cualquier navegador; no se registra ni asigna una computadora.
        </p>
        </div>
        <div className="max-w-md rounded-lg border border-[var(--brand-line)] bg-[var(--brand-surface)] p-4 text-sm">
          <strong className="block">Cómo abrirlo</strong>
          <span className="mt-1 block leading-5 text-[var(--brand-muted)]">
            Inicia sesión con una cuenta de recepción, elige la sucursal y usa “Abrir kiosco” desde su pantalla de inicio. GymOS cerrará el acceso administrativo en esa pantalla.
          </span>
        </div>
      </div>
      <p className="rounded-md border border-[var(--brand-line)] bg-white p-3 text-sm text-[var(--brand-muted)]">
        Las compras, renovaciones, cambios de plan y domiciliaciones sólo se activan cuando Stripe está conectado. Las reservas aquí son gratuitas; recursos y wellness con costo no forman parte del alcance.
      </p>
      {error ? <p className="form-message error" role="alert">{error}</p> : null}
      {message ? <p className="form-message success" role="status">{message}</p> : null}
      <div className="grid gap-4">
        {items.length === 0 && !error ? <p className="text-sm text-[var(--brand-muted)]">No hay sucursales activas para configurar.</p> : null}
        {items.map((item) => (
          <article className="rounded-lg border border-[var(--brand-line)] p-4" key={item.branchId}>
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <h3 className="font-semibold">{item.branchName}</h3>
                <div className="mt-2 flex flex-wrap gap-2 text-xs font-semibold">
                  <ReadinessBadge ready={item.capabilities.onlineCardPaymentsReady} text="Pagos Stripe" />
                  <ReadinessBadge ready={item.capabilities.recurringCardPaymentsReady} text="Domiciliación" />
                  <span className="rounded-full bg-[#eef1f3] px-2.5 py-1 text-[#42514a]">
                    {item.status === "ACTIVE" ? "Kiosco preparado" : "Se preparará al primer uso"}
                  </span>
                </div>
              </div>
              <button className="app-button" disabled={saving === item.branchId} onClick={() => void save(item)} type="button">
                {saving === item.branchId ? "Guardando..." : "Guardar sucursal"}
              </button>
            </div>
            <div className="mt-4 grid gap-2 md:grid-cols-2">
              {options.map((option) => (
                <label className="flex cursor-pointer gap-3 rounded-md border border-[var(--brand-line)] p-3" key={option.key}>
                  <input checked={item.capabilities[option.key]} onChange={() => toggle(item.branchId, option.key)} type="checkbox" />
                  <span><strong className="block text-sm">{option.title}</strong><small className="text-[var(--brand-muted)]">{option.description}</small></span>
                </label>
              ))}
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}

function ReadinessBadge({ ready, text }: { ready: boolean; text: string }) {
  return (
    <span className={`rounded-full px-2.5 py-1 ${ready ? "bg-[#eef7f0] text-[#31543d]" : "bg-[#fff7dd] text-[#745b00]"}`}>
      {text}: {ready ? "listo" : "pendiente"}
    </span>
  );
}
