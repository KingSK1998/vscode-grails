import { ErrorService } from "./ErrorService";
import { ErrorSource, ErrorSeverity } from "./errorTypes";

/** Utility functions for common error handling patterns */
export class ErrorHandler {
  /** Handle promise rejections with automatic error reporting */
  static async handleAsync<T>(
    promise: Promise<T>,
    errorService: ErrorService,
    source: ErrorSource,
    fallback?: T
  ): Promise<T | undefined> {
    try {
      return await promise;
    } catch (error) {
      errorService.handle(error, source, ErrorSeverity.Error);
      return fallback;
    }
  }

  /** Wrap function calls with error handling */
  static wrapSafe<T>(fn: () => T, errorService: ErrorService, source: ErrorSource): T | undefined {
    try {
      return fn();
    } catch (error) {
      errorService.handle(error, source, ErrorSeverity.Error);
      return undefined;
    }
  }
}
