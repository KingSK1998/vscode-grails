import { GrailsArtifactType } from "../../features/models/modelTypes";
import { TreeItemType } from "../providers/TreeExplorerBase";
import { IconThemeDetector } from "./IconThemeDetector";
import { ThemeColor, ThemeIcon } from "vscode";

export class GrailsIconProvider {
  /**
   * Get project icon based on type
   */
  static getProjectIcon(projectType: string): ThemeIcon {
    const useMaterial = IconThemeDetector.isMaterialThemeInstalled();

    if (useMaterial) {
      // Material Theme specific icons
      switch (projectType) {
        case "grails":
          return new ThemeIcon("rocket", new ThemeColor("charts.green"));
        case "grails-plugin":
          return new ThemeIcon("extensions", new ThemeColor("charts.blue"));
        case "groovy":
          return new ThemeIcon("symbol-method", new ThemeColor("charts.yellow"));
        default:
          return new ThemeIcon("folder-opened");
      }
    } else {
      // Built-in VS Code icons (more conservative)
      switch (projectType) {
        case "grails":
          return new ThemeIcon("rocket");
        case "grails-plugin":
          return new ThemeIcon("extensions");
        case "groovy":
          return new ThemeIcon("symbol-method");
        default:
          return new ThemeIcon("folder");
      }
    }
  }

  /**
   * Get artifact category icon
   */
  static getArtifactIcon(artifactType: GrailsArtifactType): ThemeIcon {
    const useMaterial = IconThemeDetector.isMaterialThemeInstalled();

    if (useMaterial) {
      // Enhanced icons for Material Theme users
      switch (artifactType) {
        case GrailsArtifactType.CONTROLLER:
          return new ThemeIcon("globe", new ThemeColor("charts.blue"));
        case GrailsArtifactType.SERVICE:
          return new ThemeIcon("gear", new ThemeColor("charts.orange"));
        case GrailsArtifactType.DOMAIN:
          return new ThemeIcon("database", new ThemeColor("charts.purple"));
        case GrailsArtifactType.VIEW:
          return new ThemeIcon("browser", new ThemeColor("charts.green"));
        case GrailsArtifactType.TAGLIB:
          return new ThemeIcon("symbol-snippet", new ThemeColor("charts.yellow"));
        case GrailsArtifactType.INTERCEPTOR:
          return new ThemeIcon("debug-disconnect", new ThemeColor("charts.red"));
        case GrailsArtifactType.ASSETS:
          return new ThemeIcon("file-media", new ThemeColor("charts.pink"));
        case GrailsArtifactType.CONFIG:
          return new ThemeIcon("settings-gear", new ThemeColor("charts.gray"));
        case GrailsArtifactType.TESTS:
          return new ThemeIcon("beaker", new ThemeColor("testing.iconPassed"));
        default:
          return new ThemeIcon("folder");
      }
    } else {
      // Simple built-in icons (guaranteed to work)
      switch (artifactType) {
        case GrailsArtifactType.CONTROLLER:
          return new ThemeIcon("symbol-class");
        case GrailsArtifactType.SERVICE:
          return new ThemeIcon("gear");
        case GrailsArtifactType.DOMAIN:
          return new ThemeIcon("symbol-struct");
        case GrailsArtifactType.VIEW:
          return new ThemeIcon("file-code");
        case GrailsArtifactType.TAGLIB:
          return new ThemeIcon("symbol-snippet");
        case GrailsArtifactType.INTERCEPTOR:
          return new ThemeIcon("debug-line-by-line");
        case GrailsArtifactType.ASSETS:
          return new ThemeIcon("file-media");
        case GrailsArtifactType.CONFIG:
          return new ThemeIcon("settings-gear");
        case GrailsArtifactType.TESTS:
          return new ThemeIcon("beaker");
        default:
          return new ThemeIcon("folder");
      }
    }
  }

  /**
   * Get generic tree item icon
   */
  static getTreeIcon(itemType: TreeItemType): ThemeIcon {
    const useMaterial = IconThemeDetector.isMaterialThemeInstalled();

    switch (itemType) {
      case TreeItemType.ARTIFACT_HEADER:
        return useMaterial
          ? new ThemeIcon("symbol-folder", new ThemeColor("charts.blue"))
          : new ThemeIcon("symbol-folder");
      case TreeItemType.SOURCE_CATEGORY:
        return useMaterial
          ? new ThemeIcon("folder-opened", new ThemeColor("charts.green"))
          : new ThemeIcon("folder-opened");
      case TreeItemType.SOURCE_FOLDER:
        return new ThemeIcon("folder");
      case TreeItemType.ARTIFACT_FILE:
        return new ThemeIcon("symbol-file");
      default:
        return new ThemeIcon("circle-filled");
    }
  }
}
