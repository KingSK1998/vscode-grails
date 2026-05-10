# ISSUE-003 · ProjectService Heavy Synchronous Scanning

## Problem
`countArtifacts()` in `ProjectService.ts` performs multiple synchronous `fs.readdirSync()` calls. On large projects, this blocks the extension host thread, causing UI freezes during project discovery.

## Proposed Solution
Convert the scanning logic from synchronous to asynchronous using `fs.promises`.

### Implementation Plan
1. **Async Conversion**: Change `countArtifacts()` and its internal scanning loops to `async` using `await fs.promises.readdir()`.
2. **Caching**: Implement a simple cache for artifact counts to avoid repeated scanning.
3. **Invalidation**: Use a file watcher on `build.gradle` to invalidate the cache when the project structure changes.
4. **Caller Update**: Update `discoverProjects()` to `await` the async counting process.

## Tradeoffs
- **Complexity**: Introduces `async/await` flow in the project discovery pipeline.
- **Concurrency**: Must ensure multiple simultaneous scans don't lead to race conditions (use a simple lock or sequential queue).
