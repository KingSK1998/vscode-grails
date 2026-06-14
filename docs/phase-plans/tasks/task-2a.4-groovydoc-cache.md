# TASK 2a.4: GroovydocCache
**EXECUTION MODE:** PATCH MODE

## Objective
Implement `GroovydocCache` using a thread-safe LRU cache with an O(1) reverse index for eviction.

## Files to modify
- `server/src/main/groovy/kingsk/grails/lsp/index/GroovydocCache.groovy` (NEW)

## Methods to add
**1. `GroovydocCache.groovy`:**
- Constructor with capacity (e.g., wrap a `ThreadSafeLruCache<String, String>`).
- State: `Map<String, Set<String>> uriToDescriptors = new ConcurrentHashMap<>()`
- `String getGroovydoc(String descriptor, String uri, Closure<String> loader)` (cache-aside pattern, registers descriptor in `uriToDescriptors`)
- `void evictFile(String uri)` (looks up descriptors by URI, invalidates from cache, removes URI entry)

## Execution Commands
**Build command:** `npm run build:server`
**Test command:** (Write a basic unit test to verify eviction clears the cache) `cd server && ./gradlew test --tests *GroovydocCacheSpec*`

## Pass Criteria
- BUILD SUCCESSFUL, 0 failures.
- Eviction does not require an O(N) scan.

## Status Update
- Update `server/STATUS.md`: Add `Phase 2a.4: GroovydocCache` -> ✅.

**STOP HERE. DO NOT CONTINUE TO THE NEXT STEP IN THE SAME SESSION.**
