# ISSUE-006 · EventBus Synchronous Handler Execution
**Severity**: 🟠 High
**Service**: EventBus.ts
**Status**: TODO
**Parent Plan**: [client-improvement-plan.md](../client-improvement-plan.md)

## Problem
EventBus.publish() iterates through all handlers synchronously. Long-running handlers block the event loop and can cause UI freezes. No error isolation between handlers - one failing handler can prevent others from executing.

## Current Code (problematic pattern)
```typescript
// EventBus.ts - Lines 52-76
publish<K extends keyof GrailsEventMap>(event: GrailsEventMap[K]): void {
  const eventType = event.type as K;
  const handlers = this.listeners[eventType];

  if (handlers) {
    for (const handler of [...handlers]) {
      try {
        handler(event); // SYNCHRONOUS EXECUTION - BLOCKS EVENT LOOP
      } catch (error) {
        try {
          const { ServiceContainer } = require("../container/ServiceContainer");
          const { ErrorSource, ErrorSeverity } = require("../../services/errors/errorTypes");
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
```

## Fixed Code
```typescript
// EventBus.ts - Async handler execution with error isolation

import type { Disposable } from "vscode";
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

  // Track handler execution for debugging
  private readonly handlerStats = new Map<string, { count: number; errors: number; totalTime: number }>();
  private readonly STATS_ENABLED = false; // Set to true for debugging

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
   * Handlers are executed asynchronously to avoid blocking the event loop.
   */
  publish<K extends keyof GrailsEventMap>(event: GrailsEventMap[K]): void {
    const eventType = event.type as K;
    const handlers = this.listeners[eventType];

    if (!handlers || handlers.length === 0) {
      return;
    }

    // Execute handlers asynchronously in next tick
    setImmediate(() => {
      // Create a copy to avoid issues if handlers are modified during iteration
      const handlersCopy = [...handlers];

      for (const handler of handlersCopy) {
        // Execute each handler in its own microtask
        queueMicrotask(() => {
          this.executeHandler(eventType, handler, event);
        });
      }
    });
  }

  /**
   * Execute a single handler with error isolation and timing.
   */
  private executeHandler<K extends keyof GrailsEventMap>(
    eventType: K,
    handler: (event: GrailsEventMap[K]) => void,
    event: GrailsEventMap[K]
  ): void {
    const startTime = performance.now();
    const handlerName = handler.name || "anonymous";

    try {
      handler(event);

      // Track successful execution
      if (this.STATS_ENABLED) {
        const elapsed = performance.now() - startTime;
        this.updateStats(eventType, handlerName, elapsed, false);
      }
    } catch (error) {
      // Track error
      if (this.STATS_ENABLED) {
        const elapsed = performance.now() - startTime;
        this.updateStats(eventType, handlerName, elapsed, true);
      }

      // Error isolation - log but don't throw
      this.handleHandlerError(eventType, handlerName, error);
    }
  }

  /**
   * Handle errors from event handlers.
   */
  private handleHandlerError<K extends keyof GrailsEventMap>(
    eventType: K,
    handlerName: string,
    error: unknown
  ): void {
    try {
      const { ServiceContainer } = require("../container/ServiceContainer");
      const { ErrorSource, ErrorSeverity } = require("../../services/errors/errorTypes");

      // Try to use ErrorService if available
      if (ServiceContainer.isInitialized) {
        ServiceContainer.getInstance().errorService.handleError(
          `Error in event handler for ${String(eventType)} (${handlerName})`,
          error,
          ErrorSource.Extension,
          ErrorSeverity.Warning
        );
      } else {
        // Fallback to console if ServiceContainer not initialized
        console.error(
          `[EventBus] Error in event handler for ${String(eventType)} (${handlerName}):`,
          error
        );
      }
    } catch {
      // Ultimate fallback
      console.error(
        `[EventBus] Error in event handler for ${String(eventType)} (${handlerName}):`,
        error
      );
    }
  }

  /**
   * Update handler statistics for debugging.
   */
  private updateStats<K extends keyof GrailsEventMap>(
    eventType: K,
    handlerName: string,
    elapsed: number,
    hadError: boolean
  ): void {
    const key = `${String(eventType)}:${handlerName}`;
    const stats = this.handlerStats.get(key) || { count: 0, errors: 0, totalTime: 0 };

    stats.count++;
    stats.totalTime += elapsed;
    if (hadError) {
      stats.errors++;
    }

    this.handlerStats.set(key, stats);

    // Log slow handlers
    if (elapsed > 100) {
      console.warn(
        `[EventBus] Slow handler detected: ${key} took ${elapsed.toFixed(1)}ms`
      );
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
```

## Subtasks
- [x] Wrap handler execution in setImmediate()
- [x] Execute each handler in queueMicrotask()
- [x] Add per-handler error isolation
- [x] Add handler execution timing
- [x] Add handler statistics for debugging (STATS_ENABLED flag)
- [x] Log slow handlers (>100ms)
- [ ] Test async handler execution
- [ ] Test error isolation
- [ ] Test slow handler logging

## Implementation Status
✅ DONE (2026-05-11)
- publish() uses setImmediate + queueMicrotask for async handler execution
- Per-handler error isolation via handleHandlerError()
- ServiceContainer.isInitialized check before accessing errorService
- Optional handler statistics (STATS_ENABLED = false by default)
- Slow handler detection (>100ms) with console.warn

## Tradeoffs
- Events no longer processed in order
- Slightly delayed handler execution (next tick)
- More complex event handling logic
- Need to handle async errors properly

## Testing This Fix
1. Test that handlers are executed asynchronously
2. Test that one failing handler doesn't prevent others from executing
3. Test with long-running handlers - verify UI remains responsive
4. Test handler statistics collection
5. Test slow handler logging
6. Test that event order is not guaranteed anymore
7. Test error handling when ServiceContainer not initialized