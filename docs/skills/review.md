# Skill: Review

Review code against project standards. Find violations, safely auto-fix what is mechanical, validate builds, and report results.

## 1. Load Standards (MANDATORY)

Before reviewing anything:

- Read `CODING_STANDARDS.md`
- Read `docs/invariants.md` — field ownership + identity contracts
- Read `docs/failure-modes.md` — known failure patterns
- If client files changed, read `client/RULES.md`
- If server files changed, read `server/RULES.md`

These files are authoritative.

Do not review code until all applicable standards files have been read.

## 2. Read Changes

Get changed files:

```bash
git diff --name-only HEAD
git diff --name-only --cached
```

Read every changed file.

Read relevant tests.

## 3. Agent Guard Rails — Check BEFORE Touching Protected Code

### State Mutation Guard

Before modifying any file in `server/src/`:

1. Check `docs/invariants.md §1` — is this field owned by the component you're editing?
2. If touching `GrailsService` write-path methods (`setupWorkspace`, `refreshAndReindexWorkspace`, `compileAndVisitAST`, `visitAST`) — **STOP. Explain why the change is needed. Get confirmation.**
3. Verify no provider writes to `visitor`, `compiler`, or `fileTracker`.

### Identity Guard

Before modifying code that uses identifiers (URIs, symbol IDs, positions):

1. Check `docs/invariants.md §2` — is the identifier durable or mutable?
2. If using a mutable identifier as a cache key or lookup key → **FLAG as CRITICAL**.
3. Verify URI normalization before comparison.

### Cache Guard

Before adding or modifying any cache:

1. Document: what invalidates it, who invalidates it, what scope.
2. Check: does `clearCrossFileCaches()` need to know about this cache?
3. Verify: no `ASTNode` references stored in cache (Phase 2 boundary rule).

### Provider Guard

Before modifying a T1 provider:

1. Verify `extends BaseProvider` with context-aware constructor.
2. Verify `createCancellationToken(uri)` at entry.
3. Verify `checkCancellation(token)` at yield points.
4. Verify `recordHealth(name, latencyMs, success)` in finally.
5. Verify no `private final GrailsService service` field.

### Failure Mode Guard

After fixing any bug:

1. Add entry to `docs/failure-modes.md` within 30 minutes.
2. Entry must include: invariant ID (e.g., `INV-OWN-004`), root cause, fix, test name.
3. If no test exists → mark "NONE — ADD ONE" and flag as incomplete.

### Invariant Review Checklist (MANDATORY)

Run this checklist mechanically. Do not rely on free-form reasoning.

```text
State Change?
□ INV-OWN-001 — Single writer preserved
□ INV-OWN-002 — No new shared mutable state outside GrailsService
□ INV-STATE-001 — Write-path methods unchanged or change justified

New Cache?
□ INV-OWN-004 — Invalidation trigger documented (what, who, scope)
□ INV-OWN-005 — No ASTNode references stored
□ INV-STATE-003 — clearCrossFileCaches() updated if cross-file

New Identifier?
□ INV-ID-* — Durable/mutable classification added to invariants §2
□ INV-KEY-002 — Not using mutable value as lookup key

New Shared State?
□ INV-OWN-001 — Ownership assigned in invariants §1 table
□ INV-OWN-002 — Lives in GrailsService (server) or ServiceContainer (client)

New Architecture Decision?
□ ADR required — document in docs/adr/decisions.md
□ Related invariants and failure modes cross-linked
```

If ANY box cannot be checked → flag as CRITICAL in report.

## 4. Validate

Validate all changes against:

- `CODING_STANDARDS.md`
- `client/RULES.md`
- `server/RULES.md`
- `docs/invariants.md`

Focus on:

- Architecture
- SOLID
- DI
- Provider tiers
- State mutation
- Lifecycle requirements
- Error handling
- Testing requirements
- Style guide compliance
- **Field ownership violations** (new)
- **Identity misuse** (new)
- **Missing cache invalidation** (new)

Always cite:

- File
- Line range
- Rule reference

## 5. Auto-Fix

Auto-fix only if:

- Mechanical
- Safe
- Behavior unchanged
- No architectural decision required

Examples:

- Imports
- Formatting
- Explicit typing
- Logging prefixes
- Groovy property syntax
- Elvis/safe-navigation conversions

Do NOT auto-fix:

- Architecture
- DI redesign
- Provider redesign
- State ownership
- Lifecycle changes
- Cache invalidation logic
- Identity handling

Report those instead.

## 6. Build Validation

Run when available:

```bash
npm run check-types
npm run lint
npm run build:server
cd server && ./gradlew test
```

Follow testing workflow from `CODING_STANDARDS.md`.

## 7. Anti-Hallucination Rules

Never claim:

- A file was modified unless modified.
- A fix occurred unless code changed.
- A test passed unless executed.
- A build passed unless executed.
- A file was reviewed unless read.
- A failure mode was checked unless `docs/failure-modes.md` was read.
- An invariant was verified unless `docs/invariants.md` was read.

Use `NOT VERIFIED` when verification is not possible.

## 8. KB Update Check (MANDATORY)

After reviewing code, check `docs/architecture/change-triggers.md`:

| If this changed... | ...update this |
|---|---|
| New cache introduced | `docs/invariants.md §1` (ownership row + invalidation) |
| New shared state | `docs/invariants.md §1` (ownership row) |
| New identifier type | `docs/invariants.md §2` (durable/mutable classification) |
| New provider | `docs/architecture/system-map.md` |
| GrailsService field added | `docs/invariants.md §1` |
| Bug fixed | `docs/failure-modes.md` (entry + index update) |
| Architecture decision | `docs/adr/decisions.md` |

Flag as INCOMPLETE if code changes require KB updates that weren't made.

## 9. Report

```text
## Review Report

### GUARD RAIL CHECKS
- State mutation: PASS | VIOLATION [INV-xxx-nnn details]
- Identity contracts: PASS | VIOLATION [INV-xxx-nnn details]
- Cache invalidation: PASS | N/A
- Provider contract: PASS | VIOLATION [details]
- Failure mode logged: YES | NO | N/A

### KB UPDATE CHECK
- Ownership table current: YES | NEEDS UPDATE | N/A
- Failure mode index current: YES | NEEDS UPDATE | N/A
- System map current: YES | NEEDS UPDATE | N/A

### AUTO-FIXED
- [file:line-range] [rule] Fix

### CRITICAL
- [file:line-range] [INV-xxx-nnn (SEVERITY)] Issue

### SUGGESTIONS
- [file:line-range] Improvement

### PASSED
- Correct items

Build:
Client: PASS | FAIL | NOT VERIFIED
Server: PASS | FAIL | NOT VERIFIED

Summary:
X fixed, Y critical, Z suggestions

Verdict:
PASS | PASS WITH FIXES | BLOCK
```

