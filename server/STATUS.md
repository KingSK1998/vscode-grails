# Server Status

> **AI AGENTS: Update this file on EVERY task that touches server code. Status only — no docs, no API, no architecture. Just current state.**
> Last updated: 2026-09-10

---

## Current Priority: R1 — Concurrency & Lifecycle Correctness

**See:** [implementation handoff](../docs/implementation-handoff.md) and [product roadmap](../docs/product-roadmap.md).

### Current verification status (2026-09-10)

| Feature / check | Status | Evidence |
|---|---|---|
| Publication & lifecycle ownership (R1-04) | ✅ Passing | Readers decoupled from compiler writer locks (<5ms latency); `RequestLease` resource tracking; `DetachedASTAccessor` AST detachment on hibernation; cache eviction on disposal; non-blocking routing and cross-project deadlock-free lock ordering. Tests: `PublicationAndLifecycleSpec` (7/7) |
| Bounded Gradle synchronization & LKG retention | ✅ Passing | Decoupled Gradle sync from compiler activation; non-blocking reads during 30s sync stall (< 10 ms); preserved usable LKG committed state on sync failure; in-flight sync cancellation on supersede and disposal; stale sync state labeled; bounded retry with triggers. Tests: `GradleSyncSpec` (12/12) |
| Revision ordering & publication safety | ✅ Passing | Sequential incremental changes applied in array order; open generation sequence tracking; obsolete versions rejected; atomic generation/version verification before commit and diagnostic publishing; distinct overlay close vs delete document lifecycle. Tests: `PositionHelperSpec` (46/46), `FileContentTrackerSpec` (18/18), `RevisionAndPublicationSpec` (4/4), `DocumentCompilationSpec` (15/15) |
| Background document compilation | ✅ Passing | `DocumentCompilationSpec`: 15/15 tests pass; bounded count/bytes, multi-root fairness, overload and newest-revision recovery, close storms, oversized input policy, root cancellation and shutdown |
| Incremental compiler regression suite | ✅ Passing | `GrailsIncrementalCompilerSpec`: 7/7 tests pass; `this.@state.get()` resolved runtime cast error under `@CompileStatic` |
| Workspace startup and folder lifecycle | ✅ Passing | `WorkspaceLifecycleSpec`: 8/8 tests pass; prior startup/root cases plus prompt terminal removal and blocked-candidate publication rejection |
| Stdio logging and custom protocol | ✅ Passing | `GrailsLanguageClient` bound via `LSPLauncher.Builder`, custom `grails/allProjects` notification, stdio stdout framed LSP, logback routed to stderr/bounded file; smoke test passed in 869 ms (exit 0) |
| Packaged release | ✅ Passing | Deterministic build/copy verified; package asset audit passed; local VSIX installed and verified with smoke test in 921 ms (R0-04) |

Reports were inspected on 2026-09-10. Historical claims below predate the current changes and do not establish full snapshot or classpath isolation.

R1-04 focused validation passes `PublicationAndLifecycleSpec` (7/7), `GradleSyncSpec` (12/12), `DocumentCompilationSpec` (15/15), `WorkspaceLifecycleSpec` (8/8), `RevisionAndPublicationSpec` (4/4), `ReactivationAndLruSpec` (4/4), `FileContentTrackerSpec` (18/18), and `PositionHelperSpec` (46/46) for a total of 114/114 passing tests. Full test suite has 23 failures confined to pre-existing legacy provider expectation suites; no R1-01, R1-02, R1-03, or R1-04 lifecycle, synchronization, or compiler spec failed.

### Completed Work (2026-06-24)
- **Phase 3: Multi-Project & Lifecycle Mastery**:
  - **Task 3b (Dependency Invalidation)**: ✅ Added comprehensive test coverage for `WorkspaceManager.propagateInvalidation()` in `DependencyInvalidationSpec` (6 tests covering dependency detection by name/jar path, dirty propagation, cycle protection, and independent project isolation).

### Completed Work (2026-06-23)
- **Phase 3: Multi-Project & Lifecycle Mastery**:
  - **Task 3b (Reactivation Lock & Pipeline)**: ✅ Implemented `ensureActivated()` with atomic `activationFuture` locking to prevent concurrent compilation resource duplication.
  - **Task 3b (LRU Eviction Enforcement)**: ✅ Updated memory budget check to fire on `ready()` transition to prevent leak states.
  - **Task 3b (Snapshot Lineage & LKG Invariant)**: ✅ Created `SnapshotManager` for soft-reference lineage retention and preserving the LKG snapshot on compile failures.
  - **Task 3c.1 (Build file watch sync)**: ✅ Build configuration changes now debounce and trigger explicit Gradle sync for the affected project context.
- **Testing**: ✅ Added integrated LRU eviction tests to `WorkspaceIsolationSpec` and resolved dynamic property access gotchas.

### Completed Work (2026-06-17)
- **Phase 3: Multi-Project & Lifecycle Mastery**: ✅ COMPLETED
  - **Task 3a.1: VersionedSnapshot**: ✅ Created immutable snapshot record bundling AST, Index, and GradleModel.
  - **Task 3a.2: Compilation Commit**: ✅ Implemented atomic commit protocol in `ProjectContextImpl` with version bumping.
  - **Task 3b.1: WorkspaceManager**: ✅ Replaced monolithic `GrailsService` state with multi-root isolated contexts.
  - **Task 3b.2: Isolation Tests**: ✅ Verified cross-project isolation in `WorkspaceIsolationSpec`.
  - **Task 3c.1: Gradle Sync Audit**: ✅ Integrated `GradleModel` into snapshots (GAP-08).
  - **Task 3c.2: Memory Lifecycle**: ✅ Implemented project hibernation and OOM recovery path.
- **Provider Tier Refactor**: ✅ Migrated all 21 LSP providers and 18 completion strategies to the new stateless, snapshot-bound architecture.

### Completed Work (2026-06-16)
- **Code Quality Standardization**: ✅ Removed inline FQCNs (`kingsk.grails.lsp.context.*`) across all 21 T1/T2 providers and utilities.
- **SignatureHelp AST Climbing**: ✅ Resolved active parameter and constructor signature help regressions by climbing the AST.

[... rest of previous status ...]
---

## Server Info
[... unchanged ...]

---

## LSP Feature Status

### ✅ Done & Working

| LSP Feature | Class / Provider | Notes |
|---|---|---|
| Text completion | `CompletionProvider` | Stateless strategies, snapshot-bound, concurrent |
| Multi-root workspace | `WorkspaceManager` | ✅ Isolated project contexts, hibernation support |
[... rest of features ...]
