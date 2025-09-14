import fs from "fs";
import path from "path";
import { TreeItemCollapsibleState, Uri } from "vscode";
import type { ProjectInfo } from "../../features/models/modelTypes";
import { ArtifactType } from "../../features/models/modelTypes";
import { GrailsTreeItem } from "./GrailsTreeItem";
import { TreeItemKind } from "./TreeItemKind";

interface PackageInfo {
  packageName: string;
  className: string;
  filePath: string;
}

interface PackageHierarchy {
  segments: string[];
  fullPath: string;
  subPackages: Map<string, PackageHierarchy>;
  classes: PackageInfo[];
  hasContent: boolean;
}

export class FlattenedPackageNode extends GrailsTreeItem {
  public children?: GrailsTreeItem[];

  constructor(
    label: string,
    public readonly packagePath: string,
    public readonly fullPackageName: string,
    projectInfo: ProjectInfo,
    resourcePath?: string
  ) {
    super(
      label,
      TreeItemCollapsibleState.Collapsed,
      TreeItemKind.Package,
      { command: "grails.noAction", title: "No Action" },
      projectInfo,
      resourcePath
    );

    this.tooltip = `Package: ${fullPackageName}`;
    this.contextValue = "grails-package-flattened";
  }

  // protected override resolveIcon(): ThemeIcon {
  //   return new ThemeIcon("package", new ThemeColor("charts.blue"));
  // }
}

export class FlattenedPackageBuilder {
  static buildFlattenedPackageTree(
    artifactPath: string,
    artifactType: ArtifactType,
    project: ProjectInfo
  ): GrailsTreeItem[] {
    if (!fs.existsSync(artifactPath)) {
      return [];
    }

    try {
      // 1. Parse all package information
      const packages = this.parsePackageStructure(artifactPath, artifactType);

      if (packages.length === 0) {
        return [
          new GrailsTreeItem(
            "No artifacts found",
            TreeItemCollapsibleState.None,
            TreeItemKind.Error,
            { command: "grails.noAction", title: "No Action" },
            project
          ),
        ];
      }

      // 2. Build initial hierarchy
      const hierarchy = this.buildPackageHierarchy(packages);

      // 3. Flatten single-child paths
      const flattened = this.flattenSingleChildPaths(hierarchy);

      // 4. Create tree nodes
      return this.createFlattenedTreeNodes(flattened, project);
    } catch (error) {
      console.warn(`Error building flattened package tree:`, error);
      return [];
    }
  }

  // Parse all Groovy files and extract package information
  private static parsePackageStructure(
    artifactPath: string,
    artifactType: ArtifactType
  ): PackageInfo[] {
    const packages: PackageInfo[] = [];
    const groovyFiles = this.findGroovyFiles(artifactPath);

    for (const filePath of groovyFiles) {
      const packageInfo = this.extractPackageFromFile(filePath, artifactType);
      if (packageInfo) {
        packages.push(packageInfo);
      }
    }

    return packages;
  }

  private static findGroovyFiles(dirPath: string): string[] {
    const files: string[] = [];

    try {
      const entries = fs.readdirSync(dirPath, { withFileTypes: true });

      for (const entry of entries) {
        const fullPath = path.join(dirPath, entry.name);

        if (entry.isDirectory()) {
          files.push(...this.findGroovyFiles(fullPath));
        } else if (entry.name.endsWith(".groovy")) {
          files.push(fullPath);
        }
      }
    } catch (error) {
      console.warn(`Error reading directory ${dirPath}:`, error);
    }

    return files;
  }

  private static extractPackageFromFile(
    filePath: string,
    artifactType: ArtifactType
  ): PackageInfo | null {
    try {
      const content = fs.readFileSync(filePath, "utf8");
      const fileName = path.basename(filePath, ".groovy");

      // Extract package declaration
      const packageMatch = content.match(/^\s*package\s+([\w.]+)\s*$/m);
      const packageName = packageMatch ? packageMatch[1] : "";

      // Validate artifact type
      const suffix = this.getArtifactSuffix(artifactType);
      if (suffix && !fileName.endsWith(suffix)) {
        return null;
      }

      return {
        packageName,
        className: fileName,
        filePath,
      };
    } catch (error) {
      console.warn(`Error reading file ${filePath}:`, error);
      return null;
    }
  }

  // ✅ KEY: Build initial hierarchy
  private static buildPackageHierarchy(packages: PackageInfo[]): Map<string, PackageHierarchy> {
    const hierarchy = new Map<string, PackageHierarchy>();

    for (const pkg of packages) {
      const segments = pkg.packageName ? pkg.packageName.split(".") : [];
      this.insertIntoHierarchy(hierarchy, segments, pkg, pkg.packageName);
    }

    return hierarchy;
  }

  private static insertIntoHierarchy(
    hierarchy: Map<string, PackageHierarchy>,
    segments: string[],
    pkg: PackageInfo,
    fullPackage: string
  ): void {
    if (segments.length === 0) {
      // Root level class
      if (!hierarchy.has("")) {
        hierarchy.set("", {
          segments: [],
          fullPath: "",
          subPackages: new Map(),
          classes: [],
          hasContent: false,
        });
      }
      hierarchy.get("")!.classes.push(pkg);
      hierarchy.get("")!.hasContent = true;
      return;
    }

    const [first, ...rest] = segments;

    if (!hierarchy.has(first)) {
      hierarchy.set(first, {
        segments: [first],
        fullPath: first,
        subPackages: new Map(),
        classes: [],
        hasContent: false,
      });
    }

    const node = hierarchy.get(first)!;

    if (rest.length === 0) {
      // This is the final segment, add the class
      node.classes.push(pkg);
      node.hasContent = true;
    } else {
      // Continue down the hierarchy
      this.insertIntoHierarchy(node.subPackages, rest, pkg, fullPackage);
      // Check if any sub-packages have content
      node.hasContent =
        node.classes.length > 0 ||
        Array.from(node.subPackages.values()).some(sub => sub.hasContent);
    }
  }

  // ✅ KEY: Flatten single-child paths
  private static flattenSingleChildPaths(
    hierarchy: Map<string, PackageHierarchy>
  ): Map<string, PackageHierarchy> {
    const flattened = new Map<string, PackageHierarchy>();

    for (const [key, node] of hierarchy) {
      const flattenedNode = this.flattenNodePath(node);
      flattened.set(flattenedNode.fullPath || key, flattenedNode);
    }

    return flattened;
  }

  private static flattenNodePath(node: PackageHierarchy): PackageHierarchy {
    // If this node has exactly one sub-package and no classes, merge with child
    while (node.subPackages.size === 1 && node.classes.length === 0) {
      const nextValue = node.subPackages.entries().next().value;
      if (nextValue) {
        const [childKey, childNode] = nextValue;
        // ...

        // Merge the paths
        const newFullPath = node.fullPath ? `${node.fullPath}.${childKey}` : childKey;
        const newSegments = [...node.segments, ...childNode.segments];

        node = {
          segments: newSegments,
          fullPath: newFullPath,
          subPackages: childNode.subPackages,
          classes: childNode.classes,
          hasContent: childNode.hasContent,
        };
      }
    }

    // Recursively flatten sub-packages
    const flattenedSubPackages = new Map<string, PackageHierarchy>();
    for (const [key, subNode] of node.subPackages) {
      const flattened = this.flattenNodePath(subNode);
      flattenedSubPackages.set(flattened.fullPath || key, flattened);
    }

    return {
      ...node,
      subPackages: flattenedSubPackages,
    };
  }

  // ✅ Create tree nodes from flattened structure
  private static createFlattenedTreeNodes(
    hierarchy: Map<string, PackageHierarchy>,
    project: ProjectInfo
  ): GrailsTreeItem[] {
    const items: GrailsTreeItem[] = [];

    for (const [key, node] of hierarchy) {
      if (!node.hasContent) {
        continue;
      }

      // Create package node (or root for empty key)
      if (key === "") {
        // Root level classes (no package)
        items.push(
          ...node.classes.map(
            cls =>
              new GrailsTreeItem(
                cls.className,
                TreeItemCollapsibleState.None,
                TreeItemKind.ArtifactFile,
                { command: "vscode.open", title: "Open", arguments: [Uri.file(cls.filePath)] },
                project,
                cls.filePath
              )
          )
        );
      } else {
        // Package node
        const packageNode = new FlattenedPackageNode(
          `📁 ${key}`,
          node.fullPath,
          node.fullPath,
          project
        );

        // Build children
        const children: GrailsTreeItem[] = [];

        // Add sub-packages
        const subPackageItems = this.createFlattenedTreeNodes(node.subPackages, project);
        children.push(...subPackageItems);

        // Add classes
        const classItems = node.classes.map(
          cls =>
            new GrailsTreeItem(
              cls.className,
              TreeItemCollapsibleState.None,
              TreeItemKind.ArtifactFile,
              { command: "vscode.open", title: "Open", arguments: [Uri.file(cls.filePath)] },
              project,
              cls.filePath
            )
        );
        children.push(...classItems);

        packageNode.children = children;
        items.push(packageNode);
      }
    }

    return items;
  }

  private static getArtifactSuffix(artifactType: ArtifactType): string {
    switch (artifactType) {
      case ArtifactType.Controller:
        return "Controller";
      case ArtifactType.Service:
        return "Service";
      case ArtifactType.Domain:
        return "";
      case ArtifactType.TagLib:
        return "TagLib";
      case ArtifactType.Interceptor:
        return "Interceptor";
      case ArtifactType.Job:
        return "Job";
      case ArtifactType.Command:
        return "Command";
      default:
        return "";
    }
  }
}
