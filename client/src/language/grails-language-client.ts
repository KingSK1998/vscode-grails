import { ExtensionContext, ProgressLocation, window } from "vscode";
import { LanguageClient } from "vscode-languageclient/node";
import { getClientOptions } from "./clientOptions";
import { getServerOptions } from "./serverOptions";
import { ErrorSeverity, GrailsMessage, ModuleType } from "../utils/constants";
import { StatusBarService } from "../services/StatusBarService";
import { ErrorService } from "../services/ErrorService";

type WorkDoneProgressKind = "begin" | "report" | "end";

interface WorkDoneProgressBase {
  kind: WorkDoneProgressKind;
  message?: string;
  percentage?: number;
  title?: string;
}

export class LanguageServerManager {
  private client: LanguageClient | undefined;

  constructor(
    private readonly context: ExtensionContext,
    private readonly statusBarService: StatusBarService,
    private readonly errorService: ErrorService,
    private readonly gradleAvailable: boolean = false,
  ) {}

  async start(): Promise<LanguageClient | undefined> {
    const clientOptions = getClientOptions();
    const serverOptions = getServerOptions(this.context); // Or pass IS_REMOTE_SERVER flag

    return window.withProgress(
      {
        location: ProgressLocation.Notification,
        title: GrailsMessage.SERVER_STARTUP,
        cancellable: false,
      },
      async (progress) => {
        try {
          this.client = new LanguageClient(
            "grailsFrameworkSupport",
            "Grails Framework Support",
            serverOptions,
            clientOptions,
          );
          // Start the language server
          await this.client.start();

          // Register progress notifications BEFORE server is fully ready
          this.client.onNotification(
            "$/progress",
            (params: { token: string; value: WorkDoneProgressBase }) => {
              if (params.token !== "GLS-SERVER-SETUP") {
                return;
              }

              this.handleServerProgress(params.value, progress);
            },
          );

          // Register server message notifications
          this.client.onNotification("window/showMessage", (params: { type: number; message: string }) => {
            this.handleServerMessage(params);
          });

          // Don't set ready state here - let progress "end" handle it
          return this.client;
        } catch (error) {
          this.errorService.handle(error, ModuleType.SERVER, ErrorSeverity.FATAL);
          throw error; // Rethrow the error to be caught in the catch block
        }
      },
    );
  }

  private handleServerProgress(
    value: WorkDoneProgressBase,
    progress: { report: (info: { message?: string; increment?: number }) => void },
  ) {
    const { kind, message, percentage, title } = value;

    switch (kind) {
      case "begin":
        progress.report({
          message: title ? `${title}: ${message}` : message,
          increment: percentage ?? 0,
        });
        this.statusBarService.sync(ModuleType.SERVER, message ?? "Starting...");
        break;

      case "report":
        progress.report({
          message,
          increment: percentage ?? 0,
        });
        this.statusBarService.sync(ModuleType.SERVER, message ?? "Working...");
        break;

      case "end":
        progress.report({
          message: message ?? "Completed",
          increment: percentage ?? 100,
        });
        this.statusBarService.ready(ModuleType.SERVER, GrailsMessage.SERVER_STARTED);
        break;
    }
  }

  private handleServerMessage(params: { type: number; message: string }): void {
    const { type, message } = params;

    // MessageType enum from LSP: Error=1, Warning=2, Info=3, Log=4
    // Use ErrorService for proper handling (it will show notifications and update status bar)
    switch (type) {
      case 1: // Error
        this.errorService.handle(new Error(message), ModuleType.SERVER, ErrorSeverity.ERROR);
        break;
      case 2: // Warning
        this.errorService.handle(new Error(message), ModuleType.SERVER, ErrorSeverity.WARNING);
        break;
      case 3: // Info
        this.errorService.handle(new Error(message), ModuleType.SERVER, ErrorSeverity.INFO);
        break;
      case 4: // Log
        console.log(`Grails Language Server: ${message}`);
        break;
      default:
        console.log(`Unknown message type ${type}: ${message}`);
    }
  }

  async stop(): Promise<void> {
    if (this.client) {
      await this.client.stop();
      this.client = undefined;
    }
  }

  // Add dispose method
  async dispose(): Promise<void> {
    await this.stop();
    this.client = undefined;
  }
}
