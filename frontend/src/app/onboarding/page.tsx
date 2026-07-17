"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
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
  description: "Producto básico para venta rápida en recepción.",
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
  const [kioskTerminalCode, setKioskTerminalCode] = useState("KIOSK-MAIN");
  const [kioskName, setKioskName] = useState("Kiosco principal");
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
        kioskTerminalCode,
        kioskName,
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

  if (!session) {
    return <AuthRequired />;
  }

  if (loading || !organization || !branch || !checklist) {
    return (
      <main className="grid min-h-screen place-items-center bg-[#f6f7f2] px-6 text-[#111815]">
        <p className="rounded-md border border-[#d8ddd2] bg-white px-4 py-3 text-sm text-[#42514a]">
          Cargando onboarding...
        </p>
      </main>
    );
  }

  return (
    <main className="min-h-screen bg-[#f6f7f2] text-[#111815]">
      <header className="border-b border-[#d8ddd2] bg-white">
        <div className="mx-auto flex max-w-7xl flex-col gap-4 px-6 py-5 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <Link className="text-sm font-semibold text-[#51735f]" href="/reception">
              Recepcion
            </Link>
            <h1 className="mt-1 text-3xl font-semibold">Onboarding piloto</h1>
            <p className="mt-1 text-sm text-[#5d6b63]">
              Configura datos comerciales minimos para mostrar y operar GymOS con un gimnasio real.
            </p>
          </div>
          <span className="rounded-md border border-[#d8ddd2] bg-white px-3 py-2 text-sm text-[#42514a]">
            {checklist.readyForPilot ? "Listo para piloto" : "Setup incompleto"}
          </span>
        </div>
      </header>

      <section className="mx-auto grid max-w-7xl gap-6 px-6 py-6 lg:grid-cols-[0.9fr_1.3fr]">
        <aside className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
          <h2 className="text-xl font-semibold">Checklist vendible</h2>
          <div className="mt-5 grid gap-3">
            <ChecklistItem done={checklist.organizationProfileComplete} label="Organizacion con contacto" value={organization.tradeName} />
            <ChecklistItem done={checklist.branchConfigured} label="Sucursal con teléfono" value={branch.name} />
            <ChecklistItem done={checklist.hasMembershipPlans} label="Planes activos" value={`${checklist.activePlanCount}`} />
            <ChecklistItem done={checklist.hasRetailProducts} label="Productos vendibles" value={`${checklist.activeProductCount}`} />
            <ChecklistItem done={checklist.hasKioskTerminal} label="Kiosco activo" value={`${checklist.activeKioskCount}`} />
            <ChecklistItem done={checklist.hasClients} label="Clientes cargados" value={`${checklist.clientCount}`} />
          </div>
          <Link
            className="mt-5 inline-flex h-10 items-center justify-center rounded-md border border-[#cdd5ca] px-4 text-sm font-semibold text-[#13251c] transition hover:bg-[#eef2ea]"
            href="/clients/import"
          >
            Importar clientes
          </Link>
        </aside>

        <form className="grid gap-6" onSubmit={submit}>
          <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
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

          <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
            <h2 className="text-xl font-semibold">Catalogo inicial</h2>
            <div className="mt-5 grid gap-4 sm:grid-cols-2">
              <Field label="SKU plan" value={plan.sku} onChange={(value) => setPlan({ ...plan, sku: value })} />
              <Field label="Nombre plan" value={plan.name} onChange={(value) => setPlan({ ...plan, name: value })} />
              <NumberField label="Precio plan en centavos" value={plan.priceCents} onChange={(value) => setPlan({ ...plan, priceCents: value })} />
              <NumberField label="Duracion dias" value={plan.durationDays} onChange={(value) => setPlan({ ...plan, durationDays: value })} />
              <Field label="SKU producto" value={product.sku} onChange={(value) => setProduct({ ...product, sku: value })} />
              <Field label="Nombre producto" value={product.name} onChange={(value) => setProduct({ ...product, name: value })} />
              <NumberField label="Precio producto en centavos" value={product.priceCents} onChange={(value) => setProduct({ ...product, priceCents: value })} />
              <Field label="Codigo kiosco" value={kioskTerminalCode} onChange={setKioskTerminalCode} />
              <Field label="Nombre kiosco" value={kioskName} onChange={setKioskName} />
            </div>
            <button
              className="mt-5 h-11 rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white transition hover:bg-[#243d30] disabled:opacity-60"
              disabled={saving}
              type="submit"
            >
              Aplicar setup piloto
            </button>
          </section>

          {error ? <Message tone="error" text={error} /> : null}
          {result ? (
            <Message
              tone="success"
              text={`Setup aplicado. Productos creados: ${result.productsCreated}. Planes creados: ${result.plansCreated}. Kiosco nuevo: ${result.kioskCreated ? "si" : "no"}.`}
            />
          ) : null}
        </form>
      </section>
    </main>
  );
}

function ChecklistItem({ done, label, value }: { done: boolean; label: string; value: string }) {
  return (
    <div className="flex items-center justify-between rounded-md border border-[#d8ddd2] px-3 py-2">
      <div>
        <p className="text-sm font-semibold">{label}</p>
        <p className="text-xs text-[#5d6b63]">{value}</p>
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
        className="h-11 rounded-md border border-[#cdd5ca] px-3 text-sm outline-none transition focus:border-[#51735f]"
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
        className="h-11 rounded-md border border-[#cdd5ca] px-3 text-sm outline-none transition focus:border-[#51735f]"
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
    ? "border-[#bad9c4] bg-[#eff8f1] text-[#31563c]"
    : "border-[#e5b7b7] bg-[#fff4f4] text-[#8c2f2f]";
  return <p className={`rounded-md border px-3 py-2 text-sm ${className}`}>{text}</p>;
}

function AuthRequired() {
  return (
    <main className="grid min-h-screen place-items-center bg-[#f6f7f2] px-6 text-[#111815]">
      <section className="w-full max-w-md rounded-lg border border-[#d8ddd2] bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-semibold">Necesitas iniciar sesión</h1>
        <Link
          className="mt-5 inline-flex h-11 items-center justify-center rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white transition hover:bg-[#243d30]"
          href="/login"
        >
          Iniciar sesion
        </Link>
      </section>
    </main>
  );
}
