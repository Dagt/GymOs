import { ArrowRightLeft, RefreshCw, Ticket, UserPlus } from "lucide-react";
import type { ReactNode } from "react";

export const membershipOperations = {
  enroll: { icon: UserPlus, title: "Asignar membresía", description: "Contrata un plan para un socio y registra su pago." },
  renew: { icon: RefreshCw, title: "Renovar membresía", description: "Extiende la vigencia de una membresía existente." },
  change: { icon: ArrowRightLeft, title: "Cambiar plan", description: "Consulta las opciones disponibles y cambia el plan del socio." },
};

export const packageOperation = {
  icon: Ticket,
  title: "Vender paquete de visitas",
  description: "Contrata paquetes de accesos consumibles y registra el pago.",
};

export function ReceptionOperationCard({ title, description, icon: Icon, selected, disabled, onSelect, children }: {
  title: string;
  description: string;
  icon: typeof Ticket;
  selected?: boolean;
  disabled?: boolean;
  onSelect?: () => void;
  children?: ReactNode;
}) {
  return (
    <article className={`app-card flex min-w-0 flex-col items-start gap-2 p-4 ${selected ? "ring-2 ring-[var(--brand-primary)]" : ""}`}>
      <Icon aria-hidden="true" className="size-5 text-[var(--brand-primary)]" />
      <h3 className="text-sm font-semibold text-[var(--brand-ink)]">{title}</h3>
      <p className="text-sm text-[var(--brand-muted)]">{description}</p>
      {children}
      {onSelect ? (
        <button
          aria-label={title}
          aria-pressed={selected}
          className="app-button-secondary mt-auto disabled:cursor-not-allowed disabled:opacity-50"
          disabled={disabled || selected}
          onClick={onSelect}
          type="button"
        >
          {selected ? "Seleccionada" : disabled ? "No disponible" : "Seleccionar"}
        </button>
      ) : null}
    </article>
  );
}
