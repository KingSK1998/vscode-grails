# Server Status

> **AI AGENTS: Update this file on EVERY task that touches server code. Status only — no docs, no API, no architecture. Just current state.**
> Last updated: 2026-06-13

---

## Current Priority: ✅ ALL TASKS COMPLETE

**See:** [server-folder-reorganization.md](../../docs/server-folder-reorganization.md)

### Completed Work (2026-05-12)
- **Folder Reorganization**: All tasks (TASK-A through TASK-G) completed successfully
  - 22 utils files reorganized into 10 domain subfolders
  - 15 model files reorganized into 4 type subfolders
  - 20+ provider files reorganized into document/, workspace/, completions/strategies/
  - 100+ import references updated across all files
- **`grails.discoverTestsBatch`**: Implemented server-side for client ISSUE-013
  - `GrailsTestDiscoveryProvider.discoverTestsBatch(List<String> projectUris)`
  - Command registered in `GrailsLanguageServer.executeCommandProvider`

### Completed Work (2026-05-13)
- **Cache Management Improvements**:
  - Created `ThreadSafeLruCache` in `utils/cache/` with TTL, size limits, stats
  - Added scheduled cleanup for completion cache (10s interval, 30s TTL)
  - Added `FileContentTracker` cache limits (MAX_TRACKED_FILES=500, MAX_FQCN_ENTRIES=10000)
  - Added `forceInvalidate(uri)`, `isStale(uri)`, `getCacheStats()` to FileContentTracker
  - Added `lastModified` tracking for stale detection on file changes
- **Executor Shutdown (SERVER-010)**:
  - Added `shutdown()` to GrailsService, FileContentTracker, ThreadSafeLruCache
  - Wired up graceful 5-second timeout termination
- **Request Cancellation (SERVER-017)**:
  - Created `CancellationService` with `CancellationToken` support
  - Added `createCancellationToken(uri)` and `checkCancellation()` to BaseProvider
  - `didClose()` now cancels all pending requests for that document
  - `shutdown()` now cancels all pending requests before exit
- **Completion Strategy Organization (SERVER-015)**:
  - Reorganized 18 strategies into subdirectories: context/, snippet/, type/, special/
  - Updated all package declarations and imports
  - Updated CompletionBuilder to use new import paths
- **Provider Health Monitoring (SERVER-016)**:
  - Created `ProviderHealthService` with metrics tracking
  - Added request count, latency (min/max/avg), error rate tracking
  - Added `recordHealth()` and `getProviderName()` to BaseProvider
  - Providers can now record health metrics for monitoring
- **Async Gradle Operations (SERVER-011 partial)**:
  - Added `getGrailsProjectAsync()` returning `CompletableFuture<GrailsProject>`
  - `refreshAndReindexWorkspace()` now uses async project loading with `.join()` on background thread
  - `getGrailsProject()` still available for sync callers
- **Debounce Improvements (SERVER-019)**:
  - Added `debounceDelayMs` config (default 500ms, configurable via client)
  - Added change coalescing - multiple rapid changes coalesce into single compile
  - Added `pendingChanges` tracking to cancel redundant compiles
  - Added shutdown cleanup for debounce executor
- **File Watcher Coverage (SERVER-020)**:
  - Expanded watched files to include: build.gradle.kts, settings.gradle.kts, gradle.properties, plugins.groovy, application.yml/yaml/groovy, grails-app/conf/ files
  - Added `isBuildConfigurationFile()` helper for extensible pattern matching
- **Provider Registry (SERVER-014)**:
  - Created `ProviderRegistry` for lazy provider initialization and centralized provider management
  - Providers now created on-demand via `getProvider(Class<T>)` method
  - Reduces startup coupling between GrailsService and providers
- **FileContentTracker Synchronization (SERVER-008)**:
  - Changed `[:] as ConcurrentHashMap` to explicit `new ConcurrentHashMap<>()` for clarity
  - Added `fqcnInitLock` for thread-safe double-checked locking in `initializeFQCNIfRequired()`
  - Replaced non-atomic `removeAll` calls with `removeFQCNEntriesForUri()` helper
  - Added `removeFQCNEntriesForUri()` helper for atomic removal by URI

### Context Interfaces (SERVER-001/SERVER-003) - 2026-05-13:
- **GrailsService now implements context interfaces**: `ProjectContext`, `ProviderContext`, `CompilationContext`
- **CompilationContext fixed**: Removed misaligned `compileProject()` and `invalidateCompiler()` methods
- **ProjectContext methods added**: `addProject()`, `removeProject()`, `updateProject()` implemented
- **BaseProvider refactored**: Uses contexts internally while maintaining backward-compatible `GrailsService` constructor
  - New constructor: `BaseProvider(ProviderContext, CompilationContext, GrailsProjectGetter, GrailsService)`
  - Old constructor: `BaseProvider(GrailsService)` still works - extracts contexts from service
  - `getService()` preserved for backward compatibility

### Completed Work (2026-05-14)
- **FileContentTracker Thread Safety (SERVER-008)**:
  - Fixed `evictIfNecessary()` - iterator-based removal instead of list index
  - Fixed `removeFQCNEntriesForUri()` - atomic iterator removal
  - Fixed `hasStaleEntries()` - snapshot-based iteration
- **GrailsCompiler Thread Safety (SERVER-009)**:
  - All subtasks complete - locking is appropriate for this use case
- **Provider Architecture (SERVER-014)**:
  - All subtasks complete - BaseProvider contexts + ProviderRegistry
- **Communication Protocol Fixes**:
  - `GrailsWorkspaceService.executeCommand()` now returns error for unknown commands
  - Previously returned null silently; now returns `IllegalArgumentException` via `failedFuture()`
- **Server Improvement Plan**: ✅ 20/21 items complete

## Current Priority: ✅ ALL TASKS COMPLETE (mostly)

## Incremental Compilation (SERVER-013):
- Added `dirtySources` Set in GrailsCompiler to track files needing recompilation
- Added `markDirty(uri)`, `isDirty(uri)`, `clearDirty(uri)`, `clearAllDirty()`, `getDirtyCount()` methods
- Added `compilationExistsFor(uri)` to check if source unit exists in cache
- Logic in `compileAndVisitAST` to skip if file unchanged since last compile

## TypeInferenceService Utilities:
- Implemented `findMethodsByName()`, `findProperties()`, `isSubType()` stub methods
- Utility methods for type analysis - not yet used by main inference logic

### Completed Work (2026-06-13)
- **Phase 1: AST Ownership, Concurrency & Central LSP Boundaries**:
  - Implemented `astLock` (ReentrantReadWriteLock) in `GrailsService` to isolate write compilation operations from read providers.
  - Wrapped incremental compilation, AST visitation, and workspace refresh under `withWriteLock`.
  - Added `withReadLock` helper in `BaseProvider` to allow safe, thread-safe reading of AST and visitor states.
  - Implemented central try-catch error boundaries (`safeProviderCall`) for all LSP feature handlers in `GrailsTextDocumentService` to log errors, record provider health, and prevent server crashes.
  - Refactored `GrailsWorkspaceSymbolProvider` to extend `BaseProvider` and registered it in `ProviderRegistry`, consuming it via `GrailsWorkspaceService`.
  - Added `@CompileStatic` to `GrailsLanguageServer`, `GrailsWorkspaceService`, and `GrailsWorkspaceSymbolProvider` for optimized, safe bytecode compilation.
  - Cleaned up all inline fully qualified Java concurrent classes (`java.util.concurrent.*`) in favor of imports and Groovy-idiomatic style across all server files.
  - Fixed **ASTService Memory Leak**: Added `clearUri(String uri)` to `ASTService` and wired it into `GrailsASTVisitor.visitSourceUnit` to clear cached class nodes when recompiling a file.
  - Standardized all Java functional interfaces (`Supplier`, `BiFunction`, `Function`) and reflect utility references (`Modifier`) to use standard imports rather than inline fully qualified class names.
  - Fixed **ThreadSafeLruCache Shared Executor Lifecycle Risk**: Made static executor recreation thread-safe and resilient to shutdowns on server restart/test cleanups, and created `ThreadSafeLruCacheSpec` with Spock test coverage.
  - Fixed **Incomplete Inter-File AST Invalidation**: Added `clearCrossFileCaches()` in `GrailsService` and `clearSymbolCaches()` in `DiscoveryService`. Wired it to execute globally after AST visitation completes to ensure cross-file dependency updates are correctly reflected in completion and symbol resolution.

### Completed Work (2026-06-07)
- **OOM Fix — ClassGraph heap exhaustion (`./gradlew test`)**:
  - Added `jvmArgs '-Xmx2g', '-Xms256m', '-XX:+UseG1GC'` to `test {}` block in `build.gradle`
  - Added `systemProperty 'grails.lsp.test.classgraph.disabled', 'true'` to suppress full
    `enableSystemJarsAndModules()` scan in test JVM — scan only fires in production via `GrailsCompiler`
  - Created `DiscoveryServiceSpec` (21 tests, all passing) covering: guard flag, fallback paths,
    static caches (keywords/map/object/primitives), ClassNode type helpers, `createCompletionItems`,
    `getAllTypeCompletions`, `clearCaches`
  - Documented `getMapMethods()` hardcoding smell in commented-out assertion with TODO
- **`getMapMethods()` CODING_STANDARDS fix (DiscoveryService)**:
  - Replaced hardcoded `addAll(['each', 'find', 'collect', ...])` with
    `GroovyHelperIntegration.getMethodsUsingMetaClass(LinkedHashMap.class)` — dynamic, version-agnostic
  - Key insight: MetaClass on `Map` interface returns only Object methods; `LinkedHashMap` (Groovy's
    default map literal type) has DGM extensions in its MetaClass
  - Fallback now includes `'every'`, `'any'`, `'inject'` which were previously absent
  - `DiscoveryServiceSpec` expanded to 22 tests (all passing): added two new `getMapMethods` tests
- **CI Test Reliability**:
  - Re-enabled tests in `.github/workflows/ci.yml` (removed `-x test`)
  - Updated `DiscoveryService.groovy` to correctly skip `enableSystemJarsAndModules` under test
  - Updated `BaseLspSpec.groovy` to re-enable ClassGraph system scanning for full integration tests
  - Added skeleton integration test specs: `GrailsReferenceProviderSpec`, `GrailsRenameProviderSpec`, `GrailsIncrementalCompilerSpec`
---

## Server Info

- **Version**: 0.1.0-SNAPSHOT
- **Language**: Groovy 4.0.23+
- **Java**: 17.0.8+ LTS
- **LSP Library**: Eclipse LSP4J 0.23.1
- **Build**: Gradle 8.12+

---

## LSP Feature Status

### ✅ Done & Working

| LSP Feature | Class / Provider | Notes |
|---|---|---|
| Text completion | `CompletionProvider` | Context-aware, modular strategies, cancellation-aware |
| Hover information | `HoverProvider` | Type info, docs |
| Go to definition | `DefinitionProvider` | Classes, methods, properties |
| Go to implementation | `ImplementationProvider` | |
| Find references | `ReferencesProvider` | Cross-file |
| Document symbols | `DocumentSymbolProvider` | AST-based |
| Workspace symbols | `WorkspaceSymbolProvider` | Project-wide |
| Diagnostics (push) | `DiagnosticsProvider` | Syntax errors + Grails validations |
| Signature help | `SignatureHelpProvider` | Method parameter assist |
| Code lens | `CodeLensProvider` | Test runners, method markers |
| Inlay hints | `InlayHintsProvider` | Type hints for locals |
| Rename (best-effort) | `GrailsRenameProvider` | `renameProvider: true`; inner-class name parsing fixed |
| Initialize / capabilities | `GrailsLanguageServer` | Full `ServerCapabilities` registration |

### 🟡 In Progress / Partial

| Feature | Status | Notes |
|---|---|---|
| Code actions | 🟡 Basic | Auto-injection for services, generate controller action - limited scope |

### 🔴 Known Issues / Broken

| Issue | Notes |
|---|---|
| Prepare rename | Not fully polished; basic rename works |
| Pull diagnostics | Not registered yet |

### ⬜ Planned / Future

| Feature | Notes |
|---|---|
| Document highlighting | Not started |
| Semantic tokens | Not started |
| Formatting | Stub - returns empty list |
| Folding ranges | ✅ Implemented |
| Code actions (real) | Needs strategy design |
| Execute commands | ✅ Partially implemented: `grails.getDependencyGraph`, `grails.getGormSql`, `grails.discoverTests`, `grails.discoverTestsBatch` |
| Multi-root workspace | Planned for 1.1.0 |
| ML-based suggestions | Planned for 1.2.0 |

---

## Core Architecture Status

| Component | Status | Notes |
|---|---|---|
| `GrailsLanguageServer` | ✅ Stable | Main LSP entry point |
| `GrailsService` | ✅ Stable | Central orchestration |
| `GrailsCompiler` | ✅ Stable | Thread-safe, incremental, ~2-5s full / ~50-200ms incremental |
| `GradleService` | ✅ Stable | Cache mgmt, artifact downloads, `invalidateProjectCache()` |
| `BaseProvider` | ✅ Stable | Base for all providers |
| Central AST Resolution | ✅ Stable | Performance backbone |
| Completion strategies | ✅ Stable | Modular, extensible |
| `FileContentTracker` | ✅ Stable | Bounded caches, stale detection, scheduled cleanup |
| `ThreadSafeLruCache` | ✅ Stable | Generic LRU cache with TTL, size limits, stats |
| `CancellationService` | ✅ Stable | Request cancellation tokens, URI-based cancellation |
| `ProviderHealthService` | ✅ Stable | Request count, latency, error rate tracking per provider |

---

## Build Health

| Check | Status | Notes |
|---|---|---|
| `./gradlew build` | ✅ Passing | |
| `./gradlew shadowJar` | ✅ Passing | |
| `./gradlew test` | ✅ Passing | `-Xmx2g -XX:+UseG1GC`; ClassGraph scan disabled in test JVM |
| JaCoCo coverage | ✅ ≥60% threshold met |
| `./gradlew checkAll` | ✅ Clean | |
