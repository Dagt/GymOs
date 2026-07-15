"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import {
  getBranches,
  getCurrentOrganization,
  getStoredSession,
  updateBranch,
  updateOrganization,
  type BranchSummary,
  type BranchUpdatePayload,
  type OrganizationSummary,
  type OrganizationUpdatePayload,
  type StoredSession,
} from "@/lib/api";

export default function OrganizationSettingsPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [organization, setOrganization] = useState<OrganizationSummary | null>(null);
  const [branches, setBranches] = useState<BranchSummary[]>([]);
  const [selectedBranchId, setSelectedBranchId] = useState("");
  const [organizationForm, setOrganizationForm] = useState<OrganizationUpdatePayload | null>(null);
  const [branchForm, setBranchForm] = useState<BranchUpdatePayload | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let active = true;

    async function loadInitial() {
      const storedSession = getStoredSession();
      if (!active) {
        return;
      }
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
      setMessage("Organizacion actualizada.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible actualizar organizacion.");
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
      setError(exception instanceof Error ? exception.message : "No fue posible actualizar sucursal.");
    } finally {
      setSaving(false);
    }
  }

  function selectBranch(branchId: string) {
    setSelectedBranchId(branchId);
    const branch = branches.find((item) => item.id === branchId);
    if (branch) {
      setBranchForm(toBranchForm(branch));
    }
  }

  if (!session) {
    return <AuthRequired />;
  }

  if (loading || !organizationForm || !branchForm) {
    return (
      <main className="grid min-h-screen place-items-center bg-[#f6f7f2] px-6 text-[#111815]">
        <p className="rounded-md border border-[#d8ddd2] bg-white px-4 py-3 text-sm text-[#42514a]">
          Cargando configuracion...
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
            <h1 className="mt-1 text-3xl font-semibold">Configuracion inicial</h1>
            <p className="mt-1 text-sm text-[#5d6b63]">
              Organizacion y sucursal activa para dejar listo el gimnasio piloto.
            </p>
          </div>
          <span className="rounded-md border border-[#d8ddd2] bg-white px-3 py-2 text-sm text-[#42514a]">
            {organization?.status ?? "ACTIVE"}
          </span>
        </div>
      </header>

      <section className="mx-auto grid max-w-7xl gap-6 px-6 py-6 lg:grid-cols-2">
        <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
          <h2 className="text-xl font-semibold">Organizacion</h2>
          <form className="mt-5 grid gap-4" onSubmit={submitOrganization}>
            <Field label="Nombre legal" value={organizationForm.legalName} onChange={(value) => setOrganizationForm({ ...organizationForm, legalName: value })} />
            <Field label="Nombre comercial" value={organizationForm.tradeName} onChange={(value) => setOrganizationForm({ ...organizationForm, tradeName: value })} />
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Tax ID / RFC" value={organizationForm.taxId ?? ""} onChange={(value) => setOrganizationForm({ ...organizationForm, taxId: value })} />
              <Field label="Moneda" value={organizationForm.defaultCurrency} onChange={(value) => setOrganizationForm({ ...organizationForm, defaultCurrency: value })} />
              <Field label="Zona horaria" value={organizationForm.timezone} onChange={(value) => setOrganizationForm({ ...organizationForm, timezone: value })} />
              <Field label="Telefono" value={organizationForm.contactPhone ?? ""} onChange={(value) => setOrganizationForm({ ...organizationForm, contactPhone: value })} />
            </div>
            <Field label="Email de contacto" type="email" value={organizationForm.contactEmail ?? ""} onChange={(value) => setOrganizationForm({ ...organizationForm, contactEmail: value })} />
            <button
              className="h-11 rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white transition hover:bg-[#243d30] disabled:opacity-60"
              disabled={saving}
              type="submit"
            >
              Guardar organizacion
            </button>
          </form>
        </section>

        <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
          <h2 className="text-xl font-semibold">Sucursal</h2>
          <form className="mt-5 grid gap-4" onSubmit={submitBranch}>
            <label className="grid gap-2 text-sm font-medium text-[#42514a]">
              Sucursal editable
              <select
                className="h-11 rounded-md border border-[#cdd5ca] bg-white px-3 text-sm outline-none transition focus:border-[#51735f]"
                onChange={(event) => selectBranch(event.target.value)}
                value={selectedBranchId}
              >
                {branches.map((branch) => (
                  <option key={branch.id} value={branch.id}>
                    {branch.name}
                  </option>
                ))}
              </select>
            </label>
            <Field label="Nombre" value={branchForm.name} onChange={(value) => setBranchForm({ ...branchForm, name: value })} />
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Codigo" value={branchForm.code} onChange={(value) => setBranchForm({ ...branchForm, code: value })} />
              <Field label="Telefono" value={branchForm.phone ?? ""} onChange={(value) => setBranchForm({ ...branchForm, phone: value })} />
            </div>
            <Field label="Zona horaria" value={branchForm.timezone} onChange={(value) => setBranchForm({ ...branchForm, timezone: value })} />
            <button
              className="h-11 rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white transition hover:bg-[#243d30] disabled:opacity-60"
              disabled={saving}
              type="submit"
            >
              Guardar sucursal
            </button>
          </form>
        </section>

        {error ? (
          <p className="rounded-md border border-[#e5b7b7] bg-[#fff4f4] px-3 py-2 text-sm text-[#8c2f2f] lg:col-span-2">
            {error}
          </p>
        ) : null}
        {message ? (
          <p className="rounded-md border border-[#bad9c4] bg-[#eff8f1] px-3 py-2 text-sm text-[#31563c] lg:col-span-2">
            {message}
          </p>
        ) : null}
      </section>
    </main>
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
