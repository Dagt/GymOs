"use client";

import { useState } from "react";
import { AppDrawer } from "@/components/AppDrawer";
import { requestPasswordRecovery, type InternalUserSummary, type PasswordRecoveryIssued } from "@/lib/api";

export function PasswordRecoveryDrawer({ user, accessToken, onClose }: {
  user: InternalUserSummary; accessToken: string; onClose: () => void;
}) {
  const [issued, setIssued] = useState<PasswordRecoveryIssued | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  async function generate() {
    setSaving(true); setError(null); setCopied(false);
    try { setIssued(await requestPasswordRecovery(accessToken, user.id)); }
    catch (exception) { setError(exception instanceof Error ? exception.message : "No fue posible generar el enlace."); }
    finally { setSaving(false); }
  }

  async function copy() {
    if (!issued) return;
    try { await navigator.clipboard.writeText(issued.resetUrl); setCopied(true); }
    catch { setError("No se pudo copiar automáticamente. Selecciona y copia el enlace del campo."); }
  }

  return <AppDrawer open title={`Recuperar acceso de ${user.fullName}`} description="El usuario elegirá su nueva contraseña mediante un enlace de un solo uso." onClose={() => { if (!saving) onClose(); }}>
    <div className="grid gap-4">
      <dl className="grid gap-2 rounded-xl border p-4 text-sm">
        <div><dt className="text-[var(--brand-muted)]">Usuario de acceso</dt><dd className="break-all font-semibold">{user.username}</dd></div>
        <div><dt className="text-[var(--brand-muted)]">Correo de entrega</dt><dd className="break-all">{user.email || "Sin correo registrado"}</dd></div>
      </dl>
      <p className="text-sm leading-6">Confirma la identidad de la persona antes de compartir el enlace. Generar uno nuevo invalida el anterior. La contraseña actual seguirá funcionando hasta que el usuario elija la nueva; entonces se cerrarán sus sesiones anteriores.</p>
      {error ? <p className="rounded-lg border border-red-300 bg-red-50 p-3 text-sm text-red-800" role="alert">{error}</p> : null}
      {issued ? <section className="grid gap-3" aria-live="polite">
        <p className="rounded-lg border p-3 text-sm" role="status">{
          issued.emailDeliveryStatus === "SENT" ? "Enlace enviado al correo del usuario. Revisa también la carpeta de spam."
            : issued.emailDeliveryStatus === "NO_EMAIL" ? "El usuario no tiene correo. Comparte el enlace por un canal seguro después de verificar su identidad."
              : issued.emailDeliveryStatus === "NOT_CONFIGURED" ? "El envío de correo no está configurado. El enlace se generó y puedes compartirlo de forma segura."
                : "No se pudo enviar el correo. El enlace sí se generó y puedes compartirlo de forma segura."}</p>
        <p className="text-sm">Vence: <strong>{new Date(issued.expiresAt).toLocaleString("es-MX", { timeZone: "America/Mexico_City", dateStyle: "medium", timeStyle: "short" })}</strong> · hora de Ciudad de México.</p>
        <label className="grid gap-1 text-sm">Enlace privado de recuperación
          <input className="app-input text-xs" readOnly value={issued.resetUrl} autoComplete="off" onFocus={(event) => event.target.select()} />
        </label>
        <button className="app-button secondary" type="button" onClick={() => void copy()}>{copied ? "Enlace copiado" : "Copiar enlace privado"}</button>
        <p className="text-xs text-[var(--brand-muted)]">El enlace sólo se muestra en este panel; no se guarda en el navegador ni en el historial del usuario.</p>
      </section> : null}
      <button className="app-button" disabled={saving} type="button" onClick={() => void generate()}>{saving ? "Generando enlace…" : issued ? "Generar otro enlace e invalidar el anterior" : "Generar y enviar enlace"}</button>
    </div>
  </AppDrawer>;
}
