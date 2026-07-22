"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { AppDrawer } from "@/components/AppDrawer";
import { AppShell, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import {
  DateField,
  EmailField,
  FieldErrorMap,
  FormError,
  GenderField,
  PhoneField,
  TextField,
  normalizePhone,
  validateEmail,
  validateGender,
  validateIsoDate,
  validatePhone,
  validateRequired,
} from "@/components/FormFields";
import {
  changeMembershipPlan,
  getClient,
  getClientMemberships,
  getPlans,
  getStoredSession,
  renewMembership,
  updateClient,
  type ChangeMembershipPlanPayload,
  type ClientDetail,
  type ClientDetailResponse,
  type ClientMembershipSummary,
  type ClientUpdatePayload,
  type PlanSummary,
  type RenewMembershipPayload,
  type StoredSession,
} from "@/lib/api";

export default function ClientDetailPage() {
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
  const [membershipAction, setMembershipAction] = useState<{
    membership: ClientMembershipSummary;
    type: "renew" | "change";
  } | null>(null);

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
        if (!active) {
          return;
        }
        const activePlans = loadedPlans.filter((plan) => plan.status === "ACTIVE");
        setData(response);
        setMemberships(loadedMemberships);
        setPlans(activePlans);
        setTargetPlanId((current) => current || activePlans[0]?.id || "");
        setForm(toForm(response.client));
      } catch (exception) {
        if (active) {
          setError(exception instanceof Error ? exception.message : "No fue posible cargar socio.");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void loadInitial();

    return () => {
      active = false;
    };
  }, [params.clientId]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || !form) {
      return;
    }
    const errors = validateClientUpdate(form);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const response = await updateClient(session.accessToken, params.clientId, {
        ...form,
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        maternalLastName: form.maternalLastName?.trim() || null,
        phone: normalizePhone(form.phone ?? ""),
        email: form.email?.trim() || null,
      });
      setData(response);
      setForm(toForm(response.client));
      setShowEditDrawer(false);
      setMessage("Datos del socio actualizados.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible actualizar socio.");
    } finally {
      setSaving(false);
    }
  }

  async function renew(activeMembership: ClientMembershipSummary) {
    if (!session) {
      return;
    }
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
    if (!session || !targetPlanId) {
      return;
    }
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

  if (!session) {
    return <AuthRequired />;
  }

  if (loading || !form || !data) {
    return <LoadingScreen text="Cargando ficha de socio..." />;
  }

  return (
    <AppShell
      eyebrow="Socios"
      session={session}
      subtitle={`Cliente desde ${formatDate(data.client.joinedAt)} - ${statusLabel(data.client.status)}`}
      title={clientFullName(data.client)}
    >
      {error ? <FormError message={error} /> : null}
      {message ? <p className="form-message success">{message}</p> : null}

      <section className="grid gap-6 xl:grid-cols-[1.1fr_0.9fr]">
        <section className="grid gap-6">
          <section className="app-card p-5">
            <div className="admin-panel-header">
              <div>
                <p>Datos personales</p>
                <h2>Ficha del socio</h2>
              </div>
              <button className="app-button" onClick={() => setShowEditDrawer(true)} type="button">
                Editar datos
              </button>
            </div>
            <dl className="mt-5 grid gap-4 sm:grid-cols-2">
              <Metric label="Nombre" value={data.client.firstName} />
              <Metric label="Apellido paterno" value={data.client.lastName} />
              <Metric label="Apellido materno" value={data.client.maternalLastName ?? "Sin registrar"} />
              <Metric label="Telefono" value={data.client.phone ?? "Sin telefono"} />
              <Metric label="Email" value={data.client.email ?? "Sin email"} />
              <Metric label="Nacimiento" value={data.client.birthDate ? formatDay(data.client.birthDate) : "Sin registrar"} />
              <Metric label="Genero" value={data.client.gender ? genderLabel(data.client.gender) : "Sin registrar"} />
              <Metric label="Estado" value={statusLabel(data.client.status)} />
            </dl>
            {data.client.notes ? <p className="mt-5 rounded-md border border-[#d8ddd2] bg-white px-4 py-3 text-sm text-[#5d6b63]">{data.client.notes}</p> : null}
          </section>

          <section className="app-card p-5">
            <div className="admin-panel-header">
              <div>
                <p>Membresias</p>
                <h2>Plan actual e historial</h2>
              </div>
            </div>
            {memberships.length === 0 ? (
              <section className="admin-empty-card">
                <strong>Sin membresia registrada</strong>
                <span>Registra una membresia inicial desde el alta de socio o desde caja seleccionando este socio.</span>
                <Link href="/pos">Abrir caja / POS</Link>
              </section>
            ) : (
              <section className="grid gap-4">
                {memberships.map((membership) => (
                  <article className="rounded-md border border-[#d8ddd2] px-4 py-3" key={membership.id}>
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
                        disabled={renewingMembershipId !== null || changingMembershipId !== null || membership.status !== "ACTIVE"}
                        onClick={() => setMembershipAction({ membership, type: "renew" })}
                        type="button"
                      >
                        {renewingMembershipId === membership.id ? "Renovando..." : "Renovar"}
                      </button>
                      <button
                        className="app-button-secondary"
                        disabled={
                          renewingMembershipId !== null
                          || changingMembershipId !== null
                          || membership.status !== "ACTIVE"
                        }
                        onClick={() => {
                          setTargetPlanId((current) => current && current !== membership.planId ? current : plans.find((plan) => plan.id !== membership.planId)?.id || "");
                          setMembershipAction({ membership, type: "change" });
                        }}
                        type="button"
                      >
                        {changingMembershipId === membership.id ? "Cambiando..." : "Cambiar plan"}
                      </button>
                    </div>
                  </article>
                ))}
              </section>
            )}
          </section>
        </section>

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
      </section>

      <AppDrawer
        description="Actualiza datos personales, contacto, estado y notas internas del socio."
        onClose={() => setShowEditDrawer(false)}
        open={showEditDrawer}
        title="Editar datos del socio"
      >
        <form className="grid gap-4" onSubmit={submit}>
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField error={fieldErrors.firstName} label="Nombre" name="firstName" value={form.firstName ?? ""} onChange={(value) => updateField("firstName", value)} />
            <TextField error={fieldErrors.lastName} label="Apellido paterno" name="lastName" value={form.lastName ?? ""} onChange={(value) => updateField("lastName", value)} />
            <TextField error={fieldErrors.maternalLastName} label="Apellido materno" name="maternalLastName" required={false} value={form.maternalLastName ?? ""} onChange={(value) => updateField("maternalLastName", value)} />
            <PhoneField error={fieldErrors.phone} label="Telefono" name="phone" required value={form.phone ?? ""} onChange={(value) => updateField("phone", value)} />
            <EmailField error={fieldErrors.email} label="Email" name="email" value={form.email ?? ""} onChange={(value) => updateField("email", value)} />
            <DateField error={fieldErrors.birthDate} label="Fecha de nacimiento" name="birthDate" value={form.birthDate ?? ""} onChange={(value) => updateField("birthDate", value)} />
            <GenderField error={fieldErrors.gender} label="Genero" name="gender" value={form.gender ?? ""} onChange={(value) => updateField("gender", value)} />
          </div>
          <label className="form-field" htmlFor="status">
            <span>Estado</span>
            <select className="app-input" id="status" onChange={(event) => updateField("status", event.target.value)} value={form.status}>
              <option value="ACTIVE">Activo</option>
              <option value="INACTIVE">Inactivo</option>
              <option value="SUSPENDED">Suspendido</option>
            </select>
          </label>
          <label className="form-field" htmlFor="notes">
            <span>Notas</span>
            <textarea className="app-input min-h-28 py-2" id="notes" onChange={(event) => updateField("notes", event.target.value)} value={form.notes ?? ""} />
          </label>
          <button className="form-primary-button" disabled={saving} type="submit">
            {saving ? "Guardando..." : "Guardar datos"}
          </button>
        </form>
      </AppDrawer>

      <AppDrawer
        description={
          membershipAction?.type === "change"
            ? "Selecciona el nuevo plan, registra el pago y aplica el cambio de membresia."
            : "Registra el pago de renovacion y define si se valida de inmediato o queda pendiente."
        }
        onClose={() => setMembershipAction(null)}
        open={Boolean(membershipAction)}
        title={membershipAction?.type === "change" ? "Cambiar plan" : "Renovar membresia"}
      >
        {membershipAction ? (
          <form
            className="grid gap-4"
            onSubmit={(event) => {
              event.preventDefault();
              if (membershipAction.type === "change") {
                void changePlan(membershipAction.membership);
              } else {
                void renew(membershipAction.membership);
              }
            }}
          >
            <section className="rounded-md border border-[#d8ddd2] bg-white p-4">
              <p className="app-eyebrow">Membresia actual</p>
              <h3 className="mt-2 text-lg font-semibold">{membershipAction.membership.planName}</h3>
              <p className="mt-1 text-sm text-[#5d6b63]">
                {membershipStatusLabel(membershipAction.membership.status)} - {formatDay(membershipAction.membership.startsOn)} a {formatDay(membershipAction.membership.endsOn)}
              </p>
            </section>
            <MembershipControls
              mode={membershipAction.type}
              plans={plans}
              renewMethod={renewMethod}
              renewReference={renewReference}
              renewValidateNow={renewValidateNow}
              targetPlanId={targetPlanId}
              onMethodChange={setRenewMethod}
              onReferenceChange={setRenewReference}
              onTargetPlanChange={setTargetPlanId}
              onValidateNowChange={setRenewValidateNow}
            />
            <button
              className="form-primary-button"
              disabled={
                renewingMembershipId !== null
                || changingMembershipId !== null
                || (membershipAction.type === "change" && (!targetPlanId || targetPlanId === membershipAction.membership.planId))
              }
              type="submit"
            >
              {membershipAction.type === "change"
                ? changingMembershipId === membershipAction.membership.id ? "Cambiando..." : "Cambiar plan"
                : renewingMembershipId === membershipAction.membership.id ? "Renovando..." : "Renovar membresia"}
            </button>
          </form>
        ) : null}
      </AppDrawer>
    </AppShell>
  );
}

function MembershipControls({
  mode,
  plans,
  renewMethod,
  renewReference,
  renewValidateNow,
  targetPlanId,
  onMethodChange,
  onReferenceChange,
  onTargetPlanChange,
  onValidateNowChange,
}: {
  mode: "renew" | "change";
  plans: PlanSummary[];
  renewMethod: RenewMembershipPayload["method"];
  renewReference: string;
  renewValidateNow: boolean;
  targetPlanId: string;
  onMethodChange: (value: RenewMembershipPayload["method"]) => void;
  onReferenceChange: (value: string) => void;
  onTargetPlanChange: (value: string) => void;
  onValidateNowChange: (value: boolean) => void;
}) {
  return (
    <section className="rounded-md border border-[#d8ddd2] bg-[color-mix(in_srgb,var(--brand-accent)_10%,white)] p-4">
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="form-field" htmlFor="renewMethod">
          <span>Metodo de pago</span>
          <select className="app-input" id="renewMethod" onChange={(event) => onMethodChange(event.target.value as RenewMembershipPayload["method"])} value={renewMethod}>
            <option value="CASH">Efectivo</option>
            <option value="TRANSFER">Transferencia</option>
            <option value="CARD_MANUAL">Tarjeta manual</option>
          </select>
        </label>
        <TextField label="Referencia" name="renewReference" required={false} value={renewReference} onChange={onReferenceChange} />
      </div>
      {mode === "change" ? (
        <label className="form-field mt-3" htmlFor="targetPlanId">
          <span>Plan nuevo</span>
          <select className="app-input" id="targetPlanId" onChange={(event) => onTargetPlanChange(event.target.value)} value={targetPlanId}>
            {plans.map((plan) => (
              <option key={plan.id} value={plan.id}>
                {plan.name} - {formatMoney(plan.priceCents, plan.currency)}
              </option>
            ))}
          </select>
        </label>
      ) : null}
      <label className="mt-3 flex items-center gap-2 text-sm font-medium text-[#42514a]">
        <input checked={renewValidateNow} onChange={(event) => onValidateNowChange(event.target.checked)} type="checkbox" />
        Validar pago ahora
      </label>
    </section>
  );
}

function toForm(client: ClientDetail): ClientUpdatePayload {
  return {
    firstName: client.firstName,
    lastName: client.lastName,
    maternalLastName: client.maternalLastName ?? "",
    birthDate: client.birthDate ?? "",
    gender: client.gender ?? "",
    phone: client.phone ?? "",
    email: client.email ?? "",
    photoReference: client.photoReference ?? "",
    status: client.status,
    notes: client.notes ?? "",
  };
}

function validateClientUpdate(form: ClientUpdatePayload) {
  const errors: FieldErrorMap = {};
  addError(errors, "firstName", validateRequired(form.firstName, "Nombre"));
  addError(errors, "lastName", validateRequired(form.lastName, "Apellido paterno"));
  addError(errors, "phone", validatePhone(form.phone ?? "", true));
  addError(errors, "email", validateEmail(form.email ?? "", false));
  addError(errors, "birthDate", validateIsoDate(form.birthDate ?? "", "Fecha de nacimiento", false));
  addError(errors, "gender", validateGender(form.gender ?? "", false));
  return errors;
}

function addError(errors: FieldErrorMap, key: string, value: string) {
  if (value) {
    errors[key] = value;
  }
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="font-medium text-[#6f7a73]">{label}</dt>
      <dd className="mt-1 font-semibold">{value}</dd>
    </div>
  );
}

function AuthRequired() {
  return (
    <AuthRequiredCard
      message="La ficha de socio contiene informacion sensible y requiere una cuenta autorizada."
      title="Inicia sesion para consultar socios"
    />
  );
}

function clientFullName(client: ClientDetail) {
  return `${client.firstName} ${client.lastName} ${client.maternalLastName ?? ""}`.trim();
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("es-MX", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

function formatDay(value: string) {
  return new Intl.DateTimeFormat("es-MX", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    timeZone: "UTC",
  }).format(new Date(`${value}T00:00:00Z`));
}

function statusLabel(status: string) {
  const labels: Record<string, string> = {
    ACTIVE: "Activo",
    INACTIVE: "Inactivo",
    SUSPENDED: "Suspendido",
  };
  return labels[status] ?? status;
}

function genderLabel(gender: string) {
  const labels: Record<string, string> = {
    FEMALE: "Mujer",
    MALE: "Hombre",
  };
  return labels[gender] ?? gender;
}

function membershipStatusLabel(status: string) {
  const labels: Record<string, string> = {
    ACTIVE: "Activa",
    EXPIRED: "Expirada",
    CANCELLED: "Cancelada",
  };
  return labels[status] ?? status;
}

function formatMoney(cents: number, currency: string) {
  return new Intl.NumberFormat("es-MX", {
    style: "currency",
    currency,
  }).format(cents / 100);
}
