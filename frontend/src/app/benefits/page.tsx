"use client";

import { FormEvent, useEffect, useState } from "react";
import { AppDrawer } from "@/components/AppDrawer";
import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import { AppNotice } from "@/components/AppNotice";
import {
  createBenefit,
  updateBenefit,
  getBenefits,
  getPlans,
  getStoredSession,
  type BenefitPayload,
  type BenefitSummary,
  type PlanSummary,
  type StoredSession,
} from "@/lib/api";

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
  const [editing, setEditing] = useState<BenefitSummary | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const canManageBenefits = Boolean(
    session?.user.permissions.includes("benefit.manage")
      || session?.user.permissions.includes("platform.admin"),
  );

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
          getBenefits(stored.accessToken),
          getPlans(stored.accessToken),
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

  async function openEditor(benefit: BenefitSummary | null) {
    if (!session) return;
    setError(null);
    setMessage(null);
    try {
      // Refresh both the plan options and optimistic version when reopening an editor.
      const [loadedPlans, loadedBenefits] = await Promise.all([getPlans(session.accessToken), getBenefits(session.accessToken)]);
      setPlans(loadedPlans);
      setBenefits(loadedBenefits);
      if (benefit) {
        benefit = loadedBenefits.find((item) => item.id === benefit?.id) ?? null;
        if (!benefit) throw new Error("El beneficio ya no está disponible. Revisa la lista actualizada.");
      }
      setEditing(benefit);
      setForm(benefit ? {
        code: benefit.code, name: benefit.name,
        benefitType: benefit.benefitType as BenefitPayload["benefitType"],
        discountType: benefit.discountType as BenefitPayload["discountType"],
        discountPercent: benefit.discountPercent, discountValueCents: benefit.discountValueCents,
        requiresEvidence: benefit.requiresEvidence, combinable: benefit.combinable, priority: benefit.priority,
        status: benefit.status, validFrom: benefit.validFrom, validUntil: benefit.validUntil,
      } : { ...defaultForm });
      setSelectedPlanIds(benefit?.planIds ?? []);
      setShowBenefitDrawer(true);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible cargar los planes.");
    }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) {
      return;
    }
    setSaving(true);
    setError(null);
    try {
      if (!session.user.branchId) {
        throw new Error("Selecciona una sucursal antes de crear el beneficio.");
      }
      const payload: BenefitPayload = {
        ...form,
        branchId: session.user.branchId,
        discountType: form.benefitType === "ACCESS" ? "NONE" : form.discountType,
        discountPercent: form.benefitType === "ACCESS" ? null : form.discountPercent,
        discountValueCents: form.benefitType === "ACCESS" ? null : form.discountValueCents,
        planIds: form.benefitType === "ACCESS" ? selectedPlanIds : [],
      };
      const saved = editing
        ? await updateBenefit(session.accessToken, editing.id, payload, editing.version ?? 0)
        : await createBenefit(session.accessToken, payload);
      setBenefits((current) => editing ? current.map((item) => item.id === saved.id ? saved : item) : [saved, ...current]);
      setMessage(editing ? "Beneficio actualizado. Los planes vinculados comparten el mismo beneficio." : "Beneficio creado.");
      setForm(defaultForm);
      setSelectedPlanIds([]);
      setShowBenefitDrawer(false);
      setEditing(null);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible guardar el beneficio.");
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
      subtitle="Administra descuentos y beneficios de acceso compartidos por tus planes."
      title="Beneficios"
    >
      {error ? <InlineError message={error} /> : null}
      {message ? <AppNotice message={message} tone="success" /> : null}

      <section className="grid gap-5">
        <section className="catalog-toolbar app-card p-5">
          <div>
            <p className="app-eyebrow">Beneficios</p>
            <h2 className="app-card-title">Accesos, promociones y descuentos</h2>
            <p className="app-card-copy">
              Un mismo acceso, como Sauna, puede incluirse en varios planes sin duplicar el beneficio.
            </p>
          </div>
          {canManageBenefits ? (
            <button className="app-button" onClick={() => void openEditor(null)} type="button">
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
          <div className="app-scroll-list mt-4 grid gap-3" aria-label="Beneficios registrados" role="region" tabIndex={0}>
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
                  <div className="flex flex-wrap gap-2">
                    <span className="app-pill">
                      {benefit.branchId === session.user.branchId
                        ? "Sucursal actual"
                        : "Todas las sucursales"}
                    </span>
                    <span className="app-pill">{benefit.status}</span>
                    {canManageBenefits ? <button className="app-button-secondary !px-3 !py-1" onClick={() => void openEditor(benefit)} type="button" aria-label={`Editar ${benefit.name}`}>Editar</button> : null}
                  </div>
                </div>
                <p className="mt-3 text-sm text-[var(--brand-muted)]">
                  {benefit.benefitType === "ACCESS" ? "Acceso heredado del plan" : benefit.discountType === "PERCENT" ? `${benefit.discountPercent ?? 0}% de descuento` : benefit.discountType}
                </p>
                {benefit.benefitType === "ACCESS" ? (
                  <p className="mt-2 text-xs font-bold text-[var(--brand-primary)]">
                    Planes: {benefit.planIds.map((id) => plans.find((plan) => plan.id === id)?.name ?? "Plan histórico vinculado").join(", ") || "Sin planes vinculados"}
                  </p>
                ) : null}
              </article>
            ))}
          </div>
        </section>
      </section>

      <AppDrawer
        description="Define descuentos o accesos para la sucursal seleccionada actualmente."
        onClose={() => { if (!saving) { setShowBenefitDrawer(false); setEditing(null); } }}
        open={showBenefitDrawer}
        title={editing ? "Editar beneficio" : "Crear beneficio"}
      >
        <form className="grid gap-4" onSubmit={submit}>
          {error ? <p role="alert" className="text-sm text-red-700">{error}</p> : null}
          {editing ? <p className="text-sm text-[var(--brand-muted)]">El código, tipo y sucursal se conservan para mantener el historial y los puntos de acceso vinculados.</p> : null}
          <Field label="Código" value={form.code} disabled={Boolean(editing)} onChange={(value) => setForm({ ...form, code: value })} />
          <Field label="Nombre" value={form.name} onChange={(value) => setForm({ ...form, name: value })} />
          <label className="grid gap-2 text-sm font-bold text-[#42514a]">
            Tipo
            <select
              className="h-11 rounded-md border border-[var(--brand-line)] px-3"
              value={form.benefitType}
              disabled={Boolean(editing)}
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
              <p className="text-sm font-bold text-[#42514a]">
                Vincular a Planes de Membresía (Selección Múltiple)
              </p>
              <div className="app-scroll-list app-scroll-list--short grid gap-2 p-3 rounded-md border border-[var(--brand-line)] bg-gray-50">
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
                {(editing?.planIds ?? []).filter((id) => !plans.some((plan) => plan.id === id)).map((id) => (
                  <label key={id} className="flex items-center gap-2 text-xs text-[#637268]">
                    <input type="checkbox" checked={selectedPlanIds.includes(id)} onChange={(event) => setSelectedPlanIds((current) => event.target.checked ? [...new Set([...current, id])] : current.filter((value) => value !== id))} />
                    Plan histórico ({id.slice(0, 8)}). Conserva el vínculo para sus membresías existentes.
                  </label>
                ))}
              </div>
              {plans.length === 0 ? <p className="text-sm text-amber-800">Crea primero un plan de membresía para poder vincular este acceso.</p> : null}
              {editing ? <p className="text-xs text-amber-800">Puedes vincular varios planes al mismo acceso. Quitar un plan retira este beneficio de los derechos de sus socios.</p> : null}
              <p className="text-xs font-medium text-gray-500 italic bg-blue-50 p-2.5 rounded-lg border border-blue-100">
                🔒 Los pases/accesos especiales se heredan automáticamente a los socios que tengan contratado cualquiera de los planes vinculados.
              </p>
            </div>
          ) : (
            <div className="grid gap-4">
              <label className="grid gap-2 text-sm font-bold text-[#42514a]">Regla de precio
                <select className="app-input" value={form.discountType} onChange={(event) => setForm({ ...form, discountType: event.target.value as BenefitPayload["discountType"] })}>
                  <option value="PERCENT">Porcentaje</option><option value="FIXED">Descuento fijo</option><option value="SPECIAL_PRICE">Precio especial</option>
                </select>
              </label>
              <label className="grid gap-2 text-sm font-bold text-[#42514a]">
                {form.discountType === "PERCENT" ? "Descuento %" : "Importe (MXN)"}
                <input className="app-input" required type="number" min="0.01" step="0.01" max={form.discountType === "PERCENT" ? 100 : undefined} value={form.discountType === "PERCENT" ? form.discountPercent ?? 0 : (form.discountValueCents ?? 0) / 100} onChange={(event) => setForm(form.discountType === "PERCENT" ? { ...form, discountPercent: Number(event.target.value), discountValueCents: null } : { ...form, discountValueCents: Math.round(Number(event.target.value) * 100), discountPercent: null })} />
              </label>
              <p className="text-xs font-medium text-gray-500 italic bg-gray-50 p-2.5 rounded-lg border border-gray-100">
                💡 Este convenio/descuento podrá asignarse a perfiles de socios y aplicarse sobre la suscripción de su plan.
              </p>
            </div>
          )}
          <label className="grid gap-2 text-sm font-bold text-[#42514a]">Estado
            <select className="app-input" value={form.status} onChange={(event) => setForm({ ...form, status: event.target.value as BenefitPayload["status"] })}><option value="ACTIVE">Activo</option><option value="INACTIVE">Inactivo</option></select>
          </label>
          <div className="grid grid-cols-2 gap-3">
            <label className="grid gap-2 text-sm font-bold">Vigente desde<input className="app-input" type="date" value={form.validFrom ?? ""} onChange={(event) => setForm({ ...form, validFrom: event.target.value })} /></label>
            <label className="grid gap-2 text-sm font-bold">Vigente hasta<input className="app-input" type="date" min={form.validFrom || undefined} value={form.validUntil ?? ""} onChange={(event) => setForm({ ...form, validUntil: event.target.value })} /></label>
          </div>
          <label className="flex items-center gap-2 text-sm font-bold text-[#42514a]">
            <input type="checkbox" checked={form.requiresEvidence} onChange={(event) => setForm({ ...form, requiresEvidence: event.target.checked })} />
            Requiere evidencia
          </label>
          <button
            className="app-button h-11"
            disabled={saving || (form.benefitType === "ACCESS" && selectedPlanIds.length === 0)}
            type="submit"
          >
            {saving ? "Guardando..." : editing ? "Guardar cambios" : "Crear beneficio"}
          </button>
        </form>
      </AppDrawer>
    </AppShell>
  );
}

function Field({ label, value, onChange, disabled = false }: { label: string; value: string; onChange: (value: string) => void; disabled?: boolean }) {
  return (
    <label className="grid gap-2 text-sm font-bold text-[#42514a]">
      {label}
      <input className="h-11 rounded-md border border-[var(--brand-line)] px-3" required disabled={disabled} maxLength={label === "Código" ? 80 : 160} value={value} onChange={(event) => onChange(event.target.value)} />
    </label>
  );
}
