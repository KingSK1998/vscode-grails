import {
  Disposable,
  ExtensionContext,
  StatusBarAlignment,
  StatusBarItem,
  ThemeIcon,
  window,
} from "vscode";
import { StatusBarIcon, StatusText, Messages, EXTENSION_NAME } from "../../utils/constants";

/**
 * Thin wrapper around a single left-aligned StatusBarItem.
 */
export class StatusBarService implements Disposable {
  private readonly item: StatusBarItem;

  constructor(ctx: ExtensionContext) {
    this.item = window.createStatusBarItem(StatusBarAlignment.Left, 100);
    ctx.subscriptions.push(this.item);
  }

  /* ---------- generic update ------------------------------------------- */

  /**
   * Update the status bar with the given state.
   */
  public update(
    icon: StatusBarIcon | ThemeIcon,
    text: string | StatusText,
    tooltip = String(text),
    timeoutMs?: number
  ): void {
    this.item.name = EXTENSION_NAME;
    this.item.text = `${icon} ${text}`;
    this.item.tooltip = tooltip;
    this.item.command = undefined;
    this.item.show();

    if (timeoutMs) {
      setTimeout(() => this.item.hide(), timeoutMs);
    }
  }

  /* ---------- convenience wrappers ------------------------------------- */

  ready(tooltip: string = Messages.EXTENSION_READY): void {
    this.update(StatusBarIcon.ROCKET, StatusText.READY, tooltip);
  }

  sync(tooltip: string = Messages.GRADLE_SYNC_START): void {
    this.update(StatusBarIcon.SYNC_SPIN, StatusText.SYNC, tooltip);
  }

  success(tooltip: string = Messages.SERVER_STARTED): void {
    this.update(StatusBarIcon.SUCCESS, StatusText.SUCCESS, tooltip, 4000);
  }

  error(tooltip: string = Messages.SERVER_START_FAILED): void {
    this.update(StatusBarIcon.ERROR, StatusText.ERROR, tooltip);
  }

  warning(tooltip: string = Messages.INVALID_PROJECT): void {
    this.update(StatusBarIcon.WARNING, StatusText.WARNING, tooltip);
  }

  info(tooltip: string = Messages.EXTENSION_STARTUP): void {
    this.update(StatusBarIcon.INFO, StatusText.INFO, tooltip);
  }

  restart(tooltip: string = Messages.SERVER_RESTARTED): void {
    this.update(StatusBarIcon.SYNC_SPIN, StatusText.RESTARTING, tooltip);
  }

  reset(): void {
    this.item.hide();
  }

  /** Clean up the status bar item. */
  dispose(): void {
    this.item.dispose();
  }
}
