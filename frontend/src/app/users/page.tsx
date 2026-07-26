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
  getCoachProfiles,
  getCurrentOrganization,
  getInternalUsers,
  getStoredSession,
  getUserRoles,
  updateInternalUserRole,
  updateInternalUserStatus,
  updateCoachProfile,
  type BranchSummary,
  type CoachProfile,
  type CreateInternalUserPayload,
  type InternalUserSummary,
  type OrganizationSummary,
  type RoleOption,
  type StoredSession,
} from "@/lib/api";
import { CoachProfileDrawer, CreateUserDrawer, TeamContextCard, TeamOverview, UsersTable } from "./UsersSections";
import { canSeeUserForSession, canShowRoleForSession, displayRoleName, validateUserForm } from "./UsersRoleHelpers";

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
  const [coachProfiles, setCoachProfiles] = useState<CoachProfile[]>([]);
  const [form, setForm] = useState<CreateInternalUserPayload>(emptyUserForm);
  const [fieldErrors, setFieldErrors] = useState<FieldErrorMap>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [showCreateUser, setShowCreateUser] = useState(false);
  const [editingCoach, setEditingCoach] = useState<InternalUserSummary | null>(null);
  const [coachSpecialties, setCoachSpecialties] = useState("");
  const [coachBio, setCoachBio] = useState("");
  const [coachBranchIds, setCoachBranchIds] = useState<string[]>([]);

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
        const canManageCoachProfiles = stored.user.permissions.includes("coach.profile.manage");
        const [currentOrganization, availableBranches, availableRoles, existingUsers, existingCoachProfiles] = await Promise.all([
          getCurrentOrganization(stored.accessToken).catch(() => null),
          getBranches(stored.accessToken),
          getUserRoles(stored.accessToken),
          getInternalUsers(stored.accessToken),
          canManageCoachProfiles
            ? getCoachProfiles(stored.accessToken).catch(() => [])
            : Promise.resolve([]),
        ]);
        if (!active) {
          return;
        }
        setOrganization(currentOrganization);
        setBranches(availableBranches);
        setRoles(availableRoles);
        setUsers(existingUsers);
        setCoachProfiles(existingCoachProfiles);
        const selectableRoles = availableRoles.filter((role) => canShowRoleForSession(stored.user.roles, role.name, stored.user.platformAdmin));
        const defaultRole = selectableRoles.find((role) => displayRoleName(role.name) === "Recepcion") ?? selectableRoles[0];
        const defaultRoleLabel = displayRoleName(defaultRole?.name ?? "");
        setForm((current) => ({
          ...current,
          roleId: defaultRole?.id ?? "",
          branchIds: defaultRoleLabel === "Dueno del gimnasio"
            ? availableBranches.map((branch) => branch.id)
            : availableBranches.length === 1
              ? [availableBranches[0].id]
              : current.branchIds,
        }));
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
  const visibleUsers = users.filter((user) => canSeeUserForSession(session?.user.roles ?? [], user.roles, Boolean(session?.user.platformAdmin)));
  const selectedRoleLabel = displayRoleName(selectableRoles.find((role) => role.id === form.roleId)?.name ?? "");
  const ownerRoleSelected = selectedRoleLabel === "Dueno del gimnasio";
  const managerSingleBranchLocked = selectedRoleLabel !== "Dueno del gimnasio"
    && branches.length === 1
    && (session?.user.roles ?? []).some((role) => displayRoleName(role) === "Gerente")
    && !session?.user.platformAdmin;

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
        branchIds: ownerRoleSelected
          ? branches.map((branch) => branch.id)
          : managerSingleBranchLocked
            ? [branches[0].id]
            : form.branchIds,
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
    if (managerSingleBranchLocked) {
      return;
    }
    setForm((current) => ({
      ...current,
      branchIds: checked
        ? [...new Set([...current.branchIds, branchId])]
        : current.branchIds.filter((item) => item !== branchId),
    }));
  }

  function openCreateUser() {
    setFieldErrors({});
    setForm((current) => ({
      ...current,
      branchIds: ownerRoleSelected
        ? branches.map((branch) => branch.id)
        : branches.length === 1
          ? [branches[0].id]
          : current.branchIds,
    }));
    setShowCreateUser(true);
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

  function openCoachProfile(user: InternalUserSummary) {
    const profile = coachProfiles.find((item) => item.userId === user.id);
    const manageableBranchIds = new Set(branches.map((branch) => branch.id));
    setEditingCoach(user);
    setCoachSpecialties(profile?.specialties.join(", ") ?? "");
    setCoachBio(profile?.bio ?? "");
    setCoachBranchIds(
      (profile?.branches.map((branch) => branch.id) ?? user.branchIds)
        .filter((branchId) => manageableBranchIds.has(branchId)),
    );
    setError(null);
    setMessage(null);
  }

  function updateCoachBranchAccess(branchId: string, checked: boolean) {
    setCoachBranchIds((current) =>
      checked
        ? [...new Set([...current, branchId])]
        : current.filter((item) => item !== branchId));
  }

  async function saveCoachProfile(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || !editingCoach || !coachBranchIds.length) return;
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const updated = await updateCoachProfile(session.accessToken, editingCoach.id, {
        specialties: coachSpecialties
          .split(",")
          .map((specialty) => specialty.trim())
          .filter(Boolean),
        bio: coachBio.trim() || null,
        status: editingCoach.status === "ACTIVE" ? "ACTIVE" : "INACTIVE",
        branchIds: coachBranchIds,
      });
      setCoachProfiles((current) => {
        const exists = current.some((profile) => profile.userId === updated.userId);
        return exists
          ? current.map((profile) => profile.userId === updated.userId ? updated : profile)
          : [...current, updated];
      });
      const branchNames = branches
        .filter((branch) => coachBranchIds.includes(branch.id))
        .map((branch) => branch.name);
      setUsers((current) => current.map((user) => user.id === editingCoach.id
        ? { ...user, branchIds: coachBranchIds, branches: branchNames }
        : user));
      setEditingCoach(null);
      setMessage("Perfil y sucursales del coach actualizados.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible actualizar el perfil del coach.");
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
        <TeamOverview branches={branches} roles={selectableRoles} onCreateUser={openCreateUser} />
        <TeamContextCard branches={branches} organization={organization} roles={selectableRoles} session={session} users={visibleUsers} />
      </section>

      <UsersTable
        roles={selectableRoles}
        saving={saving}
        session={session}
        users={visibleUsers}
        onChangeRole={changeUserRole}
        onEditCoach={openCoachProfile}
        onToggleStatus={toggleUserStatus}
      />

      <CoachProfileDrawer
        bio={coachBio}
        branches={branches}
        branchIds={coachBranchIds}
        open={Boolean(editingCoach)}
        saving={saving}
        specialties={coachSpecialties}
        user={editingCoach}
        onBioChange={setCoachBio}
        onBranchChange={updateCoachBranchAccess}
        onClose={() => setEditingCoach(null)}
        onSpecialtiesChange={setCoachSpecialties}
        onSubmit={saveCoachProfile}
      />

      <CreateUserDrawer
        branches={branches}
        errors={fieldErrors}
        form={form}
        open={showCreateUser}
        ownerRoleSelected={ownerRoleSelected}
        branchSelectionLocked={managerSingleBranchLocked}
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
