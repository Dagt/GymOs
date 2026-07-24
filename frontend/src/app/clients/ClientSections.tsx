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
        placeholder="Nombre, teléfono o email"
        value={query}
      />
      <button className="app-button" type="submit">
        Buscar
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
    <section className="catalog-toolbar app-card p-5">
      <div>
        <p className="app-eyebrow">Recepción de socios</p>
        <h2 className="app-card-title">Consulta y alta de socios</h2>
        <p className="app-card-copy">
          El socio se registra primero. La membresía inicial es opcional y se procesa como una operación separada.
        </p>
      </div>
      {canCreate ? (
        <button className="app-button" onClick={onCreateClient} type="button">
          Registrar socio
        </button>
      ) : (
        <span className="rounded-lg border border-[#e4d5af] bg-[#fffaf0] px-3 py-2 text-sm text-[#76551f]">
          Tu perfil permite consultar, pero no registrar socios.
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
    <section className="app-card p-5">
      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="app-eyebrow">Búsqueda</p>
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
              {client.phone ?? "Sin teléfono"} - {client.email ?? "Sin email"} - {client.status}
            </span>
          </Link>
        ))}
      </div>
    </section>
  );
}

type CreateClientDrawerProps = {
  canRegisterMembership: boolean;
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
  canRegisterMembership,
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
  const hasSelectedPlan = canRegisterMembership && Boolean(form.planId);

  return (
    <AppDrawer
      description="Registra primero los datos del socio. Puedes agregar una membresía y su pago en el mismo flujo, pero es opcional."
      onClose={onClose}
      open={open}
      title="Registrar socio"
    >
      <form className="grid gap-5" onSubmit={onSubmit}>
        <section className="grid gap-4 rounded-xl border border-[#d8ddd2] bg-white p-4">
          <div>
            <p className="font-semibold text-[#111815]">Datos del socio</p>
            <p className="mt-1 text-sm text-[#5d6b63]">Estos datos crean la ficha personal.</p>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField error={errors.firstName} label="Nombre" name="firstName" value={form.firstName} onChange={(value) => onFormChange("firstName", value)} />
            <TextField error={errors.lastName} label="Apellido paterno" name="lastName" value={form.lastName} onChange={(value) => onFormChange("lastName", value)} />
            <TextField error={errors.maternalLastName} label="Apellido materno" name="maternalLastName" required={false} value={form.maternalLastName ?? ""} onChange={(value) => onFormChange("maternalLastName", value)} />
            <PhoneField error={errors.phone} label="Teléfono" name="phone" required value={form.phone ?? ""} onChange={(value) => onFormChange("phone", value)} />
            <EmailField error={errors.email} label="Email" name="email" value={form.email ?? ""} onChange={(value) => onFormChange("email", value)} />
            <DateField error={errors.birthDate} label="Fecha de nacimiento" name="birthDate" value={form.birthDate ?? ""} onChange={(value) => onFormChange("birthDate", value)} />
            <GenderField error={errors.gender} label="Género" name="gender" value={form.gender ?? ""} onChange={(value) => onFormChange("gender", value)} />
          </div>
          <label className="form-field" htmlFor="notes">
            <span>Notas</span>
            <textarea
              className="app-input min-h-20 py-2"
              id="notes"
              onChange={(event) => onFormChange("notes", event.target.value)}
              value={form.notes ?? ""}
            />
          </label>
        </section>

        {canRegisterMembership ? (
          <section className="grid gap-4 rounded-xl border border-[#d8ddd2] bg-[#fafbf9] p-4">
            <div>
              <p className="font-semibold text-[#111815]">Membresía inicial opcional</p>
              <p className="mt-1 text-sm text-[#5d6b63]">
                Si la dejas vacía, sólo se crea el socio y podrás asignar la membresía después desde su ficha.
              </p>
            </div>

            <label className="form-field" htmlFor="planId">
              <span>Plan inicial</span>
              <select className="app-input" id="planId" onChange={(event) => onFormChange("planId", event.target.value)} value={form.planId}>
                <option value="">Sin membresía inicial</option>
                {plans.map((plan) => (
                  <option key={plan.id} value={plan.id}>
                    {plan.name} - {formatMoney(plan.priceCents, plan.currency)}
                  </option>
                ))}
              </select>
            </label>

            {hasSelectedPlan ? (
              <>
                <div className="grid gap-4 sm:grid-cols-2">
                  <label className="form-field" htmlFor="paymentMethod">
                    <span>Método de pago</span>
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
                  Marcar el pago como validado ahora
                </label>
              </>
            ) : null}
          </section>
        ) : (
          <p className="rounded-xl border border-[#e4d5af] bg-[#fffaf0] px-4 py-3 text-sm text-[#76551f]">
            Tu perfil puede registrar al socio, pero la venta de una membresía debe realizarla un usuario con permiso de caja.
          </p>
        )}

        {createdClientId ? (
          <Link className="text-sm font-semibold text-[var(--brand-primary)]" href={`/clients/${createdClientId}`}>
            Socio registrado. Abrir ficha.
          </Link>
        ) : null}
        <button className="form-primary-button" disabled={saving} type="submit">
          {saving ? "Guardando..." : hasSelectedPlan ? "Registrar socio y membresía" : "Registrar socio"}
        </button>
      </form>
    </AppDrawer>
  );
}
