import type { ReactNode } from "react";

export type FieldErrorMap = Record<string, string>;

type BaseProps = {
  error?: string;
  help?: string;
  label: string;
  name: string;
  onChange: (value: string) => void;
  placeholder?: string;
  required?: boolean;
  value: string;
};

export function TextField({ error, help, label, name, onChange, placeholder, required = true, value }: BaseProps) {
  return (
    <FieldFrame error={error} help={help} label={label} name={name} required={required}>
      <input
        aria-invalid={Boolean(error)}
        className="app-input"
        id={name}
        name={name}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        required={required}
        value={value}
      />
    </FieldFrame>
  );
}

export function EmailField(props: BaseProps) {
  return (
    <FieldFrame error={props.error} help={props.help} label={props.label} name={props.name} required={props.required ?? false}>
      <input
        aria-invalid={Boolean(props.error)}
        className="app-input"
        id={props.name}
        name={props.name}
        onChange={(event) => props.onChange(event.target.value)}
        placeholder={props.placeholder ?? "correo@ejemplo.com"}
        required={props.required ?? false}
        type="email"
        value={props.value}
      />
    </FieldFrame>
  );
}

export function PhoneField(props: BaseProps) {
  return (
    <FieldFrame error={props.error} help={props.help ?? "10 a 15 digitos; puedes incluir +52."} label={props.label} name={props.name} required={props.required ?? false}>
      <input
        aria-invalid={Boolean(props.error)}
        className="app-input"
        id={props.name}
        inputMode="tel"
        name={props.name}
        onChange={(event) => props.onChange(event.target.value)}
        placeholder={props.placeholder ?? "+524931234567"}
        required={props.required ?? false}
        value={props.value}
      />
    </FieldFrame>
  );
}

export function PasswordField(props: BaseProps) {
  return (
    <FieldFrame error={props.error} help={props.help ?? "Minimo 8 caracteres."} label={props.label} name={props.name} required={props.required ?? true}>
      <input
        aria-invalid={Boolean(props.error)}
        autoComplete="new-password"
        className="app-input"
        id={props.name}
        name={props.name}
        onChange={(event) => props.onChange(event.target.value)}
        required={props.required ?? true}
        type="password"
        value={props.value}
      />
    </FieldFrame>
  );
}

export function SelectField({
  error,
  label,
  name,
  onChange,
  options,
  required = true,
  value,
}: BaseProps & { options: { label: string; value: string }[] }) {
  return (
    <FieldFrame error={error} label={label} name={name} required={required}>
      <select
        aria-invalid={Boolean(error)}
        className="app-input"
        id={name}
        name={name}
        onChange={(event) => onChange(event.target.value)}
        required={required}
        value={value}
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </FieldFrame>
  );
}

export function FormError({ message }: { message: string }) {
  const [main, details] = splitDetails(message);
  return (
    <section className="form-error-box" role="alert">
      <strong>{main}</strong>
      {details ? <details><summary>Ver detalle tecnico</summary><span>{details}</span></details> : null}
    </section>
  );
}

function FieldFrame({
  children,
  error,
  help,
  label,
  name,
  required,
}: {
  children: ReactNode;
  error?: string;
  help?: string;
  label: string;
  name: string;
  required: boolean;
}) {
  return (
    <label className="form-field" htmlFor={name}>
      <span>
        {label}
        {required ? <b>*</b> : null}
      </span>
      {children}
      {error ? <small className="form-field-error">{error}</small> : help ? <small>{help}</small> : null}
    </label>
  );
}

function splitDetails(message: string) {
  const index = message.indexOf(" (");
  if (index < 0) {
    return [message, ""] as const;
  }
  return [message.slice(0, index), message.slice(index + 1)] as const;
}

export function validateRequired(value: string, label: string) {
  return value.trim() ? "" : `${label} es obligatorio.`;
}

export function validateEmail(value: string, required = false) {
  if (!value.trim()) {
    return required ? "Email es obligatorio." : "";
  }
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim()) ? "" : "Escribe un email valido.";
}

export function validatePhone(value: string, required = false) {
  if (!value.trim()) {
    return required ? "Telefono es obligatorio." : "";
  }
  const normalized = normalizePhone(value);
  return /^\+?\d{10,15}$/.test(normalized) ? "" : "Usa un telefono de 10 a 15 digitos.";
}

export function validatePassword(value: string) {
  return value.length >= 8 ? "" : "La contrasena temporal debe tener al menos 8 caracteres.";
}

export function validateBranchCode(value: string) {
  if (!value.trim()) {
    return "Codigo de sucursal es obligatorio.";
  }
  return /^[A-Za-z0-9-]{2,32}$/.test(value.trim()) ? "" : "Usa 2 a 32 caracteres: letras, numeros o guion.";
}

export function validateHexColor(value: string, label: string, required = false) {
  if (!value.trim()) {
    return required ? `${label} es obligatorio.` : "";
  }
  return /^#[0-9A-Fa-f]{6}$/.test(value.trim()) ? "" : `${label} debe tener formato #RRGGBB.`;
}

export function normalizePhone(value: string) {
  const trimmed = value.trim();
  const prefix = trimmed.startsWith("+") ? "+" : "";
  return `${prefix}${trimmed.replace(/\D/g, "")}`;
}
