import { ArrowRightLeft, RefreshCw, Ticket, UserPlus } from "lucide-react";

export const membershipOperations = {
  enroll: { icon: UserPlus, title: "Asignar membresía" },
  renew: { icon: RefreshCw, title: "Renovar membresía" },
  change: { icon: ArrowRightLeft, title: "Cambiar plan" },
};

export const operations = {
  ...membershipOperations,
  package: { icon: Ticket, title: "Vender paquete de visitas" },
};

export type ReceptionOperation = keyof typeof operations;

export function ReceptionOperationCard({ operation, selected, disabled, onSelect }: {
  operation: ReceptionOperation;
  selected: boolean;
  disabled: boolean;
  onSelect: () => void;
}) {
  const { icon: Icon, title } = operations[operation];
  return (
    <button
      aria-pressed={selected}
      className={`reception-operation flex min-h-14 items-center justify-center gap-3 rounded-lg border px-3 py-3 text-center text-sm font-semibold transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--brand-primary)] disabled:cursor-not-allowed disabled:opacity-50 ${
        selected ? "border-[var(--brand-primary)] bg-[var(--brand-surface)] text-[var(--brand-primary)]"
          : "border-[var(--brand-line)] text-[var(--brand-ink)] hover:bg-[var(--brand-surface)]"
      }`}
      disabled={disabled}
      onClick={onSelect}
      type="button"
    >
      <Icon aria-hidden="true" className="size-5 shrink-0" />
      <span>{title}</span>
    </button>
  );
}
