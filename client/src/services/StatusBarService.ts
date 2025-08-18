import { ModuleType } from "../utils/constants";
import { ExtensionContext, StatusBarAlignment, StatusBarItem, window } from "vscode";
import { GrailsMessage, StatusBarIcon, StatusText } from "../utils/constants";

/**
 * Service to manage the status bar.
 */
export class StatusBarService {
  private readonly statusBarItem: StatusBarItem;

  constructor(private context: ExtensionContext) {
    this.statusBarItem = window.createStatusBarItem(StatusBarAlignment.Left, 100);
    this.context.subscriptions.push(this.statusBarItem);
  }

  /**
   * Update the status bar with the given state.
   */
  public update(
    icon: StatusBarIcon,
    text: string | StatusText,
    tooltip?: string,
    type: ModuleType = ModuleType.EXTENSION,
    timeoutMs?: number,
  ): void {
    this.statusBarItem.name = ModuleType.GRAILS_FRAMEWORK_SUPPORT;
    this.statusBarItem.text = `${icon} ${text} ${type}`;
    this.statusBarItem.tooltip = tooltip ?? text;
    this.statusBarItem.show();
    if (timeoutMs) {
      setTimeout(() => this.statusBarItem.hide(), timeoutMs);
    }
  }

  private setState(icon: StatusBarIcon, status: StatusText, type: ModuleType, tooltip: string): void {
    this.update(icon, status, tooltip, type);
  }

  /** Set the bar to "Ready" with rocket icon. */
  public ready(
    type: ModuleType = ModuleType.EXTENSION,
    tooltip: string = GrailsMessage.EXTENSION_READY,
  ): void {
    this.setState(StatusBarIcon.ROCKET, StatusText.READY, type, tooltip);
  }

  public custom(
    icon: StatusBarIcon,
    text: string,
    tooltip?: string,
    type: ModuleType = ModuleType.PROJECT,
  ): void {
    this.update(icon, text, tooltip, type);
  }

  /** Set the bar to spinning "Syncing" state. */
  public sync(
    type: ModuleType = ModuleType.EXTENSION,
    tooltip: string = GrailsMessage.EXTENSION_SYNCING,
  ): void {
    this.update(StatusBarIcon.SYNC_SPIN, StatusText.SYNC, tooltip, type);
  }

  public success(
    type: ModuleType = ModuleType.EXTENSION,
    tooltip: string = GrailsMessage.SERVER_STARTED,
  ): void {
    this.update(StatusBarIcon.SUCCESS, StatusText.SUCCESS, tooltip, type);
  }

  /** Set error icon and message. */
  public error(
    type: ModuleType = ModuleType.EXTENSION,
    tooltip: string = GrailsMessage.SERVER_START_FAILED,
  ): void {
    this.update(StatusBarIcon.ERROR, StatusText.ERROR, tooltip, type);
  }

  /** Set warning icon and message. */
  public warning(
    type: ModuleType = ModuleType.EXTENSION,
    tooltip: string = GrailsMessage.INVALID_PROJECT,
  ): void {
    this.update(StatusBarIcon.WARNING, StatusText.WARNING, tooltip, type);
  }

  /** Set info icon and message. */
  public info(
    type: ModuleType = ModuleType.EXTENSION,
    tooltip: string = GrailsMessage.EXTENSION_STARTUP,
  ): void {
    this.update(StatusBarIcon.INFO, StatusText.INFO, tooltip, type);
  }

  public restart(
    type: ModuleType = ModuleType.SERVER,
    tooltip: string = GrailsMessage.SERVER_RESTARTED,
  ): void {
    this.update(StatusBarIcon.SYNC_SPIN, StatusText.RESTARTING, tooltip, type);
  }

  public reset(): void {
    this.statusBarItem.hide();
  }

  /** Clean up the status bar item. */
  public dispose(): void {
    this.statusBarItem.dispose();
  }
}
