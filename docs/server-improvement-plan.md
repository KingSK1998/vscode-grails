# Server Architecture Improvement Plan
Generated: 2026-05-12
Status: IN PROGRESS

## Summary
GrailsService is a "god object" with too many responsibilities. Providers initialized inconsistently. No proper LRU cache. Completion cache has no expiration. Blocking I/O on LSP thread. Gradle operations block LSP. File tracking has no invalidation strategy. No cancellation handling. 22 utility classes with overlapping concerns.

---

## Progress Overview
| Category                | Total | Done | Partial | Blocked |
| ----------------------- | ----- | ---- | ------- | ------- |
| God Object Decoupling   | 3     | 0    | 0       | 0       |
| Cache Management        | 4     | 0    | 0       | 0       |
| Thread Safety           | 3     | 0    | 0       | 0       |
| Async I/O               | 3     | 0    | 0       | 0       |
| Provider Architecture    | 3     | 0    | 0       | 0       |
| Cancellation Support    | 2     | 0    | 0       | 0       |
| File Watching           | 2     | 0    | 0       | 0       |

---

## QUICK WINS

### 1. Add grails.discoverTestsBatch Command
- **Problem**: Client wants batch test discovery for ISSUE-013
- **Solution**: Add new command handler in GrailsWorkspaceService
- **Status**: ⬜ TODO
- **Subtasks**:
  - [ ] Add grails.discoverTestsBatch to executeCommandProvider
  - [ ] Implement batch discovery in GrailsTestDiscoveryProvider
  - [ ] Test with multi-project workspaces

### 2. Add Cache Expiration to Completion Cache
- **Problem**: GRAILS-055 - Completion cache has CACHE_TTL_MS = 30s but never actually expires
- **Solution**: Add background cleanup thread
- **Status**: ⬜ TODO
- **Subtasks**:
  - [ ] Add cache cleanup scheduled executor
  - [ ] Remove expired entries periodically
  - [ ] Add cache statistics endpoint

### 3. Add Thread Interruption Check in Completion
- **Problem**: Completion can be interrupted but doesn't check Thread.interrupted()
- **Solution**: Add interruption check in doProvideCompletions
- **Status**: ⬜ TODO
- **Subtasks**:
  - [ ] Add isInterrupted() check before expensive operations
  - [ ] Throw CancellationException when interrupted

---

## ARCHITECTURAL IMPROVEMENTS

### GOD OBJECT DECOUPLING

#### SERVER-001 · GrailsService God Object · 🔴 Critical
- **Problem**: GrailsService has 20+ fields managing everything. Violates single responsibility.
- **Current**: `final GrailsWorkspaceService workspace`, `final GrailsTextDocumentService document`, etc.
- **Fix**: Split into focused services with clear interfaces. Use ServiceLocator pattern.
- **Subtasks**:
  - [ ] Extract WorkspaceContext interface
  - [ ] Extract DocumentContext interface
  - [ ] Create ServiceLocator for cross-service access
  - [ ] Break up GrailsService into composition
  - [ ] Update all providers to use new context

#### SERVER-002 · Inconsistent Provider Initialization · 🟠 High
- **Problem**: Some providers created in GrailsService constructor, others in GrailsTextDocumentService
- **Current**: `completionProvider = new GrailsCompletionProvider(service)` in both places
- **Fix**: Single provider registry with consistent lifecycle management
- **Subtasks**:
  - [ ] Create ProviderRegistry class
  - [ ] Move all provider creation to registry
  - [ ] Add provider dispose() support
  - [ ] Update GrailsTextDocumentService to use registry

#### SERVER-003 · Direct Service Access via GrailsService · 🟡 Medium
- **Problem**: All providers receive GrailsService and access everything via it
- **Fix**: Use interface segregation - providers only get what they need
- **Subtasks**:
  - [ ] Define provider-specific interfaces (ICompletionContext, IDefinitionContext)
  - [ ] Refactor BaseProvider to use contexts
  - [ ] Reduce GrailsService field exposure

---

### CACHE MANAGEMENT

#### SERVER-004 · No LRU Cache with Size Eviction · 🔴 Critical
- **Problem**: MAX_FILES_IN_MEMORY approach not sufficient for large workspaces
- **Current**: `ConcurrentHashMap` with no size limits
- **Fix**: Implement proper LRU cache with size-based eviction
- **Subtasks**:
  - [ ] Create ThreadSafeLruCache<K,V> utility
  - [ ] Add max size configuration
  - [ ] Implement eviction listener
  - [ ] Replace FileContentTracker maps with LRU

#### SERVER-005 · No Cache Invalidation with Time-Based Eviction · 🟠 High
- **Problem**: Cache entries never expire based on time
- **Fix**: Add time-based eviction alongside size-based
- **Subtasks**:
  - [ ] Add timestamp tracking to cache entries
  - [ ] Add ScheduledExecutor for cleanup
  - [ ] Configure TTL per cache type
  - [ ] Log cache eviction statistics

#### SERVER-006 · No Stale Cache Handling for File Content · 🟠 High
- **Problem**: FileContentTracker has no explicit cache invalidation strategy
- **Fix**: Add cache validity checking on access
- **Subtasks**:
  - [ ] Add lastModified tracking
  - [ ] Add isStale() method
  - [ ] Invalidate on file change notification
  - [ ] Add forceInvalidate() method

#### SERVER-007 · Completion Cache No Expiration Policy · 🟡 Medium
- **Problem**: CACHE_TTL_MS = 30s defined but never enforced
- **Current**: Entries added but cleanupCache() only removes on size overflow
- **Fix**: Implement proper TTL cleanup
- **Subtasks**:
  - [ ] Track cache entry timestamps
  - [ ] Add cleanup thread for expired entries
  - [ ] Make cleanup interval configurable

---

### THREAD SAFETY

#### SERVER-008 · FileContentTracker Synchronization · 🔴 Critical
- **Problem**: Uses ConcurrentHashMap but implementation doesn't show proper synchronization
- **Current**: `trackedFiles = [:] as ConcurrentHashMap` but operations may not be atomic
- **Fix**: Review and fix compound operations
- **Subtasks**:
  - [ ] Audit all compound operations
  - [ ] Add synchronized blocks where needed
  - [ ] Add thread-safe iterator for fileDependencies
  - [ ] Add unit tests for concurrent access

#### SERVER-009 · GrailsCompiler Thread Safety · 🟠 High
- **Problem**: Compiler has a ReentrantLock but sourceUnitsCache is ConcurrentHashMap
- **Current**: `compileLock.lock()` for compilation, but cache access is unsynchronized
- **Fix**: Ensure all cache access protected properly
- **Subtasks**:
  - [ ] Add lock around sourceUnitsCache operations
  - [ ] Make cachedErrorCollector volatile properly
  - [ ] Add lock-free alternatives where possible

#### SERVER-010 · Background Executor Resource Leaks · 🟡 Medium
- **Problem**: backgroundExecutor = Executors.newCachedThreadPool() never explicitly shutdown
- **Fix**: Add proper lifecycle management
- **Subtasks**:
  - [ ] Add shutdown() method to GrailsService
  - [ ] Register shutdown hook
  - [ ] Track running tasks for graceful shutdown

---

### ASYNC I/O

#### SERVER-011 · Blocking Gradle Operations on LSP Thread · 🔴 Critical
- **Problem**: GradleToolingAPI operations can block LSP thread
- **Current**: getGrailsProject() is synchronous
- **Fix**: Make all Gradle operations async with CompletableFuture
- **Subtasks**:
  - [ ] Make getGrailsProject() return CompletableFuture
  - [ ] Add non-blocking project loading
  - [ ] Update callers to handle async
  - [ ] Add progress reporting for background loading

#### SERVER-012 · No CompletableFuture for I/O Operations · 🟠 High
- **Problem**: Most I/O operations are synchronous
- **Fix**: Use CompletableFuture.runAsync() for all file operations
- **Subtasks**:
  - [ ] Audit blocking operations in FileContentTracker
  - [ ] Audit blocking operations in GradleService
  - [ ] Add backgroundExecutor for I/O tasks
  - [ ] Add cancellation support

#### SERVER-013 · Incremental Compilation Not Fully Implemented · 🟠 High
- **Problem**: SCALABILITY_PERFORMANCE_ISSUES mentions incremental parsing but not fully implemented
- **Current**: compileSourceFile() exists but full recompilation happens often
- **Fix**: Track file dependencies and only recompile changed + dependents
- **Subtasks**:
  - [ ] Track file dependency graph
  - [ ] Implement dirty flag per source unit
  - [ ] Only recompile affected units on change
  - [ ] Add incremental AST updates

---

### PROVIDER ARCHITECTURE

#### SERVER-014 · Provider Constructor Injection Chaos · 🟠 High
- **Problem**: Each provider created with `new Provider(service)` - tight coupling
- **Current**: `new GrailsCompletionProvider(service)`, `new GrailsHoverProvider(service)`
- **Fix**: Use dependency injection with interfaces
- **Subtasks**:
  - [ ] Define provider interfaces
  - [ ] Create ProviderModule for DI
  - [ ] Add lazy initialization for heavy providers
  - [ ] Add provider health checks

#### SERVER-015 · 18+ Completion Strategies Need Organization · 🟡 Medium
- **Problem**: completions/ folder has 14+ strategy files
- **Current**: Mixed strategies in one folder
- **Fix**: Group by purpose (context-aware, snippet, artifact)
- **Subtasks**:
  - [ ] Create subdirectories: context/, snippet/, artefact/
  - [ ] Create completion strategy registry
  - [ ] Add priority ordering
  - [ ] Document strategy selection logic

#### SERVER-016 · No Provider Health Monitoring · 🟡 Medium
- **Problem**: No way to know if a provider is overloaded or failing
- **Fix**: Add metrics and health endpoints
- **Subtasks**:
  - [ ] Add request count metrics per provider
  - [ ] Add latency tracking
  - [ ] Add error rate tracking
  - [ ] Add health check method

---

### CANCELLATION SUPPORT

#### SERVER-017 · No Request Cancellation · 🔴 Critical
- **Problem**: Long-running operations can't be cancelled
- **Current**: No CancellationToken equivalent
- **Fix**: Add cancellation support to all CompletableFuture operations
- **Subtasks**:
  - [ ] Define CancellationToken interface
  - [ ] Add cancellation check points
  - [ ] Cancel pending operations on document close
  - [ ] Cancel on server shutdown

#### SERVER-018 · No Thread Interruption Handling · 🟠 High
- **Problem**: Thread.currentThread().isInterrupted() not checked
- **Current**: Only GrailsCompletionProvider checks at line 59
- **Fix**: Add interruption checks in all blocking operations
- **Subtasks**:
  - [ ] Add isInterrupted() checks before expensive ops
  - [ ] Propagate CancellationException on interrupt
  - [ ] Clean up resources on cancellation

---

### FILE WATCHING

#### SERVER-019 · No Debounce for File Change Events · 🟠 High
- **Problem**: Each keystroke triggers didChange events
- **Current**: 500ms debounce in didChange but could be improved
- **Fix**: Improve debounce logic and batch changes
- **Subtasks**:
  - [ ] Review debounce timing
  - [ ] Batch multiple rapid changes
  - [ ] Add change coalescing
  - [ ] Add user-configurable debounce delay

#### SERVER-020 · Incomplete File Watcher Coverage · 🟡 Medium
- **Problem**: Only watches build.gradle, settings.gradle
- **Current**: `event.uri.endsWith("build.gradle") || event.uri.endsWith("settings.gradle")`
- **Fix**: Watch more project files that affect compilation
- **Subtasks**:
  - [ ] Watch gradle.properties
  - [ ] Watch plugins.groovy
  - [ ] Watch grails-app/conf/ files
  - [ ] Watch application.yml/groovy

---

## ARCHITECTURE NOTES

### Current Package Structure (kingsk.grails.lsp/)
```
├── GrailsLanguageServer.groovy    # Entry point, capability registration
├── GrailsService.groovy           # God object - 20+ fields
├── core/
│   ├── compiler/                  # GrailsCompiler, CompilerOptions
│   ├── gradle/                    # ProjectCache, GrailsProjectBuilder
│   └── visitor/                   # GrailsASTVisitor
├── model/                         # Data classes
├── providersDocument/             # 18+ providers + completions/ subfolder
├── providersWorkspace/            # Workspace-level providers
├── protocol/                      # DTOs, mappers, client interface
├── services/                      # FileContentTracker, GradleService, ASTService, etc.
└── utils/                         # 22 utility classes
```

### Key Issues
1. **Utils bloat**: 22 utility classes - some overlap (GrailsASTHelper vs GrailsASTHelperUtils)
2. **Provider explosion**: 18+ providers, some very small
3. **GrailsService dependency web**: Everything depends on GrailsService
4. **No clear layer boundaries**: utils, services, providers all mixed

### What Works Well
1. **Provider pattern**: BaseProvider + modular strategies is good
2. **Completion strategies**: Strategy pattern well implemented
3. **File tracking**: Good abstraction over file operations
4. **Compilation**: GrailsCompiler is mature and well-tested

---

## CRITICAL CONTEXT

### Server-Side Remaining Client Issue
- **ISSUE-013**: Add `grails.discoverTestsBatch` command to support batch test discovery

### Verification Commands
- Build: `./gradlew build`
- Tests: `./gradlew test`
- All checks: `./gradlew checkAll`
- Shadow JAR: `./gradlew shadowJar`

### Entry Points
- **Main**: GrailsLanguageServer.main()
- **Services**: GrailsService constructor
- **Text Doc**: GrailsTextDocumentService
- **Workspace**: GrailsWorkspaceService

---

## NEXT STEPS

### Immediate Priority
1. **SERVER-017**: Add cancellation support (blocks other improvements)
2. **SERVER-004**: Implement LRU cache (performance critical)
3. **Add grails.discoverTestsBatch**: Complete client ISSUE-013

### After Fundamentals
1. **SERVER-001**: Decouple GrailsService (architectural foundation)
2. **SERVER-011**: Async Gradle operations (responsiveness)
3. **SERVER-013**: Incremental compilation (performance)

---

*Last updated: 2026-05-12*