import { ExtensionContext } from "vscode";
import { ActivationManager } from "./core/lifecycle/ActivationManager";

let activationManager: ActivationManager | undefined;

/**
 * Extension activation entry point.
 */
export async function activate(context: ExtensionContext): Promise<void> {
  console.log("🚀 Grails extension activating...");

  try {
    activationManager = new ActivationManager(context);
    await activationManager.activate();

    context.subscriptions.push(activationManager);
    console.log("✅ Grails extension ACTIVATED successfully");
  } catch (error) {
    console.error("❌ Grails extension activation failed:", error);
    throw error;
  }
}

/**
 * Extension deactivation cleanup.
 */
export async function deactivate(): Promise<void> {
  console.log("👋 Grails extension deactivating...");

  if (activationManager) {
    activationManager.dispose();
    activationManager = undefined;
  }
}
