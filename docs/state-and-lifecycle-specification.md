# State & Lifecycle Architecture Specification

**Date:** 2026-06-03
**Status:** Core Architectural Standard

## 1. Core Philosophy & Ultimate Goal

*   **Correctness over availability:** No result is better than a wrong result. Developers can tolerate missing features temporarily; they cannot trust an IDE that confidently provides incorrect answers.
*   **Trust is the primary product:** Completion, navigation, diagnostics, references, and refactoring must be consistently correct or explicitly degraded.
*   **The LSP must be authoritative:** Every answer must be derived from committed, verifiable state.
*   **Degrade gracefully:** When correctness cannot be guaranteed, the server should explicitly degrade rather than speculate.
*   **Priorities:** Trustworthiness is more important than feature count, latency, or convenience.

## 2. State & Lifecycle Architecture

### Explicit State Ownership Hierarchy
Ownership must be explicit, strict, and enforceable. No component may hold a reference to state it does not own.

*   **WorkspaceManager:** Owns 1..N `ProjectContext`s. Handles workspace lifecycle, multi-root routing, and cross-project visibility boundaries.
*   **ProjectContext:** Owns a `ProjectStateMachine` and is the factory for `VersionedSnapshot`s.
*   **VersionedSnapshot:** An immutable, point-in-time representation of the project. It atomically bundles:
    *   `GradleModel` & `DependencyGraph` (Immutable build metadata and module relationships)
    *   `ASTs` (Immutable compiler output)
    *   `Indexes` (Immutable symbol/reference output)
    *   `SemanticModel` (Framework-aware semantic entities and relationships)
*   **Providers (Hover, Completion, etc.):** Own **nothing**. They are strictly stateless consumers of a specific `VersionedSnapshot`.

### Consistency Model: Versioned Snapshot Consistency
All requests execute against a specific, immutable `VersionedSnapshot` to eliminate race conditions and guarantee reproducible results. 
*   **Single-Snapshot Principle:** Everything visible to a provider MUST originate from a single snapshot version. A request must never mix ASTs from v42, Indexes from v43, and a GradleModel from v41.
*   **Snapshot Immutability:** Once a snapshot is committed, it NEVER changes. New state always produces a new snapshot version (e.g., v1 -> v2).
*   **Snapshot Binding & Retention:** Every LSP request explicitly captures a snapshot reference when it starts. The `ProjectContext` retains only the active snapshot and any historical snapshots currently pinned by running requests. Unreferenced snapshots are aggressively garbage collected to prevent memory leaks.
*   **Atomic Commits:** AST and Index state must always belong to the same committed snapshot. They are committed as a single unit to prevent feature inconsistencies.
*   **No Partial Visibility:** Requests must NEVER observe half-built ASTs or partially updated indexes.
*   **Reader-Writer Isolation:** While the compiler builds the next candidate snapshot (Writer), all incoming LSP requests (Readers) continue to query the *last-known-good* versioned snapshot.
*   **Cross-Feature Consistency:** All language features (completion, hover, references) must resolve symbols using the identical, authoritative snapshot instance for a given request.

## 3. The Compilation Commit Protocol

Compiler output must move from candidate state to a new `VersionedSnapshot` via a strict protocol:

1.  **Edit:** A `didChange` event is received.
2.  **Compile Candidate:** The compiler generates a new *candidate* AST in isolation.
3.  **Build Indexes:** The indexer generates *candidate* index updates from the candidate AST.
4.  **Validate:** The system verifies the candidate state's integrity (no orphaned symbols, valid AST).
5.  **Commit:** The system creates a new `VersionedSnapshot` bundling the ASTs, Indexes, and Gradle Model, and atomically swaps the active pointer to this new version.
6.  **Publish:** Diagnostic notifications are pushed to the client based on the newly committed snapshot.

*Failed compilations MUST NOT overwrite the committed state. The system retains the last-known-good state.*

**Dependency & Build Consistency:** A change to the Gradle model (e.g., refreshing dependencies) triggers a full pipeline run, culminating in a new `VersionedSnapshot`. ASTs compiled against one dependency graph are never queried using another.
**Syntax-Error Behavior:** Partially valid code is the normal state of an editor. Syntax errors during typing DO NOT constitute a "failed compilation". The compiler will produce a valid `VersionedSnapshot` containing a partial AST and error diagnostics. Normal editing must never force the server into a failure state.

## 4. Lifecycle State Machines

Projects and Compilers operate as strict state machines to prevent illegal transitions.

### Project State Machine
Projects have their own lifecycle independent of compiler state:
*   **INITIALIZING:** Loading Gradle model and resolving dependencies.
*   **INDEXING:** Performing initial full workspace compilation and index build.
*   **READY:** Project is fully loaded and accepting LSP requests.
*   **FAILED:** Project initialization failed (e.g., completely broken `build.gradle`). Emits error, waits for file changes.
*   **RECOVERING:** Attempting to restore project state after a failure (e.g., user corrected the build file). The `ProjectContext` is responsible for its own recovery actions.

### Compiler State Machine

*   **IDLE:** Waiting for file events.
*   **COMPILING:** Currently executing the compilation protocol (generating candidate state).
*   **READY:** Compilation successful, new snapshot committed. Transitions back to IDLE.
*   **FAILED:** Compilation or validation failed. Emits error, discards candidate state, keeps last-known-good state, transitions to RECOVERING or IDLE.
*   **RECOVERING:** Attempting to restore a valid state from disk cache or a full clean rebuild.

## 5. Index Architecture & Integrity

Indexes are treated as independent architectural components, owned strictly by the `IndexManager`.

*   **Index Types:** 
    *   `SymbolIndex` (Definitions, structure)
    *   `ReferenceIndex` (Usages, invocations)
    *   `CompletionIndex` (Optimized lookups for intellisense)
    *   `NavigationIndex` (Workspace symbol search)
*   **Integrity Rules:** Indexes are completely immutable once committed to a `VersionedSnapshot`. Every indexed entry MUST hold a definitive link to its originating AST node. No orphaned symbols are permitted.
*   **Index Version Alignment:** Every index must reference the exact snapshot version it belongs to. Mixed-version reads are strictly prohibited.
*   **Stable Symbol Identities:** Symbols must have durable identities (e.g., unique IDs) independent of ephemeral AST node instances. Definition, References, Hover, and Rename must operate on this stable identity to ensure refactoring correctness.
*   **Rebuild Strategy:** File-level edits trigger *incremental* index updates (evict file X's symbols -> index file X's new symbols). `build.gradle` edits trigger a *full* index rebuild.

## 6. Backpressure & Scalability

The server must protect its responsiveness under heavy load, rapid typing bursts, or massive workspaces.

*   **Coalescing Rapid Edits:** `didChange` events must be debounced (e.g., 200ms). Typing bursts result in a single compilation pipeline run, not sequential redundant runs.
*   **Cancel Obsolete Work:** Every LSP request is bound to a `CompletableFuture` and a `CancellationToken`. If the client cancels a request, or if a newer state commit renders a queued read-request obsolete, the work MUST be aborted immediately.
*   **Overload Behavior:** If request queues exceed bounds, the server will drop read-requests (e.g., hover, completion) with a standard LSP cancellation response rather than destabilizing core memory.

## 7. Failure Recovery

Failed components must disable themselves rather than return questionable results.

*   **Degraded Operating Modes:** If AST compilation fails fundamentally, the system falls back to regex/text-based structural search for standard Groovy symbols, entirely disabling Grails-specific "magic" features until compilation recovers.
*   **Formalized Recovery Workflows:**
    *   **OOM / High Memory:** Evict all in-memory caches, drop to disk-backed indexes, trigger garbage collection.
    *   **Compiler Crash:** Discard candidate state, retain last-known-good state, log diagnostic failure.
    *   **Invalid Gradle Model:** Lock workspace state, prompt user to fix `build.gradle`, suspend AST updates.
*   **Preserve Recoverable State:** Full LSP restarts are a last resort. Subsystems must encapsulate their failures.

## 8. Architectural Invariants (Verification)

These rules are non-negotiable and MUST be continuously verified by automated tests in CI.

1.  **State Consistency Test:** Providers reading from a snapshot during a concurrent write MUST NEVER see mutated data.
2.  **Index Integrity Test:** Querying the index for symbols of a deleted file MUST return 0 results immediately after commit.
3.  **Partial-Validity Test:** A syntax error injected into a source file MUST produce a valid `VersionedSnapshot` containing error diagnostics, and MUST NOT transition the server to a `FAILED` state.
4.  **Failure-Path Test:** A simulated fatal exception (e.g., OOM) MUST transition the system to a `FAILED`/`RECOVERING` state but MUST NOT crash the Language Server process.
5.  **No Provider Mutation Test:** Providers attempting to mutate an AST node or Index MUST throw an `UnsupportedOperationException`.
6.  **Semantic Consistency Test:** Definition, References, Hover, Completion, and Rename MUST resolve the exact same stable symbol identity for a given AST node.
7.  **Refactoring Transaction Test:** Refactoring operations (Rename, Move) MUST execute transactionally and validate against the semantic model before committing.

## 9. Multi-Root Architecture & Isolation

Workspace isolation must be enforced by architecture, not convention. 

*   **Formalize Workspace Isolation:** Multi-root projects must never leak state across boundaries. Each project maintains its own `ProjectContext` and `VersionedSnapshot` lineage.
*   **Dependency Graph as First-Class Architecture:** Multi-module Gradle projects and plugins require explicit dependency graph modeling rather than treating Gradle metadata as passive configuration. Cross-project navigation relies on this graph.
*   **Prevent Cross-Project Contamination:** Symbols, caches, indexes, and compiler state must NEVER leak across projects unless explicit module dependencies exist in the Gradle model.
*   **Dependency Visibility Rules:** Multi-root workspaces with inter-project dependencies require clear ownership and visibility contracts.

## 10. Large Workspace Readiness & Observability

Architecture must scale to massive workspaces and provide measurable verification.

*   **Memory Budgets & Scaling:** Establish explicit global and per-project memory limits. Long-term architecture must plan for disk-backed snapshot persistence when indexing IntelliJ-scale workspaces.
*   **Warm Startup Architecture & Persistence:** Persist snapshots and indexes to disk to avoid full workspace reindexing on every launch. Define a clear snapshot persistence strategy and recovery mechanisms for large workspaces.
*   **Restore Observability:** Metrics and telemetry must be collected to identify regressions and prove cache/index correctness. Include metrics for semantic model construction and symbol resolution accuracy.
*   **Measurable SLOs:** Establish concrete performance targets for Completion (<100ms), Navigation (<50ms), Incremental Compilation (<250ms), Startup time, and Max Memory Heap.
*   **Track Correctness Indicators:** The server must log and monitor cache invalidations, index rebuilds, recovery events, compilation failures, and any stale-state detections.

## 11. Semantic Model & Refactoring Architecture (IntelliJ-Class Direction)

We are building toward refactoring-grade correctness (Rename, Move, Safe Delete). This requires moving away from direct compiler internals.

*   **Semantic Layer Target Architecture:** `AST -> Indexes -> Semantic Model -> Providers`
*   **Define Semantic Model Ownership & Lifecycle:** Prevent the semantic model from becoming a monolithic object. Establish how semantic entities are built, validated, versioned, and committed alongside snapshots.
*   Providers will eventually consume a high-level `Semantic Model` interface instead of raw Groovy AST nodes.
*   **Grails as First-Class Entities:** Grails concepts (Controllers, Services, GORM entities, UrlMappings) will exist in the semantic layer as explicit types, completely eliminating the need for `if (isController) { ... }` magic scattered throughout the codebase.
*   **Refactoring Transaction Framework:** Rename, Move, Safe Delete, and future refactorings require transactional validation and commit semantics to avoid corrupting user code.