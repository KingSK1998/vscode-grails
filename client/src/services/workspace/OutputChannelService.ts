import type { Disposable, ExtensionContext, OutputChannel } from "vscode";
import { window } from "vscode";

/**
 * Centralized output channel service providing separate channels for client and server logs.
 */
export class OutputChannelService implements Disposable {
  private static instance: OutputChannelService | undefined;
  private readonly _clientChannel: OutputChannel;
  private readonly _serverChannel: OutputChannel;

  private constructor() {
    this._clientChannel = window.createOutputChannel("Grails Client");
    this._serverChannel = window.createOutputChannel("Grails Language Server");
  }

  static initialize(context: ExtensionContext): OutputChannelService {
    if (!OutputChannelService.instance) {
      OutputChannelService.instance = new OutputChannelService();
      context.subscriptions.push(OutputChannelService.instance);
    }
    return OutputChannelService.instance;
  }

  static getInstance(): OutputChannelService {
    if (!OutputChannelService.instance) {
      throw new Error("OutputChannelService not initialized. Call initialize() first.");
    }
    return OutputChannelService.instance;
  }

  /** ✅ Public getter for client channel */
  get clientChannel(): OutputChannel {
    return this._clientChannel;
  }

  /** ✅ Public getter for server channel */
  get serverChannel(): OutputChannel {
    return this._serverChannel;
  }

  dispose(): void {
    this._clientChannel.dispose();
    this._serverChannel.dispose();
    OutputChannelService.instance = undefined;
  }
}
