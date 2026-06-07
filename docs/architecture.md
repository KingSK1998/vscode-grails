# Architecture

Client-server extension: TypeScript **client** (VS Code) ↔ Groovy **server** (LSP4J) over stdio JSON-RPC.

## Runtime Flow

```
VS Code activation
  → ServiceContainer.initialize()
    → LanguageServerManager.start() (spawns JVM)
      → GrailsLanguageServer.main()
        → Gradle Tooling API loads project
        → GrailsCompiler full compilation
        → GrailsASTVisitor builds symbol index
      ← initialize response (capabilities)
    ← LanguageClient ready
  ← extension active
```

Subsequent file changes flow through `didChange` → `GrailsCompiler.compileSourceFile()` → `GrailsASTVisitor` update → provider reads fresh AST.

## Communication

- **Protocol**: LSP over stdio (JSON-RPC)
- **Custom namespace**: `grails/` (e.g. `grails/discoverTestsBatch`)
- **Documents**: `groovy` + `gsp`, `file` scheme only
- **Watchers**: `*.groovy`, `*.gsp`, `build.gradle`, `application.{yml,yaml,properties}`
- **Workspace**: Only `workspaceFolders[0]` indexed (single-root)

## Client (TypeScript)

| Layer | Responsibility |
|-------|---------------|
| `extension.ts` | Activation, DI |
| `ServiceContainer` | Composition root, service lifecycle |
| `UseCases` | Multi-service workflow orchestration |
| `Services` | Single-concern domain services |
| `UI` | Commands, tree views, webviews |

See [`client/RULES.md`](../client/RULES.md) for architecture rules.

## Server (Groovy)

| Layer | Responsibility |
|-------|---------------|
| `GrailsLanguageServer` | LSP entry point, capability registration |
| `GrailsService` | Composition root, pipeline coordination |
| `GrailsTextDocumentService` | Document-level LSP dispatch |
| `GrailsWorkspaceService` | Workspace-level LSP dispatch |
| `providers/` | Modular LSP feature implementations |
| `core/` | Compiler, Gradle integration, AST visitor |
| `services/` | File tracking, cancellation, health |
| `utils/` | Static utilities (TIER 2) |

See [`server/RULES.md`](../server/RULES.md) for architecture rules.

## Provider Tiers

| Tier | Pattern | Example |
|------|---------|---------|
| TIER 1 | Extends `BaseProvider`, takes `GrailsService` | Completion, Hover, Diagnostics |
| TIER 2 | Static utility, no dependencies | `GrailsUtils`, `CompletionUtil` |
| TIER 3 | Plug-n-play module, minimal deps | `GrailsYamlIntelligenceProvider(config)` |

## Feature Ownership

| Feature | Server Handler | Server Utility | Client |
|---------|---------------|----------------|--------|
| Completion | ✅ | | |
| Hover | ✅ | | |
| Diagnostics | ✅ | | |
| Go to Definition | ✅ | | |
| Find References | ✅ | | |
| Inlay Hints | ✅ | | |
| CodeLens | ✅ | | |
| Document Symbols | ✅ | | |
| Artefact type detection | | ✅ | |
| Class/package detection | | ✅ | |
| Folding ranges | | ✅ | ✅ |
| Syntax highlighting | | | ✅ |
| Auto-closing brackets | | | ✅ |

## Caching

| Scope | Use Case | Invalidation |
|-------|----------|-------------|
| Node-level | Hover, inlay hints | On demand |
| File-level | Folding, symbols | On `didChange` / `didClose` |
| Workspace-level | References, type index | On `setupWorkspace` / rebuild |

Disk cache: `.grails-lsp/` under project root (`ProjectCache`, `grails.cache.*`).

## GSP Handling

1. `GspToGroovyConverter` replaces HTML with whitespace, preserves Groovy blocks
2. `GrailsCompiler` injects virtual source into `CompilationUnit`
3. Standard providers work on the virtual AST

## Key Design Decisions

- **Single GrailsService**: Shared mutable state is intentional — providers read freely, never write to compiler/visitor/fileTracker
- **`@CompileStatic` everywhere**: Performance for real-time LSP operations
- **Push diagnostics**: Not pull-based (server pushes on change)
- **First-folder-only**: Multi-root not yet implemented
- **Shadow JAR**: Server bundled as single JAR inside extension
