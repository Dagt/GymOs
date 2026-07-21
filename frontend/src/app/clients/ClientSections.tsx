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
    <form className="admin-panel flex w-full gap-2" onSubmit={onSubmit}>
      <input
        className="app-input min-w-0 flex-1"
        onChange={(event) => onQueryChange(event.target.value)}
        placeholder="Nombre, telefono o email"
        value={query}
      />
      <button className="app-button" type="submit">
        Buscar
      </button>
    </form>
  );
}

type ClientToolbarProps = {
  onCreateClient: () => void;
};

export function ClientToolbar({ onCreateClient }: ClientToolbarProps) {
  return (
    <section className="catalog-toolbar app-card p-5">
      <div>
        <p className="app-eyebrow">Recepcion de socios</p>
        <h2 className="app-card-title">Busqueda y alta en el mismo flujo</h2>
        <p className="app-card-copy">
          La lista queda disponible para consulta rapida. El registro completo con membresia inicial se abre en un panel lateral.
        </p>
      </div>
      <button className="app-button" onClick={onCreateClient} type="button">
        Registrar socio
      </button>
    </section>
  );
}

type ClientListProps = {
  clients: ClientSummary[];
  loading: boolean;
};

export function ClientList({ clients, loading }: ClientListProps) {
  return (
    <section className="app-card p-5">
      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="app-eyebrow">Busqueda</p>
          <h2 className="mt-2 text-xl font-semibold">Socios registrados</h2>
        </div>
        <span className="text-sm text-[#6f7a73]">{clients.length} socios</span>
      </div>
      <div className="mt-5 grid gap-3">
        {loading ? <p className="text-sm text-[#5d6b63]">Cargando socios...</p> : null}
        {!loading && clients.length === 0 ? (
          <p className="text-sm text-[#5d6b63]">No hay socios para mostrar.</p>
        ) : null}
        {clients.map((client) => (
          <Link
            className="rounded-md border border-[#d8ddd2] px-4 py-3 transition hover:border-[var(--brand-primary)] hover:bg-[#f6f7f2]"
            href={`/clients/${client.id}`}
            key={client.id}
          >
            <span className="block font-semibold">{clientFullName(client)}</span>
            <span className="mt-1 block text-sm text-[#5d6b63]">
              {client.phone ?? "Sin telefono"} - {client.email ?? "Sin email"} - {client.status}
            </span>
          </Link>
        ))}
      </div>
    </section>
  );
}

type CreateClientDrawerProps = {
  createdClientId: string | null;
  errors: FieldErrorMap;
  form: ClientRegistrationForm;
  open: boolean;
  plans: PlanSummary[];
  saving: boolean;
  onClose: () => void;
  onFormChange: (field: keyof ClientRegistrationForm, value: string | boolean) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
};

export function CreateClientDrawer({
  createdClientId,
  errors,
  form,
  open,
  plans,
  saving,
  onClose,
  onFormChange,
  onSubmit,
}: CreateClientDrawerProps) {
  return (
    <AppDrawer
      description="Captura datos personales, membresia inicial y pago de entrada desde recepcion."
      onClose={onClose}
      open={open}
      title="Registrar socio"
    >
      <form className="grid gap-4" onSubmit={onSubmit}>
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField error={errors.firstName} label="Nombre" name="firstName" value={form.firstName} onChange={(value) => onFormChange("firstName", value)} />
          <TextField error={errors.lastName} label="Apellido paterno" name="lastName" value={form.lastName} onChange={(value) => onFormChange("lastName", value)} />
          <TextField error={errors.maternalLastName} label="Apellido materno" name="maternalLastName" required={false} value={form.maternalLastName ?? ""} onChange={(value) => onFormChange("maternalLastName", value)} />
          <PhoneField error={errors.phone} label="Telefono" name="phone" required value={form.phone ?? ""} onChange={(value) => onFormChange("phone", value)} />
          <EmailField error={errors.email} label="Email" name="email" value={form.email ?? ""} onChange={(value) => onFormChange("email", value)} />
          <DateField error={errors.birthDate} label="Fecha de nacimiento" name="birthDate" value={form.birthDate ?? ""} onChange={(value) => onFormChange("birthDate", value)} />
          <GenderField error={errors.gender} label="Genero" name="gender" value={form.gender ?? ""} onChange={(value) => onFormChange("gender", value)} />
        </div>

        <label className="form-field" htmlFor="planId">
          <span>Plan inicial{plans.length > 0 ? <b>*</b> : null}</span>
          <select className="app-input" id="planId" onChange={(event) => onFormChange("planId", event.target.value)} value={form.planId}>
            {plans.length === 0 ? <option value="">No hay planes activos</option> : null}
            {plans.map((plan) => (
              <option key={plan.id} value={plan.id}>
                {plan.name} - {formatMoney(plan.priceCents, plan.currency)}
              </option>
            ))}
          </select>
          {errors.planId ? <small className="form-field-error">{errors.planId}</small> : null}
        </label>

        <div className="grid gap-4 sm:grid-cols-2">
          <label className="form-field" htmlFor="paymentMethod">
            <span>Metodo de pago</span>
            <select className="app-input" id="paymentMethod" onChange={(event) => onFormChange("paymentMethod", event.target.value)} value={form.paymentMethod}>
              <option value="CASH">Efectivo</option>
              <option value="TRANSFER">Transferencia</option>
              <option value="CARD_MANUAL">Tarjeta manual</option>
            </select>
          </label>
          <TextField label="Referencia de pago" name="paymentReference" required={false} value={form.paymentReference} onChange={(value) => onFormChange("paymentReference", value)} />
        </div>

        <label className="flex items-center gap-2 text-sm font-medium text-[#42514a]">
          <input checked={form.validateNow} onChange={(event) => onFormChange("validateNow", event.target.checked)} type="checkbox" />
          Validar pago ahora
        </label>

        <label className="form-field" htmlFor="notes">
          <span>Notas</span>
          <textarea
            className="app-input min-h-24 py-2"
            id="notes"
            onChange={(event) => onFormChange("notes", event.target.value)}
            value={form.notes ?? ""}
          />
        </label>

        {createdClientId ? (
          <Link className="text-sm font-semibold text-[var(--brand-primary)]" href={`/clients/${createdClientId}`}>
            Socio registrado. Abrir ficha.
          </Link>
        ) : null}
        <button className="form-primary-button" disabled={saving} type="submit">
          {saving ? "Guardando..." : "Registrar socio"}
        </button>
      </form>
    </AppDrawer>
  );
}
