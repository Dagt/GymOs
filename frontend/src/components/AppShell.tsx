"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import type { ReactNode } from "react";
import { clearSession, type StoredSession } from "@/lib/api";
import { getConsoleProfile, isPlatformAdmin, resolveUserConsole, type UserConsole } from "@/lib/role-model";

type ShellAction = {
  href: string;
  label: string;
};

type NavigationLink = {
  label: string;
  href: string;
  hint: string;
  permissions?: string[];
};

type NavigationSection = {
  title: string;
  links: NavigationLink[];
};

const navigationSections: NavigationSection[] = [
  {
    title: "Recepcion",
    links: [
      { label: "Inicio operativo", href: "/reception", hint: "Resumen del dia", permissions: ["sale.create", "client.create", "report.sales.read"] },
      { label: "Caja / POS", href: "/pos", hint: "Ventas y pagos", permissions: ["sale.create"] },
      { label: "Socios", href: "/clients", hint: "Altas y fichas", permissions: ["client.read"] },
      { label: "Kiosco", href: "/kiosk/KIOSK-MAIN", hint: "Autoservicio", permissions: ["selfservice.terminal.manage"] },
    ],
  },
  {
    title: "Administracion",
    links: [
      { label: "Catalogo", href: "/catalog", hint: "Planes y productos", permissions: ["catalog.read"] },
      { label: "Accesos", href: "/access", hint: "Pases y visitas", permissions: ["access.read"] },
      { label: "Reservas", href: "/bookings", hint: "Agenda", permissions: ["booking.read"] },
      { label: "Inventario", href: "/inventory", hint: "Stock", permissions: ["inventory.read"] },
      { label: "Beneficios", href: "/benefits", hint: "Promos y reglas", permissions: ["benefit.read"] },
      { label: "Equipo", href: "/users", hint: "Usuarios y roles", permissions: ["user.read"] },
      { label: "Datos y sucursales", href: "/settings/organization", hint: "Marca y sedes", permissions: ["organization.update", "branch.update"] },
    ],
  },
  {
    title: "Gerencia",
    links: [
      { label: "Panel ejecutivo", href: "/dashboard", hint: "Indicadores", permissions: ["report.owner.read", "report.sales.read"] },
      { label: "Ventas", href: "/reports/sales", hint: "Reportes", permissions: ["report.sales.read"] },
      { label: "Automatizaciones", href: "/automations", hint: "Reglas", permissions: ["automation.read"] },
      { label: "Importar clientes", href: "/clients/import", hint: "Carga inicial", permissions: ["client.import"] },
      { label: "Servicios", href: "/wellness", hint: "Bienestar", permissions: ["professional.read"] },
    ],
  },
];

const platformSections: NavigationSection[] = [
  {
    title: "Plataforma",
    links: [
      { label: "Gimnasios", href: "/platform/gymnasiums", hint: "Tenants y sucursales", permissions: ["platform.admin"] },
    ],
  },
  {
    title: "Gimnasio activo",
    links: [
      { label: "Panel ejecutivo", href: "/dashboard", hint: "Indicadores", permissions: ["report.owner.read", "report.sales.read"] },
      { label: "Equipo", href: "/users", hint: "Usuarios y roles", permissions: ["user.read"] },
      { label: "Datos y sucursales", href: "/settings/organization", hint: "Marca y sedes", permissions: ["organization.update", "branch.update"] },
    ],
  },
];

const receptionSections: NavigationSection[] = [
  {
    title: "Recepcion",
    links: [
      { label: "Inicio operativo", href: "/reception", hint: "Resumen del dia", permissions: ["sale.create", "client.create", "report.sales.read"] },
      { label: "Caja / POS", href: "/pos", hint: "Ventas y pagos", permissions: ["sale.create"] },
      { label: "Socios", href: "/clients", hint: "Altas y fichas", permissions: ["client.read"] },
      { label: "Ventas", href: "/reports/sales", hint: "Pagos y saldos", permissions: ["report.sales.read"] },
    ],
  },
];

const coachSections: NavigationSection[] = [
  {
    title: "Coach",
    links: [
      { label: "Rutinas", href: "/coaches", hint: "Entrenamiento", permissions: ["coach.read", "workout.read"] },
      { label: "Socios", href: "/clients", hint: "Consulta", permissions: ["client.read"] },
    ],
  },
];

const setupSections: NavigationSection[] = [
  {
    title: "Configuracion inicial",
    links: [
      { label: "Gimnasios", href: "/platform/gymnasiums", hint: "Elegir o preparar" },
      { label: "Datos y sucursales", href: "/settings/organization", hint: "Base operativa", permissions: ["organization.update", "branch.update"] },
      { label: "Equipo y accesos", href: "/users", hint: "Usuarios por rol", permissions: ["user.read"] },
    ],
  },
];

export function AppShell({
  branchName,
  children,
  eyebrow,
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
  mode?: "setup" | "operation";
  organizationName?: string | null;
  session: StoredSession;
  subtitle?: string;
  title: string;
  variant?: "platform" | "tenant";
}) {
  const pathname = usePathname();
  const router = useRouter();
  const consoleType = resolveUserConsole(session.user);
  const hasBranch = mode === "operation" ? true : mode === "setup" ? false : Boolean(branchName || session.user.branchId);
  const role = getConsoleProfile(session.user);
  const visibleBranch = branchName ?? (session.user.branchId ? "Sucursal seleccionada" : "Sin sucursal");
  const permissionSet = new Set(session.user.permissions);
  const sections = sectionsForConsole(consoleType, hasBranch, variant)
    .map((section) => ({
      ...section,
      links: section.links.filter((link) => canSeeLink(permissionSet, link)),
    }))
    .filter((section) => section.links.length > 0);

  function logout() {
    clearSession();
    router.push("/login");
  }

  return (
    <main className="admin-console">
      <aside className="admin-sidebar">
        <div className="admin-sidebar-brand">
          <span>G</span>
          <div>
            <strong>{organizationName ?? "GymOS"}</strong>
            <small>{visibleBranch}</small>
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
            <section className="admin-nav-section" key={section.title}>
              <p>{section.title}</p>
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
            </section>
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

      <section className="admin-workspace">
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
            <div className="admin-role-chip">
              <span>Sucursal</span>
              <strong>{visibleBranch}</strong>
            </div>
          </div>
        </header>

        {!hasBranch && !pathname.startsWith("/platform") ? (
          <section className="admin-setup-banner">
            <div>
              <p>Configuracion pendiente</p>
              <h2>Primero elige o prepara una sucursal operativa</h2>
              <span>
                Mientras no exista una sucursal activa en tu sesion, GymOS mantiene caja, socios, reportes operativos
                y kiosco fuera del primer plano. El dueno o gerente del gimnasio puede completar la operacion diaria
                despues.
              </span>
            </div>
            <Link href="/platform/gymnasiums">Ir a gimnasios</Link>
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

function canSeeLink(permissions: Set<string>, link: NavigationLink) {
  if (!link.permissions || link.permissions.length === 0) {
    return true;
  }
  return link.permissions.some((permission) => permissions.has(permission));
}

function sectionsForConsole(consoleType: UserConsole, hasBranch: boolean, variant?: "platform" | "tenant") {
  if (consoleType === "PLATFORM_ADMIN") {
    return variant === "platform" ? platformSections.slice(0, 1) : platformSections;
  }
  if (!hasBranch) {
    return setupSections;
  }
  if (consoleType === "RECEPTION") {
    return receptionSections;
  }
  if (consoleType === "COACH") {
    return coachSections;
  }
  return navigationSections.filter((section) => section.title !== "Recepcion");
}

function isActive(pathname: string, href: string) {
  if (href === "/reception") {
    return pathname === href;
  }
  return pathname === href || pathname.startsWith(`${href}/`);
}
