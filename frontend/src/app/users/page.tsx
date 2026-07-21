"use client";

import { FormEvent, useEffect, useState } from "react";
import { AppShell, LoadingScreen } from "@/components/AppShell";
import { AuthRequired } from "@/components/AuthRequired";
import {
  FieldErrorMap,
  FormError,
  normalizePhone,
} from "@/components/FormFields";
import {
  createInternalUser,
  getBranches,
  getCurrentOrganization,
  getInternalUsers,
  getStoredSession,
  getUserRoles,
  updateInternalUserRole,
  updateInternalUserStatus,
  type BranchSummary,
  type CreateInternalUserPayload,
  type InternalUserSummary,
  type OrganizationSummary,
  type RoleOption,
  type StoredSession,
} from "@/lib/api";
import { CreateUserDrawer, TeamContextCard, TeamOverview, UsersTable } from "./UsersSections";
import { canShowRoleForSession, displayRoleName, validateUserForm } from "./UsersRoleHelpers";

const emptyUserForm: CreateInternalUserPayload = {
  firstName: "",
  lastName: "",
  maternalLastName: "",
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
  const [showCreateUser, setShowCreateUser] = useState(false);

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
        const selectableRoles = availableRoles.filter((role) => canShowRoleForSession(stored.user.roles, role.name, stored.user.platformAdmin));
        const defaultRole = selectableRoles.find((role) => displayRoleName(role.name) === "Recepcion") ?? selectableRoles[0];
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
  const selectableRoles = roles.filter((role) => canShowRoleForSession(session?.user.roles ?? [], role.name, Boolean(session?.user.platformAdmin)));
  const selectedRoleLabel = displayRoleName(selectableRoles.find((role) => role.id === form.roleId)?.name ?? "");
  const ownerRoleSelected = selectedRoleLabel === "Dueno del gimnasio";

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
        branchIds: ownerRoleSelected ? branches.map((branch) => branch.id) : form.branchIds,
      });
      setUsers((current) => [...current, created].sort((left, right) => left.fullName.localeCompare(right.fullName)));
      setForm({
        ...emptyUserForm,
        roleId: form.roleId,
        branchIds: ownerRoleSelected ? branches.map((branch) => branch.id) : branches.length === 1 ? [branches[0].id] : [],
      });
      setShowCreateUser(false);
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

  async function toggleUserStatus(user: InternalUserSummary) {
    if (!session) {
      return;
    }
    const nextStatus = user.status === "ACTIVE" ? "SUSPENDED" : "ACTIVE";
    const confirmed = window.confirm(
      nextStatus === "ACTIVE"
        ? `Â¿Quieres reactivar a ${user.fullName}? Podra volver a iniciar sesion.`
        : `Â¿Quieres suspender a ${user.fullName}? No podra iniciar sesion mientras este suspendido.`,
    );
    if (!confirmed) {
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const updated = await updateInternalUserStatus(session.accessToken, user.id, nextStatus);
      setUsers((current) => current.map((item) => (item.id === updated.id ? updated : item)));
      setMessage(nextStatus === "ACTIVE" ? "Usuario reactivado." : "Usuario suspendido. Ya no podra iniciar sesion.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible cambiar el estado del usuario.");
    } finally {
      setSaving(false);
    }
  }

  async function changeUserRole(user: InternalUserSummary, roleId: string) {
    if (!session || !roleId) {
      return;
    }
    const role = selectableRoles.find((item) => item.id === roleId);
    const roleLabel = role ? displayRoleName(role.name) : "el rol seleccionado";
    const confirmed = window.confirm(`Â¿Quieres cambiar el rol de ${user.fullName} a ${roleLabel}?`);
    if (!confirmed) {
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const updated = await updateInternalUserRole(session.accessToken, user.id, roleId);
      setUsers((current) => current.map((item) => (item.id === updated.id ? updated : item)));
      setMessage("Rol de usuario actualizado.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible cambiar el rol del usuario.");
    } finally {
      setSaving(false);
    }
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
        <TeamOverview branches={branches} roles={selectableRoles} onCreateUser={() => setShowCreateUser(true)} />
        <TeamContextCard branches={branches} organization={organization} roles={selectableRoles} session={session} users={users} />
      </section>

      <UsersTable
        roles={selectableRoles}
        saving={saving}
        session={session}
        users={users}
        onChangeRole={changeUserRole}
        onToggleStatus={toggleUserStatus}
      />

      <CreateUserDrawer
        branches={branches}
        errors={fieldErrors}
        form={form}
        open={showCreateUser}
        ownerRoleSelected={ownerRoleSelected}
        roles={selectableRoles}
        saving={saving}
        onBranchAccessChange={updateBranchAccess}
        onClose={() => setShowCreateUser(false)}
        onFormChange={setForm}
        onSubmit={submitUser}
      />
    </AppShell>
  );
}

