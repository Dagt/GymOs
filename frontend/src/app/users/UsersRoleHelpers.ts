import {
  type FieldErrorMap,
  validateEmail,
  validatePassword,
  validatePhone,
  validateRequired,
} from "@/components/FormFields";
import type { CreateInternalUserPayload, InternalUserSummary, RoleOption } from "@/lib/api";

export function displayRoleName(roleName: string) {
  const normalized = normalizeRoleName(roleName);
  if (normalized.includes("admin") && normalized.includes("gymos")) {
    return "Admin GymOS";
  }
  if (normalized === "administrador" || normalized.includes("owner")) {
    return "Dueno del gimnasio";
  }
  if (normalized.includes("dueno")) {
    return "Dueno del gimnasio";
  }
  if (normalized.includes("gerencia") || normalized.includes("gerente")) {
    return "Gerente";
  }
  if (normalized.includes("recepcion") || normalized.includes("caja")) {
    return "Recepcion";
  }
  if (normalized.includes("coach")) {
    return "Coach";
  }
  return roleName;
}

export function canShowRoleForSession(currentRoles: string[], roleName: string, platformAdmin: boolean) {
  const label = displayRoleName(roleName);
  if (label === "Admin GymOS") {
    return false;
  }
  if (platformAdmin) {
    return true;
  }
  const currentLabels = currentRoles.map(displayRoleName);
  const isTenantOwner = currentLabels.includes("Dueno del gimnasio");
  const isTenantManager = currentLabels.includes("Gerente");
  if (isTenantOwner) {
    return label !== "Dueno del gimnasio";
  }
  if (isTenantManager) {
    return label === "Recepcion" || label === "Coach";
  }
  return label === "Recepcion" || label === "Coach";
}

export function canSeeUserForSession(currentRoles: string[], targetRoles: string[], platformAdmin: boolean) {
  if (platformAdmin || isTenantOwner(currentRoles)) {
    return true;
  }
  if (isTenantManager(currentRoles)) {
    return !targetRoles.some((role) => {
      const label = displayRoleName(role);
      return label === "Dueno del gimnasio" || label === "Gerente";
    });
  }
  return false;
}

export function canManageUserForSession(currentRoles: string[], targetRoles: string[], platformAdmin: boolean) {
  if (platformAdmin) {
    return true;
  }
  if (targetRoles.some((role) => displayRoleName(role) === "Dueno del gimnasio")) {
    return false;
  }
  return canSeeUserForSession(currentRoles, targetRoles, false);
}

export function roleDescription(roleName: string, fallback?: string | null) {
  const label = displayRoleName(roleName);
  if (label === "Admin GymOS") {
    return "Crea gimnasios cliente y entra en modo soporte auditable.";
  }
  if (label === "Dueno del gimnasio") {
    return "Administra su gimnasio, sucursales, equipo, configuracion y metricas.";
  }
  if (label === "Gerente") {
    return "Gestiona operacion diaria, equipo autorizado, inventario, agenda y reportes.";
  }
  if (label === "Recepcion") {
    return "Registra socios, cobra ventas, renueva membresias y atiende mostrador.";
  }
  if (label === "Coach") {
    return "Consulta socios asignados y gestiona rutinas o servicios permitidos.";
  }
  return fallback ?? "Rol operativo";
}

export function validateUserForm(form: CreateInternalUserPayload) {
  const errors: FieldErrorMap = {};
  addError(errors, "firstName", validateRequired(form.firstName, "Nombre"));
  addError(errors, "lastName", validateRequired(form.lastName, "Apellido paterno"));
  addError(errors, "email", validateEmail(form.email ?? "", true));
  addError(errors, "phone", validatePhone(form.phone ?? "", true));
  addError(errors, "username", validateRequired(form.username, "Usuario de inicio de sesion"));
  addError(errors, "temporaryPassword", validatePassword(form.temporaryPassword));
  addError(errors, "roleId", validateRequired(form.roleId, "Rol"));
  if (form.branchIds.length === 0) {
    errors.branchIds = "Selecciona al menos una sucursal.";
  }
  return errors;
}

export function roleIdForUser(user: InternalUserSummary, roles: RoleOption[]) {
  const currentRoleLabel = displayRoleName(user.roles[0] ?? "");
  return roles.find((role) => displayRoleName(role.name) === currentRoleLabel)?.id ?? "";
}

function normalizeRoleName(roleName: string) {
  return roleName
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase();
}

function isTenantOwner(currentRoles: string[]) {
  return currentRoles.map(displayRoleName).includes("Dueno del gimnasio");
}

function isTenantManager(currentRoles: string[]) {
  return currentRoles.map(displayRoleName).includes("Gerente");
}

function addError(errors: FieldErrorMap, key: string, value: string) {
  if (value) {
    errors[key] = value;
  }
}
