# CLIENT ARCHITECTURE RULES

> TypeScript · VS Code Extension API · vscode-languageclient
> Any AI agent working on client code MUST read this first.

---

## 0. GOLDEN RULE

> **Client = VS Code UI + orchestration + lifecycle only.**
> Client does NOT handle AST, compilation, or Grails domain logic.
> That belongs in the server. If you are writing Grails logic in the client — STOP.

---

## 1. ARCHITECTURE OVERVIEW

```text
extension.ts
   ↓
Activation / Lifecycle Layer
   ↓
ServiceContainer (composition root)
   ↓
Application Layer (UseCases)
   ↓
Domain Services
   ↓
Infrastructure (VS Code API, LSP, Gradle)
```

---

## 2. SERVICECONTAINER — COMPOSITION ROOT

`ServiceContainer` is the single wiring point for all client services.

```
ServiceContainer
  ├── errorService           ErrorService
  ├── configurationService   ConfigurationService
  ├── projectService         ProjectService
  ├── statusBarService       StatusBarService
  ├── gradleService          GradleService
  ├── logStreamingService    LogStreamingService
  ├── languageServerManager  LanguageServerManager
  ├── lspHandlerRegistry     LspHandlerRegistry        ← added ISSUE-020
  ├── grailsTestService      GrailsTestService
  ├── debugService           DebugService
  ├── artifactService        ArtifactService
  ├── dashboardService       DashboardService
  ├── dependencyGraphService DependencyGraphService
  └── gormSqlPreviewService  GormSqlPreviewService
```

### Rules

* Singleton per workspace (multi-root aware)
* Services initialized ONCE during activation
* All services MUST implement `Disposable`
* Register all services in `context.subscriptions`
* NEVER construct services outside ServiceContainer
* NEVER pass ServiceContainer into services
* NEVER call `ServiceContainer.getInstance()` inside service constructors
* ServiceContainer is ONLY used in:

  * activation layer
  * command layer
  * UseCase creation

---

## 3. APPLICATION LAYER — USECASES (MANDATORY)

### Definition

A **UseCase = one user action / one workflow**. It orchestrates services to complete that action.

Examples: `RunGrailsAppUseCase`, `RunTestsUseCase`, `RestartServerUseCase`, `CreateArtifactUseCase`

### Rules

- ALL multi-service workflows MUST be UseCases
- Commands MUST delegate to UseCases — NEVER inline workflow logic in a command handler
- UseCases orchestrate services; services MUST NOT orchestrate other services
- Single public method: `execute()` with explicit return type
- No shared state, no subscriptions, no event listeners
- All errors caught and routed through `errorService.handleError()` — never re-thrown raw

### Structure

```ts
class RunGrailsAppUseCase {
    constructor(
        private readonly gradleService: GradleService,
        private readonly statusBarService: StatusBarService,
        private readonly errorService: ErrorService
    ) {}

    async execute(): Promise<void> {
        try {
            await this.gradleService.run('bootRun');
            this.statusBarService.setRunning();
        } catch (error) {
            this.errorService.handleError(
                'Failed to start Grails app', error, ErrorSource.GradleService
            );
        }
    }
}
```

### UseCase Location

UseCases live **alongside the primary service** they orchestrate, or in `ui/commands/` if they are command-scoped. They are instantiated inline at the call site — there is no UseCase registry.

```ts
// In a command handler (thin delegation only):
vscode.commands.registerCommand('grails.runApp', () => {
    new RunGrailsAppUseCase(container.gradleService, container.statusBarService, container.errorService)
        .execute();
});
```

---

## 4. DOMAIN SERVICES — CLIENT SIDE

Each service owns exactly ONE concern:

| Service                  | Owns                                             |
| ------------------------ | ------------------------------------------------ |
| `ErrorService`           | Error logging, notifications, status bar updates |
| `ConfigurationService`   | Reading `grails.*` settings                      |
| `ProjectService`         | Workspace detection, Grails validation           |
| `StatusBarService`       | Status bar lifecycle                             |
| `GradleService`          | Gradle task execution                            |
| `LogStreamingService`    | Output streaming                                 |
| `LanguageServerManager`  | LSP lifecycle                                    |
| `GrailsTestService`      | Test integration                                 |
| `DebugService`           | Debug lifecycle                                  |
| `ArtifactService`        | Artifact commands                                |
| `DashboardService`       | Dashboard webview                                |
| `DependencyGraphService` | Dependency graph webview                         |
| `GormSqlPreviewService`  | SQL preview webview                              |

---

### Service Rules

* MUST NOT orchestrate other services
* MUST NOT contain workflow logic
* MUST be deterministic and reusable
* MAY call:

  * ErrorService
  * ConfigurationService

---

## 5. MULTI-ROOT WORKSPACE SUPPORT

Use container-per-workspace:

```ts
Map<WorkspaceFolder, ServiceContainer>
```

### Rules

* No global mutable state for project-specific data
* Services must be workspace-aware where required
* UseCases execute in correct workspace context

---

## 6. ERROR HANDLING — ERRORSERVICE

All errors MUST go through ErrorService:

```ts
errorService.handleError(message, error, source, severity?)
```

### ErrorSource

* Extension
* LanguageServer
* GradleService
* ProjectService
* Configuration

### ErrorSeverity

* Info
* Warning
* Error
* Critical

---

## 7. COMMAND REGISTRATION

Commands are the thinnest possible layer — they exist only to bind a VS Code command ID to a UseCase.

```ts
// ✅ CORRECT — delegate immediately, no logic
vscode.commands.registerCommand('grails.openDashboard', () => {
    new OpenDashboardUseCase(container.dashboardService, container.errorService).execute();
});

// ✅ CORRECT — error handling belongs in the UseCase, not the command
vscode.commands.registerCommand('grails.runApp', () => {
    new RunGrailsAppUseCase(container.gradleService, container.statusBarService, container.errorService)
        .execute();
});

// ❌ WRONG — business logic in command handler
vscode.commands.registerCommand('grails.runApp', async () => {
    if (!workspace.workspaceFolders) { return; }         // ← logic
    await gradleService.run('bootRun');                   // ← orchestration
    statusBarService.setRunning();                        // ← orchestration
});
```

If a command body is more than 1-2 lines → it needs a UseCase.

---

## 8. DISPOSABLE PATTERN — MANDATORY

Every service MUST implement `Disposable` and guard against use-after-dispose.

```ts
export class MyService implements Disposable {
    private _disposed = false;
    private _subscription: Disposable | undefined;

    // Guard ALL public methods
    doWork(): void {
        if (this._disposed) { return; }   // ← guard every public method
        ...
    }

    // Dispose in order: stop work, clear subscriptions, null refs
    dispose(): void {
        if (this._disposed) { return; }   // ← idempotent
        this._disposed = true;
        this._subscription?.dispose();
        this._subscription = undefined;
    }
}
```

**Rules:**
- `dispose()` MUST be idempotent — calling it twice must not throw
- Guard every public method with `if (this._disposed) return`
- Register in `context.subscriptions` so VS Code disposes on deactivation
- `dispose()` must NOT throw — wrap in try/catch if needed

---

## 9. TYPESCRIPT RULES — QUICK REFERENCE

> Full TypeScript style guide: **`client/RULES.md §25`** — read it before writing any TypeScript.
> This section is a quick-reference summary only.

- `strict: true` in tsconfig — no exceptions
- No `any` — use `unknown` and narrow with `typeof` / `instanceof`
- Explicit return types on all public methods
- `interface` for object contracts; `type` for unions / aliases
- `const` object + union type instead of `enum` for string-valued variants
- `import type` for type-only imports
- Private fields: underscore prefix (`_disposed`, `_client`)
- No `!` non-null assertion — narrow the type instead

---

## 10. ASYNC / PROMISE HANDLING

* Always use `async/await`
* Always handle errors with try/catch

```ts
try {
  await task()
} catch (error) {
  errorService.handleError(...)
}
```

* No fire-and-forget unless explicit:

```ts
void asyncCall().catch(...)
```

---

## 11. STATE MANAGEMENT

Use explicit state objects, not boolean flags:

```ts
// ✅ — const object + union type (serializable, debuggable)
const ServerStatus = {
    Starting: 'starting',
    Running:  'running',
    Stopped:  'stopped',
    Error:    'error',
} as const;
type ServerStatus = typeof ServerStatus[keyof typeof ServerStatus];

// ✅ — interface for compound state
interface RunState {
    status: ServerStatus;
    pid:    number | undefined;
    error:  Error | undefined;
}

// ❌ — boolean flags compound into combinatorial explosion
let isRunning   = false;
let isStopping  = false;
let hasError    = false;

// ❌ — TypeScript enum for string values (not serializable, bad DX)
enum State { Idle, Running, Error }
```

---

## 12. LSP COMMUNICATION

**Only use `LanguageClient` APIs. Never raw JSON-RPC.**

```ts
// ✅ — sending a request (expects a response)
const result = await client.sendRequest('grails/symbolInfo', params);

// ✅ — sending a notification (fire and forget, no response)
client.sendNotification('grails/fileChanged', { uri });

// ✅ — handling a server-initiated notification
client.onNotification('grails/projectUpdated', (dto: ProjectDTO) => { ... });
```

**Rules:**
- Custom methods MUST use `grails/` namespace
- `sendRequest` when you need a response; `sendNotification` when you don't
- All custom notification/request types defined in `shared/protocol/`
- LSP communication only inside `LanguageServerManager` or LSP handler services — never in UseCases or domain services directly

---

## 13. WEBVIEW SERVICES

* Lazy initialization
* Reuse existing panel
* Dispose properly
* No unsafe HTML injection
* Use CSP and nonce
* Use `asWebviewUri`

---

## 14. OUTPUT CHANNELS

* One shared output channel
* No per-service channels

---

## 15. CONFIGURATION ACCESS

* ONLY via ConfigurationService
* NEVER use `workspace.getConfiguration()` directly

---

## 16. SERVICE DESIGN RULES

* Constructor injection only
* Max 5 dependencies
* No ServiceContainer usage inside services
* No constructor side-effects

---

## 17. USECASE DESIGN RULES

* Single method: `execute()`
* No shared state
* No subscriptions
* Orchestration only

---

## 18. EVENTING

* Use EventEmitter for UI/state updates
* Avoid overuse

---

## 19. LOGGING

- Levels: `DEBUG`, `INFO`, `WARN`, `ERROR`
- Errors → always through `errorService.handleError()` — never `console.error`
- Info/debug → OutputChannel only — never `console.log`
- Every log line MUST carry a bracketed service prefix:

```ts
// ✅
this.logger.info('[GRADLE] Task started: bootRun');
this.logger.warn('[LSP] Reconnecting after unexpected disconnect');
this.logger.debug('[DASHBOARD] Webview panel created');

// ❌ — no prefix, ungreppable
this.logger.info('Task started');
console.log('done');
```

---

## 20. FOLDER STRUCTURE

```
client/src/
  core/
    container/                      ← ServiceContainer, activation
  services/
    artifacts/
    debugging/
    errors/
    gradle/
    languageServer/
    lsp/
      handlers/                     ← LspHandlerRegistry + per-feature handlers (ISSUE-020)
    testing/
    webview/                        ← webview service infrastructure
    workspace/
  features/                         ← self-contained feature modules
    dashboard/
    dependency-graph/
    gorm-sql-preview/
    models/
  shared/
    protocol/                       ← shared types between client and server (ISSUE-021)
  store/                            ← state store
  ui/
    commands/                       ← UICommands, ProjectCommands, GrailsTaskCommands (ISSUE-019)
  utils/
  test/
  extension.ts
```

> **Note:** `services/useCases/` was removed. UseCases live **alongside their primary service**
> (e.g., `RunGrailsAppUseCase` next to `GradleService`) or in `ui/commands/` for
> command-scoped simple workflows. In all cases UseCases are instantiated inline at the
> command call site — see §3 for the exact pattern.
> **Never inline workflow logic directly in a command handler.** If it touches more than one
> service, it is a UseCase.

---

## 21. ANTI-PATTERNS (FORBIDDEN)

| Anti-pattern | Why forbidden | Correct alternative |
|-------------|--------------|--------------------|
| God services | Violates single responsibility; impossible to test | Split into focused services, each owning one concern |
| Command logic bloat | Commands must be thin delegation only | Extract to a UseCase |
| Service chaining | Services must not orchestrate each other | Use a UseCase to coordinate |
| Hidden globals | Breaks multi-root; impossible to dispose | Use `ServiceContainer` per workspace |
| VS Code API in UseCases | UseCases must be testable without VS Code; API belongs in services | Move VS Code API calls into the relevant domain service |
| `workspace.getConfiguration()` direct | Bypasses caching and change tracking | Use `ConfigurationService` only |
| `console.log` / `console.error` | Bypasses output channel and ErrorService | Use logger + `errorService.handleError()` |
| `ServiceContainer` passed into a service | Creates circular dependency, hides real deps | Constructor-inject only what is needed |

---

## 22. WHAT BELONGS WHERE

| Concern | Client | Server | Notes |
|---------|--------|--------|-------|
| VS Code UI (status bar, notifications) | ✅ | ❌ | Services only |
| Command registration | ✅ | ❌ | Thin delegates to UseCases |
| Webviews | ✅ | ❌ | Lazy init, reuse panel, dispose properly |
| LSP lifecycle | ✅ | ❌ | `LanguageServerManager` only |
| LSP requests / notifications (send) | ✅ | ❌ | Via `LanguageServerManager` / LSP handlers |
| LSP requests / notifications (handle) | ❌ | ✅ | Server providers respond |
| Configuration access | ✅ | ✅ | Client: `ConfigurationService`. Server: `config` getter from `BaseProvider` |
| AST / Compilation | ❌ | ✅ | Never in client |
| Grails domain logic | ❌ | ✅ | Never in client |
| Error routing | ✅ `ErrorService` | ✅ `errorService` in `GrailsService` | Both sides have their own |
| Logging to output | ✅ OutputChannel | ✅ Slf4j / structured prefix | Never `console.log` or bare `log.debug` |

---

## 23. WHAT NOT TO CHANGE

* ServiceContainer pattern
* ErrorService contract
* Disposable pattern
* Service separation
* UseCase orchestration model

---

## 24. CORE PRINCIPLE

> **Services do things.
> UseCases decide what to do.**

---

## 25. TYPESCRIPT STYLE GUIDE — STRICT ENFORCEMENT

> **Every line of TypeScript you write is checked against this section.**
> Violations = blocking review comment. No exceptions.

---

### 25.1 IMPORTS

#### Rule: Named imports only. No `import *`. No default imports unless library forces it.

```ts
// ✅ — named imports, grouped
import * as vscode from 'vscode';              // exception: VS Code API always uses namespace import
import { LanguageClient } from 'vscode-languageclient/node';

import { ErrorService } from '../services/errors/ErrorService';
import { ConfigurationService } from '../services/workspace/ConfigurationService';
import type { Disposable } from '../core/types';

// ❌ — barrel imports that pull in everything
import * as services from '../services';

// ❌ — default import when named exists
import ErrorService from '../services/errors/ErrorService';
```

#### Import group order (blank line between each group)

```ts
// 1. Node built-ins
import * as path from 'path';
import * as fs   from 'fs';

// 2. VS Code API
import * as vscode from 'vscode';

// 3. Third-party packages
import { LanguageClient, LanguageClientOptions } from 'vscode-languageclient/node';

// 4. Project-local (absolute-style, deepest last)
import { ServiceContainer }       from '../core/container/ServiceContainer';
import { ErrorService }           from '../services/errors/ErrorService';
import type { GrailsLspConfig }  from '../shared/protocol/types';
```

#### When to use `import type`

```ts
// ✅ — use import type for anything that is ONLY used as a type annotation
//       It is erased at compile time and avoids circular dep issues
import type { CompletionItem } from 'vscode-languageclient/node';
import type { ServiceContainer } from '../core/container/ServiceContainer';

// ❌ — importing a class as a value when you only use it as a type
import { CompletionItem } from 'vscode-languageclient/node'; // if only used in : CompletionItem
```

#### When to collapse imports

```ts
// ✅ — multiple named exports from same module — single import statement
import { Position, Range, Location, Hover } from 'vscode-languageclient/node';

// ❌ — one import per named export from the same module
import { Position } from 'vscode-languageclient/node';
import { Range }    from 'vscode-languageclient/node';
```

---

### 25.2 TYPE ANNOTATIONS

```ts
// ✅ — explicit return type on all public methods
async execute(): Promise<void> { ... }
getStatus(): ServerStatus { ... }
buildParams(uri: string): CompletionParams { ... }

// ✅ — inferred types fine for obvious locals
const uri = document.uri.toString();
const items = await client.sendRequest(CompletionRequest.type, params);

// ❌ — any is never acceptable
function handle(data: any) { ... }          // use unknown
const result: any = await fetch(...);       // use the actual response type

// ✅ — use unknown, then narrow
async function handle(data: unknown): Promise<void> {
    if (typeof data === 'string') { ... }
}

// ❌ — redundant type annotation where inference is obvious
const name: string = 'GrailsHover';
const count: number = 0;
```

---

### 25.3 INTERFACES AND TYPES

```ts
// ✅ — interface for object contracts (extendable, readable)
interface GrailsLspConfig {
    codeLensMode: boolean;
    diagnosticsEnabled: boolean;
}

// ✅ — type alias for unions, intersections, primitives
type ServerStatus = 'starting' | 'running' | 'stopped' | 'error';
type Nullable<T> = T | null;

// ❌ — class for pure data structures (use interface)
class GrailsLspConfig {
    codeLensMode: boolean = false;
}

// ❌ — enum for string literals (use const object or union type for serializability)
enum Status { Running, Stopped }            // ❌
const Status = { Running: 'running', Stopped: 'stopped' } as const;   // ✅
type Status = typeof Status[keyof typeof Status];                       // ✅
```

---

### 25.4 ASYNC / PROMISE

```ts
// ✅ — always async/await, never raw .then() chains
async execute(): Promise<void> {
    try {
        const result = await this.gradleService.run(task);
        this.statusBar.update(result);
    } catch (error) {
        this.errorService.handleError('Task failed', error, ErrorSource.GradleService);
    }
}

// ❌ — .then()/.catch() chains
this.gradleService.run(task)
    .then(result => this.statusBar.update(result))
    .catch(error => console.error(error));         // ← swallowed, never routed to ErrorService

// ✅ — explicit void for intentional fire-and-forget
void this.refresh().catch(e => this.errorService.handleError('Refresh failed', e, ...));

// ❌ — implicit fire-and-forget (unhandled rejection)
this.refresh();
```

---

### 25.5 NAMING CONVENTIONS

```ts
// Classes — PascalCase
class GrailsHoverProvider { }
class ErrorService { }

// Interfaces — PascalCase, no I-prefix
interface Disposable { dispose(): void }     // ✅
interface IDisposable { dispose(): void }    // ❌ — I-prefix is Java convention

// Enums / const objects — PascalCase keys
const ErrorSource = { Extension: 'extension', GradleService: 'gradleService' } as const;

// Functions and methods — camelCase, verb-first
async execute(): Promise<void>
buildParams(uri: string): CompletionParams
handleDidChange(event: TextDocumentChangeEvent): void

// Private fields — underscore prefix
private _disposed = false;
private _client: LanguageClient | undefined;

// Constants — SCREAMING_SNAKE only for module-level true constants
const MAX_RETRY_COUNT = 3;

// Boolean names — is/has/can prefix
isRunning(): boolean
hasGrailsProject(): boolean
canRestart(): boolean
```

---

### 25.6 NULL AND UNDEFINED HANDLING

```ts
// ✅ — use optional chaining
const name = document?.fileName ?? 'unknown';
const range = position?.range?.start;

// ✅ — nullish coalescing for defaults (not || which is falsy-based)
const timeout = config.timeout ?? 5000;
const label = item.label ?? '';

// ❌ — || for null-default (treats 0 and '' as falsy)
const timeout = config.timeout || 5000;   // 0 is a valid timeout — this breaks it

// ✅ — narrow type before use, don't assert
if (this._client) {
    this._client.stop();
}

// ❌ — non-null assertion hides real bugs
this._client!.stop();
```

---

### 25.7 COLLECTIONS AND ITERATION

```ts
// ✅ — functional style for transforms
const labels = items.map(i => i.label);
const matches = items.filter(i => i.kind === vscode.CompletionItemKind.Method);
const found = items.find(i => i.label === target);
const grouped = items.reduce((acc, i) => ({ ...acc, [i.label]: i }), {});

// ✅ — for...of for side-effectful iteration
for (const item of items) {
    await process(item);
}

// ❌ — classic for loop unless index is genuinely needed
for (let i = 0; i < items.length; i++) { ... }

// ✅ — spread for immutable collection ops
const updated = [...existing, newItem];
const merged  = { ...defaults, ...overrides };
```

---

### 25.8 STRING HANDLING

```ts
// ✅ — template literals
const msg = `[HOVER] No node at ${uri}:${position.line}`;
const cmd = `grails.${featureName}.open`;

// ❌ — concatenation
const msg = '[HOVER] No node at ' + uri + ':' + position.line;

// ✅ — string comparison with ===, never ==
if (status === 'running') { ... }

// ✅ — startsWith/endsWith/includes over regex for simple cases
if (cmd.startsWith('grails.')) { ... }
if (uri.endsWith('.groovy')) { ... }
```

---

### 25.9 CLASSES — STRUCTURE AND AESTHETICS

```ts
// ✅ — canonical class layout
@injectable()
export class ErrorService implements Disposable {
    // 1. Private fields
    private _disposed = false;
    private readonly _outputChannel: vscode.OutputChannel;

    // 2. Constructor (dependency injection only, no side effects)
    constructor(private readonly configService: ConfigurationService) {
        this._outputChannel = vscode.window.createOutputChannel('Grails LSP');
    }

    // 3. Public API
    handleError(message: string, error: unknown, source: ErrorSource): void { ... }

    // 4. Disposable
    dispose(): void {
        if (this._disposed) { return; }
        this._disposed = true;
        this._outputChannel.dispose();
    }

    // 5. Private helpers (last)
    private formatMessage(message: string, source: ErrorSource): string { ... }
}

// ❌ — public fields (breaks encapsulation)
export class ErrorService {
    outputChannel: vscode.OutputChannel;    // ❌ — should be private
    disposed = false;                        // ❌ — no underscore, public
}
```

---

### 25.10 DISPOSABLE PATTERN

```ts
// ✅ — every service guards against use-after-dispose
dispose(): void {
    if (this._disposed) { return; }
    this._disposed = true;
    this._subscription?.dispose();
    this._panel?.dispose();
}

// ✅ — guard public methods
async execute(): Promise<void> {
    if (this._disposed) { return; }
    ...
}

// ❌ — no guard, use-after-dispose causes silent undefined behaviour
dispose(): void {
    this._disposed = true;
}
```

---

### 25.11 ERROR HANDLING

```ts
// ✅ — all errors routed through ErrorService
try {
    await this.client.start();
} catch (error) {
    this.errorService.handleError(
        'Language server failed to start',
        error,
        ErrorSource.LanguageServer,
        ErrorSeverity.Critical
    );
}

// ❌ — console.error, swallowing, or bare throw from a service
console.error('Server failed', error);           // ❌ — bypasses ErrorService
throw error;                                       // ❌ — raw throw from a service method
```

---

### 25.12 WEBVIEW SAFETY

```ts
// ✅ — always use nonce + CSP
const nonce = getNonce();
const csp = `default-src 'none'; script-src 'nonce-${nonce}';`;

// ✅ — always use asWebviewUri for local resources
const scriptUri = panel.webview.asWebviewUri(
    vscode.Uri.joinPath(this._extensionUri, 'out', 'webview.js')
);

// ❌ — inline script without nonce
<script>window.data = ${JSON.stringify(data)}</script>   // ❌ — XSS vector

// ❌ — raw HTML string injection
panel.webview.html = '<script>' + userContent + '</script>';  // ❌
```

---
