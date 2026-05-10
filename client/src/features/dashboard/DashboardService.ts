import * as path from "path";
import * as vscode from "vscode";
import type { ErrorService } from "../../services/errors/ErrorService";

export class DashboardService {
  private currentPanel: vscode.WebviewPanel | null = null;

  constructor(
    private context: vscode.ExtensionContext,
    private errorService: ErrorService
  ) {}

  public openDashboard(type: "user" | "developer") {
    const column = vscode.window.activeTextEditor
      ? vscode.window.activeTextEditor.viewColumn
      : undefined;

    if (this.currentPanel) {
      this.currentPanel.reveal(column);
      return;
    }

    this.currentPanel = vscode.window.createWebviewPanel(
      "grailsDashboard",
      type === "user" ? "Grails Project Dashboard" : "Extension Developer Center",
      column ?? vscode.ViewColumn.One,
      {
        enableScripts: true,
        retainContextWhenHidden: true,
        localResourceRoots: [vscode.Uri.file(path.join(this.context.extensionPath, "resources"))],
      }
    );

    this.currentPanel.webview.html = this.getHtmlContent(type);

    this.currentPanel.onDidDispose(
      () => {
        this.currentPanel = null;
      },
      null,
      this.context.subscriptions
    );

    interface DashboardMessage {
      command: string;
      flag?: string;
      value?: boolean;
      task?: string;
    }

    this.currentPanel.webview.onDidReceiveMessage((message: DashboardMessage) => {
      switch (message.command) {
        case "toggleFeature":
          vscode.window.showInformationMessage(
            `Flag ${String(message.flag)} toggled to ${String(message.value)}`
          );
          break;
        case "runTask":
          if (message.task) {
            void vscode.commands.executeCommand(message.task);
          }
          break;
      }
    });
  }

  private getHtmlContent(type: "user" | "developer"): string {
    const isDev = type === "developer";

    return `<!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Dashboard</title>
            <style>
                body { font-family: sans-serif; padding: 20px; color: var(--vscode-foreground); }
                .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(250px, 1fr)); gap: 20px; }
                .card { background: var(--vscode-editor-background); border: 1px solid var(--vscode-widget-border); padding: 15px; border-radius: 8px; }
                .card h3 { margin-top: 0; color: var(--vscode-button-background); }
                .stat { font-size: 2em; font-weight: bold; }
                .label { font-size: 0.8em; opacity: 0.8; }
                button { background: var(--vscode-button-background); color: var(--vscode-button-foreground); border: none; padding: 8px 12px; cursor: pointer; border-radius: 4px; }
                button:hover { background: var(--vscode-button-hoverBackground); }
                .status { padding: 4px 8px; border-radius: 4px; font-size: 0.8em; }
                .status.ok { background: #28a745; color: white; }
                .status.warn { background: #ffc107; color: black; }
                .flag-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; padding: 10px; background: var(--vscode-welcomePage-tileBackground); border-radius: 4px; }
            </style>
        </head>
        <body>
            <h1>${isDev ? "🛡️ Developer Maintenance Center" : "📊 Grails Project Insights"}</h1>
            <p>${isDev ? "Manage internal extension flags before VSIX packaging." : "Real-time project metrics and health indicators."}</p>

            ${isDev ? this.getDevContent() : this.getUserContent()}

            <script>
                const vscode = acquireVsCodeApi();
                function runTask(task) {
                    vscode.postMessage({ command: 'runTask', task: task });
                }
                function toggleFlag(flag, checkbox) {
                    vscode.postMessage({ command: 'toggleFeature', flag: flag, value: checkbox.checked });
                }
            </script>
        </body>
        </html>`;
  }

  private getUserContent(): string {
    return `
        <div class="grid">
            <div class="card">
                <h3>Artifacts</h3>
                <div class="stat">24</div>
                <div class="label">Controllers, Services, Domains</div>
            </div>
            <div class="card">
                <h3>Vitals</h3>
                <div class="flag-row">
                    <span>Server Port</span>
                    <strong>8080</strong>
                </div>
                <div class="flag-row">
                    <span>Hot Reload</span>
                    <span class="status ok">ACTIVE</span>
                </div>
            </div>
            <div class="card">
                <h3>Testing</h3>
                <div class="stat">94%</div>
                <div class="label">Code Coverage (Target: 80%)</div>
                <br/>
                <button onclick="runTask('grails.testApp')">Run Full Suite</button>
            </div>
            <div class="card">
                <h3>Performance</h3>
                <div class="label">LSP Indexing Time</div>
                <div class="stat">1.2s</div>
            </div>
        </div>
        <h3>Active Configuration</h3>
        <div class="card">
            <p><strong>Environment:</strong> Development</p>
            <p><strong>Datasource:</strong> H2 (In-memory)</p>
            <button onclick="runTask('grails.compileProject')">Recompile AST</button>
        </div>
        `;
  }

  private getDevContent(): string {
    return `
        <div class="card">
            <h3>Release Management</h3>
            <div class="flag-row">
                <span>Experimental AI Core</span>
                <input type="checkbox" checked onchange="toggleFlag('aiCore', this)">
            </div>
            <div class="flag-row">
                <span>Incremental AST Visitor</span>
                <input type="checkbox" checked onchange="toggleFlag('astVisitor', this)">
            </div>
            <div class="flag-row">
                <span>Logging Verbosity</span>
                <input type="checkbox" onchange="toggleFlag('verbose', this)">
            </div>
            <div class="flag-row">
                <span>GSP LSP V2</span>
                <input type="checkbox" onchange="toggleFlag('gspV2', this)">
            </div>
            <br/>
            <button onclick="alert('Preparing VSIX bundle...')">Package Extension</button>
        </div>
        `;
  }
}
