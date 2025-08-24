import { window, ViewColumn } from "vscode";

export class GSPTemplateViewer {
  showGSPStructure(gspFile: string): void {
    const panel = window.createWebviewPanel(
      "gspStructure",
      "GSP Template Structure",
      ViewColumn.Two,
      {
        enableScripts: true,
      }
    );

    panel.webview.html = `
    <div class="gsp-structure">
      <div class="gsp-section">
        <h3>📋 Layout Structure</h3>
        <div class="layout-hierarchy">
          <div class="layout-item">
            <span class="layout-name">main.gsp</span>
            <div class="layout-children">
              <span class="current-template">${gspFile}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="gsp-section">
        <h3>🏷️ Available Tags</h3>
        <div class="tag-grid">
          <span class="tag-item">&lt;g:form&gt;</span>
          <span class="tag-item">&lt;g:link&gt;</span>
          <span class="tag-item">&lt;g:each&gt;</span>
          <span class="tag-item">&lt;g:if&gt;</span>
        </div>
      </div>
    </div>`;
  }
}
