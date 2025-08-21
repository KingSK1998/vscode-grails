# Grails Extension

## Client Side

```plaintext
grails-groovy-extension/
├── src/
│   ├── extension.ts                    // Main extension entry point and activation
│   ├── commands/
│   │   ├── index.ts                    // Command registry and exports
│   │   ├── artifactCommands.ts         // Create controller, service, domain commands
│   │   ├── grailsCommands.ts           // Run app, clean, compile commands
│   │   └── projectCommands.ts          // Project setup, configuration commands
│   ├── providers/
│   │   ├── grailsExplorerProvider.ts   // Tree data provider for Grails Explorer
│   │   ├── artifactProvider.ts         // Provider for artifacts tree view
│   │   ├── testProvider.ts             // Test results and runner provider
│   │   └── completionProvider.ts       // Custom completion items (if needed beyond LSP)
│   ├── views/
│   │   ├── grailsExplorer.ts           // Grails Explorer tree view implementation
│   │   ├── artifactExplorer.ts         // Artifacts (Controllers/Services/Domains) tree view
│   │   ├── testExplorer.ts             // Test results and management view
│   │   └── pluginManager.ts            // Plugin management view
│   ├── webviews/
│   │   ├── artifactWizard.ts           // Webview for creating new artifacts
│   │   ├── projectSettings.ts          // Project configuration webview
│   │   ├── welcomePage.ts              // Extension welcome/onboarding page
│   │   └── pluginBrowser.ts            // Plugin browser and installer webview
│   ├── utils/
│   │   ├── grailsUtils.ts              // Grails project detection and utilities
│   │   ├── fileUtils.ts                // File system operations and helpers
│   │   ├── templateUtils.ts            // Code templates and snippets
│   │   └── configUtils.ts              // Configuration reading/writing utilities
│   ├── services/
│   │   ├── grailsService.ts            // Grails CLI integration and execution
│   │   ├── gradleService.ts            // Gradle task execution service
│   │   ├── pluginService.ts            // Plugin management and installation
│   │   └── testService.ts              // Test execution and result parsing
│   ├── decorations/
│   │   ├── grailsDecorations.ts        // File decorations for Grails artifacts
│   │   └── statusBarManager.ts         // Status bar items and management
│   ├── snippets/
│   │   ├── grailsSnippets.ts           // Code snippets for Grails
│   │   └── groovySnippets.ts           // Groovy language snippets
│   └── types/
│       ├── grails.d.ts                 // Grails-specific type definitions
│       └── extension.d.ts              // Extension-wide type definitions
├── resources/
│   ├── icons/                          // Extension and tree view icons
│   │   ├── grails.svg
│   │   ├── controller.svg
│   │   ├── service.svg
│   │   └── domain.svg
│   ├── templates/                      // File templates for artifacts
│   │   ├── controller.groovy.template
│   │   ├── service.groovy.template
│   │   └── domain.groovy.template
│   └── webview/                        // HTML/CSS/JS for webviews
│       ├── artifactWizard.html
│       ├── welcomePage.html
│       └── styles/
│           └── webview.css
├── package.json                        // Extension manifest
├── tsconfig.json                       // TypeScript configuration
├── webpack.config.js                   // Bundle configuration
└── README.md                           // Extension documentation
```

### Key Files Description

#### Core Files

- extension.ts - Main activation point, registers all commands, -providers, and views
- commands/index.ts - Central command registry that exports all commands for easy management

#### Command Handlers

- artifactCommands.ts - Handles "Create Controller", "Create Service", "Create Domain" commands
- grailsCommands.ts - Grails-specific commands like "Run App", "Clean", "Test"
- projectCommands.ts - Project initialization and configuration commands

#### Providers (Tree Views)

- grailsExplorerProvider.ts - Your existing Grails Explorer functionality
- artifactProvider.ts - Provides structured view of Controllers, Services, Domains
- testProvider.ts - Test results and test runner integration

#### Webviews (Rich UI)

- artifactWizard.ts - Multi-step wizard for creating new artifacts with forms
- welcomePage.ts - Onboarding experience for new users
- pluginManager.ts - Visual plugin browser and installer

#### Services (Business Logic)

- grailsService.ts - Handles Grails CLI integration and command execution
- gradleService.ts - Integration with your existing Gradle tasks
- pluginService.ts - Plugin discovery, installation, and management

#### Utilities

- grailsUtils.ts - Project detection, convention helpers
- templateUtils.ts - Code generation and templating
- configUtils.ts - VS Code settings and workspace configuration

## Server Side

```plaintext
grails-groovy-language-server/
├── src/
│   ├── main/
│   │   ├── groovy/                     // Main source directory (Groovy files)
│   │   │   └── com/
│   │   │       └── grails/
│   │   │           └── languageserver/
│   │   │               ├── GrailsLanguageServer.groovy           // Main LSP server entry point
│   │   │               ├── GrailsLanguageServerLauncher.groovy   // Server startup and CLI handling
│   │   │               │
│   │   │               ├── services/
│   │   │               │   ├── GrailsTextDocumentService.groovy  // Text document operations (LSP standard)
│   │   │               │   ├── GrailsWorkspaceService.groovy     // Workspace operations (LSP standard)
│   │   │               │   └── GrailsLanguageClientService.groovy// Client communication service
│   │   │               │
│   │   │               ├── protocol/
│   │   │               │   ├── GrailsProtocolExtensions.groovy   // Custom LSP protocol extensions
│   │   │               │   ├── GrailsCustomCommands.groovy       // Custom command definitions
│   │   │               │   └── GrailsNotifications.groovy        // Custom notification definitions
│   │   │               │
│   │   │               ├── project/
│   │   │               │   ├── GrailsProjectManager.groovy       // Project detection and management
│   │   │               │   ├── GrailsProjectBuilder.groovy       // Project info extraction with Gradle API
│   │   │               │   ├── DependencyResolver.groovy         // Dependency resolution and caching
│   │   │               │   └── GrailsProjectWatcher.groovy       // File system watching for project changes
│   │   │               │
│   │   │               ├── completion/
│   │   │               │   ├── GrailsCompletionProvider.groovy   // Code completion logic
│   │   │               │   ├── GroovyCompletionProvider.groovy   // Groovy language completion
│   │   │               │   ├── GSPCompletionProvider.groovy      // GSP template completion
│   │   │               │   └── GrailsArtifactCompletionProvider.groovy // Grails artifact-specific completion
│   │   │               │
│   │   │               ├── navigation/
│   │   │               │   ├── GrailsDefinitionProvider.groovy   // Go to definition
│   │   │               │   ├── GrailsReferenceProvider.groovy    // Find references
│   │   │               │   ├── GrailsSymbolProvider.groovy       // Document/workspace symbols
│   │   │               │   └── GrailsConventionNavigator.groovy  // Grails convention-based navigation
│   │   │               │
│   │   │               ├── diagnostics/
│   │   │               │   ├── GrailsDiagnosticsProvider.groovy  // Error/warning detection
│   │   │               │   ├── GroovyDiagnosticsProvider.groovy  // Groovy syntax/semantic errors
│   │   │               │   └── GrailsConventionValidator.groovy  // Grails convention validation
│   │   │               │
│   │   │               ├── formatting/
│   │   │               │   ├── GrailsFormattingProvider.groovy   // Code formatting
│   │   │               │   └── GroovyFormattingProvider.groovy   // Groovy-specific formatting
│   │   │               │
│   │   │               ├── refactoring/
│   │   │               │   ├── GrailsRenameProvider.groovy       // Symbol renaming
│   │   │               │   └── GrailsCodeActionProvider.groovy   // Quick fixes and refactoring
│   │   │               │
│   │   │               ├── artifacts/
│   │   │               │   ├── ArtifactCreator.groovy            // Create Controllers, Services, Domains
│   │   │               │   ├── ArtifactDetector.groovy           // Detect artifact types from files
│   │   │               │   └── ArtifactTemplateManager.groovy    // Manage code templates
│   │   │               │
│   │   │               ├── gradle/
│   │   │               │   ├── GradleProjectAnalyzer.groovy      // Gradle Tooling API integration
│   │   │               │   ├── GradleDependencyExtractor.groovy  // Dependency extraction/caching
│   │   │               │   └── GradleTaskExecutor.groovy         // Execute Gradle tasks
│   │   │               │
│   │   │               ├── indexing/
│   │   │               │   ├── GrailsProjectIndexer.groovy       // Index project files for fast lookup
│   │   │               │   ├── SymbolIndex.groovy                // Symbol database for navigation
│   │   │               │   └── DependencyIndexer.groovy          // Index external dependencies
│   │   │               │
│   │   │               ├── utils/
│   │   │               │   ├── GrailsFileUtils.groovy            // File system utilities
│   │   │               │   ├── GrailsConventionUtils.groovy      // Convention-over-configuration helpers
│   │   │               │   ├── GroovyASTUtils.groovy             // Groovy AST analysis utilities
│   │   │               │   └── LSPUtils.groovy                   // LSP message utilities
│   │   │               │
│   │   │               └── cache/
│   │   │                   ├── ProjectCache.groovy               // Project information caching
│   │   │                   ├── DependencyCache.groovy            // Dependency resolution caching
│   │   │                   └── SymbolCache.groovy                // Symbol lookup caching
│   │   │
│   │   ├── java/                       // Java source files (if needed)
│   │   │   └── com/
│   │   │       └── grails/
│   │   │           └── languageserver/
│   │   │               └── model/
│   │   │                   ├── GrailsProject.java               // Project model (data classes)
│   │   │                   ├── DependencyNode.java              // Dependency representation
│   │   │                   ├── GrailsArtifact.java              // Artifact model
│   │   │                   └── SymbolInformation.java           // Symbol information model
│   │   │
│   │   └── resources/
│   │       ├── templates/              // Code generation templates
│   │       │   ├── controller.groovy.template
│   │       │   ├── service.groovy.template
│   │       │   ├── domain.groovy.template
│   │       │   └── taglib.groovy.template
│   │       │
│   │       ├── META-INF/
│   │       │   └── services/
│   │       │       └── org.eclipse.lsp4j.services.LanguageServer // Service loader registration
│   │       │
│   │       └── logging/
│   │           └── logback.xml         // Logging configuration
│   │
│   └── test/
│       ├── groovy/                     // Test source directory
│       │   └── com/
│       │       └── grails/
│       │           └── languageserver/
│       │               ├── GrailsLanguageServerTest.groovy
│       │               ├── project/
│       │               │   └── GrailsProjectManagerTest.groovy
│       │               ├── completion/
│       │               │   └── GrailsCompletionProviderTest.groovy
│       │               └── gradle/
│       │                   └── GradleProjectAnalyzerTest.groovy
│       │
│       └── resources/
│           └── test-projects/          // Sample Grails projects for testing
│               ├── simple-grails-app/
│               └── multi-module-grails-app/
│
├── build.gradle                        // Gradle build configuration
├── gradle.properties                   // Gradle properties
└── README.md                          // Server documentation
```

### Key Server Files Description

#### Core Server Files

- GrailsLanguageServer.groovy - Main LSP server implementation, implements LanguageServer interface
- GrailsLanguageServerLauncher.groovy - Entry point with main() method, handles CLI arguments and server startup

#### LSP Service Implementation

- GrailsTextDocumentService.groovy - Handles all text document operations (completion, definition, diagnostics)
- GrailsWorkspaceService.groovy - Handles workspace-level operations and custom commands
- GrailsLanguageClientService.groovy - Manages client communication and notifications

#### Custom Protocol Extensions

- GrailsProtocolExtensions.groovy - Defines custom LSP protocol extensions using @JsonRequest and @JsonNotification
- GrailsCustomCommands.groovy - Custom commands for artifact creation, project operations
- GrailsNotifications.groovy - Custom notifications for client communication

#### Project Management

- GrailsProjectManager.groovy - Central project management with caching strategy
- GrailsProjectBuilder.groovy - Uses Gradle Tooling API for project analysis (your current code)
- DependencyResolver.groovy - Handles dependency resolution and caching

#### Language Features

- GrailsCompletionProvider.groovy - Code completion specific to Grails conventions
- GrailsDefinitionProvider.groovy - Go-to-definition for Grails artifacts and conventions
- GrailsDiagnosticsProvider.groovy - Error detection and validation

#### Build Integration

- GradleProjectAnalyzer.groovy - Your existing Gradle Tooling API integration
- GradleDependencyExtractor.groovy - Dependency extraction with caching optimization
- GradleTaskExecutor.groovy - Execute Gradle tasks from language server

#### Performance Optimization

- ProjectCache.groovy - Cache project information to avoid repeated Gradle API calls
- DependencyCache.groovy - Cache dependency resolution results
- GrailsProjectIndexer.groovy - Index project symbols for fast lookup
