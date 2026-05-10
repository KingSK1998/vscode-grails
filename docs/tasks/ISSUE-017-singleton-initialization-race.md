# ISSUE-017 · Singleton Initialization Race
**Severity**: 🔴 Critical
**Service**: ServiceContainer.ts
**Status**: TODO
**Parent Plan**: [client-improvement-plan.md](../client-improvement-plan.md)

## Problem
ServiceContainer.getInstance() throws if not initialized. No initialization promise. Race condition if code calls getInstance() before initialize() completes. This can cause crashes during activation if services are accessed before initialization is complete.

## Current Code (problematic pattern)
```typescript
// ServiceContainer.ts - Lines 26-38
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
```

## Fixed Code
```typescript
// ServiceContainer.ts - Improved singleton with initialization promise

export class ServiceContainer {
  private static _instance: ServiceContainer;
  private static _initializationPromise: Promise<ServiceContainer> | undefined;
  private static _isInitialized = false;
  private readonly _services: Partial<ServiceRegistry> = {};
  private readonly _lazyServices: Partial<Record<ServiceName, () => ServiceRegistry[ServiceName]>> = {};
  private readonly _initializedServices = new Set<ServiceName>();

  private constructor(private readonly context: ExtensionContext) {
    this.initializeServices();
  }

  /**
   * Initialize the ServiceContainer and return a promise.
   * Safe to call multiple times - returns the same promise.
   */
  public static async initialize(context: ExtensionContext): Promise<ServiceContainer> {
    // Return existing promise if already initializing
    if (ServiceContainer._initializationPromise) {
      return ServiceContainer._initializationPromise;
    }

    // Create initialization promise
    ServiceContainer._initializationPromise = (async () => {
      if (!ServiceContainer._instance) {
        console.log("[ServiceContainer] Creating new instance");
        ServiceContainer._instance = new ServiceContainer(context);
      }
      ServiceContainer._isInitialized = true;
      console.log("[ServiceContainer] Initialization complete");
      return ServiceContainer._instance;
    })();

    return ServiceContainer._initializationPromise;
  }

  /**
   * Get the ServiceContainer instance.
   * Returns immediately if initialized, otherwise throws.
   * Use getInstanceAsync() for safe access during initialization.
   */
  public static getInstance(): ServiceContainer {
    if (!ServiceContainer._instance) {
      throw new Error("ServiceContainer must be initialized first. Call ServiceContainer.initialize() first.");
    }
    return ServiceContainer._instance;
  }

  /**
   * Get the ServiceContainer instance, waiting for initialization if needed.
   * Safe to call during initialization.
   */
  public static async getInstanceAsync(): Promise<ServiceContainer> {
    // If already initialized, return immediately
    if (ServiceContainer._isInitialized && ServiceContainer._instance) {
      return ServiceContainer._instance;
    }

    // Wait for initialization to complete
    if (!ServiceContainer._initializationPromise) {
      throw new Error("ServiceContainer.initialize() must be called first");
    }

    return ServiceContainer._initializationPromise;
  }

  /**
   * Check if the ServiceContainer is initialized.
   */
  public static get isInitialized(): boolean {
    return ServiceContainer._isInitialized;
  }

  /**
   * Check if the ServiceContainer is currently initializing.
   */
  public static get isInitializing(): boolean {
    return !!ServiceContainer._initializationPromise && !ServiceContainer._isInitialized;
  }

  /**
   * Reset the ServiceContainer (for testing only).
   */
  public static reset(): void {
    if (ServiceContainer._instance) {
      ServiceContainer._instance.dispose();
    }
    ServiceContainer._instance = undefined;
    ServiceContainer._initializationPromise = undefined;
    ServiceContainer._isInitialized = false;
  }

  /* ================= SERVICE ACCESS =============================== */

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

  /** Get ProjectService - may be lazy initialized */
  get projectService(): ProjectService {
    return this.getLazyService("ProjectService");
  }

  /** Get GradleService - may be lazy initialized */
  get gradleService(): GradleService {
    return this.getLazyService("GradleService");
  }

  /** Get LogStreamingService - may be lazy initialized */
  get logStreamingService(): LogStreamingService {
    return this.getLazyService("LogStreamingService");
  }

  /** Get LanguageServerManager - may be lazy initialized */
  get languageServerManager(): LanguageServerManager {
    return this.getLazyService("LanguageServerManager");
  }

  /** Get ArtifactService - may be lazy initialized */
  get artifactService(): ArtifactService {
    return this.getLazyService("ArtifactService");
  }

  /** Get DashboardService - may be lazy initialized */
  get dashboardService(): DashboardService {
    return this.getLazyService("DashboardService");
  }

  /** Get DebugService - may be lazy initialized */
  get debugService(): DebugService {
    return this.getLazyService("DebugService");
  }

  /** Get DependencyGraphService - may be lazy initialized */
  get dependencyGraphService(): DependencyGraphService {
    return this.getLazyService("DependencyGraphService");
  }

  /** Get GormSqlPreviewService - may be lazy initialized */
  get gormSqlPreviewService(): GormSqlPreviewService {
    return this.getLazyService("GormSqlPreviewService");
  }

  /** Get GrailsTestService - may be lazy initialized */
  get grailsTestService(): GrailsTestService {
    return this.getLazyService("GrailsTestService");
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
    console.log("[ServiceContainer] Initializing services...");

    // Phase 1: Core services (no dependencies) - initialize immediately
    this._services.ErrorService = new ErrorService();
    this._services.StatusBarService = new StatusBarService(this.context);
    this._services.ConfigurationService = new ConfigurationService();
    this._initializedServices.add("ErrorService");
    this._initializedServices.add("StatusBarService");
    this._initializedServices.add("ConfigurationService");

    console.log("[ServiceContainer] Core services initialized");

    // Phase 2: Register lazy initializers for non-core services
    this._lazyServices.GradleService = () => new GradleService(
      this._services.StatusBarService!,
      this._services.ErrorService!
    );

    this._lazyServices.LogStreamingService = () => new LogStreamingService(
      this.getGradleService(),
      this._services.StatusBarService!,
      this._services.ErrorService!
    );

    this._lazyServices.ProjectService = () => new ProjectService(
      this._services.StatusBarService!,
      this._services.ErrorService!,
      this._services.ConfigurationService!,
      EventBus.getInstance()
    );

    this._lazyServices.LanguageServerManager = () => new LanguageServerManager(
      this.context,
      this._services.StatusBarService!,
      this._services.ErrorService!,
      this._services.ConfigurationService!
    );

    this._lazyServices.ArtifactService = () => new ArtifactService(this._services.ErrorService!);
    this._lazyServices.DashboardService = () => new DashboardService(
      this.context,
      this._services.ErrorService!
    );
    this._lazyServices.DebugService = () => new DebugService(
      this.getGradleService(),
      this._services.ErrorService!
    );
    this._lazyServices.DependencyGraphService = () => new DependencyGraphService(
      this.context,
      this._services.ErrorService!
    );
    this._lazyServices.GormSqlPreviewService = () => new GormSqlPreviewService(
      this.context,
      this._services.ErrorService!
    );
    this._lazyServices.GrailsTestService = () => new GrailsTestService(
      this.context,
      this._services.ErrorService!
    );

    console.log("[ServiceContainer] Lazy service initializers registered");
  }

  // Helper method for lazy initialization
  private getLazyService<K extends ServiceName>(name: K): ServiceRegistry[K] {
    // Return immediately if already initialized
    if (this._initializedServices.has(name)) {
      return this._services[name]!;
    }

    // Check if lazy initializer exists
    const initializer = this._lazyServices[name];
    if (!initializer) {
      throw new Error(`Service "${name}" has no lazy initializer`);
    }

    // Initialize the service
    const startTime = performance.now();
    const service = initializer();
    this._services[name] = service;
    this._initializedServices.add(name);

    const elapsed = performance.now() - startTime;
    console.log(`[ServiceContainer] Lazy initialized ${name} in ${elapsed.toFixed(1)}ms`);

    return service;
  }

  /**
   * Verify all services are properly initialized and ready.
   */
  healthCheck(): { healthy: boolean; issues: string[] } {
    const issues: string[] = [];

    // Check core services (must be initialized)
    if (!this._services.ErrorService) {
      issues.push("ErrorService not initialized");
    }
    if (!this._services.StatusBarService) {
      issues.push("StatusBarService not initialized");
    }
    if (!this._services.ConfigurationService) {
      issues.push("ConfigurationService not initialized");
    }

    // Check lazy services (may not be initialized yet)
    const lazyServiceNames: ServiceName[] = [
      "GradleService",
      "ProjectService",
      "LanguageServerManager",
      "ArtifactService",
      "DashboardService",
      "DebugService",
      "DependencyGraphService",
      "GormSqlPreviewService",
      "GrailsTestService",
      "LogStreamingService",
    ];

    for (const name of lazyServiceNames) {
      if (!this._initializedServices.has(name)) {
        // Not initialized yet - this is OK for lazy services
        continue;
      }
      if (!this._services[name]) {
        issues.push(`${name} marked as initialized but not found`);
      }
    }

    // Test service readiness for initialized services
    try {
      if (this._initializedServices.has("GradleService")) {
        const gradleReady = this._services.GradleService?.isReady ?? false;
        if (!gradleReady) {
          issues.push("Gradle API not ready");
        }
      }
      if (this._initializedServices.has("LanguageServerManager")) {
        const lspReady = this._services.LanguageServerManager?.isRunning ?? false;
        if (!lspReady) {
          issues.push("Language Server not running");
        }
      }
    } catch (error) {
      issues.push(`Health check failed: ${String(error)}`);
    }

    return {
      healthy: issues.length === 0,
      issues,
    };
  }

  /* ================= CLEANUP ======================================== */

  dispose(): void {
    console.log("[ServiceContainer] Disposing services...");

    const services = Object.values(this._services) as (Disposable | undefined)[];
    services.forEach(service => {
      if (service && "dispose" in service && typeof service.dispose === "function") {
        try {
          service.dispose();
        } catch (error) {
          console.error(`[ServiceContainer] Error disposing service:`, error);
        }
      }
    });

    (Object.keys(this._services) as ServiceName[]).forEach(key => {
      delete this._services[key];
    });

    this._initializedServices.clear();
    console.log("[ServiceContainer] All services disposed");
  }
}
```

Update extension.ts to use async initialization:

```typescript
// extension.ts
import type { ExtensionContext } from "vscode";
import { ActivationManager } from "./core/lifecycle/ActivationManager";
import { OutputChannelService } from "./services/workspace/OutputChannelService";
import { ServiceContainer } from "./core/container/ServiceContainer";

const EXTENSION_ACTIVATION_TIMER = "🚀 Extension Activation";

let activationManager: ActivationManager | undefined;

/**
 * Extension activation entry point.
 */
export async function activate(context: ExtensionContext): Promise<void> {
  console.log("🚀 Grails extension activating...");
  console.time(EXTENSION_ACTIVATION_TIMER);
  const startTime = performance.now();

  try {
    // Initialize centralized output channel first
    OutputChannelService.initialize(context);

    // Initialize ServiceContainer asynchronously
    console.log("📦 Initializing ServiceContainer...");
    await ServiceContainer.initialize(context);
    console.log("✅ ServiceContainer initialized");

    activationManager = new ActivationManager(context);
    activationManager.activate();

    context.subscriptions.push(activationManager);
    console.log("✅ Grails extension ACTIVATED successfully");
  } catch (error) {
    try {
      const container = ServiceContainer.isInitialized
        ? ServiceContainer.getInstance()
        : null;

      if (container) {
        const { ErrorSource, ErrorSeverity } = require("./services/errors/errorTypes");
        container.errorService.handleError(
          "Grails extension activation failed",
          error,
          ErrorSource.Extension,
          ErrorSeverity.Error
        );
      } else {
        console.error("❌ Grails extension activation failed:", error);
      }
    } catch {
      console.error("❌ Grails extension activation failed:", error);
    }
    throw error;
  }

  const totalTime = performance.now() - startTime;
  console.timeEnd(EXTENSION_ACTIVATION_TIMER);
  console.log(`📊 Total activation: ${totalTime.toFixed(1)}ms`);
}

/**
 * Extension deactivation cleanup.
 */
export function deactivate(): void {
  console.log("👋 Grails extension deactivating...");

  if (activationManager) {
    activationManager.dispose();
    activationManager = undefined;
  }

  // Reset ServiceContainer
  ServiceContainer.reset();
}
```

## Subtasks
- [ ] Add initialization promise to ServiceContainer
- [ ] Add getInstanceAsync() method
- [ ] Add isInitialized and isInitializing flags
- [ ] Add reset() method for testing
- [ ] Update extension.ts to use async initialization
- [ ] Test initialization race conditions
- [ ] Test concurrent getInstance() calls

## Tradeoffs
- Slightly more complex singleton pattern
- Need to handle async initialization throughout codebase
- Some code may need to be updated to use getInstanceAsync()

## Testing This Fix
1. Test that getInstance() throws before initialization
2. Test that getInstanceAsync() waits for initialization
3. Test concurrent getInstanceAsync() calls during initialization
4. Test that isInitialized flag is set correctly
5. Test that reset() works for testing
6. Test activation with services accessed during initialization
7. Verify no race conditions in production