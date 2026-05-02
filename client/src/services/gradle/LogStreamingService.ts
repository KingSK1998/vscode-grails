import type { Disposable, OutputChannel } from "vscode";
import { window } from "vscode";
import type { ProjectInfo } from "../../features/models/modelTypes";
import type { ErrorService } from "../errors/ErrorService";
import { ErrorSeverity, ErrorSource } from "../errors/errorTypes";
import type { StatusBarService } from "../workspace/StatusBarService";
import type { GradleService } from "./GradleService";
import { GrailsTask } from "./gradleTypes";

/**
 * Service dedicated to streaming Grails logs and Gradle output
 * to VS Code Output Channels.
 */
export class LogStreamingService implements Disposable {
  private outputChannel: OutputChannel | undefined;
  private isStreaming = false;

  constructor(
    private readonly gradleService: GradleService,
    private readonly statusBarService: StatusBarService,
    private readonly errorService: ErrorService
  ) {}

  private getOrOutputChannel(): OutputChannel {
    this.outputChannel ??= window.createOutputChannel("Grails Logs");
    return this.outputChannel;
  }

  /**
   * Starts the Grails application and streams its output to the 'Grails Logs' channel.
   */
  async startAppWithTailing(projectInfo: ProjectInfo): Promise<void> {
    if (this.isStreaming) {
      window.showInformationMessage("An application is already running with log tailing.");
      const channel = this.getOrOutputChannel();
      channel.show();
      return;
    }

    const channel = this.getOrOutputChannel();
    channel.clear();
    channel.show();

    const timestamp = new Date().toLocaleTimeString();
    channel.appendLine(
      `[${timestamp}] 🚀 Initializing log stream for project: ${projectInfo.name}`
    );
    channel.appendLine(`[${timestamp}] 📁 Path: ${projectInfo.rootPath}`);
    channel.appendLine(
      "--------------------------------------------------------------------------------"
    );

    try {
      this.isStreaming = true;
      this.statusBarService.sync("🚀 Booting Grails...");

      // Execute task with onOutputProxy subscription
      const success = await this.gradleService.runTask(
        projectInfo,
        GrailsTask.RunApp,
        [],
        message => {
          // Stream directly to channel
          channel.append(message);
        }
      );

      if (success) {
        channel.appendLine(`\n[${new Date().toLocaleTimeString()}] ✅ App session completed.`);
      }
    } catch (error) {
      this.errorService.handleError(
        "Log stream failed",
        error,
        ErrorSource.GradleService,
        ErrorSeverity.Error
      );
      channel.appendLine(`\n[ERROR] ${error instanceof Error ? error.message : String(error)}`);
    } finally {
      this.isStreaming = false;
      this.statusBarService.ready("App stopped");
    }
  }

  /**
   * Shows the existing logs channel without starting a new task.
   */
  showLogs(): void {
    this.getOrOutputChannel().show();
  }

  dispose(): void {
    this.outputChannel?.dispose();
    this.outputChannel = undefined;
    this.isStreaming = false;
  }
}
