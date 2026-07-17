"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useState } from "react";
import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AuthRequired } from "@/components/AuthRequired";
import {
  createOrganization,
  getBranches,
  getCurrentOrganization,
  getOrganizations,
  getStoredSession,
  selectBranch,
  selectOrganization,
  storeSession,
  type BranchSummary,
  type CreateOrganizationPayload,
  type ManagedOrganizationSummary,
  type OrganizationSummary,
  type StoredSession,
} from "@/lib/api";

const emptyOrganizationForm: CreateOrganizationPayload = {
  legalName: "",
  tradeName: "",
  taxId: "",
  timezone: "America/Mexico_City",
  defaultCurrency: "MXN",
  contactEmail: "",
  contactPhone: "",
  firstBranchName: "",
  firstBranchCode: "",
  firstBranchPhone: "",
};

export default function AdminPage() {
  const router = useRouter();
  const [session, setSession] = useState<StoredSession | null>(null);
  const [organization, setOrganization] = useState<OrganizationSummary | null>(null);
  const [organizations, setOrganizations] = useState<ManagedOrganizationSummary[]>([]);
  const [branches, setBranches] = useState<BranchSummary[]>([]);
  const [form, setForm] = useState<CreateOrganizationPayload>(emptyOrganizationForm);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [selectingId, setSelectingId] = useState<string | null>(null);

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
        const [currentOrganization, availableOrganizations, availableBranches] = await Promise.all([
          getCurrentOrganization(stored.accessToken),
          getOrganizations(stored.accessToken),
          getBranches(stored.accessToken),
        ]);
        if (!active) {
          return;
        }
        setOrganization(currentOrganization);
        setOrganizations(availableOrganizations);
        setBranches(availableBranches);
      } catch (exception) {
        if (active) {
          setError(exception instanceof Error ? exception.message : "No fue posible cargar gimnasios.");
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

  async function activateOrganization(organizationId: string) {
    if (!session) {
      return;
    }

    setSelectingId(organizationId);
    setError(null);
    setMessage(null);
    try {
      const nextSession = await selectOrganization(session.accessToken, organizationId);
      storeSession(nextSession);
      setSession(nextSession);
      const [currentOrganization, availableBranches] = await Promise.all([
        getCurrentOrganization(nextSession.accessToken),
        getBranches(nextSession.accessToken),
      ]);
      setOrganization(currentOrganization);
      setBranches(availableBranches);
      setMessage("Gimnasio seleccionado.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible cambiar de gimnasio.");
    } finally {
      setSelectingId(null);
    }
  }

  async function activateBranch(branchId: string) {
    if (!session) {
      return;
    }

    setSelectingId(branchId);
    setError(null);
    setMessage(null);
    try {
      const nextSession = await selectBranch(session.accessToken, branchId);
      storeSession(nextSession);
      setSession(nextSession);
      router.push("/dashboard");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible seleccionar sucursal.");
    } finally {
      setSelectingId(null);
    }
  }

  async function submitOrganization(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) {
      return;
    }

    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const created = await createOrganization(session.accessToken, form);
      setOrganizations((current) => [...current, created].sort((left, right) => left.tradeName.localeCompare(right.tradeName)));
      setForm(emptyOrganizationForm);
      setMessage("Gimnasio creado. Seleccionalo para trabajar en el.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear el gimnasio.");
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return <LoadingScreen text="Cargando gimnasios..." />;
  }

  if (!session) {
    return (
      <AuthRequired
        message="El panel de administracion permite crear gimnasios, cambiar de gimnasio y seleccionar sucursal operativa."
        title="Inicia sesion como administrador"
      />
    );
  }

  const activeBranch = branches.find((branch) => branch.id === session.user.branchId) ?? null;

  return (
    <AppShell
      branchName={activeBranch?.name}
      eyebrow="Administrador de plataforma"
      mode={activeBranch ? "operation" : "setup"}
      organizationName={organization?.tradeName}
      session={session}
      subtitle="Crea gimnasios, cambia entre ellos y selecciona la sucursal operativa del gimnasio activo."
      title="Gimnasios"
    >
      {error ? <InlineError message={error} /> : null}
      {message ? <p className="form-message success">{message}</p> : null}

      <section className="admin-home-grid">
        <section className="admin-home-main">
          <section className="admin-hero-card">
            <div>
              <p>Gimnasio activo</p>
              <h2>{organization?.tradeName ?? "Sin gimnasio seleccionado"}</h2>
              <span>{organization?.legalName ?? "Selecciona o crea un gimnasio para continuar."}</span>
            </div>
            <Link href="/settings/organization">Editar datos y sucursales</Link>
          </section>

          <section className="admin-panel">
            <div className="admin-panel-header">
              <div>
                <p>Gimnasios</p>
                <h2>Cambia de gimnasio</h2>
              </div>
            </div>
            <div className="admin-branch-cards">
              {organizations.map((item) => (
                <article className={item.id === session.user.organizationId ? "active" : undefined} key={item.id}>
                  <div>
                    <p>{item.status}</p>
                    <h3>{item.tradeName}</h3>
                    <span>{item.activeBranchCount} sucursales activas</span>
                  </div>
                  <button
                    disabled={selectingId !== null || item.id === session.user.organizationId}
                    onClick={() => activateOrganization(item.id)}
                    type="button"
                  >
                    {item.id === session.user.organizationId ? "Gimnasio activo" : "Usar este gimnasio"}
                  </button>
                </article>
              ))}
            </div>
          </section>

          <section className="admin-panel">
            <div className="admin-panel-header">
              <div>
                <p>Sucursales del gimnasio activo</p>
                <h2>Selecciona contexto operativo</h2>
              </div>
              <Link href="/settings/organization">Administrar sucursales</Link>
            </div>

            <div className="admin-branch-cards">
              {branches.length === 0 ? (
                <section className="admin-empty-card">
                  <strong>Aun no hay sucursales</strong>
                  <span>Crea la primera sucursal para poder operar caja, socios, membresias y reportes.</span>
                  <Link href="/settings/organization">Crear sucursal</Link>
                </section>
              ) : null}

              {branches.map((branch) => (
                <article className={branch.id === session.user.branchId ? "active" : undefined} key={branch.id}>
                  <div>
                    <p>{branch.code}</p>
                    <h3>{branch.name}</h3>
                    <span>{branch.phone ?? "Sin telefono registrado"}</span>
                  </div>
                  <button
                    disabled={selectingId !== null || branch.id === session.user.branchId}
                    onClick={() => activateBranch(branch.id)}
                    type="button"
                  >
                    {branch.id === session.user.branchId ? "Sucursal activa" : "Usar esta sucursal"}
                  </button>
                </article>
              ))}
            </div>
          </section>
        </section>

        <aside className="admin-home-side">
          <section className="admin-panel">
            <div className="admin-panel-header">
              <div>
                <p>Nuevo gimnasio</p>
                <h2>Alta de gimnasio</h2>
              </div>
            </div>
            <form className="mt-4 grid gap-3" onSubmit={submitOrganization}>
              <Field label="Nombre comercial" value={form.tradeName} onChange={(value) => setForm({ ...form, tradeName: value })} />
              <Field label="Nombre legal" value={form.legalName} onChange={(value) => setForm({ ...form, legalName: value })} />
              <Field label="RFC / Tax ID" required={false} value={form.taxId ?? ""} onChange={(value) => setForm({ ...form, taxId: value })} />
              <div className="grid gap-3 sm:grid-cols-2">
                <Field label="Moneda" value={form.defaultCurrency} onChange={(value) => setForm({ ...form, defaultCurrency: value })} />
                <Field label="Zona horaria" value={form.timezone} onChange={(value) => setForm({ ...form, timezone: value })} />
              </div>
              <Field label="Email contacto" required={false} type="email" value={form.contactEmail ?? ""} onChange={(value) => setForm({ ...form, contactEmail: value })} />
              <Field label="Telefono contacto" required={false} value={form.contactPhone ?? ""} onChange={(value) => setForm({ ...form, contactPhone: value })} />
              <hr className="border-[#dbe4ee]" />
              <Field label="Primera sucursal" value={form.firstBranchName} onChange={(value) => setForm({ ...form, firstBranchName: value })} />
              <div className="grid gap-3 sm:grid-cols-2">
                <Field label="Codigo sucursal" value={form.firstBranchCode} onChange={(value) => setForm({ ...form, firstBranchCode: value })} />
                <Field label="Telefono sucursal" required={false} value={form.firstBranchPhone ?? ""} onChange={(value) => setForm({ ...form, firstBranchPhone: value })} />
              </div>
              <button className="form-primary-button" disabled={saving} type="submit">
                Crear gimnasio
              </button>
            </form>
          </section>

          <section className="admin-panel">
            <div className="admin-panel-header">
              <div>
                <p>Equipo</p>
                <h2>Usuarios por rol</h2>
              </div>
            </div>
            <p className="admin-muted-text">
              Crea cuentas para recepcion, gerencia, coaches y operacion dentro del gimnasio activo.
            </p>
            <Link className="admin-secondary-link" href="/users">
              Abrir equipo y accesos
            </Link>
          </section>
        </aside>
      </section>
    </AppShell>
  );
}

function Field({
  label,
  value,
  onChange,
  type = "text",
  required = true,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: string;
  required?: boolean;
}) {
  return (
    <label className="grid gap-2 text-sm font-medium text-[#42514a]">
      {label}
      <input
        className="h-11 rounded-md border border-[#cdd5ca] px-3 text-sm outline-none transition focus:border-[#51735f]"
        onChange={(event) => onChange(event.target.value)}
        required={required}
        type={type}
        value={value}
      />
    </label>
  );
}
