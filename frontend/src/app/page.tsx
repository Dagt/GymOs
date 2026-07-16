import Link from "next/link";
import { AppNavigation } from "@/components/AppNavigation";

const workAreas = [
  {
    title: "Recepcion diaria",
    detail: "Clientes, ventas, pagos manuales, renovaciones y cambio de sucursal.",
    href: "/reception",
    action: "Abrir recepcion",
  },
  {
    title: "Reportes y direccion",
    detail: "Dashboard ejecutivo, ventas recientes, saldos y actividad comercial.",
    href: "/dashboard",
    action: "Ver dashboard",
  },
  {
    title: "Configuracion de marca",
    detail: "Datos del gimnasio, sucursales, colores y logo para presentarlo al cliente.",
    href: "/settings/organization",
    action: "Configurar",
  },
];

const quickLinks = [
  { label: "POS", href: "/pos" },
  { label: "Clientes", href: "/clients" },
  { label: "Catalogo", href: "/catalog" },
  { label: "Reporte ventas", href: "/reports/sales" },
  { label: "Kiosco", href: "/kiosk/KIOSK-MAIN" },
  { label: "Onboarding", href: "/onboarding" },
];

export default function Home() {
  return (
    <main className="app-shell text-[#111815]">
      <div className="app-shell-inner">
        <header className="app-topbar">
          <div className="app-title-block">
            <p className="app-eyebrow">GymOS</p>
            <h1 className="app-title">Centro operativo del gimnasio</h1>
            <p className="app-subtitle">
              Un punto de entrada claro para recepcion, ventas, membresias, reportes y configuracion comercial.
            </p>
          </div>
          <div className="app-actions">
            <Link className="app-button-secondary" href="/login">
              Iniciar sesion
            </Link>
            <Link className="app-button" href="/reception">
              Entrar a operacion
            </Link>
          </div>
        </header>

        <AppNavigation />

        <section className="grid gap-5 lg:grid-cols-[1.35fr_0.85fr]">
          <section className="app-hero-panel p-6 sm:p-8">
            <div className="relative z-10 max-w-3xl">
              <p className="text-sm font-bold uppercase text-white/75">Producto vendible</p>
              <h2 className="mt-3 text-3xl font-black leading-tight sm:text-5xl">
                Una interfaz lista para mostrador, administracion y autoservicio.
              </h2>
              <p className="mt-5 max-w-2xl text-sm leading-6 text-white/80 sm:text-base">
                Los flujos principales estan agrupados por tarea cotidiana para que un usuario comun sepa donde
                empezar: cobrar, registrar, renovar, consultar reportes o ajustar la sucursal.
              </p>
              <div className="mt-6 flex flex-wrap gap-3">
                {quickLinks.map((link) => (
                  <Link className="app-button-soft bg-white/95" href={link.href} key={link.href}>
                    {link.label}
                  </Link>
                ))}
              </div>
            </div>
          </section>

          <aside className="app-card p-5">
            <p className="app-eyebrow">Personalizacion</p>
            <h2 className="mt-2 app-card-title">Marca por cliente</h2>
            <p className="mt-3 app-card-copy">
              Usa el boton Marca para ajustar nombre visible, logo y colores. La vista cambia al instante y queda
              guardada localmente para demos y pilotos.
            </p>
            <div className="mt-5 grid gap-3">
              <span className="app-pill">Color principal</span>
              <span className="app-pill">Color de acento</span>
              <span className="app-pill">Logo del gimnasio</span>
            </div>
          </aside>
        </section>

        <section className="grid gap-4 md:grid-cols-3">
          {workAreas.map((area) => (
            <article className="app-card p-5" key={area.title}>
              <h2 className="app-card-title">{area.title}</h2>
              <p className="mt-3 app-card-copy">{area.detail}</p>
              <Link className="mt-5 app-button" href={area.href}>
                {area.action}
              </Link>
            </article>
          ))}
        </section>
      </div>
    </main>
  );
}
