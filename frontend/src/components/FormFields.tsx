import type { ReactNode } from "react";
export {
  normalizePhone,
  normalizeRfc,
  type FieldErrorMap,
  validateBranchCode,
  validateEmail,
  validateGender,
  validateHexColor,
  validateIsoDate,
  validatePassword,
  validatePhone,
  validateRfc,
  validateRequired,
} from "./form-validation";

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
    <FieldFrame error={props.error} help={props.help ?? "Se guardara como +52 seguido de 10 digitos."} label={props.label} name={props.name} required={props.required ?? false}>
      <input
        aria-invalid={Boolean(props.error)}
        className="app-input"
        id={props.name}
        inputMode="tel"
        name={props.name}
        onChange={(event) => props.onChange(event.target.value)}
        placeholder={props.placeholder ?? "+524921234567"}
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

export function NumberField(props: BaseProps & { min?: string; step?: string }) {
  return (
    <FieldFrame error={props.error} help={props.help} label={props.label} name={props.name} required={props.required ?? true}>
      <input
        aria-invalid={Boolean(props.error)}
        className="app-input"
        id={props.name}
        min={props.min ?? "0"}
        name={props.name}
        onChange={(event) => props.onChange(event.target.value)}
        required={props.required ?? true}
        step={props.step ?? "1"}
        type="number"
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

export function TextAreaField(props: BaseProps & { rows?: number }) {
  return (
    <FieldFrame error={props.error} help={props.help} label={props.label} name={props.name} required={props.required ?? false}>
      <textarea
        aria-invalid={Boolean(props.error)}
        className="app-input min-h-20"
        id={props.name}
        name={props.name}
        onChange={(event) => props.onChange(event.target.value)}
        placeholder={props.placeholder}
        required={props.required ?? false}
        rows={props.rows}
        value={props.value}
      />
    </FieldFrame>
  );
}

export function ColorField(props: BaseProps) {
  return (
    <FieldFrame error={props.error} help={props.help} label={props.label} name={props.name} required={props.required ?? true}>
      <input
        aria-invalid={Boolean(props.error)}
        className="app-input h-12 p-1"
        id={props.name}
        name={props.name}
        onChange={(event) => props.onChange(event.target.value)}
        required={props.required ?? true}
        type="color"
        value={props.value}
      />
    </FieldFrame>
  );
}

export function DateField(props: BaseProps) {
  return (
    <FieldFrame error={props.error} help={props.help ?? "Formato: dd/mm/aaaa."} label={props.label} name={props.name} required={props.required ?? false}>
      <input
        aria-invalid={Boolean(props.error)}
        className="app-input"
        id={props.name}
        inputMode="numeric"
        name={props.name}
        onChange={(event) => props.onChange(dateDisplayToIso(event.target.value))}
        placeholder={props.placeholder ?? "31/12/1990"}
        required={props.required ?? false}
        value={isoDateToDisplay(props.value)}
      />
    </FieldFrame>
  );
}

export function GenderField(props: Omit<BaseProps, "placeholder">) {
  return (
    <SelectField
      {...props}
      options={[
        { label: "Selecciona genero", value: "" },
        { label: "Hombre", value: "MALE" },
        { label: "Mujer", value: "FEMALE" },
      ]}
      required={props.required ?? false}
    />
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

function isoDateToDisplay(value: string) {
  if (!value) {
    return "";
  }
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!match) {
    return value;
  }
  return `${match[3]}/${match[2]}/${match[1]}`;
}

function dateDisplayToIso(value: string) {
  const digits = value.replace(/\D/g, "").slice(0, 8);
  if (digits.length < 8) {
    return value;
  }
  const day = digits.slice(0, 2);
  const month = digits.slice(2, 4);
  const year = digits.slice(4, 8);
  return `${year}-${month}-${day}`;
}
