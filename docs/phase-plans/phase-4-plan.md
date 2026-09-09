# Phase 4: Developer Experience & Maintenance - Detailed Implementation Plan

> **Current delivery order (2026-09-08):** Use the [product roadmap](../product-roadmap.md) and [implementation handoff](../implementation-handoff.md). The checklist below is historical and must be revalidated against the current implementation before claiming completion.

**Execution Mode:** REFACTOR MODE
**Last updated:** 2026-06-14

**ACTIVE_CONSTRAINT_SET:**
- Scope: Universal (no Target rules — this is REFACTOR, not ARCHITECTURE DESIGN)
- Version Binding: §3 NOT active (Refactor mode)
- Hard Invariant: §0.1 still applies

**Dependency:** No hard dependency on Phase 3. Can run in parallel after Phase 2a.

---

## Dependency Graph

```text
Phase 2a (✅ required)
  └─► Phase 4a: Documentation alignment (parallel-safe)
  └─► Phase 4b: Dev workflow streamlining (parallel-safe)
  └─► Phase 4c: Architectural cleanup (parallel-safe)
```

All Phase 4 steps are independent and parallelizable.

---

### STEP 1 — Documentation Alignment

```
Layer: N/A (documentation only)
Goal: README.md accurately reflects current capabilities
READS: Current feature set, STATUS.md files
WRITES: README.md, docs/user-guide.md
Active rules: None (documentation, no code)
Failure mode: N/A
Validation: No code changes. No state impact.
Confidence: High
```

**Actions:**
1. Audit README.md claims against `client/STATUS.md` and `server/STATUS.md`.
2. Remove/qualify multi-root workspace claims (not implemented until Phase 3).
3. Update feature matrix to match actual provider capabilities.
4. Update build/dev instructions if stale.
5. Ensure `docs/architecture.md` reflects Phase 1-2 changes (astLock, CancellationService, ProjectIndex).

---

### STEP 2 — Unified Dev Script

```
Layer: Build system
Goal: Single command watches client + server, auto-compiles, copies artifacts
READS: package.json, esbuild.js, server/build.gradle
WRITES: package.json scripts section
Active rules: None (build tooling, no runtime code)
Failure mode: Script fails → fallback to manual `npm run compile` + `npm run build:server`
Validation: No runtime code changes. Dev-only tooling.
Confidence: High
```

**Actions:**
1. Add `concurrently` dev dependency.
2. Add `package.json` script:
   ```json
   "dev": "concurrently \"npm run watch\" \"npm run watch:server\"",
   "watch:server": "cd server && ./gradlew build --continuous"
   ```
3. Add `npm run copy-server` as post-build hook for server JAR.
4. Document in `docs/developer-guide.md`.

---

### STEP 3 — Architectural Cleanup

```
Layer: Server + Client
Goal: Fix minor structural deviations, typos, dead imports
READS: Codebase
WRITES: Minor code edits (no behavioral changes)
Active rules: §0.1 (must not break correctness)
Failure mode: N/A (cosmetic only)
Validation: All tests pass after cleanup. No behavioral change.
Confidence: High
```

**Actions:**
1. Remove dead imports across server providers.
2. Fix any `RULES.md` references that point to old line numbers (run `node scripts/sync-line-refs.js`).
3. Ensure all providers have `@CompileStatic` annotation.
4. Verify TIER classification comments are present on all provider classes.

---

## Final Validation (Phase 4)

- [x] No runtime behavior changes.
- [x] No state model changes.
- [x] All tests pass.
- [x] Documentation matches implementation.
