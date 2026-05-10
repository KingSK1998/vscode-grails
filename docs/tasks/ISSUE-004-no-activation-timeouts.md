# ISSUE-004 · No Activation Timeouts

## Problem
While background tasks have individual timeouts, there is no global timeout for the extension activation process. If a core service hangs during initialization, the extension may remain in a "loading" state indefinitely without notifying the user.

## Proposed Solution
Implement a global activation timeout wrapper in `ActivationManager.ts`.

### Implementation Plan
1. **Timeout Wrapper**: Create a promise-based timeout (e.g., 30 seconds) that wraps the `activate()` sequence.
2. **Graceful Failure**: If the timeout is hit, fail the activation process gracefully.
3. **User Notification**: Show a `window.showErrorMessage` notifying the user that activation timed out and some features may be unavailable.
4. **Logging**: Log the failure and the point of hang to the output channel for debugging.

## Tradeoffs
- **Degraded State**: The extension might start in a partially functional state.

## Implementation Status
✅ DONE (2026-05-11)
- `withTimeout<T>()` wrapper function added to ActivationManager.ts
- `ACTIVATION_TIMEOUT_MS = 30000` constant
- Background phase wrapped with 30s timeout
- Individual task timeouts: Gradle (15s), LSP (10s), Discovery (8s)
- User notification via `window.showErrorMessage` on timeout
- Console error logging on timeout
