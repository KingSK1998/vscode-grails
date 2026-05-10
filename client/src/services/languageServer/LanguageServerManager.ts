import type { Disposable, ExtensionContext } from "vscode";
import { ProgressLocation, window } from "vscode";
import { Trace } from "vscode-languageclient";
import { LanguageClient } from "vscode-languageclient/node";
import { EventBus } from "../../core/events/EventBus";
import { EventType } from "../../core/events/eventTypes";
import type { ProjectDTO } from "../../shared/protocol/project";
import { Messages } from "../../utils/constants";
import type { ErrorService } from "../errors/ErrorService";
import { ErrorSeverity, ErrorSource } from "../errors/errorTypes";
import type { ConfigurationService } from "../workspace/ConfigurationService";
import type { StatusBarService } from "../workspace/StatusBarService";
import { getClientOptions } from "./clientConfig";
import type { WorkDoneProgress } from "./languageServerTypes";
import { getServerOptions } from "./serverConfig";

/**
 * Manages the Grails Language Server lifecycle with proper progress reporting
 * and error handling integration.
 */
export class LanguageServerManager implements Disposable {
  private client: LanguageClient | undefined;
  private disposables: Disposable[] = [];

  constructor(
    private readonly context: ExtensionContext,
    private readonly statusBar: StatusBarService,
    private readonly errors: ErrorService,
    private readonly config: ConfigurationService
  ) {}

  /* ================= LIFECYCLE MANAGEMENT ======================= */

  async start(): Promise<LanguageClient | undefined> {
    if (this.client) {
      return this.client; // Already started
    }

    try {
      this.statusBar.sync(Messages.SERVER_STARTING);

      return await window.withProgress(
        {
          location: ProgressLocation.Notification,
          title: Messages.SERVER_STARTING,
          cancellable: false,
        },
        async progress => {
          // Get server configurations
          const clientOptions = getClientOptions(this.config);
          const serverOptions = getServerOptions(this.context, this.config);

          // Create the language client
          this.client = new LanguageClient(
            "grailsLanguageServer",
            "Grails Language Server",
            serverOptions,
            clientOptions
          );

          // Set trace level from configuration
          const traceLevel = this.config.traceLevel;
          void this.client.setTrace(this.mapTraceLevel(traceLevel));

          // Register handlers BEFORE starting the client
          this.registerProgressHandler(progress);
          this.registerMessageHandler();

          // Now start the client - progress events will be captured!
          await this.client.start();

          // ✅ Register Grails custom notifications AFTER start
          this.registerProjectsSyncHandlers();

          this.statusBar.success(Messages.SERVER_STARTED);

          return this.client;
        }
      );
    } catch (error) {
      this.errors.handleError(
        Messages.SERVER_START_FAILED,
        error,
        ErrorSource.LanguageServer,
        ErrorSeverity.Critical
      );
      return undefined;
    }
  }

  /* ================= PROGRESS & MESSAGE HANDLING =============== */

  /**
   * Register progress handler BEFORE client starts.
   * This ensures we catch all server progress events.
   */
  private registerProgressHandler(progress: {
    report: (info: { message?: string; increment?: number }) => void;
  }): void {
    if (!this.client) {
      console.log("[LSP Progress] No client to register progress handler");
      return;
    }

    // Listen for server-initiated progress
    const progressDisposable = this.client.onNotification(
      "$/progress",
      (params: { token: string; value: WorkDoneProgress }) => {
        if (params.token === "GLS-SERVER-SETUP" || params.token.startsWith("grails-")) {
          this.handleProgressNotification(params.value, progress);
        }
      }
    );

    this.disposables.push(progressDisposable);
  }

  private handleProgressNotification(
    value: WorkDoneProgress,
    progress: { report: (info: { message?: string; increment?: number }) => void }
  ): void {
    switch (value.kind) {
      case "begin": {
        const beginMessage = value.message ?? (value.title || "Starting...");
        progress.report({
          message: beginMessage,
          increment: value.percentage ?? 0,
        });
        this.statusBar.sync(beginMessage);
        console.log(`[LSP Progress] Begin: ${beginMessage}`);
        break;
      }

      case "report":
        if (value.message) {
          progress.report({
            message: value.message,
            increment: value.percentage ?? 0,
          });
          this.statusBar.sync(value.message);
          console.log(`[LSP Progress] Report: ${value.message} (${value.percentage ?? 0}%)`);
        }
        break;

      case "end": {
        const endMessage = value.message ?? Messages.SERVER_STARTED;
        progress.report({
          message: endMessage,
          increment: 100,
        });
        this.statusBar.ready(Messages.SERVER_STARTED);
        console.log(`[LSP Progress] End: ${endMessage}`);
        break;
      }
    }
  }

  private registerMessageHandler(): void {
    if (!this.client) {
      return;
    }

    const messageDisposable = this.client.onNotification(
      "window/showMessage",
      (params: { type: number; message: string }) => {
        this.handleServerMessage(params.type, params.message);
      }
    );

    this.disposables.push(messageDisposable);
  }

  private registerProjectsSyncHandlers(): void {
    if (!this.client) return;

    const eventBus = EventBus.getInstance();

    // 🔥 FULL PROJECT SYNC
    const fullSyncDisposable = this.client.onNotification(
      "grails/projectsFullSync",
      (projects: ProjectDTO[]) => {
        console.log("📦 LSP FULL SYNC:", projects.length);

        eventBus.publish({
          type: EventType.PROJECTS_DISCOVERED,
          projects,
          source: "LSP",
          timestamp: Date.now(),
        });
      }
    );

    // 🔥 SINGLE PROJECT UPDATE
    const updateDisposable = this.client.onNotification(
      "grails/projectUpdated",
      (project: ProjectDTO) => {
        console.log("🔄 LSP PROJECT UPDATED:", project.name);

        eventBus.publish({
          type: EventType.PROJECT_CHANGED,
          project,
          source: "LSP",
          timestamp: Date.now(),
        });
      }
    );

    this.disposables.push(fullSyncDisposable, updateDisposable);
  }

  private handleServerMessage(type: number, message: string): void {
    // MessageType enum from LSP: Error=1, Warning=2, Info=3, Log=4

    switch (type) {
      case 1:
        this.errors.handleError(
          "[Grails LSP]",
          message,
          ErrorSource.LanguageServer,
          ErrorSeverity.Error
        );
        break;
      case 2:
        this.errors.handleError(
          "[Grails LSP]",
          message,
          ErrorSource.LanguageServer,
          ErrorSeverity.Warning
        );
        break;
      case 3:
        this.errors.handleError(
          "[Grails LSP]",
          message,
          ErrorSource.LanguageServer,
          ErrorSeverity.Info
        );
        break;
      case 4:
        console.log(`[Grails LSP] ${message}`);
        break;
      default:
        console.log(`[Grails LSP] Unknown message type ${type}: ${message}`);
    }
  }

  private mapTraceLevel(configLevel: string): Trace {
    switch (configLevel.toLowerCase()) {
      case "verbose":
        return Trace.Verbose;
      case "messages":
        return Trace.Messages;
      case "off":
      default:
        return Trace.Off;
    }
  }

  async stop(): Promise<void> {
    if (!this.client) {
      return;
    }

    try {
      this.statusBar.sync(Messages.SERVER_STOPPED);
      await this.client.stop();
      this.statusBar.info(Messages.SERVER_STOPPED_SUCCESS);
    } catch (error) {
      this.errors.handleError(
        "Error stopping language server",
        error,
        ErrorSource.LanguageServer,
        ErrorSeverity.Error
      );
    } finally {
      this.client = undefined;
    }
  }

  async restart(): Promise<void> {
    this.statusBar.sync(Messages.EXTENSION_RESTARTING);
    await this.stop();
    const client = await this.start();
    if (!client) {
      this.errors.handleError(
        "Failed to restart language server",
        new Error("Server startup returned undefined"),
        ErrorSource.LanguageServer,
        ErrorSeverity.Critical
      );
    }
  }

  get isRunning(): boolean {
    return !!this.client;
  }

  get languageClient(): LanguageClient | undefined {
    return this.client;
  }

  dispose(): void {
    // Clean up all disposables
    for (const disposable of this.disposables) {
      try {
        disposable.dispose();
      } catch (error) {
        console.warn("Error disposing language server resource:", error);
      }
    }
    this.disposables = [];

    // Stop client if running
    if (this.client) {
      void this.client.stop().catch(error => {
        console.warn("Error stopping language client:", error);
      });
      this.client = undefined;
    }
  }
}
