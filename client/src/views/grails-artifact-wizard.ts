import { ExtensionContext, Uri, ViewColumn, WebviewPanel, window } from "vscode";
import { CSSHelper } from "../utils/css-helper";

export class GrailsArtifactWizard {
  createWizard(context: ExtensionContext, artifactType: string): WebviewPanel {
    const panel = window.createWebviewPanel("grailsWizard", `Create ${artifactType}`, ViewColumn.Active, {
      enableScripts: true,
      localResourceRoots: [Uri.joinPath(context.extensionUri, "resources")],
    });

    panel.webview.html = `
        <!DOCTYPE html>
        <html>
        <head>
            ${CSSHelper.getCommonWebviewHead(panel, context)}
            <title>Create ${artifactType}</title>
        </head>
        <body>
            <div class="grails-card">
                <h2>Create New ${artifactType}</h2>
                <input type="text" class="grails-input" placeholder="Enter ${artifactType} name">
                <button class="grails-button">Create ${artifactType}</button>
            </div>
        </body>
        </html>
        `;

    return panel;
  }
}
