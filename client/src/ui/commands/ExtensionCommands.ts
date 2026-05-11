import * as vscode from "vscode";
import { BaseCommandProvider } from "./BaseCommandProvider";

export class ExtensionCommands extends BaseCommandProvider {
  registerCommands(): void {
    this.register("grails.restartLanguageServer", async () => {
      await this.container.languageServerManager.restart();
    });

    this.register("grails.syncGradle", async () => {
      const success = await this.container.gradleService.waitForSync();
      if (success) {
        vscode.window.showInformationMessage("Gradle sync completed successfully");
      } else {
        vscode.window.showWarningMessage("Gradle sync completed with warnings");
      }
    });

    this.register("grails.setupWorkspace", async () => {
      await this.setupGrailsWorkspace();
    });

    this.register("grails.diagnoseIssues", () => {
      this.diagnoseExtensionIssues();
    });
  }

  private async setupGrailsWorkspace(): Promise<void> {
    let changed = false;
    const emmetConfig = vscode.workspace.getConfiguration("emmet");
    const includeLangs = emmetConfig.get<Record<string, string>>("includeLanguages") ?? {};

    if (includeLangs["gsp"] !== "html") {
      includeLangs["gsp"] = "html";
      await emmetConfig.update(
        "includeLanguages",
        includeLangs,
        vscode.ConfigurationTarget.Workspace
      );
      changed = true;
    }

    if (changed) {
      vscode.window.showInformationMessage("Grails workspace configured successfully");
    } else {
      vscode.window.showInformationMessage("Grails workspace already configured");
    }
  }

  private diagnoseExtensionIssues(): void {
    const health = this.container.healthCheck();
    if (health.healthy) {
      vscode.window.showInformationMessage("✅ No issues detected");
    } else {
      const issues = health.issues.join("\n• ");
      vscode.window
        .showErrorMessage(`❌ Issues detected:\n• ${issues}`, "Show Details")
        .then((selection: string | undefined) => {
          if (selection === "Show Details") {
            console.log("Detailed health issues:", health.issues);
          }
        });
    }
  }
}
