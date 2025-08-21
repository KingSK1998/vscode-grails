import { ThemeColor, ThemeIcon } from "vscode";
import { GrailsArtifactType } from "../utils/Constants";

export class GrailsIconProvider {
  private static readonly ARTIFACT_ICONS: Record<
    GrailsArtifactType,
    { icon: string; color: string }
  > = {
    [GrailsArtifactType.CONTROLLER]: { icon: "rocket", color: "charts.green" },
    [GrailsArtifactType.SERVICE]: { icon: "gear", color: "charts.blue" },
    [GrailsArtifactType.DOMAIN]: { icon: "database", color: "charts.purple" },
    [GrailsArtifactType.VIEW]: { icon: "browser", color: "charts.orange" },
    [GrailsArtifactType.TAGLIB]: { icon: "tag", color: "charts.red" },
    [GrailsArtifactType.CONFIG]: { icon: "settings-gear", color: "charts.gray" },
    [GrailsArtifactType.URL_MAPPING]: { icon: "link", color: "symbolIcon.interfaceForeground" },
    [GrailsArtifactType.I18N]: { icon: "globe", color: "charts.blue" },

    // Assets & Resources
    [GrailsArtifactType.ASSETS]: { icon: "symbol-color", color: "charts.orange" },
    [GrailsArtifactType.RESOURCES]: { icon: "folder-library", color: "charts.gray" },

    // Application Structure
    [GrailsArtifactType.INIT]: { icon: "rocket", color: "charts.green" },

    // Development & Testing
    [GrailsArtifactType.TESTS]: { icon: "beaker", color: "charts.blue" },
    [GrailsArtifactType.UNIT_TESTS]: { icon: "beaker", color: "charts.blue" },
    [GrailsArtifactType.INTEGRATION_TESTS]: { icon: "verified", color: "charts.green" },
    [GrailsArtifactType.GROOVY_SRC]: { icon: "symbol-method", color: "charts.green" },

    // Groovy Project Types
    [GrailsArtifactType.SOURCE_SETS]: { icon: "folder", color: "charts.gray" },
    [GrailsArtifactType.DEPENDENCIES]: { icon: "package", color: "charts.purple" },
    [GrailsArtifactType.TASKS]: { icon: "list-unordered", color: "charts.blue" },
    [GrailsArtifactType.STRUCTURE]: { icon: "symbol-structure", color: "charts.gray" },
  };

  private static readonly ARTIFACT_TOOLTIPS: Record<GrailsArtifactType, string> = {
    [GrailsArtifactType.CONTROLLER]:
      "🚀 Grails Controller - Handles HTTP requests and user interactions",
    [GrailsArtifactType.SERVICE]: "⚙️ Grails Service - Business logic and transactional operations",
    [GrailsArtifactType.DOMAIN]: "🗄️ GORM Domain Class - Data model with database mapping",
    [GrailsArtifactType.VIEW]: "📄 GSP View - Groovy Server Pages for presentation layer",
    [GrailsArtifactType.TAGLIB]: "🏷️ TagLib - Custom GSP tags for reusable view components",
    [GrailsArtifactType.CONFIG]: "⚙️ Configuration - Application settings and properties",
    [GrailsArtifactType.URL_MAPPING]: "🔗 URL Mapping - Route configuration for web requests",
    [GrailsArtifactType.I18N]: "🌐 Internationalization - Language and localization files",

    // Assets & Resources
    [GrailsArtifactType.ASSETS]: "🎨 Assets - CSS, JavaScript, and image files",
    [GrailsArtifactType.RESOURCES]: "📁 Resources - Static resources and configuration files",

    // Application Structure
    [GrailsArtifactType.INIT]: "🚀 Init - Application.groovy and BootStrap.groovy files",

    // Development & Testing
    [GrailsArtifactType.TESTS]: "🧪 Tests - Unit and integration test files",
    [GrailsArtifactType.UNIT_TESTS]: "🧪 Unit Tests - Isolated component tests",
    [GrailsArtifactType.INTEGRATION_TESTS]: "⚡ Integration Tests - Full application stack tests",
    [GrailsArtifactType.GROOVY_SRC]: "📜 Groovy Source - Additional Groovy classes and utilities",

    // Groovy Project Types
    [GrailsArtifactType.SOURCE_SETS]: "📁 Source Sets - Source code organization structure",
    [GrailsArtifactType.DEPENDENCIES]: "📦 Dependencies - External libraries and modules",
    [GrailsArtifactType.TASKS]: "🔧 Gradle Tasks - Build and development tasks",
    [GrailsArtifactType.STRUCTURE]: "📂 Project Structure - Overall project organization",
  };

  static getArtifactIcon(type: GrailsArtifactType): ThemeIcon {
    const iconConfig = this.ARTIFACT_ICONS[type];
    if (!iconConfig) {
      console.warn(`Missing icon configuration for artifact type: ${type}`);
      return new ThemeIcon("symbol-misc");
    }

    return new ThemeIcon(iconConfig.icon, new ThemeColor(iconConfig.color));
  }

  static getArtifactTooltip(type: GrailsArtifactType): string {
    const tooltip = this.ARTIFACT_TOOLTIPS[type];
    if (!tooltip) {
      console.warn(`Missing tooltip for artifact type: ${type}`);
      return `Grails ${type} artifact`;
    }
    return tooltip;
  }

  // Helper method for string-based type
  static getIconFromString(typeString: string): ThemeIcon {
    // Validate if string is a valid GrailsArtifactType
    const type = Object.values(GrailsArtifactType).find(
      t => t === typeString
    ) as GrailsArtifactType;

    if (type) {
      return this.getArtifactIcon(type);
    }

    // Fallback for unknown types
    console.warn(`Unknown artifact type string: ${typeString}`);
    return new ThemeIcon("symbol-misc", new ThemeColor("symbolIcon.miscForeground"));
  }

  static getTooltipFromString(typeString: string): string {
    const type = Object.values(GrailsArtifactType).find(
      t => t === typeString
    ) as GrailsArtifactType;

    if (type) {
      return this.getArtifactTooltip(type);
    }

    return `Grails ${typeString} artifact`;
  }

  // New method: Get file extension icon
  static getFileExtensionIcon(filePath: string): ThemeIcon {
    const ext = filePath.toLowerCase().split(".").pop();

    switch (ext) {
      case "groovy":
        return new ThemeIcon("symbol-method", new ThemeColor("charts.green"));
      case "gsp":
        return new ThemeIcon("browser", new ThemeColor("charts.orange"));
      case "yml":
      case "yaml":
        return new ThemeIcon("settings", new ThemeColor("charts.blue"));
      case "js":
        return new ThemeIcon("symbol-function", new ThemeColor("charts.yellow"));
      case "css":
      case "scss":
        return new ThemeIcon("symbol-color", new ThemeColor("charts.purple"));
      case "properties":
        return new ThemeIcon("symbol-key", new ThemeColor("charts.gray"));
      case "xml":
        return new ThemeIcon("symbol-structure", new ThemeColor("charts.orange"));
      default:
        return new ThemeIcon("symbol-file", new ThemeColor("symbolIcon.fileForeground"));
    }
  }

  // Validation method to ensure all enum values have mappings
  static validateMappings(): boolean {
    const enumValues = Object.values(GrailsArtifactType);
    const iconKeys = Object.keys(this.ARTIFACT_ICONS);
    const tooltipKeys = Object.keys(this.ARTIFACT_TOOLTIPS);

    const missingIcons = enumValues.filter(value => !iconKeys.includes(value));
    const missingTooltips = enumValues.filter(value => !tooltipKeys.includes(value));

    if (missingIcons.length > 0) {
      console.error(`Missing icon mappings for: ${missingIcons.join(", ")}`);
    }

    if (missingTooltips.length > 0) {
      console.error(`Missing tooltip mappings for: ${missingTooltips.join(", ")}`);
    }

    return missingIcons.length === 0 && missingTooltips.length === 0;
  }
}
