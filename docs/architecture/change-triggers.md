# Change Triggers — When KB Updates Are Required

> **Purpose:** Turns KB from documentation into a maintenance system.
> **Enforcement:** `scripts/pre-commit-guard.js` warns on missing updates.
> **Last updated:** 2026-09-08

---

## Trigger Matrix

| Change | Required KB Update | File |
|---|---|---|
| New cache introduced | Add to ownership table with invalidation trigger | `docs/invariants.md §1` |
| New shared state | Add ownership row (owner, readers, write methods) | `docs/invariants.md §1` |
| New identifier type | Add durable/mutable classification | `docs/invariants.md §2` |
| New provider | Add to system-map, verify ownership table | `docs/architecture/system-map.md` |
| GrailsService field added | Add to ownership table | `docs/invariants.md §1` |
| ServiceContainer service added | Add to client ownership table | `docs/invariants.md §1` |
| Bug fixed | Add failure mode entry with invariant ID | `docs/failure-modes.md` |
| Architecture decision made | Create ADR with cross-links | `docs/adr/decisions.md` |
| ADR reversed or superseded | Update ADR status, do NOT delete | `docs/adr/decisions.md` |
| Risk area behavior changed | Update risk zones | `docs/architecture/system-map.md` |
| New cross-file cache | Verify `clearCrossFileCaches()` coverage | `docs/invariants.md §3` |
| ProjectContext/lifecycle/publication changed | Update designated owner, coherent revision and retention evidence | `docs/invariants.md`, `docs/state-and-lifecycle-specification.md` |
| Framework discovery or visibility changed | Update provenance, capability, invalidation and negative fixtures | `docs/specs/library-discovery.md`, task record |
| Roadmap task accepted/reopened/blocked | Update one queue state and preserve/revise acceptance evidence; requeue dependents if invalidated | `docs/execution/task-queue.json`, task record |
| Operation schema or embedded mapping changed | Update shared contract and source/tool parity cases | `docs/specs/agent-tools.md`, `docs/specs/ide-workflows.md` |

---

## What The Pre-Commit Guard Checks

| Trigger | Detection Method | Severity |
|---|---|---|
| GrailsService.groovy changed | File path match | ⚠️ Warning |
| ServiceContainer.ts changed | File path match | ⚠️ Warning |
| New cache class in diff | Regex: `class *Cache`, `new ThreadSafeLruCache` | ⚠️ Warning |
| New provider in diff | Regex: `extends BaseProvider` | ⚠️ Warning |
| Bug-fix commit message | Regex: `fix`, `bug`, `patch`, `hotfix` | ⚠️ Warning |
| STATUS.md not staged | File missing from staged | ❌ Blocking |

KB warnings are non-blocking. STATUS.md warnings are blocking. This keeps friction low while building the habit.

---

## Validation Criteria

KB docs are valid when:

1. **Ownership table** — each shared mutable state concern has a designated owner, including per-project contexts, schedulers and caches; avoid inventing owners from old diagrams
2. **Identity table** — every identifier type used in cross-system lookups has a classification
3. **Failure modes** — every bug fixed in last 30 days has an entry
4. **ADRs** — every architectural choice in last 30 days has a record
5. **System map** — matches current component list in GrailsService and ServiceContainer

Validation is triggered by:
- Architecture change (new component, new pattern)
- New shared state
- New cache
- New provider

Validation ties to git history, not calendar dates. `Last validated` means "validated against the current architecture, not a rubber-stamped date."
