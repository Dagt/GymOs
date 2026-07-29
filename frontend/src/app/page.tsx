"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { clearSession, getStoredSession, type StoredSession } from "@/lib/api";
import { getPostLoginPath } from "@/lib/session-routing";

const publicHighlights = [
  "Control de socios, pagos y membresías",
  "Reportes para administración y gerencia",
  "Marca configurable por gimnasio",
];

export default function Home() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setSession(getStoredSession());
      setReady(true);
    }, 0);
    return () => window.clearTimeout(timer);
  }, []);

  function logout() {
    clearSession();
    setSession(null);
  }

  const sessionDestination = session ? getPostLoginPath(session.user) : "/login";

  return (
    <main className="home-screen">
      <section className="home-panel public-home-panel">
        <header className="home-header">
          <div className="home-brand">
            <span>G</span>
            <strong>GymOS</strong>
          </div>
          <nav aria-label="Acceso a GymOS">
            {ready && session ? (
              <>
                <span className="home-session-pill">{session.user.fullName}</span>
                <Link className="home-primary-link" href={sessionDestination}>
                  Continuar al panel
                </Link>
                <button className="home-plain-button" onClick={logout} type="button">
                  Salir
                </button>
              </>
            ) : (
              <Link className="home-primary-link" href="/login">
                Iniciar sesión
              </Link>
            )}
          </nav>
        </header>

        <section className="public-home-hero" aria-labelledby="home-title">
          <div>
            <p className="home-kicker">Sistema para gimnasios</p>
            <h1 id="home-title">GymOS</h1>
            <p>
              Plataforma interna para administrar la operación del gimnasio. Para entrar a caja, socios,
              reportes o configuración necesitas iniciar sesión con una cuenta autorizada.
            </p>
            <div className="home-actions">
              {ready && session ? (
                <Link className="home-cta" href={sessionDestination}>
                  Entrar al panel
                </Link>
              ) : (
                <Link className="home-cta" href="/login">
                  Iniciar sesión
                </Link>
              )}
            </div>
          </div>

          <aside className="public-login-card" aria-label="Estado de acceso">
            <p>{ready && session ? "Sesión activa" : "Acceso requerido"}</p>
            <h2>{ready && session ? "Ya puedes continuar" : "Entra para operar"}</h2>
            <span>
              {ready && session
                ? "Tu sesión sigue guardada en este navegador."
                : "No se muestran acciones internas hasta validar usuario, rol y sucursal."}
            </span>
          </aside>
        </section>

        <section className="public-home-highlights" aria-label="Qué incluye GymOS">
          {publicHighlights.map((item) => (
            <span key={item}>{item}</span>
          ))}
        </section>
      </section>
    </main>
  );
}

