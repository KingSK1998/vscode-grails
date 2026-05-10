# ISSUE-020 · No LSP Handlers Folder (Registry)
**Severity**: 🟡 Medium
**Service**: LSP Handlers
**Status**: ✅ DONE
**Parent Plan**: [client-improvement-plan.md](../client-improvement-plan.md)

## Problem
While the files have been moved to `services/lsp/handlers/`, there is no centralized registry or management system for these handlers. They are currently registered manually in `ActivationManager.ts`, which will become unmanageable as more LSP features are added.

## Proposed Solution
Implement an LSP Handler Registry to automate registration.

### Implementation Plan
1. **Registry Class**: Create an `LspHandlerRegistry` that stores all registered providers.
2. **Provider Interface**: Ensure all handlers implement a common interface (e.g., `ILspProvider`).
3. **Auto-Registration**: Modify `ActivationManager.ts` to simply call `registry.registerAll(context)`, moving the specific `languages.register...` calls into the registry.
4. **Dynamic Loading**: (Optional) Allow handlers to be loaded dynamically based on configuration.

## Tradeoffs
- **Indirection**: Adds one more layer of abstraction between the provider and the VS Code API.
