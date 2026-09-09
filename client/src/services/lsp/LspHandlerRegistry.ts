import type { Disposable } from "vscode";
import { languages } from "vscode";
import { GrailsCodeActionProvider } from "./handlers/GrailsCodeActionProvider";
import { GrailsCodeLensProvider } from "./handlers/GrailsCodeLensProvider";
import { GspCompletionProvider } from "./handlers/GspCompletionProvider";

export function registerLspHandlers(): Disposable[] {
  const disposables: Disposable[] = [];

  disposables.push(
    languages.registerCompletionItemProvider(
      ["gsp"],
      new GspCompletionProvider(),
      "<",
      '"',
      "'",
      ".",
      ":",
      "/",
      "$"
    )
  );

  disposables.push(
    languages.registerCodeLensProvider(
      [
        { scheme: "file", language: "groovy" },
        { scheme: "file", language: "gsp" },
      ],
      new GrailsCodeLensProvider()
    )
  );

  disposables.push(
    languages.registerCodeActionsProvider(
      { scheme: "file", language: "groovy" },
      new GrailsCodeActionProvider(),
      { providedCodeActionKinds: GrailsCodeActionProvider.providedCodeActionKinds }
    )
  );

  return disposables;
}
