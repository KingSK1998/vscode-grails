import type { Disposable, FileDecoration, FileDecorationProvider, Uri } from "vscode";
import { languages, ThemeColor } from "vscode";

export class DiagnosticDecorationProvider implements FileDecorationProvider, Disposable {
  private errorFiles = new Set<string>();

  constructor() {
    // Listen to LSP diagnostics
    languages.onDidChangeDiagnostics(event => {
      event.uris.forEach(uri => {
        const diagnostics = languages.getDiagnostics(uri);

        // Check if file has package-related errors
        const hasPackageError = diagnostics.some(
          diag =>
            diag.source === "grails-lsp" &&
            (diag.message.toLowerCase().includes("package") || diag.code === "package-mismatch")
        );

        if (hasPackageError) {
          this.errorFiles.add(uri.toString());
        } else {
          this.errorFiles.delete(uri.toString());
        }
      });
    });
  }

  provideFileDecoration(uri: Uri): FileDecoration | undefined {
    if (this.errorFiles.has(uri.toString())) {
      return {
        badge: "!",
        tooltip: "Package structure issues detected",
        color: new ThemeColor("errorForeground"),
      };
    }
    return undefined;
  }

  dispose() {
    this.errorFiles.clear();
  }
}
