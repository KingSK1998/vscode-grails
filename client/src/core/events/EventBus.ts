import { Disposable } from "vscode";
import { GrailsEvent } from "./eventTypes";

/**
 * Manages workspace and configuration event listeners.
 */
export class EventBus implements Disposable {
  private static _instance: EventBus;
  private readonly listeners = new Map<string, Array<(event: any) => void>>();

  private constructor() {}

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
  subscribe<T extends GrailsEvent>(eventType: T["type"], handler: (event: T) => void): Disposable {
    if (!this.listeners.has(eventType)) {
      this.listeners.set(eventType, []);
    }

    this.listeners.get(eventType)!.push(handler);

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
  publish<T extends GrailsEvent>(event: T): void {
    const eventListeners = this.listeners.get(event.type);
    if (eventListeners) {
      // Create a copy to prevent modification during iteration
      [...eventListeners].forEach(handler => {
        try {
          handler(event);
        } catch (error) {
          console.error(`EventBus: Error in event handler for ${event.type}:`, error);
        }
      });
    }
  }

  /**
   * Unsubscribe a specific callback from an event type.
   */
  private unsubscribe<T extends GrailsEvent>(
    eventType: T["type"],
    handler: (event: T) => void
  ): void {
    const eventListeners = this.listeners.get(eventType);
    if (eventListeners) {
      const index = eventListeners.indexOf(handler);
      if (index > -1) {
        eventListeners.splice(index, 1);
      }

      // Clean up empty listener arrays
      if (eventListeners.length === 0) {
        this.listeners.delete(eventType);
      }
    }
  }

  /**
   * Get number of listeners for an event type (useful for debugging).
   */
  getListenerCount(eventType: string): number {
    return this.listeners.get(eventType)?.length || 0;
  }

  /**
   * Clear all listeners for an event type.
   */
  clearEventListeners(eventType: string): void {
    this.listeners.delete(eventType);
  }

  /**
   * Clear all listeners (useful for testing).
   */
  clearAll(): void {
    this.listeners.clear();
  }

  /**
   * Dispose of the EventBus and clear all listeners.
   */
  dispose(): void {
    this.clearAll();
  }
}
