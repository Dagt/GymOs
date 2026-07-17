"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
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
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear el beneficio.");
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return <main className="admin-empty-state"><section><p>GymOS</p><h1>Cargando beneficios...</h1></section></main>;
  }

  if (!session) {
    return <main className="admin-empty-state"><section><p>GymOS</p><h1>Inicia sesion</h1><Link href="/login">Entrar</Link></section></main>;
  }

  return (
    <main className="app-shell text-[#111815]">
      <div className="app-shell-inner">
        <header className="app-topbar">
          <div className="app-title-block">
            <p className="app-eyebrow">Administracion</p>
            <h1 className="app-title">Beneficios</h1>
            <p className="app-subtitle">Configura descuentos y elegibilidad para estudiantes, convenios o promociones.</p>
          </div>
          <div className="app-actions">
            <Link className="app-button-secondary" href="/reception">Panel</Link>
            <Link className="app-button" href="/clients">Asignar a cliente</Link>
          </div>
        </header>

        {error ? <p className="admin-alert">{error}</p> : null}

        <section className="grid gap-5 lg:grid-cols-[0.9fr_1.1fr]">
          <section className="app-card p-5">
            <h2 className="app-card-title">Nuevo beneficio</h2>
            <form className="mt-5 grid gap-4" onSubmit={submit}>
              <Field label="Codigo" value={form.code} onChange={(value) => setForm({ ...form, code: value })} />
              <Field label="Nombre" value={form.name} onChange={(value) => setForm({ ...form, name: value })} />
              <label className="grid gap-2 text-sm font-bold text-[#42514a]">
                Tipo
                <select className="h-11 rounded-md border border-[#cdd5ca] px-3" value={form.benefitType} onChange={(event) => setForm({ ...form, benefitType: event.target.value as BenefitPayload["benefitType"] })}>
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
                  <input className="h-11 rounded-md border border-[#cdd5ca] px-3" type="number" min="0" max="100" value={form.discountPercent ?? 0} onChange={(event) => setForm({ ...form, discountPercent: Number(event.target.value), discountType: "PERCENT" })} />
                </label>
                <label className="grid gap-2 text-sm font-bold text-[#42514a]">
                  Prioridad
                  <input className="h-11 rounded-md border border-[#cdd5ca] px-3" type="number" min="0" value={form.priority} onChange={(event) => setForm({ ...form, priority: Number(event.target.value) })} />
                </label>
              </div>
              <label className="flex items-center gap-2 text-sm font-bold text-[#42514a]">
                <input type="checkbox" checked={form.requiresEvidence} onChange={(event) => setForm({ ...form, requiresEvidence: event.target.checked })} />
                Requiere evidencia
              </label>
              <button className="app-button h-11" disabled={saving} type="submit">{saving ? "Guardando..." : "Crear beneficio"}</button>
            </form>
          </section>

          <section className="app-card p-5">
            <h2 className="app-card-title">Beneficios configurados</h2>
            <div className="mt-4 grid gap-3">
              {benefits.length === 0 ? <p className="app-card-copy">No hay beneficios registrados.</p> : null}
              {benefits.map((benefit) => (
                <article className="rounded-md border border-[#d8ddd2] bg-white p-4" key={benefit.id}>
                  <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                    <div>
                      <p className="font-black">{benefit.name}</p>
                      <p className="mt-1 text-sm text-[#5d6b63]">{benefit.code} - {benefit.benefitType}</p>
                    </div>
                    <span className="app-pill">{benefit.status}</span>
                  </div>
                  <p className="mt-3 text-sm text-[#5d6b63]">
                    {benefit.discountType === "PERCENT" ? `${benefit.discountPercent ?? 0}% de descuento` : benefit.discountType}
                  </p>
                </article>
              ))}
            </div>
          </section>
        </section>
      </div>
    </main>
  );
}

function Field({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  return (
    <label className="grid gap-2 text-sm font-bold text-[#42514a]">
      {label}
      <input className="h-11 rounded-md border border-[#cdd5ca] px-3" value={value} onChange={(event) => onChange(event.target.value)} />
    </label>
  );
}
