import * as vscode from "vscode";
import { BaseCommandProvider } from "./BaseCommandProvider";

export class ArtifactCommands extends BaseCommandProvider {
  registerCommands(): void {
    this.register("grails.createController", async () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) return;
      const name = await vscode.window.showInputBox({
        prompt: "Enter controller name",
        placeHolder: "BookController",
        validateInput: this.validateArtifactName,
      });
      if (name) await this.container.artifactService.createController(projects[0], name);
    });

    this.register("grails.createService", async () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) return;
      const name = await vscode.window.showInputBox({
        prompt: "Enter service name",
        placeHolder: "UserService",
        validateInput: this.validateArtifactName,
      });
      if (name) await this.container.artifactService.createService(projects[0], name);
    });

    this.register("grails.createDomain", async () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) return;
      const name = await vscode.window.showInputBox({
        prompt: "Enter domain name",
        placeHolder: "User",
        validateInput: this.validateArtifactName,
      });
      if (name) await this.container.artifactService.createDomain(projects[0], name);
    });

    this.register("grails.createArtifact", async () => {
      await this.createArtifactWizard();
    });
  }

  private async createArtifactWizard(): Promise<void> {
    const artifactTypes = [
      { label: "Controller", description: "Handle web requests and responses" },
      { label: "Domain", description: "Domain model classes (GORM entities)" },
      { label: "Service", description: "Business logic services" },
      { label: "TagLib", description: "Custom GSP tags" },
      { label: "Job", description: "Quartz scheduled jobs" },
      { label: "Command", description: "Command objects for data binding" },
      { label: "Interceptor", description: "Request/response interceptors" },
    ];

    const selectedType = await vscode.window.showQuickPick(artifactTypes, {
      placeHolder: "Select artifact type to create",
      matchOnDescription: true,
    });

    if (!selectedType) return;

    const name = await vscode.window.showInputBox({
      prompt: `Enter ${selectedType.label} name`,
      placeHolder: `Book${selectedType.label}`,
      validateInput: this.validateArtifactName,
    });

    if (!name) return;

    const projects = this.container.projectService.getProjects();
    if (projects.length === 0) return;
    const project = projects[0];

    switch (selectedType.label) {
      case "Controller":
        await this.container.artifactService.createController(project, name);
        break;
      case "Service":
        await this.container.artifactService.createService(project, name);
        break;
      case "Domain":
        await this.container.artifactService.createDomain(project, name);
        break;
      case "TagLib":
        await this.container.artifactService.createTagLib(project, name);
        break;
      default:
        vscode.window.showInformationMessage(
          `Creating ${selectedType.label}: ${name} (Simplified implementation)`
        );
    }
  }

  private validateArtifactName = (value: string): string | undefined => {
    if (!value || value.trim().length === 0) return "Name cannot be empty";
    if (!/^[A-Za-z][A-Za-z0-9]*$/.test(value.trim()))
      return "Name must start with a letter and contain only letters and numbers";
    return undefined;
  };
}
