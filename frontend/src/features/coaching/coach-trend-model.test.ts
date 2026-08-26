import assert from "node:assert/strict";
import test from "node:test";
import { businessDateLabel, coachKey, coachTimeline } from "./coach-trend-model.ts";

const activity = [
  { date: "2026-08-24", coachId: "ana", coachName: "Ana", assignments: 2, firstWorkouts: 1, uniqueClients: 1 },
  { date: "2026-08-26", coachId: "ana", coachName: "Ana", assignments: 3, firstWorkouts: 0, uniqueClients: 2 },
  { date: "2026-08-25", coachId: "luis", coachName: "Luis", assignments: 1, firstWorkouts: 1, uniqueClients: 1 },
];

test("coach series use dates, include zero-activity days and keep coaches independent", () => {
  const rows = coachTimeline("2026-08-24", "2026-08-27", activity, false);
  assert.deepEqual(rows.map((row) => row.date), ["2026-08-24", "2026-08-25", "2026-08-26", "2026-08-27"]);
  assert.deepEqual(rows.map((row) => row[coachKey("ana")]), [2, 0, 3, 0]);
  assert.deepEqual(rows.map((row) => row[coachKey("luis")]), [0, 1, 0, 0]);
});

test("cumulative series accumulate assignments only inside the selected period", () => {
  assert.deepEqual(coachTimeline("2026-08-24", "2026-08-27", activity, true).map((row) => row[coachKey("ana")]), [2, 2, 5, 5]);
  assert.deepEqual(coachTimeline("2026-08-25", "2026-08-27", activity, true).map((row) => row[coachKey("ana")]), [0, 3, 3]);
});

test("an active coach without assignments has an honest zero series", () => {
  const rows = coachTimeline("2026-08-24", "2026-08-27", activity, true, [coachKey("maria")]);
  assert.deepEqual(rows.map((row) => row[coachKey("maria")]), [0, 0, 0, 0]);
});

test("calendar labels do not shift Monday back to Sunday in negative UTC offsets", () => {
  assert.match(businessDateLabel("2026-08-24"), /24/);
  assert.deepEqual(coachTimeline("2026-08-27", "2026-08-24", activity, true), []);
  assert.deepEqual(coachTimeline("invalid", "2026-08-24", activity, true), []);
});
