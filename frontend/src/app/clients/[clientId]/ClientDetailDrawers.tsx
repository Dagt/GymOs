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
  manualCardEnabled,
  mercadoPagoEnabled,
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
  manualCardEnabled: boolean;
  mercadoPagoEnabled: boolean;
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
  const initialEnrollment = Boolean(action && !action.membership.id);
  const availablePlans = action?.type === "change" && action.membership.id
    ? plans.filter((plan) => plan.id !== action.membership.planId)
    : plans;

  return (
    <AppDrawer
      description={
        initialEnrollment
          ? "Selecciona el primer plan y registra el pago para activar la membresía."
          : action?.type === "change"
            ? "Selecciona el nuevo plan. Al confirmar el pago se sustituye el plan vigente y se recalculan sus accesos."
          : "Registra el pago de renovacion y define si se valida de inmediato o queda pendiente."
      }
      onClose={onClose}
      open={Boolean(action)}
      title={initialEnrollment ? "Asignar membresía inicial" : action?.type === "change" ? "Cambiar plan" : "Renovar membresia"}
    >
      {action ? (
        <form className="grid gap-4" onSubmit={onSubmit}>
          <section className="rounded-md border border-[#d8ddd2] bg-white p-4">
            <p className="app-eyebrow">
              {initialEnrollment ? "Contratación inicial" : "Membresía actual"}
            </p>
            <h3 className="mt-2 text-lg font-semibold">
              {initialEnrollment ? "Socio sin membresía" : action.membership.planName}
            </h3>
            {initialEnrollment ? (
              <p className="mt-1 text-sm text-[#5d6b63]">
                La vigencia se calculará cuando selecciones el plan y confirmes el pago.
              </p>
            ) : (
              <p className="mt-1 text-sm text-[#5d6b63]">
                {membershipStatusLabel(action.membership.status)} - {formatDay(action.membership.startsOn)} a {formatDay(action.membership.endsOn)}
              </p>
            )}
          </section>
          <MembershipControls
            membershipQuote={membershipQuote}
            manualCardEnabled={manualCardEnabled}
            mercadoPagoEnabled={mercadoPagoEnabled}
            mode={action.type}
            plans={availablePlans}
            renewMethod={renewMethod}
            renewReference={renewReference}
            renewValidateNow={renewValidateNow}
            targetPlanId={targetPlanId}
            onMethodChange={onMethodChange}
            onReferenceChange={onReferenceChange}
            onTargetPlanChange={onTargetPlanChange}
            onValidateNowChange={onValidateNowChange}
          />
          {action.type === "change" && !initialEnrollment ? (
            <p className="rounded-md border border-[#d8ddd2] bg-[#f7f9f6] p-3 text-xs text-[#5d6b63]">
              El cambio cancela el periodo actual e inicia hoy una membresía con la duración completa del
              nuevo plan. No transfiere días restantes ni calcula prorrateo. La venta y ambos movimientos
              quedan en el historial, y los accesos heredados se reemplazan al validar el pago.
            </p>
          ) : null}
          <button
            className="form-primary-button"
            disabled={
              renewingMembershipId !== null
              || changingMembershipId !== null
              || (
                action.type === "change"
                && (!targetPlanId || (!initialEnrollment && targetPlanId === action.membership.planId))
              )
            }
            type="submit"
          >
            {action.type === "change"
              ? changingMembershipId === action.membership.id
                ? initialEnrollment ? "Asignando..." : "Cambiando..."
                : initialEnrollment ? "Asignar membresía" : "Cambiar plan"
              : renewingMembershipId === action.membership.id ? "Renovando..." : "Renovar membresia"}
          </button>
        </form>
      ) : null}
    </AppDrawer>
  );
}

function MembershipControls({
  membershipQuote,
  manualCardEnabled,
  mercadoPagoEnabled,
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
  manualCardEnabled: boolean;
  mercadoPagoEnabled: boolean;
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
            {manualCardEnabled ? <option value="CARD_MANUAL">Tarjeta manual</option> : null}
            {mercadoPagoEnabled ? <option value="CARD_MERCADO_PAGO">Mercado Pago Point</option> : null}
          </select>
        </label>
        <TextField label="Referencia" name="renewReference" required={false} value={renewReference} onChange={onReferenceChange} />
      </div>
      {mode === "change" ? (
        <label className="form-field mt-3" htmlFor="targetPlanId">
          <span>Plan nuevo</span>
          <select className="app-input" id="targetPlanId" onChange={(event) => onTargetPlanChange(event.target.value)} value={targetPlanId}>
            {plans.length === 0 ? <option value="">No hay otro plan activo disponible</option> : null}
            {plans.map((plan) => (
              <option key={plan.id} value={plan.id}>
                {plan.name} - {formatMoney(plan.priceCents, plan.currency)}
              </option>
            ))}
          </select>
        </label>
      ) : null}
      <label className="mt-3 flex items-center gap-2 text-sm font-medium text-[#42514a]">
        <input
          checked={renewMethod === "CARD_MERCADO_PAGO" ? false : mode === "change" ? true : renewValidateNow}
          disabled={mode === "change" || renewMethod === "CARD_MERCADO_PAGO"}
          onChange={(event) => onValidateNowChange(event.target.checked)}
          type="checkbox"
        />
        {renewMethod === "CARD_MERCADO_PAGO" ? "Confirmacion automatica por webhook" : mode === "change" ? "Validar pago y aplicar el cambio ahora" : "Validar pago ahora"}
      </label>
      <div className="mt-3">
        <MembershipQuoteSummary loading={false} quote={membershipQuote} />
      </div>
    </section>
  );
}
