import fs from "fs";
import path from "path";
import type { Command, Disposable, ExtensionContext, TreeDataProvider, TreeItem } from "vscode";
import { EventEmitter, TreeItemCollapsibleState, Uri } from "vscode";
import { ServiceContainer } from "../../core/container/ServiceContainer";
import { EventBus } from "../../core/events/EventBus";
import { EventType } from "../../core/events/eventTypes";
import type { ProjectInfo } from "../../features/models/modelTypes";
import { ArtifactType, ProjectType } from "../../features/models/modelTypes";
import { GrailsTreeItem } from "./GrailsTreeItem";
import { TreeItemKind } from "./TreeItemKind";

const NO_ACTION_COMMAND: Command = { command: "grails.noAction", title: "No Action" };

/** Grails-specific tree explorer using utilities and IconProvider */
export class GrailsTreeExplorer implements TreeDataProvider<GrailsTreeItem> {
  protected readonly _onDidChangeTreeData = new EventEmitter<
    GrailsTreeItem | undefined | null | void
  >();
  readonly onDidChangeTreeData = this._onDidChangeTreeData.event;

  protected container: ServiceContainer = ServiceContainer.getInstance();
  protected projects: ProjectInfo[] = [];
  protected disposables: Disposable[] = [];

  constructor(protected readonly context: ExtensionContext) {
    console.log("🌳 [GRAILS-TREE] initializing...");
    this.registerEventListeners();
    this.refresh();
  }

  /** Refresh the entire tree */
  refresh(): void {
    this.projects = this.container.projectService.getProjects();
    this._onDidChangeTreeData.fire();
  }

  /** Get tree item for display */
  getTreeItem(element: GrailsTreeItem): TreeItem {
    return element;
  }

  async getChildren(element?: GrailsTreeItem): Promise<GrailsTreeItem[]> {
    // Root level - show Grails projects only
    if (!element) {
      return Promise.resolve(this.getProjectNodes(this.projects));
    }

    if (!element.projectInfo) {
      console.debug("🌳 [GRAILS-TREE] getChildren: no projectInfo", element.label);
      return Promise.resolve([]);
    }

    console.debug(`🌳 [GRAILS-TREE] getChildren: ${element.kind}`);

    switch (element.kind) {
      case TreeItemKind.ProjectRoot:
        return Promise.resolve(this.getRootContainers(element.projectInfo));

      case TreeItemKind.GrailsAppRoot:
        return Promise.resolve(this.getGrailsArtefactCategories(element.projectInfo));
      // case TreeItemKind.ConfigRoot:
      //   return Promise.resolve(this.getConfigurationItems(element.projectInfo));
      // case TreeItemKind.ViewsRoot:
      //   return Promise.resolve(this.getViewStructure(element.projectInfo));
      // case TreeItemKind.AssetsRoot:
      //   return Promise.resolve(this.getAssetStructure(element.projectInfo));
      // case TreeItemKind.RoutesRoot:
      //   return Promise.resolve(this.getRouteList(element.projectInfo));
      // case TreeItemKind.SrcRoot:
      //   return Promise.resolve(
      //     this.getSourceDirectories(element.resourcePath, element.projectInfo)
      //   );
      // case TreeItemKind.TestsRoot:
      //   return Promise.resolve(this.getTestDirectories(element.resourcePath, element.projectInfo));
      // case TreeItemKind.DependenciesRoot:
      //   return Promise.resolve(this.getDependencyCategories(element.projectInfo));

      case TreeItemKind.GrailsArtifactFolders:
        return Promise.resolve(this.getArtifactFiles(element.projectInfo, element.artifactType));
      // case TreeItemKind.SourceDir:
      // case TreeItemKind.TestDir:
      //   return Promise.resolve(this.getDirectoryContents(element.resourcePath, element.kind));
      // case TreeItemKind.ViewsFolder:
      //   return Promise.resolve(this.getViewFiles(element.resourcePath));
      // case TreeItemKind.AssetsFolder:
      //   return Promise.resolve(this.getAssetFiles(element.resourcePath));

      // case TreeItemKind.DEPENDENCY_CATEGORY:
      //   return Promise.resolve(this.getDependencyList(element.projectInfo, element.label));

      default:
        console.log(`⚠️ [GRAILS-TREE] Unknown TreeItemKind: ${element.kind}`);
        return Promise.resolve([]);
    }
  }

  /** Get project nodes */
  protected getProjectNodes(projects: ProjectInfo[]): GrailsTreeItem[] {
    if (!projects?.length) {
      console.debug("[GRAILS-TREE] getProjectNodes: no projects found");
      return [];
    }

    const items = projects
      .filter(project => {
        if (!project?.rootPath || !project?.name) {
          console.debug(
            "[GRAILS-TREE] getProjectNodes: skipping invalid project",
            project?.name || "unnamed"
          );
          return false;
        }
        return true;
      })
      .map(
        project =>
          new GrailsTreeItem(
            this.getProjectLabel(project),
            TreeItemCollapsibleState.Expanded,
            TreeItemKind.ProjectRoot,
            {
              command: "grails.selectProject",
              title: "Select Project",
              arguments: [project],
            },
            project,
            project.rootPath
          )
      );

    console.log(`[GRAILS-TREE] getProjectNodes: created ${items.length} project nodes`);
    return items;
  }

  /** Generate project label with version info */
  private getProjectLabel(project: ProjectInfo): string {
    const typeLabel =
      project.type === ProjectType.GrailsPlugin
        ? "Plugin"
        : project.type === ProjectType.Grails
          ? "Grails"
          : "Groovy";

    const version = project.grailsVersion ?? project.pluginVersion;
    return version ? `${project.name} (${typeLabel} ${version})` : `${project.name} (${typeLabel})`;
  }

  /** Get grails-app sections */
  private getRootContainers(project: ProjectInfo): GrailsTreeItem[] {
    const items: GrailsTreeItem[] = [];

    // 1. Grails Artefacts (only if grails-app exists)
    if (this.hasGrailsApp(project)) {
      items.push(
        new GrailsTreeItem(
          "Grails Artefacts",
          TreeItemCollapsibleState.Expanded,
          TreeItemKind.GrailsAppRoot,
          NO_ACTION_COMMAND,
          project,
          path.join(project.rootPath, "grails-app")
        )
      );
    }

    // 2. Configuration (only if conf folder exists)
    if (this.hasConfigFolder(project)) {
      items.push(
        new GrailsTreeItem(
          "Configuration",
          TreeItemCollapsibleState.Collapsed,
          TreeItemKind.ConfigRoot,
          NO_ACTION_COMMAND,
          project,
          path.join(project.rootPath, "grails-app", "conf")
        )
      );
    }

    const i18nFolder = path.join(project.rootPath, "grails-app", "i18n");
    if (this.folderExists(i18nFolder)) {
      items.push(
        new GrailsTreeItem(
          "Internationalization",
          TreeItemCollapsibleState.Collapsed,
          TreeItemKind.I18nRoot,
          NO_ACTION_COMMAND,
          project,
          i18nFolder
        )
      );
    }

    const assetsFolder = path.join(project.rootPath, "grails-app", "assets");
    if (this.folderExists(assetsFolder)) {
      items.push(
        new GrailsTreeItem(
          "Assets",
          TreeItemCollapsibleState.Collapsed,
          TreeItemKind.AssetsRoot,
          NO_ACTION_COMMAND,
          project,
          assetsFolder
        )
      );
    }

    // 3. Views (only if views folder exists)
    if (this.hasViewsFolder(project)) {
      items.push(
        new GrailsTreeItem(
          "Views",
          TreeItemCollapsibleState.Collapsed,
          TreeItemKind.ViewsRoot,
          NO_ACTION_COMMAND,
          project,
          path.join(project.rootPath, "grails-app", "views")
        )
      );
    }

    // 4. Routes (Virtual - always show for Grails projects)
    // if (this.isGrailsProject(project)) {
    //   items.push(
    //     new GrailsTreeItem(
    //       "Routes",
    //       TreeItemCollapsibleState.Collapsed,
    //       TreeItemKind.RoutesRoot,
    //       NO_ACTION_COMMAND,
    //       project
    //     )
    //   );
    // }

    // 5. Source (only if src folder exists)
    if (this.hasSourceFolder(project)) {
      items.push(
        new GrailsTreeItem(
          "Sources",
          TreeItemCollapsibleState.Collapsed,
          TreeItemKind.SrcRoot,
          NO_ACTION_COMMAND,
          project,
          path.join(project.rootPath, "src", "main")
        )
      );
    }

    // 6. Tests (only if test folder exists)
    if (this.hasTestFolder(project)) {
      items.push(
        new GrailsTreeItem(
          "Tests",
          TreeItemCollapsibleState.Collapsed,
          TreeItemKind.TestsRoot,
          NO_ACTION_COMMAND,
          project,
          path.join(project.rootPath, "src", "test")
        )
      );
    }

    const intiFolder = path.join(project.rootPath, "grails-app", "init");
    if (this.folderExists(intiFolder)) {
      items.push(
        new GrailsTreeItem(
          "Init (Startup)",
          TreeItemCollapsibleState.Collapsed,
          TreeItemKind.InitRoot,
          NO_ACTION_COMMAND,
          project,
          intiFolder
        )
      );
    }

    // 7. Dependencies (always show - parsed from build.gradle)
    // items.push(
    //   new GrailsTreeItem(
    //     "Dependencies",
    //     TreeItemCollapsibleState.Collapsed,
    //     TreeItemKind.DependenciesRoot,
    //     NO_ACTION_COMMAND,
    //     project
    //   )
    // );

    // 8. Utilities (always show for productivity features)
    // items.push(
    //   new GrailsTreeItem(
    //     "Utilities",
    //     TreeItemCollapsibleState.Collapsed,
    //     TreeItemKind.UTILITIES_ROOT,
    //     NO_ACTION_COMMAND,
    //     project
    //   )
    // );

    return items;
  }

  /** Get grails-app specific structure */
  private getGrailsArtefactCategories(project: ProjectInfo): GrailsTreeItem[] {
    const items: GrailsTreeItem[] = [];

    // Assets (inside grails-app)
    // if (this.hasAssetsFolder(project)) {
    //   const assetCount = this.getAssetCount(project);
    //   items.push(
    //     new GrailsTreeItem(
    //       `Assets (${assetCount})`,
    //       TreeItemCollapsibleState.Collapsed,
    //       TreeItemKind.AssetsRoot,
    //       NO_ACTION_COMMAND,
    //       project,
    //       path.join(project.rootPath, "grails-app", "assets")
    //     )
    //   );
    // }

    const artefactCategories = [
      {
        label: "Controllers",
        type: ArtifactType.Controller,
        folder: "controllers",
      },
      {
        label: "Services",
        type: ArtifactType.Service,
        folder: "services",
      },
      {
        label: "Domains",
        type: ArtifactType.Domain,
        folder: "domain",
      },
      {
        label: "Tag Libraries",
        type: ArtifactType.TagLib,
        folder: "taglib",
      },
      {
        label: "Interceptors",
        type: ArtifactType.Interceptor,
        folder: "controllers",
      },
      {
        label: "Jobs",
        type: ArtifactType.Job,
        folder: "jobs",
      },
      {
        label: "Commands",
        type: ArtifactType.Command,
        folder: "controllers",
      },
      {
        label: "Utils",
        type: ArtifactType.Utils,
        folder: "utils",
      },
    ];

    // Only show categories that exist
    for (const category of artefactCategories) {
      const categoryPath = path.join(project.rootPath, "grails-app", category.folder);

      if (this.folderExists(categoryPath)) {
        const count = this.getArtefactCount(categoryPath, category.type);

        const displayLabel = count > 0 ? `${category.label} (${count})` : category.label;

        items.push(
          new GrailsTreeItem(
            displayLabel,
            TreeItemCollapsibleState.Collapsed,
            TreeItemKind.GrailsArtifactFolders,
            NO_ACTION_COMMAND,
            project,
            categoryPath,
            category.type
          )
        );
      }
    }

    return items;
  }

  private getConfigurationItems(project: ProjectInfo): GrailsTreeItem[] {
    const items: GrailsTreeItem[] = [];
    const confPath = path.join(project.rootPath, "grails-app", "conf");

    if (!this.folderExists(confPath)) {
      return items;
    }

    // Application Config Files
    const appConfigFiles = ["application.yml", "application.groovy", "application.properties"];

    const appConfigItems = appConfigFiles
      .map(file => path.join(confPath, file))
      .filter(filePath => fs.existsSync(filePath))
      .map(
        filePath =>
          new GrailsTreeItem(
            path.basename(filePath),
            TreeItemCollapsibleState.None,
            TreeItemKind.ConfFile,
            { command: "vscode.open", title: "Open", arguments: [Uri.file(filePath)] },
            project,
            filePath
          )
      );

    if (appConfigItems.length > 0) {
      items.push(
        new GrailsTreeItem(
          `🏗️ Application Config (${appConfigItems.length})`,
          TreeItemCollapsibleState.Expanded,
          TreeItemKind.ConfFolder,
          NO_ACTION_COMMAND,
          project,
          confPath
        )
      );
      items.push(...appConfigItems);
    }

    // Logging Config
    const logbackPath = path.join(confPath, "logback.groovy");
    if (fs.existsSync(logbackPath)) {
      items.push(
        new GrailsTreeItem(
          "logback.groovy",
          TreeItemCollapsibleState.None,
          TreeItemKind.ConfFile,
          { command: "vscode.open", title: "Open", arguments: [Uri.file(logbackPath)] },
          project,
          logbackPath
        )
      );
    }

    // Spring Beans
    const resourcesPath = path.join(confPath, "spring", "resources.groovy");
    if (fs.existsSync(resourcesPath)) {
      items.push(
        new GrailsTreeItem(
          "resources.groovy",
          TreeItemCollapsibleState.None,
          TreeItemKind.ConfFile,
          { command: "vscode.open", title: "Open", arguments: [Uri.file(resourcesPath)] },
          project,
          resourcesPath
        )
      );
    }

    // URL Mappings
    const urlMappingsPath = path.join(confPath, "UrlMappings.groovy");
    if (fs.existsSync(urlMappingsPath)) {
      items.push(
        new GrailsTreeItem(
          "UrlMappings.groovy",
          TreeItemCollapsibleState.None,
          TreeItemKind.ConfFile,
          { command: "vscode.open", title: "Open", arguments: [Uri.file(urlMappingsPath)] },
          project,
          urlMappingsPath
        )
      );
    }

    // Bootstrap
    const bootstrapPath = path.join(confPath, "BootStrap.groovy");
    if (fs.existsSync(bootstrapPath)) {
      items.push(
        new GrailsTreeItem(
          "BootStrap.groovy",
          TreeItemCollapsibleState.None,
          TreeItemKind.ConfFile,
          { command: "vscode.open", title: "Open", arguments: [Uri.file(bootstrapPath)] },
          project,
          bootstrapPath
        )
      );
    }

    return items;
  }

  private getViewStructure(project: ProjectInfo): GrailsTreeItem[] {
    const items: GrailsTreeItem[] = [];
    const viewsPath = path.join(project.rootPath, "grails-app", "views");

    if (!this.folderExists(viewsPath)) {
      return items;
    }

    try {
      // Layouts folder
      const layoutsPath = path.join(viewsPath, "layouts");
      if (this.folderExists(layoutsPath)) {
        const layoutCount = this.getFileCount(layoutsPath, [".gsp"]);
        items.push(
          new GrailsTreeItem(
            `Layouts (${layoutCount})`,
            TreeItemCollapsibleState.Collapsed,
            TreeItemKind.ViewsFolder,
            NO_ACTION_COMMAND,
            project,
            layoutsPath
          )
        );
      }

      // View folders (controllers)
      const viewFolders = fs
        .readdirSync(viewsPath, { withFileTypes: true })
        .filter(dirent => dirent.isDirectory() && dirent.name !== "layouts")
        .map(dirent => {
          const folderPath = path.join(viewsPath, dirent.name);
          const viewCount = this.getFileCount(folderPath, [".gsp"]);
          return new GrailsTreeItem(
            `${dirent.name} (${viewCount})`,
            TreeItemCollapsibleState.Collapsed,
            TreeItemKind.ViewsFolder,
            NO_ACTION_COMMAND,
            project,
            folderPath
          );
        });

      items.push(...viewFolders);
    } catch (error) {
      console.warn(`Error reading views directory ${viewsPath}:`, error);
    }

    return items;
  }

  private getAssetStructure(project: ProjectInfo): GrailsTreeItem[] {
    const items: GrailsTreeItem[] = [];
    const assetsPath = path.join(project.rootPath, "grails-app", "assets");

    if (!this.folderExists(assetsPath)) {
      return items;
    }

    try {
      const assetCategories = [
        { name: "stylesheets", extensions: [".css", ".scss", ".sass"] },
        { name: "javascripts", extensions: [".js", ".coffee"] },
        { name: "images", extensions: [".png", ".jpg", ".jpeg", ".gif", ".svg"] },
      ];

      for (const category of assetCategories) {
        const categoryPath = path.join(assetsPath, category.name);
        if (this.folderExists(categoryPath)) {
          const assetCount = this.getRecursiveFileCount(categoryPath, category.extensions);
          items.push(
            new GrailsTreeItem(
              `${category.name} (${assetCount})`,
              TreeItemCollapsibleState.Collapsed,
              TreeItemKind.AssetsFolder,
              NO_ACTION_COMMAND,
              project,
              categoryPath
            )
          );
        }
      }
    } catch (error) {
      console.warn(`Error reading assets directory ${assetsPath}:`, error);
    }

    return items;
  }

  private getRouteList(project: ProjectInfo): GrailsTreeItem[] {
    // Parse UrlMappings.groovy if it exists
    const urlMappingsPath = path.join(project.rootPath, "grails-app", "conf", "UrlMappings.groovy");

    if (!fs.existsSync(urlMappingsPath)) {
      return [
        new GrailsTreeItem(
          "❌ No URL mappings found",
          TreeItemCollapsibleState.None,
          TreeItemKind.Error,
          NO_ACTION_COMMAND,
          project
        ),
      ];
    }

    try {
      // Simple route parsing (could be enhanced with AST)
      const content = fs.readFileSync(urlMappingsPath, "utf8");
      const routes = this.parseSimpleRoutes(content);

      if (routes.length === 0) {
        return [
          new GrailsTreeItem(
            "Default routing active",
            TreeItemCollapsibleState.None,
            TreeItemKind.Route,
            NO_ACTION_COMMAND,
            project
          ),
        ];
      }

      return routes.map(
        route =>
          new GrailsTreeItem(
            `${route.method} ${route.pattern} → ${route.controller}#${route.action}`,
            TreeItemCollapsibleState.None,
            TreeItemKind.Route,
            {
              command: "grails.goToController",
              title: "Go to Controller",
              arguments: [route.controller, route.action],
            },
            project
          )
      );
    } catch (error) {
      console.warn(`Error parsing UrlMappings.groovy:`, error);
      return [
        new GrailsTreeItem(
          "❌ Error parsing URL mappings",
          TreeItemCollapsibleState.None,
          TreeItemKind.Error,
          NO_ACTION_COMMAND,
          project
        ),
      ];
    }
  }

  private parseSimpleRoutes(
    _content: string
  ): { method: string; pattern: string; controller: string; action: string }[] {
    // Simple regex-based parsing - could be enhanced with proper AST parsing
    const routes: { method: string; pattern: string; controller: string; action: string }[] = [];

    // Look for common patterns

    // Default Grails pattern
    routes.push({
      method: "ALL",
      pattern: "/$controller/$action?/$id?",
      controller: "default",
      action: "index",
    });

    return routes;
  }

  private getSourceDirectories(
    sourcePath: string | undefined,
    project: ProjectInfo
  ): GrailsTreeItem[] {
    const items: GrailsTreeItem[] = [];

    if (sourcePath === undefined || !this.folderExists(sourcePath)) {
      return items;
    }

    try {
      const sourceDirs = ["groovy", "java", "resources"];

      for (const dir of sourceDirs) {
        const dirPath = path.join(sourcePath, dir);
        if (this.folderExists(dirPath)) {
          const fileCount = this.getRecursiveFileCount(dirPath, [".groovy", ".java"]);
          items.push(
            new GrailsTreeItem(
              `${dir} (${fileCount})`,
              TreeItemCollapsibleState.Collapsed,
              TreeItemKind.SourceDir,
              NO_ACTION_COMMAND,
              project,
              dirPath
            )
          );
        }
      }
    } catch (error) {
      console.warn(`Error reading source directory ${sourcePath}:`, error);
    }

    return items;
  }

  private getTestDirectories(testPath: string | undefined, project: ProjectInfo): GrailsTreeItem[] {
    const items: GrailsTreeItem[] = [];

    if (testPath === undefined || !this.folderExists(testPath)) {
      return items;
    }

    try {
      const testDirs = ["groovy", "java", "resources"];

      for (const dir of testDirs) {
        const dirPath = path.join(testPath, dir);
        if (this.folderExists(dirPath)) {
          const testCount = this.getRecursiveFileCount(dirPath, [".groovy", ".java"]);
          items.push(
            new GrailsTreeItem(
              `${dir} (${testCount})`,
              TreeItemCollapsibleState.Collapsed,
              TreeItemKind.TestDir,
              NO_ACTION_COMMAND,
              project,
              dirPath
            )
          );
        }
      }
    } catch (error) {
      console.warn(`Error reading test directory ${testPath}:`, error);
    }

    return items;
  }

  private getDependencyCategories(project: ProjectInfo): GrailsTreeItem[] {
    const items: GrailsTreeItem[] = [];

    // Parse build.gradle for dependencies
    const buildGradlePath = path.join(project.rootPath, "build.gradle");

    if (!fs.existsSync(buildGradlePath)) {
      return [
        new GrailsTreeItem(
          "❌ No build.gradle found",
          TreeItemCollapsibleState.None,
          TreeItemKind.Error,
          NO_ACTION_COMMAND,
          project
        ),
      ];
    }

    try {
      const dependencies = this.parseBuildGradleDependencies(buildGradlePath);

      // Group by type
      const plugins = dependencies.filter(dep => dep.type === "plugin");
      const libraries = dependencies.filter(
        dep => dep.type === "implementation" || dep.type === "compile" || dep.type === "runtime"
      );

      if (plugins.length > 0) {
        items.push(
          new GrailsTreeItem(
            `Grails Plugins (${plugins.length})`,
            TreeItemCollapsibleState.Collapsed,
            TreeItemKind.DEPENDENCY_CATEGORY,
            NO_ACTION_COMMAND,
            project
          )
        );
      }

      if (libraries.length > 0) {
        items.push(
          new GrailsTreeItem(
            `External Libraries (${libraries.length})`,
            TreeItemCollapsibleState.Collapsed,
            TreeItemKind.DEPENDENCY_CATEGORY,
            NO_ACTION_COMMAND,
            project
          )
        );
      }

      if (items.length === 0) {
        items.push(
          new GrailsTreeItem(
            "No dependencies found",
            TreeItemCollapsibleState.None,
            TreeItemKind.Error,
            NO_ACTION_COMMAND,
            project
          )
        );
      }
    } catch (error) {
      console.warn(`Error parsing build.gradle:`, error);
      items.push(
        new GrailsTreeItem(
          "❌ Error parsing dependencies",
          TreeItemCollapsibleState.None,
          TreeItemKind.Error,
          NO_ACTION_COMMAND,
          project
        )
      );
    }

    return items;
  }

  private parseBuildGradleDependencies(
    buildGradlePath: string
  ): { name: string; version: string; type: string }[] {
    const content = fs.readFileSync(buildGradlePath, "utf8");
    const dependencies: { name: string; version: string; type: string }[] = [];

    // Basic regex patterns for dependencies
    const patterns = [
      /implementation\s+['"](.*?):(.*?):(.*?)['"]/g,
      /compile\s+['"](.*?):(.*?):(.*?)['"]/g,
      /runtime\s+['"](.*?):(.*?):(.*?)['"]/g,
    ];

    patterns.forEach(pattern => {
      let match;
      while ((match = pattern.exec(content)) !== null) {
        dependencies.push({
          name: `${match[1]}:${match[2]}`,
          version: match[3],
          type: "implementation",
        });
      }
    });

    // Look for plugins
    const pluginPattern = /id\s+['"](.*?)['"]\s+version\s+['"](.*?)['"]/g;
    let pluginMatch;
    while ((pluginMatch = pluginPattern.exec(content)) !== null) {
      dependencies.push({
        name: pluginMatch[1],
        version: pluginMatch[2],
        type: "plugin",
      });
    }

    return dependencies;
  }

  private getArtifactFiles(project: ProjectInfo, artifactType?: ArtifactType): GrailsTreeItem[] {
    if (!artifactType) {
      return [];
    }

    const basePath = path.join(project.rootPath, "grails-app");
    let artifactPath: string | null = null;

    switch (artifactType) {
      case ArtifactType.Controller:
        artifactPath = path.join(basePath, "controllers");
        break;
      case ArtifactType.Service:
        artifactPath = path.join(basePath, "services");
        break;
      case ArtifactType.Domain:
        artifactPath = path.join(basePath, "domain");
        break;
      case ArtifactType.TagLib:
        artifactPath = path.join(basePath, "taglib");
        break;
      case ArtifactType.Job:
        artifactPath = path.join(basePath, "jobs");
        break;
      case ArtifactType.Utils:
        artifactPath = path.join(basePath, "utils");
        break;
      default:
        // Unknown artifact; return empty list
        return [];
    }

    if (!this.folderExists(artifactPath)) {
      return [];
    }

    // Use getPackagesWithFiles to build {package} nodes, with files nested
    // Use TreeItemKind.GrailsArtifactFolders for packages and TreeItemKind.ArtifactFile for files
    return this.getPackagesWithFiles(
      artifactPath,
      artifactPath,
      TreeItemKind.GrailsArtifactFolders,
      TreeItemKind.ArtifactFile,
      project,
      artifactType
    );
  }

  private getDirectoryContents(
    resourcePath: string | undefined,
    kind: TreeItemKind
  ): GrailsTreeItem[] {
    const items: GrailsTreeItem[] = [];

    if (resourcePath === undefined || !this.folderExists(resourcePath)) {
      return items;
    }

    try {
      const entries = fs.readdirSync(resourcePath, { withFileTypes: true });

      // Directories first
      const directories = entries
        .filter(entry => entry.isDirectory())
        .map(entry => {
          const fullPath = path.join(resourcePath, entry.name);
          return new GrailsTreeItem(
            `${entry.name}`,
            TreeItemCollapsibleState.Collapsed,
            kind === TreeItemKind.SourceDir ? TreeItemKind.SourceDir : TreeItemKind.TestDir,
            { command: "grails.noAction", title: "No Action" },
            undefined,
            fullPath
          );
        });

      // Files second
      const files = entries
        .filter(entry => entry.isFile() && this.isRelevantFile(entry.name))
        .map(entry => {
          const fullPath = path.join(resourcePath, entry.name);
          return new GrailsTreeItem(
            entry.name,
            TreeItemCollapsibleState.None,
            kind === TreeItemKind.SourceDir ? TreeItemKind.SrcFile : TreeItemKind.TestFile,
            { command: "vscode.open", title: "Open", arguments: [Uri.file(fullPath)] },
            undefined,
            fullPath
          );
        });

      items.push(...directories, ...files);
    } catch (error) {
      console.warn(`Error reading directory ${resourcePath}:`, error);
    }

    return items;
  }

  private isRelevantFile(fileName: string): boolean {
    const relevantExtensions = [".groovy", ".java", ".gsp", ".yml", ".yaml", ".properties", ".xml"];
    return relevantExtensions.some(ext => fileName.endsWith(ext));
  }

  private getViewFiles(resourcePath?: string): GrailsTreeItem[] {
    const items: GrailsTreeItem[] = [];

    if (!resourcePath || !this.folderExists(resourcePath)) {
      return items;
    }

    try {
      const entries = fs
        .readdirSync(resourcePath, { withFileTypes: true })
        .filter(entry => entry.isFile() && entry.name.endsWith(".gsp"))
        .map(entry => {
          const fullPath = path.join(resourcePath, entry.name);
          return new GrailsTreeItem(
            entry.name,
            TreeItemCollapsibleState.None,
            TreeItemKind.ViewFile,
            { command: "vscode.open", title: "Open", arguments: [Uri.file(fullPath)] },
            undefined,
            fullPath
          );
        });

      items.push(...entries);
    } catch (error) {
      console.warn(`Error reading view files in ${resourcePath}:`, error);
    }

    return items;
  }

  private getAssetFiles(resourcePath?: string): GrailsTreeItem[] {
    const items: GrailsTreeItem[] = [];

    if (!resourcePath || !this.folderExists(resourcePath)) {
      return items;
    }

    try {
      const entries = fs.readdirSync(resourcePath, { withFileTypes: true });

      // Subdirectories first
      const directories = entries
        .filter(entry => entry.isDirectory())
        .map(entry => {
          const fullPath = path.join(resourcePath, entry.name);
          const fileCount = this.getRecursiveFileCount(fullPath, [
            ".css",
            ".scss",
            ".js",
            ".coffee",
            ".png",
            ".jpg",
            ".gif",
          ]);
          return new GrailsTreeItem(
            `${entry.name} (${fileCount})`,
            TreeItemCollapsibleState.Collapsed,
            TreeItemKind.AssetsFolder,
            { command: "grails.noAction", title: "No Action" },
            undefined,
            fullPath
          );
        });

      // Asset files
      const assetExtensions = [
        ".css",
        ".scss",
        ".sass",
        ".js",
        ".coffee",
        ".png",
        ".jpg",
        ".jpeg",
        ".gif",
        ".svg",
      ];
      const files = entries
        .filter(entry => entry.isFile() && assetExtensions.some(ext => entry.name.endsWith(ext)))
        .map(entry => {
          const fullPath = path.join(resourcePath, entry.name);
          return new GrailsTreeItem(
            entry.name,
            TreeItemCollapsibleState.None,
            TreeItemKind.AssetFile,
            { command: "vscode.open", title: "Open", arguments: [Uri.file(fullPath)] },
            undefined,
            fullPath
          );
        });

      items.push(...directories, ...files);
    } catch (error) {
      console.warn(`Error reading asset files in ${resourcePath}:`, error);
    }

    return items;
  }

  private getDependencyList(project: ProjectInfo, category: string): GrailsTreeItem[] {
    const items: GrailsTreeItem[] = [];

    try {
      const buildGradlePath = path.join(project.rootPath, "build.gradle");
      if (!fs.existsSync(buildGradlePath)) {
        return items;
      }

      const dependencies = this.parseBuildGradleDependencies(buildGradlePath);

      let filteredDeps: { name: string; version: string; type: string }[] = [];

      if (category.includes("Plugins")) {
        filteredDeps = dependencies.filter(dep => dep.type === "plugin");
      } else if (category.includes("Libraries")) {
        filteredDeps = dependencies.filter(
          dep => dep.type === "implementation" || dep.type === "compile" || dep.type === "runtime"
        );
      }

      const dependencyItems = filteredDeps.map(
        dep =>
          new GrailsTreeItem(
            `${dep.name} (${dep.version})`,
            TreeItemCollapsibleState.None,
            TreeItemKind.Dependency,
            NO_ACTION_COMMAND,
            project
          )
      );

      items.push(...dependencyItems);
    } catch (error) {
      console.warn(`Error getting dependency list for ${category}:`, error);
    }

    return items;
  }

  private getFileCount(dirPath: string, extensions: string[]): number {
    if (!this.folderExists(dirPath)) {
      return 0;
    }

    try {
      return fs.readdirSync(dirPath).filter(file => extensions.some(ext => file.endsWith(ext)))
        .length;
    } catch (error) {
      console.warn(`Error counting files in ${dirPath}:`, error);
      return 0;
    }
  }

  // Core folder checks
  private hasGrailsApp(project: ProjectInfo): boolean {
    return this.folderExists(path.join(project.rootPath, "grails-app"));
  }

  private hasConfigFolder(project: ProjectInfo): boolean {
    return this.folderExists(path.join(project.rootPath, "grails-app", "conf"));
  }

  private hasViewsFolder(project: ProjectInfo): boolean {
    return this.folderExists(path.join(project.rootPath, "grails-app", "views"));
  }

  private hasAssetsFolder(project: ProjectInfo): boolean {
    return this.folderExists(path.join(project.rootPath, "grails-app", "assets"));
  }

  private hasSourceFolder(project: ProjectInfo): boolean {
    return (
      this.folderExists(path.join(project.rootPath, "src", "main")) ||
      this.folderExists(path.join(project.rootPath, "src"))
    );
  }

  private hasTestFolder(project: ProjectInfo): boolean {
    return this.folderExists(path.join(project.rootPath, "src", "test"));
  }

  // Project type detection
  private isGrailsProject(project: ProjectInfo): boolean {
    return (
      project.type === ProjectType.Grails ||
      project.type === ProjectType.GrailsPlugin ||
      this.hasGrailsApp(project)
    );
  }

  // Generic folder existence check
  private folderExists(folderPath: string): boolean {
    try {
      return fs.existsSync(folderPath) && fs.lstatSync(folderPath).isDirectory();
    } catch (error) {
      console.warn(`Error checking folder ${folderPath}:`, error);
      return false;
    }
  }

  // Count artefacts by type
  private getArtefactCount(categoryPath: string, artifactType: ArtifactType): number {
    if (!this.folderExists(categoryPath)) {
      return 0;
    }

    try {
      const suffix = this.getArtefactSuffix(artifactType);
      const files = fs.readdirSync(categoryPath);

      return files.filter(file => {
        if (artifactType === ArtifactType.Domain) {
          return file.endsWith(".groovy") && !file.includes("Test");
        }
        return file.endsWith(`${suffix}.groovy`);
      }).length;
    } catch (error) {
      console.warn(`Error counting artefacts in ${categoryPath}:`, error);
      return 0;
    }
  }

  private getArtefactSuffix(artifactType: ArtifactType): string {
    switch (artifactType) {
      case ArtifactType.Controller:
        return "Controller";
      case ArtifactType.Service:
        return "Service";
      case ArtifactType.Domain:
        return "";
      case ArtifactType.TagLib:
        return "TagLib";
      case ArtifactType.Interceptor:
        return "Interceptor";
      case ArtifactType.Job:
        return "Job";
      case ArtifactType.Command:
        return "Command";
      default:
        return "";
    }
  }

  // Count assets
  private getAssetCount(project: ProjectInfo): number {
    const assetsPath = path.join(project.rootPath, "grails-app", "assets");
    if (!this.folderExists(assetsPath)) {
      return 0;
    }

    let count = 0;
    const assetDirs = ["stylesheets", "javascripts", "images"];

    for (const dir of assetDirs) {
      const dirPath = path.join(assetsPath, dir);
      if (this.folderExists(dirPath)) {
        count += this.getRecursiveFileCount(dirPath, [
          ".css",
          ".scss",
          ".js",
          ".coffee",
          ".png",
          ".jpg",
          ".gif",
        ]);
      }
    }

    return count;
  }

  // Recursive file counting
  private getRecursiveFileCount(dirPath: string, extensions: string[]): number {
    if (!this.folderExists(dirPath)) {
      return 0;
    }

    let count = 0;
    try {
      const entries = fs.readdirSync(dirPath, { withFileTypes: true });

      for (const entry of entries) {
        const fullPath = path.join(dirPath, entry.name);
        if (entry.isDirectory()) {
          count += this.getRecursiveFileCount(fullPath, extensions);
        } else if (extensions.some(ext => entry.name.endsWith(ext))) {
          count++;
        }
      }
    } catch (error) {
      console.warn(`Error reading directory ${dirPath}:`, error);
    }

    return count;
  }

  /** Register event listeners for auto-refresh */
  private registerEventListeners(): void {
    console.log("🔧 TreeExplorer: Registering event listeners");
    const eventBus = EventBus.getInstance();

    // Refresh when projects are discovered or changed
    const projectsDiscoveredSub = eventBus.subscribe(EventType.PROJECTS_DISCOVERED, event => {
      console.log(
        `🌳 TreeExplorer: PROJECTS_DISCOVERED received with ${event.projects.length} projects`
      );
      console.log(
        "🌳 Projects:",
        event.projects.map(p => ({ name: p.name, type: p.type, path: p.rootPath }))
      );
      this.projects = event.projects;
      this.refresh();
    });

    const projectChangedSub = eventBus.subscribe(EventType.PROJECT_CHANGED, event => {
      console.log(`🌳 TreeExplorer: PROJECT_CHANGED received for ${event.project.name}`);

      const index = this.projects.findIndex(p => p.id === event.project.id);
      if (index >= 0) {
        this.projects[index] = event.project;
      } else {
        this.projects.push(event.project);
      }

      this.refresh();
    });

    // Listen for tree refresh events (e.g., icon theme changes)
    const treeRefreshSub = eventBus.subscribe(EventType.TREE_REFRESH, () => {
      console.log("🌳 TreeExplorer: TREE_REFRESH received");
      this.refresh();
    });

    this.disposables.push(projectsDiscoveredSub, projectChangedSub, treeRefreshSub);
  }

  private getPackagesWithFiles(
    basePath: string,
    currentPath: string,
    kind: TreeItemKind,
    fileKind: TreeItemKind,
    project: ProjectInfo,
    artifactType?: ArtifactType
  ): GrailsTreeItem[] {
    if (!this.folderExists(currentPath)) {
      return [];
    }

    try {
      const entries = fs.readdirSync(currentPath, { withFileTypes: true });
      const dirs = entries.filter(e => e.isDirectory());

      // Flatten single-child directories into a combined package name
      const packageNameParts: string[] = [];
      let nodePath = currentPath;
      let nodeEntries = dirs;

      while (
        nodeEntries.length === 1 && // Exactly one subdirectory
        fs
          .readdirSync(path.join(nodePath, nodeEntries[0].name), { withFileTypes: true })
          .filter(e => e.isDirectory()).length <= 1 // and the child has at most one subdirectory
      ) {
        packageNameParts.push(nodeEntries[0].name);
        nodePath = path.join(nodePath, nodeEntries[0].name);
        const nextEntries = fs.readdirSync(nodePath, { withFileTypes: true });
        nodeEntries = nextEntries.filter(e => e.isDirectory());
      }

      const relativePackageName = path.relative(basePath, nodePath).split(path.sep).join(".");
      const packageLabel = relativePackageName ? `{${relativePackageName}}` : "<root>";

      const fileChildren = fs
        .readdirSync(nodePath, { withFileTypes: true })
        .filter(f => f.isFile())
        .map(
          file =>
            new GrailsTreeItem(
              file.name,
              TreeItemCollapsibleState.None,
              fileKind,
              {
                command: "vscode.open",
                title: "Open",
                arguments: [Uri.file(path.join(nodePath, file.name))],
              },
              project,
              path.join(nodePath, file.name),
              artifactType
            )
        );

      const subPackageChildren = nodeEntries.flatMap(dir =>
        this.getPackagesWithFiles(
          basePath,
          path.join(nodePath, dir.name),
          kind,
          fileKind,
          project,
          artifactType
        )
      );

      const allChildren = [...fileChildren, ...subPackageChildren];

      return [
        new GrailsTreeItem(
          packageLabel,
          TreeItemCollapsibleState.Collapsed,
          kind,
          { command: "grails.noAction", title: "No Action" },
          project,
          nodePath,
          artifactType,
          allChildren.length > 0 ? allChildren : undefined
        ),
      ];
    } catch (error) {
      console.warn(`Error reading and flattening packages in ${currentPath}:`, error);
      return [];
    }
  }

  public dispose(): void {
    void this._onDidChangeTreeData?.dispose();
    this.disposables?.forEach(d => void d.dispose());
    this.disposables = [];
  }
}
