import Link from "next/link";

const roleEntrances = [
  {
    title: "Administrar gimnasio",
    detail: "Ventas, socios, membresías, sucursales y reportes en un solo lugar.",
    href: "/reception",
    action: "Entrar al panel",
    tone: "primary",
  },
  {
    title: "Atender en recepción",
    detail: "Registrar socios, cobrar, renovar membresías y consultar pagos pendientes.",
    href: "/pos",
    action: "Abrir caja",
    tone: "warm",
  },
  {
    title: "Pantalla para socios",
    detail: "Autoservicio para pagos, renovaciones y cambios de membresía dentro del gimnasio.",
    href: "/kiosk/KIOSK-MAIN",
    action: "Abrir kiosco",
    tone: "cool",
  },
];

const everydaySignals = [
  "Cobros y saldos visibles",
  "Clientes y membresías organizados",
  "Reportes listos para el dueño",
  "Marca configurable por gimnasio",
];

export default function Home() {
  return (
    <main className="home-screen">
      <section className="home-panel">
        <header className="home-header">
          <div className="home-brand">
            <span>G</span>
            <strong>GymOS</strong>
          </div>
          <nav aria-label="Accesos principales">
            <Link href="/login">Iniciar sesión</Link>
            <Link className="home-primary-link" href="/reception">
              Ir al panel
            </Link>
          </nav>
        </header>

        <section className="home-hero" aria-labelledby="home-title">
          <div>
            <p className="home-kicker">Sistema para operar gimnasios</p>
            <h1 id="home-title">Controla recepción, pagos, membresías y reportes sin complicar al equipo.</h1>
            <p>
              Diseñado para mostrador, administración y autoservicio. Cada usuario entra directo a lo que necesita
              hacer, sin información técnica ni pasos innecesarios.
            </p>
            <div className="home-actions">
              <Link className="home-cta" href="/reception">
                Empezar a operar
              </Link>
              <Link className="home-secondary" href="/reports/sales">
                Ver reportes
              </Link>
            </div>
          </div>

          <aside className="home-preview" aria-label="Resumen de operación">
            <div className="home-preview-top">
              <span>Hoy</span>
              <strong>Mostrador activo</strong>
            </div>
            <div className="home-preview-grid">
              <PreviewMetric label="Cobros" value="$18,450" />
              <PreviewMetric label="Socios activos" value="342" />
              <PreviewMetric label="Por vencer" value="18" />
              <PreviewMetric label="Pagos por revisar" value="7" />
            </div>
          </aside>
        </section>

        <section className="home-roles" aria-label="Entradas por rol">
          {roleEntrances.map((entry) => (
            <article className={`home-role-card ${entry.tone}`} key={entry.title}>
              <h2>{entry.title}</h2>
              <p>{entry.detail}</p>
              <Link href={entry.href}>{entry.action}</Link>
            </article>
          ))}
        </section>

        <section className="home-signals" aria-label="Beneficios operativos">
          {everydaySignals.map((signal) => (
            <span key={signal}>{signal}</span>
          ))}
        </section>
      </section>
    </main>
  );
}

function PreviewMetric({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}
