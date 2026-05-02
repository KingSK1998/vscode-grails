# Server Status

> **AI AGENTS: Update this file on EVERY task that touches server code. Status only — no docs, no API, no architecture. Just current state.**
> Last updated: 2026-05-01

---

## Server Info

- **Version**: 0.1.0-SNAPSHOT
- **Language**: Groovy 4.0.23+
- **Java**: 17.0.8+ LTS
- **LSP Library**: Eclipse LSP4J 0.23.1
- **Build**: Gradle 8.12+

---

## LSP Feature Status

### ✅ Done & Working

| LSP Feature | Class / Provider | Notes |
|---|---|---|
| Text completion | `CompletionProvider` | Context-aware, modular strategies, cancellation-aware |
| Hover information | `HoverProvider` | Type info, docs |
| Go to definition | `DefinitionProvider` | Classes, methods, properties |
| Go to implementation | `ImplementationProvider` | |
| Find references | `ReferencesProvider` | Cross-file |
| Document symbols | `DocumentSymbolProvider` | AST-based |
| Workspace symbols | `WorkspaceSymbolProvider` | Project-wide |
| Diagnostics (push) | `DiagnosticsProvider` | Syntax errors + Grails validations |
| Signature help | `SignatureHelpProvider` | Method parameter assist |
| Code lens | `CodeLensProvider` | Test runners, method markers |
| Inlay hints | `InlayHintsProvider` | Type hints for locals |
| Rename (best-effort) | `GrailsRenameProvider` | `renameProvider: true`; inner-class name parsing fixed |
| Initialize / capabilities | `GrailsLanguageServer` | Full `ServerCapabilities` registration |

### 🟡 Stub / Returns Empty (Registered but No-Op)

| LSP Feature | Notes |
|---|---|
| Code actions | `textDocument/codeAction` — handler returns empty list |

### 🔴 Known Issues / Broken

| Issue | Notes |
|---|---|
| Prepare rename | Not fully polished; basic rename works |
| Pull diagnostics | Not registered yet |

### ⬜ Planned / Future

| Feature | Notes |
|---|---|
| Document highlighting | Not started |
| Semantic tokens | Not started |
| Formatting | Not started |
| Folding ranges | Not started |
| Code actions (real) | Needs strategy design |
| Execute commands | Not registered |
| Multi-root workspace | Planned for 1.1.0 |
| ML-based suggestions | Planned for 1.2.0 |

---

## Core Architecture Status

| Component | Status | Notes |
|---|---|---|
| `GrailsLanguageServer` | ✅ Stable | Main LSP entry point |
| `GrailsService` | ✅ Stable | Central orchestration |
| `GrailsCompiler` | ✅ Stable | Thread-safe, incremental, ~2-5s full / ~50-200ms incremental |
| `GradleService` | ✅ Stable | Cache mgmt, artifact downloads, `invalidateProjectCache()` |
| `BaseProvider` | ✅ Stable | Base for all providers |
| Central AST Resolution | ✅ Stable | Performance backbone |
| Completion strategies | ✅ Stable | Modular, extensible |

---

## Build Health

| Check | Status |
|---|---|
| `./gradlew build` | ✅ Passing |
| `./gradlew test` | ✅ Passing |
| JaCoCo coverage | ✅ ≥60% threshold met |
| `./gradlew checkAll` | ✅ Clean |
