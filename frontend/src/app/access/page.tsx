"use client";

import Link from "next/link";
import { FormEvent, useEffect, useMemo, useState } from "react";
import {
  createTemporaryPass,
  getAccessDevices,
  getAccessLogs,
  getStoredSession,
  manualOpenAccess,
  requestAccess,
  searchClients,
  type AccessDecision,
  type AccessDevice,
  type AccessLog,
  type ClientSummary,
  type StoredSession,
} from "@/lib/api";

export default function AccessPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [devices, setDevices] = useState<AccessDevice[]>([]);
  const [logs, setLogs] = useState<AccessLog[]>([]);
  const [clients, setClients] = useState<ClientSummary[]>([]);
  const [selectedClientId, setSelectedClientId] = useState("");
  const [query, setQuery] = useState("");
  const [identifier, setIdentifier] = useState("");
  const [deviceCode, setDeviceCode] = useState("");
  const [manualReason, setManualReason] = useState("");
  const [temporaryReason, setTemporaryReason] = useState("");
  const [temporaryHours, setTemporaryHours] = useState(4);
  const [temporaryMaxUses, setTemporaryMaxUses] = useState(1);
  const [lastDecision, setLastDecision] = useState<AccessDecision | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let active = true;

    async function load() {
      const stored = getStoredSession();
      if (!active) {
        return;
      }
      if (!stored) {
        setLoading(false);
        return;
      }
      setSession(stored);
      try {
        const [loadedDevices, loadedLogs, loadedClients] = await Promise.all([
          getAccessDevices(stored.accessToken),
          getAccessLogs(stored.accessToken),
          searchClients(stored.accessToken, "", "ACTIVE"),
        ]);
        if (!active) {
          return;
        }
        setDevices(loadedDevices);
        setLogs(loadedLogs);
        setClients(loadedClients);
        setDeviceCode(loadedDevices.find((device) => device.status === "ACTIVE")?.deviceCode ?? "");
      } catch (exception) {
        if (active) {
          setError(exception instanceof Error ? exception.message : "No fue posible cargar accesos.");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void load();

    return () => {
      active = false;
    };
  }, []);

  const selectedClient = useMemo(
    () => clients.find((client) => client.id === selectedClientId) ?? null,
    [clients, selectedClientId],
  );

  async function refreshLogs(activeSession = session) {
    if (!activeSession) {
      return;
    }
    setLogs(await getAccessLogs(activeSession.accessToken));
  }

  async function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const result = await searchClients(session.accessToken, query, "ACTIVE");
      setClients(result);
      if (result.length === 1) {
        setSelectedClientId(result[0].id);
      }
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible buscar clientes.");
    } finally {
      setBusy(false);
    }
  }

  async function evaluateAccess() {
    if (!session) {
      return;
    }
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const decision = await requestAccess(session.accessToken, {
        clientId: selectedClientId || null,
        identifier,
        deviceCode,
      });
      setLastDecision(decision);
      setMessage(decision.decision === "GRANTED" ? "Acceso concedido." : "Acceso denegado.");
      await refreshLogs(session);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible evaluar acceso.");
    } finally {
      setBusy(false);
    }
  }

  async function openManually() {
    if (!session || !manualReason.trim()) {
      return;
    }
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const decision = await manualOpenAccess(session.accessToken, {
        clientId: selectedClientId || null,
        deviceCode,
        reason: manualReason,
      });
      setLastDecision(decision);
      setManualReason("");
      setMessage("Apertura manual registrada.");
      await refreshLogs(session);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible abrir manualmente.");
    } finally {
      setBusy(false);
    }
  }

  async function createPass() {
    if (!session || !selectedClientId || !temporaryReason.trim()) {
      return;
    }
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const expiresAt = new Date(Date.now() + temporaryHours * 60 * 60 * 1000).toISOString();
      const pass = await createTemporaryPass(session.accessToken, {
        clientId: selectedClientId,
        expiresAt,
        maxUses: temporaryMaxUses,
        reason: temporaryReason,
      });
      setTemporaryReason("");
      setMessage(`Pase temporal creado para ${pass.clientName}.`);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear pase temporal.");
    } finally {
      setBusy(false);
    }
  }

  if (loading) {
    return (
      <main className="grid min-h-screen place-items-center bg-[#f6f7f2] px-6 text-[#111815]">
        <p className="rounded-md border border-[#d8ddd2] bg-white px-4 py-3 text-sm text-[#42514a]">
          Cargando accesos...
        </p>
      </main>
    );
  }

  if (!session) {
    return (
      <main className="grid min-h-screen place-items-center bg-[#f6f7f2] px-6 text-[#111815]">
        <section className="w-full max-w-md rounded-lg border border-[#d8ddd2] bg-white p-6 shadow-sm">
          <p className="text-sm font-semibold uppercase tracking-wide text-[#51735f]">GymOS</p>
          <h1 className="mt-3 text-2xl font-semibold">Necesitas iniciar sesion</h1>
          <Link
            className="mt-5 inline-flex h-11 items-center justify-center rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white transition hover:bg-[#243d30]"
            href="/login"
          >
            Iniciar sesion
          </Link>
        </section>
      </main>
    );
  }

  return (
    <main className="min-h-screen bg-[#f6f7f2] text-[#111815]">
      <header className="border-b border-[#d8ddd2] bg-white">
        <div className="mx-auto flex max-w-7xl flex-col gap-4 px-6 py-5 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <p className="text-sm font-semibold uppercase tracking-wide text-[#51735f]">Operacion</p>
            <h1 className="mt-1 text-3xl font-semibold">Control de acceso</h1>
            <p className="mt-1 text-sm text-[#5d6b63]">
              {session.user.fullName} · {session.user.branchId ? "Sucursal activa" : "Sin sucursal"}
            </p>
          </div>
          <Link
            className="inline-flex h-10 items-center justify-center rounded-md border border-[#cdd5ca] bg-white px-4 text-sm font-semibold text-[#42514a] transition hover:border-[#51735f]"
            href="/reception"
          >
            Recepcion
          </Link>
        </div>
      </header>

      <section className="mx-auto grid max-w-7xl gap-6 px-6 py-6 lg:grid-cols-[0.95fr_1.4fr]">
        <aside className="grid gap-6">
          <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
            <h2 className="text-xl font-semibold">Dispositivo</h2>
            <label className="mt-4 grid gap-2 text-sm font-medium text-[#42514a]">
              Puerta
              <select
                className="h-10 rounded-md border border-[#cdd5ca] bg-white px-3 text-sm"
                onChange={(event) => setDeviceCode(event.target.value)}
                value={deviceCode}
              >
                {devices.map((device) => (
                  <option key={device.id} value={device.deviceCode}>
                    {device.name} · {device.provider}
                  </option>
                ))}
              </select>
            </label>
          </section>

          <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
            <h2 className="text-xl font-semibold">Cliente</h2>
            <form className="mt-4 flex gap-2" onSubmit={search}>
              <input
                className="h-10 min-w-0 flex-1 rounded-md border border-[#cdd5ca] px-3 text-sm"
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Nombre, email o telefono"
                value={query}
              />
              <button
                className="h-10 rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white transition hover:bg-[#243d30] disabled:opacity-60"
                disabled={busy}
                type="submit"
              >
                Buscar
              </button>
            </form>

            <div className="mt-4 grid gap-2">
              {clients.slice(0, 6).map((client) => (
                <button
                  className={`rounded-md border px-3 py-3 text-left text-sm transition ${
                    client.id === selectedClientId
                      ? "border-[#51735f] bg-[#eef7f0]"
                      : "border-[#d8ddd2] bg-white hover:border-[#51735f]"
                  }`}
                  key={client.id}
                  onClick={() => {
                    setSelectedClientId(client.id);
                    setIdentifier(client.email ?? client.phone ?? "");
                  }}
                  type="button"
                >
                  <span className="block font-semibold">{client.firstName} {client.lastName}</span>
                  <span className="mt-1 block text-xs text-[#6f7a73]">
                    {client.email ?? client.phone ?? "Sin contacto"} · {client.status}
                  </span>
                </button>
              ))}
            </div>

            <label className="mt-4 grid gap-2 text-sm font-medium text-[#42514a]">
              Identificador directo
              <input
                className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm"
                onChange={(event) => setIdentifier(event.target.value)}
                placeholder="Email o telefono"
                value={identifier}
              />
            </label>
          </section>
        </aside>

        <section className="grid gap-6">
          {error ? (
            <p className="rounded-md border border-[#e5b7b7] bg-[#fff4f4] px-3 py-2 text-sm text-[#8c2f2f]">
              {error}
            </p>
          ) : null}
          {message ? (
            <p className="rounded-md border border-[#bfd6c7] bg-[#f0f7f2] px-3 py-2 text-sm text-[#31543d]">
              {message}
            </p>
          ) : null}

          <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
            <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
              <div>
                <h2 className="text-xl font-semibold">Evaluacion</h2>
                <p className="mt-2 text-sm text-[#5d6b63]">
                  {selectedClient ? `${selectedClient.firstName} ${selectedClient.lastName}` : "Sin cliente seleccionado"}
                </p>
              </div>
              <button
                className="h-11 rounded-md bg-[#13251c] px-5 text-sm font-semibold text-white transition hover:bg-[#243d30] disabled:opacity-60"
                disabled={busy || (!selectedClientId && !identifier.trim())}
                onClick={evaluateAccess}
                type="button"
              >
                Evaluar acceso
              </button>
            </div>

            {lastDecision ? (
              <div className={`mt-5 rounded-lg border p-5 ${
                lastDecision.decision === "GRANTED"
                  ? "border-[#9dc5aa] bg-[#eef7f0]"
                  : "border-[#e5b7b7] bg-[#fff4f4]"
              }`}>
                <p className="text-sm font-semibold uppercase tracking-wide">
                  {lastDecision.decision === "GRANTED" ? "Concedido" : "Denegado"}
                </p>
                <h3 className="mt-2 text-2xl font-semibold">
                  {lastDecision.clientName ?? "Apertura sin cliente"}
                </h3>
                <p className="mt-2 text-sm text-[#42514a]">{lastDecision.reason}</p>
                <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-3">
                  <div>
                    <dt className="text-[#6f7a73]">Puerta</dt>
                    <dd className="font-semibold">{lastDecision.deviceName}</dd>
                  </div>
                  <div>
                    <dt className="text-[#6f7a73]">Comando</dt>
                    <dd className="font-semibold">{lastDecision.commandStatus}</dd>
                  </div>
                  <div>
                    <dt className="text-[#6f7a73]">Plan</dt>
                    <dd className="font-semibold">{lastDecision.planName ?? "N/A"}</dd>
                  </div>
                </dl>
              </div>
            ) : null}
          </section>

          <section className="grid gap-6 xl:grid-cols-2">
            <article className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
              <h2 className="text-xl font-semibold">Apertura manual</h2>
              <label className="mt-4 grid gap-2 text-sm font-medium text-[#42514a]">
                Motivo
                <textarea
                  className="min-h-24 rounded-md border border-[#cdd5ca] px-3 py-2 text-sm"
                  onChange={(event) => setManualReason(event.target.value)}
                  value={manualReason}
                />
              </label>
              <button
                className="mt-4 h-10 rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white transition hover:bg-[#243d30] disabled:opacity-60"
                disabled={busy || !manualReason.trim()}
                onClick={openManually}
                type="button"
              >
                Abrir
              </button>
            </article>

            <article className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
              <h2 className="text-xl font-semibold">Pase temporal</h2>
              <div className="mt-4 grid gap-3 sm:grid-cols-2">
                <label className="grid gap-2 text-sm font-medium text-[#42514a]">
                  Horas
                  <input
                    className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm"
                    min={1}
                    onChange={(event) => setTemporaryHours(Number(event.target.value))}
                    type="number"
                    value={temporaryHours}
                  />
                </label>
                <label className="grid gap-2 text-sm font-medium text-[#42514a]">
                  Usos
                  <input
                    className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm"
                    min={1}
                    onChange={(event) => setTemporaryMaxUses(Number(event.target.value))}
                    type="number"
                    value={temporaryMaxUses}
                  />
                </label>
              </div>
              <label className="mt-3 grid gap-2 text-sm font-medium text-[#42514a]">
                Motivo
                <textarea
                  className="min-h-20 rounded-md border border-[#cdd5ca] px-3 py-2 text-sm"
                  onChange={(event) => setTemporaryReason(event.target.value)}
                  value={temporaryReason}
                />
              </label>
              <button
                className="mt-4 h-10 rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white transition hover:bg-[#243d30] disabled:opacity-60"
                disabled={busy || !selectedClientId || !temporaryReason.trim()}
                onClick={createPass}
                type="button"
              >
                Crear pase
              </button>
            </article>
          </section>

          <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
            <div className="flex items-center justify-between gap-3">
              <h2 className="text-xl font-semibold">Bitacora reciente</h2>
              <button
                className="h-10 rounded-md border border-[#cdd5ca] bg-white px-4 text-sm font-semibold text-[#42514a] transition hover:border-[#51735f]"
                onClick={() => refreshLogs()}
                type="button"
              >
                Actualizar
              </button>
            </div>
            <div className="mt-4 overflow-x-auto">
              <table className="w-full min-w-[760px] border-collapse text-sm">
                <thead>
                  <tr className="border-b border-[#e4e8df] text-left text-xs uppercase tracking-wide text-[#6f7a73]">
                    <th className="py-3 pr-4">Hora</th>
                    <th className="py-3 pr-4">Cliente</th>
                    <th className="py-3 pr-4">Decision</th>
                    <th className="py-3 pr-4">Tipo</th>
                    <th className="py-3 pr-4">Puerta</th>
                    <th className="py-3">Motivo</th>
                  </tr>
                </thead>
                <tbody>
                  {logs.map((log) => (
                    <tr className="border-b border-[#eef0eb]" key={log.id}>
                      <td className="py-3 pr-4">{formatDateTime(log.occurredAt)}</td>
                      <td className="py-3 pr-4 font-medium">{log.clientName ?? "Sin cliente"}</td>
                      <td className="py-3 pr-4">
                        <span className={`rounded-md px-2 py-1 text-xs font-semibold ${
                          log.decision === "GRANTED"
                            ? "bg-[#eef7f0] text-[#31543d]"
                            : "bg-[#fff4f4] text-[#8c2f2f]"
                        }`}>
                          {log.decision}
                        </span>
                      </td>
                      <td className="py-3 pr-4">{log.accessType}</td>
                      <td className="py-3 pr-4">{log.deviceName}</td>
                      <td className="py-3 text-[#5d6b63]">{log.reason}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        </section>
      </section>
    </main>
  );
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("es-MX", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
}
