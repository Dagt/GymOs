import test from "node:test";
import assert from "node:assert/strict";
import {
  buildMonthDates,
  dateToWeekday,
  friendlyBookingError,
  minutesToTime,
  parseResourceSchedule,
  resourceHoursForDay,
  timeToMinutes,
} from "./bookings.ts";

test("buildMonthDates creates a Monday-first six-week grid", () => {
  const dates = buildMonthDates("2026-07-15");
  assert.equal(dates.length, 42);
  assert.equal(dates[0].date, "2026-06-29");
  assert.equal(dates[41].date, "2026-08-09");
  assert.equal(dates.find((day) => day.date === "2026-07-15")?.inCurrentMonth, true);
});

test("dateToWeekday uses the business weekday names", () => {
  assert.equal(dateToWeekday("2026-07-20"), "MONDAY");
  assert.equal(dateToWeekday("2026-07-26"), "SUNDAY");
});

test("parseResourceSchedule accepts valid entries and rejects malformed notes", () => {
  const notes = JSON.stringify({
    schedule: [
      { day: "MONDAY", opensAt: "06:00", closesAt: "22:00" },
      { day: "INVALID", opensAt: "06:00", closesAt: "22:00" },
    ],
  });

  assert.deepEqual(parseResourceSchedule(notes), [
    { day: "MONDAY", opensAt: "06:00", closesAt: "22:00" },
  ]);
  assert.equal(parseResourceSchedule("not-json"), null);
  assert.equal(parseResourceSchedule(JSON.stringify({ schedule: [] })), null);
});

test("resourceHoursForDay distinguishes closed scheduled days from legacy defaults", () => {
  const scheduled = {
    notes: JSON.stringify({ schedule: [{ day: "MONDAY", opensAt: "07:00:00", closesAt: "21:00:00" }] }),
    opensAt: "06:00",
    closesAt: "22:00",
  };

  assert.deepEqual(resourceHoursForDay(scheduled, "MONDAY"), { opensAt: "07:00", closesAt: "21:00" });
  assert.equal(resourceHoursForDay(scheduled, "TUESDAY"), null);
  assert.deepEqual(resourceHoursForDay({ notes: null, opensAt: "08:00:00", closesAt: "18:00:00" }, "MONDAY"), {
    opensAt: "08:00",
    closesAt: "18:00",
  });
});

test("time conversion helpers remain inverse for minute precision", () => {
  assert.equal(timeToMinutes("06:30"), 390);
  assert.equal(minutesToTime(390), "06:30");
});

test("friendlyBookingError maps known business codes and sanitizes unknown messages", () => {
  assert.match(friendlyBookingError("BOOKING_CLIENT_CONFLICT", "fallback"), /otra reserva/);
  assert.match(friendlyBookingError("BOOKING_PLAN_NOT_ALLOWED", "fallback"), /membresía válida/);
  assert.equal(friendlyBookingError("[INTERNAL_ERROR] Something useful", "fallback"), "Something useful");
  assert.equal(friendlyBookingError("X".repeat(200), "fallback"), "fallback");
});
