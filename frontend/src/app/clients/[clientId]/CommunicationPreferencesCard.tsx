"use client";

import { useEffect, useState } from "react";
import {
  getCommunicationPreference,
  saveCommunicationPreference,
  type CommunicationPreference,
  type StoredSession,
} from "@/lib/api";

type ToggleKey = "emailOperationalEnabled" | "whatsappOperationalEnabled" | "emailMarketingEnabled" | "whatsappMarketingEnabled";

export function CommunicationPreferencesCard({ clientId, session }: { clientId: string; session: StoredSession }) {
  const [value, setValue] = useState<CommunicationPreference | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    getCommunicationPreference(session.accessToken, clientId)
      .then((result) => { if (active) setValue(result); })
      .catch((exception) => { if (active) setError(exception instanceof Error ? exception.message : "No fue posible cargar las preferencias."); });
    return () => { active = false; };
  }, [clientId, session.accessToken]);

  if (!value) return <section className="app-card p-5"><h2 className="text-lg font-semibold">Comunicaciones</h2><p className="mt-2 text-sm text-[var(--brand-muted)]">{error || "Cargando preferencias..."}</p></section>;

  async function save() {
    setSaving(true); setError(null);
    try {
      const marketing = value.emailMarketingEnabled || value.whatsappMarketingEnabled;
      setValue(await saveCommunicationPreference(session.accessToken, clientId, {
        emailOperationalEnabled: value.emailOperationalEnabled,
        whatsappOperationalEnabled: value.whatsappOperationalEnabled,
        emailMarketingEnabled: value.emailMarketingEnabled,
        whatsappMarketingEnabled: value.whatsappMarketingEnabled,
        consentVersion: marketing ? (value.consentVersion || "marketing-v1") : value.consentVersion,
        consented: marketing,
      }));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible guardar las preferencias.");
    } finally { setSaving(false); }
  }

  const toggle = (key: ToggleKey) => setValue((current) => current ? { ...current, [key]: !current[key] } : current);

  return (
    <section className="app-card p-5">
      <div><p className="app-eyebrow">Consentimiento</p><h2 className="mt-1 text-lg font-semibold">Comunicaciones al socio</h2><p className="mt-1 text-sm text-[var(--brand-muted)]">Los avisos operativos y el marketing se controlan por separado.</p></div>
      {error ? <p className="mt-3 rounded-md bg-red-50 p-3 text-sm text-red-800">{error}</p> : null}
      <div className="mt-4 grid gap-3 sm:grid-cols-2">
        <Toggle label="Correo operativo" checked={value.emailOperationalEnabled} onChange={() => toggle("emailOperationalEnabled")} />
        <Toggle label="WhatsApp operativo" checked={value.whatsappOperationalEnabled} onChange={() => toggle("whatsappOperationalEnabled")} />
        <Toggle label="Correo de marketing" checked={value.emailMarketingEnabled} onChange={() => toggle("emailMarketingEnabled")} />
        <Toggle label="WhatsApp de marketing" checked={value.whatsappMarketingEnabled} onChange={() => toggle("whatsappMarketingEnabled")} />
      </div>
      <button className="app-button mt-4" disabled={saving} onClick={() => void save()} type="button">{saving ? "Guardando..." : "Guardar preferencias"}</button>
    </section>
  );
}

function Toggle({ label, checked, onChange }: { label: string; checked: boolean; onChange: () => void }) {
  return <label className="flex items-center justify-between rounded-md border border-[var(--brand-line)] p-3 text-sm"><span>{label}</span><input type="checkbox" checked={checked} onChange={onChange} /></label>;
}
