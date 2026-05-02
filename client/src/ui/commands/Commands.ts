import * as path from "path";
import type { Disposable, ExtensionContext } from "vscode";
import * as vscode from "vscode";
import { commands, ConfigurationTarget, window, workspace } from "vscode";
import type { ServiceContainer } from "../../core/container/ServiceContainer";
import { EventBus } from "../../core/events/EventBus";
import { EventType } from "../../core/events/eventTypes";
import { ErrorSeverity, ErrorSource } from "../../services/errors/errorTypes";
import { GrailsDashboard } from "../views/GrailsDashboard";

/**
 * Modern command registration using ServiceContainer architecture.
 * Combines the best of both legacy GrailsCommands and new service-based commands.
 */
export class Commands implements Disposable {
  private disposables: Disposable[] = [];

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

  /* ================= NAVIGATION COMMANDS ========================== */

  private registerDashboardCommands(): void {
    this.register("grails.openDashboard", () => {
      const isDev = vscode.workspace
        .getConfiguration("grails.developer")
        .get<boolean>("experimentalFlags", false);
      this.container.dashboardService.openDashboard(isDev ? "developer" : "user");
    });
  }

  private registerNavigationCommands(): void {
    this.register("grails.goToView", async (docUri: vscode.Uri, actionName: string) => {
      try {
        const filePath = docUri.fsPath;
        // Controller name is e.g. UserController -> user
        const controllerPart = path
          .basename(filePath)
          .replace("Controller.groovy", "")
          .toLowerCase();

        // Find view in grails-app/views/controller/action.gsp
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
          const doc = await workspace.openTextDocument(viewUri);
          await window.showTextDocument(doc);
        } catch {
          window.showWarningMessage(`View not found: ${controllerPart}/${actionName}.gsp`);
        }
      } catch (error) {
        console.error("Navigation error:", error);
      }
    });

    this.register("grails.goToController", async (controllerName: string) => {
      try {
        const projects = this.container.projectService.getProjects();
        if (projects.length === 0) {
          return;
        }

        // Search for file named {ControllerName}Controller.groovy (case insensitive)
        const pattern = `**/grails-app/controllers/**/${controllerName.charAt(0).toUpperCase() + controllerName.slice(1)}Controller.groovy`;
        const files = await workspace.findFiles(pattern, null, 1);

        if (files.length > 0) {
          const doc = await workspace.openTextDocument(files[0]);
          await window.showTextDocument(doc);
        } else {
          window.showWarningMessage(`Controller implementation not found: ${controllerName}`);
        }
      } catch (error) {
        console.error("Navigation error:", error);
      }
    });

    this.register("grails.goToService", async (serviceName: string) => {
      try {
        const projects = this.container.projectService.getProjects();
        if (projects.length === 0) {
          return;
        }

        // Search for file named serviceName.groovy
        const files = await workspace.findFiles(
          `**/grails-app/services/**/${serviceName}.groovy`,
          null,
          1
        );
        if (files.length > 0) {
          const doc = await workspace.openTextDocument(files[0]);
          await window.showTextDocument(doc);
        } else {
          window.showWarningMessage(`Service implementation not found: ${serviceName}`);
        }
      } catch (error) {
        console.error("Navigation error:", error);
      }
    });

    this.register("grails.goToDomain", async (domainName: string) => {
      try {
        const projects = this.container.projectService.getProjects();
        if (projects.length === 0) {
          return;
        }

        const files = await workspace.findFiles(
          `**/grails-app/domain/**/${domainName}.groovy`,
          null,
          1
        );
        if (files.length > 0) {
          const doc = await workspace.openTextDocument(files[0]);
          await window.showTextDocument(doc);
        } else {
          window.showWarningMessage(`Domain class not found: ${domainName}`);
        }
      } catch (error) {
        console.error("Navigation error:", error);
      }
    });

    this.register("grails.goToTagLib", async (tagLibName: string) => {
      try {
        const projects = this.container.projectService.getProjects();
        if (projects.length === 0) {
          return;
        }

        const files = await workspace.findFiles(
          `**/grails-app/taglib/**/${tagLibName}TagLib.groovy`,
          null,
          1
        );
        if (files.length > 0) {
          const doc = await workspace.openTextDocument(files[0]);
          await window.showTextDocument(doc);
        } else {
          window.showWarningMessage(`TagLib implementation not found: ${tagLibName}`);
        }
      } catch (error) {
        console.error("Navigation error:", error);
      }
    });
  }

  /* ================= LOG COMMANDS ================================= */

  private registerLogCommands(): void {
    this.register("grails.runAppWithTailing", async () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        window.showWarningMessage("No Grails projects found");
        return;
      }
      await this.container.logStreamingService.startAppWithTailing(projects[0]);
    });

    this.register("grails.showLogs", () => {
      this.container.logStreamingService.showLogs();
    });
  }

  /* ================= UI COMMANDS ================================== */

  private registerUICommands(): void {
    this.register("grails.refreshTree", async () => {
      console.log("🔄 REFRESH COMMAND: Manual tree refresh started");

      try {
        // Step 1: Check initial state
        const initialProjects = this.container.projectService.getProjects();
        console.log(`📊 REFRESH: Initial projects count: ${initialProjects.length}`);

        // Step 2: Trigger project discovery
        console.log("🔍 REFRESH: Triggering project discovery...");
        const discoveredProjects = await this.container.projectService.discoverProjects();
        console.log(`📊 REFRESH: Discovered ${discoveredProjects.length} projects`);

        // Step 3: Publish event - TreeProvider should listen to this
        console.log("📡 REFRESH: Publishing PROJECTS_DISCOVERED event...");
        const eventBus = EventBus.getInstance();
        eventBus.publish({
          type: EventType.PROJECTS_DISCOVERED,
          projects: discoveredProjects,
          timestamp: Date.now(),
          source: "RefreshCommand",
        });
        console.log("✅ REFRESH: Event published successfully");

        window.showInformationMessage("Project tree refreshed");
        console.log("✅ REFRESH COMMAND: Completed successfully");
      } catch (error) {
        console.error("❌ REFRESH COMMAND: Failed with error:", error);
        this.container.errorService.handleError(
          "Failed to refresh project tree",
          error,
          ErrorSource.Commands,
          ErrorSeverity.Error
        );
      }
    });

    this.register("grails.showDashboard", () => {
      const dashboard = new GrailsDashboard();
      dashboard.createOrShow(this.context);
    });

    this.register("grails.showExtensionInfo", () => {
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

      window.showInformationMessage(info);
    });

    this.register("grails.statusBarClicked", () => {
      // Delegate to the same info command
      commands.executeCommand("grails.showExtensionInfo");
    });
  }

  /* ================= PROJECT COMMANDS ========================== */

  private registerProjectCommands(): void {
    this.register("grails.refreshProjects", async () => {
      await this.container.projectService.discoverProjects();
    });

    this.register("grails.showProjectConfig", () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        window.showWarningMessage("No Grails projects found");
        return;
      }

      // Show first project config (or let user pick if multiple)
      const project = projects[0];
      const configInfo = [
        `**Project:** ${project.name}`,
        `**Type:** ${project.type}`,
        `**Root:** ${project.rootPath}`,
        `**Dependencies:** ${project?.dependencies?.length}`,
      ].join("\n");

      window.showInformationMessage(configInfo);
    });
  }

  /* ================= GRAILS TASK COMMANDS ====================== */

  private registerGrailsTaskCommands(): void {
    this.register("grails.runApp", async () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        window.showWarningMessage("No Grails projects found");
        return;
      }

      await this.container.gradleService.runGrailsApp(projects[0]);
    });

    this.register("grails.debugApp", async () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        window.showWarningMessage("No Grails projects found");
        return;
      }

      await this.container.debugService.debugGrailsApp(projects[0]);
    });

    this.register("grails.showDependencyGraph", () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        window.showWarningMessage("No Grails projects found");
        return;
      }

      this.container.dependencyGraphService.openGraph(projects[0]);
    });

    this.register("grails.showGormSqlPreview", (uri?: vscode.Uri) => {
      const targetUri = uri ?? window.activeTextEditor?.document.uri;
      if (!targetUri) {
        return;
      }
      this.container.gormSqlPreviewService.openPreview(targetUri);
    });

    this.register("grails.testApp", async () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        window.showWarningMessage("No Grails projects found");
        return;
      }

      await this.container.gradleService.testGrailsApp(projects[0]);
    });

    this.register("grails.buildProject", async () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        window.showWarningMessage("No Grails projects found");
        return;
      }

      await this.container.gradleService.buildProject(projects[0]);
    });

    this.register("grails.cleanProject", async () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        window.showWarningMessage("No Grails projects found");
        return;
      }

      await this.container.gradleService.cleanProject(projects[0]);
    });

    this.register("grails.compileProject", async () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        window.showWarningMessage("No Grails projects found");
        return;
      }

      await this.container.gradleService.buildProject(projects[0], "compileGroovy");
    });
    this.register("grails.generateAction", async (uri: string, actionName: string) => {
      try {
        const editor = window.activeTextEditor;
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
          window.showInformationMessage(`Action ${actionName} generated`);
        }
      } catch (error) {
        console.error("Action generation error:", error);
      }
    });
  }

  /* ================= ARTIFACT CREATION ========================= */

  private registerArtifactCommands(): void {
    this.register("grails.createController", async () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        return;
      }

      const name = await window.showInputBox({
        prompt: "Enter controller name",
        placeHolder: "BookController",
        validateInput: this.validateArtifactName,
      });

      if (name) {
        await this.container.artifactService.createController(projects[0], name);
      }
    });

    this.register("grails.createService", async () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        return;
      }

      const name = await window.showInputBox({
        prompt: "Enter service name",
        placeHolder: "UserService",
        validateInput: this.validateArtifactName,
      });

      if (name) {
        await this.container.artifactService.createService(projects[0], name);
      }
    });

    this.register("grails.createDomain", async () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        return;
      }

      const name = await window.showInputBox({
        prompt: "Enter domain name",
        placeHolder: "User",
        validateInput: this.validateArtifactName,
      });

      if (name) {
        await this.container.artifactService.createDomain(projects[0], name);
      }
    });

    this.register("grails.createArtifact", async () => {
      // Enhanced artifact creation wizard from your existing code
      await this.createArtifactWizard();
    });
  }

  /* ================= EXTENSION MANAGEMENT ====================== */

  private registerExtensionCommands(): void {
    this.register("grails.restartLanguageServer", async () => {
      await this.container.languageServerManager.restart();
    });

    this.register("grails.syncGradle", async () => {
      const success = await this.container.gradleService.sync();
      if (success) {
        window.showInformationMessage("Gradle sync completed successfully");
      } else {
        window.showWarningMessage("Gradle sync completed with warnings");
      }
    });

    this.register("grails.setupWorkspace", async () => {
      // Your existing workspace setup logic
      await this.setupGrailsWorkspace();
    });

    this.register("grails.diagnoseIssues", () => {
      // Enhanced diagnostics using services
      this.diagnoseExtensionIssues();
    });
  }

  /* ================= LEGACY TERMINAL COMMANDS (for compatibility) === */

  private registerLegacyCommands(): void {
    // Keep existing terminal-based commands for compatibility
    this.register("grails.run", () => this.executeGrailsTerminalCommand("run-app"));
    this.register("grails.test", () => this.executeGrailsTerminalCommand("test-app"));
    this.register("grails.clean", () => this.executeGrailsTerminalCommand("clean"));
    this.register("grails.compile", () => this.executeGrailsTerminalCommand("compile"));
  }

  /* ================= COMMAND IMPLEMENTATIONS ==================== */

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

    const selectedType = await window.showQuickPick(artifactTypes, {
      placeHolder: "Select artifact type to create",
      matchOnDescription: true,
    });

    if (!selectedType) {
      return;
    }

    const name = await window.showInputBox({
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
        window.showInformationMessage(
          `Creating ${selectedType.label}: ${name} (Simplified implementation)`
        );
    }
  }

  private async setupGrailsWorkspace(): Promise<void> {
    // Your existing workspace setup logic from GrailsCommands.ts
    let changed = false;

    // 1. Enable Emmet for GSP
    const emmetConfig = workspace.getConfiguration("emmet");
    const includeLangs = emmetConfig.get<Record<string, string>>("includeLanguages") ?? {};

    if (includeLangs["gsp"] !== "html") {
      includeLangs["gsp"] = "html";
      await emmetConfig.update("includeLanguages", includeLangs, ConfigurationTarget.Workspace);
      changed = true;
    }

    // 2. TODO: Add other workspace setup tasks

    if (changed) {
      window.showInformationMessage("Grails workspace configured successfully");
    } else {
      window.showInformationMessage("Grails workspace already configured");
    }
  }

  private diagnoseExtensionIssues(): void {
    const health = this.container.healthCheck();

    if (health.healthy) {
      window.showInformationMessage("✅ No issues detected");
    } else {
      const issues = health.issues.join("\n• ");
      window
        .showErrorMessage(`❌ Issues detected:\n• ${issues}`, "Show Details")
        .then((selection: string | undefined) => {
          if (selection === "Show Details") {
            // Could open output channel or detailed diagnostics
            console.log("Detailed health issues:", health.issues);
          }
        });
    }
  }

  private executeGrailsTerminalCommand(command: string): void {
    // Your existing terminal-based command execution
    // Keep for compatibility but consider migrating to service-based approach
    const projects = this.container.projectService.getProjects();
    if (projects.length === 0) {
      window.showWarningMessage("No Grails projects found");
      return;
    }

    // Use terminal execution as fallback
    const terminal = window.createTerminal({
      name: "Grails",
      cwd: projects[0].rootPath,
    });

    terminal.sendText(`./gradlew ${command}`);
    terminal.show();
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

  /* ================= REGISTRATION HELPER ======================== */

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  private register(command: string, callback: (...args: any[]) => unknown): void {
    const disposable = commands.registerCommand(command, callback);
    this.disposables.push(disposable);
    this.context.subscriptions.push(disposable);
  }

  /* ================= DISPOSAL =================================== */

  dispose(): void {
    this.disposables.forEach(d => void d.dispose());
    this.disposables = [];
  }
}
