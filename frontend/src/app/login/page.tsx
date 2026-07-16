"use client";

import { FormEvent, useState } from "react";
import Link from "next/link";
import { login, storeSession, type CurrentUser } from "@/lib/api";

export default function LoginPage() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const session = await login(username, password);
      storeSession(session);
      setUser(session.user);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible iniciar sesion.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="app-shell text-[#111815]">
      <section className="mx-auto grid min-h-screen max-w-6xl items-center gap-6 py-8 lg:grid-cols-[0.9fr_1.1fr]">
        <div className="app-hero-panel p-6 sm:p-8">
          <div className="relative z-10">
            <p className="text-sm font-bold uppercase text-white/75">Acceso seguro</p>
            <h1 className="mt-3 text-4xl font-black leading-tight sm:text-5xl">
              Entra con tu usuario de gimnasio.
            </h1>
            <p className="mt-5 text-sm leading-6 text-white/80">
              GymOS carga tu organizacion, sucursal activa y permisos para mostrar solamente las acciones que
              corresponden a tu rol.
            </p>
            <div className="mt-6 grid gap-3 text-sm text-white/85">
              <span className="rounded-md border border-white/20 bg-white/10 px-3 py-2">Recepcion y caja</span>
              <span className="rounded-md border border-white/20 bg-white/10 px-3 py-2">Administracion y reportes</span>
              <span className="rounded-md border border-white/20 bg-white/10 px-3 py-2">Operacion multi-sucursal</span>
            </div>
          </div>
        </div>

        <section className="app-card p-6 shadow-sm">
          <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
            <div>
              <p className="app-eyebrow">GymOS</p>
              <h2 className="mt-2 text-2xl font-black">Iniciar sesion</h2>
              <p className="mt-2 text-sm leading-6 text-[#5d6b63]">
                Usa tus credenciales internas. Si no tienes sucursal activa, podras seleccionarla en recepcion.
              </p>
            </div>
            <Link className="app-button-secondary" href="/">
              Inicio
            </Link>
          </div>

          <form className="mt-6 grid gap-4" onSubmit={submit}>
            <label className="grid gap-2 text-sm font-bold text-[#42514a]">
              Usuario o correo
              <input
                className="h-12 rounded-md border border-[#cdd5ca] px-3 text-base outline-none transition"
                autoComplete="username"
                placeholder="ej. recepcion"
                value={username}
                onChange={(event) => setUsername(event.target.value)}
              />
            </label>

            <label className="grid gap-2 text-sm font-bold text-[#42514a]">
              Contrasena
              <input
                className="h-12 rounded-md border border-[#cdd5ca] px-3 text-base outline-none transition"
                type="password"
                autoComplete="current-password"
                placeholder="Tu contrasena"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
            </label>

            {error ? (
              <p className="rounded-md border border-[#e5b7b7] bg-[#fff4f4] px-3 py-2 text-sm text-[#8c2f2f]">
                {error}
              </p>
            ) : null}

            <button className="app-button h-12" disabled={loading} type="submit">
              {loading ? "Validando acceso..." : "Entrar a GymOS"}
            </button>
          </form>

          {user ? (
            <section className="mt-6 rounded-md border border-[#d8ddd2] bg-[#f6f7f2] p-4">
              <p className="app-eyebrow">Sesion activa</p>
              <h3 className="mt-2 text-xl font-black">{user.fullName}</h3>
              <p className="mt-1 text-sm text-[#5d6b63]">{user.username}</p>
              <p className="mt-4 text-xs font-bold uppercase text-[#6f7a73]">
                Permisos cargados: {user.permissions.length}
              </p>
              <Link className="mt-4 app-button" href="/reception">
                Continuar a recepcion
              </Link>
            </section>
          ) : null}
        </section>
      </section>
    </main>
  );
}
