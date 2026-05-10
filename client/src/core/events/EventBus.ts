import type { Disposable } from "vscode";
import { ErrorSeverity, ErrorSource } from "../../services/errors/errorTypes";
import { ServiceContainer } from "../container/ServiceContainer";
import type { GrailsEventMap } from "./eventTypes";

/**
 * Manages workspace and configuration event listeners.
 */
export class EventBus implements Disposable {
  private static _instance: EventBus;

  private readonly listeners: {
    [K in keyof GrailsEventMap]?: ((event: GrailsEventMap[K]) => void)[];
  } = {};
  // private readonly listeners = new Map<string, ((event: any) => void)[]>();

  private constructor() {
    // Private constructor to enforce singleton pattern
  }

  public static getInstance(): EventBus {
    if (!EventBus._instance) {
      EventBus._instance = new EventBus();
    }
    return EventBus._instance;
  }

  /**
   * Subscribe to an event type with a callback.
   * Returns a Disposable to unsubscribe.
   */
  subscribe<K extends keyof GrailsEventMap>(
    eventType: K,
    handler: (event: GrailsEventMap[K]) => void
  ): Disposable {
    if (!this.listeners[eventType]) {
      this.listeners[eventType] = [];
    }

    const handlers = this.listeners[eventType]!;
    handlers.push(handler);

    // Return disposable for cleanup
    return {
      dispose: () => {
        this.unsubscribe(eventType, handler);
      },
    };
  }

  /**
   * Publish an event to all subscribers.
   */
  publish<K extends keyof GrailsEventMap>(event: GrailsEventMap[K]): void {
    const eventType = event.type as K;
    const handlers = this.listeners[eventType];

    if (handlers) {
      for (const handler of [...handlers]) {
        try {
          handler(event);
        } catch (error) {
          try {
            ServiceContainer.getInstance().errorService.handleError(
              `Error in event handler for ${String(eventType)}`,
              error,
              ErrorSource.Extension,
              ErrorSeverity.Warning
            );
          } catch {
            console.error(`EventBus: Error in event handler for ${String(eventType)}:`, error);
          }
        }
      }
    }
  }

  /**
   * Unsubscribe a specific callback from an event type.
   */
  private unsubscribe<K extends keyof GrailsEventMap>(
    eventType: K,
    handler: (event: GrailsEventMap[K]) => void
  ): void {
    const handlers = this.listeners[eventType];
    if (!handlers) {
      return;
    }

    const index = handlers.indexOf(handler);
    if (index > -1) {
      handlers.splice(index, 1);
    }

    // Clean up empty listener arrays
    if (handlers.length === 0) {
      delete this.listeners[eventType];
    }
  }

  /**
   * Get number of listeners for an event type (useful for debugging).
   */
  getListenerCount<K extends keyof GrailsEventMap>(eventType: K): number {
    return this.listeners[eventType]?.length ?? 0;
  }

  /**
   * Clear all listeners for an event type.
   */
  clearEventListeners<K extends keyof GrailsEventMap>(eventType: K): void {
    delete this.listeners[eventType];
  }

  /**
   * Clear all listeners (useful for testing).
   */
  clearAll(): void {
    for (const key in this.listeners) {
      delete this.listeners[key as keyof GrailsEventMap];
    }
  }

  /**
   * Dispose of the EventBus and clear all listeners.
   */
  dispose(): void {
    this.clearAll();
  }
}
