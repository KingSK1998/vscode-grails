# LSP Reasoning Guide & Constraint-Based Planner (Grails LSP) — v3 (Final)

> **Historical / superseded, 2026-09-08.** The entire guide below is retained as design history, not active agent instructions. Use [agent execution](agent-execution.md), [invariants](invariants.md), the [state contract](state-and-lifecycle-specification.md) and task acceptance cards. In particular, the former bans on discovering evidence/changing an approach during execution and the old phase-specific authority order no longer govern work. Update an evidence brief when new facts invalidate its assumptions.

> Non-negotiable: This is a compiled constraint system. Rules are selected before reasoning begins. No step may introduce new active constraints.

---

## 0.0 EXECUTION MODE (REQUIRED)

Exactly one:

- ARCHITECTURE DESIGN MODE
- PATCH MODE
- DEBUG MODE
- REFACTOR MODE

No mixing.

---

## 0.1 HARD SYSTEM INVARIANT

System must remain correct under:
- stale state
- partial failure
- concurrent mutation

Any design requiring global synchronization → REJECT.

---

## 0.5 AUTHORITATIVE DOCUMENTS (STATIC PRIORITY)

1. server/RULES.md
2. client/RULES.md
3. CODING_STANDARDS.md
4. state-and-lifecycle-spec
5. this guide

These are inputs only, never re-ranked.

---

## 0.6 CONSTRAINT SCOPE MODEL

| Scope | Applies in |
|------|-----------|
| Universal | always |
| Runtime | PATCH / DEBUG / REFACTOR |
| Target | ARCHITECTURE DESIGN only |

---

## 0.7 VALIDATION HANDOFF (ARCH DESIGN ONLY)

External system validates plan. Gemini does not self-validate.

---

## 0.8 DRIFT TRIGGERS (OBSERVATION ONLY)

Triggers mark risk points only. They do NOT activate rules.

---

## 0.9 PLAN CONSTRAINT HEADER (STATIC SNAPSHOT)

ACTIVE_CONSTRAINT_SET is compiled ONCE per plan:

ACTIVE_CONSTRAINT_SET = compile(mode, phase, rules)

No modification allowed after initialization.

---

## 0.10 RULE ACTIVATION FILTER (CRITICAL)

Before reasoning:

ACTIVE_RULE_SET = compile(all_rules where:
  scope ∈ {Universal}
  AND allowed_by_mode
  AND allowed_by_phase)

Only ACTIVE_RULE_SET may be used in reasoning.

---

## 0.11 PRE-BOUND STEP CONTRACTS

Each step is pre-assigned:
- allowed rules
- allowed reads
- allowed writes

No discovery during execution.

---

## 1. ARCHITECTURE SEPARATION MODEL

Client ≠ Server ≠ Protocol

No cross-layer logic.

---

## 2. STATE MODEL

- Source of truth
- Derived state
- Cached state
- Ephemeral state

Ambiguity = INVALID.

---

## 3. VERSION BINDING (TARGET ONLY)

Only applies in ARCHITECTURE DESIGN MODE (Phase 3+).

Not applied to Runtime system.

---

## 4. STEP EXECUTION ENGINE

PATCH / DEBUG:

- Goal
- Change
- Failure mode
- Validation
- Active rules (pre-bound)

ARCHITECTURE / REFACTOR:

- Layer
- Goal
- READS
- WRITES
- Active rules
- Failure mode
- Validation

No runtime rule discovery allowed.

---

## 5. CROSS-STEP CHECKS

Only validate ACTIVE_RULE_SET constraints.

---

## 6. DEPENDENCY GRAPH

Lazy invalidation graph only. No pipeline execution model.

---

## 7. INVALIDATION OWNERSHIP

Must define:
- owner
- trigger
- scope
- strategy

---

## 8. CANCELLATION MODEL

All computations must be cancelable.

---

## 9. FAILURE MODEL

Failure is default state. All subsystems must define fallback, degraded mode, recovery.

---

## 10. DEGRADATION LADDER

- Tier 0 full
- Tier 1 partial
- Tier 2 name-only
- Tier 3 heuristic

---

## 11. BACKPRESSURE MODEL

Context-based ordering only:
- typing → completion first
- idle → hover first
- batch → indexing first

---

## 12. ALLOWED VIOLATIONS

Only valid if:
- read-only
- version-safe
- no mutation
- no persistence

---

## 13. HARD ANTI-PATTERNS

- Any reference to inactive rule → HARD FAILURE
- AST in UI layer
- Gradle in hover/completion
- non-cancelable semantic work
- missing invalidation triggers

---

## 14. FINAL VALIDATION

- Universal violations → HARD FAIL
- Multi-check failure → HARD FAIL
- minor runtime issues → SOFT FAIL
- inactive rule reference → HARD FAIL

---

## OUTPUT REQUIREMENTS

Must include:
1. Execution mode
2. ACTIVE_CONSTRAINT_SET snapshot
3. Step execution
4. Dependency graph
5. Invalidation model
6. Failure model
7. Final validation
