import fs from "fs";
import path from "path";
import { TreeItemCollapsibleState } from "vscode";
import { ServiceContainer } from "../core/container/ServiceContainer";
import type { ArtifactType, ProjectInfo } from "../features/models/modelTypes";
import { ErrorSeverity, ErrorSource } from "../services/errors/errorTypes";
import type { GrailsTreeItem } from "../ui/treeExplorer/GrailsTreeItem";

/**
 * Utility class for recursive file and directory scanning
 */
export class FileScannerUtils {
  /**
   * Recursively scan directory and return tree items
   */
  static scanDirectory(
    dirPath: string,
    project: ProjectInfo,
    createDirectoryCallback: (
      name: string,
      fullPath: string,
      project?: ProjectInfo
    ) => GrailsTreeItem,
    createFileCallback: (
      name: string,
      fullPath: string,
      project?: ProjectInfo,
      artifactType?: ArtifactType
    ) => GrailsTreeItem,
    fileFilter: (fileName: string) => boolean
  ): GrailsTreeItem[] {
    const items: GrailsTreeItem[] = [];

    try {
      const entries = fs.readdirSync(dirPath, { withFileTypes: true });

      for (const entry of entries) {
        const fullPath = path.join(dirPath, entry.name);

        if (entry.isDirectory()) {
          items.push(createDirectoryCallback(entry.name, fullPath, project));
        } else if (fileFilter(entry.name)) {
          items.push(createFileCallback(entry.name, fullPath, project));
        }
      }
    } catch (error) {
      ServiceContainer.getInstance().errorService.handleError(
        `Error reading directory ${dirPath}`,
        error,
        ErrorSource.ProjectService,
        ErrorSeverity.Warning
      );
    }

    return this.sortItems(items);
  }

  /**
   * Sort items: directories first, then files, alphabetically
   */
  static sortItems(items: GrailsTreeItem[]): GrailsTreeItem[] {
    return items.sort((a, b) => {
      // Directories first, then files
      if (a.collapsibleState !== b.collapsibleState) {
        return a.collapsibleState === TreeItemCollapsibleState.None ? 1 : -1;
      }
      return a.label.localeCompare(b.label);
    });
  }

  /**
   * Check if path exists
   */
  static pathExists(path: string): boolean {
    try {
      return fs.existsSync(path);
    } catch {
      return false;
    }
  }

  /**
   * Common source file extensions for Grails/Groovy projects
   */
  static getSourceFileExtensions(): string[] {
    return [
      ".groovy",
      ".java",
      ".gsp",
      ".properties",
      ".yml",
      ".yaml",
      ".xml",
      ".json",
      ".sql",
      ".js",
      ".css",
      ".scss",
      ".less",
    ];
  }

  /**
   * Check if filename has supported source file extension
   */
  static isSupportedSourceFile(fileName: string, extensions?: string[]): boolean {
    const supportedExtensions = extensions ?? this.getSourceFileExtensions();
    return supportedExtensions.some(ext => fileName.toLowerCase().endsWith(ext));
  }
}
