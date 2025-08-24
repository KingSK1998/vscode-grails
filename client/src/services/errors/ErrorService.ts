import { Disposable, OutputChannel, window } from "vscode";
import { StatusBarService } from "../workspace/StatusBarService";
import { ServiceContainer } from "../../core/container/ServiceContainer";
import { ErrorDetails, ErrorSeverity, ErrorSource } from "./errorTypes";

/**
 * Centralized error handler for the Grails extension.
 * Logs to output channel, shows notifications, and updates status bar.
 */
export class ErrorService implements Disposable {
  private readonly _channel: OutputChannel;
  private readonly _log: ErrorDetails[] = [];
  private _disposed = false;

  constructor() {
    this._channel = window.createOutputChannel("Grails");
  }

  /* ================= PUBLIC API ===================================== */

  /**
   * Handle an error with appropriate logging, notification, and status bar update.
   */
  public handle(
    error: unknown,
    source: ErrorSource = ErrorSource.Extension,
    severity: ErrorSeverity = ErrorSeverity.Error
  ): void {
    if (!this._disposed) {
      // Fallback to console if service is disposed
      console.error(`[${source}]`, error);
      return;
    }

    const details = this.createErrorDetails(error, source, severity);
    this._log.push(details);
    this.logToChannel(details);
    this.showNotification(details);
    this.updateStatusBar(details);
  }

  /** Get recent error history (useful for debugging/testing) */
  get history(): readonly ErrorDetails[] {
    return [...this._log];
  }

  /** Get the output channel for direct access */
  get outputChannel(): OutputChannel {
    return this._channel;
  }

  /* ================= DISPOSAL ==================================== */

  dispose(): void {
    this._disposed = true;
    this._channel.dispose();
    this._log.length = 0; // Clear log array
  }

  /* ================= PRIVATE HELPERS ============================= */

  /** Convert unknown error into structured ErrorDetails */
  private createErrorDetails(
    error: unknown,
    source: ErrorSource,
    severity: ErrorSeverity
  ): ErrorDetails {
    const message = error instanceof Error ? error.message : String(error);
    const stack = error instanceof Error ? error.stack : undefined;

    return {
      message,
      severity,
      source,
      timestamp: new Date(),
      stack,
      suggestions: this.getSuggestions(source, severity),
    };
  }

  /** Log error details to the output channel */
  private logToChannel(details: ErrorDetails): void {
    const timestamp = details.timestamp.toISOString();
    const level = details.severity.toUpperCase();

    this._channel.appendLine(`[${timestamp}] [${details.source}] ${level}: ${details.message}`);

    if (details.stack) {
      this._channel.appendLine(details.stack);
    }

    if (details.suggestions.length > 0) {
      this._channel.appendLine(`Suggestions: ${details.suggestions.join(", ")}`);
    }
  }

  /** Show VS Code notification based on severity */
  private showNotification(details: ErrorDetails): void {
    const message = `${details.source}: ${details.message}`;
    const action = details.suggestions.slice(0, 2); // Max 2 buttons

    switch (details.severity) {
      case ErrorSeverity.Critical:
      case ErrorSeverity.Error:
        window.showErrorMessage(message, ...details.suggestions);
        break;
      case ErrorSeverity.Warning:
        window.showWarningMessage(message, ...details.suggestions);
        break;
      case ErrorSeverity.Info:
        window.showInformationMessage(message);
        break;
    }
  }

  /** Update status bar via ServiceContainer (avoiding circular dependency) */
  private updateStatusBar(details: ErrorDetails): void {
    try {
      const container = ServiceContainer.getInstance();
      const statusBar = container.statusBarService;

      switch (details.severity) {
        case ErrorSeverity.Info:
          statusBar.info(details.message);
          break;
        case ErrorSeverity.Warning:
          statusBar.warning(details.message);
          break;
        case ErrorSeverity.Error:
        case ErrorSeverity.Critical:
        default:
          statusBar.error(details.message);
      }
    } catch {
      // ServiceContainer not initialized - this is fine during startup
    }
  }

  /** Get contextual suggestions based on error source */
  private getSuggestions(source: ErrorSource, severity: ErrorSeverity): string[] {
    const commonSuggestions =
      severity === ErrorSeverity.Critical ? ["Reload Window", "Restart Extension"] : [];

    switch (source) {
      case ErrorSource.GradleService:
        return [
          "Check build.gradle",
          "Verify Gradle wrapper",
          "Restart Language Server",
          ...commonSuggestions,
        ];
      case ErrorSource.LanguageServer:
        return ["Check Language Server log", "Restart Language Server", ...commonSuggestions];
      case ErrorSource.ProjectService:
        return ["Validate project structure", "Check workspace folders", ...commonSuggestions];
      case ErrorSource.Configuration:
        return ["Check extension settings", "Reset to defaults", ...commonSuggestions];
      case ErrorSource.Extension:
      default:
        return commonSuggestions;
    }
  }
}
