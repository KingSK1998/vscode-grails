import * as vscode from "vscode";
import { ActivationManager } from "./core/lifecycle/ActivationManager";

let activationManager: ActivationManager | undefined;

/**
 * Extension activation entry point.
 */
export async function activate(context: vscode.ExtensionContext): Promise<void> {
  console.log("🚀 Grails extension activating...");

  try {
    activationManager = new ActivationManager(context);
    await activationManager.activate();
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
