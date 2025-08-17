# Changelog

All notable changes to the Grails Framework Support extension will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Initial project structure and documentation setup
- Comprehensive README with feature descriptions
- Contributing guidelines for developers
- MIT License for open source distribution

### Changed
- Enhanced project documentation structure

### Fixed
- N/A

### Removed
- N/A

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