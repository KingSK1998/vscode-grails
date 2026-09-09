const { deepEqual, equal, match, throws } = require("node:assert/strict");
const { test } = require("node:test");
const { validate, select } = require("./roadmap");

const SPEC_PATH = "docs/execution/task-specifications.md";
const ROADMAP_PATH = "docs/product-roadmap.md";

function task(id, overrides = {}) {
  return {
    id, title: `Task ${id}`, status: "queued", dependsOn: [],
    spec: `${SPEC_PATH}#${id.toLowerCase()}`, record: null, owner: null,
    ...overrides
  };
}

function fixture(tasks) {
  const files = {
    [SPEC_PATH]: tasks.map(item => `## ${item.id}\n\nAcceptance criteria.`).join("\n\n"),
    [ROADMAP_PATH]: tasks.map(item => `| ${item.id} | ${item.title} |`).join("\n")
  };
  for (const item of tasks) {
    if (item.record) files[item.record] = "# Task record\n\nAcceptance: NOT RUN\n";
  }
  return { queue: { schemaVersion: 1, tasks }, sources: { files } };
}

function failure(input, expected) {
  const result = validate(input.queue, input.sources);
  equal(result.valid, false);
  match(result.errors.join("\n"), expected);
}

test("initial queue selects R0-01 and does not mutate task status", () => {
  const input = fixture([
    task("R0-01", { record: "docs/execution/records/R0-01.md" }),
    task("R0-02", { dependsOn: ["R0-01"] }),
    task("R6-01", { status: "deferred" })
  ]);
  const before = JSON.stringify(input);
  deepEqual(validate(input.queue, input.sources), { valid: true, errors: [] });
  equal(select(input.queue.tasks).task.id, "R0-01");
  equal(select(input.queue.tasks).mode, "start");
  equal(JSON.stringify(input), before);
});

test("validation rejects missing prerequisites and dependency cycles", () => {
  failure(fixture([task("R0-01", { dependsOn: ["R0-99"] })]), /missing prerequisite R0-99/);
  failure(fixture([
    task("R0-01", { dependsOn: ["R0-02"] }),
    task("R0-02", { dependsOn: ["R0-01"] })
  ]), /Dependency cycle: R0-01 -> R0-02 -> R0-01/);
  failure(fixture([task("R0-01", { dependsOn: ["R0-01"] })]), /Dependency cycle/);
});

test("validation rejects malformed schemas, IDs, statuses, and duplicate dependencies", () => {
  const input = fixture([task("R0-01")]);
  input.queue.schemaVersion = 2;
  failure(input, /schemaVersion: 1/);
  failure({ queue: { schemaVersion: 1, tasks: null } }, /nonempty array/);
  failure(fixture([task("R0-01", { status: "finished" })]), /invalid status finished/);
  failure(fixture([task("R0-01"), task("R0-01")]), /duplicate task ID/);
  failure(fixture([task("bad")]), /invalid task ID/);
  failure(fixture([task("R0-01", { dependsOn: ["R0-02", "R0-02"] }), task("R0-02")]), /duplicate prerequisite/);
});

test("done requires an existing record with real acceptance evidence", () => {
  failure(fixture([task("R0-01", { status: "done" })]), /requires an existing record/);
  const input = fixture([task("R0-01", { status: "done", record: "docs/execution/records/R0-01.md" })]);
  failure(input, /done requires standalone Acceptance: PASS/);
  input.sources.files[input.queue.tasks[0].record] = "```text\nAcceptance: PASS\n```\n";
  failure(input, /outside code fences/);
  input.sources.files[input.queue.tasks[0].record] = "# Accepted\n\nAcceptance: PASS\n";
  equal(validate(input.queue, input.sources).valid, true);
  delete input.sources.files[input.queue.tasks[0].record];
  failure(input, /file is missing/);
});

test("done cannot bypass an unfinished prerequisite even with a PASS record", () => {
  const input = fixture([
    task("R0-01"),
    task("R0-02", { status: "done", dependsOn: ["R0-01"], record: "docs/execution/records/R0-02.md" })
  ]);
  input.sources.files[input.queue.tasks[1].record] = "Acceptance: PASS\n";
  failure(input, /done requires prerequisite R0-01 to be done/);
});

test("active tasks resume in queue order before new work", () => {
  const input = fixture([
    task("R0-01"),
    task("R0-02", { status: "needs_verification", record: "docs/execution/records/R0-02.md" }),
    task("R0-03", { status: "in_progress", record: "docs/execution/records/R0-03.md" })
  ]);
  equal(validate(input.queue, input.sources).valid, true);
  const result = select(input.queue.tasks);
  equal(result.task.id, "R0-02");
  equal(result.mode, "resume");
});

test("active tasks require a record and completed prerequisites", () => {
  failure(fixture([task("R0-01", { status: "in_progress" })]), /requires an existing record/);
  const input = fixture([
    task("R0-01", { status: "blocked", blockedReason: "External fixture unavailable" }),
    task("R0-02", { status: "in_progress", dependsOn: ["R0-01"], record: "docs/execution/records/R0-02.md" })
  ]);
  failure(input, /in_progress requires prerequisite R0-01 to be done/);
  throws(() => select(input.queue.tasks), /cannot resume with unfinished prerequisites R0-01/);
});

test("blocked prerequisites are never bypassed and independent work stays eligible", () => {
  const input = fixture([
    task("R0-01", { status: "blocked", blockedReason: "Fixture unavailable" }),
    task("R0-02", { dependsOn: ["R0-01"] }),
    task("R0-03")
  ]);
  equal(validate(input.queue, input.sources).valid, true);
  equal(select(input.queue.tasks).task.id, "R0-03");
  input.queue.tasks[0].blockedReason = " ";
  failure(input, /blocked status requires blockedReason/);
});

test("no eligible work returns explicit blocked, waiting, and deferred summaries", () => {
  const tasks = [
    task("R0-01", { status: "blocked", blockedReason: "Need fixture" }),
    task("R0-02", { dependsOn: ["R0-01"] }),
    task("R6-01", { status: "deferred" })
  ];
  const result = select(tasks);
  equal(result.task, null);
  deepEqual(result.blocked, [{ id: "R0-01", reason: "Need fixture" }]);
  deepEqual(result.waiting, [{ id: "R0-02", prerequisites: ["R0-01"] }]);
  deepEqual(result.deferred, ["R6-01"]);
});

test("spec anchors must exist outside examples; explicit anchors are accepted", () => {
  const input = fixture([task("R0-01")]);
  input.sources.files[SPEC_PATH] = "# Specifications\n```markdown\n## R0-01\n```\n";
  failure(input, /missing spec heading anchor/);
  input.sources.files[SPEC_PATH] = '<a id="r0-01"></a>\n## Repair scheduler\n';
  equal(validate(input.queue, input.sources).valid, true);
  delete input.sources.files[SPEC_PATH];
  failure(input, /file is missing/);
});

test("record and spec paths reject traversal, absolute paths, and Windows escapes", () => {
  for (const record of ["../record.md", "docs/../record.md", "/record.md", "C:/record.md", "docs\\record.md", "docs/record.md:stream"]) {
    failure(fixture([task("R0-01", { record })]), /repository-relative file without traversal/);
  }
  failure(fixture([task("R0-01", { spec: "../spec.md#r0-01" })]), /repository-relative file#anchor without traversal/);
});

test("roadmap table and queue membership must agree in both directions", () => {
  const input = fixture([task("R0-01")]);
  input.sources.files[ROADMAP_PATH] = "| R0-02 | Unlisted work |\n";
  failure(input, /R0-01: queue task is absent/);
  failure(input, /R0-02: product roadmap task is absent/);
  input.sources.files[ROADMAP_PATH] = "| R0-01 | Task |\n| R0-01 | Duplicate |\n";
  failure(input, /duplicate task table IDs/);
});
