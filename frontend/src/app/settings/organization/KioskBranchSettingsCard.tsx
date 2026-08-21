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
  {
    key: "membershipEnrollmentAllowed",
    title: "Comprar membresía",
    description: "Permite contratar una membresía y pagarla con Stripe desde el kiosco.",
    requirement: "ONLINE_CARD",
  },
  {
    key: "membershipRenewalAllowed",
    title: "Renovar membresía",
    description: "Permite renovar una membresía activa mediante self checkout.",
    requirement: "ONLINE_CARD",
  },
  {
    key: "membershipPlanChangeAllowed",
    title: "Cambiar de plan",
    description: "Permite cambiar de plan cuando el ajuste requiere pago con Stripe.",
    requirement: "ONLINE_CARD",
  },
  {
    key: "autopayAllowed",
    title: "Domiciliación",
    description: "Permite activar o administrar cobros recurrentes desde el kiosco.",
    requirement: "RECURRING_CARD",
  },
];

const serviceOptions: KioskOption[] = [
  {
    key: "resourceBookingAllowed",
    title: "Reservas gratuitas",
    description: "Permite reservar recursos o clases disponibles para el socio.",
    requirement: null,
  },
  {
    key: "wellnessBookingAllowed",
    title: "Citas wellness gratuitas",
    description: "Permite gestionar citas wellness disponibles sin pago en línea.",
    requirement: null,
  },
  {
    key: "profileUpdateAllowed",
    title: "Actualizar contacto",
    description: "Permite al socio corregir su correo y teléfono desde autoservicio.",
    requirement: null,
  },
];

export function KioskBranchSettingsCard({
  accessToken,
  activeBranchId,
}: {
  accessToken: string;
  activeBranchId?: string | null;
}) {
  const [items, setItems] = useState<KioskBranchSettings[]>([]);
  const [saving, setSaving] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    getKioskBranchSettings(accessToken)
      .then((loaded) => {
        if (active) setItems(activeBranchId ? loaded.filter((item) => item.branchId === activeBranchId) : []);
      })
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
      setMessage(`Kiosco de ${updated.branchName} actualizado.`);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible guardar el kiosco.");
    } finally {
      setSaving(null);
    }
  }

  return (
    <section className="app-card settings-detail-panel grid scroll-mt-6 gap-5 p-5" id="kiosk-settings">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="app-eyebrow">Kiosco web</p>
          <h2 className="mt-2 text-xl font-semibold">Funciones disponibles para el socio</h2>
          <p className="mt-2 max-w-3xl text-sm text-[var(--brand-muted)]">
            Las funciones que requieren Stripe sólo pueden activarse cuando el medio de cobro correspondiente está habilitado en Medios de cobro.
          </p>
        </div>
      </div>
      {error ? <AppNotice message={error} tone="error" /> : null}
      {message ? <AppNotice message={message} tone="success" /> : null}
      <div className="grid gap-4">
        {items.length === 0 && !error ? <p className="text-sm text-[var(--brand-muted)]">Selecciona una sucursal activa para configurar su kiosco.</p> : null}
        {items.map((item) => (
          <article className="rounded-xl border border-[var(--brand-line)] bg-white p-5" key={item.branchId}>
            <div className="flex flex-wrap items-start justify-between gap-3 border-b border-[var(--brand-line)] pb-4">
              <div>
                <h3 className="text-lg font-semibold">{item.branchName}</h3>
                <div className="mt-2 flex flex-wrap gap-2 text-xs font-semibold">
                  <ReadinessBadge ready={item.capabilities.onlineCardPaymentsReady} text="Stripe self checkout" />
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

            <div className="mt-5 grid gap-4 xl:grid-cols-2">
              <KioskOptionGroup
                item={item}
                options={paymentOptions}
                subtitle="Compra, renovación, cambio de plan y domiciliación dependen de la configuración global de Stripe."
                title="Pagos y membresías"
                onToggle={toggle}
              />
              <KioskOptionGroup
                item={item}
                options={serviceOptions}
                subtitle="Funciones de autoservicio que no dependen del canal de pago con tarjeta."
                title="Servicios y perfil"
                onToggle={toggle}
              />
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}

function KioskOptionGroup({
  item,
  options,
  subtitle,
  title,
  onToggle,
}: {
  item: KioskBranchSettings;
  options: KioskOption[];
  subtitle: string;
  title: string;
  onToggle: (branchId: string, key: EditableKey, checked: boolean) => void;
}) {
  return (
    <section className="grid content-start gap-3 rounded-xl border border-[var(--brand-line)] bg-[var(--brand-surface)] p-4">
      <div className="pb-1">
        <h4 className="font-semibold">{title}</h4>
        <p className="mt-1 text-xs leading-5 text-[var(--brand-muted)]">{subtitle}</p>
      </div>
      {options.map((option) => {
        const disabled = !requirementReady(item, option.requirement);
        const checked = !disabled && item.capabilities[option.key];
        return (
          <div className={`flex items-start justify-between gap-4 rounded-lg border border-[var(--brand-line)] p-4 ${disabled ? "bg-[#f3f6f5]" : "bg-white"}`} key={option.key}>
            <div className="min-w-0">
              <strong className="block text-sm">{option.title}</strong>
              <span className="mt-1 block text-xs leading-5 text-[var(--brand-muted)]">
                {disabled ? requirementMessage(option.requirement) : option.description}
              </span>
            </div>
            <ToggleSwitch
              checked={checked}
              disabled={disabled}
              label={`${option.title} en ${item.branchName}`}
              onChange={(next) => onToggle(item.branchId, option.key, next)}
            />
          </div>
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
  if (requirement === "ONLINE_CARD") return "Habilita Stripe para self checkout en Medios de cobro para activar esta función.";
  if (requirement === "RECURRING_CARD") return "Habilita la domiciliación con Stripe en Medios de cobro para activar esta función.";
  return "Esta función no está disponible.";
}

function ReadinessBadge({ ready, text }: { ready: boolean; text: string }) {
  return (
    <span className={`rounded-full px-2.5 py-1 ${ready ? "bg-[#eef7f0] text-[#31543d]" : "bg-[#fff7dd] text-[#745b00]"}`}>
      {text}: {ready ? "listo" : "pendiente"}
    </span>
  );
}
