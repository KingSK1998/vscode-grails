# Continue the project from the roadmap

**Updated:** 2026-09-08. This is the execution entry point for any coding agent. The [product roadmap](product-roadmap.md) defines the intended product; this guide defines how to deliver it without treating proposals or old completion claims as working software.

## Start here

1. Read [AGENTS.md](../AGENTS.md), the [system map](architecture/system-map.md), and [implementation handoff](implementation-handoff.md). Inspect `git status --short`; preserve existing work.
2. Run `node scripts/roadmap.js next` from the repository root. It selects a task from [task-queue.json](execution/task-queue.json), without modifying files or executing builds. At this documentation baseline the answer is **R0-01**.
3. Read that task's [acceptance card](execution/task-specifications.md), linked specifications, existing record, affected source and tests. Read [invariants](invariants.md), [failure modes](failure-modes.md), [coding standards](../CODING_STANDARDS.md), and the affected component's `RULES.md` and `STATUS.md`.
4. Create/update the task record using the [record template](execution/record-template.md), set its queue `record` path, `owner` and `status: in_progress`, and validate the queue. Record the evidence brief and implementation approach. Confirm the failure or missing behavior with the smallest meaningful test before repairing code. A current, reproduced failure can serve as the initial failing test.
5. Implement one bounded slice; run its checks and the applicable [validation gates](specs/validation.md). Review correctness and architecture before final build validation.
6. Update the task record, affected STATUS and KB documents, and append the session outcome to root [MEMORY.md](../MEMORY.md). Set the queue status to `done` only when the whole card passes. Then run `node scripts/roadmap.js next` again and continue while the user's authorized scope and session budget allow.

The selector is advisory. It checks dependency/evidence bookkeeping; it cannot prove the code is correct, reserve work against another agent, run tests, or approve a release.

To verify the selector itself, run `node scripts/roadmap.test.js`. It uses Node's built-in test runner without dependencies or a child test-worker requirement. Queue validation also rejects missing card anchors and accepted tasks lacking an existing evidence record. See the [documentation baseline](execution/documentation-baseline.md) for this handoff's validation and remaining runtime work.

## Which document owns which decision?

| Question | Authority |
|---|---|
| What did the user authorize? | Current conversation and applicable higher-priority instructions |
| How do agents operate in this repository? | AGENTS and this execution guide |
| What is in 1.0, and why? | Product roadmap; R6 is future candidate work |
| What task is next and what is its acceptance state? | `execution/task-queue.json` only |
| What must that task demonstrate? | Its task card and linked specs; general validation gates also apply |
| Who may mutate state, and what must remain consistent? | `invariants.md`; ADR-009 explains corrected ownership wording |
| What implementation style applies? | Component RULES for local concerns; CODING_STANDARDS for cross-cutting concerns |
| What is implemented and verified today? | Source and reproducible test evidence; task records explain which tree and environment were checked |
| Why was an architecture choice made? | Accepted, nonsuperseded ADRs |
| What was considered earlier? | Historical phase plans, old reviews, graph report and superseded reasoning guide; context only |

Specifications state required behavior, **not certification of the current code**. A violated invariant remains a bug even when the old STATUS says complete. New classes, interfaces or cache layouts proposed in a spec are not existing APIs until verified in source. Prefer method names and paths over stale line-number ranges in older documents.

If two active requirements still conflict, quote both in the task record, inspect their source/ADR context, and choose a conforming implementation where possible. Correct demonstrably stale descriptions in the same change. Do not silently weaken an invariant to pass tests. Ask the user only when the unresolved choice changes product scope, a correctness promise, or authorization; continue independent authorized work. New evidence may change the implementation approach and its tests during a task.

## Deterministic task selection

Queue order is priority order. Status is acceptance state, not a guess about how many source files exist.

| Status | Meaning | Next action |
|---|---|---|
| `queued` | Not yet accepted; may contain existing partial implementation | Eligible when every dependency is `done` |
| `in_progress` | A task has a recorded implementation attempt | Resume/reconcile its record before selecting fresh work |
| `needs_verification` | Implementation is ready for outstanding checks | Run those checks; do not assume success |
| `blocked` | Concrete external/input dependency prevents progress | Record reason and condition to resume; other eligible tasks may proceed |
| `done` | Full acceptance card, reviews and applicable gates passed | Preserve evidence; reopen if a regression invalidates it |
| `deferred` | Candidate outside the currently admitted release scope | Never auto-select; admission requires evidence and an updated card/dependencies |

The selector first returns the earliest `in_progress` or `needs_verification` task. Otherwise it returns the earliest eligible `queued` task. A blocked prerequisite blocks its dependents. When none is eligible, it reports blockers or release-scope completion; it never makes a dependency disappear or promotes R6 automatically.

When a regression invalidates an accepted task, preserve its old evidence and append the new failure. Requeue that task and every transitive dependent currently `done`, `in_progress` or `needs_verification` in one coordinated ledger edit; preserve their records and note which acceptance must be reverified. Pause/handoff any active dependent writer first. The validator intentionally rejects accepted/active tasks with unfinished prerequisites. After the repair passes, previously completed dependents usually need focused reverification, not reimplementation; record fresh evidence before restoring `done`. Never leave the queue invalid or delete dependencies to make selection work.

One agent/coordinator owns each active task. Before claiming, inspect its owner and record. An old timestamp alone does not authorize overwriting another active agent's work. In a new session with an interrupted task, reconcile the working tree and record, then explicitly record the handoff. When using parallel agents, only the Reviewer dispatches them; use separate branches/worktrees for overlapping code or keep one writer. Queue edits require coordination; the selector does not supply locking.

## Keep sessions small enough to finish

A roadmap card may span several sessions. Each session should implement a verifiable slice of that card, not expand into the next phase. Record remaining acceptance cases explicitly. A passing subtask leaves the parent `in_progress` until all cases pass. New necessary subtasks belong in the parent record unless they need independent dependencies; if added to the queue, add a matching roadmap table row/card and update dependencies before selecting them.

For R0-01, first reproduce and repair the saved compiler cast and test-assertion failures. Then cover empty buffers, obsolete publication and lifecycle cleanup identified by its card. Startup discovery belongs to R0-02; queueing before discovery can be unit-tested in R0-01 and integrated there. Preserve this boundary instead of starting a new scheduler or snapshot framework.

Before a usage limit or interruption, save files, exact failing command/report, last verified behavior, unverified changes and next step in the record. Leave `in_progress` or `needs_verification`; do not mark done because time ran out. Do not leave an unowned background build running unless its PID/session and ownership are documented.

## Decision rules that prevent repeated architectural mistakes

| Trigger | Required approach |
|---|---|
| Hardcoded method, config key or framework completion | Classify it using the [discovery spec](specs/library-discovery.md); trace installed-artifact evidence and add removal/isolation tests |
| Slow request or large memory use | Reproduce and measure using the [performance spec](specs/performance.md); preserve correctness, bound work, compare before/after |
| New cache | Record owner, scope, key, value, size limit, invalidation, cancellation and disposal before adding it |
| State/publication change | Use the [state contract](state-and-lifecycle-specification.md); list the write transaction, reader view and revision checks |
| Existing core needs a fix | Modify the owning core with regression evidence and architecture review; do not work around it inside a provider |
| Dynamic behavior cannot be proven | Return scoped, explicitly incomplete evidence; never invent APIs or use heuristic matches for destructive refactors |
| New pattern/framework/DSA | Compare the simplest existing design against the measured requirement; an acronym or asymptotic claim alone is not evidence |
| New AI integration | Reuse the same typed analysis operations; apply the [agent contract](specs/agent-tools.md), including freshness and verification |

A provider cannot synchronously compile, activate a hibernated compiler, scan JARs or invoke Gradle to answer a read. A background refresh may be requested through the owning coordinator, with the current read returning available evidence or an explicit unavailable state. `supplyAsync` alone does not bound work or establish correct publication.

## Definition of done for every task

- Every acceptance case has a result, a test/manual procedure and evidence location. No unresolved failure is hidden by changing an assertion to check a weaker property.
- Changed state has an owner; required invariant IDs are mapped to tests or a reasoned inspection. Existing unrelated gaps remain tracked, with no new violations introduced.
- Source-derived results retain scope/origin/freshness, and resource lifecycle is bounded.
- Applicable targeted and component checks ran successfully on the final relevant tree. Record skipped environmental/platform checks as pending, not passed.
- Code review, architecture review when triggered, and final build validation are recorded. One agent may perform these roles sequentially; multiple model instances are optional.
- STATUS, failure registry, invariants/ADRs/system map as triggered, record and queue agree. The roadmap's product scope does not need a duplicate completion checkbox.

Publishing, signing a release, pushing or deleting unrelated files does not follow automatically from selecting a task. Prepare local reviewable artifacts within the existing authorization and follow the user's actual publishing instructions.

## Copy this prompt to another agent

```text
Continue this repository toward the 1.0 product in docs/product-roadmap.md.
Read AGENTS.md and docs/agent-execution.md, run node scripts/roadmap.js next,
and execute the selected task using its acceptance card, linked specs,
invariants, source and current tests. Preserve existing edits. Reuse partial
implementation and repair it before expanding architecture. Record exact
verification evidence and remaining work. Mark done only when all gates pass,
then select the next eligible task while the session allows. Do not treat old
phase-complete labels or a passing build as proof of behavior. If blocked,
record the specific condition and continue eligible independent work.
```
