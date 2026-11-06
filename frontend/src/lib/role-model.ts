import type { CurrentUser, UserConsole } from "@/lib/api";

export type { UserConsole };

export type ConsoleProfile = {
  console: UserConsole;
  label: string;
  description: string;
  homePath: string;
};

export function resolveUserConsole(user: CurrentUser): UserConsole {
	if (user.accountScope === "PLATFORM" && !user.supportMode) {
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
      label: "Admin GymFort",
      description: "Vende, crea, configura y da soporte a gimnasios cliente.",
      homePath: "/platform",
    };
  }
  if (console === "TENANT_OWNER") {
    return {
      console,
      label: user.supportMode ? "Soporte GymFort" : "Dueño del gimnasio",
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
      description: "Indicadores, socios, operación y administración autorizada.",
      homePath: managerHomePath(user),
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
    return "/platform";
  }
  return getConsoleProfile(user).homePath;
}

export function isPlatformAdmin(user: CurrentUser) {
	return user.accountScope === "PLATFORM";
}

export function isSupportMode(user: CurrentUser) {
  return Boolean(user.supportMode);
}

export function canAccessRoute(user: CurrentUser, pathname: string) {
  const console = resolveUserConsole(user);
  if (matches(pathname, "/reception/check-in")) {
    return canRegisterCheckIn(user);
  }
  if (matches(pathname, "/kiosk/launch")) {
    return canLaunchKiosk(user);
  }
  if (publicRoutes.some((route) => matches(pathname, route)) || pathname.startsWith("/kiosk")) {
    return true;
  }
  if (pathname.startsWith("/platform")) {
		return user.accountScope === "PLATFORM" && !user.supportMode;
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
    if (matches(pathname, "/dashboard")) {
      return user.permissions.includes("report.owner.read");
    }
    if (matches(pathname, "/settings/organization")) {
      return hasAny(user, ["payment.settings.manage", "selfservice.settings.manage", "notification.settings.manage"]);
    }
    return managerRoutes.some((route) => matches(pathname, route));
  }
  if (console === "RECEPTION") {
    return receptionRoutes.some((route) => matches(pathname, route));
  }
  return coachRoutes.some((route) => matches(pathname, route));
}

export function canRegisterCheckIn(user: CurrentUser) {
  return user.accountScope === "TENANT" && user.permissions.includes("access.check-in");
}

export function canLaunchKiosk(user: CurrentUser) {
  return resolveUserConsole(user) === "RECEPTION"
    && user.permissions.includes("selfservice.session.launch");
}

function hasAny(user: CurrentUser, permissions: string[]) {
  return permissions.some((permission) => user.permissions.includes(permission));
}

function managerHomePath(user: CurrentUser) {
  if (user.permissions.includes("report.owner.read")) return "/dashboard";
  if (user.permissions.includes("report.sales.read")) return "/reports/sales";
  if (hasAny(user, ["client.read", "client.create", "client.update", "membership.read", "membership.renew", "membership.change-plan"])) return "/clients";
  if (user.permissions.includes("booking.read")) return "/bookings";
  if (user.permissions.includes("professional.read")) return "/wellness";
  if (user.permissions.includes("coach.read")) return "/coaches";
  if (user.permissions.includes("inventory.read")) return "/inventory";
  if (user.permissions.includes("sale.create")) return "/pos";
  if (hasAny(user, ["payment.settings.manage", "selfservice.settings.manage", "notification.settings.manage"])) return "/settings/organization";
  if (user.permissions.includes("user.read")) return "/users";
  if (user.permissions.includes("catalog.read")) return "/catalog";
  if (user.permissions.includes("benefit.read")) return "/benefits";
  if (user.permissions.includes("automation.read")) return "/automations";
  return "/clients";
}

function isKnownConsole(value: string): value is UserConsole {
  return ["PLATFORM_OWNER", "TENANT_OWNER", "TENANT_MANAGER", "RECEPTION", "COACH"].includes(value);
}

function matches(pathname: string, route: string) {
  return pathname === route || pathname.startsWith(`${route}/`);
}

const tenantOwnerRoutes = [
  "/dashboard",
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

const receptionRoutes = ["/reception", "/pos", "/clients", "/sales", "/reports/sales", "/bookings", "/wellness"];
const coachRoutes = ["/coaches", "/clients"];
const publicRoutes = ["/", "/login", "/accept-invitation", "/reset-password", "/privacy-policy", "/terms-of-service", "/support", "/privacidad", "/terminos"];
