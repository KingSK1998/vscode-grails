import { window, workspace, Uri } from "vscode";
import { LanguageClientOptions } from "vscode-languageclient/node";
import { GrailsConfig } from "../config/GrailsConfig";

/**
 * Normalizes a VS Code Uri to a file URI string suitable for the Grails LSP server.
 * Always returns a 'file://' URI, properly encoded for all platforms.
 */
function normalizeFileUri(uri: Uri): string {
  // VS Code's Uri.toString(true) returns a properly encoded file URI
  return uri.toString();
}

export function getClientOptions(): LanguageClientOptions {
  return {
    documentSelector: [
      { scheme: "file", language: "groovy" },
      { scheme: "file", language: "gsp" },
    ],
    synchronize: {
      fileEvents: [
        workspace.createFileSystemWatcher("**/*.{groovy,gsp}"),
        workspace.createFileSystemWatcher("**/application.yml"),
      ],
    },
    outputChannel: window.createOutputChannel("Grails Framework Support"),
    uriConverters: {
      code2Protocol: normalizeFileUri,
      protocol2Code: Uri.parse,
    },
    initializationOptions: {
      javaHome: GrailsConfig.getJavaHome(),
    },
  };
}
