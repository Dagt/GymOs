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

  function applyConfiguration(loaded: EmailNotificationConfiguration) {
    setConfiguration(loaded);
    setDraft({
      emailEnabled: loaded.emailEnabled,
      providerKey: loaded.providerKey,
      fromEmail: loaded.fromEmail ?? loaded.preferredFromEmail ?? "",
      fromName: loaded.fromName ?? "",
      replyTo: loaded.replyTo ?? "",
    });
  }

  useEffect(() => {
    let active = true;
    void getEmailNotificationConfiguration(accessToken)
      .then((loaded) => {
        if (active) applyConfiguration(loaded);
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
      applyConfiguration(updated);
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
      const delivered = await sendTestEmail(accessToken, testRecipient);
      const refreshed = await getEmailNotificationConfiguration(accessToken);
      applyConfiguration(refreshed);
      if (delivered.status === "SENT") {
        setMessage(`${refreshed.providerName} aceptó el correo de prueba. El canal ya está verificado.`);
      } else {
        setError(delivered.errorMessage
          ?? `La prueba quedó en estado ${delivered.status}. Revisa la entrega en Automatizaciones.`);
      }
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible realizar la prueba.");
    } finally {
      setSaving(false);
    }
  }

  if (!configuration) {
    return <section className="app-card settings-detail-panel p-5 text-sm text-[var(--brand-muted)]">Cargando configuración de correo...</section>;
  }

  return (
    <section className="app-card settings-detail-panel grid scroll-mt-6 gap-5 p-5" id="email-settings">
      <div className="email-settings-heading">
        <div>
          <p className="app-eyebrow">Canal de salida</p>
          <h2 className="mt-2 text-xl font-semibold">Correo real con {configuration.providerName}</h2>
          <p className="mt-2 text-sm text-[var(--brand-muted)]">
            Las credenciales viven únicamente en el servidor. Aquí defines la identidad visible del gimnasio y validas una entrega real.
          </p>
        </div>
        <strong className={`email-provider-status ${configuration.ready ? "ready" : configuration.providerAvailable ? "pending" : "offline"}`}>
          {configuration.ready ? "Listo para enviar" : configuration.providerAvailable ? "Falta habilitar" : "Servidor sin configurar"}
        </strong>
      </div>

      <div className="email-readiness-grid" aria-label="Estado del canal de correo">
        <article className={configuration.providerAvailable ? "complete" : ""}>
          <span>1</span>
          <div><strong>Servidor</strong><small>{configuration.providerAvailable ? `${configuration.providerName} configurado` : "Agrega credenciales SMTP al backend"}</small></div>
        </article>
        <article className={configuration.ready ? "complete" : ""}>
          <span>2</span>
          <div><strong>Remitente</strong><small>{configuration.ready ? configuration.fromEmail : "Guarda y habilita el remitente"}</small></div>
        </article>
        <article className={configuration.lastVerifiedAt ? "complete" : ""}>
          <span>3</span>
          <div><strong>Entrega real</strong><small>{configuration.lastVerifiedAt ? `Verificada ${formatDateTime(configuration.lastVerifiedAt)}` : "Envía una prueba a tu bandeja"}</small></div>
        </article>
      </div>

      {!configuration.providerAvailable ? (
        <div className="email-server-help">
          <strong>Configuración pendiente en el backend</strong>
          <p>Define GYMOS_SMTP_ENABLED, GYMOS_SMTP_HOST, GYMOS_SMTP_USERNAME, GYMOS_SMTP_PASSWORD y reinicia el contenedor backend.</p>
        </div>
      ) : null}

      <form className="grid gap-4 md:grid-cols-2" onSubmit={save}>
        <label className="grid gap-2 text-sm font-medium">
          Nombre del remitente
          <input className="app-input" maxLength={160} onChange={(event) => setDraft({ ...draft, fromName: event.target.value })} value={draft.fromName} />
        </label>
        <label className="grid gap-2 text-sm font-medium">
          Correo remitente
          {configuration.fromEmailRestricted ? (
            <select className="app-input" onChange={(event) => setDraft({ ...draft, fromEmail: event.target.value })} value={draft.fromEmail}>
              {configuration.allowedFromEmails.map((address) => <option key={address} value={address}>{address}</option>)}
            </select>
          ) : (
            <input
              className="app-input"
              maxLength={180}
              onChange={(event) => setDraft({ ...draft, fromEmail: event.target.value })}
              placeholder={configuration.preferredFromEmail ?? "notificaciones@tu-dominio.com"}
              type="email"
              value={draft.fromEmail}
            />
          )}
          {configuration.fromEmailRestricted ? <small>Zoho sólo permite el buzón autenticado o alias autorizados en el servidor.</small> : null}
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
          <span><strong>Habilitar entregas por correo</strong><small className="mt-1 block">Las automatizaciones activas podrán enviar mensajes a los socios.</small></span>
        </label>
        <button className="app-button w-fit" disabled={saving || (draft.emailEnabled && !draft.fromEmail)} type="submit">
          {saving ? "Guardando..." : "Guardar remitente"}
        </button>
      </form>

      {configuration.ready ? (
        <div className="email-test-panel">
          <div><strong>Validar con una entrega real</strong><p>Usa una bandeja a la que tengas acceso. La respuesta confirmará si {configuration.providerName} aceptó el mensaje.</p></div>
          <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_auto]">
            <input aria-label="Destinatario de prueba" className="app-input" onChange={(event) => setTestRecipient(event.target.value)} placeholder="tu-correo@dominio.com" type="email" value={testRecipient} />
            <button className="app-button secondary" disabled={saving || !testRecipient} onClick={() => void test()} type="button">Enviar y verificar</button>
          </div>
        </div>
      ) : null}

      {error ? <AppNotice message={error} tone="error" /> : null}
      {message ? <AppNotice message={message} tone="success" /> : null}
    </section>
  );
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("es-MX", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}
