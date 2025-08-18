import { commands, ExtensionContext, Uri, ViewColumn, WebviewPanel, window } from "vscode";

export class GrailsDashboard {
  private panel: WebviewPanel | undefined;

  public createOrShow(context: ExtensionContext) {
    if (this.panel) {
      this.panel.reveal();
      return;
    }

    this.panel = window.createWebviewPanel("grailsDashboard", "Grails Dashboard", ViewColumn.One, {
      enableScripts: true,
      retainContextWhenHidden: true,
      localResourceRoots: [Uri.joinPath(context.extensionUri, "resources")],
    });

    this.panel.webview.html = this.getWebviewContent(context);

    this.panel.webview.onDidReceiveMessage(
      (message) => {
        switch (message.command) {
          case "createController":
            commands.executeCommand("grails.createController");
            break;
          case "createService":
            commands.executeCommand("grails.createService");
            break;
          case "createDomain":
            commands.executeCommand("grails.createDomain");
            break;
          case "createView":
            commands.executeCommand("grails.createView");
            break;
        }
      },
      undefined,
      context.subscriptions,
    );
  }

  private getWebviewContent(context: ExtensionContext): string {
    // Get CSS file URI
    const stylesheetUri = this.panel!.webview.asWebviewUri(
      Uri.joinPath(context.extensionUri, "resources", "styles", "grails-theme.css"),
    );

    // Get any custom icons
    const grailsLogoUri = this.panel!.webview.asWebviewUri(
      Uri.joinPath(context.extensionUri, "resources", "icons", "grails-logo.svg"),
    );

    return `
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <link href="${stylesheetUri}" rel="stylesheet">
            <title>Grails Dashboard</title>
        </head>
        <body>
            <div class="grails-project-card">
                <div class="project-header">
                    <div class="grails-logo-container">
                        <img src="${grailsLogoUri}" alt="Grails" class="grails-logo" />
                    </div>
                    <div class="project-info">
                        <h2 class="project-name">My Grails App</h2>
                        <span class="grails-version-badge">Grails 6.2.0</span>
                    </div>
                    <div class="server-status">
                        <div class="status-indicator running"></div>
                        <span>Server Running</span>
                    </div>
                </div>

                <div class="quick-stats">
                    <div class="stat-item">
                        <span class="stat-icon">🚀</span>
                        <span class="stat-number">5</span>
                        <span class="stat-label">Controllers</span>
                    </div>
                    <div class="stat-item">
                        <span class="stat-icon">⚙️</span>
                        <span class="stat-number">3</span>
                        <span class="stat-label">Services</span>
                    </div>
                    <div class="stat-item">
                        <span class="stat-icon">🗄️</span>
                        <span class="stat-number">7</span>
                        <span class="stat-label">Domains</span>
                    </div>
                </div>
            </div>

            <!-- Floating Action Button -->
            <div class="grails-fab-container">
                <button class="grails-fab-trigger" title="Create New Artifact">+</button>
                <div class="grails-fab-menu" hidden>
                    <button class="fab-action" data-command="createController">🚀 Controller</button>
                    <button class="fab-action" data-command="createService">⚙️ Service</button>
                    <button class="fab-action" data-command="createDomain">🗄️ Domain</button>
                    <button class="fab-action" data-command="createView">📄 GSP View</button>
                </div>
            </div>

            <script>
                const vscode = acquireVsCodeApi();
                const fabTrigger = document.querySelector('.grails-fab-trigger');
                const fabMenu = document.querySelector('.grails-fab-menu');

                fabTrigger.onclick = () => {
                    fabMenu.hidden = !fabMenu.hidden;
                };

                fabMenu.querySelectorAll('.fab-action').forEach(btn => {
                    btn.onclick = () => {
                        const command = btn.getAttribute('data-command');
                        vscode.postMessage({ command });
                        fabMenu.hidden = true;
                    };
                });
            </script>
          </body>
        </html>
        `;
  }
}
