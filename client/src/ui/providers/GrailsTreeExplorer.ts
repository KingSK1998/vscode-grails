import path from "path";
import { TreeItemCollapsibleState, Uri, ThemeColor, ThemeIcon } from "vscode";
import {
  ProjectInfo,
  ProjectType,
  ARTIFACT_DIRECTORIES,
  ArtifactCounts,
  GrailsArtifactType,
  FILE_PATHS,
} from "../../features/models/modelTypes";
import { ProjectTreeItem, TreeExplorerBase, TreeItemType } from "./TreeExplorerBase";
import { GrailsIconProvider } from "../icons/GrailsIconProvider";

/** Grails-specific tree explorer with full artifact structure */
export class GrailsTreeExplorer extends TreeExplorerBase {
  /** Get children for a tree node */
  getChildren(element?: ProjectTreeItem): Promise<ProjectTreeItem[]> {
    console.log("📂 Getting children for:", element?.label || "root");

    if (!element) {
      // Root level - show all Grails projects
      const grailsProjects = this.projects.filter(
        p => p.type === ProjectType.Grails || p.type === ProjectType.GrailsPlugin
      );
      return Promise.resolve(this.getFilteredProjectNodes(grailsProjects));
    }

    if (element.itemType === TreeItemType.PROJECT_ROOT && element.projectInfo) {
      // Project level - show Grails structure (artifacts + source folders)
      return Promise.resolve(this.getGrailsProjectChildren(element.projectInfo));
    }

    if (element.itemType === TreeItemType.SOURCE_CATEGORY && element.projectInfo) {
      // Source folders category - show source directories
      return Promise.resolve(this.getStandardSourceFolders(element.projectInfo));
    }

    if (element.itemType === TreeItemType.SOURCE_FOLDER && element.resourcePath) {
      // Handle source folder expansion
      return Promise.resolve(this.getSourceFiles(element.resourcePath));
    }

    if (
      element.itemType === TreeItemType.ARTIFACT_CATEGORY &&
      element.artifactType &&
      element.projectInfo
    ) {
      return Promise.resolve(this.getArtifactFiles(element.projectInfo, element.artifactType));
    }

    return Promise.resolve([]);
  }

  /** Get Grails project structure */
  private getGrailsProjectChildren(project: ProjectInfo): ProjectTreeItem[] {
    const items: ProjectTreeItem[] = [];

    // === GRAILS-APP ARTIFACTS (primary) ===
    items.push(
      new ProjectTreeItem(
        "🚀 Grails Artifacts",
        TreeItemCollapsibleState.Expanded,
        TreeItemType.ARTIFACT_HEADER,
        project
      )
    );

    const grailsAppItems = this.getGrailsAppStructure(project);
    items.push(...grailsAppItems);

    // === SOURCE FOLDERS (secondary) ===
    items.push(
      new ProjectTreeItem(
        "📁 Source Folders",
        TreeItemCollapsibleState.Collapsed,
        TreeItemType.SOURCE_CATEGORY,
        project
      )
    );

    return items;
  }

  /** Get grails-app specific structure */
  private getGrailsAppStructure(project: ProjectInfo): ProjectTreeItem[] {
    const items: ProjectTreeItem[] = [];
    const counts = project.artifactCounts;

    // Core MVC artifacts with counts
    const coreArtifacts = [
      { type: GrailsArtifactType.CONTROLLER, count: counts?.controllers || 0 },
      { type: GrailsArtifactType.SERVICE, count: counts?.services || 0 },
      { type: GrailsArtifactType.DOMAIN, count: counts?.domains || 0 },
      { type: GrailsArtifactType.VIEW, count: counts?.views || counts?.gspFiles || 0 },
      { type: GrailsArtifactType.TAGLIB, count: counts?.taglibs || 0 },
    ];

    for (const artifact of coreArtifacts) {
      const label = this.formatCategoryLabel(artifact.type, artifact.count);
      items.push(
        new ProjectTreeItem(
          `  ${label}`, // Indent for visual hierarchy
          TreeItemCollapsibleState.Collapsed,
          TreeItemType.ARTIFACT_CATEGORY,
          project,
          undefined,
          artifact.type
        )
      );
    }

    // Add other Grails-specific categories
    this.addGrailsSpecificCategories(items, project, counts);

    return items;
  }

  /** Get files within an artifact category */
  private getArtifactFiles(
    project: ProjectInfo,
    artifactType: GrailsArtifactType
  ): ProjectTreeItem[] {
    const items: ProjectTreeItem[] = [];
    const artifactDir = ARTIFACT_DIRECTORIES[artifactType];

    if (!artifactDir) {
      console.warn(`No directory mapping found for artifact type: ${artifactType}`);
      return items;
    }

    const artifactPath = path.join(project.rootPath, FILE_PATHS.GRAILS_APP, artifactDir);
    if (!this.pathExists(artifactPath)) return items;

    try {
      const fs = require("fs");
      const files = fs.readdirSync(artifactPath);

      for (const file of files) {
        if (this.isGrailsArtifactFile(file)) {
          const filePath = path.join(artifactPath, file);
          items.push(
            new GrailsTreeItem(
              file,
              TreeItemCollapsibleState.None,
              TreeItemType.ARTIFACT_FILE,
              project,
              filePath,
              artifactType,
              {
                command: "vscode.open",
                title: "Open",
                arguments: [Uri.file(filePath)],
              }
            )
          );
        }
      }
    } catch (error) {
      console.error(`Error reading artifact directory ${artifactPath}:`, error);
    }

    return items.sort((a, b) => a.label.localeCompare(b.label));
  }

  /** Check if file is a supported Grails artifact file */
  private isGrailsArtifactFile(fileName: string): boolean {
    const grailsExtensions = [
      ".groovy",
      ".gsp",
      ".java",
      ".xml",
      ".properties",
      ".yml",
      ".yaml",
      ".json",
      ".sql",
      ".js",
      ".css",
      ".scss",
      ".less",
    ];
    return grailsExtensions.some(ext => fileName.toLowerCase().endsWith(ext));
  }

  /** Add Grails-specific categories - assets, config, etc. */
  private addGrailsSpecificCategories(
    items: ProjectTreeItem[],
    project: ProjectInfo,
    counts: ArtifactCounts | undefined
  ): void {
    // Interceptors & Filters (if available)
    if (counts?.interceptors || counts?.filters) {
      items.push(
        new GrailsTreeItem(
          this.formatCategoryLabel(GrailsArtifactType.INTERCEPTOR, counts?.interceptors || 0),
          TreeItemCollapsibleState.Collapsed,
          TreeItemType.ARTIFACT_CATEGORY,
          project,
          undefined,
          GrailsArtifactType.INTERCEPTOR
        )
      );
    }

    // Configuration
    items.push(
      new GrailsTreeItem(
        this.formatCategoryLabel(GrailsArtifactType.CONFIG, counts?.config || 0),
        TreeItemCollapsibleState.Collapsed,
        TreeItemType.ARTIFACT_CATEGORY,
        project,
        undefined,
        GrailsArtifactType.CONFIG
      )
    );

    // Assets & Resources
    if (counts?.assets) {
      items.push(
        new GrailsTreeItem(
          this.formatCategoryLabel(GrailsArtifactType.ASSETS, counts.assets),
          TreeItemCollapsibleState.Collapsed,
          TreeItemType.ARTIFACT_CATEGORY,
          project,
          undefined,
          GrailsArtifactType.ASSETS
        )
      );
    }

    // Tests
    if (counts?.unitTests || counts?.integrationTests) {
      const totalTests = (counts?.unitTests || 0) + (counts?.integrationTests || 0);
      items.push(
        new GrailsTreeItem(
          this.formatCategoryLabel(GrailsArtifactType.TESTS, totalTests),
          TreeItemCollapsibleState.Collapsed,
          TreeItemType.ARTIFACT_CATEGORY,
          project,
          undefined,
          GrailsArtifactType.TESTS
        )
      );
    }
  }

  /** Format category label with count */
  private formatCategoryLabel(artifactType: GrailsArtifactType, count: number): string {
    const typeName = this.getDisplayName(artifactType);
    return count > 0 ? `${typeName} (${count})` : typeName;
  }

  /** Get display name for artifact type */
  private getDisplayName(artifactType: GrailsArtifactType): string {
    switch (artifactType) {
      case GrailsArtifactType.CONTROLLER:
        return "Controllers";
      case GrailsArtifactType.SERVICE:
        return "Services";
      case GrailsArtifactType.DOMAIN:
        return "Domain Classes";
      case GrailsArtifactType.VIEW:
        return "Views";
      case GrailsArtifactType.TAGLIB:
        return "Tag Libraries";
      case GrailsArtifactType.INTERCEPTOR:
        return "Interceptors";
      case GrailsArtifactType.CONFIG:
        return "Configuration";
      case GrailsArtifactType.ASSETS:
        return "Assets";
      case GrailsArtifactType.I18N:
        return "Internationalization";
      case GrailsArtifactType.TESTS:
        return "Tests";
      default:
        return artifactType.toString();
    }
  }

  /** Get filtered project nodes for Grails projects only */
  private getFilteredProjectNodes(grailsProjects: ProjectInfo[]): ProjectTreeItem[] {
    if (grailsProjects.length === 0) {
      return [
        new ProjectTreeItem(
          "No Grails projects found",
          TreeItemCollapsibleState.None,
          TreeItemType.PROJECT_ROOT
        ),
      ];
    }

    return grailsProjects.map(
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

  // === ABSTRACT FACTORY METHODS ===

  /** Grails-specific supported file types */
  protected isSupportedSourceFile(fileName: string): boolean {
    const supportedExtensions = [
      ".groovy",
      ".gsp",
      ".java", // Core Grails files
      ".properties",
      ".yml",
      ".yaml",
      ".xml",
      ".json", // Config files
      // Note: More restrictive than Groovy - no .kt, .scala typically
    ];
    return supportedExtensions.some(ext => fileName.toLowerCase().endsWith(ext));
  }

  /** Create Grails-specific directory items */
  protected createDirectoryItem(
    name: string,
    fullPath: string,
    project?: ProjectInfo
  ): ProjectTreeItem {
    return new GrailsTreeItem(
      name,
      TreeItemCollapsibleState.Collapsed,
      TreeItemType.SOURCE_FOLDER,
      project,
      fullPath
    );
  }

  /** Create Grails-specific file items */
  protected createFileItem(name: string, fullPath: string, project?: ProjectInfo): ProjectTreeItem {
    return new GrailsTreeItem(
      name,
      TreeItemCollapsibleState.None,
      TreeItemType.ARTIFACT_FILE,
      project,
      fullPath,
      undefined,
      {
        command: "vscode.open",
        title: "Open",
        arguments: [Uri.file(fullPath)],
      }
    );
  }
}

/** Grails-specific tree item with enhanced icons */
class GrailsTreeItem extends ProjectTreeItem {
  protected getIcon(): ThemeIcon {
    switch (this.itemType) {
      case TreeItemType.PROJECT_ROOT:
        // return this.getGrailsProjectIcon();
        return GrailsIconProvider.getProjectIcon(this.projectInfo?.type || "");
      case TreeItemType.ARTIFACT_CATEGORY:
        // return this.getGrailsCategoryIcon();
        return this.artifactType
          ? GrailsIconProvider.getArtifactIcon(this.artifactType)
          : GrailsIconProvider.getTreeIcon(this.itemType);
      default:
        // return super.getIcon();
        return GrailsIconProvider.getTreeIcon(this.itemType);
    }
  }

  private getGrailsProjectIcon(): ThemeIcon {
    if (!this.projectInfo) return new ThemeIcon("folder");

    switch (this.projectInfo.type) {
      case ProjectType.Grails:
        return new ThemeIcon("rocket");
      case ProjectType.GrailsPlugin:
        return new ThemeIcon("extensions");
      default:
        return new ThemeIcon("folder");
    }
  }

  private getGrailsCategoryIcon(): ThemeIcon {
    switch (this.artifactType) {
      case GrailsArtifactType.CONTROLLER:
        return new ThemeIcon("globe");
      case GrailsArtifactType.SERVICE:
        return new ThemeIcon("gear");
      case GrailsArtifactType.DOMAIN:
        return new ThemeIcon("database");
      case GrailsArtifactType.VIEW:
        return new ThemeIcon("browser");
      case GrailsArtifactType.TAGLIB:
        return new ThemeIcon("symbol-snippet");
      case GrailsArtifactType.INTERCEPTOR:
        return new ThemeIcon("debug-disconnect");
      case GrailsArtifactType.ASSETS:
        return new ThemeIcon("file-media");
      case GrailsArtifactType.I18N:
        return new ThemeIcon("globe");
      case GrailsArtifactType.CONFIG:
        return new ThemeIcon("settings");
      case GrailsArtifactType.TESTS:
        return new ThemeIcon("beaker");
      default:
        return new ThemeIcon("folder");
    }
  }
}
