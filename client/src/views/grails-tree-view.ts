import {
  EventEmitter,
  Event,
  TreeDataProvider,
  TreeItem,
  TreeItemCollapsibleState,
  Uri,
  ExtensionContext,
  workspace,
  ThemeIcon,
  ThemeColor,
} from "vscode";
import { Colors, GrailsArtifactType, Icons } from "../utils/constants";
import path from "path";
import fs from "fs";
import { GrailsIconProvider } from "../providers/grails-icon-provider";

export class GrailsTreeItem extends TreeItem {
  constructor(
    public readonly label: string,
    public readonly collapsibleState: TreeItemCollapsibleState,
    public readonly artifactType?: GrailsArtifactType,
    public readonly filePath?: string,
  ) {
    super(label, collapsibleState);

    // Set icon based on artifact type or file extension
    if (artifactType) {
      // Use the provider instead of inline logic
      this.iconPath = GrailsIconProvider.getArtifactIcon(artifactType);
      this.tooltip = GrailsIconProvider.getArtifactTooltip(artifactType);
      this.contextValue = `grails-${artifactType}`;
    } else if (filePath) {
      // Use file extension detection
      this.iconPath = GrailsIconProvider.getFileExtensionIcon(filePath);
      this.contextValue = `grails-file`;
    }

    // Add additional properties for file items
    if (filePath) {
      this.resourceUri = Uri.file(filePath);
      this.command = {
        command: "vscode.open",
        title: "Open File",
        arguments: [this.resourceUri],
      };

      // Add tooltip with file path
      this.tooltip = `${this.label}\n${workspace.asRelativePath(filePath)}`;
    }
  }

  private getIconForFile(filePath: string): ThemeIcon {
    const ext = path.extname(filePath).toLowerCase();

    switch (ext) {
      case ".gsp":
        return new ThemeIcon(Icons.GSP_FILE, new ThemeColor(Colors.ORANGE));
      case ".yml":
      case ".yaml":
        return new ThemeIcon(Icons.YML_FILE, new ThemeColor(Colors.BLUE));
      case ".groovy":
        return new ThemeIcon(Icons.GROOVY_FILE, new ThemeColor(Colors.PRIMARY_GREEN));
      case ".js":
        return new ThemeIcon("symbol-function", new ThemeColor(Colors.YELLOW));
      case ".css":
      case ".scss":
        return new ThemeIcon("symbol-color", new ThemeColor(Colors.PURPLE));
      case ".properties":
        return new ThemeIcon("symbol-key", new ThemeColor(Colors.GRAY));
      default:
        return new ThemeIcon("symbol-file");
    }
  }

  private getIconForArtifact(type: GrailsArtifactType): ThemeIcon {
    switch (type) {
      case GrailsArtifactType.CONTROLLER:
        return new ThemeIcon(Icons.CONTROLLER, new ThemeColor(Colors.PRIMARY_GREEN));
      case GrailsArtifactType.SERVICE:
        return new ThemeIcon(Icons.SERVICE, new ThemeColor(Colors.BLUE));
      case GrailsArtifactType.DOMAIN:
        return new ThemeIcon(Icons.DOMAIN, new ThemeColor(Colors.PURPLE));
      case GrailsArtifactType.VIEW:
        return new ThemeIcon(Icons.VIEW, new ThemeColor(Colors.ORANGE));
      case GrailsArtifactType.TAGLIB:
        return new ThemeIcon(Icons.TAGLIB, new ThemeColor(Colors.RED));
      default:
        return new ThemeIcon(Icons.GRAILS);
    }
  }
}

export class GrailsTreeDataProvider implements TreeDataProvider<GrailsTreeItem> {
  private _onDidChangeTreeData: EventEmitter<GrailsTreeItem | undefined | null | void> = new EventEmitter<
    GrailsTreeItem | undefined | null | void
  >();
  readonly onDidChangeTreeData: Event<GrailsTreeItem | undefined | null | void> =
    this._onDidChangeTreeData.event;

  constructor(private context: ExtensionContext) {
    console.log("🌳 GrailsTreeDataProvider created");
  }

  refresh(): void {
    console.log("🔄 Tree data refreshing...");
    this._onDidChangeTreeData.fire();
  }

  getTreeItem(element: GrailsTreeItem): TreeItem {
    return element;
  }

  async getChildren(element?: GrailsTreeItem): Promise<GrailsTreeItem[]> {
    console.log("📂 Getting tree children for:", element?.label || "root");

    if (!element) {
      // Root level - check if we have a valid project
      const projectType = await this.detectProjectType();
      if (projectType === "none") {
        return [new GrailsTreeItem("No Grails/Groovy projecy detected", TreeItemCollapsibleState.None)];
      }

      return this.getRootItemsForProjectType(projectType);
    }

    // ✅ Handle special nested cases
    if (element.contextValue) {
      switch (element.contextValue) {
        case "grails-view-folder":
          return this.getViewFilesForController(element.id!);

        case "grails-asset-js":
          return this.getAssetFiles("grails-app/assets/javascripts/**/*.js");

        case "grails-asset-css":
          return this.getAssetFiles("grails-app/assets/stylesheets/**/*.{css,scss}");

        case "grails-asset-images":
          return this.getAssetFiles("grails-app/assets/images/**/*.{png,jpg,jpeg,gif,svg}");
      }
    }

    // Standard artifact type expansion
    if (element.artifactType) {
      return this.getArtifactFiles(element.artifactType);
    }

    return [];
  }

  private async getRootItemsForProjectType(projectType: string): Promise<GrailsTreeItem[]> {
    switch (projectType) {
      case "grails":
        return await this.getGrailsProjectStructure();
      case "groovy":
        return await this.getGroovyProjectStructure();
      default:
        return await this.getGenericGradleStructure();
    }
  }

  private async getGrailsProjectStructure(): Promise<GrailsTreeItem[]> {
    const workspaceRoot = workspace.workspaceFolders![0].uri.fsPath;

    // Get counts for each artifact type
    const counts = await this.getArtifactCounts(workspaceRoot);

    return [
      // 🎯 CORE ARTIFACTS (Expanded - Most Important)
      new GrailsTreeItem(
        `🚀 Controllers ${counts.controllers > 0 ? `(${counts.controllers})` : ""}`,
        TreeItemCollapsibleState.Expanded,
        GrailsArtifactType.CONTROLLER,
      ),
      new GrailsTreeItem(
        `⚙️ Services ${counts.services > 0 ? `(${counts.services})` : ""}`,
        TreeItemCollapsibleState.Expanded,
        GrailsArtifactType.SERVICE,
      ),
      new GrailsTreeItem(
        `🗄️ Domains ${counts.domains > 0 ? `(${counts.domains})` : ""}`,
        TreeItemCollapsibleState.Expanded,
        GrailsArtifactType.DOMAIN,
      ),
      new GrailsTreeItem(
        `📄 Views ${counts.views > 0 ? `(${counts.views})` : ""}`,
        TreeItemCollapsibleState.Collapsed,
        GrailsArtifactType.VIEW,
      ),

      // 🎨 PRESENTATION & UI (Collapsed)
      new GrailsTreeItem(
        `🏷️ TagLibs ${counts.taglibs > 0 ? `(${counts.taglibs})` : ""}`,
        TreeItemCollapsibleState.Collapsed,
        GrailsArtifactType.TAGLIB,
      ),
      new GrailsTreeItem(
        `🎨 Assets ${counts.assets > 0 ? `(${counts.assets})` : ""}`,
        TreeItemCollapsibleState.Collapsed,
        GrailsArtifactType.ASSETS,
      ),
      new GrailsTreeItem(
        `🌐 i18n ${counts.i18n > 0 ? `(${counts.i18n})` : ""}`,
        TreeItemCollapsibleState.Collapsed,
        GrailsArtifactType.I18N,
      ),

      // ⚙️ CONFIGURATION & ROUTING
      new GrailsTreeItem(
        "🔗 URL Mappings",
        TreeItemCollapsibleState.Collapsed,
        GrailsArtifactType.URL_MAPPING,
      ),
      new GrailsTreeItem("⚙️ Configuration", TreeItemCollapsibleState.Collapsed, GrailsArtifactType.CONFIG),
      new GrailsTreeItem("🚀 Init & Bootstrap", TreeItemCollapsibleState.Collapsed, GrailsArtifactType.INIT),

      // 🧪 DEVELOPMENT & TESTING
      new GrailsTreeItem(
        `🧪 Tests ${counts.tests > 0 ? `(${counts.tests})` : ""}`,
        TreeItemCollapsibleState.Collapsed,
        GrailsArtifactType.TESTS,
      ),
      new GrailsTreeItem(
        `📁 Source ${counts.groovySrc > 0 ? `(${counts.groovySrc})` : ""}`,
        TreeItemCollapsibleState.Collapsed,
        GrailsArtifactType.GROOVY_SRC,
      ),
    ];
  }

  // Get artifact counts for better UX
  private async getArtifactCounts(workspaceRoot: string): Promise<any> {
    const counts = {
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
      // Controllers
      const controllers = await workspace.findFiles("grails-app/controllers/**/*.groovy");
      counts.controllers = controllers.length;

      // Services
      const services = await workspace.findFiles("grails-app/services/**/*.groovy");
      counts.services = services.length;

      // Domains
      const domains = await workspace.findFiles("grails-app/domain/**/*.groovy");
      counts.domains = domains.length;

      // Views (GSP files)
      const views = await workspace.findFiles("grails-app/views/**/*.gsp");
      counts.views = views.length;

      // TagLibs
      const taglibs = await workspace.findFiles("grails-app/taglib/**/*.groovy");
      counts.taglibs = taglibs.length;

      // Assets
      const assets = await workspace.findFiles("grails-app/assets/**/*");
      counts.assets = assets.length;

      // i18n files
      const i18n = await workspace.findFiles("grails-app/i18n/**/*.properties");
      counts.i18n = i18n.length;

      // Tests (both unit and integration)
      const unitTests = await workspace.findFiles("src/test/**/*Spec.groovy");
      const integrationTests = await workspace.findFiles("src/integration-test/**/*Spec.groovy");
      counts.tests = unitTests.length + integrationTests.length;

      // Additional Groovy source
      const groovySrc = await workspace.findFiles("src/main/groovy/**/*.groovy");
      counts.groovySrc = groovySrc.length;
    } catch (error) {
      console.log("Error counting artifacts:", error);
    }

    return counts;
  }

  private async detectProjectType(): Promise<"grails" | "groovy" | "gradle" | "none"> {
    const workspaceFolders = workspace.workspaceFolders;
    if (!workspaceFolders) return "none";

    const workspaceRoot = workspaceFolders[0].uri.fsPath;

    // Primary detection: Grails 7 project structure
    if (fs.existsSync(path.join(workspaceRoot, "grails-app"))) {
      console.log("🎯 Detected: Grails project");
      return "grails";
    }

    // Check for Groovy
    if (fs.existsSync(path.join(workspaceRoot, "src", "main", "groovy"))) {
      console.log("🎯 Detected: Groovy project");
      return "groovy";
    }

    // Check for Gradle with potential Groovy support
    if (fs.existsSync(path.join(workspaceRoot, "build.gradle"))) {
      console.log("🎯 Detected: Gradle project");
      return "gradle";
    }

    return "none";
  }

  private async getArtifactFiles(type: GrailsArtifactType): Promise<GrailsTreeItem[]> {
    const workspaceFolders = workspace.workspaceFolders;
    if (!workspaceFolders) return [];

    switch (type) {
      case GrailsArtifactType.CONTROLLER:
        return this.getFilesFromPattern("grails-app/controllers/**/*.groovy", type);

      case GrailsArtifactType.SERVICE:
        return this.getFilesFromPattern("grails-app/services/**/*.groovy", type);

      case GrailsArtifactType.DOMAIN:
        return this.getFilesFromPattern("grails-app/domain/**/*.groovy", type);

      case GrailsArtifactType.VIEW:
        return this.getViewsStructure();

      case GrailsArtifactType.TAGLIB:
        return this.getFilesFromPattern("grails-app/taglib/**/*.groovy", type);

      case GrailsArtifactType.ASSETS:
        return this.getAssetsStructure();

      case GrailsArtifactType.CONFIG:
        return this.getConfigurationFiles();

      case GrailsArtifactType.URL_MAPPING:
        return this.getUrlMappingFiles();

      case GrailsArtifactType.I18N:
        return this.getFilesFromPattern("grails-app/i18n/**/*.properties", type);

      case GrailsArtifactType.INIT:
        return this.getInitFiles();

      case GrailsArtifactType.TESTS:
        return this.getTestStructure();

      // ✅ Add support for nested test categories
      case GrailsArtifactType.UNIT_TESTS:
        return this.getFilesFromPattern("src/test/**/*Spec.groovy", type);

      case GrailsArtifactType.INTEGRATION_TESTS:
        return this.getFilesFromPattern("src/integration-test/**/*Spec.groovy", type);

      case GrailsArtifactType.GROOVY_SRC:
        return this.getFilesFromPattern("src/main/groovy/**/*.groovy", type);

      default:
        console.warn(`No handler for artifact type: ${type}`);
        return [];
    }
  }

  // Enhanced file pattern matching with proper icons
  private async getFilesFromPattern(pattern: string, type: GrailsArtifactType): Promise<GrailsTreeItem[]> {
    try {
      const files = await workspace.findFiles(pattern);
      return files.map((file) => {
        const fileName = path.basename(file.fsPath);
        const nameWithoutExt = path.basename(file.fsPath, path.extname(file.fsPath));

        return new GrailsTreeItem(fileName, TreeItemCollapsibleState.None, type, file.fsPath);
      });
    } catch {
      return [];
    }
  }

  // Special handling for Views (organized by controller)
  private async getViewsStructure(): Promise<GrailsTreeItem[]> {
    try {
      const viewFiles = await workspace.findFiles("grails-app/views/**/*.gsp");
      const viewsByController = new Map<string, string[]>();

      viewFiles.forEach((file) => {
        const relativePath = workspace.asRelativePath(file);
        const pathParts = this.splitPath(relativePath);

        let controllerName = "";
        if (pathParts.length >= 4) {
          // grails-app/views/controllerName/view.gsp
          controllerName = pathParts[2];
        } else if (pathParts.length === 3) {
          // grails-app/views/view.gsp (root-level views)
          controllerName = ""; // ✅ Use "root" instead of empty string
        }

        if (!viewsByController.has(controllerName)) {
          viewsByController.set(controllerName, []);
        }
        viewsByController.get(controllerName)!.push(file.fsPath);
      });

      const result: GrailsTreeItem[] = [];

      // ✅ Handle root-level views first
      if (viewsByController.has("") && viewsByController.get("")!.length > 0) {
        const rootViews = viewsByController.get("")!;

        // Add individual root views directly to result
        rootViews.forEach((filePath) => {
          const fileName = path.basename(filePath);
          result.push(
            new GrailsTreeItem(
              `📄 ${fileName}`,
              TreeItemCollapsibleState.None,
              GrailsArtifactType.VIEW,
              filePath,
            ),
          );
        });

        // Remove from map so we don't process it again
        viewsByController.delete("");
      }

      // ✅ Create sections for each controller/group
      for (const [controllerName, files] of viewsByController) {
        if (controllerName) {
          const controllerItem = new GrailsTreeItem(
            `📁 ${controllerName} (${files.length})`,
            TreeItemCollapsibleState.Collapsed,
            GrailsArtifactType.VIEW,
          );

          controllerItem.contextValue = "grails-view-folder";
          controllerItem.id = `view-folder-${controllerName}`;
          result.push(controllerItem);
        }
      }

      return result;
    } catch (error) {
      console.error("❌ Error loading views:", error);
      return [];
    }
  }

  // Assets structure (CSS, JS, Images)
  private async getAssetsStructure(): Promise<GrailsTreeItem[]> {
    const result: GrailsTreeItem[] = [];

    try {
      // JavaScript files
      const jsFiles = await workspace.findFiles("grails-app/assets/javascripts/**/*.js");
      if (jsFiles.length > 0) {
        const jsItem = new GrailsTreeItem(
          `📜 JavaScript (${jsFiles.length})`,
          TreeItemCollapsibleState.Collapsed,
          GrailsArtifactType.ASSETS, // We'll handle this specially
        );
        jsItem.contextValue = "grails-asset-js";
        jsItem.id = "asset-js";
        result.push(jsItem);
      }

      // CSS/SCSS files
      const cssFiles = await workspace.findFiles("grails-app/assets/stylesheets/**/*.{css,scss}");
      if (cssFiles.length > 0) {
        const cssItem = new GrailsTreeItem(
          `🎨 Stylesheets (${cssFiles.length})`,
          TreeItemCollapsibleState.Collapsed,
          GrailsArtifactType.ASSETS,
        );
        cssItem.contextValue = "grails-asset-css";
        cssItem.id = "asset-css";
        result.push(cssItem);
      }

      // Images
      const imageFiles = await workspace.findFiles("grails-app/assets/images/**/*.{png,jpg,jpeg,gif,svg}");
      if (imageFiles.length > 0) {
        const imageItem = new GrailsTreeItem(
          `🖼️ Images (${imageFiles.length})`,
          TreeItemCollapsibleState.Collapsed,
          GrailsArtifactType.ASSETS,
        );
        imageItem.contextValue = "grails-asset-images";
        imageItem.id = "asset-images";
        result.push(imageItem);
      }
    } catch (error) {
      console.log("Error loading assets:", error);
    }

    return result;
  }

  // Configuration files
  private async getConfigurationFiles(): Promise<GrailsTreeItem[]> {
    const result: GrailsTreeItem[] = [];
    const workspaceRoot = workspace.workspaceFolders![0].uri.fsPath;

    // Main configuration files
    const configFiles = [
      { path: "grails-app/conf/application.yml", name: "⚙️ application.yml", icon: Icons.YML_FILE },
      { path: "grails-app/conf/application.groovy", name: "⚙️ application.groovy", icon: Icons.GROOVY_FILE },
      {
        path: "grails-app/conf/spring/resources.groovy",
        name: "🌱 resources.groovy",
        icon: Icons.GROOVY_FILE,
      },
      { path: "grails-app/conf/logback.xml", name: "📋 logback.xml", icon: Icons.CONFIG },
    ];

    for (const config of configFiles) {
      const fullPath = path.join(workspaceRoot, config.path);
      if (fs.existsSync(fullPath)) {
        result.push(
          new GrailsTreeItem(config.name, TreeItemCollapsibleState.None, GrailsArtifactType.CONFIG, fullPath),
        );
      }
    }

    return result;
  }

  // URL Mapping files
  private async getUrlMappingFiles(): Promise<GrailsTreeItem[]> {
    try {
      const mappingFiles = await workspace.findFiles("grails-app/controllers/**/UrlMappings.groovy");
      return mappingFiles.map(
        (file) =>
          new GrailsTreeItem(
            `🔗 ${path.basename(file.fsPath)}`,
            TreeItemCollapsibleState.None,
            GrailsArtifactType.URL_MAPPING,
            file.fsPath,
          ),
      );
    } catch {
      return [];
    }
  }

  // Init files (Application.groovy, BootStrap.groovy)
  private async getInitFiles(): Promise<GrailsTreeItem[]> {
    const result: GrailsTreeItem[] = [];

    const initFiles = [
      { path: "grails-app/init/**/Application.groovy", name: "🚀 Application.groovy" },
      { path: "grails-app/init/**/BootStrap.groovy", name: "⚡ BootStrap.groovy" },
    ];

    for (const init of initFiles) {
      try {
        const files = await workspace.findFiles(init.path);
        files.forEach((file) => {
          result.push(
            new GrailsTreeItem(
              init.name,
              TreeItemCollapsibleState.None,
              GrailsArtifactType.INIT,
              file.fsPath,
            ),
          );
        });
      } catch (error) {
        console.log(`Error finding ${init.name}:`, error);
      }
    }

    return result;
  }

  // Test structure (Unit + Integration)
  private async getTestStructure(): Promise<GrailsTreeItem[]> {
    const result: GrailsTreeItem[] = [];

    try {
      // Unit tests
      const unitTests = await workspace.findFiles("src/test/**/*Spec.groovy");
      if (unitTests.length > 0) {
        result.push(
          new GrailsTreeItem(
            `🧪 Unit Tests (${unitTests.length})`,
            TreeItemCollapsibleState.Collapsed,
            GrailsArtifactType.UNIT_TESTS,
          ),
        );
      }

      // Integration tests
      const integrationTests = await workspace.findFiles("src/integration-test/**/*Spec.groovy");
      if (integrationTests.length > 0) {
        result.push(
          new GrailsTreeItem(
            `⚡ Integration Tests (${integrationTests.length})`,
            TreeItemCollapsibleState.Collapsed,
            GrailsArtifactType.INTEGRATION_TESTS,
          ),
        );
      }
    } catch (error) {
      console.log("Error loading tests:", error);
    }

    return result;
  }

  private async getGroovyProjectStructure(): Promise<GrailsTreeItem[]> {
    const result: GrailsTreeItem[] = [];

    // all files under /src/main/groovy are source files
    // all files under /src/test/groovy are test files
    const sourceFiles = await workspace.findFiles("/src/main/groovy/**/*.{groovy,java}");
    const testFiles = await workspace.findFiles("/src/test/groovy/**/*.{groovy,java}");

    if (sourceFiles.length > 0) {
      result.push(
        new GrailsTreeItem("Source Files", TreeItemCollapsibleState.Collapsed, GrailsArtifactType.GROOVY_SRC),
      );
    }

    if (testFiles.length > 0) {
      result.push(
        new GrailsTreeItem("Test Files", TreeItemCollapsibleState.Collapsed, GrailsArtifactType.TESTS),
      );
    }

    return result;
  }

  private async getGenericGradleStructure(): Promise<GrailsTreeItem[]> {
    const result: GrailsTreeItem[] = [];
    return result;
  }

  // Get individual view files for a specific controller
  private async getViewFilesForController(folderId: string): Promise<GrailsTreeItem[]> {
    const controllerName = folderId.replace("view-folder-", "");

    try {
      let pattern = "";
      if (controllerName === "root") {
        // Root views pattern - files directly under views/
        pattern = "grails-app/views/*.gsp";
      } else {
        // Controller views pattern
        pattern = `grails-app/views/${controllerName}/**/*.gsp`;
      }

      const viewFiles = await workspace.findFiles(pattern);

      return viewFiles.map((file) => {
        const fileName = path.basename(file.fsPath);
        return new GrailsTreeItem(
          `📄 ${fileName}`,
          TreeItemCollapsibleState.None,
          GrailsArtifactType.VIEW,
          file.fsPath,
        );
      });
    } catch (error) {
      console.error(`Error loading views for ${controllerName}:`, error);
      return [];
    }
  }

  // Get asset files by pattern
  private async getAssetFiles(pattern: string): Promise<GrailsTreeItem[]> {
    try {
      const files = await workspace.findFiles(pattern);

      return files.map((file) => {
        const fileName = path.basename(file.fsPath);
        const ext = path.extname(fileName).toLowerCase();

        let icon = "📄";
        if ([".js"].includes(ext)) icon = "📜";
        else if ([".css", ".scss"].includes(ext)) icon = "🎨";
        else if ([".png", ".jpg", ".jpeg", ".gif", ".svg"].includes(ext)) icon = "🖼️";

        return new GrailsTreeItem(
          `${icon} ${fileName}`,
          TreeItemCollapsibleState.None,
          GrailsArtifactType.ASSETS,
          file.fsPath,
        );
      });
    } catch (error) {
      console.error(`Error loading asset files with pattern ${pattern}:`, error);
      return [];
    }
  }

  // Cross-platform path splitting helper
  private splitPath(filePath: string): string[] {
    // Normalize all path separators to forward slashes, then split
    return filePath.replace(/\\/g, "/").split("/");
  }
}
