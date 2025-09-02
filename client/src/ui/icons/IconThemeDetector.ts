import { commands, extensions, window, workspace } from "vscode";

export class IconThemeDetector {
  private static readonly MATERIAL_THEME_ID = "PKief.material-icon-theme";
  private static materialThemeInstalled: boolean | null = null;

  /** Check if Material theme is installed */
  static isMaterialThemeInstalled(): boolean {
    if (this.materialThemeInstalled === null) {
      const extension = extensions.getExtension(this.MATERIAL_THEME_ID);
      this.materialThemeInstalled = extension !== undefined;

      if (this.materialThemeInstalled) {
        console.log("✅ Material Icon Theme detected - using enhanced icons");
      } else {
        console.log("ℹ️ Material Icon Theme not found - using built-in icons");
      }
    }
    return this.materialThemeInstalled;
  }

  /** Check if Material theme is currently active */
  static isMaterialThemeActive(): boolean {
    const activeTheme = workspace.getConfiguration("workbench").get<string>("iconTheme");
    return activeTheme === "material-icon-theme";
  }

  /**
   * Suggest Material Theme installation
   */
  static async suggestMaterialTheme(): Promise<void> {
    const install = await window.showInformationMessage(
      "📦 Material Icon Theme provides enhanced icons for Grails Explorer. Would you like to install it?",
      "Install Material Theme",
      "Maybe Later"
    );

    if (install === "Install Material Theme") {
      await commands.executeCommand(
        "workbench.extensions.installExtension",
        this.MATERIAL_THEME_ID
      );
    }
  }

  /**
   * Reset detection cache (useful for testing or dynamic changes)
   */
  static resetCache(): void {
    this.materialThemeInstalled = null;
  }
}
