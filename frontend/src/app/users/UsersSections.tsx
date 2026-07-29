import Link from "next/link";
import { FormEvent } from "react";
import { AppDrawer } from "@/components/AppDrawer";
import {
  EmailField,
  type FieldErrorMap,
  PasswordField,
  PhoneField,
  SelectField,
  TextField,
} from "@/components/FormFields";
import type {
  BranchSummary,
  CreateInternalUserPayload,
  InternalUserSummary,
  OrganizationSummary,
  RoleOption,
  StoredSession,
} from "@/lib/api";
import { canManageUserForSession, displayRoleName, roleDescription, roleIdForUser } from "./UsersRoleHelpers";

export function TeamOverview({ branches, roles, onCreateUser }: { branches: BranchSummary[]; roles: RoleOption[]; onCreateUser: () => void }) {
  return (
    <section className="app-card p-5">
      <div className="admin-panel-header">
        <div><p>Equipo</p><h2>Usuarios por rol</h2></div>
        <button className="app-button" disabled={roles.length === 0 || branches.length === 0} onClick={onCreateUser} type="button">Crear usuario</button>
      </div>
      <p className="mt-2 text-sm leading-6 text-[#5d6b63]">Administra accesos internos sin mezclar gimnasios ni sucursales. Los formularios de alta se abren en panel lateral para mantener esta vista limpia.</p>
      {branches.length === 0 ? (
        <section className="team-warning"><strong>Primero crea una sucursal</strong><span>Sin sucursales no podras asignar acceso operativo a recepcion, coaches o gerencia.</span><Link href="/settings/organization">Crear sucursal</Link></section>
      ) : null}
      <details className="team-role-disclosure">
        <summary>Consultar roles disponibles ({roles.length})</summary>
        <div className="team-role-list compact">
          {roles.map((role) => <article key={role.id}><strong>{displayRoleName(role.name)}</strong><span>{roleDescription(role.name, role.description)}</span><small>{role.permissionCount} permisos</small></article>)}
        </div>
      </details>
    </section>
  );
}

export function TeamContextCard({ branches, organization, session, roles, users }: { branches: BranchSummary[]; organization: OrganizationSummary | null; session: StoredSession; roles: RoleOption[]; users: InternalUserSummary[] }) {
  return (
    <section className="team-side"><section className="app-card p-5"><p className="app-eyebrow">Contexto</p><h2 className="mt-2 text-xl font-semibold">Alcance operativo</h2><div className="team-context-list">
      <article><span>Gimnasio</span><strong>{organization?.tradeName ?? session.user.organizationName ?? "GymOS"}</strong></article>
      <article><span>Sucursales</span><strong>{branches.length}</strong></article>
      <article><span>Usuarios activos</span><strong>{users.filter((user) => user.status === "ACTIVE").length}</strong></article>
      <article><span>Roles disponibles</span><strong>{roles.length}</strong></article>
    </div></section></section>
  );
}

export function UsersTable({
  roles,
  saving,
  session,
  users,
  onChangeRole,
  onEditCoach,
  onToggleStatus,
}: {
  roles: RoleOption[];
  saving: boolean;
  session: StoredSession;
  users: InternalUserSummary[];
  onChangeRole: (user: InternalUserSummary, roleId: string) => void;
  onEditCoach: (user: InternalUserSummary) => void;
  onToggleStatus: (user: InternalUserSummary) => void;
}) {
  return (
    <section className="app-card p-5">
      <div className="admin-panel-header"><div><p>Usuarios actuales</p><h2>Equipo del gimnasio</h2></div></div>
      <div className="team-user-table">
        {users.length === 0 ? <section className="admin-empty-card"><strong>No hay usuarios internos creados</strong><span>Crea cuentas para duenos, gerentes, recepcion y coaches.</span></section> : null}
        {users.length ? (
          <div aria-hidden="true" className="team-user-table-header">
            <span>Usuario</span>
            <span>Sucursales</span>
            <span>Rol</span>
            <span>Estado y acciones</span>
          </div>
        ) : null}
        {users.map((user) => {
          const isCurrentUser = user.id === session.user.userId;
          const isOwner = user.roles.some((role) => displayRoleName(role) === "Dueno del gimnasio");
          const isCoach = user.roles.some((role) => displayRoleName(role) === "Coach");
          const canManage = !isCurrentUser && canManageUserForSession(session.user.roles, user.roles, Boolean(session.user.platformAdmin));
          return (
            <article key={user.id}>
              <div className="team-user-identity">
                <span className="team-user-avatar">{userInitials(user.fullName)}</span>
                <div>
                  <strong>{user.fullName}</strong>
                  <span className="break-all">{user.username}</span>
                </div>
              </div>
              <div className="team-user-branches">
                <span className="team-mobile-label">Sucursales</span>
                <span>{user.branches.join(", ") || "Sin sucursal"}</span>
              </div>
              {canManage ? (
                <label className="team-role-control">
                  <span className="team-mobile-label">Rol</span>
                  <select
                    aria-label={`Rol de ${user.fullName}`}
                    defaultValue={roleIdForUser(user, roles)}
                    disabled={saving}
                    onChange={(event) => onChangeRole(user, event.target.value)}
                  >
                    <option value="">Seleccionar rol</option>
                    {roles.map((role) => <option key={role.id} value={role.id}>{displayRoleName(role.name)}</option>)}
                  </select>
                </label>
              ) : (
                <div className="team-role-control">
                  <span className="team-mobile-label">Rol protegido</span>
                  <strong>{user.roles.map(displayRoleName).join(", ") || "Sin rol"}</strong>
                </div>
              )}
              <div className="team-user-action-buttons">
                <p className={user.status === "ACTIVE" ? "status-pill active" : "status-pill"}>
                  {user.status === "ACTIVE" ? "Activo" : "Suspendido"}
                </p>
                {canManage && isCoach && session.user.permissions.includes("coach.profile.manage") ? (
                  <button className="safe-action" disabled={saving} onClick={() => onEditCoach(user)} type="button">
                    Datos de Coach
                  </button>
                ) : null}
                {canManage ? (
                  <button
                    className={user.status === "ACTIVE" ? "danger-action" : "safe-action"}
                    disabled={saving}
                    onClick={() => onToggleStatus(user)}
                    type="button"
                  >
                    {user.status === "ACTIVE" ? "Suspender" : "Reactivar"}
                  </button>
                ) : (
                  <small>{isCurrentUser ? "Tu cuenta" : isOwner ? "Dueno protegido" : "Solo lectura"}</small>
                )}
              </div>
            </article>
          );
        })}
      </div>
    </section>
  );
}

function userInitials(fullName: string) {
  return fullName
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join("") || "U";
}

export function CoachProfileDrawer({
  bio,
  branches,
  branchIds,
  open,
  saving,
  specialties,
  user,
  onBioChange,
  onBranchChange,
  onClose,
  onSpecialtiesChange,
  onSubmit,
}: {
  bio: string;
  branches: BranchSummary[];
  branchIds: string[];
  open: boolean;
  saving: boolean;
  specialties: string;
  user: InternalUserSummary | null;
  onBioChange: (value: string) => void;
  onBranchChange: (branchId: string, checked: boolean) => void;
  onClose: () => void;
  onSpecialtiesChange: (value: string) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <AppDrawer
      description="Complementa la ficha del coach y define en qué sucursales puede trabajar. El estado de acceso se controla desde la tabla del equipo."
      onClose={onClose}
      open={open}
      title={user ? `Perfil de ${user.fullName}` : "Perfil del coach"}
    >
      <form className="grid gap-4" onSubmit={onSubmit}>
        <label className="grid gap-1 text-sm font-medium text-[#435249]">
          <span>Especialidades</span>
          <input
            className="app-input"
            onChange={(event) => onSpecialtiesChange(event.target.value)}
            placeholder="Fuerza, movilidad, rehabilitación"
            value={specialties}
          />
        </label>
        <p className="-mt-3 text-xs text-[#637268]">Separa cada especialidad con una coma.</p>
        <label className="grid gap-1 text-sm font-medium text-[#435249]">
          <span>Biografía</span>
          <textarea
            className="app-input min-h-28"
            maxLength={1500}
            onChange={(event) => onBioChange(event.target.value)}
            value={bio}
          />
        </label>
        <section className="team-branch-selector">
          <div>
            <strong>Sucursales permitidas</strong>
            <span>Selecciona todas las sedes donde este coach puede trabajar.</span>
          </div>
          {branches.map((branch) => (
            <label key={branch.id}>
              <input
                checked={branchIds.includes(branch.id)}
                onChange={(event) => onBranchChange(branch.id, event.target.checked)}
                type="checkbox"
              />
              <span>{branch.name}</span>
              <small>{branch.code}</small>
            </label>
          ))}
        </section>
        {!branchIds.length ? (
          <p className="text-xs font-semibold text-red-700">Selecciona al menos una sucursal.</p>
        ) : null}
        <button className="form-primary-button" disabled={saving || !branchIds.length} type="submit">
          Guardar perfil
        </button>
      </form>
    </AppDrawer>
  );
}

export function CreateUserDrawer({ branches, branchSelectionLocked, coachBio, coachRoleSelected, coachSpecialties, errors, form, open, ownerRoleSelected, roles, saving, onBranchAccessChange, onCoachBioChange, onCoachSpecialtiesChange, onClose, onFormChange, onSubmit }: { branches: BranchSummary[]; branchSelectionLocked: boolean; coachBio: string; coachRoleSelected: boolean; coachSpecialties: string; errors: FieldErrorMap; form: CreateInternalUserPayload; open: boolean; ownerRoleSelected: boolean; roles: RoleOption[]; saving: boolean; onBranchAccessChange: (branchId: string, checked: boolean) => void; onCoachBioChange: (value: string) => void; onCoachSpecialtiesChange: (value: string) => void; onClose: () => void; onFormChange: (form: CreateInternalUserPayload) => void; onSubmit: (event: FormEvent<HTMLFormElement>) => void }) {
  return (
    <AppDrawer description="Crea la cuenta y define desde que sucursales podra operar. Cada usuario entrara a su consola segun su rol." onClose={onClose} open={open} title="Crear usuario del gimnasio">
      <form className="grid gap-4" onSubmit={onSubmit}>
        <div className="grid gap-4 sm:grid-cols-2"><TextField error={errors.firstName} label="Nombre" name="firstName" value={form.firstName} onChange={(value) => onFormChange({ ...form, firstName: value })} /><TextField error={errors.lastName} label="Apellido paterno" name="lastName" value={form.lastName} onChange={(value) => onFormChange({ ...form, lastName: value })} /><TextField error={errors.maternalLastName} label="Apellido materno" name="maternalLastName" required={false} value={form.maternalLastName ?? ""} onChange={(value) => onFormChange({ ...form, maternalLastName: value })} /></div>
        <div className="grid gap-4 sm:grid-cols-2"><EmailField error={errors.email} label="Email" name="email" required value={form.email ?? ""} onChange={(value) => onFormChange({ ...form, email: value, username: form.username || value })} /><PhoneField error={errors.phone} label="Telefono" name="phone" required value={form.phone ?? ""} onChange={(value) => onFormChange({ ...form, phone: value })} /></div>
        <TextField error={errors.username} label="Usuario de inicio de sesion" name="username" value={form.username} onChange={(value) => onFormChange({ ...form, username: value })} />
        <PasswordField error={errors.temporaryPassword} label="Contrasena temporal" name="temporaryPassword" value={form.temporaryPassword} onChange={(value) => onFormChange({ ...form, temporaryPassword: value })} />
        <SelectField error={errors.roleId} label="Rol" name="roleId" options={roles.map((role) => ({ label: displayRoleName(role.name), value: role.id }))} value={form.roleId} onChange={(nextRoleId) => { const nextLabel = displayRoleName(roles.find((role) => role.id === nextRoleId)?.name ?? ""); onFormChange({ ...form, roleId: nextRoleId, branchIds: nextLabel === "Dueno del gimnasio" ? branches.map((branch) => branch.id) : branches.length === 1 ? [branches[0].id] : form.branchIds }); }} />
        {coachRoleSelected ? (
          <section className="grid gap-4 rounded-xl border border-[#d9e2dc] bg-[#f8faf9] p-4">
            <div>
              <p className="text-xs font-bold uppercase tracking-wide text-[#637268]">Información del Coach</p>
              <p className="mt-1 text-sm text-[#526159]">Estos datos complementan su cuenta; después se pueden actualizar sin tratarlo como un usuario distinto.</p>
            </div>
            <label className="grid gap-1 text-sm font-medium text-[#435249]">
              <span>Especialidades</span>
              <input className="app-input" onChange={(event) => onCoachSpecialtiesChange(event.target.value)} placeholder="Fuerza, movilidad, rehabilitación" value={coachSpecialties} />
            </label>
            <p className="-mt-3 text-xs text-[#637268]">Separa cada especialidad con una coma.</p>
            <label className="grid gap-1 text-sm font-medium text-[#435249]">
              <span>Biografía</span>
              <textarea className="app-input min-h-24" maxLength={4000} onChange={(event) => onCoachBioChange(event.target.value)} value={coachBio} />
            </label>
          </section>
        ) : null}
        <section className="team-branch-selector"><div><strong>Sucursales permitidas</strong><span>{ownerRoleSelected ? "El dueno del gimnasio recibe acceso a todas las sucursales actuales." : branchSelectionLocked ? `Asignado automaticamente a ${branches[0]?.name ?? "tu sucursal"}.` : "Selecciona todas las sedes donde este usuario puede trabajar."}</span></div>{branches.map((branch) => <label key={branch.id}><input checked={ownerRoleSelected || branchSelectionLocked ? true : form.branchIds.includes(branch.id)} disabled={ownerRoleSelected || branchSelectionLocked} onChange={(event) => onBranchAccessChange(branch.id, event.target.checked)} type="checkbox" /><span>{branch.name}</span><small>{branchSelectionLocked ? "Sucursal fija para tu alcance" : branch.code}</small></label>)}</section>
        <button className="form-primary-button" disabled={saving} type="submit">Crear usuario</button>
      </form>
    </AppDrawer>
  );
}
