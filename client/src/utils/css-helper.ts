import { ExtensionContext, Uri, WebviewPanel } from "vscode";

export class CSSHelper {
  static getStylesheetUri(context: ExtensionContext, fileName: string): Uri {
    return Uri.joinPath(context.extensionUri, "resources", "styles", fileName);
  }

  static getWebViewStylesheet(panel: WebviewPanel, context: ExtensionContext, fileName: string): Uri {
    return panel.webview.asWebviewUri(this.getStylesheetUri(context, fileName));
  }

  static getCommonWebviewHead(panel: WebviewPanel, context: ExtensionContext): string {
    const variablesUri = this.getWebViewStylesheet(panel, context, "grails-variables.css");
    const componentsUri = this.getWebViewStylesheet(panel, context, "grails-components.css");
    const themeUri = this.getWebViewStylesheet(panel, context, "grails-theme.css");

    return `
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src ${panel.webview.cspSource} 'unsafe-inline'; script-src ${panel.webview.cspSource} 'unsafe-inline';">
    <link href="${variablesUri}" rel="stylesheet">
    <link href="${componentsUri}" rel="stylesheet">
    <link href="${themeUri}" rel="stylesheet">
    `;
  }
}
