import * as vscode from "vscode";
import * as fs from "fs";
import * as path from "path";
import { LanguageClient } from "vscode-languageclient/node";
import { ErrorService } from "./services/ErrorService";
import { GradleService } from "./services/gradleService";
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

// Global UI components
let grailsTreeProvider: GrailsTreeDataProvider;
let treeView: vscode.TreeView<any>;

export async function activate(context: vscode.ExtensionContext) {
  console.log("🚀 Grails extension activating...");

  try {
    // ========================================
    // STEP 1: Initialize Core Services First
    // ========================================
    console.log("📦 Initializing core services...");
    statusBar = new StatusBarService(context);
    errorHandler = new ErrorService(statusBar);
    gradleApi = new GradleService(statusBar, errorHandler);

    // ========================================
    // STEP 2: Register UI Components (Tree View, Dashboard)
    // ========================================
    console.log("🌳 Setting up UI components...");
    await setupUIComponents(context);

    // ========================================
    // STEP 3: Register Core Commands (UI-related, always available)
    // ========================================
    console.log("⚙️ Registering core commands...");
    registerCoreCommands(context);

    // ========================================
    // STEP 4: Workspace Detection and Validation
    // ========================================
    console.log("🔍 Detecting workspace type...");
    let projectFolder: vscode.WorkspaceFolder;
    let workspaceRoot: string;
    let hasGradleFile: boolean = false;

    try {
      projectFolder = getProjectWorkspace();
      workspaceRoot = projectFolder.uri.fsPath;
      hasGradleFile = isValidGradleProject(workspaceRoot);

      if (hasGradleFile) {
        console.log("✅ Gradle project detected");
      } else {
        console.log("⚠️ No build.gradle found, but continuing activation...");
      }
    } catch (error) {
      console.log("⚠️ No workspace folder found, extension activated in limited mode");
      vscode.window.showInformationMessage(
        "🎉 Grails Framework Support is active! (Limited mode - no workspace)",
      );
      return; // Exit early if no workspace
    }

    // ========================================
    // STEP 5: Gradle Integration Check
    // ========================================
    console.log("🔧 Checking Gradle integration...");
    let isGradleAvailable = false;

    try {
      isGradleAvailable = await gradleApi.sync();

      if (isGradleAvailable) {
        console.log("✅ Gradle API available - full Grails features enabled");
        statusBar.ready(ModuleType.GRADLE, "Gradle integration active");
      }
    } catch (error) {
      console.log("⚠️ Gradle unavailable - this will affect LSP compilation:", error);
      statusBar.warning(ModuleType.GRADLE, "Gradle issues detected - LSP may have limited functionality");
      console.log("🔄 Starting LSP anyway - it may work with cached data or in fallback mode");
    }

    // ========================================
    // STEP 6: Language Server Initialization
    // ========================================
    console.log("🚀 Starting Language Server...");
    try {
      serverLSP = new LanguageServerManager(context, statusBar, errorHandler, isGradleAvailable);
      // client = await serverLSP.start();

      if (client) {
        console.log("✅ Language Server started successfully");
        await initializeExtensionFeatures(context, client, gradleApi, workspaceRoot, isGradleAvailable);
      } else {
        console.log("⚠️ Language Server failed to start");
        await handleLSPStartupFailure(context, isGradleAvailable);
      }
    } catch (error) {
      console.error("❌ Language Server initialization failed:", error);
      await handleExtensionError(error);
    }

    // ========================================
    // STEP 7: Final Setup
    // ========================================
    console.log("✅ Grails Extension activated successfully");

    // Show appropriate welcome message based on detected features
    const welcomeMessage = getWelcomeMessage(hasGradleFile, isGradleAvailable, !!client);
    vscode.window.showInformationMessage(welcomeMessage);
  } catch (error) {
    console.error("❌ Grails extension activation failed:", error);
    vscode.window.showErrorMessage(`Grails extension failed to activate: ${error}`);
    await handleExtensionError(error);
  }
}

// ========================================
// UI COMPONENTS SETUP
// ========================================
async function setupUIComponents(context: vscode.ExtensionContext): Promise<void> {
  try {
    // Register tree view provider
    grailsTreeProvider = new GrailsTreeDataProvider(context);
    treeView = vscode.window.createTreeView("grailsExplorer", {
      treeDataProvider: grailsTreeProvider,
      showCollapseAll: true,
    });

    context.subscriptions.push(treeView);
    console.log("✅ Tree view registered successfully");
  } catch (error) {
    console.error("❌ Failed to setup UI components:", error);
    throw error;
  }
}

// ========================================
// CORE COMMANDS REGISTRATION (UI-related, always available)
// ========================================
function registerCoreCommands(context: vscode.ExtensionContext): void {
  const coreCommands = [
    // Tree view commands
    vscode.commands.registerCommand("grails.refreshTree", () => {
      console.log("🔄 Refreshing tree view");
      grailsTreeProvider?.refresh();
    }),

    // Dashboard commands
    vscode.commands.registerCommand("grails.showDashboard", () => {
      console.log("📊 Opening dashboard");
      const dashboard = new GrailsDashboard();
      dashboard.createOrShow(context);
    }),

    // Artifact command
    vscode.commands.registerCommand("grails.createController", async () => {
      const name = await vscode.window.showInputBox({
        prompt: "Enter controller name",
        placeHolder: "ControllerName",
      });
      if (name) {
        await vscode.window.showInformationMessage(`Creating controller: ${name}`, "OK");
        // TODO: Implement controller creation logic here
      }
    }),

    vscode.commands.registerCommand("grails.createService", async () => {
      const name = await vscode.window.showInputBox({
        prompt: "Enter service name",
        placeHolder: "UserService",
      });
      if (name) {
        await vscode.window.showInformationMessage(`Creating service: ${name}`);
        // TODO: Implement actual service creation
      }
    }),

    vscode.commands.registerCommand("grails.createDomain", async () => {
      const name = await vscode.window.showInputBox({
        prompt: "Enter domain name",
        placeHolder: "User",
      });
      if (name) {
        await vscode.window.showInformationMessage(`Creating domain: ${name}`);
        // TODO: Implement actual domain creation
      }
    }),
  ];

  // Add all core commands to context subscriptions
  context.subscriptions.push(...coreCommands);
  console.log("✅ Core commands registered successfully.");
}

// ========================================
// EXTENSION FEATURES INITIALIZATION (LSP-dependent)
// ========================================
async function initializeExtensionFeatures(
  context: vscode.ExtensionContext,
  languageClient: LanguageClient,
  gradleService: GradleService,
  projectRoot: string,
  gradleAvailable: boolean,
): Promise<void> {
  try {
    // Register workspace configuration handlers with Gradle status
    registerWorkspaceEventListeners(context, languageClient, gradleAvailable);

    // Register server restart command with Gradle status
    registerServerRestartCommand(context, gradleAvailable);

    // Register other Grails commands with Gradle availability info
    registerCommands(context, gradleService, projectRoot, gradleAvailable);

    console.log("✅ Extension features initialized successfully");
  } catch (error) {
    console.error("❌ Failed to initialize extension features:", error);
    throw error;
  }
}

// ========================================
// SERVER RESTART COMMAND
// ========================================
function registerServerRestartCommand(context: vscode.ExtensionContext, isGradleAvailable: boolean): void {
  const restartCommand = vscode.commands.registerCommand("grails.restartServer", async () => {
    try {
      console.log("🔄 Restarting Language Server...");

      if (client && serverLSP) {
        await client.stop();
        client = await serverLSP.start();

        if (client) {
          // Re-register workspace listeners with current Gradle status
          registerWorkspaceEventListeners(context, client, isGradleAvailable);

          vscode.window.showInformationMessage(GrailsMessage.SERVER_RESTARTED);
          console.log("✅ Language Server restarted successfully");
        }
      } else {
        vscode.window.showWarningMessage(GrailsMessage.SERVER_NOT_RUNNING);
        console.log("⚠️ Language Server is not running");
      }
    } catch (error) {
      console.error("❌ Failed to restart Language Server:", error);
      errorHandler.handle(error, ModuleType.SERVER, ErrorSeverity.ERROR);
    }
  });

  context.subscriptions.push(restartCommand);
}

// ========================================
// WORKSPACE EVENT LISTENERS
// ========================================

/**
 * Registers workspace config change listeners and notifies the language server.
 * Only sends notifications when Gradle is working properly.
 */
export function registerWorkspaceEventListeners(
  context: vscode.ExtensionContext,
  client: LanguageClient,
  isGradleAvailable: boolean,
): void {
  console.log("📡 Registering workspace event listeners...");
  // Always listen for configuration changes
  const configChangeListener = vscode.workspace.onDidChangeConfiguration(() => {
    const settings = {
      grails: vscode.workspace.getConfiguration("grails").get(""),
      grailsLsp: vscode.workspace.getConfiguration("grailsLsp").get(""),
    };

    // Only notify server if Gradle is working (server can compile properly)
    if (isGradleAvailable) {
      console.log("📤 Sending configuration change to LSP server (Gradle available)");
      client?.sendNotification("workspace/didChangeConfiguration", { settings });
    } else {
      console.log("⚠️ Gradle unavailable - not sending config change to LSP server");
    }
  });

  context.subscriptions.push(configChangeListener);

  // Send initial configuration if Gradle is available
  if (isGradleAvailable) {
    console.log("📤 Sending initial workspace configuration to LSP server");
    const initialSettings = {
      grails: vscode.workspace.getConfiguration("grails").get(""),
      grailsLsp: vscode.workspace.getConfiguration("grailsLsp").get(""),
    };

    // Send initial configuration to trigger: initialize workspace -> compile project -> server ready
    client?.sendNotification("workspace/didChangeConfiguration", { settings: initialSettings });
  }
}

// ========================================
// ERROR HANDLING AND UTILITY FUNCTIONS
// ========================================
async function handleLSPStartupFailure(
  context: vscode.ExtensionContext,
  isGradleAvailable: boolean,
): Promise<void> {
  if (!isGradleAvailable) {
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
      if (serverLSP) {
        client = await serverLSP.start();
        if (client) {
          const projectFolder = getProjectWorkspace();
          await initializeExtensionFeatures(
            context,
            client,
            gradleApi,
            projectFolder.uri.fsPath,
            isGradleAvailable,
          );
        }
      }
    }
  }
}

async function handleExtensionError(error: unknown): Promise<void> {
  console.error("🚨 Extension error:", error);
  errorHandler.handle(error, ModuleType.EXTENSION, ErrorSeverity.FATAL);
  statusBar.error(ModuleType.EXTENSION, GrailsMessage.SERVER_START_FAILED);
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

function getWelcomeMessage(hasGradleFile: boolean, isGradleAvailable: boolean, lspRunning: boolean): string {
  if (hasGradleFile && isGradleAvailable && lspRunning) {
    return "🎉 Grails Framework Support is fully active! All features enabled.";
  } else if (hasGradleFile && isGradleAvailable) {
    return "🎉 Grails Framework Support is active! (LSP starting...)";
  } else if (hasGradleFile) {
    return "🎉 Grails Framework Support is active! (Limited features - Gradle issues detected)";
  } else {
    return "🎉 Grails Framework Support is active! (Basic features only - no Gradle project detected)";
  }
}

// ========================================
// DEACTIVATION
// ========================================
export async function deactivate(): Promise<void> {
  console.log("👋 Grails extension deactivating...");

  try {
    // Stop language client
    if (client) {
      console.log("🛑 Stopping Language Client...");
      await client.stop();
      client = undefined;
    }

    // Stop language server
    if (serverLSP) {
      console.log("🛑 Stopping Language Server Manager...");
      await serverLSP.stop();
      serverLSP = undefined;
    }

    // Clean up core services
    if (statusBar) {
      statusBar.dispose();
    }

    console.log("✅ Grails extension deactivated successfully.");
  } catch (error) {
    console.error("❌ Error during extension deactivation:", error);
  }
}
