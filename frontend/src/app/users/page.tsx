"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import { AppShell, LoadingScreen } from "@/components/AppShell";
import { AuthRequired } from "@/components/AuthRequired";
import {
  FieldErrorMap,
  FormError,
  normalizePhone,
  validateEmail,
  validatePassword,
  validatePhone,
  validateRequired,
} from "@/components/FormFields";
import {
  createInternalUser,
  getBranches,
  getCurrentOrganization,
  getInternalUsers,
  getStoredSession,
  getUserRoles,
  type BranchSummary,
  type CreateInternalUserPayload,
  type InternalUserSummary,
  type OrganizationSummary,
  type RoleOption,
  type StoredSession,
} from "@/lib/api";

const emptyUserForm: CreateInternalUserPayload = {
  firstName: "",
  lastName: "",
  email: "",
  phone: "",
  username: "",
  temporaryPassword: "",
  roleId: "",
  branchIds: [],
};

export default function UsersPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [organization, setOrganization] = useState<OrganizationSummary | null>(null);
  const [branches, setBranches] = useState<BranchSummary[]>([]);
  const [roles, setRoles] = useState<RoleOption[]>([]);
  const [users, setUsers] = useState<InternalUserSummary[]>([]);
  const [form, setForm] = useState<CreateInternalUserPayload>(emptyUserForm);
  const [fieldErrors, setFieldErrors] = useState<FieldErrorMap>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
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
      setError(null);
      try {
        const [currentOrganization, availableBranches, availableRoles, existingUsers] = await Promise.all([
          getCurrentOrganization(stored.accessToken),
          getBranches(stored.accessToken),
          getUserRoles(stored.accessToken),
          getInternalUsers(stored.accessToken),
        ]);
        if (!active) {
          return;
        }
        setOrganization(currentOrganization);
        setBranches(availableBranches);
        setRoles(availableRoles);
        setUsers(existingUsers);
        const selectableRoles = availableRoles.filter((role) => displayRoleName(role.name) !== "Administrador de plataforma");
        const defaultRole = selectableRoles.find((role) => displayRoleName(role.name) === "Recepcion y caja") ?? selectableRoles[0];
        setForm((current) => ({ ...current, roleId: defaultRole?.id ?? "" }));
      } catch (exception) {
        if (active) {
          setError(exception instanceof Error ? exception.message : "No fue posible cargar equipo y accesos.");
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

  const activeBranch = branches.find((branch) => branch.id === session?.user.branchId) ?? null;
  const selectableRoles = roles.filter((role) => displayRoleName(role.name) !== "Administrador de plataforma");

  async function submitUser(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) {
      return;
    }
    const errors = validateUserForm(form);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const created = await createInternalUser(session.accessToken, {
        ...form,
        email: form.email?.trim() || null,
        phone: normalizePhone(form.phone ?? ""),
        username: form.username.trim(),
      });
      setUsers((current) => [...current, created].sort((left, right) => left.fullName.localeCompare(right.fullName)));
      setForm({
        ...emptyUserForm,
        roleId: form.roleId,
        branchIds: branches.length === 1 ? [branches[0].id] : [],
      });
      setMessage("Usuario creado. Ya puede iniciar sesion con su usuario y contrasena temporal.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear el usuario.");
    } finally {
      setSaving(false);
    }
  }

  function updateBranchAccess(branchId: string, checked: boolean) {
    setForm((current) => ({
      ...current,
      branchIds: checked
        ? [...new Set([...current.branchIds, branchId])]
        : current.branchIds.filter((item) => item !== branchId),
    }));
  }

  if (loading) {
    return <LoadingScreen text="Cargando equipo y accesos..." />;
  }

  if (!session) {
    return (
      <AuthRequired
        message="Necesitas iniciar sesion con un usuario con permisos para administrar equipo."
        title="Inicia sesion"
      />
    );
  }

  return (
    <AppShell
      actions={[
        { href: "/platform/gymnasiums", label: "Gimnasios" },
        { href: "/settings/organization", label: "Datos y sucursales" },
      ]}
      branchName={activeBranch?.name}
      eyebrow="Administracion"
      mode={activeBranch ? "operation" : "setup"}
      organizationName={organization?.tradeName}
      session={session}
      subtitle="Crea usuarios internos y limita lo que ven por rol y por sucursal."
      title="Equipo y accesos"
    >
      {error ? <FormError message={error} /> : null}
      {message ? <p className="form-message success">{message}</p> : null}

      <section className="team-layout">
        <section className="app-card p-5">
          <p className="app-eyebrow">Nuevo usuario</p>
          <h2 className="mt-2 text-xl font-semibold">Crear acceso interno</h2>
          <p className="mt-2 text-sm leading-6 text-[#5d6b63]">
            El rol define el menu y las acciones. Las sucursales definen donde puede operar.
          </p>

          {branches.length === 0 ? (
            <section className="team-warning">
              <strong>Primero crea una sucursal</strong>
              <span>Sin sucursales no podras asignar acceso operativo a recepcion, coaches o gerencia.</span>
              <Link href="/settings/organization">Crear sucursal</Link>
            </section>
          ) : null}

          <form className="mt-5 grid gap-4" onSubmit={submitUser}>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field error={fieldErrors.firstName} label="Nombre" value={form.firstName} onChange={(value) => setForm({ ...form, firstName: value })} />
              <Field error={fieldErrors.lastName} label="Apellido" value={form.lastName} onChange={(value) => setForm({ ...form, lastName: value })} />
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field error={fieldErrors.email} label="Email" type="email" value={form.email ?? ""} onChange={(value) => setForm({ ...form, email: value, username: form.username || value })} />
              <Field error={fieldErrors.phone} help="10 a 15 digitos; puedes incluir +52." label="Telefono" value={form.phone ?? ""} onChange={(value) => setForm({ ...form, phone: value })} />
            </div>
            <Field error={fieldErrors.username} label="Usuario de inicio de sesion" value={form.username} onChange={(value) => setForm({ ...form, username: value })} />
            <Field error={fieldErrors.temporaryPassword} help="Minimo 8 caracteres." label="Contrasena temporal" type="password" value={form.temporaryPassword} onChange={(value) => setForm({ ...form, temporaryPassword: value })} />

            <label className="grid gap-2 text-sm font-medium text-[#42514a]">
              Rol
              <select
                className="h-11 rounded-md border border-[#cdd5ca] bg-white px-3 text-sm outline-none transition focus:border-[#51735f]"
                onChange={(event) => setForm({ ...form, roleId: event.target.value })}
                required
                value={form.roleId}
              >
                {selectableRoles.map((role) => (
                  <option key={role.id} value={role.id}>
                    {displayRoleName(role.name)}
                  </option>
                ))}
              </select>
            </label>

            <section className="team-branch-selector">
              <div>
                <strong>Sucursales permitidas</strong>
                <span>Selecciona todas las sedes donde este usuario puede trabajar.</span>
              </div>
              {branches.map((branch) => (
                <label key={branch.id}>
                  <input
                    checked={form.branchIds.includes(branch.id)}
                    onChange={(event) => updateBranchAccess(branch.id, event.target.checked)}
                    type="checkbox"
                  />
                  <span>{branch.name}</span>
                  <small>{branch.code}</small>
                </label>
              ))}
              {fieldErrors.branchIds ? <small className="form-field-error">{fieldErrors.branchIds}</small> : null}
            </section>

            <button className="form-primary-button" disabled={saving || selectableRoles.length === 0} type="submit">
              Crear usuario
            </button>
          </form>
        </section>

        <section className="team-side">
          <section className="app-card p-5">
            <p className="app-eyebrow">Roles disponibles</p>
            <h2 className="mt-2 text-xl font-semibold">Perfiles de trabajo</h2>
            <div className="team-role-list">
              {selectableRoles.map((role) => (
                <article key={role.id}>
                  <strong>{displayRoleName(role.name)}</strong>
                  <span>{roleDescription(role.name, role.description)}</span>
                  <small>{role.permissionCount} permisos</small>
                </article>
              ))}
            </div>
          </section>
        </section>
      </section>

      <section className="app-card p-5">
        <div className="admin-panel-header">
          <div>
            <p>Usuarios actuales</p>
            <h2>Equipo del gimnasio</h2>
          </div>
        </div>

        <div className="team-user-table">
          {users.length === 0 ? (
            <section className="admin-empty-card">
              <strong>No hay usuarios internos creados</strong>
              <span>Crea cuentas para recepcion, gerencia, coaches u operacion.</span>
            </section>
          ) : null}

          {users.map((user) => (
            <article key={user.id}>
              <div>
                <strong>{user.fullName}</strong>
                <span>{user.username}</span>
              </div>
              <div>
                <span>{user.roles.map(displayRoleName).join(", ") || "Sin rol"}</span>
                <small>{user.branches.join(", ") || "Sin sucursal"}</small>
              </div>
              <p>{user.status}</p>
            </article>
          ))}
        </div>
      </section>
    </AppShell>
  );
}

function Field({
  error,
  help,
  label,
  value,
  onChange,
  type = "text",
}: {
  error?: string;
  help?: string;
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
        required={label !== "Telefono" && label !== "Email"}
        type={type}
        value={value}
      />
      {error ? <small className="form-field-error">{error}</small> : help ? <small>{help}</small> : null}
    </label>
  );
}

function displayRoleName(roleName: string) {
  const normalized = roleName.toLowerCase();
  if (normalized.includes("admin") && normalized.includes("gymos")) {
    return "Administrador de plataforma";
  }
  if (normalized === "administrador" || normalized.includes("owner")) {
    return "Administrador del gimnasio";
  }
  if (normalized.includes("gerencia") || normalized.includes("gerente") || normalized.includes("dueno")) {
    return "Gerencia del gimnasio";
  }
  if (normalized.includes("recepcion") || normalized.includes("caja")) {
    return "Recepcion y caja";
  }
  if (normalized.includes("coach")) {
    return "Coach";
  }
  return roleName;
}

function roleDescription(roleName: string, fallback?: string | null) {
  const label = displayRoleName(roleName);
  if (label === "Administrador de plataforma") {
    return "Crea gimnasios, cambia de gimnasio y prepara configuracion base.";
  }
  if (label === "Administrador del gimnasio") {
    return "Administra sucursales, usuarios, permisos y configuracion del gimnasio.";
  }
  if (label === "Gerencia del gimnasio") {
    return "Consulta reportes, ventas y operacion para tomar decisiones.";
  }
  if (label === "Recepcion y caja") {
    return "Registra socios, cobra ventas, renueva membresias y atiende mostrador.";
  }
  if (label === "Coach") {
    return "Consulta socios asignados y gestiona rutinas o servicios permitidos.";
  }
  return fallback ?? "Rol operativo";
}

function validateUserForm(form: CreateInternalUserPayload) {
  const errors: FieldErrorMap = {};
  addError(errors, "firstName", validateRequired(form.firstName, "Nombre"));
  addError(errors, "lastName", validateRequired(form.lastName, "Apellido"));
  addError(errors, "email", validateEmail(form.email ?? "", true));
  addError(errors, "phone", validatePhone(form.phone ?? ""));
  addError(errors, "username", validateRequired(form.username, "Usuario de inicio de sesion"));
  addError(errors, "temporaryPassword", validatePassword(form.temporaryPassword));
  addError(errors, "roleId", validateRequired(form.roleId, "Rol"));
  if (form.branchIds.length === 0) {
    errors.branchIds = "Selecciona al menos una sucursal.";
  }
  return errors;
}

function addError(errors: FieldErrorMap, key: string, value: string) {
  if (value) {
    errors[key] = value;
  }
}
