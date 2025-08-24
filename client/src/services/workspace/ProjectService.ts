import * as fs from "fs";
import * as path from "path";
import { Disposable, RelativePattern, workspace, WorkspaceFolder } from "vscode";
import { ProjectInfo, ProjectType } from "../../features/models/modelTypes";
import { StatusBarService } from "./StatusBarService";
import { ErrorService } from "../errors/ErrorService";
import { EventBus } from "../../core/events/EventBus";
import { EventType, ProjectsDiscoveredEvent } from "../../core/events/eventTypes";
import { ConfigurationService } from "./ConfigurationService";
import { ErrorSeverity, ErrorSource } from "../errors/errorTypes";

/**
 * Discovers Groovy / Grails / Grails-plugin projects in a multi-root workspace.
 * If a JSON cache created by the Grails LSP exists (.grails-lsp/projectInfo.json),
 * it is used instead of ad-hoc scanning.
 */
export class ProjectService implements Disposable {
  private readonly projects = new Map<string, ProjectInfo>();
  private watchers: Disposable[] = [];

  constructor(
    private readonly statusBar: StatusBarService,
    private readonly errors: ErrorService,
    private readonly config: ConfigurationService
  ) {}

  /* ------------------------------------------------------------------ */

  /**
   * Scan every workspace folder and build ProjectInfo for each root.
   * Emits PROJECTS_DISCOVERED when done.
   */
  async discoverProjects(): Promise<ProjectInfo[]> {
    try {
      const roots = workspace.workspaceFolders ?? [];
      const discovered: ProjectInfo[] = [];

      for (const folder of roots) {
        const info = await this.loadProject(folder);
        if (info) {
          this.projects.set(info.id, info);
          discovered.push(info);
        }
      }

      EventBus.getInstance().publish<ProjectsDiscoveredEvent>({
        type: EventType.PROJECTS_DISCOVERED,
        timestamp: Date.now(),
        source: "ProjectService",
        projects: discovered,
      });

      this.statusBar.ready(
        `${discovered.length} project${discovered.length === 1 ? "" : "s"} loaded`
      );

      this.initWatchers(); // Watch build.gradle changes
      return discovered;
    } catch (e) {
      this.errors.handle(e, ErrorSource.ProjectService, ErrorSeverity.Error);
      return [];
    }
  }

  /* -------- Public accessors ---------------------------------------- */

  getProjects(): ProjectInfo[] {
    return [...this.projects.values()];
  }

  getProjectById(id: string): ProjectInfo | undefined {
    return this.projects.get(id);
  }

  /* ------------------------------------------------------------------ */

  dispose() {
    this.watchers.forEach(w => w.dispose());
    this.watchers = [];
    this.projects.clear();
  }

  /* ================= PRIVATE IMPL =================================== */

  /** Prefer cached JSON from LSP; fall back to on-disk heuristics. */
  private async loadProject(folder: WorkspaceFolder): Promise<ProjectInfo | undefined> {
    const root = folder.uri.fsPath;

    // 1. Try LSP cache
    const cacheDir = path.join(root, this.config.cacheDirectory);
    const cacheJson = path.join(cacheDir, this.config.cacheFile);

    if (fs.existsSync(cacheJson)) {
      try {
        const cached = JSON.parse(fs.readFileSync(cacheJson, "utf8")) as ProjectInfo;
        // Ensure id/rootPath are correct even if server omitted them
        cached.id ??= root;
        cached.rootPath ??= root;
        return cached;
      } catch (e) {
        this.errors.handle(
          `Failed to read LSP cache: ${e}`,
          ErrorSource.ProjectService,
          ErrorSeverity.Error
        );
      }
    }

    // 2. Fallback: scan the actual Grails project structure
    return this.scanFolder(root);
  }

  /* Quick heuristic detection for Groovy / Grails / plugin. */
  private async scanFolder(root: string): Promise<ProjectInfo | undefined> {
    const buildFile = path.join(root, "build.gradle");

    const type = this.detectProjectType(root);
    if (!type) return undefined;

    const name = path.basename(root);
    const dependencies = await this.parseDependencies(buildFile);

    const info = {
      id: root,
      rootPath: root,
      name,
      type,
      dependencies,
    };

    return info;
  }

  private detectProjectType(root: string): ProjectType | undefined {
    const grailsDir = path.join(root, "grails-app");
    const buildGradle = path.join(root, "build.gradle");
    if (!fs.existsSync(buildGradle)) return undefined;

    const isGrails = fs.existsSync(grailsDir);
    if (isGrails) {
      // Plugin marker: grails-plugin in dependencies OR specific plugin descriptor
      const pluginDescriptor = path.join(root, "grails-app", "conf", "application.yml");
      if (this.fileContains(buildGradle, "org.grails.grails-plugin")) {
        return ProjectType.GrailsPlugin;
      }
      return ProjectType.Grails;
    }
    return ProjectType.Groovy;
  }

  /* Basic Gradle dependency parse. */
  private async parseDependencies(buildFile: string): Promise<string[]> {
    const text = fs.readFileSync(buildFile, "utf8");
    const regex = /(implementation|compile|api)\s+['"]([^'"]+)['"]/g;
    const out: string[] = [];
    let m;
    while ((m = regex.exec(text))) out.push(m[2]);
    return out;
  }

  private fileContains(file: string, needle: string): boolean {
    try {
      return fs.readFileSync(file, "utf8").includes(needle);
    } catch {
      return false;
    }
  }

  /* ------------- Watchers: refresh project on build.gradle change --- */

  private initWatchers(): void {
    if (this.watchers.length) return; // Already watching

    workspace.workspaceFolders?.forEach(folder => {
      const pat = new RelativePattern(folder, "build.gradle");
      const watch = workspace.createFileSystemWatcher(pat);

      const refresh = () => this.reload(folder);
      watch.onDidChange(refresh);
      watch.onDidCreate(refresh);
      watch.onDidDelete(refresh);
      this.watchers.push(watch);
    });
  }

  /** Re-scan a folder & publish PROJECT_CHANGED when data differs. */
  private async reload(folder: WorkspaceFolder): Promise<void> {
    const next = await this.loadProject(folder);
    if (!next) return;

    const prev = this.projects.get(next.id);
    const changed = !prev || JSON.stringify(prev) !== JSON.stringify(next);

    if (changed) {
      this.projects.set(next.id, next);

      EventBus.getInstance().publish({
        type: EventType.PROJECT_CHANGED,
        timestamp: Date.now(),
        source: "ProjectService",
        project: next,
      });
    }
  }
}
