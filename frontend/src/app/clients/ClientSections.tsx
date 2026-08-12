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
import type { ClientSummary, PlanSummary } from "@/lib/api";
import { clientMembershipStatus } from "./client-list-model";
import {
  clientFullName,
  formatMoney,
  type ClientRegistrationForm,
} from "./ClientHelpers";

type SearchBarProps = {
  query: string;
  onQueryChange: (value: string) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
};

export function ClientSearchBar({ query, onQueryChange, onSubmit }: SearchBarProps) {
  return (
    <form className="bg-white rounded-2xl border border-gray-100 p-2 shadow-sm flex w-full gap-2 items-center" onSubmit={onSubmit}>
      <div className="flex-1 relative">
        <input
          className="h-12 w-full rounded-xl border-2 border-transparent bg-gray-50 px-5 text-base font-bold focus:border-[color:var(--brand-primary)] focus:bg-white focus:ring-4 focus:ring-[color:var(--brand-primary)]/5 transition-all outline-none"
          onChange={(event) => onQueryChange(event.target.value)}
          placeholder="Busca por nombre, teléfono o email..."
          value={query}
        />
      </div>
      <button className="h-12 px-8 rounded-xl bg-[color:var(--brand-primary)] text-white font-black text-sm hover:brightness-110 active:scale-95 transition-all shadow-md" type="submit">
        Buscar Socio
      </button>
    </form>
  );
}

type ClientToolbarProps = {
  canCreate: boolean;
  onCreateClient: () => void;
};

export function ClientToolbar({ canCreate, onCreateClient }: ClientToolbarProps) {
  return (
    <section className="app-card p-6 flex flex-col md:flex-row justify-between items-start md:items-center gap-6">
      <div>
        <p className="text-[0.65rem] font-black uppercase tracking-widest text-[color:var(--brand-primary)] opacity-60">Operaciones de Socios</p>
        <h2 className="text-xl font-black text-[color:var(--brand-primary)] mt-1">Gestión de Altas</h2>
        <p className="text-sm text-gray-500 font-medium mt-1">
          Crea expedientes nuevos y vincula membresías en un solo flujo.
        </p>
      </div>
      {canCreate ? (
        <button className="h-12 px-8 rounded-xl bg-[color:var(--brand-primary)] text-white font-black text-sm hover:opacity-95 active:scale-95 transition-all shadow-md" onClick={onCreateClient} type="button">
          + Registrar Nuevo Socio
        </button>
      ) : (
        <span className="px-4 py-2 bg-amber-50 text-amber-700 text-xs font-black rounded-lg border border-amber-200">
          Modo solo lectura
        </span>
      )}
    </section>
  );
}

type ClientListProps = {
  clients: ClientSummary[];
  loading: boolean;
};

export function ClientList({ clients, loading }: ClientListProps) {
  return (
    <section className="app-card p-6">
      <div className="flex items-center justify-between gap-3 mb-6">
        <div>
          <p className="text-[0.65rem] font-black uppercase tracking-widest text-[color:var(--brand-primary)] opacity-60">Base de Datos</p>
          <h2 className="text-xl font-black text-[color:var(--brand-primary)] mt-1">Socios registrados</h2>
        </div>
        <span className="px-3 py-1 bg-[color:var(--brand-surface)] text-[color:var(--brand-primary)] text-xs font-black rounded-full border border-[color:var(--brand-line)]">{clients.length} socios</span>
      </div>
      <div className="grid gap-1.5">
        {loading ? (
          <div className="py-6 flex flex-col items-center gap-2">
            <div className="w-6 h-6 border-3 border-[color:var(--brand-primary)]/10 border-t-[color:var(--brand-primary)] rounded-full animate-spin"></div>
            <p className="text-[0.65rem] font-bold text-gray-400 uppercase tracking-widest">Sincronizando socios...</p>
          </div>
        ) : null}
        {!loading && clients.length === 0 ? (
          <p className="py-6 text-center text-xs text-gray-400 font-bold italic bg-gray-50 rounded-xl border-2 border-dashed border-gray-100">No hay socios para mostrar.</p>
        ) : null}
        {clients.map((client) => {
          const membershipStatus = clientMembershipStatus(client);
          return (
            <Link
              className="group flex items-center justify-between px-4 py-2.5 rounded-xl border border-gray-100 bg-gray-50/50 hover:bg-white hover:border-[color:var(--brand-primary)] hover:shadow-sm transition-all duration-200"
              href={`/clients/${client.id}`}
              key={client.id}
            >
              <div className="flex items-center gap-3">
                <span className="w-2 h-2 rounded-full bg-[color:var(--brand-primary)] opacity-40 group-hover:opacity-100 transition-opacity"></span>
                <div className="flex flex-col">
                  <span className="font-bold text-gray-900 group-hover:text-[color:var(--brand-primary)] transition-colors text-sm leading-tight">{clientFullName(client)}</span>
                  <span className="mt-1 flex flex-wrap items-center gap-x-1.5 gap-y-1 text-[0.65rem] font-bold uppercase tracking-wider text-gray-400">
                    <span>{client.phone || "Sin teléfono"}</span>
                    <span aria-hidden>•</span>
                    <span>{client.email || "Sin email"}</span>
                    <span aria-hidden>•</span>
                    <span className="text-gray-600">Plan: {client.currentPlanName || "Sin plan"}</span>
                    <span aria-hidden>•</span>
                    <span className={`rounded-full border px-2 py-0.5 tracking-wide ${membershipStatus.className}`}>
                      {membershipStatus.label}
                    </span>
                  </span>
                </div>
              </div>
              <span className="w-6 h-6 rounded-md bg-white border border-gray-100 flex items-center justify-center text-[color:var(--brand-primary)] text-xs font-bold group-hover:bg-[color:var(--brand-primary)] group-hover:text-white transition-all">➔</span>
            </Link>
          );
        })}
      </div>
    </section>
  );
}

type CreateClientDrawerProps = {
  canRegisterMembership: boolean;
  createdClientId: string | null;
  errors: FieldErrorMap;
  form: ClientRegistrationForm;
  manualCardEnabled: boolean;
  mercadoPagoEnabled: boolean;
  open: boolean;
  plans: PlanSummary[];
  saving: boolean;
  onClose: () => void;
  onFormChange: (field: keyof ClientRegistrationForm, value: string | boolean) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
};

export function CreateClientDrawer({
  canRegisterMembership,
  createdClientId,
  errors,
  form,
  manualCardEnabled,
  mercadoPagoEnabled,
  open,
  plans,
  saving,
  onClose,
  onFormChange,
  onSubmit,
}: CreateClientDrawerProps) {
  const hasSelectedPlan = canRegisterMembership && Boolean(form.planId);

  return (
    <AppDrawer
      description="Registra primero los datos del socio. Puedes agregar una membresía y su pago en el mismo flujo, pero es opcional."
      onClose={onClose}
      open={open}
      title="Registrar socio"
    >
      <form className="grid gap-6" onSubmit={onSubmit}>
        <section className="grid gap-5 rounded-2xl border border-[var(--brand-line)] bg-white p-6 shadow-sm">
          <div>
            <p className="text-sm font-black text-[color:var(--brand-primary)] uppercase tracking-wider">Datos Personales</p>
            <p className="mt-1 text-xs text-[var(--brand-muted)] font-medium uppercase tracking-tighter opacity-70">Información básica para el expediente</p>
          </div>
          <div className="grid gap-4 sm:grid-cols-2 mt-2">
            <TextField error={errors.firstName} label="Nombre" name="firstName" value={form.firstName} onChange={(value) => onFormChange("firstName", value)} />
            <TextField error={errors.lastName} label="Apellido paterno" name="lastName" value={form.lastName} onChange={(value) => onFormChange("lastName", value)} />
            <TextField error={errors.maternalLastName} label="Apellido materno" name="maternalLastName" required={false} value={form.maternalLastName ?? ""} onChange={(value) => onFormChange("maternalLastName", value)} />
            <PhoneField error={errors.phone} label="Teléfono" name="phone" required value={form.phone ?? ""} onChange={(value) => onFormChange("phone", value)} />
            <EmailField error={errors.email} label="Email" name="email" value={form.email ?? ""} onChange={(value) => onFormChange("email", value)} />
            <DateField error={errors.birthDate} label="Fecha de nacimiento" name="birthDate" required value={form.birthDate ?? ""} onChange={(value) => onFormChange("birthDate", value)} />
            <GenderField error={errors.gender} label="Género" name="gender" value={form.gender ?? ""} onChange={(value) => onFormChange("gender", value)} />
          </div>
          <label className="flex flex-col gap-2" htmlFor="notes">
            <span className="text-[0.65rem] font-black uppercase tracking-widest text-[color:var(--brand-primary)] opacity-60 ml-1">Notas Médicas o Generales</span>
            <textarea
              className="app-input min-h-24 py-3"
              id="notes"
              onChange={(event) => onFormChange("notes", event.target.value)}
              value={form.notes ?? ""}
            />
          </label>
        </section>

        {canRegisterMembership ? (
          <section className="grid gap-5 rounded-2xl border border-[var(--brand-line)] bg-[var(--brand-surface)] p-6 shadow-inner">
            <div>
              <p className="text-sm font-black text-[color:var(--brand-primary)] uppercase tracking-wider">Membresía Inicial</p>
              <p className="mt-1 text-xs text-[var(--brand-muted)] font-medium uppercase tracking-tighter opacity-70">
                Paso opcional: Vincula un plan de acceso ahora
              </p>
            </div>

            <label className="flex flex-col gap-2" htmlFor="planId">
              <span className="text-[0.65rem] font-black uppercase tracking-widest text-[color:var(--brand-primary)] opacity-60 ml-1">Seleccionar Plan</span>
              <select className="app-input font-bold" id="planId" onChange={(event) => onFormChange("planId", event.target.value)} value={form.planId}>
                <option value="">Sin membresía inicial</option>
                {plans.map((plan) => (
                  <option key={plan.id} value={plan.id}>
                    {plan.name} - {formatMoney(plan.priceCents, plan.currency)}
                  </option>
                ))}
              </select>
            </label>

            {hasSelectedPlan ? (
              <div className="mt-4 pt-4 border-t border-[var(--brand-line)] grid gap-5">
                <div className="grid gap-4 sm:grid-cols-2">
                  <label className="flex flex-col gap-2" htmlFor="paymentMethod">
                    <span className="text-[0.65rem] font-black uppercase tracking-widest text-[color:var(--brand-primary)] opacity-60 ml-1">Método de pago</span>
                    <select className="app-input font-bold" id="paymentMethod" onChange={(event) => onFormChange("paymentMethod", event.target.value)} value={form.paymentMethod}>
                      <option value="CASH">Efectivo</option>
                      <option value="TRANSFER">Transferencia</option>
                      {manualCardEnabled ? <option value="CARD_MANUAL">Tarjeta manual</option> : null}
                      {mercadoPagoEnabled ? <option value="CARD_MERCADO_PAGO">Mercado Pago Point</option> : null}
                    </select>
                  </label>
                  <TextField label="Referencia de pago" name="paymentReference" required={false} value={form.paymentReference} onChange={(value) => onFormChange("paymentReference", value)} />
                </div>

                <label className="flex items-center gap-3 p-4 rounded-xl bg-white border border-[var(--brand-line)] cursor-pointer group shadow-sm">
                  <input checked={form.validateNow} className="w-5 h-5 rounded border-2 border-gray-300 text-[color:var(--brand-primary)] focus:ring-[color:var(--brand-primary)]/20" disabled={form.paymentMethod === "CARD_MERCADO_PAGO"} onChange={(event) => onFormChange("validateNow", event.target.checked)} type="checkbox" />
                  <span className="text-sm font-black text-gray-700 group-hover:text-[color:var(--brand-primary)] transition-colors">{form.paymentMethod === "CARD_MERCADO_PAGO" ? "Confirmación automática por webhook" : "Validar pago automáticamente"}</span>
                </label>
              </div>
            ) : null}
          </section>
        ) : (
          <p className="rounded-2xl border-2 border-amber-100 bg-amber-50 px-5 py-4 text-xs font-black text-amber-700 uppercase tracking-wide leading-relaxed">
            Tu perfil permite el registro básico. La venta de planes debe realizarse por un cajero autorizado.
          </p>
        )}

        {createdClientId ? (
          <Link className="px-6 py-4 rounded-xl bg-green-50 text-green-700 text-sm font-black border-2 border-green-100 flex items-center justify-between group hover:bg-green-100 transition-all" href={`/clients/${createdClientId}`}>
            <span>✅ SOCIO REGISTRADO CON ÉXITO</span>
            <span className="group-hover:translate-x-1 transition-transform">ABRIR FICHA ➔</span>
          </Link>
        ) : null}
        
        <button className="mt-4 h-16 w-full rounded-2xl bg-[color:var(--brand-primary)] text-white font-black text-lg shadow-xl hover:brightness-110 active:scale-95 transition-all flex items-center justify-center gap-3" disabled={saving} type="submit">
          {saving ? (
            <span className="w-6 h-6 border-4 border-white/30 border-t-white rounded-full animate-spin"></span>
          ) : (
            <>{hasSelectedPlan ? "REGISTRAR SOCIO Y MEMBRESÍA" : "REGISTRAR SOCIO"}</>
          )}
        </button>
      </form>
    </AppDrawer>
  );
}
