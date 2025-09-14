import type { Disposable } from "vscode";
import { commands, extensions, window, workspace } from "vscode";

/**
 * Supported icon theme types
 */
export enum IconThemeType {
  MATERIAL = "material",
  VSCODE_ICONS = "vscode-icons",
  BUILT_IN = "built-in",
  UNKNOWN = "unknown",
}

/**
 * Icon theme configuration
 */
interface ThemeConfig {
  extensionId: string;
  themeId: string;
  displayName: string;
  supportsColors: boolean;
}

export class IconThemeDetector {
  static shouldUseMaterialTheme() {
    throw new Error("Method not implemented.");
  }
  private static readonly SUPPORTED_THEMES: Record<string, ThemeConfig> = {
    // Material Icon Theme
    "material-icon-theme": {
      extensionId: "PKief.material-icon-theme",
      themeId: "material-icon-theme",
      displayName: "Material Icon Theme",
      supportsColors: true,
    },

    // VSCode Icons
    "vscode-icons": {
      extensionId: "vscode-icons-team.vscode-icons",
      themeId: "vscode-icons",
      displayName: "VSCode Icons",
      supportsColors: true,
    },

    // Alternative Material themes
    "eq-material-theme-icons": {
      extensionId: "Equinusocio.vsc-material-theme-icons",
      themeId: "eq-material-theme-icons",
      displayName: "Material Theme Icons",
      supportsColors: true,
    },

    // Atom Material Icons
    "a-file-icon-vscode": {
      extensionId: "file-icons.file-icons",
      themeId: "a-file-icon-vscode",
      displayName: "A File Icon",
      supportsColors: false,
    },
  };

  private static themeCache: {
    currentTheme: IconThemeType | null;
    activeThemeId: string | null;
    installedThemes: Set<string>;
  } = {
    currentTheme: null,
    activeThemeId: null,
    installedThemes: new Set<string>(),
  };

  /**
   * Get the currently active icon theme type
   */
  static getCurrentThemeType(): IconThemeType {
    if (this.themeCache.currentTheme === null) {
      this.refreshThemeCache();
    }
    return this.themeCache.currentTheme!;
  }

  /**
   * Check if current theme supports colored icons
   */
  static supportsColoredIcons(): boolean {
    const activeThemeId = this.getActiveThemeId();
    if (!activeThemeId) {
      return false;
    }

    const themeConfig = this.SUPPORTED_THEMES[activeThemeId];
    return themeConfig?.supportsColors ?? false;
  }

  /**
   * Check if Material Icon Theme is installed
   */
  static isMaterialThemeInstalled(): boolean {
    return this.isThemeInstalled("PKief.material-icon-theme");
  }

  /**
   * Check if Material Icon Theme is active
   */
  static isMaterialThemeActive(): boolean {
    const activeTheme = this.getActiveThemeId();
    return activeTheme === "material-icon-theme" || activeTheme === "eq-material-theme-icons";
  }

  /**
   * Check if VSCode Icons is installed
   */
  static isVSCodeIconsInstalled(): boolean {
    return this.isThemeInstalled("vscode-icons-team.vscode-icons");
  }

  /**
   * Check if VSCode Icons is active
   */
  static isVSCodeIconsActive(): boolean {
    return this.getActiveThemeId() === "vscode-icons";
  }

  /**
   * Check if we should use enhanced icons (colored + advanced)
   */
  static shouldUseEnhancedIcons(): boolean {
    const activeThemeId = this.getActiveThemeId();
    const themeConfig = this.SUPPORTED_THEMES[activeThemeId ?? ""];
    return themeConfig?.supportsColors ?? false;
  }

  /**
   * Get active theme display name
   */
  static getActiveThemeDisplayName(): string {
    const activeThemeId = this.getActiveThemeId();
    if (!activeThemeId) {
      return "Built-in Icons";
    }

    const themeConfig = this.SUPPORTED_THEMES[activeThemeId];
    return themeConfig?.displayName ?? "Unknown Theme";
  }

  /**
   * Get all installed supported themes
   */
  static getInstalledSupportedThemes(): ThemeConfig[] {
    return Object.values(this.SUPPORTED_THEMES).filter(theme =>
      this.isThemeInstalled(theme.extensionId)
    );
  }

  /**
   * Suggest installing a popular icon theme
   */
  static async suggestIconTheme(): Promise<void> {
    const installedThemes = this.getInstalledSupportedThemes();

    if (installedThemes.length > 0) {
      // User has supported themes but might not be using them
      const themeNames = installedThemes.map(t => t.displayName).join(", ");
      const action = await window.showInformationMessage(
        `🎨 You have ${themeNames} installed. Consider activating it for better Grails icons!`,
        "Open Settings"
      );
      if (action === "Open Settings") {
        await commands.executeCommand("workbench.action.openSettings", "workbench.iconTheme");
      }
      return;
    }

    // Suggest installing a theme
    const suggestion = await window.showInformationMessage(
      "🎨 Enhanced icon themes provide better visual experience for Grails projects. Which would you like to install?",
      "Material Icon Theme",
      "VSCode Icons",
      "Maybe Later"
    );

    switch (suggestion) {
      case "Material Icon Theme":
        await commands.executeCommand(
          "workbench.extensions.installExtension",
          "PKief.material-icon-theme"
        );
        break;
      case "VSCode Icons":
        await commands.executeCommand(
          "workbench.extensions.installExtension",
          "vscode-icons-team.vscode-icons"
        );
        break;
    }
  }

  /**
   * Listen for theme changes and execute callback
   */
  static onThemeChanged(callback: () => void): Disposable {
    return workspace.onDidChangeConfiguration(e => {
      if (e.affectsConfiguration("workbench.iconTheme")) {
        this.resetCache();
        callback();
      }
    });
  }

  /**
   * Reset theme detection cache
   */
  static resetCache(): void {
    this.themeCache.currentTheme = null;
    this.themeCache.activeThemeId = null;
    this.themeCache.installedThemes.clear();
  }

  /**
   * Get the active theme ID from workspace configuration
   */
  private static getActiveThemeId(): string | null {
    this.themeCache.activeThemeId ??=
      workspace.getConfiguration("workbench").get<string>("iconTheme") ?? null;
    return this.themeCache.activeThemeId;
  }

  /**
   * Check if a theme extension is installed
   */
  private static isThemeInstalled(extensionId: string): boolean {
    if (!this.themeCache.installedThemes.has(extensionId)) {
      const extension = extensions.getExtension(extensionId);
      if (extension) {
        this.themeCache.installedThemes.add(extensionId);
      }
    }
    return this.themeCache.installedThemes.has(extensionId);
  }

  /**
   * Refresh theme cache by detecting current state
   */
  private static refreshThemeCache(): void {
    const activeThemeId = this.getActiveThemeId();

    if (!activeThemeId) {
      this.themeCache.currentTheme = IconThemeType.BUILT_IN;
      return;
    }

    // Check for Material themes
    if (activeThemeId === "material-icon-theme" || activeThemeId === "eq-material-theme-icons") {
      this.themeCache.currentTheme = IconThemeType.MATERIAL;
      return;
    }

    // Check for VSCode Icons
    if (activeThemeId === "vscode-icons") {
      this.themeCache.currentTheme = IconThemeType.VSCODE_ICONS;
      return;
    }

    // Check if it's any other supported theme
    if (this.SUPPORTED_THEMES[activeThemeId]) {
      this.themeCache.currentTheme = this.SUPPORTED_THEMES[activeThemeId].supportsColors
        ? IconThemeType.MATERIAL
        : IconThemeType.BUILT_IN;
      return;
    }

    this.themeCache.currentTheme = IconThemeType.UNKNOWN;
  }

  /**
   * Initialize theme detector (call this in extension activation)
   */
  static initialize(): void {
    this.refreshThemeCache();

    // Log current theme for debugging
    const themeType = this.getCurrentThemeType();
    const displayName = this.getActiveThemeDisplayName();
    console.log(`🎨 Grails Extension: Detected icon theme - ${displayName} (${themeType})`);
  }
}
