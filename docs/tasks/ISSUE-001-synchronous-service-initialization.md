# ISSUE-001 · Synchronous Service Initialization
**Severity**: 🔴 Critical
**Service**: ServiceContainer.ts
**Status**: TODO
**Parent Plan**: [client-improvement-plan.md](../client-improvement-plan.md)

## Problem
ServiceContainer initializes all 12 services synchronously in constructor (line 23). This blocks extension activation and delays UI responsiveness. Webview services (Dashboard, DependencyGraph, GormSqlPreview) are created immediately but only used when commands are invoked, wasting memory and startup time.

## Current Code (problematic pattern)
```typescript
// ServiceContainer.ts - Line 120-173
private initializeServices(): void {
  // Phase 1: Core services (no dependencies)
  this._services.ErrorService = new ErrorService();
  this._services.StatusBarService = new StatusBarService(this.context);
  this._services.ConfigurationService = new ConfigurationService();

  // Phase 2: Services with dependencies - ALL CREATED SYNCHRONOUSLY
  this._services.GradleService = new GradleService(
    this._services.StatusBarService,
    this._services.ErrorService
  );

  this._services.LogStreamingService = new LogStreamingService(
    this._services.GradleService,
    this._services.StatusBarService,
    this._services.ErrorService
  );

  this._services.ProjectService = new ProjectService(
    this._services.StatusBarService,
    this._services.ErrorService,
    this._services.ConfigurationService,
    EventBus.getInstance()
  );

  this._services.LanguageServerManager = new LanguageServerManager(
    this.context,
    this._services.StatusBarService,
    this._services.ErrorService,
    this._services.ConfigurationService
  );

  // Webview services - CREATED IMMEDIATELY BUT ONLY USED ON-DEMAND
  this._services.ArtifactService = new ArtifactService(this._services.ErrorService);
  this._services.DashboardService = new DashboardService(
    this.context,
    this._services.ErrorService
  );
  this._services.DebugService = new DebugService(
    this._services.GradleService,
    this._services.ErrorService
  );
  this._services.DependencyGraphService = new DependencyGraphService(
    this.context,
    this._services.ErrorService
  );
  this._services.GormSqlPreviewService = new GormSqlPreviewService(
    this.context,
    this._services.ErrorService
  );
  this._services.GrailsTestService = new GrailsTestService(
    this.context,
    this._services.ErrorService
  );
}
```

## Fixed Code
```typescript
// ServiceContainer.ts - Lazy initialization pattern
private readonly _lazyServices: Partial<Record<ServiceName, () => ServiceRegistry[ServiceName]>> = {};
private readonly _initializedServices = new Set<ServiceName>();

private initializeServices(): void {
  // Phase 1: Core services - initialize immediately (no dependencies)
  this._services.ErrorService = new ErrorService();
  this._services.StatusBarService = new StatusBarService(this.context);
  this._services.ConfigurationService = new ConfigurationService();
  this._initializedServices.add("ErrorService");
  this._initializedServices.add("StatusBarService");
  this._initializedServices.add("ConfigurationService");

  // Phase 2: Register lazy initializers for non-core services
  this._lazyServices.GradleService = () => new GradleService(
    this._services.StatusBarService!,
    this._services.ErrorService!
  );

  this._lazyServices.LogStreamingService = () => new LogStreamingService(
    this.getGradleService(), // Will trigger lazy init
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

  // Webview services - lazy initialized only when needed
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
}

// Update getters to use lazy initialization
get gradleService(): GradleService {
  return this.getLazyService("GradleService");
}

get logStreamingService(): LogStreamingService {
  return this.getLazyService("LogStreamingService");
}

get projectService(): ProjectService {
  return this.getLazyService("ProjectService");
}

get languageServerManager(): LanguageServerManager {
  return this.getLazyService("LanguageServerManager");
}

get artifactService(): ArtifactService {
  return this.getLazyService("ArtifactService");
}

get dashboardService(): DashboardService {
  return this.getLazyService("DashboardService");
}

get debugService(): DebugService {
  return this.getLazyService("DebugService");
}

get dependencyGraphService(): DependencyGraphService {
  return this.getLazyService("DependencyGraphService");
}

get gormSqlPreviewService(): GormSqlPreviewService {
  return this.getLazyService("GormSqlPreviewService");
}

get grailsTestService(): GrailsTestService {
  return this.getLazyService("GrailsTestService");
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

// Update healthCheck to handle lazy services
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
```

## Subtasks
- [ ] Add lazy initialization flag to ServiceContainer
- [ ] Convert non-core services to lazy getters
- [ ] Add initialization tracking for debugging
- [ ] Test activation time improvement

## Tradeoffs
- Slightly more complex getter logic with lazy initialization
- First access to lazy services has small overhead (measured in ms)
- Need to track which services are initialized for health checks

## Testing This Fix
1. Measure activation time before and after change
2. Verify core services (ErrorService, StatusBarService, ConfigurationService) are initialized immediately
3. Verify lazy services are only initialized when first accessed
4. Check console logs for lazy initialization timing
5. Run healthCheck() to verify it handles both initialized and uninitialized services
6. Test that all commands still work after lazy initialization change