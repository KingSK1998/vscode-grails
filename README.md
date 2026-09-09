# Groovy and Grails Support for VS Code

[![Build Status](https://github.com/KingSK1998/vscode-gng-support/workflows/CI/CD%20Pipeline/badge.svg)](https://github.com/KingSK1998/vscode-gng-support/actions)

A VS Code extension in development for **Grails 7+ and Groovy 4**, with a TypeScript client and a JVM language server. The goal is an IDE that discovers APIs from each project's resolved libraries, keeps editing responsive during analysis, and makes its project knowledge available to developers and agents. It does not require a patched Grails distribution.

Read the [complete product strategy and roadmap](docs/product-roadmap.md) for the finished-product definition, AI workflows, release tasks and measurable acceptance gates. The [implementation handoff](docs/implementation-handoff.md) records the interrupted work and the exact next coding task. Older Grails/Groovy versions and automatic compatibility with future releases are not currently guaranteed.

**Continuing development with an agent:** start with the [execution guide](docs/agent-execution.md) and run `node scripts/roadmap.js next`. It selects from 38 dependency-ordered tasks with [acceptance cards](docs/execution/task-specifications.md), focused specifications and evidence records. `node scripts/roadmap.js validate` checks queue consistency; it does not certify runtime correctness.

## Implemented feature areas

The repository contains the following capabilities; their presence does not mean every real-world Grails workflow has been validated:

- Groovy and GSP syntax highlighting, snippets and language configuration.
- LSP completion, hover, signature help, navigation, references, symbols and diagnostics.
- Providers for rename, code actions, inlay hints, semantic tokens, formatting and folding.
- Grails artifact explorer, artifact creation commands, run/debug/test integration.
- Dashboard, dependency graph and GORM SQL preview views.
- Dependency-based discovery, GSP-to-Groovy conversion, TagLib and i18n support.
- Project contexts, snapshots, background work and memory lifecycle infrastructure.

Full HTML/CSS/JavaScript intelligence inside GSP, comprehensive YAML/JSON schema integration, agent tools and a tested compatibility matrix remain release work. Runtime metaprogramming can limit static resolution; refactoring requires particular care around ambiguous symbols.

## Requirements

- VS Code compatible with `engines.vscode` in [package.json](package.json), currently `^1.103.0`.
- JDK 17 for the language server and the first supported Grails fixture set.
- A Grails 7/Groovy 4 project with its own Gradle wrapper.
- Node.js 20+ and Git when building the extension from source.

[Gradle for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-gradle) is declared as an extension dependency.

## Build and try the extension

Run these commands from the repository root on Windows, Linux or macOS:

```sh
npm ci
npm run build
npm run test:client
npm run test:server
npm run package
```

`npm run package` builds the extension and creates `vscode-gng-support.vsix` in the repository root. In VS Code, use **Extensions: Install from VSIX**, select that file and open a Grails project. Inspect the Grails output channels if discovery or startup fails. Packaging locally does not publish a Marketplace release.

The normal configuration launches the bundled language server. Set Java explicitly if needed:

```json
{
  "grails.javaHome": "/path/to/jdk-17",
  "grails.completion.detail": "advanced",
  "grails.completion.maxItems": 1000,
  "grails.codeLens.enabled": true
}
```

Java selection uses `grails.javaHome`, then `JAVA_HOME`, then `java` on PATH. Paths containing spaces are passed as executable paths, not shell commands.

## Development

Open the repository root in VS Code, build once, then press **F5** using the existing **Run Extension** launch configuration. Use `npm run watch` for client changes. After server changes, run `npm run build:server` and `npm run copy-server`, then restart the language server.

| Command | Purpose |
|---|---|
| `npm run build` | Build server, copy its JAR, compile and bundle client |
| `npm run build:client` | Compile and bundle client |
| `npm run build:server` | Build the server fat JAR using the project's Gradle wrapper |
| `npm run copy-server` | Copy the already built fat JAR to the stable runtime name |
| `npm run test:client` | Compile and run headless client startup/packaging tests |
| `npm run test:server` | Run server tests and the configured coverage report |
| `npm run test:smoke` | Exercise the bundled JAR over stdio with the Grails test fixture |
| `npm test` | Run both test groups |
| `npm run check-types` | TypeScript checking without output |
| `npm run lint` | Client ESLint checks |
| `npm run watch` | Watch client compilation and bundling |
| `npm run package` | Build and create a local VSIX |

The packaged runtime is `client/server/grails-language-server-current-all.jar`. The copy step requires exactly one versioned fat JAR in `server/build/libs`; it rejects ambiguous old build outputs instead of guessing by file size.

For manual language-server development, start the JVM with `-Dgrails.lsp.debug.remote=true` and configure:

```json
{
  "grails.server.developmentMode": true,
  "grails.languageServer.developmentPort": 5007
}
```

`grails.server.port` is the application port (default 8080). Existing development configurations that used it for the LSP connection must move to `grails.languageServer.developmentPort`. Leave development mode disabled for the bundled server.

## Current limitations

- Multi-project classpath isolation, Gradle dependency refresh and explicit dependency-graph integration need further work and real-project tests.
- Snapshot infrastructure does not yet establish deep immutability of every retained AST object.
- Registered providers and mock tests do not establish complete IDE behavior. Packaged-editor acceptance tests and measured performance baselines are release gates.
- Agent tooling is planned; there is no shipped ML-based offline completion model or general-purpose agent adapter.

## Documentation and contributions

| Document | Purpose |
|---|---|
| [Product roadmap](docs/product-roadmap.md) | Vision, compatibility scope, performance targets and acceptance gates |
| [Developer guide](docs/developer-guide.md) | Build, debug, tests and implementation references |
| [Architecture](docs/architecture.md) | Component responsibilities |
| [Client status](client/STATUS.md) / [Server status](server/STATUS.md) | Implementation and validation status |
| [AGENTS.md](AGENTS.md) | Contributor workflow and project rules |
| [Failure modes](docs/failure-modes.md) | Regression history and test matrix |

Before contributing, read the project rules and the tests for the affected component. Run the applicable validation, update status and knowledge-base documents, and include the observed behavior and test evidence in the pull request.

[Report an issue](https://github.com/KingSK1998/vscode-gng-support/issues) with the project/runtime versions, reproduction steps and relevant output. Licensed under the [MIT License](LICENSE).
