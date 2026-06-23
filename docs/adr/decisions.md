# Architecture Decision Records (ADR)

> **Convention:** One section per decision. Newest first.
> **Owner:** @kingsk (sole maintainer)
> **Last updated:** 2026-06-23
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

**Status:** Accepted
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

**Status:** Accepted (permanent)
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
