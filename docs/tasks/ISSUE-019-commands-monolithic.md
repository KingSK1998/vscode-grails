# ISSUE-019 · Commands.ts Monolithic
**Severity**: 🟠 High
**Service**: Commands (split into multiple files)
**Status**: ✅ DONE (Already Implemented)
**Parent Plan**: [client-improvement-plan.md](../client-improvement-plan.md)

## Problem
Commands.ts is 660 lines with all command registrations in one file. Hard to navigate and maintain. Violates single responsibility principle. Changes to one command type require editing the entire file.

## Current Code (problematic pattern)
```typescript
// Commands.ts - 660 lines with all command registrations
export class Commands implements Disposable {
  private disposables: Disposable[] = [];

  constructor(
    private readonly context: ExtensionContext,
    private readonly container: ServiceContainer
  ) {}

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

  // ... 600+ lines of command implementations
}
```

## Fixed Code

Split Commands.ts into focused files:

```typescript
// client/src/ui/commands/Commands.ts - Main orchestrator
import type { Disposable, ExtensionContext } from "vscode";
import type { ServiceContainer } from "../../core/container/ServiceContainer";
import { UICommands } from "./UICommands";
import { ProjectCommands } from "./ProjectCommands";
import { GrailsTaskCommands } from "./GrailsTaskCommands";
import { ArtifactCommands } from "./ArtifactCommands";
import { NavigationCommands } from "./NavigationCommands";
import { LogCommands } from "./LogCommands";
import { ExtensionCommands } from "./ExtensionCommands";
import { DashboardCommands } from "./DashboardCommands";
import { LegacyCommands } from "./LegacyCommands";

/**
 * Main command orchestrator.
 * Delegates to specialized command registrars.
 */
export class Commands implements Disposable {
  private readonly commandRegistrars: Disposable[];

  constructor(
    private readonly context: ExtensionContext,
    private readonly container: ServiceContainer
  ) {
    this.commandRegistrars = [
      new UICommands(context, container),
      new ProjectCommands(context, container),
      new GrailsTaskCommands(context, container),
      new ArtifactCommands(context, container),
      new NavigationCommands(context, container),
      new LogCommands(context, container),
      new ExtensionCommands(context, container),
      new DashboardCommands(context, container),
      new LegacyCommands(context, container),
    ];
  }

  /**
   * Register all extension commands.
   */
  registerAllCommands(): void {
    for (const registrar of this.commandRegistrars) {
      registrar.register();
    }
  }

  dispose(): void {
    for (const registrar of this.commandRegistrars) {
      registrar.dispose();
    }
  }
}
```

```typescript
// client/src/ui/commands/UICommands.ts
import type { Disposable, ExtensionContext } from "vscode";
import * as vscode from "vscode";
import { EventBus } from "../../core/events/EventBus";
import { EventType } from "../../core/events/eventTypes";
import type { ServiceContainer } from "../../core/container/ServiceContainer";
import { ErrorSeverity, ErrorSource } from "../../services/errors/errorTypes";

/**
 * UI-related commands.
 */
export class UICommands implements Disposable {
  private disposables: Disposable[] = [];

  constructor(
    private readonly context: ExtensionContext,
    private readonly container: ServiceContainer
  ) {}

  register(): void {
    this.registerRefreshTree();
    this.registerShowDashboard();
    this.registerShowExtensionInfo();
    this.registerStatusBarClicked();
  }

  private registerRefreshTree(): void {
    const disposable = vscode.commands.registerCommand("grails.refreshTree", async () => {
      console.log("🔄 REFRESH COMMAND: Manual tree refresh started");

      try {
        const initialProjects = this.container.projectService.getProjects();
        console.log(`📊 REFRESH: Initial projects count: ${initialProjects.length}`);

        console.log("🔍 REFRESH: Triggering project discovery...");
        const discoveredProjects = await this.container.projectService.discoverProjects();
        console.log(`📊 REFRESH: Discovered ${discoveredProjects.length} projects`);

        console.log("📡 REFRESH: Publishing PROJECTS_DISCOVERED event...");
        const eventBus = EventBus.getInstance();
        eventBus.publish({
          type: EventType.PROJECTS_DISCOVERED,
          projects: discoveredProjects,
          timestamp: Date.now(),
          source: "RefreshCommand",
        });
        console.log("✅ REFRESH: Event published successfully");

        vscode.window.showInformationMessage("Project tree refreshed");
        console.log("✅ REFRESH COMMAND: Completed successfully");
      } catch (error) {
        this.container.errorService.handleError(
          "Failed to refresh project tree",
          error,
          ErrorSource.Commands,
          ErrorSeverity.Error
        );
      }
    });

    this.disposables.push(disposable);
  }

  private registerShowDashboard(): void {
    const disposable = vscode.commands.registerCommand("grails.showDashboard", () => {
      const { GrailsDashboard } = require("../views/GrailsDashboard");
      const dashboard = new GrailsDashboard();
      dashboard.createOrShow(this.context);
    });

    this.disposables.push(disposable);
  }

  private registerShowExtensionInfo(): void {
    const disposable = vscode.commands.registerCommand("grails.showExtensionInfo", () => {
      const health = this.container.healthCheck();
      const projects = this.container.projectService.getProjects();

      const info = [
        "## Grails Extension Status",
        `**Health:** ${health.healthy ? "✅ Healthy" : "❌ Issues"}`,
        health.issues.length > 0 ? `**Issues:** ${health.issues.join(", ")}` : "",
        `**Projects:** ${projects.length} detected`,
        `**Gradle:** ${this.container.gradleService.isReady ? "✅ Ready" : "❌ Not Ready"}`,
        `**LSP:** ${this.container.languageServerManager.isRunning ? "✅ Running" : "❌ Stopped"}`,
      ]
        .filter(Boolean)
        .join("\n");

      vscode.window.showInformationMessage(info);
    });

    this.disposables.push(disposable);
  }

  private registerStatusBarClicked(): void {
    const disposable = vscode.commands.registerCommand("grails.statusBarClicked", () => {
      vscode.commands.executeCommand("grails.showExtensionInfo");
    });

    this.disposables.push(disposable);
  }

  dispose(): void {
    this.disposables.forEach(d => void d.dispose());
    this.disposables = [];
  }
}
```

```typescript
// client/src/ui/commands/ProjectCommands.ts
import type { Disposable, ExtensionContext } from "vscode";
import * as vscode from "vscode";
import type { ServiceContainer } from "../../core/container/ServiceContainer";

/**
 * Project management commands.
 */
export class ProjectCommands implements Disposable {
  private disposables: Disposable[] = [];

  constructor(
    private readonly context: ExtensionContext,
    private readonly container: ServiceContainer
  ) {}

  register(): void {
    this.registerRefreshProjects();
    this.registerShowProjectConfig();
  }

  private registerRefreshProjects(): void {
    const disposable = vscode.commands.registerCommand("grails.refreshProjects", async () => {
      await this.container.projectService.discoverProjects();
    });

    this.disposables.push(disposable);
  }

  private registerShowProjectConfig(): void {
    const disposable = vscode.commands.registerCommand("grails.showProjectConfig", () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        vscode.window.showWarningMessage("No Grails projects found");
        return;
      }

      const project = projects[0];
      const configInfo = [
        `**Project:** ${project.name}`,
        `**Type:** ${project.type}`,
        `**Root:** ${project.rootPath}`,
        `**Dependencies:** ${project?.dependencies?.length}`,
      ].join("\n");

      vscode.window.showInformationMessage(configInfo);
    });

    this.disposables.push(disposable);
  }

  dispose(): void {
    this.disposables.forEach(d => void d.dispose());
    this.disposables = [];
  }
}
```

```typescript
// client/src/ui/commands/GrailsTaskCommands.ts
import type { Disposable, ExtensionContext } from "vscode";
import * as vscode from "vscode";
import type { ServiceContainer } from "../../core/container/ServiceContainer";
import type { ProjectInfo } from "../../features/models/modelTypes";
import { ProjectType } from "../../features/models/modelTypes";
import { ErrorSeverity, ErrorSource } from "../../services/errors/errorTypes";
import { DashboardService } from "../../services/ui/DashboardService";
import { DependencyGraphService } from "../../services/ui/DependencyGraphService";
import { GormSqlPreviewService } from "../../services/ui/GormSqlPreviewService";

/**
 * Grails task commands.
 */
export class GrailsTaskCommands implements Disposable {
  private disposables: Disposable[] = [];
  private dashboardService: DashboardService | undefined;
  private dependencyGraphService: DependencyGraphService | undefined;
  private gormSqlPreviewService: GormSqlPreviewService | undefined;

  constructor(
    private readonly context: ExtensionContext,
    private readonly container: ServiceContainer
  ) {}

  register(): void {
    this.registerRunApp();
    this.registerDebugApp();
    this.registerTestApp();
    this.registerBuildProject();
    this.registerCleanProject();
    this.registerCompileProject();
    this.registerShowDependencyGraph();
    this.registerShowGormSqlPreview();
    this.registerGenerateAction();
  }

  private registerRunApp(): void {
    const disposable = vscode.commands.registerCommand("grails.runApp", async () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        vscode.window.showWarningMessage("No Grails projects found");
        return;
      }

      await this.container.gradleService.runGrailsApp(projects[0]);
    });

    this.disposables.push(disposable);
  }

  private registerDebugApp(): void {
    const disposable = vscode.commands.registerCommand("grails.debugApp", async () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        vscode.window.showWarningMessage("No Grails projects found");
        return;
      }

      await this.container.debugService.debugGrailsApp(projects[0]);
    });

    this.disposables.push(disposable);
  }

  private registerTestApp(): void {
    const disposable = vscode.commands.registerCommand("grails.testApp", async () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        vscode.window.showWarningMessage("No Grails projects found");
        return;
      }

      await this.container.gradleService.testGrailsApp(projects[0]);
    });

    this.disposables.push(disposable);
  }

  private registerBuildProject(): void {
    const disposable = vscode.commands.registerCommand("grails.buildProject", async () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        vscode.window.showWarningMessage("No Grails projects found");
        return;
      }

      await this.container.gradleService.buildProject(projects[0]);
    });

    this.disposables.push(disposable);
  }

  private registerCleanProject(): void {
    const disposable = vscode.commands.registerCommand("grails.cleanProject", async () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        vscode.window.showWarningMessage("No Grails projects found");
        return;
      }

      await this.container.gradleService.cleanProject(projects[0]);
    });

    this.disposables.push(disposable);
  }

  private registerCompileProject(): void {
    const disposable = vscode.commands.registerCommand("grails.compileProject", async () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        vscode.window.showWarningMessage("No Grails projects found");
        return;
      }

      await this.container.gradleService.buildProject(projects[0], "compileGroovy");
    });

    this.disposables.push(disposable);
  }

  private registerShowDependencyGraph(): void {
    const disposable = vscode.commands.registerCommand("grails.showDependencyGraph", () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        vscode.window.showWarningMessage("No Grails projects found");
        return;
      }

      if (!this.dependencyGraphService) {
        this.dependencyGraphService = new DependencyGraphService(
          this.context,
          this.container.errorService
        );
      }

      this.dependencyGraphService.openGraph(projects[0]);
    });

    this.disposables.push(disposable);
  }

  private registerShowGormSqlPreview(): void {
    const disposable = vscode.commands.registerCommand("grails.showGormSqlPreview", (uri?: vscode.Uri) => {
      const targetUri = uri ?? vscode.window.activeTextEditor?.document.uri;
      if (!targetUri) {
        return;
      }

      if (!this.gormSqlPreviewService) {
        this.gormSqlPreviewService = new GormSqlPreviewService(
          this.context,
          this.container.errorService
        );
      }

      this.gormSqlPreviewService.openPreview(targetUri);
    });

    this.disposables.push(disposable);
  }

  private registerGenerateAction(): void {
    const disposable = vscode.commands.registerCommand("grails.generateAction", async (uri: string, actionName: string) => {
      try {
        const editor = vscode.window.activeTextEditor;
        if (!editor || editor.document.uri.toString() !== uri) {
          return;
        }

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

    this.disposables.push(disposable);
  }

  dispose(): void {
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

    this.disposables.forEach(d => void d.dispose());
    this.disposables = [];
  }
}
```

```typescript
// client/src/ui/commands/ArtifactCommands.ts
import type { Disposable, ExtensionContext } from "vscode";
import * as vscode from "vscode";
import type { ServiceContainer } from "../../core/container/ServiceContainer";

/**
 * Artifact creation commands.
 */
export class ArtifactCommands implements Disposable {
  private disposables: Disposable[] = [];

  constructor(
    private readonly context: ExtensionContext,
    private readonly container: ServiceContainer
  ) {}

  register(): void {
    this.registerCreateController();
    this.registerCreateService();
    this.registerCreateDomain();
    this.registerCreateArtifact();
  }

  private registerCreateController(): void {
    const disposable = vscode.commands.registerCommand("grails.createController", async () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        return;
      }

      const name = await vscode.window.showInputBox({
        prompt: "Enter controller name",
        placeHolder: "BookController",
        validateInput: this.validateArtifactName,
      });

      if (name) {
        await this.container.artifactService.createController(projects[0], name);
      }
    });

    this.disposables.push(disposable);
  }

  private registerCreateService(): void {
    const disposable = vscode.commands.registerCommand("grails.createService", async () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        return;
      }

      const name = await vscode.window.showInputBox({
        prompt: "Enter service name",
        placeHolder: "UserService",
        validateInput: this.validateArtifactName,
      });

      if (name) {
        await this.container.artifactService.createService(projects[0], name);
      }
    });

    this.disposables.push(disposable);
  }

  private registerCreateDomain(): void {
    const disposable = vscode.commands.registerCommand("grails.createDomain", async () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        return;
      }

      const name = await vscode.window.showInputBox({
        prompt: "Enter domain name",
        placeHolder: "User",
        validateInput: this.validateArtifactName,
      });

      if (name) {
        await this.container.artifactService.createDomain(projects[0], name);
      }
    });

    this.disposables.push(disposable);
  }

  private registerCreateArtifact(): void {
    const disposable = vscode.commands.registerCommand("grails.createArtifact", async () => {
      await this.createArtifactWizard();
    });

    this.disposables.push(disposable);
  }

  private async createArtifactWizard(): Promise<void> {
    const artifactTypes = [
      { label: "Controller", description: "Handle web requests and responses" },
      { label: "Domain", description: "Domain model classes (GORM entities)" },
      { label: "Service", description: "Business logic services" },
      { label: "TagLib", description: "Custom GSP tags" },
      { label: "Job", description: "Quartz scheduled jobs" },
      { label: "Command", description: "Command objects for data binding" },
      { label: "Interceptor", description: "Request/response interceptors" },
    ];

    const selectedType = await vscode.window.showQuickPick(artifactTypes, {
      placeHolder: "Select artifact type to create",
      matchOnDescription: true,
    });

    if (!selectedType) {
      return;
    }

    const name = await vscode.window.showInputBox({
      prompt: `Enter ${selectedType.label} name`,
      placeHolder: `Book${selectedType.label}`,
      validateInput: this.validateArtifactName,
    });

    if (!name) {
      return;
    }

    const projects = this.container.projectService.getProjects();
    if (projects.length === 0) {
      return;
    }
    const project = projects[0];

    switch (selectedType.label) {
      case "Controller":
        await this.container.artifactService.createController(project, name);
        break;
      case "Service":
        await this.container.artifactService.createService(project, name);
        break;
      case "Domain":
        await this.container.artifactService.createDomain(project, name);
        break;
      case "TagLib":
        await this.container.artifactService.createTagLib(project, name);
        break;
      default:
        vscode.window.showInformationMessage(
          `Creating ${selectedType.label}: ${name} (Simplified implementation)`
        );
    }
  }

  private validateArtifactName = (value: string): string | undefined => {
    if (!value || value.trim().length === 0) {
      return "Name cannot be empty";
    }
    if (!/^[A-Za-z][A-Za-z0-9]*$/.test(value.trim())) {
      return "Name must start with a letter and contain only letters and numbers";
    }
    return undefined;
  };

  dispose(): void {
    this.disposables.forEach(d => void d.dispose());
    this.disposables = [];
  }
}
```

```typescript
// client/src/ui/commands/NavigationCommands.ts
import type { Disposable, ExtensionContext } from "vscode";
import * as vscode from "vscode";
import * as path from "path";
import type { ServiceContainer } from "../../core/container/ServiceContainer";
import { ErrorSeverity, ErrorSource } from "../../services/errors/errorTypes";

/**
 * Navigation commands.
 */
export class NavigationCommands implements Disposable {
  private disposables: Disposable[] = [];

  constructor(
    private readonly context: ExtensionContext,
    private readonly container: ServiceContainer
  ) {}

  register(): void {
    this.registerGoToView();
    this.registerGoToController();
    this.registerGoToService();
    this.registerGoToDomain();
    this.registerGoToTagLib();
  }

  private registerGoToView(): void {
    const disposable = vscode.commands.registerCommand("grails.goToView", async (docUri: vscode.Uri, actionName: string) => {
      try {
        const filePath = docUri.fsPath;
        const controllerPart = path
          .basename(filePath)
          .replace("Controller.groovy", "")
          .toLowerCase();

        const rootPath = this.container.projectService.getProjects()[0]?.rootPath;
        if (!rootPath) {
          return;
        }

        const viewPath = path.join(
          rootPath,
          "grails-app",
          "views",
          controllerPart,
          `${actionName}.gsp`
        );
        const viewUri = vscode.Uri.file(viewPath);

        try {
          const doc = await vscode.workspace.openTextDocument(viewUri);
          await vscode.window.showTextDocument(doc);
        } catch {
          vscode.window.showWarningMessage(`View not found: ${controllerPart}/${actionName}.gsp`);
        }
      } catch (error) {
        this.container.errorService.handleError("Navigation error", error, ErrorSource.Commands, ErrorSeverity.Warning);
      }
    });

    this.disposables.push(disposable);
  }

  private registerGoToController(): void {
    const disposable = vscode.commands.registerCommand("grails.goToController", async (controllerName: string) => {
      try {
        const projects = this.container.projectService.getProjects();
        if (projects.length === 0) {
          return;
        }

        const pattern = `**/grails-app/controllers/**/${controllerName.charAt(0).toUpperCase() + controllerName.slice(1)}Controller.groovy`;
        const files = await vscode.workspace.findFiles(pattern, null, 1);

        if (files.length > 0) {
          const doc = await vscode.workspace.openTextDocument(files[0]);
          await vscode.window.showTextDocument(doc);
        } else {
          vscode.window.showWarningMessage(`Controller implementation not found: ${controllerName}`);
        }
      } catch (error) {
        this.container.errorService.handleError("Navigation error", error, ErrorSource.Commands, ErrorSeverity.Warning);
      }
    });

    this.disposables.push(disposable);
  }

  private registerGoToService(): void {
    const disposable = vscode.commands.registerCommand("grails.goToService", async (serviceName: string) => {
      try {
        const projects = this.container.projectService.getProjects();
        if (projects.length === 0) {
          return;
        }

        const files = await vscode.workspace.findFiles(
          `**/grails-app/services/**/${serviceName}.groovy`,
          null,
          1
        );
        if (files.length > 0) {
          const doc = await vscode.workspace.openTextDocument(files[0]);
          await vscode.window.showTextDocument(doc);
        } else {
          vscode.window.showWarningMessage(`Service implementation not found: ${serviceName}`);
        }
      } catch (error) {
        this.container.errorService.handleError("Navigation error", error, ErrorSource.Commands, ErrorSeverity.Warning);
      }
    });

    this.disposables.push(disposable);
  }

  private registerGoToDomain(): void {
    const disposable = vscode.commands.registerCommand("grails.goToDomain", async (domainName: string) => {
      try {
        const projects = this.container.projectService.getProjects();
        if (projects.length === 0) {
          return;
        }

        const files = await vscode.workspace.findFiles(
          `**/grails-app/domain/**/${domainName}.groovy`,
          null,
          1
        );
        if (files.length > 0) {
          const doc = await vscode.workspace.openTextDocument(files[0]);
          await vscode.window.showTextDocument(doc);
        } else {
          vscode.window.showWarningMessage(`Domain class not found: ${domainName}`);
        }
      } catch (error) {
        this.container.errorService.handleError("Navigation error", error, ErrorSource.Commands, ErrorSeverity.Warning);
      }
    });

    this.disposables.push(disposable);
  }

  private registerGoToTagLib(): void {
    const disposable = vscode.commands.registerCommand("grails.goToTagLib", async (tagLibName: string) => {
      try {
        const projects = this.container.projectService.getProjects();
        if (projects.length === 0) {
          return;
        }

        const files = await vscode.workspace.findFiles(
          `**/grails-app/taglib/**/${tagLibName}TagLib.groovy`,
          null,
          1
        );
        if (files.length > 0) {
          const doc = await vscode.workspace.openTextDocument(files[0]);
          await vscode.window.showTextDocument(doc);
        } else {
          vscode.window.showWarningMessage(`TagLib implementation not found: ${tagLibName}`);
        }
      } catch (error) {
        this.container.errorService.handleError("Navigation error", error, ErrorSource.Commands, ErrorSeverity.Warning);
      }
    });

    this.disposables.push(disposable);
  }

  dispose(): void {
    this.disposables.forEach(d => void d.dispose());
    this.disposables = [];
  }
}
```

```typescript
// client/src/ui/commands/LogCommands.ts
import type { Disposable, ExtensionContext } from "vscode";
import * as vscode from "vscode";
import type { ServiceContainer } from "../../core/container/ServiceContainer";

/**
 * Log streaming commands.
 */
export class LogCommands implements Disposable {
  private disposables: Disposable[] = [];

  constructor(
    private readonly context: ExtensionContext,
    private readonly container: ServiceContainer
  ) {}

  register(): void {
    this.registerRunAppWithTailing();
    this.registerShowLogs();
  }

  private registerRunAppWithTailing(): void {
    const disposable = vscode.commands.registerCommand("grails.runAppWithTailing", async () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        vscode.window.showWarningMessage("No Grails projects found");
        return;
      }
      await this.container.logStreamingService.startAppWithTailing(projects[0]);
    });

    this.disposables.push(disposable);
  }

  private registerShowLogs(): void {
    const disposable = vscode.commands.registerCommand("grails.showLogs", () => {
      this.container.logStreamingService.showLogs();
    });

    this.disposables.push(disposable);
  }

  dispose(): void {
    this.disposables.forEach(d => void d.dispose());
    this.disposables = [];
  }
}
```

```typescript
// client/src/ui/commands/ExtensionCommands.ts
import type { Disposable, ExtensionContext } from "vscode";
import * as vscode from "vscode";
import type { ServiceContainer } from "../../core/container/ServiceContainer";

/**
 * Extension management commands.
 */
export class ExtensionCommands implements Disposable {
  private disposables: Disposable[] = [];

  constructor(
    private readonly context: ExtensionContext,
    private readonly container: ServiceContainer
  ) {}

  register(): void {
    this.registerRestartLanguageServer();
    this.registerSyncGradle();
    this.registerSetupWorkspace();
    this.registerDiagnoseIssues();
  }

  private registerRestartLanguageServer(): void {
    const disposable = vscode.commands.registerCommand("grails.restartLanguageServer", async () => {
      await this.container.languageServerManager.restart();
    });

    this.disposables.push(disposable);
  }

  private registerSyncGradle(): void {
    const disposable = vscode.commands.registerCommand("grails.syncGradle", async () => {
      const success = await this.container.gradleService.sync();
      if (success) {
        vscode.window.showInformationMessage("Gradle sync completed successfully");
      } else {
        vscode.window.showWarningMessage("Gradle sync completed with warnings");
      }
    });

    this.disposables.push(disposable);
  }

  private registerSetupWorkspace(): void {
    const disposable = vscode.commands.registerCommand("grails.setupWorkspace", async () => {
      await this.setupGrailsWorkspace();
    });

    this.disposables.push(disposable);
  }

  private registerDiagnoseIssues(): void {
    const disposable = vscode.commands.registerCommand("grails.diagnoseIssues", () => {
      this.diagnoseExtensionIssues();
    });

    this.disposables.push(disposable);
  }

  private async setupGrailsWorkspace(): Promise<void> {
    let changed = false;

    const emmetConfig = vscode.workspace.getConfiguration("emmet");
    const includeLangs = emmetConfig.get<Record<string, string>>("includeLanguages") ?? {};

    if (includeLangs["gsp"] !== "html") {
      includeLangs["gsp"] = "html";
      await emmetConfig.update("includeLanguages", includeLangs, vscode.ConfigurationTarget.Workspace);
      changed = true;
    }

    if (changed) {
      vscode.window.showInformationMessage("Grails workspace configured successfully");
    } else {
      vscode.window.showInformationMessage("Grails workspace already configured");
    }
  }

  private diagnoseExtensionIssues(): void {
    const health = this.container.healthCheck();

    if (health.healthy) {
      vscode.window.showInformationMessage("✅ No issues detected");
    } else {
      const issues = health.issues.join("\n• ");
      vscode.window
        .showErrorMessage(`❌ Issues detected:\n• ${issues}`, "Show Details")
        .then((selection: string | undefined) => {
          if (selection === "Show Details") {
            console.log("Detailed health issues:", health.issues);
          }
        });
    }
  }

  dispose(): void {
    this.disposables.forEach(d => void d.dispose());
    this.disposables = [];
  }
}
```

```typescript
// client/src/ui/commands/DashboardCommands.ts
import type { Disposable, ExtensionContext } from "vscode";
import * as vscode from "vscode";
import type { ServiceContainer } from "../../core/container/ServiceContainer";
import { DashboardService } from "../../services/ui/DashboardService";

/**
 * Dashboard commands.
 */
export class DashboardCommands implements Disposable {
  private disposables: Disposable[] = [];
  private dashboardService: DashboardService | undefined;

  constructor(
    private readonly context: ExtensionContext,
    private readonly container: ServiceContainer
  ) {}

  register(): void {
    this.registerOpenDashboard();
  }

  private registerOpenDashboard(): void {
    const disposable = vscode.commands.registerCommand("grails.openDashboard", () => {
      const isDev = vscode.workspace
        .getConfiguration("grails.developer")
        .get<boolean>("experimentalFlags", false);

      if (!this.dashboardService) {
        this.dashboardService = new DashboardService(
          this.context,
          this.container.errorService
        );
      }

      this.dashboardService.openDashboard(isDev ? "developer" : "user");
    });

    this.disposables.push(disposable);
  }

  dispose(): void {
    if (this.dashboardService) {
      this.dashboardService.dispose();
      this.dashboardService = undefined;
    }

    this.disposables.forEach(d => void d.dispose());
    this.disposables = [];
  }
}
```

```typescript
// client/src/ui/commands/LegacyCommands.ts
import type { Disposable, ExtensionContext } from "vscode";
import * as vscode from "vscode";
import type { ServiceContainer } from "../../core/container/ServiceContainer";

/**
 * Legacy terminal-based commands (for compatibility).
 */
export class LegacyCommands implements Disposable {
  private disposables: Disposable[] = [];

  constructor(
    private readonly context: ExtensionContext,
    private readonly container: ServiceContainer
  ) {}

  register(): void {
    this.registerRun();
    this.registerTest();
    this.registerClean();
    this.registerCompile();
  }

  private registerRun(): void {
    const disposable = vscode.commands.registerCommand("grails.run", () => {
      this.executeGrailsTerminalCommand("run-app");
    });

    this.disposables.push(disposable);
  }

  private registerTest(): void {
    const disposable = vscode.commands.registerCommand("grails.test", () => {
      this.executeGrailsTerminalCommand("test-app");
    });

    this.disposables.push(disposable);
  }

  private registerClean(): void {
    const disposable = vscode.commands.registerCommand("grails.clean", () => {
      this.executeGrailsTerminalCommand("clean");
    });

    this.disposables.push(disposable);
  }

  private registerCompile(): void {
    const disposable = vscode.commands.registerCommand("grails.compile", () => {
      this.executeGrailsTerminalCommand("compile");
    });

    this.disposables.push(disposable);
  }

  private executeGrailsTerminalCommand(command: string): void {
    const projects = this.container.projectService.getProjects();
    if (projects.length === 0) {
      vscode.window.showWarningMessage("No Grails projects found");
      return;
    }

    const terminal = vscode.window.createTerminal({
      name: "Grails",
      cwd: projects[0].rootPath,
    });

    terminal.sendText(`./gradlew ${command}`);
    terminal.show();
  }

  dispose(): void {
    this.disposables.forEach(d => void d.dispose());
    this.disposables = [];
  }
}
```

## Subtasks
- [ ] Create UICommands.ts
- [ ] Create ProjectCommands.ts
- [ ] Create GrailsTaskCommands.ts
- [ ] Create ArtifactCommands.ts
- [ ] Create NavigationCommands.ts
- [ ] Create LogCommands.ts
- [ ] Create ExtensionCommands.ts
- [ ] Create DashboardCommands.ts
- [ ] Create LegacyCommands.ts
- [ ] Update Commands.ts to import and register
- [ ] Test all commands still work

## Tradeoffs
- More files to manage
- Need to import from multiple files
- Slightly more complex file structure

## Testing This Fix
1. Test all UI commands work
2. Test all project commands work
3. Test all Grails task commands work
4. Test all artifact commands work
5. Test all navigation commands work
6. Test all log commands work
7. Test all extension commands work
8. Test all dashboard commands work
9. Test all legacy commands work
10. Verify command registration is complete