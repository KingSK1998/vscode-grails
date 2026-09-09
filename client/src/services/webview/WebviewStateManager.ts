import type { ExtensionContext, Uri } from "vscode";

export function createWebviewStateManager<T>(context: ExtensionContext, stateKey: string) {
  const key = `webview.${stateKey}`;

  return {
    getState(): T | undefined {
      return context.globalState.get<T>(key);
    },

    async setState(state: T): Promise<void> {
      await context.globalState.update(key, state);
    },

    async clearState(): Promise<void> {
      await context.globalState.update(key, undefined);
    },

    getWebviewOptions(localResourceRoots?: Uri[]): {
      enableScripts: boolean;
      retainContextWhenHidden: false;
      localResourceRoots?: readonly Uri[];
    } {
      const options: {
        enableScripts: boolean;
        retainContextWhenHidden: false;
        localResourceRoots?: readonly Uri[];
      } = {
        enableScripts: true,
        retainContextWhenHidden: false,
      };
      if (localResourceRoots) {
        options.localResourceRoots = localResourceRoots;
      }
      return options;
    },
  };
}
