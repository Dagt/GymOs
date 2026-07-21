"use client";

import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { AppDrawer } from "@/components/AppDrawer";
import { AppShell, LoadingScreen } from "@/components/AppShell";
import { AuthRequired } from "@/components/AuthRequired";
import {
  FieldErrorMap,
  FormError,
  normalizePhone,
  normalizeRfc,
} from "@/components/FormFields";
import {
  createBranch,
  createOrganization,
  getOrganizationBranches,
  getOrganizations,
  getStoredSession,
  selectBranch,
  selectOrganization,
  storeSession,
  updateOrganizationStatus,
  type BranchSummary,
  type CreateBranchPayload,
  type CreateOrganizationPayload,
  type ManagedOrganizationSummary,
  type StoredSession,
} from "@/lib/api";
import {
  BranchForm,
  OrganizationForm,
  emptyBranchForm,
  emptyOrganizationForm,
  validateBranch,
  validateOrganization,
} from "./PlatformGymnasiumForms";

export default function PlatformGymnasiumsPage() {
  const router = useRouter();
  const [session, setSession] = useState<StoredSession | null>(null);
  const [organizations, setOrganizations] = useState<ManagedOrganizationSummary[]>([]);
  const [branches, setBranches] = useState<BranchSummary[]>([]);
  const [branchesLoading, setBranchesLoading] = useState(false);
  const [selectedOrganizationId, setSelectedOrganizationId] = useState("");
  const [query, setQuery] = useState("");
  const [showCreate, setShowCreate] = useState(false);
  const [showCreateBranch, setShowCreateBranch] = useState(false);
  const [form, setForm] = useState<CreateOrganizationPayload>(emptyOrganizationForm);
  const [branchForm, setBranchForm] = useState<CreateBranchPayload>(emptyBranchForm);
  const [fieldErrors, setFieldErrors] = useState<FieldErrorMap>({});
  const [branchFieldErrors, setBranchFieldErrors] = useState<FieldErrorMap>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [selectingId, setSelectingId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const accessToken = session?.accessToken;

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
        const availableOrganizations = await getOrganizations(stored.accessToken);
        if (!active) {
          return;
        }
        setOrganizations(availableOrganizations);
        setSelectedOrganizationId((current) => {
          const preferredId = current || stored.user.organizationId;
          return availableOrganizations.some((organization) => organization.id === preferredId)
            ? preferredId
            : availableOrganizations[0]?.id || "";
        });
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

  useEffect(() => {
    let active = true;

    async function loadBranchesForSelection() {
      if (!accessToken || !selectedOrganizationId) {
        setBranches([]);
        return;
      }
      setBranches([]);
      setBranchesLoading(true);
      setError(null);
      try {
        const selectedBranches = await getOrganizationBranches(accessToken, selectedOrganizationId);
        if (active) {
          setBranches(selectedBranches);
        }
      } catch (exception) {
        if (active) {
          setBranches([]);
          setError(exception instanceof Error ? exception.message : "No fue posible cargar sucursales del gimnasio seleccionado.");
        }
      } finally {
        if (active) {
          setBranchesLoading(false);
        }
      }
    }

    void loadBranchesForSelection();

    return () => {
      active = false;
    };
  }, [accessToken, selectedOrganizationId]);

  const filteredOrganizations = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    if (!normalized) {
      return organizations;
    }
    return organizations.filter((item) =>
      [item.tradeName, item.legalName, item.taxId ?? ""].some((value) => value.toLowerCase().includes(normalized)),
    );
  }, [organizations, query]);

  const selectedOrganization = organizations.find((item) => item.id === selectedOrganizationId) ?? null;
  const activeBranch = branches.find((branch) => branch.id === session?.user.branchId) ?? null;
  const selectedIsActive = Boolean(session && selectedOrganization?.id === session.user.organizationId && session.user.supportMode);

  async function refreshBranches(accessToken: string, organizationId: string): Promise<BranchSummary[]> {
    const availableBranches = await getOrganizationBranches(accessToken, organizationId);
    setBranches(availableBranches);
    return availableBranches;
  }

  async function ensureSupport(organizationId: string): Promise<StoredSession | null> {
    if (!session) {
      return null;
    }
    setSelectingId(organizationId);
    setError(null);
    setMessage(null);
    try {
      let nextSession: StoredSession = session;
      if (session.user.organizationId !== organizationId || !session.user.supportMode) {
        const supportResponse = await selectOrganization(session.accessToken, organizationId);
        storeSession(supportResponse);
        setSession(supportResponse);
        nextSession = supportResponse;
      }
      setSelectedOrganizationId(organizationId);
      await refreshBranches(nextSession.accessToken, organizationId);
      if (!session.user.supportMode || session.user.organizationId !== organizationId) {
        setMessage("Modo soporte iniciado para este gimnasio.");
      }
      return nextSession;
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible entrar en soporte.");
      return null;
    } finally {
      setSelectingId(null);
    }
  }

  async function activateOrganization(organizationId: string) {
    const nextSession = await ensureSupport(organizationId);
    if (!nextSession) {
      return;
    }
    const availableBranches = await refreshBranches(nextSession.accessToken, organizationId);
    if (!nextSession.user.branchId && availableBranches.length === 1) {
      await activateBranch(availableBranches[0].id, false);
    }
  }

  async function activateBranch(branchId: string, navigate = true) {
    if (!session || !selectedOrganization) {
      return;
    }
    setSelectingId(branchId);
    setError(null);
    setMessage(null);
    try {
      const supportSession = await ensureSupport(selectedOrganization.id);
      if (!supportSession) {
        return;
      }
      if (supportSession.user.branchId === branchId) {
        if (navigate) {
          router.push("/dashboard");
        }
        return;
      }
      const nextSession = await selectBranch(supportSession.accessToken, branchId);
      storeSession(nextSession);
      setSession(nextSession);
      await refreshBranches(nextSession.accessToken, selectedOrganization.id);
      if (navigate) {
        router.push("/dashboard");
      } else {
        setMessage("Sucursal lista para soporte.");
      }
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible seleccionar sucursal.");
    } finally {
      setSelectingId(null);
    }
  }

  async function goToTenantSection(path: string) {
    if (!selectedOrganization) {
      return;
    }
    const nextSession = await ensureSupport(selectedOrganization.id);
    if (!nextSession) {
      return;
    }
    router.push(path);
  }

  async function openBranchDrawer() {
    if (!selectedOrganization) {
      return;
    }
    const nextSession = await ensureSupport(selectedOrganization.id);
    if (nextSession) {
      setBranchForm(emptyBranchForm);
      setBranchFieldErrors({});
      setShowCreateBranch(true);
    }
  }

  async function submitOrganization(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) {
      return;
    }
    const errors = validateOrganization(form);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const payload = {
        ...form,
        taxId: normalizeRfc(form.taxId ?? ""),
        contactPhone: normalizePhone(form.contactPhone ?? ""),
      };
      const created = await createOrganization(session.accessToken, payload);
      setOrganizations((current) => [...current, created].sort((left, right) => left.tradeName.localeCompare(right.tradeName)));
      setForm(emptyOrganizationForm);
      setShowCreate(false);
      setSelectedOrganizationId(created.id);
      setBranches([]);
      setMessage("Gimnasio creado. Ahora crea sus sucursales, marca y usuarios desde su detalle.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear el gimnasio.");
    } finally {
      setSaving(false);
    }
  }

  async function submitBranch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || !selectedOrganization) {
      return;
    }
    const nextSession = await ensureSupport(selectedOrganization.id);
    if (!nextSession) {
      return;
    }
    const errors = validateBranch(branchForm);
    setBranchFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      await createBranch(nextSession.accessToken, {
        ...branchForm,
        phone: normalizePhone(branchForm.phone ?? ""),
      });
      const selectedBranches = await getOrganizationBranches(nextSession.accessToken, selectedOrganization.id);
      setBranches(selectedBranches);
      setOrganizations((current) =>
        current.map((organization) =>
          organization.id === selectedOrganization.id
            ? {
                ...organization,
                branchCount: organization.branchCount + 1,
                activeBranchCount: organization.activeBranchCount + 1,
              }
            : organization,
        ),
      );
      setBranchForm(emptyBranchForm);
      setShowCreateBranch(false);
      setMessage("Sucursal creada. Ya puedes seleccionarla o asignar usuarios.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear la sucursal.");
    } finally {
      setSaving(false);
    }
  }

  async function toggleOrganizationStatus() {
    if (!session || !selectedOrganization) {
      return;
    }
    const nextStatus = selectedOrganization.status === "ACTIVE" ? "SUSPENDED" : "ACTIVE";
    const confirmed = window.confirm(
      nextStatus === "ACTIVE"
        ? `¿Quieres reactivar ${selectedOrganization.tradeName}? Sus usuarios podran volver a operar.`
        : `¿Quieres suspender ${selectedOrganization.tradeName}? Sus usuarios no podran iniciar sesion mientras este suspendido.`,
    );
    if (!confirmed) {
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const updated = await updateOrganizationStatus(session.accessToken, selectedOrganization.id, nextStatus);
      setOrganizations((current) => current.map((organization) => (organization.id === updated.id ? updated : organization)));
      setMessage(nextStatus === "ACTIVE" ? "Gimnasio reactivado." : "Gimnasio suspendido. Sus usuarios tenant no podran iniciar sesion.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible cambiar el estado del gimnasio.");
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return <LoadingScreen text="Cargando administracion de gimnasios..." />;
  }

  if (!session) {
    return (
      <AuthRequired
        message="El panel de plataforma permite crear gimnasios, cambiar de gimnasio y seleccionar sucursal operativa."
        title="Inicia sesion como administrador de plataforma"
      />
    );
  }

  return (
    <AppShell
      branchName={activeBranch?.name}
      eyebrow="Plataforma GymOS"
      mode="setup"
      organizationName="GymOS"
      session={session}
      subtitle="Tus clientes, sus sucursales, marca, usuarios y acceso de soporte en un solo lugar."
      title="CRM de gimnasios cliente"
      variant="platform"
    >
      {error ? <FormError message={error} /> : null}
      {message ? <p className="form-message success">{message}</p> : null}

      <section className="platform-layout">
        <section className="platform-list-panel">
          <div className="platform-list-header">
            <div>
              <p className="app-eyebrow">Gimnasios</p>
              <h2>{organizations.length} clientes registrados</h2>
            </div>
            <button className="app-button" onClick={() => setShowCreate(true)} type="button">
              Nuevo gimnasio
            </button>
          </div>
          <input
            className="app-input"
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Buscar por nombre o RFC"
            value={query}
          />
          <div className="platform-tenant-list">
            {filteredOrganizations.map((item) => (
              <button
                className={item.id === selectedOrganization?.id ? "active" : undefined}
                key={item.id}
                onClick={() => setSelectedOrganizationId(item.id)}
                type="button"
              >
                <span>{item.status}</span>
                <strong>{item.tradeName}</strong>
                <small>
                  {item.activeBranchCount === 0
                    ? "Sin sucursales activas"
                    : `${item.activeBranchCount} sucursales activas`}
                </small>
              </button>
            ))}
          </div>
        </section>

        <section className="platform-detail-panel">
          {selectedOrganization ? (
            <>
              <header className="platform-detail-header">
                <div>
                  <p className="app-eyebrow">{selectedIsActive ? "En soporte ahora" : "Cliente seleccionado"}</p>
                  <h2>{selectedOrganization.tradeName}</h2>
                  <span>{selectedOrganization.legalName}</span>
                </div>
                <button className="app-button" disabled={selectingId !== null} onClick={() => activateOrganization(selectedOrganization.id)} type="button">
                  {selectedIsActive ? "Actualizar soporte" : "Entrar en soporte"}
                </button>
              </header>

              <div className="platform-facts">
                <article><span>RFC</span><strong>{selectedOrganization.taxId || "Sin dato"}</strong></article>
                <article><span>Contacto</span><strong>{selectedOrganization.contactEmail || "Sin email"}</strong></article>
                <article><span>Sucursales</span><strong>{selectedOrganization.branchCount}</strong></article>
                <article><span>Marca</span><strong>{selectedOrganization.brandName || selectedOrganization.tradeName}</strong></article>
              </div>

              <section className="platform-status-panel">
                <div>
                  <span>Estado comercial</span>
                  <strong>{selectedOrganization.status}</strong>
                  <p>{selectedOrganization.status === "ACTIVE" ? "El gimnasio puede operar normalmente." : "El acceso de usuarios tenant queda bloqueado mientras este suspendido o inactivo."}</p>
                </div>
                <button className="app-button-secondary" disabled={saving} onClick={toggleOrganizationStatus} type="button">
                  {selectedOrganization.status === "ACTIVE" ? "Suspender gimnasio" : "Reactivar gimnasio"}
                </button>
              </section>

              <section className="admin-panel">
                <div className="admin-panel-header">
                  <div>
                    <p>Sucursales</p>
                    <h2>{selectedIsActive ? "Sucursales para soporte" : "Sucursales registradas"}</h2>
                  </div>
                  <button className="admin-text-button" disabled={selectingId !== null} onClick={openBranchDrawer} type="button">
                    Crear sucursal
                  </button>
                </div>
                {branchesLoading ? (
                  <p className="admin-muted-text">Cargando sucursales...</p>
                ) : branches.length > 0 ? (
                    <div className="platform-table">
                      <div className="platform-table-row header">
                        <span>Codigo</span>
                        <span>Sucursal</span>
                        <span>Telefono</span>
                        <span>Accion</span>
                      </div>
                      {branches.map((branch) => (
                        <div className="platform-table-row" key={branch.id}>
                          <span>{branch.code}</span>
                          <strong>{branch.name}</strong>
                          <span>{branch.phone || "Sin telefono"}</span>
                          <button disabled={selectingId !== null} onClick={() => activateBranch(branch.id)} type="button">
                            {branch.id === session.user.branchId && selectedIsActive ? "Abrir soporte" : "Entrar"}
                          </button>
                        </div>
                      ))}
                    </div>
                ) : (
                    <section className="admin-empty-card">
                      <strong>Este gimnasio todavia no tiene sucursales</strong>
                      <span>Crea al menos una sucursal para poder seleccionar contexto operativo, usuarios y recepcion.</span>
                    </section>
                )}
              </section>

              <div className="platform-actions">
                <div className="platform-action-group">
                  <span>Administracion del cliente</span>
                  <button className="admin-secondary-link" disabled={selectingId !== null} onClick={() => goToTenantSection("/settings/organization")} type="button">
                    Datos, marca y sucursales
                  </button>
                  <button className="admin-secondary-link" disabled={selectingId !== null} onClick={() => goToTenantSection("/users")} type="button">
                    Equipo y accesos
                  </button>
                </div>
              </div>
            </>
          ) : (
            <section className="admin-empty-card">
              <strong>No hay gimnasios</strong>
              <span>Crea el primer gimnasio para empezar.</span>
            </section>
          )}
        </section>
      </section>

      <AppDrawer
        description="Registra el tenant y su marca. Las sucursales se crean despues desde el detalle del gimnasio."
        onClose={() => setShowCreate(false)}
        open={showCreate}
        title="Alta de gimnasio"
      >
        <OrganizationForm
          errors={fieldErrors}
          form={form}
          onChange={setForm}
          onSubmit={submitOrganization}
          saving={saving}
        />
      </AppDrawer>

      <AppDrawer
        description={selectedOrganization ? `Nueva sede para ${selectedOrganization.tradeName}.` : "Nueva sede del gimnasio seleccionado."}
        onClose={() => setShowCreateBranch(false)}
        open={showCreateBranch}
        title="Crear sucursal"
      >
        <BranchForm
          errors={branchFieldErrors}
          form={branchForm}
          onChange={setBranchForm}
          onSubmit={submitBranch}
          saving={saving}
        />
      </AppDrawer>
    </AppShell>
  );
}
