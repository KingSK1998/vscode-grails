import * as fs from "fs";
import * as path from "path";
import * as vscode from "vscode";
import type { ProjectInfo } from "../../features/models/modelTypes";

export class ArtifactService {
  async createController(project: ProjectInfo, name: string): Promise<void> {
    const className = this.ensureSuffix(name, "Controller");
    const packageName = await this.pickPackage(project, "controllers");
    const content = this.getControllerTemplate(packageName, className);
    await this.createFile(project, "controllers", packageName, className, content);
  }

  async createService(project: ProjectInfo, name: string): Promise<void> {
    const className = this.ensureSuffix(name, "Service");
    const packageName = await this.pickPackage(project, "services");
    const content = this.getServiceTemplate(packageName, className);
    await this.createFile(project, "services", packageName, className, content);
  }

  async createDomain(project: ProjectInfo, name: string): Promise<void> {
    const className = name; // No suffix for domain
    const packageName = await this.pickPackage(project, "domain");
    const content = this.getDomainTemplate(packageName, className);
    await this.createFile(project, "domain", packageName, className, content);
  }

  async createTagLib(project: ProjectInfo, name: string): Promise<void> {
    const className = this.ensureSuffix(name, "TagLib");
    const packageName = await this.pickPackage(project, "taglib");
    const content = this.getTagLibTemplate(packageName, className);
    await this.createFile(project, "taglib", packageName, className, content);
  }

  private ensureSuffix(name: string, suffix: string): string {
    if (name.endsWith(suffix)) {
      return name;
    }
    return name + suffix;
  }

  private async pickPackage(project: ProjectInfo, _artifactFolder: string): Promise<string> {
    // Naive package detection: look at folder structure or default to project name
    const defaultPackage = project.name.toLowerCase().replace(/[^a-z0-9]/g, "");
    const input = await vscode.window.showInputBox({
      prompt: "Enter package name",
      value: defaultPackage,
    });
    return input ?? defaultPackage;
  }

  private async createFile(
    project: ProjectInfo,
    folder: string,
    packageName: string,
    className: string,
    content: string
  ): Promise<void> {
    const packagePath = packageName.replace(/\./g, "/");
    const targetDir = path.join(project.rootPath, "grails-app", folder, packagePath);
    const targetFile = path.join(targetDir, `${className}.groovy`);

    if (fs.existsSync(targetFile)) {
      vscode.window.showErrorMessage(`File already exists: ${targetFile}`);
      return;
    }

    if (!fs.existsSync(targetDir)) {
      fs.mkdirSync(targetDir, { recursive: true });
    }

    fs.writeFileSync(targetFile, content);

    const doc = await vscode.workspace.openTextDocument(vscode.Uri.file(targetFile));
    await vscode.window.showTextDocument(doc);
    vscode.window.showInformationMessage(`Created ${className} in package ${packageName}`);
  }

  private getControllerTemplate(packageName: string, className: string): string {
    return `package ${packageName}

class ${className} {

    def index() { }
}
`;
  }

  private getServiceTemplate(packageName: string, className: string): string {
    return `package ${packageName}

import grails.gorm.transactions.Transactional

@Transactional
class ${className} {

    def serviceMethod() {

    }
}
`;
  }

  private getDomainTemplate(packageName: string, className: string): string {
    return `package ${packageName}

class ${className} {

    static constraints = {
    }
}
`;
  }

  private getTagLibTemplate(packageName: string, className: string): string {
    return `package ${packageName}

class ${className} {
    static namespace = "my"

    /**
     * Example tag: <my:example name="world" />
     */
    def example = { attrs, body ->
        out << "Hello \${attrs.name}!"
    }
}
`;
  }
}
