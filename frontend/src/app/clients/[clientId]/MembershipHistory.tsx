import Link from "next/link";
import type { ClientMembershipSummary, PlanSummary } from "@/lib/api";
import {
  formatDay,
  membershipStatusLabel,
} from "./ClientDetailHelpers";
import { canChangeMembershipPlan, canRenewMembership } from "./client-membership-helpers";

export function MembershipHistory({
  changingMembershipId,
  memberships,
  onMembershipAction,
  plans,
  renewingMembershipId,
}: {
  changingMembershipId: string | null;
  memberships: ClientMembershipSummary[];
  onMembershipAction: (membership: ClientMembershipSummary, type: "renew" | "change") => void;
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
          <span>Selecciona un plan para asignar o vender la membresia inicial a este socio.</span>
          {plans.length > 0 ? (
            <button
              type="button"
              className="app-button mt-2"
              onClick={() =>
                onMembershipAction(
                  {
                    id: "",
                    planId: plans[0].id,
                    planName: plans[0].name,
                    status: "PENDING",
                    startsOn: "",
                    endsOn: "",
                    graceEndsOn: "",
                    saleId: "",
                    saleNumber: "",
                  } as any,
                  "change",
                )
              }
            >
              + Asignar Membresía Inicial
            </button>
          ) : (
            <Link href="/pos" className="app-button-secondary text-xs">Abrir caja / POS</Link>
          )}
        </section>
      ) : (
        <section className="grid gap-4">
          {memberships.map((membership) => (
            <MembershipCard
              changingMembershipId={changingMembershipId}
              key={membership.id}
              membership={membership}
              onMembershipAction={onMembershipAction}
              plans={plans}
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
  plans,
  renewingMembershipId,
}: {
  changingMembershipId: string | null;
  membership: ClientMembershipSummary;
  onMembershipAction: (membership: ClientMembershipSummary, type: "renew" | "change") => void;
  plans: PlanSummary[];
  renewingMembershipId: string | null;
}) {
  const allowRenew = canRenewMembership(membership.status);
  const allowChange = canChangeMembershipPlan(membership.status, plans.length);

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
          disabled={renewingMembershipId !== null || changingMembershipId !== null || !allowRenew}
          onClick={() => onMembershipAction(membership, "renew")}
          type="button"
        >
          {renewingMembershipId === membership.id ? "Renovando..." : "Renovar"}
        </button>
        <button
          className="app-button-secondary"
          disabled={renewingMembershipId !== null || changingMembershipId !== null || !allowChange}
          onClick={() => onMembershipAction(membership, "change")}
          type="button"
        >
          {changingMembershipId === membership.id ? "Cambiando..." : "Cambiar plan"}
        </button>
      </div>
    </article>
  );
}
