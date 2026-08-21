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
      className="relative inline-flex h-7 w-12 shrink-0 items-center rounded-full border transition-[background,border-color,box-shadow] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand-accent)] focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-45"
      disabled={disabled}
      onClick={() => onChange(!checked)}
      role="switch"
      style={{
        borderColor: "color-mix(in srgb, var(--brand-primary) 38%, var(--brand-line))",
        background: checked
          ? "linear-gradient(135deg, color-mix(in srgb, var(--brand-primary) 9%, white), color-mix(in srgb, var(--brand-accent) 22%, white))"
          : "linear-gradient(135deg, white, color-mix(in srgb, var(--brand-primary) 4%, white))",
        boxShadow: checked
          ? "inset 0 0 0 1px color-mix(in srgb, var(--brand-accent) 8%, transparent)"
          : "none",
      }}
      type="button"
    >
      <span
        aria-hidden="true"
        className={`h-5 w-5 rounded-full shadow-sm transition-[transform,background-color,box-shadow] ${checked ? "translate-x-6" : "translate-x-1"}`}
        style={{
          backgroundColor: checked
            ? "var(--brand-primary)"
            : "color-mix(in srgb, var(--brand-primary) 58%, white)",
          boxShadow: checked
            ? "0 1px 2px rgba(0,0,0,.16), 0 0 0 2px color-mix(in srgb, var(--brand-accent) 16%, transparent)"
            : "0 1px 2px rgba(0,0,0,.12)",
        }}
      />
    </button>
  );
}
