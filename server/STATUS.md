# Server Status

> **AI AGENTS: Update this file on EVERY task that touches server code. Status only — no docs, no API, no architecture. Just current state.**
> Last updated: 2026-09-09

---

## Current Priority: R0 — Recover Interrupted Implementation

**See:** [implementation handoff](../docs/implementation-handoff.md) and [product roadmap](../docs/product-roadmap.md).

### Current verification status (2026-09-09)

| Feature / check | Status | Evidence |
|---|---|---|
| Background document compilation | ✅ Passing | `DocumentCompilationSpec`: 9/9 tests pass; nonblocking open, rapid edit coalescing, copied buffer immutability, blocked worker ordering |
| Incremental compiler regression suite | ✅ Passing | `GrailsIncrementalCompilerSpec`: 7/7 tests pass; `this.@state.get()` resolved runtime cast error under `@CompileStatic` |
| Workspace startup and folder lifecycle | ✅ Passing | `WorkspaceLifecycleSpec`: 6/6 tests pass; zero/multi-root capability response, root generations discard late results, longest prefix boundary matching, still-open buffer replay, build-watch debounce |
| Stdio logging | 🟡 Source fix present | Console changed to stderr; rebuilt JAR smoke test queued in R0-03 |
| Packaged release | ⬜ Unverified | No final VSIX/editor acceptance run (R0-04) |

Reports were inspected on 2026-09-08; their timestamps are 2026-09-07. No tests were rerun during the roadmap-only update. Historical claims below predate the current changes and do not establish full snapshot or classpath isolation.

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
