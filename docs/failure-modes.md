# Failure Mode Registry

> **Status:** ACTIVE — add entries within 30 minutes of discovering a bug.
> **Owner:** @kingsk (sole maintainer)
> **Last updated:** 2026-06-23
> **Validation triggers:** Bug fixed, architecture change
> **Rule:** Every agent surprise → one entry here. No exceptions.

---

## Index

### By Component

| Component | Failure Modes |
|---|---|
| GrailsService | CI-001 |
| ASTService | SM-001 |
| ThreadSafeLruCache | RL-001 |
| ProviderRegistry / BaseProvider | PC-001 |
| GrailsIncrementalCompilerSpec | AL-001 |

### By Invariant

| Invariant | Failure Modes |
|---|---|
| `INV-OWN-001` | PC-001 |
| `INV-OWN-004` | SM-001, CI-001, RL-001 |
| `INV-STATE-002` | PC-001 |
| `INV-STATE-003` | CI-001 |

---

## How To Add An Entry

```markdown
### XX-NNN: Short Title
- **Status:** Active | Resolved | Historical
- **Bug:** What happened
- **Invariant violated:** INV-XXX-NNN
- **Root cause:** Why
- **Fix:** What was done
- **Test:** Test name or "NONE — ADD ONE"
- **Detected by:** How found
- **Related ADRs:** ADR-NNN
- **Date:** YYYY-MM-DD
```

### Entry Lifecycle

| Status | Meaning | Transition when |
|---|---|---|
| **Active** | Fixed but pattern could recur | Default for new entries |
| **Resolved** | Root cause eliminated by architecture change | ADR or structural change prevents recurrence |
| **Historical** | Reference only. Skip during review | 6+ months, no recurrence, architecture moved past |

---

## Categories

| Category | Covers |
|---|---|
| State Mutation | Unauthorized writes to shared state |
| Cache Invalidation | Stale data served after state change |
| Concurrency | Race conditions, deadlocks, partial visibility |
| AST Lifecycle | Compiler/visitor ordering, null AST references |
| Provider Contract | Tier violations, missing cancellation/health |
| Resource Leak | Memory not freed, executors not shut down |

---

## State Mutation

### SM-001: AST Service Memory Leak — Artifact Map Duplication
- **Status:** Resolved
- **Bug:** `ASTService` cached `ClassNode` refs by URI. On recompilation, old nodes never evicted → memory leak + query pollution.
- **Invariant violated:** `INV-OWN-004`
- **Root cause:** No eviction in visitor pipeline. `visitSourceUnit` appended without clearing.
- **Fix:** Added `clearUri(String uri)` in `ASTService`. Eviction wired into `GrailsASTVisitor.visitSourceUnit`.
- **Test:** `GrailsIncrementalCompilerSpec` — "ASTService artifact cache rebuilding"
- **Detected by:** Architecture review (Phase 1)
- **Related ADRs:** ADR-006
- **Date:** 2026-06-03

---

## Cache Invalidation

### CI-001: Incomplete Cross-File AST Cache Invalidation
- **Status:** Resolved
- **Bug:** Local AST evicted per-file, but cross-file caches (`DiscoveryService`, completion) had no invalidation trigger. Stale data after cross-file changes.
- **Invariant violated:** `INV-STATE-003`
- **Root cause:** Invalidation was file-scoped only. No cross-file trigger on incremental compile.
- **Fix:** Added `clearCrossFileCaches()` in `GrailsService`, `clearSymbolCaches()` in `DiscoveryService`. Wired at END of incremental cycle.
- **Test:** `GrailsIncrementalCompilerSpec` — "cross-file cache eviction"
- **Detected by:** Architecture review (Phase 1)
- **Related ADRs:** ADR-004
- **Date:** 2026-06-03

---

## Resource Leak

### RL-001: ThreadSafeLruCache Static Executor Lifecycle
- **Status:** Resolved
- **Bug:** Static scheduled executor for cache expiration. `shutdown()` killed it globally — executor rejection on restart.
- **Invariant violated:** `INV-OWN-004`
- **Root cause:** Static final executor, no re-initialization path.
- **Fix:** Non-final static ref with lazy `getExecutor()` re-init. `shutdown()` terminates + nulls. Next access recreates.
- **Test:** `ThreadSafeLruCacheSpec` — multiple restart cycles
- **Detected by:** Test failure on server restart
- **Related ADRs:** ADR-002
- **Date:** 2026-06-03

---

## AST Lifecycle

### AL-001: Stub Test — Zero Regression Protection
- **Status:** Resolved
- **Bug:** `GrailsIncrementalCompilerSpec` contained only `expect: true`. Zero protection for most critical pipeline.
- **Invariant violated:** Testing mandate (CODING_STANDARDS §Testing)
- **Root cause:** Placeholder never filled in.
- **Fix:** Replaced with 7 integration tests covering AST update, cache eviction, node removal, diagnostics, syntax error isolation, position lookup.
- **Test:** `GrailsIncrementalCompilerSpec` (7 cases)
- **Detected by:** Architecture review
- **Related ADRs:** —
- **Date:** 2026-06-03

---

## Provider Contract

### PC-001: Old Constructor Pattern Leaking GrailsService
- **Status:** Resolved
- **Bug:** Providers used `private final GrailsService service` directly. Had access to write methods they must never call.
- **Invariant violated:** `INV-STATE-002`, `INV-OWN-001`
- **Root cause:** Pre-architecture pattern carried forward. No enforcement.
- **Fix:** All T1 providers migrated to `BaseProvider(ProviderContext, CompilationContext, ProjectContext)`. Old field removed.
- **Test:** `@CompileStatic` catches direct state access at compile time
- **Detected by:** Architecture review
- **Related ADRs:** ADR-005
- **Date:** 2026-06-07

---

## Failure Test Matrix — Use Before Every Feature

| Category | What To Test | Example |
|---|---|---|
| **Null/Empty** | Required fields absent | LSP request with no URI |
| **Invariant violations** | Contract broken | Provider writing to `visitor` |
| **External failure** | Dependency down | Gradle crash mid-sync |
| **State transition** | Out of order | Completion before `setupWorkspace()` |
| **Concurrency** | Simultaneous ops | Two completions + `didChange` |
| **Data mutation** | Mutable as key | AST node identity as cache key |
| **Resource exhaustion** | Limits exceeded | 50 rapid `didChange` events |
| **Cancellation** | Mid-op abort | `didClose` during completion |
| **Recovery** | Post-failure state | Provider state after OOM |

### How To Use

1. Give agent this matrix + feature spec BEFORE implementation
2. Agent generates one test per category
3. Review — extend with one case agent missed
4. Run tests — all should fail (not implemented yet)
5. Implement feature
6. Run tests — all should pass
