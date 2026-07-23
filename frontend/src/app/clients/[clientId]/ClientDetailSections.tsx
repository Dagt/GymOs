import Link from "next/link";
import type { FormEvent } from "react";
import { AppDrawer } from "@/components/AppDrawer";
import {
  DateField,
  EmailField,
  type FieldErrorMap,
  GenderField,
  PhoneField,
  TextField,
} from "@/components/FormFields";
import type {
  ClientDetail,
  ClientDetailResponse,
  ClientMembershipSummary,
  ClientUpdatePayload,
  PlanSummary,
  RenewMembershipPayload,
} from "@/lib/api";
import {
  formatDate,
  formatDay,
  formatMoney,
  genderLabel,
  membershipStatusLabel,
  statusLabel,
} from "./ClientDetailHelpers";
import type { MembershipAction } from "./useClientDetailController";

export function ClientProfileCard({
  client,
  onEdit,
}: {
  client: ClientDetail;
  onEdit: () => void;
}) {
  return (
    <section className="app-card p-5">
      <div className="admin-panel-header">
        <div>
          <p>Datos personales</p>
          <h2>Ficha del socio</h2>
        </div>
        <button className="app-button" onClick={onEdit} type="button">
          Editar datos
        </button>
      </div>
      <dl className="mt-5 grid gap-4 sm:grid-cols-2">
        <Metric label="Nombre" value={client.firstName} />
        <Metric label="Apellido paterno" value={client.lastName} />
        <Metric label="Apellido materno" value={client.maternalLastName ?? "Sin registrar"} />
        <Metric label="Telefono" value={client.phone ?? "Sin telefono"} />
        <Metric label="Email" value={client.email ?? "Sin email"} />
        <Metric label="Nacimiento" value={client.birthDate ? formatDay(client.birthDate) : "Sin registrar"} />
        <Metric label="Genero" value={client.gender ? genderLabel(client.gender) : "Sin registrar"} />
        <Metric label="Estado" value={statusLabel(client.status)} />
      </dl>
      {client.notes ? (
        <p className="mt-5 rounded-md border border-[#d8ddd2] bg-white px-4 py-3 text-sm text-[#5d6b63]">
          {client.notes}
        </p>
      ) : null}
    </section>
  );
}

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
        <section className="admin-empty-card">
          <strong>Sin membresia registrada</strong>
          <span>Registra una membresia inicial desde el alta de socio o desde caja seleccionando este socio.</span>
          <Link href="/pos">Abrir caja / POS</Link>
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

export function EditClientDrawer({
  errors,
  form,
  onClose,
  onFieldChange,
  onSubmit,
  open,
  saving,
}: {
  errors: FieldErrorMap;
  form: ClientUpdatePayload;
  onClose: () => void;
  onFieldChange: (field: keyof ClientUpdatePayload, value: string) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  open: boolean;
  saving: boolean;
}) {
  return (
    <AppDrawer
      description="Actualiza datos personales, contacto, estado y notas internas del socio."
      onClose={onClose}
      open={open}
      title="Editar datos del socio"
    >
      <form className="grid gap-4" onSubmit={onSubmit}>
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField error={errors.firstName} label="Nombre" name="firstName" value={form.firstName ?? ""} onChange={(value) => onFieldChange("firstName", value)} />
          <TextField error={errors.lastName} label="Apellido paterno" name="lastName" value={form.lastName ?? ""} onChange={(value) => onFieldChange("lastName", value)} />
          <TextField error={errors.maternalLastName} label="Apellido materno" name="maternalLastName" required={false} value={form.maternalLastName ?? ""} onChange={(value) => onFieldChange("maternalLastName", value)} />
          <PhoneField error={errors.phone} label="Telefono" name="phone" required value={form.phone ?? ""} onChange={(value) => onFieldChange("phone", value)} />
          <EmailField error={errors.email} label="Email" name="email" value={form.email ?? ""} onChange={(value) => onFieldChange("email", value)} />
          <DateField error={errors.birthDate} label="Fecha de nacimiento" name="birthDate" value={form.birthDate ?? ""} onChange={(value) => onFieldChange("birthDate", value)} />
          <GenderField error={errors.gender} label="Genero" name="gender" value={form.gender ?? ""} onChange={(value) => onFieldChange("gender", value)} />
        </div>
        <label className="form-field" htmlFor="status">
          <span>Estado</span>
          <select className="app-input" id="status" onChange={(event) => onFieldChange("status", event.target.value)} value={form.status}>
            <option value="ACTIVE">Activo</option>
            <option value="INACTIVE">Inactivo</option>
            <option value="SUSPENDED">Suspendido</option>
          </select>
        </label>
        <label className="form-field" htmlFor="notes">
          <span>Notas</span>
          <textarea className="app-input min-h-28 py-2" id="notes" onChange={(event) => onFieldChange("notes", event.target.value)} value={form.notes ?? ""} />
        </label>
        <button className="form-primary-button" disabled={saving} type="submit">
          {saving ? "Guardando..." : "Guardar datos"}
        </button>
      </form>
    </AppDrawer>
  );
}

export function MembershipActionDrawer({
  action,
  changingMembershipId,
  onClose,
  onMethodChange,
  onReferenceChange,
  onSubmit,
  onTargetPlanChange,
  onValidateNowChange,
  plans,
  renewMethod,
  renewReference,
  renewingMembershipId,
  renewValidateNow,
  targetPlanId,
}: {
  action: MembershipAction | null;
  changingMembershipId: string | null;
  onClose: () => void;
  onMethodChange: (value: RenewMembershipPayload["method"]) => void;
  onReferenceChange: (value: string) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onTargetPlanChange: (value: string) => void;
  onValidateNowChange: (value: boolean) => void;
  plans: PlanSummary[];
  renewMethod: RenewMembershipPayload["method"];
  renewReference: string;
  renewingMembershipId: string | null;
  renewValidateNow: boolean;
  targetPlanId: string;
}) {
  return (
    <AppDrawer
      description={
        action?.type === "change"
          ? "Selecciona el nuevo plan, registra el pago y aplica el cambio de membresia."
          : "Registra el pago de renovacion y define si se valida de inmediato o queda pendiente."
      }
      onClose={onClose}
      open={Boolean(action)}
      title={action?.type === "change" ? "Cambiar plan" : "Renovar membresia"}
    >
      {action ? (
        <form className="grid gap-4" onSubmit={onSubmit}>
          <section className="rounded-md border border-[#d8ddd2] bg-white p-4">
            <p className="app-eyebrow">Membresia actual</p>
            <h3 className="mt-2 text-lg font-semibold">{action.membership.planName}</h3>
            <p className="mt-1 text-sm text-[#5d6b63]">
              {membershipStatusLabel(action.membership.status)} - {formatDay(action.membership.startsOn)} a {formatDay(action.membership.endsOn)}
            </p>
          </section>
          <MembershipControls
            mode={action.type}
            plans={plans}
            renewMethod={renewMethod}
            renewReference={renewReference}
            renewValidateNow={renewValidateNow}
            targetPlanId={targetPlanId}
            onMethodChange={onMethodChange}
            onReferenceChange={onReferenceChange}
            onTargetPlanChange={onTargetPlanChange}
            onValidateNowChange={onValidateNowChange}
          />
          <button
            className="form-primary-button"
            disabled={
              renewingMembershipId !== null
              || changingMembershipId !== null
              || (action.type === "change" && (!targetPlanId || targetPlanId === action.membership.planId))
            }
            type="submit"
          >
            {action.type === "change"
              ? changingMembershipId === action.membership.id ? "Cambiando..." : "Cambiar plan"
              : renewingMembershipId === action.membership.id ? "Renovando..." : "Renovar membresia"}
          </button>
        </form>
      ) : null}
    </AppDrawer>
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
          disabled={renewingMembershipId !== null || changingMembershipId !== null || membership.status !== "ACTIVE"}
          onClick={() => onMembershipAction(membership, "renew")}
          type="button"
        >
          {renewingMembershipId === membership.id ? "Renovando..." : "Renovar"}
        </button>
        <button
          className="app-button-secondary"
          disabled={renewingMembershipId !== null || changingMembershipId !== null || membership.status !== "ACTIVE" || plans.length < 2}
          onClick={() => onMembershipAction(membership, "change")}
          type="button"
        >
          {changingMembershipId === membership.id ? "Cambiando..." : "Cambiar plan"}
        </button>
      </div>
    </article>
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

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="font-medium text-[#6f7a73]">{label}</dt>
      <dd className="mt-1 font-semibold">{value}</dd>
    </div>
  );
}
