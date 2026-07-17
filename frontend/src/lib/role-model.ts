import type { CurrentUser } from "@/lib/api";

export type UserConsole = "PLATFORM_ADMIN" | "GYM_ADMIN" | "RECEPTION" | "COACH";

export type ConsoleProfile = {
  console: UserConsole;
  label: string;
  description: string;
  homePath: string;
};

export function resolveUserConsole(user: CurrentUser): UserConsole {
  const permissions = new Set(user.permissions);

  if (permissions.has("platform.admin")) {
    return "PLATFORM_ADMIN";
  }
  if (permissions.has("sale.create") || permissions.has("payment.manual.create") || permissions.has("client.create")) {
    return "RECEPTION";
  }
  if (permissions.has("coach.read") || permissions.has("workout.manage")) {
    return "COACH";
  }
  return "GYM_ADMIN";
}

export function getConsoleProfile(user: CurrentUser): ConsoleProfile {
  const console = resolveUserConsole(user);
  if (console === "PLATFORM_ADMIN") {
    return {
      console,
      label: "Administrador de plataforma",
      description: "Crea gimnasios, cambia de gimnasio y prepara configuracion base.",
      homePath: "/platform/gymnasiums",
    };
  }
  if (console === "RECEPTION") {
    return {
      console,
      label: "Recepcion y caja",
      description: "Atiende socios, cobra ventas y opera el mostrador.",
      homePath: "/reception",
    };
  }
  if (console === "COACH") {
    return {
      console,
      label: "Coach",
      description: "Consulta socios asignados y gestiona rutinas o servicios permitidos.",
      homePath: "/coaches",
    };
  }
  return {
    console,
    label: "Gerencia del gimnasio",
    description: "Consulta reportes y administra la operacion del gimnasio.",
    homePath: "/dashboard",
  };
}

export function getPostLoginPath(user: CurrentUser) {
  return getConsoleProfile(user).homePath;
}

export function isPlatformAdmin(user: CurrentUser) {
  return resolveUserConsole(user) === "PLATFORM_ADMIN";
}

export function canAccessRoute(user: CurrentUser, pathname: string) {
  const console = resolveUserConsole(user);
  if (pathname === "/" || pathname === "/login" || pathname.startsWith("/kiosk")) {
    return true;
  }
  if (pathname.startsWith("/platform")) {
    return console === "PLATFORM_ADMIN";
  }
  if (console === "PLATFORM_ADMIN") {
    return true;
  }
  if (console === "RECEPTION") {
    return receptionRoutes.some((route) => matches(pathname, route));
  }
  if (console === "COACH") {
    return coachRoutes.some((route) => matches(pathname, route));
  }
  return gymAdminRoutes.some((route) => matches(pathname, route));
}

function matches(pathname: string, route: string) {
  return pathname === route || pathname.startsWith(`${route}/`);
}

const receptionRoutes = ["/reception", "/pos", "/clients", "/sales", "/reports/sales", "/benefits"];
const coachRoutes = ["/coaches", "/clients"];
const gymAdminRoutes = [
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
  "/coaches",
  "/wellness",
];
