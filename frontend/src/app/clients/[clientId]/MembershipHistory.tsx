import Link from "next/link";
import type {
  ClientMembershipSummary,
  PlanSummary,
} from "@/lib/api";
import {
  formatDay,
  membershipStatusLabel,
} from "./ClientDetailHelpers";
import {
  canChangeMembershipPlan,
  canRenewMembership,
  currentMembership,
} from "./client-membership-helpers";

export function MembershipHistory({
  canChange,
  canEnroll,
  canRenew,
  changingMembershipId,
  memberships,
  onMembershipAction,
  plans,
  renewingMembershipId,
}: {
  canChange: boolean;
  canEnroll: boolean;
  canRenew: boolean;
  changingMembershipId?: string | null;
  memberships: ClientMembershipSummary[];
  onMembershipAction: (
    membership: ClientMembershipSummary,
    type: "renew" | "change",
  ) => void;
  plans: PlanSummary[];
  renewingMembershipId: string | null;
}) {
  const membership = currentMembership(memberships);

  return (
    <section className="app-card p-5">
      <div className="admin-panel-header">
        <div>
          <p>Membresía</p>
          <h2>Plan actual</h2>
        </div>
      </div>
      {!membership ? (
        <section className="admin-empty-card flex flex-col items-start gap-2">
          <strong>Sin membresía registrada</strong>
          <span>
            Selecciona un plan del catálogo para activar o contratar la membresía inicial de este socio.
          </span>
          {canEnroll ? (
            <button
              className="app-button mt-2"
              disabled={plans.length === 0}
              onClick={() =>
                onMembershipAction(
                  {
                    id: "",
                    clientId: "",
                    planId: plans[0]?.id || "",
                    planName: plans[0]?.name || "Plan inicial",
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
              type="button"
            >
              Asignar membresía inicial
            </button>
          ) : null}
        </section>
      ) : (
        <MembershipCard
          canChange={canChange}
          canRenew={canRenew}
          changingMembershipId={changingMembershipId ?? null}
          membership={membership}
          onMembershipAction={onMembershipAction}
          renewingMembershipId={renewingMembershipId}
        />
      )}
    </section>
  );
}

function MembershipCard({
  canChange,
  canRenew,
  changingMembershipId,
  membership,
  onMembershipAction,
  renewingMembershipId,
}: {
  canChange: boolean;
  canRenew: boolean;
  changingMembershipId: string | null;
  membership: ClientMembershipSummary;
  onMembershipAction: (
    membership: ClientMembershipSummary,
    type: "renew" | "change",
  ) => void;
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
            {membershipStatusLabel(membership.status)} · {formatDay(membership.startsOn)} a{" "}
            {formatDay(membership.endsOn)}
          </p>
          <p className="mt-1 text-xs text-[#6f7a73]">
            Gracia hasta {formatDay(membership.graceEndsOn)}
          </p>
        </div>
        <Link
          className="text-sm font-semibold text-[var(--brand-primary)]"
          href={`/sales/${membership.saleId}`}
        >
          {membership.saleNumber}
        </Link>
      </div>

      {canRenew || canChange ? (
        <div className="mt-3 flex flex-wrap gap-2">
          {canRenew ? (
            <button
              className="app-button"
              disabled={renewingMembershipId !== null || !allowRenew}
              onClick={() => onMembershipAction(membership, "renew")}
              type="button"
            >
              {renewingMembershipId === membership.id ? "Renovando..." : "Renovar"}
            </button>
          ) : null}
          {canChange ? (
            <button
              className="app-button-secondary"
              disabled={changingMembershipId !== null || !allowPlanChange}
              onClick={() => onMembershipAction(membership, "change")}
              type="button"
            >
              {changingMembershipId === membership.id ? "Cambiando..." : "Cambiar plan"}
            </button>
          ) : null}
        </div>
      ) : null}
      {canChange && !allowPlanChange ? (
        <p className="mt-2 text-xs text-[#6f7a73]">
          El cambio de plan sólo se aplica sobre una membresía activa. Si está vencida, renueva primero
          el plan actual.
        </p>
      ) : null}
    </article>
  );
}
