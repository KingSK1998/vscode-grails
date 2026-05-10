import * as vscode from "vscode";
import type { ProjectInfo } from "../../features/models/modelTypes";
import type { ErrorService } from "../errors/ErrorService";
import { ErrorSeverity, ErrorSource } from "../errors/errorTypes";
import type { GradleService } from "../gradle/GradleService";

export class DebugService {
  constructor(
    private gradleService: GradleService,
    private errorService: ErrorService
  ) {}

  async debugGrailsApp(projectInfo: ProjectInfo): Promise<boolean> {
    // Run the bootRun task with JVM debugging enabled
    // Grails uses Gradle's bootRun task. Adding --debug-jvm tells the JVM to wait for a
    // debugger attachment on port 5005.

    vscode.window.showInformationMessage("Starting Grails application in debug mode...");

    // Start the process but don't await because it runs continuously
    this.gradleService.runTask(projectInfo, "bootRun", ["--debug-jvm"]).catch(err => {
      this.errorService.handleError(
        "bootRun failed",
        err,
        ErrorSource.GradleService,
        ErrorSeverity.Error
      );
    });

    // Wait a few seconds for the JVM process to bind to port 5005 before attaching
    await new Promise(resolve => setTimeout(resolve, 8000));

    try {
      // Attach VS Code's Java debugger
      const success = await vscode.debug.startDebugging(vscode.workspace.workspaceFolders?.[0], {
        type: "java",
        name: "Debug Grails (Attach)",
        request: "attach",
        hostName: "localhost",
        port: 5005,
      });

      if (success) {
        vscode.window.showInformationMessage(
          "Debugger successfully attached to Grails application."
        );
      } else {
        vscode.window.showErrorMessage(
          "Failed to attach the Java debugger. Ensure 'Language Support for Java' extension is installed."
        );
      }
      return success;
    } catch (error) {
      vscode.window.showErrorMessage(`Failed to start debugging: ${String(error)}`);
      return false;
    }
  }
}
