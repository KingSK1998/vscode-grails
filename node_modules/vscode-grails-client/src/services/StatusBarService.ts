import { MODULE_TYPE } from "./../utils/Constants";
import { ExtensionContext, StatusBarAlignment, StatusBarItem, window } from "vscode";
import { GRAILS_MESSAGE, STATUS_BAR_ICONS, STATUS_TEXT_MESSAGES } from "../utils/Constants";

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
    icon: STATUS_BAR_ICONS,
    text: string | STATUS_TEXT_MESSAGES,
    tooltip?: string,
    type: MODULE_TYPE = MODULE_TYPE.EXTENSION,
    timeoutMs?: number,
  ): void {
    this.statusBarItem.name = MODULE_TYPE.GRAILS_FRAMEWORK_SUPPORT;
    this.statusBarItem.text = `${icon} ${text} ${type}`;
    this.statusBarItem.tooltip = tooltip ?? text;
    this.statusBarItem.show();
    if (timeoutMs) {
      setTimeout(() => this.statusBarItem.hide(), timeoutMs);
    }
  }

  private setState(
    icon: STATUS_BAR_ICONS,
    status: STATUS_TEXT_MESSAGES,
    type: MODULE_TYPE,
    tooltip: string,
  ): void {
    this.update(icon, status, tooltip, type);
  }

  /** Set the bar to "Ready" with rocket icon. */
  public ready(
    type: MODULE_TYPE = MODULE_TYPE.EXTENSION,
    tooltip: string = GRAILS_MESSAGE.EXTENSION_READY,
  ): void {
    this.setState(STATUS_BAR_ICONS.ROCKET, STATUS_TEXT_MESSAGES.READY, type, tooltip);
  }

  public custom(
    icon: STATUS_BAR_ICONS,
    text: string,
    tooltip?: string,
    type: MODULE_TYPE = MODULE_TYPE.PROJECT,
  ): void {
    this.update(icon, text, tooltip, type);
  }

  /** Set the bar to spinning "Syncing" state. */
  public sync(
    type: MODULE_TYPE = MODULE_TYPE.EXTENSION,
    tooltip: string = GRAILS_MESSAGE.EXTENSION_SYNCING,
  ): void {
    this.update(STATUS_BAR_ICONS.SYNC_SPIN, STATUS_TEXT_MESSAGES.SYNC, tooltip, type);
  }

  public success(
    type: MODULE_TYPE = MODULE_TYPE.EXTENSION,
    tooltip: string = GRAILS_MESSAGE.SERVER_STARTUP_SUCCESS,
  ): void {
    this.update(STATUS_BAR_ICONS.SUCCESS, STATUS_TEXT_MESSAGES.SUCCESS, tooltip, type);
  }

  /** Set error icon and message. */
  public error(
    type: MODULE_TYPE = MODULE_TYPE.EXTENSION,
    tooltip: string = GRAILS_MESSAGE.SERVER_STARTUP_FAILED,
  ): void {
    this.update(STATUS_BAR_ICONS.ERROR, STATUS_TEXT_MESSAGES.ERROR, tooltip, type);
  }

  /** Set warning icon and message. */
  public warning(
    type: MODULE_TYPE = MODULE_TYPE.EXTENSION,
    tooltip: string = GRAILS_MESSAGE.INVALID_PROJECT,
  ): void {
    this.update(STATUS_BAR_ICONS.WARNING, STATUS_TEXT_MESSAGES.WARNING, tooltip, type);
  }

  /** Set info icon and message. */
  public info(
    type: MODULE_TYPE = MODULE_TYPE.EXTENSION,
    tooltip: string = GRAILS_MESSAGE.EXTENSION_STARTUP,
  ): void {
    this.update(STATUS_BAR_ICONS.INFO, STATUS_TEXT_MESSAGES.INFO, tooltip, type);
  }

  public restart(
    type: MODULE_TYPE = MODULE_TYPE.SERVER,
    tooltip: string = GRAILS_MESSAGE.SERVER_RESTARTED,
  ): void {
    this.update(STATUS_BAR_ICONS.SYNC_SPIN, STATUS_TEXT_MESSAGES.RESTARTING, tooltip, type);
  }

  public reset(): void {
    this.statusBarItem.hide();
  }

  /** Clean up the status bar item. */
  public dispose(): void {
    this.statusBarItem.dispose();
  }
}
