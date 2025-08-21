// Grails-specific commands

import {
  ExtensionContext,
  Terminal,
  commands,
  window,
  workspace,
  ConfigurationTarget,
  Uri,
} from "vscode";
import { GrailsConfig } from "../config/GrailsConfig";
import { GradleService } from "../services/gradleService";
import * as fs from "fs";
import * as path from "path";

export class GrailsCommands {
  private readonly grailsConfig: GrailsConfig;
  private grailsTerminal: Terminal | undefined;

  constructor(context: ExtensionContext) {
    this.grailsConfig = new GrailsConfig();
    this.registerCommands(context);
  }

  private registerCommands(context: ExtensionContext) {
    context.subscriptions.push(
      commands.registerCommand("grails.run", () => this.executeGrailsCommand("run-app")),
      commands.registerCommand("grails.test", () => this.executeGrailsCommand("test-app")),
      commands.registerCommand("grails.clean", () => this.executeGrailsCommand("clean")),
      commands.registerCommand("grails.compile", () => this.executeGrailsCommand("compile")),
      // Only minimal, essential Grails commands. For Gradle, use VS Code Gradle extension.
      commands.registerCommand("grails.createArtifact", async () => {
        await this.createArtifactWizard();
      }),
      commands.registerCommand("grails.showConfig", async () => {
        await this.showGrailsConfig();
      }),
      commands.registerCommand("grails.openGrailsApp", async () => {
        await this.openGrailsAppFolder();
      }),
      commands.registerCommand("grails.diagnoseGradle", async () => {
        await this.diagnoseGradleIssues();
      }),
      commands.registerCommand("grails.setupWorkspace", async () => {
        // 1. Enable Emmet for GSP
        const emmetConfig = workspace.getConfiguration("emmet");
        const includeLangs = emmetConfig.get<{ [key: string]: string }>("includeLanguages") || {};
        let changed = false;
        if (includeLangs["gsp"] !== "html") {
          includeLangs["gsp"] = "html";
          await emmetConfig.update("includeLanguages", includeLangs, ConfigurationTarget.Workspace);
          changed = true;
        }
        // 2. Add Grails Run/Debug configuration to launch.json
        const wsFolders = workspace.workspaceFolders;
        if (wsFolders && wsFolders.length > 0) {
          const workspaceRoot = wsFolders[0].uri.fsPath;
          const vscodeDir = path.join(workspaceRoot, ".vscode");
          const launchPath = path.join(vscodeDir, "launch.json");
          let launchConfig: any = { version: "0.2.0", configurations: [] };
          try {
            if (!fs.existsSync(vscodeDir)) fs.mkdirSync(vscodeDir);
            if (fs.existsSync(launchPath)) {
              launchConfig = JSON.parse(fs.readFileSync(launchPath, "utf8"));
            }
          } catch {}
          const grailsConfig = {
            type: "java",
            name: "Debug Grails App",
            request: "launch",
            mainClass: "org.grails.cli.GrailsCli",
            projectName: "${workspaceFolderBasename}",
            args: ["run-app"],
            cwd: "${workspaceFolder}",
          };
          if (!launchConfig.configurations.some((c: any) => c.name === "Debug Grails App")) {
            launchConfig.configurations.push(grailsConfig);
            fs.writeFileSync(launchPath, JSON.stringify(launchConfig, null, 2));
            changed = true;
          }
          // 3. Recommend HTML/CSS/JS extensions (optional, not auto-install)
          const extPath = path.join(workspaceRoot, ".vscode", "extensions.json");
          let extConfig: any = { recommendations: [] };
          try {
            if (fs.existsSync(extPath)) {
              extConfig = JSON.parse(fs.readFileSync(extPath, "utf8"));
            }
          } catch {}
          const recs = [
            "esbenp.prettier-vscode",
            "dbaeumer.vscode-eslint",
            "ecmel.vscode-html-css",
            "xabikos.JavaScriptSnippets",
          ];
          let added = false;
          for (const rec of recs) {
            if (!extConfig.recommendations.includes(rec)) {
              extConfig.recommendations.push(rec);
              added = true;
            }
          }
          if (added) {
            fs.writeFileSync(extPath, JSON.stringify(extConfig, null, 2));
            changed = true;
          }
        }
        if (changed) {
          window.showInformationMessage(
            "Grails workspace setup: Emmet for GSP, run/debug config, and extension recommendations applied."
          );
        } else {
          window.showInformationMessage("Grails workspace already configured.");
        }
      })
    );
  }

  private async executeGrailsCommand(command: string) {
    // Validate configuration
    const configError = GrailsConfig.validateConfig();
    if (configError) {
      const action = await window.showErrorMessage(
        `Grails configuration error: ${configError}`,
        "Open Settings",
        "Set GRAILS_HOME"
      );

      if (action === "Open Settings") {
        commands.executeCommand("workbench.action.openSettings", "grails");
      } else if (action === "Set GRAILS_HOME") {
        const grailsPath = await window.showInputBox({
          prompt: "Enter path to Grails installation",
          placeHolder: "/path/to/grails",
        });
        if (grailsPath) {
          await workspace
            .getConfiguration("grails")
            .update("path", grailsPath, ConfigurationTarget.Workspace);
          window.showInformationMessage("Grails path updated. Please try the command again.");
        }
      }
      return;
    }

    // Validate project structure (but allow override)
    const projectRoot = GrailsConfig.getProjectRoot();
    if (!projectRoot) {
      window.showErrorMessage("No workspace folder found. Please open a project.");
      return;
    }

    // Optional: Warn about non-Grails projects but don't block
    if (!GrailsConfig.isGrailsProjectFolder(projectRoot)) {
      console.log("No grails-app directory found, but continuing...");
    }

    // Create or reuse terminal
    if (!this.grailsTerminal || this.grailsTerminal.exitStatus) {
      this.grailsTerminal = window.createTerminal({
        name: "Grails",
        cwd: projectRoot,
      });
    }

    const grailsPath = GrailsConfig.getGrailsPath();
    const grailsExecutable = process.platform === "win32" ? "grails.bat" : "grails";
    const fullCommand = `"${grailsPath}/bin/${grailsExecutable}" ${command}`;

    this.grailsTerminal.sendText(fullCommand);
    this.grailsTerminal.show();

    // Show progress notification for long-running commands
    if (["run-app", "test-app"].includes(command)) {
      window.showInformationMessage(`Grails: Executing ${command}...`);
    }
  }

  private async createArtifactWizard() {
    // Validate configuration first
    const configError = GrailsConfig.validateConfig();
    if (configError) {
      window.showErrorMessage(`Cannot create artifact: ${configError}`);
      return;
    }

    const projectRoot = GrailsConfig.getProjectRoot();
    if (!projectRoot || !GrailsConfig.isGrailsProjectFolder(projectRoot)) {
      window.showErrorMessage("Please open a Grails project to create artifacts.");
      return;
    }

    // Artifact types with descriptions
    const artifactTypes = [
      { label: "Controller", description: "Handle web requests and responses" },
      { label: "Domain", description: "Domain model classes (GORM entities)" },
      { label: "Service", description: "Business logic services" },
      { label: "TagLib", description: "Custom GSP tags" },
      { label: "Job", description: "Quartz scheduled jobs" },
      { label: "Command", description: "Command objects for data binding" },
      { label: "Interceptor", description: "Request/response interceptors" },
      { label: "Codec", description: "Encoding/decoding utilities" },
      { label: "Filter", description: "Web filters" },
    ];

    const selectedType = await window.showQuickPick(artifactTypes, {
      placeHolder: "Select artifact type to create",
      matchOnDescription: true,
    });

    if (!selectedType) return;

    const name = await window.showInputBox({
      prompt: `Enter ${selectedType.label} name`,
      placeHolder: `e.g., Book${selectedType.label}`,
      validateInput: value => {
        if (!value || value.trim().length === 0) {
          return "Name cannot be empty";
        }
        if (!/^[A-Za-z][A-Za-z0-9]*$/.test(value.trim())) {
          return "Name must start with a letter and contain only letters and numbers";
        }
        return null;
      },
    });

    if (!name) return;

    // Optional package input
    const packageName = await window.showInputBox({
      prompt: `Enter package name (optional)`,
      placeHolder: "com.example.myapp",
      validateInput: value => {
        if (value && value.trim().length > 0) {
          if (!/^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)*$/.test(value.trim())) {
            return "Package name must be lowercase and follow Java package naming conventions";
          }
        }
        return null;
      },
    });

    // Build the command
    const grailsPath = GrailsConfig.getGrailsPath();
    const grailsExecutable = process.platform === "win32" ? "grails.bat" : "grails";
    const artifactCommand = `create-${selectedType.label.toLowerCase()}`;
    const fullName = packageName ? `${packageName}.${name.trim()}` : name.trim();

    const terminal = window.createTerminal({
      name: "Grails Artifact Creator",
      cwd: projectRoot,
    });

    const command = `"${grailsPath}/bin/${grailsExecutable}" ${artifactCommand} ${fullName}`;
    terminal.sendText(command);
    terminal.show();

    window.showInformationMessage(`Creating ${selectedType.label}: ${fullName}`);
  }

  private async showGrailsConfig() {
    const config = GrailsConfig.getAllConfig();
    const configInfo = [
      `**Grails Configuration**`,
      ``,
      `• **Grails Path:** ${config.grailsPath || "Not configured"}`,
      `• **Java Home:** ${config.javaHome || "Using system default"}`,
      `• **Project Root:** ${config.projectRoot || "No workspace"}`,
      `• **Grails Version:** ${config.grailsVersion || "Unknown"}`,
      `• **Is Configured:** ${config.isConfigured ? "✅" : "❌"}`,
      `• **Is Grails Project:** ${config.isGrailsProject ? "✅" : "❌"}`,
      ``,
      `**Available Grails Projects:**`,
      ...GrailsConfig.getGrailsProjects().map(project => `• ${project}`),
    ].join("\n");

    const panel = window.createWebviewPanel(
      "grailsConfig",
      "Grails Configuration",
      window.activeTextEditor?.viewColumn || 1,
      {}
    );

    panel.webview.html = `
      <!DOCTYPE html>
      <html>
      <head>
        <style>
          body { font-family: var(--vscode-font-family); padding: 20px; }
          h1 { color: var(--vscode-foreground); }
          pre { background: var(--vscode-textBlockQuote-background); padding: 10px; border-radius: 4px; }
        </style>
      </head>
      <body>
        <h1>Grails Configuration</h1>
        <pre>${configInfo}</pre>
      </body>
      </html>
    `;
  }

  private async openGrailsAppFolder() {
    const projectRoot = GrailsConfig.getProjectRoot();
    if (!projectRoot) {
      window.showErrorMessage("No workspace folder found.");
      return;
    }

    const grailsAppPath = path.join(projectRoot, "grails-app");
    if (!fs.existsSync(grailsAppPath)) {
      window.showErrorMessage("grails-app directory not found in current workspace.");
      return;
    }

    const uri = Uri.file(grailsAppPath);
    await commands.executeCommand("revealFileInOS", uri);
  }

  private async diagnoseGradleIssues() {
    const projectRoot = GrailsConfig.getProjectRoot();
    if (!projectRoot) {
      window.showErrorMessage("No workspace folder found.");
      return;
    }

    window.showInformationMessage("Running Gradle diagnostics...");

    // Create diagnostic terminal
    const diagnosticTerminal = window.createTerminal({
      name: "Gradle Diagnostics",
      cwd: projectRoot,
    });

    // Run basic Gradle diagnostics
    const hasWrapper = fs.existsSync(
      path.join(projectRoot, process.platform === "win32" ? "gradlew.bat" : "gradlew")
    );
    const gradleCmd = hasWrapper
      ? process.platform === "win32"
        ? "gradlew.bat"
        : "./gradlew"
      : "gradle";

    // Run diagnostic commands
    diagnosticTerminal.sendText(`echo "=== Gradle Diagnostics for Grails Project ==="`);
    diagnosticTerminal.sendText(`echo "Project: ${projectRoot}"`);
    diagnosticTerminal.sendText(`echo ""`);

    diagnosticTerminal.sendText(`echo "1. Checking Gradle version..."`);
    diagnosticTerminal.sendText(`${gradleCmd} --version`);
    diagnosticTerminal.sendText(`echo ""`);

    diagnosticTerminal.sendText(`echo "2. Checking project structure..."`);
    diagnosticTerminal.sendText(`${gradleCmd} projects`);
    diagnosticTerminal.sendText(`echo ""`);

    diagnosticTerminal.sendText(`echo "3. Checking dependencies (this may take a while)..."`);
    diagnosticTerminal.sendText(`${gradleCmd} dependencies --configuration compileClasspath`);
    diagnosticTerminal.sendText(`echo ""`);

    diagnosticTerminal.sendText(`echo "4. Checking for build issues..."`);
    diagnosticTerminal.sendText(`${gradleCmd} compileGroovy --dry-run`);
    diagnosticTerminal.sendText(`echo ""`);

    diagnosticTerminal.sendText(`echo "=== Diagnostics Complete ==="`);
    diagnosticTerminal.sendText(
      `echo "If you see errors above, those are likely causing LSP issues."`
    );
    diagnosticTerminal.sendText(`echo "Common fixes:"`);
    diagnosticTerminal.sendText(`echo "- Run 'gradle clean' to clear build cache"`);
    diagnosticTerminal.sendText(`echo "- Check internet connection for dependency downloads"`);
    diagnosticTerminal.sendText(`echo "- Verify Grails version compatibility in build.gradle"`);
    diagnosticTerminal.sendText(`echo "- Check for syntax errors in build.gradle"`);

    diagnosticTerminal.show();
  }
}

export function registerCommands(
  context: ExtensionContext,
  gradleService: GradleService,
  projectRoot: string,
  gradleAvailable: boolean = false
) {
  // Register enhanced Gradle integration if available
  if (gradleAvailable) {
    context.subscriptions.push(
      commands.registerCommand("grails.runGradleTask", async () => {
        try {
          await gradleService.runGradleTask(projectRoot, "properties");
          window.showInformationMessage("Task 'properties' executed successfully.");
        } catch (error) {
          window.showErrorMessage(`Failed to execute task \"properties\": ${error}`);
        }
      }),
      commands.registerCommand("grails.syncGradle", async () => {
        try {
          const success = await gradleService.sync();
          if (success) {
            window.showInformationMessage("Gradle sync completed successfully.");
          } else {
            window.showWarningMessage("Gradle sync completed with warnings.");
          }
        } catch (error) {
          window.showErrorMessage(`Gradle sync failed: ${error}`);
        }
      })
    );
  }
}
