# Changelog

All notable changes to the Grails Framework Support extension will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- Documentation: merged many `docs/*.md` files into **`docs/user-guide.md`** and **`docs/developer-guide.md`** (see `docs/README.md`).

## [0.0.2] - 2026-04-11

### Added
- Centralized documentation under `docs/` (later trimmed to `user-guide.md` + `developer-guide.md`; see Unreleased).
- Language server **rename** wired (`textDocument/rename`); `GrailsRenameProvider` fixes (`newName`, inner-class name parsing).
- Server **perf** debug logging: `compileAndVisitAST` duration when log level is DEBUG.
- Completion path respects **cancellation** (`CancellationException` propagates).
- Gradle **project cache** staleness checks `settings.gradle` / `.kts` and `build.gradle.kts`.

### Changed
- Marketplace keywords: removed misleading “multi-root ready”; documented **first workspace folder only** for the LSP.
- Root README requirements aligned with `package.json` (VS Code `^1.103.0`, Node 20.x in CI).

### Documentation
- `CLIENT_INTEGRATION_GUIDE` server/client stubs point at monorepo `docs/`; LSP details live in developer guide.

## [0.0.1] - 2024-01-XX

### Added
- **Core Extension Features**
  - Language Server integration for Groovy and GSP files
  - Project Explorer with Controllers, Services, and Domains tree view
  - Command palette integration for Grails commands
  - Artifact creation wizard (Controller, Domain, Service, TagLib, etc.)
  - Workspace setup automation

- **Language Support**
  - Groovy syntax highlighting with TextMate grammar
  - GSP (Groovy Server Pages) syntax highlighting
  - Code snippets for common Grails patterns
  - Auto-closing pairs and bracket matching
  - Comment toggling support

- **Development Tools**
  - Gradle integration via VS Code Gradle extension
  - Terminal integration for Grails commands
  - Status bar indicators for extension state
  - Comprehensive error handling and reporting

- **Configuration Options**
  - Grails path and Java home configuration
  - Language server settings (completion detail, max items, etc.)
  - Development server configuration (port, host, JVM args)
  - CodeLens and compiler phase settings

- **Commands**
  - `grails.run` - Run Grails application
  - `grails.test` - Run tests
  - `grails.clean` - Clean project
  - `grails.compile` - Compile project
  - `grails.createArtifact` - Create new artifacts
  - `grails.setupWorkspace` - Configure workspace
  - `grails.restartServer` - Restart language server
  - `grails.runGradleTask` - Run Gradle tasks

### Technical Implementation
- TypeScript with strict type checking
- Service-oriented architecture (GradleService, StatusBarService, ErrorService)
- Language Server Protocol client implementation
- VS Code Extension API integration
- Comprehensive error handling with severity levels

### Dependencies
- VS Code Gradle extension (required)
- vscode-languageclient for LSP communication
- TypeScript, ESLint, and testing framework setup