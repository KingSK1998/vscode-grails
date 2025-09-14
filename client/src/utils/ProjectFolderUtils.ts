import path from "path";
import type { ProjectInfo } from "../features/models/modelTypes";
import type { GrailsTreeExplorer } from "../ui/treeExplorer/GrailsTreeExplorer";
import { FileScannerUtils } from "./FileScannerUtils";

/**
 * Utility for standard project folder operations
 */
export class ProjectFolderUtils {
  /**
   * Get standard source folders for Groovy/Grails projects
   */
  static getStandardSourceFolders(
    project: ProjectInfo,
    createDirectoryCallback: (
      name: string,
      fullPath: string,
      project?: ProjectInfo
    ) => GrailsTreeExplorer
  ): GrailsTreeExplorer[] {
    const items: GrailsTreeExplorer[] = [];

    const sourceFolders = [
      { path: path.join(project.rootPath, "src", "main", "groovy"), label: "src/main/groovy" },
      { path: path.join(project.rootPath, "src", "main", "java"), label: "src/main/java" },
      {
        path: path.join(project.rootPath, "src", "main", "resources"),
        label: "src/main/resources",
      },
      { path: path.join(project.rootPath, "src", "test", "groovy"), label: "src/test/groovy" },
      { path: path.join(project.rootPath, "src", "test", "java"), label: "src/test/java" },
      // Grails-specific integration test folder
      {
        path: path.join(project.rootPath, "src", "integration-test", "groovy"),
        label: "src/integration-test/groovy",
      },
    ];

    for (const folder of sourceFolders) {
      if (FileScannerUtils.pathExists(folder.path)) {
        items.push(createDirectoryCallback(`  ${folder.label}`, folder.path, project));
      }
    }

    return items;
  }
}
