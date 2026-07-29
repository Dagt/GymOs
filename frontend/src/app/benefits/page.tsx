"use client";

import { FormEvent, useEffect, useState } from "react";
import { AppDrawer } from "@/components/AppDrawer";
import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import {
  createBenefit,
  getBenefits,
  getStoredSession,
  type BenefitPayload,
  type BenefitSummary,
  type StoredSession,
} from "@/lib/api";

const defaultForm: BenefitPayload = {
  code: "",
  name: "",
  benefitType: "STUDENT",
  discountType: "PERCENT",
  discountPercent: 10,
  discountValueCents: null,
  requiresEvidence: true,
  combinable: false,
  priority: 10,
  status: "ACTIVE",
};

export default function BenefitsPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [benefits, setBenefits] = useState<BenefitSummary[]>([]);
  const [form, setForm] = useState<BenefitPayload>(defaultForm);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showBenefitDrawer, setShowBenefitDrawer] = useState(false);

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
        const loaded = await getBenefits(stored.accessToken);
        if (active) {
          setBenefits(loaded);
        }
      } catch (exception) {
        if (active) {
          setError(exception instanceof Error ? exception.message : "No fue posible cargar beneficios.");
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

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) {
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const created = await createBenefit(session.accessToken, form);
      setBenefits((current) => [created, ...current]);
      setForm(defaultForm);
      setShowBenefitDrawer(false);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear el beneficio.");
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return <LoadingScreen text="Cargando beneficios..." />;
  }

  if (!session) {
    return (
      <AuthRequiredCard
        message="Los beneficios requieren una cuenta con permisos para administrar promociones o descuentos."
        title="Inicia sesion para ver beneficios"
      />
    );
  }

  return (
    <AppShell
      eyebrow="Administracion"
      session={session}
      subtitle="Configura descuentos y elegibilidad para estudiantes, convenios o promociones."
      title="Beneficios"
    >
      {error ? <InlineError message={error} /> : null}

      <section className="grid gap-5">
        <section className="catalog-toolbar app-card p-5">
          <div>
            <p className="app-eyebrow">Beneficios</p>
            <h2 className="app-card-title">Promociones y descuentos</h2>
            <p className="app-card-copy">
              Consulta beneficios vigentes y crea nuevos descuentos solo cuando cambie una regla comercial.
            </p>
          </div>
          <button className="app-button" onClick={() => setShowBenefitDrawer(true)} type="button">
            Crear beneficio
          </button>
        </section>

        <section className="app-card p-5">
          <div className="admin-panel-header">
            <div>
              <p>Configurados</p>
              <h2>Beneficios registrados</h2>
            </div>
            <span className="app-pill">{benefits.length}</span>
          </div>
          <div className="mt-4 grid gap-3">
            {benefits.length === 0 ? (
              <section className="admin-empty-card">
                <strong>No hay beneficios registrados</strong>
                <span>Crea descuentos para estudiantes, convenios, promociones o accesos especiales.</span>
              </section>
            ) : null}
            {benefits.map((benefit) => (
              <article className="rounded-md border border-[var(--brand-line)] bg-white p-4" key={benefit.id}>
                <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                  <div>
                    <p className="font-black">{benefit.name}</p>
                    <p className="mt-1 text-sm text-[var(--brand-muted)]">{benefit.code} - {benefit.benefitType}</p>
                  </div>
                  <span className="app-pill">{benefit.status}</span>
                </div>
                <p className="mt-3 text-sm text-[var(--brand-muted)]">
                  {benefit.discountType === "PERCENT" ? `${benefit.discountPercent ?? 0}% de descuento` : benefit.discountType}
                </p>
              </article>
            ))}
          </div>
        </section>
      </section>

      <AppDrawer
        description="Define descuentos aplicables a socios, convenios o promociones temporales."
        onClose={() => setShowBenefitDrawer(false)}
        open={showBenefitDrawer}
        title="Crear beneficio"
      >
        <form className="grid gap-4" onSubmit={submit}>
          <Field label="Codigo" value={form.code} onChange={(value) => setForm({ ...form, code: value })} />
          <Field label="Nombre" value={form.name} onChange={(value) => setForm({ ...form, name: value })} />
          <label className="grid gap-2 text-sm font-bold text-[#42514a]">
            Tipo
            <select className="h-11 rounded-md border border-[var(--brand-line)] px-3" value={form.benefitType} onChange={(event) => setForm({ ...form, benefitType: event.target.value as BenefitPayload["benefitType"] })}>
              <option value="STUDENT">Estudiante</option>
              <option value="SENIOR">Adulto mayor</option>
              <option value="AGREEMENT">Convenio</option>
              <option value="PROMOTION">Promocion</option>
              <option value="ACCESS">Acceso</option>
              <option value="CUSTOM">Personalizado</option>
            </select>
          </label>
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="grid gap-2 text-sm font-bold text-[#42514a]">
              Descuento %
              <input className="h-11 rounded-md border border-[var(--brand-line)] px-3" type="number" min="0" max="100" value={form.discountPercent ?? 0} onChange={(event) => setForm({ ...form, discountPercent: Number(event.target.value), discountType: "PERCENT" })} />
            </label>
            <label className="grid gap-2 text-sm font-bold text-[#42514a]">
              Prioridad
              <input className="h-11 rounded-md border border-[var(--brand-line)] px-3" type="number" min="0" value={form.priority} onChange={(event) => setForm({ ...form, priority: Number(event.target.value) })} />
            </label>
          </div>
          <label className="flex items-center gap-2 text-sm font-bold text-[#42514a]">
            <input type="checkbox" checked={form.requiresEvidence} onChange={(event) => setForm({ ...form, requiresEvidence: event.target.checked })} />
            Requiere evidencia
          </label>
          <button className="app-button h-11" disabled={saving} type="submit">{saving ? "Guardando..." : "Crear beneficio"}</button>
        </form>
      </AppDrawer>
    </AppShell>
  );
}

function Field({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  return (
    <label className="grid gap-2 text-sm font-bold text-[#42514a]">
      {label}
      <input className="h-11 rounded-md border border-[var(--brand-line)] px-3" value={value} onChange={(event) => onChange(event.target.value)} />
    </label>
  );
}

