"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import { AppShell, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import {
  applyPilotSetup,
  getBranches,
  getCurrentOrganization,
  getOnboardingChecklist,
  getStoredSession,
  type BranchUpdatePayload,
  type OnboardingChecklist,
  type OrganizationUpdatePayload,
  type PilotSetupPayload,
  type PilotSetupResult,
  type StoredSession,
} from "@/lib/api";

const defaultPlan = {
  sku: "PLAN-PILOTO-MENSUAL",
  name: "Membresia mensual piloto",
  description: "Acceso general mensual configurado desde onboarding.",
  priceCents: 69900,
  currency: "MXN",
  durationDays: 30,
  graceDays: 3,
  benefitsJson: "{\"access\":\"general\",\"sessions\":\"unlimited\"}",
};

const defaultProduct = {
  sku: "AGUA-PILOTO",
  name: "Botella de agua",
  description: "Producto basico para venta rapida en recepcion.",
  productType: "PHYSICAL" as const,
  priceCents: 2500,
  currency: "MXN",
  taxable: true,
};

export default function OnboardingPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [checklist, setChecklist] = useState<OnboardingChecklist | null>(null);
  const [organization, setOrganization] = useState<OrganizationUpdatePayload | null>(null);
  const [branch, setBranch] = useState<BranchUpdatePayload | null>(null);
  const [plan, setPlan] = useState(defaultPlan);
  const [product, setProduct] = useState(defaultProduct);
  const [result, setResult] = useState<PilotSetupResult | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function load() {
      const stored = getStoredSession();
      if (!stored) {
        setLoading(false);
        return;
      }
      setSession(stored);
      try {
        const [currentOrganization, branches, currentChecklist] = await Promise.all([
          getCurrentOrganization(stored.accessToken),
          getBranches(stored.accessToken),
          getOnboardingChecklist(stored.accessToken),
        ]);
        if (!active) {
          return;
        }
        const activeBranch = branches.find((item) => item.id === stored.user.branchId) ?? branches[0];
        setOrganization({
          legalName: currentOrganization.legalName,
          tradeName: currentOrganization.tradeName,
          taxId: currentOrganization.taxId ?? "",
          timezone: currentOrganization.timezone,
          defaultCurrency: currentOrganization.defaultCurrency,
          contactEmail: currentOrganization.contactEmail ?? "",
          contactPhone: currentOrganization.contactPhone ?? "",
        });
        if (activeBranch) {
          setBranch({
            name: activeBranch.name,
            code: activeBranch.code,
            timezone: activeBranch.timezone,
            phone: activeBranch.phone ?? "",
          });
        }
        setChecklist(currentChecklist);
      } catch (exception) {
        if (active) {
          setError(exception instanceof Error ? exception.message : "No fue posible cargar onboarding.");
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
    if (!session || !organization || !branch) {
      return;
    }
    setSaving(true);
    setError(null);
    setResult(null);
    try {
      const payload: PilotSetupPayload = {
        organization,
        branch,
        plans: [plan],
        products: [product],
        kioskTerminalCode: "KIOSK-MAIN",
        kioskName: "Kiosco de sucursal",
      };
      const response = await applyPilotSetup(session.accessToken, payload);
      setChecklist(response.checklist);
      setResult(response);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible aplicar setup piloto.");
    } finally {
      setSaving(false);
    }
  }

  if (!session && !loading) {
    return <AuthRequiredCard message="Necesitas una sesion activa para preparar el onboarding." title="Inicia sesion para continuar" />;
  }

  if (loading || !session || !organization || !branch || !checklist) {
    return <LoadingScreen text="Cargando onboarding..." />;
  }

  return (
    <AppShell
      eyebrow="Configuracion"
      session={session}
      subtitle="Configura datos comerciales minimos para mostrar y operar GymOS con un gimnasio real."
      title="Onboarding operativo"
    >
      <section className="grid gap-6 lg:grid-cols-[0.9fr_1.3fr]">
        <aside className="rounded-lg border border-[var(--brand-line)] bg-white p-5 shadow-sm">
          <div className="flex items-start justify-between gap-3">
            <div>
              <p className="app-eyebrow">Checklist</p>
              <h2 className="mt-2 text-xl font-semibold">Base operativa</h2>
            </div>
            <span className="rounded-md border border-[var(--brand-line)] bg-white px-3 py-2 text-sm text-[#42514a]">
              {checklist.readyForPilot ? "Listo" : "Incompleto"}
            </span>
          </div>
          <div className="mt-5 grid gap-3">
            <ChecklistItem done={checklist.organizationProfileComplete} label="Organizacion con contacto" value={organization.tradeName} />
            <ChecklistItem done={checklist.branchConfigured} label="Sucursal con telefono" value={branch.name} />
            <ChecklistItem done={checklist.hasMembershipPlans} label="Planes activos" value={`${checklist.activePlanCount}`} />
            <ChecklistItem done={checklist.hasRetailProducts} label="Productos vendibles" value={`${checklist.activeProductCount}`} />
            <ChecklistItem done={checklist.hasKioskTerminal} label="Kiosco activo" value={`${checklist.activeKioskCount}`} />
            <ChecklistItem done={checklist.hasClients} label="Socios cargados" value={`${checklist.clientCount}`} />
          </div>
          <Link className="app-button-secondary mt-5" href="/clients/import">
            Importar socios
          </Link>
        </aside>

        <form className="grid gap-6" onSubmit={submit}>
          <section className="rounded-lg border border-[var(--brand-line)] bg-white p-5 shadow-sm">
            <h2 className="text-xl font-semibold">Gimnasio y sucursal</h2>
            <div className="mt-5 grid gap-4 sm:grid-cols-2">
              <Field label="Nombre legal" value={organization.legalName} onChange={(value) => setOrganization({ ...organization, legalName: value })} />
              <Field label="Nombre comercial" value={organization.tradeName} onChange={(value) => setOrganization({ ...organization, tradeName: value })} />
              <Field label="RFC / Tax ID" value={organization.taxId ?? ""} onChange={(value) => setOrganization({ ...organization, taxId: value })} />
              <Field label="Moneda" value={organization.defaultCurrency} onChange={(value) => setOrganization({ ...organization, defaultCurrency: value })} />
              <Field label="Email contacto" type="email" value={organization.contactEmail ?? ""} onChange={(value) => setOrganization({ ...organization, contactEmail: value })} />
              <Field label="Telefono contacto" value={organization.contactPhone ?? ""} onChange={(value) => setOrganization({ ...organization, contactPhone: value })} />
              <Field label="Sucursal" value={branch.name} onChange={(value) => setBranch({ ...branch, name: value })} />
              <Field label="Codigo sucursal" value={branch.code} onChange={(value) => setBranch({ ...branch, code: value })} />
              <Field label="Telefono sucursal" value={branch.phone ?? ""} onChange={(value) => setBranch({ ...branch, phone: value })} />
              <Field label="Zona horaria" value={branch.timezone} onChange={(value) => setBranch({ ...branch, timezone: value })} />
            </div>
          </section>

          <section className="rounded-lg border border-[var(--brand-line)] bg-white p-5 shadow-sm">
            <h2 className="text-xl font-semibold">Catalogo inicial</h2>
            <div className="mt-5 grid gap-4 sm:grid-cols-2">
              <Field label="SKU plan" value={plan.sku} onChange={(value) => setPlan({ ...plan, sku: value })} />
              <Field label="Nombre plan" value={plan.name} onChange={(value) => setPlan({ ...plan, name: value })} />
              <NumberField label="Precio plan en centavos" value={plan.priceCents} onChange={(value) => setPlan({ ...plan, priceCents: value })} />
              <NumberField label="Duracion dias" value={plan.durationDays} onChange={(value) => setPlan({ ...plan, durationDays: value })} />
              <Field label="SKU producto" value={product.sku} onChange={(value) => setProduct({ ...product, sku: value })} />
              <Field label="Nombre producto" value={product.name} onChange={(value) => setProduct({ ...product, name: value })} />
              <NumberField label="Precio producto en centavos" value={product.priceCents} onChange={(value) => setProduct({ ...product, priceCents: value })} />
            </div>
            <p className="mt-4 rounded-md border border-[var(--brand-line)] bg-[var(--brand-surface)] p-3 text-sm text-[var(--brand-muted)]">
              Se creara automaticamente un perfil logico de kiosco para la sucursal. No necesitas registrar ni asignar cada computadora.
            </p>
            <button className="form-primary-button mt-5" disabled={saving} type="submit">
              Aplicar setup operativo
            </button>
          </section>

          {error ? <Message tone="error" text={error} /> : null}
          {result ? (
            <Message
              tone="success"
              text={`Setup aplicado. Productos creados: ${result.productsCreated}. Planes creados: ${result.plansCreated}. Perfil de kiosco nuevo: ${result.kioskCreated ? "si" : "no"}.`}
            />
          ) : null}
        </form>
      </section>
    </AppShell>
  );
}

function ChecklistItem({ done, label, value }: { done: boolean; label: string; value: string }) {
  return (
    <div className="flex items-center justify-between rounded-md border border-[var(--brand-line)] px-3 py-2">
      <div>
        <p className="text-sm font-semibold">{label}</p>
        <p className="text-xs text-[var(--brand-muted)]">{value}</p>
      </div>
      <span className={`rounded-md px-2 py-1 text-xs font-semibold ${done ? "bg-[#e4f4e9] text-[#31563c]" : "bg-[#fff2d6] text-[#7a5018]"}`}>
        {done ? "Listo" : "Falta"}
      </span>
    </div>
  );
}

function Field({ label, value, onChange, type = "text" }: { label: string; value: string; onChange: (value: string) => void; type?: string }) {
  return (
    <label className="grid gap-2 text-sm font-medium text-[#42514a]">
      {label}
      <input
        className="h-11 rounded-md border border-[var(--brand-line)] px-3 text-sm outline-none transition focus:border-[#51735f]"
        onChange={(event) => onChange(event.target.value)}
        type={type}
        value={value}
      />
    </label>
  );
}

function NumberField({ label, value, onChange }: { label: string; value: number; onChange: (value: number) => void }) {
  return (
    <label className="grid gap-2 text-sm font-medium text-[#42514a]">
      {label}
      <input
        className="h-11 rounded-md border border-[var(--brand-line)] px-3 text-sm outline-none transition focus:border-[#51735f]"
        min={0}
        onChange={(event) => onChange(Number(event.target.value))}
        type="number"
        value={value}
      />
    </label>
  );
}

function Message({ tone, text }: { tone: "success" | "error"; text: string }) {
  const className = tone === "success"
    ? "border-[var(--brand-line)] bg-[var(--brand-surface)] text-[#31563c]"
    : "border-[#e5b7b7] bg-[#fff4f4] text-[#8c2f2f]";
  return <p className={`rounded-md border px-3 py-2 text-sm ${className}`}>{text}</p>;
}
