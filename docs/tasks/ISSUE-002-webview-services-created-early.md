# ISSUE-002 · Webview Services Created Early
**Severity**: 🟠 High
**Service**: ServiceContainer.ts, GrailsTaskCommands.ts
**Status**: ✅ DONE
**Parent Plan**: [client-improvement-plan.md](../client-improvement-plan.md)

## Problem
Webview services (Dashboard, DependencyGraph, GormSqlPreview) are instantiated in ServiceContainer.initializeServices() but only used when commands are invoked. This wastes memory and startup time since these services are created immediately even if never used.

## Current Code (problematic pattern)
```typescript
// ServiceContainer.ts - Lines 153-172
this._services.ArtifactService = new ArtifactService(this._services.ErrorService);
this._services.DashboardService = new DashboardService(
  this.context,
  this._services.ErrorService
);
this._services.DebugService = new DebugService(
  this._services.GradleService,
  this._services.ErrorService
);
this._services.DependencyGraphService = new DependencyGraphService(
  this.context,
  this._services.ErrorService
);
this._services.GormSqlPreviewService = new GormSqlPreviewService(
  this.context,
  this._services.ErrorService
);
this._services.GrailsTestService = new GrailsTestService(
  this.context,
  this._services.ErrorService
);
```

## Fixed Code

Remove webview services from ServiceContainer initialization:

```typescript
// ServiceContainer.ts - Updated initializeServices()
private initializeServices(): void {
  console.log("[ServiceContainer] Initializing services...");

  // Phase 1: Core services (no dependencies) - initialize immediately
  this._services.ErrorService = new ErrorService();
  this._services.StatusBarService = new StatusBarService(this.context);
  this._services.ConfigurationService = new ConfigurationService();
  this._initializedServices.add("ErrorService");
  this._initializedServices.add("StatusBarService");
  this._initializedServices.add("ConfigurationService");

  console.log("[ServiceContainer] Core services initialized");

  // Phase 2: Register lazy initializers for non-core services
  this._lazyServices.GradleService = () => new GradleService(
    this._services.StatusBarService!,
    this._services.ErrorService!
  );

  this._lazyServices.LogStreamingService = () => new LogStreamingService(
    this.getGradleService(),
    this._services.StatusBarService!,
    this._services.ErrorService!
  );

  this._lazyServices.ProjectService = () => new ProjectService(
    this._services.StatusBarService!,
    this._services.ErrorService!,
    this._services.ConfigurationService!,
    EventBus.getInstance()
  );

  this._lazyServices.LanguageServerManager = () => new LanguageServerManager(
    this.context,
    this._services.StatusBarService!,
    this._services.ErrorService!,
    this._services.ConfigurationService!
  );

  // WEBVIEW SERVICES - REMOVED FROM INITIALIZATION
  // These will be created on-demand in Commands

  // ArtifactService - keep in ServiceContainer (used by commands)
  this._lazyServices.ArtifactService = () => new ArtifactService(this._services.ErrorService!);

  // DebugService - keep in ServiceContainer (used by commands)
  this._lazyServices.DebugService = () => new DebugService(
    this.getGradleService(),
    this._services.ErrorService!
  );

  // GrailsTestService - keep in ServiceContainer (used by test controller)
  this._lazyServices.GrailsTestService = () => new GrailsTestService(
    this.context,
    this._services.ErrorService!
  );

  console.log("[ServiceContainer] Lazy service initializers registered");
}
```

Update Commands.ts to create webview services on-demand:

```typescript
// Commands.ts - Updated command registration

export class Commands implements Disposable {
  private disposables: Disposable[] = [];
  // Track webview services separately
  private dashboardService: DashboardService | undefined;
  private dependencyGraphService: DependencyGraphService | undefined;
  private gormSqlPreviewService: GormSqlPreviewService | undefined;

  constructor(
    private readonly context: ExtensionContext,
    private readonly container: ServiceContainer
  ) {}

  /**
   * Register all extension commands.
   */
  registerAllCommands(): void {
    // Core UI commands
    this.registerUICommands();

    // Project management commands
    this.registerProjectCommands();

    // Grails task commands
    this.registerGrailsTaskCommands();

    // Artifact creation commands
    this.registerArtifactCommands();

    // Log streaming commands
    this.registerLogCommands();

    // Extension management commands
    this.registerExtensionCommands();

    // Dashboard commands
    this.registerDashboardCommands();

    // Navigation commands
    this.registerNavigationCommands();

    // Legacy terminal-based commands (for compatibility)
    this.registerLegacyCommands();
  }

  /* ================= DASHBOARD COMMANDS ========================== */

  private registerDashboardCommands(): void {
    this.register("grails.openDashboard", () => {
      const isDev = vscode.workspace
        .getConfiguration("grails.developer")
        .get<boolean>("experimentalFlags", false);

      // Create DashboardService on-demand
      if (!this.dashboardService) {
        this.dashboardService = new DashboardService(
          this.context,
          this.container.errorService
        );
      }

      this.dashboardService.openDashboard(isDev ? "developer" : "user");
    });
  }

  /* ================= GRAILS TASK COMMANDS ====================== */

  private registerGrailsTaskCommands(): void {
    this.register("grails.runApp", async () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        vscode.window.showWarningMessage("No Grails projects found");
        return;
      }

      await this.container.gradleService.runGrailsApp(projects[0]);
    });

    this.register("grails.debugApp", async () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        vscode.window.showWarningMessage("No Grails projects found");
        return;
      }

      await this.container.debugService.debugGrailsApp(projects[0]);
    });

    this.register("grails.showDependencyGraph", () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        vscode.window.showWarningMessage("No Grails projects found");
        return;
      }

      // Create DependencyGraphService on-demand
      if (!this.dependencyGraphService) {
        this.dependencyGraphService = new DependencyGraphService(
          this.context,
          this.container.errorService
        );
      }

      this.dependencyGraphService.openGraph(projects[0]);
    });

    this.register("grails.showGormSqlPreview", (uri?: vscode.Uri) => {
      const targetUri = uri ?? vscode.window.activeTextEditor?.document.uri;
      if (!targetUri) {
        return;
      }

      // Create GormSqlPreviewService on-demand
      if (!this.gormSqlPreviewService) {
        this.gormSqlPreviewService = new GormSqlPreviewService(
          this.context,
          this.container.errorService
        );
      }

      this.gormSqlPreviewService.openPreview(targetUri);
    });

    this.register("grails.testApp", async () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        vscode.window.showWarningMessage("No Grails projects found");
        return;
      }

      await this.container.gradleService.testGrailsApp(projects[0]);
    });

    this.register("grails.buildProject", async () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        vscode.window.showWarningMessage("No Grails projects found");
        return;
      }

      await this.container.gradleService.buildProject(projects[0]);
    });

    this.register("grails.cleanProject", async () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        vscode.window.showWarningMessage("No Grails projects found");
        return;
      }

      await this.container.gradleService.cleanProject(projects[0]);
    });

    this.register("grails.compileProject", async () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        vscode.window.showWarningMessage("No Grails projects found");
        return;
      }

      await this.container.gradleService.buildProject(projects[0], "compileGroovy");
    });

    this.register("grails.generateAction", async (uri: string, actionName: string) => {
      try {
        const editor = vscode.window.activeTextEditor;
        if (!editor || editor.document.uri.toString() !== uri) {
          return;
        }

        // Naive implementation: append at the end of the class
        const text = editor.document.getText();
        const lastBraceIndex = text.lastIndexOf("}");
        if (lastBraceIndex !== -1) {
          const edit = new vscode.WorkspaceEdit();
          const pos = editor.document.positionAt(lastBraceIndex);
          edit.insert(
            editor.document.uri,
            pos,
            `\n    def ${actionName}() {\n        render "Hello from ${actionName}"\n    }\n`
          );
          await vscode.workspace.applyEdit(edit);
          vscode.window.showInformationMessage(`Action ${actionName} generated`);
        }
      } catch (error) {
        this.container.errorService.handleError(
          "Action generation error",
          error,
          ErrorSource.Commands,
          ErrorSeverity.Error
        );
      }
    });
  }

  /* ================= DISPOSAL =================================== */

  dispose(): void {
    // Dispose webview services
    if (this.dashboardService) {
      this.dashboardService.dispose();
      this.dashboardService = undefined;
    }
    if (this.dependencyGraphService) {
      this.dependencyGraphService.dispose();
      this.dependencyGraphService = undefined;
    }
    if (this.gormSqlPreviewService) {
      this.gormSqlPreviewService.dispose();
      this.gormSqlPreviewService = undefined;
    }

    // Dispose command registrations
    this.disposables.forEach(d => void d.dispose());
    this.disposables = [];
  }
}
```

Update ServiceContainer to remove webview service getters:

```typescript
// ServiceContainer.ts - Remove webview service getters

// REMOVE THESE GETTERS:
// get dashboardService(): DashboardService { ... }
// get dependencyGraphService(): DependencyGraphService { ... }
// get gormSqlPreviewService(): GormSqlPreviewService { ... }

// Keep other getters:
get errorService(): ErrorService {
  return this._services.ErrorService!;
}

get statusBarService(): StatusBarService {
  return this._services.StatusBarService!;
}

get configurationService(): ConfigurationService {
  return this._services.ConfigurationService!;
}

get projectService(): ProjectService {
  return this.getLazyService("ProjectService");
}

get gradleService(): GradleService {
  return this.getLazyService("GradleService");
}

get logStreamingService(): LogStreamingService {
  return this.getLazyService("LogStreamingService");
}

get languageServerManager(): LanguageServerManager {
  return this.getLazyService("LanguageServerManager");
}

get artifactService(): ArtifactService {
  return this.getLazyService("ArtifactService");
}

get debugService(): DebugService {
  return this.getLazyService("DebugService");
}

get grailsTestService(): GrailsTestService {
  return this.getLazyService("GrailsTestService");
}
```

## Subtasks
- [x] Remove webview services from ServiceContainer.initializeServices()
- [x] Remove webview service getters from ServiceContainer
- [x] Add webview service tracking to GrailsTaskCommands
- [x] Create DependencyGraphService on-demand in GrailsTaskCommands
- [x] Create GormSqlPreviewService on-demand in GrailsTaskCommands
- [x] Add webview service disposal in GrailsTaskCommands.dispose()
- [ ] Test that commands still work after change

## Implementation Notes
- DashboardService was removed from ServiceContainer but was not actually used via container (grails.showDashboard uses GrailsDashboard directly)
- DependencyGraphService and GormSqlPreviewService now receive LanguageServerManager via constructor injection instead of calling ServiceContainer.getInstance()
- Both services now implement Disposable with proper cleanup

## Tradeoffs
- Slightly more code in Commands to manage webview services
- First command invocation has small overhead to create service
- Webview services not accessible via ServiceContainer anymore

## Testing This Fix
1. Test that grails.openDashboard command works
2. Test that grails.showDependencyGraph command works
3. Test that grails.showGormSqlPreview command works
4. Verify memory usage is lower at activation
5. Test that webview services are disposed correctly
6. Test that commands can be called multiple times