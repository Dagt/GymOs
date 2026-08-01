"use client";

import { FormEvent, useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import { getAutomations, getStoredSession, type AutomationRule, type StoredSession } from "@/lib/api";
import {
  archiveAutomation,
  duplicateAutomation,
  getAutomationVersions,
  previewAutomation,
  updateAutomation,
} from "@/lib/api/automations";
import type {
  AutomationPreview,
  AutomationRuleRevision,
} from "@/lib/api/automation-types";

export default function AutomationRuleDetailPage() {
  const params = useParams<{ ruleId: string }>();
  const router = useRouter();
  const [session, setSession] = useState<StoredSession | null>(null);
  const [rule, setRule] = useState<AutomationRule | null>(null);
  const [versions, setVersions] = useState<AutomationRuleRevision[]>([]);
  const [preview, setPreview] = useState<AutomationPreview | null>(null);
  const [name, setName] = useState("");
  const [triggerType, setTriggerType] = useState("MANUAL");
  const [conditionJson, setConditionJson] = useState("{}");
  const [actionConfigJson, setActionConfigJson] = useState("{}");
  const [retryLimit, setRetryLimit] = useState(3);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    const stored = getStoredSession();
    setSession(stored);
    if (!stored) {
      setLoading(false);
      return;
    }
    void load(stored);
  }, [params.ruleId]);

  async function load(activeSession: StoredSession) {
    setLoading(true);
    setError(null);
    try {
      const [rules, loadedVersions] = await Promise.all([
        getAutomations(activeSession.accessToken),
        getAutomationVersions(activeSession.accessToken, params.ruleId),
      ]);
      const selected = rules.find((candidate) => candidate.id === params.ruleId) ?? null;
      if (!selected) throw new Error("La regla no existe o no está disponible.");
      setRule(selected);
      setVersions(loadedVersions);
      setName(selected.name);
      setTriggerType(selected.triggerType);
      setConditionJson(selected.conditionJson);
      setActionConfigJson(selected.actionConfigJson);
      setRetryLimit(selected.retryLimit);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible cargar la regla.");
    } finally {
      setLoading(false);
    }
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || !rule) return;
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const updated = await updateAutomation(session.accessToken, rule.id, {
        name,
        triggerType,
        conditionJson,
        actionType: "CREATE_NOTIFICATION",
        actionConfigJson,
        retryLimit,
      });
      setRule(updated);
      setVersions(await getAutomationVersions(session.accessToken, rule.id));
      setMessage(`Definición v${updated.version} publicada.`);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible actualizar la regla.");
    } finally {
      setSaving(false);
    }
  }

  async function runPreview() {
    if (!session || !rule) return;
    setError(null);
    try {
      setPreview(await previewAutomation(session.accessToken, rule.id));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible generar la vista previa.");
    }
  }

  async function duplicate() {
    if (!session || !rule) return;
    setSaving(true);
    try {
      const copy = await duplicateAutomation(session.accessToken, rule.id);
      router.push(`/automations/${copy.id}`);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible duplicar la regla.");
    } finally {
      setSaving(false);
    }
  }

  async function archive() {
    if (!session || !rule) return;
    setSaving(true);
    try {
      await archiveAutomation(session.accessToken, rule.id);
      router.push("/automations");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible archivar la regla.");
      setSaving(false);
    }
  }

  if (loading) return <LoadingScreen text="Cargando regla..." />;
  if (!session) return <AuthRequiredCard title="Inicia sesión" message="La administración de reglas requiere una sesión activa." />;

  return (
    <AppShell
      eyebrow="Automatizaciones"
      session={session}
      subtitle="Edita una nueva versión sin alterar las ocurrencias que ya están en cola."
      title={rule?.name ?? "Regla"}
      actions={[{ href: "/automations", label: "Volver" }]}
    >
      {error ? <InlineError message={error} /> : null}
      {message ? <p className="rounded-md border border-[var(--brand-line)] bg-[var(--brand-surface)] px-3 py-2 text-sm">{message}</p> : null}

      <section className="grid gap-6 lg:grid-cols-[1.15fr_0.85fr]">
        <form className="app-card grid gap-4 p-5" onSubmit={save}>
          <div>
            <p className="app-eyebrow">Definición actual</p>
            <h2 className="mt-1 text-xl font-semibold">Versión {rule?.version ?? "-"}</h2>
          </div>
          <label className="grid gap-2 text-sm font-medium">Nombre
            <input className="h-10 rounded-md border border-[var(--brand-line)] px-3" value={name} onChange={(event) => setName(event.target.value)} />
          </label>
          <label className="grid gap-2 text-sm font-medium">Disparador
            <select className="h-10 rounded-md border border-[var(--brand-line)] bg-white px-3" value={triggerType} onChange={(event) => setTriggerType(event.target.value)}>
              <option value="MANUAL">Manual</option>
              <option value="SCHEDULED">Programada</option>
              <option value="MEMBERSHIP_EXPIRING">Membresía próxima a vencer</option>
            </select>
          </label>
          <label className="grid gap-2 text-sm font-medium">Condición JSON
            <textarea className="min-h-28 rounded-md border border-[var(--brand-line)] px-3 py-2 font-mono text-xs" value={conditionJson} onChange={(event) => setConditionJson(event.target.value)} />
          </label>
          <label className="grid gap-2 text-sm font-medium">Acción JSON
            <textarea className="min-h-40 rounded-md border border-[var(--brand-line)] px-3 py-2 font-mono text-xs" value={actionConfigJson} onChange={(event) => setActionConfigJson(event.target.value)} />
          </label>
          <label className="grid gap-2 text-sm font-medium">Reintentos
            <input className="h-10 rounded-md border border-[var(--brand-line)] px-3" type="number" min={0} max={10} value={retryLimit} onChange={(event) => setRetryLimit(Number(event.target.value))} />
          </label>
          <div className="flex flex-wrap gap-3">
            <button className="app-button" disabled={saving || !name.trim()} type="submit">Publicar nueva versión</button>
            <button className="app-button-secondary" disabled={saving} onClick={runPreview} type="button">Vista previa</button>
            <button className="app-button-secondary" disabled={saving} onClick={duplicate} type="button">Duplicar</button>
            <button className="app-button-secondary" disabled={saving} onClick={archive} type="button">Archivar</button>
          </div>
        </form>

        <div className="grid content-start gap-6">
          <section className="app-card p-5">
            <h2 className="text-lg font-semibold">Vista previa</h2>
            {!preview ? <p className="mt-3 text-sm text-[var(--brand-muted)]">Genera una vista previa para saber qué registros coinciden.</p> : null}
            {preview ? (
              <div className="mt-3 grid gap-3">
                <p className="text-sm">{preview.matchCount} coincidencias con la versión {preview.definitionVersion}.</p>
                {preview.matches.map((match) => (
                  <article className="rounded-md border border-[var(--brand-line)] p-3" key={match.reference}>
                    <p className="text-sm font-semibold">{match.summary}</p>
                    <p className="mt-1 text-xs text-[var(--brand-muted)]">{match.reference}</p>
                  </article>
                ))}
              </div>
            ) : null}
          </section>

          <section className="app-card p-5">
            <h2 className="text-lg font-semibold">Historial de versiones</h2>
            <div className="mt-3 grid gap-3">
              {versions.map((version) => (
                <article className="rounded-md border border-[var(--brand-line)] p-3" key={version.id}>
                  <div className="flex items-start justify-between gap-3">
                    <p className="font-semibold">v{version.definitionVersion} · {version.name}</p>
                    <span className="text-xs text-[var(--brand-muted)]">{version.changeType}</span>
                  </div>
                  <p className="mt-1 text-xs text-[var(--brand-muted)]">{formatDateTime(version.changedAt)}</p>
                </article>
              ))}
            </div>
          </section>
        </div>
      </section>
    </AppShell>
  );
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("es-MX", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}
