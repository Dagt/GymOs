"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { clearSession, getStoredSession, type StoredSession } from "@/lib/api";

type GlobalLink = {
  label: string;
  href: string;
  permissions?: string[];
};

const operationLinks: GlobalLink[] = [
  { label: "Gimnasios", href: "/admin" },
  { label: "Recepcion", href: "/reception", permissions: ["sale.create", "client.create", "report.sales.read"] },
  { label: "Caja / POS", href: "/pos", permissions: ["sale.create"] },
  { label: "Socios", href: "/clients", permissions: ["client.read"] },
  { label: "Catalogo", href: "/catalog", permissions: ["catalog.read"] },
  { label: "Accesos", href: "/access", permissions: ["access.read"] },
  { label: "Reservas", href: "/bookings", permissions: ["booking.read"] },
  { label: "Inventario", href: "/inventory", permissions: ["inventory.read"] },
  { label: "Reportes", href: "/reports/sales", permissions: ["report.sales.read"] },
  { label: "Panel ejecutivo", href: "/dashboard", permissions: ["report.owner.read", "report.sales.read"] },
  { label: "Equipo", href: "/users", permissions: ["user.read"] },
  { label: "Datos y sucursales", href: "/settings/organization", permissions: ["organization.update", "branch.update"] },
];

const setupLinks: GlobalLink[] = [
  { label: "Gimnasios", href: "/admin" },
  { label: "Datos y sucursales", href: "/settings/organization", permissions: ["organization.update", "branch.update"] },
  { label: "Equipo y accesos", href: "/users", permissions: ["user.read"] },
  { label: "Preparar piloto", href: "/onboarding", permissions: ["onboarding.manage"] },
];

const shellRoutes = ["/admin", "/reception", "/pos", "/clients", "/catalog", "/dashboard", "/reports/sales", "/settings/organization", "/users"];

export function GlobalSessionNav() {
  const pathname = usePathname();
  const router = useRouter();
  const [session, setSession] = useState<StoredSession | null>(null);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    let cancelled = false;
    const stored = getStoredSession();
    const isPublicRoute = pathname === "/" || pathname === "/login" || pathname.startsWith("/kiosk");
    const hasRouteShell = shellRoutes.some((route) => pathname === route || pathname.startsWith(`${route}/`));

    const timer = window.setTimeout(() => {
      if (cancelled) {
        return;
      }
      const hasShell = Boolean(document.querySelector(".admin-console"));
      const shouldShow = Boolean(stored) && !isPublicRoute && !hasRouteShell && !hasShell;
      setSession(stored);
      setVisible(shouldShow);
      document.body.classList.toggle("has-global-session-nav", shouldShow);
    }, 50);

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
      document.body.classList.remove("has-global-session-nav");
    };
  }, [pathname]);

  function logout() {
    clearSession();
    setSession(null);
    setVisible(false);
    document.body.classList.remove("has-global-session-nav");
    router.push("/login");
  }

  if (!visible || !session) {
    return null;
  }

  const permissionSet = new Set(session.user.permissions);
  const links = (session.user.branchId ? operationLinks : setupLinks).filter((item) => canSeeLink(permissionSet, item));

  return (
    <aside className="global-session-nav" aria-label="Menu global">
      <div className="global-session-brand">
        <span>G</span>
        <div>
          <strong>GymOS</strong>
          <small>{resolveRole(session)}</small>
        </div>
      </div>

      <nav>
        {links.map((item) => (
          <Link className={isActive(pathname, item.href) ? "active" : undefined} href={item.href} key={item.href}>
            {item.label}
          </Link>
        ))}
      </nav>

      <footer>
        <span>{session.user.fullName}</span>
        <button onClick={logout} type="button">
          Salir
        </button>
      </footer>
    </aside>
  );
}

function resolveRole(session: StoredSession) {
  const permissions = new Set(session.user.permissions);
  if (session.user.username.toLowerCase().includes("admin") || permissions.has("user.manage") || permissions.has("organization.update")) {
    return session.user.branchId ? "Administrador" : "Administrador GymOS";
  }
  if (permissions.has("report.owner.read") || permissions.has("report.sales.read") || permissions.has("catalog.manage")) {
    return "Dueno / Gerencia";
  }
  if (permissions.has("workout.manage") || permissions.has("coach.read")) {
    return "Coach";
  }
  if (permissions.has("sale.create") || permissions.has("client.create")) {
    return "Recepcion / Caja";
  }
  return "Usuario operativo";
}

function canSeeLink(permissionSet: Set<string>, link: GlobalLink) {
  if (!link.permissions || link.permissions.length === 0) {
    return true;
  }
  return link.permissions.some((permission) => permissionSet.has(permission));
}

function isActive(pathname: string, href: string) {
  if (href === "/reception") {
    return pathname === href;
  }
  return pathname === href || pathname.startsWith(`${href}/`);
}
