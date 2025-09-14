import { ThemeColor, ThemeIcon } from "vscode";
import { ArtifactType, ProjectType } from "../../features/models/modelTypes";
import { TreeItemKind } from "../treeExplorer/TreeItemKind";
import { IconThemeDetector } from "./IconThemeDetector";

/** Icon configuration */
interface IconConfig {
  icon: string;
  color: string;
}

/** Centralized, theme-aware icon provider */
export class IconProvider {
  // Project types
  private static readonly PROJECT_ICONS: Record<ProjectType, IconConfig> = {
    [ProjectType.Grails]: { icon: "rocket", color: "charts.green" },
    [ProjectType.GrailsPlugin]: { icon: "extensions", color: "charts.blue" },
    [ProjectType.Groovy]: { icon: "symbol-method", color: "charts.yellow" },
    [ProjectType.Unknown]: { icon: "question", color: "charts.gray" },
  };

  // Root sections (headers)
  private static readonly ROOT_SECTIONS: Partial<Record<TreeItemKind, IconConfig>> = {
    [TreeItemKind.GrailsAppRoot]: { icon: "rocket", color: "charts.green" },
    [TreeItemKind.AssetsRoot]: { icon: "folder-library", color: "charts.pink" },
    [TreeItemKind.ConfigRoot]: { icon: "settings-gear", color: "charts.gray" },
    [TreeItemKind.IntiRoot]: { icon: "play", color: "charts.green" },
    [TreeItemKind.ViewsRoot]: { icon: "browser", color: "charts.green" },
    [TreeItemKind.RoutesRoot]: { icon: "link", color: "charts.purple" },
    [TreeItemKind.SrcRoot]: { icon: "symbol-package", color: "charts.yellow" },
    [TreeItemKind.TestsRoot]: { icon: "beaker", color: "testing.iconPassed" },
    [TreeItemKind.DependenciesRoot]: { icon: "package", color: "charts.purple" },
  };

  // Artifact category headers
  private static readonly ARTIFACT_CATEGORIES: Partial<Record<ArtifactType, IconConfig>> = {
    [ArtifactType.Controller]: { icon: "globe", color: "charts.blue" },
    [ArtifactType.Service]: { icon: "gear", color: "charts.orange" },
    [ArtifactType.Domain]: { icon: "database", color: "charts.purple" },
    [ArtifactType.TagLib]: { icon: "symbol-snippet", color: "charts.yellow" },
    [ArtifactType.Interceptor]: { icon: "debug-disconnect", color: "charts.red" },
    [ArtifactType.Command]: { icon: "terminal", color: "charts.purple" },
    [ArtifactType.Job]: { icon: "clock", color: "charts.orange" },
    [ArtifactType.Utils]: { icon: "tools", color: "charts.yellow" },
    [ArtifactType.Config]: { icon: "settings-gear", color: "charts.gray" },
    [ArtifactType.Tests]: { icon: "beaker", color: "testing.iconPassed" },
  };

  // Grails artifact files (grails-app/*)
  private static readonly GRAILS_ARTIFACT_FILES: Partial<Record<ArtifactType, IconConfig>> = {
    [ArtifactType.Controller]: { icon: "globe", color: "charts.blue" },
    [ArtifactType.Service]: { icon: "gear", color: "charts.orange" },
    [ArtifactType.Domain]: { icon: "database", color: "charts.purple" },
    [ArtifactType.TagLib]: { icon: "symbol-snippet", color: "charts.yellow" },
    [ArtifactType.Interceptor]: { icon: "debug-disconnect", color: "charts.red" },
    [ArtifactType.Command]: { icon: "terminal", color: "charts.purple" },
    [ArtifactType.Job]: { icon: "clock", color: "charts.orange" },
    [ArtifactType.Utils]: { icon: "tools", color: "charts.yellow" },
  };

  // Special Grails files (explicit)
  private static readonly SPECIAL_GRAILS_FILES: Partial<Record<ArtifactType, IconConfig>> = {
    [ArtifactType.UrlMapping]: { icon: "link", color: "charts.purple" }, // UrlMappings.groovy
    [ArtifactType.Bootstrap]: { icon: "play", color: "charts.green" }, // Bootstrap.groovy
    [ArtifactType.ApplicationConfig]: { icon: "gear", color: "charts.blue" }, // Application.groovy
    [ArtifactType.SpringConfig]: { icon: "symbol-property", color: "charts.green" }, // resources under conf/spring
    [ArtifactType.HibernateConfig]: { icon: "database", color: "charts.blue" }, // DataSource.groovy/hibernate
    [ArtifactType.Resources]: { icon: "folder-library", color: "charts.gray" }, // resources.groovy (legacy)
    [ArtifactType.Filter]: { icon: "filter", color: "charts.orange" }, // *Filters.groovy
  };

  // Map filenames to ArtifactType for special files
  private static readonly SPECIAL_FILE_MAPPINGS: Record<string, ArtifactType> = {
    "urlmappings.groovy": ArtifactType.UrlMapping,
    "bootstrap.groovy": ArtifactType.Bootstrap,
    "application.groovy": ArtifactType.ApplicationConfig,
    "resources.groovy": ArtifactType.Resources,
    "datasource.groovy": ArtifactType.HibernateConfig,
    "config.groovy": ArtifactType.Config,
  };

  // Special tree items
  private static readonly SPECIAL_TREE_ITEMS: Partial<Record<TreeItemKind, IconConfig>> = {
    [TreeItemKind.Package]: { icon: "symbol-package", color: "charts.yellow" },
    [TreeItemKind.Route]: { icon: "symbol-ruler", color: "charts.purple" },
    [TreeItemKind.Dependency]: { icon: "package", color: "charts.purple" },
    [TreeItemKind.Error]: { icon: "error", color: "errorForeground" },
  };

  // =========================
  // PUBLIC API METHODS
  // =========================

  static getProjectIcon(projectType: ProjectType): ThemeIcon {
    const config = this.PROJECT_ICONS[projectType];
    return this.createThemedIcon(config);
  }

  /** Single entry point for icon decisions */
  static getIcon(
    kind: TreeItemKind,
    projectType?: ProjectType,
    artifactType?: ArtifactType,
    resourcePath?: string
  ): ThemeIcon | undefined {
    // Project root
    if (kind === TreeItemKind.ProjectRoot) {
      const type = projectType ?? ProjectType.Unknown;
      const config = this.PROJECT_ICONS[type];
      return this.createThemedIcon(config);
    }

    // Special tree items
    if (this.isSpecialTreeItem(kind)) {
      const config = this.SPECIAL_TREE_ITEMS[kind];
      return config ? this.createThemedIcon(config) : undefined;
    }

    // Root sections
    if (this.isRootSection(kind)) {
      if (kind === TreeItemKind.AssetsRoot) {
        return undefined;
      }
      const config = this.ROOT_SECTIONS[kind];
      return config ? this.createThemedIcon(config) : undefined;
    }

    // Sub-categories (headers under roots)
    if (this.isSubCategory(kind) && artifactType) {
      const config = this.ARTIFACT_CATEGORIES[artifactType];
      return config ? this.createThemedIcon(config) : undefined;
    }

    // Files (leaf nodes)
    if (this.isLeaf(kind) && artifactType) {
      return this.getFileIcon(artifactType, resourcePath);
    }

    // No custom icon
    return undefined;
  }

  static getRootIcons(kind: TreeItemKind): ThemeIcon | undefined {
    const config = this.ROOT_SECTIONS[kind];
    return config ? this.createThemedIcon(config) : undefined;
  }

  static getSubCategoryIcons(artifactType: ArtifactType): ThemeIcon | undefined {
    const config = this.ARTIFACT_CATEGORIES[artifactType];
    return config ? this.createThemedIcon(config) : undefined;
  }

  static getSpecialTreeItem(itemType: TreeItemKind): ThemeIcon | undefined {
    const config = this.SPECIAL_TREE_ITEMS[itemType];
    return config ? this.createThemedIcon(config) : undefined;
  }

  /** File icons — only for Grails artifacts we should control */
  static getFileIcon(artifactType: ArtifactType, resourcePath?: string): ThemeIcon | undefined {
    // Special Grails files take precedence
    if (resourcePath && this.isSpecialGrailsFile(resourcePath)) {
      return this.getSpecialGrailsFileIcon(resourcePath);
    }

    // Grails artifact files in grails-app/*
    if (resourcePath && this.isGrailsArtifactFile(resourcePath)) {
      const config = this.GRAILS_ARTIFACT_FILES[artifactType];
      return config ? this.createThemedIcon(config) : undefined;
    }

    // Let VS Code/theme handle everything else
    return undefined;
  }

  static getSpecialGrailsFileIcon(resourcePath: string): ThemeIcon | undefined {
    const fileName = resourcePath.split(/[/\\]/).pop()?.toLowerCase() ?? "";
    const artifactType = this.SPECIAL_FILE_MAPPINGS[fileName];
    if (artifactType) {
      const config = this.SPECIAL_GRAILS_FILES[artifactType];
      return config ? this.createThemedIcon(config) : undefined;
    }
    return undefined;
  }

  // Internal helpers

  static isLeafKind(kind: TreeItemKind): boolean {
    return this.isLeaf(kind);
  }

  private static isSpecialTreeItem(kind: TreeItemKind): boolean {
    return [
      TreeItemKind.Route,
      TreeItemKind.Dependency,
      TreeItemKind.Error,
      TreeItemKind.Package,
    ].includes(kind);
  }

  private static isRootSection(kind: TreeItemKind): boolean {
    return Object.prototype.hasOwnProperty.call(this.ROOT_SECTIONS, kind);
  }

  private static isSubCategory(kind: TreeItemKind): boolean {
    return [
      TreeItemKind.GrailsArtifactFolders,
      TreeItemKind.AssetsFolder,
      TreeItemKind.ViewsFolder,
    ].includes(kind);
  }

  private static isLeaf(kind: TreeItemKind): boolean {
    return [
      TreeItemKind.ArtifactFile,
      TreeItemKind.ConfFile,
      TreeItemKind.ViewFile,
      TreeItemKind.AssetFile,
      TreeItemKind.SrcFile,
      TreeItemKind.TestFile,
    ].includes(kind);
  }

  /** Theme-aware icon creation */
  private static createThemedIcon(config: IconConfig): ThemeIcon {
    const useEnhanced = IconThemeDetector.shouldUseEnhancedIcons();
    const colorSupported = IconThemeDetector.supportsColoredIcons();

    if (useEnhanced) {
      return colorSupported && config.color
        ? new ThemeIcon(config.icon, new ThemeColor(config.color))
        : new ThemeIcon(config.icon);
    }
    return new ThemeIcon(config.icon);
  }

  private static isSpecialGrailsFile(resourcePath: string): boolean {
    const fileName = resourcePath.split(/[/\\]/).pop()?.toLowerCase() ?? "";
    return Object.prototype.hasOwnProperty.call(this.SPECIAL_FILE_MAPPINGS, fileName);
  }

  private static isGrailsArtifactFile(resourcePath: string): boolean {
    const grailsArtifactPaths = [
      "grails-app/controllers/",
      "grails-app/services/",
      "grails-app/domain/",
      "grails-app/taglib/",
      "grails-app/interceptors/",
      "grails-app/jobs/",
      "grails-app/commands/",
      "grails-app/utils/",
      "grails-app/views/",
    ];
    return grailsArtifactPaths.some(path => resourcePath.includes(path));
  }

  // Optional utility badges
  static getStatusIcon(count: number, hasErrors = false): ThemeIcon {
    if (hasErrors) {
      return new ThemeIcon("error", new ThemeColor("errorForeground"));
    }
    return new ThemeIcon(
      count > 0 ? "circle-filled" : "circle-outline",
      new ThemeColor(count > 0 ? "charts.green" : "charts.gray")
    );
  }

  static getBadgeText(count: number): string | undefined {
    return count > 0 ? count.toString() : undefined;
  }
}
