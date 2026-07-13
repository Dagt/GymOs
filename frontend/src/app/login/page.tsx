"use client";

import { FormEvent, useState } from "react";
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
    <main className="min-h-screen bg-[#f6f7f2] px-6 py-8 text-[#111815]">
      <section className="mx-auto grid max-w-6xl gap-8 lg:grid-cols-[1fr_1.1fr]">
        <div className="flex flex-col justify-between rounded-lg bg-[#13251c] p-6 text-white">
          <div>
            <p className="text-sm font-semibold uppercase tracking-wide text-[#9cc7ad]">
              GymOS
            </p>
            <h1 className="mt-4 text-4xl font-semibold leading-tight">
              Acceso operativo para recepcion y administracion.
            </h1>
          </div>
          <p className="mt-12 text-sm leading-6 text-[#dbe8df]">
            La sesion mantiene contexto de organizacion, permisos y sucursal activa
            para que cada flujo opere dentro de sus limites.
          </p>
        </div>

        <div className="rounded-lg border border-[#d8ddd2] bg-white p-6 shadow-sm">
          <h2 className="text-2xl font-semibold">Iniciar sesion</h2>
          <form className="mt-6 grid gap-4" onSubmit={submit}>
            <label className="grid gap-2 text-sm font-medium text-[#42514a]">
              Usuario
              <input
                className="h-12 rounded-md border border-[#cdd5ca] px-3 text-base outline-none transition focus:border-[#51735f]"
                autoComplete="username"
                value={username}
                onChange={(event) => setUsername(event.target.value)}
              />
            </label>

            <label className="grid gap-2 text-sm font-medium text-[#42514a]">
              Password
              <input
                className="h-12 rounded-md border border-[#cdd5ca] px-3 text-base outline-none transition focus:border-[#51735f]"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
            </label>

            {error ? (
              <p className="rounded-md border border-[#e5b7b7] bg-[#fff4f4] px-3 py-2 text-sm text-[#8c2f2f]">
                {error}
              </p>
            ) : null}

            <button
              className="h-12 rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white transition hover:bg-[#243d30] disabled:cursor-not-allowed disabled:opacity-60"
              disabled={loading}
              type="submit"
            >
              {loading ? "Validando..." : "Entrar"}
            </button>
          </form>

          {user ? (
            <section className="mt-6 rounded-md border border-[#d8ddd2] bg-[#f6f7f2] p-4">
              <p className="text-sm font-semibold text-[#51735f]">Sesion activa</p>
              <h3 className="mt-2 text-xl font-semibold">{user.fullName}</h3>
              <p className="mt-1 text-sm text-[#5d6b63]">{user.username}</p>
              <p className="mt-4 text-xs font-medium uppercase tracking-wide text-[#6f7a73]">
                Permisos cargados: {user.permissions.length}
              </p>
            </section>
          ) : null}
        </div>
      </section>
    </main>
  );
}
