# Client Status

> **AI AGENTS: Update this file on EVERY task that touches client code. Status only — no docs, no API, no architecture. Just current state.**
> Last updated: 2026-05-11

---

## Extension Info

- **Version**: 0.0.2
- **Language**: TypeScript (strict mode)
- **VS Code Engine**: ^1.103.0
- **Node**: 20.x

---

## Feature Status

### ✅ Done & Working

| Feature | Notes |
|---|---|
| Groovy syntax highlighting | TextMate grammar |
| GSP syntax highlighting | TextMate grammar |
| Groovy code snippets | Controller, Service, Domain, Test templates |
| GSP code snippets | Forms, tags, layouts, i18n |
| Auto-closing pairs / bracket matching | Groovy + GSP configs |
| Comment toggling | Both languages |
| LSP client integration | `vscode-languageclient`, connects to Grails Language Server |
| Language Server lifecycle | `LanguageServerManager` handles start/stop/restart |
| Project Explorer (tree view) | Shows Controllers, Services, Domains |
| Status bar indicator | Extension state display |
| `grails.run` command | Runs Grails app via terminal |
| `grails.test` command | Runs tests |
| `grails.clean` command | Cleans project |
| `grails.compile` command | Compiles project |
| `grails.createArtifact` command | Wizard for Controller/Domain/Service/TagLib creation |
| `grails.setupWorkspace` command | Workspace auto-configuration |
| `grails.restartServer` command | Restarts LSP server |
| `grails.runGradleTask` command | Runs arbitrary Gradle tasks |
| GradleService integration | Hooks into VS Code Gradle extension API |
| ErrorService | Centralized error handling, severity levels |
| Grails project detection | Checks for `grails-app/` directory |
| Configuration settings | `grails.path`, `grails.javaHome`, `grailsLsp.*` |
| Dev mode LSP connection | Connects to remote server on port 5007 |
| Production mode LSP connection | Uses local JAR `server/grails-language-server-1.0-all.jar` |
| **ISSUE-004** | Activation timeout wrapper (30s) with user notification |
| **ISSUE-006** | EventBus async handler execution with error isolation |
| **ISSUE-015** | Bundle D3.js locally for offline support |

### 🟡 In Progress

| Feature | Notes |
|---|---|
| `grails.runGradleTask` UI | Command wired; picker UI needs polish |

### 🔴 Known Issues / Broken

| Issue | Notes |
|---|---|
| Multi-root workspace | Only first workspace folder used by LSP — documented limitation |

### ⬜ Planned / Future

| Feature | Notes |
|---|---|
| Multi-root workspace support | Needs server-side multi-root first |
| Grails plugin marketplace listing | Extension not yet published |
| Enhanced project explorer | More artifact types (Jobs, TagLibs, etc.) |

---

## Build Health

| Check | Status |
|---|---|
| TypeScript compile | ✅ Clean |
| ESLint | ✅ Clean |
| Tests | ✅ Passing |
| esbuild bundle | ✅ OK |
