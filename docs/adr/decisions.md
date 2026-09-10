# Architecture Decision Records (ADR)

> **Convention:** One section per decision. Newest first.
> **Owner:** @kingsk (sole maintainer)
> **Last updated:** 2026-09-10
> **Rule:** Document WHAT was decided, WHY, and WHAT was rejected.

### ADR Lifecycle States

| Status | Meaning |
|---|---|
| **Proposed** | Under discussion, not yet committed |
| **Accepted** | Active and enforced |
| **Superseded by ADR-NNN** | Replaced by newer decision |
| **Deprecated** | No longer relevant, kept for history |

---

> **Note:** Future architecture ideas without evidence live in `docs/architecture/backlog.md`, not here.
> Promote to ADR only when a reproduced issue or benchmark justifies the decision.

---

## ADR-010: Request Lease and Detached AST Retention for Lifecycle Safety (2026-09-10)

**Status:** Accepted
**Evidence:** Verified in `kingsk.grails.lsp.context.PublicationAndLifecycleSpec` across concurrent compilation, snapshot publication, hibernation AST detachment, and project disposal.
**Context:** When a project hibernates (`INV-STATE-010`) or is disposed (`INV-OWN-009`), live compiler and visitor references were cleared, but `SnapshotManager` history retained visitor instances which referenced Groovy `ClassNode`s, `GroovyClassLoader`s, and `CompilationUnit`s in heap memory. Readers actively executing queries during hibernation or disposal could either crash or prevent memory reclamation. Furthermore, document providers previously accessed live compiler/visitor instances interchangeably with snapshots, risking tearing under concurrent compilation (`INV-STATE-004`).

**Decision:**
- Introduce `RequestLease` implementing `AutoCloseable`, tracking active readers via an atomic counter (`activeLeases`) on `ProjectContextImpl`. All document providers acquire `RequestContext` with `try-finally` blocks that ensure deterministic lease release.
- Introduce `DetachedASTAccessor` (singleton `INSTANCE` returning null/empty collections with zero retained AST or ClassLoader state).
- When a project hibernates, `SnapshotManager.detachAst()` atomically points active and LKG snapshots to `DetachedASTAccessor.INSTANCE`, dropping AST references while preserving usable `IndexSnapshot` and `GradleModel` facts. If active reader leases exist at the moment of hibernation, detachment is deferred until the last lease closes (`releaseLease`).
- When a project is disposed, snapshots and auxiliary caches (`MethodScopeCache`, `GroovydocCache`) are fully cleared once active reader leases drain.
- Provider completion strategies access AST and type information exclusively through `RequestContext.ast()` and `RequestContext.classLoader()`, never through unmanaged live compiler getters.
- Cross-project dirty propagation (`propagateInvalidation`) operates strictly via project status flags without nested project write locks, preventing deadlocks (`INV-STATE-011`).

**Rejected:**
- Deep-cloning the entire Groovy AST tree on every compilation snapshot (prohibitive CPU and memory overhead; violates `docs/specs/performance.md`).
- Retaining full AST structures across hibernated projects (leads to OutOfMemoryError under workspace LRU with multiple projects).
- Blocking readers during compilation with coarse-grained reader-writer locks (increases latency >100ms; violates responsive read SLA).

**Related invariants:** `INV-OWN-001`, `INV-OWN-005`, `INV-OWN-006`, `INV-OWN-009`, `INV-STATE-001`, `INV-STATE-002`, `INV-STATE-004`, `INV-STATE-010`, `INV-STATE-011`.

---

## ADR-009: Project Ownership and Executable Contracts (2026-09-08)

**Status:** Accepted for documentation/execution contracts within the user's requested roadmap handoff. This records current ownership and required acceptance behavior; it does not accept an unmeasured new snapshot implementation.

**Evidence:** Current GrailsService implements ProviderContext; ProjectContextImpl implements ProjectContext/CompilationContext and owns compiler, visitor, index, state and publication. VersionedSnapshot/SnapshotManager and document scheduling exist, while saved runtime tests fail and visitor `copyFrom()` retains shallow AST references. The user requested actionable specs and automatic task selection that preserve correctness and performance. See [handoff](../implementation-handoff.md), [system map](../architecture/system-map.md) and [state contract](../state-and-lifecycle-specification.md).

**Decision:**

- Keep GrailsService as composition root and assign one explicit scoped owner to each mutable state item. Correct ADR-004's physical field-location rule and ADR-005's all-three-interfaces wiring to reflect project contexts. Providers remain readers; no new service locator or distributed state architecture is implied.
- Preserve ADR-006's no-AST index boundary. An origin link is a value locator. Coherent request generations and resource release require behavioral evidence, not an atomic record/shallow copy claim.
- Reconcile active/LKG retention with hibernation: retain usable safe facts while released compiler generations lose all owned retention paths after bounded request leases drain. R1-04 chooses/proves the concrete representation; no automatic deep-clone redesign is approved here.
- Use the [execution guide](../agent-execution.md), one task queue and acceptance cards/records. Current code evidence, required invariants and future proposals are distinct. A marker validator cannot certify runtime correctness.
- Discover project APIs from resolved artifacts and bounded capability rules; fixed language grammar is legitimate. Treat responsive reads and bounded resource lifecycles as required contracts and measure claimed optimizations.

**Supersedes:** Only the physical field-owner/wiring clauses of ADR-004/005 and conflicting old documentation. Single writer, context segregation, static compilation and AST boundaries remain in force. New INV-DISC/PERF/TOOL/EDIT rules express the user's requested product constraints; current violations remain open tasks.

**Rejected:** Restoring monolithic field ownership to make old diagrams true; declaring current snapshots immutable without proof; forbidding all constants; permitting unlimited background work because it uses futures; marking tasks complete from source presence or old phase labels.

**Consequences:** Agents repair designated owners with regression tests and update affected KB. Significant isolation/storage changes still require measured alternatives and a separate ADR. The withdrawn snapshot proposal once called ADR-008 remains [BACKLOG-001](../architecture/backlog.md); its number is reserved and is not reused here.

**Related invariants:** INV-OWN-001/002/005/006, INV-STATE-001/003/004/010/011, INV-DISC/PERF/TOOL/EDIT.

---

## ADR-007: Stateless Completion Strategies (2026-06-16)

**Status:** Accepted
**Last validated:** 2026-06-23
**Related invariants:** `INV-OWN-001`, `INV-STATE-002`
**Related failure modes:** —

**Context:** Completion strategies mutated a shared `CompletionRequest` object, adding items to a mutable list. Order-dependent, hard to test, impossible to parallelize.

**Decision:** All completion strategies return `List<CompletionItem>` (functional, stateless). `CompletionBuilder` collects results.

**Rejected:**
- Reflection-based strategy loading → no transparency or type safety under `@CompileStatic`.
- Strategy registry with dynamic dispatch → explicit wiring is simpler and compile-time verified.

**Consequences:**
- Independently testable with pure input/output.
- `CompletionRequest` becomes immutable coordinate object.
- Order-independence enables future parallelization.

---

## ADR-006: ProjectIndex with AST Boundary Rule (2026-06-14)

**Status:** Accepted
**Last validated:** 2026-06-23
**Related invariants:** `INV-OWN-005`, `INV-ID-003`, `INV-ID-004`, `INV-KEY-002`, `INV-KEY-003`
**Related failure modes:** SM-001, CI-001

**Context:** Providers depended on `GrailsService` monolith for state access. AST nodes leaked into caches — destroying old AST corrupted caches holding references.

**Decision:** `ProjectIndex` holds only `SymbolInfo` (primitive/LSP types). No `ASTNode` reference may exist in ProjectIndex, SymbolInfo, or MethodScopeCache after construction.

**Rejected:**
- `WeakReference` wrapping → GC timing unpredictable, null mid-provider worse than no data.
- Deep-cloning ASTNodes → circular references, expensive, clones drift from source.

**Consequences:**
- Incremental recompilation safely destroys old AST without corrupting index.
- Index rebuild slightly more work upfront but eliminates stale-reference bugs entirely.

---

## ADR-005: Context Interfaces Over Direct GrailsService Injection (2026-06-07)

**Status:** Accepted interface segregation; wiring clause superseded by ADR-009. Historical wording below describes the original implementation.
**Last validated:** 2026-06-23
**Related invariants:** `INV-OWN-001`, `INV-STATE-002`, `INV-KEY-005`
**Related failure modes:** PC-001

**Context:** All T1 providers took `GrailsService` directly. ISP violation — access to write methods they must never call.

**Decision:** Split into `ProviderContext`, `CompilationContext`, `ProjectContext`. `GrailsService` implements all three. `ProviderRegistry` wires `(service, service, service)`.

**Rejected:**
- `ReadOnlyGrailsService` wrapper → still exposes unrelated concerns.
- Individual fields → constructor signatures become unwieldy.

**Consequences:**
- Write methods hidden by interface boundary. `@CompileStatic` enforces at compile time.
- `ProviderRegistry` is single wiring point.

---

## ADR-004: Single GrailsService Composition Root (2026-06-03)

**Status:** Accepted composition root/single writer; physical field-location clause superseded by ADR-009. Historical wording below describes the original implementation.
**Last validated:** 2026-06-23
**Related invariants:** `INV-OWN-001`, `INV-OWN-002`, `INV-STATE-001`
**Related failure modes:** CI-001

**Context:** LSP server needs shared state: one compiler, one visitor, one file tracker. Distributing creates synchronization nightmares.

**Decision:** `GrailsService` is single composition root. Shared mutable state lives here. Providers read freely, never write.

**Rejected:**
- Distributed service with message passing → single-process, adds latency with zero benefit.
- Event-driven propagation → eventual consistency unacceptable for IDE.

**Consequences:**
- Simple mental model: one owner, everyone reads.
- Write path small and auditable (4 methods).
- God Object risk mitigated by ADR-005 and ADR-006.

---

## ADR-003: @CompileStatic Everywhere (2026-06-03)

**Status:** Accepted (permanent, non-negotiable)
**Last validated:** 2026-06-23
**Related invariants:** —
**Related failure modes:** —

**Context:** Groovy dynamic dispatch introduces runtime overhead and type-safety gaps in LSP server processing thousands of requests/minute.

**Decision:** `@CompileStatic` on ALL classes. Type error → fix types, never remove annotation.

**Rejected:**
- Hot-path only → "hot path" is subjective. Consistency cheaper than analysis.
- `@TypeChecked` → still allows dynamic features that break under incremental compilation.

**Consequences:**
- Java-like performance. Type errors caught at compile time.
- Some Groovy convenience unavailable. Acceptable tradeoff.

---

## ADR-002: LRU Cache for Groovydoc (2026-06-14)

**Status:** Accepted
**Last validated:** 2026-06-23
**Related invariants:** `INV-OWN-004`
**Related failure modes:** RL-001

**Context:** Groovydoc extraction expensive. Storing in `SymbolInfo` bloats index. Extracting every hover too slow.

**Decision:** Separate `GroovydocCache` with LRU eviction. Lazy extraction on first hover.

**Rejected:**
- Eager indexing → most symbols never hovered. Wastes memory.
- No caching → AST traversal too slow for interactive hover.

**Consequences:**
- Memory-efficient: caches only what's requested. LRU prevents unbounded growth.
- Invalidated on AST change.

---

## ADR-001: UseCase Pattern for Client Workflows (2026-06-03)

**Status:** Accepted
**Last validated:** 2026-06-23
**Related invariants:** `INV-OWN-003`
**Related failure modes:** —

**Context:** VS Code command handlers accumulate business logic. Untestable, tightly coupled.

**Decision:** Multi-service workflows go through `UseCase` classes. Commands thin delegates (1-2 lines).

**Rejected:**
- Service-to-service orchestration → circular deps, hidden workflow logic.
- Fat command handlers → untestable without VS Code runtime.

**Consequences:**
- Commands trivially simple. UseCases testable without VS Code.
- Services do things, UseCases decide what to do.
