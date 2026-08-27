"use client";

import { useEffect, useState } from "react";
import { AppNotice } from "@/components/AppNotice";
import { ToggleSwitch } from "@/components/ToggleSwitch";
import {
  getClientLifecycleSettings,
  updateClientLifecycleSettings,
  type ClientLifecycleSettings,
} from "@/lib/api";

export function ClientLifecycleSettingsCard({ accessToken }: { accessToken: string }) {
  const [settings, setSettings] = useState<ClientLifecycleSettings | null>(null);
  const [feeAmount, setFeeAmount] = useState("0");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    getClientLifecycleSettings(accessToken)
      .then((loaded) => {
        if (!active) return;
        setSettings(loaded);
        setFeeAmount((loaded.registrationFeeCents / 100).toFixed(2));
      })
      .catch((exception) => {
        if (active) setError(exception instanceof Error ? exception.message : "No fue posible cargar las reglas de socios.");
      });
    return () => { active = false; };
  }, [accessToken]);

  async function save() {
    if (!settings) return;
    const amountCents = Math.round(Number(feeAmount) * 100);
    if (!Number.isInteger(settings.inactivityMonths)
        || settings.inactivityMonths < 1
        || settings.inactivityMonths > 120) {
      setError("El periodo de inactividad debe estar entre 1 y 120 meses.");
      return;
    }
    if (!Number.isFinite(amountCents) || (settings.registrationFeeEnabled && amountCents <= 0)) {
      setError("Captura un importe de inscripción válido.");
      return;
    }
    if (settings.registrationFeeEnabled && !settings.registrationFeeLabel.trim()) {
      setError("Captura el concepto que aparecerá en la venta de inscripción.");
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const updated = await updateClientLifecycleSettings(accessToken, {
        registrationFeeEnabled: settings.registrationFeeEnabled,
        registrationFeeCents: amountCents,
        registrationFeeLabel: settings.registrationFeeLabel.trim(),
        autoInactivationEnabled: settings.autoInactivationEnabled,
        inactivityMonths: settings.inactivityMonths,
      });
      setSettings(updated);
      setFeeAmount((updated.registrationFeeCents / 100).toFixed(2));
      setMessage("Configuración de socios actualizada.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible guardar las reglas de socios.");
    } finally {
      setSaving(false);
    }
  }

  if (!settings && !error) return null;

  return (
    <section className="app-card settings-detail-panel grid scroll-mt-6 gap-6 p-5" id="client-lifecycle-settings">
      <div>
        <h2 className="text-xl font-semibold">Socios e inscripción</h2>
        <p className="mt-2 max-w-3xl text-sm text-[var(--brand-muted)]">
          Configura la inscripción y cómo GymFort gestiona periodos prolongados sin membresía.
        </p>
      </div>
      {error ? <AppNotice message={error} tone="error" /> : null}
      {message ? <AppNotice message={message} tone="success" /> : null}
      {settings ? (
        <div className="grid gap-5">
          <article className="grid gap-3 rounded-xl border bg-white p-5">
            <label className="grid max-w-sm gap-2 text-sm font-semibold">
              Periodo de inactividad
              <span className="flex items-center gap-3">
                <input
                  className="app-input w-28"
                  max="120"
                  min="1"
                  onChange={(event) => setSettings({ ...settings, inactivityMonths: Number(event.target.value) })}
                  type="number"
                  value={settings.inactivityMonths}
                />
                <span className="font-normal">meses sin membresía</span>
              </span>
            </label>
            <p className="max-w-3xl text-sm text-[var(--brand-muted)]">
              GymFort considera que un socio ha entrado en periodo de inactividad cuando ha transcurrido este tiempo desde el final de su última vigencia.
            </p>
          </article>

          <article className="grid gap-4 rounded-xl border bg-white p-5">
            <p className="text-xs font-black uppercase tracking-[0.16em] text-[var(--brand-muted)]">Inscripción</p>
            <div className="flex items-start justify-between gap-4">
              <div>
                <strong>Cobrar inscripción</strong>
                <p className="mt-1 text-sm text-[var(--brand-muted)]">Se cobra en la primera membresía y nuevamente cuando el socio regresa después del periodo de inactividad.</p>
              </div>
              <ToggleSwitch checked={settings.registrationFeeEnabled} label="Cobrar inscripción" onChange={(checked) => setSettings({ ...settings, registrationFeeEnabled: checked })} />
            </div>
            {settings.registrationFeeEnabled ? (
              <div className="grid gap-4 md:grid-cols-2">
                <label className="grid gap-2 text-sm font-semibold">Concepto<input className="app-input" maxLength={120} onChange={(event) => setSettings({ ...settings, registrationFeeLabel: event.target.value })} value={settings.registrationFeeLabel} /></label>
                <label className="grid gap-2 text-sm font-semibold">Importe ({settings.currency})<input className="app-input" min="0.01" onChange={(event) => setFeeAmount(event.target.value)} step="0.01" type="number" value={feeAmount} /></label>
              </div>
            ) : null}
          </article>

          <article className="grid content-start gap-4 rounded-xl border bg-white p-5">
            <p className="text-xs font-black uppercase tracking-[0.16em] text-[var(--brand-muted)]">Gestión de inactividad</p>
            <div className="flex items-start justify-between gap-4">
              <div>
                <strong>Dar de baja automáticamente</strong>
                <p className="mt-1 text-sm text-[var(--brand-muted)]">Al alcanzar el periodo de inactividad, GymFort cambia al socio a INACTIVE. No elimina su expediente, ventas, pagos, membresías ni auditoría.</p>
              </div>
              <ToggleSwitch checked={settings.autoInactivationEnabled} label="Dar de baja automáticamente" onChange={(checked) => setSettings({ ...settings, autoInactivationEnabled: checked })} />
            </div>
          </article>
          <div className="flex justify-end"><button className="app-button" disabled={saving} onClick={() => void save()} type="button">{saving ? "Guardando..." : "Guardar configuración de socios"}</button></div>
        </div>
      ) : null}
    </section>
  );
}
