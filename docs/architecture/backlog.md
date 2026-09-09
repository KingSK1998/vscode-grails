# Architecture Backlog

> **Purpose:** Ideas and future directions that are NOT yet decisions.
> **Rule:** Items move here when they lack evidence (reproduced issue or benchmark). Promote to ADR when justified.
> **Last updated:** 2026-09-08

---

## BACKLOG-001: VersionedSnapshot for Full Snapshot Isolation

**Current disposition (2026-09-08):** The original proposal below is historical. VersionedSnapshot, request capture and lineage now exist in source; full isolation and retention remain unproven, with active repairs under R0-01/R1-04. Do not add another snapshot framework on the assumption this is unimplemented. Follow the [state contract](../state-and-lifecycle-specification.md) and [task card](../execution/task-specifications.md#r1-04), reproduce the race, then choose a measured fix. The withdrawn ADR-008 identifier is reserved; ADR-009 documents the current ownership correction.

**Origin:** Theoretical concern from architecture analysis (formerly CO-001, then ADR-008)
**Moved here:** 2026-06-23 — no reproduced issue demonstrating partial visibility in practice.

**Problem (theoretical):** Current Phase 1-2 architecture uses `clearCrossFileCaches()` at end of incremental cycle. This prevents stale data but does not provide true snapshot isolation — a provider could theoretically observe mid-cycle state.

**Proposed solution:** Introduce `VersionedSnapshot` as immutable, point-in-time project state. All LSP requests bind to a snapshot at entry. Writer builds next snapshot in isolation. Atomic swap on commit.

**Alternatives under consideration:**
- Copy-on-write AST → expensive, Groovy AST not designed for it
- Read-write locks per provider → too granular, deadlock risk

**Evidence needed to promote to ADR:**
- [ ] Reproduced failure: provider returning stale data during active compilation
- [ ] Benchmark: measurable latency or correctness issue under concurrent load
- [ ] User report: incorrect completions/hover during typing bursts

**Related invariants:** `INV-STATE-004`, `INV-STATE-001`
**Related spec:** `docs/state-and-lifecycle-specification.md` §2-3

**Risk if premature:** Agents optimize for snapshot isolation before evidence it's needed. Over-engineering.
