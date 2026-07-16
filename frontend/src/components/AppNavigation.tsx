"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const navigationSections = [
  {
    title: "Operación",
    links: [
      { label: "Recepción", href: "/reception", hint: "Mostrador" },
      { label: "Clientes", href: "/clients", hint: "Altas y fichas" },
      { label: "POS", href: "/pos", hint: "Ventas y pagos" },
      { label: "Kiosco", href: "/kiosk/KIOSK-MAIN", hint: "Autoservicio" },
    ],
  },
  {
    title: "Gestión",
    links: [
      { label: "Catálogo", href: "/catalog", hint: "Planes y productos" },
      { label: "Accesos", href: "/access", hint: "Pases y visitas" },
      { label: "Reservas", href: "/bookings", hint: "Agenda" },
      { label: "Inventario", href: "/inventory", hint: "Stock" },
    ],
  },
  {
    title: "Dirección",
    links: [
      { label: "Dashboard", href: "/dashboard", hint: "Indicadores" },
      { label: "Ventas", href: "/reports/sales", hint: "Reportes" },
      { label: "Automatizaciones", href: "/automations", hint: "Reglas" },
      { label: "Configuración", href: "/settings/organization", hint: "Marca y sucursales" },
    ],
  },
];

export function AppNavigation() {
  const pathname = usePathname();

  return (
    <nav className="app-nav" aria-label="Navegación principal">
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
                <strong>{link.label}</strong>
                <span>{link.hint}</span>
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
