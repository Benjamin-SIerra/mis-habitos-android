const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const { calculateAvailability } = require("../app/src/main/assets/availability.js");
const html = fs.readFileSync(
  path.join(__dirname, "../app/src/main/assets/HabitosApp.html"),
  "utf8",
);

test("empty schedule exposes the full day as one explicit free range", () => {
  const result = calculateAvailability([]);
  assert.deepEqual(result.freeRanges, [[0, 1440]]);
  assert.equal(result.freeMinutes, 1440);
  assert.equal(result.overlap, false);
});

test("work hours produce the exact free ranges before and after work", () => {
  const result = calculateAvailability([[9 * 60, 17 * 60]]);
  assert.deepEqual(result.freeRanges, [[0, 9 * 60], [17 * 60, 1440]]);
  assert.equal(result.freeMinutes, 16 * 60);
});

test("overlapping commitments are merged and flagged without double counting", () => {
  const result = calculateAvailability([
    [9 * 60, 17 * 60],
    [15 * 60, 16 * 60],
  ]);
  assert.deepEqual(result.busyRanges, [[9 * 60, 17 * 60]]);
  assert.deepEqual(result.freeRanges, [[0, 9 * 60], [17 * 60, 1440]]);
  assert.equal(result.overlap, true);
  assert.equal(result.freeMinutes, 16 * 60);
});

test("overnight commitments represented at day edges leave the middle range free", () => {
  const result = calculateAvailability([[0, 6 * 60], [23 * 60, 1440]]);
  assert.deepEqual(result.freeRanges, [[6 * 60, 23 * 60]]);
  assert.equal(result.overlap, false);
});

test("touching intervals are not conflicts and invalid ranges are ignored", () => {
  const result = calculateAvailability([
    [-20, 60],
    [60, 120],
    [120, 120],
    [1380, 1500],
  ]);
  assert.deepEqual(result.busyRanges, [[0, 120], [1380, 1440]]);
  assert.deepEqual(result.freeRanges, [[120, 1380]]);
  assert.equal(result.overlap, false);
});

test("home and weekly schedule show explicit free-range containers", () => {
  assert.match(html, /id="todayFreeRanges"/);
  assert.match(html, /id="dayFreeRanges"/);
  assert.match(html, /function rangeLabel\(/);
  assert.match(html, /function renderFreeRanges\(/);
});

test("the schedule has no commute fields or commute calculations", () => {
  assert.match(html, /const blockNames = \["work", "sleep", "other"\]/);
  assert.doesNotMatch(html, /commute|trayecto|traslado|tráfico/i);
});

test("daily philosopher quotes include attribution and are rendered on both screens", () => {
  assert.match(html, /author: "Epicteto"/);
  assert.match(html, /author: "Marco Aurelio"/);
  assert.match(html, /author: "Séneca"/);
  assert.match(html, /id="homeQuote"/);
  assert.match(html, /id="motivationQuote"/);
  assert.match(html, /function renderDailyQuote\(/);
});
