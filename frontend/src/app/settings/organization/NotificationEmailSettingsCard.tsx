"use client";

import { FormEvent, useEffect, useState } from "react";
import { AppNotice } from "@/components/AppNotice";
import {
  getEmailNotificationConfiguration,
  sendTestEmail,
  updateEmailNotificationConfiguration,
  type EmailNotificationConfiguration,
} from "@/lib/api";

export function NotificationEmailSettingsCard({ accessToken }: { accessToken: string }) {
  const [configuration, setConfiguration] = useState<EmailNotificationConfiguration | null>(null);
  const [draft, setDraft] = useState({
    emailEnabled: false,
    providerKey: "SMTP",
    fromEmail: "",
    fromName: "",
    replyTo: "",
  });
  const [testRecipient, setTestRecipient] = useState("");
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    void getEmailNotificationConfiguration(accessToken)
      .then((loaded) => {
        if (!active) return;
        setConfiguration(loaded);
        setDraft({
          emailEnabled: loaded.emailEnabled,
          providerKey: loaded.providerKey,
          fromEmail: loaded.fromEmail ?? "",
          fromName: loaded.fromName ?? "",
          replyTo: loaded.replyTo ?? "",
        });
      })
      .catch((exception) => {
        if (active) setError(exception instanceof Error ? exception.message : "No fue posible cargar el correo.");
      });
    return () => {
      active = false;
    };
  }, [accessToken]);

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const updated = await updateEmailNotificationConfiguration(accessToken, {
        enabled: draft.emailEnabled,
        providerKey: draft.providerKey,
        fromEmail: draft.fromEmail,
        fromName: draft.fromName,
        replyTo: draft.replyTo,
      });
      setConfiguration(updated);
      setDraft({
        emailEnabled: updated.emailEnabled,
        providerKey: updated.providerKey,
        fromEmail: updated.fromEmail ?? "",
        fromName: updated.fromName ?? "",
        replyTo: updated.replyTo ?? "",
      });
      setMessage(updated.ready ? "Correo habilitado y listo para entregar." : "Configuración del remitente guardada.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible guardar el correo.");
    } finally {
      setSaving(false);
    }
  }

  async function test() {
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const queued = await sendTestEmail(accessToken, testRecipient);
      setMessage(`Correo de prueba en cola (${queued.id.slice(0, 8)}).`);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible encolar la prueba.");
    } finally {
      setSaving(false);
    }
  }

  if (!configuration) {
    return <section className="app-card settings-detail-panel p-5 text-sm text-[var(--brand-muted)]">Cargando configuración de correo...</section>;
  }

  return (
    <section className="app-card settings-detail-panel grid scroll-mt-6 gap-5 p-5" id="email-settings">
      <h2 className="text-xl font-semibold">Correo electrónico</h2>

      <strong className={`w-fit rounded-full border px-3 py-1 text-sm ${configuration.providerAvailable ? "border-[#b9d8c6] bg-[#f2faf5] text-[#31543d]" : "border-[#ead6a3] bg-[#fffaf0] text-[#745b00]"}`}>
        {configuration.providerAvailable ? "Proveedor disponible" : "Proveedor no disponible"}
      </strong>

      <form className="grid gap-4 md:grid-cols-2" onSubmit={save}>
        <label className="grid gap-2 text-sm font-medium">
          Nombre del remitente
          <input className="app-input" maxLength={160} onChange={(event) => setDraft({ ...draft, fromName: event.target.value })} value={draft.fromName} />
        </label>
        <label className="grid gap-2 text-sm font-medium">
          Correo remitente
          <input className="app-input" maxLength={180} onChange={(event) => setDraft({ ...draft, fromEmail: event.target.value })} type="email" value={draft.fromEmail} />
        </label>
        <label className="grid gap-2 text-sm font-medium">
          Responder a (opcional)
          <input className="app-input" maxLength={180} onChange={(event) => setDraft({ ...draft, replyTo: event.target.value })} type="email" value={draft.replyTo} />
        </label>
        <label className="flex items-start gap-3 rounded-md border border-[var(--brand-line)] p-4 md:col-span-2">
          <input
            checked={draft.emailEnabled}
            disabled={!configuration.providerAvailable && !draft.emailEnabled}
            onChange={(event) => setDraft({ ...draft, emailEnabled: event.target.checked })}
            type="checkbox"
          />
          <strong>Habilitar entregas por correo</strong>
        </label>
        <button className="app-button w-fit" disabled={saving || (draft.emailEnabled && !draft.fromEmail)} type="submit">
          {saving ? "Guardando..." : "Guardar remitente"}
        </button>
      </form>

      {configuration.ready ? (
        <div className="grid gap-3 border-t border-[var(--brand-line)] pt-5 sm:grid-cols-[minmax(0,1fr)_auto]">
          <input aria-label="Destinatario de prueba" className="app-input" onChange={(event) => setTestRecipient(event.target.value)} type="email" value={testRecipient} />
          <button className="app-button secondary" disabled={saving || !testRecipient} onClick={() => void test()} type="button">Enviar prueba</button>
        </div>
      ) : null}

      {error ? <AppNotice message={error} tone="error" /> : null}
      {message ? <AppNotice message={message} tone="success" /> : null}
    </section>
  );
}
