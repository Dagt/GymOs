export type FieldErrorMap = Record<string, string>;

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
  return /^\+52\d{10}$/.test(normalized) ? "" : "Usa +52 seguido de 10 digitos.";
}

export function validateRfc(value: string, required = false) {
  if (!value.trim()) {
    return required ? "RFC es obligatorio." : "";
  }
  return /^[A-ZÑ&]{4}\d{6}[A-Z0-9]{3}$/.test(normalizeRfc(value))
    ? ""
    : "El RFC debe tener 4 letras, 6 numeros y 3 caracteres alfanumericos.";
}

export function validatePassword(value: string) {
  return value.length >= 8 ? "" : "La contrasena temporal debe tener al menos 8 caracteres.";
}

export function validateIsoDate(value: string, label: string, required = false) {
  if (!value.trim()) {
    return required ? `${label} es obligatorio.` : "";
  }
  return /^\d{4}-\d{2}-\d{2}$/.test(value) ? "" : `${label} debe tener formato dd/mm/aaaa.`;
}

export function validateGender(value: string, required = false) {
  if (!value.trim()) {
    return required ? "Genero es obligatorio." : "";
  }
  return value === "MALE" || value === "FEMALE" ? "" : "Selecciona Hombre o Mujer.";
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
  const digits = value.replace(/\D/g, "");
  if (!digits) {
    return "";
  }
  if (digits.length === 10) {
    return `+52${digits}`;
  }
  if (digits.length === 12 && digits.startsWith("52")) {
    return `+${digits}`;
  }
  return value.trim().startsWith("+") ? `+${digits}` : digits;
}

export function normalizeRfc(value: string) {
  return value.replace(/[^A-Za-z0-9Ññ&]/g, "").toUpperCase();
}
