import Link from "next/link";
import type {
  ClientDetailResponse,
  MembershipPeriodSummary,
} from "@/lib/api";
import { formatDate, formatDay } from "./ClientDetailHelpers";
import { Metric } from "./ClientDetailMetric";
import { membershipPeriodsForTimeline } from "./client-membership-helpers";

export function ClientSummaryAside({
  data,
  periodsByMembership,
}: {
  data: ClientDetailResponse;
  periodsByMembership: Record<string, MembershipPeriodSummary[]>;
}) {
  const activity = [
    ...data.timeline.map((entry) => ({
      id: `audit:${entry.action}:${entry.occurredAt}`,
      occurredAt: entry.occurredAt,
      title: activityLabel(entry.action),
      detail: entry.reason,
      origin: entry.origin ?? "Sistema",
      saleId: null,
      saleNumber: null,
    })),
    ...membershipPeriodsForTimeline(periodsByMembership).map((period) => ({
      id: `membership-period:${period.id}`,
      occurredAt: period.createdAt,
      title: membershipPeriodLabel(period.operationType),
      detail: `${period.planName} · ${formatDay(period.startsOn)} a ${formatDay(period.endsOn)}`,
      origin: "Membresía",
      saleId: period.saleId,
      saleNumber: period.saleNumber,
    })),
  ].sort((left, right) => right.occurredAt.localeCompare(left.occurredAt));

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
          {activity.length === 0 ? <p className="text-sm text-[#5d6b63]">Sin eventos registrados.</p> : null}
          {activity.map((entry) => (
            <article className="border-l-2 border-[var(--brand-accent)] pl-3" key={entry.id}>
              <div className="flex flex-wrap items-start justify-between gap-2">
                <p className="text-sm font-semibold">{entry.title}</p>
                {entry.saleId && entry.saleNumber ? (
                  <Link
                    className="text-xs font-semibold text-[var(--brand-primary)]"
                    href={`/sales/${entry.saleId}`}
                  >
                    {entry.saleNumber}
                  </Link>
                ) : null}
              </div>
              {entry.detail ? (
                <p className="mt-1 text-xs text-[#5d6b63]">{entry.detail}</p>
              ) : null}
              <p className="mt-1 text-xs text-[#6f7a73]">
                {formatDate(entry.occurredAt)} - {entry.origin}
              </p>
            </article>
          ))}
        </div>
      </section>
    </aside>
  );
}

function membershipPeriodLabel(operationType: MembershipPeriodSummary["operationType"]) {
  switch (operationType) {
    case "ENROLLMENT":
      return "Contratación de membresía";
    case "RENEWAL":
      return "Renovación de membresía";
    case "PLAN_CHANGE":
      return "Cambio de plan";
    default:
      return "Movimiento de membresía";
  }
}

function activityLabel(action: string) {
  const labels: Record<string, string> = {
    CLIENT_REGISTERED: "Socio registrado",
    CLIENT_UPDATED: "Datos del socio actualizados",
    CLIENT_BENEFIT_ASSIGNED: "Descuento asignado",
    CLIENT_BENEFIT_VERIFIED: "Descuento verificado",
  };
  return labels[action] ?? action;
}
