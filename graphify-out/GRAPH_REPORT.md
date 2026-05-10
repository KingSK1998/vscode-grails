# Graph Report - D:\Grails_Framework_Support_Extension\vscode-gng-support  (2026-05-11)

## Corpus Check
- 74 files · ~105,021 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 538 nodes · 853 edges · 64 communities detected
- Extraction: 77% EXTRACTED · 23% INFERRED · 0% AMBIGUOUS · INFERRED: 198 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Community 0|Community 0]]
- [[_COMMUNITY_Community 1|Community 1]]
- [[_COMMUNITY_Community 2|Community 2]]
- [[_COMMUNITY_Community 3|Community 3]]
- [[_COMMUNITY_Community 4|Community 4]]
- [[_COMMUNITY_Community 5|Community 5]]
- [[_COMMUNITY_Community 6|Community 6]]
- [[_COMMUNITY_Community 7|Community 7]]
- [[_COMMUNITY_Community 8|Community 8]]
- [[_COMMUNITY_Community 9|Community 9]]
- [[_COMMUNITY_Community 10|Community 10]]
- [[_COMMUNITY_Community 11|Community 11]]
- [[_COMMUNITY_Community 12|Community 12]]
- [[_COMMUNITY_Community 13|Community 13]]
- [[_COMMUNITY_Community 14|Community 14]]
- [[_COMMUNITY_Community 15|Community 15]]
- [[_COMMUNITY_Community 16|Community 16]]
- [[_COMMUNITY_Community 17|Community 17]]
- [[_COMMUNITY_Community 18|Community 18]]
- [[_COMMUNITY_Community 19|Community 19]]
- [[_COMMUNITY_Community 20|Community 20]]
- [[_COMMUNITY_Community 21|Community 21]]
- [[_COMMUNITY_Community 22|Community 22]]
- [[_COMMUNITY_Community 23|Community 23]]
- [[_COMMUNITY_Community 24|Community 24]]
- [[_COMMUNITY_Community 25|Community 25]]
- [[_COMMUNITY_Community 26|Community 26]]
- [[_COMMUNITY_Community 27|Community 27]]
- [[_COMMUNITY_Community 28|Community 28]]
- [[_COMMUNITY_Community 29|Community 29]]
- [[_COMMUNITY_Community 30|Community 30]]
- [[_COMMUNITY_Community 31|Community 31]]
- [[_COMMUNITY_Community 32|Community 32]]
- [[_COMMUNITY_Community 33|Community 33]]
- [[_COMMUNITY_Community 34|Community 34]]
- [[_COMMUNITY_Community 35|Community 35]]
- [[_COMMUNITY_Community 36|Community 36]]
- [[_COMMUNITY_Community 37|Community 37]]
- [[_COMMUNITY_Community 38|Community 38]]
- [[_COMMUNITY_Community 39|Community 39]]
- [[_COMMUNITY_Community 40|Community 40]]
- [[_COMMUNITY_Community 41|Community 41]]
- [[_COMMUNITY_Community 42|Community 42]]
- [[_COMMUNITY_Community 43|Community 43]]
- [[_COMMUNITY_Community 44|Community 44]]
- [[_COMMUNITY_Community 45|Community 45]]
- [[_COMMUNITY_Community 46|Community 46]]
- [[_COMMUNITY_Community 47|Community 47]]
- [[_COMMUNITY_Community 48|Community 48]]
- [[_COMMUNITY_Community 49|Community 49]]
- [[_COMMUNITY_Community 50|Community 50]]
- [[_COMMUNITY_Community 51|Community 51]]
- [[_COMMUNITY_Community 52|Community 52]]
- [[_COMMUNITY_Community 53|Community 53]]
- [[_COMMUNITY_Community 54|Community 54]]
- [[_COMMUNITY_Community 55|Community 55]]
- [[_COMMUNITY_Community 56|Community 56]]
- [[_COMMUNITY_Community 57|Community 57]]
- [[_COMMUNITY_Community 58|Community 58]]
- [[_COMMUNITY_Community 59|Community 59]]
- [[_COMMUNITY_Community 60|Community 60]]
- [[_COMMUNITY_Community 61|Community 61]]
- [[_COMMUNITY_Community 62|Community 62]]
- [[_COMMUNITY_Community 63|Community 63]]

## God Nodes (most connected - your core abstractions)
1. `GrailsTreeExplorer` - 40 edges
2. `log()` - 33 edges
3. `ProjectService` - 22 edges
4. `StatusBarService` - 22 edges
5. `ServiceContainer` - 21 edges
6. `ConfigurationService` - 20 edges
7. `ActivationManager` - 19 edges
8. `IconProvider` - 18 edges
9. `IconThemeDetector` - 18 edges
10. `GradleService` - 16 edges

## Surprising Connections (you probably didn't know these)
- `connectToRemoteServer()` --calls--> `log()`  [INFERRED]
  client\src\services\languageServer\serverConfig.ts → D:\Grails_Framework_Support_Extension\vscode-gng-support\graphify-out\graphify_update.py
- `Comprehensive Test Cases Skill` --semantically_similar_to--> `Read Tests Before Change Skill`  [INFERRED] [semantically similar]
  comprehensive-test-cases/SKILL.md → read-tests-before-change/SKILL.md
- `Grails SVG Brand Icon` --semantically_similar_to--> `Grails SVG Test Asset`  [INFERRED] [semantically similar]
  resources/icons/grails.svg → server/src/test/resources/test-projects/grails-test-project/grails-app/assets/images/grails.svg
- `deactivate()` --calls--> `log()`  [INFERRED]
  D:\Grails_Framework_Support_Extension\vscode-gng-support\client\src\extension.ts → D:\Grails_Framework_Support_Extension\vscode-gng-support\graphify-out\graphify_update.py
- `getServerJarPath()` --calls--> `log()`  [INFERRED]
  client\src\services\languageServer\serverConfig.ts → D:\Grails_Framework_Support_Extension\vscode-gng-support\graphify-out\graphify_update.py

## Hyperedges (group relationships)
- **LSP Client-Server Communication Bridge** — claude_client_ts, claude_vscode_lsp_client, claude_lsp4j, claude_server_groovy [EXTRACTED 0.95]
- **Core Compilation Pipeline** — claude_grails_compiler, claude_ast_visitor, claude_gradle_api, claude_incremental_compile [EXTRACTED 0.90]
- **Modular LSP Provider System** — server_api_base_provider, server_api_completion_provider, server_api_hover_provider, server_api_definition_provider, server_api_diagnostics_provider, server_api_codelens_provider [EXTRACTED 0.95]
- **Test Infrastructure** — server_testing_spock, server_testing_base_lsp_spec, server_testing_project_types, server_status_jacoco_60pct [EXTRACTED 0.90]

## Communities

### Community 0 - "Community 0"
Cohesion: 0.07
Nodes (9): ActivationManager, getClientOptions(), copyServer(), ErrorService, activate(), log(), run_update(), LanguageServerManager (+1 more)

### Community 1 - "Community 1"
Cohesion: 0.12
Nodes (1): GrailsTreeExplorer

### Community 2 - "Community 2"
Cohesion: 0.06
Nodes (9): ArtifactCommands, register(), GrailsTaskCommands, GrailsTestService, LegacyCommands, LogCommands, NavigationCommands, ProjectCommands (+1 more)

### Community 3 - "Community 3"
Cohesion: 0.11
Nodes (3): ProjectMapper, ProjectService, ProjectStore

### Community 4 - "Community 4"
Cohesion: 0.11
Nodes (2): ConfigurationService, GrailsGutterProvider

### Community 5 - "Community 5"
Cohesion: 0.12
Nodes (2): GrailsTreeItem, IconProvider

### Community 6 - "Community 6"
Cohesion: 0.08
Nodes (2): ExtensionCommands, ServiceContainer

### Community 7 - "Community 7"
Cohesion: 0.11
Nodes (3): DebugService, GradleService, RunGrailsAppUseCase

### Community 8 - "Community 8"
Cohesion: 0.11
Nodes (20): GrailsASTVisitor, Client TypeScript Extension, esbuild Bundler, Gradle Tooling API 8.12, GradleService, GrailsCompiler, Incremental Compilation, LanguageServerManager (+12 more)

### Community 9 - "Community 9"
Cohesion: 0.18
Nodes (1): IconThemeDetector

### Community 10 - "Community 10"
Cohesion: 0.2
Nodes (1): StatusBarService

### Community 11 - "Community 11"
Cohesion: 0.3
Nodes (1): ArtifactService

### Community 12 - "Community 12"
Cohesion: 0.15
Nodes (14): BaseProvider, CodeLensProvider, CompletionProvider, DefinitionProvider, DiagnosticsProvider, GrailsLanguageServer, GrailsService, HoverProvider (+6 more)

### Community 13 - "Community 13"
Cohesion: 0.25
Nodes (1): EventBus

### Community 14 - "Community 14"
Cohesion: 0.39
Nodes (1): GspCompletionProvider

### Community 15 - "Community 15"
Cohesion: 0.25
Nodes (2): FileScannerUtils, ProjectFolderUtils

### Community 16 - "Community 16"
Cohesion: 0.32
Nodes (2): CSSHelper, GrailsArtifactWizard

### Community 17 - "Community 17"
Cohesion: 0.43
Nodes (1): DashboardService

### Community 18 - "Community 18"
Cohesion: 0.48
Nodes (6): connectToRemoteServer(), getDebugConfiguration(), getJavaDebugArgs(), getLocalServerOptions(), getServerJarPath(), getServerOptions()

### Community 19 - "Community 19"
Cohesion: 0.29
Nodes (1): OutputChannelService

### Community 20 - "Community 20"
Cohesion: 0.47
Nodes (1): DependencyGraphService

### Community 21 - "Community 21"
Cohesion: 0.47
Nodes (1): GormSqlPreviewService

### Community 22 - "Community 22"
Cohesion: 0.4
Nodes (1): LogStreamingService

### Community 23 - "Community 23"
Cohesion: 0.53
Nodes (1): GrailsCodeActionProvider

### Community 24 - "Community 24"
Cohesion: 0.4
Nodes (2): main(), deactivate()

### Community 25 - "Community 25"
Cohesion: 0.4
Nodes (1): Commands

### Community 26 - "Community 26"
Cohesion: 0.4
Nodes (1): DiagnosticDecorationProvider

### Community 27 - "Community 27"
Cohesion: 0.4
Nodes (5): VS Code Extension Icon PNG, Grails Logo SVG Icon, Grails SVG Brand Icon, Grails Cupsonly Logo White SVG, Grails SVG Test Asset

### Community 28 - "Community 28"
Cohesion: 0.67
Nodes (1): GrailsCodeLensProvider

### Community 29 - "Community 29"
Cohesion: 0.67
Nodes (1): GrailsDashboard

### Community 30 - "Community 30"
Cohesion: 0.5
Nodes (4): Full Compilation 2-5 Seconds, Compiler Incremental 50-200ms, Performance Optimization Skill, Profile Before Optimize Principle

### Community 31 - "Community 31"
Cohesion: 0.5
Nodes (4): JaCoCo 60% Coverage Threshold, BaseLspSpec Base Test Class, ProjectType DUMMY GROOVY GRAILS, Spock 2.3 Test Framework

### Community 32 - "Community 32"
Cohesion: 0.67
Nodes (2): Api, GradleTaskProvider

### Community 33 - "Community 33"
Cohesion: 0.67
Nodes (1): GrailsConventions

### Community 34 - "Community 34"
Cohesion: 0.67
Nodes (1): GrailsEnvironmentIndicator

### Community 35 - "Community 35"
Cohesion: 0.67
Nodes (1): GrailsProjectCard

### Community 36 - "Community 36"
Cohesion: 0.67
Nodes (1): GrailsQuickActions

### Community 37 - "Community 37"
Cohesion: 0.67
Nodes (1): GSPTemplateViewer

### Community 38 - "Community 38"
Cohesion: 1.0
Nodes (0): 

### Community 39 - "Community 39"
Cohesion: 1.0
Nodes (0): 

### Community 40 - "Community 40"
Cohesion: 1.0
Nodes (2): Comprehensive Test Cases Skill, Read Tests Before Change Skill

### Community 41 - "Community 41"
Cohesion: 1.0
Nodes (2): AST Node Level Execution Scope, Workspace Level Caching Strategy

### Community 42 - "Community 42"
Cohesion: 1.0
Nodes (2): Advanced Grails SVG Asset, Documentation SVG Asset

### Community 43 - "Community 43"
Cohesion: 1.0
Nodes (0): 

### Community 44 - "Community 44"
Cohesion: 1.0
Nodes (0): 

### Community 45 - "Community 45"
Cohesion: 1.0
Nodes (0): 

### Community 46 - "Community 46"
Cohesion: 1.0
Nodes (0): 

### Community 47 - "Community 47"
Cohesion: 1.0
Nodes (0): 

### Community 48 - "Community 48"
Cohesion: 1.0
Nodes (0): 

### Community 49 - "Community 49"
Cohesion: 1.0
Nodes (0): 

### Community 50 - "Community 50"
Cohesion: 1.0
Nodes (0): 

### Community 51 - "Community 51"
Cohesion: 1.0
Nodes (0): 

### Community 52 - "Community 52"
Cohesion: 1.0
Nodes (0): 

### Community 53 - "Community 53"
Cohesion: 1.0
Nodes (0): 

### Community 54 - "Community 54"
Cohesion: 1.0
Nodes (0): 

### Community 55 - "Community 55"
Cohesion: 1.0
Nodes (0): 

### Community 56 - "Community 56"
Cohesion: 1.0
Nodes (0): 

### Community 57 - "Community 57"
Cohesion: 1.0
Nodes (0): 

### Community 58 - "Community 58"
Cohesion: 1.0
Nodes (1): Auto Tool Selection Skill

### Community 59 - "Community 59"
Cohesion: 1.0
Nodes (1): User Guide

### Community 60 - "Community 60"
Cohesion: 1.0
Nodes (1): ReferencesProvider

### Community 61 - "Community 61"
Cohesion: 1.0
Nodes (1): Server Build Status - Passing

### Community 62 - "Community 62"
Cohesion: 1.0
Nodes (1): Slack SVG Community Link

### Community 63 - "Community 63"
Cohesion: 1.0
Nodes (1): Apple Touch Icon PNG

## Knowledge Gaps
- **46 isolated node(s):** `Api`, `GradleTaskProvider`, `LanguageServerManager`, `GradleService`, `LSP4J 0.23.1` (+41 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **Thin community `Community 38`** (2 nodes): `modelTypes.ts`, `getArtifactDirectory()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 39`** (2 nodes): `handlers.ts`, `registerProjectHandlers()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 40`** (2 nodes): `Comprehensive Test Cases Skill`, `Read Tests Before Change Skill`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 41`** (2 nodes): `AST Node Level Execution Scope`, `Workspace Level Caching Strategy`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 42`** (2 nodes): `Advanced Grails SVG Asset`, `Documentation SVG Asset`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 43`** (1 nodes): `eslint.config.mjs`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 44`** (1 nodes): `ServiceRegistry.ts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 45`** (1 nodes): `eventTypes.ts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 46`** (1 nodes): `errorTypes.ts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 47`** (1 nodes): `languageServerTypes.ts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 48`** (1 nodes): `statusBarTypes.ts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 49`** (1 nodes): `project.ts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 50`** (1 nodes): `extension.test.ts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 51`** (1 nodes): `TreeItemKind.ts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 52`** (1 nodes): `constants.ts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 53`** (1 nodes): `grails-themes.ts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 54`** (1 nodes): `GrailsIcons.ts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 55`** (1 nodes): `run-gradlew.js`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 56`** (1 nodes): `application.js`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 57`** (1 nodes): `application.js`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 58`** (1 nodes): `Auto Tool Selection Skill`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 59`** (1 nodes): `User Guide`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 60`** (1 nodes): `ReferencesProvider`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 61`** (1 nodes): `Server Build Status - Passing`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 62`** (1 nodes): `Slack SVG Community Link`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 63`** (1 nodes): `Apple Touch Icon PNG`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `log()` connect `Community 0` to `Community 1`, `Community 2`, `Community 3`, `Community 4`, `Community 9`, `Community 10`, `Community 18`, `Community 24`, `Community 29`?**
  _High betweenness centrality (0.143) - this node is a cross-community bridge._
- **Why does `GrailsTreeExplorer` connect `Community 1` to `Community 0`, `Community 24`, `Community 2`?**
  _High betweenness centrality (0.084) - this node is a cross-community bridge._
- **Are the 31 inferred relationships involving `log()` (e.g. with `activate()` and `deactivate()`) actually correct?**
  _`log()` has 31 INFERRED edges - model-reasoned connections that need verification._
- **What connects `Api`, `GradleTaskProvider`, `LanguageServerManager` to the rest of the system?**
  _46 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.07 - nodes in this community are weakly interconnected._
- **Should `Community 1` be split into smaller, more focused modules?**
  _Cohesion score 0.12 - nodes in this community are weakly interconnected._
- **Should `Community 2` be split into smaller, more focused modules?**
  _Cohesion score 0.06 - nodes in this community are weakly interconnected._