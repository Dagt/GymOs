"use client";

import { FormEvent, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import type { FieldErrorMap } from "@/components/FormFields";
import {
  changeMembershipPlan,
  getClient,
  getClientMemberships,
  getPlans,
  getStoredSession,
  renewMembership,
  updateClient,
  type ChangeMembershipPlanPayload,
  type ClientDetailResponse,
  type ClientMembershipSummary,
  type ClientUpdatePayload,
  type PlanSummary,
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
        const [response, loadedMemberships, loadedPlans] = await Promise.all([
          getClient(storedSession.accessToken, params.clientId),
          getClientMemberships(storedSession.accessToken, params.clientId),
          getPlans(storedSession.accessToken),
        ]);
        if (!active) return;
        const activePlans = loadedPlans.filter((plan) => plan.status === "ACTIVE");
        setData(response);
        setMemberships(loadedMemberships);
        setPlans(activePlans);
        setTargetPlanId((current) => current || activePlans[0]?.id || "");
        setForm(toClientUpdateForm(response.client));
      } catch (exception) {
        if (active) {
          setError(exception instanceof Error ? exception.message : "No fue posible cargar socio.");
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

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || !form) return;

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
    if (!session) return;
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
      setMemberships(await getClientMemberships(session.accessToken, params.clientId));
      setRenewReference("");
      setMembershipAction(null);
      setMessage(
        response.membership
          ? `Membresia renovada hasta ${formatDay(response.membership.endsOn)}.`
          : `Venta ${response.saleNumber} creada con pago pendiente.`,
      );
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible renovar membresia.");
    } finally {
      setRenewingMembershipId(null);
    }
  }

  async function changePlan(activeMembership: ClientMembershipSummary) {
    if (!session || !targetPlanId) return;
    setChangingMembershipId(activeMembership.id);
    setError(null);
    setMessage(null);
    try {
      const payload: ChangeMembershipPlanPayload = {
        targetPlanId,
        method: renewMethod,
        reference: renewReference,
        validateNow: renewValidateNow,
        notes: "Cambio de plan desde ficha de socio",
      };
      const response = await changeMembershipPlan(session.accessToken, activeMembership.id, payload);
      setMemberships(await getClientMemberships(session.accessToken, params.clientId));
      setRenewReference("");
      setMembershipAction(null);
      setMessage(
        response.membership
          ? `Plan cambiado a ${response.membership.planName}.`
          : `Venta ${response.saleNumber} creada.`,
      );
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible cambiar plan.");
    } finally {
      setChangingMembershipId(null);
    }
  }

  function updateField(field: keyof ClientUpdatePayload, value: string) {
    setForm((current) => (current ? { ...current, [field]: value } : current));
  }

  function openMembershipAction(activeMembership: ClientMembershipSummary, type: "renew" | "change") {
    if (type === "change") {
      setTargetPlanId((current) => current && current !== activeMembership.planId ? current : plans.find((plan) => plan.id !== activeMembership.planId)?.id || "");
    }
    setMembershipAction({ membership: activeMembership, type });
  }

  async function submitMembershipAction(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!membershipAction) return;
    if (membershipAction.type === "change") {
      await changePlan(membershipAction.membership);
      return;
    }
    await renew(membershipAction.membership);
  }

  return {
    changingMembershipId,
    data,
    error,
    fieldErrors,
    form,
    loading,
    membershipAction,
    memberships,
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
