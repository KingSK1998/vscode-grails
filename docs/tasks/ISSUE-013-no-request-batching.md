# ISSUE-013 · No Request Batching

## Problem
`GrailsTestService.discoverAllTests()` sends individual LSP requests for every project in a multi-project workspace. This creates excessive network overhead and server-side context switching.

## Proposed Solution
Implement request batching for test discovery.

### Implementation Plan
1. **Protocol Update**: Update the server-side protocol to accept a list of project URIs in a single `discoverTests` request.
2. **Client Update**: Modify `discoverAllTests()` to collect all project URIs and send one batch request.
3. **Response Handling**: Update the client to map the batched response back to the individual projects.
4. **Fallback**: Implement a fallback to individual requests if the server version does not support batching.

## Tradeoffs
- **Server Dependency**: Requires matching changes on the Groovy server side.
