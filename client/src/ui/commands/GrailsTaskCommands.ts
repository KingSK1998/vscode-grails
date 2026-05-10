import * as vscode from "vscode";
import { DependencyGraphService } from "../../features/dependency-graph/DependencyGraphService";
import { GormSqlPreviewService } from "../../features/gorm-sql-preview/GormSqlPreviewService";
import { ErrorSeverity, ErrorSource } from "../../services/errors/errorTypes";
import { BaseCommandProvider } from "./BaseCommandProvider";

export class GrailsTaskCommands extends BaseCommandProvider {
  private dependencyGraphService: DependencyGraphService | null = null;
  private gormSqlPreviewService: GormSqlPreviewService | null = null;

  registerCommands(): void {
    this.register("grails.runApp", async () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        vscode.window.showWarningMessage("No Grails projects found");
        return;
      }
      await this.container.gradleService.runGrailsApp(projects[0]);
    });

    this.register("grails.debugApp", async () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        vscode.window.showWarningMessage("No Grails projects found");
        return;
      }
      await this.container.debugService.debugGrailsApp(projects[0]);
    });

    this.register("grails.showDependencyGraph", () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        vscode.window.showWarningMessage("No Grails projects found");
        return;
      }
      this.dependencyGraphService ??= new DependencyGraphService(
        this.context,
        this.container.errorService,
        this.container.languageServerManager
      );
      this.dependencyGraphService.openGraph(projects[0]);
    });

    this.register("grails.showGormSqlPreview", (uri?: vscode.Uri) => {
      const targetUri = uri ?? vscode.window.activeTextEditor?.document.uri;
      if (!targetUri) return;
      this.gormSqlPreviewService ??= new GormSqlPreviewService(
        this.context,
        this.container.errorService,
        this.container.languageServerManager
      );
      this.gormSqlPreviewService.openPreview(targetUri);
    });

    this.register("grails.testApp", async () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        vscode.window.showWarningMessage("No Grails projects found");
        return;
      }
      await this.container.gradleService.testGrailsApp(projects[0]);
    });

    this.register("grails.buildProject", async () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        vscode.window.showWarningMessage("No Grails projects found");
        return;
      }
      await this.container.gradleService.buildProject(projects[0]);
    });

    this.register("grails.cleanProject", async () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        vscode.window.showWarningMessage("No Grails projects found");
        return;
      }
      await this.container.gradleService.cleanProject(projects[0]);
    });

    this.register("grails.compileProject", async () => {
      const projects = this.container.projectService.getProjects();
      if (projects.length === 0) {
        vscode.window.showWarningMessage("No Grails projects found");
        return;
      }
      await this.container.gradleService.buildProject(projects[0], "compileGroovy");
    });

    this.register("grails.generateAction", async (uri: string, actionName: string) => {
      try {
        const editor = vscode.window.activeTextEditor;
        if (!editor || editor.document.uri.toString() !== uri) return;
        const text = editor.document.getText();
        const lastBraceIndex = text.lastIndexOf("}");
        if (lastBraceIndex !== -1) {
          const edit = new vscode.WorkspaceEdit();
          const pos = editor.document.positionAt(lastBraceIndex);
          edit.insert(
            editor.document.uri,
            pos,
            `\n    def ${actionName}() {\n        render "Hello from ${actionName}"\n    }\n`
          );
          await vscode.workspace.applyEdit(edit);
          vscode.window.showInformationMessage(`Action ${actionName} generated`);
        }
      } catch (error) {
        this.container.errorService.handleError(
          "Action generation error",
          error,
          ErrorSource.Commands,
          ErrorSeverity.Error
        );
      }
    });
  }

  override dispose(): void {
    if (this.dependencyGraphService) {
      this.dependencyGraphService.dispose();
      this.dependencyGraphService = null;
    }
    if (this.gormSqlPreviewService) {
      this.gormSqlPreviewService.dispose();
      this.gormSqlPreviewService = null;
    }
    super.dispose();
  }
}