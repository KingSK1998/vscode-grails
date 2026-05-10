# ISSUE-005 · Synchronous File Reading in ProjectService
**Severity**: 🔴 Critical
**Service**: ProjectService.ts
**Status**: TODO
**Parent Plan**: [client-improvement-plan.md](../client-improvement-plan.md)

## Problem
ProjectService uses synchronous file operations (fs.readFileSync, fs.readdirSync) throughout. This blocks the extension host thread, causing UI freezes on large projects. Operations like parseDependencies(), fileContains(), and extractVersion() all use synchronous I/O.

## Current Code (problematic pattern)
```typescript
// ProjectService.ts - Lines 350-366
private parseDependencies(buildFile: string): string[] {
  try {
    const text = fs.readFileSync(buildFile, "utf8"); // BLOCKING
    const regex = /(implementation|compile|api|runtimeOnly)\s+['"]([^'"]+)['"]/g;
    const deps: string[] = [];
    let match: RegExpExecArray | null = null;

    while ((match = regex.exec(text)) !== null) {
      const dep = match[2];
      if (dep) deps.push(dep);
    }

    return deps;
  } catch {
    return [];
  }
}

// Lines 407-413
private fileContains(file: string, needle: string): boolean {
  try {
    return fs.readFileSync(file, "utf8").includes(needle); // BLOCKING
  } catch {
    return false;
  }
}

// Lines 390-397
private extractVersion(file: string, regex: RegExp): string | undefined {
  try {
    const content = fs.readFileSync(file, "utf8"); // BLOCKING
    return content.match(regex)?.[1];
  } catch {
    return undefined;
  }
}

// Lines 368-387
private countInDirectory(dir: string, ...extensions: string[]): number {
  if (!fs.existsSync(dir)) {
    return 0;
  }

  try {
    const files = fs.readdirSync(dir, { withFileTypes: true }); // BLOCKING
    return files.filter(file => {
      if (!file.isFile()) {
        return false;
      }
      if (extensions.length === 0) {
        return true;
      }
      return extensions.some(ext => file.name.endsWith(ext));
    }).length;
  } catch {
    return 0;
  }
}
```

## Fixed Code
```typescript
import * as fs from "fs/promises"; // Use promises API

// Update all file operations to async
private async parseDependencies(buildFile: string): Promise<string[]> {
  try {
    const text = await fs.readFile(buildFile, "utf8");
    const regex = /(implementation|compile|api|runtimeOnly)\s+['"]([^'"]+)['"]/g;
    const deps: string[] = [];
    let match: RegExpExecArray | null = null;

    while ((match = regex.exec(text)) !== null) {
      const dep = match[2];
      if (dep) deps.push(dep);
    }

    return deps;
  } catch {
    return [];
  }
}

private async fileContains(file: string, needle: string): Promise<boolean> {
  try {
    const content = await fs.readFile(file, "utf8");
    return content.includes(needle);
  } catch {
    return false;
  }
}

private async extractVersion(file: string, regex: RegExp): Promise<string | undefined> {
  try {
    const content = await fs.readFile(file, "utf8");
    return content.match(regex)?.[1];
  } catch {
    return undefined;
  }
}

private async countInDirectory(dir: string, ...extensions: string[]): Promise<number> {
  try {
    const stat = await fs.stat(dir);
    if (!stat.isDirectory()) {
      return 0;
    }
  } catch {
    return 0;
  }

  try {
    const files = await fs.readdir(dir, { withFileTypes: true });
    return files.filter(file => {
      if (!file.isFile()) {
        return false;
      }
      if (extensions.length === 0) {
        return true;
      }
      return extensions.some(ext => file.name.endsWith(ext));
    }).length;
  } catch {
    return 0;
  }
}

// Update scanFolder to be async
private async scanFolder(root: string): Promise<ProjectInfo | undefined> {
  const build = path.join(root, "build.gradle");
  try {
    await fs.access(build); // Check if file exists asynchronously
  } catch {
    return undefined;
  }

  const type = await this.detectProjectType(root);
  if (!type) return undefined;

  const [dependencies, artifactCounts] = await Promise.all([
    this.parseDependencies(build),
    this.countArtifacts(root),
  ]);

  const info: ProjectInfo = {
    id: root,
    rootPath: root,
    name: path.basename(root),
    type,
    dependencies,
    artifactCounts,
  };

  // Add project-specific metadata
  if (type === ProjectType.Grails || type === ProjectType.GrailsPlugin) {
    const [grailsVersion, groovyVersion] = await Promise.all([
      this.extractVersion(build, /grailsVersion\s*=\s*['"]([^'"]+)['"]/),
      this.extractVersion(build, /groovyVersion\s*=\s*['"]([^'"]+)['"]/),
    ]);
    info.grailsVersion = grailsVersion;
    info.groovyVersion = groovyVersion;
  }

  if (type === ProjectType.GrailsPlugin) {
    info.pluginVersion = await this.extractVersion(build, /version\s*=\s*['"]([^'"]+)['"]/);
  }

  return info;
}

// Update detectProjectType to be async
private async detectProjectType(root: string): Promise<ProjectType | undefined> {
  const grailsDir = path.join(root, "grails-app");
  const build = path.join(root, "build.gradle");

  try {
    await fs.access(build);
  } catch {
    return undefined;
  }

  const hasGrailsApp = await this.directoryExists(grailsDir);
  if (hasGrailsApp) {
    // Check for plugin markers
    const [hasPluginMarker1, hasPluginMarker2] = await Promise.all([
      this.fileContains(build, "org.grails.grails-plugin"),
      this.fileContains(build, "grails-plugin"),
    ]);

    if (hasPluginMarker1 || hasPluginMarker2) {
      return ProjectType.GrailsPlugin;
    }
    return ProjectType.Grails;
  }

  // Check if it's a Groovy project not spring boot
  const [hasGroovyDep, hasGroovySrc] = await Promise.all([
    this.fileContains(build, "groovy"),
    this.directoryExists(path.join(root, "src", "main", "groovy")),
  ]);

  if (hasGroovyDep || hasGroovySrc) {
    return ProjectType.Groovy;
  }

  return undefined;
}

// Helper method for async directory existence check
private async directoryExists(dir: string): Promise<boolean> {
  try {
    const stat = await fs.stat(dir);
    return stat.isDirectory();
  } catch {
    return false;
  }
}

// Update countArtifacts to be async
private async countArtifacts(root: string): Promise<ArtifactCounts> {
  const grailsApp = path.join(root, "grails-app");
  const counts: ArtifactCounts = {
    // Core MVC
    controllers: 0,
    services: 0,
    domains: 0,
    views: 0,
    taglibs: 0,

    // Interceptors & Filters
    interceptors: 0,
    filters: 0,

    // Configuration
    config: 0,
    urlMappings: 0,
    bootstrap: 0,
    applicationConfig: 0,
    springConfigs: 0,
    hibernateConfigs: 0,

    // Testing
    unitTests: 0,
    integrationTests: 0,
    spockSpecs: 0,
    functionalTests: 0,

    // Assets & Resources
    assets: 0,
    i18n: 0,
    resources: 0,
    gspFiles: 0,
    staticFiles: 0,

    // Commands & Scripts
    commands: 0,
    scripts: 0,

    // Plugin artifacts
    jobs: 0,
    utils: 0,
    codecs: 0,

    // Source code
    groovySrc: 0,
    javaSrc: 0,
    dependencies: 0,
    tasks: 0,
  };

  // Count core artifacts in parallel
  const [
    controllers,
    urlMappings,
    services,
    domains,
    taglibs,
    views,
    interceptors,
    filters,
    config,
    springConfigs,
    hibernateConfigs,
    unitTests,
    integrationTests,
    spockSpecs,
    functionalTests,
    assets,
    i18n,
    resources,
    gspFiles,
    commands,
    scripts,
    jobs,
    utils,
    codecs,
    groovySrc,
    javaSrc,
  ] = await Promise.all([
    this.countInDirectory(path.join(grailsApp, "controllers"), "Controller.groovy"),
    this.countInDirectory(path.join(grailsApp, "controllers"), "UrlMappings.groovy"),
    this.countInDirectory(path.join(grailsApp, "services"), "Service.groovy"),
    this.countInDirectory(path.join(grailsApp, "domain"), ".groovy"),
    this.countInDirectory(path.join(grailsApp, "taglib"), "TagLib.groovy"),
    this.countInDirectory(path.join(grailsApp, "views"), ".gsp"),
    this.countInDirectory(path.join(grailsApp, "controllers"), "Interceptor.groovy"),
    this.countInDirectory(path.join(grailsApp, "conf"), "Filters.groovy"),
    this.countInDirectory(path.join(grailsApp, "conf"), ".groovy", ".yml", ".properties"),
    this.countInDirectory(path.join(grailsApp, "conf", "spring"), ".groovy"),
    this.countInDirectory(path.join(grailsApp, "conf", "hibernate"), ".groovy"),
    this.countInDirectory(path.join(root, "src/test/groovy"), "Test.groovy"),
    this.countInDirectory(path.join(root, "src/integration-test/groovy"), "Spec.groovy"),
    this.countInDirectory(path.join(root, "src/test/groovy"), "Spec.groovy"),
    this.countInDirectory(path.join(root, "src/test/functional"), ".groovy"),
    this.countInDirectory(path.join(grailsApp, "assets"), ".js", ".css", ".scss"),
    this.countInDirectory(path.join(grailsApp, "i18n"), ".properties"),
    this.countInDirectory(path.join(root, "src/main/resources"), ""),
    this.countInDirectory(path.join(grailsApp, "views"), ".gsp"),
    this.countInDirectory(path.join(grailsApp, "commands"), "Command.groovy"),
    this.countInDirectory(path.join(root, "src/main/scripts"), ".groovy"),
    this.countInDirectory(path.join(grailsApp, "jobs"), ".groovy"),
    this.countInDirectory(path.join(grailsApp, "utils"), ".groovy"),
    this.countInDirectory(path.join(grailsApp, "utils"), "Codec.groovy"),
    this.countInDirectory(path.join(root, "src/main/groovy"), ".groovy"),
    this.countInDirectory(path.join(root, "src/main/java"), ".java"),
  ]);

  // Assign counts
  counts.controllers = controllers;
  counts.urlMappings = urlMappings;
  counts.services = services;
  counts.domains = domains;
  counts.taglibs = taglibs;
  counts.views = views;
  counts.interceptors = interceptors;
  counts.filters = filters;
  counts.config = config;
  counts.springConfigs = springConfigs;
  counts.hibernateConfigs = hibernateConfigs;
  counts.unitTests = unitTests;
  counts.integrationTests = integrationTests;
  counts.spockSpecs = spockSpecs;
  counts.functionalTests = functionalTests;
  counts.assets = assets;
  counts.i18n = i18n;
  counts.resources = resources;
  counts.gspFiles = gspFiles;
  counts.commands = commands;
  counts.scripts = scripts;
  counts.jobs = jobs;
  counts.utils = utils;
  counts.codecs = codecs;
  counts.groovySrc = groovySrc;
  counts.javaSrc = javaSrc;

  // Check for specific files
  const confDir = path.join(grailsApp, "conf");
  const [bootstrapExists] = await Promise.all([
    this.fileExists(path.join(confDir, "BootStrap.groovy")),
  ]);
  counts.bootstrap = bootstrapExists ? 1 : 0;

  return counts;
}

// Helper method for async file existence check
private async fileExists(file: string): Promise<boolean> {
  try {
    await fs.access(file);
    return true;
  } catch {
    return false;
  }
}

// Update quickScan to use async operations
quickScan(): ProjectInfo[] {
  // Keep quickScan synchronous for immediate UI feedback
  // But use minimal file checks
  try {
    this.statusBarService.sync("🔍 Scanning for projects...");
    const start = performance.now();

    const roots = workspace.workspaceFolders ?? [];
    const quickInfo: ProjectInfo[] = [];

    for (const folder of roots) {
      const root = folder.uri.fsPath;
      const build = path.join(root, "build.gradle");

      // Use synchronous check for quick scan only
      if (!fs.existsSync(build)) continue;

      // Minimal project info for immediate UI
      quickInfo.push({
        id: root,
        rootPath: root,
        name: path.basename(root),
        type: this.quickDetectType(root),
        dependencies: [], // Will be filled during full discovery
      });
    }

    console.log(`📊 Quick scan: ${(performance.now() - start).toFixed(1)}ms`);

    return quickInfo;
  } catch (error) {
    this.errorService.handleError(
      "Quick scan failed",
      error,
      ErrorSource.ProjectService,
      ErrorSeverity.Warning
    );
    return [];
  }
}

// Update reloadProject to be async
private async reloadProject(folder: WorkspaceFolder): Promise<void> {
  const updated = await this.scanFolder(folder.uri.fsPath);
  if (!updated) return;

  const previous = this.projects.get(updated.id);
  const prevDeps = previous?.dependencies ?? [];
  const updatedDeps = updated.dependencies ?? [];

  const changed =
    !previous || previous.type !== updated.type || prevDeps.length !== updatedDeps.length;

  if (!changed) return;

  console.log(`📦 ProjectService: Project ${updated.name} changed`);

  this.projects.set(updated.id, updated);

  this.eventBus.publish({
    type: EventType.PROJECT_CHANGED,
    timestamp: Date.now(),
    source: ProjectService.name,
    project: updated,
  });
}
```

## Subtasks
- [ ] Convert parseDependencies() to async
- [ ] Convert fileContains() to async
- [ ] Convert extractVersion() to async
- [ ] Convert countInDirectory() to async
- [ ] Convert scanFolder() to async
- [ ] Convert detectProjectType() to async
- [ ] Convert countArtifacts() to async
- [ ] Convert reloadProject() to async
- [ ] Update discoverProjects() to await async operations
- [ ] Add helper methods for async file/directory existence checks

## Tradeoffs
- More async/await complexity throughout ProjectService
- Need to handle concurrent file reads properly
- Slightly more complex error handling with async operations

## Testing This Fix
1. Test project discovery on a large Grails project
2. Measure time to discover projects before and after
3. Verify UI remains responsive during project scanning
4. Test that all project information is correctly parsed
5. Verify file watchers still trigger project reloads
6. Test error handling for missing files/directories