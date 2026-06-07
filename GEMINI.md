# Project Overview
**vscode-gng-support (Groovy & Grails Support for VS Code)**

This is a comprehensive VS Code extension that provides full-featured support for Grails (7+) and Groovy (4) development with intelligent Language Server Protocol (LSP) integration.

**[MANDATORY] Coding Standards:** 
See [CODING_STANDARDS.md](./CODING_STANDARDS.md) for project-wide rules that MUST be followed by all AI agents and contributors.

The extension uses a **client-server architecture**:
- **Client (TypeScript):** VS Code extension. Responsible for UI, commands, workflow orchestration (UseCases), and LSP client lifecycle.
- **Server (Groovy/Java):** Language Server Protocol (LSP) backend built with Gradle. Responsible for Groovy/Grails compilation, AST visiting, Gradle Tooling API integration, and symbol indexing.

---

# Architecture & Development Conventions

Detailed rules for each component are found in their respective directories. You **must** adhere to these architectural guidelines.

### Server (Language Server)
- **Reference:** `server/RULES.md`
- **Core Engine:** Written in Groovy 4.0.23, utilizing LSP4J and Gradle Tooling API. Requires Java 17+.
- **Strict Typing:** Apply `@CompileStatic` on ALL classes unless absolutely necessary.
- **Composition Root:** `GrailsService` is the single wiring point for shared mutable state (this is intentional, do not refactor).  
  **Key components added in recent work:** CancellationService (request cancellation),
  ProviderHealthService (latency/error metrics), ProviderRegistry (lazy provider init).
  All live in GrailsService. See `server/RULES.md` Rules 15–16 for usage patterns.
- **Read/Write Paths:** Only specific `GrailsService` methods can mutate state (e.g., `setupWorkspace`, `compileAndVisitAST`). Providers **NEVER** write to `visitor`, `compiler`, or `fileTracker`. They read freely via getters.
- **Provider Tiers:**
  - **TIER 1:** Extends `BaseProvider`, takes `GrailsService`. (e.g., Completion, Hover, Diagnostics)  
    TIER 1 providers must also call `createCancellationToken(uri)` at handler entry,
    `checkCancellation(token)` at yield points, and `recordHealth(latencyMs, success)` on exit.
  - **TIER 2:** Static utility, no dependencies. (e.g., `GrailsUtils`)
  - **TIER 3:** Plug-n-play module, injects only what it needs.
- **Style:** Use Groovy property getters (e.g., `visitor.getClassNodes` instead of `getVisitor().getClassNodes`), single-expression methods, safe navigation `?.`, and elvis operators `?:`.

### Client (VS Code Extension)
- **Reference:** `client/RULES.md`
- **Core Stack:** TypeScript, `vscode-languageclient`, VS Code Extension API.
- **Composition Root:** `ServiceContainer` is the single wiring point initialized once per workspace during activation.
- **Application Layer (UseCases):** ALL multi-service workflows and commands MUST delegate to a UseCase (e.g., `RunGrailsAppUseCase`). UseCases orchestrate services.
- **Domain Services:** Services own exactly ONE concern (e.g., `ErrorService`, `GradleService`) and MUST NOT orchestrate other services. 
- **Error Handling:** All errors MUST route through `ErrorService`.
- **Memory Management:** Every service MUST implement `Disposable` and properly release resources.
- **Style:** Strict mode TypeScript, avoid `any`, explicit return types for public methods, async/await with try/catch for everything.
- **Client structure:** `core/`, `services/` (including `lsp/handlers/` for LspHandlerRegistry),
  `features/` (dashboard, dependency-graph, gorm-sql-preview), `shared/protocol/`, `ui/commands/`.
  See `client/RULES.md` Rule 20 for full structure.

---

# Building and Running

**Prerequisites:** Node.js 20+, Java 17+, and Gradle 7.0+.

### Setup & Build
- `npm run setup` — One-time setup to install dependencies and build both components.
- `npm run build` — Builds both client and server.
- `npm run build:client` — Compiles (tsc) and bundles (esbuild) the VS Code extension.
- `npm run build:server` — Builds the Language Server JAR using Gradle.
- `npm run copy-server` — Builds the server JAR and copies it into `client/server/` for the extension to use.

### Development Workflow
1. **Client Watcher:** Run `npm run watch` to recompile the client extension on file changes.
2. **Server Updates:** When editing the server, run `npm run copy-server` to update the bundled JAR.
3. **Launch Server Debug:** Run `npm run dev:server` (or run debug config in IntelliJ).
4. **Launch Client Debug:** Press `F5` in VS Code to launch the Extension Development Host.

### Testing & Packaging
- `npm run test` — Runs extension tests.
- `npm run lint` / `npm run format` — Lints and formats client code.
- `npm run package` — Packages the extension into a `.vsix` file using `vsce`.

---

# Key Files and Directories
- `client/src/extension.ts` — VS Code extension entry point and activation layer.
- `server/src/main/groovy/kingsk/grails/lsp/GrailsLanguageServer.groovy` — LSP entry point.
- `docs/architecture.md` — High-level architecture and feature ownership details.
- `.vscode/launch.json` — VS Code debug configurations for client and server.