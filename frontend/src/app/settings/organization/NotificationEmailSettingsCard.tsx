"use client";

import { FormEvent, useEffect, useState } from "react";
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
    return <section className="app-card p-5 text-sm text-[var(--brand-muted)]">Cargando configuración de correo...</section>;
  }

  return (
    <section className="app-card grid scroll-mt-6 gap-5 p-5" id="email-settings">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
        <p className="app-eyebrow">Notificaciones</p>
        <h2 className="text-xl font-semibold">Correo electrónico</h2>
        <p className="mt-2 text-sm text-[var(--brand-muted)]">
          Esta sección tiene dos pasos: el equipo técnico conecta al proveedor y después el gimnasio define el remitente. Las automatizaciones internas no necesitan correo.
        </p>
        </div>
        <a className="app-button-secondary" href="/automations">Ir a automatizaciones</a>
      </div>

      <div className={`rounded-md border p-4 text-sm ${configuration.providerAvailable ? "border-[#b9d8c6] bg-[#f2faf5]" : "border-[#ead6a3] bg-[#fffaf0]"}`}>
        <strong>{configuration.providerAvailable ? "Paso técnico completado" : "Paso técnico pendiente"}</strong>
        <p className="mt-1 text-[var(--brand-muted)]">
          {configuration.providerAvailable
            ? "El proveedor ya está conectado. Completa el remitente, habilita las entregas y envía una prueba."
            : "Pide al responsable técnico que conecte el proveedor SMTP. Mientras tanto puedes preparar el nombre y correo que verán los socios."}
        </p>
        {!configuration.providerAvailable ? (
          <details className="mt-3 text-xs text-[var(--brand-muted)]">
            <summary className="cursor-pointer font-semibold text-[#42514a]">Datos para el responsable técnico</summary>
            <p className="mt-2">Debe configurar en el servidor: GYMOS_SMTP_ENABLED, GYMOS_SMTP_HOST, GYMOS_SMTP_USERNAME y GYMOS_SMTP_PASSWORD.</p>
          </details>
        ) : null}
      </div>

      <form className="grid gap-4 md:grid-cols-2" onSubmit={save}>
        <div className="rounded-md border border-[var(--brand-line)] bg-[var(--brand-surface)] p-4 text-sm">
          <span className="text-xs text-[var(--brand-muted)]">Tipo de conexión</span>
          <strong className="mt-1 block">SMTP estándar</strong>
          <span className="mt-1 block text-xs text-[var(--brand-muted)]">Compatible con el proveedor que elijas.</span>
        </div>
        <label className="grid gap-2 text-sm font-medium">
          Nombre del remitente
          <input className="app-input" maxLength={160} onChange={(event) => setDraft({ ...draft, fromName: event.target.value })} placeholder="Mi gimnasio" value={draft.fromName} />
          <small className="font-normal text-[var(--brand-muted)]">Ejemplo: Appex Fitness.</small>
        </label>
        <label className="grid gap-2 text-sm font-medium">
          Correo remitente
          <input className="app-input" maxLength={180} onChange={(event) => setDraft({ ...draft, fromEmail: event.target.value })} placeholder="notificaciones@gimnasio.com" type="email" value={draft.fromEmail} />
        </label>
        <label className="grid gap-2 text-sm font-medium">
          Responder a (opcional)
          <input className="app-input" maxLength={180} onChange={(event) => setDraft({ ...draft, replyTo: event.target.value })} placeholder="recepcion@gimnasio.com" type="email" value={draft.replyTo} />
        </label>
        <label className="flex items-start gap-3 rounded-md border border-[var(--brand-line)] p-4 md:col-span-2">
          <input
            checked={draft.emailEnabled}
            disabled={!configuration.providerAvailable && !draft.emailEnabled}
            onChange={(event) => setDraft({ ...draft, emailEnabled: event.target.checked })}
            type="checkbox"
          />
          <span><strong>Habilitar entregas por correo</strong><small className="mt-1 block">Las automatizaciones podrán escribir al correo registrado del socio.</small></span>
        </label>
        <button className="app-button w-fit" disabled={saving || (draft.emailEnabled && !draft.fromEmail)} type="submit">
          {saving ? "Guardando..." : "Guardar remitente"}
        </button>
      </form>

      {configuration.ready ? (
        <div className="grid gap-3 border-t border-[var(--brand-line)] pt-5 sm:grid-cols-[minmax(0,1fr)_auto]">
          <input className="app-input" onChange={(event) => setTestRecipient(event.target.value)} placeholder="correo@ejemplo.com" type="email" value={testRecipient} />
          <button className="app-button secondary" disabled={saving || !testRecipient} onClick={() => void test()} type="button">Enviar prueba</button>
        </div>
      ) : null}

      {error ? <p className="form-message error" role="alert">{error}</p> : null}
      {message ? <p className="form-message success" role="status">{message}</p> : null}
    </section>
  );
}
