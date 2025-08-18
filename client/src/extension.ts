import * as vscode from "vscode";
import * as fs from "fs";
import * as path from "path";
import { LanguageClient } from "vscode-languageclient/node";
import { ErrorService } from "./services/ErrorService";
import { GradleService } from "./services/gradle-service";
import { StatusBarService } from "./services/StatusBarService";
import { GrailsMessage, ErrorSeverity, ModuleType } from "./utils/constants";
import { LanguageServerManager } from "./language/grails-language-client";
import { registerCommands } from "./commands/grails-commands";
import { GrailsTreeDataProvider } from "./views/grails-tree-view";
import { GrailsDashboard } from "./views/grails-dashboard";

// Global language client and server
let client: LanguageClient | undefined;
let serverLSP: LanguageServerManager | undefined;

// Global core services
let statusBar: StatusBarService;
let errorHandler: ErrorService;
let gradleApi: GradleService;

// TODO: Add support for Grails Debugger

export async function activate(context: vscode.ExtensionContext) {
  console.log("Activating Grails Extension...");

  // Initialize core services once
  statusBar = new StatusBarService(context);
  errorHandler = new ErrorService(statusBar);
  gradleApi = new GradleService(statusBar, errorHandler);

  try {
    const projectFolder = getProjectWorkspace();
    const workspaceRoot = projectFolder.uri.fsPath;

    // Basic project validation - just check if it's a reasonable workspace
    const hasGradleFile = isValidGradleProject(workspaceRoot);
    if (!hasGradleFile) {
      console.log("No build.gradle found, but continuing activation...");
    }

    // Try to initialize Gradle API - if this fails, underlying Gradle has issues
    let gradleAvailable = false;
    try {
      gradleAvailable = await gradleApi.sync();
      if (gradleAvailable) {
        console.log("Gradle API available - full Grails features enabled");
        statusBar.ready(ModuleType.GRADLE, "Gradle integration active");
      }
    } catch (error) {
      console.log("Gradle unavailable - this will affect LSP compilation:", error);
      statusBar.warning(ModuleType.GRADLE, "Gradle issues detected - LSP may have limited functionality");

      // Still start LSP - it might have cached data or work in degraded mode
      console.log("Starting LSP anyway - it may work with cached data or in fallback mode");
    }

    // Initialize language server with Gradle status
    serverLSP = new LanguageServerManager(context, statusBar, errorHandler, gradleAvailable);
    client = await serverLSP.start();
    if (client) {
      await initializeExtensionFeatures(context, client, gradleApi, workspaceRoot, gradleAvailable);
    } else if (!gradleAvailable) {
      // LSP failed and Gradle unavailable - likely build issues
      statusBar.error(ModuleType.SERVER, "LSP startup failed - check Gradle build for errors");
      const action = await vscode.window.showErrorMessage(
        "Grails Language Server failed to start. This is often due to Gradle build issues.",
        "Check Gradle Problems",
        "View Output",
        "Retry",
      );

      if (action === "Check Gradle Problems") {
        vscode.commands.executeCommand("workbench.panel.markers.view.focus");
      } else if (action === "View Output") {
        vscode.commands.executeCommand("workbench.action.output.toggleOutput");
      } else if (action === "Retry") {
        // Retry LSP startup
        client = await serverLSP.start();
        if (client) {
          await initializeExtensionFeatures(context, client, gradleApi, workspaceRoot, gradleAvailable);
        }
      }
    }

    console.log("Grails Extension activated successfully.");
  } catch (error) {
    handleExtensionError(error);
  }
}

function getProjectWorkspace(): vscode.WorkspaceFolder {
  const workspaceFolders = vscode.workspace.workspaceFolders;
  if (!workspaceFolders?.length) {
    throw new Error(GrailsMessage.INVALID_PROJECT);
  }
  return workspaceFolders[0];
}

/** Returns true if the folder is a Gradle project (has build.gradle). */
function isValidGradleProject(folderPath: string): boolean {
  return fs.existsSync(path.join(folderPath, "build.gradle"));
}

async function initializeExtensionFeatures(
  context: vscode.ExtensionContext,
  languageClient: LanguageClient,
  gradleService: GradleService,
  projectRoot: string,
  gradleAvailable: boolean,
): Promise<void> {
  // Register workspace configuration handlers with Gradle status
  registerWorkspaceEventListeners(context, languageClient, gradleAvailable);

  // Register server restart command with Gradle status
  registerServerRestartCommand(context, gradleAvailable);

  // Initialize project explorer with Gradle availability info
  // const projectExplorer = new GrailsExplorerProvider(languageClient, gradleAvailable);
  // context.subscriptions.push(vscode.window.registerTreeDataProvider("grailsExplorer", projectExplorer));

  // Register Grails tree view
  const grailsTreeProvider = new GrailsTreeDataProvider(context);
  vscode.window.registerTreeDataProvider("grailsExplorer", grailsTreeProvider);

  // Register refresh command
  vscode.commands.registerCommand("grails.refreshTree", () => {
    grailsTreeProvider.refresh();
  });

  // Register dashboard command
  const grailsDashboard = new GrailsDashboard();
  vscode.commands.registerCommand("grails.showDashboard", () => {
    grailsDashboard.createOrShow(context);
  });

  // Register artifact creation command
  vscode.commands.registerCommand("grails.createController", async () => {
    const name = await vscode.window.showInputBox({
      prompt: "Enter controller name",
      placeHolder: "UserController",
    });
    if (name) {
      // TODO: Implement controller creation
      await vscode.window.showInformationMessage(`Creating controller: ${name}`);
    }
  });

  vscode.commands.registerCommand("grails.createService", async () => {
    const name = await vscode.window.showInputBox({
      prompt: "Enter service name",
      placeHolder: "UserService",
    });
    if (name) {
      // TODO: Implement service creation
      await vscode.window.showInformationMessage(`Creating service: ${name}`);
    }
  });

  vscode.commands.registerCommand("grails.createDomain", async () => {
    const name = await vscode.window.showInputBox({
      prompt: "Enter domain name",
      placeHolder: "User",
    });
    if (name) {
      // TODO: Implement domain creation
      vscode.window.showInformationMessage(`Creating domain: ${name}`);
    }
  });

  // Register other Grails commands with Gradle availability info
  registerCommands(context, gradleService, projectRoot, gradleAvailable);
}

function registerServerRestartCommand(context: vscode.ExtensionContext, gradleAvailable: boolean): void {
  context.subscriptions.push(
    vscode.commands.registerCommand("grails.restartServer", async () => {
      try {
        if (client && serverLSP) {
          await client.stop();

          client = await serverLSP.start();
          if (client) {
            // Re-register workspace listeners with current Gradle status
            registerWorkspaceEventListeners(context, client, gradleAvailable);
            vscode.window.showInformationMessage(GrailsMessage.SERVER_RESTARTED);
          }
        } else {
          vscode.window.showWarningMessage(GrailsMessage.SERVER_NOT_RUNNING);
        }
      } catch (error) {
        errorHandler.handle(error, ModuleType.SERVER, ErrorSeverity.ERROR);
      }
    }),
  );
}

/**
 * Registers workspace config change listeners and notifies the language server.
 * Only sends notifications when Gradle is working properly.
 */
export function registerWorkspaceEventListeners(
  context: vscode.ExtensionContext,
  client: LanguageClient,
  gradleAvailable: boolean,
): void {
  // Always listen for configuration changes
  context.subscriptions.push(
    vscode.workspace.onDidChangeConfiguration(() => {
      const settings = {
        grails: vscode.workspace.getConfiguration("grails").get(""),
        grailsLsp: vscode.workspace.getConfiguration("grailsLsp").get(""),
      };

      // Only notify server if Gradle is working (server can compile properly)
      if (gradleAvailable) {
        console.log("Sending configuration change to LSP server (Gradle available)");
        client?.sendNotification("workspace/didChangeConfiguration", { settings });
      } else {
        console.log("Gradle unavailable - not sending config change to LSP server");
      }
    }),
  );

  // Listen for Gradle status changes and trigger initial workspace setup
  if (gradleAvailable) {
    console.log("Gradle available - sending initial workspace configuration to LSP server");
    const initialSettings = {
      grails: vscode.workspace.getConfiguration("grails").get(""),
      grailsLsp: vscode.workspace.getConfiguration("grailsLsp").get(""),
    };

    // Send initial configuration to trigger: initialize workspace -> compile project -> server ready
    client?.sendNotification("workspace/didChangeConfiguration", { settings: initialSettings });
  }
}

async function handleExtensionError(error: unknown): Promise<void> {
  errorHandler.handle(error, ModuleType.EXTENSION, ErrorSeverity.FATAL);
  statusBar.error(ModuleType.EXTENSION, GrailsMessage.SERVER_START_FAILED);
}

export async function deactivate(): Promise<void> {
  try {
    if (client) {
      await client.stop();
      client = undefined;
    }
    if (serverLSP) {
      await serverLSP.stop();
      serverLSP = undefined;
    }
    // Clean up core services
    statusBar.dispose();
    // No need to clear references for global singletons
  } catch (error) {
    console.error("Error during extension deactivation:", error);
  }
}

// Check for Grails project (look for grails-app directory)
// const grailsAppPath = require("path").join(workspaceRoot, "grails-app");
// const fs = require("fs");
// if (fs.existsSync(grailsAppPath) && fs.statSync(grailsAppPath).isDirectory()) {
//   // Check if workspace is already configured (Emmet for GSP, launch.json, extensions.json)
//   const vscodeDir = require("path").join(workspaceRoot, ".vscode");
//   let alreadyConfigured = false;
//   try {
//     // Check Emmet config
//     const emmetConfig = vscode.workspace.getConfiguration("emmet");
//     const includeLangs = emmetConfig.get<{ [key: string]: string }>("includeLanguages") || {};
//     const emmetOk = (includeLangs as Record<string, string>)["gsp"] === "html";
//     // Check launch.json
//     const launchPath = require("path").join(vscodeDir, "launch.json");
//     let launchOk = false;
//     if (fs.existsSync(launchPath)) {
//       const launchConfig = JSON.parse(fs.readFileSync(launchPath, "utf8"));
//       launchOk =
//         Array.isArray(launchConfig.configurations) &&
//         launchConfig.configurations.some((c: any) => c.name === "Debug Grails App");
//     }
//     // Check extensions.json
//     const extPath = require("path").join(vscodeDir, "extensions.json");
//     let extOk = false;
//     if (fs.existsSync(extPath)) {
//       const extConfig = JSON.parse(fs.readFileSync(extPath, "utf8"));
//       extOk =
//         Array.isArray(extConfig.recommendations) &&
//         extConfig.recommendations.includes("esbenp.prettier-vscode");
//     }
//     alreadyConfigured = emmetOk && launchOk && extOk;
//   } catch {}
//   // Suggest workspace configuration if not already configured
//   if (!alreadyConfigured) {
//     const configMsg =
//       "Grails project detected! Would you like to configure your workspace for optimal Grails/Groovy development?";
//     const choice = await vscode.window.showInformationMessage(configMsg, "Yes", "No");
//     if (choice === "Yes") {
//       await vscode.commands.executeCommand("grails.setupWorkspace");
//     }
//   } else {
//     vscode.window.showInformationMessage(
//       "Grails workspace is already configured for optimal development.",
//     );
//   }
// }
