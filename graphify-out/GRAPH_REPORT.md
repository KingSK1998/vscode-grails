# Graph Report - d:\Grails_Framework_Support_Extension\vscode-grails (2026-04-30)

## Corpus Check

- 92 files · ~69,350 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary

- 411 nodes · 634 edges · 55 communities detected
- Extraction: 82% EXTRACTED · 18% INFERRED · 0% AMBIGUOUS · INFERRED: 116 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)

- [[_COMMUNITY_Grails Tree Explorer|Grails Tree Explorer]]
- [[_COMMUNITY_Core Activation Manager|Core Activation Manager]]
- [[_COMMUNITY_LSP & Error Services|LSP & Error Services]]
- [[_COMMUNITY_Icon Theme Detector|Icon Theme Detector]]
- [[_COMMUNITY_Project Discovery Service|Project Discovery Service]]
- [[_COMMUNITY_Configuration Service|Configuration Service]]
- [[_COMMUNITY_Extension Architecture Maps|Extension Architecture Maps]]
- [[_COMMUNITY_Gradle Integration Service|Gradle Integration Service]]
- [[_COMMUNITY_Icon Resolution Provider|Icon Resolution Provider]]
- [[_COMMUNITY_Status Bar Service|Status Bar Service]]
- [[_COMMUNITY_Service Container DI|Service Container DI]]
- [[_COMMUNITY_Command Registration|Command Registration]]
- [[_COMMUNITY_LSP Feature Providers|LSP Feature Providers]]
- [[_COMMUNITY_Output Channels|Output Channels]]
- [[_COMMUNITY_Grails Tree Items|Grails Tree Items]]
- [[_COMMUNITY_File Scanning Utils|File Scanning Utils]]
- [[_COMMUNITY_Event Bus|Event Bus]]
- [[_COMMUNITY_Webview CSS & Wizard|Webview CSS & Wizard]]
- [[_COMMUNITY_Server Debug Config|Server Debug Config]]
- [[_COMMUNITY_Diagnostic Decorator|Diagnostic Decorator]]
- [[_COMMUNITY_Visual Extension Icons|Visual Extension Icons]]
- [[_COMMUNITY_Grails Dashboard View|Grails Dashboard View]]
- [[_COMMUNITY_Compiler Optimization Skills|Compiler Optimization Skills]]
- [[_COMMUNITY_Testing Architecture|Testing Architecture]]
- [[_COMMUNITY_Gradle Task Types|Gradle Task Types]]
- [[_COMMUNITY_Naming Conventions View|Naming Conventions View]]
- [[_COMMUNITY_Environment Indicator|Environment Indicator]]
- [[_COMMUNITY_Project Card View|Project Card View]]
- [[_COMMUNITY_Quick Actions View|Quick Actions View]]
- [[_COMMUNITY_GSP Template Viewer|GSP Template Viewer]]
- [[_COMMUNITY_Model Artifact Utils|Model Artifact Utils]]
- [[_COMMUNITY_Testing Skills|Testing Skills]]
- [[_COMMUNITY_LSP Execution Scope|LSP Execution Scope]]
- [[_COMMUNITY_Test App Assets|Test App Assets]]
- [[_COMMUNITY_ESLint Config|ESLint Config]]
- [[_COMMUNITY_Service Registry|Service Registry]]
- [[_COMMUNITY_Event Types|Event Types]]
- [[_COMMUNITY_Error Types|Error Types]]
- [[_COMMUNITY_LSP Types|LSP Types]]
- [[_COMMUNITY_Status Bar Types|Status Bar Types]]
- [[_COMMUNITY_Client Test Entry|Client Test Entry]]
- [[_COMMUNITY_Tree Item Kinds|Tree Item Kinds]]
- [[_COMMUNITY_Extension Constants|Extension Constants]]
- [[_COMMUNITY_Grails Themes|Grails Themes]]
- [[_COMMUNITY_Grails Icons Types|Grails Icons Types]]
- [[_COMMUNITY_Run Gradle Script|Run Gradle Script]]
- [[_COMMUNITY_Test JS Assets|Test JS Assets]]
- [[_COMMUNITY_Test JS Assets Src|Test JS Assets Src]]
- [[_COMMUNITY_Shared Grails Types|Shared Grails Types]]
- [[_COMMUNITY_Auto Tool Skill|Auto Tool Skill]]
- [[_COMMUNITY_User Guide Docs|User Guide Docs]]
- [[_COMMUNITY_LSP Reference Provider|LSP Reference Provider]]
- [[_COMMUNITY_Server Build Status|Server Build Status]]
- [[_COMMUNITY_Slack SVG Asset|Slack SVG Asset]]
- [[_COMMUNITY_Apple Touch Icon|Apple Touch Icon]]

## God Nodes (most connected - your core abstractions)

1. `GrailsTreeExplorer` - 40 edges
2. `ProjectService` - 23 edges
3. `StatusBarService` - 22 edges
4. `ConfigurationService` - 20 edges
5. `ActivationManager` - 19 edges
6. `IconProvider` - 18 edges
7. `IconThemeDetector` - 18 edges
8. `GradleService` - 16 edges
9. `Commands` - 15 edges
10. `ServiceContainer` - 14 edges

## Surprising Connections (you probably didn't know these)

- `Read Tests Before Change Skill` --semantically_similar_to--> `Comprehensive Test Cases Skill` [INFERRED] [semantically similar]
  read-tests-before-change/SKILL.md → comprehensive-test-cases/SKILL.md
- `Grails SVG Test Asset` --semantically_similar_to--> `Grails SVG Brand Icon` [INFERRED] [semantically similar]
  server/src/test/resources/test-projects/grails-test-project/grails-app/assets/images/grails.svg → resources/icons/grails.svg
- `Architecture for Performance Skill` --conceptually_related_to--> `GrailsCompiler` [INFERRED]
  architecture-for-performance/SKILL.md → CLAUDE.md
- `Extension Settings grailsLsp` --references--> `Client TypeScript Extension` [EXTRACTED]
  README.md → CLAUDE.md
- `Client API Reference` --references--> `Client TypeScript Extension` [EXTRACTED]
  docs/Client_API.md → CLAUDE.md

## Hyperedges (group relationships)

- **LSP Client-Server Communication Bridge** — claude_client_ts, claude_vscode_lsp_client, claude_lsp4j, claude_server_groovy [EXTRACTED 0.95]
- **Core Compilation Pipeline** — claude_grails_compiler, claude_ast_visitor, claude_gradle_api, claude_incremental_compile [EXTRACTED 0.90]
- **Modular LSP Provider System** — server_api_base_provider, server_api_completion_provider, server_api_hover_provider, server_api_definition_provider, server_api_diagnostics_provider, server_api_codelens_provider [EXTRACTED 0.95]
- **Test Infrastructure** — server_testing_spock, server_testing_base_lsp_spec, server_testing_project_types, server_status_jacoco_60pct [EXTRACTED 0.90]

## Communities

### Community 0 - "Grails Tree Explorer"

Cohesion: 0.12
Nodes (1): GrailsTreeExplorer

### Community 1 - "Core Activation Manager"

Cohesion: 0.09
Nodes (3): ActivationManager, copyServer(), createProjectTreeProvider()

### Community 2 - "LSP & Error Services"

Cohesion: 0.11
Nodes (2): ErrorService, LanguageServerManager

### Community 3 - "Icon Theme Detector"

Cohesion: 0.12
Nodes (4): main(), activate(), deactivate(), IconThemeDetector

### Community 4 - "Project Discovery Service"

Cohesion: 0.13
Nodes (1): ProjectService

### Community 5 - "Configuration Service"

Cohesion: 0.18
Nodes (1): ConfigurationService

### Community 6 - "Extension Architecture Maps"

Cohesion: 0.11
Nodes (20): GrailsASTVisitor, Client TypeScript Extension, esbuild Bundler, Gradle Tooling API 8.12, GradleService, GrailsCompiler, Incremental Compilation, LanguageServerManager (+12 more)

### Community 7 - "Gradle Integration Service"

Cohesion: 0.17
Nodes (1): GradleService

### Community 8 - "Icon Resolution Provider"

Cohesion: 0.2
Nodes (1): IconProvider

### Community 9 - "Status Bar Service"

Cohesion: 0.2
Nodes (1): StatusBarService

### Community 10 - "Service Container DI"

Cohesion: 0.14
Nodes (1): ServiceContainer

### Community 11 - "Command Registration"

Cohesion: 0.27
Nodes (1): Commands

### Community 12 - "LSP Feature Providers"

Cohesion: 0.15
Nodes (14): BaseProvider, CodeLensProvider, CompletionProvider, DefinitionProvider, DiagnosticsProvider, GrailsLanguageServer, GrailsService, HoverProvider (+6 more)

### Community 13 - "Output Channels"

Cohesion: 0.18
Nodes (2): getClientOptions(), OutputChannelService

### Community 14 - "Grails Tree Items"

Cohesion: 0.33
Nodes (1): GrailsTreeItem

### Community 15 - "File Scanning Utils"

Cohesion: 0.24
Nodes (2): FileScannerUtils, ProjectFolderUtils

### Community 16 - "Event Bus"

Cohesion: 0.25
Nodes (1): EventBus

### Community 17 - "Webview CSS & Wizard"

Cohesion: 0.32
Nodes (2): CSSHelper, GrailsArtifactWizard

### Community 18 - "Server Debug Config"

Cohesion: 0.48
Nodes (5): getDebugConfiguration(), getJavaDebugArgs(), getLocalServerOptions(), getServerJarPath(), getServerOptions()

### Community 19 - "Diagnostic Decorator"

Cohesion: 0.4
Nodes (1): DiagnosticDecorationProvider

### Community 20 - "Visual Extension Icons"

Cohesion: 0.4
Nodes (5): VS Code Extension Icon PNG, Grails Logo SVG Icon, Grails SVG Brand Icon, Grails Cupsonly Logo White SVG, Grails SVG Test Asset

### Community 21 - "Grails Dashboard View"

Cohesion: 0.67
Nodes (1): GrailsDashboard

### Community 22 - "Compiler Optimization Skills"

Cohesion: 0.5
Nodes (4): Full Compilation 2-5 Seconds, Compiler Incremental 50-200ms, Performance Optimization Skill, Profile Before Optimize Principle

### Community 23 - "Testing Architecture"

Cohesion: 0.5
Nodes (4): JaCoCo 60% Coverage Threshold, BaseLspSpec Base Test Class, ProjectType DUMMY GROOVY GRAILS, Spock 2.3 Test Framework

### Community 24 - "Gradle Task Types"

Cohesion: 0.67
Nodes (2): Api, GradleTaskProvider

### Community 25 - "Naming Conventions View"

Cohesion: 0.67
Nodes (1): GrailsConventions

### Community 26 - "Environment Indicator"

Cohesion: 0.67
Nodes (1): GrailsEnvironmentIndicator

### Community 27 - "Project Card View"

Cohesion: 0.67
Nodes (1): GrailsProjectCard

### Community 28 - "Quick Actions View"

Cohesion: 0.67
Nodes (1): GrailsQuickActions

### Community 29 - "GSP Template Viewer"

Cohesion: 0.67
Nodes (1): GSPTemplateViewer

### Community 30 - "Model Artifact Utils"

Cohesion: 1.0
Nodes (0):

### Community 31 - "Testing Skills"

Cohesion: 1.0
Nodes (2): Comprehensive Test Cases Skill, Read Tests Before Change Skill

### Community 32 - "LSP Execution Scope"

Cohesion: 1.0
Nodes (2): AST Node Level Execution Scope, Workspace Level Caching Strategy

### Community 33 - "Test App Assets"

Cohesion: 1.0
Nodes (2): Advanced Grails SVG Asset, Documentation SVG Asset

### Community 34 - "ESLint Config"

Cohesion: 1.0
Nodes (0):

### Community 35 - "Service Registry"

Cohesion: 1.0
Nodes (0):

### Community 36 - "Event Types"

Cohesion: 1.0
Nodes (0):

### Community 37 - "Error Types"

Cohesion: 1.0
Nodes (0):

### Community 38 - "LSP Types"

Cohesion: 1.0
Nodes (0):

### Community 39 - "Status Bar Types"

Cohesion: 1.0
Nodes (0):

### Community 40 - "Client Test Entry"

Cohesion: 1.0
Nodes (0):

### Community 41 - "Tree Item Kinds"

Cohesion: 1.0
Nodes (0):

### Community 42 - "Extension Constants"

Cohesion: 1.0
Nodes (0):

### Community 43 - "Grails Themes"

Cohesion: 1.0
Nodes (0):

### Community 44 - "Grails Icons Types"

Cohesion: 1.0
Nodes (0):

### Community 45 - "Run Gradle Script"

Cohesion: 1.0
Nodes (0):

### Community 46 - "Test JS Assets"

Cohesion: 1.0
Nodes (0):

### Community 47 - "Test JS Assets Src"

Cohesion: 1.0
Nodes (0):

### Community 48 - "Shared Grails Types"

Cohesion: 1.0
Nodes (0):

### Community 49 - "Auto Tool Skill"

Cohesion: 1.0
Nodes (1): Auto Tool Selection Skill

### Community 50 - "User Guide Docs"

Cohesion: 1.0
Nodes (1): User Guide

### Community 51 - "LSP Reference Provider"

Cohesion: 1.0
Nodes (1): ReferencesProvider

### Community 52 - "Server Build Status"

Cohesion: 1.0
Nodes (1): Server Build Status - Passing

### Community 53 - "Slack SVG Asset"

Cohesion: 1.0
Nodes (1): Slack SVG Community Link

### Community 54 - "Apple Touch Icon"

Cohesion: 1.0
Nodes (1): Apple Touch Icon PNG

## Knowledge Gaps

- **46 isolated node(s):** `Api`, `GradleTaskProvider`, `LanguageServerManager`, `GradleService`, `LSP4J 0.23.1` (+41 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **Thin community `Model Artifact Utils`** (2 nodes): `modelTypes.ts`, `getArtifactDirectory()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Testing Skills`** (2 nodes): `Comprehensive Test Cases Skill`, `Read Tests Before Change Skill`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `LSP Execution Scope`** (2 nodes): `AST Node Level Execution Scope`, `Workspace Level Caching Strategy`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Test App Assets`** (2 nodes): `Advanced Grails SVG Asset`, `Documentation SVG Asset`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `ESLint Config`** (1 nodes): `eslint.config.mjs`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Service Registry`** (1 nodes): `ServiceRegistry.ts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Event Types`** (1 nodes): `eventTypes.ts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Error Types`** (1 nodes): `errorTypes.ts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `LSP Types`** (1 nodes): `languageServerTypes.ts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Status Bar Types`** (1 nodes): `statusBarTypes.ts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Client Test Entry`** (1 nodes): `extension.test.ts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Tree Item Kinds`** (1 nodes): `TreeItemKind.ts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Extension Constants`** (1 nodes): `constants.ts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Grails Themes`** (1 nodes): `grails-themes.ts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Grails Icons Types`** (1 nodes): `GrailsIcons.ts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Run Gradle Script`** (1 nodes): `run-gradlew.js`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Test JS Assets`** (1 nodes): `application.js`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Test JS Assets Src`** (1 nodes): `application.js`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Shared Grails Types`** (1 nodes): `grails-types.ts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Auto Tool Skill`** (1 nodes): `Auto Tool Selection Skill`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `User Guide Docs`** (1 nodes): `User Guide`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `LSP Reference Provider`** (1 nodes): `ReferencesProvider`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Server Build Status`** (1 nodes): `Server Build Status - Passing`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Slack SVG Asset`** (1 nodes): `Slack SVG Community Link`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Apple Touch Icon`** (1 nodes): `Apple Touch Icon PNG`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.

## Suggested Questions

_Questions this graph is uniquely positioned to answer:_

- **Why does `GrailsTreeExplorer` connect `Grails Tree Explorer` to `Core Activation Manager`, `Icon Theme Detector`?**
  _High betweenness centrality (0.122) - this node is a cross-community bridge._
- **Why does `IconThemeDetector` connect `Icon Theme Detector` to `Core Activation Manager`?**
  _High betweenness centrality (0.078) - this node is a cross-community bridge._
- **Why does `ProjectService` connect `Project Discovery Service` to `Core Activation Manager`?**
  _High betweenness centrality (0.078) - this node is a cross-community bridge._
- **What connects `Api`, `GradleTaskProvider`, `LanguageServerManager` to the rest of the system?**
  _46 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Grails Tree Explorer` be split into smaller, more focused modules?**
  _Cohesion score 0.12 - nodes in this community are weakly interconnected._
- **Should `Core Activation Manager` be split into smaller, more focused modules?**
  _Cohesion score 0.09 - nodes in this community are weakly interconnected._
- **Should `LSP & Error Services` be split into smaller, more focused modules?**
  _Cohesion score 0.11 - nodes in this community are weakly interconnected._
