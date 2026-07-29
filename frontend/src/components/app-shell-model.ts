import type { StoredSession } from "../lib/api/types.ts";
import { isPlatformAdmin, isSupportMode, type UserConsole } from "../lib/role-model.ts";

export type ShellBrand = {
  logoUrl?: string | null;
  name: string;
  scope: string;
};

export type NavigationLink = {
  label: string;
  href: string;
  hint: string;
  permissions?: string[];
  requiresBranch?: boolean;
};

export type NavigationSection = {
  title: string;
  links: NavigationLink[];
};

const memberWorkflowPermissions = [
  "client.read",
  "client.create",
  "client.update",
  "membership.read",
  "membership.create",
  "membership.update",
];

const platformSections: NavigationSection[] = [
  {
    title: "Negocio GymOS",
    links: [
      { label: "Gimnasios cliente", href: "/platform/gymnasiums", hint: "CRM, tenants y soporte", permissions: ["platform.admin"] },
    ],
  },
];

const ownerSections: NavigationSection[] = [
  {
    title: "Direccion",
    links: [
      { label: "Metricas del negocio", href: "/dashboard", hint: "Ingresos, socios y salud", permissions: ["report.owner.read", "report.sales.read"] },
      { label: "Ventas y pagos", href: "/reports/sales", hint: "Reportes financieros", permissions: ["report.sales.read"] },
      { label: "Coaches y rutinas", href: "/coaches", hint: "Equipo y seguimiento", permissions: ["coach.read", "report.coaches.read"] },
    ],
  },
  {
    title: "Administracion del gimnasio",
    links: [
      { label: "Operacion", href: "/operations", hint: "Pendientes y herramientas", permissions: ["operations.read", "inventory.read", "booking.read"] },
      { label: "Socios", href: "/clients", hint: "Altas, fichas y membresias", permissions: memberWorkflowPermissions },
      { label: "Equipo y accesos", href: "/users", hint: "Duenos, gerentes y staff", permissions: ["user.read"] },
      { label: "Datos y sucursales", href: "/settings/organization", hint: "Marca, sedes y contacto", permissions: ["organization.update", "branch.update"] },
      { label: "Catalogo", href: "/catalog", hint: "Planes y productos", permissions: ["catalog.read"] },
      { label: "Inventario", href: "/inventory", hint: "Stock y movimientos", permissions: ["inventory.read"] },
      { label: "Reservas", href: "/bookings", hint: "Agenda y recursos", permissions: ["booking.read"] },
      { label: "Wellness", href: "/wellness", hint: "Bienestar", permissions: ["professional.read"] },
      { label: "Automatizaciones", href: "/automations", hint: "Reglas y avisos", permissions: ["automation.read"] },
    ],
  },
  {
    title: "Mostrador",
    links: [
      { label: "Caja / POS", href: "/pos", hint: "Ventas y pagos", permissions: ["sale.create"] },
      { label: "Accesos", href: "/access", hint: "Visitas y pases", permissions: ["access.read", "visit.read"] },
      { label: "Beneficios", href: "/benefits", hint: "Promos y reglas", permissions: ["benefit.read"] },
    ],
  },
];

const managerSections: NavigationSection[] = [
  {
    title: "Gerencia",
    links: [
      { label: "Operacion", href: "/operations", hint: "Pendientes diarios", permissions: ["operations.read", "booking.read", "inventory.read"] },
      { label: "Indicadores", href: "/dashboard", hint: "Resumen ejecutivo", permissions: ["report.owner.read", "report.sales.read"] },
      { label: "Ventas", href: "/reports/sales", hint: "Pagos y saldos", permissions: ["report.sales.read"] },
    ],
  },
  {
    title: "Administracion diaria",
    links: [
      { label: "Socios", href: "/clients", hint: "Altas y fichas", permissions: memberWorkflowPermissions },
      { label: "Equipo", href: "/users", hint: "Usuarios autorizados", permissions: ["user.read"] },
      { label: "Catalogo", href: "/catalog", hint: "Planes y productos", permissions: ["catalog.read"] },
      { label: "Inventario", href: "/inventory", hint: "Stock", permissions: ["inventory.read"] },
      { label: "Reservas", href: "/bookings", hint: "Agenda", permissions: ["booking.read"] },
      { label: "Wellness", href: "/wellness", hint: "Bienestar", permissions: ["professional.read", "booking.read"] },
      { label: "Coaches", href: "/coaches", hint: "Rutinas", permissions: ["coach.read"] },
      { label: "Beneficios", href: "/benefits", hint: "Promociones y convenios", permissions: ["benefit.read"] },
    ],
  },
  {
    title: "Mostrador",
    links: [
      { label: "Caja / POS", href: "/pos", hint: "Ventas", permissions: ["sale.create"] },
    ],
  },
];

const receptionSections: NavigationSection[] = [
  {
    title: "Mostrador",
    links: [
      { label: "Inicio recepcion", href: "/reception", hint: "Acciones del dia", permissions: ["client.read", "sale.create"] },
      { label: "Renovaciones", href: "/reception/renewals", hint: "Planes y extensión", permissions: ["membership.update", "sale.create"] },
      { label: "Caja / POS", href: "/pos", hint: "Ventas y pagos", permissions: ["sale.create"] },
      { label: "Socios", href: "/clients", hint: "Altas y fichas", permissions: memberWorkflowPermissions },
      { label: "Reservas", href: "/bookings", hint: "Agenda", permissions: ["booking.read"] },
      { label: "Wellness", href: "/wellness", hint: "Bienestar", permissions: ["professional.read", "booking.read"] },
      { label: "Ventas", href: "/reports/sales", hint: "Pagos pendientes", permissions: ["report.sales.read"] },
    ],
  },
];

const coachSections: NavigationSection[] = [
  {
    title: "Entrenamiento",
    links: [
      { label: "Rutinas", href: "/coaches", hint: "Planes y asignaciones", permissions: ["coach.read", "workout.read.own", "workout.read.all"] },
      {
        label: "Socios",
        href: "/clients",
        hint: "Consulta de contacto para rutinas",
        permissions: memberWorkflowPermissions,
      },
    ],
  },
];

export function sectionsForConsole(consoleType: UserConsole, variant?: "platform" | "tenant") {
  if (variant === "platform" || consoleType === "PLATFORM_OWNER") {
    return platformSections;
  }
  if (consoleType === "TENANT_OWNER") {
    return ownerSections;
  }
  if (consoleType === "TENANT_MANAGER") {
    return managerSections;
  }
  if (consoleType === "RECEPTION") {
    return receptionSections;
  }
  return coachSections;
}

export function visibleSectionsForConsole({
  consoleType,
  hasBranch,
  permissions,
  variant,
}: {
  consoleType: UserConsole;
  hasBranch: boolean;
  permissions: Set<string>;
  variant?: "platform" | "tenant";
}) {
  return sectionsForConsole(consoleType, variant)
    .map((section) => ({
      ...section,
      links: section.links.filter((link) => canSeeLink(permissions, link) && canUseWithoutBranch(hasBranch, link)),
    }))
    .filter((section) => section.links.length > 0);
}

export function canSeeLink(permissions: Set<string>, link: NavigationLink) {
  if (!link.permissions || link.permissions.length === 0) {
    return true;
  }
  return link.permissions.some((permission) => permissions.has(permission));
}

export function canUseWithoutBranch(hasBranch: boolean, link: NavigationLink) {
  return hasBranch || link.requiresBranch === false || link.href === "/users" || link.href === "/settings/organization";
}

export function isActive(pathname: string, href: string) {
  return pathname === href || pathname.startsWith(`${href}/`);
}

export function resolveShellBrand({
  organizationName,
  session,
  variant,
  visibleBranch,
}: {
  organizationName?: string | null;
  session: StoredSession;
  variant?: "platform" | "tenant";
  visibleBranch: string;
}): ShellBrand {
  if (variant === "platform" || (isPlatformAdmin(session.user) && !isSupportMode(session.user))) {
    return {
      logoUrl: null,
      name: "GymOS Plataforma",
      scope: "CRM de gimnasios",
    };
  }

  return {
    logoUrl: session.user.logoUrl,
    name: organizationName ?? session.user.brandName ?? session.user.organizationName ?? "GymOS",
    scope: visibleBranch,
  };
}
