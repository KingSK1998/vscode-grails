import * as vscode from "vscode";
import { BaseCommandProvider } from "./BaseCommandProvider";

export class LegacyCommands extends BaseCommandProvider {
  registerCommands(): void {
    this.register("grails.run", () => this.executeGrailsTerminalCommand("run-app"));
    this.register("grails.test", () => this.executeGrailsTerminalCommand("test-app"));
    this.register("grails.clean", () => this.executeGrailsTerminalCommand("clean"));
    this.register("grails.compile", () => this.executeGrailsTerminalCommand("compile"));
  }

  private executeGrailsTerminalCommand(command: string): void {
    const projects = this.container.projectService.getProjects();
    if (projects.length === 0) {
      vscode.window.showWarningMessage("No Grails projects found");
      return;
    }
    const terminal = vscode.window.createTerminal({
      name: "Grails",
      cwd: projects[0].rootPath,
    });
    terminal.sendText(`./gradlew ${command}`);
    terminal.show();
  }
}
