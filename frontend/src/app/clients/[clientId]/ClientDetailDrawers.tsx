import type { FormEvent } from "react";
import { AppDrawer } from "@/components/AppDrawer";
import { MembershipQuoteSummary } from "@/components/MembershipQuoteSummary";
import {
  DateField,
  EmailField,
  type FieldErrorMap,
  GenderField,
  PhoneField,
  TextField,
} from "@/components/FormFields";
import type {
  ClientUpdatePayload,
  MembershipQuote,
  PlanSummary,
  RenewMembershipPayload,
} from "@/lib/api";
import {
  formatDay,
  formatMoney,
  membershipStatusLabel,
} from "./ClientDetailHelpers";
import type { MembershipAction } from "./useClientDetailController";

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
  membershipQuote,
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
  membershipQuote: MembershipQuote | null;
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
            membershipQuote={membershipQuote}
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

function MembershipControls({
  membershipQuote,
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
  membershipQuote: MembershipQuote | null;
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
      <div className="mt-3">
        <MembershipQuoteSummary loading={false} quote={membershipQuote} />
      </div>
    </section>
  );
}
