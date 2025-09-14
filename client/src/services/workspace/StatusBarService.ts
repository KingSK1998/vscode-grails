import type { Disposable, ExtensionContext, StatusBarItem } from "vscode";
import { StatusBarAlignment, window } from "vscode";
import { ProjectType } from "../../features/models/modelTypes";
import { EXTENSION_NAME, Messages } from "../../utils/constants";
import { StatusBarIcon, StatusText } from "./statusBarTypes";

/**
 * Thin wrapper around a single left-aligned StatusBarItem.
 */
export class StatusBarService implements Disposable {
  private item: StatusBarItem;
  private isDisposed = false;

  constructor(ctx: ExtensionContext) {
    console.log("🔧 Creating status bar item...");

    // Right alignment for professional appearance
    this.item = window.createStatusBarItem(StatusBarAlignment.Right, 1000);
    this.item.command = "grails.statusBarClicked"; // Make it clickable

    ctx.subscriptions.push(this.item);
    this.item.show();
  }

  /**
   * Central update method - all status changes go through here
   * @param icon - {@link StatusBarIcon} enum value
   * @param text - {@link StatusText} enum value (concise state)
   * @param tooltip - Detailed information for hover
   * @param timeoutMs - Auto-return to ready state after timeout
   */
  private update(
    icon: StatusBarIcon,
    text: StatusText | string,
    tooltip = String(text),
    timeoutMs?: number
  ): void {
    if (this.isDisposed) {
      console.warn("⚠️ Attempted to update disposed status bar");
      return;
    }

    this.item.name = EXTENSION_NAME;
    this.item.text = `Grails: ${icon} ${text}`;
    this.item.tooltip = `Grails: ${tooltip}`;
    this.item.show();

    if (timeoutMs) {
      setTimeout(() => {
        if (!this.isDisposed) {
          this.ready(); // Return to ready state
        }
      }, timeoutMs);
    }
  }

  /* ---------- Lifecycle States ---------------------------------------- */

  starting(tooltip: string = Messages.EXTENSION_STARTING): void {
    this.update(StatusBarIcon.SYNC_SPIN, StatusText.STARTING, tooltip);
  }

  ready(tooltip: string = Messages.EXTENSION_READY): void {
    this.update(StatusBarIcon.ROCKET, StatusText.READY, tooltip);
  }

  running(tooltip: string = Messages.EXTENSION_RUNNING): void {
    this.update(StatusBarIcon.PLAY, StatusText.RUNNING, tooltip);
  }

  stopped(tooltip: string = Messages.EXTENSION_STOPPED): void {
    this.update(StatusBarIcon.EMPTY, StatusText.STOPPED, tooltip);
  }

  restarting(tooltip: string = Messages.EXTENSION_RESTARTING): void {
    this.update(StatusBarIcon.SYNC_SPIN, StatusText.RESTARTING, tooltip);
  }

  /* ---------- Operation States -------------------------------------- */

  sync(tooltip: string = Messages.GRADLE_SYNC_START, timeoutMs?: number): void {
    this.update(StatusBarIcon.SYNC_SPIN, StatusText.SYNC, tooltip, timeoutMs);
  }

  testing(tooltip: string = Messages.TESTING, timeoutMs?: number): void {
    this.update(StatusBarIcon.TEST, StatusText.TESTING, tooltip, timeoutMs);
  }

  /* ---------- Status Indicators -------------------------------------- */

  info(tooltip: string = Messages.INFO): void {
    this.update(StatusBarIcon.INFO, StatusText.INFO, tooltip);
  }

  success(tooltip: string = Messages.SUCCESS, timeoutMs = 3000): void {
    this.update(StatusBarIcon.SUCCESS, StatusText.SUCCESS, tooltip, timeoutMs);
  }

  warning(tooltip: string = Messages.WARNING): void {
    this.update(StatusBarIcon.WARNING, StatusText.WARNING, tooltip);
  }

  error(tooltip: string = Messages.ERROR): void {
    this.update(StatusBarIcon.ERROR, StatusText.ERROR, tooltip);
  }

  /* ---------- Server Connection States ---------------------------- */

  connecting(tooltip: string = Messages.SERVER_CONNECTING): void {
    this.update(StatusBarIcon.SYNC_SPIN, StatusText.CONNECTING, tooltip);
  }

  disconnected(tooltip: string = Messages.SERVER_DISCONNECTED): void {
    this.update(StatusBarIcon.DISCONNECTED, StatusText.DISCONNECTED, tooltip);
  }

  /* ---------- Advanced Context-Aware Methods --------------------- */

  /**
   * Project-aware ready state with project name in tooltip
   */
  projectReady(projectName: string, projectType: ProjectType = ProjectType.Grails): void {
    this.update(
      StatusBarIcon.ROCKET,
      StatusText.READY,
      `${projectType} project "${projectName}" ready - Click for project info`
    );
  }

  /**
   * Server running with port information
   */
  serverRunning(port?: number, projectName?: string): void {
    const portInfo = port ? ` on port ${port}` : "";
    const projectInfo = projectName ? ` (${projectName})` : "";
    this.update(
      StatusBarIcon.PLAY,
      StatusText.RUNNING,
      `Application running${portInfo}${projectInfo} - Click to stop`
    );
  }

  /**
   * Gradle task execution with task name
   */
  gradleTask(taskName: string, timeoutMs = 5000): void {
    this.update(
      StatusBarIcon.GRADLE,
      StatusText.RUNNING,
      `Executing Gradle task: ${taskName}`,
      timeoutMs
    );
  }

  /**
   * Build from LSP with context
   */
  building(context = "project", timeoutMs?: number): void {
    this.update(
      StatusBarIcon.BUILD,
      "Building",
      `Building ${context} - Compilation in progress`,
      timeoutMs
    );
  }

  /* ---------- Utility Methods --------------------------------------- */

  /**
   * Custom status for special cases
   */
  custom(icon: StatusBarIcon, text: string, tooltip: string, timeoutMs?: number): void {
    this.update(icon, text, tooltip, timeoutMs);
  }

  /** Clean up */
  dispose(): void {
    console.log("🗑️ Disposing status bar service");
    this.isDisposed = true;
    this.item.dispose();
  }
}
