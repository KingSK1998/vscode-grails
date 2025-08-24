import { window, workspace, Uri, ExtensionContext, OutputChannel } from "vscode";
import { LanguageClientOptions, RevealOutputChannelOn } from "vscode-languageclient/node";
import { ConfigurationService } from "../workspace/ConfigurationService";

/**
 * Normalizes a VS Code Uri to a file URI string suitable for the Grails LSP server.
 * Always returns a 'file://' URI, properly encoded for all platforms.
 */
function normalizeFileUri(uri: Uri): string {
  // VS Code's Uri.toString(true) returns a properly encoded file URI
  return uri.toString();
}

let grailsOutput: OutputChannel;

export function getClientOptions(
  context: ExtensionContext,
  config: ConfigurationService
): LanguageClientOptions {
  // Create output channel once
  if (!grailsOutput) {
    grailsOutput = window.createOutputChannel("Grails Lanaguage Server");
    context.subscriptions.push(grailsOutput);
  }

  return {
    documentSelector: [
      { scheme: "file", language: "groovy" },
      { scheme: "file", language: "gsp" },
    ],
    synchronize: {
      fileEvents: [
        workspace.createFileSystemWatcher("**/*.{groovy,gsp}"),
        workspace.createFileSystemWatcher("**/build.gradle"),
        workspace.createFileSystemWatcher("**/application.{yml,yaml,properties}"),
      ],
    },
    outputChannel: grailsOutput,
    traceOutputChannel: grailsOutput,
    revealOutputChannelOn: RevealOutputChannelOn.Never,
    uriConverters: {
      code2Protocol: normalizeFileUri,
      protocol2Code: Uri.parse,
    },
    initializationOptions: {
      javaHome: config.javaHome,
      completionDetail: config.completionDetail,
      maxCompletionItems: config.maxCompletionItems,
      cacheEnabled: config.cacheEnabled,
      codeLensEnabled: config.codeLensEnabled,
    },
  };
}
