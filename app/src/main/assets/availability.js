(function (root, factory) {
  const api = factory();
  if (typeof module !== "undefined" && module.exports) {
    module.exports = api;
  } else {
    root.HabitosAvailability = api;
  }
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  function calculateAvailability(intervals, dayMinutes = 1440) {
    const limit = Number.isFinite(dayMinutes) && dayMinutes > 0 ? dayMinutes : 1440;
    const valid = (Array.isArray(intervals) ? intervals : [])
      .filter((range) => Array.isArray(range) && range.length >= 2)
      .map(([start, end]) => [Number(start), Number(end)])
      .filter(([start, end]) => Number.isFinite(start) && Number.isFinite(end))
      .map(([start, end]) => [Math.max(0, start), Math.min(limit, end)])
      .filter(([start, end]) => end > start)
      .sort((left, right) => left[0] - right[0] || left[1] - right[1]);

    let overlap = false;
    for (let i = 0; i < valid.length; i++) {
      for (let j = i + 1; j < valid.length && valid[j][0] < valid[i][1]; j++) {
        if (valid[i][0] < valid[j][1]) overlap = true;
      }
    }

    const busyRanges = [];
    for (const [start, end] of valid) {
      const previous = busyRanges[busyRanges.length - 1];
      if (previous && start <= previous[1]) previous[1] = Math.max(previous[1], end);
      else busyRanges.push([start, end]);
    }

    const freeRanges = [];
    let cursor = 0;
    for (const [start, end] of busyRanges) {
      if (start > cursor) freeRanges.push([cursor, start]);
      cursor = Math.max(cursor, end);
    }
    if (cursor < limit) freeRanges.push([cursor, limit]);

    return {
      busyRanges,
      freeRanges,
      freeMinutes: freeRanges.reduce((total, [start, end]) => total + end - start, 0),
      overlap,
    };
  }

  return { calculateAvailability };
});
