"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import { AppDrawer } from "@/components/AppDrawer";
import { AppNotice } from "@/components/AppNotice";
import { NumberField, SelectField, TextAreaField, TextField } from "@/components/FormFields";
import {
  createVisitPackagePlan,
  getCatalogAccessBenefits,
  getBranches,
  getVisitPackagePlans,
  updateVisitPackagePlan,
  type BenefitSummary,
  type BranchSummary,
  type VisitPackageBenefitUsage,
  type VisitPackagePlanPayload,
  type VisitPackagePlanSummary,
} from "@/lib/api";
import { formatMoney } from "@/app/catalog/CatalogHelpers";

type Draft = {
  sku: string;
  name: string;
  description: string;
  price: string;
  currency: string;
  totalVisits: string;
  validityDays: string;
  branchScope: VisitPackagePlanPayload["branchScope"];
  branchIds: string[];
  benefits: VisitPackageBenefitUsage[];
  selfServiceAcquisitionEnabled: boolean;
  registrationFeePolicy: VisitPackagePlanPayload["registrationFeePolicy"];
  status: VisitPackagePlanPayload["status"];
  version?: number;
};

const emptyDraft: Draft = {
  sku: "",
  name: "",
  description: "",
  price: "",
  currency: "MXN",
  totalVisits: "10",
  validityDays: "30",
  branchScope: "SELECTED_BRANCHES",
  branchIds: [],
  benefits: [],
  selfServiceAcquisitionEnabled: true,
  registrationFeePolicy: "DEFAULT",
  status: "ACTIVE",
};

export function VisitPackagePlansSection({
  accessToken,
  currentBranchId,
  canCreate,
  canUpdate,
}: {
  accessToken: string;
  currentBranchId: string | null;
  canCreate: boolean;
  canUpdate: boolean;
}) {
  const [plans, setPlans] = useState<VisitPackagePlanSummary[]>([]);
  const [branches, setBranches] = useState<BranchSummary[]>([]);
  const [benefits, setBenefits] = useState<BenefitSummary[]>([]);
  const [draft, setDraft] = useState<Draft>(emptyDraft);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const accessBenefits = useMemo(
    () => benefits.filter((benefit) => benefit.benefitType === "ACCESS" && benefit.status === "ACTIVE"),
    [benefits],
  );
  const selectedPackageBranchIds = useMemo(
    () => draft.branchScope === "ALL_BRANCHES" ? branches.map((branch) => branch.id) : draft.branchIds,
    [branches, draft.branchIds, draft.branchScope],
  );
  const currentBranch = useMemo(
    () => branches.find((branch) => branch.id === currentBranchId) ?? null,
    [branches, currentBranchId],
  );
  const additionalBranches = useMemo(
    () => branches.filter((branch) => branch.id !== currentBranchId),
    [branches, currentBranchId],
  );
  const uncoveredSelectedBranches = useMemo(
    () => branchesWithoutConsumingAccess(
      selectedPackageBranchIds,
      draft.benefits,
      accessBenefits,
    ),
    [accessBenefits, draft.benefits, selectedPackageBranchIds],
  );

  useEffect(() => {
    let active = true;
    async function load() {
      setLoading(true);
      try {
        const [loadedPlans, loadedBranches, loadedBenefits] = await Promise.all([
          getVisitPackagePlans(accessToken),
          getBranches(accessToken),
          getCatalogAccessBenefits(accessToken),
        ]);
        if (!active) return;
        setPlans(loadedPlans);
        setBranches(loadedBranches.filter((branch) => branch.status === "ACTIVE"));
        setBenefits(loadedBenefits);
      } catch (exception) {
        if (active) setError(errorMessage(exception, "No fue posible cargar los paquetes de visitas."));
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => { active = false; };
  }, [accessToken]);

  function create() {
    setEditingId(null);
    setDraft({
      ...emptyDraft,
      branchScope: "SELECTED_BRANCHES",
      branchIds: currentBranchId ? [currentBranchId] : [],
    });
    setError(null);
    setMessage(null);
    setOpen(true);
  }

  function edit(plan: VisitPackagePlanSummary) {
    setEditingId(plan.id);
    setDraft({
      sku: plan.sku,
      name: plan.name,
      description: plan.description ?? "",
      price: (plan.priceCents / 100).toFixed(2),
      currency: plan.currency,
      totalVisits: String(plan.totalVisits),
      validityDays: String(plan.validityDays),
      branchScope: plan.branchScope,
      branchIds: plan.branchScope === "ALL_BRANCHES"
        ? branches.map((branch) => branch.id)
        : currentBranchId
          ? Array.from(new Set([...plan.branchIds, currentBranchId]))
          : plan.branchIds,
      benefits: plan.benefits ?? plan.benefitIds.map((benefitId) => ({
        benefitId,
        usageMode: "CONSUMES_VISIT" as const,
      })),
      selfServiceAcquisitionEnabled: plan.selfServiceAcquisitionEnabled,
      registrationFeePolicy: plan.registrationFeePolicy,
      status: plan.status,
      version: plan.version,
    });
    setError(null);
    setMessage(null);
    setOpen(true);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    const price = Number(draft.price);
    const totalVisits = Number(draft.totalVisits);
    const validityDays = Number(draft.validityDays);
    if (!Number.isFinite(price) || price < 0 || !Number.isInteger(totalVisits) || totalVisits < 1
      || !Number.isInteger(validityDays) || validityDays < 1) {
      setError("Revisa precio, número de visitas y vigencia.");
      return;
    }
    if (draft.branchScope === "SELECTED_BRANCHES" && draft.branchIds.length === 0) {
      setError("Selecciona al menos una sucursal.");
      return;
    }
    if (currentBranchId && draft.branchScope === "SELECTED_BRANCHES" && !draft.branchIds.includes(currentBranchId)) {
      setError("El paquete debe incluir la sucursal en la que estás operando.");
      return;
    }
    if (draft.benefits.length === 0) {
      setError("Selecciona al menos un acceso para el paquete.");
      return;
    }
    if (!draft.benefits.some((benefit) => benefit.usageMode === "CONSUMES_VISIT")) {
      setError("Selecciona al menos un acceso que consuma una visita.");
      return;
    }
    const incompatibleBenefit = draft.benefits.find((usage) => {
      const benefit = accessBenefits.find((item) => item.id === usage.benefitId);
      return !benefit || !benefitCoversAnySelectedBranch(benefit, selectedPackageBranchIds);
    });
    if (incompatibleBenefit) {
      setError("Quita los accesos que no existen en ninguna de las sucursales elegidas.");
      return;
    }
    if (uncoveredSelectedBranches.length > 0) {
      const names = branches
        .filter((branch) => uncoveredSelectedBranches.includes(branch.id))
        .map((branch) => branch.name)
        .join(", ");
      setError(`Selecciona al menos un acceso disponible en: ${names}.`);
      return;
    }
    const payload: VisitPackagePlanPayload = {
      sku: editingId ? undefined : draft.sku.trim(),
      name: draft.name.trim(),
      description: draft.description.trim() || null,
      priceCents: Math.round(price * 100),
      currency: draft.currency,
      totalVisits,
      validityDays,
      branchScope: draft.branchScope,
      branchIds: draft.branchScope === "ALL_BRANCHES" ? [] : draft.branchIds,
      benefits: draft.benefits,
      selfServiceAcquisitionEnabled: draft.selfServiceAcquisitionEnabled,
      registrationFeePolicy: draft.registrationFeePolicy,
      status: draft.status,
      expectedVersion: draft.version,
    };
    setSaving(true);
    try {
      const saved = editingId
        ? await updateVisitPackagePlan(accessToken, editingId, payload)
        : await createVisitPackagePlan(accessToken, payload);
      setPlans((current) => editingId
        ? current.map((plan) => plan.id === saved.id ? saved : plan)
        : [...current, saved]);
      setOpen(false);
      setMessage(editingId ? "Paquete actualizado." : "Paquete creado.");
    } catch (exception) {
      setError(errorMessage(exception, "No fue posible guardar el paquete."));
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="grid gap-3">
      {error && !open ? <AppNotice message={error} tone="error" /> : null}
      {message ? <AppNotice message={message} tone="success" /> : null}
      <section className="app-card p-4">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="app-eyebrow">Accesos consumibles</p>
            <h2 className="app-card-title">Paquetes de visitas</h2>
            <p className="mt-1 text-sm text-[var(--brand-muted)]">
              Define visitas, vigencia y sucursales una sola vez. Cada compra mantiene su propio saldo.
            </p>
          </div>
          {canCreate ? <button className="app-button h-10" type="button" onClick={create}>Nuevo paquete</button> : null}
        </div>
        {loading ? <p className="mt-5 text-sm text-[var(--brand-muted)]">Cargando paquetes...</p> : null}
        {!loading && plans.length === 0 ? (
          <p className="mt-5 rounded-lg border border-dashed border-[var(--brand-line)] p-6 text-center text-sm text-[var(--brand-muted)]">
            Aún no hay paquetes de visitas configurados.
          </p>
        ) : null}
        <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          {plans.map((plan) => (
            <article className="flex min-h-44 flex-col rounded-xl border border-[var(--brand-line)] bg-[#fbfcfa] p-4" key={plan.id}>
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="font-semibold text-[#111815]">{plan.name}</p>
                  <p className="text-xs text-[var(--brand-muted)]">{plan.sku}</p>
                </div>
                <span className={plan.status === "ACTIVE" ? "rounded-full bg-[#eaf6ed] px-2 py-1 text-[11px] font-semibold text-[#28733e]" : "rounded-full bg-[#fff3df] px-2 py-1 text-[11px] font-semibold text-[#8a5a22]"}>
                  {plan.status === "ACTIVE" ? "Activo" : "Inactivo"}
                </span>
              </div>
              <p className="mt-2 text-lg font-semibold text-[var(--brand-primary)]">{formatMoney(plan.priceCents, plan.currency)}</p>
              <div className="mt-3 flex flex-wrap gap-2 text-xs text-[var(--brand-muted)]">
                <span className="rounded-full bg-white px-2 py-1">{plan.totalVisits} visitas</span>
                <span className="rounded-full bg-white px-2 py-1">{plan.validityDays} días</span>
                <span className="rounded-full bg-white px-2 py-1">{plan.branchScope === "ALL_BRANCHES" ? "Todas las sucursales" : `${plan.branchIds.length} sucursal(es)`}</span>
                {!plan.selfServiceAcquisitionEnabled ? <span className="rounded-full bg-[#f1ede5] px-2 py-1">Oculto en kiosco</span> : null}
                {plan.registrationFeePolicy === "WAIVED" ? <span className="rounded-full bg-[#eaf6ed] px-2 py-1 text-[#28733e]">Sin inscripción</span> : null}
              </div>
              {canUpdate ? <button className="app-button-secondary mt-auto h-9 w-full" type="button" onClick={() => edit(plan)}>Editar</button> : null}
            </article>
          ))}
        </div>
      </section>

      <AppDrawer
        description="La venta crea un saldo independiente; nunca una membresía ilimitada."
        onClose={() => setOpen(false)}
        open={open}
        title={editingId ? "Editar paquete de visitas" : "Nuevo paquete de visitas"}
        wide
      >
        <form className="grid gap-4" onSubmit={submit}>
          {error ? <AppNotice message={error} tone="error" /> : null}
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <TextField disabled={Boolean(editingId)} label="Código" name="visitPackageSku" placeholder="VISITAS-10" value={draft.sku} onChange={(sku) => setDraft({ ...draft, sku })} />
            <TextField label="Nombre" name="visitPackageName" placeholder="10 visitas en 30 días" value={draft.name} onChange={(name) => setDraft({ ...draft, name })} />
            <NumberField label="Precio" name="visitPackagePrice" step="0.01" value={draft.price} onChange={(price) => setDraft({ ...draft, price })} />
            <NumberField label="Número de visitas" name="visitPackageVisits" min="1" value={draft.totalVisits} onChange={(totalVisits) => setDraft({ ...draft, totalVisits })} />
            <NumberField label="Vigencia en días" name="visitPackageValidity" min="1" value={draft.validityDays} onChange={(validityDays) => setDraft({ ...draft, validityDays })} />
            <SelectField label="Estado" name="visitPackageStatus" value={draft.status} options={[{ label: "Activo", value: "ACTIVE" }, { label: "Inactivo", value: "INACTIVE" }]} onChange={(status) => setDraft({ ...draft, status: status as Draft["status"] })} />
          </div>
          {editingId ? <p className="-mt-3 text-xs text-[var(--brand-muted)]">El código queda fijo para conservar el historial del artículo.</p> : null}
          <TextAreaField label="Descripción" name="visitPackageDescription" required={false} rows={2} value={draft.description} onChange={(description) => setDraft({ ...draft, description })} />

          <div className="grid gap-4 rounded-xl border border-[var(--brand-line)] bg-[#fbfcfa] p-4 lg:grid-cols-2">
            <div className="grid content-start gap-3">
              <div>
                <p className="text-sm font-semibold text-[#111815]">Dónde puede usarse</p>
                <p className="mt-1 text-xs text-[var(--brand-muted)]">
                  La sucursal en la que estás operando siempre forma parte del paquete. Agrega sólo las sucursales adicionales que necesites.
                </p>
              </div>
              {currentBranch ? (
                <div className="rounded-lg border border-[#bfd7c5] bg-[#edf7ef] px-3 py-2">
                  <p className="text-xs font-semibold uppercase tracking-wide text-[#477252]">Sucursal base</p>
                  <p className="mt-0.5 font-semibold text-[#173b22]">{currentBranch.name}</p>
                  <p className="mt-0.5 text-xs text-[#477252]">Siempre incluida porque estás operando aquí.</p>
                </div>
              ) : (
                <p className="rounded-lg bg-[#fff3df] px-3 py-2 text-xs font-medium text-[#80561f]">
                  Selecciona una sucursal operativa antes de crear el paquete.
                </p>
              )}
              {additionalBranches.length > 0 ? (
                <div className="grid gap-2">
                  <p className="text-xs font-semibold uppercase tracking-wide text-[var(--brand-muted)]">Agregar otras sucursales</p>
                  <CheckboxList
                    empty="No hay otras sucursales activas."
                    items={additionalBranches.map((branch) => ({
                      id: branch.id,
                      label: branch.name,
                      description: branchHasAvailableAccess(branch.id, accessBenefits)
                        ? "El saldo también podrá usarse aquí"
                        : "No hay accesos configurados para paquetes en esta sucursal",
                      disabled: !branchHasAvailableAccess(branch.id, accessBenefits),
                    }))}
                    selected={draft.branchIds.filter((branchId) => branchId !== currentBranchId)}
                    onChange={(additionalBranchIds) => {
                      const branchIds = currentBranchId
                        ? [currentBranchId, ...additionalBranchIds]
                        : additionalBranchIds;
                      setDraft({
                        ...draft,
                        branchScope: "SELECTED_BRANCHES",
                        branchIds,
                        benefits: compatibleBenefits(draft.benefits, accessBenefits, branchIds),
                      });
                    }}
                  />
                </div>
              ) : (
                <p className="text-xs text-[var(--brand-muted)]">No hay otras sucursales activas para agregar.</p>
              )}
              {!currentBranchId ? (
                <CheckboxList
                  empty="No hay sucursales activas."
                  items={branches.map((branch) => ({
                    id: branch.id,
                    label: branch.name,
                    description: branchHasAvailableAccess(branch.id, accessBenefits)
                      ? undefined
                      : "No hay accesos configurados para paquetes en esta sucursal",
                    disabled: !branchHasAvailableAccess(branch.id, accessBenefits),
                  }))}
                  selected={draft.branchIds}
                  onChange={(branchIds) => setDraft({
                    ...draft,
                    branchScope: "SELECTED_BRANCHES",
                    branchIds,
                    benefits: compatibleBenefits(draft.benefits, accessBenefits, branchIds),
                  })}
                />
              ) : null}
            </div>
            <div className="grid content-start gap-2">
              <p className="text-sm font-semibold text-[#111815]">Accesos del paquete</p>
              <p className="text-xs text-[var(--brand-muted)]">
                Elige qué acceso registra la visita y cuáles quedan incluidos después, sin otro descuento.
              </p>
              <BenefitUsageList
                empty="Primero crea un beneficio de tipo Acceso."
                items={accessBenefits.map((benefit) => ({
                  id: benefit.id,
                  label: benefit.name,
                  description: benefitAvailabilityLabel(benefit, branches, selectedPackageBranchIds),
                  disabled: !benefitCoversAnySelectedBranch(benefit, selectedPackageBranchIds),
                }))}
                selected={draft.benefits}
                onChange={(benefits) => {
                  setDraft({ ...draft, benefits });
                  setError(null);
                }}
              />
              <div className="grid gap-1 rounded-lg bg-[#f1f5ef] px-3 py-2 text-xs text-[#31563c]">
                <p><strong>Consume una visita:</strong> descuenta una unidad del saldo.</p>
                <p><strong>Se incluye con la visita:</strong> requiere una visita registrada ese día en esa misma sucursal.</p>
              </div>
              <p className="rounded-lg bg-[#f1f5ef] px-3 py-2 text-xs text-[#31563c]">
                El paquete no amplía un acceso. Si Sauna es sólo de Centro, seguirá disponible sólo en Centro aunque el saldo pueda usarse también en Tecnológica.
              </p>
              {uncoveredSelectedBranches.length > 0 ? (
                <p className="rounded-lg bg-[#fff3df] px-3 py-2 text-xs font-medium text-[#80561f]">
                  Falta un acceso que consuma una visita en {branches
                    .filter((branch) => uncoveredSelectedBranches.includes(branch.id))
                    .map((branch) => branch.name)
                    .join(", ")}.
                </p>
              ) : null}
            </div>
          </div>

          <div className="grid gap-3 rounded-xl border border-[var(--brand-line)] p-4 sm:grid-cols-2">
            <label className="flex items-start gap-3 text-sm">
              <input className="mt-0.5 h-4 w-4 accent-[var(--brand-primary)]" type="checkbox" checked={draft.selfServiceAcquisitionEnabled} onChange={(event) => setDraft({ ...draft, selfServiceAcquisitionEnabled: event.target.checked })} />
              <span><strong className="block text-[#111815]">Mostrar para contratar en kiosco</strong><span className="text-xs text-[var(--brand-muted)]">Si se apaga, recepción todavía puede venderlo.</span></span>
            </label>
            <SelectField label="Inscripción" name="visitPackageRegistration" value={draft.registrationFeePolicy} options={[{ label: "Aplicar configuración general", value: "DEFAULT" }, { label: "No cobrar inscripción", value: "WAIVED" }]} onChange={(registrationFeePolicy) => setDraft({ ...draft, registrationFeePolicy: registrationFeePolicy as Draft["registrationFeePolicy"] })} />
          </div>
          <button className="app-button h-11" disabled={saving} type="submit">
            {saving ? "Guardando..." : editingId ? "Guardar cambios" : "Crear paquete"}
          </button>
        </form>
      </AppDrawer>
    </section>
  );
}

function CheckboxList({
  empty,
  items,
  onChange,
  selected,
}: {
  empty: string;
  items: { id: string; label: string; description?: string; disabled?: boolean }[];
  onChange: (ids: string[]) => void;
  selected: string[];
}) {
  if (items.length === 0) return <p className="rounded-lg border border-dashed border-[var(--brand-line)] p-3 text-xs text-[var(--brand-muted)]">{empty}</p>;
  return (
    <div className="grid max-h-40 gap-1 overflow-y-auto rounded-lg border border-[var(--brand-line)] bg-white p-2">
      {items.map((item) => (
        <label className={`flex items-start gap-2 rounded-md px-2 py-1.5 text-sm ${item.disabled ? "cursor-not-allowed bg-[#f5f7f4]" : "hover:bg-[#f5f7f4]"}`} key={item.id}>
          <input
            className="mt-0.5 h-4 w-4 accent-[var(--brand-primary)]"
            type="checkbox"
            checked={selected.includes(item.id)}
            disabled={item.disabled}
            onChange={(event) => onChange(event.target.checked
              ? [...selected, item.id]
              : selected.filter((id) => id !== item.id))}
          />
          <span>
            <span className="block">{item.label}</span>
            {item.description ? <span className="block text-[11px] text-[var(--brand-muted)]">{item.description}</span> : null}
          </span>
        </label>
      ))}
    </div>
  );
}

function BenefitUsageList({
  empty,
  items,
  onChange,
  selected,
}: {
  empty: string;
  items: { id: string; label: string; description?: string; disabled?: boolean }[];
  onChange: (benefits: VisitPackageBenefitUsage[]) => void;
  selected: VisitPackageBenefitUsage[];
}) {
  if (items.length === 0) {
    return <p className="rounded-lg border border-dashed border-[var(--brand-line)] p-3 text-xs text-[var(--brand-muted)]">{empty}</p>;
  }
  return (
    <div className="grid max-h-56 gap-1 overflow-y-auto rounded-lg border border-[var(--brand-line)] bg-white p-2">
      {items.map((item) => {
        const usage = selected.find((benefit) => benefit.benefitId === item.id);
        return (
          <div className={`grid gap-2 rounded-md px-2 py-2 ${item.disabled ? "bg-[#f5f7f4]" : "hover:bg-[#f5f7f4]"}`} key={item.id}>
            <label className={`flex items-start gap-2 text-sm ${item.disabled ? "cursor-not-allowed" : ""}`}>
              <input
                className="mt-0.5 h-4 w-4 accent-[var(--brand-primary)]"
                type="checkbox"
                checked={Boolean(usage)}
                disabled={item.disabled}
                onChange={(event) => onChange(event.target.checked
                  ? [...selected, { benefitId: item.id, usageMode: "CONSUMES_VISIT" }]
                  : selected.filter((benefit) => benefit.benefitId !== item.id))}
              />
              <span>
                <span className="block font-medium text-[#111815]">{item.label}</span>
                {item.description ? <span className="block text-[11px] text-[var(--brand-muted)]">{item.description}</span> : null}
              </span>
            </label>
            {usage ? (
              <select
                aria-label={`Modo para ${item.label}`}
                className="h-9 rounded-lg border border-[var(--brand-line)] bg-white px-2 text-xs font-medium text-[#111815]"
                value={usage.usageMode}
                onChange={(event) => onChange(selected.map((benefit) => benefit.benefitId === item.id
                  ? { ...benefit, usageMode: event.target.value as VisitPackageBenefitUsage["usageMode"] }
                  : benefit))}
              >
                <option value="CONSUMES_VISIT">Consume una visita</option>
                <option value="INCLUDED_WITH_VISIT">Se incluye con la visita</option>
              </select>
            ) : null}
          </div>
        );
      })}
    </div>
  );
}

function benefitAvailabilityLabel(
  benefit: BenefitSummary,
  branches: BranchSummary[],
  selectedBranchIds: string[],
) {
  const coverage = benefitCoverageBranchIds(benefit);
  const availableNames = branches
    .filter((branch) => selectedBranchIds.includes(branch.id) && coverage.includes(branch.id))
    .map((branch) => branch.name);
  const unavailableNames = branches
    .filter((branch) => selectedBranchIds.includes(branch.id) && !coverage.includes(branch.id))
    .map((branch) => branch.name);
  if (availableNames.length === 0) return "No existe en las sucursales elegidas";
  const available = `Disponible en: ${availableNames.join(", ")}`;
  return unavailableNames.length > 0
    ? `${available} · No disponible en: ${unavailableNames.join(", ")}`
    : available;
}

function benefitCoverageBranchIds(benefit: BenefitSummary) {
  if (benefit.accessBranchIds && benefit.accessBranchIds.length > 0) return benefit.accessBranchIds;
  return benefit.branchId ? [benefit.branchId] : [];
}

function benefitCoversAnySelectedBranch(benefit: BenefitSummary, selectedBranchIds: string[]) {
  const coverage = benefitCoverageBranchIds(benefit);
  return selectedBranchIds.some((branchId) => coverage.includes(branchId));
}

function branchHasAvailableAccess(branchId: string, benefits: BenefitSummary[]) {
  return benefits.some((benefit) => benefitCoverageBranchIds(benefit).includes(branchId));
}

function branchesWithoutConsumingAccess(
  branchIds: string[],
  selectedUsages: VisitPackageBenefitUsage[],
  benefits: BenefitSummary[],
) {
  const consumingBenefitIds = selectedUsages
    .filter((usage) => usage.usageMode === "CONSUMES_VISIT")
    .map((usage) => usage.benefitId);
  const selectedBenefits = benefits.filter((benefit) => consumingBenefitIds.includes(benefit.id));
  return branchIds.filter((branchId) => !selectedBenefits.some(
    (benefit) => benefitCoverageBranchIds(benefit).includes(branchId),
  ));
}

function compatibleBenefits(
  selected: VisitPackageBenefitUsage[],
  benefits: BenefitSummary[],
  branchIds: string[],
) {
  return selected.filter((usage) => {
    const benefit = benefits.find((item) => item.id === usage.benefitId);
    return benefit ? benefitCoversAnySelectedBranch(benefit, branchIds) : false;
  });
}

function errorMessage(exception: unknown, fallback: string) {
  const rawMessage = exception instanceof Error ? exception.message : "";
  if (rawMessage.includes("VISIT_PACKAGE_ACTIVE_BRANCH_REQUIRED")) {
    return "El paquete debe incluir la sucursal en la que estás operando.";
  }
  if (rawMessage.includes("VISIT_PACKAGE_BRANCH_WITHOUT_ACCESS")) {
    return "Cada sucursal elegida debe tener al menos un acceso que consuma una visita.";
  }
  if (rawMessage.includes("VISIT_PACKAGE_BENEFIT_OUTSIDE_SCOPE")) {
    return "Quita los accesos que no existen en las sucursales elegidas.";
  }
  if (rawMessage.includes("VISIT_PACKAGE_CONSUMING_BENEFIT_REQUIRED")) {
    return "Selecciona al menos un acceso que consuma una visita.";
  }
  return rawMessage || fallback;
}
