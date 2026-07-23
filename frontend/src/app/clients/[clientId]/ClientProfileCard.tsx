import type { ClientDetail } from "@/lib/api";
import {
  formatDay,
  genderLabel,
  statusLabel,
} from "./ClientDetailHelpers";
import { Metric } from "./ClientDetailMetric";

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
