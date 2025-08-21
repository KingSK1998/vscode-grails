import {
  EventEmitter,
  Event,
  TreeDataProvider,
  TreeItem,
  TreeItemCollapsibleState,
  Uri,
  ExtensionContext,
  workspace,
} from "vscode";
import { GrailsArtifactType, Icons, ProjectType } from "../utils/Constants";
import path from "path";
import fs from "fs";
import { GrailsIconProvider } from "../providers/grails-icon-provider";
import { ArtifactCounts, ConfigFile } from "../types/grails-types";

export class GrailsTreeItem extends TreeItem {
  constructor(
    public readonly label: string,
    public readonly collapsibleState: TreeItemCollapsibleState,
    public readonly artifactType?: GrailsArtifactType,
    public readonly filePath?: string
  ) {
    super(label, collapsibleState);

    this.setupIcon();
    this.setupFileProperties();
  }

  private setupIcon(): void {
    if (this.artifactType) {
      this.iconPath = GrailsIconProvider.getArtifactIcon(this.artifactType);
      this.tooltip = GrailsIconProvider.getArtifactTooltip(this.artifactType);
      this.contextValue = `grails-${this.artifactType}`;
    } else if (this.filePath) {
      this.iconPath = GrailsIconProvider.getFileExtensionIcon(this.filePath);
      this.contextValue = "grails-file";
    }
  }

  private setupFileProperties(): void {
    if (!this.filePath) return;

    this.resourceUri = Uri.file(this.filePath);
    this.command = {
      command: "vscode.open",
      title: "Open File",
      arguments: [this.resourceUri],
    };
    this.tooltip = `${this.label}\n${workspace.asRelativePath(this.filePath)}`;
  }
}

export class GrailsTreeDataProvider implements TreeDataProvider<GrailsTreeItem> {
  private readonly _onDidChangeTreeData = new EventEmitter<
    GrailsTreeItem | undefined | null | void
  >();
  readonly onDidChangeTreeData: Event<GrailsTreeItem | undefined | null | void> =
    this._onDidChangeTreeData.event;

  private projectType: ProjectType | null = null;
  private artifactCountsCache: ArtifactCounts | null = null;

  constructor(private readonly context: ExtensionContext) {
    console.log("🌳 GrailsTreeDataProvider initialized");
  }

  refresh(): void {
    console.log("🔄 Refreshing tree data...");
    this.clearCache();
    this._onDidChangeTreeData.fire();
  }

  private clearCache(): void {
    this.projectType = null;
    this.artifactCountsCache = null;
  }

  getTreeItem(element: GrailsTreeItem): TreeItem {
    return element;
  }

  async getChildren(element?: GrailsTreeItem): Promise<GrailsTreeItem[]> {
    console.log("📂 Getting children for:", element?.label || "root");

    if (!element) {
      return this.getRootItems();
    }

    return this.getElementChildren(element);
  }

  private async getRootItems(): Promise<GrailsTreeItem[]> {
    const projectType = await this.getProjectType();

    if (projectType === "none") {
      return [
        new GrailsTreeItem("No Grails/Groovy project detected", TreeItemCollapsibleState.None),
      ];
    }

    return this.getProjectStructure(projectType);
  }

  private async getElementChildren(element: GrailsTreeItem): Promise<GrailsTreeItem[]> {
    // Handle special context values
    if (element.contextValue) {
      const handler = this.getContextHandler(element.contextValue);
      if (handler) {
        return handler(element);
      }
    }

    // Handle artifact types
    if (element.artifactType) {
      return this.getArtifactFiles(element.artifactType);
    }

    return [];
  }

  private getContextHandler(
    contextValue: string
  ): ((element: GrailsTreeItem) => Promise<GrailsTreeItem[]>) | null {
    const handlers: Record<string, (element: GrailsTreeItem) => Promise<GrailsTreeItem[]>> = {
      "grails-view-folder": (element: GrailsTreeItem) =>
        this.getViewFilesForController(element.id!),
      "grails-asset-js": () => this.getAssetFiles("grails-app/assets/javascripts/**/*.js", "📜"),
      "grails-asset-css": () =>
        this.getAssetFiles("grails-app/assets/stylesheets/**/*.{css,scss}", "🎨"),
      "grails-asset-images": () =>
        this.getAssetFiles("grails-app/assets/images/**/*.{png,jpg,jpeg,gif,svg}", "🖼️"),
    };

    return handlers[contextValue] ?? null;
  }

  private async getProjectType(): Promise<ProjectType> {
    if (this.projectType !== null) {
      return this.projectType;
    }

    this.projectType = await this.detectProjectType();
    return this.projectType;
  }

  private async detectProjectType(): Promise<ProjectType> {
    const workspaceFolders = workspace.workspaceFolders;
    if (!workspaceFolders) return "none";

    const workspaceRoot = workspaceFolders[0].uri.fsPath;

    // Check for Grails project
    if (await this.fileExists(path.join(workspaceRoot, "grails-app"))) {
      console.log("🎯 Detected: Grails project");
      return "grails";
    }

    // Check for Groovy project
    if (await this.fileExists(path.join(workspaceRoot, "src", "main", "groovy"))) {
      console.log("🎯 Detected: Groovy project");
      return "groovy";
    }

    // Check for Gradle project
    if (await this.fileExists(path.join(workspaceRoot, "build.gradle"))) {
      console.log("🎯 Detected: Gradle project");
      return "gradle";
    }

    return "none";
  }

  private async fileExists(filePath: string): Promise<boolean> {
    try {
      await fs.promises.access(filePath);
      return true;
    } catch {
      return false;
    }
  }

  private async getProjectStructure(projectType: ProjectType): Promise<GrailsTreeItem[]> {
    switch (projectType) {
      case "grails":
        return this.getGrailsProjectStructure();
      case "groovy":
        return this.getGroovyProjectStructure();
      case "gradle":
        return this.getGradleProjectStructure();
      default:
        return [];
    }
  }

  private async getGrailsProjectStructure(): Promise<GrailsTreeItem[]> {
    const counts = await this.getArtifactCounts();

    return [
      // Core artifacts (expanded by default)
      this.createArtifactItem(
        "🚀 Controllers",
        counts.controllers,
        GrailsArtifactType.CONTROLLER,
        TreeItemCollapsibleState.Expanded
      ),
      this.createArtifactItem(
        "⚙️ Services",
        counts.services,
        GrailsArtifactType.SERVICE,
        TreeItemCollapsibleState.Expanded
      ),
      this.createArtifactItem(
        "🗄️ Domains",
        counts.domains,
        GrailsArtifactType.DOMAIN,
        TreeItemCollapsibleState.Expanded
      ),
      this.createArtifactItem(
        "📄 Views",
        counts.views,
        GrailsArtifactType.VIEW,
        TreeItemCollapsibleState.Collapsed
      ),

      // Presentation & UI (collapsed)
      this.createArtifactItem(
        "🏷️ TagLibs",
        counts.taglibs,
        GrailsArtifactType.TAGLIB,
        TreeItemCollapsibleState.Collapsed
      ),
      this.createArtifactItem(
        "🎨 Assets",
        counts.assets,
        GrailsArtifactType.ASSETS,
        TreeItemCollapsibleState.Collapsed
      ),
      this.createArtifactItem(
        "🌐 i18n",
        counts.i18n,
        GrailsArtifactType.I18N,
        TreeItemCollapsibleState.Collapsed
      ),

      // Configuration & routing
      new GrailsTreeItem(
        "🔗 URL Mappings",
        TreeItemCollapsibleState.Collapsed,
        GrailsArtifactType.URL_MAPPING
      ),
      new GrailsTreeItem(
        "⚙️ Configuration",
        TreeItemCollapsibleState.Collapsed,
        GrailsArtifactType.CONFIG
      ),
      new GrailsTreeItem(
        "🚀 Init & Bootstrap",
        TreeItemCollapsibleState.Collapsed,
        GrailsArtifactType.INIT
      ),

      // Development & testing
      this.createArtifactItem(
        "🧪 Tests",
        counts.tests,
        GrailsArtifactType.TESTS,
        TreeItemCollapsibleState.Collapsed
      ),
      this.createArtifactItem(
        "📝 Source",
        counts.groovySrc,
        GrailsArtifactType.GROOVY_SRC,
        TreeItemCollapsibleState.Collapsed
      ),
    ];
  }

  private createArtifactItem(
    label: string,
    count: number,
    artifactType: GrailsArtifactType,
    collapsibleState: TreeItemCollapsibleState
  ): GrailsTreeItem {
    const displayLabel = count > 0 ? `${label} (${count})` : label;
    return new GrailsTreeItem(displayLabel, collapsibleState, artifactType);
  }

  private async getArtifactCounts(): Promise<ArtifactCounts> {
    if (this.artifactCountsCache) {
      return this.artifactCountsCache;
    }

    const patterns = {
      controllers: "grails-app/controllers/**/*.groovy",
      services: "grails-app/services/**/*.groovy",
      domains: "grails-app/domain/**/*.groovy",
      views: "grails-app/views/**/*.gsp",
      taglibs: "grails-app/taglib/**/*.groovy",
      assets: "grails-app/assets/**/*",
      i18n: "grails-app/i18n/**/*.properties",
      groovySrc: "src/main/groovy/**/*.groovy",
    };

    const counts: ArtifactCounts = {
      controllers: 0,
      services: 0,
      domains: 0,
      views: 0,
      taglibs: 0,
      assets: 0,
      i18n: 0,
      tests: 0,
      groovySrc: 0,
    };

    try {
      // Count regular artifacts
      for (const [key, pattern] of Object.entries(patterns)) {
        const files = await workspace.findFiles(pattern);
        counts[key as keyof ArtifactCounts] = files.length;
      }

      // Count tests separately (unit + integration)
      const [unitTests, integrationTests] = await Promise.all([
        workspace.findFiles("src/test/**/*Spec.groovy"),
        workspace.findFiles("src/integration-test/**/*Spec.groovy"),
      ]);
      counts.tests = unitTests.length + integrationTests.length;
    } catch (error) {
      console.error("Error counting artifacts:", error);
    }

    this.artifactCountsCache = counts;
    return counts;
  }

  private async getArtifactFiles(type: GrailsArtifactType): Promise<GrailsTreeItem[]> {
    const handlers: Partial<Record<GrailsArtifactType, () => Promise<GrailsTreeItem[]>>> = {
      [GrailsArtifactType.CONTROLLER]: () =>
        this.getFilesFromPattern("grails-app/controllers/**/*.groovy", type),
      [GrailsArtifactType.SERVICE]: () =>
        this.getFilesFromPattern("grails-app/services/**/*.groovy", type),
      [GrailsArtifactType.DOMAIN]: () =>
        this.getFilesFromPattern("grails-app/domain/**/*.groovy", type),
      [GrailsArtifactType.VIEW]: () => this.getViewsStructure(),
      [GrailsArtifactType.TAGLIB]: () =>
        this.getFilesFromPattern("grails-app/taglib/**/*.groovy", type),
      [GrailsArtifactType.ASSETS]: () => this.getAssetsStructure(),
      [GrailsArtifactType.CONFIG]: () => this.getConfigurationFiles(),
      [GrailsArtifactType.URL_MAPPING]: () => this.getUrlMappingFiles(),
      [GrailsArtifactType.I18N]: () =>
        this.getFilesFromPattern("grails-app/i18n/**/*.properties", type),
      [GrailsArtifactType.INIT]: () => this.getInitFiles(),
      [GrailsArtifactType.TESTS]: () => this.getTestStructure(),
      [GrailsArtifactType.UNIT_TESTS]: () =>
        this.getFilesFromPattern("src/test/**/*Spec.groovy", type),
      [GrailsArtifactType.INTEGRATION_TESTS]: () =>
        this.getFilesFromPattern("src/integration-test/**/*Spec.groovy", type),
      [GrailsArtifactType.GROOVY_SRC]: () =>
        this.getFilesFromPattern("src/main/groovy/**/*.groovy", type),
    };

    const handler = handlers[type];
    if (!handler) {
      console.warn(`No handler for artifact type: ${type}`);
      return [];
    }

    return handler();
  }

  private async getFilesFromPattern(
    pattern: string,
    type: GrailsArtifactType
  ): Promise<GrailsTreeItem[]> {
    try {
      const files = await workspace.findFiles(pattern);
      return files.map(file => {
        const fileName = path.basename(file.fsPath);
        return new GrailsTreeItem(fileName, TreeItemCollapsibleState.None, type, file.fsPath);
      });
    } catch (error) {
      console.error(`Error loading files with pattern ${pattern}:`, error);
      return [];
    }
  }

  private async getViewsStructure(): Promise<GrailsTreeItem[]> {
    try {
      const viewFiles = await workspace.findFiles("grails-app/views/**/*.gsp");
      const viewsByController = this.groupViewsByController(viewFiles);

      return this.createViewTreeItems(viewsByController);
    } catch (error) {
      console.error("Error loading views:", error);
      return [];
    }
  }

  private groupViewsByController(viewFiles: readonly Uri[]): Map<string, string[]> {
    const viewsByController = new Map<string, string[]>();

    viewFiles.forEach(file => {
      const relativePath = workspace.asRelativePath(file);
      const pathParts = this.splitPath(relativePath);

      const controllerName = pathParts.length >= 4 ? pathParts[2] : "";

      if (!viewsByController.has(controllerName)) {
        viewsByController.set(controllerName, []);
      }
      viewsByController.get(controllerName)!.push(file.fsPath);
    });

    return viewsByController;
  }

  private createViewTreeItems(viewsByController: Map<string, string[]>): GrailsTreeItem[] {
    const result: GrailsTreeItem[] = [];

    // Handle root-level views
    if (viewsByController.has("")) {
      const rootViews = viewsByController.get("")!;
      rootViews.forEach(filePath => {
        const fileName = path.basename(filePath);
        result.push(
          new GrailsTreeItem(
            `📄 ${fileName}`,
            TreeItemCollapsibleState.None,
            GrailsArtifactType.VIEW,
            filePath
          )
        );
      });
      viewsByController.delete("");
    }

    // Create controller view folders
    for (const [controllerName, files] of viewsByController) {
      if (controllerName) {
        const controllerItem = new GrailsTreeItem(
          `📁 ${controllerName} (${files.length})`,
          TreeItemCollapsibleState.Collapsed,
          GrailsArtifactType.VIEW
        );
        controllerItem.contextValue = "grails-view-folder";
        controllerItem.id = `view-folder-${controllerName}`;
        result.push(controllerItem);
      }
    }

    return result;
  }

  private async getAssetsStructure(): Promise<GrailsTreeItem[]> {
    const assetTypes = [
      {
        pattern: "grails-app/assets/javascripts/**/*.js",
        label: "📜 JavaScript",
        contextValue: "grails-asset-js",
      },
      {
        pattern: "grails-app/assets/stylesheets/**/*.{css,scss}",
        label: "🎨 Stylesheets",
        contextValue: "grails-asset-css",
      },
      {
        pattern: "grails-app/assets/images/**/*.{png,jpg,jpeg,gif,svg}",
        label: "🖼️ Images",
        contextValue: "grails-asset-images",
      },
    ];

    const result: GrailsTreeItem[] = [];

    for (const assetType of assetTypes) {
      try {
        const files = await workspace.findFiles(assetType.pattern);
        if (files.length > 0) {
          const item = new GrailsTreeItem(
            `${assetType.label} (${files.length})`,
            TreeItemCollapsibleState.Collapsed,
            GrailsArtifactType.ASSETS
          );
          item.contextValue = assetType.contextValue;
          item.id = assetType.contextValue.replace("grails-", "");
          result.push(item);
        }
      } catch (error) {
        console.error(`Error loading ${assetType.label}:`, error);
      }
    }

    return result;
  }

  private async getConfigurationFiles(): Promise<GrailsTreeItem[]> {
    const workspaceRoot = workspace.workspaceFolders![0].uri.fsPath;

    const configFiles: ConfigFile[] = [
      { path: "grails-app/conf/application.yml", name: "⚙️ application.yml", icon: Icons.YML_FILE },
      {
        path: "grails-app/conf/application.groovy",
        name: "⚙️ application.groovy",
        icon: Icons.GROOVY_FILE,
      },
      {
        path: "grails-app/conf/spring/resources.groovy",
        name: "🌱 resources.groovy",
        icon: Icons.GROOVY_FILE,
      },
      { path: "grails-app/conf/logback.xml", name: "📋 logback.xml", icon: Icons.CONFIG },
    ];

    const result: GrailsTreeItem[] = [];

    for (const config of configFiles) {
      const fullPath = path.join(workspaceRoot, config.path);
      if (await this.fileExists(fullPath)) {
        result.push(
          new GrailsTreeItem(
            config.name,
            TreeItemCollapsibleState.None,
            GrailsArtifactType.CONFIG,
            fullPath
          )
        );
      }
    }

    return result;
  }

  private async getUrlMappingFiles(): Promise<GrailsTreeItem[]> {
    try {
      const mappingFiles = await workspace.findFiles(
        "grails-app/controllers/**/UrlMappings.groovy"
      );
      return mappingFiles.map(
        file =>
          new GrailsTreeItem(
            `🔗 ${path.basename(file.fsPath)}`,
            TreeItemCollapsibleState.None,
            GrailsArtifactType.URL_MAPPING,
            file.fsPath
          )
      );
    } catch (error) {
      console.error("Error loading URL mappings:", error);
      return [];
    }
  }

  private async getInitFiles(): Promise<GrailsTreeItem[]> {
    const initFilePatterns = [
      { pattern: "grails-app/init/**/Application.groovy", name: "🚀 Application.groovy" },
      { pattern: "grails-app/init/**/BootStrap.groovy", name: "⚡ BootStrap.groovy" },
    ];

    const result: GrailsTreeItem[] = [];

    for (const init of initFilePatterns) {
      try {
        const files = await workspace.findFiles(init.pattern);
        files.forEach(file => {
          result.push(
            new GrailsTreeItem(
              init.name,
              TreeItemCollapsibleState.None,
              GrailsArtifactType.INIT,
              file.fsPath
            )
          );
        });
      } catch (error) {
        console.error(`Error finding ${init.name}:`, error);
      }
    }

    return result;
  }

  private async getTestStructure(): Promise<GrailsTreeItem[]> {
    const result: GrailsTreeItem[] = [];

    try {
      const [unitTests, integrationTests] = await Promise.all([
        workspace.findFiles("src/test/**/*Spec.groovy"),
        workspace.findFiles("src/integration-test/**/*Spec.groovy"),
      ]);

      if (unitTests.length > 0) {
        result.push(
          new GrailsTreeItem(
            `🧪 Unit Tests (${unitTests.length})`,
            TreeItemCollapsibleState.Collapsed,
            GrailsArtifactType.UNIT_TESTS
          )
        );
      }

      if (integrationTests.length > 0) {
        result.push(
          new GrailsTreeItem(
            `⚡ Integration Tests (${integrationTests.length})`,
            TreeItemCollapsibleState.Collapsed,
            GrailsArtifactType.INTEGRATION_TESTS
          )
        );
      }
    } catch (error) {
      console.error("Error loading tests:", error);
    }

    return result;
  }

  private async getGroovyProjectStructure(): Promise<GrailsTreeItem[]> {
    const result: GrailsTreeItem[] = [];

    const [sourceFiles, testFiles] = await Promise.all([
      workspace.findFiles("src/main/groovy/**/*.{groovy,java}"),
      workspace.findFiles("src/test/groovy/**/*.{groovy,java}"),
    ]);

    if (sourceFiles.length > 0) {
      result.push(
        new GrailsTreeItem(
          `📝 Source Files (${sourceFiles.length})`,
          TreeItemCollapsibleState.Collapsed,
          GrailsArtifactType.GROOVY_SRC
        )
      );
    }

    if (testFiles.length > 0) {
      result.push(
        new GrailsTreeItem(
          `🧪 Test Files (${testFiles.length})`,
          TreeItemCollapsibleState.Collapsed,
          GrailsArtifactType.TESTS
        )
      );
    }

    return result;
  }

  private async getGradleProjectStructure(): Promise<GrailsTreeItem[]> {
    // Implement Gradle-specific structure if needed
    return [];
  }

  private async getViewFilesForController(folderId: string): Promise<GrailsTreeItem[]> {
    const controllerName = folderId.replace("view-folder-", "");

    try {
      const pattern =
        controllerName === "root"
          ? "grails-app/views/*.gsp"
          : `grails-app/views/${controllerName}/**/*.gsp`;

      const viewFiles = await workspace.findFiles(pattern);

      return viewFiles.map(file => {
        const fileName = path.basename(file.fsPath);
        return new GrailsTreeItem(
          `📄 ${fileName}`,
          TreeItemCollapsibleState.None,
          GrailsArtifactType.VIEW,
          file.fsPath
        );
      });
    } catch (error) {
      console.error(`Error loading views for ${controllerName}:`, error);
      return [];
    }
  }

  private async getAssetFiles(pattern: string, emoji: string): Promise<GrailsTreeItem[]> {
    try {
      const files = await workspace.findFiles(pattern);

      return files.map(file => {
        const fileName = path.basename(file.fsPath);
        return new GrailsTreeItem(
          `${emoji} ${fileName}`,
          TreeItemCollapsibleState.None,
          GrailsArtifactType.ASSETS,
          file.fsPath
        );
      });
    } catch (error) {
      console.error(`Error loading asset files with pattern ${pattern}:`, error);
      return [];
    }
  }

  private splitPath(filePath: string): string[] {
    return filePath.replace(/\\/g, "/").split("/");
  }
}
