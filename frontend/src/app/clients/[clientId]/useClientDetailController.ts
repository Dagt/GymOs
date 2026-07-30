"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import { useParams } from "next/navigation";
import type { FieldErrorMap } from "@/components/FormFields";
import {
  changeMembershipPlan,
  enrollMembership,
  getClient,
  getClientMemberships,
  getMembershipPeriods,
  getMembershipQuote,
  getPlans,
  getStoredSession,
  renewMembership,
  updateClient,
  type ChangeMembershipPlanPayload,
  type ClientDetailResponse,
  type ClientMembershipSummary,
  type ClientUpdatePayload,
  type PlanSummary,
  type MembershipPeriodSummary,
  type MembershipQuote,
  type RenewMembershipPayload,
  type StoredSession,
} from "@/lib/api";
import {
  formatDay,
  normalizeClientUpdateForm,
  toClientUpdateForm,
  validateClientUpdate,
} from "./ClientDetailHelpers";

export type MembershipAction = {
  membership: ClientMembershipSummary;
  type: "renew" | "change";
};

export function useClientDetailController() {
  const params = useParams<{ clientId: string }>();
  const [session, setSession] = useState<StoredSession | null>(null);
  const [data, setData] = useState<ClientDetailResponse | null>(null);
  const [memberships, setMemberships] = useState<ClientMembershipSummary[]>([]);
  const [membershipPeriods, setMembershipPeriods] = useState<Record<string, MembershipPeriodSummary[]>>({});
  const [membershipQuote, setMembershipQuote] = useState<MembershipQuote | null>(null);
  const [plans, setPlans] = useState<PlanSummary[]>([]);
  const [form, setForm] = useState<ClientUpdatePayload | null>(null);
  const [fieldErrors, setFieldErrors] = useState<FieldErrorMap>({});
  const [renewMethod, setRenewMethod] = useState<RenewMembershipPayload["method"]>("CASH");
  const [renewReference, setRenewReference] = useState("");
  const [renewValidateNow, setRenewValidateNow] = useState(true);
  const [targetPlanId, setTargetPlanId] = useState("");
  const [renewingMembershipId, setRenewingMembershipId] = useState<string | null>(null);
  const [changingMembershipId, setChangingMembershipId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [showEditDrawer, setShowEditDrawer] = useState(false);
  const [membershipAction, setMembershipAction] = useState<MembershipAction | null>(null);

  const canEditClient = useMemo(() => hasPermission(session, "client.update"), [session]);
  const canReadMemberships = useMemo(() => hasPermission(session, "membership.read"), [session]);
  const canManageMemberships = useMemo(
    () =>
      hasPermission(session, "membership.update") ||
      hasPermission(session, "membership.create") ||
      hasPermission(session, "sale.create") ||
      hasPermission(session, "client.create"),
    [session],
  );
  const limitedCoachView = useMemo(
    () => Boolean(session) && !canEditClient && !canReadMemberships,
    [session, canEditClient, canReadMemberships],
  );

  useEffect(() => {
    let active = true;

    async function loadInitial() {
      const storedSession = getStoredSession();
      if (!storedSession) {
        setLoading(false);
        return;
      }
      setSession(storedSession);
      setLoading(true);
      setError(null);

      try {
        const response = await getClient(storedSession.accessToken, params.clientId);
        if (!active) return;
        setData(response);
        setForm(toClientUpdateForm(response.client));

        const mayReadMemberships = hasPermission(storedSession, "membership.read");
        const mayManageMemberships = hasPermission(storedSession, "membership.update")
          && hasPermission(storedSession, "sale.create")
          && hasPermission(storedSession, "catalog.read");

        if (mayReadMemberships) {
          try {
            const snapshot = await loadMembershipSnapshot(storedSession.accessToken, params.clientId);
            if (active) {
              setMemberships(snapshot.memberships);
              setMembershipPeriods(snapshot.periodsByMembership);
            }
          } catch (membershipError) {
            if (active && mayManageMemberships) {
              setError(membershipError instanceof Error ? membershipError.message : "No fue posible cargar membresias.");
            }
          }
        }

        if (mayManageMemberships) {
          try {
            const loadedPlans = await getPlans(storedSession.accessToken);
            if (!active) return;
            const activePlans = loadedPlans.filter((plan) => plan.status === "ACTIVE");
            setPlans(activePlans);
            setTargetPlanId((current) => current || activePlans[0]?.id || "");
          } catch (planError) {
            if (active) setError(planError instanceof Error ? planError.message : "No fue posible cargar planes.");
          }
        }
      } catch (exception) {
        if (active) {
          setError(exception instanceof Error ? exception.message : "No fue posible cargar socio.");
          setData(null);
          setForm(null);
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    void loadInitial();

    return () => {
      active = false;
    };
  }, [params.clientId]);

  useEffect(() => {
    if (!session || !membershipAction) return;
    const planId = membershipAction.type === "change"
      ? targetPlanId
      : membershipAction.membership.planId;
    if (!planId) return;
    let active = true;
    getMembershipQuote(session.accessToken, params.clientId, planId)
      .then((quote) => {
        if (active) setMembershipQuote(quote);
      })
      .catch(() => {
        if (active) setMembershipQuote(null);
      });
    return () => {
      active = false;
    };
  }, [membershipAction, params.clientId, session, targetPlanId]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || !form || !canEditClient) return;

    const errors = validateClientUpdate(form);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setSaving(true);
    setError(null);
    try {
      const response = await updateClient(session.accessToken, params.clientId, normalizeClientUpdateForm(form));
      setData(response);
      setForm(toClientUpdateForm(response.client));
      setShowEditDrawer(false);
      setMessage("Datos del socio actualizados.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible actualizar socio.");
    } finally {
      setSaving(false);
    }
  }

  async function renew(activeMembership: ClientMembershipSummary) {
    if (!session || !canManageMemberships) return;
    setRenewingMembershipId(activeMembership.id);
    setError(null);
    setMessage(null);
    try {
      const response = await renewMembership(session.accessToken, activeMembership.id, {
        method: renewMethod,
        reference: renewReference,
        validateNow: renewValidateNow,
        notes: "Renovacion desde ficha de socio",
      });
      const snapshot = await loadMembershipSnapshot(session.accessToken, params.clientId);
      setMemberships(snapshot.memberships);
      setMembershipPeriods(snapshot.periodsByMembership);
      setRenewReference("");
      setMembershipAction(null);
      setMessage(
        response.membership
          ? `Membresia renovada hasta ${formatDay(response.membership.endsOn)} por ${formatMoney(response.totalCents, response.currency)}.`
          : `Venta ${response.saleNumber} creada con pago pendiente.`,
      );
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible renovar membresia.");
    } finally {
      setRenewingMembershipId(null);
    }
  }

  async function changePlan(activeMembership: ClientMembershipSummary) {
    if (!session || !targetPlanId || !canManageMemberships) return;
    setChangingMembershipId(activeMembership.id || "new");
    setError(null);
    setMessage(null);
    try {
      if (!activeMembership.id) {
        const selectedPlan = plans.find((p) => p.id === targetPlanId);
        if (!selectedPlan) return;
        const res = await enrollMembership(session.accessToken, {
          clientId: params.clientId,
          planId: selectedPlan.id,
          method: renewMethod,
          reference: renewReference,
          validateNow: renewValidateNow,
          notes: "Venta de membresía inicial",
        });
        const snapshot = await loadMembershipSnapshot(session.accessToken, params.clientId);
        setMemberships(snapshot.memberships);
        setMembershipPeriods(snapshot.periodsByMembership);
        setMembershipAction(null);
        setMessage(`Membresía inicial asignada por ${formatMoney(res.totalCents, res.currency)}. Venta ${res.saleNumber} creada.`);
        return;
      }

      const payload: ChangeMembershipPlanPayload = {
        targetPlanId,
        method: renewMethod,
        reference: renewReference,
        validateNow: renewValidateNow,
        notes: "Cambio de plan desde ficha de socio",
      };
      const response = await changeMembershipPlan(session.accessToken, activeMembership.id, payload);
      const snapshot = await loadMembershipSnapshot(session.accessToken, params.clientId);
      setMemberships(snapshot.memberships);
      setMembershipPeriods(snapshot.periodsByMembership);
      setRenewReference("");
      setMembershipAction(null);
      setMessage(
        response.membership
          ? `Plan cambiado a ${response.membership.planName} por ${formatMoney(response.totalCents, response.currency)}.`
          : `Venta ${response.saleNumber} creada.`,
      );
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible cambiar plan.");
    } finally {
      setChangingMembershipId(null);
    }
  }

  function updateField(field: keyof ClientUpdatePayload, value: string) {
    if (!canEditClient) return;
    setForm((current) => (current ? { ...current, [field]: value } : current));
  }

  function openMembershipAction(activeMembership: ClientMembershipSummary, type: "renew" | "change") {
    if (!canManageMemberships) return;
    if (type === "change") {
      setTargetPlanId((current) => current && current !== activeMembership.planId ? current : plans.find((plan) => plan.id !== activeMembership.planId)?.id || "");
    }
    setMembershipQuote(null);
    setMembershipAction({ membership: activeMembership, type });
  }

  async function submitMembershipAction(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!membershipAction || !canManageMemberships) return;
    if (membershipAction.type === "change") {
      await changePlan(membershipAction.membership);
      return;
    }
    await renew(membershipAction.membership);
  }

  return {
    canEditClient,
    canManageMemberships,
    canReadMemberships,
    changingMembershipId,
    data,
    error,
    fieldErrors,
    form,
    limitedCoachView,
    loading,
    membershipAction,
    memberships,
    membershipPeriods,
    membershipQuote: visibleMembershipQuote(membershipQuote, membershipAction, targetPlanId),
    message,
    plans,
    renewMethod,
    renewReference,
    renewingMembershipId,
    renewValidateNow,
    saving,
    session,
    setMembershipAction,
    setRenewMethod,
    setRenewReference,
    setRenewValidateNow,
    setShowEditDrawer,
    setTargetPlanId,
    showEditDrawer,
    submit,
    submitMembershipAction,
    targetPlanId,
    updateField,
    openMembershipAction,
  };
}

function visibleMembershipQuote(
  quote: MembershipQuote | null,
  action: MembershipAction | null,
  targetPlanId: string,
) {
  if (!quote || !action) return null;
  const expectedPlanId = action.type === "change" ? targetPlanId : action.membership.planId;
  return quote.planId === expectedPlanId ? quote : null;
}

function formatMoney(cents: number, currency: string) {
  return new Intl.NumberFormat("es-MX", {
    style: "currency",
    currency,
  }).format(cents / 100);
}

async function loadMembershipSnapshot(accessToken: string, clientId: string) {
  const memberships = await getClientMemberships(accessToken, clientId);
  const periodEntries = await Promise.all(
    memberships.map(async (membership) => {
      try {
        const periods = await getMembershipPeriods(accessToken, membership.id);
        return [membership.id, periods] as const;
      } catch {
        return [membership.id, []] as const;
      }
    }),
  );
  return {
    memberships,
    periodsByMembership: Object.fromEntries(periodEntries) as Record<string, MembershipPeriodSummary[]>,
  };
}

function hasPermission(session: StoredSession | null, permission: string) {
  return Boolean(
    session?.user.permissions.includes(permission)
      || session?.user.permissions.includes("platform.admin")
      || session?.user.platformAdmin,
  );
}
