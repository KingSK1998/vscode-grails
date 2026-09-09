# Skill: Code Review

Review code against project standards. Find violations, auto-fix mechanical issues, report results.

If project standards are not already available in context, load the applicable standards defined in AGENTS.md §7.

## Guard Rails

### State Mutation
- Check `docs/invariants.md §1` — field owned by this component?
- Core write-path change → inspect reproducer, ownership and publication impact before accepting. Necessary authorized fixes may change the owning core; no extra permission is implied by review.
- No provider writes to `visitor`, `compiler`, `fileTracker`.

### Identity
- Check `docs/invariants.md §2` — durable or mutable?
- Mutable identifier as cache/lookup key → CRITICAL.
- Verify URI normalization.

### Cache
- Document: what/who/scope for invalidation.
- Check affected-scope invalidation and coherent publication, including legacy `clearCrossFileCaches()` coverage where used.
- No ASTNode references in value indexes/caches prohibited by INV-OWN-005. Request/AST generation retention must satisfy the explicit state/lease contract; do not confuse it with a durable cache.

### Provider (T1)
- `extends BaseProvider` with context constructor.
- `createCancellationToken(uri)` at entry.
- `checkCancellation(token)` at yield points.
- `recordHealth(...)` in finally.
- No `private final GrailsService service` field.

### Failure Mode
- Bug fixed → entry in `docs/failure-modes.md` with invariant ID, root cause, fix, test.
- No test → flag incomplete.

## Invariant Checklist

Run mechanically:

```text
State Change?
□ INV-OWN-001 — Single writer preserved
□ INV-OWN-002 — Designated owner and project/workspace scope
□ INV-STATE-001 — Write-path unchanged or justified

New Cache?
□ INV-OWN-004 — Invalidation documented
□ INV-OWN-005 — No ASTNode references
□ INV-STATE-003 — Coherent affected-scope invalidation/publication verified

New Identifier?
□ INV-ID-* — Classification in invariants §2
□ INV-KEY-002 — Not using mutable as lookup key

New Shared State?
□ INV-OWN-001 — Ownership in invariants §1
□ INV-OWN-002 — Owner appears in the ownership map; providers remain readers

New Architecture Decision?
□ ADR in docs/adr/decisions.md
□ Cross-links to invariants and failure modes
```

For each applicable box, record evidence or a violation. Mark unrelated boxes not applicable with a reason; a documentation/helper-script task does not require an artificial compiler architecture change. An unchecked applicable correctness rule blocks acceptance.

## Auto-Fix

Only if mechanical, safe, and behavior-preserving: imports, formatting, typing, logging prefixes. Groovy property/getter/field syntax, map member access and elvis/safe-nav can change behavior (including empty/zero values); do not transform them mechanically without proving equivalence.

Never auto-fix: architecture, DI, providers, state ownership, lifecycle, caches, identity.

## Anti-Hallucination

Never claim a file was reviewed unless read, a test passed unless executed, a fix occurred unless code changed. Use `NOT VERIFIED` when applicable.

## Report

```text
## Code Review Report

GUARD RAILS: [PASS | VIOLATION per category]
AUTO-FIXED: [file:line] [rule] [fix]
CRITICAL: [file:line] [INV-xxx] [issue]
SUGGESTIONS: [file:line] [improvement]

Verdict: PASS | PASS WITH FIXES | BLOCK
```
