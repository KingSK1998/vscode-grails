import * as vscode from "vscode";
import * as util from "util";
import { ErrorService } from "./ErrorService";
import { ErrorSeverity, GRADLE_EXTENSION_ID, GrailsMessage, ModuleType } from "../utils/constants";
import { StatusBarService } from "./StatusBarService";
import { Api, GradleTaskResult } from "./gradleApiInterfaces/GradleApiInterface";

export class GradleService {
  private gradleApi: Api | undefined;

  constructor(private readonly statusBar: StatusBarService, private readonly errorService: ErrorService) {}

  /**
   * Initialize the Gradle API by activating the vscode-gradle extension.
   */
  async sync(): Promise<boolean> {
    try {
      this.statusBar.sync(ModuleType.GRADLE, GrailsMessage.GRADLE_API_INITIALIZING);

      const gradleExtension = vscode.extensions.getExtension(GRADLE_EXTENSION_ID);

      if (!gradleExtension) {
        this.reportGradleError(GrailsMessage.GRADLE_EXTENSION_MISSING);
        return false;
      }

      if (!gradleExtension.isActive) {
        await gradleExtension.activate();
      }

      const gradleApi = gradleExtension.exports;
      if (!gradleApi) {
        this.reportGradleError(GrailsMessage.GRADLE_API_NOT_AVAILABLE);
        return false;
      }

      this.gradleApi = gradleApi;

      this.statusBar.sync(ModuleType.GRADLE, GrailsMessage.GRADLE_SYNC_START);

      return await vscode.window.withProgress(
        {
          location: vscode.ProgressLocation.Notification,
          title: GrailsMessage.GRADLE_SYNC_PROGRESS_TITLE,
        },
        () =>
          new Promise<boolean>((resolve) => {
            const taskProvider = this.gradleApi?.getTaskProvider();

            if (!taskProvider || typeof taskProvider.onDidLoadTasks !== "function") {
              this.statusBar.warning(ModuleType.GRADLE, GrailsMessage.GRADLE_TASK_PROVIDER_MISSING);
              resolve(false);
              return;
            }

            const disposable = taskProvider.onDidLoadTasks((tasks: vscode.Task[]) => {
              console.log(GrailsMessage.GRADLE_TASKS_LOADED, tasks);
              disposable.dispose();
              this.statusBar.info(ModuleType.GRADLE, GrailsMessage.GRADLE_SYNC_COMPLETE);
              resolve(true);
            });

            taskProvider.provideTasks();
          }),
      );
    } catch (error) {
      this.reportGradleError(GrailsMessage.GRADLE_API_NOT_AVAILABLE, error);
      return false;
    }
  }

  /**
   * Run a Gradle task and collect its output.
   */
  async runGradleTask(
    projectFolder: string,
    taskName: string,
    onProgress?: (info: string) => void,
  ): Promise<GradleTaskResult> {
    if (!this.gradleApi) {
      return this.reportGradleError(GrailsMessage.GRADLE_API_NOT_AVAILABLE);
    }

    const outputData: string[] = [];
    const relevantPatterns = [
      /BUILD SUCCESSFUL/,
      /BUILD FAILED/,
      /^> Task /,
      /^:.*$/,
      /FAILURE:/,
      /ERROR:/,
      /Starting a Gradle Daemon/,
      /UP-TO-DATE/,
      /SKIPPED/,
      /EXECUTED/,
    ];

    const handleGradleOutput = (output: any) => {
      const message = new util.TextDecoder("utf-8").decode(output.getOutputBytes_asU8());
      if (message) {
        // Only collect and report relevant lines
        if (relevantPatterns.some((pattern) => pattern.test(message))) {
          outputData.push(message);
          if (onProgress) {
            onProgress(message);
          }
        }
      }
    };

    try {
      this.statusBar.sync(ModuleType.GRADLE, this.formatMessage(GrailsMessage.GRADLE_TASK_STARTED, taskName));

      await this.gradleApi.runTask({
        projectFolder,
        taskName,
        showOutputColors: true,
        onOutput: handleGradleOutput,
      });

      this.statusBar.success(
        ModuleType.GRADLE,
        this.formatMessage(GrailsMessage.GRADLE_TASK_SUCCESS, taskName),
      );

      return {
        success: true,
        message: this.formatMessage(GrailsMessage.GRADLE_TASK_SUCCESS, taskName),
        data: outputData,
      };
    } catch (error) {
      return this.reportGradleError(this.formatMessage(GrailsMessage.GRADLE_TASK_FAILED, taskName), error);
    }
  }

  /**
   * Handle errors by logging them and using the error service.
   */
  private reportGradleError(message: string, error?: unknown): GradleTaskResult {
    const errorMessage = error instanceof Error ? error.message : String(error ?? "");
    const fullMessage = error ? `${message}: ${errorMessage}` : message;

    this.errorService.handle(fullMessage, ModuleType.GRADLE, ErrorSeverity.ERROR);
    this.statusBar.error(ModuleType.GRADLE, fullMessage);
    return { success: false, message: fullMessage };
  }

  /**
   * Helper to format messages with placeholders.
   */
  private formatMessage(template: string, ...args: string[]): string {
    return template.replace(/{(\d+)}/g, (match, index) => args[index] ?? match);
  }
}
