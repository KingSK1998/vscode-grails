import { workspace } from "vscode";
import * as fs from "fs";
import * as path from "path";

const GRAILS_CONFIG_SECTION = "grails";
const GRAILS_PATH_KEY = "path";
const JAVA_HOME_KEY = "javaHome";
const GRAILS_VERSION_KEY = "version";

/**
 * Standardized configuration and environment access for Grails projects.
 */
export class GrailsConfig {
  /** Returns true if Grails is configured via settings or environment. */
  static isGrailsConfigured(): boolean {
    return !!this.getGrailsPath();
  }

  /** Returns the configured Grails path, or GRAILS_HOME, or empty string. */
  static getGrailsPath(): string {
    const configuredPath = workspace
      .getConfiguration(GRAILS_CONFIG_SECTION)
      .get<string>(GRAILS_PATH_KEY);
    return configuredPath || process.env.GRAILS_HOME || "";
  }

  /** Returns the configured Java Home for Grails, or JAVA_HOME, or undefined. */
  static getJavaHome(): string | undefined {
    return (
      workspace.getConfiguration(GRAILS_CONFIG_SECTION).get<string>(JAVA_HOME_KEY) ||
      process.env.JAVA_HOME
    );
  }

  /** Returns the Grails version, if specified in settings or detected from project. */
  static getGrailsVersion(projectRoot?: string): string | undefined {
    // Prefer explicit config
    const configuredVersion = workspace
      .getConfiguration(GRAILS_CONFIG_SECTION)
      .get<string>(GRAILS_VERSION_KEY);
    if (configuredVersion) {
      return configuredVersion;
    }

    // Try to detect from application.properties or build.gradle
    if (projectRoot) {
      // Check for application.properties
      const propPath = path.join(projectRoot, "grails-app", "conf", "application.properties");
      if (fs.existsSync(propPath)) {
        const content = fs.readFileSync(propPath, "utf8");
        const match = content.match(/^app\.grails\.version=(.+)$/m);
        if (match) {
          return match[1].trim();
        }
      }
      // Check gradle.properties
      const gradlePropPath = path.join(projectRoot, "gradle.properties");
      if (fs.existsSync(gradlePropPath)) {
        const content = fs.readFileSync(gradlePropPath, "utf8");
        const match = content.match(/^grailsVersion=(.+)$/m);
        if (match) {
          return match[1].trim();
        }
      }
    }
    return undefined;
  }

  /** Returns the workspace folder for the current Grails project, if any. */
  static getProjectRoot(): string | undefined {
    const folders = workspace.workspaceFolders;
    if (!folders || folders.length === 0) return undefined;
    // Optionally, add more robust detection here
    return folders[0].uri.fsPath;
  }

  /** Returns true if the given folder is a Grails project (has grails-app dir). */
  static isGrailsProjectFolder(folderPath: string): boolean {
    return fs.existsSync(path.join(folderPath, "grails-app"));
  }

  /** Validates the Grails configuration and returns an error message if invalid, or undefined if valid. */
  static validateConfig(): string | undefined {
    const grailsPath = this.getGrailsPath();
    if (!grailsPath) {
      return "Grails path is not configured. Please set GRAILS_HOME environment variable or configure 'grails.path' setting.";
    }

    if (!fs.existsSync(grailsPath)) {
      return `Grails path does not exist: ${grailsPath}`;
    }

    // Check if grails executable exists
    const grailsExecutable = process.platform === "win32" ? "grails.bat" : "grails";
    const grailsBin = path.join(grailsPath, "bin", grailsExecutable);
    if (!fs.existsSync(grailsBin)) {
      return `Grails executable not found: ${grailsBin}`;
    }

    const javaHome = this.getJavaHome();
    if (javaHome && !fs.existsSync(javaHome)) {
      return `Configured Java Home does not exist: ${javaHome}`;
    }

    return undefined;
  }

  /** Get all Grails-related configuration as an object */
  static getAllConfig() {
    return {
      grailsPath: this.getGrailsPath(),
      javaHome: this.getJavaHome(),
      projectRoot: this.getProjectRoot(),
      grailsVersion: this.getGrailsVersion(this.getProjectRoot()),
      isConfigured: this.isGrailsConfigured(),
      isGrailsProject: this.getProjectRoot()
        ? this.isGrailsProjectFolder(this.getProjectRoot()!)
        : false,
    };
  }

  /** Check if current workspace has multiple Grails projects */
  static getGrailsProjects(): string[] {
    const folders = workspace.workspaceFolders;
    if (!folders) return [];

    return folders
      .map(folder => folder.uri.fsPath)
      .filter(folderPath => this.isGrailsProjectFolder(folderPath));
  }
}
