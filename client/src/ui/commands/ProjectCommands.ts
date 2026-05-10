import * as vscode from "vscode";
import { BaseCommandProvider } from "./BaseCommandProvider";

export class ProjectCommands extends BaseCommandProvider {
  registerCommands(): void {
    this.register("grails.refreshProjects", async () => {
      await this.container.projectService.discoverProjects();
    });

    this.register("grails.showProjectConfig", () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        vscode.window.showWarningMessage("No Grails projects found");
        return;
      }

      const project = projects[0];
      const configInfo = [
        `**Project:** ${project.name}`,
        `**Type:** ${project.type}`,
        `**Root:** ${project.rootPath}`,
        `**Dependencies:** ${project?.dependencies?.length}`,
      ].join("\n");

      vscode.window.showInformationMessage(configInfo);
    });
  }
}
