"use client";

import { useEffect, useState } from "react";
import { AppNotice } from "@/components/AppNotice";
import {
  getEmailRecipientPreference,
  updateEmailRecipientPreference,
  type EmailRecipientPreference,
} from "@/lib/api";

export function EmailPreferencesCard({
  accessToken,
  recipient,
}: {
  accessToken: string;
  recipient: string;
}) {
  const [preference, setPreference] = useState<EmailRecipientPreference | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    void getEmailRecipientPreference(accessToken, recipient)
      .then((loaded) => {
        if (active) {
          setError(null);
          setPreference(loaded);
        }
      })
      .catch((exception) => {
        if (active) {
          setError(exception instanceof Error ? exception.message : "No fue posible cargar las preferencias de correo.");
        }
      });
    return () => {
      active = false;
    };
  }, [accessToken, recipient]);

  function setEmailEnabled(enabled: boolean) {
    setPreference((current) => current ? {
      ...current,
      emailEnabled: enabled,
      marketingEnabled: enabled ? current.marketingEnabled : false,
    } : current);
  }

  async function save() {
    if (!preference) return;
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      await updateEmailRecipientPreference(accessToken, {
        recipient,
        emailEnabled: preference.emailEnabled,
        marketingEnabled: preference.emailEnabled && preference.marketingEnabled,
      });
      setPreference({
        ...preference,
        marketingEnabled: preference.emailEnabled && preference.marketingEnabled,
        updatedAt: new Date().toISOString(),
      });
      setMessage("Preferencias de correo actualizadas.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible guardar las preferencias.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="app-card grid gap-4 p-5">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="app-eyebrow">Comunicaciones</p>
          <h2 className="mt-1 text-xl font-semibold">Preferencias de correo</h2>
          <p className="mt-1 text-sm text-[var(--brand-muted)]">
            Define si este socio puede recibir avisos operativos y mensajes comerciales en {recipient}.
          </p>
        </div>
        {preference?.updatedAt ? (
          <span className="rounded-full bg-[#eef1f3] px-3 py-1 text-xs text-[#52615a]">
            Preferencia registrada
          </span>
        ) : null}
      </div>

      {!preference && !error ? (
        <p className="text-sm text-[var(--brand-muted)]">Cargando preferencias...</p>
      ) : null}

      {preference ? (
        <div className="grid gap-2 md:grid-cols-2">
          <label className="flex cursor-pointer gap-3 rounded-md border border-[var(--brand-line)] p-4">
            <input
              checked={preference.emailEnabled}
              onChange={(event) => setEmailEnabled(event.target.checked)}
              type="checkbox"
            />
            <span>
              <strong className="block text-sm">Correos operativos</strong>
              <small className="mt-1 block text-[var(--brand-muted)]">
                Renovaciones, pagos, accesos y avisos del servicio.
              </small>
            </span>
          </label>
          <label className={`flex gap-3 rounded-md border border-[var(--brand-line)] p-4 ${preference.emailEnabled ? "cursor-pointer" : "cursor-not-allowed opacity-60"}`}>
            <input
              checked={preference.marketingEnabled}
              disabled={!preference.emailEnabled}
              onChange={(event) => setPreference({ ...preference, marketingEnabled: event.target.checked })}
              type="checkbox"
            />
            <span>
              <strong className="block text-sm">Correos comerciales</strong>
              <small className="mt-1 block text-[var(--brand-muted)]">
                Promociones y campañas; requiere que el correo esté habilitado.
              </small>
            </span>
          </label>
        </div>
      ) : null}

      {error ? <AppNotice message={error} tone="error" /> : null}
      {message ? <AppNotice message={message} tone="success" /> : null}
      {preference ? (
        <button className="app-button w-fit" disabled={saving} onClick={() => void save()} type="button">
          {saving ? "Guardando..." : "Guardar preferencias"}
        </button>
      ) : null}
    </section>
  );
}
