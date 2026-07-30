"use client";

import { useEffect, useMemo, useState } from "react";
import { AppDrawer } from "@/components/AppDrawer";
import {
  assignClientBenefit,
  getBenefits,
  getClientBenefits,
  getEffectiveAccessBenefits,
  type BenefitSummary,
  type ClientBenefitSummary,
  type EffectiveAccessBenefit,
  type StoredSession,
} from "@/lib/api";
import {
  isDiscountBenefit,
  validateClientBenefitAssignment,
} from "@/app/benefits/benefits-helpers";

export function ClientBenefitsSection({
  canAssignBenefits,
  canReadAccessBenefits,
  clientId,
  refreshKey,
  session,
}: {
  canAssignBenefits: boolean;
  canReadAccessBenefits: boolean;
  clientId: string;
  refreshKey: string;
  session: StoredSession;
}) {
  const [clientBenefits, setClientBenefits] = useState<ClientBenefitSummary[]>([]);
  const [accessBenefits, setAccessBenefits] = useState<EffectiveAccessBenefit[]>([]);
  const [availableBenefits, setAvailableBenefits] = useState<BenefitSummary[]>([]);
  const [selectedBenefitId, setSelectedBenefitId] = useState("");
  const [validUntil, setValidUntil] = useState("");
  const [showDrawer, setShowDrawer] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loadWarning, setLoadWarning] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function load() {
      setLoading(true);
      setError(null);
      setLoadWarning(null);
      const issues: string[] = [];
      const assignedPromise = getClientBenefits(session.accessToken, clientId)
        .catch(() => {
          issues.push("No fue posible consultar los descuentos asignados.");
          return [];
        });
      const accessPromise = canReadAccessBenefits && session.user.branchId
        ? getEffectiveAccessBenefits(session.accessToken, clientId).catch(() => {
            issues.push("No fue posible calcular los accesos heredados.");
            return [];
          })
        : Promise.resolve([]);
      const catalogPromise = canAssignBenefits
        ? getBenefits(session.accessToken).catch(() => {
            issues.push("No fue posible cargar el catálogo de beneficios.");
            return [];
          })
        : Promise.resolve([]);

      const [assigned, inheritedAccess, catalog] = await Promise.all([
        assignedPromise,
        accessPromise,
        catalogPromise,
      ]);
      if (!active) return;

      setClientBenefits(assigned);
      setAccessBenefits(inheritedAccess);
      setAvailableBenefits(
        catalog.filter(
          (benefit) =>
            (!benefit.status || benefit.status.toUpperCase() === "ACTIVE")
            && isDiscountBenefit(benefit),
        ),
      );
      setLoadWarning(issues.length > 0 ? issues.join(" ") : null);
      setLoading(false);
    }

    void load();
    return () => {
      active = false;
    };
  }, [
    canAssignBenefits,
    canReadAccessBenefits,
    clientId,
    refreshKey,
    session.accessToken,
    session.user.branchId,
  ]);

  const selectedBenefit = useMemo(
    () => availableBenefits.find((benefit) => benefit.id === selectedBenefitId),
    [availableBenefits, selectedBenefitId],
  );

  async function handleAssignBenefit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedBenefit || !canAssignBenefits) return;

    const validation = validateClientBenefitAssignment({
      benefit: selectedBenefit,
      validUntil: validUntil || null,
    });
    if (!validation.valid) {
      setError(validation.error ?? "Completa la vigencia del beneficio.");
      return;
    }

    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      await assignClientBenefit(session.accessToken, clientId, {
        benefitId: selectedBenefitId,
        validFrom: todayIso(),
        validUntil,
        notes: "Asignado desde ficha de socio",
      });
      const updatedAssigned = await getClientBenefits(session.accessToken, clientId);
      setClientBenefits(updatedAssigned);
      setShowDrawer(false);
      setSelectedBenefitId("");
      setValidUntil("");
      setMessage(`Se asignó ${selectedBenefit.name} al socio.`);
    } catch (exception) {
      setError(
        exception instanceof Error
          ? exception.message
          : "No fue posible asignar el beneficio de descuento.",
      );
    } finally {
      setSaving(false);
    }
  }

  function openAssignmentDrawer() {
    setError(null);
    setMessage(null);
    setSelectedBenefitId("");
    setValidUntil("");
    setShowDrawer(true);
  }

  return (
    <section className="app-card p-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <p className="app-eyebrow">Elegibilidad vigente</p>
          <h2 className="app-card-title">Beneficios y accesos</h2>
          <p className="mt-1 text-sm text-[#5d6b63]">
            Los accesos se heredan del plan activo; los descuentos se asignan directamente al socio.
          </p>
        </div>
        {canAssignBenefits ? (
          <button
            className="app-button-secondary text-xs"
            onClick={openAssignmentDrawer}
            type="button"
          >
            Asignar descuento
          </button>
        ) : null}
      </div>

      {message ? <p className="form-message success mt-4">{message}</p> : null}
      {error && !showDrawer ? <p className="form-message error mt-4">{error}</p> : null}
      {loadWarning ? <p className="form-message error mt-4">{loadWarning}</p> : null}

      {loading ? (
        <p className="mt-4 text-sm text-[#6f7a73]">Cargando beneficios del socio...</p>
      ) : (
        <div className="mt-5 grid gap-5">
          <section>
            <h3 className="text-sm font-semibold text-[#26352e]">Accesos heredados del plan activo</h3>
            <div className="mt-2 grid gap-2">
              {!session.user.branchId ? (
                <EmptyBenefitState text="Selecciona una sucursal para calcular los accesos efectivos." />
              ) : !canReadAccessBenefits ? (
                <EmptyBenefitState text="Tu rol no permite consultar reglas de acceso." />
              ) : accessBenefits.length === 0 ? (
                <EmptyBenefitState text="El plan activo no concede accesos configurados para esta sucursal." />
              ) : (
                accessBenefits.map((benefit) => (
                  <article
                    className="rounded-lg border border-[#d8ddd2] bg-[#f7f9f6] p-3"
                    key={benefit.benefitId}
                  >
                    <div className="flex flex-wrap items-start justify-between gap-2">
                      <div>
                        <strong className="text-sm text-[#26352e]">{benefit.name}</strong>
                        <p className="mt-1 text-xs text-[#5d6b63]">
                          Heredado de {benefit.planName} · Código {benefit.code}
                        </p>
                      </div>
                      <span className="rounded bg-blue-100 px-2 py-1 text-[0.65rem] font-bold uppercase text-blue-700">
                        Heredado
                      </span>
                    </div>
                  </article>
                ))
              )}
            </div>
          </section>

          <section>
            <h3 className="text-sm font-semibold text-[#26352e]">Descuentos asignados al socio</h3>
            <div className="mt-2 grid gap-2">
              {clientBenefits.length === 0 ? (
                <EmptyBenefitState text="El socio no tiene descuentos o convenios asignados." />
              ) : (
                clientBenefits.map((benefit) => {
                  const presentation = assignmentPresentation(benefit);
                  return (
                    <article
                      className="rounded-lg border border-[#d8ddd2] bg-white p-3"
                      key={benefit.id}
                    >
                      <div className="flex flex-wrap items-start justify-between gap-2">
                        <div>
                          <strong className="text-sm text-[#26352e]">{benefit.benefitName}</strong>
                          <p className="mt-1 text-xs text-[#5d6b63]">
                            {benefit.validUntil
                              ? `Vigencia hasta ${benefit.validUntil}`
                              : "Sin fecha de expiración registrada"}
                          </p>
                        </div>
                        <span className={`rounded px-2 py-1 text-[0.65rem] font-bold uppercase ${presentation.className}`}>
                          {presentation.label}
                        </span>
                      </div>
                    </article>
                  );
                })
              )}
            </div>
          </section>
        </div>
      )}

      <AppDrawer
        description="El descuento se considerará al cotizar la siguiente contratación, renovación o cambio de plan."
        onClose={() => setShowDrawer(false)}
        open={showDrawer}
        title="Asignar beneficio de descuento"
      >
        <form className="grid gap-4" onSubmit={handleAssignBenefit}>
          {error ? <p className="form-message error">{error}</p> : null}

          <label className="form-field" htmlFor="clientBenefit">
            <span>Convenio o beneficio</span>
            <select
              className="app-input"
              id="clientBenefit"
              onChange={(event) => setSelectedBenefitId(event.target.value)}
              required
              value={selectedBenefitId}
            >
              <option value="">Selecciona un descuento</option>
              {availableBenefits.map((benefit) => (
                <option key={benefit.id} value={benefit.id}>
                  {benefit.name}
                </option>
              ))}
            </select>
          </label>

          {availableBenefits.length === 0 ? (
            <p className="text-sm text-[#6f7a73]">
              No hay beneficios de descuento activos. Créalo primero en el módulo de Beneficios.
            </p>
          ) : null}

          <label className="form-field" htmlFor="benefitValidUntil">
            <span>Vigente hasta</span>
            <input
              className="app-input"
              id="benefitValidUntil"
              min={todayIso()}
              onChange={(event) => setValidUntil(event.target.value)}
              required
              type="date"
              value={validUntil}
            />
            <small>La expiración evita que un descuento permanezca activo indefinidamente.</small>
          </label>

          <button
            className="form-primary-button"
            disabled={saving || !selectedBenefitId || !validUntil}
            type="submit"
          >
            {saving ? "Asignando..." : "Asignar descuento"}
          </button>
        </form>
      </AppDrawer>
    </section>
  );
}

function EmptyBenefitState({ text }: { text: string }) {
  return (
    <p className="rounded-lg border border-dashed border-[#d8ddd2] px-3 py-3 text-xs text-[#6f7a73]">
      {text}
    </p>
  );
}

function assignmentPresentation(benefit: ClientBenefitSummary) {
  const status = benefit.status.trim().toUpperCase();
  const today = todayIso();
  if (status === "REJECTED") {
    return { label: "Rechazado", className: "bg-red-100 text-red-700" };
  }
  if (status === "EXPIRED" || (benefit.validUntil && benefit.validUntil < today)) {
    return { label: "Vencido", className: "bg-gray-100 text-gray-600" };
  }
  if (benefit.validFrom && benefit.validFrom > today) {
    return { label: "Programado", className: "bg-amber-100 text-amber-700" };
  }
  if (status === "VERIFIED") {
    return { label: "Verificado", className: "bg-green-100 text-green-700" };
  }
  return { label: "Vigente", className: "bg-green-100 text-green-700" };
}

function todayIso() {
  const now = new Date();
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 10);
}
