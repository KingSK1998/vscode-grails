# Phase 3: Multi-Project & Lifecycle Mastery — Detailed Implementation Plan

**Execution Mode:** ARCHITECTURE DESIGN MODE
**Status:** 🟡 ACTIVE (2026-06-16)
**Last updated:** 2026-06-16

**ACTIVE_CONSTRAINT_SET:**
- Scope: Universal + Target
- Hard Invariant: §0.1
- Version Binding: §3 (NOW ACTIVE — Phase 3+ target)
- All Universal rules from Phase 2 carry forward

**Dependency:** Phase 2c complete (providers decoupled from monolithic AST).

---

## Dependency Graph

```text
Phase 2c (✅ required)
  └─► Phase 3a: VersionedSnapshot infrastructure
        ├─► Phase 3a.1: VersionedSnapshot record
        ├─► Phase 3a.2: Compilation Commit Protocol
        └─► Phase 3a.3: Snapshot binding in providers
              └─► Phase 3b: Multi-root workspace isolation
                    ├─► Phase 3b.1: WorkspaceManager
                    ├─► Phase 3b.2: Per-project ProjectContext
                    └─► Phase 3b.3: Cross-project isolation tests
                          └─► Phase 3c: Gradle lifecycle
                                ├─► Phase 3c.1: Gradle sync audit
                                ├─► Phase 3c.2: Timeout + fallback
                                └─► Phase 3c.3: Memory lifecycle
```

---

## Phase 3a: VersionedSnapshot Infrastructure

### STEP 1 — VersionedSnapshot Record

```
Layer: Server
Goal: Create immutable VersionedSnapshot bundling AST, Index, GradleModel atomically
READS: Existing IndexSnapshot, AST output, GradleModel
WRITES: New VersionedSnapshot type definition
Active rules: §2 (state model), §3 (version binding — NOW ACTIVE)
Failure mode: N/A (type definition only)
Validation: Immutable record. No ASTNode references (server/RULES.md §0.1).
Confidence: High
```

**Actions:**
1. Create `VersionedSnapshot.groovy`:
   ```groovy
   @CompileStatic
   record VersionedSnapshot(
       long version,
       IndexSnapshot index,
       GradleModel gradleModel,        // GAP-08 FIX: required for Single-Snapshot Principle
       Map<String, String> fileHashes, // uri → content hash (for staleness detection)
       long timestamp
   ) {}
   ```
2. **GAP-08 FIX:** `GradleModel` is mandatory in VersionedSnapshot per state-and-lifecycle-spec §2. Providers must never use index from version N with GradleModel from version N-1. ASTs compiled against one dependency graph must only be queried with the matching GradleModel.
3. Version is monotonically increasing counter per ProjectContext.
4. `fileHashes` enables version-safe cache lookups (§12 allowed violations).

**State classification:** Derived state. Immutable after construction. Owned by ProjectContext.

---

### STEP 2 — Compilation Commit Protocol

```
Layer: Server
Goal: Implement atomic compile → index → commit → publish cycle
READS: Compiler output, IndexBuilder
WRITES: ProjectContext.activeSnapshot (AtomicReference swap)
Active rules: §0.1 (correctness under concurrent mutation), §3 (version binding), §6 (lazy invalidation)
Failure mode: Compilation fails → retain last-known-good snapshot. Log error. Publish error diagnostics.
Validation: Readers always see complete snapshot (old or new, never partial). AtomicReference guarantees this.
Confidence: High
```

**Actions:**
1. Refactor `compileAndVisitAST` to produce candidate snapshot:
   ```
   compile → visit AST → [release write lock] → build index → create VersionedSnapshot → atomic commit
   ```
2. **GAP-10 FIX — Write lock scope is compile+visit ONLY:**
   ```groovy
   // Inside write lock: compile + visit only
   withWriteLock {
       compiler.compileSourceFile(textFile)
       def sourceUnit = compiler.getSourceUnit(textFile)
       visitor.visitSourceUnit(sourceUnit)
       // Capture class nodes before releasing lock
       classNodes = visitor.getClassNodes(uri)
   }
   // OUTSIDE write lock: index build (can take hundreds of ms)
   indexManager.rebuildFile(uri, classNodes)
   clearCrossFileCaches()
   diagnostics.publishDiagnosticsForFile(uri)
   ```
   This means concurrent hover/completion requests are not blocked during index build.
3. Failed compilation: candidate discarded, old snapshot retained.
4. Syntax errors during typing: produce valid snapshot with partial AST + error diagnostics (per state-and-lifecycle-spec §3).
5. Add `ProjectContext.commitSnapshot(VersionedSnapshot)` — atomic swap via AtomicReference.

**Invalidation ownership:**
- Owner: GrailsService (compilation pipeline)
- Trigger: `didChange` event
- Scope: Per-file (incremental) or Full (workspace refresh)
- Strategy: Eager (compile immediately, commit atomically)

---

### STEP 3 — Provider Snapshot Binding

```
Layer: Server
Goal: Providers receive snapshot reference at request start, use it throughout
READS: ProjectContext.activeSnapshot
WRITES: Nothing (providers are read-only)
Active rules: §3 (version binding), §0.1, §8 (cancelable)
Failure mode: Snapshot is stale → acceptable (§0.1 allows stale reads). Provider produces stale-but-correct result.
Validation: Single-Snapshot Principle (state-and-lifecycle-spec §2): provider never mixes versions.
Confidence: High
```

**Actions:**
1. Add to BaseProvider: `protected VersionedSnapshot captureSnapshot()` — grabs current snapshot at request entry.
2. Provider methods call `captureSnapshot()` once, use that snapshot for entire request.
3. No more `visitor.getClassNodes()` calls mid-request (eliminates version mixing).
4. Fallback: if snapshot is EMPTY, fall back to live AST (Phase 2 fallback path preserved).

---

## Phase 3b: Multi-Root Workspace Isolation

### STEP 4 — WorkspaceManager

```
Layer: Server
Goal: Create WorkspaceManager owning 1..N ProjectContexts
READS: Workspace folders from LSP initialize params
WRITES: Creates/destroys ProjectContext instances
Active rules: §1 (architecture separation), §2, §7 (invalidation ownership)
Failure mode: Project init fails → that ProjectContext enters FAILED state. Other projects unaffected.
Validation: No cross-project state leakage. Each ProjectContext owns its own snapshot lineage.
Confidence: Medium (significant refactor of GrailsService)
```

**Actions:**
1. **GAP-09 FIX — Define memory budget BEFORE WorkspaceManager is built:**
   - Define `ProjectContextMemoryBudget`: max heap fraction per project (e.g., 40% for single project, 20% per project for 3-project workspace).
   - Enforce max concurrent ACTIVE ProjectContexts (default: 2). Beyond this, least-recently-used ProjectContext is **hibernated**: compiler released, visitor released, IndexSnapshot kept in SoftReference.
   - Hibernation policy: project becomes active again on next file open/request for that project URI.
   - Document memory cost: single GrailsCompiler ≈ 200-400MB depending on project size.
2. Create `WorkspaceManager.groovy` — replaces `projects` map in GrailsService.
3. Each workspace folder → one `ProjectContextImpl` instance (implements ProjectContext interface).
4. `ProjectContextImpl` owns: `ProjectIndex`, `IndexManager`, `MethodScopeCache`, `GroovydocCache`, `GrailsCompiler` instance, `GrailsASTVisitor` instance.
5. GrailsService becomes thin orchestrator delegating to WorkspaceManager.
6. LSP request routing: `WorkspaceManager.getProjectForUri(uri)` → correct ProjectContext (activates from hibernation if needed).

**Invalidation ownership:**
- Owner: WorkspaceManager
- Trigger: `workspace/didChangeWorkspaceFolders` event
- Scope: Per-project (add/remove project)
- Strategy: Eager (create/destroy ProjectContext on event)

---

### STEP 5 — Cross-Project Isolation Tests

```
Layer: Server
Goal: Verify zero state leakage between projects
READS: Multiple ProjectContext instances
WRITES: Test assertions only
Active rules: §0.1, §2
Failure mode: N/A (test only)
Validation: Symbols from Project A never appear in Project B queries. Index, cache, AST all isolated.
Confidence: High
```

**Actions:**
1. `WorkspaceIsolationSpec`:
   - Two mock projects with overlapping class names.
   - Verify `getSymbolAt()` returns project-specific results.
   - Verify `evictFile()` in one project doesn't affect another.
   - Verify compiler failure in one project doesn't degrade another.

---

## Phase 3c: Gradle Lifecycle & Memory

### STEP 6 — Gradle Sync Audit

```
Layer: Server
Goal: Define predictable, atomic Gradle sync behavior
READS: build.gradle, Gradle Tooling API
WRITES: GradleModel in ProjectContext
Active rules: §7 (invalidation), §9 (failure model)
Failure mode: Gradle hang → 30s timeout → retain last-known-good model. Gradle error → FAILED state, prompt user.
Validation: Gradle sync never blocks LSP request thread. Always async via backgroundExecutor.
Confidence: Medium
```

**Actions:**
1. Add timeout to `gradle.getGrailsProjectAsync()`: 30s default, configurable.
2. **GAP-11 FIX — Explicit cleanup on Gradle timeout:**
   - On timeout: cancel the Gradle Tooling API `BuildLauncher`/`ModelBuilder` connection explicitly (call `connection.close()` or interrupt the future).
   - Log: `[GRADLE] Sync timeout after 30s. Daemon may be unresponsive. Retained last-known-good model.`
   - Emit user-visible diagnostic: "Gradle sync timed out. Please check Gradle daemon status."
   - Do NOT auto-retry. Wait for next `build.gradle` file change event.
   - If executor thread pool is full (prior timeout thread still alive): log warning, emit diagnostic.
3. Gradle error (non-timeout) → ProjectContext state machine enters FAILED → emits diagnostic → waits for file change to retry.
4. `build.gradle` change → triggers full reindex (IndexManager.rebuildAll + new VersionedSnapshot with new GradleModel).

**Invalidation ownership:**
- Owner: GradleService
- Trigger: `build.gradle` file change (watcher), explicit user command
- Scope: Full (entire project reindex)
- Strategy: Eager with timeout

---

### STEP 7 — Memory Lifecycle

```
Layer: Server
Goal: Enforce memory boundaries, prevent unbounded growth
READS: Current memory usage metrics
WRITES: Cache eviction, SoftReference wrappers
Active rules: §9 (failure recovery), §10 (degradation)
Failure mode: OOM → evict all caches → GC → degrade to Tier 2 (name-only). Log recovery event.
Validation: Memory ceiling defined. Recovery doesn't crash server.
Confidence: Medium
```

**Actions:**
1. Add memory budget per ProjectContext: configurable max heap fraction.
2. GroovydocCache: already LRU-bounded.
3. IndexSnapshot: use SoftReference for older snapshots (only active snapshot is strong ref).
4. OOM recovery: `GrailsService.handleOOM()` → evict all caches, trigger lazy rebuild on next request.

---

## Final Validation (Phase 3)

- [ ] Version binding active (§3): All computations bound to snapshot version.
- [ ] Layer separation: Server only.
- [ ] Multi-root isolation: Each project owns independent state.
- [ ] Gradle failure handled: Timeout + last-known-good.
- [ ] Memory bounded: LRU caches + SoftReferences + OOM recovery.
- [ ] All failure modes defined.
- [ ] No hidden mutation in read paths.
