"use client";

import Image from "next/image";
import Link from "next/link";
import { type FormEvent, useEffect, useState } from "react";
import { acceptPasswordRecovery, clearSession } from "@/lib/api";

export default function ResetPasswordPage() {
  const [token, setToken] = useState("");
  const [ready, setReady] = useState(false);
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [completed, setCompleted] = useState(false);

  useEffect(() => {
    // Fragments never reach the HTTP server or its access logs. Keep the token
    // only in component memory and remove it from the address bar/history.
    const timer = window.setTimeout(() => {
      setToken(new URLSearchParams(window.location.hash.slice(1)).get("token") ?? "");
      window.history.replaceState(null, "", window.location.pathname);
      setReady(true);
    }, 0);
    return () => window.clearTimeout(timer);
  }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError(null);
    if (password !== confirmation) { setError("Las contraseñas no coinciden."); return; }
    setSaving(true);
    try {
      await acceptPasswordRecovery(token, password);
      setToken(""); setPassword(""); setConfirmation(""); setCompleted(true);
      clearSession();
    } catch (exception) { setError(exception instanceof Error ? exception.message : "No fue posible cambiar la contraseña."); }
    finally { setSaving(false); }
  }

  return <main className="login-screen">
    <section className="login-card invitation-accept-card">
      <div className="login-hero"><p>Recuperación segura</p><h1>Vuelve a entrar a tu gimnasio.</h1><span>Tu contraseña es personal. No la compartas con nadie, tampoco con quien administra tu cuenta.</span></div>
      <section className="login-form-panel">
        <Image className="login-form-logo" src="/gymfort_web_assets/gymfort-logo-navbar-96px.png" alt="GymFort" width={178} height={33} priority />
        {!ready ? <p role="status">Preparando recuperación…</p> : completed ? <div className="grid gap-4" role="status">
          <h2>Contraseña actualizada</h2><p>Las sesiones anteriores de esta cuenta ya no son válidas. Inicia sesión con tu usuario o correo y la nueva contraseña.</p>
          <Link className="form-primary-button text-center" href="/login">Ir a iniciar sesión</Link>
        </div> : !token ? <div className="grid gap-4">
          <h2>¿Olvidaste tu contraseña?</h2>
          <p>Si eres gerente, coach o recepcionista, pide al dueño del gimnasio que abra <strong>Equipo y accesos → Recuperar contraseña</strong> en tu usuario. El gerente también puede ayudar a su personal autorizado.</p>
          <p>Recibirás un enlace de un solo uso para elegir la nueva contraseña. Si venció o ya se usó, solicita uno nuevo. Si acabas de recargar esta página, vuelve a abrir el enlace del correo.</p>
          <p className="text-sm text-[var(--brand-muted)]">Si eres dueño o administrador de plataforma, contacta al responsable de tu cuenta por el canal de soporte habitual.</p>
          <Link className="form-primary-button text-center" href="/login">Volver al inicio de sesión</Link>
        </div> : <form onSubmit={submit}>
          <h2>Elige tu nueva contraseña</h2>
          <p>Usa 12 o más caracteres, con mayúscula, minúscula, número y símbolo.</p>
          <label>Nueva contraseña<input autoComplete="new-password" minLength={12} maxLength={120} required type="password" value={password} onChange={(event) => setPassword(event.target.value)} /></label>
          <label>Confirmar contraseña<input autoComplete="new-password" minLength={12} maxLength={120} required type="password" value={confirmation} onChange={(event) => setConfirmation(event.target.value)} /></label>
          {error ? <p className="login-error" role="alert">{error}</p> : null}
          <button disabled={saving} type="submit">{saving ? "Guardando…" : "Guardar nueva contraseña"}</button>
          <Link className="text-sm underline" href="/login">Volver al inicio de sesión</Link>
        </form>}
      </section>
    </section>
  </main>;
}
