# ISSUE-015 · D3.js CDN Dependency

## Problem
`DependencyGraphService` loads D3.js from a CDN. This causes failures in corporate environments with strict firewalls and slows down the webview loading on poor connections.

## Proposed Solution
Bundle the D3.js library locally within the extension resources.

### Implementation Plan
1. **Local Asset**: Download D3.js v7 and place it in `client/resources/lib/d3.min.js`.
2. **URI Resolution**: Use `webview.asWebviewUri` to point the HTML script tag to the local file.
3. **Fallback Mechanism**: Implement a fallback to the CDN only if the local load fails (optional).
4. **Verification**: Test the Dependency Graph in an offline environment.

## Tradeoffs
- **Bundle Size**: Slightly increases the extension's installation size (~200KB).
