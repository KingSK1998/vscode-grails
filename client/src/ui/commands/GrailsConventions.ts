import { ViewColumn, window } from "vscode";

export class GrailsConventions {
  // Show Grails naming conventions overlay
  async showNamingConventions(): Promise<void> {
    const panel = window.createWebviewPanel(
      "grailsConventions",
      "Grails Conventions Helper",
      ViewColumn.Beside,
      { enableScripts: true },
    );

    panel.webview.html = `
    <div class="conventions-helper">
      <div class="convention-card">
        <h3>📁 File Locations</h3>
        <div class="convention-rule">
          <code>UserController.groovy</code>
          <span>→</span>
          <code>grails-app/controllers/</code>
        </div>
        <div class="convention-rule">
          <code>UserService.groovy</code>
          <span>→</span>
          <code>grails-app/services/</code>
        </div>
      </div>

      <div class="convention-card">
        <h3>🔗 URL Mappings</h3>
        <div class="convention-rule">
          <code>UserController.index()</code>
          <span>→</span>
          <code>/user/index</code>
        </div>
      </div>
    </div>`;
  }
}
