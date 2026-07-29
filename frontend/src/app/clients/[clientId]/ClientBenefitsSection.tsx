"use client";

import { useEffect, useState } from "react";
import { AppDrawer } from "@/components/AppDrawer";
import {
  assignClientBenefit,
  getBenefits,
  getClientBenefits,
  type BenefitSummary,
  type ClientBenefitSummary,
  type StoredSession,
} from "@/lib/api";
import { isDiscountBenefit, validateClientBenefitAssignment } from "@/app/benefits/benefits-helpers";

export function ClientBenefitsSection({
  clientId,
  session,
}: {
  clientId: string;
  session: StoredSession;
}) {
  const [clientBenefits, setClientBenefits] = useState<ClientBenefitSummary[]>([]);
  const [availableBenefits, setAvailableBenefits] = useState<BenefitSummary[]>([]);
  const [selectedBenefitId, setSelectedBenefitId] = useState("");
  const [validUntil, setValidUntil] = useState("");
  const [showDrawer, setShowDrawer] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    async function load() {
      // 1. Fetch assigned benefits independently
      try {
        const assigned = await getClientBenefits(session.accessToken, clientId);
        if (active) setClientBenefits(assigned);
      } catch {
        if (active) setClientBenefits([]);
      }

      // 2. Fetch available catalog benefits independently
      try {
        const benefitsList = await getBenefits(session.accessToken);
        if (active) {
          setAvailableBenefits(benefitsList.filter((b) => !b.status || b.status.toUpperCase() === "ACTIVE"));
        }
      } catch {
        if (active) setAvailableBenefits([]);
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => {
      active = false;
    };
  }, [clientId, session]);

  const selectedBenefit = availableBenefits.find((b) => b.id === selectedBenefitId);

  async function handleAssignBenefit(e: React.FormEvent) {
    e.preventDefault();
    if (!selectedBenefit) return;

    const validation = validateClientBenefitAssignment({
      benefit: selectedBenefit,
      validUntil: validUntil || null,
    });

    if (!validation.valid) {
      setError(validation.error || "Validación fallida.");
      return;
    }

    setSaving(true);
    setError(null);
    try {
      await assignClientBenefit(session.accessToken, clientId, {
        benefitId: selectedBenefitId,
        validFrom: new Date().toISOString().slice(0, 10),
        validUntil: validUntil || null,
        notes: "Asignado desde ficha de socio",
      });
      const updatedAssigned = await getClientBenefits(session.accessToken, clientId).catch(() => []);
      setClientBenefits(updatedAssigned);
      setShowDrawer(false);
      setSelectedBenefitId("");
      setValidUntil("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Error al asignar convenio/beneficio.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="app-card p-5">
      <div className="flex items-center justify-between">
        <div>
          <p className="app-eyebrow">Convenios y Beneficios</p>
          <h2 className="app-card-title">Beneficios Asignados al Socio</h2>
        </div>
        <button
          className="app-button-secondary text-xs px-3 py-1.5"
          onClick={() => setShowDrawer(true)}
          type="button"
        >
          + Asignar Convenio / Beneficio
        </button>
      </div>

      <div className="mt-4 grid gap-2">
        {loading ? (
          <p className="text-xs text-gray-400 font-bold italic py-2">Cargando beneficios...</p>
        ) : clientBenefits.length === 0 ? (
          <p className="text-xs text-gray-400 font-bold italic py-2">
            El socio no tiene convenios ni beneficios asignados.
          </p>
        ) : (
          clientBenefits.map((cb) => (
            <div
              key={cb.id}
              className="p-3 rounded-lg border border-[var(--brand-line)] bg-gray-50/50 flex items-center justify-between"
            >
              <div>
                <strong className="text-sm font-bold text-gray-900">{cb.benefitName}</strong>
                <p className="text-xs text-gray-500">
                  {cb.validUntil ? `Vence: ${cb.validUntil}` : "Sin expiración definida"}
                </p>
              </div>
              <span className="text-[0.65rem] font-black uppercase px-2 py-0.5 rounded bg-green-100 text-green-700">
                Vigente
              </span>
            </div>
          ))
        )}
      </div>

      <AppDrawer
        open={showDrawer}
        onClose={() => setShowDrawer(false)}
        description="Selecciona un convenio o descuento para aplicar sobre la suscripción/renovación del socio."
        title="Asignar Convenio / Beneficio"
      >
        <form className="grid gap-4" onSubmit={handleAssignBenefit}>
          {error ? <p className="p-3 text-xs font-bold text-red-600 bg-red-50 rounded-lg">{error}</p> : null}

          <label className="grid gap-2 text-sm font-bold text-gray-700">
            Convenio o Beneficio
            <select
              className="app-input"
              value={selectedBenefitId}
              onChange={(e) => setSelectedBenefitId(e.target.value)}
              required
            >
              <option value="" disabled>Selecciona un beneficio...</option>
              {availableBenefits.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.name} ({b.benefitType})
                </option>
              ))}
            </select>
          </label>

          {selectedBenefit && isDiscountBenefit(selectedBenefit) ? (
            <label className="grid gap-2 text-sm font-bold text-gray-700">
              Fecha de Expiración Obligatoria (Hasta cuándo estará vigente)
              <input
                className="app-input"
                type="date"
                value={validUntil}
                onChange={(e) => setValidUntil(e.target.value)}
                required
              />
              <span className="text-[0.7rem] text-gray-500 font-normal">
                Evita descuentos permanentes definiendo la fecha límite del beneficio.
              </span>
            </label>
          ) : null}

          <button
            type="submit"
            disabled={saving || !selectedBenefitId}
            className="app-button w-full mt-2"
          >
            {saving ? "Guardando..." : "Confirmar Asignación"}
          </button>
        </form>
      </AppDrawer>
    </section>
  );
}
