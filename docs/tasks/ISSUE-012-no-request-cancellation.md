# ISSUE-012 · No Request Cancellation

## Problem
The `LanguageServerManager` sends LSP requests without `CancellationToken` support. If a user cancels an action or closes a file, the server continues processing the request, wasting server-side CPU and memory.

## Proposed Solution
Integrate `CancellationToken` into the LSP request pipeline.

### Implementation Plan
1. **Token Support**: Update `LanguageServerManager.sendRequest` to accept an optional `CancellationToken`.
2. **Token Propagation**: Pass the token down to the `vscode-languageclient` request call.
3. **Automatic Cancellation**: Trigger cancellation automatically when a webview is closed or when a new request of the same type is initiated (superseding the old one).
4. **Dispose Integration**: Ensure all pending requests are cancelled during `LanguageServerManager.dispose()`.

## Tradeoffs
- **API Changes**: Requires updating all calls to `sendRequest` across the codebase.
