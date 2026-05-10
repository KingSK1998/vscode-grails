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

A **UseCase = one user action / workflow**

Examples:

* RunGrailsAppUseCase
* RunTestsUseCase
* RestartServerUseCase
* CreateArtifactUseCase

---

### Rules

* ALL multi-service workflows MUST be UseCases
* Commands MUST delegate to UseCases
* UseCases orchestrate services
* Services MUST NOT orchestrate other services

---

### Structure

```ts
class XUseCase {
  async execute(): Promise<void>
}
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

* Commands MUST be thin
* Commands MUST NOT contain business logic
* Commands MUST delegate to UseCases

```ts
vscode.commands.registerCommand('grails.openDashboard', () => {
  new OpenDashboardUseCase(...).execute()
})
```

---

## 8. DISPOSABLE PATTERN — MANDATORY

Every service MUST implement Disposable:

```ts
dispose(): void {
  this._disposed = true
}
```

Guard:

```ts
if (this._disposed) return
```

---

## 9. TYPESCRIPT RULES

* Strict mode enabled
* No `any` (use `unknown`)
* Explicit return types for public methods
* Prefer `interface` for contracts

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

Use explicit state models:

```ts
enum State {
  Idle,
  Running,
  Error
}
```

Avoid booleans like `isRunning`.

---

## 12. LSP COMMUNICATION

* Use LanguageClient APIs only
* Custom methods use `grails/` namespace

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

* Levels: INFO, DEBUG, WARN, ERROR
* Errors → ErrorService
* Logs → OutputChannel

---

## 20. FOLDER STRUCTURE

```
client/src/
  core/
    container/
  services/
    useCases/
    artifacts/
    debugging/
    errors/
    gradle/
    languageServer/
    testing/
    ui/
    workspace/
  ui/
    commands/
  utils/
  extension.ts
```

---

## 21. ANTI-PATTERNS (FORBIDDEN)

* God services
* Command logic bloat
* Service chaining
* Hidden globals
* Direct VS Code API usage inside UseCases

---

## 22. WHAT BELONGS WHERE

| Concern           | Client | Server |
| ----------------- | ------ | ------ |
| VS Code UI        | ✅      | ❌      |
| Commands          | ✅      | ❌      |
| Webviews          | ✅      | ❌      |
| LSP lifecycle     | ✅      | ❌      |
| AST / Compilation | ❌      | ✅      |
| Grails logic      | ❌      | ✅      |

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
