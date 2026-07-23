import { type FormEvent, type ReactNode } from "react";
import {
  type ProfessionalAvailability,
  type ProfessionalServiceSummary,
  type ProfessionalSummary,
} from "@/lib/api";

export function WellnessNotice({ tone, text }: { tone: "ok" | "error"; text: string }) {
  const styles =
    tone === "ok"
      ? "border-[#bad9c4] bg-[#eff8f1] text-[#31563c]"
      : "border-[#e5b7b7] bg-[#fff4f4] text-[#8c2f2f]";
  return <p className={`rounded-lg border px-3 py-2 text-sm font-semibold ${styles}`}>{text}</p>;
}

export function ProfessionalForm({
  displayName,
  email,
  phone,
  professionalType,
  saving,
  onDisplayNameChange,
  onEmailChange,
  onPhoneChange,
  onProfessionalTypeChange,
  onSubmit,
}: {
  displayName: string;
  email: string;
  phone: string;
  professionalType: string;
  saving: boolean;
  onDisplayNameChange: (value: string) => void;
  onEmailChange: (value: string) => void;
  onPhoneChange: (value: string) => void;
  onProfessionalTypeChange: (value: string) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <form className="admin-panel" onSubmit={onSubmit}>
      <div className="admin-panel-header">
        <div>
          <p>Equipo externo o interno</p>
          <h2>Nuevo profesional</h2>
        </div>
      </div>
      <div className="mt-4 grid gap-3">
        <Field label="Nombre visible">
          <input
            className="app-input"
            onChange={(event) => onDisplayNameChange(event.target.value)}
            placeholder="Ej. Dra. Ana Lopez"
            value={displayName}
          />
        </Field>
        <Field label="Especialidad">
          <select
            className="app-input"
            onChange={(event) => onProfessionalTypeChange(event.target.value)}
            value={professionalType}
          >
            <option value="NUTRITIONIST">Nutriologo</option>
            <option value="PHYSIOTHERAPIST">Fisioterapeuta</option>
            <option value="CHIROPRACTOR">Quiropractico</option>
            <option value="WELLNESS">Bienestar</option>
          </select>
        </Field>
        <Field label="Correo">
          <input
            className="app-input"
            onChange={(event) => onEmailChange(event.target.value)}
            placeholder="correo@ejemplo.com"
            value={email}
          />
        </Field>
        <Field label="Telefono">
          <input
            className="app-input"
            onChange={(event) => onPhoneChange(event.target.value)}
            placeholder="+52..."
            value={phone}
          />
        </Field>
      </div>
      <button className="form-primary-button mt-4" disabled={saving || !displayName.trim()} type="submit">
        Crear profesional
      </button>
    </form>
  );
}

export function ProfessionalsDirectory({
  professionals,
  selectedProfessionalId,
  onRefresh,
  onSelectProfessional,
}: {
  professionals: ProfessionalSummary[];
  selectedProfessionalId: string;
  onRefresh: () => void;
  onSelectProfessional: (professionalId: string) => void;
}) {
  return (
    <section className="admin-panel">
      <div className="admin-panel-header">
        <div>
          <p>Directorio</p>
          <h2>Profesionales</h2>
        </div>
        <button className="app-button-secondary" onClick={onRefresh} type="button">
          Actualizar
        </button>
      </div>
      <div className="mt-4 grid gap-3">
        {professionals.length === 0 ? (
          <p className="admin-muted-text">Aun no hay profesionales registrados para este gimnasio.</p>
        ) : (
          professionals.map((professional) => (
            <button
              className={`rounded-lg border p-4 text-left ${
                selectedProfessionalId === professional.id
                  ? "border-[var(--brand-primary)] bg-[var(--brand-primary-soft)]"
                  : "border-[var(--brand-line)] bg-white"
              }`}
              key={professional.id}
              onClick={() => onSelectProfessional(professional.id)}
              type="button"
            >
              <strong className="block text-base font-black text-[var(--brand-ink)]">
                {professional.displayName}
              </strong>
              <span className="mt-1 block text-sm text-[var(--brand-muted)]">
                {professional.professionalType} - {professional.status}
              </span>
            </button>
          ))
        )}
      </div>
    </section>
  );
}

export function ProfessionalServiceForm({
  durationMinutes,
  price,
  saving,
  selectedProfessionalId,
  serviceName,
  serviceType,
  onDurationChange,
  onPriceChange,
  onServiceNameChange,
  onServiceTypeChange,
  onSubmit,
}: {
  durationMinutes: number;
  price: string;
  saving: boolean;
  selectedProfessionalId: string;
  serviceName: string;
  serviceType: string;
  onDurationChange: (value: number) => void;
  onPriceChange: (value: string) => void;
  onServiceNameChange: (value: string) => void;
  onServiceTypeChange: (value: string) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <form className="admin-panel" onSubmit={onSubmit}>
      <div className="admin-panel-header">
        <div>
          <p>Catalogo del profesional</p>
          <h2>Agregar servicio</h2>
        </div>
      </div>
      <div className="mt-4 grid gap-3 md:grid-cols-2">
        <Field label="Nombre del servicio">
          <input
            className="app-input"
            onChange={(event) => onServiceNameChange(event.target.value)}
            placeholder="Consulta nutricional"
            value={serviceName}
          />
        </Field>
        <Field label="Tipo">
          <input
            className="app-input"
            onChange={(event) => onServiceTypeChange(event.target.value)}
            placeholder="NUTRITION"
            value={serviceType}
          />
        </Field>
        <Field label="Duracion en minutos">
          <input
            className="app-input"
            min={15}
            onChange={(event) => onDurationChange(Number(event.target.value))}
            type="number"
            value={durationMinutes}
          />
        </Field>
        <Field label="Precio">
          <input
            className="app-input"
            min={0}
            onChange={(event) => onPriceChange(event.target.value)}
            step="0.01"
            type="number"
            value={price}
          />
        </Field>
      </div>
      <button
        className="form-primary-button mt-4"
        disabled={saving || !selectedProfessionalId || !serviceName.trim()}
        type="submit"
      >
        Agregar servicio
      </button>
    </form>
  );
}

export function ProfessionalAvailabilityForm({
  closesAt,
  dayOfWeek,
  opensAt,
  saving,
  selectedProfessionalId,
  onClosesAtChange,
  onDayOfWeekChange,
  onOpensAtChange,
  onSubmit,
}: {
  closesAt: string;
  dayOfWeek: number;
  opensAt: string;
  saving: boolean;
  selectedProfessionalId: string;
  onClosesAtChange: (value: string) => void;
  onDayOfWeekChange: (value: number) => void;
  onOpensAtChange: (value: string) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <form className="admin-panel" onSubmit={onSubmit}>
      <div className="admin-panel-header">
        <div>
          <p>Agenda</p>
          <h2>Agregar disponibilidad</h2>
        </div>
      </div>
      <div className="mt-4 grid gap-3 md:grid-cols-3">
        <Field label="Dia de semana">
          <input
            className="app-input"
            max={7}
            min={1}
            onChange={(event) => onDayOfWeekChange(Number(event.target.value))}
            type="number"
            value={dayOfWeek}
          />
        </Field>
        <Field label="Abre">
          <input className="app-input" onChange={(event) => onOpensAtChange(event.target.value)} type="time" value={opensAt} />
        </Field>
        <Field label="Cierra">
          <input className="app-input" onChange={(event) => onClosesAtChange(event.target.value)} type="time" value={closesAt} />
        </Field>
      </div>
      <button className="form-primary-button mt-4" disabled={saving || !selectedProfessionalId} type="submit">
        Agregar horario
      </button>
    </form>
  );
}

export function WellnessSummaryPanels({
  availability,
  services,
}: {
  availability: ProfessionalAvailability[];
  services: ProfessionalServiceSummary[];
}) {
  return (
    <section className="grid gap-5 md:grid-cols-2">
      <Panel
        title="Servicios registrados"
        items={services.map(
          (service) =>
            `${service.name} - ${formatMoney(service.priceCents, service.currency)} - ${service.durationMinutes} min`,
        )}
      />
      <Panel
        title="Horarios registrados"
        items={availability.map((slot) => `Dia ${slot.dayOfWeek}: ${slot.opensAt} - ${slot.closesAt}`)}
      />
    </section>
  );
}

function Field({ children, label }: { children: ReactNode; label: string }) {
  return (
    <label className="grid gap-2 text-sm font-bold text-[var(--brand-muted)]">
      {label}
      {children}
    </label>
  );
}

function Panel({ title, items }: { title: string; items: string[] }) {
  return (
    <section className="admin-panel">
      <div className="admin-panel-header">
        <div>
          <p>Resumen</p>
          <h2>{title}</h2>
        </div>
      </div>
      <div className="mt-4 grid gap-2 text-sm text-[var(--brand-muted)]">
        {items.length === 0 ? (
          <p>Sin registros.</p>
        ) : (
          items.map((item) => (
            <p className="rounded-lg border border-[var(--brand-line)] bg-white px-3 py-2" key={item}>
              {item}
            </p>
          ))
        )}
      </div>
    </section>
  );
}

function formatMoney(cents: number, currency: string) {
  return new Intl.NumberFormat("es-MX", { style: "currency", currency }).format(cents / 100);
}
