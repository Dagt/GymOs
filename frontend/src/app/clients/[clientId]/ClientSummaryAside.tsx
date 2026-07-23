import type { ClientDetailResponse } from "@/lib/api";
import { formatDate } from "./ClientDetailHelpers";
import { Metric } from "./ClientDetailMetric";

export function ClientSummaryAside({ data }: { data: ClientDetailResponse }) {
  return (
    <aside className="grid gap-6 content-start">
      <section className="app-card p-5">
        <p className="app-eyebrow">Resumen</p>
        <h2 className="mt-2 text-xl font-semibold">Contacto y estado</h2>
        <dl className="mt-4 grid gap-3 text-sm">
          <Metric label="Telefono" value={data.client.phone ?? "Sin telefono"} />
          <Metric label="Email" value={data.client.email ?? "Sin email"} />
          <Metric label="Actualizado" value={formatDate(data.client.updatedAt)} />
        </dl>
      </section>

      <section className="app-card p-5">
        <p className="app-eyebrow">Actividad</p>
        <h2 className="mt-2 text-xl font-semibold">Timeline</h2>
        <div className="mt-4 grid gap-3">
          {data.timeline.length === 0 ? <p className="text-sm text-[#5d6b63]">Sin eventos registrados.</p> : null}
          {data.timeline.map((entry) => (
            <article className="border-l-2 border-[var(--brand-accent)] pl-3" key={`${entry.action}-${entry.occurredAt}`}>
              <p className="text-sm font-semibold">{entry.action}</p>
              <p className="mt-1 text-xs text-[#6f7a73]">
                {formatDate(entry.occurredAt)} - {entry.origin ?? "Sistema"}
              </p>
            </article>
          ))}
        </div>
      </section>
    </aside>
  );
}
