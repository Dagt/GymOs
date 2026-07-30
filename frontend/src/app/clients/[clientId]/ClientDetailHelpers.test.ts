import test from "node:test";
import assert from "node:assert/strict";
import { formatDate, formatDay } from "./client-date-helpers.ts";

test("date formatting does not crash an initial membership without dates", () => {
  assert.equal(formatDay(""), "Sin fecha");
  assert.equal(formatDay("not-a-date"), "Sin fecha");
  assert.equal(formatDate("not-a-date"), "Sin fecha");
});
