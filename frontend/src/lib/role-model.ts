import type { CurrentUser, UserConsole } from "@/lib/api";

export type { UserConsole };

export type ConsoleProfile = {
  console: UserConsole;
  label: string;
  description: string;
  homePath: string;
};

export function resolveUserConsole(user: CurrentUser): UserConsole {
  if (user.platformAdmin && !user.supportMode) {
    return "PLATFORM_OWNER";
  }
  if (isPlatformAdmin(user) && !user.supportMode) {
    return "PLATFORM_OWNER";
  }
  if (isKnownConsole(user.console)) {
    return user.console;
  }
  const roles = user.roles.map((role) => role.toLowerCase());
  if (roles.some((role) => role.includes("dueno") || role.includes("owner") || role.includes("administrador"))) {
    return "TENANT_OWNER";
  }
  if (roles.some((role) => role.includes("gerente"))) {
    return "TENANT_MANAGER";
  }
  if (hasAny(user, ["sale.create", "payment.manual.create", "client.create"])) {
    return "RECEPTION";
  }
  if (hasAny(user, ["coach.read", "workout.manage.own", "workout.manage.all"])) {
    return "COACH";
  }
  return "TENANT_MANAGER";
}

export function getConsoleProfile(user: CurrentUser): ConsoleProfile {
  const console = resolveUserConsole(user);
  if (console === "PLATFORM_OWNER") {
    return {
      console,
      label: "Admin GymOS",
      description: "Vende, crea, configura y da soporte a gimnasios cliente.",
      homePath: "/platform/gymnasiums",
    };
  }
  if (console === "TENANT_OWNER") {
    return {
      console,
      label: user.supportMode ? "Soporte GymOS" : "Dueño del gimnasio",
      description: user.supportMode
        ? "Estas dentro de este gimnasio para apoyar al cliente."
        : "Metricas, sucursales, equipo y decisiones del negocio.",
      homePath: "/dashboard",
    };
  }
  if (console === "TENANT_MANAGER") {
    return {
      console,
      label: "Gerente",
      description: "Operacion diaria, pendientes, equipo y administracion autorizada.",
      homePath: "/operations",
    };
  }
  if (console === "RECEPTION") {
    return {
      console,
      label: "Recepcion",
      description: "Clientes, ventas, pagos, membresias y reservas por sucursal.",
      homePath: "/reception",
    };
  }
  return {
    console,
    label: "Coach",
    description: "Clientes asignados, rutinas y seguimiento de entrenamiento.",
    homePath: "/coaches",
  };
}

export function getPostLoginPath(user: CurrentUser) {
  if (isPlatformAdmin(user) && !user.supportMode) {
    return "/platform/gymnasiums";
  }
  return getConsoleProfile(user).homePath;
}

export function isPlatformAdmin(user: CurrentUser) {
  return user.platformAdmin || user.permissions.includes("platform.admin");
}

export function isSupportMode(user: CurrentUser) {
  return Boolean(user.supportMode);
}

export function canAccessRoute(user: CurrentUser, pathname: string) {
  const console = resolveUserConsole(user);
  if (pathname === "/" || pathname === "/login" || pathname.startsWith("/kiosk")) {
    return true;
  }
  if (pathname.startsWith("/platform")) {
    return isPlatformAdmin(user);
  }
  if (isPlatformAdmin(user) && !user.supportMode) {
    return false;
  }
  if (isPlatformAdmin(user) && user.supportMode) {
    return tenantOwnerRoutes.some((route) => matches(pathname, route));
  }
  if (console === "PLATFORM_OWNER") {
    return pathname.startsWith("/platform");
  }
  if (console === "TENANT_OWNER") {
    return tenantOwnerRoutes.some((route) => matches(pathname, route));
  }
  if (console === "TENANT_MANAGER") {
    return managerRoutes.some((route) => matches(pathname, route));
  }
  if (console === "RECEPTION") {
    return receptionRoutes.some((route) => matches(pathname, route));
  }
  return coachRoutes.some((route) => matches(pathname, route));
}

function hasAny(user: CurrentUser, permissions: string[]) {
  return permissions.some((permission) => user.permissions.includes(permission));
}

function isKnownConsole(value: string): value is UserConsole {
  return ["PLATFORM_OWNER", "TENANT_OWNER", "TENANT_MANAGER", "RECEPTION", "COACH"].includes(value);
}

function matches(pathname: string, route: string) {
  return pathname === route || pathname.startsWith(`${route}/`);
}

const tenantOwnerRoutes = [
  "/dashboard",
  "/operations",
  "/reports",
  "/settings/organization",
  "/users",
  "/catalog",
  "/access",
  "/bookings",
  "/inventory",
  "/benefits",
  "/automations",
  "/clients",
  "/clients/import",
  "/coaches",
  "/wellness",
  "/reception",
  "/pos",
  "/sales",
];

const managerRoutes = [
  "/operations",
  "/dashboard",
  "/reports",
  "/users",
  "/catalog",
  "/access",
  "/bookings",
  "/inventory",
  "/benefits",
  "/automations",
  "/clients",
  "/coaches",
  "/wellness",
  "/reception",
  "/pos",
  "/sales",
];

const receptionRoutes = ["/reception", "/pos", "/clients", "/sales", "/reports/sales", "/bookings", "/wellness"];
const coachRoutes = ["/coaches", "/clients"];
