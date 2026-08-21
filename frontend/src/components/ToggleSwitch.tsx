type ToggleSwitchProps = {
  checked: boolean;
  disabled?: boolean;
  label: string;
  onChange: (checked: boolean) => void;
};

export function ToggleSwitch({ checked, disabled = false, label, onChange }: ToggleSwitchProps) {
  return (
    <button
      aria-checked={checked}
      aria-label={label}
      className="relative inline-flex h-7 w-12 shrink-0 items-center rounded-full border border-[var(--brand-primary)] transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand-accent)] focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-45"
      disabled={disabled}
      onClick={() => onChange(!checked)}
      role="switch"
      style={{
        backgroundColor: checked
          ? "color-mix(in srgb, var(--brand-accent) 48%, white)"
          : "white",
      }}
      type="button"
    >
      <span
        aria-hidden="true"
        className={`h-5 w-5 rounded-full bg-[var(--brand-primary)] shadow-sm transition-transform ${checked ? "translate-x-6" : "translate-x-1"}`}
      />
    </button>
  );
}
