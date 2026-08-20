"use client";

import { type FormEvent, useState } from "react";
import {
  createMembershipPlan,
  updateMembershipPlan,
  type PlanSummary,
  type StoredSession,
} from "@/lib/api";
import {
  emptyPlan,
  planDraftToPayload,
  planToDraft,
  type PlanDraft,
} from "@/app/catalog/CatalogHelpers";

export function useCatalogPlanEditor(
  session: StoredSession | null,
  reload: () => Promise<void>,
) {
  const [form, setForm] = useState<PlanDraft>(emptyPlan);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  function create() {
    setEditingId(null);
    setForm(emptyPlan);
    setOpen(true);
  }

  function edit(plan: PlanSummary) {
    setEditingId(plan.id);
    setForm(planToDraft(plan));
    setOpen(true);
  }

  function close() {
    setForm(emptyPlan);
    setEditingId(null);
    setOpen(false);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) return;
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const payload = planDraftToPayload(form);
      if (editingId) await updateMembershipPlan(session.accessToken, editingId, payload);
      else await createMembershipPlan(session.accessToken, payload);
      await reload();
      const wasEditing = Boolean(editingId);
      close();
      setMessage(wasEditing
        ? "Plan de membresía actualizado."
        : "Plan disponible en esta sucursal. Si el código ya existía en el gimnasio, se reutilizó el mismo plan sin duplicarlo.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible guardar plan.");
    } finally {
      setSaving(false);
    }
  }

  return { close, create, edit, editingId, error, form, message, open, saving, setForm, submit };
}
