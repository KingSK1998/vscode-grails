import { LanguageServerManager } from "../../services/languageServer/LanguageServerManager";
import { ProjectService } from "../../services/workspace/ProjectService";
import { ConfigurationService } from "../../services/workspace/ConfigurationService";
import { StatusBarService } from "../../services/workspace/StatusBarService";
import { ErrorService } from "../../services/errors/ErrorService";
import { GradleService } from "../../services/gradle/GradleService";

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
