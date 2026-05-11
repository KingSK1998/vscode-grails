import type { Disposable } from "vscode";
import { ErrorSeverity, ErrorSource } from "../../services/errors/errorTypes";
import { ServiceContainer } from "../container/ServiceContainer";
import type { GrailsEventMap } from "./eventTypes";

/**
 * Manages workspace and configuration event listeners.
 * Handlers are executed asynchronously to avoid blocking the event loop.
 */
export class EventBus implements Disposable {
  private static _instance: EventBus;

  private readonly listeners: {
    [K in keyof GrailsEventMap]?: ((event: GrailsEventMap[K]) => void)[];
  } = {};

  private readonly handlerStats = new Map<string, { count: number; errors: number; totalTime: number }>();
  private readonly STATS_ENABLED = false;
  private readonly MAX_LISTENERS_PER_EVENT = 100;

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

    if (handlers.length >= this.MAX_LISTENERS_PER_EVENT) {
      console.warn(`[EventBus] Listener limit reached for ${String(eventType)} (${handlers.length}). Auto-removing oldest listener.`);
      handlers.shift();
    }

    handlers.push(handler);

    if (this.listeners[eventType]!.length > this.MAX_LISTENERS_PER_EVENT * 0.8) {
      console.warn(`[EventBus] Listener accumulation warning for ${String(eventType)}: ${handlers.length}/${this.MAX_LISTENERS_PER_EVENT}`);
    }

    // Return disposable for cleanup
    return {
      dispose: () => {
        this.unsubscribe(eventType, handler);
      },
    };
  }

  /**
   * Subscribe to an event exactly once, then auto-unsubscribe.
   */
  subscribeOnce<K extends keyof GrailsEventMap>(
    eventType: K,
    handler: (event: GrailsEventMap[K]) => void
  ): Disposable {
    const wrappedHandler = (event: GrailsEventMap[K]) => {
      this.unsubscribe(eventType, wrappedHandler);
      handler(event);
    };

    if (!this.listeners[eventType]) {
      this.listeners[eventType] = [];
    }
    this.listeners[eventType]!.push(wrappedHandler);

    return {
      dispose: () => {
        this.unsubscribe(eventType, wrappedHandler);
      },
    };
  }

  /**
   * Publish an event to all subscribers.
   * Handlers are executed asynchronously to avoid blocking the event loop.
   */
  publish<K extends keyof GrailsEventMap>(event: GrailsEventMap[K]): void {
    const eventType = event.type as K;
    const handlers = this.listeners[eventType];

    if (!handlers || handlers.length === 0) {
      return;
    }

    setImmediate(() => {
      const handlersCopy = [...handlers];

      for (const handler of handlersCopy) {
        queueMicrotask(() => {
          this.executeHandler(eventType, handler, event);
        });
      }
    });
  }

  private executeHandler<K extends keyof GrailsEventMap>(
    eventType: K,
    handler: (event: GrailsEventMap[K]) => void,
    event: GrailsEventMap[K]
  ): void {
    const startTime = performance.now();
    const handlerName = handler.name || "anonymous";

    try {
      handler(event);

      if (this.STATS_ENABLED) {
        const elapsed = performance.now() - startTime;
        this.updateStats(eventType, handlerName, elapsed, false);
      }
    } catch (error) {
      if (this.STATS_ENABLED) {
        const elapsed = performance.now() - startTime;
        this.updateStats(eventType, handlerName, elapsed, true);
      }

      this.handleHandlerError(eventType, handlerName, error);
    }
  }

  private handleHandlerError<K extends keyof GrailsEventMap>(
    eventType: K,
    handlerName: string,
    error: unknown
  ): void {
    try {
      if (ServiceContainer.isInitialized) {
        ServiceContainer.getInstance().errorService.handleError(
          `Error in event handler for ${String(eventType)} (${handlerName})`,
          error,
          ErrorSource.Extension,
          ErrorSeverity.Warning
        );
      } else {
        console.error(`[EventBus] Error in event handler for ${String(eventType)} (${handlerName}):`, error);
      }
    } catch {
      console.error(`[EventBus] Error in event handler for ${String(eventType)} (${handlerName}):`, error);
    }
  }

  private updateStats<K extends keyof GrailsEventMap>(
    eventType: K,
    handlerName: string,
    elapsed: number,
    hadError: boolean
  ): void {
    const key = `${String(eventType)}:${handlerName}`;
    const stats = this.handlerStats.get(key) ?? { count: 0, errors: 0, totalTime: 0 };

    stats.count++;
    stats.totalTime += elapsed;
    if (hadError) {
      stats.errors++;
    }

    this.handlerStats.set(key, stats);

    if (elapsed > 100) {
      console.warn(`[EventBus] Slow handler detected: ${key} took ${elapsed.toFixed(1)}ms`);
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
   * Get handler statistics for debugging.
   */
  getHandlerStats(): Record<string, { count: number; errors: number; avgTime: number }> {
    const result: Record<string, { count: number; errors: number; avgTime: number }> = {};

    for (const [key, stats] of this.handlerStats.entries()) {
      result[key] = {
        count: stats.count,
        errors: stats.errors,
        avgTime: stats.count > 0 ? stats.totalTime / stats.count : 0,
      };
    }

    return result;
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
    this.handlerStats.clear();
  }

  /**
   * Dispose of the EventBus and clear all listeners.
   */
  dispose(): void {
    this.clearAll();
  }
}
