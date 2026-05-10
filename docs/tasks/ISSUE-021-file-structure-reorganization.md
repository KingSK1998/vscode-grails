# ISSUE-021 · File Structure Reorganization

## Problem

### File Structure Issues
Current file structure has grown organically without consistent organization:

| Directory | File Count | Issues |
|---|---|---|
| `ui/commands` | 11 | Too many files - violates single responsibility |
| `utils` | 7 | Miscellaneous utility dumping ground |
| `services` | 20+ | Inconsistent grouping, mixed concerns |
| `features/*` | 7 | One-file folders (dashboard, gorm-sql-preview, dependency-graph) |
| `services/languageServer` + `services/lsp` | 6 | Overlapping concerns, unclear distinction |

**Symptoms:**
- `services/languageServer` and `services/lsp` both handle LSP - unclear which does what
- `useCase` folder has 1 file - organizational overhead without benefit
- `features/dashboard`, `features/dependency-graph`, `features/gorm-sql-preview` each have 1-2 files - too granular
- `utils/` has 7 mixed utilities
- `ui/commands/` has 11 files - potential violation of single responsibility
- Some services reference other services directly, bypassing ServiceContainer

### Large File Issues
Current largest files by line count:

| File | Lines | Problem |
|---|---|---|
| `GrailsTreeExplorer.ts` | 1216 | Massive tree data provider doing too much |
| `ProjectService.ts` | 532 | Discovery, caching, watching all in one class |
| `ActivationManager.ts` | 443 | Complex activation orchestration |
| `LanguageServerManager.ts` | 281 | Mixed concerns |
| `GspCompletionProvider.ts` | 265 | Handler with potential to split |
| `GradleService.ts` | 261 | Gradle operations all in one place |
| `DependencyGraphService.ts` | 257 | Single large feature file |
| `IconThemeDetector.ts` | 252 | Theme detection + caching + suggestions |

**Rule of thumb**: Files over ~300 lines are hard to navigate. Files over ~500 lines are at high risk of becoming unmaintainable.

## Breaking Down Large Files

Not every large file needs splitting - sometimes a class genuinely does one thing. But when it doesn't, here are practical patterns:

### When to Split
- A method can't be understood without reading 500+ lines first
- Multiple unrelated responsibilities mixed (e.g., "this handles caching AND discovery AND file watching")
- Methods could be grouped by a noun (e.g., `*Helper`, `*Builder`, `*Strategy`)
- You find yourself writing `// Section: X` comments

### Pattern 1: Extract Helpers/Utilities
**Before** (in ProjectService.ts):
```typescript
private hasGrailsApp(project: ProjectInfo): boolean { ... }
private hasConfigFolder(project: ProjectInfo): boolean { ... }
private hasViewsFolder(project: ProjectInfo): boolean { ... }
private hasSourceFolder(project: ProjectInfo): boolean { ... }
private folderExists(path: string): boolean { ... }
```

**After**: Create `ProjectFileHelper.ts`:
```typescript
// services/project/ProjectFileHelper.ts
export class ProjectFileHelper {
  hasGrailsApp(project: ProjectInfo): boolean { ... }
  hasConfigFolder(project: ProjectInfo): boolean { ... }
  hasViewsFolder(project: ProjectInfo): boolean { ... }
  hasSourceFolder(project: ProjectInfo): boolean { ... }
  folderExists(path: string): boolean { ... }
}
```

### Pattern 2: Extract Builders
**Before**: Complex object construction with many options:
```typescript
private buildTreeItem(label: string, state: TreeItemCollapsibleState, kind: TreeItemKind, command: Command, project: ProjectInfo, resourcePath?: string): GrailsTreeItem { ... }
```

**After**: Create `TreeItemBuilder.ts`:
```typescript
// ui/treeExplorer/TreeItemBuilder.ts
export class TreeItemBuilder {
  withLabel(label: string): this { ... }
  withState(state: TreeItemCollapsibleState): this { ... }
  withKind(kind: TreeItemKind): this { ... }
  withCommand(command: Command): this { ... }
  withProject(project: ProjectInfo): this { ... }
  withResourcePath(path: string): this { ... }
  build(): GrailsTreeItem { ... }
}
```

### Pattern 3: Extract Strategies
**Before** (in GrailsTreeExplorer):
```typescript
case TreeItemKind.ProjectRoot: return this.getRootContainers(element.projectInfo);
case TreeItemKind.GrailsAppRoot: return this.getGrailsArtefactCategories(element.projectInfo);
case TreeItemKind.GrailsArtifactFolders: return this.getArtifactFiles(element.projectInfo, element.artifactType);
```

**After**: Create `TreeNodeResolver.ts`:
```typescript
// ui/treeExplorer/TreeNodeResolver.ts
export interface TreeNodeStrategy {
  canResolve(kind: TreeItemKind): boolean;
  resolve(element: GrailsTreeItem): GrailsTreeItem[];
}

export class ProjectRootStrategy implements TreeNodeStrategy {
  canResolve(kind: TreeItemKind) { return kind === TreeItemKind.ProjectRoot; }
  resolve(element: GrailsTreeItem) { return this.getRootContainers(element.projectInfo); }
}
```

### Pattern 4: Extract Transformers
**Before** (inline in a method):
```typescript
const items = projects
  .filter(project => project?.rootPath && project?.name)
  .map(project => new GrailsTreeItem(this.getProjectLabel(project), ...));
```

**After**: Create `ProjectNodeMapper.ts`:
```typescript
// features/projects/ProjectNodeMapper.ts
export class ProjectNodeMapper {
  constructor(private iconProvider: IconProvider) {}

  toTreeItems(projects: ProjectInfo[]): GrailsTreeItem[] {
    return projects
      .filter(p => p?.rootPath && p?.name)
      .map(p => this.toTreeItem(p));
  }

  private toTreeItem(project: ProjectInfo): GrailsTreeItem {
    return new GrailsTreeItem(
      this.getLabel(project),
      TreeItemCollapsibleState.Expanded,
      TreeItemKind.ProjectRoot,
      { command: "grails.selectProject", title: "Select Project", arguments: [project] },
      project,
      project.rootPath
    );
  }
}
```

### Pattern 5: Collapse One-File Folders
**Before**: Multiple one-file folders in `features/`:
```
features/dashboard/DashboardService.ts
features/dependency-graph/DependencyGraphService.ts
features/gorm-sql-preview/GormSqlPreviewService.ts
```

**After**: Consolidate by domain:
```
features/
  ├── dashboard/
  │   ├── DashboardService.ts
  │   └── dashboardStyles.ts (if needed)
  └── preview/
      ├── DependencyGraphService.ts
      └── GormSqlPreviewService.ts
```

Or flatten if each feature is truly independent and small.

### Refactoring GrailsTreeExplorer.ts (1216 lines)
Suggested split:
1. `TreeDataProvider` interface only (the 4 methods)
2. `ProjectNodeMapper.ts` - transforms ProjectInfo[] to GrailsTreeItem[]
3. `ArtifactNodeBuilder.ts` - builds artifact category nodes (Controllers, Services, etc.)
4. `ProjectFileHelper.ts` - all the `has*` and `folderExists` checks
5. Keep `GrailsTreeExplorer.ts` as thin orchestrator (skeleton)

### Refactoring ProjectService.ts (532 lines)
Suggested split:
1. `ProjectDiscovery.ts` - `discoverProjects()`, `quickScan()`, caching
2. `ProjectRegistry.ts` - `getProjects()`, `getProjectById()`, `getActiveProject()`, `setActiveProject()`
3. `ProjectWatcher.ts` - all file watcher management (`initWatchers`, `disposeWatcher`, etc.)
4. Keep `ProjectService.ts` as facade that composes the above

## Proposed Solution

### Restructure into Clear Layered Architecture

```
src/
├── core/                          # DI container, lifecycle, events (already good)
│   ├── container/
│   ├── lifecycle/
│   └── events/
├── services/                       # Single responsibility per folder
│   ├── languageServer/             # Language Server lifecycle management
│   ├── project/                    # Project scanning, discovery
│   ├── gradle/                     # Gradle integration
│   ├── errors/                     # Error handling
│   ├── diagnostics/                # Diagnostics management
│   ├── testing/                    # Test execution
│   ├── artifacts/                  # Artifact creation
│   ├── debug/                      # Debugging
│   └── ui/                         # UI-related services (status bar, output, config)
│       ├── StatusBarService.ts
│       ├── OutputChannelService.ts
│       └── ConfigurationService.ts
├── ui/                            # Pure UI components (no business logic)
│   ├── commands/                   # Command registrations only (delegate to services)
│   ├── views/                      # Webview panels
│   ├── treeExplorer/               # Tree data providers
│   ├── decorations/                # Decoration providers
│   └── icons/                      # Icon detection
├── features/                       # Feature modules (grouped by feature)
│   ├── dashboard/
│   ├── dependency-graph/
│   └── gorm-sql-preview/
├── models/                         # DTOs, types, interfaces (flat, no subdirs)
│   ├── ProjectInfo.ts
│   ├── BuildInfo.ts
│   └── ...
├── utils/                         # Shared utilities (generic, no domain knowledge)
│   ├── debounce.ts
│   ├── promise.ts                  # withTimeout, race utilities
│   ├── fs.ts                      # Async fs helpers
│   └── string.ts
├── store/                          # State management (if needed)
└── test/                           # Test utilities
```

### Key Principles
1. **Services are domain verbs** (scanning, building, running, testing)
2. **UI is passive** - commands delegate to services, views are data-driven
3. **No one-file folders** - consolidate singleton feature folders
4. **Clear dependency direction** - `ui/` depends on `services/`, never vice versa
5. **Consolidate `languageServer` and `lsp`** into single `languageServer/` folder
6. **Move types to `models/`** - currently scattered in individual service folders

## Implementation Plan

### Phase 1: Analyze & Plan
- [ ] Map all 70 files to proposed structure
- [ ] Identify circular dependencies
- [ ] Document import changes needed
- [ ] Identify which large files need splitting (analyze > 300 line files)
- [ ] Prioritize: GrailsTreeExplorer.ts (1216 lines) → ProjectService.ts (532 lines) → others

### Phase 2: Break Down Large Files (Before Moving)
Split files in place first (easier to verify), then move to new structure.
- [ ] Split GrailsTreeExplorer.ts → 4-5 smaller files
- [ ] Split ProjectService.ts → 3-4 smaller files
- [ ] Split other large files (> 300 lines) as needed
- [ ] Run lint/typecheck after each split
- [ ] Verify no regressions

### Phase 3: Create New Structure
- [ ] Create new directories
- [ ] Move split files into new structure
- [ ] Update imports in each batch
- [ ] Run lint/typecheck after each batch

### Phase 4: Cleanup
- [ ] Remove old empty directories
- [ ] Update ServiceContainer references
- [ ] Update path aliases in tsconfig if needed
- [ ] Full lint/typecheck pass

## Tradeoffs
- **Import churn**: All import paths will need updates
- **Risk**: Moving files may break existing imports if not careful
- **Benefit**: Clearer architecture enables easier onboarding and scaling

## Status
⬜ Pending - awaiting implementation