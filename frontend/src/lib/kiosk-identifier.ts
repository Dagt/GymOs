export function normalizeKioskIdentifier(value: string) {
  const trimmed = value.trim();
  if (trimmed.includes("@")) {
    return trimmed.toLowerCase();
  }

  const digits = trimmed.replace(/\D/g, "");
  if (digits.length === 10) {
    return `+52${digits}`;
  }
  if (digits.length === 12 && digits.startsWith("52")) {
    return `+${digits}`;
  }
  if (digits.length === 13 && digits.startsWith("521")) {
    return `+52${digits.slice(3)}`;
  }
  return trimmed.startsWith("+") && digits ? `+${digits}` : digits || trimmed.toLowerCase();
}
