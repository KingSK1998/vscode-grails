# TASK 2a.1: GroovydocCache Gate Check
**EXECUTION MODE:** PATCH MODE

## Objective
Verify if `MethodNode.getGroovydoc()` returns content in the current Groovy 4.0.23 compiler configuration. This determines if we can build a GroovydocCache.

## Files to modify
- `server/src/test/groovy/kingsk/grails/lsp/index/GroovydocGateSpec.groovy` (NEW)

## Code to write
Create a Spock test that:
1. Compiles a simple Groovy class string with a Javadoc comment on a method.
2. Uses `GrailsCompiler` (or `GroovyClassLoader`) to parse it into a `ClassNode`.
3. Extracts the `MethodNode` and calls `.getGroovydoc()`.
4. Asserts whether the result is null or contains the text.
*(Note: even if it returns null, the test should complete. Just log the result and update STATUS.md based on the outcome).*

## Execution Commands
**Build command:** `npm run build:server`
**Test command:** `cd server && ./gradlew test --tests *GroovydocGateSpec*`

## Pass Criteria
- Test compiles and runs successfully.
- Observation recorded: Does Groovy 4 return Groovydoc metadata for AST nodes?

## Status Update
- Update `server/STATUS.md`: Add a row `Phase 2a.1: Groovydoc Gate Check` and set to ✅.
- Note the outcome (supported or not) in `MEMORY.md`.

**STOP HERE. DO NOT CONTINUE TO THE NEXT STEP IN THE SAME SESSION.**
