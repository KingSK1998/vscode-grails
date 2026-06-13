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

## Next Session Priorities

1. **Phase 2 Implementation (Decoupling & Modularity)**:
   - Reduce `GrailsService` responsibility by introducing `CompilationContext` and `ProjectContext` interfaces.
   - Clarify Groovy vs. Grails layering in completion and AST provider strategies.
2. **Global Skills Cleanup**:
   - Audit the user's global skill directory (`C:\Users\shiva\.agents\skills\`) and clean up deprecated folders (`arch-vscode`, `find-skills`, `read-project-context`) to save another 37 KB of context.
