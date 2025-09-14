import type { ErrorService } from "../../services/errors/ErrorService";
import type { GradleService } from "../../services/gradle/GradleService";
import type { LanguageServerManager } from "../../services/languageServer/LanguageServerManager";
import type { ConfigurationService } from "../../services/workspace/ConfigurationService";
import type { ProjectService } from "../../services/workspace/ProjectService";
import type { StatusBarService } from "../../services/workspace/StatusBarService";

export interface ServiceRegistry {
  ErrorService: ErrorService;
  StatusBarService: StatusBarService;
  ConfigurationService: ConfigurationService;
  ProjectService: ProjectService;
  GradleService: GradleService;
  LanguageServerManager: LanguageServerManager;
}

/** Service names that match the interface keys exactly */
export type ServiceName = keyof ServiceRegistry;
