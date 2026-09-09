# Architecture Improvement Plan

**Date:** 2026-06-03
**Last updated:** 2026-06-14
**Status:** Active
**Execution Mode:** ARCHITECTURE DESIGN MODE

> **Product planning update (2026-09-08):** Use the [product roadmap](product-roadmap.md) for delivery priorities and the [implementation handoff](implementation-handoff.md) for current test evidence. This document retains technical history; historical completion labels do not establish release readiness. Product releases R0–R6 are distinct from the architecture phase numbers below.

## Overview

This document outlines the strategic roadmap for improving the `vscode-gng-support` architecture. The plan shifts focus away from superficial structural changes (like folder organization) and concentrates heavily on **state management, cache correctness, compiler lifecycle discipline, and architectural decoupling**.

The plan is divided into chronological phases, starting with a foundational definition of state ownership, followed by prioritizing correctness and stability before scaling to multi-project support.

### Constraint Compliance

This plan is governed by the [LSP Reasoning Guide & Constraint-Based Planner](grails-lsp-reasoning-guide.md).

**ACTIVE_CONSTRAINT_SET:**
- Scope: Universal + Target (Architecture Design)
- Hard Invariant: System must remain correct under stale state, partial failure, concurrent mutation
- Authoritative Documents: `server/RULES.md` > `client/RULES.md` > `CODING_STANDARDS.md` > `state-and-lifecycle-specification.md` > reasoning guide

---

## Architectural Findings & Resolved Problems

The following critical issues were identified and addressed during codebase validation:

1. **AST Service Memory Leak (Grails Artifact Map Duplication) — Resolved** ✅
   - **Problem:** `ASTService` caches `ClassNode` references by normalized URI in `grailsServices`, `grailsControllers`, and `grailsTagLibs`. However, when a file was recompiled, the old class nodes for that URI were never evicted. The visitor merely appended new class nodes, causing memory leaks and query pollution in `findInjectedService()`.
   - **Fix:** Added `clearUri(String uri)` in `ASTService` and integrated eviction in `GrailsASTVisitor.visitSourceUnit` to clear state before traversing new source units.
   - **State classification:** Derived state (AST cache). Invalidation trigger: document change event. Owner: ASTService.

2. **ThreadSafeLruCache Shared Executor Lifecycle Risk — Resolved** ✅
   - **Problem:** `ThreadSafeLruCache` utilized a static single-threaded scheduled executor `cleanupExecutor` to trigger background cache expirations. The `shutdown()` method was static and shut down the executor globally, making it impossible to reuse the executor on server restarts or test cleanups.
   - **Fix:** Refactored `cleanupExecutor` to be a non-final static reference, dynamically recreated and scheduler-started via a thread-safe synchronized `getExecutor()` helper if null or previously shut down. Modified `shutdown()` to terminate the executor gracefully, reset the reference to null, and allow clean re-initialization on subsequent cache constructions. Added `ThreadSafeLruCacheSpec` covering multiple restarts.
   - **Failure mode:** Executor rejection on restart → now self-heals via lazy re-initialization.

3. **Incomplete Inter-File AST Invalidation — Resolved** ✅
   - **Problem:** While local AST state is evicted per file on recompilation, there was no reactive model to evict cross-file dependencies or caches referencing updated type symbols.
   - **Fix:** Added `clearCrossFileCaches()` in `GrailsService` and `clearSymbolCaches()` in `DiscoveryService`. Wired `clearCrossFileCaches()` to execute globally at the end of every incremental AST visitation cycle (`compileAndVisitAST`), ensuring that all completion caches and dynamically discovered methods (via `GroovyRuntimeIntegration`, etc.) are proactively evicted and strictly reflect the most recent cross-file AST relationships.
   - **Invalidation ownership:** Owner: GrailsService. Trigger: end of incremental AST cycle. Scope: full (all cross-file caches).

4. **GrailsIncrementalCompilerSpec — Resolved** ✅
   - **Problem:** `GrailsIncrementalCompilerSpec` existed but contained only a stub assertion (`true`). Zero regression protection.
   - **Fix:** Replaced with 7 robust integration tests verifying AST update, cross-file cache eviction, correct node removal, ASTService artifact cache rebuilding, diagnostic publication, isolated syntax error boundaries, and position-based lookup post-incremental edits.

---

## Phase 0: State & Lifecycle Architecture (The Foundation) ✅

Before modifying code, we must define the theoretical model for state in the language server. Everything else in this plan becomes easier once this specification exists.

1.  **Create the State & Lifecycle Architecture Specification** ✅
    *   **Action:** Write a dedicated, definitive architectural document that formally dictates state boundaries, ownership, and behavior under stress.
    *   **Must Define:**
        *   **State ownership hierarchy & Index ownership**: Explicit rules detailing which components exclusively own memory, ASTs, and the global symbol index.
        *   **Lifecycles & State transitions**: Explicit definitions for Workspace, Project, Compiler, AST, Index, and Cache lifecycles, including all valid state machine transitions.
        *   **Consistency semantics**: Precise guarantees about what state an LSP request observes when executing concurrently with an active compilation (e.g., read-uncommitted vs. snapshot isolation).
        *   **Cancellation guarantees**: Strict rules on how `CompletableFuture` cancellation halts work without leaving partial or corrupted state.
        *   **Backpressure strategy**: How the server responds to and throttles an overwhelming volume of incoming LSP requests or rapid file change events.
        *   **Failure recovery model**: How the server recovers from out-of-memory (OOM) errors, compiler crashes, or invalid Gradle models.
        *   **Observability & Performance SLOs**: How metrics will be tracked to validate architectural improvements, alongside hard Service Level Objectives (SLOs) for compilation latency, response times, and memory limits.
    *   **Constraint check:** State classification (§2), invalidation ownership (§7), failure model (§9) — all satisfied by state-and-lifecycle-specification.md.

2.  **Early Multi-Root & Memory Ownership Assessment** ✅
    *   **Action:** Document exactly how the memory boundaries defined above will scale to true multi-root isolation (multi-project configurations) before any decoupling work begins.

---

## Phase 1: Stabilization & Correctness (Immediate Priority) ✅

Before decoupling or adding complex features, the foundation must be mathematically sound, automatically verified, and adhere to the Phase 0 specification.

1.  **Enable Server Tests in CI** ✅
    *   **Action (done):** OOM resolved — `jvmArgs '-Xmx2g'` + `systemProperty 'grails.lsp.test.classgraph.disabled', 'true'` added to `build.gradle`.
    *   **Action (done):** CI (`ci.yml`) runs `./gradlew clean build --stacktrace` which includes the `test` task by default.

2.  **Formalize AST Ownership & Cache Invalidation** ✅
    *   **Action:** Implement the rules defined in Phase 0. Establish a single source of truth for AST and Index state.
    *   **Action:** Ensure `didChange` and `didClose` events deterministically evict stale ASTs and indexes before any provider attempts to read them.
    *   **Invalidation ownership:** AST → document change. Index → AST change. Cache → end of incremental cycle.

3.  **Audit Concurrency and Cancellation** ✅
    *   **Action:** Apply the Request Consistency and Cancellation guarantees from Phase 0 to all LSP handlers.
    *   **Action:** Prevent providers from observing a partially rebuilt AST state if a request fires during incremental compilation.
    *   **Cancellation model:** `CancellationService` (SERVER-017) provides URI-scoped tokens. `checkCancellation()` at yield points. `didClose` auto-cancels.

4.  **Validate Incremental Compilation Correctness** ✅
    *   **Action:** Replaced `GrailsIncrementalCompilerSpec` stub with 7 robust integration tests.
    *   **Action:** Verified AST update, cross-file cache eviction, correct node removal, ASTService artifact cache rebuilding, diagnostic publication, isolated syntax error boundaries, and position-based lookup post-incremental edits.

---

## Phase 2: Decoupling & Modularity (Taming the Monolith) ✅

With tests passing and caches formalized, reduced the gravitational pull of the `GrailsService` "God Object" by introducing a pure `ProjectIndex` architecture layered with local caches.

**Dependency graph for Phase 2:**
```text
Phase 1 (✅) → Phase 2a (Infrastructure) ✅
                  → Phase 2b (Migration: Hover, Definition, References) ✅
                       → Phase 2c (Shadow Validation) ✅
                            → Phase 2d (CompletionProvider) ✅
```

### State model for Phase 2 components:

| Component | Classification | Owner | Invalidation Trigger | Scope |
|-----------|---------------|-------|---------------------|-------|
| `ProjectIndex` | Derived state | IndexManager (new) | AST change, Gradle change | Partial (per-file) or Full (Gradle) |
| `MethodScopeCache` | Cached state | Per-file scope | Document change | Full (per-file eviction) |
| `GroovydocCache` | Cached state (LRU) | GroovydocCache | Eviction policy (LRU) + AST change | Partial (per-symbol) |
| `SymbolInfo` | Derived state | ProjectIndex | Index rebuild | N/A (immutable value) |

### Degradation tiers for Phase 2:

| Feature | Tier 0 | Tier 1 | Tier 2 | Tier 3 |
|---------|--------|--------|--------|--------|
| Hover | Full type + Groovydoc from ProjectIndex | Type info only (no Groovydoc) | Name-only from symbol table | Raw text under cursor |
| Definition | Resolved via ProjectIndex | Heuristic file-scan fallback | Name-based grep | None |
| References | Full cross-file from ProjectIndex | Single-file references only | Name-based grep | None |
| Completion | Live AST via ExpressionContext | ProjectIndex fallback | Name-only completions | None |

### Phase 2a: Infrastructure ✅

*   **Action:** Built `ProjectIndex` for classes, methods, fields, and properties. Implemented `ResolutionAccuracy` (`STATIC`, `HEURISTIC`, `UNRESOLVED`) for references to support dynamic Groovy code.
*   **Action:** Built `MethodScopeCache` for fast, per-file local variable and parameter resolution without thrashing the global index on every keystroke.
*   **Action:** Built `GroovydocCache` (lazy, LRU-backed) to prevent massive memory overhead from storing documentation in `SymbolInfo`.

### Phase 2b & 2c: Migration & Validation ✅

*   **Action:** Migrated `HoverProvider`, `DefinitionProvider`, and `ReferencesProvider`. They query `ProjectIndex` -> `MethodScopeCache`, and use the `GroovydocCache`.
*   **Action:** Ensured `GrailsService` mutable state is still available as an emergency fallback, guarded by a feature flag.

### Phase 2d: Completion Provider Last ✅

*   **Action:** `CompletionProvider` stays on the live AST via the `ExpressionContext` escape hatch. It is too dynamic to force through the index prematurely.

---

## Phase 3: Multi-Project & Lifecycle Mastery (Scaling Up) 🟡

Once the core is decoupled and stable, expand the server's capabilities to handle complex, real-world workspace configurations.

**Dependency:** Phase 2c complete (providers decoupled from monolithic AST). ✅

### State model transition:
Phase 3 introduces `VersionedSnapshot` as defined in the state-and-lifecycle-specification.md. This replaces the Phase 1-2 model of shared mutable `GrailsService` state.

1.  **Implement Proper Multi-Root Workspace Support** 🔴
    *   **Action:** Transition the architecture from single-root (`workspaceFolders[0]`) to true multi-root.
    *   **Action:** Introduce explicit workspace and project state boundaries. Ensure state ownership is isolated so cross-project contamination is impossible.
    *   **Invalidation ownership:** Each `ProjectContext` owns its own snapshot lineage. Cross-project invalidation only via explicit Gradle dependency graph edges.

2.  **Review Gradle Synchronization Lifecycle** 🔴
    *   **Action:** Audit how the server reacts to `build.gradle` changes. Ensure dependency updates and project model rebuilds have predictable, atomic effects on the server state.
    *   **Failure mode:** Gradle hang → timeout + retain last-known-good snapshot. Invalid model → lock workspace, prompt user.

3.  **Strengthen Memory Lifecycle Management** 🔴
    *   **Action:** Enforce the memory ownership rules defined in Phase 0.
    *   **Action:** Implement memory boundaries or soft references where appropriate to prevent uncontrolled memory growth in long-running language servers and facilitate failure recovery.

---

## Phase 4: Developer Experience & Maintenance (Housekeeping) 🔴

Address lower-priority maintenance tasks that improve the contributor experience.

**Dependency:** No hard dependency on Phase 3. Can run in parallel after Phase 2a.

1.  **Align Documentation with Implementation** 🔴
    *   **Action:** Update the main `README.md` to accurately reflect current capabilities (e.g., remove claims of multi-root support until Phase 3 is complete).

2.  **Streamline Local Development Workflow** 🔴
    *   **Action:** Create a unified dev script (e.g., a concurrent runner in `package.json`) that watches both the TypeScript client and Groovy server, auto-compiling and copying artifacts without manual intervention.

3.  **Fix Architectural Inconsistencies** 🔴
    *   **Action:** Clean up typos and minor structural deviations that do not pose structural risks but add cognitive load for contributors.

---

## Phase 5: Semantic Intelligence & Refactoring (IntelliJ-Class Direction) 🔴

With the foundational state management proven at scale, the architecture shifts to providing deep, framework-aware intelligence and safe refactoring.

**Dependency:** Phase 3 complete (VersionedSnapshot model active, multi-root working).

### Degradation tiers for semantic features:

| Feature | Tier 0 | Tier 1 | Tier 2 | Tier 3 |
|---------|--------|--------|--------|--------|
| Rename | Full transactional rename via semantic model | Single-file rename only | None | None |
| GORM navigation | Full entity relationship traversal | Property-level only | Name grep | None |

1.  **Design Semantic Model Architecture** 🔴
    *   **Action:** Build a semantic layer representing Grails concepts (Controllers, Services, GORM) as first-class entities. Prevent this model from becoming a new monolithic God Object.
    *   **State classification:** Derived state. Owner: SemanticModelService (new). Invalidation: Index change.

2.  **Introduce Refactoring Transaction Framework** 🔴
    *   **Action:** Implement transactional validation and commit semantics for Rename, Move, and Safe Delete operations.

3.  **Add Semantic Consistency Verification** 🔴
    *   **Action:** Ensure Definition, References, Hover, Completion, and Rename all resolve the exact same symbol identities to guarantee feature consistency.

---

## Excluded / De-prioritized Items

Based on architectural review, the following items are intentionally deprioritized to focus on system stability:
*   Restructuring the `UseCase` folder hierarchy (state ownership matters more).
*   Modifying bootstrap initialization order (current state is acceptable).
*   "Open-source readiness" metadata (does not impact technical stability).

---

## Task Backlog — Prioritized

> **Last updated:** 2026-06-16
> Order reflects both urgency and dependency chain. Do not start a task until its upstream is ✅.

### ✅ Complete

| # | Task | Dependency | Status | Notes |
|---|---|---|---|---|
| 1 | **Fix test infrastructure + CI** | None | ✅ | Server tests pass in CI. `DiscoveryServiceSpec` stable. |
| 2 | **Define/enforce state ownership** | #1 | ✅ | Formal read/write boundaries per component via `astLock`, `clearCrossFileCaches()`, `CancellationService`. |
| 3 | **Incremental compilation correctness** | #2 | ✅ | 7 integration tests replacing stub. Cross-file cache eviction verified. |
| 5 | **GrailsService decomposition (ProjectIndex)** | #2, #3 | ✅ | Phase 2 completed. Built `ProjectIndex`, `MethodScopeCache`, `GroovydocCache`. Migrated providers. |

### 🟡 In Progress

| # | Task | Dependency | Status | Notes |
|---|---|---|---|---|
| 7 | **Multi-root workspace support** | #5 | 🟡 | Phase 3 transition. Requires introducing `VersionedSnapshot` model and transitioning away from shared mutable `GrailsService` state. |

### 🔴 Blocked / Future

| # | Task | Dependency | Status | Notes |
|---|---|---|---|---|
| 4 | **Stable symbol identities** | #5 | 🔴 | Consistent node IDs across Definition / References / Hover / Rename. Requires ProjectIndex to provide stable IDs. |
| 6 | **Gradle lifecycle correctness** | #3 | 🔴 | Correct build-tool integration (sync, invalidation, daemon). Less user-visible than compilation bugs but important for reliability. |
| 8 | **Semantic model** | #5, #7 | 🔴 | Grails-domain entities (Controller, Service, GORM) as first-class LSP nodes. Must not become a new God Object. |
| 9 | **Refactoring transactions** | #4, #8 | 🔴 | Transactional Rename / Move / Safe Delete with rollback. Needs stable symbol IDs (#4) and semantic model (#8). |
