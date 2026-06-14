# TASK 2a.3: IndexManager & ProjectIndex CAS
**EXECUTION MODE:** PATCH MODE

## Objective
Add thread-safe CAS operations to `ProjectIndex` and implement `IndexManager` as a service-layer orchestrator for index and method scope cache population.

## Files to modify
- `server/src/main/groovy/kingsk/grails/lsp/index/ProjectIndex.groovy`
- `server/src/main/groovy/kingsk/grails/lsp/index/IndexManager.groovy` (NEW)

## Methods to add
**1. `ProjectIndex.groovy`:**
- `boolean compareAndCommit(IndexSnapshot expected, IndexSnapshot next)` (calls `currentSnapshot.compareAndSet(expected, next)`)

**2. `IndexManager.groovy` (Service Component, NOT BaseProvider):**
- Constructor: `IndexManager(ProjectIndex projectIndex, MethodScopeCache methodScopeCache, GroovydocCache groovydocCache)`
- `void rebuildFile(String uri, List<ClassNode> classNodes)` (implements CAS retry loop using `compareAndCommit`, then calls `methodScopeCache.evict()` and `putLocals()`, and `groovydocCache.evictFile()`)
- `void rebuildAll(Map<String, List<ClassNode>> allNodes)`
- `void evictFile(String uri)` (evicts from ProjectIndex, methodScopeCache, and groovydocCache)

## Execution Commands
**Build command:** `npm run build:server`
**Test command:** `cd server && ./gradlew test --tests *IndexManagerSpec*` (Optional but recommended to verify CAS logic)

## Pass Criteria
- BUILD SUCCESSFUL.
- `IndexManager` does NOT extend `BaseProvider`. It only depends on index/cache classes.

## Status Update
- Update `server/STATUS.md`: Add `Phase 2a.3: IndexManager` -> ✅.

**STOP HERE. DO NOT CONTINUE TO THE NEXT STEP IN THE SAME SESSION.**
