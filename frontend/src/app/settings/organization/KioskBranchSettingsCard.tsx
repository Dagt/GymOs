"use client";

import { useEffect, useRef, useState } from "react";
import { AppNotice } from "@/components/AppNotice";
import { ToggleSwitch } from "@/components/ToggleSwitch";
import {
  getKioskBranchSettings,
  updateKioskBranchSettings,
  type KioskBranchSettings,
  type UpdateKioskBranchSettings,
} from "@/lib/api";

type EditableKey = "clientRegistrationAllowed" | "membershipEnrollmentAllowed" | "membershipRenewalAllowed" |
  "membershipPlanChangeAllowed" | "resourceBookingAllowed" | "wellnessBookingAllowed" |
  "profileUpdateAllowed" | "autopayAllowed";
type Requirement = "ONLINE_CARD" | "RECURRING_CARD" | null;
type SaveStatus = "saving" | "saved" | null;
type KioskOption = {
  key: EditableKey;
  title: string;
  description: string;
  requirement: Requirement;
};
type BranchSaveState = {
  revision: number;
  completedRevision: number;
  running: boolean;
  confirmed: KioskBranchSettings;
};

const paymentOptions: KioskOption[] = [
  { key: "membershipEnrollmentAllowed", title: "Comprar membresía", description: "Compra y pago con Stripe.", requirement: "ONLINE_CARD" },
  { key: "membershipRenewalAllowed", title: "Renovar membresía", description: "Renovación y pago desde el kiosco.", requirement: "ONLINE_CARD" },
  { key: "membershipPlanChangeAllowed", title: "Cambiar de plan", description: "Cambio de plan cuando requiere un cobro.", requirement: "ONLINE_CARD" },
  { key: "autopayAllowed", title: "Domiciliación", description: "Alta y gestión de cobros recurrentes.", requirement: "RECURRING_CARD" },
];

const serviceOptions: KioskOption[] = [
  { key: "clientRegistrationAllowed", title: "Registrar nuevos socios", description: "Permite el alta o reactivación con datos personales desde el kiosco.", requirement: null },
  { key: "resourceBookingAllowed", title: "Reservas gratuitas", description: "Reserva recursos o clases disponibles.", requirement: null },
  { key: "wellnessBookingAllowed", title: "Citas wellness gratuitas", description: "Gestiona citas wellness sin pago en línea.", requirement: null },
  { key: "profileUpdateAllowed", title: "Actualizar contacto", description: "Permite corregir correo y teléfono.", requirement: null },
];

export function KioskBranchSettingsCard({ accessToken, activeBranchId }: { accessToken: string; activeBranchId?: string | null }) {
  const [items, setItems] = useState<KioskBranchSettings[]>([]);
  const [saveStatuses, setSaveStatuses] = useState<Record<string, SaveStatus>>({});
  const [error, setError] = useState<string | null>(null);
  const itemsRef = useRef<KioskBranchSettings[]>([]);
  const saveStatesRef = useRef(new Map<string, BranchSaveState>());
  const generationRef = useRef(0);

  useEffect(() => {
    const generation = generationRef.current + 1;
    generationRef.current = generation;
    let active = true;
    getKioskBranchSettings(accessToken)
      .then((loaded) => {
        if (!active || generationRef.current !== generation) return;
        const visible = activeBranchId ? loaded.filter((item) => item.branchId === activeBranchId) : [];
        itemsRef.current = visible;
        saveStatesRef.current = new Map(visible.map((item) => [item.branchId, {
          revision: 0,
          completedRevision: 0,
          running: false,
          confirmed: item,
        }]));
        setItems(visible);
        setSaveStatuses({});
      })
      .catch((exception) => {
        if (active && generationRef.current === generation) {
          setError(exception instanceof Error ? exception.message : "No fue posible cargar el kiosco.");
        }
      });
    return () => {
      active = false;
      if (generationRef.current === generation) generationRef.current += 1;
    };
  }, [accessToken, activeBranchId]);

  function toggle(branchId: string, key: EditableKey, checked: boolean) {
    const current = itemsRef.current.find((item) => item.branchId === branchId);
    if (!current) return;
    const next = { ...current, capabilities: { ...current.capabilities, [key]: checked } };
    replaceItem(next);
    const state = saveStatesRef.current.get(branchId) ?? {
      revision: 0,
      completedRevision: 0,
      running: false,
      confirmed: current,
    };
    state.revision += 1;
    saveStatesRef.current.set(branchId, state);
    setError(null);
    setSaveStatus(branchId, "saving");
    void persistQueuedChanges(branchId, generationRef.current);
  }

  async function persistQueuedChanges(branchId: string, generation: number) {
    const state = saveStatesRef.current.get(branchId);
    if (!state || state.running) return;
    state.running = true;
    try {
      while (state.completedRevision < state.revision) {
        const requestedRevision = state.revision;
        const desired = itemsRef.current.find((item) => item.branchId === branchId);
        if (!desired) return;
        try {
          const updated = await updateKioskBranchSettings(
            accessToken,
            branchId,
            toUpdatePayload(desired),
          );
          state.confirmed = updated;
          state.completedRevision = requestedRevision;
          if (state.revision === requestedRevision && generationRef.current === generation) {
            replaceItem(updated);
            setSaveStatus(branchId, "saved");
          }
        } catch (exception) {
          state.completedRevision = state.revision;
          if (generationRef.current === generation) {
            replaceItem(state.confirmed);
            setSaveStatus(branchId, null);
            setError(exception instanceof Error ? exception.message : "No fue posible guardar el kiosco.");
          }
          return;
        }
      }
    } finally {
      state.running = false;
    }
  }

  function replaceItem(next: KioskBranchSettings) {
    itemsRef.current = itemsRef.current.map((item) => item.branchId === next.branchId ? next : item);
    setItems(itemsRef.current);
  }

  function setSaveStatus(branchId: string, status: SaveStatus) {
    setSaveStatuses((current) => ({ ...current, [branchId]: status }));
  }

  return (
    <section className="app-card settings-detail-panel grid scroll-mt-6 gap-5 p-5" id="kiosk-settings">
      <div>
        <h2 className="text-xl font-semibold">Kiosco web</h2>
        <p className="mt-2 max-w-3xl text-sm text-[var(--brand-muted)]">Elige qué desea ofrecer el gimnasio. Stripe se muestra como disponibilidad técnica y no borra esta configuración.</p>
      </div>
      {error ? <AppNotice message={error} tone="error" /> : null}
      <div className="grid gap-4">
        {items.length === 0 && !error ? <p className="text-sm text-[var(--brand-muted)]">Selecciona una sucursal activa para configurar su kiosco.</p> : null}
        {items.map((item) => (
          <div className="grid gap-5" key={item.branchId}>
            <div className="flex items-center justify-between gap-4">
              <h3 className="sr-only">{item.branchName}</h3>
              <span aria-live="polite" className="ml-auto text-xs font-semibold text-[var(--brand-muted)]">
                {saveStatuses[item.branchId] === "saving" ? "Guardando..." : null}
                {saveStatuses[item.branchId] === "saved" ? "Guardado" : null}
              </span>
            </div>
            <div className="grid gap-8 xl:grid-cols-2">
              <KioskOptionGroup item={item} options={paymentOptions} title="Pagos y membresías" onToggle={toggle} />
              <KioskOptionGroup item={item} options={serviceOptions} title="Servicios y perfil" onToggle={toggle} />
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
        const ready = requirementReady(item, option.requirement);
        const configured = item.capabilities[option.key];
        const pendingProvider = configured && !ready;
        return (
          <article className="flex items-start justify-between gap-4 rounded-xl border bg-white p-4" key={option.key}>
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-2">
                <strong className="block text-sm">{option.title}</strong>
                {pendingProvider ? (
                  <span className="inline-flex items-center gap-1.5 text-[11px] font-semibold text-amber-700">
                    <span aria-hidden="true" className="h-1.5 w-1.5 rounded-full bg-amber-500" />
                    Stripe pendiente
                  </span>
                ) : null}
              </div>
              <span className="mt-1 block text-xs leading-5 text-[var(--brand-muted)]">
                {pendingProvider ? requirementMessage(option.requirement) : configured ? option.description : `Función deshabilitada. ${option.description}`}
              </span>
            </div>
            <ToggleSwitch checked={configured} label={`${option.title} en ${item.branchName}`} onChange={(next) => onToggle(item.branchId, option.key, next)} />
          </article>
        );
      })}
    </section>
  );
}

function toUpdatePayload(item: KioskBranchSettings): UpdateKioskBranchSettings {
  const capabilities = item.capabilities;
  return {
    clientRegistration: capabilities.clientRegistrationAllowed,
    membershipEnrollment: capabilities.membershipEnrollmentAllowed,
    membershipRenewal: capabilities.membershipRenewalAllowed,
    membershipPlanChange: capabilities.membershipPlanChangeAllowed,
    resourceBooking: capabilities.resourceBookingAllowed,
    wellnessBooking: capabilities.wellnessBookingAllowed,
    profileUpdate: capabilities.profileUpdateAllowed,
    autopay: capabilities.autopayAllowed,
  };
}

function requirementReady(item: KioskBranchSettings, requirement: Requirement) {
  if (requirement === "ONLINE_CARD") return item.capabilities.onlineCardPaymentsReady;
  if (requirement === "RECURRING_CARD") return item.capabilities.recurringCardPaymentsReady;
  return true;
}

function requirementMessage(requirement: Requirement) {
  if (requirement === "ONLINE_CARD") return "Se habilitará automáticamente cuando Stripe esté listo para pagos en línea.";
  if (requirement === "RECURRING_CARD") return "Se habilitará automáticamente cuando Stripe esté listo para domiciliación.";
  return "Esta función no está disponible.";
}
