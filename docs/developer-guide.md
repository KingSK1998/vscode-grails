# Developer guide

Monorepo: TypeScript **client** + Groovy **server** (LSP4J).

## Prerequisites

- **Node.js 20.x** (CI: **20.18.1**)
- **JDK 17**
- **VS Code** matching `engines.vscode` in [`package.json`](../package.json)

## Clone & build

```bash
git clone https://github.com/KingSK1998/vscode-gng-support.git
cd vscode-gng-support
npm ci
npm run build              # client compile+bundle + server shadowJar
npm run build:client
npm run build:server
```

## Debug

1. Open the **repo root** in VS Code.
2. `npm run watch` (or compile + bundle watch).
3. **Press F5** — Extension Development Host ([`.vscode/launch.json`](../.vscode/launch.json)).
4. Optional: attach a Java debugger to the LSP (`GrailsLanguageServer.main`, remote debug flags).

**Server only:** `cd server && ./gradlew run`

## Tests

- Client: `npm test` (see `package.json`).
- Server: `cd server && ./gradlew test` — **not** run in default CI.

## Architecture

| Path | Role |
|------|------|
| [`client/`](../client/) | Extension → `client/out/extension.js` (`tsc` + `esbuild`) |
| [`server/`](../server/) | LSP → Gradle `shadowJar`, bundled for the extension |
| [`resources/`](../resources/) | Grammars, snippets, icons |

**Runtime:** activation → `LanguageServerManager` starts JVM + stdio LSP → `GrailsLanguageServer.initialize` (Gradle load, capabilities) → `GrailsTextDocumentService` + `GrailsCompiler` / `GrailsASTVisitor`.

**Caching:** `.grails-lsp` under project root (`ProjectCache`, `grails.cache.*`); AST/completion caches invalidate on `didChange` / `didClose`.

## GSP Architecture

The server handles `.gsp` files by converting them into "Virtual Groovy" files before compilation.

1. **Conversion**: `GspToGroovyConverter` replaces non-Groovy characters (HTML) with whitespaces and preserves Groovy code blocks.
2. **Compilation**: The `GrailsCompiler` injects this virtual source into the `CompilationUnit`.
3. **Completions**: Standard `GroovyHelperIntegration` and `ASTVisitor` work on the virtual AST.

## Server Capabilities

Source: [`GrailsLanguageServer.groovy`](../server/src/main/groovy/kingsk/grails/lsp/GrailsLanguageServer.groovy) `initialize()`.

### Enabled

| Feature | Notes |
|---------|-------|
| Incremental sync | |
| Hover, completion (+ resolve), signature help | |
| Definition, implementation, references | |
| Document + workspace symbols | |
| Code lens (+ resolve), inlay hints | |
| Rename | `RenameOptions(false)` — no prepare step |

### Disabled / Stubbed

| Feature | Notes |
|---------|-------|
| Document highlight, semantic tokens, formatting, folding | Commented off |
| Pull `diagnosticProvider` | Diagnostics still **pushed** |
| `codeAction` | Returns empty list |
| Workspace folder changes | Not handled |

### Workspace

`setupWorkspace` uses **`workspaceFolders[0].uri`** only.

### VS Code client

- **Documents:** `groovy` + `gsp`, `file` scheme — [`clientConfig.ts`](../client/src/services/languageServer/clientConfig.ts)
- **Watchers:** `*.groovy`, `*.gsp`, `build.gradle`, `application.{yml,yaml,properties}`

---

## Testing Guide

### Base Test Classes

- `BaseLspSpec`: Base for all LSP tests
- `CompletionTestSpec`: Completion functionality
- `DiagnosticsTestSpec`: Diagnostics
- `DefinitionTestSpec`: Definition
- `SignatureHelpTestSpec`: Signature help
- `TypeDefinitionTestSpec`: Type definition

### Project Types

- `ProjectType.DUMMY`: Fast unit tests (default)
- `ProjectType.GROOVY`: Groovy project integration
- `ProjectType.GRAILS`: Full Grails integration

### Test Structure

```groovy
class MyFeatureSpec extends CompletionTestSpec {
    def "should provide specific functionality"() {
        given:
        initializeProject(ProjectType.DUMMY)
        String content = '''class MyClass { def myMethod() {} }'''

        when:
        List<CompletionItem> items = getCompletionItems(content, 3, 10)

        then:
        assertContainsItem(items, "expectedItem")
    }
}
```

### Running Tests

```bash
./gradlew test                        # All tests
./gradlew test --tests "MySpec"        # Specific class
./gradlew jacocoTestReport             # With coverage
./gradlew checkAll                     # All checks
```

---

## Compiler Architecture

### GrailsCompiler

The GrailsCompiler provides full project compilation with incremental file updates.

**Core Methods:**

```groovy
void compileProject()              // Full project compilation
void compileSourceFile(TextFile)    // Incremental compilation
void invalidateCompiler()           // Clear caches
SourceUnit getSourceUnit(TextFile)  // Get compiled AST
ErrorCollector getErrorCollectorOrNull()
```

**Thread-safe design:** Uses `ReentrantLock` for compile operations, `ConcurrentHashMap` for source unit caching.

**Performance:** ~2-5s full project, ~50-200ms incremental.

---

## Logging Standards

### Format: `[COMPONENT] message`

```
log.info("[COMPILER] Starting full project compilation")
log.info("[AST] Visiting source unit: MyClass.groovy")
log.info("[COMPLETION] Resolving completions for: MyClass.groovy")
log.warn("[COMPILER] Cannot compile null text file")
log.error("[GRADLE] Project build failed", exception)
```

### Component Tags

| Tag | Purpose |
|-----|---------|
| `[COMPILER]` | Compilation operations |
| `[AST]` | AST manipulation |
| `[COMPLETION]` | Code completion |
| `[HOVER]` | Hover information |
| `[DIAGNOSTICS]` | Diagnostics |
| `[DEFINITION]` | Go to definition |
| `[WORKSPACE]` | Workspace operations |
| `[GRADLE]` | Gradle integration |

---

## Feature Responsibilities

### Feature Ownership

| Feature | Handler | Utility | Client |
|---------|---------|---------|--------|
| Inlay Hints | ✅ | | |
| Completion | ✅ | | |
| Hover | ✅ | | |
| CodeLens | ✅ | | |
| Document Symbols | ✅ | | |
| Go to Definition | ✅ | | |
| Find References | ✅ | | |
| Diagnostics | ✅ | | |
| Artefact type detection | | ✅ | |
| Class/package detection | | ✅ | |
| Folding ranges | | ✅ | ✅ |
| Syntax highlighting | | | ✅ |

### Caching Strategy

| Scope | Use Case |
|-------|----------|
| Node-level | Hover, inlay hints (evaluate on demand) |
| File-level | Folding, symbols (re-evaluate on save) |
| Workspace-level | References, type index (compute once on load) |

---

## Client Source Map

`client/src/`: `core/` (activation, DI, events), `services/` (LSP, Gradle, config), `features/`, `ui/` (commands, tree, views), `utils/`, `extension.ts`.

---

## CI

Workflow: [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) · [GitHub Actions](https://github.com/KingSK1998/vscode-gng-support/actions)

- **Java 17**, **Node 20.18.1**, Ubuntu
- Server: `./gradlew clean build -x test`
- Client: `npm ci`, `compile`, `bundle`

Release: `npm ci && npm run vscode:prepublish`

---

[← README](../README.md) · [Architecture →](./architecture.md) · [User guide →](./user-guide.md)