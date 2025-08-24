/** Error severity levels */
export enum ErrorSeverity {
  Info = "info",
  Warning = "warning",
  Error = "error",
  Critical = "critical",
}

/** Service types that can report errors */
export enum ErrorSource {
  Extension = "extension",
  GradleService = "gradle-service",
  ProjectService = "project-service",
  LanguageServer = "language-server",
  Configuration = "configuration",
}

/** Shape of an error entry kept in memory */
export interface ErrorDetails {
  message: string;
  severity: ErrorSeverity;
  source: ErrorSource;
  timestamp: Date;
  stack?: string;
  suggestions: string[];
}
