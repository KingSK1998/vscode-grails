import { Disposable, ExtensionContext, ProgressLocation, window } from "vscode";
import { LanguageClient, Trace } from "vscode-languageclient/node";
import { getClientOptions } from "./clientConfig";
import { getServerOptions } from "./serverConfig";
import { Messages } from "../../utils/constants";
import { ErrorService } from "../errors/ErrorService";
import { StatusBarService } from "../workspace/StatusBarService";
import { ConfigurationService } from "../workspace/ConfigurationService";
import { ErrorSeverity, ErrorSource } from "../errors/errorTypes";
import { MessageType, WorkDoneProgress } from "./languageServerTypes";

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
          const clientOptions = getClientOptions(this.context, this.config);
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
          this.client.setTrace(this.mapTraceLevel(traceLevel));

          // Register handlers BEFORE starting the client
          this.registerProgressHandler(progress);
          this.registerMessageHandler();

          // Now start the client - progress events will be captured!
          await this.client.start();

          this.statusBar.success(Messages.SERVER_STARTED);

          return this.client;
        }
      );
    } catch (error) {
      this.errors.handle(
        `Failed to start langauge server: ${error}`,
        ErrorSource.LanguageServer,
        ErrorSeverity.Critical
      );
      throw undefined;
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
    if (!this.client) return;

    // Listen for server-initiated progress
    const progressDisposable = this.client.onNotification(
      "$/progress",
      (params: { token: string; value: WorkDoneProgress }) => {
        // Handle server setup progress (or any other progress with this token)
        if (params.token === "GLS-SERVER-SETUP" || params.token.startsWith("grails-")) {
          this.handleServerProgress(params.value, progress);
        }
      }
    );

    this.disposables.push(progressDisposable);
  }

  private handleServerProgress(
    value: WorkDoneProgress,
    progress: { report: (info: { message?: string; increment?: number }) => void }
  ): void {
    switch (value.kind) {
      case "begin":
        const beginMessage = value.message || value.title;
        progress.report({
          message: beginMessage,
          increment: value.percentage || 0,
        });
        this.statusBar.sync(beginMessage);
        console.log(`[LSP Progress] Begin: ${beginMessage}`);
        break;

      case "report":
        if (value.message) {
          progress.report({
            message: value.message,
            increment: value.percentage,
          });
          this.statusBar.sync(value.message);
          console.log(`[LSP Progress] Report: ${value.message} (${value.percentage || 0}%)`);
        }
        break;

      case "end":
        const endMessage = value.message || "Server ready";
        progress.report({
          message: endMessage,
          increment: 100,
        });
        this.statusBar.ready("Language server ready");
        console.log(`[LSP Progress] End: ${endMessage}`);
        break;
    }
  }

  private registerMessageHandler(): void {
    if (!this.client) return;

    const messageDisposable = this.client.onNotification(
      "window/showMessage",
      (params: { type: number; message: string }) => {
        this.handleServerMessage(params.type, params.message);
      }
    );

    this.disposables.push(messageDisposable);
  }

  private handleServerMessage(type: number, message: string): void {
    // MessageType enum from LSP: Error=1, Warning=2, Info=3, Log=4
    // Use ErrorService for proper handling (it will show notifications and update status bar)
    switch (type) {
      case MessageType.Error:
        this.errors.handle(message, ErrorSource.LanguageServer, ErrorSeverity.Error);
        break;
      case MessageType.Warning:
        this.errors.handle(message, ErrorSource.LanguageServer, ErrorSeverity.Warning);
        break;
      case MessageType.Info:
        this.errors.handle(message, ErrorSource.LanguageServer, ErrorSeverity.Info);
        break;
      case MessageType.Log:
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
    if (!this.client) return;

    try {
      this.statusBar.sync("Stopping Grails Language Server...");
      await this.client.stop();
      this.statusBar.info("Language server stopped");
    } catch (error) {
      this.errors.handle(
        `Error stopping language server: ${error}`,
        ErrorSource.LanguageServer,
        ErrorSeverity.Error
      );
    } finally {
      this.client = undefined;
    }
  }

  async restart(): Promise<void> {
    this.statusBar.sync("Restarting Grails Language Server...");
    await this.stop();
    await this.start();
  }

  get isRunning(): boolean {
    return !!this.client;
  }

  get languageClient(): LanguageClient | undefined {
    return this.client;
  }

  dispose(): void {
    this.disposables.forEach(d => d.dispose());
    this.disposables = [];

    if (this.client) {
      this.client.stop();
      this.client = undefined;
    }
  }
}
