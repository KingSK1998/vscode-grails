# Session Memory — Agent Optimization

## Decisions Made & Patterns Established

[... same as previous ...]

9. **Multi-Project & Lifecycle Architecture (Phase 3)**:
   - Established **Single Source of Truth** per request via `VersionedSnapshot`. All LSP handlers are now bound to an immutable snapshot of the workspace (AST, Index, Gradle Metadata) at request entry, preventing state-drift mid-calculation.
   - Implemented **Compilation Commit Protocol**: Compilers now work on private state and atomically "commit" new snapshots only after validation. This decouples long-running index rebuilds from instant-response LSP lookups.
   - **WorkspaceManager Isolation**: Replaced monolithic maps in `GrailsService` with a proper workspace router. Each project root owns its own `ProjectContextImpl`, ensuring strict isolation between multiple workspace folders.
   - **Stateless Completion Engine**: Completion strategies are now completely stateless, returning `List<CompletionItem>` instead of mutating a request object. Reflection-based strategy loading was rejected in favor of explicit wiring in `CompletionBuilder` for transparency and type safety.
   - **Memory Lifecycle (Hibernation)**: Projects now support a `hibernate()` state where compiler and visitor caches are explicitly dropped to free up memory when projects are removed or system resources are low.

10. **Agentic Knowledge Base System (KB)**:
    - Established an **Agent-Readable Knowledge Base** to move from "vibe coding" to structured agentic engineering.
    - Created **`docs/invariants.md`** defining explicit state ownership, ID contracts (durable vs mutable), state consistency rules with severity ratings (CRITICAL/HIGH/MEDIUM), and an invariant review checklist.
    - Established **`docs/failure-modes.md`** as a Failure Mode Registry indexing past bugs by component and invariant rule with a Status lifecycle (Active/Resolved/Historical) and a 9-category Failure Test Matrix.
    - Documented active and past architectural decisions in **`docs/adr/decisions.md`** with status lifecycles (Proposed/Accepted/Superseded/Deprecated).
    - Created **`docs/architecture/system-map.md`** as the first file read by new agents to understand data flow, state ownership, provider tiers, and architectural risk zones.
    - Defined a clear maintenance protocol in **`docs/architecture/change-triggers.md`** to list required KB updates per change.
    - Backlogged hypothetical design proposals in **`docs/architecture/backlog.md`** (e.g. `VersionedSnapshot`) until empirical evidence or benchmarks justify promotion.
    - Automated drift prevention by updating **`scripts/pre-commit-guard.js`** to warn on missing KB updates (e.g. `GrailsService` changes without `invariants.md` edits, new caches/providers, bug-fix commit messages without `failure-modes.md` updates).
    - Embedded the **Invariant Review Checklist** and KB update checks directly into the agent-facing **`docs/skills/review.md`** skill.

## Next Session Priorities

1. **Phase 4 (Refactor & Aesthetics)**:
   - Phase 3 core is finished and verified. Next step is Phase 4: code cleanup, dev script automation, and documentation alignment to the new multi-root architecture.
2. **Completion Strategy Polish**:
   - Audit the remaining minor completion strategies for edge cases and ensure 100% test coverage for the new stateless pattern.
3. **Gradle Sync Stability**:
   - Investigate persistent Gradle tooling daemon stability and implement Task 3c.1 (Gradle Sync Audit) fully to handle long-running Gradle timeouts.
4. **Knowledge Base Enforcement Habit**:
   - Ensure subsequent model sessions execute the `docs/skills/review.md` checklist and adhere to the change-trigger update requirements when making code edits.

