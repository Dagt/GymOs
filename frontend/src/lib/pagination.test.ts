import assert from "node:assert/strict";
import test from "node:test";
import { lastPage, pageNumber, pageRange, pageSize } from "./pagination.ts";

test("pagination reports the complete total and the partial final page", () => {
  assert.deepEqual(pageRange(0, 25, 127), { from: 1, to: 25, pages: 6 });
  assert.deepEqual(pageRange(5, 25, 127), { from: 126, to: 127, pages: 6 });
  assert.deepEqual(pageRange(0, 25, 0), { from: 0, to: 0, pages: 1 });
});

test("a filtered or deleted final page can be brought back into range", () => {
  assert.equal(lastPage(100, 25), 3);
  assert.equal(lastPage(1, 25), 0);
  assert.equal(lastPage(0, 25), 0);
  assert.deepEqual(pageRange(5, 25, 1), { from: 0, to: 0, pages: 1 });
});

test("URL pagination uses bounded integer values", () => {
  for (const value of ["-1", "1.5", "NaN", "Infinity", "1e20"]) assert.equal(pageNumber(value), 0);
  assert.equal(pageNumber("2"), 2);
  assert.equal(pageSize(null), 25);
  assert.equal(pageSize("0"), 1);
  assert.equal(pageSize("10000"), 100);
});
