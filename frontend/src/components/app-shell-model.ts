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
  "membership.renew",
  "membership.change-plan",
  "sale.create",
];

const platformSections: NavigationSection[] = [
  {
    title: "Dirección GymFort",
    links: [
      { label: "Resumen de cartera", href: "/platform", hint: "Prioridades, salud y MRR", permissions: ["platform.admin"], requiresBranch: false },
    ],
  },
  {
    title: "Clientes",
    links: [
      { label: "Gimnasios cliente", href: "/platform/gymnasiums", hint: "Expedientes, tenants y soporte", permissions: ["platform.admin"], requiresBranch: false },
    ],
  },
  {
    title: "Ingresos",
    links: [
      { label: "Cobros y contratos", href: "/platform/billing", hint: "Planes, vencidos y pagos", permissions: ["platform.admin"], requiresBranch: false },
    ],
  },
];

const ownerSections: NavigationSection[] = [
  {
    title: "Dirección",
    links: [
      { label: "Métricas del negocio", href: "/dashboard", hint: "Ingresos, socios y salud", permissions: ["report.owner.read"] },
      { label: "Ventas y pagos", href: "/reports/sales", hint: "Reportes financieros", permissions: ["report.sales.read"] },
    ],
  },
  {
    title: "Gestión operativa",
    links: [
      { label: "Socios", href: "/clients", hint: "Altas, fichas y membresías", permissions: memberWorkflowPermissions },
      { label: "Reservas", href: "/bookings", hint: "Agenda y recursos", permissions: ["booking.read"] },
      { label: "Wellness", href: "/wellness", hint: "Bienestar", permissions: ["professional.read"] },
      { label: "Inventario", href: "/inventory", hint: "Stock y movimientos", permissions: ["inventory.read"] },
    ],
  },
  {
    title: "Administración",
    links: [
      { label: "Equipo y accesos", href: "/users", hint: "Dueños, gerentes y staff", permissions: ["user.read"] },
      { label: "Catálogo", href: "/catalog", hint: "Planes y productos", permissions: ["catalog.read"] },
      { label: "Beneficios", href: "/benefits", hint: "Promociones, descuentos y accesos", permissions: ["benefit.read"] },
      { label: "Automatizaciones", href: "/automations", hint: "Reglas y avisos", permissions: ["automation.read"] },
      { label: "Configuración", href: "/settings/organization", hint: "Sucursales, cobros, kiosco y correo", permissions: ["organization.update", "branch.update"] },
    ],
  },
  {
    title: "Mostrador",
    links: [
      { label: "Caja / POS", href: "/pos", hint: "Ventas y pagos", permissions: ["sale.create"] },
      { label: "Registro de entradas", href: "/reception/check-in", hint: "Acceso presencial e historial", permissions: ["access.check-in"] },
      { label: "Accesos", href: "/access", hint: "Visitas y pases", permissions: ["access.read", "visit.read"] },
    ],
  },
];

const managerSections: NavigationSection[] = [
  {
    title: "Gerencia",
    links: [
      { label: "Indicadores", href: "/dashboard", hint: "Resumen ejecutivo", permissions: ["report.owner.read"] },
      { label: "Ventas y pagos", href: "/reports/sales", hint: "Pagos y saldos", permissions: ["report.sales.read"] },
    ],
  },
  {
    title: "Gestión operativa",
    links: [
      { label: "Socios", href: "/clients", hint: "Altas y fichas", permissions: memberWorkflowPermissions },
      { label: "Reservas", href: "/bookings", hint: "Agenda", permissions: ["booking.read"] },
      { label: "Wellness", href: "/wellness", hint: "Bienestar", permissions: ["professional.read", "booking.read"] },
      { label: "Inventario", href: "/inventory", hint: "Stock y movimientos", permissions: ["inventory.read"] },
    ],
  },
  {
    title: "Administración",
    links: [
      { label: "Equipo y accesos", href: "/users", hint: "Usuarios autorizados", permissions: ["user.read"] },
      { label: "Catálogo", href: "/catalog", hint: "Planes y productos", permissions: ["catalog.read"] },
      { label: "Beneficios", href: "/benefits", hint: "Promociones y convenios", permissions: ["benefit.read"] },
      { label: "Automatizaciones", href: "/automations", hint: "Reglas, avisos e historial", permissions: ["automation.read"] },
      { label: "Configuración", href: "/settings/organization", hint: "Cobros, kiosco y correo", permissions: ["payment.settings.manage", "selfservice.settings.manage", "notification.settings.manage"] },
    ],
  },
  {
    title: "Mostrador",
    links: [
      { label: "Caja / POS", href: "/pos", hint: "Ventas", permissions: ["sale.create"] },
      { label: "Registro de entradas", href: "/reception/check-in", hint: "Acceso presencial e historial", permissions: ["access.check-in"] },
    ],
  },
];

const receptionSections: NavigationSection[] = [
  {
    title: "Mostrador",
    links: [
      { label: "Inicio recepción", href: "/reception", hint: "Acciones del día", permissions: ["client.read", "sale.create"] },
      { label: "Caja / POS", href: "/pos", hint: "Ventas y pagos", permissions: ["sale.create"] },
      { label: "Registro de entradas", href: "/reception/check-in", hint: "Acceso presencial e historial", permissions: ["access.check-in"] },
      { label: "Pantalla para socios", href: "/kiosk/launch", hint: "Abrir kiosco seguro", permissions: ["selfservice.session.launch"] },
    ],
  },
  {
    title: "Atención a socios",
    links: [
      { label: "Socios", href: "/clients", hint: "Altas y fichas", permissions: memberWorkflowPermissions },
      { label: "Reservas", href: "/bookings", hint: "Agenda", permissions: ["booking.read"] },
      { label: "Wellness", href: "/wellness", hint: "Bienestar", permissions: ["professional.read", "booking.read"] },
    ],
  },
  {
    title: "Seguimiento",
    links: [
      { label: "Ventas y pagos", href: "/reports/sales", hint: "Pagos pendientes", permissions: ["report.sales.read"] },
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
  if (href === "/platform") {
    return pathname === href;
  }
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
      logoUrl: "/gymfort_web_assets/gymfort-isotipo-128.png",
      name: "GymFort Plataforma",
      scope: "CRM de gimnasios",
    };
  }

  return {
    logoUrl: session.user.logoUrl ?? "/gymfort_web_assets/gymfort-isotipo-128.png",
    name: organizationName ?? session.user.brandName ?? session.user.organizationName ?? "GymFort",
    scope: visibleBranch,
  };
}
