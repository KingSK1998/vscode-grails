import { commands, ExtensionContext, Uri, ViewColumn, WebviewPanel, window } from "vscode";

export class GrailsDashboard {
  private panel: WebviewPanel | undefined;

  public createOrShow(context: ExtensionContext) {
    console.log("📊 Creating/showing Grails dashboard");

    if (this.panel) {
      this.panel.reveal();
      return;
    }

    this.panel = window.createWebviewPanel("grailsDashboard", "Grails Dashboard", ViewColumn.One, {
      enableScripts: true,
      retainContextWhenHidden: true,
      localResourceRoots: [Uri.joinPath(context.extensionUri, "resources")],
    });

    this.panel.webview.html = this.getWebviewContent();

    // Handle dispose
    this.panel.onDidDispose(() => {
      this.panel = undefined;
    });

    // Handle messages from webview
    this.panel.webview.onDidReceiveMessage(
      message => {
        switch (message.command) {
          case "createController":
            // commands.executeCommand("grails.createController");
            window.showInformationMessage("Create Controller clicked!");
            break;
          case "createService":
            // commands.executeCommand("grails.createService");
            window.showInformationMessage("Create Service clicked!");
            break;
          case "createDomain":
            // commands.executeCommand("grails.createDomain");
            window.showInformationMessage("Create Domain clicked!");
            break;
          case "createView":
            // commands.executeCommand("grails.createView");
            window.showInformationMessage("Create View clicked!");
            break;
        }
      },
      undefined,
      context.subscriptions
    );

    console.log("✅ Dashboard created successfully");
  }

  private getWebviewContent(): string {
    return `
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Grails Dashboard</title>
            <style>
                body {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                    padding: 20px;
                    background: var(--vscode-editor-background);
                    color: var(--vscode-foreground);
                }
                .dashboard-header {
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                    margin-bottom: 30px;
                    padding: 20px;
                    background: var(--vscode-editor-background);
                    border: 1px solid var(--vscode-widget-border);
                    border-radius: 16px;
                }
                .project-info h1 {
                    margin: 0;
                    font-size: 24px;
                    font-weight: 600;
                }
                .version-badge {
                    background: linear-gradient(135deg, #48b946, #7bc142);
                    color: white;
                    padding: 6px 16px;
                    border-radius: 50px;
                    font-size: 14px;
                    font-weight: 600;
                    margin-left: 12px;
                }
                .quick-actions {
                    display: grid;
                    gap: 12px;
                    margin-top: 20px;
                }
                .action-btn {
                    background: linear-gradient(135deg, #48b946, #7bc142);
                    border: none;
                    border-radius: 12px;
                    color: white;
                    padding: 16px 20px;
                    font-size: 16px;
                    font-weight: 600;
                    cursor: pointer;
                    transition: all 0.3s ease;
                }
                .action-btn:hover {
                    transform: translateY(-2px);
                    box-shadow: 0 8px 24px rgba(72, 185, 70, 0.3);
                }
            </style>
        </head>
        <body>
            <div class="grails-dashboard">
                <header class="dashboard-header">
                    <div class="project-info">
                        <h1>🚀 Grails Project</h1>
                        <span class="version-badge">Grails 6.2.0</span>
                    </div>
                </header>

                <div class="quick-actions">
                    <button class="action-btn" onclick="createArtifact('controller')">
                        🚀 New Controller
                    </button>
                    <button class="action-btn" onclick="createArtifact('service')">
                        ⚙️ New Service
                    </button>
                    <button class="action-btn" onclick="createArtifact('domain')">
                        🗄️ New Domain
                    </button>
                </div>
            </div>

            <script>
                const vscode = acquireVsCodeApi();

                function createArtifact(type) {
                    vscode.postMessage({
                        command: 'create' + type.charAt(0).toUpperCase() + type.slice(1)
                    });
                }
            </script>
        </body>
        </html>
        `;
  }

  // private getProjectSpecificDashboard(projectType: string): string {
  //   const dashboardContent =
  //     projectType === "grails"
  //       ? `
  //       <div class="grails-hero">
  //           <div class="grails-logo-section">
  //               <img src="${this.grailsLogoUri}" class="grails-logo-large" alt="Grails">
  //               <h1>Grails 7 Project</h1>
  //               <span class="version-badge">Production Ready</span>
  //           </div>
  //           <div class="project-stats">
  //               <div class="stat-card">
  //                   <div class="stat-icon">🚀</div>
  //                   <div class="stat-value">${this.getControllerCount()}</div>
  //                   <div class="stat-label">Controllers</div>
  //               </div>
  //               <div class="stat-card">
  //                   <div class="stat-icon">⚙️</div>
  //                   <div class="stat-value">${this.getServiceCount()}</div>
  //                   <div class="stat-label">Services</div>
  //               </div>
  //               <div class="stat-card">
  //                   <div class="stat-icon">🗄️</div>
  //                   <div class="stat-value">${this.getDomainCount()}</div>
  //                   <div class="stat-label">Domains</div>
  //               </div>
  //           </div>
  //           <div class="quick-actions-grid">
  //               <button class="action-card" onclick="createArtifact('controller')">
  //                   <div class="action-icon">🚀</div>
  //                   <div class="action-title">New Controller</div>
  //                   <div class="action-desc">Create REST endpoints</div>
  //               </button>
  //               <button class="action-card" onclick="createArtifact('service')">
  //                   <div class="action-icon">⚙️</div>
  //                   <div class="action-title">New Service</div>
  //                   <div class="action-desc">Business logic layer</div>
  //               </button>
  //               <button class="action-card" onclick="createArtifact('domain')">
  //                   <div class="action-icon">🗄️</div>
  //                   <div class="action-title">New Domain</div>
  //                   <div class="action-desc">Data model class</div>
  //               </button>
  //           </div>
  //       </div>
  //   `
  //       : `
  //       <div class="groovy-hero">
  //           <div class="groovy-header">
  //               <h1>Groovy Project</h1>
  //               <span class="tech-badge">Gradle Build</span>
  //           </div>
  //           <div class="groovy-actions">
  //               <button class="action-card" onclick="createGroovyClass()">
  //                   <div class="action-icon">📄</div>
  //                   <div class="action-title">New Groovy Class</div>
  //               </button>
  //               <button class="action-card" onclick="runTests()">
  //                   <div class="action-icon">🧪</div>
  //                   <div class="action-title">Run Tests</div>
  //               </button>
  //           </div>
  //       </div>
  //   `;

  //   return dashboardContent;
  // }
}
