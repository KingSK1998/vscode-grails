# Architecture Improvement Plan

**Date:** 2026-06-03
**Status:** Active

## Overview

This document outlines the strategic roadmap for improving the `vscode-gng-support` architecture. The plan shifts focus away from superficial structural changes (like folder organization) and concentrates heavily on **state management, cache correctness, compiler lifecycle discipline, and architectural decoupling**.

The plan is divided into chronological phases, starting with a foundational definition of state ownership, followed by prioritizing correctness and stability before scaling to multi-project support.

---

## Phase 0: State & Lifecycle Architecture (The Foundation)

Before modifying code, we must define the theoretical model for state in the language server. Everything else in this plan becomes easier once this specification exists.

1.  **Create the State & Lifecycle Architecture Specification**
    *   **Action:** Write a dedicated, definitive architectural document that formally dictates state boundaries, ownership, and behavior under stress.
    *   **Must Define:**
        *   **State ownership hierarchy & Index ownership**: Explicit rules detailing which components exclusively own memory, ASTs, and the global symbol index.
        *   **Lifecycles & State transitions**: Explicit definitions for Workspace, Project, Compiler, AST, Index, and Cache lifecycles, including all valid state machine transitions.
        *   **Consistency semantics**: Precise guarantees about what state an LSP request observes when executing concurrently with an active compilation (e.g., read-uncommitted vs. snapshot isolation).
        *   **Cancellation guarantees**: Strict rules on how `CompletableFuture` cancellation halts work without leaving partial or corrupted state.
        *   **Backpressure strategy**: How the server responds to and throttles an overwhelming volume of incoming LSP requests or rapid file change events.
        *   **Failure recovery model**: How the server recovers from out-of-memory (OOM) errors, compiler crashes, or invalid Gradle models.
        *   **Observability & Performance SLOs**: How metrics will be tracked to validate architectural improvements, alongside hard Service Level Objectives (SLOs) for compilation latency, response times, and memory limits.

2.  **Early Multi-Root & Memory Ownership Assessment**
    *   **Action:** Document exactly how the memory boundaries defined above will scale to true multi-root isolation (multi-project configurations) before any decoupling work begins.

---

## Phase 1: Stabilization & Correctness (Immediate Priority)

Before decoupling or adding complex features, the foundation must be mathematically sound, automatically verified, and adhere to the Phase 0 specification.

1.  **Enable Server Tests in CI**
    *   **Action:** Resolve the OutOfMemory (OOM) errors currently preventing server tests from running.
    *   **Action:** Add `./gradlew test` to the primary GitHub Actions workflow.
    *   **Rationale:** The server contains the AST processing and language intelligence. Manual regression testing is unsustainable.

2.  **Formalize AST Ownership & Cache Invalidation**
    *   **Action:** Implement the rules defined in Phase 0. Establish a single source of truth for AST and Index state.
    *   **Action:** Ensure `didChange` and `didClose` events deterministically evict stale ASTs and indexes before any provider attempts to read them.

3.  **Audit Concurrency and Cancellation**
    *   **Action:** Apply the Request Consistency and Cancellation guarantees from Phase 0 to all LSP handlers.
    *   **Action:** Prevent providers from observing a partially rebuilt AST state if a request fires during incremental compilation.

4.  **Validate Incremental Compilation Correctness**
    *   **Action:** Create robust integration tests specifically verifying that incremental edits correctly update dependencies and do not corrupt the global index.

---

## Phase 2: Decoupling & Modularity (Taming the Monolith)

With tests passing and caches formalized, reduce the gravitational pull of the `GrailsService` "God Object".

1.  **Reduce `GrailsService` Responsibility**
    *   **Action:** Continue introducing provider-specific context interfaces (e.g., `CompilationContext`, `ProjectContext`).
    *   **Action:** Refactor TIER 1 providers to depend on these granular interfaces rather than the monolithic `GrailsService`.

2.  **Clarify Groovy vs. Grails Layering**
    *   **Action:** Separate standard Groovy language features from Grails-specific "magic" (e.g., dynamic finders, GORM).
    *   **Action:** Grails features should be implemented as extensions/decorators over a solid Groovy core, rather than being scattered throughout AST visitors as conditional logic.

3.  **Evaluate Provider Dependency Graph**
    *   **Action:** Ensure providers do not have hidden coupling. Shared infrastructure (like type resolution) should be extracted into isolated TIER 2 utilities or TIER 3 modules.

---

## Phase 3: Multi-Project & Lifecycle Mastery (Scaling Up)

Once the core is decoupled and stable, expand the server's capabilities to handle complex, real-world workspace configurations.

1.  **Implement Proper Multi-Root Workspace Support**
    *   **Action:** Transition the architecture from single-root (`workspaceFolders[0]`) to true multi-root.
    *   **Action:** Introduce explicit workspace and project state boundaries. Ensure state ownership is isolated so cross-project contamination is impossible.

2.  **Review Gradle Synchronization Lifecycle**
    *   **Action:** Audit how the server reacts to `build.gradle` changes. Ensure dependency updates and project model rebuilds have predictable, atomic effects on the server state.

3.  **Strengthen Memory Lifecycle Management**
    *   **Action:** Enforce the memory ownership rules defined in Phase 0.
    *   **Action:** Implement memory boundaries or soft references where appropriate to prevent uncontrolled memory growth in long-running language servers and facilitate failure recovery.

---

## Phase 4: Developer Experience & Maintenance (Housekeeping)

Address lower-priority maintenance tasks that improve the contributor experience.

1.  **Align Documentation with Implementation**
    *   **Action:** Update the main `README.md` to accurately reflect current capabilities (e.g., remove claims of multi-root support until Phase 3 is complete).

2.  **Streamline Local Development Workflow**
    *   **Action:** Create a unified dev script (e.g., a concurrent runner in `package.json`) that watches both the TypeScript client and Groovy server, auto-compiling and copying artifacts without manual intervention.

3.  **Fix Architectural Inconsistencies**
    *   **Action:** Clean up typos and minor structural deviations that do not pose structural risks but add cognitive load for contributors.

---

## Phase 5: Semantic Intelligence & Refactoring (IntelliJ-Class Direction)

With the foundational state management proven at scale, the architecture shifts to providing deep, framework-aware intelligence and safe refactoring.

1.  **Design Semantic Model Architecture**
    *   **Action:** Build a semantic layer representing Grails concepts (Controllers, Services, GORM) as first-class entities. Prevent this model from becoming a new monolithic God Object.
2.  **Introduce Refactoring Transaction Framework**
    *   **Action:** Implement transactional validation and commit semantics for Rename, Move, and Safe Delete operations.
3.  **Add Semantic Consistency Verification**
    *   **Action:** Ensure Definition, References, Hover, Completion, and Rename all resolve the exact same symbol identities to guarantee feature consistency.

---

## Excluded / De-prioritized Items

Based on architectural review, the following items are intentionally deprioritized to focus on system stability:
*   Restructuring the `UseCase` folder hierarchy (state ownership matters more).
*   Modifying bootstrap initialization order (current state is acceptable).
*   "Open-source readiness" metadata (does not impact technical stability).

---

## Task Backlog — Prioritized

> **Last updated:** 2026-06-07  
> Order reflects both urgency and dependency chain. Do not start a task until its upstream is ✅.

### 🔴 Critical

| # | Task | Dependency | Notes |
|---|---|---|---|
| 1 | **Fix test infrastructure + CI** | None | Server tests must pass in CI before trusting any change. `DiscoveryServiceSpec` now stable; extend coverage. |
| 2 | **Define/enforce state ownership** | #1 | Formal read/write boundaries per component. Prerequisite for all decomposition work. Without this, #5 is unsafe. |

### 🟠 High

| # | Task | Dependency | Notes |
|---|---|---|---|
| 3 | **Incremental compilation correctness** | #2 | Stale completions are the most visible user-facing bug. Requires state ownership to be clean first. |
| 4 | **Stable symbol identities** | #2 | Consistent node IDs across Definition / References / Hover / Rename. Prerequisite for #9. Could move to Medium if rename/refactoring work is further out. |
| 5 | **GrailsService decomposition** | #2 | Break GrailsService into bounded contexts. Do NOT start until state ownership (#2) is fully enforced — premature decomposition will scatter bugs. |

### 🟡 Medium

| # | Task | Dependency | Notes |
|---|---|---|---|
| 6 | **Gradle lifecycle correctness** | #3 | Correct build-tool integration (sync, invalidation, daemon). Less user-visible than compilation bugs but important for reliability. |
| 7 | **Multi-root workspace support** | #5 | Requires decomposed GrailsService — currently hard-wired to `workspaceFolders[0]`. Cannot be done before #5. |

### ⚪ Future

| # | Task | Dependency | Notes |
|---|---|---|---|
| 8 | **Semantic model** | #5 | Grails-domain entities (Controller, Service, GORM) as first-class LSP nodes. Build carefully — must not become a new God Object. |
| 9 | **Refactoring transactions** | #4, #8 | Transactional Rename / Move / Safe Delete with rollback. Needs stable symbol IDs (#4) and semantic model (#8). |