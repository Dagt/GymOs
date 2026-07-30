export const CATALOG_UNIT_OPTIONS = [
  { value: "PIECE", label: "Pieza" },
  { value: "BOTTLE", label: "Botella" },
  { value: "CAN", label: "Lata" },
  { value: "PACKAGE", label: "Paquete" },
  { value: "BOX", label: "Caja" },
  { value: "BAG", label: "Bolsa" },
  { value: "SACHET", label: "Sobre" },
  { value: "SERVING", label: "Porción" },
  { value: "TABLET", label: "Tableta" },
  { value: "CAPSULE", label: "Cápsula" },
  { value: "MILLILITER", label: "Mililitro" },
  { value: "LITER", label: "Litro" },
  { value: "GRAM", label: "Gramo" },
  { value: "KILOGRAM", label: "Kilogramo" },
] as const;

const CATALOG_UNIT_LABELS = new Map<string, string>(
  CATALOG_UNIT_OPTIONS.map((option) => [option.value, option.label]),
);

const LEGACY_UNIT_ALIASES: Record<string, string> = {
  UNIDAD: "PIECE",
  UNIT: "PIECE",
  PIEZA: "PIECE",
  PIEZAS: "PIECE",
  PZA: "PIECE",
  BOTELLA: "BOTTLE",
  LATA: "CAN",
  PAQUETE: "PACKAGE",
  CAJA: "BOX",
  BOLSA: "BAG",
  SOBRE: "SACHET",
  PORCION: "SERVING",
  TABLETA: "TABLET",
  CAPSULA: "CAPSULE",
  ML: "MILLILITER",
  MILILITRO: "MILLILITER",
  L: "LITER",
  LITRO: "LITER",
  G: "GRAM",
  GRAMO: "GRAM",
  KG: "KILOGRAM",
  KILOGRAMO: "KILOGRAM",
};

export function normalizeCatalogUnit(value?: string | null) {
  const normalized = value
    ?.trim()
    .toUpperCase()
    .normalize("NFD")
    .replace(/\p{Diacritic}/gu, "");
  if (!normalized) return null;
  if (CATALOG_UNIT_LABELS.has(normalized)) return normalized;
  return LEGACY_UNIT_ALIASES[normalized] ?? null;
}

export function catalogUnitLabel(value?: string | null) {
  const normalized = normalizeCatalogUnit(value);
  return normalized ? CATALOG_UNIT_LABELS.get(normalized) ?? normalized : "Sin unidad";
}
