import { ThemeColor, ThemeIcon } from "vscode";
import { GrailsArtifactType } from "../types/grails-types";

export class GrailsIconProvider {
  private static readonly ARTIFACT_ICONS: Record<GrailsArtifactType, { icon: string; color: string }> = {
    [GrailsArtifactType.CONTROLLER]: { icon: "rocket", color: "charts.green" },
    [GrailsArtifactType.SERVICE]: { icon: "gear", color: "charts.blue" },
    [GrailsArtifactType.DOMAIN]: { icon: "database", color: "charts.purple" },
    [GrailsArtifactType.VIEW]: { icon: "browser", color: "charts.orange" },
    [GrailsArtifactType.TAGLIB]: { icon: "tag", color: "charts.red" },
    [GrailsArtifactType.PLUGIN]: { icon: "extensions", color: "charts.yellow" },
    [GrailsArtifactType.CONFIG]: { icon: "settings-gear", color: "charts.gray" },
    [GrailsArtifactType.URL_MAPPING]: { icon: "link", color: "symbolIcon.interfaceForeground" },
  };

  private static readonly ARTIFACT_TOOLTIPS: Record<GrailsArtifactType, string> = {
    [GrailsArtifactType.CONTROLLER]: "🚀 Grails Controller - Handles HTTP requests and user interactions",
    [GrailsArtifactType.SERVICE]: "⚙️ Grails Service - Business logic and transactional operations",
    [GrailsArtifactType.DOMAIN]: "🗄️ GORM Domain Class - Data model with database mapping",
    [GrailsArtifactType.VIEW]: "📄 GSP View - Groovy Server Pages for presentation layer",
    [GrailsArtifactType.TAGLIB]: "🏷️ TagLib - Custom GSP tags for reusable view components",
    [GrailsArtifactType.PLUGIN]: "🔌 Grails Plugin - Modular functionality extension",
    [GrailsArtifactType.CONFIG]: "⚙️ Configuration - Application settings and properties",
    [GrailsArtifactType.URL_MAPPING]: "🔗 URL Mapping - Route configuration for web requests",
  };

  static getArtifactIcon(type: GrailsArtifactType): ThemeIcon {
    const iconConfig = this.ARTIFACT_ICONS[type];
    if (!iconConfig) {
      return new ThemeIcon("symbol-misc");
    }

    return new ThemeIcon(iconConfig.icon, new ThemeColor(iconConfig.color));
  }

  static getArtifactTooltip(type: GrailsArtifactType): string {
    return this.ARTIFACT_TOOLTIPS[type] || "Grails artifact";
  }

  // Helper method for string-based type
  static getIconFromString(typeString: string): ThemeIcon {
    // Validate if string is a valid GrailsArtifactType
    const type = Object.values(GrailsArtifactType).find((t) => t === typeString) as GrailsArtifactType;

    if (type) {
      return this.getArtifactIcon(type);
    }

    // Fallback for unknown types
    return new ThemeIcon("symbol-misc", new ThemeColor("symbolIcon.miscForeground"));
  }

  static getTooltipFromString(typeString: string): string {
    const type = Object.values(GrailsArtifactType).find((t) => t === typeString) as GrailsArtifactType;

    if (type) {
      return this.getArtifactTooltip(type);
    }

    return `Grails ${typeString} artifact`;
  }
}
