const { readFileSync, realpathSync, statSync } = require("node:fs");
const path = require("node:path");

const QUEUE_PATH = "docs/execution/task-queue.json";
const ROADMAP_PATH = "docs/product-roadmap.md";
const STATUSES = new Set(["queued", "in_progress", "needs_verification", "blocked", "done", "deferred"]);
const RESUME_STATUSES = new Set(["in_progress", "needs_verification"]);
const TASK_ID = /^R\d+-\d{2}$/;

function isRelativeFile(file) {
  return typeof file === "string" && file.length > 0 &&
    !path.posix.isAbsolute(file) && !path.win32.isAbsolute(file) &&
    !/[\\:#?\0]/.test(file) &&
    file.split("/").every(part => part !== "" && part !== "." && part !== "..");
}

function visibleMarkdown(markdown) {
  let fence = null;
  return markdown.split(/\r?\n/).filter(line => {
    const match = /^ {0,3}(`{3,}|~{3,})(.*)$/.exec(line);
    if (fence) {
      if (match && match[1][0] === fence[0] && match[1].length >= fence.length && !match[2].trim()) {
        fence = null;
      }
      return false;
    }
    if (match) {
      fence = match[1];
      return false;
    }
    return true;
  });
}

function markdownAnchors(markdown) {
  const anchors = new Set();
  const counts = new Map();
  for (const line of visibleMarkdown(markdown)) {
    for (const match of line.matchAll(/<a\s+[^>]*?id=["']([^"']+)["'][^>]*>/gi)) {
      anchors.add(match[1]);
    }
    const heading = /^ {0,3}#{1,6}\s+(.+?)\s*#*\s*$/.exec(line);
    if (!heading) continue;
    const slug = heading[1].toLowerCase().replace(/<[^>]*>/g, "")
      .replace(/[^\p{L}\p{N}\p{M}\s_-]/gu, "").replace(/\s/g, "-");
    const count = counts.get(slug) || 0;
    counts.set(slug, count + 1);
    anchors.add(count ? `${slug}-${count}` : slug);
  }
  return anchors;
}

// Pure validation: files maps repository-relative paths to text or { error }.
// Filesystem reads and symlink containment checks belong to loadSources().
function validate(queue, sources = {}) {
  const errors = [];
  const files = sources.files || {};
  const getText = file => {
    const value = Object.hasOwn(files, file) ? files[file] : undefined;
    if (typeof value === "string") return value;
    errors.push(`${file}: ${value?.error || "file is missing"}`);
    return null;
  };
  if (!queue || typeof queue !== "object" || Array.isArray(queue) || queue.schemaVersion !== 1) {
    errors.push("Queue must be an object with schemaVersion: 1.");
  }
  if (!Array.isArray(queue?.tasks) || queue.tasks.length === 0) {
    errors.push("Queue tasks must be a nonempty array.");
    return { valid: false, errors };
  }

  const tasks = new Map();
  for (const [index, task] of queue.tasks.entries()) {
    if (!task || typeof task !== "object" || Array.isArray(task)) {
      errors.push(`Task at index ${index} must be an object.`);
      continue;
    }
    const label = typeof task.id === "string" ? task.id : `Task at index ${index}`;
    if (typeof task.id !== "string" || !TASK_ID.test(task.id)) errors.push(`${label}: invalid task ID.`);
    if (tasks.has(task.id)) errors.push(`${label}: duplicate task ID.`);
    tasks.set(task.id, task);
    if (typeof task.title !== "string" || !task.title.trim()) errors.push(`${label}: title is required.`);
    if (!STATUSES.has(task.status)) errors.push(`${label}: invalid status ${String(task.status)}.`);
    if (task.owner !== null && (typeof task.owner !== "string" || !task.owner.trim())) {
      errors.push(`${label}: owner must be null or a nonempty string.`);
    }
    if (!Array.isArray(task.dependsOn) || task.dependsOn.some(id => typeof id !== "string" || !TASK_ID.test(id))) {
      errors.push(`${label}: dependsOn must be an array of task IDs.`);
    } else if (new Set(task.dependsOn).size !== task.dependsOn.length) {
      errors.push(`${label}: duplicate prerequisite.`);
    }
    if (task.status === "blocked" && (typeof task.blockedReason !== "string" || !task.blockedReason.trim())) {
      errors.push(`${label}: blocked status requires blockedReason.`);
    }

    const parts = typeof task.spec === "string" ? task.spec.split("#") : [];
    if (parts.length !== 2 || !isRelativeFile(parts[0]) || !parts[1]) {
      errors.push(`${label}: spec must be a repository-relative file#anchor without traversal.`);
    } else {
      const markdown = getText(parts[0]);
      if (markdown !== null && !markdownAnchors(markdown).has(parts[1])) {
        errors.push(`${label}: missing spec heading anchor ${task.spec}.`);
      }
    }

    const needsRecord = task.status === "done" || RESUME_STATUSES.has(task.status);
    if (task.record === null) {
      if (needsRecord) errors.push(`${label}: ${task.status} requires an existing record.`);
    } else if (!isRelativeFile(task.record)) {
      errors.push(`${label}: record must be null or a repository-relative file without traversal.`);
    } else {
      const record = getText(task.record);
      if (task.status === "done" && record !== null && !visibleMarkdown(record).some(line => /^Acceptance: PASS\s*$/.test(line))) {
        errors.push(`${label}: done requires standalone Acceptance: PASS in its record, outside code fences.`);
      }
    }
  }

  for (const task of tasks.values()) {
    if (!Array.isArray(task.dependsOn)) continue;
    for (const dependency of task.dependsOn) {
      if (!tasks.has(dependency)) {
        errors.push(`${task.id}: missing prerequisite ${String(dependency)}.`);
      } else if ((task.status === "done" || RESUME_STATUSES.has(task.status)) && tasks.get(dependency).status !== "done") {
        errors.push(`${task.id}: ${task.status} requires prerequisite ${dependency} to be done.`);
      }
    }
  }

  const visited = new Set();
  const visiting = new Set();
  function visit(id, chain) {
    if (visiting.has(id)) {
      errors.push(`Dependency cycle: ${[...chain, id].join(" -> ")}.`);
      return;
    }
    if (visited.has(id)) return;
    visiting.add(id);
    const task = tasks.get(id);
    for (const dependency of Array.isArray(task.dependsOn) ? task.dependsOn : []) {
      if (tasks.has(dependency)) visit(dependency, [...chain, id]);
    }
    visiting.delete(id);
    visited.add(id);
  }
  for (const id of tasks.keys()) visit(id, []);

  const roadmap = getText(ROADMAP_PATH);
  if (roadmap !== null) {
    const roadmapIds = visibleMarkdown(roadmap).map(line => /^\|\s*(R\d+-\d{2})\s*\|/.exec(line)?.[1]).filter(Boolean);
    const roadmapSet = new Set(roadmapIds);
    if (roadmapSet.size !== roadmapIds.length) errors.push("Product roadmap contains duplicate task table IDs.");
    for (const id of tasks.keys()) {
      if (!roadmapSet.has(id)) errors.push(`${id}: queue task is absent from product roadmap tables.`);
    }
    for (const id of roadmapSet) {
      if (!tasks.has(id)) errors.push(`${id}: product roadmap task is absent from queue.`);
    }
  }
  return { valid: errors.length === 0, errors };
}

function select(tasks) {
  const byId = new Map(tasks.map(task => [task.id, task]));
  const remaining = task => task.dependsOn.filter(id => byId.get(id)?.status !== "done");
  const active = tasks.filter(task => RESUME_STATUSES.has(task.status));
  for (const task of active) {
    const prerequisites = remaining(task);
    if (prerequisites.length) throw new Error(`${task.id}: cannot resume with unfinished prerequisites ${prerequisites.join(", ")}.`);
  }
  const task = active[0] || tasks.find(candidate => candidate.status === "queued" && remaining(candidate).length === 0) || null;
  return {
    task,
    mode: task ? (RESUME_STATUSES.has(task.status) ? "resume" : "start") : null,
    waiting: tasks.filter(candidate => candidate.status === "queued").map(candidate => ({ id: candidate.id, prerequisites: remaining(candidate) })),
    blocked: tasks.filter(candidate => candidate.status === "blocked").map(candidate => ({ id: candidate.id, reason: candidate.blockedReason })),
    deferred: tasks.filter(candidate => candidate.status === "deferred").map(candidate => candidate.id),
    done: tasks.filter(candidate => candidate.status === "done").length
  };
}

function readRepositoryFile(repoRoot, file) {
  if (!isRelativeFile(file)) throw new Error("path must remain within the repository without traversal");
  const root = realpathSync(repoRoot);
  const target = realpathSync(path.join(root, file));
  const relative = path.relative(root, target);
  if (relative === ".." || relative.startsWith(`..${path.sep}`) || path.isAbsolute(relative)) {
    throw new Error("resolved path escapes the repository (including symlinks)");
  }
  if (!statSync(target).isFile()) throw new Error("path is not a regular file");
  return readFileSync(target, "utf8").replace(/^\uFEFF/, "");
}

function loadSources(queue, repoRoot) {
  const paths = new Set([ROADMAP_PATH]);
  for (const task of Array.isArray(queue?.tasks) ? queue.tasks : []) {
    if (typeof task?.spec === "string" && isRelativeFile(task.spec.split("#")[0])) paths.add(task.spec.split("#")[0]);
    if (isRelativeFile(task?.record)) paths.add(task.record);
  }
  const files = Object.create(null);
  for (const file of paths) {
    try {
      files[file] = readRepositoryFile(repoRoot, file);
    } catch (error) {
      files[file] = { error: error.message };
    }
  }
  return { files };
}

function main(args) {
  const command = args[0] || "next";
  if (args.length > 1 || !["validate", "next"].includes(command)) {
    throw new Error("Usage: node scripts/roadmap.js [validate|next]");
  }
  const root = path.resolve(__dirname, "..");
  const queue = JSON.parse(readRepositoryFile(root, QUEUE_PATH));
  const result = validate(queue, loadSources(queue, root));
  if (!result.valid) throw new Error(`Queue validation failed:\n${result.errors.map(error => `- ${error}`).join("\n")}`);
  if (command === "validate") {
    console.log(`Roadmap queue valid: ${queue.tasks.length} tasks; prerequisites, specs, and required acceptance records checked.`);
    return;
  }
  const selection = select(queue.tasks);
  if (selection.task) {
    const task = selection.task;
    console.log(`${selection.mode === "resume" ? "Resume" : "Next"}: ${task.id} — ${task.title}`);
    console.log(`Status: ${task.status}; owner: ${task.owner || "unassigned"}`);
    console.log(`Prerequisites: ${task.dependsOn.join(", ") || "none"}`);
    console.log(`Spec: ${task.spec}`);
    console.log(`Record: ${task.record || "create a task record using docs/execution/record-template.md before implementation"}`);
    console.log("Selection is read-only; review the execution guide and record before claiming or continuing work.");
    return;
  }
  console.log(`No eligible task. Done: ${selection.done}/${queue.tasks.length}.`);
  for (const task of selection.blocked) console.log(`Blocked ${task.id}: ${task.reason}`);
  for (const task of selection.waiting) console.log(`Waiting ${task.id}: ${task.prerequisites.join(", ")}`);
  if (selection.deferred.length) console.log(`Deferred: ${selection.deferred.join(", ")}`);
}

module.exports = { validate, select, loadSources };

if (require.main === module) {
  try {
    main(process.argv.slice(2));
  } catch (error) {
    console.error(`[ROADMAP] ${error.message}`);
    process.exitCode = 1;
  }
}
