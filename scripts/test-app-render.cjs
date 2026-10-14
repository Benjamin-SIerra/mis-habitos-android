const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const { test } = require("node:test");
const availability = require("../app/src/main/assets/availability.js");
const html = fs.readFileSync(
  path.join(__dirname, "../app/src/main/assets/HabitosApp.html"),
  "utf8",
);
const inlineScripts = [...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)]
  .map((match) => match[1])
  .filter(Boolean);

function createNode() {
  return {
    textContent: "",
    innerHTML: "",
    value: "",
    checked: false,
    hidden: false,
    dataset: {},
    style: {},
    classList: {
      add() {},
      remove() {},
      toggle() {},
    },
    addEventListener() {},
    querySelectorAll() { return []; },
    setAttribute() {},
    focus() {},
    reset() {},
  };
}

function renderWithSavedProfile() {
  const now = new Date();
  const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
  const schedule = Array.from({ length: 7 }, () => ({
    work: { start: "09:00", end: "17:00" },
    sleep: { start: "22:00", end: "06:00" },
    other: { start: "", end: "" },
  }));
  const saved = {
    profile: { name: "Prueba", age: 30, exercise: null },
    habits: [],
    schedule,
    activeDates: [today],
    completed: {},
  };
  const nodes = new Map();
  const screens = ["setup", "home", "myHabits", "addHabitScreen", "motivation"]
    .map((id) => Object.assign(createNode(), { id }));
  const document = {
    getElementById(id) {
      if (!nodes.has(id)) nodes.set(id, createNode());
      return nodes.get(id);
    },
    querySelectorAll(selector) {
      return selector === ".screen" ? screens : [];
    },
  };
  const window = {
    HabitosAvailability: availability,
    HabitosAndroid: {
      loadState: () => JSON.stringify(saved),
      saveState() {},
      syncReminders() {},
    },
    scrollTo() {},
  };
  const context = {
    document,
    window,
    localStorage: { getItem() { return null; }, setItem() {}, removeItem() {} },
    console,
  };
  vm.runInNewContext(inlineScripts[0], context, { timeout: 1500 });
  return nodes;
}

test("the full app script renders exact free intervals and a sourced quote", () => {
  assert.equal(inlineScripts.length, 1, "expected one inline application script");
  const nodes = renderWithSavedProfile();
  const ranges = [...nodes.get("todayFreeRanges").innerHTML.matchAll(/class="free-range">([^<]+)</g)]
    .map((match) => match[1]);

  assert.equal(nodes.get("todayRemaining").textContent, "8 h disponibles");
  assert.equal(ranges.length, 2);
  assert.match(ranges[0], /6:00.*9:00/);
  assert.match(ranges[1], /5:00.*10:00/);
  assert.match(nodes.get("homeQuote").textContent, /“.+”/);
  assert.match(nodes.get("homeQuoteSource").textContent, /Epicteto|Marco Aurelio|Séneca/);
});
