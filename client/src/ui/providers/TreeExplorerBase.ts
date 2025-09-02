import path from "path";
import {
  TreeItem,
  TreeItemCollapsibleState,
  Command,
  Uri,
  ThemeIcon,
  EventEmitter,
  ExtensionContext,
  TreeDataProvider,
} from "vscode";
import { GrailsArtifactType, ProjectInfo, ProjectType } from "../../features/models/modelTypes";
import { ServiceContainer } from "../../core/container/ServiceContainer";
import { EventBus } from "../../core/events/EventBus";
import {
  ProjectsDiscoveredEvent,
  EventType,
  ProjectChangedEvent,
} from "../../core/events/eventTypes";

/** Tree item types for different content categories */
export enum TreeItemType {
  PROJECT_ROOT = "project-root",
  ARTIFACT_HEADER = "artifact-header", // "🚀 Grails Artifacts"
  ARTIFACT_CATEGORY = "artifact-category", // Controllers, Services, etc.
  SOURCE_CATEGORY = "source-category", // "📁 Source Folders"
  SOURCE_FOLDER = "source-folder", // src/main/groovy, etc.
  ARTIFACT_FILE = "artifact-file",
  CONFIG_FOLDER = "config-folder",
}

/** Base Tree item with common functionality */
export class ProjectTreeItem extends TreeItem {
  constructor(
    public readonly label: string,
    public readonly collapsibleState: TreeItemCollapsibleState,
    public readonly itemType: TreeItemType,
    public readonly projectInfo?: ProjectInfo,
    public readonly resourcePath?: string,
    public readonly artifactType?: GrailsArtifactType,
    public readonly command?: Command
  ) {
    super(label, collapsibleState);
    // Set context value for command enablement
    this.contextValue = this.getContextValue();
    // Set icon based on item type and project type
    this.iconPath = this.getIcon();
    // Add tooltip with additional info
    this.tooltip = this.getTooltip();

    // Set resource URI if available
    if (resourcePath) {
      this.resourceUri = Uri.file(resourcePath);
    }
  }

  private getContextValue(): string {
    const contexts: string[] = [this.itemType];

    if (this.projectInfo) {
      contexts.push(this.projectInfo.type);
    }

    if (this.artifactType) {
      contexts.push(this.artifactType);
    }

    return contexts.join("-");
  }

  protected getIcon(): ThemeIcon {
    // Base icon logic - can be overridden
    switch (this.itemType) {
      case TreeItemType.PROJECT_ROOT:
        return new ThemeIcon("folder");
      case TreeItemType.SOURCE_FOLDER:
        return new ThemeIcon("folder");
      case TreeItemType.ARTIFACT_FILE:
        return this.getFileIcon();
      case TreeItemType.CONFIG_FOLDER:
        return new ThemeIcon("settings-gear");
      default:
        return new ThemeIcon("file");
    }
  }

  private getFileIcon(): ThemeIcon {
    if (!this.resourcePath) return new ThemeIcon("file");

    const ext = path.extname(this.resourcePath).toLowerCase();
    switch (ext) {
      case ".groovy":
        return new ThemeIcon("symbol-method");
      case ".gsp":
        return new ThemeIcon("browser");
      case ".yml":
      case ".yaml":
        return new ThemeIcon("settings");
      case ".properties":
        return new ThemeIcon("gear");
      default:
        return new ThemeIcon("file");
    }
  }

  private getTooltip(): string {
    if (this.projectInfo && this.itemType === TreeItemType.PROJECT_ROOT) {
      const lines = [
        `**${this.projectInfo.name}**`,
        `Type: ${this.projectInfo.type}`,
        `Path: ${this.projectInfo.rootPath}`,
      ];

      if (this.projectInfo.grailsVersion) {
        lines.push(`Grails: ${this.projectInfo.grailsVersion}`);
      }

      if (this.projectInfo.groovyVersion) {
        lines.push(`Groovy: ${this.projectInfo.groovyVersion}`);
      }

      if (this.projectInfo.dependencies?.length) {
        lines.push(`Dependencies: ${this.projectInfo.dependencies.length}`);
      }

      return lines.join("\n");
    }

    return this.label;
  }
}

/** Abstract base class for all tree explorers */
export abstract class TreeExplorerBase implements TreeDataProvider<ProjectTreeItem> {
  protected readonly _onDidChangeTreeData = new EventEmitter<
    ProjectTreeItem | undefined | null | void
  >();
  readonly onDidChangeTreeData = this._onDidChangeTreeData.event;

  protected container: ServiceContainer;
  protected projects: ProjectInfo[] = [];

  constructor(protected readonly context: ExtensionContext) {
    this.container = ServiceContainer.getInstance();
    this.registerEventListeners();
    this.refresh();
    console.log("🌳 TreeDataProvider initialized");
  }

  /** Refresh the entire tree */
  refresh(): void {
    console.log("🔄 Refreshing tree data...");
    this.projects = this.container.projectService.getProjects();
    this._onDidChangeTreeData.fire();
  }

  /** Get tree item for display */
  getTreeItem(element: ProjectTreeItem): TreeItem {
    return element;
  }

  /** Get children for a tree node - implemented by subclasses */
  abstract getChildren(element?: ProjectTreeItem): Thenable<ProjectTreeItem[]>;

  /** Shared source file scanning with abstract factory methods */
  protected getSourceFiles(folderPath: string): ProjectTreeItem[] {
    const items: ProjectTreeItem[] = [];

    try {
      const fs = require("fs");
      const entries = fs.readdirSync(folderPath, { withFileTypes: true });

      for (const entry of entries) {
        const fullPath = path.join(folderPath, entry.name);

        if (entry.isDirectory()) {
          items.push(this.createDirectoryItem(entry.name, fullPath));
        } else if (this.isSupportedSourceFile(entry.name)) {
          items.push(this.createFileItem(entry.name, fullPath));
        }
      }
    } catch (error) {
      console.error(`Error reading source directory ${folderPath}:`, error);
    }

    return items.sort((a, b) => {
      // Directories first, then files
      if (a.collapsibleState !== b.collapsibleState) {
        return a.collapsibleState === TreeItemCollapsibleState.None ? 1 : -1;
      }
      return a.label.localeCompare(b.label);
    });
  }

  /** Get project root nodes */
  protected getProjectNodes(): ProjectTreeItem[] {
    if (this.projects.length === 0) {
      return [
        new ProjectTreeItem(
          "No projects found",
          TreeItemCollapsibleState.None,
          TreeItemType.PROJECT_ROOT
        ),
      ];
    }

    return this.projects.map(
      project =>
        new ProjectTreeItem(
          this.getProjectLabel(project),
          TreeItemCollapsibleState.Expanded,
          TreeItemType.PROJECT_ROOT,
          project,
          project.rootPath
        )
    );
  }

  /** Get project label with version info */
  protected getProjectLabel(project: ProjectInfo): string {
    const typeLabel =
      project.type === ProjectType.GrailsPlugin
        ? "Plugin"
        : project.type === ProjectType.Grails
          ? "Grails"
          : "Groovy";

    const version = project.grailsVersion || project.pluginVersion;
    return version ? `${project.name} (${typeLabel} ${version})` : `${project.name} (${typeLabel})`;
  }

  /** Get standard source folders - shared between Groovy and Grails */
  protected getStandardSourceFolders(project: ProjectInfo): ProjectTreeItem[] {
    const items: ProjectTreeItem[] = [];

    const sourceFolders = [
      { path: path.join(project.rootPath, "src", "main", "groovy"), label: "src/main/groovy" },
      { path: path.join(project.rootPath, "src", "main", "java"), label: "src/main/java" },
      {
        path: path.join(project.rootPath, "src", "main", "resources"),
        label: "src/main/resources",
      },
      { path: path.join(project.rootPath, "src", "test", "groovy"), label: "src/test/groovy" },
      { path: path.join(project.rootPath, "src", "test", "java"), label: "src/test/java" },
      // Grails-specific integration test folder (ignore if doesn't exist)
      {
        path: path.join(project.rootPath, "src", "integration-test", "groovy"),
        label: "src/integration-test/groovy",
      },
    ];

    for (const folder of sourceFolders) {
      if (this.pathExists(folder.path)) {
        items.push(this.createDirectoryItem(`  ${folder.label}`, folder.path, project));
      }
    }

    return items;
  }

  /** Utility: Check if path exists */
  protected pathExists(path: string): boolean {
    try {
      const fs = require("fs");
      return fs.existsSync(path);
    } catch {
      return false;
    }
  }

  /** Register event listeners for auto-refresh */
  private registerEventListeners(): void {
    const eventBus = EventBus.getInstance();

    // Refresh when projects are discovered or changed
    eventBus.subscribe<ProjectsDiscoveredEvent>(EventType.PROJECTS_DISCOVERED, () => {
      this.refresh();
    });

    eventBus.subscribe<ProjectChangedEvent>(EventType.PROJECT_CHANGED, () => {
      this.refresh();
    });
  }

  /** Abstract: Check if file type is supported by this explorer */
  protected abstract isSupportedSourceFile(fileName: string): boolean;

  /** Abstract: Create directory tree item with appropriate subclasses */
  protected abstract createDirectoryItem(
    name: string,
    fullPath: string,
    project?: ProjectInfo
  ): ProjectTreeItem;

  /** Abstract: Create file tree item with appropriate subclasses */
  protected abstract createFileItem(
    name: string,
    fullPath: string,
    project?: ProjectInfo
  ): ProjectTreeItem;
}
