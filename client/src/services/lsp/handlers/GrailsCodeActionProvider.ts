import type {
  CancellationToken,
  CodeActionContext,
  CodeActionProvider,
  Range,
  Selection,
  TextDocument,
} from "vscode";
import { CodeAction, CodeActionKind, WorkspaceEdit } from "vscode";

/**
 * Provides quick fixes for missing Grails dependencies.
 * If 'userService.doSomething()' is found but 'userService' is not declared,
 * it suggests injecting it: 'def userService'.
 */
export class GrailsCodeActionProvider implements CodeActionProvider {
  public static readonly providedCodeActionKinds = [CodeActionKind.QuickFix];

  provideCodeActions(
    document: TextDocument,
    range: Range | Selection,
    _context: CodeActionContext,
    _token: CancellationToken
  ): CodeAction[] {
    const codeActions: CodeAction[] = [];

    // For now, we perform a naive check even if there are no LSP diagnostics
    // (in case the LSP hasn't caught up or we want to be proactive).
    // However, a better approach is to respond to "unresolved symbol" diagnostics.

    // Let's look at the current line or selection
    const lineText = document.lineAt(range.start.line).text;

    // Simple regex: look for something like 'userService.' or 'userService '
    const serviceUsageRegex = /([a-z][a-zA-Z0-9_]*Service)(?:\.|\s)/g;
    let match;

    while ((match = serviceUsageRegex.exec(lineText)) !== null) {
      const serviceName = match[1];

      // Check if this service name is already declared in the document
      if (!this.isServiceDeclared(document, serviceName)) {
        const action = this.createFix(document, serviceName);
        if (action) {
          codeActions.push(action);
        }
      }
    }

    return codeActions;
  }

  private isServiceDeclared(document: TextDocument, serviceName: string): boolean {
    const text = document.getText();
    // Look for 'def userService', 'UserService userService', etc.
    const declarationRegex = new RegExp(
      `(?:def|${this.capitalize(serviceName)})\\s+${serviceName}\\b`,
      "g"
    );
    return declarationRegex.test(text);
  }

  private createFix(document: TextDocument, serviceName: string): CodeAction | undefined {
    const fix = new CodeAction(`Inject ${serviceName}`, CodeActionKind.QuickFix);
    fix.edit = new WorkspaceEdit();

    // Find the best place to insert: usually after class declaration or before other defs
    const text = document.getText();
    const classMatch = /class\s+[a-zA-Z0-9_]+\s*(?:extends\s+[a-zA-Z0-9_]+\s*)?\{/.exec(text);

    if (classMatch) {
      const insertPos = document.positionAt(classMatch.index + classMatch[0].length);
      // Multi-line insert with indentation
      fix.edit.insert(document.uri, insertPos, `\n    def ${serviceName}`);
      fix.isPreferred = true;
      return fix;
    }

    return undefined;
  }

  private capitalize(s: string): string {
    return s.charAt(0).toUpperCase() + s.slice(1);
  }
}
