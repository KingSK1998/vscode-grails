# Failure Mode Registry

> **Status:** ACTIVE — add entries within 30 minutes of discovering a bug.
> **Owner:** @kingsk (sole maintainer)
> **Last updated:** 2026-09-08
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
| Client startup / packaging | ST-001 |
| Stdio transport / Logback | ST-002 |
| Server workspace / Source duplication | ST-003 |
| Background document compilation / ProjectContextImpl | CC-001 |

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
- **Status:** Open | Active | Resolved | Historical
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
| **Open** | Observed unresolved bug; fix or required validation remains incomplete | Record owning roadmap task and current evidence |
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

## Startup and Interrupted Concurrency Work

### ST-001: Installed Extension Uses Development Startup Defaults
- **Status:** Active
- **Bug:** The manifest default selected a manual TCP server; Java configuration was ignored by the launcher, application/LSP port definitions collided, and initial project notifications could arrive before handlers were registered.
- **Invariant violated:** Client lifecycle/configuration ownership; `INV-STATE-006` fail-safe defaults.
- **Root cause:** Development defaults and post-start notification registration persisted into the production path.
- **Fix:** Source changes select the bundled JAR by default, honor the configured Java installation, separate ports and register project handlers before startup. Deterministic artifact copying was added.
- **Test:** Five headless tests passed in the preceding implementation session via `npm run test:client`; final packaged-editor acceptance remains pending.
- **Detected by:** September startup audit and failing regression tests.
- **Date:** 2026-09-08 (handoff recorded).

### ST-002: Logs Corrupt Stdio Protocol Framing
- **Status:** Resolved (verified in R0-03)
- **Bug:** A real bundled-JAR smoke test received colored log output where an LSP `Content-Length` header was required.
- **Invariant violated:** LSP transport contract: stdout must carry protocol messages only.
- **Root cause:** Logback console appender defaulted to stdout.
- **Fix:** Console target changed to `System.err`; rolling log retention bounded to 14 days; LSPLauncher builder configured with `.setRemoteInterface(GrailsLanguageClient)`.
- **Test:** `scripts/smoke-server.js` verified with code 0 in 1403 ms (SHA-256: `5B829F88B51443370BD00B0924D72CCBC4E65DBC2D70CEC58017F191B76A549A`).
- **Detected by:** Real JVM transport smoke test.
- **Date:** 2026-09-09.

### ST-003: Duplicate Source Directory Indexing via server/bin
- **Status:** Resolved
- **Bug:** VS Code reported 120 errors ("variable already in scope", "repetitive method name/signature") across Groovy classes.
- **Invariant violated:** `INV-STATE-002` single canonical source of truth for symbols.
- **Root cause:** `server/bin` directory contained a duplicated copy of Groovy source files generated by an external build, causing Groovy/Java compiler to index both `src/main/groovy` and `bin/main`.
- **Fix:** Removed `server/bin`, added `bin/` to `.gitignore`, added `"**/bin": true` to `.vscode/settings.json` `files.exclude`.
- **Test:** `.\gradlew.bat compileGroovy compileTestGroovy` passes with 0 errors.
- **Detected by:** VS Code Problems panel reporting 120 duplicate variable and method scope errors.
- **Date:** 2026-09-09.

### CC-001: Interrupted Background Compilation Migration
- **Status:** Open — unresolved (R0-01)
- **Bug:** Latest saved incremental suite has seven failures with `ProjectState` to `AtomicReference` cast errors. Three scheduler tests additionally use `.empty` map assertions that return null for an empty map.
- **Invariant violated:** `INV-STATE-004` publication correctness and the regression-test gate.
- **Root cause:** The interrupted writer identified Groovy property dispatch inside new project-lock closures; the saved XML confirms the cast. Verify exact stacks before repair. The map failures arise from property/key semantics in the tests.
- **Fix:** Pending R0-01; see [implementation handoff](implementation-handoff.md). Preserve `@CompileStatic` and behavioral assertions.
- **Test:** Saved reports dated 2026-09-07: `GrailsIncrementalCompilerSpec` 7/7 failed; `DocumentCompilationSpec` 3/6 failed. Read on 2026-09-08; not rerun during roadmap work.
- **Detected by:** Targeted tests during migration from inline to queued document compilation.
- **Date:** 2026-09-08.

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
