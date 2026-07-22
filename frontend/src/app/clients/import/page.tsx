"use client";

import { ChangeEvent, FormEvent, useEffect, useState } from "react";
import { AppShell, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import {
  getStoredSession,
  importClientsCsv,
  type ClientImportResult,
  type StoredSession,
} from "@/lib/api";

const template = [
  "firstName,lastName,birthDate,gender,phone,email,notes",
  "Ana,Lopez,1994-02-12,FEMALE,+525500000001,ana@example.com,Cliente piloto",
  "Carlos,Ruiz,1989-08-21,MALE,+525500000002,carlos@example.com,",
].join("\n");

export default function ClientImportPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [fileName, setFileName] = useState("clientes-piloto.csv");
  const [csvText, setCsvText] = useState(template);
  const [dryRun, setDryRun] = useState(true);
  const [result, setResult] = useState<ClientImportResult | null>(null);
  const [checkingSession, setCheckingSession] = useState(true);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const validRows = result?.rows.filter((row) => row.status === "VALID").length ?? 0;

  useEffect(() => {
    let active = true;

    async function loadSession() {
      const stored = getStoredSession();
      if (!active) {
        return;
      }
      setSession(stored);
      setCheckingSession(false);
    }

    void loadSession();

    return () => {
      active = false;
    };
  }, []);

  async function loadFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }
    setFileName(file.name);
    setCsvText(await file.text());
    setResult(null);
    setError(null);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) {
      return;
    }
    setLoading(true);
    setError(null);
    setResult(null);
    try {
      setResult(await importClientsCsv(session.accessToken, fileName, csvText, dryRun));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible importar socios.");
    } finally {
      setLoading(false);
    }
  }

  if (checkingSession) {
    return <LoadingScreen text="Cargando importacion..." />;
  }

  if (!session) {
    return <AuthRequiredCard message="Necesitas una sesion activa para importar socios." title="Inicia sesion para importar socios" />;
  }

  return (
    <AppShell
      eyebrow="Socios"
      session={session}
      subtitle="Carga inicial por CSV con validacion previa, duplicados y resultado por fila."
      title="Importar socios"
    >
      <section className="grid gap-6 lg:grid-cols-[0.9fr_1.4fr]">
        <form className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm" onSubmit={submit}>
          <h2 className="text-xl font-semibold">Archivo CSV</h2>
          <div className="mt-5 grid gap-4">
            <label className="grid gap-2 text-sm font-medium text-[#42514a]">
              Seleccionar archivo
              <input
                accept=".csv,text/csv"
                className="rounded-md border border-[#cdd5ca] bg-white px-3 py-2 text-sm"
                onChange={loadFile}
                type="file"
              />
            </label>
            <label className="grid gap-2 text-sm font-medium text-[#42514a]">
              Nombre de archivo
              <input
                className="h-11 rounded-md border border-[#cdd5ca] px-3 text-sm outline-none transition focus:border-[#51735f]"
                onChange={(event) => setFileName(event.target.value)}
                value={fileName}
              />
            </label>
            <label className="grid gap-2 text-sm font-medium text-[#42514a]">
              Contenido
              <textarea
                className="min-h-64 rounded-md border border-[#cdd5ca] px-3 py-2 font-mono text-xs outline-none transition focus:border-[#51735f]"
                onChange={(event) => setCsvText(event.target.value)}
                value={csvText}
              />
            </label>
            <label className="flex items-center gap-3 text-sm font-medium text-[#42514a]">
              <input checked={dryRun} onChange={(event) => setDryRun(event.target.checked)} type="checkbox" />
              Validar sin crear socios
            </label>
            <button
              className="h-11 rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white transition hover:bg-[#243d30] disabled:opacity-60"
              disabled={loading}
              type="submit"
            >
              {dryRun ? "Validar CSV" : "Importar socios"}
            </button>
          </div>
        </form>

        <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
          <h2 className="text-xl font-semibold">Resultado</h2>
          {error ? <Message tone="error" text={error} /> : null}
          {!result && !error ? (
            <p className="mt-5 rounded-md border border-[#d8ddd2] bg-[#fafbf8] px-3 py-2 text-sm text-[#5d6b63]">
              Ejecuta una validacion para revisar errores antes de importar.
            </p>
          ) : null}
          {result ? (
            <div className="mt-5 grid gap-4">
              <div className="grid gap-3 sm:grid-cols-4">
                <Metric label="Filas" value={result.totalRows} />
                <Metric label={result.dryRun ? "Validas" : "Creadas"} value={result.dryRun ? validRows : result.createdCount} />
                <Metric label="Omitidas" value={result.dryRun ? 0 : result.skippedCount} />
                <Metric label="Errores" value={result.errorCount} />
              </div>
              <div className="overflow-x-auto">
                <table className="min-w-full border-separate border-spacing-0 text-sm">
                  <thead>
                    <tr className="text-left text-[#5d6b63]">
                      <th className="border-b border-[#d8ddd2] py-2 pr-4">Fila</th>
                      <th className="border-b border-[#d8ddd2] py-2 pr-4">Estado</th>
                      <th className="border-b border-[#d8ddd2] py-2 pr-4">Socio</th>
                      <th className="border-b border-[#d8ddd2] py-2 pr-4">Contacto</th>
                      <th className="border-b border-[#d8ddd2] py-2 pr-4">Detalle</th>
                    </tr>
                  </thead>
                  <tbody>
                    {result.rows.map((row) => (
                      <tr key={`${row.rowNumber}-${row.email ?? row.phone ?? row.firstName}`} className="align-top">
                        <td className="border-b border-[#edf0ea] py-2 pr-4">{row.rowNumber}</td>
                        <td className="border-b border-[#edf0ea] py-2 pr-4">
                          <StatusBadge status={row.status} />
                        </td>
                        <td className="border-b border-[#edf0ea] py-2 pr-4">
                          {row.firstName} {row.lastName}
                        </td>
                        <td className="border-b border-[#edf0ea] py-2 pr-4 text-[#5d6b63]">
                          {row.email ?? row.phone ?? "Sin contacto"}
                        </td>
                        <td className="border-b border-[#edf0ea] py-2 pr-4 text-[#5d6b63]">{row.reason}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          ) : null}
        </section>
      </section>
    </AppShell>
  );
}

function Metric({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-md border border-[#d8ddd2] px-3 py-2">
      <p className="text-xs font-semibold uppercase text-[#5d6b63]">{label}</p>
      <p className="mt-1 text-2xl font-semibold">{value}</p>
    </div>
  );
}

function StatusBadge({ status }: { status: string }) {
  const className =
    status === "ERROR"
      ? "bg-[#fff4f4] text-[#8c2f2f]"
      : status === "SKIPPED"
        ? "bg-[#fff2d6] text-[#7a5018]"
        : "bg-[#e4f4e9] text-[#31563c]";
  return <span className={`rounded-md px-2 py-1 text-xs font-semibold ${className}`}>{status}</span>;
}

function Message({ tone, text }: { tone: "success" | "error"; text: string }) {
  const className = tone === "success"
    ? "border-[#bad9c4] bg-[#eff8f1] text-[#31563c]"
    : "border-[#e5b7b7] bg-[#fff4f4] text-[#8c2f2f]";
  return <p className={`mt-5 rounded-md border px-3 py-2 text-sm ${className}`}>{text}</p>;
}
