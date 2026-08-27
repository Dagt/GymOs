"use client";

import { useEffect, useRef, useState } from "react";
import { AppNotice } from "@/components/AppNotice";
import { ToggleSwitch } from "@/components/ToggleSwitch";
import {
  getClientLifecycleSettings,
  updateClientLifecycleSettings,
  type ClientLifecycleSettings,
} from "@/lib/api";

type SaveStatus = "saving" | "saved" | null;
type LifecycleSaveState = {
  revision: number;
  queuedRevision: number;
  completedRevision: number;
  running: boolean;
  confirmed: ClientLifecycleSettings;
};

export function ClientLifecycleSettingsCard({ accessToken }: { accessToken: string }) {
  const [settings, setSettings] = useState<ClientLifecycleSettings | null>(null);
  const [feeAmount, setFeeAmount] = useState("0");
  const [saveStatus, setSaveStatus] = useState<SaveStatus>(null);
  const [error, setError] = useState<string | null>(null);
  const settingsRef = useRef<ClientLifecycleSettings | null>(null);
  const feeAmountRef = useRef("0");
  const saveStateRef = useRef<LifecycleSaveState | null>(null);
  const generationRef = useRef(0);
  const autosaveTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    const generation = generationRef.current + 1;
    generationRef.current = generation;
    let active = true;
    getClientLifecycleSettings(accessToken)
      .then((loaded) => {
        if (!active || generationRef.current !== generation) return;
        const amount = (loaded.registrationFeeCents / 100).toFixed(2);
        settingsRef.current = loaded;
        feeAmountRef.current = amount;
        saveStateRef.current = {
          revision: 0,
          queuedRevision: 0,
          completedRevision: 0,
          running: false,
          confirmed: loaded,
        };
        setSettings(loaded);
        setFeeAmount(amount);
      })
      .catch((exception) => {
        if (active && generationRef.current === generation) {
          setError(exception instanceof Error ? exception.message : "No fue posible cargar las reglas de socios.");
        }
      });
    return () => {
      active = false;
      if (autosaveTimerRef.current) clearTimeout(autosaveTimerRef.current);
      if (generationRef.current === generation) generationRef.current += 1;
    };
  }, [accessToken]);

  function editSettings(next: ClientLifecycleSettings, autosave = false) {
    const state = saveStateRef.current;
    if (state) state.revision += 1;
    settingsRef.current = next;
    setSettings(next);
    setSaveStatus(null);
    setError(null);
    if (autosave) scheduleSave();
  }

  function editFeeAmount(next: string) {
    const state = saveStateRef.current;
    if (state) state.revision += 1;
    feeAmountRef.current = next;
    setFeeAmount(next);
    setSaveStatus(null);
    setError(null);
    scheduleSave();
  }

  function toggle(key: "registrationFeeEnabled" | "autoInactivationEnabled", checked: boolean) {
    const current = settingsRef.current;
    if (!current) return;
    const next = { ...current, [key]: checked };
    const validationError = validate(next, feeAmountRef.current);
    if (validationError) {
      setError(validationError);
      return;
    }
    editSettings(next);
    queueSave();
  }

  function scheduleSave() {
    if (autosaveTimerRef.current) clearTimeout(autosaveTimerRef.current);
    setSaveStatus("saving");
    autosaveTimerRef.current = setTimeout(() => {
      autosaveTimerRef.current = null;
      const current = settingsRef.current;
      if (!current) return;
      const validationError = validate(current, feeAmountRef.current);
      if (validationError) {
        setSaveStatus(null);
        setError(validationError);
        return;
      }
      queueSave();
    }, 500);
  }

  function queueSave() {
    const state = saveStateRef.current;
    if (!state) return;
    if (autosaveTimerRef.current) {
      clearTimeout(autosaveTimerRef.current);
      autosaveTimerRef.current = null;
    }
    state.queuedRevision = state.revision;
    setSaveStatus("saving");
    void persistQueuedChanges(generationRef.current);
  }

  async function persistQueuedChanges(generation: number) {
    const state = saveStateRef.current;
    if (!state || state.running) return;
    state.running = true;
    try {
      while (state.completedRevision < state.queuedRevision) {
        const requestedRevision = state.queuedRevision;
        const desired = settingsRef.current;
        const amount = feeAmountRef.current;
        if (!desired) return;
        try {
          const updated = await updateClientLifecycleSettings(accessToken, toPayload(desired, amount));
          state.confirmed = updated;
          state.completedRevision = requestedRevision;
          if (generationRef.current !== generation) return;
          if (state.revision === requestedRevision) {
            const confirmedAmount = (updated.registrationFeeCents / 100).toFixed(2);
            settingsRef.current = updated;
            feeAmountRef.current = confirmedAmount;
            setSettings(updated);
            setFeeAmount(confirmedAmount);
            setSaveStatus("saved");
          }
        } catch (exception) {
          state.completedRevision = state.queuedRevision;
          state.revision = state.queuedRevision;
          if (generationRef.current === generation) {
            const confirmedAmount = (state.confirmed.registrationFeeCents / 100).toFixed(2);
            settingsRef.current = state.confirmed;
            feeAmountRef.current = confirmedAmount;
            setSettings(state.confirmed);
            setFeeAmount(confirmedAmount);
            setSaveStatus(null);
            setError(exception instanceof Error ? exception.message : "No fue posible guardar la configuración de socios.");
          }
          return;
        }
      }
    } finally {
      state.running = false;
    }
  }

  if (!settings && !error) return null;

  return (
    <section className="app-card settings-detail-panel grid scroll-mt-6 gap-5 p-5" id="client-lifecycle-settings">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-xl font-semibold">Socios e inscripción</h2>
          <p className="mt-2 max-w-3xl text-sm text-[var(--brand-muted)]">
            Configura la inscripción y cómo GymFort gestiona periodos prolongados sin membresía.
          </p>
        </div>
        <SaveIndicator status={saveStatus} />
      </div>
      {error ? <AppNotice message={error} tone="error" /> : null}
      {settings ? (
        <div className="grid gap-4 lg:grid-cols-2">
          <article className="grid content-start gap-4 rounded-xl border bg-white p-4">
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="text-xs font-black uppercase tracking-[0.14em] text-[var(--brand-muted)]">Inscripción</p>
                <strong className="mt-1 block">Cobrar inscripción</strong>
                <p className="mt-1 text-xs leading-5 text-[var(--brand-muted)]">
                  Primera membresía y regreso después del periodo de inactividad.
                </p>
              </div>
              <ToggleSwitch checked={settings.registrationFeeEnabled} label="Cobrar inscripción" onChange={(checked) => toggle("registrationFeeEnabled", checked)} />
            </div>
            <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_9rem]">
              <label className="grid gap-1.5 text-xs font-semibold">
                Concepto
                <input className="app-input h-10" maxLength={120} onChange={(event) => editSettings({ ...settings, registrationFeeLabel: event.target.value }, true)} value={settings.registrationFeeLabel} />
              </label>
              <label className="grid gap-1.5 text-xs font-semibold">
                Importe ({settings.currency})
                <input className="app-input h-10" min="0" onChange={(event) => editFeeAmount(event.target.value)} step="0.01" type="number" value={feeAmount} />
              </label>
            </div>
            <p className="text-xs leading-5 text-[var(--brand-muted)]">Puedes preparar estos valores y mantener el cobro apagado.</p>
          </article>

          <article className="grid content-start gap-4 rounded-xl border bg-white p-4">
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="text-xs font-black uppercase tracking-[0.14em] text-[var(--brand-muted)]">Inactividad</p>
                <strong className="mt-1 block">Dar de baja automáticamente</strong>
                <p className="mt-1 text-xs leading-5 text-[var(--brand-muted)]">
                  Cambia al socio a INACTIVE sin eliminar expediente ni historial.
                </p>
              </div>
              <ToggleSwitch checked={settings.autoInactivationEnabled} label="Dar de baja automáticamente" onChange={(checked) => toggle("autoInactivationEnabled", checked)} />
            </div>
            <label className="grid max-w-xs gap-1.5 text-xs font-semibold">
              Periodo de inactividad
              <span className="flex items-center gap-2">
                <input className="app-input h-10 w-20" max="120" min="1" onChange={(event) => editSettings({ ...settings, inactivityMonths: Number(event.target.value) }, true)} type="number" value={settings.inactivityMonths} />
                <span className="font-normal text-[var(--brand-muted)]">meses sin membresía</span>
              </span>
            </label>
            <p className="text-xs leading-5 text-[var(--brand-muted)]">
              El mismo periodo determina cuándo vuelve a corresponder la inscripción. Puedes configurarlo y mantener la baja automática apagada.
            </p>
          </article>

        </div>
      ) : null}
    </section>
  );
}

function SaveIndicator({ status }: { status: SaveStatus }) {
  return (
    <span aria-live="polite" className="min-h-5 text-xs font-semibold text-[var(--brand-muted)]">
      {status === "saving" ? "Guardando..." : status === "saved" ? "Guardado" : null}
    </span>
  );
}

function validate(settings: ClientLifecycleSettings, feeAmount: string) {
  if (!Number.isInteger(settings.inactivityMonths) || settings.inactivityMonths < 1 || settings.inactivityMonths > 120) {
    return "El periodo de inactividad debe estar entre 1 y 120 meses.";
  }
  const amountCents = parseAmountCents(feeAmount);
  if (!Number.isFinite(amountCents) || amountCents < 0 || (settings.registrationFeeEnabled && amountCents <= 0)) {
    return "Captura un importe de inscripción válido.";
  }
  if (settings.registrationFeeEnabled && !settings.registrationFeeLabel.trim()) {
    return "Captura el concepto que aparecerá en la venta de inscripción.";
  }
  return null;
}

function toPayload(settings: ClientLifecycleSettings, feeAmount: string) {
  return {
    registrationFeeEnabled: settings.registrationFeeEnabled,
    registrationFeeCents: parseAmountCents(feeAmount),
    registrationFeeLabel: settings.registrationFeeLabel.trim(),
    autoInactivationEnabled: settings.autoInactivationEnabled,
    inactivityMonths: settings.inactivityMonths,
  };
}

function parseAmountCents(value: string) {
  return Math.round(Number(value.trim() || "0") * 100);
}
