import { window } from "vscode";
import { StatusBarService } from "./StatusBarService";
import { ErrorSeverity, GrailsMessage, ModuleType } from "../utils/Constants";
import { formatTemplate } from "../utils/TemplateUtils";

interface ErrorDetails {
  message: string;
  severity: ErrorSeverity;
  source: ModuleType;
  timestamp: Date;
  stack?: string;
  suggestions: string[];
}

/**
 * Service to handle errors in the Grails extension.
 * It logs errors, shows notifications, and updates the status bar.
 */
export class ErrorService {
  private readonly statusBarService: StatusBarService;
  private readonly errorLog: ErrorDetails[] = [];

  constructor(statusBarService: StatusBarService) {
    this.statusBarService = statusBarService;
  }

  /**
   * Handle and log an error, show notification, and update status bar if needed.
   */
  public handle(error: unknown, source: ModuleType, severity: ErrorSeverity = ErrorSeverity.ERROR): void {
    const details = this.createErrorDetails(error, source, severity);
    this.log(details);
    this.notify(details);

    // Update status bar based on severity
    switch (severity) {
      case ErrorSeverity.ERROR:
      case ErrorSeverity.FATAL:
        this.statusBarService.error(source, this.statusBarMessage(source, details.message));
        break;
      case ErrorSeverity.WARNING:
        this.statusBarService.warning(source, details.message);
        break;
      case ErrorSeverity.INFO:
        this.statusBarService.info(source, details.message);
        break;
    }
  }

  /** Converts unknown error input into structured ErrorDetails. */
  private createErrorDetails(error: unknown, source: ModuleType, severity: ErrorSeverity): ErrorDetails {
    return {
      message: error instanceof Error ? error.message : String(error),
      severity,
      source,
      timestamp: new Date(),
      stack: error instanceof Error ? error.stack : undefined,
      suggestions: this.suggestions(source),
    };
  }

  /** Suggestion hints to show in error notifications. */
  private suggestions(source: ModuleType): string[] {
    switch (source) {
      case ModuleType.GRADLE:
        return [
          "Check Gradle configuration in build.gradle",
          "Verify Gradle wrapper version",
          "Try restarting the Language Server",
          "Check server logs for more details",
        ];
      case ModuleType.SERVER:
        return ["Check server configuration"];
      case ModuleType.CLIENT:
        return ["Check client configuration"];
      case ModuleType.PROJECT:
        return ["Verify project structure"];
      case ModuleType.EXTENSION:
        return ["Try reloading the extension"];
      default:
        return [];
    }
  }

  /** Logs to internal memory and console. */
  private log(details: ErrorDetails): void {
    this.errorLog.push(details);
    console.error(
      `[${details.timestamp.toISOString()}] [${details.source}] [${details.severity}]: ${details.message}`,
    );
  }

  /** Shows a VS Code notification with appropriate severity. */
  private notify(details: ErrorDetails): void {
    const message = `${details.source}: ${details.message}`;
    switch (details.severity) {
      case ErrorSeverity.ERROR:
      case ErrorSeverity.FATAL:
        window.showErrorMessage(message, ...details.suggestions);
        break;
      case ErrorSeverity.WARNING:
        window.showWarningMessage(message, ...details.suggestions);
        break;
      case ErrorSeverity.INFO:
        window.showInformationMessage(message);
        break;
    }
  }

  /** Returns an appropriate status bar message based on the error source. */
  private statusBarMessage(source: ModuleType, message: string): string {
    switch (source) {
      case ModuleType.SERVER:
        return formatTemplate(GrailsMessage.SERVER_ERROR, message);
      case ModuleType.CLIENT:
        return formatTemplate(GrailsMessage.CLIENT_ERROR, message);
      case ModuleType.GRADLE:
        return formatTemplate(GrailsMessage.GRADLE_ERROR, message);
      case ModuleType.PROJECT:
      case ModuleType.EXTENSION:
      default:
        return formatTemplate(GrailsMessage.SERVER_ERROR, message);
    }
  }
}
