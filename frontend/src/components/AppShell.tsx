"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import type { ReactNode } from "react";
import { clearSession, type StoredSession } from "@/lib/api";

type ShellAction = {
  href: string;
  label: string;
};

const navigationSections = [
  {
    title: "Recepción",
    links: [
      { label: "Inicio operativo", href: "/reception", hint: "Resumen del día" },
      { label: "Caja / POS", href: "/pos", hint: "Ventas y pagos" },
      { label: "Socios", href: "/clients", hint: "Altas y fichas" },
      { label: "Kiosco", href: "/kiosk/KIOSK-MAIN", hint: "Autoservicio" },
    ],
  },
  {
    title: "Administración",
    links: [
      { label: "Catálogo", href: "/catalog", hint: "Planes y productos" },
      { label: "Accesos", href: "/access", hint: "Pases y visitas" },
      { label: "Reservas", href: "/bookings", hint: "Agenda" },
      { label: "Inventario", href: "/inventory", hint: "Stock" },
      { label: "Beneficios", href: "/benefits", hint: "Promos y reglas" },
      { label: "Marca", href: "/settings/organization", hint: "Colores y sucursales" },
    ],
  },
  {
    title: "Gerencia",
    links: [
      { label: "Panel ejecutivo", href: "/dashboard", hint: "Indicadores" },
      { label: "Ventas", href: "/reports/sales", hint: "Reportes" },
      { label: "Automatizaciones", href: "/automations", hint: "Reglas" },
      { label: "Onboarding", href: "/onboarding", hint: "Preparación" },
      { label: "Importar clientes", href: "/clients/import", hint: "Carga inicial" },
      { label: "Servicios", href: "/wellness", hint: "Bienestar" },
    ],
  },
];

const setupSections = [
  {
    title: "Configuración inicial",
    links: [
      { label: "Gimnasios", href: "/admin", hint: "Elegir o preparar" },
      { label: "Datos y sucursales", href: "/settings/organization", hint: "Base operativa" },
    ],
  },
];

export function AppShell({
  actions = [],
  branchName,
  children,
  eyebrow,
  organizationName,
  mode,
  session,
  subtitle,
  title,
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
}) {
  const pathname = usePathname();
  const router = useRouter();
  const hasBranch = mode === "operation" ? true : mode === "setup" ? false : Boolean(branchName || session.user.branchId);
  const role = resolveUserRole(session.user.permissions, session.user.username, hasBranch);
  const visibleBranch = branchName ?? (session.user.branchId ? "Sucursal seleccionada" : "Sin sucursal");
  const sections = hasBranch ? navigationSections : setupSections;

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

        <nav aria-label="Menú principal">
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
            {actions.map((action) => (
              hasBranch || action.href === "/admin" || action.href === "/onboarding" || action.href === "/settings/organization" ? (
                <Link href={action.href} key={action.href}>
                  {action.label}
                </Link>
              ) : null
            ))}
          </div>
        </header>

        {!hasBranch ? (
          <section className="admin-setup-banner">
            <div>
              <p>Configuración pendiente</p>
              <h2>Primero elige o prepara una sucursal operativa</h2>
              <span>
                Mientras no exista una sucursal activa en tu sesión, GymOS mantiene caja, socios, reportes operativos
                y kiosco fuera del primer plano. El dueño o gerente del gimnasio puede completar la operación diaria
                después.
              </span>
            </div>
            <Link href="/admin">Ir a gimnasios</Link>
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
      <strong>No se pudo cargar esta sección</strong>
      <span>{message}</span>
    </section>
  );
}

function resolveUserRole(permissions: string[], username: string, hasBranch: boolean) {
  const values = new Set(permissions);
  const normalizedUsername = username.toLowerCase();

  if (
    normalizedUsername.includes("admin") ||
    values.has("organization.update") ||
    values.has("branch.update") ||
    values.has("selfservice.terminal.manage") ||
    values.has("audit.read")
  ) {
    if (!hasBranch) {
      return {
        label: "Administrador GymOS",
        description: "Administra altas de gimnasios y configuración base. La operación diaria queda para cada gimnasio.",
      };
    }

    return {
      label: "Administrador",
      description: "Supervisa configuración, permisos y operación del gimnasio seleccionado.",
    };
  }

  if (values.has("report.sales.read") || values.has("sale.refund") || values.has("catalog.manage")) {
    return {
      label: "Dueño / Gerencia",
      description: "Consulta reportes, ventas, decisiones y supervisión del negocio.",
    };
  }

  if (values.has("sale.create") || values.has("payment.manual.create") || values.has("client.create")) {
    return {
      label: "Recepción / Caja",
      description: "Atiende socios, registra ventas, cobra pagos y opera el mostrador.",
    };
  }

  return {
    label: "Usuario operativo",
    description: "Acceso limitado según los permisos asignados a esta cuenta.",
  };
}

function isActive(pathname: string, href: string) {
  if (href === "/reception") {
    return pathname === href;
  }
  return pathname === href || pathname.startsWith(`${href}/`);
}
