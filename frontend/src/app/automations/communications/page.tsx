"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AuthRequired } from "@/components/AuthRequired";
import {
  archiveCommunicationTemplate,
  createCommunicationTemplate,
  getCommunicationDeliverySummary,
  getCommunicationTemplates,
  getStoredSession,
  type CommunicationTemplate,
  type DeliverySummary,
  type StoredSession,
} from "@/lib/api";

export default function CommunicationsPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [templates, setTemplates] = useState<CommunicationTemplate[]>([]);
  const [summary, setSummary] = useState<DeliverySummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [code, setCode] = useState("MEMBERSHIP_EXPIRING");
  const [channel, setChannel] = useState<"EMAIL" | "WHATSAPP">("EMAIL");
  const [subject, setSubject] = useState("Tu membresía está por vencer");
  const [body, setBody] = useState("Hola {{clientName}}, tu membresía vence el {{endsOn}}.");
  const [variables, setVariables] = useState("clientName,endsOn");

  async function load(activeSession: StoredSession) {
    const [loadedTemplates, loadedSummary] = await Promise.all([
      getCommunicationTemplates(activeSession.accessToken),
      getCommunicationDeliverySummary(activeSession.accessToken, 30),
    ]);
    setTemplates(loadedTemplates);
    setSummary(loadedSummary);
  }

  useEffect(() => {
    let active = true;
    async function initialize() {
      const stored = getStoredSession();
      if (!active) return;
      if (!stored) {
        setLoading(false);
        return;
      }
      setSession(stored);
      try {
        await load(stored);
      } catch (exception) {
        if (active) setError(exception instanceof Error ? exception.message : "No fue posible cargar comunicaciones.");
      } finally {
        if (active) setLoading(false);
      }
    }
    void initialize();
    return () => { active = false; };
  }, []);

  const channelTotals = useMemo(() => {
    const values = new Map<string, number>();
    summary.forEach((item) => values.set(item.channel, (values.get(item.channel) ?? 0) + Number(item.total)));
    return values;
  }, [summary]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) return;
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      await createCommunicationTemplate(session.accessToken, {
        code: code.trim().toUpperCase(),
        channel,
        locale: "es-MX",
        subject: channel === "EMAIL" ? subject : null,
        body,
        allowedVariablesJson: JSON.stringify(
          variables.split(",").map((item) => item.trim()).filter(Boolean),
        ),
        status: "ACTIVE",
      });
      setMessage("Plantilla creada como una nueva versión activa.");
      await load(session);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear la plantilla.");
    } finally {
      setSaving(false);
    }
  }

  async function archive(template: CommunicationTemplate) {
    if (!session) return;
    setError(null);
    try {
      await archiveCommunicationTemplate(session.accessToken, template.id);
      await load(session);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible archivar la plantilla.");
    }
  }

  if (loading) return <LoadingScreen text="Cargando comunicaciones..." />;
  if (!session) return <AuthRequired title="Comunicaciones" message="Inicia sesión para administrar plantillas." />;

  return (
    <AppShell
      eyebrow="Automatizaciones"
      session={session}
      title="Comunicaciones a socios"
      subtitle="Plantillas por canal y resultados agregados de entrega."
    >
      {error ? <InlineError message={error} /> : null}
      {message ? <section className="app-card p-4 text-sm text-emerald-800">{message}</section> : null}

      <section className="grid gap-4 md:grid-cols-2">
        <Metric label="Correos generados · 30 días" value={channelTotals.get("EMAIL") ?? 0} />
        <Metric label="WhatsApp generados · 30 días" value={channelTotals.get("WHATSAPP") ?? 0} />
      </section>

      <section className="grid gap-6 lg:grid-cols-[0.9fr_1.1fr]">
        <form className="app-card grid gap-4 p-5" onSubmit={submit}>
          <div>
            <p className="app-eyebrow">Nueva versión</p>
            <h2 className="mt-1 text-xl font-semibold">Crear plantilla</h2>
          </div>
          <label className="grid gap-1 text-sm font-medium">Código
            <input className="app-input" value={code} onChange={(event) => setCode(event.target.value)} required />
          </label>
          <label className="grid gap-1 text-sm font-medium">Canal
            <select className="app-select" value={channel} onChange={(event) => setChannel(event.target.value as "EMAIL" | "WHATSAPP")}>
              <option value="EMAIL">Correo</option>
              <option value="WHATSAPP">WhatsApp</option>
            </select>
          </label>
          {channel === "EMAIL" ? (
            <label className="grid gap-1 text-sm font-medium">Asunto
              <input className="app-input" value={subject} onChange={(event) => setSubject(event.target.value)} required />
            </label>
          ) : null}
          <label className="grid gap-1 text-sm font-medium">Mensaje
            <textarea className="app-textarea min-h-32" value={body} onChange={(event) => setBody(event.target.value)} required />
          </label>
          <label className="grid gap-1 text-sm font-medium">Variables permitidas, separadas por coma
            <input className="app-input" value={variables} onChange={(event) => setVariables(event.target.value)} />
          </label>
          <button className="app-button" disabled={saving} type="submit">{saving ? "Guardando..." : "Crear versión"}</button>
        </form>

        <section className="app-card p-5">
          <p className="app-eyebrow">Biblioteca</p>
          <h2 className="mt-1 text-xl font-semibold">Plantillas registradas</h2>
          <div className="mt-4 grid gap-3">
            {templates.length === 0 ? <p className="text-sm text-[var(--brand-muted)]">No hay plantillas.</p> : null}
            {templates.map((template) => (
              <article className="rounded-md border border-[var(--brand-line)] p-4" key={template.id}>
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <strong>{template.code}</strong>
                    <p className="text-sm text-[var(--brand-muted)]">{template.channel} · {template.locale} · versión {template.version}</p>
                  </div>
                  <span className="rounded-full bg-[#edf4ef] px-3 py-1 text-xs font-semibold">{template.status}</span>
                </div>
                {template.subject ? <p className="mt-3 text-sm font-medium">{template.subject}</p> : null}
                <p className="mt-2 whitespace-pre-wrap text-sm text-[var(--brand-muted)]">{template.body}</p>
                {template.status !== "ARCHIVED" ? (
                  <button className="mt-4 text-sm font-semibold text-red-700" onClick={() => archive(template)} type="button">Archivar</button>
                ) : null}
              </article>
            ))}
          </div>
        </section>
      </section>

      <section className="app-card p-5">
        <h2 className="text-xl font-semibold">Resultados por canal</h2>
        <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {summary.map((item) => <Metric key={`${item.channel}-${item.status}`} label={`${item.channel} · ${item.status}`} value={Number(item.total)} />)}
        </div>
      </section>
    </AppShell>
  );
}

function Metric({ label, value }: { label: string; value: number }) {
  return <div className="app-card p-4"><span className="text-sm text-[var(--brand-muted)]">{label}</span><strong className="mt-1 block text-2xl">{value}</strong></div>;
}
