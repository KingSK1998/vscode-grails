# ISSUE-016 · No Webview Cleanup on Dispose

## Problem
The `onDidDispose` handlers for webviews (Dashboard, Dependency Graph, SQL Preview) only set the `currentPanel` to null. They do not clear internal state, remove event listeners, or cancel pending timers, potentially leading to memory leaks.

## Proposed Solution
Implement comprehensive cleanup logic in the `onDidDispose` callbacks of all webview services.

### Implementation Plan
1. **State Clearing**: Explicitly nullify large data objects and caches used by the webview.
2. **Listener Removal**: Unsubscribe from any `EventBus` listeners created specifically for that webview instance.
3. **Timer Cancellation**: Call `clearInterval` or `clearTimeout` on any active timers.
4. **Verification**: Use the VS Code process explorer to ensure memory is reclaimed after closing multiple webview panels.

## Tradeoffs
- **Code Volume**: More boilerplate in the disposal logic of each service.
