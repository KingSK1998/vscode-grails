# Developer guide

Monorepo: TypeScript **client** + Groovy **server** (LSP4J). Deep server internals: [`server/README.md`](../server/README.md), [`server/.agent.md`](../server/.agent.md), [`server/docs/`](../server/docs/) (compiler, testing, logging, feature matrix).

## Prerequisites

- **Node.js 20.x** (CI: **20.18.1**)
- **JDK 17**
- **VS Code** matching `engines.vscode` in [`package.json`](../package.json)

## Clone & build

```bash
git clone https://github.com/KingSK1998/vscode-grails.git
cd vscode-grails
npm ci
npm run build              # client compile+bundle + server shadowJar
npm run build:client
npm run build:server
```

## Debug

1. Open the **repo root** in VS Code.  
2. `npm run watch` (or compile + bundle watch).  
3. **F5** — Extension Development Host ([`.vscode/launch.json`](../.vscode/launch.json)).  
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

Feature ownership / caching tiers (handler vs client vs scope): [`server/docs/FEATURE_RESPONSIBILITIES.md`](../server/docs/FEATURE_RESPONSIBILITIES.md).

## Language server reference

Source of truth: [`server/.../GrailsLanguageServer.groovy`](../server/src/main/groovy/kingsk/grails/lsp/GrailsLanguageServer.groovy) `initialize()` and [`GrailsTextDocumentService.groovy`](../server/src/main/groovy/kingsk/grails/lsp/services/GrailsTextDocumentService.groovy).

### Enabled `ServerCapabilities`

| Area | Notes |
|------|--------|
| Incremental sync | |
| Hover, completion (+ resolve), signature help | Triggers in server code |
| Definition, implementation, references | |
| Document + workspace symbols | |
| Code lens (+ resolve), inlay hints | |
| Rename | `RenameOptions(false)` — no prepare step |

### Disabled or stubbed

| Area | Notes |
|------|--------|
| Document highlight, semantic tokens, formatting, folding, document links, execute command | Commented off in `initialize()` |
| Pull `diagnosticProvider` | Commented; diagnostics still **pushed** |
| `codeAction` | Declared path returns **empty** list |
| Workspace folder changes | Not handled |

### Workspace

`setupWorkspace` uses **`workspaceFolders[0].uri`** only.

### VS Code client

- **Documents:** `groovy` + `gsp`, `file` scheme — [`client/src/services/languageServer/clientConfig.ts`](../client/src/services/languageServer/clientConfig.ts)  
- **Watchers:** `*.groovy`, `*.gsp`, `build.gradle`, `application.{yml,yaml,properties}`

### Initialize (conceptual)

1. Require non-empty `workspaceFolders`.  
2. Progress begin → `setupWorkspace(firstFolderUri, async)` → capabilities → progress end → `InitializeResult`.

Progress and `showMessage` use LSP4J client notifications.

## CI

Workflow: [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) · Runs: [GitHub Actions](https://github.com/KingSK1998/vscode-grails/actions)

- Triggers: `main`, `develop` — push + PR.  
- **Java 17**, **Node 20.18.1**, Ubuntu.  
- Server: `./gradlew clean build -x test`.  
- Root: `npm ci`, `npm run compile`, `npm run bundle`, JAR validation.  
- **Does not** run server unit tests or client `npm test` today.

Release build locally: `npm ci && npm run vscode:prepublish` (or `vsce package`).

## Client source map

Under `client/src/`: `core/` (activation, DI, events), `services/` (LSP, Gradle, config, workspace), `features/`, `ui/` (commands, tree, views), `utils/`, `extension.ts`.

---

[← Documentation home](./README.md) · [User guide →](./user-guide.md)
