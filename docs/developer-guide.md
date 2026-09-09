# Developer guide

Monorepo: TypeScript **client** + Groovy **server** (LSP4J).

The first release target is Grails 7+ with Groovy 4. See the [product roadmap](product-roadmap.md) for compatibility boundaries and release gates. Build commands below run from the repository root unless a different directory is shown.

For implementation work, use the [agent execution guide](agent-execution.md), task cards and specs. Run `node scripts/roadmap.js next` to select work and `node scripts/roadmap.js validate` after task metadata changes. The [system map](architecture/system-map.md) distinguishes current source from unverified target behavior.

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
npm run copy-server        # copy an already built server JAR
npm run package            # build and create vscode-gng-support.vsix
```

## Debug

1. Open the **repo root** in VS Code.
2. Build once with `npm run build`, then use `npm run watch` for client changes.
3. **Press F5** — Extension Development Host ([`.vscode/launch.json`](../.vscode/launch.json)).
4. Optional: attach a Java debugger to the LSP (`GrailsLanguageServer.main`, remote debug flags).

**Server only:** `cd server && ./gradlew run`

Normal extension startup launches the bundled JAR over stdio. Manual TCP development is opt-in with `grails.server.developmentMode: true` and `grails.languageServer.developmentPort: 5007`; launch the server JVM with `-Dgrails.lsp.debug.remote=true`. `grails.server.port` configures the application, not the language-server connection.

## Tests

- Client unit tests: `npm run test:client` (no editor launch).
- Server: `npm run test:server`; CI also runs server tests through Gradle `build`.
- Both: `npm test`.
- Bundled transport: `npm run test:smoke`, after `npm run build`. Uses the Grails test fixture and an in-memory document; it validates protocol framing, discovery, diagnostics and symbols. It requires the fixture's resolved dependencies and can write normal Gradle/LSP caches.
- Type/lint gates: `npm run check-types` and `npm run lint`.

Headless tests do not replace a fresh VSIX install in an Extension Development Host. Verify explorer, completion, navigation, run/debug/test, and accessible UI behavior there before a release.

## Architecture

| Path | Role |
|------|------|
| [`client/`](../client/) | Extension → `client/out/extension.js` (`tsc` + `esbuild`) |
| [`server/`](../server/) | LSP → Gradle `shadowJar`, bundled for the extension |
| [`resources/`](../resources/) | Grammars, snippets, icons |

**Runtime:** activation → LanguageServerManager starts JVM + stdio LSP → initialize returns capabilities → initialized-time background discovery (R0-02 currently pending) → queued document analysis in project contexts → committed provider reads. Initialize must not wait for Gradle. See the [system map](architecture/system-map.md) for current gaps.

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

**Performance:** No reproducible benchmark baseline is established here. Measure warm/cold latency, heap and storage against the [release targets](product-roadmap.md#8-acceptance-scorecard-and-evaluation).

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
- Server: `./gradlew --no-daemon clean build --stacktrace` (includes tests).
- Client: `npm ci`, `compile`, `bundle`, `test:client`.
- Packaging copies the current server to `client/server/grails-language-server-current-all.jar`. The VSIX excludes obsolete JARs, analysis output, logs and development sources.

Release: `npm ci && npm run vscode:prepublish`

---

[← README](../README.md) · [Architecture →](./architecture.md) · [User guide →](./user-guide.md)
