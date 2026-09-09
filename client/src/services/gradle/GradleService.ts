import type { Disposable, Task } from "vscode";
import { extensions, ProgressLocation, window } from "vscode";
import type { ProjectInfo } from "../../features/models/modelTypes";
import { ProjectType } from "../../features/models/modelTypes";
import type { ErrorService } from "../errors/ErrorService";
import { ErrorSeverity, ErrorSource } from "../errors/errorTypes";
import type { StatusBarService } from "../workspace/StatusBarService";
import type { Api, RunTaskOpts } from "./gradleTypes";
import { GrailsTask } from "./gradleTypes";

/**
 * Grails-focused Gradle service that enhances the existing vscode-gradle extension
 * with Grails-specific shortcuts and integrations.
 */
export class GradleService implements Disposable {
  private static readonly GRADLE_EXTENSION_ID = "vscjava.vscode-gradle";
  private gradleApi: Api | undefined;
  private isInitialized = false;
  private intializationPromise?: Promise<boolean>;

  constructor(
    private readonly statusBarService: StatusBarService,
    private readonly errorService: ErrorService
  ) {}

  /* ================= CORE SYNC FUNCTIONALITY =================== */

  /**
   * Fast sync - returns immediately if already initialized.
   * Otherwise, starts initialization but does not wait for it to complete.
   */
  quickSync(): boolean {
    if (this.isInitialized) {
      return true; // Already synced
    }

    // Start initialization in background if not already started
    this.intializationPromise ??= this.fullSync();

    return false; // Not ready yet, but initializing
  }

  /**
   * Full blocking sync - only calls when actually needed.
   */
  async fullSync(): Promise<boolean> {
    if (this.isInitialized) {
      return true; // Already synced
    }

    try {
      this.statusBarService.sync("🔄 Initializing Gradle...");

      // Add timeout wrapper
      const syncResult = await Promise.race([
        this.performGradleSync(),
        this.createTimeoutPromise(15000), // 15 sec timeout
      ]);

      if (syncResult) {
        this.isInitialized = true;
        this.statusBarService.success("✅ Gradle ready");
        return true;
      } else {
        this.statusBarService.warning("⚠️ Gradle intialization timed out");
        return false;
      }
    } catch (error) {
      this.errorService.handleError(
        "Gradle sync failed",
        error,
        ErrorSource.GradleService,
        ErrorSeverity.Warning
      );
      this.statusBarService.warning("⚠️ Gradle unavailable - limited features");
      return false;
    }
  }

  private async performGradleSync(): Promise<boolean> {
    // Your existing sync logic here
    const extension = extensions.getExtension(GradleService.GRADLE_EXTENSION_ID);
    if (!extension) {
      return false;
    }

    if (!extension.isActive) {
      await extension.activate();
    }

    this.gradleApi = extension.exports as Api;
    return !!this.gradleApi;
  }

  private createTimeoutPromise(timeoutMs: number): Promise<boolean> {
    return new Promise<boolean>(resolve => {
      setTimeout(() => resolve(false), timeoutMs);
    });
  }

  /**
   * Non-blocking sync - starts initialization in background and returns immediately.
   * Use `isReady` to check status or `await sync()` to wait for completion.
   */
  sync(): void {
    if (this.isInitialized && this.gradleApi) {
      return; // Already synced
    }

    if (this.intializationPromise) {
      return; // Already initializing
    }

    this.intializationPromise ??= this.doSync().catch(error => {
      this.errorService.handleError(
        "Gradle sync failed",
        error,
        ErrorSource.GradleService,
        ErrorSeverity.Warning
      );
      return false;
    });
  }

  /**
   * Wait for Gradle sync to complete.
   */
  async waitForSync(): Promise<boolean> {
    if (this.isInitialized && this.gradleApi) {
      return true;
    }

    this.intializationPromise ??= this.doSync().catch(error => {
      this.errorService.handleError(
        "Gradle sync failed",
        error,
        ErrorSource.GradleService,
        ErrorSeverity.Warning
      );
      return false;
    });

    return this.intializationPromise;
  }

  private async doSync(): Promise<boolean> {
    try {
      this.statusBarService.sync("🔄 Initializing Gradle...");

      const extension = extensions.getExtension(GradleService.GRADLE_EXTENSION_ID);
      if (!extension) {
        this.errorService.handleError(
          "Gradle extension not found. Please install 'Gradle for Java' extension.",
          null,
          ErrorSource.GradleService,
          ErrorSeverity.Error
        );
        return false;
      }

      if (!extension.isActive) {
        this.statusBarService.sync("Activating Gradle extension...");
        await extension.activate();
      }

      this.gradleApi = extension.exports as Api;
      if (!this.gradleApi) {
        this.errorService.handleError(
          "Gradle API not available",
          null,
          ErrorSource.GradleService,
          ErrorSeverity.Error
        );
        return false;
      }

      this.statusBarService.sync("Syncing with Gradle projects...");

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
        this.statusBarService.success("✅ Gradle ready");
        return true;
      } else {
        this.errorService.handleError(
          "Gradle task provider not available",
          null,
          ErrorSource.GradleService,
          ErrorSeverity.Error
        );
        return false;
      }
    } catch (error) {
      this.errorService.handleError(
        "Failed to sync with Gradle",
        error,
        ErrorSource.GradleService,
        ErrorSeverity.Error
      );
      return false;
    }
  }

  private async waitForTaskProviderReady(): Promise<boolean> {
    const provider = this.gradleApi?.getTaskProvider?.();
    if (!provider || typeof provider.onDidLoadTasks !== "function") {
      return false;
    }

    const timeoutPromise = new Promise((_, reject) => {
      setTimeout(() => {
        reject(new Error("Timeout waiting for task provider to be ready"));
      }, 30000);
    });

    const readyPromise = new Promise(resolve => {
      const disposable = provider.onDidLoadTasks((tasks: Task[]) => {
        console.log(`[GradleService] Tasks loaded: ${tasks.length} tasks found`);
        disposable.dispose();
        resolve(true);
      });
    });

    await provider.provideTasks();
    const result = await Promise.race([readyPromise, timeoutPromise]);
    return result as boolean;
  }

  /* ================= TASK EXECUTION ============================= */

  /**
   * Run any Gradle task using the vscode-gradle API.
   */
  async runTask(
    projectInfo: ProjectInfo,
    taskName: GrailsTask | string,
    _args: string[] = [],
    onOutputProxy?: (message: string) => void
  ): Promise<boolean> {
    const synced = await this.waitForSync();
    if (!synced) {
      this.errorService.handle(
        "Cannot run task: Gradle synchronization failed",
        ErrorSource.GradleService,
        ErrorSeverity.Error
      );
      return false;
    }

    try {
      this.statusBarService.sync(`Running ${taskName}...`);

      const taskOptions: RunTaskOpts = {
        projectFolder: projectInfo.rootPath,
        taskName: taskName,
        showOutputColors: true,
        onOutput: output => {
          const message = new TextDecoder("utf-8").decode(output.getOutputBytes_asU8());
          if (message.trim()) {
            console.log(`[${taskName}]`, message.trim());
            if (onOutputProxy) {
              onOutputProxy(message);
            }
          }
        },
      };

      await this.gradleApi!.runTask(taskOptions);
      this.statusBarService.success(`${taskName} completed successfully`);
      return true;
    } catch (error) {
      this.errorService.handleError(
        `Task ${taskName} failed`,
        error,
        ErrorSource.GradleService,
        ErrorSeverity.Error
      );
      return false;
    }
  }

  /* ================= GRAILS TASK SHORTCUTS ========================= */

  async runGrailsApp(projectInfo: ProjectInfo): Promise<boolean> {
    if (projectInfo.type !== ProjectType.Grails) {
      this.errorService.handleError(
        "bootRun is only available for Grails projects",
        null,
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

  async buildProject(
    projectInfo: ProjectInfo,
    taskName: string = GrailsTask.Build
  ): Promise<boolean> {
    return this.runTask(projectInfo, taskName);
  }

  async cleanProject(projectInfo: ProjectInfo): Promise<boolean> {
    return this.runTask(projectInfo, GrailsTask.Clean);
  }

  /* ================= UTILITIES =================================== */

  hasGrailsTasks(projectInfo: ProjectInfo): boolean {
    return projectInfo.type === ProjectType.Grails || projectInfo.type === ProjectType.GrailsPlugin;
  }

  get isReady(): boolean {
    return !!this.gradleApi && this.isInitialized;
  }

  dispose(): void {
    this.gradleApi = undefined;
    this.isInitialized = false;
  }
}
