"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import { AppShell, LoadingScreen } from "@/components/AppShell";
import {
  createBranch,
  getBranches,
  getCurrentOrganization,
  getStoredSession,
  updateBranch,
  updateOrganization,
  type BranchSummary,
  type BranchUpdatePayload,
  type CreateBranchPayload,
  type OrganizationSummary,
  type OrganizationUpdatePayload,
  type StoredSession,
} from "@/lib/api";

const emptyBranchForm: CreateBranchPayload = {
  name: "",
  code: "",
  timezone: "America/Mexico_City",
  phone: "",
};

export default function OrganizationSettingsPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [organization, setOrganization] = useState<OrganizationSummary | null>(null);
  const [branches, setBranches] = useState<BranchSummary[]>([]);
  const [selectedBranchId, setSelectedBranchId] = useState("");
  const [organizationForm, setOrganizationForm] = useState<OrganizationUpdatePayload | null>(null);
  const [branchForm, setBranchForm] = useState<BranchUpdatePayload | null>(null);
  const [newBranchForm, setNewBranchForm] = useState<CreateBranchPayload>(emptyBranchForm);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let active = true;

    async function loadInitial() {
      const storedSession = getStoredSession();
      if (!storedSession) {
        setLoading(false);
        return;
      }

      setSession(storedSession);
      setLoading(true);
      setError(null);
      try {
        const [currentOrganization, availableBranches] = await Promise.all([
          getCurrentOrganization(storedSession.accessToken),
          getBranches(storedSession.accessToken),
        ]);
        if (!active) {
          return;
        }
        setOrganization(currentOrganization);
        setBranches(availableBranches);
        setOrganizationForm(toOrganizationForm(currentOrganization));
        const branch = availableBranches.find((item) => item.id === storedSession.user.branchId) ?? availableBranches[0];
        if (branch) {
          setSelectedBranchId(branch.id);
          setBranchForm(toBranchForm(branch));
        }
      } catch (exception) {
        if (active) {
          setError(exception instanceof Error ? exception.message : "No fue posible cargar configuracion.");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void loadInitial();

    return () => {
      active = false;
    };
  }, []);

  const activeBranch = branches.find((branch) => branch.id === session?.user.branchId) ?? null;

  async function submitOrganization(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || !organizationForm) {
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const response = await updateOrganization(session.accessToken, organizationForm);
      setOrganization(response);
      setOrganizationForm(toOrganizationForm(response));
      setMessage("Datos del gimnasio actualizados.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible actualizar el gimnasio.");
    } finally {
      setSaving(false);
    }
  }

  async function submitBranch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || !branchForm || !selectedBranchId) {
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const response = await updateBranch(session.accessToken, selectedBranchId, branchForm);
      setBranches((current) => current.map((branch) => (branch.id === response.id ? response : branch)));
      setBranchForm(toBranchForm(response));
      setMessage("Sucursal actualizada.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible actualizar la sucursal.");
    } finally {
      setSaving(false);
    }
  }

  async function submitNewBranch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) {
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const response = await createBranch(session.accessToken, newBranchForm);
      setBranches((current) => [...current, response].sort((left, right) => left.name.localeCompare(right.name)));
      setSelectedBranchId(response.id);
      setBranchForm(toBranchForm(response));
      setNewBranchForm(emptyBranchForm);
      setMessage("Sucursal creada. Ya puedes asignarla a usuarios o seleccionarla para operar.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear la sucursal.");
    } finally {
      setSaving(false);
    }
  }

  function selectEditableBranch(branchId: string) {
    setSelectedBranchId(branchId);
    const branch = branches.find((item) => item.id === branchId);
    setBranchForm(branch ? toBranchForm(branch) : null);
  }

  if (!session) {
    return <AuthRequired />;
  }

  if (loading || !organizationForm) {
    return <LoadingScreen text="Cargando datos y sucursales..." />;
  }

  return (
    <AppShell
      actions={[
        { href: "/admin", label: "Gimnasios" },
        { href: "/users", label: "Equipo y accesos" },
      ]}
      branchName={activeBranch?.name}
      eyebrow="Administracion"
      mode={activeBranch ? "operation" : "setup"}
      organizationName={organization?.tradeName}
      session={session}
      subtitle="Administra la cadena o gimnasio, crea varias sucursales y prepara el acceso del equipo."
      title="Datos y sucursales"
    >
      <section className="settings-summary">
        <article>
          <span>Gimnasio actual</span>
          <strong>{organization?.tradeName}</strong>
          <p>{organization?.legalName}</p>
        </article>
        <article>
          <span>Sucursales</span>
          <strong>{branches.length}</strong>
          <p>{branches.length === 1 ? "Una sucursal registrada." : "Sucursales registradas para este gimnasio."}</p>
        </article>
        <article>
          <span>Sucursal operativa</span>
          <strong>{activeBranch?.name ?? "Sin seleccionar"}</strong>
          <p>Se elige desde Gimnasios para entrar al contexto de trabajo.</p>
        </article>
      </section>

      {error ? <p className="form-message error">{error}</p> : null}
      {message ? <p className="form-message success">{message}</p> : null}

      <section className="settings-forms-grid">
        <section className="app-card p-5">
          <p className="app-eyebrow">Gimnasio</p>
          <h2 className="mt-2 text-xl font-semibold">Datos comerciales</h2>
          <p className="mt-2 text-sm leading-6 text-[#5d6b63]">
            Esta informacion identifica al gimnasio o cadena que estas administrando.
          </p>
          <form className="mt-5 grid gap-4" onSubmit={submitOrganization}>
            <Field label="Nombre legal" value={organizationForm.legalName} onChange={(value) => setOrganizationForm({ ...organizationForm, legalName: value })} />
            <Field label="Nombre comercial" value={organizationForm.tradeName} onChange={(value) => setOrganizationForm({ ...organizationForm, tradeName: value })} />
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="RFC / Tax ID" value={organizationForm.taxId ?? ""} onChange={(value) => setOrganizationForm({ ...organizationForm, taxId: value })} />
              <Field label="Moneda" value={organizationForm.defaultCurrency} onChange={(value) => setOrganizationForm({ ...organizationForm, defaultCurrency: value })} />
              <Field label="Zona horaria" value={organizationForm.timezone} onChange={(value) => setOrganizationForm({ ...organizationForm, timezone: value })} />
              <Field label="Telefono" value={organizationForm.contactPhone ?? ""} onChange={(value) => setOrganizationForm({ ...organizationForm, contactPhone: value })} />
            </div>
            <Field label="Email de contacto" type="email" value={organizationForm.contactEmail ?? ""} onChange={(value) => setOrganizationForm({ ...organizationForm, contactEmail: value })} />
            <button className="form-primary-button" disabled={saving} type="submit">
              Guardar gimnasio
            </button>
          </form>
        </section>

        <section className="app-card p-5">
          <p className="app-eyebrow">Sucursales</p>
          <h2 className="mt-2 text-xl font-semibold">Crear nueva sucursal</h2>
          <p className="mt-2 text-sm leading-6 text-[#5d6b63]">
            Cada sucursal puede tener usuarios, caja, reportes y operacion propios.
          </p>
          <form className="mt-5 grid gap-4" onSubmit={submitNewBranch}>
            <Field label="Nombre de sucursal" value={newBranchForm.name} onChange={(value) => setNewBranchForm({ ...newBranchForm, name: value })} />
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Codigo corto" value={newBranchForm.code} onChange={(value) => setNewBranchForm({ ...newBranchForm, code: value })} />
              <Field label="Telefono" value={newBranchForm.phone ?? ""} onChange={(value) => setNewBranchForm({ ...newBranchForm, phone: value })} />
            </div>
            <Field label="Zona horaria" value={newBranchForm.timezone} onChange={(value) => setNewBranchForm({ ...newBranchForm, timezone: value })} />
            <button className="form-primary-button" disabled={saving} type="submit">
              Crear sucursal
            </button>
          </form>
        </section>

        <section className="app-card p-5">
          <p className="app-eyebrow">Sucursal editable</p>
          <h2 className="mt-2 text-xl font-semibold">Editar sucursal existente</h2>
          {branches.length === 0 ? (
            <p className="mt-4 text-sm leading-6 text-[#5d6b63]">
              Aun no hay sucursales. Crea la primera para poder operar.
            </p>
          ) : (
            <form className="mt-5 grid gap-4" onSubmit={submitBranch}>
              <label className="grid gap-2 text-sm font-medium text-[#42514a]">
                Sucursal
                <select
                  className="h-11 rounded-md border border-[#cdd5ca] bg-white px-3 text-sm outline-none transition focus:border-[#51735f]"
                  onChange={(event) => selectEditableBranch(event.target.value)}
                  value={selectedBranchId}
                >
                  {branches.map((branch) => (
                    <option key={branch.id} value={branch.id}>
                      {branch.name} ({branch.code})
                    </option>
                  ))}
                </select>
              </label>
              {branchForm ? (
                <>
                  <Field label="Nombre" value={branchForm.name} onChange={(value) => setBranchForm({ ...branchForm, name: value })} />
                  <div className="grid gap-4 sm:grid-cols-2">
                    <Field label="Codigo" value={branchForm.code} onChange={(value) => setBranchForm({ ...branchForm, code: value })} />
                    <Field label="Telefono" value={branchForm.phone ?? ""} onChange={(value) => setBranchForm({ ...branchForm, phone: value })} />
                  </div>
                  <Field label="Zona horaria" value={branchForm.timezone} onChange={(value) => setBranchForm({ ...branchForm, timezone: value })} />
                  <button className="form-primary-button" disabled={saving} type="submit">
                    Guardar sucursal
                  </button>
                </>
              ) : null}
            </form>
          )}
        </section>

        <section className="app-card p-5">
          <p className="app-eyebrow">Equipo</p>
          <h2 className="mt-2 text-xl font-semibold">Usuarios y permisos</h2>
          <p className="mt-2 text-sm leading-6 text-[#5d6b63]">
            Crea cuentas para recepcion, gerencia, coaches y operacion. Cada usuario vera una experiencia segun sus permisos.
          </p>
          <div className="mt-5 grid gap-3">
            <Link className="admin-secondary-link" href="/users">
              Abrir equipo y accesos
            </Link>
            <p className="text-sm leading-6 text-[#5d6b63]">
              Primero crea las sucursales necesarias. Despues asigna a cada persona solo las sucursales que debe usar.
            </p>
          </div>
        </section>
      </section>
    </AppShell>
  );
}

function toOrganizationForm(organization: OrganizationSummary): OrganizationUpdatePayload {
  return {
    legalName: organization.legalName,
    tradeName: organization.tradeName,
    taxId: organization.taxId ?? "",
    timezone: organization.timezone,
    defaultCurrency: organization.defaultCurrency,
    contactEmail: organization.contactEmail ?? "",
    contactPhone: organization.contactPhone ?? "",
  };
}

function toBranchForm(branch: BranchSummary): BranchUpdatePayload {
  return {
    name: branch.name,
    code: branch.code,
    timezone: branch.timezone,
    phone: branch.phone ?? "",
  };
}

function Field({
  label,
  value,
  onChange,
  type = "text",
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: string;
}) {
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

function AuthRequired() {
  return (
    <main className="grid min-h-screen place-items-center bg-[#f6f7f2] px-6 text-[#111815]">
      <section className="w-full max-w-md rounded-lg border border-[#d8ddd2] bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-semibold">Necesitas iniciar sesion</h1>
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
