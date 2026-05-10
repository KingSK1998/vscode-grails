import * as path from "path";
import * as vscode from "vscode";
import { ErrorSeverity, ErrorSource } from "../../services/errors/errorTypes";
import { BaseCommandProvider } from "./BaseCommandProvider";

export class NavigationCommands extends BaseCommandProvider {
  registerCommands(): void {
    this.register("grails.goToView", async (docUri: vscode.Uri, actionName: string) => {
      try {
        const filePath = docUri.fsPath;
        const controllerPart = path
          .basename(filePath)
          .replace("Controller.groovy", "")
          .toLowerCase();
        const rootPath = this.container.projectService.getProjects()[0]?.rootPath;
        if (!rootPath) return;

        const viewPath = path.join(
          rootPath,
          "grails-app",
          "views",
          controllerPart,
          `${actionName}.gsp`
        );
        const viewUri = vscode.Uri.file(viewPath);

        try {
          const doc = await vscode.workspace.openTextDocument(viewUri);
          await vscode.window.showTextDocument(doc);
        } catch {
          vscode.window.showWarningMessage(`View not found: ${controllerPart}/${actionName}.gsp`);
        }
      } catch (error) {
        this.container.errorService.handleError(
          "Navigation error",
          error,
          ErrorSource.Commands,
          ErrorSeverity.Warning
        );
      }
    });

    this.register("grails.goToController", async (controllerName: string) => {
      try {
        const projects = this.container.projectService.getProjects();
        if (projects.length === 0) return;
        const pattern = `**/grails-app/controllers/**/${controllerName.charAt(0).toUpperCase() + controllerName.slice(1)}Controller.groovy`;
        const files = await vscode.workspace.findFiles(pattern, null, 1);
        if (files.length > 0) {
          const doc = await vscode.workspace.openTextDocument(files[0]);
          await vscode.window.showTextDocument(doc);
        } else {
          vscode.window.showWarningMessage(
            `Controller implementation not found: ${controllerName}`
          );
        }
      } catch (error) {
        this.container.errorService.handleError(
          "Navigation error",
          error,
          ErrorSource.Commands,
          ErrorSeverity.Warning
        );
      }
    });

    this.register("grails.goToService", async (serviceName: string) => {
      try {
        const projects = this.container.projectService.getProjects();
        if (projects.length === 0) return;
        const files = await vscode.workspace.findFiles(
          `**/grails-app/services/**/${serviceName}.groovy`,
          null,
          1
        );
        if (files.length > 0) {
          const doc = await vscode.workspace.openTextDocument(files[0]);
          await vscode.window.showTextDocument(doc);
        } else {
          vscode.window.showWarningMessage(`Service implementation not found: ${serviceName}`);
        }
      } catch (error) {
        this.container.errorService.handleError(
          "Navigation error",
          error,
          ErrorSource.Commands,
          ErrorSeverity.Warning
        );
      }
    });

    this.register("grails.goToDomain", async (domainName: string) => {
      try {
        const projects = this.container.projectService.getProjects();
        if (projects.length === 0) return;
        const files = await vscode.workspace.findFiles(
          `**/grails-app/domain/**/${domainName}.groovy`,
          null,
          1
        );
        if (files.length > 0) {
          const doc = await vscode.workspace.openTextDocument(files[0]);
          await vscode.window.showTextDocument(doc);
        } else {
          vscode.window.showWarningMessage(`Domain class not found: ${domainName}`);
        }
      } catch (error) {
        this.container.errorService.handleError(
          "Navigation error",
          error,
          ErrorSource.Commands,
          ErrorSeverity.Warning
        );
      }
    });

    this.register("grails.goToTagLib", async (tagLibName: string) => {
      try {
        const projects = this.container.projectService.getProjects();
        if (projects.length === 0) return;
        const files = await vscode.workspace.findFiles(
          `**/grails-app/taglib/**/${tagLibName}TagLib.groovy`,
          null,
          1
        );
        if (files.length > 0) {
          const doc = await vscode.workspace.openTextDocument(files[0]);
          await vscode.window.showTextDocument(doc);
        } else {
          vscode.window.showWarningMessage(`TagLib implementation not found: ${tagLibName}`);
        }
      } catch (error) {
        this.container.errorService.handleError(
          "Navigation error",
          error,
          ErrorSource.Commands,
          ErrorSeverity.Warning
        );
      }
    });
  }
}
