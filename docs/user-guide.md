# User guide

Extension: **Groovy & Grails Support** · Marketplace id `KingSK1998.vscode-gng-support` · Version tracked in root [`package.json`](../package.json).

## Install

1. Install from the [VS Code Marketplace](https://marketplace.visualstudio.com/items?itemName=KingSK1998.vscode-gng-support).
2. **Gradle for Java** is installed automatically as a dependency.

## Requirements

- **VS Code** `^1.103.0` (`package.json` → `engines.vscode`).
- **JDK 17+** for the language server (`grails.javaHome` if needed).
- A **Gradle-based** Groovy or Grails project (activation matches `build.gradle`, `grails-app`, etc. in `activationEvents`).

## Open a project

Open the folder that is the app root (where `build.gradle` or `grails-app` lives).

**Multi-root:** the language server only loads **the first workspace folder**. Use one window per project until multi-root is implemented.

## First settings

In **Settings**, search **Grails**. Important keys: `grails.javaHome`, `grails.server.jvmArgs` (memory for the LSP JVM). See the table below.

## Features (what works today)

**Language server** (when the JAR is running and the project has loaded):

- Completion (with resolve), hover, signature help  
- Go to definition, go to implementation, find references  
- Document / workspace symbols, code lens, inlay hints  
- Rename (best-effort)  
- Diagnostics (pushed by the server; not LSP pull-diagnostics registration)

**Not available yet:** formatting, folding, semantic tokens, document highlights, code actions / quick fixes (empty handler).

**VS Code UI:** Grails project tree, commands (run/test/build/clean, artifacts, restart LSP, Gradle sync, dashboard), syntax + snippets for `groovy` and `gsp`, status bar and output channels.

**GSP:** grammar, snippets, and LSP via the same `documentSelector` as Groovy—not full separate template-IDE parity. Optional GSP UI: `client/src/ui/views/GSPTemplateViewer.ts`.

**Gradle / Grails:** uses the Gradle extension and your project’s tasks.

## Settings (`package.json`)

| Setting | Default | Description |
|---------|---------|-------------|
| `grails.grailsHome` | `""` | Grails install (optional) |
| `grails.javaHome` | `""` | Java home for LSP / tooling |
| `grails.server.autoStart` | `false` | Auto-start Grails dev server |
| `grails.server.port` | `5007` | Dev server port (setting) |
| `grails.completion.detail` | `advanced` | `basic` / `standard` / `advanced` |
| `grails.completion.maxItems` | `1000` | Max completions |
| `grails.includeSnippets` | `true` | Snippets in completion |
| `grails.codeLens.enabled` | `true` | CodeLens |
| `grails.diagnostics.enabled` | `true` | Diagnostics |
| `grails.server.jvmArgs` | `["-Xmx1g","-XX:+UseG1GC"]` | JVM args for **language server** |
| `grails.languageServer.trace` | `off` | `off` / `messages` / `verbose` |
| `grails.cache.enabled` | `true` | Project cache |
| `grails.cache.directory` | `.grails-lsp` | Cache directory |
| `grails.experimental.smartRecompilation` | `false` | Experimental |
| `grails.debug.logLevel` | `warn` | Extension logs |
| `grails.server.developmentMode` | `true` | LSP dev mode |
| `grails.icons.suggestMaterialTheme` | `true` | Icon theme hint |

Initialization options sent to the server come from `ConfigurationService` → `clientConfig.ts` (`javaHome`, `completionDetail`, `maxCompletionItems`, `cacheEnabled`, `codeLensEnabled`). If you add settings, update the manifest and `ConfigurationService.ts`.

## Troubleshooting

- **LSP never ready:** JDK 17+, correct `grails.javaHome`, Output panel + `grails.languageServer.trace`, valid Gradle/Grails folder.  
- **Weak completions:** wait for indexing; try `grails.completion.maxItems` / `detail` = `basic`; remember only the **first** workspace root is indexed.  
- **Stale Gradle model:** delete `.grails-lsp` in the project root; restart LSP after `build.gradle` changes.  
- **CI vs local:** [GitHub Actions](https://github.com/KingSK1998/vscode-gng-support/actions) builds with Java 17 and Node 20.18.1; server tests are skipped in the default workflow (see [developer-guide.md](./developer-guide.md#ci)).

**Issues:** [github.com/KingSK1998/vscode-gng-support/issues](https://github.com/KingSK1998/vscode-gng-support/issues)

## Status and roadmap

Update this section when behavior changes (not only README marketing).

**Done:** client UI + explorer + commands; bundled LSP; `groovy`/`gsp` grammars and LSP selector; server features listed above; first-folder-only workspace; CI builds JAR + client bundle (tests not required in workflow).

**Roadmap:** real multi-root; code actions, highlights, formatting, folding, semantic tokens, pull diagnostics; large-repo performance.

**Releases:** [`client/CHANGELOG.md`](../client/CHANGELOG.md). **Build health:** [Actions](https://github.com/KingSK1998/vscode-gng-support/actions).

---

[← README](../README.md) · [Developer guide →](./developer-guide.md)
