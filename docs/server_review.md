# Server Architecture Review

**Date:** 2026-06-13  
**Scope:** Full server codebase, architecture docs, improvement plan, state spec

---

## TL;DR Verdict

**Strong foundation. Seriously impressive for a solo/small-team LSP server.** Clear tier system, disciplined provider pattern, real cancellation/health infra. Your docs are better than most production projects.

Biggest risks are all in the *gap between current implementation and Phase 3 spec* — you've designed a VersionedSnapshot future but the present still runs on shared mutable state with no formal isolation between reads and writes.

---

## 🟢 What's Working Well

### 1. Architecture Discipline
- **Tier system (T1/T2/T3)** — clean, enforceable, well-documented. Rare in Groovy codebases.
- **BaseProvider pattern** — single access path, `@CompileStatic` enforcement of `private _service`, context getters. Canonical form is good.
- **ProviderRegistry** — lazy init, reduces startup coupling. Good design.

### 2. Infrastructure Quality
- **CancellationService** — URI-scoped tokens, `didClose` auto-cancel, `CancellationToken.none` sentinel. Clean.
- **ProviderHealthService** — request count, latency, error rate per provider. Observability baked in early.
- **ThreadSafeLruCache** — TTL + size limits + stats. Not just a HashMap.
- **Debounce in TextDocumentService** — coalescing rapid edits, configurable delay. Correct approach.

### 3. Write Path Discipline
- Only 4 methods mutate shared state (`setupWorkspace`, `refreshAndReindexWorkspace`, `compileAndVisitAST`, `visitAST`). Well-defined.
- Providers genuinely read-only — rule enforced by `@CompileStatic` + `private`.

### 4. Context Interfaces
- `ProjectContext`, `ProviderContext`, `CompilationContext` — seams for future decoupling already in place. BaseProvider already wired to use them. Smart forward investment.

### 5. Documentation
- `server/RULES.md` — 433 lines of actionable rules, not vague principles. Best agent-facing server doc I've seen in an extension project.
- State & lifecycle spec — VersionedSnapshot design is theoretically sound, phase applicability notes are honest about current vs future state.

### 6. Test Coverage
- 33 test files. Spock specs for compiler, visitor, providers, services, utilities. `DiscoveryServiceSpec` at 22 tests.
- Integration test base (`BaseLspSpec`) exists with real compilation.

---

## 🟡 Concerns — Real But Manageable

### 1. `GrailsService` — God Object (You Know This)

**330 lines, 18 fields, 3 context interfaces, all state.** Your improvement plan (Phase 2, Task #5) correctly identifies this. Not alarming yet — but every new feature makes decomposition harder.

**Current risk:** `refreshAndReindexWorkspace()` does invalidate → compile → visit → diagnostics as a linear pipeline on `backgroundExecutor`. If a provider reads mid-pipeline, it sees partial state. Your spec calls this out (§2 "No Partial Visibility") but **current code does not enforce it**.

> [!IMPORTANT]
> This is the #1 correctness gap. A provider calling `visitor.getClassNodes(uri)` between `compiler.compileProject()` and `visitor.visitCompilationUnit(compiler)` reads stale data. No lock, no snapshot, no version gate.

### 2. `backgroundExecutor` = `newCachedThreadPool()` — Unbounded

```groovy
private final Executor backgroundExecutor = Executors.newCachedThreadPool()
```

Cached thread pool creates new threads without limit. Under heavy load (rapid `refreshAndReindexWorkspace` calls, many `executeCommand` calls), this can create dozens of threads. For a language server, **use a bounded pool** — 2-4 threads max for background work.

### 3. `GrailsWorkspaceService` Creates New Provider Instances Per Call

```groovy
// Line 94 — new instance every symbol() call
return new GrailsWorkspaceSymbolProvider(grailsService).provideWorkspaceSymbols(params.query)
```

`GrailsTextDocumentService` uses `ProviderRegistry` for lazy singletons (correct). But `GrailsWorkspaceService.symbol()` creates `new GrailsWorkspaceSymbolProvider` on every invocation. Should go through `ProviderRegistry` too.

### 4. Missing `@CompileStatic` on Two Key Classes

| Class | Has `@CompileStatic`? |
|---|---|
| `GrailsLanguageServer` | ❌ Missing |
| `GrailsWorkspaceService` | ❌ Missing |

Server RULES.md §1 says "on ALL classes." These two violate that rule. Dynamic dispatch in these classes means LSP handler errors surface at runtime, not compile time.

### 5. `didChange` Debounce — Race Condition Window

```groovy
compileTasks[uriString] = debounceExecutor.schedule({
    pendingChanges.remove(uriString)
    def latestTextFile = service.fileTracker.getTextFile(uriString)
    if (latestTextFile) {
        service.compiler.markDirty(latestTextFile.uri)  // <-- markDirty + compileAndVisitAST not atomic
        service.compileAndVisitAST(latestTextFile)
    }
} as Runnable, delay, ...)
```

If two debounced compiles for *different URIs* fire simultaneously on the cached thread pool, both call `compileAndVisitAST` → `compiler.compileSourceFile()` → `visitor.visitSourceUnit()`. The visitor is a single shared instance. Two concurrent `visitSourceUnit` calls can corrupt internal index maps. **The visitor needs synchronization or sequential execution guarantee.**

### 6. `projects` Map — Not Thread-Safe

```groovy
Map<String, GrailsProject> projects = [:]  // HashMap, not ConcurrentHashMap
```

`projects` is read from `getProjectForUri()` (any LSP thread) and written to in `refreshAndReindexWorkspace()` (background thread). This is a race. Should be `ConcurrentHashMap`.

### 7. Shutdown Order — Tasks May Cancel Before Executor Shuts Down

```groovy
void shutdown() {
    // 1. Shuts down backgroundExecutor
    // 2. fileTracker?.shutdown()
    // 3. document?.shutdown()        ← cancels debounce tasks
    // 4. cancellationService?.cancelAll()
}
```

`cancellationService.cancelAll()` should fire **first** — cancel in-flight requests before shutting down executors. Currently, background tasks may be mid-execution when cancellation fires. Reverse order: cancel first, then drain executors.

### 8. `lastSent` Map in `publishProject()` — Not Thread-Safe

```groovy
private final Map<String, ProjectDTO> lastSent = [:]  // HashMap
```

`publishProject()` called from `refreshAndReindexWorkspace()` on background thread. If multiple workspace refreshes overlap, `lastSent` has concurrent read/write on a HashMap.

---

## 🔴 Critical Gaps — Must Fix Before Phase 2

### 1. No Read-Write Isolation (The Big One)

**Current state:** Providers read `visitor.getClassNodes()` and `compiler.getSourceUnit()` at any time. Write path (`compileAndVisitAST`) mutates these concurrently. No locking, no snapshot, no version gate.

**Impact:** Stale completions, phantom definitions, intermittent NPEs during rapid typing. These are the bugs users notice but can't reproduce.

**Fix options (Phase 1):**
- **Quick:** Add a `ReentrantReadWriteLock` to `GrailsService`. Write path acquires write lock. Provider reads acquire read lock. Simple, correct, minimal change.
- **Better (Phase 3):** VersionedSnapshot as designed in your spec. But don't wait for Phase 3 to fix this — the read-write lock is a stepping stone.

### 2. No Error Boundary Around Provider Execution

`GrailsTextDocumentService` handler methods like `hover()`, `completion()`, etc. have **no try-catch**. If a provider throws (NPE, class cast, etc.), the exception propagates to LSP4J which:
- Returns an error response to the client
- But doesn't log the stack trace server-side

```groovy
// Current — no protection
CompletableFuture<Hover> hover(HoverParams params) {
    TextFile textFile = service.fileTracker.getTextFile(params.textDocument.uri)
    // ... direct call, no try-catch
    return hoverProvider.provideHover(params.textDocument, params.position)
}
```

Compare with `didOpen`/`didChange`/`didClose` — those all have try-catch + `errorService.handleError()`. The feature handlers don't. Add a uniform error boundary.

### 3. Test Package Structure Divergence

Tests exist in two different package trees:
- `kingsk.grails.lsp.providersDocument.*` (old naming, flat)
- `kingsk.grails.lsp.providers.document.*` (new naming, matches source)

This is confusing and some old specs may reference wrong package structure. Consolidate.

---

## 📊 Improvement Priority Matrix

Mapped against your existing improvement plan:

| Priority | What | Your Plan Ref | My Assessment |
|---|---|---|---|
| 🔴 P0 | Read-write lock on shared state | Phase 1 #3, Phase 0 §2 | **Do now.** 50-line change, prevents entire class of concurrency bugs |
| 🔴 P0 | Error boundaries on LSP handlers | Not in plan | **Add.** One wrapper method in BaseProvider or TextDocumentService |
| 🔴 P0 | `projects` → `ConcurrentHashMap` | Not in plan | **Trivial fix.** Change `[:]` to `new ConcurrentHashMap<>()` |
| 🟠 P1 | Bound the thread pool | Not in plan | `Executors.newFixedThreadPool(4)` — 1-line change |
| 🟠 P1 | `@CompileStatic` on GrailsLanguageServer + GrailsWorkspaceService | Not in plan | Your own rule violation |
| 🟠 P1 | Workspace symbol provider through registry | Phase 2 #1 | Consistency fix |
| 🟡 P2 | Shutdown order fix | Not in plan | Cancel → drain → close |
| 🟡 P2 | `lastSent` → `ConcurrentHashMap` | Not in plan | Same pattern as `projects` |
| 🟡 P2 | Test package consolidation | Phase 4 #3 | Housekeeping |
| ⚪ P3 | Visitor synchronization | Phase 1 #3 | Needed for true incremental safety |

---

## Architecture Plan Assessment

Your 5-phase plan is **correctly ordered and dependency-aware**. The Phase 0 state spec is theoretically excellent. My notes:

### What's Good About Your Plan
1. **Phase ordering respects dependencies** — won't decompose GrailsService (#5) before state ownership (#2). Smart.
2. **VersionedSnapshot design** — immutable snapshots, atomic commits, reader-writer isolation. Right target architecture.
3. **Honest about current state** — "Phase applicability" note in spec, "server/RULES.md governs Phase 1-2". No pretending you're already there.
4. **Backlog is correctly prioritized** — test infra first, state ownership second, decomposition third.

### What Needs Attention
1. **Gap between Phase 0 (spec) and Phase 1 (implementation)** — the spec describes VersionedSnapshot but Phase 1 tasks don't include *any* snapshot implementation. What's the Phase 1 consistency mechanism? A read-write lock? Document this explicitly.
2. **No Phase 1 SLO baselines** — Phase 0 §10 defines SLOs (Completion <100ms, etc.) but no task in Phase 1 establishes *current* baselines. You have `ProviderHealthService` — add a task to collect and log baseline metrics before optimizing.
3. **Semantic tokens registered but potentially incomplete** — `GrailsLanguageServer` registers semantic tokens capability but `STATUS.md` says "Not started" for semantic tokens. Advertising capabilities you can't fulfill causes client-side confusion.
4. **`didChangeWorkspaceFolders` calls `setupWorkspace` for added folders** but never calls anything for *removed* folders. When a workspace folder is removed, you leak its project state, compiler state, visitor entries, and diagnostics.

---

## Quick Wins — Can Do Today

1. **`projects = new ConcurrentHashMap<>()`** — 1-line fix
2. **`lastSent = new ConcurrentHashMap<>()`** — 1-line fix  
3. **`Executors.newFixedThreadPool(4)` for backgroundExecutor** — 1-line fix
4. **Add `@CompileStatic` to `GrailsLanguageServer` and `GrailsWorkspaceService`** — 2-line fix
5. **Handle removed workspace folders in `didChangeWorkspaceFolders`** — 5-line fix

---

## Summary Verdict

| Aspect | Grade | Notes |
|---|---|---|
| **Architecture design** | A | Tier system, BaseProvider, context interfaces — well thought out |
| **Documentation** | A+ | RULES.md, improvement plan, state spec — best I've seen for this type of project |
| **Current correctness** | B- | Concurrency gaps, no read-write isolation, unbounded threads |
| **Test coverage** | B | 33 specs exist, but integration tests are skeleton stubs |
| **Forward planning** | A | VersionedSnapshot target is right, phase ordering is correct |
| **Implementation risk** | Medium | Gap between spec (Phase 3) and reality (Phase 1) needs bridging work |

**Bottom line:** Your server is architecturally mature beyond its age. The improvement plan is honest and correctly ordered. The biggest risk isn't bad design — it's the concurrency gaps that exist *right now* while you build toward the VersionedSnapshot future. Bridge that gap with a read-write lock in Phase 1 and you'll be in great shape.
