"use client";

import { useEffect, useState } from "react";
import {
  getKioskBranchSettings,
  updateKioskBranchSettings,
  type KioskBranchSettings,
} from "@/lib/api";

type EditableKey = "membershipEnrollmentAllowed" | "membershipRenewalAllowed" |
  "membershipPlanChangeAllowed" | "resourceBookingAllowed" | "wellnessBookingAllowed" |
  "profileUpdateAllowed" | "autopayAllowed";

const options: Array<{ key: EditableKey; title: string }> = [
  { key: "membershipEnrollmentAllowed", title: "Comprar membresía" },
  { key: "membershipRenewalAllowed", title: "Renovar membresía" },
  { key: "membershipPlanChangeAllowed", title: "Cambiar de plan" },
  { key: "autopayAllowed", title: "Domiciliación" },
  { key: "resourceBookingAllowed", title: "Reservas gratuitas" },
  { key: "wellnessBookingAllowed", title: "Citas wellness gratuitas" },
  { key: "profileUpdateAllowed", title: "Actualizar contacto" },
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
        wellnessBooking: capabilities.wellnessBookingAllowed,
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
      <h2 className="text-xl font-semibold">Funciones disponibles para el socio</h2>
      {error ? <p className="form-message error" role="alert">{error}</p> : null}
      {message ? <p className="form-message success" role="status">{message}</p> : null}
      <div className="grid gap-4">
        {items.length === 0 && !error ? <p className="text-sm text-[var(--brand-muted)]">Selecciona una sucursal activa para configurar su kiosco.</p> : null}
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
                  <strong className="text-sm">{option.title}</strong>
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
