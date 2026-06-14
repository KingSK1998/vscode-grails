# TASK 2a.5: GrailsService Wiring
**EXECUTION MODE:** PATCH MODE

## Objective
Wire the infrastructure components into `GrailsService` and `GrailsTextDocumentService` with the correct lock scoping and invalidation triggers.

## Files to modify
- `server/src/main/groovy/kingsk/grails/lsp/GrailsService.groovy`
- `server/src/main/groovy/kingsk/grails/lsp/services/GrailsTextDocumentService.groovy`
- `server/src/main/groovy/kingsk/grails/lsp/context/CompilationContext.groovy`

## Methods to modify
**1. `GrailsService.groovy`:**
- Add properties for `ProjectIndex`, `MethodScopeCache`, `GroovydocCache`, `IndexManager`.
- Initialize them in constructor.
- Modify `compileAndVisitAST(TextFile textFile)`: 
  - Restructure to hold `withWriteLock` ONLY for `compiler.compileSourceFile`, `getSourceUnit`, and `visitor.visitSourceUnit()`. Capture `classNodes` inside the lock.
  - OUTSIDE the lock: call `indexManager.rebuildFile(uri, classNodes)`.
- Modify `refreshAndReindexWorkspace()`: Collect all classNodes, call `indexManager.rebuildAll()` outside the write lock.

**2. `GrailsTextDocumentService.groovy`:**
- Modify `didClose()`: Call `service.indexManager.evictFile(textFile.uri)`.

**3. `CompilationContext.groovy`:**
- Expose `ProjectIndex getProjectIndex()`.

## Execution Commands
**Build command:** `npm run build:server`
**Test command:** `cd server && ./gradlew test` (Run all tests to ensure wiring doesn't break anything)

## Pass Criteria
- BUILD SUCCESSFUL, 0 failures.
- Write lock is not held during `indexManager.rebuildFile`.
- Closing a file successfully evicts its symbols from the index.

## Status Update
- Update `server/STATUS.md`: Add `Phase 2a.5: GrailsService Wiring` -> ✅.

**STOP HERE. DO NOT CONTINUE TO THE NEXT STEP IN THE SAME SESSION.**
