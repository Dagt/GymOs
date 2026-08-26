"use client";

import { useState } from "react";
import { Check, CircleCheck, Copy, Mail, ShieldCheck, TriangleAlert, UserRound } from "lucide-react";
import { AppDrawer } from "@/components/AppDrawer";
import { requestPasswordRecovery, type InternalUserSummary, type PasswordRecoveryIssued } from "@/lib/api";

export function PasswordRecoveryDrawer({ user, accessToken, onClose }: {
  user: InternalUserSummary; accessToken: string; onClose: () => void;
}) {
  const [issued, setIssued] = useState<PasswordRecoveryIssued | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const [showLink, setShowLink] = useState(false);

  async function generate() {
    setSaving(true); setError(null); setCopied(false);
    try {
      const next = await requestPasswordRecovery(accessToken, user.id);
      setIssued(next);
      setShowLink(next.emailDeliveryStatus !== "SENT");
    }
    catch (exception) { setError(exception instanceof Error ? exception.message : "No fue posible generar el enlace."); }
    finally { setSaving(false); }
  }

  async function copy() {
    if (!issued) return;
    try { await navigator.clipboard.writeText(issued.resetUrl); setCopied(true); setError(null); }
    catch { setShowLink(true); setError("No se pudo copiar automáticamente. Selecciona y copia el enlace del campo."); }
  }

  const sent = issued?.emailDeliveryStatus === "SENT";

  return <AppDrawer open title="Restablecer contraseña" onClose={() => { if (!saving) onClose(); }}>
    <div className="password-recovery">
      <div className="password-recovery-person">
        <span className="password-recovery-avatar" aria-hidden="true"><UserRound size={22} /></span>
        <div><strong>{user.fullName}</strong><span>{user.email || "Sin correo registrado"}</span>
          {user.username !== user.email ? <small>Usuario: {user.username}</small> : null}
        </div>
      </div>
      {error ? <p className="rounded-lg border border-red-300 bg-red-50 p-3 text-sm text-red-800" role="alert">{error}</p> : null}
      {issued ? <section className="password-recovery-result">
        <div className={`password-recovery-status ${sent ? "sent" : "attention"}`} role="status">
          {sent ? <CircleCheck size={20} aria-hidden="true" /> : <TriangleAlert size={20} aria-hidden="true" />}
          <div>
            <strong>{sent ? "Enlace enviado" : "Enlace listo para compartir"}</strong>
            <p>{sent ? "Pídele que revise su correo y la carpeta de spam."
              : issued.emailDeliveryStatus === "NO_EMAIL" ? "El usuario no tiene correo. Comparte el enlace por un canal seguro."
                : issued.emailDeliveryStatus === "NOT_CONFIGURED" ? "El envío de correo no está configurado. Comparte el enlace por un canal seguro."
                  : "No se pudo enviar el correo. Puedes compartir el enlace por un canal seguro."}</p>
          </div>
        </div>
        <p className="password-recovery-expiry">Vence {new Date(issued.expiresAt).toLocaleString("es-MX", { timeZone: "America/Mexico_City", dateStyle: "medium", timeStyle: "short" })} (CDMX)</p>
        <div className="password-recovery-share">
          <button className={sent ? "app-button-secondary" : "app-button"} disabled={saving} type="button" onClick={() => void copy()}>
            {copied ? <Check size={16} aria-hidden="true" /> : <Copy size={16} aria-hidden="true" />}{copied ? "Enlace copiado" : "Copiar enlace privado"}
          </button>
          <button className="password-recovery-text-button" disabled={saving} aria-expanded={showLink} aria-controls="password-recovery-link" type="button" onClick={() => setShowLink(!showLink)}>{showLink ? "Ocultar enlace" : "Ver enlace"}</button>
        </div>
        {showLink ? <label className="grid gap-1 text-xs" id="password-recovery-link">Enlace privado de recuperación
          <input className="app-input text-xs" readOnly value={issued.resetUrl} autoComplete="off" onFocus={(event) => event.target.select()} />
        </label> : null}
      </section> : <>
        <p>{user.email ? "Enviaremos un enlace a su correo para que elija una nueva contraseña." : "Genera un enlace privado para que elija una nueva contraseña y compártelo por un canal seguro."}</p>
        <button className="app-button" disabled={saving} type="button" onClick={() => void generate()}>
          <Mail size={17} aria-hidden="true" />{saving ? "Generando enlace…" : user.email ? "Generar y enviar enlace" : "Generar enlace privado"}
        </button>
      </>}
      <div className="password-recovery-safety"><ShieldCheck size={17} aria-hidden="true" /><span>Verifica su identidad antes de compartir el enlace.</span></div>
      <details className="password-recovery-help">
        <summary>Cómo funciona la recuperación</summary>
        <ul>
          <li>El enlace es de un solo uso. Generar uno nuevo invalida el anterior.</li>
          <li>La contraseña actual sigue funcionando hasta que el usuario la cambie; entonces se cierran sus sesiones anteriores.</li>
          <li>El enlace sólo se muestra en este panel. No se guarda en el navegador ni en el historial del usuario.</li>
        </ul>
      </details>
      {issued ? <div className="password-recovery-reissue">
        <button className="password-recovery-text-button" disabled={saving} type="button" onClick={() => void generate()}>{saving ? "Generando enlace…" : "Generar otro enlace"}</button>
        <small>Invalida el anterior.</small>
      </div> : null}
    </div>
  </AppDrawer>;
}
