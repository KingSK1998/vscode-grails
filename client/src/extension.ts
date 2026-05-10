import type { ExtensionContext } from "vscode";
import { ServiceContainer } from "./core/container/ServiceContainer";
import { ActivationManager } from "./core/lifecycle/ActivationManager";
import { ErrorSeverity, ErrorSource } from "./services/errors/errorTypes";
import { OutputChannelService } from "./services/workspace/OutputChannelService";

const EXTENSION_ACTIVATION_TIMER = "🚀 Extension Activation";

let activationManager: ActivationManager | undefined;

/**
 * Extension activation entry point.
 */
export function activate(context: ExtensionContext): void {
  console.log("🚀 Grails extension activating...");
  console.time(EXTENSION_ACTIVATION_TIMER);
  const startTime = performance.now();

  try {
    // Initialize centralized output channel first
    OutputChannelService.initialize(context);

    activationManager = new ActivationManager(context);
    activationManager.activate();

    context.subscriptions.push(activationManager);
    console.log("✅ Grails extension ACTIVATED successfully");
  } catch (error) {
    try {
      ServiceContainer.getInstance().errorService.handleError(
        "Grails extension activation failed",
        error,
        ErrorSource.Extension,
        ErrorSeverity.Error
      );
    } catch {
      console.error("❌ Grails extension activation failed:", error);
    }
    throw error;
  }

  const totalTime = performance.now() - startTime;
  console.timeEnd(EXTENSION_ACTIVATION_TIMER);
  console.log(`📊 Total activation: ${totalTime.toFixed(1)}ms`);

  // Log breakdown
  console.log("📈 Activation breakdown:", {
    serviceInit: "?ms", // We'll measure each phase
    projectDiscovery: "?ms",
    uiSetup: "?ms",
    lspStart: "?ms",
  });
}

/**
 * Extension deactivation cleanup.
 */
export function deactivate(): void {
  console.log("👋 Grails extension deactivating...");

  if (activationManager) {
    activationManager.dispose();
    activationManager = undefined;
  }
}
