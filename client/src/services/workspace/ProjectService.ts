import * as fs from "fs";
import * as path from "path";
import { Disposable, RelativePattern, workspace, WorkspaceFolder } from "vscode";
import { ArtifactCounts, ProjectInfo, ProjectType } from "../../features/models/modelTypes";
import { StatusBarService } from "./StatusBarService";
import { ErrorService } from "../errors/ErrorService";
import { EventBus } from "../../core/events/EventBus";
import {
  EventType,
  ProjectChangedEvent,
  ProjectsDiscoveredEvent,
} from "../../core/events/eventTypes";
import { ConfigurationService } from "./ConfigurationService";
import { ErrorSeverity, ErrorSource } from "../errors/errorTypes";
import { LanguageServerManager } from "../languageServer/LanguageServerManager";

/**
 * Discovers Groovy / Grails / Grails-plugin projects in a multi-root workspace.
 * Client-side project service.
 * Responsibilities:
 * - Fast folder-based project discovery
 * - File system watching and change detection
 * - Basic artifact counting and organization
 * - Artifact metadata caching
 */
export class ProjectService implements Disposable {
  private readonly projects = new Map<string, ProjectInfo>();
  private readonly watchers: Disposable[] = [];
  private activeProjectId?: string; // Track active project for UI

  constructor(
    private readonly statusBar: StatusBarService,
    private readonly errors: ErrorService,
    private readonly config: ConfigurationService,
    private readonly lspManager?: LanguageServerManager // Optional LSP integration
  ) {}

  /* ================= PUBLIC API ===================================== */

  /**
   * Scan every workspace folder and build ProjectInfo for each root.
   * Emits PROJECTS_DISCOVERED when done.
   */
  async discoverProjects(): Promise<ProjectInfo[]> {
    try {
      const roots = workspace.workspaceFolders ?? [];
      const discovered: ProjectInfo[] = [];

      // Process each workspace root
      for (const folder of roots) {
        const info = await this.loadProject(folder);
        if (info) {
          this.projects.set(info.id, info);
          discovered.push(info);
        }
      }

      // Set first project as active by default
      if (discovered.length > 0 && !this.activeProjectId) {
        this.activeProjectId = discovered[0].id;
      }

      // Notify listeners
      EventBus.getInstance().publish<ProjectsDiscoveredEvent>({
        type: EventType.PROJECTS_DISCOVERED,
        timestamp: Date.now(),
        source: "ProjectService",
        projects: discovered,
      });

      // Update status bar
      this.statusBar.ready(
        `${discovered.length} project${discovered.length === 1 ? "" : "s"} loaded`
      );

      // Watch build.gradle changes
      this.initWatchers();
      return discovered;
    } catch (e) {
      this.errors.handle(e, ErrorSource.ProjectService, ErrorSeverity.Error);
      return [];
    }
  }

  /** Get all discovered projects */
  getProjects(): ProjectInfo[] {
    return [...this.projects.values()];
  }

  /** Get project by ID */
  getProjectById(id: string): ProjectInfo | undefined {
    return this.projects.get(id);
  }

  /** Set active project - triggers UI updates */
  setActiveProject(projectId: string): void {
    const project = this.projects.get(projectId);
    if (project) {
      this.activeProjectId = projectId;
      EventBus.getInstance().publish<ProjectChangedEvent>({
        type: EventType.PROJECT_CHANGED,
        timestamp: Date.now(),
        source: "ProjectService",
        project,
      });
    }
  }

  dispose() {
    this.watchers.forEach(w => w.dispose());
    this.watchers.length = 0;
    this.projects.clear();
  }

  /* ================= PRIVATE IMPL =================================== */

  /** Load project from LSP cache or fallback to file scanning */
  private async loadProject(folder: WorkspaceFolder): Promise<ProjectInfo | undefined> {
    const root = folder.uri.fsPath;

    // 1. Try LSP cache first (configurable paths)
    const cacheDir = path.join(root, this.config.cacheDirectory);
    const cacheFile = path.join(cacheDir, this.config.cacheFile);

    if (fs.existsSync(cacheFile)) {
      try {
        // Handle both .json and .cache files
        if (cacheFile.endsWith(".json")) {
          const cached = JSON.parse(fs.readFileSync(cacheFile, "utf8")) as ProjectInfo;
          // Ensure required fields
          cached.id ??= root;
          cached.rootPath ??= root;
          return cached;
        } else {
          // Binary cache - skip for now (LSP will provide JSON alternative)
          this.statusBar.info(
            `Binary cache detected for ${path.basename(root)} - using fallback detection`
          );
        }
      } catch (e) {
        this.errors.handle(
          `Failed to read cache for ${path.basename(root)}: ${e}`,
          ErrorSource.ProjectService,
          ErrorSeverity.Warning
        );
      }
    }

    // 2. Fallback: scan project structure
    return this.scanFolder(root);
  }

  /** Scan project structure and build ProjectInfo */
  private async scanFolder(root: string): Promise<ProjectInfo | undefined> {
    const buildFile = path.join(root, "build.gradle");
    if (!fs.existsSync(buildFile)) return undefined;

    const type = this.detectProjectType(root);
    if (!type) return undefined;

    const name = path.basename(root);
    const dependencies = await this.parseDependencies(buildFile);

    const info: ProjectInfo = {
      id: root,
      rootPath: root,
      name,
      type,
      dependencies,
    };

    // Add project-specific metadata
    if (type === ProjectType.Grails || type === ProjectType.GrailsPlugin) {
      info.grailsVersion = this.extractVersion(buildFile, /grailsVersion\s*=\s*['"]([^'"]+)['"]/);
      info.groovyVersion = this.extractVersion(buildFile, /groovyVersion\s*=\s*['"]([^'"]+)['"]/);
      info.artifactCounts = await this.countArtifacts(root);
    }

    if (type === ProjectType.GrailsPlugin) {
      info.pluginVersion = this.extractVersion(buildFile, /version\s*=\s*['"]([^'"]+)['"]/);
    }

    return info;
  }

  private detectProjectType(root: string): ProjectType | undefined {
    const grailsDir = path.join(root, "grails-app");
    const buildGradle = path.join(root, "build.gradle");

    if (!fs.existsSync(buildGradle)) return undefined;

    const hasGrailsApp = fs.existsSync(grailsDir);
    if (hasGrailsApp) {
      // Check for plugin markers
      if (
        this.fileContains(buildGradle, "org.grails.grails-plugin") ||
        this.fileContains(buildGradle, "grails-plugin")
      ) {
        return ProjectType.GrailsPlugin;
      }
      return ProjectType.Grails;
    }

    // Check if it's a Groovy project
    if (
      this.fileContains(buildGradle, "groovy") ||
      fs.existsSync(path.join(root, "src", "main", "groovy"))
    ) {
      return ProjectType.Groovy;
    }

    return undefined;
  }

  /** Parse Gradle dependencies */
  private async parseDependencies(buildFile: string): Promise<string[]> {
    try {
      const text = fs.readFileSync(buildFile, "utf8");
      const regex = /(implementation|compile|api|runtimeOnly)\s+['"]([^'"]+)['"]/g;
      const deps: string[] = [];
      let match;
      while ((match = regex.exec(text))) {
        deps.push(match[2]);
      }
      return deps;
    } catch {
      return [];
    }
  }

  /** Count Grails artifacts for UI display */
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

    // Count core artifacts
    counts.controllers = this.countInDirectory(
      path.join(grailsApp, "controllers"),
      "Controller.groovy"
    );
    counts.urlMappings = this.countInDirectory(
      path.join(grailsApp, "controllers"),
      "UrlMappings.groovy"
    );
    counts.services = this.countInDirectory(path.join(grailsApp, "services"), "Service.groovy");
    counts.domains = this.countInDirectory(path.join(grailsApp, "domain"), ".groovy");
    counts.taglibs = this.countInDirectory(path.join(grailsApp, "taglib"), "TagLib.groovy");
    counts.views = this.countInDirectory(path.join(grailsApp, "views"), ".gsp");

    // Count interceptors (in controllers directory)
    counts.interceptors = this.countInDirectory(
      path.join(grailsApp, "controllers"),
      "Interceptor.groovy"
    );

    // Count filters (in conf directory)
    counts.filters = this.countInDirectory(path.join(grailsApp, "conf"), "Filters.groovy");

    // Count configuration files
    const confDir = path.join(grailsApp, "conf");
    counts.config = this.countInDirectory(confDir, ".groovy", ".yml", ".properties");
    counts.bootstrap = fs.existsSync(path.join(confDir, "BootStrap.groovy")) ? 1 : 0;
    counts.springConfigs = this.countInDirectory(path.join(confDir, "spring"), ".groovy");
    counts.hibernateConfigs = this.countInDirectory(path.join(confDir, "hibernate"), ".groovy");

    // Count testing artifacts
    counts.unitTests = this.countInDirectory(path.join(root, "src/test/groovy"), "Test.groovy");
    counts.integrationTests = this.countInDirectory(
      path.join(root, "src/integration-test/groovy"),
      "Spec.groovy"
    );
    counts.spockSpecs = this.countInDirectory(path.join(root, "src/test/groovy"), "Spec.groovy");
    counts.functionalTests = this.countInDirectory(
      path.join(root, "src/test/functional"),
      ".groovy"
    );

    // Count assets & resources
    counts.assets = this.countInDirectory(path.join(grailsApp, "assets"), ".js", ".css", ".scss");
    counts.i18n = this.countInDirectory(path.join(grailsApp, "i18n"), ".properties");
    counts.resources = this.countInDirectory(path.join(root, "src/main/resources"), "");
    counts.gspFiles = this.countInDirectory(path.join(grailsApp, "views"), ".gsp");

    // Count commands & scripts
    counts.commands = this.countInDirectory(path.join(grailsApp, "commands"), "Command.groovy");
    counts.scripts = this.countInDirectory(path.join(root, "src/main/scripts"), ".groovy");

    // Count plugin artifacts
    counts.jobs = this.countInDirectory(path.join(grailsApp, "jobs"), ".groovy");
    counts.utils = this.countInDirectory(path.join(grailsApp, "utils"), ".groovy");
    counts.codecs = this.countInDirectory(path.join(grailsApp, "utils"), "Codec.groovy");

    // Count source files
    counts.groovySrc = this.countInDirectory(path.join(root, "src/main/groovy"), ".groovy");
    counts.javaSrc = this.countInDirectory(path.join(root, "src/main/java"), ".java");

    return counts;
  }

  private countInDirectory(dir: string, ...extensions: string[]): number {
    if (!fs.existsSync(dir)) return 0;

    try {
      const files = fs.readdirSync(dir, { withFileTypes: true });
      return files.filter(file => {
        if (!file.isFile()) return false;
        if (extensions.length === 0) return true;
        return extensions.some(ext => file.name.endsWith(ext));
      }).length;
    } catch {
      return 0;
    }
  }

  /** Extract version from build.gradle */
  private extractVersion(buildFile: string, regex: RegExp): string | undefined {
    try {
      const content = fs.readFileSync(buildFile, "utf8");
      const match = content.match(regex);
      return match?.[1];
    } catch {
      return undefined;
    }
  }

  /** Check if file contains text */
  private fileContains(file: string, needle: string): boolean {
    try {
      return fs.readFileSync(file, "utf8").includes(needle);
    } catch {
      return false;
    }
  }

  /* ------------- Watchers: refresh project on build.gradle change --- */

  /** Initialize file watchers for live updates */
  private initWatchers(): void {
    if (this.watchers.length > 0) return; // Already watching

    workspace.workspaceFolders?.forEach(folder => {
      const pattern = new RelativePattern(folder, "build.gradle");
      const watcher = workspace.createFileSystemWatcher(pattern);

      const refresh = () => this.reloadProject(folder);
      watcher.onDidChange(refresh);
      watcher.onDidCreate(refresh);
      watcher.onDidDelete(refresh);

      this.watchers.push(watcher);
    });
  }

  /** Reload project and notify if changed */
  private async reloadProject(folder: WorkspaceFolder): Promise<void> {
    const updated = await this.loadProject(folder);
    if (!updated) return;

    const previous = this.projects.get(updated.id);
    const hasChanged = !previous || JSON.stringify(previous) !== JSON.stringify(updated);

    if (hasChanged) {
      this.projects.set(updated.id, updated);

      EventBus.getInstance().publish({
        type: EventType.PROJECT_CHANGED,
        timestamp: Date.now(),
        source: "ProjectService",
        project: updated,
      });
    }
  }
}
