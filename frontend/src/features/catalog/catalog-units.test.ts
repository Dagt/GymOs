import assert from "node:assert/strict";
import test from "node:test";
import {
  CATALOG_UNIT_OPTIONS,
  catalogUnitLabel,
  normalizeCatalogUnit,
} from "./catalog-units.ts";

test("catalog units expose a closed set of unique common options", () => {
  const values = CATALOG_UNIT_OPTIONS.map((option) => option.value);
  assert.equal(new Set(values).size, values.length);
  assert.ok(values.includes("PIECE"));
  assert.ok(values.includes("BOTTLE"));
  assert.ok(values.includes("LITER"));
  assert.ok(values.includes("KILOGRAM"));
});

test("legacy unit labels normalize to canonical catalog values", () => {
  assert.equal(normalizeCatalogUnit("Pieza"), "PIECE");
  assert.equal(normalizeCatalogUnit("ml"), "MILLILITER");
  assert.equal(normalizeCatalogUnit("kg"), "KILOGRAM");
  assert.equal(normalizeCatalogUnit("cubeta personalizada"), null);
  assert.equal(catalogUnitLabel("BOTTLE"), "Botella");
});
