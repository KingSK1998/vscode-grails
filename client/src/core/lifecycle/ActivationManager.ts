import { commands, Disposable, ExtensionContext, window, workspace } from "vscode";
import { ServiceContainer } from "../container/ServiceContainer";
import { ErrorSeverity, ErrorSource } from "../../services/errors/errorTypes";
import { EventBus } from "../events/EventBus";
import { EventType, ProjectChangedEvent, ProjectsDiscoveredEvent } from "../events/eventTypes";
import { GrailsDashboard } from "../../ui/views/GrailsDashboard";
import { GrailsTreeDataProvider } from "../../ui/providers/TreeDataProvider";
import { Commands } from "../../ui/commands/Commands";

/**
 * Manages the complete extension activation lifecycle.
 * Coordinates service initialization, command registration, and event setup.
 */
export class ActivationManager implements Disposable {
  private disposables: Disposable[] = [];
  private container: ServiceContainer;
  private commands: Commands;

  constructor(private readonly context: ExtensionContext) {
    this.container = ServiceContainer.intialize(context);
    this.commands = new Commands(context, this.container);
  }

  /**
   * Main activation sequence - called from extension.ts
   */
  async activate(): Promise<void> {
    try {
      console.log("📦 Initializing services...");
      this.container.statusBarService.sync("Initializing Grails extension...");

      // Phase 1: Register commands and UI components early
      this.commands.registerAllCommands();
      this.setupUIComponents();

      // Phase 2: Setup event listeners
      this.setupEventListeners();

      // Phase 3: Initialize core services
      await this.initializeServices();

      // Phase 4: Health check and final setup
      await this.performHealthCheck();

      console.log("✅ Grails Extension activated successfully");
    } catch (error) {
      this.container.errorService.handle(
        `Extension activation failed: ${error}`,
        ErrorSource.Extension,
        ErrorSeverity.Critical
      );
      throw error;
    }
  }

  /**
   * Setup UI components like tree view.
   */
  private setupUIComponents(): void {
    try {
      // Create and register tree view
      const treeProvider = new GrailsTreeDataProvider(this.context);
      const treeView = window.createTreeView("grailsExplorer", {
        treeDataProvider: treeProvider,
        showCollapseAll: true,
      });

      this.context.subscriptions.push(treeView);
      console.log("✅ Tree view registered successfully");
    } catch (error) {
      console.error("❌ Failed to setup UI components:", error);
      this.container.errorService.handle(
        `UI setup failed: ${error}`,
        ErrorSource.Extension,
        ErrorSeverity.Error
      );
    }
  }

  /**
   * Setup workspace and configuration event listeners.
   */
  private setupEventListeners(): void {
    // Configuration changes
    this.disposables.push(
      workspace.onDidChangeConfiguration(async e => {
        if (e.affectsConfiguration("grails")) {
          this.container.statusBarService.info("Configuration changed");

          // Restart LSP if needed
          if (
            e.affectsConfiguration("grails.completion") ||
            e.affectsConfiguration("grails.server")
          ) {
            await this.container.languageServerManager.restart();
          }

          // Re-sync Gradle if JVM args changed
          if (e.affectsConfiguration("grails.server.jvmArgs")) {
            await this.container.gradleService.sync();
          }
        }
      })
    );

    // Workspace changes
    this.disposables.push(
      workspace.onDidChangeWorkspaceFolders(async () => {
        this.container.statusBarService.info("Workspace changed, discovering projects...");
        await this.container.projectService.discoverProjects();
      })
    );

    // Build file changes
    this.disposables.push(
      workspace.createFileSystemWatcher("**/build.gradle").onDidChange(async () => {
        this.container.statusBarService.info("Build file changed, refreshing projects...");
        await this.container.projectService.discoverProjects();
      })
    );

    // Internal event subscriptions
    const eventBus = EventBus.getInstance();
    this.disposables.push(
      eventBus.subscribe<ProjectsDiscoveredEvent>(EventType.PROJECTS_DISCOVERED, event => {
        this.container.statusBarService.info(`Discovered ${event.projects.length} projects`);
      })
    );

    this.disposables.push(
      eventBus.subscribe<ProjectChangedEvent>(EventType.PROJECT_CHANGED, event => {
        this.container.statusBarService.info(`Project ${event.project.name} changed`);
      })
    );
  }

  /**
   * Initialize services in the correct order.
   */
  private async initializeServices(): Promise<void> {
    // Step 1: Sync with Gradle (foundation for everything else)
    console.log("🔧 Syncing with Gradle...");
    const gradleSynced = await this.container.gradleService.sync();

    if (!gradleSynced) {
      this.container.statusBarService.warning("Gradle sync failed - some features may be limited");

      // Show user-friendly error with actions
      const action = await window.showWarningMessage(
        "Gradle synchronization failed. Some features may not work properly.",
        "Install Gradle Extension",
        "Retry",
        "Continue Anyway"
      );

      if (action === "Install Gradle Extension") {
        commands.executeCommand("workbench.extensions.installExtension", "vscjava.vscode-gradle");
        return; // Exit early, let user install and restart
      } else if (action === "Retry") {
        await this.container.gradleService.sync();
      }
      // Continue with "Continue Anyway" or no selection
    }

    // Step 2: Start Language Server
    console.log("🚀 Starting Language Server...");
    const lspStarted = await this.container.languageServerManager.start();

    if (!lspStarted) {
      this.container.statusBarService.warning("Language server failed to start");
    }

    // Step 3: Discover projects
    console.log("🔍 Discovering projects...");
    await this.container.projectService.discoverProjects();
  }

  /**
   * Perform final health check and report status.
   */
  private async performHealthCheck(): Promise<void> {
    const health = await this.container.healthCheck();

    if (health.healthy) {
      this.container.statusBarService.ready("Grails extension ready");

      // Show success message with project count
      const projectCount = this.container.projectService.getProjects().length;
      const message =
        projectCount > 0
          ? `🎉 Grails Framework Support is ready! ${projectCount} project${projectCount === 1 ? "" : "s"} detected.`
          : "🎉 Grails Framework Support is ready!";

      window.showInformationMessage(message);
    } else {
      this.container.statusBarService.warning("Extension ready with issues");

      window
        .showWarningMessage(
          `Grails extension activated with issues: ${health.issues.join(", ")}`,
          "Show Details"
        )
        .then(selection => {
          if (selection === "Show Details") {
            // Could open output channel or show detailed info
            console.log("Health issues:", health.issues);
          }
        });
    }
  }

  /**
   * Helper to register a command with disposal tracking.
   */
  private registerCommand(command: string, callback: (...args: any[]) => any): void {
    const disposable = commands.registerCommand(command, callback);
    this.disposables.push(disposable);
    this.context.subscriptions.push(disposable);
  }

  /**
   * Cleanup on extension deactivation.
   */
  dispose(): void {
    this.commands.dispose();
    this.disposables.forEach(d => d.dispose());
    this.disposables = [];
    this.container.dispose();
  }
}
