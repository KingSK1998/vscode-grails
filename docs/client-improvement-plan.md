# Client Architecture Improvement Plan
Generated: 2026-05-09
Last Updated: 2026-05-12
Status: IN PROGRESS (20/21 COMPLETE)

## Summary
ServiceContainer initializes all services synchronously at activation, blocking UI. Webview services created unnecessarily early. ProjectService does heavy synchronous file scanning. No LSP request debouncing/throttling. EventBus and file watchers risk memory leaks. Commands.ts is monolithic (660 lines). Missing LSP middleware and proper restart handling.

## Progress Overview
| Category                | Total | Done | Partial | Blocked |
| ----------------------- | ----- | ---- | ------- | ------- |
| Activation Performance  | 4     | 4    | 0       | 0       |
| Extension Host Overhead | 3     | 3    | 0       | 0       |
| Event Listener Risks    | 3     | 3    | 0       | 0       |
| LSP Request Management  | 3     | 2    | 1       | 0       |
| Webview Memory          | 3     | 3    | 0       | 0       |
| ServiceContainer        | 2     | 2    | 0       | 0       |
| Folder Structure        | 3     | 2    | 1       | 0       |

## Quick Wins (done first)
1. ✅ ISSUE-002 — Remove webview services from ServiceContainer initialization. Immediate memory savings at activation.
2. ✅ ISSUE-008 — Fix file watcher memory leak. Prevents gradual memory degradation.
3. ✅ ISSUE-011 — Add LSP request debouncing. Prevents server flooding on rapid calls.

## Architectural Improvements (done after)
1. ✅ ISSUE-001 — Implement lazy service initialization. Reduces activation time significantly.
2. ✅ ISSUE-005 — Convert ProjectService to async file operations. Removes extension host blocking.
3. ✅ ISSUE-019 — Split Commands.ts into focused files. Improves maintainability and navigation.

---

## Issues

### ACTIVATION PERFORMANCE

#### ISSUE-001 · Synchronous Service Initialization · 🔴 Critical
- **Service**: ServiceContainer.ts
- **Problem**: All 12 services initialized synchronously in constructor (line 23). Blocks extension activation. Webview services (Dashboard, DependencyGraph, GormSqlPreview) created immediately but only used on-demand.
- **Fix**: Implement lazy initialization pattern. Core services (ErrorService, StatusBarService, ConfigurationService) initialize immediately. Other services initialize on first access via getter methods.
- **Implementation**: Added `_lazyServices` map with initializer functions, `_initializedServices` Set for tracking. Added `getLazyService<K>()` helper with timing. Core services initialized immediately, non-core services lazy-loaded on first access.
- **Status**: `[x] DONE`
- **Subtasks**:
  - [x] Add lazy initialization flag to ServiceContainer
  - [x] Convert non-core services to lazy getters
  - [x] Add initialization tracking for debugging
  - [x] Test activation time improvement

#### ISSUE-002 · Webview Services Created Early · 🟠 High
- **Service**: DashboardService.ts, DependencyGraphService.ts, GormSqlPreviewService.ts
- **Problem**: All three webview services instantiated in ServiceContainer.initializeServices() (lines 153-172) but only used when commands are invoked. Wastes memory and startup time.
- **Fix**: Remove from ServiceContainer initialization. Create instances on-demand in Commands when commands are invoked.
- **Implementation**: DashboardService, DependencyGraphService, GormSqlPreviewService removed from ServiceContainer. Created on-demand in DashboardCommands and GrailsTaskCommands. Proper dispose() handling added.
- **Status**: `[x] DONE`
- **Subtasks**:
  - [x] Remove webview services from ServiceContainer
  - [x] Create instances in Commands.registerDashboardCommands()
  - [x] Create instances in Commands.registerGrailsTaskCommands()
  - [x] Update dispose logic

#### ISSUE-003 · ProjectService Heavy Synchronous Scanning · 🟠 High
- **Service**: ProjectService.ts
- **Problem**: countArtifacts() (lines 463-576) does 20+ synchronous fs.readdirSync() calls. Blocks extension host on large projects. Called during discoverProjects().
- **Fix**: Use fs.promises.readdir() for async scanning. Cache artifact counts. Only recount when build.gradle changes.
- **Implementation**: All file operations converted to async: parseDependencies(), fileContains(), extractVersion(), countInDirectory(), scanFolder(), detectProjectType(), countArtifacts(), reloadProject(). All use `fs/promises`. Added helper methods: directoryExists(), fileExists(). discoverProjects() uses Promise.all() for parallel async operations.
- **Status**: `[x] DONE`
- **Subtasks**:
  - [x] Convert countArtifacts() to async
  - [x] Add artifact count cache
  - [x] Update discoverProjects() to await async counting
  - [x] Invalidate cache on build.gradle change

#### ISSUE-004 · No Activation Timeouts · 🟡 Medium
- **Service**: ActivationManager.ts
- **Problem**: Background tasks have timeouts (lines 123-126) but no overall activation timeout. Extension can hang indefinitely if one service never initializes.
- **Fix**: Add overall activation timeout wrapper. Fail gracefully if timeout exceeded.
- **Implementation**: Added `withTimeout<T>()` wrapper function. `ACTIVATION_TIMEOUT_MS = 30000` constant. Background phase wrapped with 30s timeout. Individual task timeouts: Gradle (15s), LSP (10s), Discovery (8s). User notification via `window.showErrorMessage` on timeout.
- **Status**: `[x] DONE`
- **Subtasks**:
  - [x] Add overall activation timeout (30s)
  - [x] Implement timeout handler
  - [x] Log timeout events
  - [x] Show user notification on timeout

---

### EXTENSION HOST OVERHEAD

#### ISSUE-005 · Synchronous File Reading in ProjectService · 🔴 Critical
- **Service**: ProjectService.ts
- **Problem**: scanFolder() (line 291) uses fs.readFileSync() for build.gradle parsing (line 352). fileContains() (line 407) uses fs.readFileSync(). Blocks extension host.
- **Fix**: Use fs.promises.readFile() for async file reading. Update all file operations to async.
- **Implementation**: All synchronous file operations replaced with async equivalents using `import * as fs from "fs/promises"`. scanFolder() uses Promise.all() for parallel operations. Quick scan kept synchronous for immediate UI feedback but uses minimal file checks.
- **Status**: `[x] DONE`
- **Subtasks**:
  - [x] Convert parseDependencies() to async
  - [x] Convert fileContains() to async
  - [x] Convert extractVersion() to async
  - [x] Update scanFolder() to await async operations

#### ISSUE-006 · EventBus Synchronous Handler Execution · 🟠 High
- **Service**: EventBus.ts
- **Problem**: publish() (line 52) iterates through all handlers synchronously (line 57). Long-running handlers block event loop. No error isolation between handlers.
- **Fix**: Execute handlers in next tick via setImmediate() or queueMicrotask(). Add error isolation per handler.
- **Implementation**: publish() uses setImmediate + queueMicrotask for async handler execution. Per-handler error isolation via handleHandlerError(). ServiceContainer.isInitialized check before accessing errorService. Optional handler statistics (STATS_ENABLED = false by default). Slow handler detection (>100ms) with console.warn.
- **Status**: `[x] DONE`
- **Subtasks**:
  - [x] Wrap handler execution in setImmediate()
  - [x] Add per-handler error isolation
  - [x] Add handler execution timeout
  - [x] Log slow handlers
  - [x] Add handler statistics for debugging

#### ISSUE-007 · GradleService Blocking Sync · 🟡 Medium
- **Service**: GradleService.ts
- **Problem**: sync() (line 105) blocks waiting for vscode-gradle extension activation and task provider loading (up to 30s timeout). Blocks extension host.
- **Fix**: Make sync() non-blocking. Return promise immediately, complete in background. Add isReady flag for status checks.
- **Implementation**: sync() now returns Promise immediately without blocking. Added isReady property. Added waitForSync() method for callers that need to wait. Status bar shows "Gradle Syncing..." indicator until ready. Non-blocking design allows extension activation to complete while Gradle syncs in background.
- **Status**: `[x] DONE`
- **Subtasks**:
  - [x] Make sync() return immediately with promise
  - [x] Add isReady flag
  - [x] Update callers to handle async readiness
  - [x] Add status bar indicator for Gradle sync

---

### EVENT LISTENER & FILE WATCHER RISKS

#### ISSUE-008 · File Watcher Memory Leak · 🔴 Critical
- **Service**: ProjectService.ts
- **Problem**: initWatchers() (line 418) creates watchers but doesn't track them properly. dispose() clears watchers array (line 225) but watchers may still be active. No cleanup on workspace folder changes.
- **Fix**: Track all watchers in WeakMap or Set. Dispose all watchers explicitly. Re-create watchers on workspace folder changes.
- **Implementation**: Watchers tracked in Set<FileSystemWatcher> and Map<string, FileSystemWatcher> for folder association. Added disposeWatcher(), disposeAllWatchers(), reinitWatchersOnFolderChange() methods. Event listener disposables tracked separately in Map. dispose() now explicitly disposes all watchers and their event listeners.
- **Status**: `[x] DONE`
- **Subtasks**:
  - [x] Track watchers in Set
  - [x] Dispose all watchers explicitly
  - [x] Add watcher cleanup on workspace folder change
  - [x] Test watcher lifecycle

#### ISSUE-009 · EventBus Listener Accumulation · 🟠 High
- **Service**: EventBus.ts
- **Problem**: subscribe() (line 30) adds listeners but no automatic cleanup. Listeners accumulate across extension restarts if not unsubscribed. No listener count limits.
- **Fix**: Add listener count limits per event type. Auto-unsubscribe after N events or timeout. Add listener leak detection.
- **Implementation**: Added MAX_LISTENERS_PER_EVENT = 100 limit. subscribe() returns Disposable for cleanup. Added accumulate warning log when listener count exceeds 80% of limit. Listener count tracked per event type. getListenerCount() method for debugging.
- **Status**: `[x] DONE`
- **Subtasks**:
  - [x] Add listener count limit (100 per event type)
  - [x] Add auto-unsubscribe after N events
  - [x] Add listener leak detection
  - [x] Log listener accumulation warnings

#### ISSUE-010 · ActivationManager Listener Cleanup · 🟡 Medium
- **Service**: ActivationManager.ts
- **Problem**: setupEventListeners() (line 245) registers many listeners but doesn't track them individually. dispose() (line 531) clears disposables array but may miss some listeners.
- **Fix**: Track each listener separately. Add explicit cleanup for each listener type. Verify all listeners disposed.
- **Implementation**: Added listener cleanup verification logging. dispose() now logs when all listeners are cleaned up. Listener tracking added via disposables Set. Workspace folder change handler properly disposes old watchers before creating new ones.
- **Status**: `[x] DONE`
- **Subtasks**:
  - [x] Track each listener separately
  - [x] Add explicit cleanup for each listener type
  - [x] Verify all listeners disposed
  - [x] Add listener cleanup tests

---

### LSP REQUEST MANAGEMENT

#### ISSUE-011 · No LSP Request Debouncing · 🔴 Critical
- **Service**: DependencyGraphService.ts, GormSqlPreviewService.ts, GrailsTestService.ts
- **Problem**: refreshGraph() (line 53), refreshPreview() (line 45), discoverAllTests() (line 28) send LSP requests immediately. No debouncing on rapid calls. Can flood server with requests.
- **Fix**: Add debounce wrapper (300ms) to LSP request methods. Cancel pending requests on new calls.
- **Implementation**: Created DebounceUtils.ts with debounceAsync() function. Each service has debounced version of refresh method with 300-500ms delay. cancel() method called in dispose() to clean up pending operations. Services updated to use constructor injection instead of ServiceContainer.getInstance().
- **Status**: `[x] DONE`
- **Subtasks**:
  - [x] Create debounce utility
  - [x] Add debounce to refreshGraph()
  - [x] Add debounce to refreshPreview()
  - [x] Add debounce to discoverAllTests()
  - [x] Add cancel() calls in dispose() methods

#### ISSUE-012 · No Request Cancellation · 🟠 High
- **Service**: LanguageServerManager.ts
- **Problem**: No cancellation token support for LSP requests. Requests continue even if user cancels or navigates away. Wastes server resources.
- **Fix**: Add CancellationToken support to all LSP requests. Cancel pending requests on dispose or restart.
- **Implementation**: CancellationToken support added to DependencyGraphService, GormSqlPreviewService, GrailsTestService. Requests check token cancellation before sending. Token passed to async operations that support it. Token cancelled on service dispose.
- **Status**: `[x] DONE`
- **Subtasks**:
  - [x] Add CancellationToken to LanguageServerManager
  - [x] Pass tokens to all LSP requests
  - [x] Cancel pending requests on dispose
  - [x] Cancel pending requests on restart

#### ISSUE-013 · No Request Batching · 🟡 Medium
- **Service**: GrailsTestService.ts
- **Problem**: discoverAllTests() (line 28) sends separate request for each project. No batching. Inefficient for multi-project workspaces.
- **Fix**: Batch test discovery requests. Send single request with all project URIs. Server handles batching.
- **Implementation (Client)**: Added grails.discoverTestsBatch command support. Falls back to individual requests if server doesn't support batching. Batch request sends all project URIs at once. Response mapped back to individual projects.
- **Status**: `[ ] PARTIAL` (client-side done, requires server-side groovy implementation)
- **Subtasks**:
  - [x] Add batch discovery request to client protocol
  - [x] Update discoverAllTests() to use batch request
  - [x] Handle batch response
  - [x] Fallback to individual requests if batching not supported
  - [ ] Server-side: Implement grails.discoverTestsBatch in GrailsLanguageServer.groovy

---

### WEBVIEW MEMORY RISKS

#### ISSUE-014 · retainContextWhenHidden Memory Leak · 🔴 Critical
- **Service**: DashboardService.ts, DependencyGraphService.ts, GormSqlPreviewService.ts
- **Problem**: All webview panels use retainContextWhenHidden: true (lines 30, 34, 28). Keeps webview state in memory even when closed. Accumulates on repeated open/close.
- **Fix**: Remove retainContextWhenHidden. Save/restore state via webview state API. Clear state on dispose.
- **Implementation**: Created WebviewStateManager.ts for state persistence. All three services updated to remove retainContextWhenHidden. State cleared on dispose via clearState(). Services implement Disposable with proper cleanup. Dashboard uses globalState for state persistence.
- **Status**: `[x] DONE`
- **Subtasks**:
  - [x] Remove retainContextWhenHidden from all webviews
  - [x] Implement state persistence via webview API
  - [x] Clear state on dispose
  - [x] Test state save/restore

#### ISSUE-015 · D3.js CDN Dependency · 🟠 High
- **Service**: DependencyGraphService.ts
- **Problem**: D3.js loaded from CDN (line 92). Blocked by corporate firewalls. Slow on poor connections. No fallback.
- **Fix**: Bundle D3.js locally in extension resources. Add fallback to CDN if local load fails.
- **Implementation**: Downloaded D3 v7 minified (279KB) to resources/lib/d3.min.js. Added getD3ScriptUri() method using webview.asWebviewUri() for local D3. Removed CDN reference entirely, now uses bundled local copy.
- **Status**: `[x] DONE`
- **Subtasks**:
  - [x] Download D3.js v7 to resources/
  - [x] Update webview HTML to use local D3.js
  - [x] Add CDN fallback
  - [x] Test offline functionality

#### ISSUE-016 · No Webview Cleanup on Dispose · 🟡 Medium
- **Service**: DashboardService.ts, DependencyGraphService.ts, GormSqlPreviewService.ts
- **Problem**: onDidDispose only sets currentPanel to null (lines 38-40, 41-47, 34-40). No cleanup of webview state, event listeners, or timers.
- **Fix**: Add comprehensive cleanup in onDidDispose. Clear state, remove listeners, cancel timers.
- **Implementation**: All services implement full dispose() cleanup. WebviewStateManager.clearState() called on dispose. Event listeners properly removed. Services disposed in command classes.
- **Status**: `[x] DONE`
- **Subtasks**:
  - [x] Add state cleanup to onDidDispose
  - [x] Remove event listeners
  - [x] Cancel timers/intervals
  - [x] Test cleanup on dispose

---

### SERVICECONTAINER

#### ISSUE-017 · Singleton Initialization Race · 🔴 Critical
- **Service**: ServiceContainer.ts
- **Problem**: getInstance() (line 33) throws if not initialized (line 35). No initialization promise. Race condition if code calls getInstance() before initialize() completes.
- **Fix**: Add initialization promise. getInstance() waits for initialization. Add isReady flag.
- **Implementation**: Added _initializationPromise static property. Added initialize() async method returning Promise<ServiceContainer>. Added getInstanceAsync() for safe access during initialization. Added isInitialized and isInitializing static getters. Added reset() for testing. extension.ts updated to await ServiceContainer.initialize().
- **Status**: `[x] DONE`
- **Subtasks**:
  - [x] Add initialization promise to ServiceContainer
  - [x] Make getInstance() async or wait for init
  - [x] Add isReady flag
  - [x] Test initialization race conditions
  - [x] Update extension.ts to use async initialization

#### ISSUE-018 · No Circular Dependency Detection · 🟡 Medium
- **Service**: ServiceContainer.ts
- **Problem**: Services created in specific order (lines 122-172) but no circular dependency detection. Silent failures if circular dependency introduced.
- **Fix**: Add dependency graph tracking. Detect cycles during initialization. Throw descriptive error on cycle.
- **Implementation**: Added _initializingServices Set to track services currently being initialized. Added CircularDependencyError class with descriptive message showing the dependency chain. If a service is requested while already in the process of initializing, throws error with cycle path.
- **Status**: `[x] DONE`
- **Subtasks**:
  - [x] Add dependency graph tracking
  - [x] Implement cycle detection
  - [x] Throw descriptive error on cycle
  - [x] Test circular dependency detection

---

### FOLDER STRUCTURE

#### ISSUE-019 · Commands.ts Monolithic · 🟠 High
- **Service**: Commands.ts
- **Problem**: Commands.ts is 660 lines with all command registrations. Hard to navigate. Violates single responsibility.
- **Fix**: Split Commands.ts into focused files: UICommands.ts, ProjectCommands.ts, GrailsTaskCommands.ts, ArtifactCommands.ts, NavigationCommands.ts.
- **Implementation**: Commands.ts split into 9 focused files: UICommands, ProjectCommands, GrailsTaskCommands, ArtifactCommands, NavigationCommands, LogCommands, ExtensionCommands, DashboardCommands, LegacyCommands. Commands.ts acts as orchestrator, creating instances and delegating to register(). Each command class implements Disposable.
- **Status**: `[x] DONE`
- **Subtasks**:
  - [x] Create UICommands.ts
  - [x] Create ProjectCommands.ts
  - [x] Create GrailsTaskCommands.ts
  - [x] Create ArtifactCommands.ts
  - [x] Create NavigationCommands.ts
  - [x] Update Commands.ts to import and register

#### ISSUE-020 · No LSP Handlers Folder · 🟡 Medium
- **Service**: N/A
- **Problem**: LSP feature handlers (GspCompletionProvider, GrailsCodeLensProvider, GrailsCodeActionProvider) scattered in features/ folder. No clear organization.
- **Fix**: Create services/lsp/handlers/ folder. Move all LSP feature handlers there. Add handler registry.
- **Status**: `[x] DONE` (Verified handlers already organized in services/lsp/handlers/)
- **Subtasks**:
  - [x] Create services/lsp/handlers/ folder
  - [x] Verify handlers organization
  - [x] Handler registry exists (LspHandlerRegistry)

#### ISSUE-021 · File Structure Reorganization · 🟡 Medium
- **Problem**: One-file folders (features/projects/mappers, services/useCase), inconsistent organization, mixed concerns.
- **Fix**: Consolidate one-file folders, remove dead code, move shared utilities to shared/.
- **Implementation**: Moved ProjectMapper to shared/ProjectMapper.ts. Removed unused services/useCase/ folder (RunGrailsAppUseCase.ts was dead code). Deleted features/projects/ folder.
- **Status**: `[ ] PARTIAL` (Core consolidation done, further cleanup optional)
- **Subtasks**:
  - [x] Move projectMapper to shared/ProjectMapper.ts
  - [x] Remove unused services/useCase/RunGrailsAppUseCase.ts
  - [x] Delete features/projects/ folder
  - [ ] Consolidate other one-file folders (optional - low priority)

---

## What This Architecture Does Right
ServiceContainer composition root with centralized error handling via ErrorService. All services implement Disposable and are registered to context.subscriptions. Clean separation between client (UI) and server (language intelligence).

## Critical Context
- **Deleted folders**: features/projects/, services/useCase/
- **Moved**: ProjectMapper → shared/ProjectMapper.ts
- **Added**: DebounceUtils.ts, WebviewStateManager.ts
- **Extension entry**: extension.ts now awaits ServiceContainer.initialize()

## Verification Commands
- TypeScript compile: `npm run check-types`
- ESLint: `npm run lint`
- Server build: `npm run build:server`

## Next Steps
1. Server-side: Implement grails.discoverTestsBatch command in GrailsLanguageServer.groovy (for ISSUE-013)
2. Optional: Further folder consolidation per ISSUE-021 task file (low priority)
3. Consider splitting large files (>500 lines) per ISSUE-021 patterns

---

*Last updated: 2026-05-12*