import * as vscode from "vscode";
import { BaseCommandProvider } from "./BaseCommandProvider";

export class LogCommands extends BaseCommandProvider {
  registerCommands(): void {
    this.register("grails.runAppWithTailing", async () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        vscode.window.showWarningMessage("No Grails projects found");
        return;
      }
      await this.container.logStreamingService.startAppWithTailing(projects[0]);
    });

    this.register("grails.showLogs", () => {
      this.container.logStreamingService.showLogs();
    });
  }
}
