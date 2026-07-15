const operationalCards = [
  {
    title: "Recepcion",
    value: "Lista para operar",
    detail: "Alta de clientes, pagos, renovaciones y reservas.",
  },
  {
    title: "Administracion",
    value: "Base modular",
    detail: "Organizaciones, sucursales, permisos y auditoria.",
  },
  {
    title: "Kiosco",
    value: "Autoservicio previsto",
    detail: "Sesiones temporales para pagos y membresias.",
  },
];

const foundationItems = [
  "Spring Boot + Spring Modulith",
  "Next.js + TypeScript + Tailwind",
  "PostgreSQL, Redis y MinIO",
  "OpenAPI, Flyway y auditoria",
];

export default function Home() {
  return (
    <main className="min-h-screen bg-[#f6f7f2] text-[#111815]">
      <section className="border-b border-[#d8ddd2] bg-white">
        <div className="mx-auto flex max-w-7xl flex-col gap-8 px-6 py-8 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <p className="text-sm font-semibold uppercase tracking-wide text-[#51735f]">
              GymOS
            </p>
            <h1 className="mt-2 max-w-3xl text-4xl font-semibold leading-tight sm:text-5xl">
              Operacion integral para gimnasios, lista para crecer por modulos.
            </h1>
          </div>
          <div className="grid gap-2 text-sm text-[#42514a]">
            {foundationItems.map((item) => (
              <span key={item} className="rounded-md border border-[#d8ddd2] px-3 py-2">
                {item}
              </span>
            ))}
          </div>
        </div>
      </section>

      <section className="mx-auto grid max-w-7xl gap-6 px-6 py-8 lg:grid-cols-[1.5fr_1fr]">
        <div className="grid gap-4 sm:grid-cols-3">
          {operationalCards.map((card) => (
            <article
              key={card.title}
              className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm"
            >
              <p className="text-sm font-medium text-[#51735f]">{card.title}</p>
              <h2 className="mt-3 text-2xl font-semibold">{card.value}</h2>
              <p className="mt-3 text-sm leading-6 text-[#5d6b63]">{card.detail}</p>
            </article>
          ))}
        </div>

        <aside className="rounded-lg border border-[#d8ddd2] bg-[#13251c] p-5 text-white shadow-sm">
          <p className="text-sm font-medium text-[#9cc7ad]">Hito 0</p>
          <h2 className="mt-3 text-2xl font-semibold">Base tecnica vendible</h2>
          <p className="mt-3 text-sm leading-6 text-[#dbe8df]">
            Esta pantalla confirma que el monorepo ya tiene frontend operativo.
            El siguiente corte conectara clientes, ventas y pagos manuales
            contra la API.
          </p>
          <div className="mt-5 flex flex-wrap gap-3">
            <a
              href="/reception"
              className="inline-flex h-11 items-center justify-center rounded-md bg-[#d7f2df] px-4 text-sm font-semibold text-[#13251c] transition hover:bg-white"
            >
              Ir a recepcion
            </a>
            <a
              href="/login"
              className="inline-flex h-11 items-center justify-center rounded-md border border-[#d7f2df] px-4 text-sm font-semibold text-white transition hover:bg-[#243d30]"
            >
              Iniciar sesion
            </a>
          </div>
        </aside>
      </section>
    </main>
  );
}
