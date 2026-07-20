"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import { clearSession, getBranches, selectBranch, selectOrganization, storeSession, type StoredSession } from "@/lib/api";
import {
  getConsoleProfile,
  isPlatformAdmin,
  isSupportMode,
  resolveUserConsole,
  type UserConsole,
} from "@/lib/role-model";

type ShellAction = {
  href: string;
  label: string;
};

type NavigationLink = {
  label: string;
  href: string;
  hint: string;
  permissions?: string[];
  requiresBranch?: boolean;
};

type NavigationSection = {
  title: string;
  links: NavigationLink[];
};

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
      { label: "Equipo y accesos", href: "/users", hint: "Dueños, gerentes y staff", permissions: ["user.read"] },
      { label: "Datos y sucursales", href: "/settings/organization", hint: "Marca, sedes y contacto", permissions: ["organization.update", "branch.update"] },
      { label: "Catalogo", href: "/catalog", hint: "Planes y productos", permissions: ["catalog.read"] },
      { label: "Inventario", href: "/inventory", hint: "Stock y movimientos", permissions: ["inventory.read"] },
      { label: "Reservas", href: "/bookings", hint: "Agenda y recursos", permissions: ["booking.read"] },
      { label: "Servicios", href: "/wellness", hint: "Bienestar", permissions: ["professional.read"] },
      { label: "Automatizaciones", href: "/automations", hint: "Reglas y avisos", permissions: ["automation.read"] },
    ],
  },
  {
    title: "Mostrador",
    links: [
      { label: "Caja / POS", href: "/pos", hint: "Ventas y pagos", permissions: ["sale.create"] },
      { label: "Socios", href: "/clients", hint: "Altas y fichas", permissions: ["client.read"] },
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
      { label: "Equipo", href: "/users", hint: "Usuarios autorizados", permissions: ["user.read"] },
      { label: "Catalogo", href: "/catalog", hint: "Planes y productos", permissions: ["catalog.read"] },
      { label: "Inventario", href: "/inventory", hint: "Stock", permissions: ["inventory.read"] },
      { label: "Reservas", href: "/bookings", hint: "Agenda", permissions: ["booking.read"] },
      { label: "Coaches", href: "/coaches", hint: "Rutinas", permissions: ["coach.read"] },
    ],
  },
  {
    title: "Mostrador",
    links: [
      { label: "Caja / POS", href: "/pos", hint: "Ventas", permissions: ["sale.create"] },
      { label: "Socios", href: "/clients", hint: "Clientes", permissions: ["client.read"] },
    ],
  },
];

const receptionSections: NavigationSection[] = [
  {
    title: "Mostrador",
    links: [
      { label: "Inicio recepcion", href: "/reception", hint: "Acciones del dia", permissions: ["client.read", "sale.create"] },
      { label: "Caja / POS", href: "/pos", hint: "Ventas y pagos", permissions: ["sale.create"] },
      { label: "Socios", href: "/clients", hint: "Altas y fichas", permissions: ["client.read"] },
      { label: "Reservas", href: "/bookings", hint: "Agenda", permissions: ["booking.read"] },
      { label: "Ventas", href: "/reports/sales", hint: "Pagos pendientes", permissions: ["report.sales.read"] },
      { label: "Beneficios", href: "/benefits", hint: "Aplicacion", permissions: ["benefit.read"] },
    ],
  },
];

const coachSections: NavigationSection[] = [
  {
    title: "Entrenamiento",
    links: [
      { label: "Rutinas", href: "/coaches", hint: "Planes y asignaciones", permissions: ["coach.read", "workout.read"] },
      { label: "Socios", href: "/clients", hint: "Consulta autorizada", permissions: ["client.read"] },
    ],
  },
];

export function AppShell({
  actions = [],
  branchName,
  children,
  eyebrow,
  hideSidebar = false,
  organizationName,
  mode,
  session,
  subtitle,
  title,
  variant,
}: {
  actions?: ShellAction[];
  branchName?: string | null;
  children: ReactNode;
  eyebrow: string;
  hideSidebar?: boolean;
  mode?: "setup" | "operation";
  organizationName?: string | null;
  session: StoredSession;
  subtitle?: string;
  title: string;
  variant?: "platform" | "tenant";
}) {
  const pathname = usePathname();
  const router = useRouter();
  const [resolvedBranch, setResolvedBranch] = useState<{ branchId: string; name: string } | null>(null);
  const [availableBranches, setAvailableBranches] = useState<{ branchId: string; name: string }[]>([]);
  const [switchingBranch, setSwitchingBranch] = useState(false);
  const consoleType = resolveUserConsole(session.user);
  const role = getConsoleProfile(session.user);
  const hasBranch = mode === "operation" ? true : mode === "setup" ? false : Boolean(branchName || session.user.branchId);
  const visibleBranch =
    branchName
    ?? (resolvedBranch?.branchId === session.user.branchId ? resolvedBranch.name : null)
    ?? (session.user.branchId ? "Sucursal seleccionada" : "Sin sucursal");
  const permissionSet = new Set(session.user.permissions);
  const showStaticBranchChip = variant !== "platform" && !isPlatformAdmin(session.user) && consoleType !== "TENANT_OWNER";
  const isSingleBranchOperationalUser =
    (consoleType === "TENANT_MANAGER" || consoleType === "RECEPTION" || consoleType === "COACH")
    && availableBranches.length <= 1;
  const showBranchSwitcher = variant !== "platform" && availableBranches.length > 0 && !isSingleBranchOperationalUser;
  const sections = sectionsForConsole(consoleType, variant)
    .map((section) => ({
      ...section,
      links: section.links.filter((link) => canSeeLink(permissionSet, link) && canUseWithoutBranch(hasBranch, link)),
    }))
    .filter((section) => section.links.length > 0);
  const visibleActions = variant === "platform" ? actions : [];

  useEffect(() => {
    if (variant === "platform" || (isPlatformAdmin(session.user) && !isSupportMode(session.user))) {
      return;
    }
    let cancelled = false;
    async function resolveBranchName() {
      try {
        const branches = await getBranches(session.accessToken);
        if (cancelled) {
          return;
        }
        setAvailableBranches(branches.map((item) => ({ branchId: item.id, name: item.name })));
        if (!branchName && session.user.branchId) {
          const branch = branches.find((item) => item.id === session.user.branchId);
          if (branch) {
            setResolvedBranch({ branchId: branch.id, name: branch.name });
          }
        }
      } catch {
        // The shell falls back to a neutral label if branch lookup is unavailable.
      }
    }
    void resolveBranchName();
    return () => {
      cancelled = true;
    };
  }, [branchName, session.accessToken, session.user, session.user.branchId, variant]);

  useEffect(() => {
    if (hasBranch || variant === "platform" || isPlatformAdmin(session.user) || consoleType === "TENANT_OWNER" || !session.user.organizationId) {
      return;
    }
    let cancelled = false;
    async function recoverBranchContext() {
      try {
        const nextSession = await selectOrganization(session.accessToken, session.user.organizationId);
        if (!cancelled && nextSession.user.branchId) {
          storeSession(nextSession);
          window.location.reload();
        }
      } catch {
        // The setup banner remains visible when the user truly has no branch access.
      }
    }
    void recoverBranchContext();
    return () => {
      cancelled = true;
    };
  }, [consoleType, hasBranch, session, variant]);

  function logout() {
    clearSession();
    router.push("/login");
  }

  async function changeBranch(branchId: string) {
    if (!branchId || branchId === session.user.branchId || switchingBranch) {
      return;
    }
    setSwitchingBranch(true);
    try {
      const selectedSession = await selectBranch(session.accessToken, branchId);
      storeSession(selectedSession);
      window.location.reload();
    } finally {
      setSwitchingBranch(false);
    }
  }

  return (
    <main className={`admin-console ${hideSidebar ? "no-sidebar" : ""} ${isSupportMode(session.user) ? "support-mode" : ""}`}>
      {!hideSidebar ? (
      <aside className="admin-sidebar">
        <div className="admin-sidebar-brand">
          <span>G</span>
          <div>
            <strong>{variant === "platform" ? "GymOS Plataforma" : organizationName ?? session.user.brandName ?? session.user.organizationName ?? "GymOS"}</strong>
            <small>{variant === "platform" ? "CRM de gimnasios" : visibleBranch}</small>
          </div>
        </div>

        <section className="admin-user-card" aria-label="Perfil activo">
          <p>Entraste como</p>
          <strong>{role.label}</strong>
          <span>{role.description}</span>
        </section>

        {isPlatformAdmin(session.user) && !pathname.startsWith("/platform") ? (
          <Link className="admin-platform-return" href="/platform/gymnasiums">
            Volver a administracion de gimnasios
          </Link>
        ) : null}

        <nav aria-label="Menu principal">
          {sections.map((section) => (
            <details className="admin-nav-section" key={section.title} open={sections.length === 1 || section.links.some((item) => isActive(pathname, item.href))}>
              <summary>{section.title}</summary>
              {section.links.map((item) => (
                <Link
                  aria-current={isActive(pathname, item.href) ? "page" : undefined}
                  className={isActive(pathname, item.href) ? "active" : undefined}
                  href={item.href}
                  key={item.href}
                >
                  <strong>{item.label}</strong>
                  <span>{item.hint}</span>
                </Link>
              ))}
            </details>
          ))}
        </nav>

        <div className="admin-sidebar-footer">
          <span>{session.user.fullName}</span>
          <small>{session.user.username}</small>
          <button onClick={logout} type="button">
            Salir
          </button>
        </div>
      </aside>
      ) : null}

      <section className="admin-workspace">
        {isSupportMode(session.user) ? (
          <section className="support-banner" role="status">
            <strong>Modo soporte GymOS</strong>
            <span>Estas operando dentro de este gimnasio para apoyar al cliente. Las acciones quedan auditadas.</span>
          </section>
        ) : null}

        <header className="admin-command-bar">
          <div>
            <p>{eyebrow}</p>
            <h1>{title}</h1>
            {subtitle ? <span>{subtitle}</span> : null}
          </div>
          <div className="admin-command-actions">
            <div className="admin-role-chip">
              <span>Perfil</span>
              <strong>{role.label}</strong>
            </div>
            {showStaticBranchChip ? (
              <div className="admin-role-chip">
                <span>Sucursal</span>
                <strong>{visibleBranch}</strong>
              </div>
            ) : null}
            {showBranchSwitcher ? (
              <label className="admin-branch-switcher">
                <span>Operar en</span>
                <select disabled={switchingBranch} onChange={(event) => changeBranch(event.target.value)} value={session.user.branchId ?? ""}>
                  <option value="">Sin sucursal</option>
                  {availableBranches.map((branch) => (
                    <option key={branch.branchId} value={branch.branchId}>
                      {branch.name}
                    </option>
                  ))}
                </select>
              </label>
            ) : null}
            {visibleActions.map((action) => (
              <Link className="app-button-secondary" href={action.href} key={action.href}>
                {action.label}
              </Link>
            ))}
          </div>
        </header>

        {!hasBranch && variant !== "platform" ? (
          <section className="admin-setup-banner">
            <div>
              <p>Configuracion pendiente</p>
              <h2>Primero selecciona una sucursal operativa</h2>
              <span>
                Caja, recepcion, membresias, reservas y kiosco necesitan una sucursal activa para operar sin mezclar sedes.
              </span>
            </div>
            <Link href={isPlatformAdmin(session.user) ? "/platform/gymnasiums" : "/settings/organization"}>
              {isPlatformAdmin(session.user) ? "Ir a gimnasios" : "Ir a sucursales"}
            </Link>
          </section>
        ) : null}

        {children}
      </section>
    </main>
  );
}

export function LoadingScreen({ text }: { text: string }) {
  return (
    <main className="admin-empty-state">
      <section>
        <p>GymOS</p>
        <h1>{text}</h1>
      </section>
    </main>
  );
}

export function InlineError({ message }: { message: string }) {
  return (
    <section className="admin-alert" role="alert">
      <strong>No se pudo cargar esta seccion</strong>
      <span>{message}</span>
    </section>
  );
}

function sectionsForConsole(consoleType: UserConsole, variant?: "platform" | "tenant") {
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

function canSeeLink(permissions: Set<string>, link: NavigationLink) {
  if (!link.permissions || link.permissions.length === 0) {
    return true;
  }
  return link.permissions.some((permission) => permissions.has(permission));
}

function canUseWithoutBranch(hasBranch: boolean, link: NavigationLink) {
  return hasBranch || link.requiresBranch === false || link.href === "/users" || link.href === "/settings/organization";
}

function isActive(pathname: string, href: string) {
  return pathname === href || pathname.startsWith(`${href}/`);
}
