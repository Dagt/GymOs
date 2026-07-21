import assert from "node:assert/strict";
import test from "node:test";
import {
  addMinutes,
  currentTimeInput,
  formatMexicanDateInput,
  isPastDate,
  isPastDateTime,
  isValidTimeInput,
  normalizeTimeInput,
  parseMexicanDate,
  toDateInput,
} from "./date-time.ts";

test("formats local dates without converting them to UTC", () => {
  assert.equal(toDateInput(new Date(2026, 6, 20, 23, 30)), "2026-07-20");
});

test("normalizes time values with optional seconds", () => {
  assert.equal(normalizeTimeInput("06:30:00"), "06:30");
  assert.equal(normalizeTimeInput("22:15"), "22:15");
  assert.equal(isValidTimeInput("23:59"), true);
  assert.equal(isValidTimeInput("24:00"), false);
});

test("adds minutes preserving the local date-time representation", () => {
  assert.equal(addMinutes("2026-07-20T23:45", 30), "2026-07-21T00:15");
  assert.equal(addMinutes("invalid", 30), "");
});

test("distinguishes a past day from the current day", () => {
  const now = new Date(2026, 6, 20, 17, 0);
  assert.equal(isPastDate("2026-07-19", now), true);
  assert.equal(isPastDate("2026-07-20", now), false);
  assert.equal(isPastDate("2026-07-21", now), false);
});

test("rejects a time that has already passed on the current day", () => {
  const now = new Date(2026, 6, 20, 17, 0);
  assert.equal(isPastDateTime("2026-07-20", "16:55", now), true);
  assert.equal(isPastDateTime("2026-07-20", "17:00", now), true);
  assert.equal(isPastDateTime("2026-07-20", "17:05", now), false);
});

test("rounds the minimum booking time to the next five-minute boundary", () => {
  assert.equal(currentTimeInput(new Date(2026, 6, 20, 17, 2, 35)), "17:05");
  assert.equal(currentTimeInput(new Date(2026, 6, 20, 17, 5, 0)), "17:05");
});

test("formats and parses Mexican birth dates", () => {
  assert.equal(formatMexicanDateInput("25081990"), "25/08/1990");
  assert.equal(formatMexicanDateInput("25-08-1990"), "25/08/1990");
  assert.equal(parseMexicanDate("25/08/1990", 2026), "1990-08-25");
});

test("rejects impossible, incomplete and future Mexican dates", () => {
  assert.equal(parseMexicanDate("31/02/1990", 2026), null);
  assert.equal(parseMexicanDate("1/2/1990", 2026), null);
  assert.equal(parseMexicanDate("25/08/2027", 2026), null);
  assert.equal(parseMexicanDate("25/08/1899", 2026), null);
});
