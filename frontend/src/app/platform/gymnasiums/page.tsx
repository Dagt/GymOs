"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { AppShell, LoadingScreen } from "@/components/AppShell";
import { AuthRequired } from "@/components/AuthRequired";
import {
  EmailField,
  FieldErrorMap,
  FormError,
  PhoneField,
  TextField,
  normalizePhone,
  validateBranchCode,
  validateEmail,
  validatePhone,
  validateRequired,
} from "@/components/FormFields";
import {
  createOrganization,
  getBranches,
  getOrganizations,
  getStoredSession,
  selectBranch,
  selectOrganization,
  storeSession,
  type BranchSummary,
  type CreateOrganizationPayload,
  type ManagedOrganizationSummary,
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
  brandName: "",
  logoUrl: "",
  primaryColor: "#164E63",
  accentColor: "#F59E0B",
  surfaceColor: "#F4F7FB",
  firstBranchName: "",
  firstBranchCode: "",
  firstBranchPhone: "",
};

export default function PlatformGymnasiumsPage() {
  const router = useRouter();
  const [session, setSession] = useState<StoredSession | null>(null);
  const [organizations, setOrganizations] = useState<ManagedOrganizationSummary[]>([]);
  const [branches, setBranches] = useState<BranchSummary[]>([]);
  const [selectedOrganizationId, setSelectedOrganizationId] = useState("");
  const [query, setQuery] = useState("");
  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState<CreateOrganizationPayload>(emptyOrganizationForm);
  const [fieldErrors, setFieldErrors] = useState<FieldErrorMap>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [selectingId, setSelectingId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

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
        const [availableOrganizations, availableBranches] = await Promise.all([
          getOrganizations(stored.accessToken),
          getBranches(stored.accessToken),
        ]);
        if (!active) {
          return;
        }
        setOrganizations(availableOrganizations);
        setBranches(availableBranches);
        setSelectedOrganizationId(stored.user.organizationId);
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

  const filteredOrganizations = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    if (!normalized) {
      return organizations;
    }
    return organizations.filter((item) =>
      [item.tradeName, item.legalName, item.taxId ?? ""].some((value) => value.toLowerCase().includes(normalized)),
    );
  }, [organizations, query]);

  const selectedOrganization = organizations.find((item) => item.id === selectedOrganizationId) ?? organizations[0] ?? null;
  const activeBranch = branches.find((branch) => branch.id === session?.user.branchId) ?? null;
  const selectedIsActive = Boolean(session && selectedOrganization?.id === session.user.organizationId);

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
      const [availableBranches] = await Promise.all([
        getBranches(nextSession.accessToken),
      ]);
      setBranches(availableBranches);
      setSelectedOrganizationId(organizationId);
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
        contactPhone: normalizePhone(form.contactPhone ?? ""),
        firstBranchPhone: normalizePhone(form.firstBranchPhone ?? ""),
      };
      const created = await createOrganization(session.accessToken, payload);
      setOrganizations((current) => [...current, created].sort((left, right) => left.tradeName.localeCompare(right.tradeName)));
      setForm(emptyOrganizationForm);
      setShowCreate(false);
      setSelectedOrganizationId(created.id);
      setMessage("Gimnasio creado. Seleccionalo para trabajar en el.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear el gimnasio.");
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
      subtitle="Administra tenants, elige gimnasio activo y prepara la base operativa sin mezclarla con caja o recepcion."
      title="Administracion de gimnasios"
      variant="platform"
    >
      {error ? <FormError message={error} /> : null}
      {message ? <p className="form-message success">{message}</p> : null}

      <section className="platform-layout">
        <section className="platform-list-panel">
          <div className="platform-list-header">
            <div>
              <p className="app-eyebrow">Gimnasios</p>
              <h2>{organizations.length} registrados</h2>
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
                <small>{item.activeBranchCount} sucursales activas</small>
              </button>
            ))}
          </div>
        </section>

        <section className="platform-detail-panel">
          {selectedOrganization ? (
            <>
              <header className="platform-detail-header">
                <div>
                  <p className="app-eyebrow">{selectedIsActive ? "Gimnasio activo" : "Gimnasio seleccionado"}</p>
                  <h2>{selectedOrganization.tradeName}</h2>
                  <span>{selectedOrganization.legalName}</span>
                </div>
                <button
                  className="app-button"
                  disabled={selectingId !== null || selectedIsActive}
                  onClick={() => activateOrganization(selectedOrganization.id)}
                  type="button"
                >
                  {selectedIsActive ? "Activo" : "Usar gimnasio"}
                </button>
              </header>

              <div className="platform-facts">
                <article><span>RFC</span><strong>{selectedOrganization.taxId || "Sin dato"}</strong></article>
                <article><span>Contacto</span><strong>{selectedOrganization.contactEmail || "Sin email"}</strong></article>
                <article><span>Sucursales</span><strong>{selectedOrganization.branchCount}</strong></article>
                <article><span>Marca</span><strong>{selectedOrganization.brandName || selectedOrganization.tradeName}</strong></article>
              </div>

              <section className="admin-panel">
                <div className="admin-panel-header">
                  <div>
                    <p>Sucursales</p>
                    <h2>{selectedIsActive ? "Contexto operativo" : "Selecciona el gimnasio para ver sucursales"}</h2>
                  </div>
                  {selectedIsActive ? <Link href="/settings/organization">Crear sucursal</Link> : null}
                </div>
                {selectedIsActive ? (
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
                        <button disabled={selectingId !== null || branch.id === session.user.branchId} onClick={() => activateBranch(branch.id)} type="button">
                          {branch.id === session.user.branchId ? "Activa" : "Usar"}
                        </button>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="admin-muted-text">Para administrar sucursales, primero usa este gimnasio como contexto activo.</p>
                )}
              </section>

              <div className="platform-actions">
                <Link className="admin-secondary-link" href="/settings/organization">Datos, marca y sucursales</Link>
                <Link className="admin-secondary-link" href="/users">Equipo y accesos</Link>
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

      {showCreate ? (
        <aside className="platform-drawer" aria-label="Nuevo gimnasio">
          <div className="platform-drawer-card">
            <header>
              <div>
                <p className="app-eyebrow">Nuevo gimnasio</p>
                <h2>Alta de gimnasio</h2>
              </div>
              <button onClick={() => setShowCreate(false)} type="button">Cerrar</button>
            </header>
            <form className="grid gap-4" onSubmit={submitOrganization}>
              <TextField error={fieldErrors.tradeName} label="Nombre comercial" name="tradeName" value={form.tradeName} onChange={(value) => setForm({ ...form, tradeName: value })} />
              <TextField error={fieldErrors.legalName} label="Nombre legal" name="legalName" value={form.legalName} onChange={(value) => setForm({ ...form, legalName: value })} />
              <TextField label="RFC / Tax ID" name="taxId" required={false} value={form.taxId ?? ""} onChange={(value) => setForm({ ...form, taxId: value })} />
              <EmailField error={fieldErrors.contactEmail} label="Email contacto" name="contactEmail" value={form.contactEmail ?? ""} onChange={(value) => setForm({ ...form, contactEmail: value })} />
              <PhoneField error={fieldErrors.contactPhone} label="Telefono contacto" name="contactPhone" value={form.contactPhone ?? ""} onChange={(value) => setForm({ ...form, contactPhone: value })} />
              <TextField error={fieldErrors.firstBranchName} label="Primera sucursal" name="firstBranchName" value={form.firstBranchName} onChange={(value) => setForm({ ...form, firstBranchName: value })} />
              <TextField error={fieldErrors.firstBranchCode} label="Codigo sucursal" name="firstBranchCode" value={form.firstBranchCode} onChange={(value) => setForm({ ...form, firstBranchCode: value })} />
              <PhoneField error={fieldErrors.firstBranchPhone} label="Telefono sucursal" name="firstBranchPhone" value={form.firstBranchPhone ?? ""} onChange={(value) => setForm({ ...form, firstBranchPhone: value })} />
              <button className="form-primary-button" disabled={saving} type="submit">Crear gimnasio</button>
            </form>
          </div>
        </aside>
      ) : null}
    </AppShell>
  );
}

function validateOrganization(form: CreateOrganizationPayload) {
  const errors: FieldErrorMap = {};
  addError(errors, "tradeName", validateRequired(form.tradeName, "Nombre comercial"));
  addError(errors, "legalName", validateRequired(form.legalName, "Nombre legal"));
  addError(errors, "contactEmail", validateEmail(form.contactEmail ?? ""));
  addError(errors, "contactPhone", validatePhone(form.contactPhone ?? ""));
  addError(errors, "firstBranchName", validateRequired(form.firstBranchName, "Primera sucursal"));
  addError(errors, "firstBranchCode", validateBranchCode(form.firstBranchCode));
  addError(errors, "firstBranchPhone", validatePhone(form.firstBranchPhone ?? ""));
  return errors;
}

function addError(errors: FieldErrorMap, key: string, value: string) {
  if (value) {
    errors[key] = value;
  }
}
