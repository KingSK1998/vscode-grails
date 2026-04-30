# Graph Report - .  (2026-04-22)

## Corpus Check
- Corpus is ~39,581 words - fits in a single context window. You may not need a graph.

## Summary
- 368 nodes · 591 edges · 51 communities detected
- Extraction: 83% EXTRACTED · 17% INFERRED · 0% AMBIGUOUS · INFERRED: 103 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Core Lifecycle Management|Core Lifecycle Management]]
- [[_COMMUNITY_Tree Explorer UI|Tree Explorer UI]]
- [[_COMMUNITY_Error Handling Services|Error Handling Services]]
- [[_COMMUNITY_Icon Theme Integration|Icon Theme Integration]]
- [[_COMMUNITY_Project Discovery Services|Project Discovery Services]]
- [[_COMMUNITY_Event Handling|Event Handling]]
- [[_COMMUNITY_Configuration Management|Configuration Management]]
- [[_COMMUNITY_Icon Provider|Icon Provider]]
- [[_COMMUNITY_Language Server Management|Language Server Management]]
- [[_COMMUNITY_Service Container|Service Container]]
- [[_COMMUNITY_Commands and Wizards|Commands and Wizards]]
- [[_COMMUNITY_Tree View Items|Tree View Items]]
- [[_COMMUNITY_File Utilities|File Utilities]]
- [[_COMMUNITY_CSS Helpers|CSS Helpers]]
- [[_COMMUNITY_Debug Configuration|Debug Configuration]]
- [[_COMMUNITY_Diagnostics UI|Diagnostics UI]]
- [[_COMMUNITY_Documentation|Documentation]]
- [[_COMMUNITY_Dashboard UI|Dashboard UI]]
- [[_COMMUNITY_Gradle Task Provider|Gradle Task Provider]]
- [[_COMMUNITY_Grails Conventions|Grails Conventions]]
- [[_COMMUNITY_Environment Indicator|Environment Indicator]]
- [[_COMMUNITY_Project Card UI|Project Card UI]]
- [[_COMMUNITY_Quick Actions UI|Quick Actions UI]]
- [[_COMMUNITY_GSP Template Viewer|GSP Template Viewer]]
- [[_COMMUNITY_Artifact Directories|Artifact Directories]]
- [[_COMMUNITY_Build Configuration|Build Configuration]]
- [[_COMMUNITY_Type Definitions|Type Definitions]]
- [[_COMMUNITY_Test Framework|Test Framework]]
- [[_COMMUNITY_Extension Constants|Extension Constants]]
- [[_COMMUNITY_Client Types|Client Types]]
- [[_COMMUNITY_Test Utilities|Test Utilities]]
- [[_COMMUNITY_Client Services|Client Services]]
- [[_COMMUNITY_Client Features|Client Features]]
- [[_COMMUNITY_Core Types|Core Types]]
- [[_COMMUNITY_Client Utils|Client Utils]]
- [[_COMMUNITY_Client Models|Client Models]]
- [[_COMMUNITY_Client Scripts|Client Scripts]]
- [[_COMMUNITY_Build Scripts|Build Scripts]]
- [[_COMMUNITY_Utility Scripts|Utility Scripts]]
- [[_COMMUNITY_TypeScript Config|TypeScript Config]]
- [[_COMMUNITY_LSP4J Library|LSP4J Library]]
- [[_COMMUNITY_Server API|Server API]]
- [[_COMMUNITY_Server Main|Server Main]]
- [[_COMMUNITY_Service Layer|Service Layer]]
- [[_COMMUNITY_Workspace Service|Workspace Service]]
- [[_COMMUNITY_Compiler Core|Compiler Core]]
- [[_COMMUNITY_AST Visitor|AST Visitor]]
- [[_COMMUNITY_Project Structure|Project Structure]]
- [[_COMMUNITY_Extension Entry|Extension Entry]]
- [[_COMMUNITY_Brand Assets|Brand Assets]]
- [[_COMMUNITY_Extension Icons|Extension Icons]]

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
- `Client Component (TypeScript)` --is_part_of--> `VS Code Grails Extension`  [EXTRACTED]
  CLAUDE.md → README.md
- `Server Component (Groovy LSP)` --is_part_of--> `VS Code Grails Extension`  [EXTRACTED]
  CLAUDE.md → README.md

## Communities

### Community 0 - "Core Lifecycle Management"
Cohesion: 0.08
Nodes (4): ActivationManager, copyServer(), StatusBarService, createProjectTreeProvider()

### Community 1 - "Tree Explorer UI"
Cohesion: 0.11
Nodes (1): GrailsTreeExplorer

### Community 2 - "Error Handling Services"
Cohesion: 0.11
Nodes (2): ErrorService, GradleService

### Community 3 - "Icon Theme Integration"
Cohesion: 0.12
Nodes (4): main(), activate(), deactivate(), IconThemeDetector

### Community 4 - "Project Discovery Services"
Cohesion: 0.13
Nodes (1): ProjectService

### Community 5 - "Event Handling"
Cohesion: 0.1
Nodes (3): getClientOptions(), EventBus, OutputChannelService

### Community 6 - "Configuration Management"
Cohesion: 0.17
Nodes (1): ConfigurationService

### Community 7 - "Icon Provider"
Cohesion: 0.2
Nodes (1): IconProvider

### Community 8 - "Language Server Management"
Cohesion: 0.18
Nodes (1): LanguageServerManager

### Community 9 - "Service Container"
Cohesion: 0.14
Nodes (1): ServiceContainer

### Community 10 - "Commands and Wizards"
Cohesion: 0.27
Nodes (1): Commands

### Community 11 - "Tree View Items"
Cohesion: 0.33
Nodes (1): GrailsTreeItem

### Community 12 - "File Utilities"
Cohesion: 0.24
Nodes (2): FileScannerUtils, ProjectFolderUtils

### Community 13 - "CSS Helpers"
Cohesion: 0.32
Nodes (2): CSSHelper, GrailsArtifactWizard

### Community 14 - "Debug Configuration"
Cohesion: 0.48
Nodes (5): getDebugConfiguration(), getJavaDebugArgs(), getLocalServerOptions(), getServerJarPath(), getServerOptions()

### Community 15 - "Diagnostics UI"
Cohesion: 0.4
Nodes (1): DiagnosticDecorationProvider

### Community 16 - "Documentation"
Cohesion: 0.4
Nodes (4): Client Component (TypeScript), Root README.md, Server Component (Groovy LSP), VS Code Grails Extension

### Community 17 - "Dashboard UI"
Cohesion: 0.67
Nodes (1): GrailsDashboard

### Community 18 - "Gradle Task Provider"
Cohesion: 0.67
Nodes (2): Api, GradleTaskProvider

### Community 19 - "Grails Conventions"
Cohesion: 0.67
Nodes (1): GrailsConventions

### Community 20 - "Environment Indicator"
Cohesion: 0.67
Nodes (1): GrailsEnvironmentIndicator

### Community 21 - "Project Card UI"
Cohesion: 0.67
Nodes (1): GrailsProjectCard

### Community 22 - "Quick Actions UI"
Cohesion: 0.67
Nodes (1): GrailsQuickActions

### Community 23 - "GSP Template Viewer"
Cohesion: 0.67
Nodes (1): GSPTemplateViewer

### Community 24 - "Artifact Directories"
Cohesion: 1.0
Nodes (0): 

### Community 25 - "Build Configuration"
Cohesion: 1.0
Nodes (0): 

### Community 26 - "Type Definitions"
Cohesion: 1.0
Nodes (0): 

### Community 27 - "Test Framework"
Cohesion: 1.0
Nodes (0): 

### Community 28 - "Extension Constants"
Cohesion: 1.0
Nodes (0): 

### Community 29 - "Client Types"
Cohesion: 1.0
Nodes (0): 

### Community 30 - "Test Utilities"
Cohesion: 1.0
Nodes (0): 

### Community 31 - "Client Services"
Cohesion: 1.0
Nodes (0): 

### Community 32 - "Client Features"
Cohesion: 1.0
Nodes (0): 

### Community 33 - "Core Types"
Cohesion: 1.0
Nodes (0): 

### Community 34 - "Client Utils"
Cohesion: 1.0
Nodes (0): 

### Community 35 - "Client Models"
Cohesion: 1.0
Nodes (0): 

### Community 36 - "Client Scripts"
Cohesion: 1.0
Nodes (0): 

### Community 37 - "Build Scripts"
Cohesion: 1.0
Nodes (0): 

### Community 38 - "Utility Scripts"
Cohesion: 1.0
Nodes (0): 

### Community 39 - "TypeScript Config"
Cohesion: 1.0
Nodes (0): 

### Community 40 - "LSP4J Library"
Cohesion: 1.0
Nodes (1): Eclipse LSP4J

### Community 41 - "Server API"
Cohesion: 1.0
Nodes (1): Gradle Tooling API

### Community 42 - "Server Main"
Cohesion: 1.0
Nodes (1): Grails Language Server

### Community 43 - "Service Layer"
Cohesion: 1.0
Nodes (1): GrailsTextDocumentService

### Community 44 - "Workspace Service"
Cohesion: 1.0
Nodes (1): GrailsWorkspaceService

### Community 45 - "Compiler Core"
Cohesion: 1.0
Nodes (1): GrailsCompiler

### Community 46 - "AST Visitor"
Cohesion: 1.0
Nodes (1): GrailsASTVisitor

### Community 47 - "Project Structure"
Cohesion: 1.0
Nodes (1): Monorepo Structure

### Community 48 - "Extension Entry"
Cohesion: 1.0
Nodes (1): Grails Framework Extension Icon

### Community 49 - "Brand Assets"
Cohesion: 1.0
Nodes (1): Grails Framework Logo

### Community 50 - "Extension Icons"
Cohesion: 1.0
Nodes (1): Grails Activity Bar Icon

## Knowledge Gaps
- **16 isolated node(s):** `Api`, `GradleTaskProvider`, `Root README.md`, `Client Component (TypeScript)`, `Server Component (Groovy LSP)` (+11 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **Thin community `Artifact Directories`** (2 nodes): `modelTypes.ts`, `getArtifactDirectory()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Build Configuration`** (1 nodes): `eslint.config.mjs`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Type Definitions`** (1 nodes): `ServiceRegistry.ts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Test Framework`** (1 nodes): `eventTypes.ts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Extension Constants`** (1 nodes): `errorTypes.ts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Client Types`** (1 nodes): `languageServerTypes.ts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Test Utilities`** (1 nodes): `statusBarTypes.ts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Client Services`** (1 nodes): `extension.test.ts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Client Features`** (1 nodes): `TreeItemKind.ts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Core Types`** (1 nodes): `constants.ts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Client Utils`** (1 nodes): `grails-themes.ts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Client Models`** (1 nodes): `GrailsIcons.ts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Client Scripts`** (1 nodes): `run-gradlew.js`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Build Scripts`** (1 nodes): `application.js`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Utility Scripts`** (1 nodes): `application.js`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `TypeScript Config`** (1 nodes): `grails-types.ts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `LSP4J Library`** (1 nodes): `Eclipse LSP4J`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Server API`** (1 nodes): `Gradle Tooling API`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Server Main`** (1 nodes): `Grails Language Server`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Service Layer`** (1 nodes): `GrailsTextDocumentService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Workspace Service`** (1 nodes): `GrailsWorkspaceService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Compiler Core`** (1 nodes): `GrailsCompiler`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `AST Visitor`** (1 nodes): `GrailsASTVisitor`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Project Structure`** (1 nodes): `Monorepo Structure`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Extension Entry`** (1 nodes): `Grails Framework Extension Icon`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Brand Assets`** (1 nodes): `Grails Framework Logo`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Extension Icons`** (1 nodes): `Grails Activity Bar Icon`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `GrailsTreeExplorer` connect `Tree Explorer UI` to `Icon Theme Integration`?**
  _High betweenness centrality (0.152) - this node is a cross-community bridge._
- **Why does `IconThemeDetector` connect `Icon Theme Integration` to `Core Lifecycle Management`?**
  _High betweenness centrality (0.098) - this node is a cross-community bridge._
- **Why does `ProjectService` connect `Project Discovery Services` to `Core Lifecycle Management`?**
  _High betweenness centrality (0.098) - this node is a cross-community bridge._
- **What connects `Api`, `GradleTaskProvider`, `Root README.md` to the rest of the system?**
  _16 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Core Lifecycle Management` be split into smaller, more focused modules?**
  _Cohesion score 0.08 - nodes in this community are weakly interconnected._
- **Should `Tree Explorer UI` be split into smaller, more focused modules?**
  _Cohesion score 0.11 - nodes in this community are weakly interconnected._
- **Should `Error Handling Services` be split into smaller, more focused modules?**
  _Cohesion score 0.11 - nodes in this community are weakly interconnected._