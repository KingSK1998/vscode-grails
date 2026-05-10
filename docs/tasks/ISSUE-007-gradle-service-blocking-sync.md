# ISSUE-007 · GradleService Blocking Sync

## Problem
`GradleService.sync()` blocks the extension host while waiting for the `vscode-gradle` extension to activate and for task providers to load. This can take up to 30 seconds, causing the extension to appear frozen.

## Proposed Solution
Make the sync process non-blocking by returning a promise immediately and completing the sync in the background.

### Implementation Plan
1. **Async Sync**: Modify `sync()` to return a `Promise<void>` immediately.
2. **Readiness Flag**: Add an `isReady` boolean flag to `GradleService` to track the actual sync state.
3. **Status Indicator**: Use `StatusBarService` to show a "Gradle Syncing..." indicator until `isReady` becomes true.
4. **Caller Updates**: Update any commands or services that depend on Gradle to check the `isReady` flag or await the sync promise.

## Tradeoffs
- **Initial Latency**: Features relying on Gradle might be unavailable for a few seconds after the extension starts.
