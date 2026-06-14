# Session Memory — Agent Optimization

## Decisions Made & Patterns Established

1. **Single Source of Truth**:
   - `AGENTS.md` is now the single source of truth for all AI agents.
   - `CLAUDE.md` and `GEMINI.md` are thin wrappers pointing back to `AGENTS.md` using specific line ranges to minimize context bloat.
   - Exhaustive guidelines remain in `client/RULES.md` and `server/RULES.md` which are linked with line-anchored ranges.

2. **Automated Line-Reference Syncing**:
   - Created `scripts/sync-line-refs.js`. It parses `AGENTS.md` headers for sections (`## §N TITLE (LXX-LYY)`), updates them to their actual positions, and propagates these line-number updates to `CLAUDE.md`, `GEMINI.md`, and `.claude/agents/*.md`.
   - Pattern established: Always run `node scripts/sync-line-refs.js` (or `npm run sync-refs` if mapped) after modifying `AGENTS.md`.

3. **Status Check Pre-commit Guard**:
   - Created `scripts/pre-commit-guard.js`. It checks staged files; if code changes are staged under `client/` or `server/`, it blocks the commit unless the corresponding `STATUS.md` is also staged.
   - Hook is installed to `.git/hooks/pre-commit` via `scripts/install-hooks.js`.
   - Setup is accessible via `npm run setup` (or manually run `node scripts/install-hooks.js`).

4. **Subagent Consolidation**:
   - Specialized agents reduced from 5 to 3 core files: `architect`, `review`, and `recover`.
   - Deleted `remember` (logic absorbed into `AGENTS.md` §5) and `imprint` (logic merged into `review` Step 4 as UI Pattern Capture).
   - Removed `auto-tool-selection` skill (absorbed into `AGENTS.md` §6).
   - Moved `architecture-for-performance` skill to `docs/skills/architecture-perf.md` to keep it agent-agnostic.

5. **Concurrency & Thread Safety Model**:
   - Introduced `astLock` (ReentrantReadWriteLock) in `GrailsService`.
   - Pattern: Every write (workspace refresh, compilation, AST visitation) must be enclosed in `withWriteLock`. Every read (LSP feature providers) must be enclosed in `withReadLock`.
   - Providers leverage `withReadLock` transparently through `BaseProvider` helpers or explicitly for custom AST/visitor queries.
   - Centralized try-catch error boundaries via `safeProviderCall` in `GrailsTextDocumentService` to shield LSP handlers from unhandled exceptions.
   - Standardized all files to import Java concurrent classes and function interfaces rather than using inline fully qualified references.
   - Fixed ASTService memory leak by evicting cached ClassNodes per URI during visitors' visitSourceUnit pass.
   - ThreadSafeLruCache Executor Lifecycle: Refactored static `cleanupExecutor` to be non-final and nullable, initialized dynamically on demand via synchronized `getExecutor()`. Avoids executor rejection/exhaustion on server restarts or test cleanups. Added ThreadSafeLruCacheSpec Spock verification.
   - Inter-File AST Invalidation: Introduced `clearCrossFileCaches()` in `GrailsService` to explicitly evict globally tracked resolution caches (like `GrailsCompletionProvider` completion lists and static Groovy method caches in `DiscoveryService`, `GroovyRuntimeIntegration`) strictly *after* successful incremental AST cycles, ensuring cross-file completion dependencies never go stale.
   - Incremental Compilation Test Coverage: Replaced `GrailsIncrementalCompilerSpec` stub with 7 robust integration tests verifying AST updates, caching eviction, error handling, etc. Phase 1 is now fully complete ✅.
6. **Reasoning Guide → v3 (Final)**:
   - Canonical file: `docs/grails-lsp-reasoning-guide.md`. Old `docs/reasoning-guide.md` removed.
   - Compiled constraint system with mode-scoped rules, pre-bound step contracts, drift triggers as observation-only, and static document priority.
   - Version binding scoped to ARCHITECTURE DESIGN only (Phase 3+), not forced on runtime system.
7. **Architecture Improvement Plan Updated**:
   - Applied reasoning guide constraints to `docs/architecture-improvement-plan.md`.
   - Added: state classifications per component, invalidation ownership tables, degradation tier matrices, dependency graphs between phases, read/write contracts for migrated providers, and failure modes.
   - Fixed stale task backlog statuses (Task #3 incremental compilation now ✅, Task #5 now 🟡).
8. **Detailed Phase Plans Generated**:
   - Created `docs/phase-plans/phase-2-plan.md` — 12 steps covering IndexBuilder, IndexManager, MethodScopeCache, GroovydocCache, HoverProvider migration, shadow validation, Definition/References migration, CompletionProvider allowed violation.
   - Created `docs/phase-plans/phase-3-plan.md` — 7 steps covering VersionedSnapshot, Compilation Commit Protocol, multi-root WorkspaceManager, Gradle timeout/fallback, memory lifecycle.
   - Created `docs/phase-plans/phase-4-plan.md` — 3 steps (docs alignment, dev scripts, cleanup). REFACTOR MODE.
   - Created `docs/phase-plans/phase-5-plan.md` — 7 steps covering GrailsEntity type system, SemanticModelBuilder, stable symbol IDs, cross-feature consistency, RefactoringContext, RenameTransaction.
   - All plans use reasoning guide step format with READS/WRITES contracts, failure modes, invalidation ownership, and degradation tiers.

## Next Session Priorities

1. **Phase 2a Implementation (ProjectIndex Infrastructure)**:
   - **Task 2a.1 (Groovydoc Gate Check)**: ✅ Done. Verified `CompilerConfiguration.GROOVYDOC = true` extracts Groovydoc for Groovy 4.0.23.
   - **Task 2a.2 (IndexBuilder)**: Extend `SymbolInfo` and implement `IndexBuilder` (TIER 2 utility) to extract AST nodes into `SymbolInfo` without AST leakage.
   - **Task 2a.3 (IndexManager & ProjectIndex CAS)**: Add thread-safe CAS operations to `ProjectIndex` and implement `IndexManager` orchestration.
   - **Task 2a.4 (GroovydocCache)**: Implement LRU cache with O(1) reverse index eviction.
   - **Task 2a.5 (GrailsService Wiring)**: Wire the infrastructure into `GrailsService` with exact lock scoping.
2. **Global Skills Cleanup**:
   - Audit the user's global skill directory (`C:\Users\shiva\.agents\skills\`) and clean up deprecated folders to save context.
