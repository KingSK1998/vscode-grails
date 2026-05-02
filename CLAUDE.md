# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a **VS Code extension for Grails Framework support** with a client-server architecture:

- **Client** (`client/`): TypeScript VS Code extension using `vscode-languageclient`
- **Server** (`server/`): Groovy Language Server using LSP4J and Gradle Tooling API

## Build Commands

### Full Build (Client + Server)

```bash
npm run build              # Build both client and server
npm run clean              # Clean build artifacts
npm run vscode:prepublish  # Clean + build (used for publishing)
```

### Client (TypeScript)

```bash
npm run compile            # TypeScript compilation only (outputs to client/out/)
npm run bundle             # Bundle with esbuild (production)
npm run watch              # Watch mode for development (compile + bundle)
npm run check-types        # Type-check without emitting
```

### Server (Groovy/Gradle)

```bash
npm run build:server       # Build server shadow JAR via Gradle
node scripts/run-gradlew.js shadowJar   # Direct Gradle build
```

Or from `server/` directory:

```bash
cd server
./gradlew shadowJar        # Build fat JAR
./gradlew test             # Run Spock tests
./gradlew run              # Run server standalone (for debugging)
```

### Lint & Format

```bash
npm run lint               # ESLint on client/src/**/*.ts
npm run lint:fix           # Auto-fix ESLint issues
npm run format             # Prettier formatting
```

### Tests

```bash
npm run test               # Run client tests (requires compilation first)
cd server && ./gradlew test  # Run server Spock tests
```

## Development Workflow

1. **Open the repo root** in VS Code (not just client/)
2. **Terminal 1**: `npm run watch` (keeps client compiled)
3. **F5** to launch Extension Development Host
4. To debug server: `cd server && ./gradlew run` or attach debugger to port 5005

After server code changes:

```bash
npm run build:server       # Rebuild shadow JAR
```

## Architecture

### Client Structure (`client/src/`)

```
core/          # Activation, lifecycle management, DI
services/      # LSP client, Gradle integration, workspace, config
features/      # LSP feature handlers
ui/            # Commands, tree views, views
utils/         # Helper utilities
extension.ts   # Entry point
```

Key services:

- `LanguageServerManager`: Starts/stops the Groovy LSP server
- `GradleService`: Gradle project integration
- `GrailsTreeDataProvider`: Project explorer tree view

### Server Structure (`server/src/main/groovy/kingsk/grails/lsp/`)

```
core/
  compiler/       # Groovy compilation (GrailsCompiler, CompilerOptions)
  gradle/         # Gradle integration (GrailsProjectBuilder, ProjectCache)
  visitor/        # AST analysis (GrailsASTVisitor)
services/         # LSP handlers (GrailsTextDocumentService, GrailsWorkspaceService)
model/            # Data models (GrailsProject, GrailsArtifactInfo, etc.)
GrailsLanguageServer.groovy    # LSP entry point
GrailsService.groovy           # Shared service container
```

### Key Technologies

- **Client**: TypeScript, `vscode-languageclient` ^9.0.1, esbuild bundling
- **Server**: Groovy 4.0.23, LSP4J 0.23.1, Gradle Tooling API 8.12
- **Build**: Gradle 8.x (shadow plugin for fat JAR), Node 20.x

## Server Capabilities

Enabled in `GrailsLanguageServer.initialize()`:

- Incremental text document sync
- Hover, completion (with resolve), signature help
- Definition, implementation, references
- Document symbols, workspace symbols
- Code lens (with resolve), inlay hints
- Rename (best-effort)

Disabled/stubbed:

- Document highlight, semantic tokens, formatting, folding
- Document links, execute command
- Code actions (returns empty)

## Important Implementation Notes

### Workspace Handling

- Only **first workspace folder** (`workspaceFolders[0]`) is indexed
- Multi-root workspaces are not fully supported yet

### Caching

- Cache stored in `.grails-lsp/` under project root
- AST and completion caches invalidate on `didChange`/`didClose`
- Configurable via `grails.cache.*` settings

### Compilation

- Server uses `GrailsCompiler` with custom `GrailsASTVisitor`
- Gradle project structure resolved via Tooling API
- Incremental compilation supported

## Environment Requirements

- **Node.js**: 20.x (CI uses 20.18.1)
- **JDK**: 17+ (for server)
- **VS Code**: ^1.103.0 (see `engines.vscode` in package.json)

## CI/CD

- Workflow: `.github/workflows/ci.yml`
- Server build: `./gradlew clean build -x test` (skips tests)
- Client: `npm ci`, `npm run compile`, `npm run bundle`
- Release build: `npm ci && npm run vscode:prepublish`

## Documentation

- `docs/user-guide.md` — Installation, settings, features, troubleshooting
- `docs/developer-guide.md` — Architecture, LSP reference, debug setup
- `server/README.md` — Deep server internals
- `server/API.md` — LSP handler documentation

## graphify

This project has a graphify knowledge graph at graphify-out/.

Rules:

- Before answering architecture or codebase questions, read graphify-out/GRAPH_REPORT.md for god nodes and community structure
- If graphify-out/wiki/index.md exists, navigate it instead of reading raw files
- After modifying code files in this session, run `graphify update .` to keep the graph current (AST-only, no API cost)
