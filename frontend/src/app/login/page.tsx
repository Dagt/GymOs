"use client";

import Link from "next/link";
import Image from "next/image";
import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useState } from "react";
import { getStoredSession, login, storeSession } from "@/lib/api";
import { getPostLoginPath } from "@/lib/session-routing";

export default function LoginPage() {
  const router = useRouter();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [checkingSession, setCheckingSession] = useState(true);

  useEffect(() => {
    const stored = getStoredSession();
    if (stored) {
      router.replace(getPostLoginPath(stored.user));
      return;
    }
    const timer = window.setTimeout(() => setCheckingSession(false), 0);
    return () => window.clearTimeout(timer);
  }, [router]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const session = await login(username, password);
      storeSession(session);
      router.replace(getPostLoginPath(session.user));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible iniciar sesion.");
    } finally {
      setLoading(false);
    }
  }

  if (checkingSession) {
    return (
      <main className="admin-empty-state">
        <section>
          <Image className="login-brand-logo" src="/gymfort_web_assets/gymfort-logo-navbar-96px.png" alt="GymFort" width={216} height={40} priority />
          <h1>Abriendo tu panel...</h1>
          <span>Tu sesion sigue activa en este navegador.</span>
        </section>
      </main>
    );
  }

  return (
    <main className="login-screen">
      <section className="login-card">
        <div className="login-hero">
          <p>Acceso seguro</p>
          <h1>Entra directo a tu panel.</h1>
          <span>
            GymFort identifica tu rol y te lleva a administración de plataforma, gerencia o recepción sin pasos extra.
          </span>
          <div>
            <small>Admin GymFort</small>
            <small>Dueño del gimnasio</small>
            <small>Gerente</small>
            <small>Recepcion y caja</small>
          </div>
        </div>

        <section className="login-form-panel">
          <div className="login-form-header">
            <div>
              <Image className="login-form-logo" src="/gymfort_web_assets/gymfort-logo-navbar-96px.png" alt="GymFort" width={178} height={33} />
              <h2>Iniciar sesion</h2>
              <span>Al validar tus credenciales entraras automaticamente al panel correcto.</span>
            </div>
            <Link href="/">Inicio</Link>
          </div>

          <form onSubmit={submit}>
            <label>
              Usuario o correo
              <input
                autoComplete="username"
                onChange={(event) => setUsername(event.target.value)}
                placeholder="tu@empresa.com"
                value={username}
              />
            </label>

            <label>
              Contrasena
              <input
                autoComplete="current-password"
                onChange={(event) => setPassword(event.target.value)}
                placeholder="Tu contrasena"
                type="password"
                value={password}
              />
            </label>

            {error ? <p className="login-error">{error}</p> : null}

            <button disabled={loading} type="submit">
              {loading ? "Validando acceso..." : "Entrar a GymFort"}
            </button>
            <Link className="text-center text-sm underline underline-offset-4" href="/reset-password">Olvidé mi contraseña</Link>
          </form>
        </section>
      </section>
    </main>
  );
}
