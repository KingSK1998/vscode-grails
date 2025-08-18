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
import { GrailsArtifact, GrailsArtifactType } from "../types/grails-types";
import { GrailsIconProvider } from "../providers/grails-icon-provider";

export class GrailsTreeItem extends TreeItem {
  constructor(
    public readonly label: string,
    public readonly collapsibleState: TreeItemCollapsibleState,
    public readonly grailsType: GrailsArtifactType | string,
    public readonly artifact?: GrailsArtifact,
  ) {
    super(label, collapsibleState);

    // Set icon and tooltip using the provider
    if (Object.values(GrailsArtifactType).includes(grailsType as GrailsArtifactType)) {
      this.iconPath = GrailsIconProvider.getArtifactIcon(grailsType as GrailsArtifactType);
      this.tooltip = GrailsIconProvider.getArtifactTooltip(grailsType as GrailsArtifactType);
    } else {
      // Handle string-based types (for backward compatibility)
      this.iconPath = GrailsIconProvider.getIconFromString(grailsType);
      this.tooltip = GrailsIconProvider.getTooltipFromString(grailsType);
    }

    this.contextValue = `grails-${grailsType}`;

    // Add additional properties for file items
    if (artifact) {
      this.resourceUri = Uri.file(artifact.path);
      this.command = {
        command: "vscode.open",
        title: "Open File",
        arguments: [this.resourceUri],
      };
    }
  }
}

export class GrailsTreeDataProvider implements TreeDataProvider<GrailsTreeItem> {
  private _onDidChangeTreeData: EventEmitter<GrailsTreeItem | undefined | null | void> = new EventEmitter<
    GrailsTreeItem | undefined | null | void
  >();
  readonly onDidChangeTreeData: Event<GrailsTreeItem | undefined | null | void> =
    this._onDidChangeTreeData.event;

  constructor(private context: ExtensionContext) {}

  refresh(): void {
    this._onDidChangeTreeData.fire();
  }

  getTreeItem(element: GrailsTreeItem): TreeItem {
    return element;
  }

  async getChildren(element?: GrailsTreeItem): Promise<GrailsTreeItem[]> {
    if (!element) {
      // Root level - show main Grails categories
      return this.getGrailsRootItems();
    } else {
      // Child level - show Grails artifacts
      return this.getGrailsArtifacts(element.grailsType);
    }
  }

  private async getGrailsRootItems(): Promise<GrailsTreeItem[]> {
    return [
      new GrailsTreeItem("Controller", TreeItemCollapsibleState.Expanded, GrailsArtifactType.CONTROLLER),
      new GrailsTreeItem("Services", TreeItemCollapsibleState.Expanded, GrailsArtifactType.SERVICE),
      new GrailsTreeItem("Domains", TreeItemCollapsibleState.Expanded, GrailsArtifactType.DOMAIN),
      new GrailsTreeItem("View", TreeItemCollapsibleState.Expanded, GrailsArtifactType.VIEW),
      new GrailsTreeItem("TagLib", TreeItemCollapsibleState.Expanded, GrailsArtifactType.TAGLIB),
      new GrailsTreeItem("URL Mappings", TreeItemCollapsibleState.Expanded, GrailsArtifactType.URL_MAPPING),
      new GrailsTreeItem("Configuration", TreeItemCollapsibleState.Expanded, GrailsArtifactType.CONFIG),
    ];
  }

  private async getGrailsArtifacts(type: GrailsArtifactType | string): Promise<GrailsTreeItem[]> {
    // TODO: Replace with actual project scanning logic
    // For now, return mock data
    const mockArtifacts = await this.scanForArtifacts(type);

    return mockArtifacts.map(
      (artifact) => new GrailsTreeItem(artifact.name, TreeItemCollapsibleState.None, artifact.type, artifact),
    );
  }

  private async scanForArtifacts(type: GrailsArtifactType | string): Promise<GrailsArtifact[]> {
    // Mock implementation - replace with actual file scanning
    const workspaceFolders = workspace.workspaceFolders;
    if (!workspaceFolders) {
      return [];
    }

    const rootPath = workspaceFolders[0].uri.fsPath;

    // TODO: Implement actual file scanning logic based on Grails conventions
    // For now, return mock data
    const mockData: GrailsArtifact[] = [
      {
        name: "UserController",
        type: GrailsArtifactType.CONTROLLER,
        path: `${rootPath}/grails-app/controllers/UserController.groovy`,
      },
      {
        name: "UserService",
        type: GrailsArtifactType.SERVICE,
        path: `${rootPath}/grails-app/services/UserService.groovy`,
      },
    ];

    return mockData.filter((artifact) => artifact.type === type);
  }
}
