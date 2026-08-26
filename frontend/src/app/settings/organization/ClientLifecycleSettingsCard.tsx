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
    if (!Number.isFinite(amountCents) || amountCents < 0) {
      setError("Captura un importe de inscripción válido.");
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const updated = await updateClientLifecycleSettings(accessToken, {
        registrationFeeEnabled: settings.registrationFeeEnabled,
        registrationFeeCents: amountCents,
        registrationFeeLabel: settings.registrationFeeLabel,
        registrationRechargeMonths: settings.registrationRechargeMonths,
        autoInactivationEnabled: settings.autoInactivationEnabled,
        inactivityMonths: settings.inactivityMonths,
      });
      setSettings(updated);
      setFeeAmount((updated.registrationFeeCents / 100).toFixed(2));
      setMessage("Reglas de inscripción e inactividad actualizadas.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible guardar las reglas de socios.");
    } finally {
      setSaving(false);
    }
  }

  if (!settings && !error) return null;

  return (
    <section className="app-card grid gap-5 p-5" id="client-lifecycle-settings">
      <div>
        <h2 className="text-xl font-semibold">Inscripción y socios inactivos</h2>
        <p className="mt-2 max-w-3xl text-sm text-[var(--brand-muted)]">
          Configura el cargo de entrada y la baja automática. La baja conserva ventas, pagos y auditoría; el socio puede reactivarse al regresar.
        </p>
      </div>
      {error ? <AppNotice message={error} tone="error" /> : null}
      {message ? <AppNotice message={message} tone="success" /> : null}
      {settings ? (
        <div className="grid gap-5 xl:grid-cols-2">
          <article className="grid gap-4 rounded-xl border bg-white p-5">
            <div className="flex items-start justify-between gap-4">
              <div><strong>Cobrar inscripción</strong><p className="mt-1 text-sm text-[var(--brand-muted)]">Se agrega automáticamente a la primera membresía y después del periodo de regreso.</p></div>
              <ToggleSwitch checked={settings.registrationFeeEnabled} label="Cobrar inscripción" onChange={(checked) => setSettings({ ...settings, registrationFeeEnabled: checked })} />
            </div>
            <label className="grid gap-2 text-sm font-semibold">Concepto<input className="app-input" maxLength={120} onChange={(event) => setSettings({ ...settings, registrationFeeLabel: event.target.value })} value={settings.registrationFeeLabel} /></label>
            <label className="grid gap-2 text-sm font-semibold">Importe ({settings.currency})<input className="app-input" min="0" onChange={(event) => setFeeAmount(event.target.value)} step="0.01" type="number" value={feeAmount} /></label>
            <label className="grid gap-2 text-sm font-semibold">Volver a cobrar después de<input className="app-input" max="120" min="1" onChange={(event) => setSettings({ ...settings, registrationRechargeMonths: Number(event.target.value) })} type="number" value={settings.registrationRechargeMonths} /><span className="text-xs font-normal text-[var(--brand-muted)]">Meses desde el fin de la última membresía.</span></label>
          </article>
          <article className="grid content-start gap-4 rounded-xl border bg-white p-5">
            <div className="flex items-start justify-between gap-4">
              <div><strong>Baja automática por inactividad</strong><p className="mt-1 text-sm text-[var(--brand-muted)]">Marca como inactivo al socio sin membresía vigente; no elimina su historial.</p></div>
              <ToggleSwitch checked={settings.autoInactivationEnabled} label="Baja automática" onChange={(checked) => setSettings({ ...settings, autoInactivationEnabled: checked })} />
            </div>
            <label className="grid gap-2 text-sm font-semibold">Dar de baja después de<input className="app-input" max="120" min="1" onChange={(event) => setSettings({ ...settings, inactivityMonths: Number(event.target.value) })} type="number" value={settings.inactivityMonths} /><span className="text-xs font-normal text-[var(--brand-muted)]">Meses desde la última vigencia. El valor habitual es 6.</span></label>
          </article>
          <div className="flex justify-end xl:col-span-2"><button className="app-button" disabled={saving} onClick={() => void save()} type="button">{saving ? "Guardando..." : "Guardar reglas de socios"}</button></div>
        </div>
      ) : null}
    </section>
  );
}
