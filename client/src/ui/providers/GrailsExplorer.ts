import * as fs from "fs";
import path = require("path");
import * as vscode from "vscode";
import { LanguageClient } from "vscode-languageclient/node";

export class GrailsExplorerProvider implements vscode.TreeDataProvider<GrailsItem> {
  private _onDidChangeTreeData: vscode.EventEmitter<GrailsItem | undefined | void> =
    new vscode.EventEmitter<GrailsItem | undefined | void>();
  readonly onDidChangeTreeData: vscode.Event<GrailsItem | undefined | void> =
    this._onDidChangeTreeData.event;

  private languageClient?: LanguageClient;
  private gradleAvailable: boolean;

  constructor(languageClient?: LanguageClient, gradleAvailable: boolean = false) {
    this.languageClient = languageClient;
    this.gradleAvailable = gradleAvailable;
  }

  refresh(): void {
    this._onDidChangeTreeData.fire();
  }

  getTreeItem(element: GrailsItem): vscode.TreeItem {
    return element;
  }

  async getChildren(element?: GrailsItem): Promise<GrailsItem[]> {
    const workspaceFolders = vscode.workspace.workspaceFolders;
    if (!workspaceFolders || workspaceFolders.length === 0) {
      return Promise.resolve([]);
    }
    const workspaceRoot = workspaceFolders[0].uri.fsPath;
    let projectInfo: any = undefined;
    if (this.languageClient) {
      try {
        // Use LSP workspace/executeCommand to fetch project info
        projectInfo = await this.languageClient.sendRequest("workspace/executeCommand", {
          command: "grails.projectInfo",
          arguments: [workspaceRoot],
        });
      } catch (error) {
        console.error("LSP project info fetch failed, falling back to file", error);
      }
    }
    if (!projectInfo) {
      projectInfo = await readProjectInfo(workspaceRoot);
    }

    if (!projectInfo) {
      return element
        ? Promise.resolve([])
        : Promise.resolve([
            new GrailsItem("Controllers", vscode.TreeItemCollapsibleState.None, "controllers"),
            new GrailsItem("Services", vscode.TreeItemCollapsibleState.None, "services"),
            new GrailsItem("Domains", vscode.TreeItemCollapsibleState.None, "domains"),
          ]);
    }

    if (!element) {
      // Top-level: Show all available Grails directories
      const items: GrailsItem[] = [];

      // Always show main artifact types
      items.push(
        new GrailsItem("Controllers", vscode.TreeItemCollapsibleState.Collapsed, "controllers")
      );
      items.push(new GrailsItem("Services", vscode.TreeItemCollapsibleState.Collapsed, "services"));
      items.push(new GrailsItem("Domains", vscode.TreeItemCollapsibleState.Collapsed, "domains"));

      // Add other directories if they exist
      const grailsAppPath = path.join(workspaceRoot, "grails-app");
      if (fs.existsSync(grailsAppPath)) {
        const additionalDirs = [
          { name: "Views", path: "views", contextValue: "views" },
          { name: "TagLibs", path: "taglib", contextValue: "taglib" },
          { name: "Utils", path: "utils", contextValue: "utils" },
          { name: "Configuration", path: "conf", contextValue: "conf" },
        ];

        for (const dir of additionalDirs) {
          const dirPath = path.join(grailsAppPath, dir.path);
          if (fs.existsSync(dirPath)) {
            items.push(
              new GrailsItem(dir.name, vscode.TreeItemCollapsibleState.Collapsed, dir.contextValue)
            );
          }
        }
      }

      return Promise.resolve(items);
    } else if (["controllers", "services", "domains"].includes(element.contextValue)) {
      // List artifact files
      return await getArtefactItems(
        projectInfo,
        element.contextValue as "controllers" | "services" | "domains"
      );
    } else if (["views", "taglib", "utils", "conf"].includes(element.contextValue)) {
      // List other directory contents
      return await getOtherDirectoryItems(workspaceRoot, element.contextValue);
    }
    return Promise.resolve([]);
  }
}

export class GrailsItem extends vscode.TreeItem {
  constructor(
    public readonly label: string,
    public readonly collapsibleState: vscode.TreeItemCollapsibleState,
    public readonly contextValue: string
  ) {
    super(label, collapsibleState);

    // Set icons based on context
    this.iconPath = this.getIcon();
  }

  private getIcon(): vscode.ThemeIcon {
    switch (this.contextValue) {
      case "controllers":
        return new vscode.ThemeIcon("globe");
      case "services":
        return new vscode.ThemeIcon("gear");
      case "domains":
        return new vscode.ThemeIcon("database");
      case "views":
        return new vscode.ThemeIcon("browser");
      case "taglib":
        return new vscode.ThemeIcon("tag");
      case "utils":
        return new vscode.ThemeIcon("tools");
      case "conf":
        return new vscode.ThemeIcon("settings-gear");
      default:
        // For individual files
        if (this.label.endsWith(".groovy")) {
          return new vscode.ThemeIcon("file-code");
        } else if (this.label.endsWith(".java")) {
          return new vscode.ThemeIcon("file-code");
        } else if (this.label.endsWith(".gsp")) {
          return new vscode.ThemeIcon("file-code");
        }
        return new vscode.ThemeIcon("file");
    }
  }
}

async function readProjectInfo(workspaceRoot: string): Promise<any | undefined> {
  // First try to read projectInfo.json if it exists
  const projectInfoPath = path.join(workspaceRoot, "projectInfo.json");
  try {
    if (fs.existsSync(projectInfoPath)) {
      const data = await fs.promises.readFile(projectInfoPath, "utf-8");
      return JSON.parse(data);
    }
  } catch (error) {
    console.error("Error reading projectInfo.json:", error);
  }

  // Fallback: scan the actual Grails project structure
  return await scanGrailsProject(workspaceRoot);
}

async function scanGrailsProject(workspaceRoot: string): Promise<any> {
  const grailsAppPath = path.join(workspaceRoot, "grails-app");

  if (!fs.existsSync(grailsAppPath)) {
    return null;
  }

  const projectInfo = {
    sourceDirectories: [] as string[],
    controllers: [] as string[],
    services: [] as string[],
    domains: [] as string[],
  };

  // Standard Grails directories
  const standardDirs = [
    { type: "controllers", path: path.join(grailsAppPath, "controllers") },
    { type: "services", path: path.join(grailsAppPath, "services") },
    { type: "domains", path: path.join(grailsAppPath, "domain") }, // Note: 'domain' not 'domains'
    { type: "views", path: path.join(grailsAppPath, "views") },
    { type: "conf", path: path.join(grailsAppPath, "conf") },
    { type: "taglib", path: path.join(grailsAppPath, "taglib") },
    { type: "utils", path: path.join(grailsAppPath, "utils") },
  ];

  for (const dir of standardDirs) {
    if (fs.existsSync(dir.path)) {
      projectInfo.sourceDirectories.push(dir.path);
    }
  }

  return projectInfo;
}

async function getArtefactItems(
  projectInfo: any,
  type: "controllers" | "services" | "domains"
): Promise<GrailsItem[]> {
  if (!projectInfo) {
    return [];
  }

  const workspaceRoot = vscode.workspace.workspaceFolders?.[0]?.uri.fsPath;
  if (!workspaceRoot) {
    return [];
  }

  // Map types to actual directory names in Grails
  const dirMap: Record<string, string> = {
    controllers: "controllers",
    services: "services",
    domains: "domain", // Grails uses 'domain' not 'domains'
  };

  const grailsAppPath = path.join(workspaceRoot, "grails-app");
  const targetDir = path.join(grailsAppPath, dirMap[type]);

  const items: GrailsItem[] = [];

  if (!fs.existsSync(targetDir)) {
    return items;
  }

  try {
    await scanDirectoryRecursively(targetDir, targetDir, items, type);
  } catch (error) {
    console.error(`Error scanning ${targetDir}:`, error);
  }

  return items.sort((a, b) => a.label.localeCompare(b.label));
}

async function scanDirectoryRecursively(
  currentDir: string,
  baseDir: string,
  items: GrailsItem[],
  type: string
): Promise<void> {
  try {
    const entries = await fs.promises.readdir(currentDir, { withFileTypes: true });

    for (const entry of entries) {
      const fullPath = path.join(currentDir, entry.name);

      if (entry.isDirectory()) {
        // Recursively scan subdirectories (for package structures)
        await scanDirectoryRecursively(fullPath, baseDir, items, type);
      } else if (
        entry.isFile() &&
        (entry.name.endsWith(".groovy") || entry.name.endsWith(".java"))
      ) {
        // Calculate relative path for display
        const relativePath = path.relative(baseDir, fullPath);
        const displayName = relativePath.replace(/\\/g, "/"); // Use forward slashes for display

        const item = new GrailsItem(displayName, vscode.TreeItemCollapsibleState.None, type);
        item.resourceUri = vscode.Uri.file(fullPath);
        item.command = {
          command: "vscode.open",
          title: "Open File",
          arguments: [vscode.Uri.file(fullPath)],
        };
        item.tooltip = fullPath;

        items.push(item);
      }
    }
  } catch (error) {
    console.error(`Error reading directory ${currentDir}:`, error);
  }
}

async function getOtherDirectoryItems(
  workspaceRoot: string,
  contextValue: string
): Promise<GrailsItem[]> {
  const grailsAppPath = path.join(workspaceRoot, "grails-app");

  // Map context values to directory names
  const dirMap: Record<string, string> = {
    views: "views",
    taglib: "taglib",
    utils: "utils",
    conf: "conf",
  };

  const targetDir = path.join(grailsAppPath, dirMap[contextValue]);
  const items: GrailsItem[] = [];

  if (!fs.existsSync(targetDir)) {
    return items;
  }

  try {
    await scanOtherDirectoryRecursively(targetDir, targetDir, items, contextValue);
  } catch (error) {
    console.error(`Error scanning ${targetDir}:`, error);
  }

  return items.sort((a, b) => a.label.localeCompare(b.label));
}

async function scanOtherDirectoryRecursively(
  currentDir: string,
  baseDir: string,
  items: GrailsItem[],
  contextValue: string
): Promise<void> {
  try {
    const entries = await fs.promises.readdir(currentDir, { withFileTypes: true });

    for (const entry of entries) {
      const fullPath = path.join(currentDir, entry.name);

      if (entry.isDirectory()) {
        // For views, show directories as expandable items
        if (contextValue === "views") {
          const relativePath = path.relative(baseDir, fullPath);
          const displayName = relativePath.replace(/\\/g, "/");

          const item = new GrailsItem(
            displayName,
            vscode.TreeItemCollapsibleState.Collapsed,
            `${contextValue}-dir`
          );
          item.resourceUri = vscode.Uri.file(fullPath);
          item.tooltip = fullPath;
          items.push(item);
        }

        // Recursively scan subdirectories
        await scanOtherDirectoryRecursively(fullPath, baseDir, items, contextValue);
      } else if (entry.isFile()) {
        // Show files based on context
        const shouldInclude =
          (contextValue === "views" &&
            (entry.name.endsWith(".gsp") || entry.name.endsWith(".html"))) ||
          (contextValue === "conf" &&
            (entry.name.endsWith(".groovy") ||
              entry.name.endsWith(".yml") ||
              entry.name.endsWith(".properties"))) ||
          (contextValue === "taglib" && entry.name.endsWith(".groovy")) ||
          (contextValue === "utils" &&
            (entry.name.endsWith(".groovy") || entry.name.endsWith(".java")));

        if (shouldInclude) {
          const relativePath = path.relative(baseDir, fullPath);
          const displayName = relativePath.replace(/\\/g, "/");

          const item = new GrailsItem(
            displayName,
            vscode.TreeItemCollapsibleState.None,
            contextValue
          );
          item.resourceUri = vscode.Uri.file(fullPath);
          item.command = {
            command: "vscode.open",
            title: "Open File",
            arguments: [vscode.Uri.file(fullPath)],
          };
          item.tooltip = fullPath;

          items.push(item);
        }
      }
    }
  } catch (error) {
    console.error(`Error reading directory ${currentDir}:`, error);
  }
}
