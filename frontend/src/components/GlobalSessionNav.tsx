"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { clearSession, getStoredSession, type StoredSession } from "@/lib/api";

const internalLinks = [
  { label: "Recepción", href: "/reception" },
  { label: "Caja / POS", href: "/pos" },
  { label: "Socios", href: "/clients" },
  { label: "Catálogo", href: "/catalog" },
  { label: "Accesos", href: "/access" },
  { label: "Reservas", href: "/bookings" },
  { label: "Inventario", href: "/inventory" },
  { label: "Reportes", href: "/reports/sales" },
  { label: "Panel ejecutivo", href: "/dashboard" },
  { label: "Configuración", href: "/settings/organization" },
];

const shellRoutes = ["/reception", "/pos", "/clients", "/catalog", "/dashboard", "/reports/sales"];

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

  return (
    <aside className="global-session-nav" aria-label="Menú global">
      <div className="global-session-brand">
        <span>G</span>
        <div>
          <strong>GymOS</strong>
          <small>{resolveRole(session)}</small>
        </div>
      </div>

      <nav>
        {internalLinks.map((item) => (
          <Link className={isActive(pathname, item.href) ? "active" : undefined} href={item.href} key={item.href}>
            {item.label}
          </Link>
        ))}
      </nav>

      <footer>
        <span>{session.user.fullName}</span>
        <button onClick={logout} type="button">Salir</button>
      </footer>
    </aside>
  );
}

function resolveRole(session: StoredSession) {
  const permissions = new Set(session.user.permissions);
  if (session.user.username.toLowerCase().includes("admin") || permissions.has("organization.update")) {
    return "Administrador";
  }
  if (permissions.has("report.sales.read") || permissions.has("catalog.manage")) {
    return "Dueño / Gerencia";
  }
  if (permissions.has("sale.create") || permissions.has("client.create")) {
    return "Recepción / Caja";
  }
  return "Usuario operativo";
}

function isActive(pathname: string, href: string) {
  if (href === "/reception") {
    return pathname === href;
  }
  return pathname === href || pathname.startsWith(`${href}/`);
}
