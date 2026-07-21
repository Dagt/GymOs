import Link from "next/link";
import { FormEvent } from "react";
import { AppDrawer } from "@/components/AppDrawer";
import type { FieldErrorMap } from "@/components/FormFields";
import type {
  BranchSummary,
  CreateInternalUserPayload,
  InternalUserSummary,
  OrganizationSummary,
  RoleOption,
  StoredSession,
} from "@/lib/api";
import { displayRoleName, roleDescription, roleIdForUser } from "./UsersRoleHelpers";

export function TeamOverview({
  branches,
  roles,
  onCreateUser,
}: {
  branches: BranchSummary[];
  roles: RoleOption[];
  onCreateUser: () => void;
}) {
  return (
    <section className="app-card p-5">
      <div className="admin-panel-header">
        <div>
          <p>Equipo</p>
          <h2>Usuarios por rol</h2>
        </div>
        <button className="app-button" disabled={roles.length === 0 || branches.length === 0} onClick={onCreateUser} type="button">
          Crear usuario
        </button>
      </div>
      <p className="mt-2 text-sm leading-6 text-[#5d6b63]">
        Administra accesos internos sin mezclar gimnasios ni sucursales. Los formularios de alta se abren en panel lateral para mantener esta vista limpia.
      </p>

      {branches.length === 0 ? (
        <section className="team-warning">
          <strong>Primero crea una sucursal</strong>
          <span>Sin sucursales no podras asignar acceso operativo a recepcion, coaches o gerencia.</span>
          <Link href="/settings/organization">Crear sucursal</Link>
        </section>
      ) : null}

      <div className="team-role-list compact">
        {roles.map((role) => (
          <article key={role.id}>
            <strong>{displayRoleName(role.name)}</strong>
            <span>{roleDescription(role.name, role.description)}</span>
            <small>{role.permissionCount} permisos</small>
          </article>
        ))}
      </div>
    </section>
  );
}

export function TeamContextCard({
  branches,
  organization,
  session,
  roles,
  users,
}: {
  branches: BranchSummary[];
  organization: OrganizationSummary | null;
  session: StoredSession;
  roles: RoleOption[];
  users: InternalUserSummary[];
}) {
  return (
    <section className="team-side">
      <section className="app-card p-5">
        <p className="app-eyebrow">Contexto</p>
        <h2 className="mt-2 text-xl font-semibold">Alcance operativo</h2>
        <div className="team-context-list">
          <article>
            <span>Gimnasio</span>
            <strong>{organization?.tradeName ?? session.user.organizationName ?? "GymOS"}</strong>
          </article>
          <article>
            <span>Sucursales</span>
            <strong>{branches.length}</strong>
          </article>
          <article>
            <span>Usuarios activos</span>
            <strong>{users.filter((user) => user.status === "ACTIVE").length}</strong>
          </article>
          <article>
            <span>Roles disponibles</span>
            <strong>{roles.length}</strong>
          </article>
        </div>
      </section>
    </section>
  );
}

export function UsersTable({
  roles,
  saving,
  session,
  users,
  onChangeRole,
  onToggleStatus,
}: {
  roles: RoleOption[];
  saving: boolean;
  session: StoredSession;
  users: InternalUserSummary[];
  onChangeRole: (user: InternalUserSummary, roleId: string) => void;
  onToggleStatus: (user: InternalUserSummary) => void;
}) {
  return (
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
            <span>Crea cuentas para duenos, gerentes, recepcion y coaches.</span>
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
            <div className="team-user-actions">
              <label className="team-role-control">
                <span>Rol</span>
                <select
                  defaultValue={roleIdForUser(user, roles)}
                  disabled={saving || user.id === session.user.userId}
                  onChange={(event) => onChangeRole(user, event.target.value)}
                >
                  <option value="">Seleccionar rol</option>
                  {roles.map((role) => (
                    <option key={role.id} value={role.id}>
                      {displayRoleName(role.name)}
                    </option>
                  ))}
                </select>
              </label>
              <p className={user.status === "ACTIVE" ? "status-pill active" : "status-pill"}>{user.status}</p>
              {user.id !== session.user.userId ? (
                <button className={user.status === "ACTIVE" ? "danger-action" : "safe-action"} disabled={saving} onClick={() => onToggleStatus(user)} type="button">
                  {user.status === "ACTIVE" ? "Suspender" : "Reactivar"}
                </button>
              ) : (
                <small>Tu cuenta</small>
              )}
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}

export function CreateUserDrawer({
  branches,
  errors,
  form,
  open,
  ownerRoleSelected,
  roles,
  saving,
  onBranchAccessChange,
  onClose,
  onFormChange,
  onSubmit,
}: {
  branches: BranchSummary[];
  errors: FieldErrorMap;
  form: CreateInternalUserPayload;
  open: boolean;
  ownerRoleSelected: boolean;
  roles: RoleOption[];
  saving: boolean;
  onBranchAccessChange: (branchId: string, checked: boolean) => void;
  onClose: () => void;
  onFormChange: (form: CreateInternalUserPayload) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <AppDrawer
      description="Crea la cuenta y define desde que sucursales podra operar. Cada usuario entrara a su consola segun su rol."
      onClose={onClose}
      open={open}
      title="Crear usuario del gimnasio"
    >
      <form className="grid gap-4" onSubmit={onSubmit}>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field error={errors.firstName} label="Nombre" value={form.firstName} onChange={(value) => onFormChange({ ...form, firstName: value })} />
          <Field error={errors.lastName} label="Apellido paterno" value={form.lastName} onChange={(value) => onFormChange({ ...form, lastName: value })} />
          <Field error={errors.maternalLastName} label="Apellido materno" required={false} value={form.maternalLastName ?? ""} onChange={(value) => onFormChange({ ...form, maternalLastName: value })} />
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field error={errors.email} label="Email" type="email" value={form.email ?? ""} onChange={(value) => onFormChange({ ...form, email: value, username: form.username || value })} />
          <Field error={errors.phone} help="Se guardara como +52 seguido de 10 digitos." label="Telefono" value={form.phone ?? ""} onChange={(value) => onFormChange({ ...form, phone: value })} />
        </div>
        <Field error={errors.username} label="Usuario de inicio de sesion" value={form.username} onChange={(value) => onFormChange({ ...form, username: value })} />
        <Field error={errors.temporaryPassword} help="Minimo 8 caracteres." label="Contrasena temporal" type="password" value={form.temporaryPassword} onChange={(value) => onFormChange({ ...form, temporaryPassword: value })} />

        <label className="grid gap-2 text-sm font-medium text-[#42514a]">
          Rol
          <select
            className="app-input"
            onChange={(event) => {
              const nextRoleId = event.target.value;
              const nextLabel = displayRoleName(roles.find((role) => role.id === nextRoleId)?.name ?? "");
              onFormChange({
                ...form,
                roleId: nextRoleId,
                branchIds: nextLabel === "Dueno del gimnasio"
                  ? branches.map((branch) => branch.id)
                  : form.branchIds,
              });
            }}
            required
            value={form.roleId}
          >
            {roles.map((role) => (
              <option key={role.id} value={role.id}>
                {displayRoleName(role.name)}
              </option>
            ))}
          </select>
        </label>

        <section className="team-branch-selector">
          <div>
            <strong>Sucursales permitidas</strong>
            <span>
              {ownerRoleSelected
                ? "El dueno del gimnasio recibe acceso a todas las sucursales actuales."
                : "Selecciona todas las sedes donde este usuario puede trabajar."}
            </span>
          </div>
          {branches.map((branch) => (
            <label key={branch.id}>
              <input
                checked={ownerRoleSelected || form.branchIds.includes(branch.id)}
                disabled={ownerRoleSelected}
                onChange={(event) => onBranchAccessChange(branch.id, event.target.checked)}
                type="checkbox"
              />
              <span>{branch.name}</span>
              <small>{branch.code}</small>
            </label>
          ))}
          {errors.branchIds ? <small className="form-field-error">{errors.branchIds}</small> : null}
        </section>

        <button className="form-primary-button" disabled={saving || roles.length === 0} type="submit">
          Crear usuario
        </button>
      </form>
    </AppDrawer>
  );
}

function Field({
  error,
  help,
  label,
  required = true,
  value,
  onChange,
  type = "text",
}: {
  error?: string;
  help?: string;
  label: string;
  required?: boolean;
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
        required={required && label !== "Telefono" && label !== "Email"}
        type={type}
        value={value}
      />
      {error ? <small className="form-field-error">{error}</small> : help ? <small>{help}</small> : null}
    </label>
  );
}
