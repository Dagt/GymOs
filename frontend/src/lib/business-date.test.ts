import assert from "node:assert/strict";
import test from "node:test";
import { businessToday, businessWeekStart, assignmentDateError } from "./business-date.ts";

test("late Mexico evenings do not become tomorrow just because UTC crossed midnight", () => {
  assert.equal(businessToday(new Date("2026-08-26T02:00:00Z")), "2026-08-25");
  assert.equal(businessToday(new Date("2026-08-26T06:00:00Z")), "2026-08-26");
  assert.equal(businessWeekStart(new Date("2026-08-24T02:00:00Z")), "2026-08-17");
  assert.equal(businessWeekStart(new Date("2026-08-24T12:00:00Z")), "2026-08-24");
});

test("assignment dates are mandatory, not past, ordered, and inclusive", () => {
  assert.ok(assignmentDateError("", "", "2026-08-25"));
  assert.ok(assignmentDateError("2026-08-24", "2026-08-26", "2026-08-25"));
  assert.ok(assignmentDateError("2026-08-27", "2026-08-26", "2026-08-25"));
  assert.equal(assignmentDateError("2026-08-25", "2026-08-25", "2026-08-25"), null);
  assert.equal(assignmentDateError("2026-09-01", "2026-09-28", "2026-08-25"), null);
});
