import {
  commands,
  Disposable,
  env,
  ExtensionContext,
  extensions,
  Uri,
  window,
  workspace,
} from "vscode";
import { ServiceContainer } from "../container/ServiceContainer";
import { ErrorSeverity, ErrorSource } from "../../services/errors/errorTypes";
import { EventBus } from "../events/EventBus";
import { EventType, ProjectChangedEvent, ProjectsDiscoveredEvent } from "../events/eventTypes";
import { createProjectTreeProvider } from "../../ui/providers/TreeDataProvider";
import { Commands } from "../../ui/commands/Commands";
import { IconThemeDetector } from "../../ui/icons/IconThemeDetector";

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

      // Phase 2: Setup event listeners
      this.setupEventListeners();

      // Phase 3: Initialize core services
      await this.initializeServices();

      // Phase 4: Health check and final setup
      await this.performHealthCheck();

      // Suggest Material Icon Theme if not installed
      await this.suggestMaterialThemeIfNeeded();

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
   * Optionally suggest Material Theme installation
   */
  private async suggestMaterialThemeIfNeeded(): Promise<void> {
    const shouldSuggest = workspace
      .getConfiguration("grails")
      .get<boolean>("icons.suggestMaterialTheme", true);

    if (shouldSuggest && !IconThemeDetector.isMaterialThemeInstalled()) {

      // Don't block activation, just suggest
      setTimeout(() => {
        IconThemeDetector.suggestMaterialTheme();
      }, 2000); // Delay so it doesn't interfere with startup
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

        if (e.affectsConfiguration("workbench.iconTheme")) {
          console.log("🎨 Icon theme changed - refreshing tree view");
          this.refreshTreeView();
        }
      })
    );

    this.disposables.push(
      extensions.onDidChange(() => {
        IconThemeDetector.resetCache();
        // Optionally refresh tree view
        this.refreshTreeView();
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
        console.log(`📂 Projects discovered: ${event.projects.length} projects`);

        this.container.statusBarService.info(`Discovered ${event.projects.length} projects`);

        // Create UI components now that projects are available
        this.setupUIComponents();
      })
    );

    this.disposables.push(
      eventBus.subscribe<ProjectChangedEvent>(EventType.PROJECT_CHANGED, event => {
        console.log(`🔄 Project changed: ${event.project.name}`);
        this.container.statusBarService.info(`Project ${event.project.name} changed`);

        // Potentially recreate UI if project types changed
        this.recreateUIIfNeeded();
      })
    );
  }

  private refreshTreeView(): void {
    // Trigger tree refresh if needed
    const eventBus = EventBus.getInstance();
    eventBus?.publish({
      type: EventType.TREE_REFRESH,
      timestamp: Date.now(),
      source: "IconThemeChange",
    });
  }

  /**
   * Setup UI components only if supported projects exist. Called after project discovery.
   */
  private setupUIComponents(): void {
    try {
      // Create and register tree view
      const treeProvider = createProjectTreeProvider(this.context);
      if (!treeProvider) {
        console.log("❌ No supported projects found in the workspace, skipping UI setup.");

        window
          .showInformationMessage(
            "No Grails or Groovy projects found in the workspace.",
            "Learn More"
          )
          .then(selection => {
            if (selection === "Learn More") {
              env.openExternal(Uri.parse("https://grails.org"));
            }
          });

        return;
      }

      const treeView = window.createTreeView("grailsExplorer", {
        treeDataProvider: treeProvider,
        showCollapseAll: true,
        canSelectMany: false,
      });

      this.context.subscriptions.push(treeView);
      console.log("✅ Tree view registered successfully");

      // Update status bar to show tree view is ready
      this.container.statusBarService.ready("Project Explorer ready");
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
   * Recreate UI if project composition changed (optional)
   */
  private recreateUIIfNeeded(): void {
    // Advanced: If project types changed, recreate tree with different provider
    // For now, existing tree will auto-refresh via its own event listeners
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
   * Cleanup on extension deactivation.
   */
  dispose(): void {
    this.commands.dispose();
    this.disposables.forEach(d => d.dispose());
    this.disposables = [];
    this.container.dispose();
  }
}
