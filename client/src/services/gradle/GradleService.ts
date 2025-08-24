import { Disposable, extensions, window, ProgressLocation, tasks } from "vscode";
import { ErrorService } from "../errors/ErrorService";
import { Api, GrailsTask, RunTaskOpts } from "./gradleTypes";
import { StatusBarService } from "../workspace/StatusBarService";
import { ErrorSeverity, ErrorSource } from "../errors/errorTypes";
import { ProjectInfo, ProjectType } from "../../features/models/modelTypes";

/**
 * Grails-focused Gradle service that enhances the existing vscode-gradle extension
 * with Grails-specific shortcuts and integrations.
 */
export class GradleService implements Disposable {
  private static readonly GRADLE_EXTENSION_ID = "vscjava.vscode-gradle";
  private gradleApi: Api | undefined;
  private isInitialized = false;

  constructor(
    private readonly statusBar: StatusBarService,
    private readonly errors: ErrorService
  ) {}

  /* ================= CORE SYNC FUNCTIONALITY =================== */

  /**
   * Initialize Gradle API and wait for task provider to be ready.
   * Shows progress and ensures full synchronization with vscode-gradle.
   */
  async sync(): Promise<boolean> {
    if (this.isInitialized && this.gradleApi) {
      return true; // Already synced
    }

    try {
      this.statusBar.sync("Initializing Gradle API...");

      // 1. Get the Gradle extension
      const extension = extensions.getExtension(GradleService.GRADLE_EXTENSION_ID);
      if (!extension) {
        return this.fail("Gradle extension not found. Please install 'Gradle for Java' extension.");
      }

      // 2. Activate if needed
      if (!extension.isActive) {
        this.statusBar.sync("Activating Gradle extension...");
        await extension.activate();
      }

      // 3. Get the API
      this.gradleApi = extension.exports as Api;
      if (!this.gradleApi) {
        return this.fail("Gradle API not available");
      }

      // 4. Wait for task provider to be fully loaded
      this.statusBar.sync("Syncing with Gradle projects...");

      const syncSuccess = await window.withProgress(
        {
          location: ProgressLocation.Notification,
          title: "Synchronizing Gradle projects",
          cancellable: false,
        },
        () => this.waitForTaskProviderReady()
      );

      if (syncSuccess) {
        this.isInitialized = true;
        this.statusBar.success("Gradle synchronization complete");
        return true;
      } else {
        return this.fail("Gradle task provider not available");
      }
    } catch (error) {
      return this.fail("Failed to sync with Gradle", error);
    }
  }

  private waitForTaskProviderReady(): Promise<boolean> {
    return new Promise<boolean>(resolve => {
      const provider = this.gradleApi?.getTaskProvider?.();
      if (!provider || typeof provider.onDidLoadTasks !== "function") {
        resolve(false);
        return;
      }

      const disposable = provider.onDidLoadTasks((tasks: any[]) => {
        console.log(`[GradleService] Tasks loaded: ${tasks.length} tasks found`);
        disposable.dispose();
        resolve(true);
      });

      provider.provideTasks();

      // Timeout after 30 seconds
      setTimeout(() => {
        disposable.dispose();
        resolve(false);
      }, 30000);
    });
  }

  /* ================= TASK EXECUTION ============================= */

  /**
   * Run any Gradle task using the vscode-gradle API.
   */
  async runTask(
    projectInfo: ProjectInfo,
    taskName: GrailsTask | string,
    _args: string[] = []
  ): Promise<boolean> {
    const synced = await this.sync();
    if (!synced) {
      this.errors.handle(
        "Cannot run task: Gradle synchronization failed",
        ErrorSource.GradleService,
        ErrorSeverity.Error
      );
      return false;
    }

    try {
      this.statusBar.sync(`Running ${taskName}...`);

      const taskOptions: RunTaskOpts = {
        projectFolder: projectInfo.rootPath,
        taskName: taskName,
        showOutputColors: true,
        onOutput: output => {
          const message = new TextDecoder("utf-8").decode(output.getOutputBytes_asU8());
          if (message.trim()) {
            console.log(`[${taskName}]`, message.trim());
          }
        },
      };

      await this.gradleApi!.runTask(taskOptions);
      this.statusBar.success(`${taskName} completed successfully`);
      return true;
    } catch (error) {
      this.errors.handle(
        `Task ${taskName} failed: ${error}`,
        ErrorSource.GradleService,
        ErrorSeverity.Error
      );
      return false;
    }
  }

  /* ================= GRAILS TASK SHORTCUTS ========================= */

  async runGrailsApp(projectInfo: ProjectInfo): Promise<boolean> {
    if (projectInfo.type !== ProjectType.Grails) {
      this.errors.handle(
        "bootRun is only available for Grails projects",
        ErrorSource.GradleService,
        ErrorSeverity.Warning
      );
      return false;
    }
    return this.runTask(projectInfo, GrailsTask.RunApp);
  }

  async testGrailsApp(projectInfo: ProjectInfo): Promise<boolean> {
    return this.runTask(projectInfo, GrailsTask.TestApp);
  }

  async buildProject(projectInfo: ProjectInfo): Promise<boolean> {
    return this.runTask(projectInfo, GrailsTask.Build);
  }

  async cleanProject(projectInfo: ProjectInfo): Promise<boolean> {
    return this.runTask(projectInfo, GrailsTask.Clean);
  }

  /* ================= UTILITIES =================================== */

  async hasGrailsTasks(projectInfo: ProjectInfo): Promise<boolean> {
    return projectInfo.type === ProjectType.Grails || projectInfo.type === ProjectType.GrailsPlugin;
  }

  get isReady(): boolean {
    return !!this.gradleApi && this.isInitialized;
  }

  private fail(msg: string, err?: unknown): false {
    const full = err ? `${msg}: ${err instanceof Error ? err.message : err}` : msg;
    this.errors.handle(full, ErrorSource.GradleService, ErrorSeverity.Error);
    this.statusBar.error(full);
    return false;
  }

  dispose(): void {
    this.gradleApi = undefined;
    this.isInitialized = false;
  }
}

/** Code Lens:

const gradleService = container.gradleService;
if (await gradleService.hasGrailsTasks(projectInfo)) {
  // Show "Run" lens on controller methods
}

*/

/** For Commands:

// Register commands that use the service
vscode.commands.registerCommand('grails.runApp', async () => {
  const projectInfo = container.projectService.getCurrentProject();
  if (projectInfo) {
    await container.gradleService.runGrailsApp(projectInfo);
  }
});

 */

/**

// In your extension activation
export async function activate(context: ExtensionContext) {
  const container = ServiceContainer.initialize(context);

  // 1. First sync Gradle (required for project analysis)
  const gradleService = container.gradleService;
  const gradleSynced = await gradleService.sync();

  if (!gradleSynced) {
    // Show user-friendly error but don't block extension
    window.showErrorMessage(
      "Gradle synchronization failed. Some features may not work properly.",
      "Install Gradle Extension",
      "Retry"
    ).then(selection => {
      if (selection === "Install Gradle Extension") {
        commands.executeCommand("workbench.extensions.installExtension", "vscjava.vscode-gradle");
      } else if (selection === "Retry") {
        gradleService.sync();
      }
    });
  }

  // 2. Start LSP (it can work with basic functionality even without full Gradle sync)
  const lspManager = container.languageServerManager;
  await lspManager.start();

  // 3. Discover projects (uses Gradle info if available, falls back to heuristics)
  const projectService = container.projectService;
  await projectService.discoverProjects();
}


 */
