# Phase 2: Decoupling & Modularity — Detailed Implementation Plan

**Execution Mode:** ARCHITECTURE DESIGN MODE
**Last updated:** 2026-06-14

**ACTIVE_CONSTRAINT_SET:**
- Scope: Universal + Target
- Hard Invariant: §0.1 (stale state, partial failure, concurrent mutation)
- Architecture Separation: §1 (Server layer only — no client changes)
- State Model: §2 (all data classified)
- Version Binding: §3 (Target only — Phase 3+ enforcement)
- Dependency Graph: §6 (lazy invalidation, not pipeline)
- Invalidation Ownership: §7 (owner, trigger, scope, strategy)
- Cancellation: §8 (all computations cancelable)
- Failure Model: §9 (fallback, degraded, recovery)
- Degradation Ladder: §10 (tiers declared per feature)

---

## Dependency Graph

```text
Phase 1 (✅)
  └─► Phase 2a: Infrastructure
        ├─► Phase 2a.1: GroovydocCache gate check
        ├─► Phase 2a.2: IndexBuilder (AST → SymbolInfo extraction)
        ├─► Phase 2a.3: ProjectIndex population + commit wiring
        └─► Phase 2a.4: MethodScopeCache population
              └─► Phase 2b: HoverProvider migration
                    └─► Phase 2c: Shadow validation + Definition/References migration
                          └─► Phase 2d: CompletionProvider (stays on live AST)
```

---

## Phase 2a: Infrastructure

### STEP 1 — GroovydocCache Gate Check

```
Layer: Server
Goal: Verify getGroovydoc() works in current Groovy 4.0.23 compiler config
READS: GrailsCompiler output, GroovyDoc AST metadata
WRITES: None (observation only)
Active rules: §9 (failure model), §10 (degradation)
Failure mode: getGroovydoc() returns null/empty → GroovydocCache disabled, hover degrades to Tier 1
Validation: No state mutation. No cross-layer dependency.
Confidence: Medium (Groovy 4 Groovydoc support uncertain)
```

**Actions:**
1. Write a Spock test: compile a Groovy file with Javadoc comments, check `MethodNode.getGroovydoc()` returns content.
2. If returns content → proceed with GroovydocCache.
3. If returns null → mark GroovydocCache as disabled. HoverProvider Tier 1 (type+signature only). Document in STATUS.md.

**Drift check:** No concept redefinition. AST/Index/Cache meanings unchanged.

---

### STEP 2 — IndexBuilder: AST → SymbolInfo Extraction

```
Layer: Server
Goal: Build IndexBuilder that walks AST and produces List<SymbolInfo> per file
READS: GrailsASTVisitor (ClassNodes, MethodNodes, FieldNodes, PropertyNodes per URI)
WRITES: Creates new SymbolInfo records (immutable values — no state mutation)
Active rules: §0.1 (correctness under stale), §2 (derived state), §7 (invalidation ownership)
Failure mode: AST partially parsed → IndexBuilder produces partial SymbolInfo list (Tier 1). Never crashes.
Validation: No ASTNode references in output (server/RULES.md §0.1). All SymbolInfo fields are primitives/LSP types.
Confidence: High
```

**Actions:**
1. Create `IndexBuilder.groovy` in `kingsk.grails.lsp.index` package.
2. Static utility class (TIER 2 — no dependencies, no instance state).
3. Methods:
   - `static List<SymbolInfo> buildSymbols(String uri, List<ClassNode> classNodes)` — walks class/method/field/property nodes.
   - `static String buildDescriptor(ClassNode, MethodNode|FieldNode)` — generates stable symbol ID (e.g., `com.example.BookController#index()`).
   - `static List<LocalSymbolInfo> buildLocals(String uri, List<MethodNode> methods)` — extracts local variables and parameters per method scope.
4. **AST boundary rule enforcement:** Extract `name`, `range`, `returnType`, `signature`, `modifierFlags` as primitives. Zero `ASTNode` references in output.
5. Test: `IndexBuilderSpec` — verify SymbolInfo produced from sample ClassNodes matches expected descriptors, ranges, kinds.

**Invalidation ownership:**
- Owner: IndexBuilder (stateless — produces output, doesn't own it)
- Trigger: Called by IndexManager after AST change
- Scope: Per-file (one URI at a time)
- Strategy: Eager (called immediately after AST visit)

**Drift check:** IndexBuilder is TIER 2 static utility. No state. No drift possible.

---

### STEP 3 — IndexManager: Wiring IndexBuilder → ProjectIndex

```
Layer: Server
Goal: Create IndexManager that orchestrates index rebuilds and commits snapshots to ProjectIndex
READS: GrailsASTVisitor (classNodes per URI), current IndexSnapshot
WRITES: ProjectIndex.commit(newSnapshot) via compareAndSet
Active rules: §0.1, §6 (lazy invalidation graph), §7 (invalidation ownership), §8 (cancelable)
Failure mode: IndexBuilder fails for one file → skip that file, retain previous symbols for it in snapshot. Log warning.
Validation: Commit is atomic via compareAndSet retry. Readers see old or new snapshot, never partial.
Confidence: High
```

> [!WARNING]
> **GAP-01 FIX:** IndexManager is a **service-layer component**, NOT TIER 1. It must NOT extend `BaseProvider`. Extending BaseProvider would couple IndexManager to ProviderContext (ErrorService, HealthService, etc.), preventing isolated testing and blocking WorkspaceManager's per-project isolation in Phase 3.

> [!WARNING]
> **GAP-02 FIX:** Plain `AtomicReference.set()` has an ABA race: two threads both read `snapshot_v1`, build independent `snapshot_v2_A` and `snapshot_v2_B`, and one silently overwrites the other. Use `compareAndSet` with retry loop.

**Actions:**
1. Create `IndexManager.groovy` in `kingsk.grails.lsp.index` — **service-layer component** (no BaseProvider, no ProviderContext).
2. Constructor takes `ProjectIndex`, `MethodScopeCache`, `GroovydocCache` only. Visitor passed per-call.
3. Methods:
   - `void rebuildFile(String uri, List<ClassNode> classNodes)` — receives classNodes directly from caller (no visitor access needed)
   - `void rebuildAll(Map<String, List<ClassNode>> allNodes)` — full workspace rebuild
   - `void evictFile(String uri)` — removes symbols for a file, commits new snapshot
4. **Snapshot merge with CAS retry loop (fixes GAP-02):**
   ```groovy
   void rebuildFile(String uri, List<ClassNode> classNodes) {
       def newSymbols = IndexBuilder.buildSymbols(uri, classNodes)
       IndexSnapshot expected, next
       do {
           expected = projectIndex.snapshot
           def newByDescriptor = new HashMap<>(expected.byDescriptor)
           expected.byUri[uri]?.each { newByDescriptor.remove(it.descriptor) }
           newSymbols.each { newByDescriptor[it.descriptor] = it }
           def newByUri = new HashMap<>(expected.byUri)
           newByUri[uri] = Collections.unmodifiableList(newSymbols)
           next = new IndexSnapshot(newByDescriptor, newByUri)
       } while (!projectIndex.compareAndCommit(expected, next))
       // Also update MethodScopeCache outside the CAS loop
       methodScopeCache.evict(uri)
       methodScopeCache.putLocals(uri, IndexBuilder.buildLocals(uri, classNodes))
   }
   ```
5. **Add `compareAndCommit` to ProjectIndex:**
   ```groovy
   boolean compareAndCommit(IndexSnapshot expected, IndexSnapshot next) {
       currentSnapshot.compareAndSet(expected, next)
   }
   ```
6. **Wiring point:** IndexManager.rebuildFile(uri, classNodes) called from `GrailsService.visitASTInternal()` after visitor completes. Called OUTSIDE the write lock — index build happens after AST lock is released (see GAP-10 fix in Phase 3).

**Invalidation ownership:**
- Owner: IndexManager
- Trigger: `compileAndVisitAST` (per-file), `refreshAndReindexWorkspace` (full)
- Scope: Partial (per-file) or Full (workspace rebuild)
- Strategy: Eager (index rebuilt immediately after AST visit)

**Drift check:** AST = input to IndexBuilder. Index = derived output. Cache = not involved here. No redefinition.

---

### STEP 4 — MethodScopeCache Population

Incorporated into Step 3's `rebuildFile()` CAS loop. See GAP-02 fix above — MethodScopeCache update happens after the CAS succeeds, ensuring the local scope cache always reflects the committed index.

**Invalidation ownership:**
- Owner: MethodScopeCache (per-file scope)
- Trigger: Document change (via IndexManager.rebuildFile)
- Scope: Full per-file eviction
- Strategy: Eager (evict then repopulate within same call)

---

### STEP 5 — GroovydocCache (Conditional)

```
Layer: Server
Goal: Build lazy LRU cache for Groovydoc content, keyed by symbol descriptor
READS: Compiler output (GroovyDoc metadata from AST)
WRITES: Internal LRU cache state only
Active rules: §2 (cached state), §7 (invalidation), §9 (failure)
Failure mode: Groovydoc unavailable → return null, hover degrades to Tier 1
Validation: LRU eviction prevents unbounded memory growth. AST change invalidates relevant entries.
Confidence: Medium (depends on Step 1 gate)
```

**Actions:**
1. Create `GroovydocCache.groovy` in `kingsk.grails.lsp.index` — wraps `ThreadSafeLruCache<String, String>`.
2. Key: symbol descriptor (from SymbolInfo). Value: rendered markdown string.
3. `getGroovydoc(String descriptor, Closure<String> loader)` — cache-aside pattern.
4. **GAP-03 FIX:** Maintain a reverse index `Map<String, Set<String>> uriToDescriptors` for O(1) eviction:
   ```groovy
   void putGroovydoc(String descriptor, String uri, String content) {
       cache.put(descriptor, content)
       uriToDescriptors.computeIfAbsent(uri, { [] as Set }).add(descriptor)
   }
   void evictFile(String uri) {
       uriToDescriptors.remove(uri)?.each { cache.invalidate(it) }
   }
   ```
5. Wire into IndexManager: `groovydocCache.evictFile(uri)` during `rebuildFile()`.

**Invalidation ownership:**
- Owner: GroovydocCache
- Trigger: AST change (via IndexManager), LRU eviction
- Scope: Partial (per-symbol)
- Strategy: Lazy (computed on first access, evicted on AST change or LRU pressure)

---

### STEP 6 — Wire ProjectIndex into GrailsService

```
Layer: Server
Goal: Add ProjectIndex + IndexManager + MethodScopeCache + GroovydocCache to GrailsService constructor
READS: None
WRITES: GrailsService constructor wiring
Active rules: §1 (server layer), §7 (ownership)
Failure mode: None (wiring only)
Validation: All new components are server-layer. No client or protocol changes. No cross-layer dependency.
Confidence: High
```

**Actions:**
1. Add fields to GrailsService: `projectIndex`, `indexManager`, `methodScopeCache`, `groovydocCache`.
2. Initialize in constructor after `visitor` and `compiler`.
3. Wire `indexManager.rebuildFile(uri, classNodes)` call in `visitASTInternal()` after `visitor.visitSourceUnit()` — OUTSIDE the write lock (classNodes captured before lock release).
4. Wire `indexManager.rebuildAll()` call in `refreshAndReindexWorkspace()` after `visitor.visitCompilationUnit()` — outside write lock.
5. Expose `projectIndex` via `CompilationContext` interface (add `ProjectIndex getProjectIndex()`).
6. **GAP-05 FIX — Wire eviction in didClose:** Add to `GrailsTextDocumentService.didClose()`:
   ```groovy
   service.indexManager.evictFile(textFile.uri)
   service.methodScopeCache.evict(textFile.uri)
   ```
   This prevents zombie symbols surviving file close/delete.

---

## Phase 2b: HoverProvider Migration

### STEP 7 — Migrate HoverProvider to ProjectIndex

```
Layer: Server
Goal: HoverProvider queries ProjectIndex instead of live AST for symbol info
READS: ProjectIndex.snapshot, MethodScopeCache, GroovydocCache
WRITES: Nothing (zero state mutation)
Active rules: §0.1, §2, §8 (cancelable), §10 (degradation ladder)
Failure mode: ProjectIndex returns null → fall back to live AST via existing getNodeAtPosition() path
Validation: Read-only. No mutation. Feature flag guards fallback.
Confidence: High
```

**Actions:**
1. **GAP-06 FIX — Per-feature flags (not single global toggle):**
   - `config.hoverUsesIndex` (default: false) — enabled in Phase 2b
   - `config.definitionUsesIndex` (default: false) — enabled in Phase 2c
   - `config.referencesUsesIndex` (default: false) — enabled in Phase 2c
   - Allows independent rollback per feature. Prevents UX inconsistency when hover and definition are on different paths.
2. HoverProvider.provideHover():
   ```groovy
   if (config.hoverUsesIndex) {
       // Step 1: Check local scope first
       def local = methodScopeCache.getLocalAt(uri, position)
       if (local) return buildLocalHover(local)
       // Step 2: Check global index
       def symbol = projectIndex.snapshot.getSymbolAt(uri, position)
       if (symbol) return buildSymbolHover(symbol)
       // Step 3: Fallback to live AST
   }
   // Existing live AST path (unchanged)
   ```
3. `buildSymbolHover(SymbolInfo)` — renders type, signature, Groovydoc (from cache).
4. `buildLocalHover(LocalSymbolInfo)` — renders local variable info.

**Degradation tiers (Hover):**
- Tier 0: Full type + Groovydoc from ProjectIndex
- Tier 1: Type + signature only (no Groovydoc)
- Tier 2: Name-only from symbol table
- Tier 3: Raw text under cursor (live AST fallback)

---

### STEP 8 — Feature Flag + Fallback Wiring

```
Layer: Server
Goal: Add config toggle and ensure clean fallback to live AST
READS: GrailsLspConfig
WRITES: Config model only
Active rules: §9 (failure model), §12 (allowed violations)
Failure mode: ProjectIndex empty/stale → seamless fallback to live AST. No user-visible error.
Validation: Fallback path is the current working implementation. Zero regression risk.
Confidence: High
```

**Actions:**
1. Add per-feature flags to `GrailsLspConfig`: `hoverUsesIndex`, `definitionUsesIndex`, `referencesUsesIndex` (all default false).
2. Map to VS Code settings: `grails.experimental.hoverIndex`, `grails.experimental.definitionIndex`, `grails.experimental.referencesIndex`.
3. Log which path was taken per feature: `[HOVER] path=index|ast tier={0-3}`.

---

## Phase 2c: Shadow Validation + Provider Expansion

### STEP 9 — Shadow Mode for HoverProvider

```
Layer: Server
Goal: Run both paths (ProjectIndex + live AST) and compare results
READS: ProjectIndex.snapshot, live AST (via visitor)
WRITES: Telemetry log only (no state mutation)
Active rules: §0.1, §10 (degradation measurement)
Failure mode: Shadow comparison fails → log mismatch, serve live AST result. No user impact.
Validation: Shadow mode is read-only observation. No mutation.
Confidence: High
```

**Actions:**
1. When `config.shadowMode` enabled, run both paths and compare.
2. **GAP-04 FIX — Concrete match definition:**
   - Match = both paths return non-null hover content AND the primary symbol name extracted from both hover texts is identical
   - Implemented as: `extractSymbolName(indexResult) == extractSymbolName(astResult)`
3. Track via ProviderHealthService counters: `shadowMatches` / `shadowTotal`.
4. Log: `[SHADOW] MATCH={true/false} name={symbolName} indexTier={0-3} astNodeType={nodeType}`.
5. Gate: ≥ 95% match rate over minimum 200 requests before proceeding to Step 10.
6. Surface accuracy metric via `healthProvider.getShadowAccuracy()` command.

---

### STEP 10 — DefinitionProvider Migration

```
Layer: Server
Goal: Migrate DefinitionProvider to use ProjectIndex for symbol resolution
READS: ProjectIndex.snapshot (getSymbolAt → getSymbolByDescriptor for cross-file lookup)
WRITES: Nothing
Active rules: §0.1, §2, §8, §10
Failure mode: Symbol not found in index → fall back to live AST getDefinitionNode() path
Validation: Read-only. Fallback preserves existing behavior.
Confidence: High
```

**Actions:**
1. DefinitionProvider queries `projectIndex.snapshot.getSymbolAt(uri, pos)` to get SymbolInfo.
2. Uses `symbolInfo.fileUri` + `symbolInfo.selectionRange` to build Location.
3. Fallback: if SymbolInfo not found or fileUri null → existing live AST path.

**Degradation tiers (Definition):**
- Tier 0: Resolved via ProjectIndex descriptor lookup
- Tier 1: Heuristic file-scan fallback
- Tier 2: Name-based grep
- Tier 3: None (no result)

---

### STEP 11 — ReferencesProvider Migration

```
Layer: Server
Goal: Migrate ReferencesProvider to use ProjectIndex
READS: ProjectIndex.snapshot (scan all URIs for matching descriptors)
WRITES: Nothing
Active rules: §0.1, §2, §8, §10
Failure mode: Index incomplete → fall back to GrailsASTHelper.getReferences()
Validation: Read-only. Fallback preserves existing behavior.
Confidence: Medium (cross-file reference accuracy depends on index completeness)
```

**Actions:**
1. **GAP-07 FIX — ReferenceIndex is a distinct sub-step, not a footnote.**

   **Phase 2c.1 (prerequisite): ReferenceIndexBuilder**
   - Separate from `IndexBuilder` (declaration walker). `ReferenceIndexBuilder` walks method bodies for call sites, field accesses, type references.
   - Scope: Only static dispatch. Dynamic `methodMissing` / `propertyMissing` calls are UNRESOLVED (not indexed).
   - Estimated build cost: 3-5x longer than declaration indexing. Must be cancellable.
   - Add cancellation token parameter: `buildReferences(String uri, List<ClassNode> nodes, CancellationToken token)`
   - Test: `ReferenceIndexBuilderSpec` verifying call sites and field accesses tracked correctly

   **Phase 2c.2 (migration): ReferencesProvider uses ReferenceIndex**
   - Add to IndexSnapshot: `Map<String, List<ReferenceInfo>> referencesByDescriptor` (key = target descriptor, value = list of call site locations)
   - `ReferenceInfo` record: `(String uri, Range callSiteRange, String callerDescriptor)`
   - ReferencesProvider: symbol at position → descriptor → referencesByDescriptor lookup → return ReferenceInfo locations
   - Fallback: existing `GrailsASTHelper.getReferences()` path

**Drift check:** `ReferenceIndexBuilder` is new TIER 2 utility. `ReferenceInfo` is new immutable record. `IndexSnapshot` gains `referencesByDescriptor` field. All remain derived state. No concept redefinition.

---

## Phase 2d: CompletionProvider (Stays on Live AST)

### STEP 12 — Document Allowed Violation

```
Layer: Server
Goal: Explicitly document that CompletionProvider remains on live AST
READS: Live AST via ExpressionContext
WRITES: Nothing (documentation only)
Active rules: §12 (allowed violations)
Failure mode: N/A
Validation: Allowed violation conditions met: read-only, version-bound to current doc, no cross-layer mutation, no persistence.
Confidence: High
```

**Actions:**
1. Add comment block in `GrailsCompletionProvider` documenting the allowed violation.
2. CompletionProvider uses `ExpressionContext` which holds live AST reference — too dynamic for static index.
3. Future Phase 3+ may introduce `CompletionIndex` as an optimization layer.

---

## Final Validation (Phase 2)

- [x] Layer separation: All changes in Server layer. No Client or Protocol changes.
- [x] State classification: ProjectIndex (derived), MethodScopeCache (cached), GroovydocCache (cached), SymbolInfo (derived/immutable).
- [x] Invalidation ownership: All components have defined owner, trigger, scope, strategy.
- [x] Cancellation: All provider methods remain cancelable (existing CancellationService unchanged).
- [x] Failure modes: Every step defines fallback to live AST.
- [x] Degradation tiers: Hover, Definition, References, Completion all have declared tiers.
- [x] No hidden mutation: All new read paths are read-only. Write paths are IndexManager only.
- [x] AST boundary rule: No ASTNode in SymbolInfo, LocalSymbolInfo, or IndexSnapshot.
- [x] Dependency graph: Valid DAG, no cycles.
