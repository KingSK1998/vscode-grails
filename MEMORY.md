# Session Memory — Agent Optimization

## Decisions Made & Patterns Established

[... same as previous ...]

9. **Multi-Project & Lifecycle Architecture (Phase 3)**:
   - Established **Single Source of Truth** per request via `VersionedSnapshot`. All LSP handlers are now bound to an immutable snapshot of the workspace (AST, Index, Gradle Metadata) at request entry, preventing state-drift mid-calculation.
   - Implemented **Compilation Commit Protocol**: Compilers now work on private state and atomically "commit" new snapshots only after validation. This decouples long-running index rebuilds from instant-response LSP lookups.
   - **WorkspaceManager Isolation**: Replaced monolithic maps in `GrailsService` with a proper workspace router. Each project root owns its own `ProjectContextImpl`, ensuring strict isolation between multiple workspace folders.
   - **Stateless Completion Engine**: Completion strategies are now completely stateless, returning `List<CompletionItem>` instead of mutating a request object. Reflection-based strategy loading was rejected in favor of explicit wiring in `CompletionBuilder` for transparency and type safety.
   - **Memory Lifecycle (Hibernation)**: Projects now support a `hibernate()` state where compiler and visitor caches are explicitly dropped to free up memory when projects are removed or system resources are low.

10. **Agentic Knowledge Base System (KB)**:
    - Established an **Agent-Readable Knowledge Base** to move from "vibe coding" to structured agentic engineering.
    - Created **`docs/invariants.md`** defining explicit state ownership, ID contracts (durable vs mutable), state consistency rules with severity ratings (CRITICAL/HIGH/MEDIUM), and an invariant review checklist.
    - Established **`docs/failure-modes.md`** as a Failure Mode Registry indexing past bugs by component and invariant rule with a Status lifecycle (Active/Resolved/Historical) and a 9-category Failure Test Matrix.
    - Documented active and past architectural decisions in **`docs/adr/decisions.md`** with status lifecycles (Proposed/Accepted/Superseded/Deprecated).
    - Created **`docs/architecture/system-map.md`** as the first file read by new agents to understand data flow, state ownership, provider tiers, and architectural risk zones.
    - Defined a clear maintenance protocol in **`docs/architecture/change-triggers.md`** to list required KB updates per change.
    - Backlogged hypothetical design proposals in **`docs/architecture/backlog.md`** (e.g. `VersionedSnapshot`) until empirical evidence or benchmarks justify promotion.
    - Automated drift prevention by updating **`scripts/pre-commit-guard.js`** to warn on missing KB updates (e.g. `GrailsService` changes without `invariants.md` edits, new caches/providers, bug-fix commit messages without `failure-modes.md` updates).
    - Embedded the **Invariant Review Checklist** and KB update checks directly into the agent-facing **`docs/skills/review.md`** skill.

11. **Project reactivation & concurrency locking**: Integrated thread-safe reactivation lock via `activationFuture` and `ensureActivated()` in `ProjectContextImpl` to prevent duplicate compiler instantiation under concurrent requests.
12. **LRU Memory budgeting and eviction**: Added automatic memory eviction to `WorkspaceManager` that keeps at most 2 active projects READY, evicting the least recently used to HIBERNATED state. Wired the check to trigger both on workspace access and when project states transition to READY.
13. **Snapshot manager lineage tracking**: Created `SnapshotManager` to maintain versioned snapshot lineage with a maximum of 3 historical records. Used SoftReferences to hold history so the JVM can reclaim them under memory pressure, while guaranteeing LKG is preserved on compilation/sync failures.
14. **Groovy Direct Field Access in Lifecycle Methods**: Used `this.@compiler` and
    `this.@visitor` inside `hibernate()` and `dispose()` as a consistency discipline —
    bypasses Groovy's property dispatch unconditionally, protecting lock-guarded
    nullification from any future getter side effects introduced in subclasses or refactors.

## Previous Session Priorities (superseded by the 2026-09-08 handoff below)

1. **Gradle Sync Audit & Debouncing (Phase 3c.1)**:
   - Implement 2-second debounce on build.gradle watches, a 30-second timeout, and proper connection pool cancellation.
2. **Memory Escalation Path (Phase 3c.2)**:
   - Implement 3-stage OOM escalation path (Tier 2 -> Global Hibernation -> Rejected Operations) and cool-down duration exit criteria.
3. **Dependency Invalidation — Gradle Integration (Phase 3b.3)**:
   - Integrate actual Gradle dependency graph resolution into `projectDependsOn()`.
   - Test with real multi-project Gradle workspace.
4. **Integration Test Suite**:
   - Write integration tests for reactivation under concurrent edits, dependency propagation, and Gradle timeouts.

## Decisions Made & Patterns Established (2026-06-24)

15. **Dependency Graph Invalidation Tests (Phase 3b.3)**: Created `DependencyInvalidationSpec` with 6 comprehensive tests covering:
    - Dependency detection by name match
    - Dependency detection by jar classpath containment
    - Independent project isolation (no false positives)
    - Dirty flag propagation on upstream snapshot commit
    - Cycle protection in circular dependency graphs (A->B->C->A)
    - All tests passing — validates `WorkspaceManager.propagateInvalidation()` BFS traversal with visited-set cycle detection.

## Product direction and interrupted implementation handoff (2026-09-08)

- The user confirmed **Grails 7+ / Groovy 4 first**, then explicitly requested a complete roadmap and handoff because usage limits interrupted coding. The latest task is documentation/strategy; runtime work remains unfinished.
- [docs/product-roadmap.md](docs/product-roadmap.md) now defines the intended 1.0 product, human and agent workflows, project-derived framework discovery, performance/storage targets, UI direction and task IDs R0–R6. These are plans, not implementation claims.
- Strategic direction: maintain one local analysis engine used by the IDE and agent tools. Provide installed-version API evidence, Grails relationships, conservative change impact and reproducible checks. Keep deterministic IDE features useful without a model or hosted account. Evaluate agent benefits against the same agent with ordinary search/build access; future relevance is a hypothesis to measure.
- Introduce useful editor agent reads in R3 after query correctness, without waiting for every UI/refactoring feature. Add MCP portability when typed operations stabilize. No custom completion-model training or mandatory chat/cloud layer is planned for 1.0.
- [docs/implementation-handoff.md](docs/implementation-handoff.md) is the execution entry point. **Next task: R0-01**, repair interrupted document scheduling/project publication and pass the affected tests before expanding scope.
- Current saved reports: `GrailsIncrementalCompilerSpec` has 7 tests / 7 failures (`ProjectState` cast to `AtomicReference`); `DocumentCompilationSpec` has 6 tests / 3 failures (map `.empty` assertions). Reports were read on 2026-09-08, not rerun. The earlier passing compiler baseline predates these changes.
- Client startup/build changes and five passing headless tests exist from the preceding session. Real JVM smoke reproduced stdout logging corruption; stderr source fix exists but final JAR/VSIX validation remains pending.
- Initial workspace discovery remains a TODO, folder add/remove remains incomplete, and the startup agent stopped at a usage limit before implementation. Preserve all existing edits. No commit/push/publish was performed.
- Corrections to older memory: current compiler code combines workspace classpaths, and snapshot map copies do not prove deep AST immutability or classloader release. Earlier broad “strict isolation”/“complete” claims require revalidation; do not use them as acceptance evidence.

## Agent execution contracts and specifications (2026-09-08)

- The user requested actionable documentation/specs so other agents can continue from the roadmap and preserve invariants. This session delivered documentation plus two Node execution-tool files; it did not repair or revalidate client/server runtime behavior.
- [docs/agent-execution.md](docs/agent-execution.md) is now the continuing-work entry point. The roadmap, README and AGENTS point there. Run `node scripts/roadmap.js next`; current selection is **R0-01**, queued/unassigned with existing partial code. The earlier implementation handoff is baseline evidence, not a permanently fixed next-task instruction.
- [docs/execution/task-queue.json](docs/execution/task-queue.json) is the single acceptance-status/dependency ledger. All 38 roadmap IDs have [acceptance cards](docs/execution/task-specifications.md); R0-R5 are admitted, R6 deferred. Cards contain numbered cases, source starting points, spec links and invariant mappings. Records preserve evidence and interruption recovery. Reopening a task also requeues accepted/active transitive dependents for reverification.
- Added specs for library discovery, performance, agent operations, IDE/embedded workflows and validation; replaced the old state/lifecycle proposal with an explicit required contract and current evidence gaps. Fixed grammar keywords remain legitimate; framework/library API inventories need resolved-artifact evidence, capability guards and negative removal/isolation tests.
- ADR-009 reconciles GrailsService composition with per-project ownership; ADR-004/005 retain historical clauses marked superseded. Actual provider constructors take ProviderContext/WorkspaceManager and capture RequestContext; do not copy obsolete three-context wiring. ADR-008 remains reserved for a withdrawn snapshot proposal.
- Retention must preserve usable active/LKG facts while releasing compiler/classloader generations after bounded request leases drain. Shallow AST copies, live field nullification and SoftReference alone are not proof. R1-04 must select/prove the concrete mechanism; no automatic deep-AST-copy rewrite is directed.
- Query freshness and completeness are separate. Project/source-set/document/open/dependency/config revisions constrain results; unknown dynamic behavior never becomes certain refactor evidence. New agent adapters reuse typed operations rather than another index.
- Reconciled system map, architecture overview, invariants, rules, review/performance/planning/recovery skills and old reasoning-guide status. Planning/diagnostic stages return to authorized implementation; routine fixes do not require repetitive permission requests.
- Verification: selector focused tests **12/12 passed**, actual queue validates **38 tasks**, and `next` selects R0-01. Direct Node test invocation worked after `node --test` child-worker spawn EPERM. Link/anchor and independent contract review evidence is in [docs/execution/documentation-baseline.md](docs/execution/documentation-baseline.md). No commit, push, publish or new product build/test was performed for this documentation session.

## R0-01 and R0-02 Execution Milestone (2026-09-09)

- **R0-01 Completed & Accepted**:
  - Repaired document compilation scheduler: nonblocking open, rapid edit coalescing, copied buffer immutability, blocked worker ordering.
  - Resolved `ProjectState` to `AtomicReference` runtime cast error under `@CompileStatic` by using direct field accessor `this.@state.get()`.
  - All 9/9 tests in `DocumentCompilationSpec` and 7/7 tests in `GrailsIncrementalCompilerSpec` passed.
  - Documented in `docs/execution/records/R0-01.md`.

- **R0-02 Completed & Accepted**:
  - Implemented prompt capability return on `initialize` supporting 0 and multiple workspace roots without blocking on Gradle.
  - Implemented asynchronous discovery triggering on `initialized` LSP notification via `WorkspaceManager.initializeRoots()`.
  - Implemented root generations in `WorkspaceManager` with in-flight discovery tracking to discard late results from removed or re-added roots.
  - Enhanced path normalization (`TextFile.normalizePath`) to handle Windows backslashes, percent-encoded URIs, and root boundary containment (`isSameOrChildPath`).
  - Enforced strict project routing in `WorkspaceManager.getProjectForUri`: returns `null` for unmatched external URIs without falling back to a default project.
  - Implemented `replayStillOpenBuffers` on newly registered roots, safely filtering to only still-open buffers belonging to that root.
  - Updated build-watch debounce in `GrailsWorkspaceService` and `ActivationManager.ts` to cover Kotlin scripts (`build.gradle.kts`, `settings.gradle.kts`), properties (`gradle.properties`), and version catalogs (`*.versions.toml`), unioning all changed URIs across debounce windows.
  - Designed resilient single-threaded scheduler lifecycle in `GrailsWorkspaceService` with `getOrCreateScheduler()` to prevent `RejectedExecutionException` across test/restart cycles.
  - All 6/6 tests in `WorkspaceLifecycleSpec` and full server regression suite (48/48 tests across 7 specs) passed with exit code 0.
  - Client checks (`npm run compile`, `npm run check-types`, `npm run lint`) passed with exit code 0.
  - Documented in `docs/execution/records/R0-02.md`, updated `task-queue.json` and `STATUS.md` files. Validated with `node scripts/roadmap.js validate`.

## R0-03, R0-04, and R0-05 Execution Milestone (2026-09-09)

- **R0-03 Completed & Accepted**:
  - Validated real bundled stdio transport; protocol framing on stdout strictly compliant with JSON-RPC.
  - Configured Logback to direct logging to stderr and size-bounded rolling file appender (`~/.grails-lsp/grails-lsp.log`).
  - Wired `GrailsLanguageClient` remote proxy via `LSPLauncher.Builder` supporting custom notification `grails/allProjects`.
  - Attached client notification handlers prior to startup launch to eliminate early message loss race conditions.
  - Real smoke test (`node scripts/smoke-server.js`) passed in 1403 ms (exit code 0): initialized fixture, discovered project, opened unsaved buffer, received diagnostics and document symbols, and exited cleanly with zero lingering processes.
  - Documented in `docs/execution/records/R0-03.md`. Committed in `9979f99`.

- **R0-04 Completed & Accepted**:
  - Implemented cross-platform bundler `scripts/bundle.js` using `esbuild.buildSync` Node API, producing a deterministic standalone ~1018 KB CommonJS bundle (`client/out/extension.js`).
  - Implemented cross-platform cleanup `scripts/clean.js` invoking `gradle.js clean` and removing build outputs and `.vsix` artifacts without shell dependency.
  - Added obsolete/legacy JAR pruning in `scripts/copy-server.js` to ensure deterministic single-JAR packaging in `client/server/`.
  - Enhanced Java executable resolution in `serverConfig.ts` and `smoke-server.js` to support surrounding quotes, whitespace trimming, and spaces in `JAVA_HOME`.
  - Added unit test suite `client/src/test/serverConfig.unit.test.ts` and package audit test `scripts/package.test.js` (verifying 24 runtime assets and 0 forbidden assets via `@vscode/vsce.listFiles`).
  - Successfully built VSIX package (`vscode-gng-support.vsix`), installed into isolated extension directory with `code --install-extension`, and verified packaged server JAR execution via smoke test (exit code 0, 921 ms).
  - Aligned CI workflow `.github/workflows/ci.yml` with `npm run test:smoke` and unified package output path.
  - Documented in `docs/execution/records/R0-04.md`. Committed in `9a2c4a2`.

- **R0-05 Completed & Accepted**:
  - Reconciled all Phase 0 baseline evidence across `docs/architecture/system-map.md`, `docs/invariants.md`, `docs/failure-modes.md`, `server/STATUS.md`, `client/STATUS.md`, and `MEMORY.md`.
  - Verified that all R0-01 through R0-04 acceptance cases reflect actual passing tests on the current tree.
  - Resolved failure modes ST-001, ST-002, ST-003, and CC-001 with documented test evidence.
  - Indexed and preserved open failure modes for upcoming phases: CC-002 (R1-01: Document queue bounds and fairness), CC-003 (R1-04: Concurrent reader visibility and AST snapshot isolation), and DP-001 (R2-01/R2-03: Classpath isolation and resolved Gradle edges).
  - Strictly enforced invariant boundaries: documented that AST copies are not deep immutable ASTs and project contexts do not yet isolate classpaths.
  - Queue validation confirmed passing (38 tasks). Phase 0 is complete. Next milestone: **Phase 1: Concurrency & Lifecycle Correctness** starting with **R1-01**.

## R1-01 Document Scheduling Milestone (2026-09-09)

- `GrailsTextDocumentService` owns one active candidate, bounded URI/version tickets and one scheduled dispatcher task. Provisional limits are 256 pending tickets, 32 ordinary tickets per root, 256 KiB conservatively estimated ticket metadata and 4 MiB automatic source input; R1-05 owns measurement and tuning.
- Pending work retains no source text. The worker copies the latest `FileContentTracker` buffer only when a ticket becomes active. Same-URI notifications coalesce, active work is cooperatively superseded and completion during overload waits on the global recovery barrier.
- Ready roots alternate. Overflow recovery scans outside notification locks, partitions current inputs by root, interleaves them, and admits at most 256 candidates per pass. Handled URI/version pairs prevent unchanged buffers from being recompiled.
- Close work may exceed the ordinary per-root cap while remaining within global bounds. Global close overflow collapses to one marker; `WorkspaceManager` reconciles current buffers with `ProjectContextImpl`'s committed overlay URI set on the worker, avoiding an unbounded tombstone collection.
- Root removal removes routing and enters DISPOSING immediately. It cancels queued work, invalidates active publication and releases context resources asynchronously after the active document future drains.
- Focused evidence passes: `DocumentCompilationSpec` 15/15, `WorkspaceLifecycleSpec` 8/8 and `ReactivationAndLruSpec` 4/4. The full 290-test run has 23 provider expectation failures in completion/inlay/rename/signature suites; do not describe the full server suite as green until those are resolved or rebaselined.
- R1-02 remains next: received-order incremental edits, UTF-16, obsolete versions, open generations and stale diagnostics/publication.

## R1-02 Revision Ordering and Publication Safety Milestone (2026-09-10)

- `FileContentTracker.didChangeFile` applies sequential `contentChanges` in array order without sorting, strictly adhering to LSP 3.17 specification.
- `PositionHelper.getOffset` and `PositionHelper.getPosition` fixed to correctly handle empty files `""` at position `(0, 0)` -> offset 0. Verified CRLF (`\r\n`), standalone `\r`, and UTF-16 surrogate pairs (2 code units for astral plane characters/emojis).
- Monotonic `AtomicLong generationSequence` in `FileContentTracker` generates `openGeneration` per `didOpenFile`. Obsolete versions (`version <= currentVersion` within the same open generation) and obsolete open generations are rejected.
- `GrailsTextDocumentService` tracks `HandledRevision` (both `openGeneration` and `version`), checking revision validity during scheduling, execution, and work recovery.
- `ProjectContextImpl` enforces atomic validation of live open generation and version before compiling, before AST visiting, and immediately before `commitSnapshotLocked()`. Stale candidate snapshots are discarded.
- `GrailsDiagnosticService` checks open generation and version before and after diagnostics generation, and attaches LSP 3.15+ `version` parameter in `PublishDiagnosticsParams` so clients reject out-of-order diagnostic publications.
- Distinct lifecycles established for closing an overlay vs deleting a file: closing restores disk-backed facts while `didDeleteFile` / `deleteDocument` permanently purges tracked buffers, compilation units, AST visitors, and project index state.
- Focused tests passing: `PositionHelperSpec` (46/46), `FileContentTrackerSpec` (18/18), `RevisionAndPublicationSpec` (4/4), `DocumentCompilationSpec` (15/15), and `ReactivationAndLruSpec` (4/4). Full test suite completed 309 tests with 23 pre-existing provider expectation failures unchanged. Client checks (`check-types`, `lint`, `test:client`, `test:smoke`) passed 100%.
- Next roadmap task: **R1-03** (Bound Gradle synchronization and retain usable state).

