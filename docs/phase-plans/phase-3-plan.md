# Phase 3: Multi-Project & Lifecycle Mastery — Remaining Tasks

**Execution Mode:** ARCHITECTURE DESIGN MODE
**Status:** 🟡 ACTIVE (2026-06-24)
**Last updated:** 2026-06-24

> **Current delivery order (2026-09-08):** Lifecycle and isolation work maps to R0–R2 in the [product roadmap](../product-roadmap.md). Resume from the [implementation handoff](../implementation-handoff.md); the latest saved server tests fail, so historical “implemented” labels below are not acceptance evidence.

---

## Remaining Tasks Roadmap

### 1. Workspace Isolation & Lifecycle State Machine (Phase 3b) — ✅ **IMPLEMENTED**

**Goal:** Correctly handle project activation, hibernation, and resource disposal without race conditions.

**Status:** Core state machine IMPLEMENTED and TESTED. All transitions working as designed.

**What:** Strict state machine (`INITIALIZING`, `READY`, `HIBERNATED`, `REACTIVATING`, `FAILED`, `DISPOSING`) with explicit resource boundaries.
- **Why:** Prevents race conditions causing duplicate compiler instantiations. Differentiates temporary memory release (hibernation) from terminal workspace removal (disposal).
- **When:** Triggers on project initialization, memory limit breach (hibernation), incoming LSP request (reactivation), or workspace folder closure (disposal).

**Implemented Proof:**
- ✅ `ProjectState` enum defined with all 6 states
- ✅ `ProjectContextImpl` implements state transitions in `ready()`, `hibernate()`, `dispose()`, `reactivate()`
- ✅ `activationFuture` prevents duplicate compiler initialization during `REACTIVATING`
- ✅ `WorkspaceManager.enforceMemoryBudget()` enforces LRU hibernation at `maxActiveProjects=2`
- ✅ Test passing: "should hibernate LRU project context when active projects exceed limit"
- ✅ `hibernate()` semantics: nullifies `compiler`, `visitor`, retains `activeSnapshot` and `LKG`
- ✅ `dispose()` semantics: terminal state, cancels `activationFuture`, rejects pending operations

**Remaining:** FAILED exit criteria (reactivation on build file change) — partial implementation pending in §4.

---

### 2. Snapshot Lineage & Ownership (Phase 3b) — ✅ **IMPLEMENTED**

**Goal:** Establish formal lineage tracking for historical and pinned snapshots within `ProjectContext`.

**Status:** Core snapshot management IMPLEMENTED and IN USE.

**What:** `SnapshotManager` class exclusively owning the historical snapshot lineage with strict retention boundaries.
- **Why:** Prevents `OutOfMemoryError` leaks from stale snapshots while guaranteeing safe, lock-free resolution for long-running LSP requests (Single-Snapshot Principle).

**Implemented Proof:**
- ✅ `SnapshotManager` class manages `VersionedSnapshot` lineage
- ✅ `active` snapshot tracks current compilation state
- ✅ `LKG` (Last Known Good) pinned independently from compilation failures
- ✅ Snapshot retention policy: strict bounds on historical lineage (in `SnapshotManager`)
- ✅ OOM recovery uses snapshot lineage eviction (see `GrailsService.handleOOM()`)
- ✅ Single-Snapshot Principle: LSP requests pin to snapshot at request start

**Remaining:** Explicit documentation of retention thresholds and soft reference cleanup rules.

---

### 3. Dependency Graph Invalidation Semantics (Phase 3b) — ✅ **IMPLEMENTED & TESTED**

**Goal:** Handle cross-project symbol invalidation using explicit dependency graph edges.

**Status:** Framework in place, invalidation propagation IMPLEMENTED and TESTED.

**What:** Explicit cross-project symbol invalidation using the Gradle dependency graph.
- **Why:** Prevents downstream projects from serving stale or incorrect references when upstream APIs change.

**Implemented Proof:**
- ✅ `dependencyDirty` field tracks downstream invalidation state in `ProjectContextImpl`
- ✅ `WorkspaceManager.propagateDependencyInvalidation()` handles BFS traversal with cycle protection
- ✅ Dirty state does NOT block requests (async re-compilation)
- ✅ Cross-project reads use last committed snapshot
- ✅ Test coverage: `DependencyInvalidationSpec` (6 tests) — dependency detection by name/jar path, dirty propagation, cycle protection, independent project isolation

**Remaining:**
- Integration with actual Gradle dependency graph
- Performance validation on large workspaces

---

### 4. Gradle Sync Audit, Sync Storms & Cancellation (Phase 3c.1) — 🟡 **PARTIAL**

**Goal:** Predictable, resource-safe Gradle sync with timeout, cancellation, and debounce.

**Status:** Build file watch debounce IMPLEMENTED. Gradle sync trigger IMPLEMENTED. Timeout/cancellation pending validation.

**What:** Predictable, exclusively-owned Gradle sync pipeline with debouncing, timeouts, and clean cancellation.
- **Why:** Prevents CPU starvation, Gradle daemon connection pool exhaustion, and concurrent sync file-lock collisions.

**Implemented Proof:**
- ✅ Build file watch debouncing (2 second) in `didChangeWatchedFiles()`
- ✅ Gradle sync triggered for affected project on `build.gradle` / `settings.gradle` change
- ✅ `GrailsTextDocumentService.didChangeWatchedFiles()` routes to `ProjectContext.reloadGradleModel()`
- ✅ Transition to `ProjectState.FAILED` on sync error, emit diagnostics
- ✅ LKG model retained if sync fails or times out

**Remaining:**
- ⏳ Validate 30-second timeout on `gradle.getGrailsProjectAsync()`
- ⏳ Validate `CancellationTokenSource` cleanup (prevent daemon leaks)
- ⏳ Test sync storms scenario with rapid build file edits
- ⏳ Connection ownership audit (ensure no connection sharing)

---

### 5. Memory Lifecycle & Tier-2 Exit Criteria (Phase 3c.2) — 🟡 **PARTIAL**

**Goal:** Safe resource eviction and stable recovery from memory pressure.

**Status:** Tier 2 degradation framework present. Exit criteria validation pending.

**What:** Controlled degradation to Tier-2 (regex/name-only search) and safe recovery exit criteria.
- **Why:** Prevents server crashes on massive workspaces. Exit criteria prevent infinite OOM oscillation loops.

**Implemented Proof:**
- ✅ Classloader cleanup on hibernation (nullify references)
- ✅ Snapshot reference rules (strong: `activeSnapshot`, `LKG`; soft: historical lineage only)
- ✅ `GrailsService.handleOOM()` escalation path defined
- ✅ Tier 2 entry (`isTier2()` flag) prevents expensive operations

**Remaining:**
- ⏳ Validate Classloader release guarantees (GC profiling)
- ⏳ Cool-down duration (60s) and heap check (<75%) before Tier 2 exit
- ⏳ Test OOM recovery under sustained load
- ⏳ Prevent GC thrashing oscillation (test stability)

---

## Summary of Phase 3 Status

| Component | Section | Status | Impl % |
|-----------|---------|--------|--------|
| State Machine | 3b | ✅ **DONE** | 100% |
| Snapshot Manager | 3b | ✅ **DONE** | 100% |
| Dependency Invalidation | 3b | ✅ **DONE** | 85% (framework + tests done, Gradle integration pending) |
| Gradle Sync | 3c.1 | 🟡 **PARTIAL** | 60% (trigger + debounce done, timeout/cancel pending) |
| Memory Lifecycle | 3c.2 | 🟡 **PARTIAL** | 50% (framework done, exit criteria pending) |

**Overall Phase 3 Progress:** ~80% Complete (core infrastructure done, advanced features and validation in progress)

---

## Immediate Next Steps

1. **Validate Gradle Sync Timeout & Cancellation** (§4)
   - Write test for 30-second timeout behavior
   - Verify `CancellationTokenSource` actually terminates daemon threads
   - Check for connection leaks

2. **Memory Exit Criteria Validation** (§5)
   - Run server against 50+ project workspace
   - Trigger OOM, validate Tier 2 degradation
   - Verify stable recovery with cool-down

3. **Dependency Invalidation — Gradle Integration** (§3)
   - Integrate actual Gradle dependency graph resolution into `projectDependsOn()`
   - Test with real multi-project Gradle workspace

  2. **LKG Invariant:** `LKG` ALWAYS points to the most recent successfully committed snapshot. Compilation failure, Gradle sync failure, and OOM recovery NEVER change `LKG`.
  3. Implement request-pinned reference tracking (ensuring JVM reachability is sufficient for active requests, but defining lineage boundaries for historical lookups).
  4. Specify OOM recovery eviction rules for lineage (e.g., clearing historical lineage first, then soft references, before falling back to Tier 2).
  5. Lineage retention: Hibernated projects retain their lineage. Only `DISPOSE` clears it.

---

### 3. Dependency Graph Invalidation Semantics (Phase 3b)

**Goal:** Handle cross-project symbol invalidation using explicit dependency graph edges.

- **What:** Explicit cross-project symbol invalidation using the Gradle dependency graph.
- **Why:** Prevents downstream projects from serving stale or incorrect references when upstream APIs change. Enforces non-blocking cross-project reads to maintain high performance.
- **When:** Triggered asynchronously immediately after an upstream project (Project B) commits a new `VersionedSnapshot`.
- **How (Actions):**
  1. Implement invalidation propagation in `WorkspaceManager`: when Project B's snapshot changes, trace downstream dependency graph edges to find dependent projects (e.g., Project A).
  2. **Cycle Protection:** Propagation must traverse each project at most once. `WorkspaceManager` maintains a visited set during propagation to prevent infinite invalidation loops (e.g., A -> B -> C -> A).
  3. **Dirty State Semantics:** Define `boolean dependencyDirty` (or `ProjectHealth` enum). Mark downstream projects `DIRTY`.
     - `DIRTY` means the current snapshot remains valid, but newer dependency snapshots exist.
     - `DIRTY` does NOT block requests. Requests continue using the current committed snapshot until a replacement snapshot commits.
  4. Schedule asynchronous re-compilation and re-indexing for the affected downstream projects.
  5. **Cross-Project Visibility Invariant:**
     - Cross-project reads always use the last committed snapshot of dependent projects.
     - Dependent project recompilation is asynchronous and never blocks requests.
     - No project may observe an uncommitted dependency snapshot.

---

### 4. Gradle Sync Audit, Sync Storms & Cancellation (Phase 3c.1)

**Goal:** Predictable, resource-safe Gradle sync with timeout, cancellation, and debounce.

- **Progress:** Build file changes now debounce in `GrailsWorkspaceService.didChangeWatchedFiles()` and trigger explicit Gradle sync for the affected project context.
- **What:** Predictable, exclusively-owned Gradle sync pipeline with debouncing, timeouts, and clean cancellation.
- **Why:** Prevents CPU starvation, Gradle daemon connection pool exhaustion, and concurrent sync file-lock collisions during rapid user edits.
- **When:** Triggered on `build.gradle` edits (after debounce), or terminated upon reaching a 30-second execution timeout.
- **How (Actions):**
  1. Coalesce rapid build file edits: implement a 2-second debounce for build file watch events to prevent Gradle sync storms.
  2. Implement a 30-second timeout on `gradle.getGrailsProjectAsync()`.
  3. On timeout, trigger cancellation. **Validate Gradle Tooling API cancellation behavior:** verify whether native `CancellationTokenSource` successfully terminates the daemon thread/socket, or if connection disposal is required to prevent daemon process leaks.
  4. **Connection Ownership:** Each sync operation exclusively owns its `ProjectConnection`. `ProjectConnection` objects are never shared across concurrent sync operations.
  5. Retain last-known-good (`LKG`) model if sync fails or times out.
  6. Gradle error (non-timeout) -> transition `ProjectState` to `FAILED` and emit user diagnostics.

---

### 5. Memory Lifecycle & Tier-2 Exit Criteria (Phase 3c.2)

**Goal:** Safe resource eviction and stable recovery from memory pressure.

- **What:** Controlled degradation to Tier-2 (regex/name-only search) and safe recovery exit criteria.
- **Why:** Prevents server crashes on massive workspaces. Exit criteria prevent infinite OOM oscillation loops (GC thrashing) where the server repeatedly crashes immediately after recovering.
- **When:** Degrades upon catching `OutOfMemoryError`. Recovers only when cooldown timer and heap threshold checks pass.
- **How (Actions):**
  1. **Classloader Release Guarantees:** Implement clean hibernation by explicitly nullifying/severing all paths from `ProjectContext` to `GroovyClassLoader`, `CompilationUnit`, and the AST graph to guarantee garbage collection of compiled class nodes.
  2. **Snapshot Reference Rules:**
     - **Strong references:** `activeSnapshot` and `LKG snapshot`.
     - **Soft references:** Strictly limited to historical lineage only.
  3. Refactor OOM recovery in `GrailsService.handleOOM()` to evict all caches, run GC, and follow an explicit escalation path:
     - **OOM #1:** Enter Tier 2 (name-only search).
     - **OOM while in Tier 2:** Hibernate all non-active projects across the workspace.
     - **OOM after global hibernation:** Reject expensive operations entirely and emit fatal diagnostics.
  4. **Tier-2 Exit Criteria:** Implement cool-down duration (e.g., 60 seconds) and heap check threshold (e.g., <75% heap usage) to prevent OOM oscillation before returning to Tier 0/1 operations.
