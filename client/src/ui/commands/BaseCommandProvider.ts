import type { Disposable, ExtensionContext } from "vscode";
import * as vscode from "vscode";
import type { ServiceContainer } from "../../core/container/ServiceContainer";

export abstract class BaseCommandProvider implements Disposable {
  protected disposables: Disposable[] = [];

  constructor(
    protected readonly context: ExtensionContext,
    protected readonly container: ServiceContainer
  ) {}

  protected register(
    command: string,
    callback: (
      ...args: // eslint-disable-next-line @typescript-eslint/no-explicit-any
      any[]
    ) => unknown
  ): void {
    const disposable = vscode.commands.registerCommand(command, callback);
    this.disposables.push(disposable);
    this.context.subscriptions.push(disposable);
  }

  dispose(): void {
    this.disposables.forEach(d => void d.dispose());
    this.disposables = [];
  }

  abstract registerCommands(): void;
}
