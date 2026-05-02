import type { DecorationOptions, TextEditor } from "vscode";
import * as vscode from "vscode";
import { Range, window, workspace } from "vscode";

/**
 * Provides IntelliJ-style gutter icons for Grails artifacts.
 * Currently supports:
 * - Controller Actions -> GSP View links
 * - Service Injections -> Service definition links
 * - Domain Injections -> Domain definition links
 * - TagLib Injections -> TagLib definition links
 */
export class GrailsGutterProvider implements vscode.Disposable {
  private disposables: vscode.Disposable[] = [];
  private decorationTypes = new Map<string, vscode.TextEditorDecorationType>();

  // Data URI SVGs for gutter icons
  private readonly icons = {
    view: "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxNiIgaGVpZ2h0PSIxNiIgdmlld0JveD0iMCAwIDI0IDI0IiBmaWxsPSJub25lIiBzdHJva2U9IiM2MGE1ZmEiIHN0cm9rZS13aWR0aD0iMiIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIiBzdHJva2UtbGluZWpvaW49InJvdW5kIj48cmVjdCB4PSIzIiB5PSIzIiB3aWR0aD0iMTgiIGhlaWdodD0iMTgiIHJ4PSIyIiByeT0iMiIvPjxsaW5lIHgxPSIzIiB5MT0iOSIgeDI9IjIxIiB5Mj0iOSIvPjxsaW5lIHgxPSI5IiB5MT0iMjEiIHgyPSI5IiB5Mj0iOSIvPjwvc3ZnPg==",
    service:
      "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxNiIgaGVpZ2h0PSIxNiIgdmlld0JveD0iMCAwIDI0IDI0IiBmaWxsPSJub25lIiBzdHJva2U9IiNhODU1ZjciIHN0cm9rZS13aWR0aD0iMiIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIiBzdHJva2UtbGluZWpvaW49InJvdW5kIj48cGF0aCBkPSJNMTAgMnY4TDIuNSAxMWwxLjUgMiA0LjUgMi41IDEuNSAzIDIgMi41IDIuNS0yLjUgMS41LTMgNC41LTIuNSAxLjUtMkwxNCAxMFYySDEweiIvPjwvc3ZnPg==",
    domain:
      "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxNiIgaGVpZ2h0PSIxNiIgdmlld0JveD0iMCAwIDI0IDI0IiBmaWxsPSJub25lIiBzdHJva2U9IiMxMGI5ODEiIHN0cm9rZS13aWR0aD0iMiIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIiBzdHJva2UtbGluZWpvaW49InJvdW5kIj48ZWxsaXBzZSBjeD0iMTIiIGN5PSI1IiByeD0iOSIgcnk9IjMiLz48cGF0aCBkPSJNMjEgMTJjMCAxLjY2LTQgMy05IDNzLTktMS4zNC05LTNWNSIvPjxwYXRoIGQ9Ik0yMSA1djE0YzAgMS42Ni00IDMtOSAzcz0tOS0xLjM0LTktM1Y1Ii8+PC9zdmc+",
    controller:
      "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxNiIgaGVpZ2h0PSIxNiIgdmlld0JveD0iMCAwIDI0IDI0IiBmaWxsPSJub25lIiBzdHJva2U9IiNmNTljMDUiIHN0cm9rZS13aWR0aD0iMiIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIiBzdHJva2UtbGluZWpvaW49InJvdW5kIj48Y2lyY2xlIGN4PSIxMiIgY3k9IjEyIiByPSIxMCIvPjxsaW5lIHgxPSIyIiB5MT0iMTIiIHgyPSIyMiIgeTI9IjEyIi8+PHBhdGggZD0iTTEyIDJhMTUuMyAxNS4zIDAgMCAxIDQgMTBhMTUuMyAxNS4zIDAgMCAxLTQgMTBhMTUuMyAxNS4zIDAgMCAxIDQtMTBhMTUuMyAxNS4zIDAgMCAxLTQtMTBaIi8+PC9zdmc+",
    i18n: "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxNiIgaGVpZ2h0PSIxNiIgdmlld0JveD0iMCAwIDI0IDI0IiBmaWxsPSJub25lIiBzdHJva2U9IiNmZmIzM2EiIHN0cm9rZS13aWR0aD0iMiIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIiBzdHJva2UtbGluZWpvaW49InJvdW5kIj48Y2lyY2xlIGN4PSIxMiIgY3k9IjEyIiByPSIxMCIvPjxsaW5lIHgxPSIyIiB5MT0iMTIiIHgyPSIyMiIgeTI9IjEyIi8+PHBhdGggZD0iTTEyIDJhMTUuMyAxNS4zIDAgMCAxIDQgMTBhMTUuMyAxNS4zIDAgMCAxLTQgMTBhMTUuMyAxNS4zIDAgMCAxLTQtMTBhMTUuMyAxNS4zIDAgMCAxIDQtMTBaIi8+PC9zdmc+",
    asset:
      "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxNiIgaGVpZ2h0PSIxNiIgdmlld0JveD0iMCAwIDI0IDI0IiBmaWxsPSJub25lIiBzdHJva2U9IiNlOTFlNjMiIHN0cm9rZS13aWR0aD0iMiIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIiBzdHJva2UtbGluZWpvaW49InJvdW5kIj48cGF0aCBkPSJNMTIgMTljLTMuMzEgMC02LTIuNjktNi02IDAtMi43NiAyLjI0LTVidTIuNS00Ljc1YzAtMy41IDctNC41IDctNC41cyAxIDcgMSA3IDAtOC0xLThjMC0yLjY4IDIuMzIgNC45OCA0LjUgNy43NSAyLTIuNTIuNS02LjI1LjUtNi4yNXMtMSAxMi02IDYgNi01IDEwIDEwYzAgMy4zMS0yLjY5IDYtNiA2eiIvPjwvc3ZnPg==",
  };

  constructor(private readonly context: vscode.ExtensionContext) {
    this.initDecorations();

    // Listen for changes
    this.disposables.push(window.onDidChangeActiveTextEditor(editor => void this.update(editor)));
    this.disposables.push(
      workspace.onDidChangeTextDocument(event => {
        const editor = window.visibleTextEditors.find(e => e.document === event.document);
        if (editor) {
          void this.update(editor);
        }
      })
    );

    // Initial update
    if (window.activeTextEditor) {
      void this.update(window.activeTextEditor);
    }

    // Register internal command for gutter navigation
    this.disposables.push(
      vscode.commands.registerCommand("grails.internal.gutterGoTo", args =>
        this.handleGutterNav(args)
      )
    );
  }

  private initDecorations() {
    Object.entries(this.icons).forEach(([key, icon]) => {
      this.decorationTypes.set(
        key,
        window.createTextEditorDecorationType({
          gutterIconPath: vscode.Uri.parse(icon),
          gutterIconSize: "contain",
        })
      );
    });
  }

  private update(editor: TextEditor | undefined) {
    if (!editor || !this.isGrailsFile(editor.document)) {
      return;
    }

    const text = editor.document.getText();
    const decorations = new Map<string, DecorationOptions[]>();

    // Initialize arrays
    this.decorationTypes.forEach((_, key) => decorations.set(key, []));

    // 1. Detect Controller Actions -> GSP View
    if (editor.document.fileName.endsWith("Controller.groovy")) {
      // Match method declarations AND closure properties: def index() { ... } or def index = { ... }
      const actionRegex = /(?:def|public)?\s+([a-zA-Z0-9_]+)\s*(?:\([^)]*\)\s*\{|=\s*\{)/g;
      let match;
      while ((match = actionRegex.exec(text)) !== null) {
        const actionName = match[1];
        const startPos = editor.document.positionAt(match.index);

        const md = new vscode.MarkdownString(
          `**Grails Controller Action**\n\n[Jump to View: ${actionName}.gsp](command:grails.goToView?${encodeURIComponent(JSON.stringify([editor.document.uri, actionName]))})`
        );
        md.isTrusted = true;

        decorations.get("view")?.push({
          range: new Range(startPos, startPos),
          hoverMessage: md,
        });
      }
    }

    // 2. Detect Service Injections
    const serviceRegex =
      /(?:def|@Autowired|@Resource)?\s+([a-zA-Z0-9_]+Service)\s+([a-zA-Z0-9_]+)(?:\s|=|;)/g;
    let serviceMatch;
    while ((serviceMatch = serviceRegex.exec(text)) !== null) {
      const serviceType = serviceMatch[1];
      const startPos = editor.document.positionAt(serviceMatch.index);

      const md = new vscode.MarkdownString(
        `**Service Injection**\n\n[Jump to Definition: ${serviceType}](command:grails.goToService?${encodeURIComponent(JSON.stringify([serviceType]))})`
      );
      md.isTrusted = true;

      decorations.get("service")?.push({
        range: new Range(startPos, startPos),
        hoverMessage: md,
      });
    }

    // 3. Detect Domain Classes (Simple detection)
    const domainRegex =
      /\b([A-Z][a-zA-Z0-9_]*)\.withCriteria|\b([A-Z][a-zA-Z0-9_]*)\.findBy|\b([A-Z][a-zA-Z0-9_]*)\.findAllBy/g;
    let domainMatch;
    while ((domainMatch = domainRegex.exec(text)) !== null) {
      const domainName = domainMatch[1] || domainMatch[2] || domainMatch[3];
      const startPos = editor.document.positionAt(domainMatch.index);

      const md = new vscode.MarkdownString(
        `**GORM Domain Access**\n\n[Jump to Domain: ${domainName}](command:grails.goToDomain?${encodeURIComponent(JSON.stringify([domainName]))})`
      );
      md.isTrusted = true;

      decorations.get("domain")?.push({
        range: new Range(startPos, startPos),
        hoverMessage: md,
      });
    }

    // 3. Detect I18N
    const i18nRegex = /message\(\s*code:\s*['"]([^'"]+)['"]/g;
    const gI18nRegex = /<g:message\s+code=['"]([^'"]+)['"]/g;
    let i18nMatch;
    while (
      (i18nMatch = i18nRegex.exec(text)) !== null ||
      (i18nMatch = gI18nRegex.exec(text)) !== null
    ) {
      const code = i18nMatch[1];
      const startPos = editor.document.positionAt(i18nMatch.index);
      decorations.get("i18n")?.push({
        range: new Range(startPos, startPos),
        hoverMessage: `I18N Key: ${code}`,
      });
    }

    // 4. Detect Assets
    const assetRegex = /asset\.(?:image|javascript|stylesheet|link)\(\s*src:\s*['"]([^'"]+)['"]/g;
    const gAssetRegex = /<asset:(?:image|javascript|stylesheet|link)\s+src=['"]([^'"]+)['"]/g;
    let assetMatch;
    while (
      (assetMatch = assetRegex.exec(text)) !== null ||
      (assetMatch = gAssetRegex.exec(text)) !== null
    ) {
      const src = assetMatch[1];
      const startPos = editor.document.positionAt(assetMatch.index);
      decorations.get("asset")?.push({
        range: new Range(startPos, startPos),
        hoverMessage: `Asset: ${src}`,
      });
    }

    // Apply decorations
    this.decorationTypes.forEach((type, key) => {
      editor.setDecorations(type, decorations.get(key) ?? []);
    });
  }

  private isGrailsFile(doc: vscode.TextDocument): boolean {
    return (
      doc.languageId === "groovy" || doc.languageId === "gsp" || doc.fileName.includes("grails-app")
    );
  }

  private getControllerName(filePath: string): string {
    const basename = filePath.split(/[\\/]/).pop() ?? "";
    return basename.replace("Controller.groovy", "").toLowerCase();
  }

  private handleGutterNav(args: unknown) {
    // Implementation for navigation when we have more metadata
    console.log("Gutter navigation requested", args);
  }

  dispose() {
    this.decorationTypes.forEach(d => {
      d.dispose();
    });
    this.disposables.forEach(d => {
      d.dispose();
    });
  }
}
