"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const navigationSections = [
  {
    title: "Operacion",
    links: [
      { label: "Recepcion", href: "/reception" },
      { label: "Clientes", href: "/clients" },
      { label: "POS", href: "/pos" },
      { label: "Kiosco", href: "/kiosk/KIOSK-MAIN" },
    ],
  },
  {
    title: "Gestion",
    links: [
      { label: "Catalogo", href: "/catalog" },
      { label: "Accesos", href: "/access" },
      { label: "Reservas", href: "/bookings" },
      { label: "Inventario", href: "/inventory" },
    ],
  },
  {
    title: "Direccion",
    links: [
      { label: "Dashboard", href: "/dashboard" },
      { label: "Ventas", href: "/reports/sales" },
      { label: "Automatizaciones", href: "/automations" },
      { label: "Configuracion", href: "/settings/organization" },
    ],
  },
];

export function AppNavigation() {
  const pathname = usePathname();

  return (
    <nav className="app-nav" aria-label="Navegacion principal">
      {navigationSections.map((section) => (
        <section className="app-nav-section" key={section.title}>
          <p>{section.title}</p>
          <div>
            {section.links.map((link) => (
              <Link
                aria-current={isActive(pathname, link.href) ? "page" : undefined}
                className={isActive(pathname, link.href) ? "active" : undefined}
                href={link.href}
                key={link.href}
              >
                {link.label}
              </Link>
            ))}
          </div>
        </section>
      ))}
    </nav>
  );
}

function isActive(pathname: string, href: string) {
  if (href === "/reception") {
    return pathname === href;
  }
  return pathname === href || pathname.startsWith(`${href}/`);
}
