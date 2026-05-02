import * as vscode from "vscode";
import { ServiceContainer } from "../../core/container/ServiceContainer";

export class GormSqlPreviewService {
  private currentPanel: vscode.WebviewPanel | null = null;
  private container: ServiceContainer = ServiceContainer.getInstance();

  constructor(private context: vscode.ExtensionContext) {}

  public openPreview(uri: vscode.Uri) {
    if (this.currentPanel) {
      this.currentPanel.reveal(vscode.ViewColumn.Beside);
      void this.refreshPreview(uri);
      return;
    }

    this.currentPanel = vscode.window.createWebviewPanel(
      "gormSqlPreview",
      "GORM SQL Preview",
      vscode.ViewColumn.Beside,
      {
        enableScripts: true,
        retainContextWhenHidden: true,
      }
    );

    this.currentPanel.webview.html = this.getHtmlContent();

    this.currentPanel.onDidDispose(
      () => {
        this.currentPanel = null;
      },
      null,
      this.context.subscriptions
    );

    void this.refreshPreview(uri);
  }

  private async refreshPreview(uri: vscode.Uri) {
    if (!this.currentPanel) {
      return;
    }

    try {
      const client = this.container.languageServerManager.languageClient;
      if (!client) {
        vscode.window.showErrorMessage("Language server not running");
        return;
      }

      const sql = await client.sendRequest("workspace/executeCommand", {
        command: "grails.getGormSql",
        arguments: [uri.toString()],
      });

      this.currentPanel.webview.postMessage({
        command: "updateSql",
        sql: sql ?? "-- No SQL generated.",
      });
    } catch (error) {
      vscode.window.showErrorMessage(`Failed to generate SQL preview: ${String(error)}`);
    }
  }

  private getHtmlContent(): string {
    return `<!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>GORM SQL Preview</title>
            <style>
                body { margin: 0; padding: 20px; background-color: var(--vscode-editor-background); color: var(--vscode-editor-foreground); font-family: var(--vscode-editor-font-family); font-size: var(--vscode-editor-font-size); }
                pre { background: var(--vscode-textCodeBlock-background); padding: 15px; border-radius: 8px; border: 1px solid var(--vscode-widget-border); overflow-auto; }
                .keyword { color: #569cd6; }
                .type { color: #4ec9b0; }
                .comment { color: #6a9955; }
            </style>
        </head>
        <body>
            <h3>GORM SQL DDL Preview</h3>
            <pre id="sqlOutput">-- Loading preview...</pre>

            <script>
                window.addEventListener('message', event => {
                    const message = event.data;
                    if (message.command === 'updateSql') {
                        const output = document.getElementById('sqlOutput');
                        output.textContent = message.sql;
                        highlight(output);
                    }
                });

                function highlight(el) {
                    let text = el.textContent;
                    text = text.replace(/(\\/\\*[\\s\\S]*?\\*\\/|--.*)/g, '<span class="comment">$1</span>');
                    text = text.replace(/\b(CREATE|TABLE|NOT|NULL|PRIMARY|KEY|AUTO_INCREMENT|BIGINT|INT|VARCHAR|BOOLEAN|DATE|TIMESTAMP|DECIMAL)\b/g, '<span class="keyword">$1</span>');
                    el.innerHTML = text;
                }
            </script>
        </body>
        </html>`;
  }
}
