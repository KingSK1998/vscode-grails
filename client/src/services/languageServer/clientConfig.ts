import { Uri, workspace } from "vscode";
import { RevealOutputChannelOn, type LanguageClientOptions } from "vscode-languageclient";
import type { ConfigurationService } from "../workspace/ConfigurationService";
import { OutputChannelService } from "../workspace/OutputChannelService";

/**
 * Normalizes a VS Code Uri to a file URI string suitable for the Grails LSP server.
 * Always returns a 'file://' URI, properly encoded for all platforms.
 */
export function getClientOptions(config: ConfigurationService): LanguageClientOptions {
  const serverOutput = OutputChannelService.getInstance().serverChannel;

  return {
    documentSelector: [
      { scheme: "file", language: "groovy" },
      { scheme: "file", language: "gsp" },
      { scheme: "file", language: "properties" },
    ],
    synchronize: {
      fileEvents: [
        workspace.createFileSystemWatcher("**/*.{groovy,gsp,properties}"),
        workspace.createFileSystemWatcher("**/build.gradle"),
        workspace.createFileSystemWatcher("**/application.{yml,yaml,properties}"),
      ],
    },
    outputChannel: serverOutput,
    traceOutputChannel: serverOutput,
    revealOutputChannelOn: RevealOutputChannelOn.Never,
    uriConverters: {
      code2Protocol: uri => uri.toString(true),
      protocol2Code: value => {
        try {
          const uri = Uri.parse(value);
          if (uri.scheme !== "file") {
            throw new Error(`Unsupported URI scheme: ${uri.scheme}`);
          }
          return uri;
        } catch {
          // Fallback: return an empty file URI or handle error as needed
          return Uri.file("");
        }
      },
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
