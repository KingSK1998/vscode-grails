import type { CodeLensProvider, TextDocument } from "vscode";
import { CodeLens, Range } from "vscode";

/**
 * Provides clickable links (CodeLens) for Grails artifacts.
 * Complements Gutter icons for a full IntelliJ-like experience.
 */
export class GrailsCodeLensProvider implements CodeLensProvider {
  provideCodeLenses(document: TextDocument): CodeLens[] {
    const lenses: CodeLens[] = [];
    const text = document.getText();

    // 1. Controller Actions -> GSP
    if (document.fileName.endsWith("Controller.groovy")) {
      const actionRegex = /(?:def|public)?\s+([a-zA-Z0-9_]+)\s*(?:\([^)]*\)\s*\{|=\s*\{)/g;
      let match;
      while ((match = actionRegex.exec(text)) !== null) {
        const actionName = match[1];
        const startPos = document.positionAt(match.index);
        const range = new Range(startPos, startPos);

        lenses.push(
          new CodeLens(range, {
            title: `$(layout) view`,
            command: "grails.goToView",
            arguments: [document.uri, actionName],
          })
        );
      }
    }

    // 2. Service Injections
    const serviceRegex =
      /(?:def|@Autowired|@Resource)?\s+([a-zA-Z0-9_]+Service)\s+([a-zA-Z0-9_]+)(?:\s|=|;)/g;
    let serviceMatch;
    while ((serviceMatch = serviceRegex.exec(text)) !== null) {
      const serviceType = serviceMatch[1];
      const startPos = document.positionAt(serviceMatch.index);
      const range = new Range(startPos, startPos);

      lenses.push(
        new CodeLens(range, {
          title: `$(gear) implementation`,
          command: "grails.goToService",
          arguments: [serviceType],
        })
      );
    }

    // 3. GSP -> Controller
    if (document.fileName.endsWith(".gsp")) {
      const controllerName = this.getControllerNameFromPath(document.fileName);
      if (controllerName) {
        lenses.push(
          new CodeLens(new Range(0, 0, 0, 0), {
            title: `$(file-code) controller`,
            command: "grails.goToController",
            arguments: [controllerName],
          })
        );
      }
    }

    return lenses;
  }

  private getControllerNameFromPath(filePath: string): string | undefined {
    // .../grails-app/views/user/index.gsp -> user
    const parts = filePath.split(/[\\/]/);
    const viewsIndex = parts.indexOf("views");
    if (viewsIndex !== -1 && viewsIndex < parts.length - 1) {
      return parts[viewsIndex + 1];
    }
    return undefined;
  }
}
