import path from "path";
import { ProjectInfo, ProjectType } from "../../features/models/modelTypes";
import { TreeExplorerBase, ProjectTreeItem, TreeItemType } from "./TreeExplorerBase";
import { ThemeColor, ThemeIcon, TreeItemCollapsibleState, Uri } from "vscode";

/** Groovy-specific tree explorer with source folder structure */
export class GroovyTreeExplorer extends TreeExplorerBase {
  getChildren(element?: ProjectTreeItem): Promise<ProjectTreeItem[]> {
    if (!element) {
      // Root level - show all Groovy projects
      const groovyProjects = this.projects.filter(p => p.type === ProjectType.Groovy);
      return Promise.resolve(this.getFilteredProjectNodes(groovyProjects));
    }

    if (element.itemType === TreeItemType.PROJECT_ROOT && element.projectInfo) {
      // Pure Groovy projects - show source folders only
      return Promise.resolve(this.getStandardSourceFolders(element.projectInfo));
    }

    if (element.itemType === TreeItemType.SOURCE_FOLDER && element.resourcePath) {
      // Handle source folder expansion
      return Promise.resolve(this.getSourceFiles(element.resourcePath));
    }

    return Promise.resolve([]);
  }

  /** Get filtered project nodes for Groovy projects only */
  private getFilteredProjectNodes(groovyProjects: ProjectInfo[]): ProjectTreeItem[] {
    if (groovyProjects.length === 0) {
      return [
        new ProjectTreeItem(
          "No Groovy projects found",
          TreeItemCollapsibleState.None,
          TreeItemType.PROJECT_ROOT
        ),
      ];
    }

    return groovyProjects.map(
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

  /** Groovy-specific supported file types */
  protected isSupportedSourceFile(fileName: string): boolean {
    const supportedExtensions = [
      ".groovy",
      ".java",
      ".kt",
      ".scala", // JVM languages
      ".properties",
      ".xml",
      ".json",
      ".yml",
      ".yaml",
    ];
    return supportedExtensions.some(ext => fileName.toLowerCase().endsWith(ext));
  }

  /** Create Groovy-specific directory item */
  protected createDirectoryItem(
    name: string,
    fullPath: string,
    project?: ProjectInfo
  ): ProjectTreeItem {
    return new GroovyTreeItem(
      name,
      TreeItemCollapsibleState.Collapsed,
      TreeItemType.SOURCE_FOLDER,
      project,
      fullPath
    );
  }

  /** Create Groovy-specific file item */
  protected createFileItem(name: string, fullPath: string, project?: ProjectInfo): ProjectTreeItem {
    return new GroovyTreeItem(
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

/** Groovy-specific tree item with enhanced icons */
class GroovyTreeItem extends ProjectTreeItem {
  protected getIcon(): ThemeIcon {
    if (this.itemType === TreeItemType.PROJECT_ROOT && this.projectInfo?.type === "groovy") {
      return new ThemeIcon("symbol-method", new ThemeColor("charts.yellow"));
    }
    return super.getIcon();
  }
}
