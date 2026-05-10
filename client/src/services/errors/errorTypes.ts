/** Error severity levels */
export enum ErrorSeverity {
  Info = "info",
  Warning = "warning",
  Error = "error",
  Critical = "critical",
}

/** Service types that can report errors */
export enum ErrorSource {
  Extension = "Extension",
  GradleService = "GradleService",
  ProjectService = "ProjectService",
  LanguageServer = "LanguageServer",
  Configuration = "Configuration",
  Commands = "Commands",
  Artifacts = "Artifacts",
  Testing = "Testing",
  UI = "UI",
}

/** Shape of an error entry kept in memory */
export interface ErrorDetails {
  message: string;
  severity: ErrorSeverity;
  source: ErrorSource;
  timestamp: Date;
  stack?: string | undefined;
  suggestions: string[];
}
