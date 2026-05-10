# ISSUE-009 · EventBus Listener Accumulation

## Problem
`EventBus.subscribe()` adds listeners without a built-in mechanism for automatic cleanup. If listeners are not explicitly unsubscribed, they accumulate across extension restarts/reloads, leading to memory leaks and duplicate event execution.

## Proposed Solution
Implement listener limits and auto-cleanup mechanisms in `EventBus.ts`.

### Implementation Plan
1. **Count Limits**: Implement a maximum number of listeners per event type (e.g., 100) to prevent runaway accumulation.
2. **Auto-Unsubscribe**: Add optional parameters to `subscribe()` for `once: true` or a timeout after which the listener is removed.
3. **Leak Detection**: Add a debug method to log the current number of listeners per event type.
4. **Lifecycle Integration**: Ensure the `ServiceContainer` triggers a full clear of the `EventBus` during disposal.

## Tradeoffs
- **Unexpected Unsubscriptions**: Auto-unsubscribe logic might remove listeners that were still needed if timeouts are too aggressive.
