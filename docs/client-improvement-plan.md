# Client Architecture Improvement Plan
Generated: 2026-05-09
Status: IN PROGRESS

## Summary
ServiceContainer initializes all services synchronously at activation, blocking UI. Webview services created unnecessarily early. ProjectService does heavy synchronous file scanning. No LSP request debouncing/throttling. EventBus and file watchers risk memory leaks. Commands.ts is monolithic (660 lines). Missing LSP middleware and proper restart handling.

## Progress Overview
| Category                | Total | Done | In Progress | Blocked |
| ----------------------- | ----- | ---- | ----------- | ------- |
| Activation Performance  | 4     | 0    | 0           | 0       |
| Extension Host Overhead | 3     | 0    | 0           | 0       |
| Event Listener Risks    | 3     | 0    | 0           | 0       |
| LSP Request Management  | 3     | 0    | 0           | 0       |
| Webview Memory          | 3     | 0    | 0           | 0       |
| ServiceContainer        | 2     | 0    | 0           | 0       |
| Folder Structure        | 2     | 0    | 0           | 0       |

## Issues

### ACTIVATION PERFORMANCE

#### ISSUE-001 · Synchronous Service Initialization · 🔴 Critical
- **Service**: ServiceContainer.ts
- **Problem**: All 12 services initialized synchronously in constructor (line 23). Blocks extension activation. Webview services (Dashboard, DependencyGraph, GormSqlPreview) created immediately but only used on-demand.
- **Fix**: Implement lazy initialization pattern. Core services (ErrorService, StatusBarService, ConfigurationService) initialize immediately. Other services initialize on first access via getter methods.
- **Tradeoff**: Slightly more complex getter logic. First access to lazy services has small overhead.
- **Status**: `[ ] TODO`
- **Subtasks**:
  - [ ] Add lazy initialization flag to ServiceContainer
  - [ ] Convert non-core services to lazy getters
  - [ ] Add initialization tracking for debugging
  - [ ] Test activation time improvement

#### ISSUE-002 · Webview Services Created Early · 🟠 High
- **Service**: DashboardService.ts, DependencyGraphService.ts, GormSqlPreviewService.ts
- **Problem**: All three webview services instantiated in ServiceContainer.initializeServices() (lines 153-172) but only used when commands are invoked. Wastes memory and startup time.
- **Fix**: Remove from ServiceContainer initialization. Create instances on-demand in Commands when commands are invoked.
- **Tradeoff**: Slightly more code in Commands. First command invocation has small overhead.
- **Status**: `[ ] TODO`
- **Subtasks**:
  - [ ] Remove webview services from ServiceContainer
  - [ ] Create instances in Commands.registerDashboardCommands()
  - [ ] Create instances in Commands.registerGrailsTaskCommands()
  - [ ] Update dispose logic

#### ISSUE-003 · ProjectService Heavy Synchronous Scanning · 🟠 High
- **Service**: ProjectService.ts
- **Problem**: countArtifacts() (lines 463-576) does 20+ synchronous fs.readdirSync() calls. Blocks extension host on large projects. Called during discoverProjects().
- **Fix**: Use fs.promises.readdir() for async scanning. Cache artifact counts. Only recount when build.gradle changes.
- **Tradeoff**: More complex async flow. Need to handle concurrent scans.
- **Status**: `[ ] TODO`
- **Subtasks**:
  - [ ] Convert countArtifacts() to async
  - [ ] Add artifact count cache
  - [ ] Update discoverProjects() to await async counting
  - [ ] Invalidate cache on build.gradle change

#### ISSUE-004 · No Activation Timeouts · 🟡 Medium
- **Service**: ActivationManager.ts
- **Problem**: Background tasks have timeouts (lines 123-126) but no overall activation timeout. Extension can hang indefinitely if one service never initializes.
- **Fix**: Add overall activation timeout wrapper. Fail gracefully if timeout exceeded.
- **Tradeoff**: Extension may start with degraded features if timeout hit.
- **Status**: `[ ] TODO`
- **Subtasks**:
  - [ ] Add overall activation timeout (30s)
  - [ ] Implement timeout handler
  - [ ] Log timeout events
  - [ ] Show user notification on timeout

### EXTENSION HOST OVERHEAD

#### ISSUE-005 · Synchronous File Reading in ProjectService · 🔴 Critical
- **Service**: ProjectService.ts
- **Problem**: scanFolder() (line 291) uses fs.readFileSync() for build.gradle parsing (line 352). fileContains() (line 407) uses fs.readFileSync(). Blocks extension host.
- **Fix**: Use fs.promises.readFile() for async file reading. Update all file operations to async.
- **Tradeoff**: More async/await complexity. Need to handle concurrent file reads.
- **Status**: `[ ] TODO`
- **Subtasks**:
  - [ ] Convert parseDependencies() to async
  - [ ] Convert fileContains() to async
  - [ ] Convert extractVersion() to async
  - [ ] Update scanFolder() to await async operations

#### ISSUE-006 · EventBus Synchronous Handler Execution · 🟠 High
- **Service**: EventBus.ts
- **Problem**: publish() (line 52) iterates through all handlers synchronously (line 57). Long-running handlers block event loop. No error isolation between handlers.
- **Fix**: Execute handlers in next tick via setImmediate() or queueMicrotask(). Add error isolation per handler.
- **Tradeoff**: Events no longer processed in order. Slightly delayed handler execution.
- **Status**: `[ ] TODO`
- **Subtasks**:
  - [ ] Wrap handler execution in setImmediate()
  - [ ] Add per-handler error isolation
  - [ ] Add handler execution timeout
  - [ ] Log slow handlers

#### ISSUE-007 · GradleService Blocking Sync · 🟡 Medium
- **Service**: GradleService.ts
- **Problem**: sync() (line 105) blocks waiting for vscode-gradle extension activation and task provider loading (up to 30s timeout). Blocks extension host.
- **Fix**: Make sync() non-blocking. Return promise immediately, complete in background. Add isReady flag for status checks.
- **Tradeoff**: Features dependent on Gradle may not work immediately after activation.
- **Status**: `[ ] TODO`
- **Subtasks**:
  - [ ] Make sync() return immediately with promise
  - [ ] Add isReady flag
  - [ ] Update callers to handle async readiness
  - [ ] Add status bar indicator for Gradle sync

### EVENT LISTENER & FILE WATCHER RISKS

#### ISSUE-008 · File Watcher Memory Leak · 🔴 Critical
- **Service**: ProjectService.ts
- **Problem**: initWatchers() (line 418) creates watchers but doesn't track them properly. dispose() clears watchers array (line 225) but watchers may still be active. No cleanup on workspace folder changes.
- **Fix**: Track all watchers in WeakMap or Set. Dispose all watchers explicitly. Re-create watchers on workspace folder changes.
- **Tradeoff**: Slightly more complex watcher management.
- **Status**: `[ ] TODO`
- **Subtasks**:
  - [ ] Track watchers in Set
  - [ ] Dispose all watchers explicitly
  - [ ] Add watcher cleanup on workspace folder change
  - [ ] Test watcher lifecycle

#### ISSUE-009 · EventBus Listener Accumulation · 🟠 High
- **Service**: EventBus.ts
- **Problem**: subscribe() (line 30) adds listeners but no automatic cleanup. Listeners accumulate across extension restarts if not unsubscribed. No listener count limits.
- **Fix**: Add listener count limits per event type. Auto-unsubscribe after N events or timeout. Add listener leak detection.
- **Tradeoff**: Listeners may be auto-unsubscribed unexpectedly. More complex lifecycle.
- **Status**: `[ ] TODO`
- **Subtasks**:
  - [ ] Add listener count limit (100 per event type)
  - [ ] Add auto-unsubscribe after N events
  - [ ] Add listener leak detection
  - [ ] Log listener accumulation warnings

#### ISSUE-010 · ActivationManager Listener Cleanup · 🟡 Medium
- **Service**: ActivationManager.ts
- **Problem**: setupEventListeners() (line 245) registers many listeners but doesn't track them individually. dispose() (line 531) clears disposables array but may miss some listeners.
- **Fix**: Track each listener separately. Add explicit cleanup for each listener type. Verify all listeners disposed.
- **Tradeoff**: More code to track listeners.
- **Status**: `[ ] TODO`
- **Subtasks**:
  - [ ] Track each listener separately
  - [ ] Add explicit cleanup for each listener type
  - [ ] Verify all listeners disposed
  - [ ] Add listener cleanup tests

### LSP REQUEST MANAGEMENT

#### ISSUE-011 · No LSP Request Debouncing · 🔴 Critical
- **Service**: DependencyGraphService.ts, GormSqlPreviewService.ts, GrailsTestService.ts
- **Problem**: refreshGraph() (line 53), refreshPreview() (line 45), discoverAllTests() (line 28) send LSP requests immediately. No debouncing on rapid calls. Can flood server with requests.
- **Fix**: Add debounce wrapper (300ms) to LSP request methods. Cancel pending requests on new calls.
- **Tradeoff**: Slight delay in response. More complex request management.
- **Status**: `[ ] TODO`
- **Subtasks**:
  - [ ] Create debounce utility
  - [ ] Add debounce to refreshGraph()
  - [ ] Add debounce to refreshPreview()
  - [ ] Add debounce to discoverAllTests()

#### ISSUE-012 · No Request Cancellation · 🟠 High
- **Service**: LanguageServerManager.ts
- **Problem**: No cancellation token support for LSP requests. Requests continue even if user cancels or navigates away. Wastes server resources.
- **Fix**: Add CancellationToken support to all LSP requests. Cancel pending requests on dispose or restart.
- **Tradeoff**: More complex request handling. Need to manage cancellation tokens.
- **Status**: `[ ] TODO`
- **Subtasks**:
  - [ ] Add CancellationToken to LanguageServerManager
  - [ ] Pass tokens to all LSP requests
  - [ ] Cancel pending requests on dispose
  - [ ] Cancel pending requests on restart

#### ISSUE-013 · No Request Batching · 🟡 Medium
- **Service**: GrailsTestService.ts
- **Problem**: discoverAllTests() (line 28) sends separate request for each project. No batching. Inefficient for multi-project workspaces.
- **Fix**: Batch test discovery requests. Send single request with all project URIs. Server handles batching.
- **Tradeoff**: Requires server-side batching support.
- **Status**: `[ ] TODO`
- **Subtasks**:
  - [ ] Add batch discovery request to server protocol
  - [ ] Update discoverAllTests() to use batch request
  - [ ] Handle batch response
  - [ ] Fallback to individual requests if batching not supported

### WEBVIEW MEMORY RISKS

#### ISSUE-014 · retainContextWhenHidden Memory Leak · 🔴 Critical
- **Service**: DashboardService.ts, DependencyGraphService.ts, GormSqlPreviewService.ts
- **Problem**: All webview panels use retainContextWhenHidden: true (lines 30, 34, 28). Keeps webview state in memory even when closed. Accumulates on repeated open/close.
- **Fix**: Remove retainContextWhenHidden. Save/restore state via webview state API. Clear state on dispose.
- **Tradeoff**: Webview loses state when hidden. Need to implement state persistence.
- **Status**: `[ ] TODO`
- **Subtasks**:
  - [ ] Remove retainContextWhenHidden from all webviews
  - [ ] Implement state persistence via webview API
  - [ ] Clear state on dispose
  - [ ] Test state save/restore

#### ISSUE-015 · D3.js CDN Dependency · 🟠 High
- **Service**: DependencyGraphService.ts
- **Problem**: D3.js loaded from CDN (line 92). Blocked by corporate firewalls. Slow on poor connections. No fallback.
- **Fix**: Bundle D3.js locally in extension resources. Add fallback to CDN if local load fails.
- **Tradeoff**: Increases extension bundle size (~200KB).
- **Status**: `[ ] TODO`
- **Subtasks**:
  - [ ] Download D3.js v7 to resources/
  - [ ] Update webview HTML to use local D3.js
  - [ ] Add CDN fallback
  - [ ] Test offline functionality

#### ISSUE-016 · No Webview Cleanup on Dispose · 🟡 Medium
- **Service**: DashboardService.ts, DependencyGraphService.ts, GormSqlPreviewService.ts
- **Problem**: onDidDispose only sets currentPanel to null (lines 38-40, 41-47, 34-40). No cleanup of webview state, event listeners, or timers.
- **Fix**: Add comprehensive cleanup in onDidDispose. Clear state, remove listeners, cancel timers.
- **Tradeoff**: More cleanup code.
- **Status**: `[ ] TODO`
- **Subtasks**:
  - [ ] Add state cleanup to onDidDispose
  - [ ] Remove event listeners
  - [ ] Cancel timers/intervals
  - [ ] Test cleanup on dispose

### SERVICECONTAINER

#### ISSUE-017 · Singleton Initialization Race · 🔴 Critical
- **Service**: ServiceContainer.ts
- **Problem**: getInstance() (line 33) throws if not initialized (line 35). No initialization promise. Race condition if code calls getInstance() before initialize() completes.
- **Fix**: Add initialization promise. getInstance() waits for initialization. Add isReady flag.
- **Tradeoff**: Slightly more complex singleton pattern.
- **Status**: `[ ] TODO`
- **Subtasks**:
  - [ ] Add initialization promise to ServiceContainer
  - [ ] Make getInstance() async or wait for init
  - [ ] Add isReady flag
  - [ ] Test initialization race conditions

#### ISSUE-018 · No Circular Dependency Detection · 🟡 Medium
- **Service**: ServiceContainer.ts
- **Problem**: Services created in specific order (lines 122-172) but no circular dependency detection. Silent failures if circular dependency introduced.
- **Fix**: Add dependency graph tracking. Detect cycles during initialization. Throw descriptive error on cycle.
- **Tradeoff**: More complex initialization. Slight overhead.
- **Status**: `[ ] TODO`
- **Subtasks**:
  - [ ] Add dependency graph tracking
  - [ ] Implement cycle detection
  - [ ] Throw descriptive error on cycle
  - [ ] Test circular dependency detection

### FOLDER STRUCTURE

#### ISSUE-019 · Commands.ts Monolithic · 🟠 High
- **Service**: Commands.ts
- **Problem**: Commands.ts is 660 lines with all command registrations. Hard to navigate. Violates single responsibility.
- **Fix**: Split Commands.ts into focused files: UICommands.ts, ProjectCommands.ts, GrailsTaskCommands.ts, ArtifactCommands.ts, NavigationCommands.ts.
- **Tradeoff**: More files to manage. Need to import from multiple files.
- **Status**: `[ ] TODO`
- **Subtasks**:
  - [ ] Create UICommands.ts
  - [ ] Create ProjectCommands.ts
  - [ ] Create GrailsTaskCommands.ts
  - [ ] Create ArtifactCommands.ts
  - [ ] Create NavigationCommands.ts
  - [ ] Update Commands.ts to import and register

#### ISSUE-020 · No LSP Handlers Folder · 🟡 Medium
- **Service**: N/A
- **Problem**: LSP feature handlers (GspCompletionProvider, GrailsCodeLensProvider, GrailsCodeActionProvider) scattered in features/ folder. No clear organization.
- **Fix**: Create services/lsp/handlers/ folder. Move all LSP feature handlers there. Add handler registry.
- **Tradeoff**: More folder structure. Need to update imports.
- **Status**: `[ ] TODO`
- **Subtasks**:
  - [ ] Create services/lsp/handlers/ folder
  - [ ] Move GspCompletionProvider to handlers/
  - [ ] Move GrailsCodeLensProvider to handlers/
  - [ ] Move GrailsCodeActionProvider to handlers/
  - [ ] Add handler registry
  - [ ] Update imports

---

## Quick Wins (do these first)
1. ISSUE-002 — Remove webview services from ServiceContainer initialization. Immediate memory savings at activation.
2. ISSUE-008 — Fix file watcher memory leak. Prevents gradual memory degradation.
3. ISSUE-011 — Add LSP request debouncing. Prevents server flooding on rapid calls.

## Architectural Improvements (do these after)
1. ISSUE-001 — Implement lazy service initialization. Reduces activation time significantly.
2. ISSUE-005 — Convert ProjectService to async file operations. Removes extension host blocking.
3. ISSUE-019 — Split Commands.ts into focused files. Improves maintainability and navigation.

## What This Architecture Does Right
ServiceContainer composition root with centralized error handling via ErrorService. All services implement Disposable and are registered to context.subscriptions. Clean separation between client (UI) and server (language intelligence).