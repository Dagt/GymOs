"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import { AppShell, LoadingScreen } from "@/components/AppShell";
import { type FieldErrorMap } from "@/components/FormFields";
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
import {
  emptyBranchForm,
  normalizeBranchForm,
  normalizeOrganizationForm,
  toBranchForm,
  toOrganizationForm,
  validateBranchForm,
  validateOrganizationForm,
} from "./OrganizationSettingsHelpers";
import {
  BranchesOverviewCard,
  EditBranchDrawer,
  NewBranchDrawer,
  OrganizationDrawer,
  OrganizationOverviewCard,
  SettingsSummary,
  TeamAccessCard,
} from "./OrganizationSettingsSections";

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
  const [fieldErrors, setFieldErrors] = useState<FieldErrorMap>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [showOrganizationDrawer, setShowOrganizationDrawer] = useState(false);
  const [showNewBranchDrawer, setShowNewBranchDrawer] = useState(false);
  const [showEditBranchDrawer, setShowEditBranchDrawer] = useState(false);

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
    const errors = validateOrganizationForm(organizationForm);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const response = await updateOrganization(session.accessToken, normalizeOrganizationForm(organizationForm));
      setOrganization(response);
      setOrganizationForm(toOrganizationForm(response));
      setShowOrganizationDrawer(false);
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
    const errors = validateBranchForm(branchForm, "branch");
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const response = await updateBranch(session.accessToken, selectedBranchId, normalizeBranchForm(branchForm));
      setBranches((current) => current.map((branch) => (branch.id === response.id ? response : branch)));
      setBranchForm(toBranchForm(response));
      setShowEditBranchDrawer(false);
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
    const errors = validateBranchForm(newBranchForm, "newBranch");
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const response = await createBranch(session.accessToken, normalizeBranchForm(newBranchForm));
      setBranches((current) => [...current, response].sort((left, right) => left.name.localeCompare(right.name)));
      setSelectedBranchId(response.id);
      setBranchForm(toBranchForm(response));
      setNewBranchForm(emptyBranchForm);
      setShowNewBranchDrawer(false);
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

  function openOrganizationDrawer() {
    setFieldErrors({});
    setShowOrganizationDrawer(true);
  }

  function openNewBranchDrawer() {
    setFieldErrors({});
    setShowNewBranchDrawer(true);
  }

  function openEditBranch(branchId: string) {
    selectEditableBranch(branchId);
    setFieldErrors({});
    setShowEditBranchDrawer(true);
  }

  if (!session) {
    return <AuthRequired />;
  }

  if (loading) {
    return <LoadingScreen text="Cargando datos y sucursales..." />;
  }

  if (!organizationForm) {
    return (
      <AppShell
        branchName={activeBranch?.name}
        eyebrow="Administracion"
        mode={activeBranch ? "operation" : "setup"}
        organizationName={session.user.organizationName}
        session={session}
        subtitle="Administra la cadena o gimnasio, crea varias sucursales y prepara el acceso del equipo."
        title="Datos y sucursales"
      >
        <p className="form-message error">
          {error ?? "No fue posible cargar los datos del gimnasio. Cierra sesion y vuelve a iniciar si el problema persiste."}
        </p>
      </AppShell>
    );
  }

  return (
    <AppShell
      branchName={activeBranch?.name}
      eyebrow="Administracion"
      mode={activeBranch ? "operation" : "setup"}
      organizationName={organization?.tradeName}
      session={session}
      subtitle="Administra la cadena o gimnasio, crea varias sucursales y prepara el acceso del equipo."
      title="Datos y sucursales"
    >
      <SettingsSummary
        activeBranchName={activeBranch?.name}
        branchCount={branches.length}
        legalName={organization?.legalName}
        tradeName={organization?.tradeName}
      />

      {error ? <p className="form-message error">{error}</p> : null}
      {message ? <p className="form-message success">{message}</p> : null}

      <section className="settings-forms-grid">
        <OrganizationOverviewCard form={organizationForm} onEdit={openOrganizationDrawer} />
        <BranchesOverviewCard branches={branches} onEditBranch={openEditBranch} onNewBranch={openNewBranchDrawer} />
        <TeamAccessCard />
      </section>

      <OrganizationDrawer
        errors={fieldErrors}
        form={organizationForm}
        open={showOrganizationDrawer}
        saving={saving}
        onChange={setOrganizationForm}
        onClose={() => setShowOrganizationDrawer(false)}
        onSubmit={submitOrganization}
      />

      <NewBranchDrawer
        errors={fieldErrors}
        form={newBranchForm}
        open={showNewBranchDrawer}
        saving={saving}
        onChange={setNewBranchForm}
        onClose={() => setShowNewBranchDrawer(false)}
        onSubmit={submitNewBranch}
      />

      <EditBranchDrawer
        branchId={selectedBranchId}
        branches={branches}
        errors={fieldErrors}
        form={branchForm}
        open={showEditBranchDrawer}
        saving={saving}
        onBranchSelect={selectEditableBranch}
        onChange={setBranchForm}
        onClose={() => setShowEditBranchDrawer(false)}
        onSubmit={submitBranch}
      />
    </AppShell>
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
