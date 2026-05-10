# ISSUE-010 · ActivationManager Listener Cleanup

## Problem
`ActivationManager.setupEventListeners()` registers numerous VS Code event listeners (workspace changes, config changes, etc.) but does not track them individually. The current `dispose()` method clears a general disposables array, which may miss some specific listeners.

## Proposed Solution
Implement a strict tracking and disposal system for all event listeners in `ActivationManager.ts`.

### Implementation Plan
1. **Explicit Tracking**: Use a `Set<Disposable>` to track every single listener registered in `setupEventListeners()`.
2. **Categorized Cleanup**: Implement a method to clear specific categories of listeners (e.g., only workspace listeners) when relevant changes occur.
3. **Verification**: Add logging to `dispose()` to verify that the number of registered listeners matches the number of disposed ones.

## Tradeoffs
- **Boilerplate**: Slightly more code required to track each listener separately.
