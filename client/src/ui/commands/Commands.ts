import {
  ExtensionContext,
  commands,
  window,
  workspace,
  ConfigurationTarget,
  Disposable,
} from "vscode";
import { ServiceContainer } from "../../core/container/ServiceContainer";
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

    // Extension management commands
    this.registerExtensionCommands();

    // Legacy terminal-based commands (for compatibility)
    this.registerLegacyCommands();
  }

  /* ================= UI COMMANDS ================================== */

  private registerUICommands(): void {
    this.register("grails.refreshTree", () => {
      // TODO: Emit refresh event via EventBus
      console.log("🔄 Refreshing tree view");
    });

    this.register("grails.showDashboard", () => {
      const dashboard = new GrailsDashboard();
      dashboard.createOrShow(this.context);
    });

    this.register("grails.showExtensionInfo", async () => {
      const health = await this.container.healthCheck();
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
  }

  /* ================= PROJECT COMMANDS ========================== */

  private registerProjectCommands(): void {
    this.register("grails.refreshProjects", async () => {
      await this.container.projectService.discoverProjects();
    });

    this.register("grails.showProjectConfig", async () => {
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
  }

  /* ================= ARTIFACT CREATION ========================= */

  private registerArtifactCommands(): void {
    this.register("grails.createController", async () => {
      const name = await window.showInputBox({
        prompt: "Enter controller name",
        placeHolder: "BookController",
        validateInput: this.validateArtifactName,
      });

      if (name) {
        // TODO: Use service-based artifact creation
        window.showInformationMessage(`Creating controller: ${name}`);
      }
    });

    this.register("grails.createService", async () => {
      const name = await window.showInputBox({
        prompt: "Enter service name",
        placeHolder: "UserService",
        validateInput: this.validateArtifactName,
      });

      if (name) {
        window.showInformationMessage(`Creating service: ${name}`);
      }
    });

    this.register("grails.createDomain", async () => {
      const name = await window.showInputBox({
        prompt: "Enter domain name",
        placeHolder: "User",
        validateInput: this.validateArtifactName,
      });

      if (name) {
        window.showInformationMessage(`Creating domain: ${name}`);
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

    this.register("grails.diagnoseIssues", async () => {
      // Enhanced diagnostics using services
      await this.diagnoseExtensionIssues();
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

    if (!selectedType) return;

    const name = await window.showInputBox({
      prompt: `Enter ${selectedType.label} name`,
      placeHolder: `Book${selectedType.label}`,
      validateInput: this.validateArtifactName,
    });

    if (!name) return;

    // TODO: Implement service-based artifact creation
    window.showInformationMessage(`Creating ${selectedType.label}: ${name}`);
  }

  private async setupGrailsWorkspace(): Promise<void> {
    // Your existing workspace setup logic from GrailsCommands.ts
    let changed = false;

    // 1. Enable Emmet for GSP
    const emmetConfig = workspace.getConfiguration("emmet");
    const includeLangs = emmetConfig.get<{ [key: string]: string }>("includeLanguages") || {};

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

  private async diagnoseExtensionIssues(): Promise<void> {
    const health = await this.container.healthCheck();

    if (health.healthy) {
      window.showInformationMessage("✅ No issues detected");
    } else {
      const issues = health.issues.join("\n• ");
      window
        .showErrorMessage(`❌ Issues detected:\n• ${issues}`, "Show Details")
        .then(selection => {
          if (selection === "Show Details") {
            // Could open output channel or detailed diagnostics
            console.log("Detailed health issues:", health.issues);
          }
        });
    }
  }

  private async executeGrailsTerminalCommand(command: string): Promise<void> {
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

  private validateArtifactName(value: string): string | null {
    if (!value || value.trim().length === 0) {
      return "Name cannot be empty";
    }
    if (!/^[A-Za-z][A-Za-z0-9]*$/.test(value.trim())) {
      return "Name must start with a letter and contain only letters and numbers";
    }
    return null;
  }

  /* ================= REGISTRATION HELPER ======================== */

  private register(command: string, callback: (...args: any[]) => any): void {
    const disposable = commands.registerCommand(command, callback);
    this.disposables.push(disposable);
    this.context.subscriptions.push(disposable);
  }

  /* ================= DISPOSAL =================================== */

  dispose(): void {
    this.disposables.forEach(d => d.dispose());
    this.disposables = [];
  }
}
