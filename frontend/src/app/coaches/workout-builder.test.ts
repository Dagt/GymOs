import assert from "node:assert/strict";
import test from "node:test";
import {
  detailedSetPlan,
  emptyBlock,
  emptyExercise,
  emptySetSegment,
  emptySession,
  moveItem,
  updateExercise,
  validateWorkout,
} from "./workout-builder.ts";

test("builder supports multiple ordered sessions and blocks", () => {
  const sessions = [
    emptySession(0),
    { ...emptySession(1), blocks: [emptyBlock("Calentamiento", "WARMUP"), emptyBlock()] },
  ];

  const moved = moveItem(sessions, 1, -1);

  assert.equal(moved[0].name, "Día 2");
  assert.equal(moved[0].blocks.length, 2);
  assert.equal(moved[0].blocks[0].type, "WARMUP");
});

test("exercise prescription fields are updated without losing block structure", () => {
  const sessions = [{
    ...emptySession(0),
    blocks: [emptyBlock("A"), emptyBlock("B")],
  }];

  const updated = updateExercise(sessions, 0, 1, 0, {
    exerciseName: "Sentadilla libre",
    sets: 4,
    reps: "8-10",
    load: "RPE 8",
    restSeconds: 90,
    tempo: "3-1-1",
    groupKey: "B1",
  });

  assert.equal(updated[0].blocks[0].name, "A");
  assert.deepEqual(updated[0].blocks[1].exercises[0], {
    exerciseName: "Sentadilla libre",
    prescription: "",
    optional: false,
    sets: 4,
    reps: "8-10",
    load: "RPE 8",
    restSeconds: 90,
    tempo: "3-1-1",
    groupKey: "B1",
  });
});

test("detailed set plans support progressive series and drop-set segments", () => {
  const exercise = {
    ...emptyExercise(),
    sets: 3,
    reps: "12",
    load: "20 kg",
  };

  const plan = detailedSetPlan(exercise);
  plan[0] = { segments: [{ reps: "20", load: "20 kg" }] };
  plan[1] = { segments: [{ reps: "15", load: "25 kg" }] };
  plan[2] = {
    segments: [
      { reps: "10", load: "30 kg" },
      emptySetSegment("20", "20 kg"),
    ],
  };

  assert.equal(plan.length, 3);
  assert.deepEqual(plan[2].segments, [
    { reps: "10", load: "30 kg" },
    { reps: "20", load: "20 kg" },
  ]);
});

test("workout validation rejects incomplete nested content", () => {
  assert.equal(validateWorkout("", [emptySession(0)]), "Escribe un título para la rutina.");
  assert.equal(validateWorkout("Fuerza", [emptySession(0)]), "Cada bloque y ejercicio necesita nombre.");

  const valid = [emptySession(0)];
  valid[0].blocks[0].exercises[0].exerciseName = "Peso muerto";
  assert.equal(validateWorkout("Fuerza", valid), null);
});
