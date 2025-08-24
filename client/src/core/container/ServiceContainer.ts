import { Disposable, ExtensionContext } from "vscode";
import { ErrorService } from "../../services/errors/ErrorService";
import { StatusBarService } from "../../services/workspace/StatusBarService";
import { LanguageServerManager } from "../../services/languageServer/LanguageServerManager";
import { GradleService } from "../../services/gradle/GradleService";
import { ConfigurationService } from "../../services/workspace/ConfigurationService";
import { ProjectService } from "../../services/workspace/ProjectService";
import { ServiceRegistry, ServiceName } from "./ServiceRegistry";

export class ServiceContainer {
  private static _instance: ServiceContainer;
  private readonly _services: Partial<ServiceRegistry> = {};

  private constructor(private readonly context: ExtensionContext) {
    this.initializeServices();
  }

  public static intialize(context: ExtensionContext): ServiceContainer {
    if (!ServiceContainer._instance) {
      ServiceContainer._instance = new ServiceContainer(context);
    }
    return ServiceContainer._instance;
  }

  public static getInstance(): ServiceContainer {
    if (!ServiceContainer._instance) {
      throw new Error("ServiceContainer must be initialized first");
    }
    return ServiceContainer._instance;
  }

  /* ================================ SERVICE ACCESS =============================== */

  /** Get ErrorService - always available */
  get errorService(): ErrorService {
    return this._services.ErrorService!;
  }

  /** Get StatusBarService - always available */
  get statusBarService(): StatusBarService {
    return this._services.StatusBarService!;
  }

  /** Get ConfigurationService - always available */
  get configurationService(): ConfigurationService {
    return this._services.ConfigurationService!;
  }

  /** Get ProjectService - always available */
  get projectService(): ProjectService {
    return this._services.ProjectService!;
  }

  /** Get GradleService - always available */
  get gradleService(): GradleService {
    return this._services.GradleService!;
  }

  /** Get LanguageServerManager - always available */
  get languageServerManager(): LanguageServerManager {
    return this._services.LanguageServerManager!;
  }

  /* ================= GENERIC ACCESS (for special cases) ============ */

  /** Generic getter - only use if the specific getter doesn't exist */
  get<K extends ServiceName>(name: K): ServiceRegistry[K] {
    const service = this._services[name];
    if (!service) {
      throw new Error(`Service "${name}" not found or not initialized`);
    }
    return service;
  }

  /* ================= INITIALIZATION ================================= */

  private initializeServices(): void {
    // Phase 1: Core services (no dependencies)
    this._services.ErrorService = new ErrorService();
    this._services.StatusBarService = new StatusBarService(this.context);
    this._services.ConfigurationService = new ConfigurationService();

    // Phase 2: Services with dependencies
    this._services.GradleService = new GradleService(
      this._services.StatusBarService!,
      this._services.ErrorService!
    );

    this._services.ProjectService = new ProjectService(
      this._services.StatusBarService!,
      this._services.ErrorService!,
      this._services.ConfigurationService!
    );

    this._services.LanguageServerManager = new LanguageServerManager(
      this.context,
      this._services.StatusBarService!,
      this._services.ErrorService!,
      this._services.ConfigurationService!
    );
  }

  /**
   * Verify all services are properly initialized and ready.
   */
  async healthCheck(): Promise<{ healthy: boolean; issues: string[] }> {
    const issues: string[] = [];

    // Check core services
    // Check core services
    if (!this._services.ErrorService) issues.push("ErrorService not initialized");
    if (!this._services.StatusBarService) issues.push("StatusBarService not initialized");
    if (!this._services.ConfigurationService) issues.push("ConfigurationService not initialized");

    // Check dependent services
    if (!this._services.GradleService) issues.push("GradleService not initialized");
    if (!this._services.ProjectService) issues.push("ProjectService not initialized");
    if (!this._services.LanguageServerManager) issues.push("LanguageServerManager not initialized");

    // Test service readiness
    try {
      const gradleReady = this._services.GradleService?.isReady ?? false;
      const lspReady = this._services.LanguageServerManager?.isRunning ?? false;

      if (!gradleReady) issues.push("Gradle API not ready");
      if (!lspReady) issues.push("Language Server not running");
    } catch (error) {
      issues.push(`Health check failed: ${error}`);
    }

    return {
      healthy: issues.length === 0,
      issues,
    };
  }

  /* ================= CLEANUP ======================================== */

  dispose(): void {
    const services = Object.values(this._services) as Array<Disposable | undefined>;
    services.forEach(service => {
      if (service && "dispose" in service && typeof service.dispose === "function") {
        service.dispose();
      }
    });

    (Object.keys(this._services) as ServiceName[]).forEach(key => {
      delete this._services[key];
    });
  }
}
