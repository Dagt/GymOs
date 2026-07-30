"use client";

import { FormEvent, useEffect, useState } from "react";
import { AppDrawer } from "@/components/AppDrawer";
import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import {
  createBenefit,
  getBenefits,
  getPlans,
  getStoredSession,
  type BenefitPayload,
  type BenefitSummary,
  type PlanSummary,
  type StoredSession,
} from "@/lib/api";
import { resolveUserConsole } from "@/lib/role-model";

const defaultForm: BenefitPayload = {
  code: "",
  name: "",
  benefitType: "STUDENT",
  discountType: "PERCENT",
  discountPercent: 10,
  discountValueCents: null,
  requiresEvidence: false,
  combinable: false,
  priority: 10,
  status: "ACTIVE",
};

export default function BenefitsPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [benefits, setBenefits] = useState<BenefitSummary[]>([]);
  const [plans, setPlans] = useState<PlanSummary[]>([]);
  const [selectedPlanIds, setSelectedPlanIds] = useState<string[]>([]);
  const [form, setForm] = useState<BenefitPayload>(defaultForm);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showBenefitDrawer, setShowBenefitDrawer] = useState(false);

  const canManageBenefits = session
    ? session.user.platformAdmin ||
      ["TENANT_OWNER", "TENANT_MANAGER", "PLATFORM_OWNER"].includes(resolveUserConsole(session.user))
    : false;

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
        const [loadedBenefits, loadedPlans] = await Promise.all([
          getBenefits(stored.accessToken).catch(() => []),
          getPlans(stored.accessToken).catch(() => []),
        ]);
        if (active) {
          setBenefits(loadedBenefits);
          setPlans(loadedPlans);
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
      const created = await createBenefit(session.accessToken, {
        ...form,
        discountType: form.benefitType === "ACCESS" ? "NONE" : form.discountType,
        discountPercent: form.benefitType === "ACCESS" ? null : form.discountPercent,
        discountValueCents: form.benefitType === "ACCESS" ? null : form.discountValueCents,
        planIds: form.benefitType === "ACCESS" ? selectedPlanIds : [],
      });
      setBenefits((current) => [created, ...current]);
      setForm(defaultForm);
      setSelectedPlanIds([]);
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
              Consulta convenios y descuentos vigentes aplicables a ventas de socios.
            </p>
          </div>
          {canManageBenefits ? (
            <button className="app-button" onClick={() => setShowBenefitDrawer(true)} type="button">
              Crear beneficio
            </button>
          ) : null}
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
                {benefit.benefitType === "ACCESS" ? (
                  <p className="mt-2 text-xs font-bold text-[var(--brand-primary)]">
                    {benefit.planIds.length} {benefit.planIds.length === 1 ? "plan vinculado" : "planes vinculados"}
                  </p>
                ) : null}
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
            <select
              className="h-11 rounded-md border border-[var(--brand-line)] px-3"
              value={form.benefitType}
              onChange={(event) => {
                const benefitType = event.target.value as BenefitPayload["benefitType"];
                setForm({
                  ...form,
                  benefitType,
                  discountType: benefitType === "ACCESS" ? "NONE" : "PERCENT",
                  discountPercent: benefitType === "ACCESS" ? null : (form.discountPercent ?? 10),
                  discountValueCents: null,
                });
              }}
            >
              <option value="STUDENT">Estudiante</option>
              <option value="SENIOR">Adulto mayor</option>
              <option value="AGREEMENT">Convenio</option>
              <option value="PROMOTION">Promocion</option>
              <option value="ACCESS">Acceso</option>
              <option value="CUSTOM">Personalizado</option>
            </select>
          </label>
          {form.benefitType === "ACCESS" ? (
            <div className="grid gap-2">
              <label className="text-sm font-bold text-[#42514a]">
                Vincular a Planes de Membresía (Selección Múltiple)
              </label>
              <div className="grid gap-2 max-h-40 overflow-y-auto p-3 rounded-md border border-[var(--brand-line)] bg-gray-50">
                {plans.map((p) => {
                  const isChecked = selectedPlanIds.includes(p.id);
                  return (
                    <label key={p.id} className="flex items-center gap-2 text-xs font-bold text-gray-800 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={isChecked}
                        onChange={(e) => {
                          const next = e.target.checked
                            ? [...selectedPlanIds, p.id]
                            : selectedPlanIds.filter((id) => id !== p.id);
                          setSelectedPlanIds(next);
                          if (next.length > 0) {
                            const firstPlan = plans.find((pl) => pl.id === next[0]);
                            setForm({
                              ...form,
                              name: form.name || `Pase/Acceso: ${firstPlan?.name || ""}`,
                              code: form.code || `ACC-${p.id.slice(0, 6).toUpperCase()}`,
                              discountPercent: null,
                              discountType: "NONE",
                            });
                          }
                        }}
                      />
                      <span>{p.name}</span>
                    </label>
                  );
                })}
              </div>
              <p className="text-xs font-medium text-gray-500 italic bg-blue-50 p-2.5 rounded-lg border border-blue-100">
                🔒 Los pases/accesos especiales se heredan automáticamente a los socios que tengan contratado cualquiera de los planes vinculados.
              </p>
            </div>
          ) : (
            <div className="grid gap-4">
              <label className="grid gap-2 text-sm font-bold text-[#42514a]">
                Descuento %
                <input className="h-11 rounded-md border border-[var(--brand-line)] px-3" type="number" min="0" max="100" value={form.discountPercent ?? 0} onChange={(event) => setForm({ ...form, discountPercent: Number(event.target.value), discountType: "PERCENT" })} />
              </label>
              <p className="text-xs font-medium text-gray-500 italic bg-gray-50 p-2.5 rounded-lg border border-gray-100">
                💡 Este convenio/descuento podrá asignarse a perfiles de socios y aplicarse sobre la suscripción de su plan.
              </p>
            </div>
          )}
          <label className="flex items-center gap-2 text-sm font-bold text-[#42514a]">
            <input type="checkbox" checked={form.requiresEvidence} onChange={(event) => setForm({ ...form, requiresEvidence: event.target.checked })} />
            Requiere evidencia
          </label>
          <button
            className="app-button h-11"
            disabled={saving || (form.benefitType === "ACCESS" && selectedPlanIds.length === 0)}
            type="submit"
          >
            {saving ? "Guardando..." : "Crear beneficio"}
          </button>
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
