import Link from "next/link";
import type { ClientMembershipSummary, MembershipPeriodSummary, PlanSummary } from "@/lib/api";
import {
  formatDay,
  membershipStatusLabel,
} from "./ClientDetailHelpers";
import { canChangeMembershipPlan, canRenewMembership } from "./client-membership-helpers";

export function MembershipHistory({
  changingMembershipId,
  memberships,
  onMembershipAction,
  periodsByMembership,
  plans,
  renewingMembershipId,
}: {
  changingMembershipId?: string | null;
  memberships: ClientMembershipSummary[];
  onMembershipAction: (membership: ClientMembershipSummary, type: "renew" | "change") => void;
  periodsByMembership: Record<string, MembershipPeriodSummary[]>;
  plans: PlanSummary[];
  renewingMembershipId: string | null;
}) {
  return (
    <section className="app-card p-5">
      <div className="admin-panel-header">
        <div>
          <p>Membresias</p>
          <h2>Plan actual e historial</h2>
        </div>
      </div>
      {memberships.length === 0 ? (
        <section className="admin-empty-card flex flex-col items-start gap-2">
          <strong>Sin membresia registrada</strong>
          <span>Selecciona un plan del catálogo para activar o contratar la membresía inicial de este socio.</span>
          <button
            type="button"
            className="app-button mt-2"
            onClick={() =>
              onMembershipAction(
                {
                  id: "",
                  clientId: "",
                  planId: plans[0]?.id || "",
                  planName: plans[0]?.name || "Plan Inicial",
                  status: "EXPIRED",
                  activatedAt: "",
                  startsOn: "",
                  endsOn: "",
                  graceEndsOn: "",
                  saleId: "",
                  saleNumber: "",
                },
                "change",
              )
            }
            disabled={plans.length === 0}
          >
            + Asignar Membresía Inicial
          </button>
        </section>
      ) : (
        <section className="grid gap-4">
          {memberships.map((membership) => (
            <MembershipCard
              changingMembershipId={changingMembershipId ?? null}
              key={membership.id}
              membership={membership}
              onMembershipAction={onMembershipAction}
              periods={periodsByMembership[membership.id] ?? []}
              renewingMembershipId={renewingMembershipId}
            />
          ))}
        </section>
      )}
    </section>
  );
}

function MembershipCard({
  changingMembershipId,
  membership,
  onMembershipAction,
  periods,
  renewingMembershipId,
}: {
  changingMembershipId: string | null;
  membership: ClientMembershipSummary;
  onMembershipAction: (membership: ClientMembershipSummary, type: "renew" | "change") => void;
  periods: MembershipPeriodSummary[];
  renewingMembershipId: string | null;
}) {
  const allowRenew = canRenewMembership(membership.status);
  const allowPlanChange = canChangeMembershipPlan(membership.status);

  return (
    <article className="rounded-md border border-[#d8ddd2] px-4 py-3">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <p className="font-semibold">{membership.planName}</p>
          <p className="mt-1 text-sm text-[#5d6b63]">
            {membershipStatusLabel(membership.status)} - {formatDay(membership.startsOn)} a {formatDay(membership.endsOn)}
          </p>
          <p className="mt-1 text-xs text-[#6f7a73]">Gracia hasta {formatDay(membership.graceEndsOn)}</p>
        </div>
        <Link className="text-sm font-semibold text-[var(--brand-primary)]" href={`/sales/${membership.saleId}`}>
          {membership.saleNumber}
        </Link>
      </div>
      <div className="mt-3 flex flex-wrap gap-2">
        <button
          className="app-button"
          disabled={renewingMembershipId !== null || !allowRenew}
          onClick={() => onMembershipAction(membership, "renew")}
          type="button"
        >
          {renewingMembershipId === membership.id ? "Renovando..." : "Renovar"}
        </button>
        <button
          className="app-button-secondary"
          disabled={changingMembershipId !== null || !allowPlanChange}
          onClick={() => onMembershipAction(membership, "change")}
          type="button"
        >
          {changingMembershipId === membership.id ? "Cambiando..." : "Cambiar plan"}
        </button>
      </div>
      {periods.length > 0 ? (
        <div className="mt-3 grid gap-2 border-t border-[#d8ddd2] pt-3">
          <p className="text-xs font-semibold uppercase tracking-wide text-[#6f7a73]">Movimientos de vigencia</p>
          {periods.map((period) => (
            <div className="flex flex-col gap-1 text-sm sm:flex-row sm:items-center sm:justify-between" key={period.id}>
              <span className="text-[#5d6b63]">
                {membershipPeriodLabel(period.operationType)} · {period.planName} · {formatDay(period.startsOn)} a {formatDay(period.endsOn)}
              </span>
              <Link className="font-semibold text-[var(--brand-primary)]" href={`/sales/${period.saleId}`}>
                {period.saleNumber}
              </Link>
            </div>
          ))}
        </div>
      ) : null}
    </article>
  );
}

function membershipPeriodLabel(operationType: MembershipPeriodSummary["operationType"]) {
  switch (operationType) {
    case "ENROLLMENT":
      return "Contratación";
    case "RENEWAL":
      return "Renovación";
    case "PLAN_CHANGE":
      return "Cambio de plan";
    default:
      return "Periodo anterior";
  }
}
