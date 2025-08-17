import { window } from "vscode";
import { StatusBarService } from "./StatusBarService";
import { ERROR_SEVERITY, GRAILS_MESSAGE, MODULE_TYPE } from "../utils/Constants";
import { formatTemplate } from "../utils/TemplateUtils";

interface ErrorDetails {
  message: string;
  severity: ERROR_SEVERITY;
  source: MODULE_TYPE;
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
  public handle(
    error: unknown,
    source: MODULE_TYPE,
    severity: ERROR_SEVERITY = ERROR_SEVERITY.ERROR,
  ): void {
    const details = this.createErrorDetails(error, source, severity);
    this.log(details);
    this.notify(details);
    
    // Update status bar based on severity
    switch (severity) {
      case ERROR_SEVERITY.ERROR:
      case ERROR_SEVERITY.FATAL:
        this.statusBarService.error(source, this.statusBarMessage(source, details.message));
        break;
      case ERROR_SEVERITY.WARNING:
        this.statusBarService.warning(source, details.message);
        break;
      case ERROR_SEVERITY.INFO:
        this.statusBarService.info(source, details.message);
        break;
    }
  }

  /** Converts unknown error input into structured ErrorDetails. */
  private createErrorDetails(
    error: unknown,
    source: MODULE_TYPE,
    severity: ERROR_SEVERITY,
  ): ErrorDetails {
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
  private suggestions(source: MODULE_TYPE): string[] {
    switch (source) {
      case MODULE_TYPE.GRADLE:
        return [
          "Check Gradle configuration in build.gradle",
          "Verify Gradle wrapper version",
          "Try restarting the Language Server",
          "Check server logs for more details",
        ];
      case MODULE_TYPE.SERVER:
        return ["Check server configuration"];
      case MODULE_TYPE.CLIENT:
        return ["Check client configuration"];
      case MODULE_TYPE.PROJECT:
        return ["Verify project structure"];
      case MODULE_TYPE.EXTENSION:
        return ["Try reloading the extension"];
      default:
        return [];
    }
  }

  /** Logs to internal memory and console. */
  private log(details: ErrorDetails): void {
    this.errorLog.push(details);
    console.error(
      `[${details.timestamp.toISOString()}] [${details.source}] [${details.severity}]: ${
        details.message
      }`,
    );
  }

  /** Shows a VS Code notification with appropriate severity. */
  private notify(details: ErrorDetails): void {
    const message = `${details.source}: ${details.message}`;
    switch (details.severity) {
      case ERROR_SEVERITY.ERROR:
      case ERROR_SEVERITY.FATAL:
        window.showErrorMessage(message, ...details.suggestions);
        break;
      case ERROR_SEVERITY.WARNING:
        window.showWarningMessage(message, ...details.suggestions);
        break;
      case ERROR_SEVERITY.INFO:
        window.showInformationMessage(message);
        break;
    }
  }

  /** Returns an appropriate status bar message based on the error source. */
  private statusBarMessage(source: MODULE_TYPE, message: string): string {
    switch (source) {
      case MODULE_TYPE.SERVER:
        return formatTemplate(GRAILS_MESSAGE.SERVER_ERROR, message);
      case MODULE_TYPE.CLIENT:
        return formatTemplate(GRAILS_MESSAGE.CLIENT_ERROR, message);
      case MODULE_TYPE.GRADLE:
        return formatTemplate(GRAILS_MESSAGE.GRADLE_ERROR, message);
      case MODULE_TYPE.PROJECT:
      case MODULE_TYPE.EXTENSION:
      default:
        return formatTemplate(GRAILS_MESSAGE.SERVER_ERROR, message);
    }
  }
}
