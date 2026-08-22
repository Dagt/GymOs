"use client";

import { useEffect, useState } from "react";
import { AppNotice } from "@/components/AppNotice";
import { ToggleSwitch } from "@/components/ToggleSwitch";
import {
  getKioskBranchSettings,
  updateKioskBranchSettings,
  type KioskBranchSettings,
} from "@/lib/api";

type EditableKey = "membershipEnrollmentAllowed" | "membershipRenewalAllowed" |
  "membershipPlanChangeAllowed" | "resourceBookingAllowed" | "wellnessBookingAllowed" |
  "profileUpdateAllowed" | "autopayAllowed";
type Requirement = "ONLINE_CARD" | "RECURRING_CARD" | null;
type KioskOption = {
  key: EditableKey;
  title: string;
  description: string;
  requirement: Requirement;
};

const paymentOptions: KioskOption[] = [
  { key: "membershipEnrollmentAllowed", title: "Comprar membresía", description: "Compra y pago con Stripe.", requirement: "ONLINE_CARD" },
  { key: "membershipRenewalAllowed", title: "Renovar membresía", description: "Renovación y pago desde el kiosco.", requirement: "ONLINE_CARD" },
  { key: "membershipPlanChangeAllowed", title: "Cambiar de plan", description: "Cambio de plan cuando requiere un cobro.", requirement: "ONLINE_CARD" },
  { key: "autopayAllowed", title: "Domiciliación", description: "Alta y gestión de cobros recurrentes.", requirement: "RECURRING_CARD" },
];

const serviceOptions: KioskOption[] = [
  { key: "resourceBookingAllowed", title: "Reservas gratuitas", description: "Reserva recursos o clases disponibles.", requirement: null },
  { key: "wellnessBookingAllowed", title: "Citas wellness gratuitas", description: "Gestiona citas wellness sin pago en línea.", requirement: null },
  { key: "profileUpdateAllowed", title: "Actualizar contacto", description: "Permite corregir correo y teléfono.", requirement: null },
];

export function KioskBranchSettingsCard({ accessToken, activeBranchId }: { accessToken: string; activeBranchId?: string | null }) {
  const [items, setItems] = useState<KioskBranchSettings[]>([]);
  const [saving, setSaving] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    getKioskBranchSettings(accessToken)
      .then((loaded) => { if (active) setItems(activeBranchId ? loaded.filter((item) => item.branchId === activeBranchId) : []); })
      .catch((exception) => { if (active) setError(exception instanceof Error ? exception.message : "No fue posible cargar el kiosco."); });
    return () => { active = false; };
  }, [accessToken, activeBranchId]);

  function toggle(branchId: string, key: EditableKey, checked: boolean) {
    setItems((current) => current.map((item) => item.branchId === branchId
      ? { ...item, capabilities: { ...item.capabilities, [key]: checked } }
      : item));
  }

  async function save(item: KioskBranchSettings) {
    setSaving(item.branchId);
    setError(null);
    setMessage(null);
    try {
      const capabilities = item.capabilities;
      const updated = await updateKioskBranchSettings(accessToken, item.branchId, {
        membershipEnrollment: capabilities.onlineCardPaymentsReady && capabilities.membershipEnrollmentAllowed,
        membershipRenewal: capabilities.onlineCardPaymentsReady && capabilities.membershipRenewalAllowed,
        membershipPlanChange: capabilities.onlineCardPaymentsReady && capabilities.membershipPlanChangeAllowed,
        resourceBooking: capabilities.resourceBookingAllowed,
        wellnessBooking: capabilities.wellnessBookingAllowed,
        profileUpdate: capabilities.profileUpdateAllowed,
        autopay: capabilities.recurringCardPaymentsReady && capabilities.autopayAllowed,
      });
      setItems((current) => current.map((entry) => entry.branchId === updated.branchId ? updated : entry));
      setMessage("Configuración del kiosco actualizada.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible guardar el kiosco.");
    } finally {
      setSaving(null);
    }
  }

  return (
    <section className="app-card settings-detail-panel grid scroll-mt-6 gap-5 p-5" id="kiosk-settings">
      <div>
        <h2 className="text-xl font-semibold">Kiosco web</h2>
        <p className="mt-2 max-w-3xl text-sm text-[var(--brand-muted)]">Elige qué puede hacer el socio en la sucursal seleccionada.</p>
      </div>
      {error ? <AppNotice message={error} tone="error" /> : null}
      {message ? <AppNotice message={message} tone="success" /> : null}
      <div className="grid gap-4">
        {items.length === 0 && !error ? <p className="text-sm text-[var(--brand-muted)]">Selecciona una sucursal activa para configurar su kiosco.</p> : null}
        {items.map((item) => (
          <div className="grid gap-5" key={item.branchId}>
            <div className="grid gap-8 xl:grid-cols-2">
              <KioskOptionGroup item={item} options={paymentOptions} title="Pagos y membresías" onToggle={toggle} />
              <KioskOptionGroup item={item} options={serviceOptions} title="Servicios y perfil" onToggle={toggle} />
            </div>
            <div className="flex justify-end pt-1">
              <button className="app-button" disabled={saving === item.branchId} onClick={() => void save(item)} type="button">
                {saving === item.branchId ? "Guardando..." : "Guardar configuración del kiosco"}
              </button>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}

function KioskOptionGroup({ item, options, title, onToggle }: {
  item: KioskBranchSettings;
  options: KioskOption[];
  title: string;
  onToggle: (branchId: string, key: EditableKey, checked: boolean) => void;
}) {
  return (
    <section className="grid content-start gap-3">
      <h4 className="font-semibold">{title}</h4>
      {options.map((option) => {
        const disabled = !requirementReady(item, option.requirement);
        const checked = !disabled && item.capabilities[option.key];
        return (
          <article className={`bg-white flex items-start justify-between gap-4 rounded-xl border p-4 ${disabled ? "opacity-60" : ""}`} key={option.key}>
            <div className="min-w-0">
              <strong className="block text-sm">{option.title}</strong>
              <span className="mt-1 block text-xs leading-5 text-[var(--brand-muted)]">
                {disabled ? requirementMessage(option.requirement) : option.description}
              </span>
            </div>
            <ToggleSwitch checked={checked} disabled={disabled} label={`${option.title} en ${item.branchName}`} onChange={(next) => onToggle(item.branchId, option.key, next)} />
          </article>
        );
      })}
    </section>
  );
}

function requirementReady(item: KioskBranchSettings, requirement: Requirement) {
  if (requirement === "ONLINE_CARD") return item.capabilities.onlineCardPaymentsReady;
  if (requirement === "RECURRING_CARD") return item.capabilities.recurringCardPaymentsReady;
  return true;
}

function requirementMessage(requirement: Requirement) {
  if (requirement === "ONLINE_CARD") return "Activa self checkout con Stripe en Medios de cobro.";
  if (requirement === "RECURRING_CARD") return "Activa domiciliación con Stripe en Medios de cobro.";
  return "Esta función no está disponible.";
}
